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
import com.example.tangemunichainhelper.core.NetworkConstants
import com.example.tangemunichainhelper.core.TangemManager
import com.example.tangemunichainhelper.ui.MainViewModel
import com.example.tangemunichainhelper.ui.TransferParams
import com.example.tangemunichainhelper.ui.theme.TangemUnichainTheme
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
            uiState.lastTransactionHash?.let { txHash ->
                TransactionSuccessCard(
                    txHash = txHash,
                    onDismiss = { viewModel.clearTransactionHash() }
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
                        onPrepareTransfer = { address, amount, isUsdc ->
                            viewModel.prepareTransfer(address, amount, isUsdc)
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun TransactionSuccessCard(txHash: String, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✓ Transaction Sent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
            Text(
                text = "Transaction Hash:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = txHash,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(txHash))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Hash")
                }
            }
            Text(
                text = "View on explorer: ${NetworkConstants.EXPLORER_URL}/tx/$txHash",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
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
    onPrepareTransfer: (String, String, Boolean) -> Unit
) {
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedToken by remember { mutableStateOf(0) } // 0 = ETH, 1 = USDC

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
                    onClick = { selectedToken = 0 },
                    label = { Text("ETH") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedToken == 1,
                    onClick = { selectedToken = 1 },
                    label = { Text("USDC") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Recipient Address
            OutlinedTextField(
                value = recipientAddress,
                onValueChange = { recipientAddress = it },
                label = { Text("Recipient Address") },
                placeholder = { Text("0x...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onPrepareTransfer(recipientAddress, amount, selectedToken == 1)
                },
                enabled = !isLoading && recipientAddress.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Prepare Transfer")
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
    var gasPriceGwei by remember {
        mutableStateOf(transferParams.gasPrice.divide(BigInteger.TEN.pow(9)).toString())
    }
    var gasLimit by remember { mutableStateOf(transferParams.gasLimit.toString()) }

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
                InfoRow("Gas Price", "${transferParams.gasPrice.divide(BigInteger.TEN.pow(9))} Gwei")
                InfoRow("Gas Limit", transferParams.gasLimit.toString())

                val gasFee = transferParams.gasPrice
                    .multiply(transferParams.gasLimit)
                    .toBigDecimal()
                    .divide(BigInteger.TEN.pow(18).toBigDecimal())
                InfoRow("Est. Gas Fee", "$gasFee ETH")

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