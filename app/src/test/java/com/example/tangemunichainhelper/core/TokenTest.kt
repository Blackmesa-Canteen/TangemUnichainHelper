package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Unit tests for Token and TokenRegistry.
 */
class TokenTest {

    // =========================================================================
    // Token.ETH Tests
    // =========================================================================

    @Test
    fun `ETH token should have correct properties`() {
        val eth = Token.ETH

        assertEquals("ETH", eth.symbol)
        assertEquals("Ethereum", eth.name)
        assertEquals(18, eth.decimals)
        assertEquals(BigInteger.valueOf(21000), eth.defaultGasLimit)
        assertTrue(eth.isNative)
        assertFalse(eth.isERC20)
    }

    @Test
    fun `ETH toSmallestUnit should convert to Wei`() {
        val eth = Token.ETH

        // 1 ETH = 10^18 Wei
        val oneEth = BigDecimal.ONE
        val wei = eth.toSmallestUnit(oneEth)
        assertEquals(BigInteger.TEN.pow(18), wei)
    }

    @Test
    fun `ETH toSmallestUnit should handle fractional amounts`() {
        val eth = Token.ETH

        // 0.5 ETH = 5 * 10^17 Wei
        val halfEth = BigDecimal("0.5")
        val wei = eth.toSmallestUnit(halfEth)
        assertEquals(BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17)), wei)
    }

    @Test
    fun `ETH toSmallestUnit should handle small amounts`() {
        val eth = Token.ETH

        // 0.000001 ETH = 10^12 Wei
        val smallEth = BigDecimal("0.000001")
        val wei = eth.toSmallestUnit(smallEth)
        assertEquals(BigInteger.TEN.pow(12), wei)
    }

    @Test
    fun `ETH fromSmallestUnit should convert from Wei`() {
        val eth = Token.ETH

        // 10^18 Wei = 1 ETH
        val wei = BigInteger.TEN.pow(18)
        val ethAmount = eth.fromSmallestUnit(wei)
        assertEquals(0, BigDecimal.ONE.compareTo(ethAmount))
    }

    @Test
    fun `ETH fromSmallestUnit should handle fractional amounts`() {
        val eth = Token.ETH

        // 5 * 10^17 Wei = 0.5 ETH
        val wei = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17))
        val ethAmount = eth.fromSmallestUnit(wei)
        assertEquals(0, BigDecimal("0.5").compareTo(ethAmount))
    }

    @Test
    fun `ETH round trip conversion should be accurate`() {
        val eth = Token.ETH

        val original = BigDecimal("1.234567890123456789")
        val wei = eth.toSmallestUnit(original)
        val converted = eth.fromSmallestUnit(wei)

        // Should match to 18 decimal places
        assertEquals(0, original.compareTo(converted))
    }

    // =========================================================================
    // Token.ERC20 Tests
    // =========================================================================

    @Test
    fun `USDC token should have correct properties`() {
        val usdc = TokenRegistry.USDC

        assertEquals("USDC", usdc.symbol)
        assertEquals("USD Coin", usdc.name)
        assertEquals(6, usdc.decimals)
        assertEquals(BigInteger.valueOf(65000), usdc.defaultGasLimit)
        assertFalse(usdc.isNative)
        assertTrue(usdc.isERC20)
        assertEquals("0x078D782b760474a361dDA0AF3839290b0EF57AD6", (usdc as Token.ERC20).contractAddress)
    }

    @Test
    fun `USDC toSmallestUnit should convert to 6 decimals`() {
        val usdc = TokenRegistry.USDC

        // 1 USDC = 10^6 smallest units
        val oneUsdc = BigDecimal.ONE
        val smallest = usdc.toSmallestUnit(oneUsdc)
        assertEquals(BigInteger.valueOf(1000000), smallest)
    }

    @Test
    fun `USDC toSmallestUnit should handle cents`() {
        val usdc = TokenRegistry.USDC

        // 0.01 USDC = 10000 smallest units
        val oneCent = BigDecimal("0.01")
        val smallest = usdc.toSmallestUnit(oneCent)
        assertEquals(BigInteger.valueOf(10000), smallest)
    }

    @Test
    fun `USDC fromSmallestUnit should convert from smallest units`() {
        val usdc = TokenRegistry.USDC

        // 1000000 smallest = 1 USDC
        val smallest = BigInteger.valueOf(1000000)
        val usdcAmount = usdc.fromSmallestUnit(smallest)
        assertEquals(0, BigDecimal.ONE.compareTo(usdcAmount))
    }

    @Test
    fun `USDC round trip conversion should be accurate`() {
        val usdc = TokenRegistry.USDC

        val original = BigDecimal("123.456789")
        val smallest = usdc.toSmallestUnit(original)
        val converted = usdc.fromSmallestUnit(smallest)

        // Should match to 6 decimal places
        assertEquals(0, BigDecimal("123.456789").compareTo(converted))
    }

    @Test
    fun `custom ERC20 token should have correct properties`() {
        val customToken = Token.ERC20(
            symbol = "TEST",
            name = "Test Token",
            contractAddress = "0x1234567890123456789012345678901234567890",
            decimals = 8,
            defaultGasLimit = BigInteger.valueOf(100000)
        )

        assertEquals("TEST", customToken.symbol)
        assertEquals("Test Token", customToken.name)
        assertEquals(8, customToken.decimals)
        assertEquals(BigInteger.valueOf(100000), customToken.defaultGasLimit)
        assertFalse(customToken.isNative)
        assertTrue(customToken.isERC20)
    }

    // =========================================================================
    // TokenRegistry Tests
    // =========================================================================

    @Test
    fun `TokenRegistry should contain ETH and USDC`() {
        val tokens = TokenRegistry.allTokens

        assertTrue("Should contain ETH", tokens.any { it.symbol == "ETH" })
        assertTrue("Should contain USDC", tokens.any { it.symbol == "USDC" })
        assertTrue("Should have at least 2 tokens", tokens.size >= 2)
    }

    @Test
    fun `TokenRegistry ETH should be singleton`() {
        assertSame(Token.ETH, TokenRegistry.ETH)
    }

    @Test
    fun `TokenRegistry findBySymbol should find ETH`() {
        val found = TokenRegistry.findBySymbol("ETH")
        assertNotNull(found)
        assertEquals("ETH", found?.symbol)
    }

    @Test
    fun `TokenRegistry findBySymbol should find USDC case-insensitive`() {
        val found1 = TokenRegistry.findBySymbol("USDC")
        val found2 = TokenRegistry.findBySymbol("usdc")
        val found3 = TokenRegistry.findBySymbol("Usdc")

        assertNotNull(found1)
        assertNotNull(found2)
        assertNotNull(found3)
        assertEquals("USDC", found1?.symbol)
    }

    @Test
    fun `TokenRegistry findBySymbol should return null for unknown`() {
        val found = TokenRegistry.findBySymbol("UNKNOWN")
        assertNull(found)
    }

    @Test
    fun `TokenRegistry findByContractAddress should find USDC`() {
        val found = TokenRegistry.findByContractAddress("0x078D782b760474a361dDA0AF3839290b0EF57AD6")
        assertNotNull(found)
        assertEquals("USDC", found?.symbol)
    }

    @Test
    fun `TokenRegistry findByContractAddress should be case-insensitive`() {
        val found = TokenRegistry.findByContractAddress("0x078d782b760474a361dda0af3839290b0ef57ad6")
        assertNotNull(found)
        assertEquals("USDC", found?.symbol)
    }

    @Test
    fun `TokenRegistry findByContractAddress should return null for unknown`() {
        val found = TokenRegistry.findByContractAddress("0x0000000000000000000000000000000000000000")
        assertNull(found)
    }

    @Test
    fun `TokenRegistry erc20Tokens should not contain ETH`() {
        val erc20s = TokenRegistry.erc20Tokens

        assertFalse("Should not contain ETH", erc20s.any { it.symbol == "ETH" })
        assertTrue("Should contain USDC", erc20s.any { it.symbol == "USDC" })
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `toSmallestUnit with zero should return zero`() {
        assertEquals(BigInteger.ZERO, Token.ETH.toSmallestUnit(BigDecimal.ZERO))
        assertEquals(BigInteger.ZERO, TokenRegistry.USDC.toSmallestUnit(BigDecimal.ZERO))
    }

    @Test
    fun `fromSmallestUnit with zero should return zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(Token.ETH.fromSmallestUnit(BigInteger.ZERO)))
        assertEquals(0, BigDecimal.ZERO.compareTo(TokenRegistry.USDC.fromSmallestUnit(BigInteger.ZERO)))
    }

    @Test
    fun `toSmallestUnit with large amount should work`() {
        val largeAmount = BigDecimal("1000000") // 1 million ETH
        val wei = Token.ETH.toSmallestUnit(largeAmount)

        // 1,000,000 * 10^18 = 10^24
        assertEquals(BigInteger.TEN.pow(24), wei)
    }
}
