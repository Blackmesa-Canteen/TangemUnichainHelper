# Tangem Unichain Helper - Developer Guide

This document provides comprehensive documentation for developers working on this project.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [The Tangem Hack Explained](#the-tangem-hack-explained)
4. [Adding New Tokens](#adding-new-tokens)
5. [Adding New Networks](#adding-new-networks)
6. [Key Components](#key-components)
7. [Transaction Flow](#transaction-flow)
8. [Security Considerations](#security-considerations)
9. [Testing](#testing)
10. [Troubleshooting](#troubleshooting)

---

## Project Overview

This Android app enables users to transfer ETH and ERC-20 tokens on **Unichain** (Chain ID: 130) using **Tangem NFC cards**. Since the official Tangem app doesn't support Unichain, this app implements a workaround to make it work.

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
│   ├── AddressUtils.kt      # EIP-55 address validation
│   ├── GasUtils.kt          # Gas formatting and calculations
│   ├── NetworkConstants.kt  # Unichain RPC and config
│   ├── TangemManager.kt     # Tangem SDK integration
│   ├── Token.kt             # Token abstraction & registry
│   └── Web3Manager.kt       # All Web3/blockchain operations
├── ui/
│   ├── MainViewModel.kt     # State management & business logic
│   └── theme/Theme.kt       # Material Design theme
├── MainActivity.kt          # Compose UI
└── TangemUnichainApp.kt     # Application class
```

---

## The Tangem Hack Explained

### The Problem

Tangem SDK is configured for specific chains (Ethereum, etc.) and doesn't support Unichain (Chain ID: 130). When you ask Tangem to sign a transaction, it expects to sign for a known chain.

### The Solution: Legacy Transaction Format

We use **legacy transaction format** (pre-EIP-155) which is chain-agnostic:

```
┌─────────────────────────────────────────────────────────────────┐
│                    HOW THE HACK WORKS                           │
├─────────────────────────────────────────────────────────────────┤
│ 1. Create transaction data (nonce, gasPrice, to, value, data)   │
│                                                                 │
│ 2. Hash with LEGACY format (no chain ID):                       │
│    hash = keccak256(rlp(nonce,gasPrice,gasLimit,to,value,data)) │
│                                                                 │
│ 3. Tangem signs the hash (card doesn't know which chain)        │
│    → Returns 64-byte signature (r, s)                           │
│                                                                 │
│ 4. Find correct recovery ID (0 or 1) by trying both             │
│    → Test which one recovers the correct public key             │
│                                                                 │
│ 5. Broadcast with LEGACY v value:                               │
│    v = 27 + recoveryId  (NOT chainId*2+35!)                     │
│                                                                 │
│ 6. Transaction is valid on ANY EVM chain (including Unichain)   │
├─────────────────────────────────────────────────────────────────┤
│ Trade-off: No replay protection (same tx valid on other chains) │
│ Acceptable for: Fund recovery from unsupported chains           │
└─────────────────────────────────────────────────────────────────┘
```

### Key Code Location

The v value calculation is in `MainViewModel.kt`:

```kotlin
// CRITICAL: Since we sign with LEGACY hash format (no chain ID in hash),
// we MUST use legacy v value (27 or 28), NOT EIP-155 v value.
val vValue = 27 + recoveryId
```

**DO NOT CHANGE THIS** to use EIP-155 format (`chainId * 2 + 35`) unless you also change the hash format.

---

## Adding New Tokens

Adding a new ERC-20 token takes just **2 steps**:

### Step 1: Define the Token

Open `core/Token.kt` and add your token in `TokenRegistry`:

```kotlin
object TokenRegistry {
    // ... existing tokens ...

    // Add your new token:
    val USDT = Token.ERC20(
        symbol = "USDT",
        name = "Tether USD",
        contractAddress = "0x...",  // Find on Unichain explorer
        decimals = 6,               // Usually 6 for stablecoins, 18 for most tokens
        defaultGasLimit = BigInteger.valueOf(65000)  // Optional, 65000 is default
    )
}
```

### Step 2: Add to Token List

In the same file, add your token to `allTokens`:

```kotlin
val allTokens: List<Token> = listOf(
    ETH,
    USDC,
    USDT,  // Add here
)
```

**That's it!** The UI will automatically show the new token.

### Finding Token Information

1. Go to [Uniscan](https://uniscan.xyz)
2. Search for the token name or paste its contract address
3. Find:
   - **Contract Address**: The `0x...` address
   - **Decimals**: Usually shown in token info (6 for USDC/USDT, 18 for most others)

### Example: Adding WETH

```kotlin
val WETH = Token.ERC20(
    symbol = "WETH",
    name = "Wrapped Ether",
    contractAddress = "0x4200000000000000000000000000000000000006",  // Common WETH address
    decimals = 18
)

val allTokens: List<Token> = listOf(ETH, USDC, WETH)
```

---

## Adding New Networks

To support a different EVM chain (not just Unichain):

### Step 1: Update NetworkConstants

Edit `core/NetworkConstants.kt`:

```kotlin
object NetworkConstants {
    // Change these for your target network:
    const val CHAIN_ID = 130L  // e.g., 1 for Ethereum, 137 for Polygon
    const val NETWORK_NAME = "Unichain Mainnet"
    const val RPC_URL = "https://mainnet.unichain.org"
    const val EXPLORER_URL = "https://uniscan.xyz"

    // Update token addresses for the new network
    const val USDC_CONTRACT_ADDRESS = "0x..."
}
```

### Step 2: Update TokenRegistry

Token contract addresses are **different on each chain**. Update all addresses in `Token.kt`:

```kotlin
val USDC = Token.ERC20(
    symbol = "USDC",
    name = "USD Coin",
    contractAddress = "0x...",  // USDC address on YOUR chain
    decimals = 6
)
```

### Note on the Hack

The legacy transaction format works on **any EVM chain**, so the signing logic doesn't need to change. Only the RPC URL and contract addresses need updating.

---

## Key Components

### Token.kt

Defines the token abstraction:

```kotlin
sealed class Token {
    abstract val symbol: String
    abstract val name: String
    abstract val decimals: Int
    abstract val defaultGasLimit: BigInteger

    data object ETH : Token() { ... }
    data class ERC20(...) : Token() { ... }

    fun toSmallestUnit(amount: BigDecimal): BigInteger
    fun fromSmallestUnit(amount: BigInteger): BigDecimal
}
```

### Web3Manager.kt

Handles all blockchain operations:

| Method | Purpose |
|--------|---------|
| `getTokenBalance(address, token)` | Get balance for any token |
| `getAllTokenBalances(address)` | Get all token balances |
| `estimateGasForTransfer(...)` | Estimate gas for any transfer |
| `createTransferTransaction(...)` | Create unsigned transaction |
| `getTransactionHashForTangemSigning(tx)` | Get legacy hash for signing |
| `sendSignedTransaction(signedTx)` | Broadcast to network |

### MainViewModel.kt

Business logic and state management:

| Method | Purpose |
|--------|---------|
| `scanCard()` | Scan Tangem card via NFC |
| `loadBalances()` | Load all token balances |
| `calculateMaxTransferAmount(token)` | Calculate max sendable (with gas reserved) |
| `prepareTransfer(address, amount, token)` | Validate and prepare transfer |
| `executeTransfer()` | Sign with Tangem and broadcast |

### GasUtils.kt

Gas formatting and calculations:

| Method | Purpose |
|--------|---------|
| `formatGasPriceGwei(wei)` | Format gas price for display |
| `formatGasLimit(limit, isErc20)` | Format with context label |
| `formatGasFeeEth(wei)` | Format gas fee in ETH |
| `addGasBuffer(estimate)` | Add 20% safety buffer |

### AddressUtils.kt

Address validation:

| Method | Purpose |
|--------|---------|
| `validateAddress(address)` | Full validation with EIP-55 checksum |
| `toChecksumAddress(address)` | Convert to checksum format |
| `isValidAddressFormat(address)` | Quick format check |

---

## Transaction Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                     TRANSACTION FLOW                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User Input                                                   │
│     └─► Recipient address, amount, token selection               │
│                                                                  │
│  2. Validation (prepareTransfer)                                 │
│     ├─► EIP-55 address checksum                                  │
│     ├─► Self-send prevention                                     │
│     ├─► Amount > 0                                               │
│     └─► Sufficient balance (token + ETH for gas)                 │
│                                                                  │
│  3. Get Network Data                                             │
│     ├─► Nonce (transaction count)                                │
│     ├─► Gas price (current network price)                        │
│     └─► Gas estimate (with 20% buffer)                           │
│                                                                  │
│  4. Create Transaction (createTransferTransaction)               │
│     ├─► For ETH: RawTransaction.createEtherTransaction()         │
│     └─► For ERC-20: RawTransaction with encoded transfer()       │
│                                                                  │
│  5. Generate Hash (getTransactionHashForTangemSigning)           │
│     └─► LEGACY format: keccak256(rlp(nonce,...,data))            │
│                                                                  │
│  6. Sign with Tangem (signTransactionHash)                       │
│     ├─► User taps NFC card                                       │
│     ├─► Card signs hash                                          │
│     └─► Returns 64-byte signature (r, s)                         │
│                                                                  │
│  7. Find Recovery ID (findCorrectRecoveryId)                     │
│     ├─► Try recoveryId = 0, recover public key                   │
│     ├─► Try recoveryId = 1, recover public key                   │
│     └─► Return ID that matches expected public key               │
│                                                                  │
│  8. Encode Signed Transaction                                    │
│     └─► RLP encode with v = 27 + recoveryId (LEGACY)             │
│                                                                  │
│  9. Broadcast (sendSignedTransaction)                            │
│     └─► eth_sendRawTransaction to Unichain RPC                   │
│                                                                  │
│ 10. Success                                                      │
│     ├─► Show transaction hash                                    │
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
| Input Validation | ✅ Good | EIP-55 checksum, self-send prevention |
| Gas Estimation | ✅ Good | 20% buffer prevents failures |

### Known Trade-offs

| Aspect | Status | Notes |
|--------|--------|-------|
| Replay Protection | ⚠️ None | Legacy tx valid on any chain |
| RPC Security | ⚠️ Basic | Uses HTTPS but no cert pinning |
| Logging | ⚠️ Verbose | Disable in production |

### Replay Attack Risk

Since we use legacy transactions, the same signed transaction could theoretically be replayed on other EVM chains if:
1. The sender has funds on another chain
2. The nonce matches
3. The recipient address exists on that chain

**Mitigation**: This app is designed for **fund recovery**, not daily use. Users should transfer all funds in one go.

---

## Testing

### Manual Testing Checklist

- [ ] Scan Tangem card, verify address matches Tangem app
- [ ] Check ETH balance matches explorer
- [ ] Check ERC-20 balances match explorer
- [ ] Test "Max" button for ETH (should reserve gas)
- [ ] Test "Max" button for ERC-20 (should show full balance)
- [ ] Test gas edit UI (change values and verify update)
- [ ] Test small ETH transfer
- [ ] Test small ERC-20 transfer
- [ ] Verify transaction on Uniscan explorer

### Common Test Scenarios

1. **Insufficient gas**: Try ERC-20 transfer with 0 ETH
2. **Invalid address**: Enter malformed address
3. **Wrong checksum**: Enter valid address with wrong case
4. **Self-send**: Try sending to own address
5. **Amount > balance**: Try sending more than available

---

## Troubleshooting

### Transaction Fails with "Invalid Sender"

**Cause**: v value mismatch between signing and verification.

**Solution**: Ensure `vValue = 27 + recoveryId` (legacy format), NOT `chainId * 2 + 35`.

### Transaction Fails with "Insufficient Funds"

**Cause**: Not enough ETH for gas, or trying to send more than balance.

**Solution**: Check ETH balance covers gas. Use "Max" button to auto-calculate.

### Card Scan Fails

**Cause**: NFC not enabled, or card not positioned correctly.

**Solution**:
1. Enable NFC in device settings
2. Hold card flat against phone's NFC area
3. Keep card steady until scan completes

### Balance Shows 0 for New Token

**Cause**: Wrong contract address or decimals.

**Solution**: Verify contract address on Uniscan. Check decimals match.

### "Could Not Determine Recovery ID"

**Cause**: Signature doesn't match expected public key.

**Solution**: This is rare. Try re-scanning the card and signing again.

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

*Last updated: December 2025*
