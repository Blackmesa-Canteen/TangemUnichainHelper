package com.example.tangemunichainhelper.core

import java.math.BigInteger

object NetworkConstants {
    // Unichain Mainnet Configuration
    const val CHAIN_ID = 130L
    const val NETWORK_NAME = "Unichain Mainnet"
    const val RPC_URL = "https://rpc.unichain.org"
    const val EXPLORER_URL = "https://uniscan.xyz"
    const val CURRENCY_SYMBOL = "ETH"

    // Token Contracts
    const val USDC_CONTRACT_ADDRESS = "0x078D782b760474a361dDA0AF3839290b0EF57AD6"

    // Your Wallet Address
    const val WALLET_ADDRESS = "0x5A4dC932a92Eb68529522eA79b566C01515F6436"

    // Gas Configuration
    val DEFAULT_GAS_LIMIT_ETH = BigInteger.valueOf(21000)
    val DEFAULT_GAS_LIMIT_ERC20 = BigInteger.valueOf(65000)

    // ERC-20 ABI for USDC
    val ERC20_TRANSFER_FUNCTION = "transfer(address,uint256)"
    val ERC20_BALANCE_FUNCTION = "balanceOf(address)"
    val ERC20_DECIMALS_FUNCTION = "decimals()"
}