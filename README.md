# Tangem Wallet Unofficial Unichain Support App

A simple Android app for managing ETH and USDC on Unichain Mainnet using Tangem NFC cards.

## Background

I have USDC and ETH assets on Unichain that became inaccessible because the official Tangem wallet app doesn't support signing Unichain transactions. This app was created to solve that problem.

## ⚠️ IMPORTANT - READ BEFORE USING

### Known Issues
- **Gas fee calculation bug**: The gas fee estimation is currently broken and under active development.

### Security Notice
**Please review the code before using this app.**

This is my first Web3 project and may contain bugs or security issues. I strongly recommend:
- Reviewing the source code thoroughly
- Understanding the transaction workflow
- Verifying it suits your specific use case
- Testing with small amounts first

**This app handles real cryptocurrency funds. Please read carefully:**

1. **Test with small amounts first** - Always start with minimal amounts to verify everything works
2. **Triple-check recipient addresses** - Transactions cannot be reversed
3. **Secure your Tangem card** - Keep your 4-digit access code safe
4. **Verify transaction details** - Always review gas fees and amounts before signing
5. **Check network** - This app is configured for Unichain Mainnet (Chain ID: 130)

**Do not use this app without understanding what it does.**

## Features

✅ Read Tangem card via NFC
✅ View ETH and USDC balances on Unichain
✅ Send ETH transfers
✅ Send USDC (ERC-20) transfers
✅ Automatic gas estimation with manual editing option
✅ Transaction signing with Tangem card
✅ Transaction hash display and copy
✅ Comprehensive error handling

## Requirements

- Android device with NFC support
- Android 7.0 (API 24) or higher
- Tangem card with your wallet
- Android Studio 2024.1.1 or higher (for building)
- JDK 17

## Configuration

The app is pre-configured with:
- **Network**: Unichain Mainnet
- **Chain ID**: 130
- **RPC**: https://rpc.unichain.org
- **Explorer**: https://uniscan.xyz
- **USDC Contract**: 0x078D782b760474a361dDA0AF3839290b0EF57AD6

## Building the App

### 1. Clone/Download the Project

```bash
cd /path/to/project
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open" and choose the project folder
3. Wait for Gradle sync to complete

### 3. Build APK

```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease
```

The APK will be in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

### 4. Install on Device

**Option A: Via USB**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option B: Via Android Studio**
- Connect device via USB
- Enable USB debugging on device
- Click "Run" button in Android Studio

**Option C: Transfer APK**
- Copy APK to device
- Open file manager and install
- Allow "Install from unknown sources" if prompted

## Using the App

### First Time Setup

1. **Launch App**
    - Open "Tangem Unichain" app

2. **Scan Your Card**
    - Tap "Scan Card" button
    - Hold Tangem card to phone's NFC reader
    - Enter your 4-digit access code when prompted
    - Wait for card to be read

3. **View Balances**
    - ETH and USDC balances will load automatically
    - Tap refresh icon (🔄) to reload balances

### Sending Transactions

#### Prepare Transfer

1. Select token (ETH or USDC)
2. Enter recipient address (must start with 0x)
3. Enter amount (e.g., 0.001 for ETH or 1.5 for USDC)
4. Tap "Prepare Transfer"

#### Review & Adjust Gas

The app will show:
- Recipient address
- Amount and token
- Gas price (in Gwei)
- Gas limit
- Estimated gas fee

To edit gas:
1. Tap "Edit Gas"
2. Modify gas price or gas limit
3. Tap "Update"

#### Sign & Send

1. Review all details carefully
2. Tap "Sign & Send"
3. Hold Tangem card to NFC reader
4. Enter your 4-digit access code
5. Wait for transaction to be signed and broadcast

#### View Transaction

After successful send:
- Transaction hash is displayed
- Tap "Copy Hash" to copy to clipboard
- View on explorer: https://uniscan.xyz/tx/YOUR_TX_HASH
- Balances will refresh automatically

## Troubleshooting

### NFC Not Working
- Ensure NFC is enabled in device settings
- Remove phone case if thick
- Hold card flat against phone back
- Try different positions

### Card Scan Failed
- Check 4-digit access code is correct
- Ensure card is close to NFC reader
- Keep card still during scan
- Card may be damaged - try another card

### Transaction Failed
- Check you have sufficient balance (including gas)
- Verify recipient address is correct
- Gas limit may be too low - increase it
- Network may be congested - increase gas price

### Gas Estimation Failed
- App will use default gas limit
- Manually set higher gas limit if needed
- ETH transfers: 21,000 gas
- USDC transfers: 65,000 gas

### Balance Not Updating
- Tap refresh button (🔄)
- Check internet connection
- RPC node may be slow - wait and retry

## Technical Details

### Architecture

```
MainActivity
    ├── MainViewModel (State Management)
    │   ├── TangemManager (Card Operations)
    │   └── Web3Manager (Blockchain Interactions)
    └── Compose UI (User Interface)
