package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Chain and ChainRegistry.
 */
class ChainTest {

    // =========================================================================
    // Chain.Unichain Tests
    // =========================================================================

    @Test
    fun `Unichain should have correct chain ID`() {
        assertEquals(130L, Chain.Unichain.chainId)
    }

    @Test
    fun `Unichain should have correct properties`() {
        val unichain = Chain.Unichain

        assertEquals("Unichain Mainnet", unichain.name)
        assertEquals("Unichain", unichain.shortName)
        assertEquals("ETH", unichain.nativeCurrencySymbol)
        assertEquals(18, unichain.nativeCurrencyDecimals)
        assertEquals("https://uniscan.xyz", unichain.explorerUrl)
        assertFalse(unichain.isTestnet)
        assertTrue(unichain.isPredefined)
    }

    @Test
    fun `Unichain should have valid RPC URLs`() {
        val rpcUrls = Chain.Unichain.rpcUrls

        assertTrue("Should have at least one RPC URL", rpcUrls.isNotEmpty())
        assertTrue("Primary RPC should be dRPC", rpcUrls.first().contains("drpc.org"))
        assertTrue("Should have fallback RPC", rpcUrls.size >= 2)
    }

    @Test
    fun `Unichain primaryRpcUrl should return first URL`() {
        assertEquals(Chain.Unichain.rpcUrls.first(), Chain.Unichain.primaryRpcUrl)
    }

    @Test
    fun `Unichain txExplorerUrl should generate correct URL`() {
        val txHash = "0x1234567890abcdef"
        val expectedUrl = "https://uniscan.xyz/tx/0x1234567890abcdef"

        assertEquals(expectedUrl, Chain.Unichain.txExplorerUrl(txHash))
    }

    @Test
    fun `Unichain addressExplorerUrl should generate correct URL`() {
        val address = "0xABCD1234"
        val expectedUrl = "https://uniscan.xyz/address/0xABCD1234"

        assertEquals(expectedUrl, Chain.Unichain.addressExplorerUrl(address))
    }

    @Test
    fun `Unichain tokenExplorerUrl should generate correct URL`() {
        val contractAddress = "0x078D782b760474a361dDA0AF3839290b0EF57AD6"
        val expectedUrl = "https://uniscan.xyz/token/0x078D782b760474a361dDA0AF3839290b0EF57AD6"

        assertEquals(expectedUrl, Chain.Unichain.tokenExplorerUrl(contractAddress))
    }

    // =========================================================================
    // Chain.Sepolia Tests
    // =========================================================================

    @Test
    fun `Sepolia should have correct chain ID`() {
        assertEquals(11155111L, Chain.Sepolia.chainId)
    }

    @Test
    fun `Sepolia should have correct properties`() {
        val sepolia = Chain.Sepolia

        assertEquals("Sepolia Testnet", sepolia.name)
        assertEquals("Sepolia", sepolia.shortName)
        assertEquals("ETH", sepolia.nativeCurrencySymbol)
        assertEquals(18, sepolia.nativeCurrencyDecimals)
        assertEquals("https://sepolia.etherscan.io", sepolia.explorerUrl)
        assertTrue(sepolia.isTestnet)
        assertTrue(sepolia.isPredefined)
    }

    @Test
    fun `Sepolia should have valid RPC URLs`() {
        val rpcUrls = Chain.Sepolia.rpcUrls

        assertTrue("Should have at least one RPC URL", rpcUrls.isNotEmpty())
    }

    // =========================================================================
    // Chain.Custom Tests
    // =========================================================================

    @Test
    fun `Custom chain should have correct properties`() {
        val polygon = Chain.Custom(
            chainId = 137L,
            name = "Polygon Mainnet",
            shortName = "Polygon",
            nativeCurrencySymbol = "MATIC",
            explorerUrl = "https://polygonscan.com",
            rpcUrls = listOf("https://polygon-rpc.com"),
            isTestnet = false
        )

        assertEquals(137L, polygon.chainId)
        assertEquals("Polygon Mainnet", polygon.name)
        assertEquals("Polygon", polygon.shortName)
        assertEquals("MATIC", polygon.nativeCurrencySymbol)
        assertEquals(18, polygon.nativeCurrencyDecimals)
        assertEquals("https://polygonscan.com", polygon.explorerUrl)
        assertFalse(polygon.isTestnet)
        assertFalse(polygon.isPredefined)
    }

    @Test
    fun `Custom chain should support custom decimals`() {
        val customChain = Chain.Custom(
            chainId = 999L,
            name = "Custom Chain",
            nativeCurrencySymbol = "CUST",
            nativeCurrencyDecimals = 8,
            explorerUrl = "https://example.com",
            rpcUrls = listOf("https://rpc.example.com")
        )

        assertEquals(8, customChain.nativeCurrencyDecimals)
    }

