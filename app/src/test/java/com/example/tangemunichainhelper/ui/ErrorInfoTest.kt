package com.example.tangemunichainhelper.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ErrorInfo categorization.
 */
class ErrorInfoTest {

    // =========================================================================
    // Card Error Tests
    // =========================================================================

    @Test
    fun `card scan error should be categorized as CARD`() {
        val error = ErrorInfo.fromMessage("Failed to scan card: User cancelled")

        assertEquals(ErrorType.CARD, error.type)
        assertEquals("Card Error", error.title)
        assertTrue(error.isRetryable)
        assertNotNull(error.suggestion)
        assertTrue(error.suggestion!!.contains("NFC"))
    }

    @Test
    fun `tangem error should be categorized as CARD`() {
        val error = ErrorInfo.fromMessage("Tangem manager not initialized")

        assertEquals(ErrorType.CARD, error.type)
        assertEquals("Card Error", error.title)
    }

    @Test
    fun `NFC error should be categorized as CARD`() {
        val error = ErrorInfo.fromMessage("NFC not available on this device")

        assertEquals(ErrorType.CARD, error.type)
    }

    // =========================================================================
    // Network Error Tests
    // =========================================================================

    @Test
    fun `nonce error should be categorized as NETWORK`() {
        val error = ErrorInfo.fromMessage("Failed to get nonce: Connection timeout")

        assertEquals(ErrorType.NETWORK, error.type)
        assertEquals("Network Error", error.title)
        assertTrue(error.isRetryable)
        assertNotNull(error.suggestion)
        assertTrue(error.suggestion!!.contains("internet"))
        assertNotNull(error.technicalDetails)
    }

    @Test
    fun `gas price error should be categorized as NETWORK`() {
        val error = ErrorInfo.fromMessage("Failed to get gas price: RPC error")

        assertEquals(ErrorType.NETWORK, error.type)
    }

    @Test
    fun `connection error should be categorized as NETWORK`() {
        val error = ErrorInfo.fromMessage("Connection refused by server")

        assertEquals(ErrorType.NETWORK, error.type)
    }

    @Test
    fun `timeout error should be categorized as NETWORK`() {
        val error = ErrorInfo.fromMessage("Request timeout after 30 seconds")

        assertEquals(ErrorType.NETWORK, error.type)
    }

    // =========================================================================
    // Balance Error Tests
    // =========================================================================

    @Test
    fun `insufficient ETH error should be categorized as BALANCE`() {
        val error = ErrorInfo.fromMessage("Insufficient ETH. You need 0.01 ETH but have 0.005 ETH")

        assertEquals(ErrorType.BALANCE, error.type)
        assertEquals("Insufficient Balance", error.title)
        assertFalse(error.isRetryable)
        assertNotNull(error.suggestion)
    }

    @Test
    fun `insufficient gas error should have specific title`() {
        val error = ErrorInfo.fromMessage("Insufficient ETH for gas. You need at least 0.0001 ETH for gas fees.")

        assertEquals(ErrorType.BALANCE, error.type)
        assertEquals("Insufficient Gas", error.title)
        assertTrue(error.suggestion!!.contains("ETH"))
    }

    @Test
    fun `insufficient token balance should be categorized as BALANCE`() {
        val error = ErrorInfo.fromMessage("Insufficient USDC balance. You have 10 USDC.")

        assertEquals(ErrorType.BALANCE, error.type)
    }

    // =========================================================================
    // Validation Error Tests
    // =========================================================================

    @Test
    fun `invalid address should be categorized as VALIDATION`() {
        val error = ErrorInfo.fromMessage("Invalid address format")

        assertEquals(ErrorType.VALIDATION, error.type)
        assertEquals("Invalid Input", error.title)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `checksum error should be categorized as VALIDATION`() {
        val error = ErrorInfo.fromMessage("Invalid address checksum. Did you mean: 0x...")

        assertEquals(ErrorType.VALIDATION, error.type)
    }

    @Test
    fun `invalid amount should be categorized as VALIDATION`() {
        val error = ErrorInfo.fromMessage("Invalid amount")

        assertEquals(ErrorType.VALIDATION, error.type)
    }

    @Test
    fun `empty address should be categorized as VALIDATION`() {
        val error = ErrorInfo.fromMessage("Address is empty")

        assertEquals(ErrorType.VALIDATION, error.type)
    }

    @Test
    fun `amount must be greater than zero should be VALIDATION`() {
        val error = ErrorInfo.fromMessage("Amount must be greater than zero")

        assertEquals(ErrorType.VALIDATION, error.type)
    }

    // =========================================================================
    // Transaction Error Tests
    // =========================================================================

    @Test
    fun `send transaction error should be categorized as TRANSACTION`() {
        // Use a message that doesn't contain "nonce" (which triggers NETWORK)
        val error = ErrorInfo.fromMessage("Failed to send transaction: rejected")

        assertEquals(ErrorType.TRANSACTION, error.type)
        assertEquals("Transaction Failed", error.title)
        assertTrue(error.isRetryable)
        // Message should be cleaned up
        assertFalse(error.message.startsWith("Failed to send transaction:"))
    }

    @Test
    fun `execute transfer error should be categorized as TRANSACTION`() {
        // Use a message without keywords that trigger other categories
        val error = ErrorInfo.fromMessage("Failed to execute transfer: reverted")

        assertEquals(ErrorType.TRANSACTION, error.type)
        assertFalse(error.message.startsWith("Failed to execute transfer:"))
    }

    @Test
    fun `sign transaction error should be categorized as TRANSACTION`() {
        val error = ErrorInfo.fromMessage("Failed to sign transaction: User cancelled")

        assertEquals(ErrorType.TRANSACTION, error.type)
    }

    @Test
    fun `broadcast error should be categorized as TRANSACTION`() {
        val error = ErrorInfo.fromMessage("Failed to broadcast transaction")

        assertEquals(ErrorType.TRANSACTION, error.type)
    }

    // =========================================================================
    // Unknown Error Tests
    // =========================================================================

    @Test
    fun `unknown error should be categorized as UNKNOWN`() {
        val error = ErrorInfo.fromMessage("Something completely unexpected happened")

        assertEquals(ErrorType.UNKNOWN, error.type)
        assertEquals("Error", error.title)
        assertTrue(error.isRetryable)
        assertEquals("Something completely unexpected happened", error.message)
    }

    @Test
    fun `generic error should preserve original message`() {
        val originalMessage = "Some weird error nobody expected"
        val error = ErrorInfo.fromMessage(originalMessage)

        assertEquals(originalMessage, error.message)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `empty error message should be handled`() {
        val error = ErrorInfo.fromMessage("")

        assertEquals(ErrorType.UNKNOWN, error.type)
        assertEquals("", error.message)
    }

    @Test
    fun `case insensitive matching should work`() {
        val error1 = ErrorInfo.fromMessage("FAILED TO SCAN CARD")
        val error2 = ErrorInfo.fromMessage("failed to scan card")
        val error3 = ErrorInfo.fromMessage("Failed To Scan Card")

        assertEquals(ErrorType.CARD, error1.type)
        assertEquals(ErrorType.CARD, error2.type)
        assertEquals(ErrorType.CARD, error3.type)
    }

    @Test
    fun `error with multiple keywords should be categorized correctly`() {
        // Contains both "card" and "transaction" - should prioritize based on order in when clause
        val error = ErrorInfo.fromMessage("Card failed during transaction")

        // "card" comes first in the when clause, so should be CARD
        assertEquals(ErrorType.CARD, error.type)
    }
}
