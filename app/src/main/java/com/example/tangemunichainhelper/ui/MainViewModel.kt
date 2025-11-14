package com.example.tangemunichainhelper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tangemunichainhelper.core.CardInfo
import com.example.tangemunichainhelper.core.NetworkConstants
import com.example.tangemunichainhelper.core.TangemManager
import com.example.tangemunichainhelper.core.Web3Manager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

class MainViewModel : ViewModel() {

    private val web3Manager = Web3Manager()
    private var tangemManager: TangemManager? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun initTangemManager(tangemManager: TangemManager) {
        this.tangemManager = tangemManager
    }

    fun scanCard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val result = tangemManager?.scanCard()

                result?.fold(
                    onSuccess = { cardInfo ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                cardInfo = cardInfo,
                                error = null
                            )
                        }
                        // Automatically load balances after scanning card
                        loadBalances()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to scan card: ${error.message}"
                            )
                        }
                    }
                ) ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Tangem manager not initialized"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error scanning card")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadBalances() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBalances = true) }

            try {
                // Load ETH balance
                val ethResult = web3Manager.getEthBalance(NetworkConstants.WALLET_ADDRESS)
                val ethBalance = ethResult.getOrNull() ?: BigDecimal.ZERO

                // Load USDC balance
                val usdcResult = web3Manager.getUsdcBalance(NetworkConstants.WALLET_ADDRESS)
                val usdcBalance = usdcResult.getOrNull() ?: BigDecimal.ZERO

                _uiState.update {
                    it.copy(
                        isLoadingBalances = false,
                        ethBalance = ethBalance,
                        usdcBalance = usdcBalance
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load balances")
                _uiState.update {
                    it.copy(
                        isLoadingBalances = false,
                        error = "Failed to load balances: ${e.message}"
                    )
                }
            }
        }
    }

    fun prepareTransfer(
        recipientAddress: String,
        amount: String,
        isUsdc: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Validate inputs
                if (recipientAddress.isBlank() || !recipientAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Invalid recipient address"
                        )
                    }
                    return@launch
                }

                val amountDecimal = try {
                    BigDecimal(amount)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Invalid amount"
                        )
                    }
                    return@launch
                }

                if (amountDecimal <= BigDecimal.ZERO) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Amount must be greater than zero"
                        )
                    }
                    return@launch
                }

                // Get nonce and gas price
                val nonceResult = web3Manager.getNonce(NetworkConstants.WALLET_ADDRESS)
                val nonce = nonceResult.getOrElse {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get nonce: ${it.error}"
                        )
                    }
                    return@launch
                }

                val gasPriceResult = web3Manager.getGasPrice()
                val gasPrice = gasPriceResult.getOrElse {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get gas price: ${it.error}"
                        )
                    }
                    return@launch
                }

                // Estimate gas
                val gasLimitResult = if (isUsdc) {
                    val amountInSmallestUnit = amountDecimal.multiply(BigDecimal.TEN.pow(6)).toBigInteger()
                    web3Manager.estimateGasForUsdcTransfer(
                        NetworkConstants.WALLET_ADDRESS,
                        recipientAddress,
                        amountInSmallestUnit
                    )
                } else {
                    val amountInWei = amountDecimal.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
                    web3Manager.estimateGasForEthTransfer(
                        NetworkConstants.WALLET_ADDRESS,
                        recipientAddress,
                        amountInWei
                    )
                }

                val gasLimit = gasLimitResult.getOrElse {
                    if (isUsdc) NetworkConstants.DEFAULT_GAS_LIMIT_ERC20
                    else NetworkConstants.DEFAULT_GAS_LIMIT_ETH
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transferParams = TransferParams(
                            recipientAddress = recipientAddress,
                            amount = amountDecimal,
                            isUsdc = isUsdc,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit,
                            nonce = nonce
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare transfer")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to prepare transfer: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateGasParams(gasPrice: BigInteger, gasLimit: BigInteger) {
        _uiState.update { state ->
            state.transferParams?.let { params ->
                state.copy(
                    transferParams = params.copy(
                        gasPrice = gasPrice,
                        gasLimit = gasLimit
                    )
                )
            } ?: state
        }
    }

    fun executeTransfer() {
        viewModelScope.launch {
            val cardInfo = _uiState.value.cardInfo
            val transferParams = _uiState.value.transferParams

            if (cardInfo == null || transferParams == null) {
                _uiState.update {
                    it.copy(error = "Missing card info or transfer parameters")
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Create transaction
                val rawTransactionResult = if (transferParams.isUsdc) {
                    web3Manager.createUsdcTransferTransaction(
                        to = transferParams.recipientAddress,
                        amountInUsdc = transferParams.amount,
                        gasPrice = transferParams.gasPrice,
                        gasLimit = transferParams.gasLimit,
                        nonce = transferParams.nonce
                    )
                } else {
                    web3Manager.createEthTransferTransaction(
                        to = transferParams.recipientAddress,
                        amountInEth = transferParams.amount,
                        gasPrice = transferParams.gasPrice,
                        gasLimit = transferParams.gasLimit,
                        nonce = transferParams.nonce
                    )
                }

                val rawTransaction = rawTransactionResult.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to create transaction: ${error.message}"
                        )
                    }
                    return@launch
                }

                // Get transaction hash for signing
                val transactionHash = web3Manager.getTransactionHash(rawTransaction)

                // Sign with Tangem card
                val signatureResult = tangemManager?.signTransactionHash(
                    cardId = cardInfo.cardId,
                    walletPublicKey = Numeric.hexStringToByteArray(cardInfo.publicKey),
                    transactionHash = transactionHash
                )

                val signature = signatureResult?.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to sign transaction: ${error.message}"
                        )
                    }
                    return@launch
                } ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Tangem manager not initialized"
                        )
                    }
                    return@launch
                }

                // Encode signed transaction
                val signedTransaction = encodeSignedTransactionWithSignature(
                    rawTransaction = rawTransaction,
                    signature = signature
                )

                // Send transaction
                val txHashResult = web3Manager.sendSignedTransaction(signedTransaction)

                txHashResult.fold(
                    onSuccess = { txHash ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastTransactionHash = txHash,
                                transferParams = null,
                                error = null
                            )
                        }
                        // Reload balances after successful transfer
                        loadBalances()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to send transaction: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute transfer")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to execute transfer: ${e.message}"
                    )
                }
            }
        }
    }

    private fun encodeSignedTransactionWithSignature(
        rawTransaction: RawTransaction,
        signature: ByteArray
    ): ByteArray {
        // Extract signature components
        if (signature.size < 65) {
            throw IllegalArgumentException("Invalid signature length: ${signature.size}")
        }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)
        val v = signature[64]

        // For EIP-155, encode v with chain ID
        val vWithChainId = (NetworkConstants.CHAIN_ID * 2 + 35 + (v.toInt() and 1)).toByte()

        // Create signature data
        val signatureData = org.web3j.crypto.Sign.SignatureData(
            vWithChainId,
            r,
            s
        )

        // Encode the transaction with signature
        return TransactionEncoder.encode(rawTransaction, signatureData)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearTransactionHash() {
        _uiState.update { it.copy(lastTransactionHash = null) }
    }

    fun cancelTransfer() {
        _uiState.update { it.copy(transferParams = null) }
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val isLoadingBalances: Boolean = false,
    val cardInfo: CardInfo? = null,
    val ethBalance: BigDecimal = BigDecimal.ZERO,
    val usdcBalance: BigDecimal = BigDecimal.ZERO,
    val transferParams: TransferParams? = null,
    val lastTransactionHash: String? = null,
    val error: String? = null
)

data class TransferParams(
    val recipientAddress: String,
    val amount: BigDecimal,
    val isUsdc: Boolean,
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
    val nonce: BigInteger
)
