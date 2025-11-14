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
            Timber.d("Estimated gas for ETH transfer: $estimatedGas")
            Result.success(estimatedGas)
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
            Timber.d("Estimated gas for USDC transfer: $estimatedGas")
            Result.success(estimatedGas)
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
        try {
            val hexValue = Numeric.toHexString(signedTransaction)

            Timber.d("=== SENDING TRANSACTION ===")
            Timber.d("To network: ${NetworkConstants.RPC_URL}")
            Timber.d("Expected chain ID: ${NetworkConstants.CHAIN_ID}")
            Timber.d("Raw transaction hex: $hexValue")

            val response: EthSendTransaction = web3j.ethSendRawTransaction(hexValue).send()

            if (response.hasError()) {
                Timber.e("Network rejected transaction!")
                Timber.e("Error code: ${response.error.code}")
                Timber.e("Error message: ${response.error.message}")
                Timber.e("Error data: ${response.error.data}")
                throw Exception("Transaction error: ${response.error.message}")
            }

            val txHash = response.transactionHash
            Timber.d("✓ Transaction sent successfully!")
            Timber.d("Transaction hash: $txHash")
            Result.success(txHash)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send signed transaction")
            Result.failure(e)
        }
    }
}