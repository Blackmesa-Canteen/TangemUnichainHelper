# Tangem Unichain Helper - Developer Guide

This document provides comprehensive documentation for developers working on this project.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [The Tangem Hack Explained](#the-tangem-hack-explained)
4. [Multi-Chain Support](#multi-chain-support)
5. [Adding New Chains](#adding-new-chains)
6. [Adding New Tokens](#adding-new-tokens)
7. [Key Components](#key-components)
8. [Transaction Flow](#transaction-flow)
9. [Security Considerations](#security-considerations)
10. [Testing](#testing)
11. [Troubleshooting](#troubleshooting)

---

## Project Overview

This Android app enables users to transfer ETH and ERC-20 tokens on **any EVM chain** using **Tangem NFC cards**. The default chain is **Unichain** (Chain ID: 130), but the architecture supports adding any EVM chain.

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Blockchain**: Web3j library
- **NFC**: Tangem SDK
- **Architecture**: MVVM with StateFlow

---

## Architecture

```
app/src/main/java/com/example/tangemunichainhelper/
├── core/
│   ├── AddressUtils.kt          # EIP-55 address validation
│   ├── Chain.kt                 # Chain sealed class + ChainRegistry
│   ├── GasUtils.kt              # Gas formatting and calculations
│   ├── NetworkConstants.kt      # (Deprecated) Legacy constants
│   ├── TangemManager.kt         # Tangem SDK integration
│   ├── Token.kt                 # Token sealed class + TokenRegistry
│   ├── TokenContractRegistry.kt # Chain-specific token addresses
│   └── Web3Manager.kt           # Chain-aware blockchain operations
├── ui/
│   ├── MainViewModel.kt         # State management & business logic
│   └── theme/Theme.kt           # Material Design theme
├── MainActivity.kt              # Compose UI with ChainSelector
└── TangemUnichainApp.kt         # Application class
```

### Multi-Chain Data Model

```
Chain (sealed class)
├── Unichain (data object)     → chainId=130, default
├── Sepolia (data object)      → chainId=11155111, testnet
└── Custom (data class)        → For developer-defined chains

Token (sealed class)
├── Native (data object)       → ETH on all chains
└── ERC20 (data class)         → Symbol, name, decimals (no address)

TokenContractRegistry
└── Maps (Token, Chain) → contract address
```

---

## How Tangem Signing Works with Any EVM Chain

### The Key Insight

Tangem cards **don't care about chains** — they just sign whatever 32-byte hash you give them. We leverage this to sign for any EVM chain by including the correct chain ID in the EIP-155 hash:

```
┌─────────────────────────────────────────────────────────────────┐
│              HOW TANGEM SIGNS ANY EVM TRANSACTION               │
├─────────────────────────────────────────────────────────────────┤
│ 1. Create transaction data (nonce, gasPrice, to, value, data)   │
│                                                                 │
│ 2. Hash with EIP-155 format (includes chain ID):                │
│    hash = keccak256(rlp(nonce,gasPrice,gasLimit,to,value,       │
│                         data,chainId,0,0))                      │
│    Examples:                                                    │
│      • Unichain: chainId = 130                                  │
│      • Ethereum: chainId = 1                                    │
│      • Polygon: chainId = 137                                   │
│                                                                 │
│ 3. Tangem signs the hash (card just signs 32 bytes)             │
│    → Returns 64-byte signature (r, s)                           │
│                                                                 │
│ 4. Find correct recovery ID (0 or 1) by trying both             │
│    → Test which one recovers the correct public key             │
│                                                                 │
│ 5. Encode with EIP-155 v value:                                 │
│    v = chainId * 2 + 35 + recoveryId                            │
│    Examples:                                                    │
│      • Unichain: v = 130 * 2 + 35 + recoveryId = 295 or 296     │
│      • Ethereum: v = 1 * 2 + 35 + recoveryId = 37 or 38         │
│                                                                 │
│ 6. Broadcast to the chain's RPC                                 │
│    → Transaction is replay-protected for that specific chain    │
├─────────────────────────────────────────────────────────────────┤
│ ✅ Works with ANY EVM chain by changing chain ID and RPC        │
│ ✅ Replay Protection: Transaction only valid on target chain    │
└─────────────────────────────────────────────────────────────────┘
```

### Key Code Locations

**Chain-aware hash generation** (`Web3Manager.kt`):
```kotlin
fun getTransactionHashForTangemSigning(rawTransaction: RawTransaction): ByteArray {
    // Uses current chain's ID for EIP-155 encoding
    val encoded = TransactionEncoder.encode(rawTransaction, _chain.chainId)
    return Hash.sha3(encoded)
}
```

**v Value Calculation** (`MainViewModel.kt`):
```kotlin
val chainId = web3Manager.currentChain.chainId
val vValue = chainId * 2 + 35 + recoveryId
```

---

## Multi-Chain Support

### Shipped Chains

This app ships with two chains configured:

| Chain | Chain ID | Type | Native | Purpose |
|-------|----------|------|--------|---------|
| Unichain | 130 | Mainnet (default) | ETH | Primary chain for production use |
| Sepolia | 11155111 | Testnet | ETH | Development and testing |

### Shipped Tokens (Unichain only)

| Token | Contract Address | Verified Source |
|-------|------------------|-----------------|
| USDC | `0x078D782b760474a361dDA0AF3839290b0EF57AD6` | [Unichain Docs](https://docs.unichain.org/docs/building-on-unichain/transfer-usdc) |
| USDT | `0x9151434b16b9763660705744891fa906f660ecc5` | [USDT0 - LayerZero](https://zapper.xyz/token/unichain/0x9151434b16b9763660705744891fa906f660ecc5) |

**Note**: Sepolia is a testnet with no production tokens configured.

---

## Adding New Chains

Developers can add support for any EVM chain in 3 steps:

### Step 1: Define the Chain

Open `core/Chain.kt` and add your chain:

**Option A: Add as data object (recommended for well-known chains)**
```kotlin
sealed class Chain {
    // ... existing chains ...

    /**
     * Ethereum Mainnet
     * Chain ID: 1
     */
    data object Ethereum : Chain() {
        override val chainId: Long = 1L
        override val name: String = "Ethereum Mainnet"
        override val shortName: String = "Ethereum"
        override val nativeCurrencySymbol: String = "ETH"
        override val explorerUrl: String = "https://etherscan.io"
        override val rpcUrls: List<String> = listOf(
            "https://eth.drpc.org",
            "https://ethereum-rpc.publicnode.com"
        )
        override val isTestnet: Boolean = false
    }
}
```

**Option B: Use Custom data class**
```kotlin
val polygon = Chain.Custom(
    chainId = 137L,
    name = "Polygon Mainnet",
    shortName = "Polygon",
    nativeCurrencySymbol = "MATIC",
    explorerUrl = "https://polygonscan.com",
    rpcUrls = listOf("https://polygon-rpc.com"),
    isTestnet = false
)
```

### Step 2: Register in ChainRegistry

Add your chain to the `allChains` list in `ChainRegistry`:

```kotlin
object ChainRegistry {
    val allChains: List<Chain> = listOf(
        Chain.Unichain,
        Chain.Sepolia,
        Chain.Ethereum,  // Add your chain here
    )
}
```

### Step 3: Add Token Contracts (Optional)

If your chain has tokens, add their contract addresses in `TokenContractRegistry.kt`:

```kotlin
private val contracts: Map<Long, Map<String, String>> = mapOf(
    // Existing Unichain tokens...
    130L to mapOf(
        "USDC" to "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
        "USDT" to "0x9151434b16b9763660705744891fa906f660ecc5"
    ),

    // Add your chain's tokens:
    1L to mapOf(  // Ethereum Mainnet
        "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        "USDT" to "0xdAC17F958D2ee523a2206206994597C13D831ec7"
    )
)
```

### Security Warning

**ALWAYS verify chain IDs and contract addresses from official sources**:
- Chain IDs: [chainlist.org](https://chainlist.org)
- Token contracts: Official project documentation or block explorers
- Wrong addresses can result in **permanent loss of funds**

---

## Adding New Tokens

Adding a new ERC-20 token takes 2-3 steps:

### Step 1: Define the Token

Open `core/Token.kt` and add your token in `TokenRegistry`:

```kotlin
object TokenRegistry {
    // ... existing tokens ...

    val WETH = Token.ERC20(
        symbol = "WETH",
        name = "Wrapped Ether",
        decimals = 18  // Most tokens use 18, stablecoins often use 6
    )

    // Add to allTokens list:
    val allTokens: List<Token> = listOf(
        Native,
        USDC,
        USDT,
        WETH,  // Add here
    )
}
```

### Step 2: Add Contract Addresses

Open `core/TokenContractRegistry.kt` and add the token's contract address for each chain:

```kotlin
private val contracts: Map<Long, Map<String, String>> = mapOf(
    130L to mapOf(  // Unichain
        "USDC" to "0x078D782b760474a361dDA0AF3839290b0EF57AD6",
        "USDT" to "0x9151434b16b9763660705744891fa906f660ecc5",
        "WETH" to "0x..."  // Add WETH address for Unichain
    ),

    // Add for other chains you support:
    1L to mapOf(  // Ethereum
        "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        "WETH" to "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
    )
)
```

**That's it!** The UI will automatically show the token on chains where it's configured.

### Finding Token Information

1. Go to the chain's block explorer (e.g., [Uniscan](https://uniscan.xyz))
2. Search for the token or paste its contract address
3. Find:
   - **Contract Address**: The `0x...` address
   - **Decimals**: Usually 6 for stablecoins, 18 for most others

---

## Key Components

### Chain.kt

Defines the chain abstraction:

```kotlin
sealed class Chain {
    abstract val chainId: Long
    abstract val name: String
    abstract val nativeCurrencySymbol: String
    abstract val rpcUrls: List<String>
    abstract val explorerUrl: String
    abstract val isTestnet: Boolean

    data object Unichain : Chain() { ... }
    data object Sepolia : Chain() { ... }
    data class Custom(...) : Chain()

    fun txExplorerUrl(txHash: String): String
    fun addressExplorerUrl(address: String): String
}
```

### Token.kt

Defines the token abstraction (chain-agnostic):

```kotlin
sealed class Token {
    abstract val symbol: String
    abstract val name: String
    abstract val decimals: Int
    abstract val defaultGasLimit: BigInteger

    data object Native : Token() { ... }
    data class ERC20(symbol, name, decimals) : Token()

    fun toSmallestUnit(amount: BigDecimal): BigInteger
    fun fromSmallestUnit(amount: BigInteger): BigDecimal
}
```

### TokenContractRegistry.kt

Maps tokens to chain-specific contract addresses:

```kotlin
object TokenContractRegistry {
    fun getContractAddress(token: Token.ERC20, chain: Chain): String?
    fun getTokensForChain(chain: Chain): List<Token>
    fun isTokenAvailable(token: Token, chain: Chain): Boolean
    fun findByContractAddress(address: String, chain: Chain): Token.ERC20?
}
```

### Web3Manager.kt

Handles all chain-aware blockchain operations:

| Method | Purpose |
|--------|---------|
| `switchChain(chain)` | Switch to a different chain |
| `getNativeBalance(address)` | Get native currency balance |
| `getErc20Balance(address, token)` | Get ERC-20 token balance |
| `getAllTokenBalances(address)` | Get all available token balances |
| `estimateGasForTransfer(...)` | Estimate gas for transfer |
| `createTransferTransaction(...)` | Create unsigned transaction |
| `getTransactionHashForTangemSigning(tx)` | Get EIP-155 hash for signing |
| `sendSignedTransaction(signedTx)` | Broadcast to current chain |

### MainViewModel.kt

Business logic and state management:

| Method | Purpose |
|--------|---------|
| `selectChain(chain)` | Switch chain and reload balances |
| `scanCard()` | Scan Tangem card via NFC |
| `loadBalances()` | Load all token balances for current chain |
| `calculateMaxTransferAmount(token)` | Calculate max sendable |
| `prepareTransfer(address, amount, token)` | Validate and prepare transfer |
| `executeTransfer()` | Sign with Tangem and broadcast |

---

## Transaction Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                     TRANSACTION FLOW                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User selects chain (via ChainSelector dropdown)              │
│     └─► Web3Manager switches RPC connection                      │
│                                                                  │
│  2. User Input                                                   │
│     └─► Recipient address, amount, token selection               │
│                                                                  │
│  3. Validation (prepareTransfer)                                 │
│     ├─► EIP-55 address checksum                                  │
│     ├─► Self-send prevention                                     │
│     ├─► Amount > 0                                               │
│     └─► Sufficient balance (token + native for gas)              │
│                                                                  │
│  4. Get Network Data (chain-aware)                               │
│     ├─► Nonce (from current chain's RPC)                         │
│     ├─► Gas price (from current chain)                           │
│     └─► Gas estimate (with 20% buffer)                           │
│                                                                  │
│  5. Create Transaction (with chain's token contract)             │
│     ├─► For native: RawTransaction.createEtherTransaction()      │
│     └─► For ERC-20: Uses TokenContractRegistry for address       │
│                                                                  │
│  6. Generate Hash (chain-specific EIP-155)                       │
│     └─► hash = keccak256(rlp(...,chain.chainId,0,0))             │
│                                                                  │
│  7. Sign with Tangem (chain-agnostic)                            │
│     └─► Card signs 32-byte hash, returns (r, s)                  │
│                                                                  │
│  8. Find Recovery ID & Encode                                    │
│     └─► v = chain.chainId * 2 + 35 + recoveryId                  │
│                                                                  │
│  9. Broadcast (to current chain's RPC)                           │
│                                                                  │
│ 10. Success                                                      │
│     ├─► Show transaction hash with chain's explorer link         │
│     └─► Refresh balances                                         │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Security Considerations

### What's Secure

| Aspect | Status | Notes |
|--------|--------|-------|
| Private Keys | ✅ Secure | Never leave Tangem card |
| Signing | ✅ Secure | Happens in card's secure element |
| Replay Protection | ✅ Good | EIP-155 makes transactions chain-specific |
| Input Validation | ✅ Good | EIP-55 checksum, self-send prevention |

### Contract Address Security

**Critical**: Wrong token contract addresses can result in lost funds.

- Only Unichain token addresses are shipped (verified from official sources)
- Developers adding other chains MUST verify addresses from official documentation
- Consider adding a warning UI when using unverified custom chains

### Known Trade-offs

| Aspect | Status | Notes |
|--------|--------|-------|
| RPC Security | ⚠️ Basic | Uses HTTPS but no cert pinning |
| Logging | ✅ Fixed | Debug logging disabled in release builds |

---

## Testing

### Unit Tests

The project includes comprehensive unit tests:

- `ChainTest.kt` - Chain sealed class and ChainRegistry
- `TokenContractRegistryTest.kt` - Token-to-chain mappings
- `TokenTest.kt` - Token conversion and registry
- `AddressUtilsTest.kt` - EIP-55 validation
- `GasUtilsTest.kt` - Gas calculations
- `ErrorInfoTest.kt` - Error categorization
- `TransactionResultTest.kt` - Transaction display

Run tests: `./gradlew test`

### Manual Testing Checklist

- [ ] Switch between chains, verify balances update
- [ ] Scan Tangem card, verify address matches Tangem app
- [ ] Check native balance matches explorer
- [ ] Check ERC-20 balances match explorer
- [ ] Test "Max" button for native currency (should reserve gas)
- [ ] Test "Max" button for ERC-20 (should show full balance)
- [ ] Test small native transfer
- [ ] Test small ERC-20 transfer
- [ ] Verify transaction on chain's explorer

---

## Troubleshooting

### Transaction Fails with "Invalid Sender"

**Cause**: v value or hash format mismatch with chain ID.

**Solution**: Ensure hash and v value use the same chain ID:
- Hash: `TransactionEncoder.encode(rawTransaction, chain.chainId)`
- v value: `chain.chainId * 2 + 35 + recoveryId`

### Transaction Fails with "only replay-protected transactions allowed"

**Cause**: Using legacy format instead of EIP-155.

**Solution**: The RPC requires EIP-155 transactions. Verify chain ID is included in hash.

### Token Not Showing for a Chain

**Cause**: Token not configured for that chain in TokenContractRegistry.

**Solution**: Add the token's contract address for the chain in `TokenContractRegistry.kt`.

### Balance Shows 0 for New Token

**Cause**: Wrong contract address or decimals.

**Solution**: Verify contract address on the chain's block explorer. Check decimals match.

---

## Code Style

- Use Kotlin idioms (when expressions, extension functions)
- Prefer `Result<T>` for functions that can fail
- Use `StateFlow` for UI state
- Add KDoc comments for public APIs
- Log with Timber (debug only, redact in production)

---

## Contributing

1. Create feature branch from `main`
2. Add tests for new features
3. Update this documentation
4. Submit PR with clear description

---

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

---

*Last updated: December 2024*
