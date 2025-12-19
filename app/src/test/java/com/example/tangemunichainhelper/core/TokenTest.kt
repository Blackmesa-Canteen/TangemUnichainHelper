package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Unit tests for Token and TokenRegistry.
 *
 * Note: Contract addresses are now managed by [TokenContractRegistry], not [Token.ERC20].
 */
class TokenTest {

    // =========================================================================
    // Token.Native Tests
    // =========================================================================

    @Test
    fun `Native token should have correct properties`() {
        val native = Token.Native

        assertEquals("ETH", native.symbol)
        assertEquals("Native Currency", native.name)
        assertEquals(18, native.decimals)
        assertEquals(BigInteger.valueOf(21000), native.defaultGasLimit)
        assertTrue(native.isNative)
        assertFalse(native.isERC20)
    }

    @Test
    fun `Native toSmallestUnit should convert to Wei`() {
        val native = Token.Native

        // 1 ETH = 10^18 Wei
        val oneEth = BigDecimal.ONE
        val wei = native.toSmallestUnit(oneEth)
        assertEquals(BigInteger.TEN.pow(18), wei)
    }

    @Test
    fun `Native toSmallestUnit should handle fractional amounts`() {
        val native = Token.Native

        // 0.5 ETH = 5 * 10^17 Wei
        val halfEth = BigDecimal("0.5")
        val wei = native.toSmallestUnit(halfEth)
        assertEquals(BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17)), wei)
    }

    @Test
    fun `Native toSmallestUnit should handle small amounts`() {
        val native = Token.Native

        // 0.000001 ETH = 10^12 Wei
        val smallEth = BigDecimal("0.000001")
        val wei = native.toSmallestUnit(smallEth)
        assertEquals(BigInteger.TEN.pow(12), wei)
    }

    @Test
    fun `Native fromSmallestUnit should convert from Wei`() {
        val native = Token.Native

        // 10^18 Wei = 1 ETH
        val wei = BigInteger.TEN.pow(18)
        val ethAmount = native.fromSmallestUnit(wei)
        assertEquals(0, BigDecimal.ONE.compareTo(ethAmount))
    }

    @Test
    fun `Native fromSmallestUnit should handle fractional amounts`() {
        val native = Token.Native

        // 5 * 10^17 Wei = 0.5 ETH
        val wei = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17))
        val ethAmount = native.fromSmallestUnit(wei)
        assertEquals(0, BigDecimal("0.5").compareTo(ethAmount))
    }

    @Test
    fun `Native round trip conversion should be accurate`() {
        val native = Token.Native

        val original = BigDecimal("1.234567890123456789")
        val wei = native.toSmallestUnit(original)
        val converted = native.fromSmallestUnit(wei)

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
    }

    @Test
    fun `USDT token should have correct properties`() {
        val usdt = TokenRegistry.USDT

        assertEquals("USDT", usdt.symbol)
        assertEquals("Tether USD", usdt.name)
        assertEquals(6, usdt.decimals)
        assertFalse(usdt.isNative)
        assertTrue(usdt.isERC20)
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
    fun `TokenRegistry should contain Native, USDC, and USDT`() {
        val tokens = TokenRegistry.allTokens

        assertTrue("Should contain ETH (Native)", tokens.any { it.symbol == "ETH" })
        assertTrue("Should contain USDC", tokens.any { it.symbol == "USDC" })
        assertTrue("Should contain USDT", tokens.any { it.symbol == "USDT" })
        assertEquals("Should have exactly 3 tokens", 3, tokens.size)
    }

    @Test
    fun `TokenRegistry Native should be singleton`() {
        assertSame(Token.Native, TokenRegistry.Native)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `TokenRegistry ETH deprecated alias should work`() {
        // Backward compatibility test
        assertSame(Token.Native, TokenRegistry.ETH)
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
    fun `TokenRegistry erc20Tokens should not contain Native`() {
        val erc20s = TokenRegistry.erc20Tokens

        assertFalse("Should not contain Native", erc20s.any { it is Token.Native })
        assertTrue("Should contain USDC", erc20s.any { it.symbol == "USDC" })
        assertTrue("Should contain USDT", erc20s.any { it.symbol == "USDT" })
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `toSmallestUnit with zero should return zero`() {
        assertEquals(BigInteger.ZERO, Token.Native.toSmallestUnit(BigDecimal.ZERO))
        assertEquals(BigInteger.ZERO, TokenRegistry.USDC.toSmallestUnit(BigDecimal.ZERO))
    }

    @Test
    fun `fromSmallestUnit with zero should return zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(Token.Native.fromSmallestUnit(BigInteger.ZERO)))
        assertEquals(0, BigDecimal.ZERO.compareTo(TokenRegistry.USDC.fromSmallestUnit(BigInteger.ZERO)))
    }

    @Test
    fun `toSmallestUnit with large amount should work`() {
        val largeAmount = BigDecimal("1000000") // 1 million ETH
        val wei = Token.Native.toSmallestUnit(largeAmount)

        // 1,000,000 * 10^18 = 10^24
        assertEquals(BigInteger.TEN.pow(24), wei)
    }

    // =========================================================================
    // Backward Compatibility Tests
    // =========================================================================

    @Test
    @Suppress("DEPRECATION")
    fun `Token_ETH deprecated alias should work`() {
        assertSame(Token.Native, Token.ETH)
    }
}
