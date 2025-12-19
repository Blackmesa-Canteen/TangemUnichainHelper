package com.example.tangemunichainhelper.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AddressUtils - EIP-55 address validation and checksum.
 */
class AddressUtilsTest {

    // =========================================================================
    // Valid Address Tests
    // =========================================================================

    @Test
    fun `valid lowercase address should pass`() {
        val address = "0xab5801a7d398351b8be11c439e05c5b3259aec9b"
        val result = AddressUtils.validateAddress(address)
        assertTrue("Lowercase address should be valid", result.isValid)
        assertNull(result.error)
        assertNotNull(result.checksumAddress)
    }

    @Test
    fun `valid uppercase address should pass`() {
        val address = "0xAB5801A7D398351B8BE11C439E05C5B3259AEC9B"
        val result = AddressUtils.validateAddress(address)
        assertTrue("Uppercase address should be valid", result.isValid)
        assertNull(result.error)
    }

    @Test
    fun `valid checksum address should pass`() {
        // This is a known valid EIP-55 checksum address
        val address = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        val result = AddressUtils.validateAddress(address)
        assertTrue("Valid checksum address should pass", result.isValid)
        assertNull(result.error)
    }

    @Test
    fun `USDC contract address should be valid`() {
        val address = "0x078D782b760474a361dDA0AF3839290b0EF57AD6"
        val result = AddressUtils.validateAddress(address)
        assertTrue("USDC contract address should be valid", result.isValid)
    }

    // =========================================================================
    // Invalid Address Tests
    // =========================================================================

    @Test
    fun `empty address should fail`() {
        val result = AddressUtils.validateAddress("")
        assertFalse("Empty address should be invalid", result.isValid)
        assertEquals("Address is empty", result.error)
    }

    @Test
    fun `blank address should fail`() {
        val result = AddressUtils.validateAddress("   ")
        assertFalse("Blank address should be invalid", result.isValid)
        assertEquals("Address is empty", result.error)
    }

    @Test
    fun `address without 0x prefix should fail`() {
        val address = "ab5801a7d398351b8be11c439e05c5b3259aec9b"
        val result = AddressUtils.validateAddress(address)
        assertFalse("Address without 0x should be invalid", result.isValid)
        assertEquals("Address must start with 0x", result.error)
    }

    @Test
    fun `address too short should fail`() {
        val address = "0xab5801a7d398351b8be11c439e05c5b3259aec"
        val result = AddressUtils.validateAddress(address)
        assertFalse("Short address should be invalid", result.isValid)
        assertEquals("Address must be 42 characters (0x + 40 hex digits)", result.error)
    }

    @Test
    fun `address too long should fail`() {
        val address = "0xab5801a7d398351b8be11c439e05c5b3259aec9b00"
        val result = AddressUtils.validateAddress(address)
        assertFalse("Long address should be invalid", result.isValid)
        assertEquals("Address must be 42 characters (0x + 40 hex digits)", result.error)
    }

    @Test
    fun `address with invalid characters should fail`() {
        val address = "0xzb5801a7d398351b8be11c439e05c5b3259aec9b"
        val result = AddressUtils.validateAddress(address)
        assertFalse("Address with invalid chars should be invalid", result.isValid)
        assertEquals("Address contains invalid characters", result.error)
    }

    @Test
    fun `address with spaces should fail`() {
        val address = "0xab5801a7d398351b8be11c439e05c5b3259 ec9b"
        val result = AddressUtils.validateAddress(address)
        assertFalse("Address with spaces should be invalid", result.isValid)
    }

    @Test
    fun `invalid checksum address should fail with suggestion`() {
        // This is a deliberately wrong checksum (swapped case on some chars)
        val address = "0xAB5801a7D398351b8bE11C439e05C5B3259aeC9B"
        val result = AddressUtils.validateAddress(address)

        // Should fail because checksum is wrong
        assertFalse("Wrong checksum should be invalid", result.isValid)
        assertNotNull("Should have error message", result.error)
        assertTrue("Error should mention checksum", result.error!!.contains("checksum"))
        assertNotNull("Should suggest correct address", result.suggestedAddress)
    }

    // =========================================================================
    // EIP-55 Checksum Tests
    // =========================================================================

    @Test
    fun `toChecksumAddress should convert lowercase to checksum`() {
        val lowercase = "0xab5801a7d398351b8be11c439e05c5b3259aec9b"
        val checksum = AddressUtils.toChecksumAddress(lowercase)

        // The checksum address should have proper casing
        assertNotEquals("Should not be all lowercase", lowercase, checksum)
        assertEquals("Should still be 42 characters", 42, checksum.length)
        assertTrue("Should start with 0x", checksum.startsWith("0x"))
    }

    @Test
    fun `toChecksumAddress should be idempotent`() {
        val address = "0xab5801a7d398351b8be11c439e05c5b3259aec9b"
        val checksum1 = AddressUtils.toChecksumAddress(address)
        val checksum2 = AddressUtils.toChecksumAddress(checksum1)

        assertEquals("Applying checksum twice should give same result", checksum1, checksum2)
    }

    @Test
    fun `toChecksumAddress known test vectors`() {
        // Test vectors from EIP-55
        val testCases = mapOf(
            "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed" to "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
            "0xfb6916095ca1df60bb79ce92ce3ea74c37c5d359" to "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359"
        )

        for ((input, expected) in testCases) {
            val result = AddressUtils.toChecksumAddress(input)
            assertEquals("Checksum for $input", expected, result)
        }
    }

    // =========================================================================
    // isValidAddressFormat Tests
    // =========================================================================

    @Test
    fun `isValidAddressFormat should accept valid format`() {
        assertTrue(AddressUtils.isValidAddressFormat("0xab5801a7d398351b8be11c439e05c5b3259aec9b"))
        assertTrue(AddressUtils.isValidAddressFormat("0xAB5801A7D398351B8BE11C439E05C5B3259AEC9B"))
        assertTrue(AddressUtils.isValidAddressFormat("0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"))
    }

    @Test
    fun `isValidAddressFormat should reject invalid format`() {
        assertFalse(AddressUtils.isValidAddressFormat(""))
        assertFalse(AddressUtils.isValidAddressFormat("ab5801a7d398351b8be11c439e05c5b3259aec9b"))
        assertFalse(AddressUtils.isValidAddressFormat("0xab5801a7d398351b8be11c439e05c5b3259aec")) // too short
        assertFalse(AddressUtils.isValidAddressFormat("0xzb5801a7d398351b8be11c439e05c5b3259aec9b")) // invalid char
    }
}
