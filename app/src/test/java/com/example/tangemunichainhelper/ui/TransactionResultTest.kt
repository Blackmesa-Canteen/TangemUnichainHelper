package com.example.tangemunichainhelper.ui

import com.example.tangemunichainhelper.core.Chain
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Unit tests for TransactionResult.
 */
class TransactionResultTest {

    private fun createTestResult(
        txHash: String = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
        recipientAddress: String = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
    ): TransactionResult {
        return TransactionResult(
            txHash = txHash,
            amount = BigDecimal("1.5"),
            tokenSymbol = "ETH",
            recipientAddress = recipientAddress,
            fromAddress = "0x1234567890123456789012345678901234567890",
            gasFee = BigDecimal("0.000021"),
            gasPrice = BigInteger.TEN.pow(9),
            gasLimit = BigInteger.valueOf(21000),
            nonce = BigInteger.valueOf(42),
            networkName = Chain.Unichain.name,
            explorerUrl = "${Chain.Unichain.explorerUrl}/tx/$txHash"
        )
    }

    // =========================================================================
    // Short Transaction Hash Tests
    // =========================================================================

    @Test
    fun `shortTxHash should shorten long hash`() {
        val result = createTestResult(
            txHash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        )

        assertEquals("0x12345678...90abcdef", result.shortTxHash)
    }

    @Test
    fun `shortTxHash should preserve short hash`() {
        val shortHash = "0x1234567890"
        val result = createTestResult(txHash = shortHash)

        assertEquals(shortHash, result.shortTxHash)
    }

    @Test
    fun `shortTxHash should handle exact 20 character boundary`() {
        val hash20 = "0x123456789012345678"
        val result = createTestResult(txHash = hash20)

        assertEquals(hash20, result.shortTxHash)
    }

    @Test
    fun `shortTxHash should handle 21 character hash`() {
        val hash21 = "0x1234567890123456789"
        val result = createTestResult(txHash = hash21)

        // Should be shortened since > 20
        assertTrue(result.shortTxHash.contains("..."))
    }

    // =========================================================================
    // Short Recipient Address Tests
    // =========================================================================

    @Test
    fun `shortRecipientAddress should shorten long address`() {
        val result = createTestResult(
            recipientAddress = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        )
        // Takes first 8 chars and last 6 chars
        assertEquals("0xAb5801...9aeC9B", result.shortRecipientAddress)
    }

    @Test
    fun `shortRecipientAddress should preserve short address`() {
        val shortAddress = "0x12345678"
        val result = createTestResult(recipientAddress = shortAddress)

        assertEquals(shortAddress, result.shortRecipientAddress)
    }

    @Test
    fun `shortRecipientAddress should handle exact 16 character boundary`() {
        val addr16 = "0x12345678901234"
        val result = createTestResult(recipientAddress = addr16)

        assertEquals(addr16, result.shortRecipientAddress)
    }

    // =========================================================================
    // Property Tests
    // =========================================================================

    @Test
    fun `result should preserve all properties`() {
        val txHash = "0xabc123"
        val amount = BigDecimal("100.5")
        val tokenSymbol = "USDC"
        val recipientAddress = "0xrecipient"
        val fromAddress = "0xfrom"
        val gasFee = BigDecimal("0.001")
        val gasPrice = BigInteger.valueOf(5000000000)
        val gasLimit = BigInteger.valueOf(65000)
        val nonce = BigInteger.valueOf(10)
        val networkName = "Unichain Mainnet"
        val explorerUrl = "https://example.com/tx/0xabc123"

        val result = TransactionResult(
            txHash = txHash,
            amount = amount,
            tokenSymbol = tokenSymbol,
            recipientAddress = recipientAddress,
            fromAddress = fromAddress,
            gasFee = gasFee,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            nonce = nonce,
            networkName = networkName,
            explorerUrl = explorerUrl
        )

        assertEquals(txHash, result.txHash)
        assertEquals(amount, result.amount)
        assertEquals(tokenSymbol, result.tokenSymbol)
        assertEquals(recipientAddress, result.recipientAddress)
        assertEquals(fromAddress, result.fromAddress)
        assertEquals(gasFee, result.gasFee)
        assertEquals(gasPrice, result.gasPrice)
        assertEquals(gasLimit, result.gasLimit)
        assertEquals(nonce, result.nonce)
        assertEquals(networkName, result.networkName)
        assertEquals(explorerUrl, result.explorerUrl)
    }

    @Test
    fun `result should have correct network name from chain`() {
        val result = createTestResult()

        assertEquals(Chain.Unichain.name, result.networkName)
    }

    @Test
    fun `result should have timestamp`() {
        val before = System.currentTimeMillis()
        val result = createTestResult()
        val after = System.currentTimeMillis()

        assertTrue(result.timestamp >= before)
        assertTrue(result.timestamp <= after)
    }

    @Test
    fun `custom network name should be preserved`() {
        val result = TransactionResult(
            txHash = "0x123",
            amount = BigDecimal.ONE,
            tokenSymbol = "ETH",
            recipientAddress = "0xrecipient",
            fromAddress = "0xfrom",
            gasFee = BigDecimal.ZERO,
            gasPrice = BigInteger.ONE,
            gasLimit = BigInteger.ONE,
            nonce = BigInteger.ZERO,
            networkName = "Custom Network",
            explorerUrl = "https://example.com"
        )

        assertEquals("Custom Network", result.networkName)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `empty txHash should work`() {
        val result = createTestResult(txHash = "")

        assertEquals("", result.shortTxHash)
    }

    @Test
    fun `empty recipientAddress should work`() {
        val result = createTestResult(recipientAddress = "")

        assertEquals("", result.shortRecipientAddress)
    }

    @Test
    fun `zero amount should work`() {
        val result = TransactionResult(
            txHash = "0x123",
            amount = BigDecimal.ZERO,
            tokenSymbol = "ETH",
            recipientAddress = "0xrecipient",
            fromAddress = "0xfrom",
            gasFee = BigDecimal.ZERO,
            gasPrice = BigInteger.ZERO,
            gasLimit = BigInteger.ZERO,
            nonce = BigInteger.ZERO,
            networkName = "Unichain Mainnet",
            explorerUrl = ""
        )

        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount))
    }

    @Test
    fun `large nonce should work`() {
        val largeNonce = BigInteger.valueOf(Long.MAX_VALUE)
        val result = TransactionResult(
            txHash = "0x123",
            amount = BigDecimal.ONE,
            tokenSymbol = "ETH",
            recipientAddress = "0xrecipient",
            fromAddress = "0xfrom",
            gasFee = BigDecimal.ZERO,
            gasPrice = BigInteger.ONE,
            gasLimit = BigInteger.ONE,
            nonce = largeNonce,
            networkName = "Unichain Mainnet",
            explorerUrl = ""
        )

        assertEquals(largeNonce, result.nonce)
    }
}
