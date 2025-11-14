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
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

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

            _uiState.update { it.copy(isLoading = true) }

            // Use the address from scanned card
            val address = cardInfo.walletAddress

            val ethBalanceResult = web3Manager.getEthBalance(address)
            val usdcBalanceResult = web3Manager.getUsdcBalance(address)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    ethBalance = ethBalanceResult.getOrDefault(BigDecimal.ZERO),
                    usdcBalance = usdcBalanceResult.getOrDefault(BigDecimal.ZERO),
                    error = when {
                        ethBalanceResult.isFailure -> "Failed to load ETH balance"
                        usdcBalanceResult.isFailure -> "Failed to load USDC balance"
                        else -> null
                    }
                )
            }
        }
    }

    fun prepareTransfer(
        recipientAddress: String,
        amount: String,
        isUsdc: Boolean
    ) {
        viewModelScope.launch {
            val cardInfo = currentCardInfo
            if (cardInfo == null) {
                _uiState.update { it.copy(error = "Please scan card first") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Use the address from scanned card
                val fromAddress = cardInfo.walletAddress

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
                val nonceResult = web3Manager.getNonce(fromAddress)
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
                        fromAddress,
                        recipientAddress,
                        amountInSmallestUnit
                    )
                } else {
                    val amountInWei = amountDecimal.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
                    web3Manager.estimateGasForEthTransfer(
                        fromAddress,
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
        Timber.d("Chain ID: ${NetworkConstants.CHAIN_ID}")

        require(signature.size == 64) { "Expected 64-byte signature, got ${signature.size}" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        Timber.d("r: ${Numeric.toHexString(r)}")
        Timber.d("s: ${Numeric.toHexString(s)}")

        // Calculate v for EIP-155: v = CHAIN_ID * 2 + 35 + recoveryId
        // DON'T cast to byte - use BigInteger for proper encoding
        val vValue = NetworkConstants.CHAIN_ID * 2 + 35 + recoveryId
        val vBigInt = BigInteger.valueOf(vValue)

        Timber.d("Calculated v (EIP-155): $vValue (0x${vValue.toString(16)})")

        // Convert signature components to BigInteger
        val rBigInt = BigInteger(1, r)
        val sBigInt = BigInteger(1, s)

        // Create the signed transaction manually using RLP encoding
        val signedValues = listOf(
            RlpString.create(rawTransaction.nonce),
            RlpString.create(rawTransaction.gasPrice),
            RlpString.create(rawTransaction.gasLimit),
            RlpString.create(Numeric.hexStringToByteArray(rawTransaction.to)),
            RlpString.create(rawTransaction.value),
            RlpString.create(rawTransaction.data),
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

    private fun findCorrectRecoveryId(
        rawTransaction: RawTransaction,
        signature: ByteArray,
        expectedPublicKey: ByteArray
    ): Int {
        require(signature.size == 64) { "Signature must be 64 bytes for recovery" }

        val r = signature.copyOfRange(0, 32)
        val s = signature.copyOfRange(32, 64)

        val txHash = web3Manager.getTransactionHashForTangemSigning(rawTransaction)

        Timber.d("Trying to find recovery ID...")
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

        // Try both recovery IDs
        for (recoveryId in 0..1) {
            try {
                val v = (27 + recoveryId).toByte()
                val signatureData = Sign.SignatureData(v, r, s)

                Timber.d("Trying recovery ID $recoveryId (v=$v)...")

                val recoveredKey = Sign.signedMessageHashToKey(txHash, signatureData)
                val recoveredPublicKey = Numeric.toBytesPadded(recoveredKey, 64)

                Timber.d("Recovered key: ${Numeric.toHexString(recoveredPublicKey)}")

                if (recoveredPublicKey.contentEquals(expectedKeyUncompressed)) {
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

        Timber.w("Could not determine correct recovery ID, defaulting to 0")
        return 0
    }

    private fun decompressPublicKey(compressedKey: ByteArray): ByteArray {
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
