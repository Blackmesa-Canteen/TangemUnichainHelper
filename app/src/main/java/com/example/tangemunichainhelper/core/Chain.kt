package com.example.tangemunichainhelper.core

/**
 * Represents an EVM-compatible blockchain network.
 *
 * ## Design Philosophy
 * This sealed class follows the same pattern as [Token], providing:
 * - Type safety via sealed class hierarchy
 * - Predefined chains as data objects for well-known networks
 * - [Custom] data class for developer-defined networks
 *
 * ## Why Tangem Can Sign for Any Chain
 * Tangem cards sign raw 32-byte hashes - they don't know or care about chains.
 * The chain-awareness comes from:
 * 1. Including the chain ID in the EIP-155 transaction hash
 * 2. Broadcasting to the correct RPC endpoint
 *
 * This means you can sign for ANY EVM chain by simply changing the chain configuration.
 *
 * ## Adding a New Chain
 * Developers can add new chains by:
 * 1. Adding a new data object in this sealed class (or use [Custom])
 * 2. Adding it to [ChainRegistry.allChains]
 * 3. Adding token contract addresses in [TokenContractRegistry]
 *
 * @see ChainRegistry for chain management
 * @see TokenContractRegistry for chain-specific token addresses
 */
sealed class Chain {
    /** Unique chain ID used in EIP-155 transaction signing */
    abstract val chainId: Long

    /** Human-readable network name (e.g., "Unichain Mainnet") */
    abstract val name: String

    /** Short display name for UI (e.g., "Unichain") */
    abstract val shortName: String

    /** Native currency symbol (e.g., "ETH", "MATIC") */
    abstract val nativeCurrencySymbol: String

    /** Native currency decimals (18 for most EVM chains) */
    open val nativeCurrencyDecimals: Int = 18

    /** Block explorer base URL (e.g., "https://uniscan.xyz") */
    abstract val explorerUrl: String

    /** RPC endpoints in order of preference (first is primary, rest are fallbacks) */
    abstract val rpcUrls: List<String>

    /** Whether this is a testnet */
    abstract val isTestnet: Boolean

    // =========================================================================
    // Predefined Chains - Only Unichain and Sepolia
    // Developers: Add your own chains following this pattern
    // =========================================================================

    /**
     * Unichain Mainnet - The default chain for this app.
     *
     * Chain ID: 130
     * Verified: https://docs.unichain.org/docs/technical-information/network-information
     */
    data object Unichain : Chain() {
        override val chainId: Long = 130L
        override val name: String = "Unichain Mainnet"
        override val shortName: String = "Unichain"
        override val nativeCurrencySymbol: String = "ETH"
        override val explorerUrl: String = "https://uniscan.xyz"
        override val rpcUrls: List<String> = listOf(
            "https://unichain.drpc.org",      // dRPC (recommended, reliable)
            "https://mainnet.unichain.org"     // Official (rate-limited)
        )
        override val isTestnet: Boolean = false
    }

    /**
     * Ethereum Sepolia Testnet - For development and testing.
     *
     * Chain ID: 11155111
     * Verified: https://chainlist.org/chain/11155111
     */
    data object Sepolia : Chain() {
        override val chainId: Long = 11155111L
        override val name: String = "Sepolia Testnet"
        override val shortName: String = "Sepolia"
        override val nativeCurrencySymbol: String = "ETH"
        override val explorerUrl: String = "https://sepolia.etherscan.io"
        override val rpcUrls: List<String> = listOf(
            "https://sepolia.drpc.org",
            "https://rpc.sepolia.org",
            "https://rpc2.sepolia.org"
        )
        override val isTestnet: Boolean = true
    }

    // =========================================================================
    // Custom Chain Support
    // =========================================================================

    /**
     * Custom chain for developer-defined networks.
     *
     * Use this when you need to add a chain that isn't predefined.
     *
     * ## Example: Adding Polygon
     * ```kotlin
     * val polygon = Chain.Custom(
     *     chainId = 137L,
     *     name = "Polygon Mainnet",
     *     shortName = "Polygon",
     *     nativeCurrencySymbol = "MATIC",
     *     explorerUrl = "https://polygonscan.com",
     *     rpcUrls = listOf("https://polygon-rpc.com"),
     *     isTestnet = false
     * )
     * ```
     */
    data class Custom(
        override val chainId: Long,
        override val name: String,
        override val shortName: String = name,
        override val nativeCurrencySymbol: String = "ETH",
        override val nativeCurrencyDecimals: Int = 18,
        override val explorerUrl: String,
        override val rpcUrls: List<String>,
        override val isTestnet: Boolean = false
    ) : Chain()

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /** Primary RPC URL (first in the list) */
    val primaryRpcUrl: String
        get() = rpcUrls.first()

    /** Generate transaction explorer URL */
    fun txExplorerUrl(txHash: String): String = "$explorerUrl/tx/$txHash"

    /** Generate address explorer URL */
    fun addressExplorerUrl(address: String): String = "$explorerUrl/address/$address"

    /** Generate token contract explorer URL */
    fun tokenExplorerUrl(contractAddress: String): String = "$explorerUrl/token/$contractAddress"

    /** Check if this is a predefined chain (not custom) */
    val isPredefined: Boolean
        get() = this !is Custom
}

/**
 * Registry of all supported blockchain networks.
 *
 * ## Design Notes
 * - [Unichain] is the default chain (first in list)
 * - Only Unichain and Sepolia are included by default
 * - Developers should add their own chains for other networks
 *
 * ## Adding a New Chain
 * 1. Add as data object in [Chain] (or use [Chain.Custom])
 * 2. Add to [allChains] list
 * 3. Add token contracts in [TokenContractRegistry]
 *
 * @see Chain for chain definitions
 */
object ChainRegistry {

    /**
     * Default chain for the app. Unichain is the primary focus.
     */
    val default: Chain = Chain.Unichain

    /**
     * All available chains.
     *
     * Currently only includes Unichain and Sepolia.
     * Developers: Add your chains here after defining them in [Chain].
     */
    val allChains: List<Chain> = listOf(
        Chain.Unichain,  // Default - Mainnet
        Chain.Sepolia    // Testnet for development
        // Developers: Add your chains here
        // Chain.Ethereum,
        // Chain.Polygon,
        // Chain.Arbitrum,
        // etc.
    )

    /**
     * Only mainnet chains (excludes testnets).
     */
    val mainnetChains: List<Chain>
        get() = allChains.filter { !it.isTestnet }

    /**
     * Only testnet chains.
     */
    val testnetChains: List<Chain>
        get() = allChains.filter { it.isTestnet }

    /**
     * Find chain by chain ID.
     *
     * @param chainId The EIP-155 chain ID
     * @return The chain if found, null otherwise
     */
    fun findByChainId(chainId: Long): Chain? {
        return allChains.find { it.chainId == chainId }
    }

    /**
     * Find chain by name (case-insensitive).
     *
     * Matches against both [Chain.name] and [Chain.shortName].
     *
     * @param name The chain name to search for
     * @return The chain if found, null otherwise
     */
    fun findByName(name: String): Chain? {
        return allChains.find {
            it.name.equals(name, ignoreCase = true) ||
            it.shortName.equals(name, ignoreCase = true)
        }
    }

    /**
     * Check if a chain ID is supported.
     *
     * @param chainId The EIP-155 chain ID
     * @return true if the chain is in [allChains]
     */
    fun isSupported(chainId: Long): Boolean {
        return findByChainId(chainId) != null
    }
}
