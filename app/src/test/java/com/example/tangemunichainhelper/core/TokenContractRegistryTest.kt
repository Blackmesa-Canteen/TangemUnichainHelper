package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TokenContractRegistry.
 *
 * Tests the mapping between tokens and their chain-specific contract addresses.
 */
class TokenContractRegistryTest {

    // =========================================================================
    // getContractAddress Tests - Unichain
    // =========================================================================

    @Test
    fun `getContractAddress should return USDC address for Unichain`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDC, Chain.Unichain)

        assertNotNull(address)
        assertEquals("0x078D782b760474a361dDA0AF3839290b0EF57AD6", address)
    }

    @Test
    fun `getContractAddress should return USDT address for Unichain`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDT, Chain.Unichain)

        assertNotNull(address)
        assertEquals("0x9151434b16b9763660705744891fa906f660ecc5", address)
    }

    // =========================================================================
    // getContractAddress Tests - Sepolia (No tokens)
    // =========================================================================

    @Test
    fun `getContractAddress should return null for USDC on Sepolia`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDC, Chain.Sepolia)

        assertNull(address)
    }

    @Test
    fun `getContractAddress should return null for USDT on Sepolia`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDT, Chain.Sepolia)

        assertNull(address)
    }

    // =========================================================================
    // getTokensForChain Tests
    // =========================================================================

    @Test
    fun `getTokensForChain should return Native, USDC, and USDT for Unichain`() {
        val tokens = TokenContractRegistry.getTokensForChain(Chain.Unichain)

        assertEquals(3, tokens.size)
        assertTrue("Should contain Native", tokens.any { it is Token.Native })
        assertTrue("Should contain USDC", tokens.any { it.symbol == "USDC" })
        assertTrue("Should contain USDT", tokens.any { it.symbol == "USDT" })
    }

    @Test
    fun `getTokensForChain should return only Native for Sepolia`() {
        val tokens = TokenContractRegistry.getTokensForChain(Chain.Sepolia)

        assertEquals(1, tokens.size)
        assertTrue("Should contain Native", tokens.first() is Token.Native)
    }

    @Test
    fun `getTokensForChain should always include Native token`() {
        // For any chain, Native should always be available
        ChainRegistry.allChains.forEach { chain ->
            val tokens = TokenContractRegistry.getTokensForChain(chain)
            assertTrue(
                "Native should be available on ${chain.name}",
                tokens.any { it is Token.Native }
            )
        }
    }

    // =========================================================================
    // isTokenAvailable Tests
    // =========================================================================

    @Test
    fun `isTokenAvailable should return true for Native on any chain`() {
        assertTrue(TokenContractRegistry.isTokenAvailable(Token.Native, Chain.Unichain))
        assertTrue(TokenContractRegistry.isTokenAvailable(Token.Native, Chain.Sepolia))
    }

    @Test
    fun `isTokenAvailable should return true for USDC on Unichain`() {
        assertTrue(TokenContractRegistry.isTokenAvailable(TokenRegistry.USDC, Chain.Unichain))
    }

    @Test
    fun `isTokenAvailable should return false for USDC on Sepolia`() {
        assertFalse(TokenContractRegistry.isTokenAvailable(TokenRegistry.USDC, Chain.Sepolia))
    }

    @Test
    fun `isTokenAvailable should return true for USDT on Unichain`() {
        assertTrue(TokenContractRegistry.isTokenAvailable(TokenRegistry.USDT, Chain.Unichain))
    }

    @Test
    fun `isTokenAvailable should return false for USDT on Sepolia`() {
        assertFalse(TokenContractRegistry.isTokenAvailable(TokenRegistry.USDT, Chain.Sepolia))
    }

    // =========================================================================
    // findByContractAddress Tests
    // =========================================================================

    @Test
    fun `findByContractAddress should find USDC on Unichain`() {
        val token = TokenContractRegistry.findByContractAddress(
            "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
            Chain.Unichain
        )

        assertNotNull(token)
        assertEquals("USDC", token?.symbol)
    }

    @Test
    fun `findByContractAddress should find USDT on Unichain`() {
        val token = TokenContractRegistry.findByContractAddress(
            "0x9151434b16b9763660705744891fa906f660ecc5",
            Chain.Unichain
        )

        assertNotNull(token)
        assertEquals("USDT", token?.symbol)
    }

    @Test
    fun `findByContractAddress should be case-insensitive`() {
        val tokenLower = TokenContractRegistry.findByContractAddress(
            "0x078d782b760474a361dda0af3839290b0ef57ad6",
            Chain.Unichain
        )
        val tokenUpper = TokenContractRegistry.findByContractAddress(
            "0x078D782B760474A361DDA0AF3839290B0EF57AD6",
            Chain.Unichain
        )

        assertNotNull(tokenLower)
        assertNotNull(tokenUpper)
        assertEquals("USDC", tokenLower?.symbol)
        assertEquals("USDC", tokenUpper?.symbol)
    }

    @Test
    fun `findByContractAddress should return null for unknown address`() {
        val token = TokenContractRegistry.findByContractAddress(
            "0x0000000000000000000000000000000000000000",
            Chain.Unichain
        )

        assertNull(token)
    }

    @Test
    fun `findByContractAddress should return null for wrong chain`() {
        // USDC address from Unichain shouldn't be found on Sepolia
        val token = TokenContractRegistry.findByContractAddress(
            "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
            Chain.Sepolia
        )

        assertNull(token)
    }

    // =========================================================================
    // getChainsForToken Tests
    // =========================================================================

    @Test
    fun `getChainsForToken should return all chains for Native`() {
        val chains = TokenContractRegistry.getChainsForToken(Token.Native)

        assertEquals(ChainRegistry.allChains.size, chains.size)
        assertTrue(chains.containsAll(ChainRegistry.allChains))
    }

    @Test
    fun `getChainsForToken should return only Unichain for USDC`() {
        val chains = TokenContractRegistry.getChainsForToken(TokenRegistry.USDC)

        assertEquals(1, chains.size)
        assertTrue(chains.contains(Chain.Unichain))
        assertFalse(chains.contains(Chain.Sepolia))
    }

    @Test
    fun `getChainsForToken should return only Unichain for USDT`() {
        val chains = TokenContractRegistry.getChainsForToken(TokenRegistry.USDT)

        assertEquals(1, chains.size)
        assertTrue(chains.contains(Chain.Unichain))
    }

    // =========================================================================
    // TokenRegistry Integration Tests
    // =========================================================================

    @Test
    fun `TokenRegistry findByContractAddress should delegate to TokenContractRegistry`() {
        val token = TokenRegistry.findByContractAddress(
            "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
            Chain.Unichain
        )

        assertNotNull(token)
        assertEquals("USDC", token?.symbol)
    }

    @Test
    fun `TokenRegistry getTokensForChain should delegate to TokenContractRegistry`() {
        val tokens = TokenRegistry.getTokensForChain(Chain.Unichain)

        assertEquals(3, tokens.size)
    }

    // =========================================================================
    // Contract Address Verification Tests
    // =========================================================================

    @Test
    fun `USDC contract address should be valid checksum format`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDC, Chain.Unichain)

        assertNotNull(address)
        assertTrue("Address should start with 0x", address!!.startsWith("0x"))
        assertEquals("Address should be 42 characters", 42, address.length)
    }

    @Test
    fun `USDT contract address should be valid format`() {
        val address = TokenContractRegistry.getContractAddress(TokenRegistry.USDT, Chain.Unichain)

        assertNotNull(address)
        assertTrue("Address should start with 0x", address!!.startsWith("0x"))
        assertEquals("Address should be 42 characters", 42, address.length)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `getContractAddress with custom ERC20 should return null if not registered`() {
        val customToken = Token.ERC20(
            symbol = "CUSTOM",
            name = "Custom Token",
            decimals = 18
        )

        val address = TokenContractRegistry.getContractAddress(customToken, Chain.Unichain)

        assertNull(address)
    }

    @Test
    fun `getTokensForChain with unknown chain should return only Native`() {
        val customChain = Chain.Custom(
            chainId = 999999L,
            name = "Unknown Chain",
            explorerUrl = "https://example.com",
            rpcUrls = listOf("https://rpc.example.com")
        )

        val tokens = TokenContractRegistry.getTokensForChain(customChain)

        assertEquals(1, tokens.size)
        assertTrue(tokens.first() is Token.Native)
    }
}
