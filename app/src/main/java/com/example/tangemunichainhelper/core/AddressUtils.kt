package com.example.tangemunichainhelper.core

import org.web3j.crypto.Hash
import org.web3j.crypto.Keys

/**
 * Utility functions for Ethereum address validation.
 */
object AddressUtils {

    /**
     * Validate Ethereum address format.
     * Returns ValidationResult with details.
     */
    fun validateAddress(address: String): AddressValidationResult {
        // Check basic format
        if (address.isBlank()) {
            return AddressValidationResult(
                isValid = false,
                error = "Address is empty"
            )
        }

        // Must start with 0x
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            return AddressValidationResult(
                isValid = false,
                error = "Address must start with 0x"
            )
        }

        // Must be 42 characters (0x + 40 hex chars)
        if (address.length != 42) {
            return AddressValidationResult(
                isValid = false,
                error = "Address must be 42 characters (0x + 40 hex digits)"
            )
        }

        // Must be valid hex
        val hexPart = address.substring(2)
        if (!hexPart.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return AddressValidationResult(
                isValid = false,
                error = "Address contains invalid characters"
            )
        }

        // Check EIP-55 checksum if address has mixed case
        val hasUpperCase = hexPart.any { it.isUpperCase() }
        val hasLowerCase = hexPart.any { it.isLowerCase() }
        val isMixedCase = hasUpperCase && hasLowerCase

        if (isMixedCase) {
            // Address has mixed case, validate EIP-55 checksum
            val checksumAddress = toChecksumAddress(address)
            if (address != checksumAddress) {
                return AddressValidationResult(
                    isValid = false,
                    error = "Invalid address checksum. Did you mean: $checksumAddress",
                    suggestedAddress = checksumAddress
                )
            }
        }

        return AddressValidationResult(
            isValid = true,
            checksumAddress = toChecksumAddress(address)
        )
    }

    /**
     * Convert address to EIP-55 checksum format.
     * https://eips.ethereum.org/EIPS/eip-55
     */
    fun toChecksumAddress(address: String): String {
        val cleanAddress = address.lowercase().removePrefix("0x")
        val hash = Hash.sha3String(cleanAddress)

        val checksumAddress = StringBuilder("0x")
        for (i in cleanAddress.indices) {
            val c = cleanAddress[i]
            if (c in '0'..'9') {
                checksumAddress.append(c)
            } else {
                // If the ith digit of the hash is >= 8, uppercase the character
                val hashChar = hash[i + 2] // Skip "0x" prefix
                val hashValue = if (hashChar.isDigit()) {
                    hashChar.digitToInt()
                } else {
                    hashChar.lowercaseChar() - 'a' + 10
                }
                checksumAddress.append(if (hashValue >= 8) c.uppercaseChar() else c)
            }
        }

        return checksumAddress.toString()
    }

    /**
     * Quick check if address format is valid (without checksum validation).
     */
    fun isValidAddressFormat(address: String): Boolean {
        return address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
    }
}

data class AddressValidationResult(
    val isValid: Boolean,
    val error: String? = null,
    val checksumAddress: String? = null,
    val suggestedAddress: String? = null
)
