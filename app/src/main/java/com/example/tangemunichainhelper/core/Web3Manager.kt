package com.example.tangemunichainhelper.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Manages all Web3/blockchain interactions for a specific chain.
 *
 * ## Multi-Chain Design
 * - Each Web3Manager instance is bound to a specific [Chain]
 * - Call [switchChain] to change to a different network
 * - Contract addresses are resolved via [TokenContractRegistry]
 * - Uses [Chain.chainId] for EIP-155 transaction signing
 *
 * @param initialChain The blockchain network to connect to (default: [ChainRegistry.default])
 */
class Web3Manager(initialChain: Chain = ChainRegistry.default) {

    private var _chain: Chain = initialChain
    private var web3j: Web3j = createWeb3jInstance(_chain)

    /** Current chain this manager is connected to */
    val currentChain: Chain get() = _chain

    /**
     * Switch to a different blockchain network.
     * Creates a new Web3j connection to the new chain's RPC.
     *
     * @param newChain The chain to switch to
     */
    fun switchChain(newChain: Chain) {
        if (newChain.chainId != _chain.chainId) {
            Timber.d("Switching from ${_chain.shortName} to ${newChain.shortName}")
            _chain = newChain
            web3j = createWeb3jInstance(newChain)
        }
    }

    private fun createWeb3jInstance(chain: Chain): Web3j {
        return Web3j.build(HttpService(chain.primaryRpcUrl))
    }

    // =========================================================================
    // NATIVE CURRENCY (ETH) METHODS
    // =========================================================================

    /**
     * Get native currency balance for an address.
     */
    suspend fun getNativeBalance(address: String): Result<BigDecimal> = withContext(Dispatchers.IO) {
        try {
            val ethBalance = web3j.ethGetBalance(
                address,
                DefaultBlockParameterName.LATEST
            ).send()

            val balanceInWei = ethBalance.balance
            val balanceInEth = Convert.fromWei(balanceInWei.toBigDecimal(), Convert.Unit.ETHER)

            Timber.d("${_chain.nativeCurrencySymbol} Balance on ${_chain.shortName}: $balanceInEth")
            Result.success(balanceInEth)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get ${_chain.nativeCurrencySymbol} balance on ${_chain.shortName}")
            Result.failure(e)
        }
    }

    /**
     * Get ETH balance for an address (alias for [getNativeBalance]).
     * @deprecated Use [getNativeBalance] for multi-chain compatibility
     */
    @Deprecated("Use getNativeBalance for multi-chain compatibility", ReplaceWith("getNativeBalance(address)"))
    suspend fun getEthBalance(address: String): Result<BigDecimal> = getNativeBalance(address)

    // =========================================================================
    // GAS METHODS
    // =========================================================================