```

### Key Components

**TangemManager**
- Scans Tangem card via NFC
- Signs transaction hashes
- Handles access code protection

**Web3Manager**
- Connects to Unichain RPC
- Gets balances (ETH & USDC)
- Creates transactions
- Estimates gas
- Broadcasts signed transactions

**MainViewModel**
- Coordinates between managers
- Manages UI state
- Handles errors
- Transaction flow control

### Transaction Flow

1. User enters transfer details
2. App prepares transaction
3. App estimates gas
4. User reviews and confirms
5. App creates raw transaction
6. App gets transaction hash
7. Tangem card signs hash
8. App encodes signed transaction
9. App broadcasts to network
10. Transaction hash returned

### Security Notes

- **Private keys never leave Tangem card**
- Access code required for each signature
- Transaction details shown before signing
- Signature done in secure element
- No private key export possible

## Gas Fee Estimation

Gas fees depend on:
- Network congestion
- Transaction type (ETH vs USDC)
- Gas price (Gwei)
- Gas limit (units)

**Total Fee** = Gas Price × Gas Limit

Example:
- Gas Price: 0.01 Gwei
- Gas Limit: 21,000
- Fee: 0.00021 ETH

## Error Codes

Common errors and solutions:

- **"Invalid recipient address"**: Check address format (0x...)
- **"Insufficient funds"**: Add more ETH (for gas)
- **"Gas too low"**: Increase gas limit
- **"Nonce too low"**: Transaction pending, wait
- **"User cancelled"**: You cancelled card scan/sign
- **"Card not found"**: Card not detected, try again

## Network Information

- **Network**: Unichain Mainnet
- **Chain ID**: 130
- **Block Time**: ~1 second
- **Finality**: ~250ms
- **Currency**: ETH
- **Explorer**: https://uniscan.xyz

## USDC Information

- **Contract**: 0x078D782b760474a361dDA0AF3839290b0EF57AD6
- **Decimals**: 6
- **Type**: ERC-20
- **Issuer**: Circle

## Support & Resources

- **Tangem**: https://tangem.com
- **Unichain Docs**: https://docs.unichain.org
- **Explorer**: https://uniscan.xyz
- **Web3j**: https://docs.web3j.io

## Development

### Project Structure

```
app/src/main/
├── java/com/tangem/unichain/
│   ├── TangemUnichainApp.kt      # Application class
│   ├── MainActivity.kt            # Main UI
│   ├── core/
│   │   ├── NetworkConstants.kt   # Network config
│   │   ├── TangemManager.kt      # Card operations
│   │   └── Web3Manager.kt        # Blockchain ops
│   └── ui/
│       ├── MainViewModel.kt      # State management
│       └── theme/
│           └── Theme.kt          # Material theme
└── res/
    ├── values/
    │   ├── strings.xml
    │   └── themes.xml
    └── xml/
        └── nfc_tech_filter.xml   # NFC config
```

### Dependencies

- Tangem SDK 3.8.0
- Web3j 4.11.3
- Jetpack Compose
- Kotlin Coroutines
- Material Design 3

### Modifying for Different Network

To use a different EVM network:

1. Edit `NetworkConstants.kt`:
```kotlin
const val CHAIN_ID = YOUR_CHAIN_ID
const val RPC_URL = "YOUR_RPC_URL"
const val EXPLORER_URL = "YOUR_EXPLORER"
```

2. Update token contracts as needed

### Adding More Tokens

To add ERC-20 support:

1. Add contract address to `NetworkConstants.kt`
2. Add balance function in `Web3Manager.kt`
3. Add transfer function in `Web3Manager.kt`
4. Update UI in `MainActivity.kt`

## License

This is example code for educational purposes. Use at your own risk.

## Disclaimer

**This software is provided "as is" without warranty of any kind.**

- Test thoroughly before use
- Verify all transactions
- Keep backups
- Use at your own risk
- Author not responsible for lost funds

---

# Please feel free to code review before using this app, or fork as your own version!
