package com.example.tangemunichainhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tangemunichainhelper.core.CardInfo
import com.example.tangemunichainhelper.core.GasUtils
import com.example.tangemunichainhelper.core.NetworkConstants
import com.example.tangemunichainhelper.core.TangemManager
import com.example.tangemunichainhelper.ui.ErrorInfo
import com.example.tangemunichainhelper.ui.ErrorType
import com.example.tangemunichainhelper.ui.MainViewModel
import com.example.tangemunichainhelper.ui.MaxTransferInfo
import com.example.tangemunichainhelper.ui.TransactionResult
import com.example.tangemunichainhelper.ui.TransferParams
import com.example.tangemunichainhelper.ui.theme.TangemUnichainTheme
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigInteger
import androidx.compose.material3.ExperimentalMaterial3Api // Add for FilterChip

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var tangemManager: TangemManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Tangem Manager
        tangemManager = TangemManager(this)
        viewModel.initTangemManager(tangemManager)

        Timber.d("MainActivity created")

        setContent {
            TangemUnichainTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tangem Unichain Wallet") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Display
            uiState.error?.let { error ->
                ErrorCard(error = error, onDismiss = { viewModel.clearError() })
            }

            // Transaction Success
            uiState.lastTransactionResult?.let { result ->
                TransactionSuccessCard(
                    result = result,
                    onDismiss = { viewModel.clearTransactionResult() }
                )
            }

            // Card Scan Section
            CardScanSection(
                cardInfo = uiState.cardInfo,
                isLoading = uiState.isLoading,
                onScanCard = { viewModel.scanCard() }
            )

            // Balances Section
            if (uiState.cardInfo != null) {
                BalancesSection(
                    ethBalance = uiState.ethBalance.toPlainString(),
                    usdcBalance = uiState.usdcBalance.toPlainString(),
                    isLoading = uiState.isLoadingBalances,
                    onRefresh = { viewModel.loadBalances() }
                )

                // Transfer Section
                val transferParams = uiState.transferParams
                if (transferParams == null) {
                    TransferSection(
                        isLoading = uiState.isLoading,
                        ethBalance = uiState.ethBalance.toPlainString(),
                        usdcBalance = uiState.usdcBalance.toPlainString(),
                        onPrepareTransfer = { address, amount, isUsdc ->
                            viewModel.prepareTransfer(address, amount, isUsdc)
                        },
                        onCalculateMax = { isUsdc ->
                            viewModel.calculateMaxTransferAmount(isUsdc)
                        }
                    )
                } else {
                    // Now transferParams is smart-cast to non-null
                    TransferConfirmationSection(
                        transferParams = transferParams,
                        isLoading = uiState.isLoading,
                        onConfirm = { viewModel.executeTransfer() },
                        onCancel = { viewModel.cancelTransfer() },
                        onUpdateGas = { gasPrice, gasLimit ->
                            viewModel.updateGasParams(gasPrice, gasLimit)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorCard(error: String, onDismiss: () -> Unit) {
    val errorInfo = remember(error) { ErrorInfo.fromMessage(error) }
    var showDetails by remember { mutableStateOf(false) }

    // Get icon based on error type
    val icon = when (errorInfo.type) {
        ErrorType.CARD -> "📱"
        ErrorType.NETWORK -> "🌐"
        ErrorType.VALIDATION -> "⚠️"
        ErrorType.TRANSACTION -> "❌"
        ErrorType.BALANCE -> "💰"
        ErrorType.UNKNOWN -> "❗"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = errorInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Error message
            Text(
                text = errorInfo.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            // Suggestion (if available)
            errorInfo.suggestion?.let { suggestion ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "💡",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Technical details toggle (if available and different from message)
            if (errorInfo.technicalDetails != null && errorInfo.technicalDetails != errorInfo.message) {
                TextButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (showDetails) "Hide technical details" else "Show technical details",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (showDetails) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorInfo.technicalDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Dismiss button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
fun TransactionSuccessCard(result: TransactionResult, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copiedField by remember { mutableStateOf<String?>(null) }

    // Auto-clear "Copied!" message after 2 seconds
    LaunchedEffect(copiedField) {
        if (copiedField != null) {
            kotlinx.coroutines.delay(2000)
            copiedField = null
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction Sent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }

            // Amount sent (prominent display)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "-${result.amount.stripTrailingZeros().toPlainString()} ${result.tokenSymbol}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "Sent successfully",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Transaction Details
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Transaction Hash with copy
            CopyableInfoRow(
                label = "Transaction ID",
                value = result.shortTxHash,
                fullValue = result.txHash,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(result.txHash))
                    copiedField = "txHash"
                },
                isCopied = copiedField == "txHash"
            )

            // Recipient
            CopyableInfoRow(
                label = "To",
                value = result.shortRecipientAddress,
                fullValue = result.recipientAddress,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(result.recipientAddress))
                    copiedField = "recipient"
                },
                isCopied = copiedField == "recipient"
            )

            // Network
            InfoRow(label = "Network", value = result.networkName)

            // Nonce (Transaction Number)
            InfoRow(label = "Nonce", value = "#${result.nonce}")

            // Gas Fee
            InfoRow(
                label = "Gas Fee",
                value = "${result.gasFee.setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()} ETH"
            )

            HorizontalDivider()

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(result.txHash))
                        copiedField = "txHash"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (copiedField == "txHash") "Copied!" else "Copy Tx ID")
                }
            }

            // Explorer Link
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "View on Explorer",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = result.explorerUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.explorerUrl))
                            copiedField = "explorer"
                        }
                    ) {
                        Text(
                            text = if (copiedField == "explorer") "✓" else "📋",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CopyableInfoRow(
    label: String,
    value: String,
    fullValue: String,
    onCopy: () -> Unit,
    isCopied: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        TextButton(onClick = onCopy) {
            Text(
                text = if (isCopied) "Copied!" else "Copy",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CardScanSection(
    cardInfo: CardInfo?,
    isLoading: Boolean,
    onScanCard: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tangem Card",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (cardInfo == null) {
                Text(
                    text = "Tap your Tangem card to NFC reader",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onScanCard,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Scan Card")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Card ID", cardInfo.cardId)
                    InfoRow("Address", cardInfo.walletAddress)
                }
                OutlinedButton(
                    onClick = onScanCard,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Again")
                }
            }
        }
    }
}

@Composable
fun BalancesSection(
    ethBalance: String,
    usdcBalance: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Balances",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    Text("🔄", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                BalanceRow("ETH", ethBalance)
                BalanceRow("USDC", usdcBalance)
            }
        }
    }
}

