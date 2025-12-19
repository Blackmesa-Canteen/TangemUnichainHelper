package com.example.tangemunichainhelper.core

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Represents a token that can be transferred.
 *
 * ## Adding a New Token
 *
 * To add support for a new ERC-20 token (e.g., USDT), simply add it to [TokenRegistry]:
 *
 * ```kotlin
 * val USDT = Token.ERC20(
 *     symbol = "USDT",
 *     name = "Tether USD",
 *     contractAddress = "0x...", // USDT contract on Unichain
 *     decimals = 6,
 *     defaultGasLimit = BigInteger.valueOf(65000)
 * )
 * ```
 *
 * Then add it to the `allTokens` list in [TokenRegistry].
 */
sealed class Token {
    abstract val symbol: String
    abstract val name: String
    abstract val decimals: Int
    abstract val defaultGasLimit: BigInteger

    /**
     * Native ETH token.
     */
    data object ETH : Token() {
        override val symbol = "ETH"
        override val name = "Ethereum"
        override val decimals = 18
        override val defaultGasLimit: BigInteger = BigInteger.valueOf(21000)
    }

    /**
     * ERC-20 token with contract address.
     */
    data class ERC20(
        override val symbol: String,
        override val name: String,
        val contractAddress: String,
        override val decimals: Int,
        override val defaultGasLimit: BigInteger = BigInteger.valueOf(65000)
    ) : Token()

    /**
     * Check if this is the native ETH token.
     */
    val isNative: Boolean get() = this is ETH

    /**
     * Check if this is an ERC-20 token.
     */
    val isERC20: Boolean get() = this is ERC20

    /**
     * Convert human-readable amount to smallest unit (wei for ETH, smallest unit for ERC-20).
     */
    fun toSmallestUnit(amount: BigDecimal): BigInteger {
        return amount.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()
    }

    /**
     * Convert smallest unit to human-readable amount.
     */
    fun fromSmallestUnit(amount: BigInteger): BigDecimal {
        return BigDecimal(amount).divide(BigDecimal.TEN.pow(decimals), decimals, java.math.RoundingMode.DOWN)
    }
}

/**
 * Registry of all supported tokens on Unichain.
 *
 * ## How to Add a New Token
 *
 * 1. Define the token as a val in this object
 * 2. Add it to the [allTokens] list
 * 3. That's it! The UI will automatically show the new token.
 *
 * ## Example: Adding USDT
 *
 * ```kotlin
 * val USDT = Token.ERC20(
 *     symbol = "USDT",
 *     name = "Tether USD",
 *     contractAddress = "0x...", // Find on Unichain explorer
 *     decimals = 6
 * )
 *
 * val allTokens: List<Token> = listOf(ETH, USDC, USDT)
 * ```
 */
object TokenRegistry {

    /**
     * Native ETH token.
     */
    val ETH = Token.ETH

    /**
     * USDC on Unichain.
     * Contract: https://uniscan.xyz/token/0x078D782b760474a361dDA0AF3839290b0EF57AD6
     */
    val USDC = Token.ERC20(
        symbol = "USDC",
        name = "USD Coin",
        contractAddress = "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
        decimals = 6
    )

    // =========================================================================
    // ADD NEW TOKENS HERE
    // =========================================================================
    //
    // Example: USDT (uncomment and update contract address when available)
    //
    // val USDT = Token.ERC20(
    //     symbol = "USDT",
    //     name = "Tether USD",
    //     contractAddress = "0x...", // TODO: Add Unichain USDT contract address
    //     decimals = 6
    // )
    //
    // Example: WETH
    //
    // val WETH = Token.ERC20(
    //     symbol = "WETH",
    //     name = "Wrapped Ether",
    //     contractAddress = "0x...", // TODO: Add Unichain WETH contract address
    //     decimals = 18
    // )
    //
    // =========================================================================

    /**
     * List of all supported tokens.
     * Add new tokens here after defining them above.
     */
    val allTokens: List<Token> = listOf(
        ETH,
        USDC,
        // Add new tokens here:
        // USDT,
        // WETH,
    )

    /**
     * Get all ERC-20 tokens (excludes native ETH).
     */
    val erc20Tokens: List<Token.ERC20>
        get() = allTokens.filterIsInstance<Token.ERC20>()

    /**
     * Find token by symbol.
     */
    fun findBySymbol(symbol: String): Token? {
        return allTokens.find { it.symbol.equals(symbol, ignoreCase = true) }
    }

    /**
     * Find ERC-20 token by contract address.
     */
    fun findByContractAddress(address: String): Token.ERC20? {
        return erc20Tokens.find { it.contractAddress.equals(address, ignoreCase = true) }
    }
}
