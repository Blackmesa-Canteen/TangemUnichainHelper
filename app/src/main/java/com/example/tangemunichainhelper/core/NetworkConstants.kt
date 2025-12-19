package com.example.tangemunichainhelper.core

import java.math.BigInteger

object NetworkConstants {
    // Unichain Mainnet Configuration
    const val CHAIN_ID = 130L
    const val NETWORK_NAME = "Unichain Mainnet"
    const val EXPLORER_URL = "https://uniscan.xyz"

    // RPC Endpoints (in order of preference)
    // Primary: dRPC (more reliable for production)
    // Fallback: Official public endpoint (rate limited, not for production)
    val RPC_URLS = listOf(
        "https://unichain.drpc.org",
        "https://mainnet.unichain.org"
    )

    // Default RPC (primary)
    const val RPC_URL = "https://unichain.drpc.org"

    // Token Contracts
    const val USDC_CONTRACT_ADDRESS = "0x078D782b760474a361dDA0AF3839290b0EF57AD6"

    // Gas Configuration
    val DEFAULT_GAS_LIMIT_ETH = BigInteger.valueOf(21000)
    val DEFAULT_GAS_LIMIT_ERC20 = BigInteger.valueOf(65000)
}