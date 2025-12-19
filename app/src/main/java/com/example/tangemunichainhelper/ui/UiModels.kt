package com.example.tangemunichainhelper.ui

import com.example.tangemunichainhelper.core.CardInfo
import com.example.tangemunichainhelper.core.Chain
import com.example.tangemunichainhelper.core.ChainRegistry
import com.example.tangemunichainhelper.core.Token
import com.example.tangemunichainhelper.core.TokenContractRegistry
import com.example.tangemunichainhelper.core.TokenRegistry
import java.math.BigDecimal
import java.math.BigInteger

/**
 * UI state for the main screen.
 */
data class UiState(
    val isLoading: Boolean = false,
    val isLoadingBalances: Boolean = false,
    val cardInfo: CardInfo? = null,
    /** Currently selected blockchain network. */
    val selectedChain: Chain = ChainRegistry.default,
    /** Map of token symbol to balance. Use getBalance(token) helper. */
    val tokenBalances: Map<String, BigDecimal> = emptyMap(),
    val transferParams: TransferParams? = null,
    /** Detailed result of the last successful transaction. */
    val lastTransactionResult: TransactionResult? = null,
    val error: String? = null
) {
    /** Get balance for a specific token. Returns ZERO if not loaded. */
    fun getBalance(token: Token): BigDecimal = tokenBalances[token.symbol] ?: BigDecimal.ZERO

    /** Shortcut for native currency balance (ETH on most chains). */
    val ethBalance: BigDecimal get() = getBalance(TokenRegistry.Native)

    /** Shortcut for USDC balance (for backward compatibility). */
    val usdcBalance: BigDecimal get() = getBalance(TokenRegistry.USDC)

    /** For backward compatibility - returns just the hash. */
    val lastTransactionHash: String? get() = lastTransactionResult?.txHash

    /** Native currency symbol for the selected chain (e.g., "ETH"). */
    val nativeCurrencySymbol: String get() = selectedChain.nativeCurrencySymbol

    /** Tokens available on the selected chain. */
    val availableTokens: List<Token> get() = TokenContractRegistry.getTokensForChain(selectedChain)
}

/**
 * Parameters for a pending transfer.
 */
data class TransferParams(
    val recipientAddress: String,
    val amount: BigDecimal,
    /** The token being transferred. */
    val token: Token,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val nonce: BigInteger
) {
    /** For backward compatibility. */
    val isUsdc: Boolean get() = token.symbol == "USDC"
}

/**
 * Information about maximum transferable amount.
 */
data class MaxTransferInfo(
    val maxAmount: BigDecimal,
    val gasCostEth: BigDecimal,
    val hasEnoughGas: Boolean,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger
)

/**
 * Categorized error types for user-friendly display.
 */
enum class ErrorType {
    CARD,           // NFC/Tangem card related errors
    NETWORK,        // RPC/connection errors
    VALIDATION,     // Input validation errors
    TRANSACTION,    // Transaction execution errors
    BALANCE,        // Insufficient balance errors
    UNKNOWN         // Other errors
}

/**
 * Holds detailed error information for user-friendly display.
 */
data class ErrorInfo(
    val type: ErrorType,
    val title: String,
    val message: String,
    val suggestion: String? = null,
    val isRetryable: Boolean = false,
    val technicalDetails: String? = null
) {
    companion object {
        fun fromMessage(message: String): ErrorInfo {
            return when {
                // Card-related errors
                message.contains("scan card", ignoreCase = true) ||
                message.contains("tangem", ignoreCase = true) ||
                message.contains("NFC", ignoreCase = true) ||
                message.contains("card", ignoreCase = true) -> {
                    ErrorInfo(
                        type = ErrorType.CARD,
                        title = "Card Error",
                        message = message.removePrefix("Failed to scan card: "),
                        suggestion = "Make sure NFC is enabled and hold your card steady against the phone",
                        isRetryable = true
                    )
                }

                // Network errors
                message.contains("nonce", ignoreCase = true) ||
                message.contains("gas price", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ||
                message.contains("connection", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("RPC", ignoreCase = true) -> {
                    ErrorInfo(
                        type = ErrorType.NETWORK,
                        title = "Network Error",
                        message = "Unable to connect to Unichain network",
                        suggestion = "Check your internet connection and try again",
                        isRetryable = true,
                        technicalDetails = message
                    )
                }

                // Balance errors
                message.contains("insufficient", ignoreCase = true) ||
                message.contains("balance", ignoreCase = true) -> {
                    val isGasError = message.contains("gas", ignoreCase = true)
                    ErrorInfo(
                        type = ErrorType.BALANCE,
                        title = if (isGasError) "Insufficient Gas" else "Insufficient Balance",
                        message = message,
                        suggestion = if (isGasError)
                            "You need ETH to pay for transaction fees. Add more ETH to your wallet."
                        else
                            "Reduce the amount or add more funds to your wallet.",
                        isRetryable = false
                    )
                }

                // Validation errors
                message.contains("address", ignoreCase = true) ||
                message.contains("amount", ignoreCase = true) ||
                message.contains("checksum", ignoreCase = true) ||
                message.contains("invalid", ignoreCase = true) ||
                message.contains("empty", ignoreCase = true) -> {
                    ErrorInfo(
                        type = ErrorType.VALIDATION,
                        title = "Invalid Input",
                        message = message,
                        suggestion = "Please check your input and try again",
                        isRetryable = false
                    )
                }

                // Transaction errors
                message.contains("transaction", ignoreCase = true) ||
                message.contains("transfer", ignoreCase = true) ||
                message.contains("send", ignoreCase = true) ||
                message.contains("sign", ignoreCase = true) ||
                message.contains("broadcast", ignoreCase = true) -> {
                    ErrorInfo(
                        type = ErrorType.TRANSACTION,
                        title = "Transaction Failed",
                        message = message.removePrefix("Failed to send transaction: ")
                            .removePrefix("Failed to execute transfer: "),
                        suggestion = "Please try again. If the problem persists, check the network status.",
                        isRetryable = true,
                        technicalDetails = message
                    )
                }

                // Default
                else -> ErrorInfo(
                    type = ErrorType.UNKNOWN,
                    title = "Error",
                    message = message,
                    suggestion = null,
                    isRetryable = true
                )
            }
        }
    }
}

/**
 * Holds detailed information about a successful transaction.
 * Displayed to user after transfer completes.
 */
data class TransactionResult(
    val txHash: String,
    val amount: BigDecimal,
    val tokenSymbol: String,
    val recipientAddress: String,
    val fromAddress: String,
    val gasFee: BigDecimal,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val nonce: BigInteger,
    val networkName: String,
    val explorerUrl: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Shortened transaction hash for display (first 10 + last 8 chars) */
    val shortTxHash: String
        get() = if (txHash.length > 20) {
            "${txHash.take(10)}...${txHash.takeLast(8)}"
        } else txHash

    /** Shortened recipient address for display */
    val shortRecipientAddress: String
        get() = if (recipientAddress.length > 16) {
            "${recipientAddress.take(8)}...${recipientAddress.takeLast(6)}"
        } else recipientAddress
}
