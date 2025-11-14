package com.example.tangemunichainhelper.core

import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.Config
import com.tangem.common.core.TangemSdkError
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.sdk.extensions.init
import kotlinx.coroutines.suspendCancellableCoroutine
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.Wallet
import org.web3j.utils.Numeric
import timber.log.Timber
import kotlin.coroutines.resume

class TangemManager(private val activity: ComponentActivity) {

    private val tangemSdk: TangemSdk = TangemSdk.init(
        activity = activity,
        config = Config().apply {
            // Set default derivation paths for Ethereum
            defaultDerivationPaths = mutableMapOf(
                EllipticCurve.Secp256k1 to listOf(
                    DerivationPath(rawPath = "m/44'/60'/0'/0/0")  // Ethereum
                )
            )
        }
    )

    /**
     * Scan Tangem card to get card info
     */
    suspend fun scanCard(accessCode: String? = null): Result<CardInfo> =
        suspendCancellableCoroutine { continuation ->
            Timber.d("Starting card scan...")
            tangemSdk.scanCard { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        val card = result.data
                        Timber.d("Card scanned successfully. Card ID: ${card.cardId}")
                        Timber.d("Is HD wallet: ${card.settings.isHDWalletAllowed}")
                        Timber.d("Number of wallets: ${card.wallets.size}")

                        card.wallets.forEachIndexed { index, wallet ->
                            Timber.d("=== Wallet $index ===")
                            Timber.d("  Public key: ${Numeric.toHexString(wallet.publicKey)}")
                            Timber.d("  Curve: ${wallet.curve}")
                            Timber.d("  Has backup: ${wallet.hasBackup}")
                            Timber.d("  Settings: ${wallet.settings}")
                            Timber.d("  Index: ${wallet.index}")

                            val ethPath = DerivationPath(rawPath = "m/44'/60'/0'/0/0")
                            val derivedKey = wallet.derivedKeys[ethPath]

                            if (derivedKey != null) {
                                // this is the address!
                                val ethAddress = calculateEthereumAddress(derivedKey.publicKey)
                                Timber.d("Test Derived Ethereum address: $ethAddress")
                                // This should match 0x5A4dC932a92Eb68529522eA79b566C01515F6436
                            }

                            // Check if this wallet has derivation info
                            try {
                                val walletClass = wallet::class.java
                                walletClass.declaredFields.forEach { field ->
                                    field.isAccessible = true
                                    val value = field.get(wallet)
                                    if (field.name.contains("deriv", ignoreCase = true) ||
                                        field.name.contains("path", ignoreCase = true) ||
                                        field.name.contains("hd", ignoreCase = true)) {
                                        Timber.d("  ${field.name}: $value")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e("Could not inspect wallet fields: ${e.message}")
                            }

                            // Try to calculate address
                            val calculatedAddress = try {
                                calculateEthereumAddress(wallet.publicKey)
                            } catch (e: Exception) {
                                null
                            }
                            Timber.d("  Calculated address: $calculatedAddress")
                            Timber.d("  Target address: ${NetworkConstants.WALLET_ADDRESS}")
                        }

                        // For now, use the first wallet
                        val wallet = card.wallets.firstOrNull()

                        if (wallet == null) {
                            continuation.resume(Result.failure(Exception("No wallet found on card")))
                            return@scanCard
                        }

                        val cardInfo = CardInfo(
                            cardId = card.cardId,
                            publicKey = Numeric.toHexString(wallet.publicKey),
                            walletAddress = NetworkConstants.WALLET_ADDRESS  // Use the known correct address for now
                        )

                        continuation.resume(Result.success(cardInfo))
                    }
                    is CompletionResult.Failure -> {
                        // ... existing error handling ...
                    }
                }
            }
        }

