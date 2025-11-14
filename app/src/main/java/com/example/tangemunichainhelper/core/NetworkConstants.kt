package com.example.tangemunichainhelper.core

import java.math.BigInteger

object NetworkConstants {
    // Unichain Mainnet Configuration
    const val CHAIN_ID = 130L
    const val NETWORK_NAME = "Unichain Mainnet"
    const val RPC_URL = "https://mainnet.unichain.org"
    const val EXPLORER_URL = "https://uniscan.xyz"

    // Token Contracts
    const val USDC_CONTRACT_ADDRESS = "0x078D782b760474a361dDA0AF3839290b0EF57AD6"

    // Gas Configuration
    val DEFAULT_GAS_LIMIT_ETH = BigInteger.valueOf(21000)
    val DEFAULT_GAS_LIMIT_ERC20 = BigInteger.valueOf(65000)
}