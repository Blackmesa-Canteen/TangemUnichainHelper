package com.example.tangemunichainhelper.core

import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.sdk.extensions.init
import kotlinx.coroutines.suspendCancellableCoroutine
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
            tangemSdk.scanCard { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        val card = result.data
                        Timber.d("Card scanned successfully. Card ID: ${card.cardId}")

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

        Timber.d("Signing transaction with card: $cardId")
        Timber.d("Transaction hash: ${Numeric.toHexString(transactionHash)}")

        // Tangem SDK sign method
        tangemSdk.sign(
            hashes = arrayOf(transactionHash),
            cardId = cardId,
            walletPublicKey = walletPublicKey,
        ) { result ->
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
                    Timber.e("Transaction signing failed: ${error.customMessage}")

                    val exception = when (error) {
                        is TangemSdkError.UserCancelled ->
                            Exception("User cancelled transaction signing")
                        else ->
                            Exception("Signing failed: ${error.customMessage}")
                    }

                    continuation.resume(Result.failure(exception))
                }
            }
        }
    }

    /**
     * Sign and encode transaction for Ethereum
     * This converts the Tangem signature to Ethereum format
     */
    fun encodeSignedTransaction(
        transactionHash: ByteArray,
        signature: ByteArray,
        chainId: Long
    ): ByteArray {
        // Tangem returns signature in format: r (32 bytes) + s (32 bytes) + v (1 byte)
        // We need to convert it to Ethereum format

        if (signature.size < 65) {
            throw IllegalArgumentException("Invalid signature length: ${signature.size}")
        }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)
        val v = signature[64]

        Timber.d("Signature components - r: ${Numeric.toHexString(r)}, s: ${Numeric.toHexString(s)}, v: $v")

        // For EIP-155, we need to encode v with chain ID
        // v = CHAIN_ID * 2 + 35 + {0, 1}
        val vWithChainId = (chainId * 2 + 35 + (v.toInt() and 1)).toByte()

        // Create the signature data
        val signatureData = Sign.SignatureData(
            vWithChainId,
            r,
            s
        )

        Timber.d("Encoded v with chain ID: $vWithChainId")

        // Combine signature components
        return r + s + byteArrayOf(vWithChainId)
    }
}

data class CardInfo(
    val cardId: String,
    val publicKey: String,
    val walletAddress: String
)
