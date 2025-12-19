# Security Policy

## Supported Versions

The following versions of Tangem Unichain Helper receive security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.2.x   | :white_check_mark: |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :x:                |

We recommend always using the latest version for the best security.

## Security Model

### What This App Does

- **Signs transactions** using your Tangem hardware wallet (private keys never leave the card)
- **Connects to blockchain RPCs** over HTTPS to broadcast transactions
- **Displays balances** by querying public blockchain data

### What This App Does NOT Do

- Store private keys (they remain in the Tangem secure element)
- Store passwords or sensitive credentials
- Collect or transmit personal data
- Require account registration

### Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App                               │
│  - Creates unsigned transaction                              │
│  - Sends hash to Tangem card for signing                    │
│  - Broadcasts signed transaction to blockchain              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Tangem Card (Secure Element)                    │
│  - Stores private keys (never exported)                     │
│  - Signs transaction hashes                                  │
│  - Requires physical card + access code                     │
└─────────────────────────────────────────────────────────────┘
```

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please report it responsibly.

### How to Report

1. **DO NOT** open a public GitHub issue for security vulnerabilities
2. **Email** the maintainer directly (check the repository for contact info)
3. **Or** use [GitHub's private vulnerability reporting](https://github.com/anthropics/claude-code/security/advisories/new) if enabled

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response Timeline

| Action | Timeframe |
|--------|-----------|
| Initial acknowledgment | 48 hours |
| Preliminary assessment | 7 days |
| Fix development | 14-30 days (depending on severity) |
| Public disclosure | After fix is released |

### What to Expect

- **Accepted**: We'll work on a fix, credit you in the release notes (unless you prefer anonymity), and notify you when the fix is released
- **Declined**: We'll explain why we don't consider it a vulnerability (e.g., intended behavior, out of scope)

## Security Best Practices for Users

1. **Keep your Tangem card secure** - It's your private key
2. **Verify addresses carefully** - Always double-check recipient addresses
3. **Use the latest app version** - We fix security issues in updates
4. **Download only from trusted sources** - GitHub releases or official channels
5. **Verify APK signatures** - Ensure the APK is signed by the official release key

## Scope

### In Scope

- Vulnerabilities in the Android application code
- Issues with transaction signing or encoding
- Address validation bypasses
- Sensitive data exposure

### Out of Scope

- Vulnerabilities in third-party dependencies (report to upstream)
- Tangem SDK vulnerabilities (report to [Tangem](https://tangem.com))
- Blockchain network issues
- Social engineering attacks
- Physical attacks on devices

## Security Audits

See [SECURITY_AUDIT.md](SECURITY_AUDIT.md) for the latest security audit report.

---

Thank you for helping keep Tangem Unichain Helper secure!
