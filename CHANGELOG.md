# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Nothing yet

## [1.0.1] - 2024-12-19

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

## [1.0.0] - 2024-12-19

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
