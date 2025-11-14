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
import org.web3j.crypto.Sign
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

                // Step 4: Re-encode with Unichain chain ID (130)
                val recoveryId = findCorrectRecoveryId(
                    rawTransaction = rawTransaction,
                    signature = signature,
                    expectedPublicKey = Numeric.hexStringToByteArray(cardInfo.publicKey)
                )

                val signedTransaction = encodeSignedTransactionWithSignature(
                    rawTransaction = rawTransaction,
                    signature = signature,
                    recoveryId = recoveryId  // Pass the correct recovery ID
                )

                // Step 5: Send to Unichain network
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
        signature: ByteArray,
        recoveryId: Int
    ): ByteArray {
        Timber.d("Encoding signed transaction...")
        Timber.d("Signature length: ${signature.size}")
        Timber.d("Recovery ID: $recoveryId")

        require(signature.size == 64) { "Expected 64-byte signature, got ${signature.size}" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        Timber.d("r: ${Numeric.toHexString(r)}")
        Timber.d("s: ${Numeric.toHexString(s)}")

        // Calculate v for Unichain using EIP-155
        // v = CHAIN_ID * 2 + 35 + recoveryId
        val v = (NetworkConstants.CHAIN_ID * 2 + 35 + recoveryId).toByte()

        Timber.d("Calculated v for chain ID ${NetworkConstants.CHAIN_ID}: $v")

        // Create signature data
        val signatureData = Sign.SignatureData(v, r, s)

        // Encode the signed transaction
        val signedTx = TransactionEncoder.encode(rawTransaction, signatureData)

        Timber.d("Final signed transaction length: ${signedTx.size}")
        Timber.d("Final signed transaction: ${Numeric.toHexString(signedTx)}")

        return signedTx
    }

    private fun findCorrectRecoveryId(
        rawTransaction: RawTransaction,
        signature: ByteArray,
        expectedPublicKey: ByteArray
    ): Int {
        require(signature.size == 64) { "Signature must be 64 bytes for recovery" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        // Get the transaction hash we signed (legacy format, no chain ID)
        val txHash = web3Manager.getTransactionHashForTangemSigning(rawTransaction)

        Timber.d("Trying to find recovery ID...")
        Timber.d("Expected public key: ${Numeric.toHexString(expectedPublicKey)}")

        // Try both recovery IDs (0 and 1)
        for (recoveryId in 0..1) {
            try {
                // Use legacy v values (27 or 28) for recovery
                val v = (27 + recoveryId).toByte()
                val signatureData = Sign.SignatureData(v, r, s)

                Timber.d("Trying recovery ID $recoveryId (v=$v)...")

                // Recover the public key
                val recoveredKey = Sign.signedMessageHashToKey(txHash, signatureData)
                val recoveredPublicKey = Numeric.toBytesPadded(recoveredKey, 64)

                Timber.d("Recovered public key: ${Numeric.toHexString(recoveredPublicKey)}")

                // Handle different public key formats
                val expectedKeyToCompare = when {
                    // Uncompressed key with 0x04 prefix (65 bytes)
                    expectedPublicKey.size == 65 && expectedPublicKey[0] == 0x04.toByte() -> {
                        expectedPublicKey.copyOfRange(1, 65)
                    }
                    // Compressed key (33 bytes) - need to decompress
                    expectedPublicKey.size == 33 -> {
                        // For compressed keys, we need to decompress them
                        // This is complex, so let's try a different approach
                        Timber.w("Compressed key format not fully supported")
                        continue
                    }
                    // Already in correct format (64 bytes)
                    expectedPublicKey.size == 64 -> {
                        expectedPublicKey
                    }
                    else -> {
                        Timber.w("Unexpected public key size: ${expectedPublicKey.size}")
                        continue
                    }
                }

                if (recoveredPublicKey.contentEquals(expectedKeyToCompare)) {
                    Timber.d("✓ Found correct recovery ID: $recoveryId")
                    return recoveryId
                } else {
                    Timber.d("✗ Recovery ID $recoveryId doesn't match")
                }
            } catch (e: Exception) {
                Timber.w("Recovery ID $recoveryId failed: ${e.message}")
                continue
            }
        }

        // If we can't determine, default to 0
        Timber.w("Could not determine correct recovery ID, defaulting to 0")
        return 0
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
