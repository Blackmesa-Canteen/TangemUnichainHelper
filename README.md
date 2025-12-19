# Tangem Unichain Helper

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An Android app that enables **Tangem NFC card** users to transfer ETH and ERC-20 tokens on **Unichain** (Chain ID: 130) — a network not officially supported by the Tangem app.

## Why This Exists

The official Tangem app doesn't support Unichain. If you have funds stuck on Unichain that were sent to your Tangem wallet address, this app lets you recover them using your Tangem card.

**Key Features:**
- Transfer ETH and ERC-20 tokens (USDC, etc.) on Unichain
- Scan any Tangem card via NFC
- View token balances
- Automatic gas estimation with manual editing
- Max button with automatic gas reservation
- EIP-55 address checksum validation
- Easy to extend with new tokens

## Security

- **Private keys never leave your Tangem card** — all signing happens in the card's secure element
- No seed phrases, no key exports
- Transaction details shown before signing
- Open source — audit the code yourself

## Screenshots

<!-- Add screenshots here -->
| Scan Card | View Balance | Send Transaction |
|:---------:|:------------:|:----------------:|
| <!-- screenshot --> | <!-- screenshot --> | <!-- screenshot --> |

## Quick Start

### Requirements

- Android device with NFC
- Android 8.0+ (API 26)
- Tangem card

### Installation

#### Option 1: Download APK (Easiest)
1. Go to [Releases](../../releases)
2. Download the latest `app-release.apk`
3. Install on your Android device
4. Allow "Install from unknown sources" if prompted

#### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/996Worker/tangem-unichain-helper.git
cd tangem-unichain-helper

# Build debug APK
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Usage

1. **Scan Card** — Tap "Scan Card" and hold your Tangem card to the phone
2. **View Balances** — ETH and token balances load automatically
3. **Send Tokens** — Enter recipient address, amount, select token
4. **Review & Sign** — Check gas fees, tap your card to sign

## Supported Tokens

| Token | Contract | Decimals |
|-------|----------|----------|
| ETH | Native | 18 |
| USDC | `0x078D782b760474a361dDA0AF3839290b0EF57AD6` | 6 |

Want to add more tokens? See [Adding New Tokens](#adding-new-tokens).

## How It Works

Since Tangem SDK doesn't support Unichain, we use a **legacy transaction format** that's chain-agnostic:

```
1. Create transaction (nonce, gasPrice, to, value, data)
2. Hash WITHOUT chain ID (legacy format)
3. Tangem signs the hash (card doesn't know which chain)
4. Encode with legacy v value (27 or 28)
5. Broadcast to Unichain RPC
```

This works because legacy transactions are valid on any EVM chain. See [DEVELOPER.md](DEVELOPER.md) for technical details.

## Adding New Tokens

Adding a token takes 2 lines of code:

```kotlin
// In app/src/main/java/.../core/Token.kt

// 1. Define the token
val USDT = Token.ERC20(
    symbol = "USDT",
    name = "Tether USD",
    contractAddress = "0x...",  // Find on uniscan.xyz
    decimals = 6
)

// 2. Add to list
val allTokens = listOf(ETH, USDC, USDT)
```

See [DEVELOPER.md](DEVELOPER.md) for detailed instructions.

## Configuration

### Network Settings

Edit `core/NetworkConstants.kt` to change network:

```kotlin
object NetworkConstants {
    const val CHAIN_ID = 130L
    const val RPC_URL = "https://mainnet.unichain.org"
    const val EXPLORER_URL = "https://uniscan.xyz"
}
```

## Project Structure

```
app/src/main/java/com/example/tangemunichainhelper/
├── core/
│   ├── AddressUtils.kt      # EIP-55 address validation
│   ├── GasUtils.kt          # Gas formatting
│   ├── NetworkConstants.kt  # Network config
│   ├── TangemManager.kt     # Tangem SDK wrapper
│   ├── Token.kt             # Token abstraction
│   └── Web3Manager.kt       # Blockchain operations
├── ui/
│   ├── MainViewModel.kt     # State management
│   └── theme/Theme.kt       # Material theme
├── MainActivity.kt          # Compose UI
└── TangemUnichainApp.kt     # Application class
```

## Troubleshooting

### NFC Not Working
- Enable NFC in device settings
- Remove thick phone case
- Hold card flat against phone back
- Try different positions (NFC location varies by phone)

### Transaction Failed
- Ensure sufficient ETH for gas
- Verify recipient address is correct
- Increase gas limit if needed
- Check [Uniscan](https://uniscan.xyz) for network status

### "Invalid Sender" Error
This means the transaction signature is invalid. If you modified the code, ensure v value uses legacy format (`27 + recoveryId`), not EIP-155 format.

### Balance Not Showing
- Tap refresh button
- Check internet connection
- Verify token contract address is correct

## Development

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Blockchain**: Web3j
- **NFC**: Tangem SDK
- **Architecture**: MVVM with StateFlow

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Dependencies

- Tangem SDK 3.9.2
- Web3j 5.0.1
- Jetpack Compose + Material 3
- Kotlin Coroutines 1.6.4
- Timber (logging)

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Documentation

- [DEVELOPER.md](DEVELOPER.md) — Technical deep-dive, the "hack" explained, adding tokens/networks
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contribution guidelines

## Disclaimer

**USE AT YOUR OWN RISK.**

- This software is provided "as is" without warranty
- Always test with small amounts first
- Triple-check recipient addresses — transactions cannot be reversed
- The authors are not responsible for any lost funds

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Tangem](https://tangem.com) — for the amazing NFC cards and SDK
- [Unichain](https://unichain.org) — for the network
- [Web3j](https://web3j.io) — for the Ethereum library

## Support

If this project helped you recover your funds, consider supporting development:

- **ETH/EVM**: `openjdk.eth`
- **Star this repo** to help others find it

---

**If this project helped you recover your funds, consider starring the repo!**