@Composable
fun BalanceRow(symbol: String, balance: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = balance,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSection(
    isLoading: Boolean,
    ethBalance: String,
    usdcBalance: String,
    onPrepareTransfer: (String, String, Boolean) -> Unit,
    onCalculateMax: suspend (Boolean) -> Result<MaxTransferInfo>
) {
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedToken by remember { mutableStateOf(0) } // 0 = ETH, 1 = USDC
    var isCalculatingMax by remember { mutableStateOf(false) }
    var maxInfo by remember { mutableStateOf<MaxTransferInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Send Transaction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Token Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedToken == 0,
                    onClick = {
                        selectedToken = 0
                        maxInfo = null // Reset max info when switching tokens
                    },
                    label = { Text("ETH") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedToken == 1,
                    onClick = {
                        selectedToken = 1
                        maxInfo = null // Reset max info when switching tokens
                    },
                    label = { Text("USDC") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Show current balance for selected token
            Text(
                text = "Available: ${if (selectedToken == 0) ethBalance else usdcBalance} ${if (selectedToken == 0) "ETH" else "USDC"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Recipient Address
            OutlinedTextField(
                value = recipientAddress,
                onValueChange = { recipientAddress = it },
                label = { Text("Recipient Address") },
                placeholder = { Text("0x...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Amount with Max button
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isCalculatingMax = true
                                val result = onCalculateMax(selectedToken == 1)
                                result.onSuccess { info ->
                                    maxInfo = info
                                    if (info.hasEnoughGas) {
                                        // Set amount to max (with reasonable precision)
                                        amount = if (selectedToken == 1) {
                                            info.maxAmount.setScale(6, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
                                        } else {
                                            info.maxAmount.setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
                                        }
                                    }
                                }
                                isCalculatingMax = false
                            }
                        },
                        enabled = !isLoading && !isCalculatingMax
                    ) {
                        if (isCalculatingMax) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Max")
                        }
                    }
                }
            )

            // Show max info if calculated
            maxInfo?.let { info ->
                val gasFeeWei = info.gasPrice.multiply(info.gasLimit)
                val formattedGasFee = GasUtils.formatGasFeeEth(gasFeeWei)

                if (!info.hasEnoughGas) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Insufficient ETH for gas. You need at least $formattedGasFee for gas fees.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Est. gas fee: $formattedGasFee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    onPrepareTransfer(recipientAddress, amount, selectedToken == 1)
                },
                enabled = !isLoading && recipientAddress.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Prepare Transfer")
                }
            }
        }
    }
}

@Composable
fun TransferConfirmationSection(
    transferParams: TransferParams,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUpdateGas: (BigInteger, BigInteger) -> Unit
) {
    var editingGas by remember { mutableStateOf(false) }
    // Use transferParams values as keys so state updates when they change
    var gasPriceGwei by remember(transferParams.gasPrice) {
        mutableStateOf(transferParams.gasPrice.divide(BigInteger.TEN.pow(9)).toString())
    }
    var gasLimit by remember(transferParams.gasLimit) {
        mutableStateOf(transferParams.gasLimit.toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Confirm Transfer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            InfoRow("To", transferParams.recipientAddress)
            InfoRow(
                "Amount",
                "${transferParams.amount} ${if (transferParams.isUsdc) "USDC" else "ETH"}"
            )

            Divider()

            if (!editingGas) {
                // Format gas values for better readability
                val formattedGasPrice = GasUtils.formatGasPriceGwei(transferParams.gasPrice)
                val formattedGasLimit = GasUtils.formatGasLimit(transferParams.gasLimit, transferParams.isUsdc)
                val gasFeeWei = GasUtils.calculateGasFeeWei(transferParams.gasPrice, transferParams.gasLimit)
                val formattedGasFee = GasUtils.formatGasFeeEth(gasFeeWei)

                InfoRow("Gas Price", "$formattedGasPrice Gwei")
                InfoRow("Gas Limit", formattedGasLimit)
                InfoRow("Est. Gas Fee", formattedGasFee)

                OutlinedButton(
                    onClick = { editingGas = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Gas")
                }
            } else {
                OutlinedTextField(
                    value = gasPriceGwei,
                    onValueChange = { gasPriceGwei = it },
                    label = { Text("Gas Price (Gwei)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = gasLimit,
                    onValueChange = { gasLimit = it },
                    label = { Text("Gas Limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingGas = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            try {
                                val newGasPrice = BigInteger(gasPriceGwei).multiply(BigInteger.TEN.pow(9))
                                val newGasLimit = BigInteger(gasLimit)
                                onUpdateGas(newGasPrice, newGasLimit)
                                editingGas = false
                            } catch (e: Exception) {
                                // Handle invalid input
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Update")
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Sign & Send")
                    }
                }
            }

            Text(
                text = "You will be prompted to scan your Tangem card to sign the transaction",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}