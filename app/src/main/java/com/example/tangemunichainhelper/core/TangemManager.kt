package com.example.tangemunichainhelper.core

import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.sdk.extensions.init
import kotlinx.coroutines.suspendCancellableCoroutine
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import kotlin.coroutines.resume

class TangemManager(private val activity: ComponentActivity) {

    private val tangemSdk: TangemSdk = TangemSdk.init(activity)

    /**
     * Scan Tangem card to get card info
     */
    suspend fun scanCard(accessCode: String? = null): Result<CardInfo> =
        suspendCancellableCoroutine { continuation ->
            Timber.d("testtst")
            tangemSdk.scanCard { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        val card = result.data
                        Timber.d("Card scanned successfully. Card ID: ${card.cardId}")

                        // LOG CARD CAPABILITIES
                        Timber.d("Card ID: ${card.cardId}")
                        Timber.d("Card firmware: ${card.firmwareVersion}")
                        Timber.d("Card settings: ${card.settings}")
                        Timber.d("Supported curves: ${card.supportedCurves}")
                        Timber.d("testtst")

                        // Get the first wallet (you can modify this to select specific wallet)
                        val wallet = card.wallets.firstOrNull()

                        if (wallet == null) {
                            continuation.resume(Result.failure(Exception("No wallet found on card")))
                            return@scanCard
                        }

                        val cardInfo = CardInfo(
                            cardId = card.cardId,
                            publicKey = Numeric.toHexString(wallet.publicKey),
                            walletAddress = NetworkConstants.WALLET_ADDRESS
                        )

                        continuation.resume(Result.success(cardInfo))
                    }
                    is CompletionResult.Failure -> {
                        val error = result.error
                        Timber.e("Card scan failed: ${error.customMessage}")

                        val exception = when (error) {
                            is TangemSdkError.UserCancelled ->
                                Exception("User cancelled card scan")
                            else ->
                                Exception("Card scan failed: ${error.customMessage}")
                        }

                        continuation.resume(Result.failure(exception))
                    }
                }
            }
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
