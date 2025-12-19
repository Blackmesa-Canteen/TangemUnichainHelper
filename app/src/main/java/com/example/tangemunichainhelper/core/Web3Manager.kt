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
 * Manages all Web3/blockchain interactions.
 *
 * This class provides methods for:
 * - Querying balances (ETH and any ERC-20 token)
 * - Creating transactions
 * - Estimating gas
 * - Broadcasting signed transactions
 */
class Web3Manager {
    private val web3j: Web3j = Web3j.build(HttpService(NetworkConstants.RPC_URL))

    /**
     * Get ETH balance for an address
     */
    suspend fun getEthBalance(address: String): Result<BigDecimal> = withContext(Dispatchers.IO) {
        try {
            val ethBalance = web3j.ethGetBalance(
                address,
                DefaultBlockParameterName.LATEST
            ).send()

            val balanceInWei = ethBalance.balance
            val balanceInEth = Convert.fromWei(balanceInWei.toBigDecimal(), Convert.Unit.ETHER)

            Timber.d("ETH Balance: $balanceInEth ETH")
            Result.success(balanceInEth)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get ETH balance")
            Result.failure(e)
        }
    }

    /**
     * Get USDC balance for an address
     */
    suspend fun getUsdcBalance(address: String): Result<BigDecimal> = withContext(Dispatchers.IO) {
        try {
            // Create balanceOf function call
            val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // Call the contract
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    address,
                    NetworkConstants.USDC_CONTRACT_ADDRESS,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError()) {
                throw Exception("Error calling USDC contract: ${response.error.message}")
            }

            // Decode the response
            val returnValues = FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )

            if (returnValues.isEmpty()) {
                throw Exception("Empty response from USDC contract")
            }

            val balanceInSmallestUnit = (returnValues[0] as Uint256).value
            // USDC has 6 decimals
            val balanceInUsdc = BigDecimal(balanceInSmallestUnit)
                .divide(BigDecimal.TEN.pow(6), 6, RoundingMode.DOWN)

