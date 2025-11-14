# Quick Start Guide - Recovering Your Stuck Funds

This guide is specifically for your situation: recovering USDC and ETH from Unichain where Tangem app cannot sign transactions.

## Your Situation

- **Wallet Address**: 0x5A4dC932a92Eb68529522eA79b566C01515F6436
- **Network**: Unichain Mainnet (Chain ID 130)
- **Problem**: Official Tangem app cannot sign transactions on Unichain
- **Solution**: This custom app that uses Tangem SDK directly

## Step-by-Step Recovery

### Phase 1: Build and Install (One-Time Setup)

#### Option A: Build from Source (Recommended)

1. **Install Android Studio**
    - Download from https://developer.android.com/studio
    - Install with default settings
    - Install JDK 17 if prompted

2. **Open Project**
   ```bash
   # Navigate to project folder
   cd /path/to/tangem-unichain-app
   
   # Open in Android Studio
   # File -> Open -> Select folder
   ```

3. **Wait for Gradle Sync**
    - Android Studio will download dependencies
    - Wait for "BUILD SUCCESSFUL" message
    - This may take 5-10 minutes first time

4. **Connect Phone**
    - Enable Developer Options on Android phone:
        - Settings → About Phone → Tap "Build Number" 7 times
    - Enable USB Debugging:
        - Settings → Developer Options → USB Debugging
    - Connect phone via USB cable
    - Allow USB debugging when prompted

5. **Build and Install**
    - Click green "Run" button (▶️) in Android Studio
    - Or run: `./gradlew installDebug`
    - App will install and launch automatically

#### Option B: Install Pre-built APK

If you have the APK file:
1. Copy `app-debug.apk` to your phone
2. Open file manager on phone
3. Tap the APK file
4. Allow "Install from unknown sources" if asked
5. Tap "Install"

### Phase 2: Test with Small Amount

**⚠️ CRITICAL: Always test with minimal amounts first!**

#### Test #1: Scan Card and Check Balance

1. **Launch App**
    - Open "Tangem Unichain" app
    - Ensure NFC is enabled on phone

2. **Scan Tangem Card**
    - Tap "Scan Card" button
    - Hold card flat against phone back
    - When prompted, enter your 4-digit access code
    - App will scan and show card ID

3. **Verify Balance Display**
    - ETH balance should show your actual balance
    - USDC balance should show your actual balance
    - If incorrect, check wallet address in app

#### Test #2: Send Tiny ETH Amount

1. **Prepare Test Transfer**
    - Select "ETH" token
    - Recipient: (use a test address you control)
    - Amount: `0.0001` (very small test)
    - Tap "Prepare Transfer"

2. **Review Gas Fees**
    - Gas price will be shown (in Gwei)
    - Gas limit: ~21,000 for ETH
    - Estimated fee: ~0.00021 ETH
    - If looks reasonable, continue
    - If gas seems too high, tap "Edit Gas"

3. **Sign Transaction**
    - Tap "Sign & Send"
    - Hold card to phone
    - Enter 4-digit access code
    - Wait for "Transaction Sent" message

4. **Verify on Explorer**
    - Copy transaction hash
    - Open: https://uniscan.xyz/tx/YOUR_TX_HASH
    - Wait for confirmation (~1-2 seconds)
    - Check recipient received funds

#### Test #3: Send Tiny USDC Amount

Only proceed if ETH test succeeded:

1. **Prepare USDC Transfer**
    - Select "USDC" token
    - Recipient: (same test address)
    - Amount: `0.01` (1 cent worth)
    - Tap "Prepare Transfer"

2. **Review Gas Fees**
    - Gas limit: ~65,000 for USDC (higher than ETH)
    - Make sure you have enough ETH for gas
    - Continue if looks good

3. **Sign and Verify**
    - Same process as ETH test
    - Verify on explorer
    - Check recipient received USDC

### Phase 3: Recover Your Funds

**Only proceed if both tests succeeded!**

#### Decide on Strategy

**Option A: Move Everything to Safe Address**
Best if you're closing Unichain position:
1. Send all USDC first (keep enough ETH for 1 more tx)
2. Then send remaining ETH

**Option B: Move to Exchange/Different Wallet**
Good for selling or consolidating:
1. Get destination address
2. Send USDC first
3. Send ETH after

