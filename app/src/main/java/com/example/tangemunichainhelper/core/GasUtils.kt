package com.example.tangemunichainhelper.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Utility functions for gas calculations and formatting.
 * Common practices from popular wallets like MetaMask, Trust Wallet, etc.
 */
object GasUtils {

    // Gas buffer multiplier (20% extra to prevent out-of-gas failures)
    private const val GAS_BUFFER_MULTIPLIER = 1.2

    /**
     * Add a safety buffer to gas limit estimate.
     * Common practice: add 10-20% buffer to prevent transaction failures.
     */
    fun addGasBuffer(estimatedGas: BigInteger): BigInteger {
        return estimatedGas
            .toBigDecimal()
            .multiply(BigDecimal.valueOf(GAS_BUFFER_MULTIPLIER))
            .setScale(0, RoundingMode.UP)
            .toBigInteger()
    }

    /**
     * Format gas price from Wei to Gwei with appropriate precision.
     * Shows up to 4 decimal places, strips trailing zeros.
     */
    fun formatGasPriceGwei(gasPriceWei: BigInteger): String {
        val gwei = BigDecimal(gasPriceWei)
            .divide(BigDecimal.TEN.pow(9), 9, RoundingMode.HALF_UP)

        return when {
            gwei >= BigDecimal.ONE -> gwei.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            gwei >= BigDecimal("0.001") -> gwei.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            else -> gwei.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }

    /**
     * Format gas limit with thousands separator and context label.
     */
    fun formatGasLimit(gasLimit: BigInteger, isErc20: Boolean): String {
        val formatter = DecimalFormat("#,###")
        val formatted = formatter.format(gasLimit)

        val label = when {
            gasLimit <= BigInteger.valueOf(21000) -> "standard"
            isErc20 -> "token transfer"
            else -> "contract call"
        }

        return "$formatted ($label)"
    }

    /**
     * Format gas limit for editing (plain number without label).
     */
    fun formatGasLimitPlain(gasLimit: BigInteger): String {
        val formatter = DecimalFormat("#,###")
        return formatter.format(gasLimit)
    }

    /**
     * Format gas fee in ETH with appropriate precision.
     * For very small amounts, shows scientific notation or "< 0.0001 ETH".
     */
    fun formatGasFeeEth(gasFeeWei: BigInteger): String {
        val eth = BigDecimal(gasFeeWei)
            .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.HALF_UP)

        return when {
            eth >= BigDecimal("0.01") -> {
                // >= 0.01 ETH: show 4 decimals
                "${eth.setScale(4, RoundingMode.UP).stripTrailingZeros().toPlainString()} ETH"
            }
            eth >= BigDecimal("0.0001") -> {
                // >= 0.0001 ETH: show 6 decimals
                "${eth.setScale(6, RoundingMode.UP).stripTrailingZeros().toPlainString()} ETH"
            }
            eth >= BigDecimal("0.00000001") -> {
                // >= 0.00000001 ETH: show 8 decimals
                "${eth.setScale(8, RoundingMode.UP).stripTrailingZeros().toPlainString()} ETH"
            }
            eth > BigDecimal.ZERO -> {
                // Very small: show "< 0.00000001 ETH"
                "< 0.00000001 ETH"
            }
            else -> "0 ETH"
        }
    }

    /**
     * Calculate gas fee in Wei.
     */
    fun calculateGasFeeWei(gasPrice: BigInteger, gasLimit: BigInteger): BigInteger {
        return gasPrice.multiply(gasLimit)
    }

    /**
     * Get recommended gas limit for different transaction types.
     */
    fun getRecommendedGasLimit(isErc20: Boolean): BigInteger {
        return if (isErc20) {
            // ERC-20 transfers typically use 50,000-70,000 gas
            // We use 65,000 as a safe default with buffer already included
            NetworkConstants.DEFAULT_GAS_LIMIT_ERC20
        } else {
            // ETH transfers always use exactly 21,000 gas
            NetworkConstants.DEFAULT_GAS_LIMIT_ETH
        }
    }
}