            Timber.d("USDC Balance: $balanceInUsdc USDC")
            Result.success(balanceInUsdc)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get USDC balance")
            Result.failure(e)
        }
    }

    /**
     * Get current gas price
     */
    suspend fun getGasPrice(): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            Timber.d("Current gas price: $gasPrice wei")
            Result.success(gasPrice)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get gas price")
            Result.failure(e)
        }
    }

    /**
     * Get nonce for an address
     */
    suspend fun getNonce(address: String): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val nonce = web3j.ethGetTransactionCount(
                address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            Timber.d("Nonce for $address: $nonce")
            Result.success(nonce)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get nonce")
            Result.failure(e)
        }
    }

    /**
     * Estimate gas for ETH transfer
     */
    suspend fun estimateGasForEthTransfer(
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
            // Add 20% buffer to prevent out-of-gas failures
            val bufferedGas = GasUtils.addGasBuffer(estimatedGas)
            Timber.d("Estimated gas for ETH transfer: $estimatedGas (with buffer: $bufferedGas)")
            Result.success(bufferedGas)
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate gas for ETH transfer")
            // Return default if estimation fails
            Result.success(NetworkConstants.DEFAULT_GAS_LIMIT_ETH)
        }
    }

    /**
     * Estimate gas for USDC transfer
     */
    suspend fun estimateGasForUsdcTransfer(
        from: String,
        to: String,
        amount: BigInteger
    ): Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
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
                NetworkConstants.USDC_CONTRACT_ADDRESS,
                encodedFunction
            )

            val gasEstimate = web3j.ethEstimateGas(transaction).send()

            if (gasEstimate.hasError()) {
                throw Exception("Gas estimation error: ${gasEstimate.error.message}")
            }

            val estimatedGas = gasEstimate.amountUsed
            // Add 20% buffer to prevent out-of-gas failures
            val bufferedGas = GasUtils.addGasBuffer(estimatedGas)
            Timber.d("Estimated gas for USDC transfer: $estimatedGas (with buffer: $bufferedGas)")
            Result.success(bufferedGas)
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate gas for USDC transfer")
            // Return default if estimation fails
            Result.success(NetworkConstants.DEFAULT_GAS_LIMIT_ERC20)
        }
    }

    /**
     * Create unsigned ETH transfer transaction
     */
    suspend fun createEthTransferTransaction(
        to: String,
        amountInEth: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> = withContext(Dispatchers.IO) {
        try {
            val amountInWei = Convert.toWei(amountInEth, Convert.Unit.ETHER).toBigInteger()

            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                to,
                amountInWei
            )

            Timber.d("Created ETH transfer transaction: $amountInEth ETH to $to")
            Result.success(rawTransaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ETH transfer transaction")
            Result.failure(e)
        }
    }

    /**
     * Create unsigned USDC transfer transaction
     */
    suspend fun createUsdcTransferTransaction(
        to: String,
        amountInUsdc: BigDecimal,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger
    ): Result<RawTransaction> = withContext(Dispatchers.IO) {
        try {
            // Convert USDC amount to smallest unit (6 decimals)
            val amountInSmallestUnit = amountInUsdc
                .multiply(BigDecimal.TEN.pow(6))
                .toBigInteger()

            // Create transfer function
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
                NetworkConstants.USDC_CONTRACT_ADDRESS,
                BigInteger.ZERO,
                encodedFunction
            )

            Timber.d("Created USDC transfer transaction: $amountInUsdc USDC to $to")
            Result.success(rawTransaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create USDC transfer transaction")
            Result.failure(e)
        }
    }

    /**
     * Get transaction hash for signing
     */
    fun getTransactionHash(rawTransaction: RawTransaction): ByteArray {
        return TransactionEncoder.encode(rawTransaction, NetworkConstants.CHAIN_ID)
    }

    /**
     * Get transaction hash for signing with Tangem
     * Uses LEGACY format (no chain ID) for maximum compatibility
     */
    fun getTransactionHashForTangemSigning(rawTransaction: RawTransaction): ByteArray {
        // Encode transaction WITHOUT chain ID (legacy format)
        val encoded = TransactionEncoder.encode(rawTransaction)

        // Hash it with Keccak256
        val hash = Hash.sha3(encoded)

        Timber.d("Legacy encoded tx length: ${encoded.size} bytes")
        Timber.d("Keccak256 hash: ${Numeric.toHexString(hash)}")

        return hash
    }

    suspend fun sendSignedTransaction(signedTransaction: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val hexValue = Numeric.toHexString(signedTransaction)
        var lastException: Exception? = null

        // Try each RPC endpoint
        for (rpcUrl in NetworkConstants.RPC_URLS) {
            try {
                Timber.d("=== SENDING TRANSACTION ===")
                Timber.d("To network: $rpcUrl")
                Timber.d("Expected chain ID: ${NetworkConstants.CHAIN_ID}")
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
        Timber.e("All RPC endpoints failed")
        Result.failure(lastException ?: Exception("All RPC endpoints failed"))
    }

    // =========================================================================
    // GENERIC TOKEN METHODS
    // These methods work with any Token from TokenRegistry
    // =========================================================================

    /**
     * Get balance for any token (ETH or ERC-20).
     *
     * @param address The wallet address to check
     * @param token The token to get balance for
     * @return Balance in human-readable units (e.g., "1.5" ETH, "100" USDC)
     */
    suspend fun getTokenBalance(address: String, token: Token): Result<BigDecimal> {
        return when (token) {
            is Token.ETH -> getEthBalance(address)
            is Token.ERC20 -> getErc20Balance(address, token)
        }
    }

    /**
     * Get ERC-20 token balance.
     */
    suspend fun getErc20Balance(address: String, token: Token.ERC20): Result<BigDecimal> = withContext(Dispatchers.IO) {
        try {
            val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    address,
                    token.contractAddress,
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

            Timber.d("${token.symbol} Balance: $balance")
            Result.success(balance)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get ${token.symbol} balance")
            Result.failure(e)
        }
    }

    /**
     * Get balances for all tokens in TokenRegistry.
     *
     * @param address The wallet address to check
     * @return Map of token symbol to balance
     */
    suspend fun getAllTokenBalances(address: String): Map<String, Result<BigDecimal>> {
        return TokenRegistry.allTokens.associate { token ->
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
            is Token.ETH -> estimateGasForEthTransfer(from, to, amount)
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
                token.contractAddress,
                encodedFunction
            )

            val gasEstimate = web3j.ethEstimateGas(transaction).send()

            if (gasEstimate.hasError()) {
                throw Exception("Gas estimation error: ${gasEstimate.error.message}")
            }

            val estimatedGas = gasEstimate.amountUsed
            val bufferedGas = GasUtils.addGasBuffer(estimatedGas)
            Timber.d("Estimated gas for ${token.symbol} transfer: $estimatedGas (with buffer: $bufferedGas)")
            Result.success(bufferedGas)
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate gas for ${token.symbol} transfer")
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
            is Token.ETH -> createEthTransferTransaction(to, amount, gasPrice, gasLimit, nonce)
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
                token.contractAddress,
                BigInteger.ZERO,
                encodedFunction
            )

            Timber.d("Created ${token.symbol} transfer transaction: $amount ${token.symbol} to $to")
            Result.success(rawTransaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ${token.symbol} transfer transaction")
            Result.failure(e)
        }
    }
}