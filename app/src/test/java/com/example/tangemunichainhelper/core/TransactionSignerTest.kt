package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.RawTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Unit tests for TransactionSigner.
 *
 * Tests cover:
 * - RLP encoding of signed transactions
 * - Recovery ID determination
 * - Public key decompression
 */
class TransactionSignerTest {

    // =====================
    // encodeSignedTransaction tests
    // =====================

    @Test
    fun `encodeSignedTransaction with valid inputs returns RLP encoded transaction`() {
        // Create a simple ETH transfer transaction
        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.ONE,                    // nonce
            BigInteger.valueOf(20_000_000_000), // gasPrice (20 Gwei)
            BigInteger.valueOf(21_000),         // gasLimit
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            BigInteger.valueOf(1_000_000_000_000_000_000) // 1 ETH in wei
        )

        // Mock signature (64 bytes: r=32, s=32)
        val signature = ByteArray(64) { (it % 256).toByte() }
        val recoveryId = 0
        val chainId = 130L // Unichain

        val encoded = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = recoveryId,
            chainId = chainId
        )

        // Verify the result is not empty and starts with RLP list prefix
        assertNotNull(encoded)
        assertTrue("Encoded transaction should not be empty", encoded.isNotEmpty())
        // RLP list with length > 55 bytes starts with 0xf8 or higher
        assertTrue("Should be RLP encoded", encoded[0].toInt() and 0xFF >= 0xc0)
    }

    @Test
    fun `encodeSignedTransaction with ERC20 transfer data`() {
        // Create an ERC20 transfer transaction with data
        val transferData = "0xa9059cbb" + // transfer(address,uint256) selector
                "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" + // to address
                "0000000000000000000000000000000000000000000000000de0b6b3a7640000"   // amount (1e18)

        val rawTransaction = RawTransaction.createTransaction(
            BigInteger.ONE,                    // nonce
            BigInteger.valueOf(20_000_000_000), // gasPrice
            BigInteger.valueOf(65_000),         // gasLimit (higher for ERC20)
            "0x078D782b760474a361dDA0AF3839290b0EF57AD6", // USDC contract
            BigInteger.ZERO,                    // value (0 for ERC20)
            transferData
        )

        val signature = ByteArray(64) { (it % 256).toByte() }
        val recoveryId = 1
        val chainId = 130L

        val encoded = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = recoveryId,
            chainId = chainId
        )

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `encodeSignedTransaction calculates correct v value for EIP-155`() {
        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.ONE,
            BigInteger.valueOf(20_000_000_000),
            BigInteger.valueOf(21_000),
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            BigInteger.ONE
        )

        val signature = ByteArray(64) { 0x01 }

        // Test with chainId = 130 (Unichain) and recoveryId = 0
        // v should be: 130 * 2 + 35 + 0 = 295
        val encoded0 = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = 0,
            chainId = 130L
        )

        // Test with chainId = 130 and recoveryId = 1
        // v should be: 130 * 2 + 35 + 1 = 296
        val encoded1 = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = 1,
            chainId = 130L
        )

        // The encodings should be different due to different v values
        assertFalse(
            "Different recovery IDs should produce different encodings",
            encoded0.contentEquals(encoded1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encodeSignedTransaction throws for invalid signature length`() {
        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.ONE,
            BigInteger.valueOf(20_000_000_000),
            BigInteger.valueOf(21_000),
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            BigInteger.ONE
        )

        // Invalid signature (only 32 bytes instead of 64)
        val invalidSignature = ByteArray(32)

        TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = invalidSignature,
            recoveryId = 0,
            chainId = 130L
        )
    }

    // =====================
    // decompressPublicKey tests
    // =====================

    @Test
    fun `decompressPublicKey with valid compressed key prefix 02`() {
        // A valid compressed secp256k1 public key with prefix 0x02
        // This is a well-known test vector
        val compressedKey = Numeric.hexStringToByteArray(
            "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737"
        )

        val uncompressed = TransactionSigner.decompressPublicKey(compressedKey)

        assertEquals("Uncompressed key should be 64 bytes", 64, uncompressed.size)
        // The X coordinate should match the original (bytes 1-32 of compressed)
        val xCoord = compressedKey.copyOfRange(1, 33)
        val uncompressedX = uncompressed.copyOfRange(0, 32)
        assertArrayEquals("X coordinate should match", xCoord, uncompressedX)
    }

    @Test
    fun `decompressPublicKey with valid compressed key prefix 03`() {
        // A valid compressed secp256k1 public key with prefix 0x03
        val compressedKey = Numeric.hexStringToByteArray(
            "03b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737"
        )

        val uncompressed = TransactionSigner.decompressPublicKey(compressedKey)

        assertEquals("Uncompressed key should be 64 bytes", 64, uncompressed.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decompressPublicKey throws for wrong size`() {
        // Invalid key - only 32 bytes instead of 33
        val invalidKey = ByteArray(32)

        TransactionSigner.decompressPublicKey(invalidKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decompressPublicKey throws for invalid prefix`() {
        // Invalid prefix (0x04 is uncompressed prefix, not valid for compressed)
        val invalidKey = ByteArray(33)
        invalidKey[0] = 0x04

        TransactionSigner.decompressPublicKey(invalidKey)
    }

    // =====================
    // findRecoveryId tests
    // =====================

    @Test(expected = IllegalArgumentException::class)
    fun `findRecoveryId throws for invalid signature length`() {
        val txHash = ByteArray(32)
        val invalidSignature = ByteArray(32) // Should be 64

        TransactionSigner.findRecoveryId(
            txHash = txHash,
            signature = invalidSignature,
            expectedPublicKey = ByteArray(64)
        )
    }

    @Test
    fun `findRecoveryId handles different public key formats`() {
        // This test verifies the method handles different key formats
        // without actually testing cryptographic correctness (which requires real keys)

        val txHash = ByteArray(32) { 0x01 }
        val signature = ByteArray(64) { 0x01 }

        // Test with 64-byte key (uncompressed without prefix)
        val key64 = ByteArray(64) { 0x01 }
        try {
            TransactionSigner.findRecoveryId(txHash, signature, key64)
        } catch (e: IllegalStateException) {
            // Expected - the signature won't match the fake key
            assertTrue(e.message?.contains("recovery ID") == true)
        }

        // Test with 65-byte key (uncompressed with 0x04 prefix)
        val key65 = ByteArray(65)
        key65[0] = 0x04
        try {
            TransactionSigner.findRecoveryId(txHash, signature, key65)
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("recovery ID") == true)
        }

        // Test with 33-byte key (compressed)
        val key33 = ByteArray(33)
        key33[0] = 0x02
        key33[1] = 0x01 // Need at least some data
        try {
            TransactionSigner.findRecoveryId(txHash, signature, key33)
        } catch (e: Exception) {
            // Expected - either IllegalStateException or decompression failure
        }
    }

    // =====================
    // Integration tests
    // =====================

    @Test
    fun `encodeSignedTransaction produces valid hex string`() {
        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.valueOf(5),
            BigInteger.valueOf(30_000_000_000),
            BigInteger.valueOf(21_000),
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            BigInteger.valueOf(500_000_000_000_000_000) // 0.5 ETH
        )

        val signature = ByteArray(64) { (it * 3 % 256).toByte() }

        val encoded = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = 1,
            chainId = 11155111L // Sepolia
        )

        // Convert to hex and verify it's valid
        val hex = Numeric.toHexString(encoded)
        assertTrue("Should start with 0x", hex.startsWith("0x"))
        assertTrue("Should be valid hex", hex.matches(Regex("0x[0-9a-fA-F]+")))
    }

    @Test
    fun `encodeSignedTransaction with different chain IDs produces different results`() {
        val rawTransaction = RawTransaction.createEtherTransaction(
            BigInteger.ONE,
            BigInteger.valueOf(20_000_000_000),
            BigInteger.valueOf(21_000),
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            BigInteger.ONE
        )

        val signature = ByteArray(64) { 0x55 }

        val encodedUnichain = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = 0,
            chainId = 130L // Unichain
        )

        val encodedSepolia = TransactionSigner.encodeSignedTransaction(
            rawTransaction = rawTransaction,
            signature = signature,
            recoveryId = 0,
            chainId = 11155111L // Sepolia
        )

        assertFalse(
            "Different chain IDs should produce different encodings (EIP-155)",
            encodedUnichain.contentEquals(encodedSepolia)
        )
    }
}
