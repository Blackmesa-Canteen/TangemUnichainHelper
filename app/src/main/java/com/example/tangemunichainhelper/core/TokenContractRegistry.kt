package com.example.tangemunichainhelper.core

/**
 * Registry mapping tokens to their contract addresses on each chain.
 *
 * ## Why This Exists
 * The same token (e.g., USDC) has **different contract addresses** on different chains.
 * This registry provides the mapping from (Token, Chain) -> contract address.
 *
 * ## Design Notes
 * - Only Unichain tokens are included by default
 * - Sepolia is a testnet with no production tokens
 * - Native tokens (ETH) don't need contract addresses
 * - Developers must add their own chain's token contracts
 *
 * ## Adding Token Contracts for a New Chain
 * ```kotlin
 * // In the contracts map:
 * 137L to mapOf(  // Polygon
 *     "USDC" to "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
 *     "USDT" to "0xc2132D05D31c914a87C6611C10748AEb04B58e8F"
 * )
 * ```
 *
 * ## Security Note
 * ALWAYS verify contract addresses from official sources before adding them.
 * Incorrect addresses could result in lost funds.
 *
 * @see Token for token definitions
 * @see Chain for chain definitions
 */
object TokenContractRegistry {

    /**
     * Map of chain ID -> token symbol -> contract address.
     *
     * ## Verified Sources:
     * - Unichain USDC: https://docs.unichain.org/docs/building-on-unichain/transfer-usdc
     * - Unichain USDT (USDT0): https://zapper.xyz/token/unichain/0x9151434b16b9763660705744891fa906f660ecc5
     *
     * ## IMPORTANT:
     * - Only Unichain tokens are included
     * - Developers must add their own chain's token contracts
     * - Always verify addresses from official sources
     */
    private val contracts: Map<Long, Map<String, String>> = mapOf(
        // =====================================================================
        // Unichain Mainnet (Chain ID: 130)
        // Verified from official Unichain documentation
        // =====================================================================
        130L to mapOf(
            // USDC - USD Coin
            // Source: https://docs.unichain.org/docs/building-on-unichain/transfer-usdc
            "USDC" to "0x078D782b760474a361dDA0AF3839290b0EF57AD6",

            // USDT - Tether USD (USDT0 via LayerZero)
            // Source: https://zapper.xyz/token/unichain/0x9151434b16b9763660705744891fa906f660ecc5
            "USDT" to "0x9151434b16b9763660705744891fa906f660ecc5"
        )

        // =====================================================================
        // Sepolia Testnet (Chain ID: 11155111)
        // No production tokens - testnet only
        // Developers can add test token contracts here if needed
        // =====================================================================
        // 11155111L to mapOf(
        //     "USDC" to "0x..." // Test USDC if available
        // )

        // =====================================================================
        // ADD YOUR CHAIN'S TOKEN CONTRACTS HERE
        // =====================================================================
        // Example: Ethereum Mainnet (Chain ID: 1)
        // 1L to mapOf(
        //     "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        //     "USDT" to "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        // )
        //
        // Example: Polygon (Chain ID: 137)
        // 137L to mapOf(
        //     "USDC" to "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
        //     "USDT" to "0xc2132D05D31c914a87C6611C10748AEb04B58e8F"
        // )
    )

    /**
     * Get contract address for a token on a specific chain.
     *
     * @param token The ERC-20 token
     * @param chain The blockchain network
     * @return Contract address if available, null otherwise
     */
    fun getContractAddress(token: Token.ERC20, chain: Chain): String? {
        return contracts[chain.chainId]?.get(token.symbol)
    }

    /**
     * Get all tokens available on a specific chain.
     *
     * Returns native token plus all ERC-20 tokens that have contract addresses
     * registered for the given chain.
     *
     * @param chain The blockchain network
     * @return List of available tokens (native + ERC-20s with addresses)
     */
    fun getTokensForChain(chain: Chain): List<Token> {
        val chainContracts = contracts[chain.chainId] ?: emptyMap()

        // Native token is always available
        val availableTokens = mutableListOf<Token>(Token.Native)

        // Add ERC-20 tokens that have contract addresses for this chain
        TokenRegistry.erc20Tokens.forEach { erc20 ->
            if (chainContracts.containsKey(erc20.symbol)) {
                availableTokens.add(erc20)
            }
        }

        return availableTokens
    }

    /**
     * Check if a token is available on a specific chain.
     *
     * Native tokens are always available. ERC-20 tokens require a registered
     * contract address for the chain.
     *
     * @param token The token to check
     * @param chain The blockchain network
     * @return true if the token can be used on the chain
     */
    fun isTokenAvailable(token: Token, chain: Chain): Boolean {
        return when (token) {
            is Token.Native -> true  // Native is always available
            is Token.ERC20 -> getContractAddress(token, chain) != null
        }
    }

    /**
     * Find token by contract address on a specific chain.
     *
     * @param address The contract address to look up
     * @param chain The blockchain network
     * @return The token if found, null otherwise
     */
    fun findByContractAddress(address: String, chain: Chain): Token.ERC20? {
        val chainContracts = contracts[chain.chainId] ?: return null

        val symbol = chainContracts.entries.find {
            it.value.equals(address, ignoreCase = true)
        }?.key ?: return null

        return TokenRegistry.findBySymbol(symbol) as? Token.ERC20
    }

    /**
     * Get all chains that support a specific token.
     *
     * @param token The token to check
     * @return List of chains where the token is available
     */
    fun getChainsForToken(token: Token): List<Chain> {
        return when (token) {
            is Token.Native -> ChainRegistry.allChains  // Native available everywhere
            is Token.ERC20 -> ChainRegistry.allChains.filter { chain ->
                contracts[chain.chainId]?.containsKey(token.symbol) == true
            }
        }
    }
}