**Option C: Multiple Small Transactions**
Safest for large amounts:
1. Split into 3-4 smaller transfers
2. Verify each one on explorer
3. Continue if all succeed

#### Executing Main Transfer

1. **USDC Transfer**
    - Select USDC
    - Enter destination address (verify 3 times!)
    - Enter amount (all or partial)
    - Review gas fees
    - Sign with card
    - Copy transaction hash
    - Wait for confirmation
    - Verify on explorer

2. **ETH Transfer**
    - Select ETH
    - Same destination or different
    - Leave enough for gas (~0.001 ETH)
    - Or send "all minus gas"
    - Review and sign
    - Verify on explorer

3. **Verify Receipt**
    - Check destination address on explorer
    - Verify amounts received
    - If using exchange, check deposit credited

## Common Issues and Solutions

### Issue: "Card Not Found"
**Solutions:**
- Remove phone case
- Try different position on phone back
- Enable NFC in phone settings
- Card may be damaged - try backup card

### Issue: "Invalid Access Code"
**Solutions:**
- Check you're entering correct 4-digit code
- Try recovery access code if set
- Contact Tangem support if locked

### Issue: "Transaction Failed - Insufficient Funds"
**Solutions:**
- You need ETH for gas fees
- Even USDC transfers require ETH
- Check ETH balance > estimated gas fee
- If stuck: send more ETH to this address first

### Issue: "Gas Estimation Failed"
**Solutions:**
- App will use default gas limit
- For USDC: Manually set gas limit to 100,000
- For ETH: Manually set gas limit to 30,000
- This increases cost slightly but ensures success

### Issue: High Gas Fees
**Solutions:**
- Check current Unichain gas prices
- Wait for lower congestion
- Or accept higher fee to move funds quickly
- Unichain is generally cheap (< $0.001)

### Issue: Transaction Stuck/Pending
**Solutions:**
- Wait 1-2 minutes for Unichain confirmation
- Check on explorer: https://uniscan.xyz
- If truly stuck (>5 min), may need to replace with higher gas
- Contact support with transaction hash

## Safety Checklist

Before each transaction, verify:

- [ ] Recipient address is correct (triple-check!)
- [ ] Amount is correct
- [ ] You have enough ETH for gas
- [ ] Gas fees are reasonable
- [ ] You're on correct network (Unichain)
- [ ] Tangem card is ready
- [ ] You know your access code

## Transaction Costs

Typical costs on Unichain:
- ETH transfer: ~$0.0001 - $0.001
- USDC transfer: ~$0.0005 - $0.005

If seeing higher fees:
- Check gas price (should be < 1 Gwei normally)
- Network may be congested
- Wait or increase budget

## Emergency Contacts

If something goes wrong:
- **Tangem Support**: support@tangem.com
- **Explorer**: https://uniscan.xyz
- **Your Transaction**: Include hash in any support request

## After Recovery

Once funds are safely moved:
1. Keep this app installed (may need again)
2. Update Tangem app (may fix Unichain support)
3. Test official app on Unichain again
4. Keep small amount on Unichain for gas if staying

## Preventing Future Issues

1. **Test before sending large amounts**
2. **Keep multiple wallets** for different networks
3. **Verify app support** before sending to new networks
4. **Keep emergency ETH** for gas fees
5. **Save transaction hashes** for records

## Advanced: Multiple Wallets

If your Tangem card has multiple wallets:
1. App uses first wallet by default
2. To use different wallet, need to modify code
3. In `TangemManager.kt`, change:
   ```kotlin
   val wallet = card.wallets.firstOrNull()
   // Change to specific index:
   val wallet = card.wallets.getOrNull(INDEX)
   ```
4. Rebuild app

## Questions?

Common questions answered:

**Q: Is this safe?**
A: Yes - private keys never leave your Tangem card. App just facilitates signing.

**Q: Can I use this for other networks?**
A: Yes, but need to modify `NetworkConstants.kt` with different chain ID and RPC.

**Q: What if I lose my phone?**
A: No problem - app doesn't store keys. Just install on new phone.

**Q: Can someone steal funds if they have app?**
A: No - they need your physical Tangem card AND access code.

**Q: Should I trust this app?**
A: Code is open source - you can review it. Or have developer audit it.

**Q: Will official Tangem app support Unichain?**
A: Maybe in future - check their updates. Until then, use this.

---

**Good luck recovering your funds! 🚀**

**Remember: Test with small amounts first!**