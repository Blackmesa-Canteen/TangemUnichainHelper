# Contributing to Tangem Unichain Helper

Thank you for your interest in contributing! This project helps Tangem card users transfer assets on Unichain.

## How to Contribute

### Reporting Bugs

1. **Search existing issues** first to avoid duplicates
2. Open a new issue with:
   - Clear title describing the problem
   - Steps to reproduce
   - Expected vs actual behavior
   - Device model and Android version
   - Tangem card firmware version (if relevant)

### Suggesting Features

Open an issue with the `enhancement` label describing:
- What problem does this solve?
- How would it work?
- Any implementation ideas?

### Submitting Code

1. **Fork** the repository
2. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**
4. **Test thoroughly** on a real device with a Tangem card
5. **Commit** with clear messages:
   ```bash
   git commit -m "Add: brief description of change"
   ```
6. **Push** to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
7. **Open a Pull Request** against `main`

## Development Setup

### Requirements

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android device with NFC for testing
- Tangem card for testing

### Building

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/tangem-unichain-helper.git
cd tangem-unichain-helper

# Build
./gradlew assembleDebug
```

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small

## Pull Request Guidelines

- **One feature per PR** - Keep PRs focused
- **Update documentation** if needed
- **Test on real device** - Emulators don't support NFC
- **Describe your changes** in the PR description
- **Link related issues** if applicable

## Adding New Tokens

See [DEVELOPER.md](DEVELOPER.md#adding-new-tokens) for step-by-step instructions.

## Adding Network Support

See [DEVELOPER.md](DEVELOPER.md#adding-new-networks) for details on supporting other EVM chains.

## Security

- **Never commit** test wallet addresses or real funds
- **Never log** sensitive data like signatures
- **Review** the security considerations in DEVELOPER.md

## Questions?

- Check existing issues and documentation first
- Open an issue for technical questions
- Be patient and respectful

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for helping improve this project!