    /**
     * Get current gas price.
     */
    suspend fun getGasPrice(): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            Timber.d("Current gas price on ${_chain.shortName}: $gasPrice wei")
            Result.success(gasPrice)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get gas price on ${_chain.shortName}")
            Result.failure(e)
        }
    }

    /**
     * Get nonce for an address.
     */
    suspend fun getNonce(address: String): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val nonce = web3j.ethGetTransactionCount(
                address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            Timber.d("Nonce for $address on ${_chain.shortName}: $nonce")
            Result.success(nonce)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get nonce on ${_chain.shortName}")
            Result.failure(e)
        }
    }

    /**
     * Estimate gas for native currency transfer.
     */
    suspend fun estimateGasForNativeTransfer(
        from: String,
        to: String,
        amount: BigInteger
    ): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val transaction = Transaction.createEtherTransaction(
                from,
                null,
                null,
                null,
                to,
                amount
            )

            val gasEstimate = web3j.ethEstimateGas(transaction).send()

            if (gasEstimate.hasError()) {
                throw Exception("Gas estimation error: ${gasEstimate.error.message}")
            }

            val estimatedGas = gasEstimate.amountUsed
            val bufferedGas = GasUtils.addGasBuffer(estimatedGas)
            Timber.d("Estimated gas for ${_chain.nativeCurrencySymbol} transfer: $estimatedGas (with buffer: $bufferedGas)")
            Result.success(bufferedGas)
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate gas for ${_chain.nativeCurrencySymbol} transfer")
            Result.success(Token.Native.defaultGasLimit)
        }
    }

    /**
     * @deprecated Use [estimateGasForNativeTransfer] for multi-chain compatibility
     */
    @Deprecated("Use estimateGasForNativeTransfer", ReplaceWith("estimateGasForNativeTransfer(from, to, amount)"))
    suspend fun estimateGasForEthTransfer(
        from: String,
        to: String,
        amount: BigInteger
    ): Result<BigInteger> = estimateGasForNativeTransfer(from, to, amount)

    // =========================================================================
    // TRANSACTION CREATION
    // =========================================================================

    /**
     * Create unsigned native currency transfer transaction.
     */
    suspend fun createNativeTransferTransaction(
        to: String,
        amount: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> = withContext(Dispatchers.IO) {
        try {
            val amountInWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger()

            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                to,
                amountInWei
            )

            Timber.d("Created ${_chain.nativeCurrencySymbol} transfer: $amount to $to on ${_chain.shortName}")
            Result.success(rawTransaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ${_chain.nativeCurrencySymbol} transfer transaction")
            Result.failure(e)
        }
    }

    /**
     * @deprecated Use [createNativeTransferTransaction] for multi-chain compatibility
     */
    @Deprecated("Use createNativeTransferTransaction", ReplaceWith("createNativeTransferTransaction(to, amountInEth, gasPrice, gasLimit, nonce)"))
    suspend fun createEthTransferTransaction(
        to: String,
        amountInEth: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> = createNativeTransferTransaction(to, amountInEth, gasPrice, gasLimit, nonce)

    // =========================================================================
    // EIP-155 TRANSACTION SIGNING
    // =========================================================================

    /**
     * Get transaction hash for signing with Tangem.
     * Uses EIP-155 format (with chain ID) for replay protection.
     */
    fun getTransactionHashForTangemSigning(rawTransaction: RawTransaction): ByteArray {
        // Encode transaction WITH chain ID (EIP-155 format)
        val encoded = TransactionEncoder.encode(rawTransaction, _chain.chainId)

        // Hash it with Keccak256
        val hash = Hash.sha3(encoded)

        Timber.d("EIP-155 encoded tx length: ${encoded.size} bytes")
        Timber.d("Chain: ${_chain.name} (ID: ${_chain.chainId})")
        Timber.d("Keccak256 hash: ${Numeric.toHexString(hash)}")

        return hash
    }

    /**
     * Get transaction hash (alias for compatibility).
     * @deprecated Use [getTransactionHashForTangemSigning]
     */
    @Deprecated("Use getTransactionHashForTangemSigning", ReplaceWith("getTransactionHashForTangemSigning(rawTransaction)"))
    fun getTransactionHash(rawTransaction: RawTransaction): ByteArray {
        return TransactionEncoder.encode(rawTransaction, _chain.chainId)
    }

    /**
     * Send signed transaction with RPC fallback.
     */
    suspend fun sendSignedTransaction(signedTransaction: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val hexValue = Numeric.toHexString(signedTransaction)
        var lastException: Exception? = null

        // Try each RPC endpoint
        for (rpcUrl in _chain.rpcUrls) {
            try {
                Timber.d("=== SENDING TRANSACTION ===")
                Timber.d("To network: ${_chain.name} via $rpcUrl")
                Timber.d("Chain ID: ${_chain.chainId}")
                Timber.d("Raw transaction hex: $hexValue")

                val web3jInstance = Web3j.build(HttpService(rpcUrl))
                val response: EthSendTransaction = web3jInstance.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    Timber.e("Network rejected transaction on $rpcUrl!")
                    Timber.e("Error code: ${response.error.code}")
                    Timber.e("Error message: ${response.error.message}")
                    Timber.e("Error data: ${response.error.data}")
                    lastException = Exception("Transaction error: ${response.error.message}")
                    continue // Try next RPC
                }

                val txHash = response.transactionHash
                Timber.d("✓ Transaction sent successfully via $rpcUrl!")
                Timber.d("Transaction hash: $txHash")
                return@withContext Result.success(txHash)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send transaction via $rpcUrl")
                lastException = e
                // Continue to try next RPC
            }
        }

        // All RPCs failed
        Timber.e("All RPC endpoints failed for ${_chain.name}")
        Result.failure(lastException ?: Exception("All RPC endpoints failed"))
    }

    // =========================================================================
    // GENERIC TOKEN METHODS
    // These methods work with any Token from TokenRegistry
    // =========================================================================

    /**
     * Get balance for any token (native or ERC-20).
     *
     * @param address The wallet address to check
     * @param token The token to get balance for
     * @return Balance in human-readable units
     */
    suspend fun getTokenBalance(address: String, token: Token): Result<BigDecimal> {
        return when (token) {
            is Token.Native -> getNativeBalance(address)
            is Token.ERC20 -> getErc20Balance(address, token)
        }
    }

    /**
     * Get ERC-20 token balance.
     */
    suspend fun getErc20Balance(address: String, token: Token.ERC20): Result<BigDecimal> = withContext(Dispatchers.IO) {
        try {
            val contractAddress = TokenContractRegistry.getContractAddress(token, _chain)
                ?: return@withContext Result.failure(
                    Exception("${token.symbol} is not available on ${_chain.name}")
                )

            val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    address,
                    contractAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError()) {
                throw Exception("Error calling ${token.symbol} contract: ${response.error.message}")
            }

            val returnValues = FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )

            if (returnValues.isEmpty()) {
                throw Exception("Empty response from ${token.symbol} contract")
            }

            val balanceInSmallestUnit = (returnValues[0] as Uint256).value
            val balance = token.fromSmallestUnit(balanceInSmallestUnit)

            Timber.d("${token.symbol} Balance on ${_chain.shortName}: $balance")
            Result.success(balance)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get ${token.symbol} balance on ${_chain.shortName}")
            Result.failure(e)
        }
    }

    /**
     * Get balances for all tokens available on current chain.
     *
     * @param address The wallet address to check
     * @return Map of token symbol to balance
     */
    suspend fun getAllTokenBalances(address: String): Map<String, Result<BigDecimal>> {
        val availableTokens = TokenContractRegistry.getTokensForChain(_chain)
        return availableTokens.associate { token ->
            token.symbol to getTokenBalance(address, token)
        }
    }

    /**
     * Estimate gas for any token transfer.
     */
    suspend fun estimateGasForTransfer(
        from: String,
        to: String,
        token: Token,
        amount: BigInteger
    ): Result<BigInteger> {
        return when (token) {
            is Token.Native -> estimateGasForNativeTransfer(from, to, amount)
            is Token.ERC20 -> estimateGasForErc20Transfer(from, to, token, amount)
        }
    }

    /**
     * Estimate gas for ERC-20 transfer.
     */
    suspend fun estimateGasForErc20Transfer(
        from: String,
        to: String,
        token: Token.ERC20,
        amount: BigInteger
    ): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val contractAddress = TokenContractRegistry.getContractAddress(token, _chain)
                ?: return@withContext Result.failure(
                    Exception("${token.symbol} is not available on ${_chain.name}")
                )

            val function = Function(
                "transfer",
                listOf(Address(to), Uint256(amount)),
                emptyList()
            )

            val encodedFunction = FunctionEncoder.encode(function)

            val transaction = Transaction.createFunctionCallTransaction(
                from,
                null,
                null,
                null,
                contractAddress,
                encodedFunction
            )

            val gasEstimate = web3j.ethEstimateGas(transaction).send()

            if (gasEstimate.hasError()) {
                throw Exception("Gas estimation error: ${gasEstimate.error.message}")
            }

            val estimatedGas = gasEstimate.amountUsed
            val bufferedGas = GasUtils.addGasBuffer(estimatedGas)
            Timber.d("Estimated gas for ${token.symbol} transfer on ${_chain.shortName}: $estimatedGas (with buffer: $bufferedGas)")
            Result.success(bufferedGas)
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate gas for ${token.symbol} transfer on ${_chain.shortName}")
            Result.success(token.defaultGasLimit)
        }
    }

    /**
     * Create unsigned transaction for any token transfer.
     */
    suspend fun createTransferTransaction(
        to: String,
        token: Token,
        amount: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> {
        return when (token) {
            is Token.Native -> createNativeTransferTransaction(to, amount, gasPrice, gasLimit, nonce)
            is Token.ERC20 -> createErc20TransferTransaction(to, token, amount, gasPrice, gasLimit, nonce)
        }
    }

    /**
     * Create unsigned ERC-20 transfer transaction.
     */
    suspend fun createErc20TransferTransaction(
        to: String,
        token: Token.ERC20,
        amount: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> = withContext(Dispatchers.IO) {
        try {
            val contractAddress = TokenContractRegistry.getContractAddress(token, _chain)
                ?: return@withContext Result.failure(
                    Exception("${token.symbol} is not available on ${_chain.name}")
                )

            val amountInSmallestUnit = token.toSmallestUnit(amount)

            val function = Function(
                "transfer",
                listOf(
                    Address(to),
                    Uint256(amountInSmallestUnit)
                ),
                emptyList()
            )

            val encodedFunction = FunctionEncoder.encode(function)

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                encodedFunction
            )

            Timber.d("Created ${token.symbol} transfer: $amount to $to on ${_chain.shortName}")
            Result.success(rawTransaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ${token.symbol} transfer transaction on ${_chain.shortName}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // LEGACY METHODS (for backward compatibility)
    // =========================================================================

    /**
     * Get USDC balance for an address.
     * @deprecated Use [getErc20Balance] with [TokenRegistry.USDC] instead
     */
    @Deprecated("Use getErc20Balance with TokenRegistry.USDC", ReplaceWith("getErc20Balance(address, TokenRegistry.USDC)"))
    suspend fun getUsdcBalance(address: String): Result<BigDecimal> {
        return getErc20Balance(address, TokenRegistry.USDC)
    }

    /**
     * Estimate gas for USDC transfer.
     * @deprecated Use [estimateGasForErc20Transfer] with [TokenRegistry.USDC] instead
     */
    @Deprecated("Use estimateGasForErc20Transfer with TokenRegistry.USDC")
    suspend fun estimateGasForUsdcTransfer(
        from: String,
        to: String,
        amount: BigInteger
    ): Result<BigInteger> {
        return estimateGasForErc20Transfer(from, to, TokenRegistry.USDC, amount)
    }

    /**
     * Create unsigned USDC transfer transaction.
     * @deprecated Use [createErc20TransferTransaction] with [TokenRegistry.USDC] instead
     */
    @Deprecated("Use createErc20TransferTransaction with TokenRegistry.USDC")
    suspend fun createUsdcTransferTransaction(
        to: String,
        amountInUsdc: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> {
        return createErc20TransferTransaction(to, TokenRegistry.USDC, amountInUsdc, gasPrice, gasLimit, nonce)
    }
}
