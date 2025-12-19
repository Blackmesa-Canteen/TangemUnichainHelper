package com.example.tangemunichainhelper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tangemunichainhelper.core.AddressUtils
import com.example.tangemunichainhelper.core.CardInfo
import com.example.tangemunichainhelper.core.Chain
import com.example.tangemunichainhelper.core.ChainRegistry
import com.example.tangemunichainhelper.core.TangemManager
import com.example.tangemunichainhelper.core.Token
import com.example.tangemunichainhelper.core.TransactionSigner
import com.example.tangemunichainhelper.core.TokenContractRegistry
import com.example.tangemunichainhelper.core.TokenRegistry
import com.example.tangemunichainhelper.core.Web3Manager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class MainViewModel : ViewModel() {

    private val web3Manager = Web3Manager()
    private var tangemManager: TangemManager? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Store the scanned card info
    private var currentCardInfo: CardInfo? = null

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
                        currentCardInfo = cardInfo
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
            val cardInfo = currentCardInfo
            if (cardInfo == null) {
                _uiState.update { it.copy(error = "Please scan card first") }
                return@launch
            }

            _uiState.update { it.copy(isLoadingBalances = true) }

            // Use the address from scanned card
            val address = cardInfo.walletAddress

            // Load balances for all registered tokens
            val balanceResults = web3Manager.getAllTokenBalances(address)

            // Convert to Map<String, BigDecimal>, defaulting failed ones to ZERO
            val balances = balanceResults.mapValues { (_, result) ->
                result.getOrDefault(BigDecimal.ZERO)
            }

            // Find any errors
            val errors = balanceResults.filter { it.value.isFailure }
            val errorMessage = if (errors.isNotEmpty()) {
                "Failed to load: ${errors.keys.joinToString(", ")}"
            } else null

            _uiState.update {
                it.copy(
                    isLoadingBalances = false,
                    tokenBalances = balances,
                    error = errorMessage
                )
            }
        }
    }

    /**
     * Calculate the maximum amount that can be transferred.
     * For native ETH: balance - estimated gas cost
     * For ERC-20: full token balance (but validates ETH for gas)
     *
     * @param token The token to calculate max for
     */
    suspend fun calculateMaxTransferAmount(token: Token): Result<MaxTransferInfo> {
        val state = _uiState.value
        val cardInfo = state.cardInfo ?: return Result.failure(Exception("Card not scanned"))

        return try {
            // Get current gas price
            val gasPriceResult = web3Manager.getGasPrice()
            val gasPrice = gasPriceResult.getOrElse {
                return Result.failure(Exception("Failed to get gas price"))
            }

            // Use default gas limit for the token type
            val gasLimit = token.defaultGasLimit

            // Calculate gas cost in ETH
            val gasCostWei = gasPrice.multiply(gasLimit)
            val gasCostEth = BigDecimal(gasCostWei)
                .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.UP)

            val ethBalance = state.ethBalance
            val tokenBalance = state.getBalance(token)

            when (token) {
                is Token.Native -> {
                    // For native currency (ETH): return balance minus gas cost
                    val maxEth = (ethBalance - gasCostEth).coerceAtLeast(BigDecimal.ZERO)
                    Result.success(
                        MaxTransferInfo(
                            maxAmount = maxEth,
                            gasCostEth = gasCostEth,
                            hasEnoughGas = ethBalance >= gasCostEth,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    )
                }
                is Token.ERC20 -> {
                    // For ERC-20: return full token balance, but check if ETH covers gas
                    val hasEnoughGas = ethBalance >= gasCostEth
                    Result.success(
                        MaxTransferInfo(
                            maxAmount = tokenBalance,
                            gasCostEth = gasCostEth,
                            hasEnoughGas = hasEnoughGas,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate max transfer amount for ${token.symbol}")
            Result.failure(e)
        }
    }

    // Backward compatibility wrapper
    suspend fun calculateMaxTransferAmount(isUsdc: Boolean): Result<MaxTransferInfo> {
        val token = if (isUsdc) TokenRegistry.USDC else TokenRegistry.Native
        return calculateMaxTransferAmount(token)
    }

    /**
     * Prepare a transfer for any token.
     *
     * @param recipientAddress The recipient's Ethereum address
     * @param amount The amount to transfer (in human-readable units)
     * @param token The token to transfer
     */
    fun prepareTransfer(
        recipientAddress: String,
        amount: String,
        token: Token
    ) {
        viewModelScope.launch {
            val cardInfo = currentCardInfo
            if (cardInfo == null) {
                _uiState.update { it.copy(error = "Please scan card first") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val fromAddress = cardInfo.walletAddress

                // Validate recipient address with EIP-55 checksum support
                val addressValidation = AddressUtils.validateAddress(recipientAddress)
                if (!addressValidation.isValid) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = addressValidation.error ?: "Invalid recipient address"
                        )
                    }
                    return@launch
                }

                // Prevent sending to self
                if (recipientAddress.equals(fromAddress, ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Cannot send to your own address"
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
                val nonceResult = web3Manager.getNonce(fromAddress)
                val nonce = nonceResult.getOrElse { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get nonce: ${exception.message}"
                        )
                    }
                    return@launch
                }

                val gasPriceResult = web3Manager.getGasPrice()
                val gasPrice = gasPriceResult.getOrElse { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get gas price: ${exception.message}"
                        )
                    }
                    return@launch
                }

                // Estimate gas using generic method
                val amountInSmallestUnit = token.toSmallestUnit(amountDecimal)
                val gasLimitResult = web3Manager.estimateGasForTransfer(
                    fromAddress,
                    recipientAddress,
                    token,
                    amountInSmallestUnit
                )

                val gasLimit = gasLimitResult.getOrElse { token.defaultGasLimit }

                // Calculate gas cost in ETH
                val gasCostWei = gasPrice.multiply(gasLimit)
                val gasCostEth = BigDecimal(gasCostWei)
                    .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.UP)

                // Validate sufficient balance
                val ethBalance = _uiState.value.ethBalance
                val chain = web3Manager.currentChain
                when (token) {
                    is Token.Native -> {
                        // For native currency: need amount + gas
                        val totalNeeded = amountDecimal + gasCostEth
                        if (ethBalance < totalNeeded) {
                            val maxSendable = (ethBalance - gasCostEth).coerceAtLeast(BigDecimal.ZERO)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Insufficient ${chain.nativeCurrencySymbol}. You need ${totalNeeded.setScale(8, RoundingMode.UP).stripTrailingZeros().toPlainString()} ${chain.nativeCurrencySymbol} (amount + gas), but have ${ethBalance.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} ${chain.nativeCurrencySymbol}. Max sendable: ${maxSendable.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} ${chain.nativeCurrencySymbol}"
                                )
                            }
                            return@launch
                        }
                    }
                    is Token.ERC20 -> {
                        // For ERC-20: need native currency for gas, and enough token balance
                        if (ethBalance < gasCostEth) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Insufficient ${chain.nativeCurrencySymbol} for gas. You need at least ${gasCostEth.setScale(8, RoundingMode.UP).stripTrailingZeros().toPlainString()} ${chain.nativeCurrencySymbol} for gas fees."
                                )
                            }
                            return@launch
                        }
                        val tokenBalance = _uiState.value.getBalance(token)
                        if (tokenBalance < amountDecimal) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Insufficient ${token.symbol} balance. You have ${tokenBalance.stripTrailingZeros().toPlainString()} ${token.symbol}."
                                )
                            }
                            return@launch
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transferParams = TransferParams(
                            recipientAddress = recipientAddress,
                            amount = amountDecimal,
                            token = token,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit,
                            nonce = nonce
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare ${token.symbol} transfer")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to prepare transfer: ${e.message}"
                    )
                }
            }
        }
    }

    // Backward compatibility wrapper
    fun prepareTransfer(recipientAddress: String, amount: String, isUsdc: Boolean) {
        val token = if (isUsdc) TokenRegistry.USDC else TokenRegistry.Native
        prepareTransfer(recipientAddress, amount, token)
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

            Timber.d("=== TRANSACTION DETAILS ===")
            Timber.d("From address (cardInfo): ${cardInfo.walletAddress}")
            Timber.d("Master public key: ${cardInfo.masterPublicKey}")
            Timber.d("Derived public key: ${cardInfo.derivedPublicKey}")

            val balanceCheck = web3Manager.getEthBalance(cardInfo.walletAddress)
            Timber.d("Balance at ${cardInfo.walletAddress}: ${balanceCheck.getOrNull()} ETH")

            try {
                // Create transaction using generic method
                val rawTransactionResult = web3Manager.createTransferTransaction(
                    to = transferParams.recipientAddress,
                    token = transferParams.token,
                    amount = transferParams.amount,
                    gasPrice = transferParams.gasPrice,
                    gasLimit = transferParams.gasLimit,
                    nonce = transferParams.nonce
                )

                val rawTransaction = rawTransactionResult.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to create transaction: ${error.message}"
                        )
                    }
                    return@launch
                }

                // Step 2: Get hash for Tangem signing (uses Ethereum chain ID 1)
                val transactionHash = web3Manager.getTransactionHashForTangemSigning(rawTransaction)

                Timber.d("=== ABOUT TO SIGN ===")
                Timber.d("Raw transaction nonce: ${rawTransaction.nonce}")
                Timber.d("Raw transaction gasPrice: ${rawTransaction.gasPrice}")
                Timber.d("Raw transaction gasLimit: ${rawTransaction.gasLimit}")
                Timber.d("Raw transaction to: ${rawTransaction.to}")
                Timber.d("Raw transaction value: ${rawTransaction.value}")
                Timber.d("Raw transaction data: ${rawTransaction.data}")
                Timber.d("Transaction hash to sign: ${Numeric.toHexString(transactionHash)}")
                Timber.d("Transaction hash length: ${transactionHash.size} bytes")

                // Step 3: Sign with Tangem (card thinks it's signing Ethereum)
                val signatureResult = tangemManager?.signTransactionHash(
                    cardId = cardInfo.cardId,
                    walletPublicKey = Numeric.hexStringToByteArray(cardInfo.masterPublicKey),
                    transactionHash = transactionHash,
                    derivationPath = cardInfo.derivationPath  // With derivation path
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

                // Step 4: Find correct recovery ID
                val recoveryId = TransactionSigner.findRecoveryId(
                    txHash = transactionHash,
                    signature = signature,
                    expectedPublicKey = Numeric.hexStringToByteArray(cardInfo.derivedPublicKey)
                )

                // Step 5: Encode signed transaction with EIP-155 chain ID
                val signedTransaction = TransactionSigner.encodeSignedTransaction(
                    rawTransaction = rawTransaction,
                    signature = signature,
                    recoveryId = recoveryId,
                    chainId = web3Manager.currentChain.chainId
                )

                // Step 6: Send to Unichain network
                val txHashResult = web3Manager.sendSignedTransaction(signedTransaction)

                txHashResult.fold(
                    onSuccess = { txHash ->
                        val chain = web3Manager.currentChain
                        Timber.d("✓ Transaction successful!")
                        Timber.d("Transaction hash: $txHash")
                        Timber.d("View on explorer: ${chain.txExplorerUrl(txHash)}")

                        // Calculate gas fee in native currency
                        val gasFeeWei = transferParams.gasPrice.multiply(transferParams.gasLimit)
                        val gasFeeEth = BigDecimal(gasFeeWei)
                            .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.DOWN)

                        val transactionResult = TransactionResult(
                            txHash = txHash,
                            amount = transferParams.amount,
                            tokenSymbol = transferParams.token.symbol,
                            recipientAddress = transferParams.recipientAddress,
                            fromAddress = cardInfo.walletAddress,
                            gasFee = gasFeeEth,
                            gasPrice = transferParams.gasPrice,
                            gasLimit = transferParams.gasLimit,
                            nonce = transferParams.nonce,
                            networkName = chain.name,
                            explorerUrl = chain.txExplorerUrl(txHash)
                        )

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastTransactionResult = transactionResult,
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearTransactionResult() {
        _uiState.update { it.copy(lastTransactionResult = null) }
    }

    fun cancelTransfer() {
        _uiState.update { it.copy(transferParams = null) }
    }

    /**
     * Switch to a different blockchain network.
     *
     * This will:
     * 1. Update the Web3Manager to use the new chain's RPC
     * 2. Clear cached balances
     * 3. Reload balances for the new chain
     *
     * @param chain The blockchain network to switch to
     */
    fun selectChain(chain: Chain) {
        if (chain.chainId == _uiState.value.selectedChain.chainId) {
            return  // Already on this chain
        }

        Timber.d("Switching chain from ${_uiState.value.selectedChain.name} to ${chain.name}")

        // Switch the Web3Manager to use the new chain
        web3Manager.switchChain(chain)

        // Update UI state: set new chain and clear balances
        _uiState.update {
            it.copy(
                selectedChain = chain,
                tokenBalances = emptyMap(),
                transferParams = null  // Clear any pending transfer
            )
        }

        // Reload balances for the new chain if card is scanned
        if (currentCardInfo != null) {
            loadBalances()
        }
    }
}