    /**
     * Calculate Ethereum address from public key
     */
    private fun calculateEthereumAddress(publicKey: ByteArray): String {
        Timber.d("Calculating Ethereum address from public key (${publicKey.size} bytes)")

        val publicKeyWithoutPrefix = when {
            // Uncompressed key with 0x04 prefix (65 bytes)
            publicKey.size == 65 && publicKey[0] == 0x04.toByte() -> {
                Timber.d("Uncompressed key detected, removing 0x04 prefix")
                publicKey.copyOfRange(1, 65)
            }
            // Compressed key (33 bytes starting with 0x02 or 0x03)
            publicKey.size == 33 && (publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte()) -> {
                Timber.d("Compressed key detected, decompressing...")
                val spec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
                val point = spec.curve.decodePoint(publicKey)
                val uncompressed = point.getEncoded(false) // false = uncompressed
                Timber.d("Decompressed to ${uncompressed.size} bytes")
                uncompressed.copyOfRange(1, uncompressed.size) // Remove 0x04 prefix
            }
            // Already uncompressed without prefix (64 bytes)
            publicKey.size == 64 -> {
                Timber.d("Already uncompressed 64-byte key")
                publicKey
            }
            else -> {
                throw IllegalArgumentException("Unexpected public key size: ${publicKey.size} bytes")
            }
        }

        require(publicKeyWithoutPrefix.size == 64) {
            "Public key must be 64 bytes after processing, got ${publicKeyWithoutPrefix.size}"
        }

        // Keccak256 hash of the 64-byte public key
        val hash = Hash.sha3(publicKeyWithoutPrefix)

        // Ethereum address is the last 20 bytes of the hash
        val addressBytes = hash.copyOfRange(hash.size - 20, hash.size)

        // Convert to checksummed address
        val address = Numeric.toHexString(addressBytes)

        Timber.d("Derived Ethereum address: $address")

        return address
    }

    /**
     * Sign transaction hash with Tangem card
     */
    suspend fun signTransactionHash(
        cardId: String,
        transactionHash: ByteArray,
        walletPublicKey: ByteArray,
    ): Result<ByteArray> = suspendCancellableCoroutine { continuation ->

        Timber.d("=== SIGNING TRANSACTION ===")
        Timber.d("Card ID: $cardId")
        Timber.d("Transaction hash length: ${transactionHash.size}")
        Timber.d("Transaction hash: ${Numeric.toHexString(transactionHash)}")
        Timber.d("Wallet public key: ${Numeric.toHexString(walletPublicKey)}")

        // Track if continuation was already resumed
        var isResumed = false

        // Handle cancellation
        continuation.invokeOnCancellation {
            Timber.d("Transaction signing was cancelled")
        }

        // Tangem SDK sign method
        tangemSdk.sign(
            hashes = arrayOf(transactionHash),
            cardId = cardId,
            walletPublicKey = walletPublicKey,
        ) { result ->
            // Only resume if not already resumed
            if (!isResumed) {
                isResumed = true

                when (result) {
                    is CompletionResult.Success -> {
                        val signResponse = result.data

                        if (signResponse.signatures.isEmpty()) {
                            continuation.resume(
                                Result.failure(Exception("No signature received from card"))
                            )
                            return@sign
                        }

                        val signature = signResponse.signatures[0]
                        Timber.d("Transaction signed successfully")
                        Timber.d("Signature: ${Numeric.toHexString(signature)}")

                        continuation.resume(Result.success(signature))
                    }
                    is CompletionResult.Failure -> {
                        val error = result.error

                        // DETAILED ERROR LOGGING
                        Timber.e("=== SIGNING FAILED ===")
                        Timber.e("Error class: ${error::class.java.name}")
                        Timber.e("Error simpleName: ${error::class.simpleName}")
                        Timber.e("Error message: ${error.customMessage}")
                        Timber.e("Error code: ${error.code}")
                        Timber.e("Error toString: $error")

                        // Try to get more details
                        try {
                            val errorFields = error::class.java.declaredFields
                            Timber.e("Error fields:")
                            errorFields.forEach { field ->
                                field.isAccessible = true
                                try {
                                    val value = field.get(error)
                                    Timber.e("  ${field.name}: $value")
                                } catch (e: Exception) {
                                    Timber.e("  ${field.name}: <unable to read>")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e("Could not inspect error fields: ${e.message}")
                        }

                        val exception = when (error) {
                            is TangemSdkError.UserCancelled ->
                                Exception("User cancelled transaction signing")
                            else ->
                                Exception("Signing failed: ${error.code} - ${error.customMessage}")
                        }

                        continuation.resume(Result.failure(exception))
                    }
                }
            } else {
                Timber.w("Attempted to resume continuation multiple times")
            }
        }
    }
}

data class CardInfo(
    val cardId: String,
    val publicKey: String,
    val walletAddress: String
)
