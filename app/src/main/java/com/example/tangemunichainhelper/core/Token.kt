package com.example.tangemunichainhelper.core

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Represents a token that can be transferred on any EVM chain.
 *
 * ## Multi-Chain Design
 * Tokens are defined **chain-agnostic** - they don't include contract addresses.
 * Contract addresses vary per chain and are managed by [TokenContractRegistry].
 *
 * ## Adding a New Token
 * 1. Define the token in [TokenRegistry] (symbol, name, decimals)
 * 2. Add contract addresses for each chain in [TokenContractRegistry]
 *
 * ```kotlin
 * // In TokenRegistry:
 * val WETH = Token.ERC20(
 *     symbol = "WETH",
 *     name = "Wrapped Ether",
 *     decimals = 18
 * )
 *
 * // In TokenContractRegistry contracts map:
 * 130L to mapOf(
 *     "WETH" to "0x..." // Unichain WETH address
 * )
 * ```
 *
 * @see TokenRegistry for token definitions
 * @see TokenContractRegistry for chain-specific contract addresses
 */
sealed class Token {
    /** Token symbol (e.g., "ETH", "USDC") */
    abstract val symbol: String

    /** Human-readable name (e.g., "Ethereum", "USD Coin") */
    abstract val name: String

    /** Decimal places for amount formatting */
    abstract val decimals: Int

    /** Default gas limit for transfers */
    abstract val defaultGasLimit: BigInteger

    /**
     * Native currency token (ETH, MATIC, etc.).
     *
     * The actual symbol displayed in UI should come from [Chain.nativeCurrencySymbol]
     * since different chains have different native currencies.
     */
    data object Native : Token() {
        override val symbol: String = "ETH"  // Default, UI can override with chain's symbol
        override val name: String = "Native Currency"
        override val decimals: Int = 18
        override val defaultGasLimit: BigInteger = BigInteger.valueOf(21000)
    }

    /**
     * ERC-20 token definition (chain-agnostic).
     *
     * Contract addresses are looked up via [TokenContractRegistry.getContractAddress].
     * This allows the same token to be used across multiple chains.
     */
    data class ERC20(
        override val symbol: String,
        override val name: String,
        override val decimals: Int,
        override val defaultGasLimit: BigInteger = BigInteger.valueOf(65000)
    ) : Token()

    /**
     * Check if this is the native currency token.
     */
    val isNative: Boolean get() = this is Native

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

    companion object {
        /**
         * Backward compatibility alias for Token.Native.
         * @deprecated Use Token.Native instead
         */
        @Deprecated("Use Token.Native instead", ReplaceWith("Token.Native"))
        val ETH: Token get() = Native
    }
}

/**
 * Registry of all supported tokens (chain-agnostic definitions).
 *
 * ## Multi-Chain Design
 * Tokens are defined here without contract addresses.
 * Contract addresses are managed by [TokenContractRegistry] per chain.
 *
 * ## Adding a New Token
 * 1. Define the token here (symbol, name, decimals)
 * 2. Add it to [allTokens]
 * 3. Add contract addresses in [TokenContractRegistry]
 *
 * @see TokenContractRegistry for chain-specific contract addresses
 */
object TokenRegistry {

    /**
     * Native currency token (ETH on most chains, MATIC on Polygon, etc.).
     */
    val Native = Token.Native

    /**
     * Backward compatibility alias for Native.
     * @deprecated Use Native instead
     */
    @Deprecated("Use Native instead", ReplaceWith("Native"))
    val ETH = Token.Native

    /**
     * USDC - USD Coin (available on most chains).
     * Contract addresses are in [TokenContractRegistry].
     */
    val USDC = Token.ERC20(
        symbol = "USDC",
        name = "USD Coin",
        decimals = 6
    )

    /**
     * USDT - Tether USD (available on most chains).
     * On Unichain, this is USDT0 (omnichain Tether via LayerZero).
     * Contract addresses are in [TokenContractRegistry].
     */
    val USDT = Token.ERC20(
        symbol = "USDT",
        name = "Tether USD",
        decimals = 6
    )

    // =========================================================================
    // ADD NEW TOKENS HERE
    // =========================================================================
    //
    // Example: WETH (Wrapped Ether)
    //
    // val WETH = Token.ERC20(
    //     symbol = "WETH",
    //     name = "Wrapped Ether",
    //     decimals = 18
    // )
    //
    // Then add contract addresses in TokenContractRegistry for each chain.
    //
    // =========================================================================

    /**
     * List of all supported tokens.
     * Add new tokens here after defining them above.
     */
    val allTokens: List<Token> = listOf(
        Native,
        USDC,
        USDT
        // Add new tokens here:
        // WETH,
    )

    /**
     * Get all ERC-20 tokens (excludes native currency).
     */
    val erc20Tokens: List<Token.ERC20>
        get() = allTokens.filterIsInstance<Token.ERC20>()

    /**
     * Find token by symbol (case-insensitive).
     *
     * @param symbol The token symbol (e.g., "USDC")
     * @return The token if found, null otherwise
     */
    fun findBySymbol(symbol: String): Token? {
        return allTokens.find { it.symbol.equals(symbol, ignoreCase = true) }
    }

    /**
     * Find ERC-20 token by contract address on a specific chain.
     *
     * @param address The contract address
     * @param chain The blockchain network
     * @return The token if found, null otherwise
     */
    fun findByContractAddress(address: String, chain: Chain): Token.ERC20? {
        return TokenContractRegistry.findByContractAddress(address, chain)
    }

    /**
     * Get tokens available on a specific chain.
     *
     * @param chain The blockchain network
     * @return List of tokens with contract addresses on the chain
     */
    fun getTokensForChain(chain: Chain): List<Token> {
        return TokenContractRegistry.getTokensForChain(chain)
    }
}
