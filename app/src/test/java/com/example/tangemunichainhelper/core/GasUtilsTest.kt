package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/**
 * Unit tests for GasUtils - gas calculations and formatting.
 */
class GasUtilsTest {

    // =========================================================================
    // Gas Buffer Tests
    // =========================================================================

    @Test
    fun `addGasBuffer should add 20 percent`() {
        val estimated = BigInteger.valueOf(100000)
        val buffered = GasUtils.addGasBuffer(estimated)

        // 100,000 * 1.2 = 120,000
        assertEquals(BigInteger.valueOf(120000), buffered)
    }

    @Test
    fun `addGasBuffer should round up`() {
        val estimated = BigInteger.valueOf(21000)
        val buffered = GasUtils.addGasBuffer(estimated)

        // 21,000 * 1.2 = 25,200
        assertEquals(BigInteger.valueOf(25200), buffered)
    }

    @Test
    fun `addGasBuffer with odd number should round up`() {
        val estimated = BigInteger.valueOf(55555)
        val buffered = GasUtils.addGasBuffer(estimated)

        // 55,555 * 1.2 = 66,666
        assertEquals(BigInteger.valueOf(66666), buffered)
    }

    @Test
    fun `addGasBuffer with zero should return zero`() {
        val estimated = BigInteger.ZERO
        val buffered = GasUtils.addGasBuffer(estimated)
        assertEquals(BigInteger.ZERO, buffered)
    }

    // =========================================================================
    // Gas Price Formatting Tests
    // =========================================================================

    @Test
    fun `formatGasPriceGwei should format 1 Gwei`() {
        val oneGwei = BigInteger.TEN.pow(9) // 1 Gwei = 10^9 Wei
        val formatted = GasUtils.formatGasPriceGwei(oneGwei)
        assertEquals("1", formatted)
    }

    @Test
    fun `formatGasPriceGwei should format large values with 2 decimals`() {
        val tenGwei = BigInteger.TEN.pow(10) // 10 Gwei
        val formatted = GasUtils.formatGasPriceGwei(tenGwei)
        assertEquals("10", formatted)
    }

    @Test
    fun `formatGasPriceGwei should format fractional Gwei`() {
        val halfGwei = BigInteger.valueOf(500000000) // 0.5 Gwei
        val formatted = GasUtils.formatGasPriceGwei(halfGwei)
        assertEquals("0.5", formatted)
    }

    @Test
    fun `formatGasPriceGwei should format very small values`() {
        val microGwei = BigInteger.valueOf(1000) // 0.000001 Gwei
        val formatted = GasUtils.formatGasPriceGwei(microGwei)
        assertEquals("0.000001", formatted)
    }

    @Test
    fun `formatGasPriceGwei should handle zero`() {
        val formatted = GasUtils.formatGasPriceGwei(BigInteger.ZERO)
        assertEquals("0", formatted)
    }

    // =========================================================================
    // Gas Limit Formatting Tests
    // =========================================================================

    @Test
    fun `formatGasLimit should format ETH transfer`() {
        val ethGasLimit = BigInteger.valueOf(21000)
        val formatted = GasUtils.formatGasLimit(ethGasLimit, isErc20 = false)
        assertEquals("21,000 (standard)", formatted)
    }

    @Test
    fun `formatGasLimit should format ERC20 transfer`() {
        val erc20GasLimit = BigInteger.valueOf(65000)
        val formatted = GasUtils.formatGasLimit(erc20GasLimit, isErc20 = true)
        assertEquals("65,000 (token transfer)", formatted)
    }

    @Test
    fun `formatGasLimit should format large gas limit as contract call`() {
        val largeGasLimit = BigInteger.valueOf(200000)
        val formatted = GasUtils.formatGasLimit(largeGasLimit, isErc20 = false)
        assertEquals("200,000 (contract call)", formatted)
    }

    @Test
    fun `formatGasLimitPlain should not include label`() {
        val gasLimit = BigInteger.valueOf(65000)
        val formatted = GasUtils.formatGasLimitPlain(gasLimit)
        assertEquals("65,000", formatted)
    }

    // =========================================================================
    // Gas Fee Formatting Tests
    // =========================================================================

    @Test
    fun `formatGasFeeEth should format typical ETH transfer fee`() {
        // 21,000 gas * 1 Gwei = 0.000021 ETH
        val gasFeeWei = BigInteger.valueOf(21000).multiply(BigInteger.TEN.pow(9))
        val formatted = GasUtils.formatGasFeeEth(gasFeeWei)
        assertEquals("0.000021 ETH", formatted)
    }

    @Test
    fun `formatGasFeeEth should format large fee`() {
        // 0.01 ETH
        val gasFeeWei = BigInteger.TEN.pow(16)
        val formatted = GasUtils.formatGasFeeEth(gasFeeWei)
        assertEquals("0.01 ETH", formatted)
    }

    @Test
    fun `formatGasFeeEth should format very small fee`() {
        // 0.0000001 ETH
        val gasFeeWei = BigInteger.TEN.pow(11)
        val formatted = GasUtils.formatGasFeeEth(gasFeeWei)
        assertEquals("0.0000001 ETH", formatted)
    }

    @Test
    fun `formatGasFeeEth should format zero fee`() {
        val formatted = GasUtils.formatGasFeeEth(BigInteger.ZERO)
        assertEquals("0 ETH", formatted)
    }

    @Test
    fun `formatGasFeeEth should show less than for extremely small amounts`() {
        val tinyFee = BigInteger.valueOf(1) // 1 Wei
        val formatted = GasUtils.formatGasFeeEth(tinyFee)
        assertEquals("< 0.00000001 ETH", formatted)
    }

    // =========================================================================
    // Gas Fee Calculation Tests
    // =========================================================================

    @Test
    fun `calculateGasFeeWei should multiply price by limit`() {
        val gasPrice = BigInteger.TEN.pow(9) // 1 Gwei
        val gasLimit = BigInteger.valueOf(21000)
        val gasFee = GasUtils.calculateGasFeeWei(gasPrice, gasLimit)

        assertEquals(BigInteger.valueOf(21000).multiply(BigInteger.TEN.pow(9)), gasFee)
    }

    @Test
    fun `calculateGasFeeWei with zero price should be zero`() {
        val gasPrice = BigInteger.ZERO
        val gasLimit = BigInteger.valueOf(21000)
        val gasFee = GasUtils.calculateGasFeeWei(gasPrice, gasLimit)

        assertEquals(BigInteger.ZERO, gasFee)
    }

    @Test
    fun `calculateGasFeeWei with zero limit should be zero`() {
        val gasPrice = BigInteger.TEN.pow(9)
        val gasLimit = BigInteger.ZERO
        val gasFee = GasUtils.calculateGasFeeWei(gasPrice, gasLimit)

        assertEquals(BigInteger.ZERO, gasFee)
    }

    // =========================================================================
    // Recommended Gas Limit Tests
    // =========================================================================

    @Test
    fun `getRecommendedGasLimit for ETH should return 21000`() {
        val gasLimit = GasUtils.getRecommendedGasLimit(isErc20 = false)
        assertEquals(BigInteger.valueOf(21000), gasLimit)
    }

    @Test
    fun `getRecommendedGasLimit for ERC20 should return 65000`() {
        val gasLimit = GasUtils.getRecommendedGasLimit(isErc20 = true)
        assertEquals(BigInteger.valueOf(65000), gasLimit)
    }
}