    @Test
    fun `Custom chain shortName should default to name`() {
        val customChain = Chain.Custom(
            chainId = 999L,
            name = "Custom Chain",
            explorerUrl = "https://example.com",
            rpcUrls = listOf("https://rpc.example.com")
        )

        assertEquals("Custom Chain", customChain.shortName)
    }

    // =========================================================================
    // ChainRegistry Tests
    // =========================================================================

    @Test
    fun `ChainRegistry default should be Unichain`() {
        assertSame(Chain.Unichain, ChainRegistry.default)
    }

    @Test
    fun `ChainRegistry allChains should contain Unichain and Sepolia`() {
        val chains = ChainRegistry.allChains

        assertTrue("Should contain Unichain", chains.contains(Chain.Unichain))
        assertTrue("Should contain Sepolia", chains.contains(Chain.Sepolia))
        assertEquals("Should have exactly 2 chains", 2, chains.size)
    }

    @Test
    fun `ChainRegistry mainnetChains should only contain mainnets`() {
        val mainnets = ChainRegistry.mainnetChains

        assertTrue("Should contain Unichain", mainnets.contains(Chain.Unichain))
        assertFalse("Should not contain Sepolia", mainnets.contains(Chain.Sepolia))
        assertTrue("All chains should be mainnet", mainnets.all { !it.isTestnet })
    }

    @Test
    fun `ChainRegistry testnetChains should only contain testnets`() {
        val testnets = ChainRegistry.testnetChains

        assertFalse("Should not contain Unichain", testnets.contains(Chain.Unichain))
        assertTrue("Should contain Sepolia", testnets.contains(Chain.Sepolia))
        assertTrue("All chains should be testnet", testnets.all { it.isTestnet })
    }

    @Test
    fun `ChainRegistry findByChainId should find Unichain`() {
        val found = ChainRegistry.findByChainId(130L)

        assertNotNull(found)
        assertSame(Chain.Unichain, found)
    }

    @Test
    fun `ChainRegistry findByChainId should find Sepolia`() {
        val found = ChainRegistry.findByChainId(11155111L)

        assertNotNull(found)
        assertSame(Chain.Sepolia, found)
    }

    @Test
    fun `ChainRegistry findByChainId should return null for unknown`() {
        val found = ChainRegistry.findByChainId(999L)

        assertNull(found)
    }

    @Test
    fun `ChainRegistry findByName should find Unichain by full name`() {
        val found = ChainRegistry.findByName("Unichain Mainnet")

        assertNotNull(found)
        assertSame(Chain.Unichain, found)
    }

    @Test
    fun `ChainRegistry findByName should find Unichain by short name`() {
        val found = ChainRegistry.findByName("Unichain")

        assertNotNull(found)
        assertSame(Chain.Unichain, found)
    }

    @Test
    fun `ChainRegistry findByName should be case-insensitive`() {
        val found1 = ChainRegistry.findByName("UNICHAIN")
        val found2 = ChainRegistry.findByName("unichain")
        val found3 = ChainRegistry.findByName("UnIcHaIn")

        assertNotNull(found1)
        assertNotNull(found2)
        assertNotNull(found3)
        assertSame(Chain.Unichain, found1)
    }

    @Test
    fun `ChainRegistry findByName should return null for unknown`() {
        val found = ChainRegistry.findByName("Ethereum")

        assertNull(found)
    }

    @Test
    fun `ChainRegistry isSupported should return true for known chains`() {
        assertTrue(ChainRegistry.isSupported(130L))
        assertTrue(ChainRegistry.isSupported(11155111L))
    }

    @Test
    fun `ChainRegistry isSupported should return false for unknown chains`() {
        assertFalse(ChainRegistry.isSupported(1L)) // Ethereum mainnet
        assertFalse(ChainRegistry.isSupported(137L)) // Polygon
        assertFalse(ChainRegistry.isSupported(0L))
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `Chain data objects should be singletons`() {
        assertSame(Chain.Unichain, Chain.Unichain)
        assertSame(Chain.Sepolia, Chain.Sepolia)
    }

    @Test
    fun `Custom chains with same properties should be equal`() {
        val chain1 = Chain.Custom(
            chainId = 137L,
            name = "Polygon",
            explorerUrl = "https://polygonscan.com",
            rpcUrls = listOf("https://rpc.com")
        )
        val chain2 = Chain.Custom(
            chainId = 137L,
            name = "Polygon",
            explorerUrl = "https://polygonscan.com",
            rpcUrls = listOf("https://rpc.com")
        )

        assertEquals(chain1, chain2)
    }
}
