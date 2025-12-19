# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Nothing yet

## [1.2.1] - 2025-12-19

### Security
- **Disabled application backup** (`android:allowBackup="false"`) to prevent potential data exposure through Android backup mechanisms

### Fixed
- Removed unused variable in `MainViewModel.calculateMaxTransferAmount()`
- Fixed SECURITY.md vulnerability reporting link pointing to wrong repository

## [1.2.0] - 2025-12-19

### Changed
- **Major code refactoring** for improved maintainability
  - Split `MainActivity.kt` (1050 lines) into focused component files:
    - `ui/components/WalletComponents.kt` - Card scan, chain selector, balances
    - `ui/components/TransferComponents.kt` - Transfer form and confirmation
    - `ui/components/FeedbackComponents.kt` - Error and success cards
  - Split `MainViewModel.kt` (994 lines) into:
    - `ui/UiModels.kt` - Data classes (UiState, TransferParams, etc.)
    - `core/TransactionSigner.kt` - Transaction encoding and signing utilities
  - `MainActivity.kt` now ~140 lines (orchestration only)
  - `MainViewModel.kt` now ~600 lines (business logic only)

### Technical
- Extracted `TransactionSigner` utility with:
  - `encodeSignedTransaction()` - RLP encoding with EIP-155
  - `findRecoveryId()` - ECDSA recovery ID determination
  - `decompressPublicKey()` - secp256k1 key decompression
- Added comprehensive unit tests for `TransactionSigner` (10 tests)
- Total test count: 169 tests

## [1.1.2] - 2025-12-19

### Fixed
- Send Transaction token selector now shows all available tokens for the selected chain
  - Previously hardcoded to ETH and USDC only
  - Now dynamically displays tokens from `TokenContractRegistry` for the current chain

## [1.1.1] - 2025-12-19

### Added
- Custom app icon (chain link with upward arrow representing "chain rescue")
  - Purple-blue gradient background (crypto/blockchain themed)
  - White foreground with chain links and rescue arrow

### Changed
- Updated documentation for multi-chain consistency

## [1.1.0] - 2025-12-19

### Added
- **Multi-chain architecture** - Extensible support for any EVM chain
  - `Chain` sealed class with `Unichain`, `Sepolia`, and `Custom` types
  - `ChainRegistry` for managing available chains
  - `TokenContractRegistry` for chain-specific token contract addresses
  - Chain selector dropdown in UI
- Sepolia testnet support for development and testing
- Chain-aware transaction signing with dynamic chain ID
- `switchChain()` method for runtime chain switching
- Comprehensive unit tests for Chain and TokenContractRegistry

### Changed
- **Breaking**: `Token.ETH` renamed to `Token.Native` (deprecated alias available)
- **Breaking**: `Token.ERC20` no longer includes `contractAddress` (moved to `TokenContractRegistry`)
- Web3Manager now accepts `Chain` parameter and manages chain state
- MainViewModel includes `selectedChain` state and `selectChain()` method
- Transaction encoding uses chain-specific chain ID for EIP-155
- Explorer URLs now dynamically generated from chain configuration
- Updated documentation with multi-chain extension guides

### Deprecated
- `NetworkConstants.kt` - Use `Chain` properties instead
- `Token.ETH` - Use `Token.Native` instead
- `TokenRegistry.ETH` - Use `TokenRegistry.Native` instead

### Security
- GitHub Actions security scanning workflow added:
  - Gitleaks for secret detection
  - CodeQL SAST (Static Application Security Testing) for Java/Kotlin
  - Dependency vulnerability checking
  - Android Lint security checks
- Fixed Timber logging to only enable in debug builds
  - Prevents sensitive transaction data from being logged in production

### Technical
- Token definitions are now chain-agnostic (no contract addresses)
- Contract addresses managed per-chain in `TokenContractRegistry`
- UI dynamically shows tokens available for selected chain
- 159 unit tests (added Chain and TokenContractRegistry tests)

## [1.0.2] - 2025-12-19

### Added
- Added USDT (Tether USD) token support
  - Contract: `0x9151434b16b9763660705744891fa906f660ecc5`
  - Uses USDT0 (omnichain Tether via LayerZero)

### Fixed
- Fixed "only replay-protected (EIP-155) transactions allowed" error
- Transactions now use proper EIP-155 format with chain ID
- Fixed Tangem SDK JitPack dependency casing (JitPack is case-sensitive)

### Changed
- Transaction hash now includes chain ID (EIP-155 compliant)
- v value calculation updated: `chainId * 2 + 35 + recoveryId`
- Updated documentation to reflect EIP-155 signing process

### Security
- Added replay protection - transactions are now chain-specific
- Signed transactions for Unichain cannot be replayed on other chains

## [1.0.1] - 2025-12-19

### Fixed
- Fixed "Failed to convert CurveId to EllipticCurve" error when scanning newer Tangem cards
- Fixed 502 RPC errors when sending transactions

### Changed
- Upgraded Tangem SDK from 3.8.2 to 3.9.2 for newer card firmware support
- Upgraded Java compatibility from 17 to 21 (required by web3j 5.0.1)
- Switched primary RPC endpoint to dRPC for better reliability
- Added RPC fallback with automatic retry logic
- Increased Gradle JVM memory to 4GB for stable builds

### Technical
- Primary RPC: `https://unichain.drpc.org` (recommended by Unichain docs)
- Fallback RPC: `https://mainnet.unichain.org`
- CI/CD workflows updated with proper memory settings

## [1.0.0] - 2025-12-19

### Added
- Initial release
- Tangem NFC card scanning with derivation path support
- ETH balance display and transfer
- USDC (ERC-20) balance display and transfer
- Automatic gas estimation with 20% safety buffer
- Manual gas price and limit editing
- "Max" button with automatic gas reservation
- EIP-55 address checksum validation
- Transaction success screen with detailed info:
  - Transaction hash with copy button
  - Amount sent
  - Recipient address
  - Gas fee
  - Nonce (transaction number)
  - Explorer link
- User-friendly error messages with:
  - Error categorization (Card, Network, Balance, Validation, Transaction)
  - Helpful suggestions
  - Expandable technical details
- Support for adding new tokens easily via `TokenRegistry`
- Comprehensive unit tests (104 tests)

### Technical
- Uses legacy transaction format for Tangem compatibility
- Supports Unichain Mainnet (Chain ID: 130)
- Built with Jetpack Compose and Material Design 3
- MVVM architecture with StateFlow

### Security
- Private keys never leave Tangem card
- All signing happens in card's secure element
- Access code required for every signature
- Open source for audit

---

## How to Update This File

When releasing a new version:

1. Move items from `[Unreleased]` to a new version section
2. Add the version number and date: `## [X.Y.Z] - YYYY-MM-DD`
3. Categorize changes under:
   - `Added` - New features
   - `Changed` - Changes to existing features
   - `Deprecated` - Features to be removed
   - `Removed` - Removed features
   - `Fixed` - Bug fixes
   - `Security` - Security improvements

Example:
```markdown
## [1.1.0] - 2024-12-25

### Added
- Support for USDT token
- Transaction history view

### Fixed
- Gas estimation for complex contracts
```
