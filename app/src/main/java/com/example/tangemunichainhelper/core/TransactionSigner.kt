package com.example.tangemunichainhelper.core

import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

/**
 * Utility object for encoding and signing Ethereum transactions.
 *
 * Handles:
 * - RLP encoding of signed transactions with EIP-155 replay protection
 * - Recovery ID determination for ECDSA signatures
 * - Public key decompression (secp256k1)
 */
object TransactionSigner {

    /**
     * Encodes a signed transaction with the given signature for broadcast.
     *
     * Uses EIP-155 replay protection by including the chain ID in the v value:
     * v = chainId * 2 + 35 + recoveryId
     *
     * @param rawTransaction The unsigned transaction
     * @param signature 64-byte ECDSA signature (r || s)
     * @param recoveryId Recovery ID (0 or 1) for public key recovery
     * @param chainId The chain ID for EIP-155 replay protection
     * @return RLP-encoded signed transaction ready for broadcast
     */
    fun encodeSignedTransaction(
        rawTransaction: RawTransaction,
        signature: ByteArray,
        recoveryId: Int,
        chainId: Long
    ): ByteArray {
        Timber.d("Encoding signed transaction...")
        Timber.d("Signature length: ${signature.size}")
        Timber.d("Recovery ID: $recoveryId")
        Timber.d("Chain ID: $chainId")

        require(signature.size == 64) { "Expected 64-byte signature, got ${signature.size}" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        Timber.d("r: ${Numeric.toHexString(r)}")
        Timber.d("s: ${Numeric.toHexString(s)}")

        // EIP-155 v value calculation:
        // v = chainId * 2 + 35 + recoveryId
        //
        // For Unichain (chainId = 130):
        // v = 130 * 2 + 35 + recoveryId = 295 + recoveryId
        // So v will be 295 (if recoveryId=0) or 296 (if recoveryId=1)
        val vValue = chainId * 2 + 35 + recoveryId
        val vBigInt = BigInteger.valueOf(vValue)

        Timber.d("Calculated v (EIP-155): $vValue (chainId=$chainId, recoveryId=$recoveryId)")

        // Convert signature components to BigInteger
        val rBigInt = BigInteger(1, r)
        val sBigInt = BigInteger(1, s)

        // Create the signed transaction manually using RLP encoding
        // Note: data field must be converted from hex string to bytes
        val dataBytes = if (rawTransaction.data.isNullOrEmpty()) {
            ByteArray(0)
        } else {
            Numeric.hexStringToByteArray(rawTransaction.data)
        }

        val signedValues = listOf(
            RlpString.create(rawTransaction.nonce),
            RlpString.create(rawTransaction.gasPrice),
            RlpString.create(rawTransaction.gasLimit),
            RlpString.create(Numeric.hexStringToByteArray(rawTransaction.to)),
            RlpString.create(rawTransaction.value),
            RlpString.create(dataBytes),
            RlpString.create(vBigInt),
            RlpString.create(rBigInt),
            RlpString.create(sBigInt)
        )

        val rlpList = RlpList(signedValues)
        val encoded = RlpEncoder.encode(rlpList)

        Timber.d("Final signed transaction length: ${encoded.size}")
        Timber.d("Final signed transaction: ${Numeric.toHexString(encoded)}")

        return encoded
    }

    /**
     * Finds the correct recovery ID by trying both values and comparing recovered public keys.
     *
     * The recovery ID is needed to recover the signer's public key from an ECDSA signature.
     * Since there are two possible public keys for any valid signature, we need to determine
     * which one matches the expected public key.
     *
     * @param txHash The 32-byte transaction hash that was signed
     * @param signature 64-byte ECDSA signature (r || s)
     * @param expectedPublicKey The expected public key (33, 64, or 65 bytes)
     * @return The correct recovery ID (0 or 1)
     * @throws IllegalStateException if neither recovery ID produces the expected public key
     */
    fun findRecoveryId(
        txHash: ByteArray,
        signature: ByteArray,
        expectedPublicKey: ByteArray
    ): Int {
        require(signature.size == 64) { "Signature must be 64 bytes for recovery" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        Timber.d("=== FINDING RECOVERY ID ===")
        Timber.d("Expected public key: ${Numeric.toHexString(expectedPublicKey)}")

        // Decompress the public key if needed
        val expectedKeyUncompressed = when (expectedPublicKey.size) {
            33 -> {
                // Compressed key - decompress it
                Timber.d("Decompressing public key...")
                try {
                    val decompressed = decompressPublicKey(expectedPublicKey)
                    Timber.d("Decompressed key: ${Numeric.toHexString(decompressed)}")
                    decompressed
                } catch (e: Exception) {
                    Timber.e("Failed to decompress key: ${e.message}")
                    return 0
                }
            }
            65 -> {
                // Uncompressed with 0x04 prefix - remove prefix
                expectedPublicKey.copyOfRange(1, 65)
            }
            64 -> {
                // Already uncompressed without prefix
                expectedPublicKey
            }
            else -> {
                Timber.e("Unexpected public key size: ${expectedPublicKey.size}")
                return 0
            }
        }

        // Calculate and log expected address
        val expectedAddress = try {
            val hash = Hash.sha3(expectedKeyUncompressed)
            val addressBytes = hash.copyOfRange(hash.size - 20, hash.size)
            Numeric.toHexString(addressBytes)
        } catch (e: Exception) {
            Timber.e("Failed to calculate expected address: ${e.message}")
            null
        }
        Timber.d("Expected address from public key: $expectedAddress")

        // Try both recovery IDs
        for (recoveryId in 0..1) {
            try {
                val v = (27 + recoveryId).toByte()
                val signatureData = Sign.SignatureData(v, r, s)

                Timber.d("Trying recovery ID $recoveryId (v=$v)...")

                val recoveredKey = Sign.signedMessageHashToKey(txHash, signatureData)
                val recoveredPublicKey = Numeric.toBytesPadded(recoveredKey, 64)

                // Calculate recovered address
                val recoveredAddress = try {
                    val hash = Hash.sha3(recoveredPublicKey)
                    val addressBytes = hash.copyOfRange(hash.size - 20, hash.size)
                    Numeric.toHexString(addressBytes)
                } catch (e: Exception) {
                    null
                }

                Timber.d("Recovered public key: ${Numeric.toHexString(recoveredPublicKey)}")
                Timber.d("Recovered address: $recoveredAddress")

                if (recoveredPublicKey.contentEquals(expectedKeyUncompressed)) {
                    Timber.d("✓ Found correct recovery ID: $recoveryId")
                    Timber.d("✓ Address match confirmed: $recoveredAddress")
                    return recoveryId
                } else {
                    Timber.d("✗ Recovery ID $recoveryId doesn't match")
                    Timber.d("  Expected address: $expectedAddress")
                    Timber.d("  Recovered address: $recoveredAddress")
                }
            } catch (e: Exception) {
                Timber.w("Recovery ID $recoveryId failed: ${e.message}")
                continue
            }
        }

        // If we can't find the correct recovery ID, the transaction would fail anyway
        // Better to fail early with a clear error than broadcast an invalid transaction
        Timber.e("Could not determine correct recovery ID!")
        Timber.e("Expected public key: ${Numeric.toHexString(expectedPublicKey)}")
        Timber.e("This typically means the signature doesn't match the expected public key")
        throw IllegalStateException(
            "Could not determine correct recovery ID. " +
            "The signature may not match the expected public key."
        )
    }

    /**
     * Decompresses a secp256k1 public key from 33 bytes (compressed) to 64 bytes (uncompressed).
     *
     * Compressed public keys have format: prefix (0x02 or 0x03) + 32-byte X coordinate
     * Uncompressed public keys have format: 64 bytes (X + Y coordinates, no prefix)
     *
     * @param compressedKey 33-byte compressed public key
     * @return 64-byte uncompressed public key (without 0x04 prefix)
     */
    fun decompressPublicKey(compressedKey: ByteArray): ByteArray {
        require(compressedKey.size == 33) { "Compressed key must be 33 bytes" }

        val prefix = compressedKey[0]
        require(prefix == 0x02.toByte() || prefix == 0x03.toByte()) {
            "Invalid compressed key prefix: $prefix"
        }

        try {
            // Use Bouncy Castle to decompress
            val spec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
            val point = spec.curve.decodePoint(compressedKey)

            // Get uncompressed encoding (without 0x04 prefix)
            val uncompressed = point.getEncoded(false) // false = uncompressed format

            Timber.d("Original compressed (${compressedKey.size} bytes): ${Numeric.toHexString(compressedKey)}")
            Timber.d("Decompressed (${uncompressed.size} bytes): ${Numeric.toHexString(uncompressed)}")

            // Remove the 0x04 prefix byte to get just the 64-byte key
            return uncompressed.copyOfRange(1, uncompressed.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decompress public key")
            throw e
        }
    }
}
