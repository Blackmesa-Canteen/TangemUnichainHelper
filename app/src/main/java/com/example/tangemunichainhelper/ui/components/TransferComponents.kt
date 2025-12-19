package com.example.tangemunichainhelper.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tangemunichainhelper.core.GasUtils
import com.example.tangemunichainhelper.core.Token
import com.example.tangemunichainhelper.ui.MaxTransferInfo
import com.example.tangemunichainhelper.ui.TransferParams
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Section for preparing a token transfer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSection(
    isLoading: Boolean,
    availableTokens: List<Token>,
    tokenBalances: Map<String, BigDecimal>,
    nativeCurrencySymbol: String,
    onPrepareTransfer: (String, String, Token) -> Unit,
    onCalculateMax: suspend (Token) -> Result<MaxTransferInfo>
) {
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedTokenIndex by remember { mutableStateOf(0) }
    var isCalculatingMax by remember { mutableStateOf(false) }
    var maxInfo by remember { mutableStateOf<MaxTransferInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Get the currently selected token
    val selectedToken = availableTokens.getOrNull(selectedTokenIndex) ?: Token.Native

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

            // Token Selection - Dynamic based on available tokens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTokens.forEachIndexed { index, token ->
                    val displaySymbol = if (token is Token.Native) nativeCurrencySymbol else token.symbol
                    FilterChip(
                        selected = selectedTokenIndex == index,
                        onClick = {
                            selectedTokenIndex = index
                            maxInfo = null // Reset max info when switching tokens
                        },
                        label = { Text(displaySymbol) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Show current balance for selected token
            val displaySymbol = if (selectedToken is Token.Native) nativeCurrencySymbol else selectedToken.symbol
            val balance = tokenBalances[selectedToken.symbol] ?: BigDecimal.ZERO
            Text(
                text = "Available: ${balance.stripTrailingZeros().toPlainString()} $displaySymbol",
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
                                val result = onCalculateMax(selectedToken)
                                result.onSuccess { info ->
                                    maxInfo = info
                                    if (info.hasEnoughGas) {
                                        // Set amount to max (with reasonable precision based on token decimals)
                                        val scale = if (selectedToken.decimals <= 6) selectedToken.decimals else 8
                                        amount = info.maxAmount.setScale(scale, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
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
                    onPrepareTransfer(recipientAddress, amount, selectedToken)
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

/**
 * Section for confirming and executing a transfer with gas editing.
 */
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

            HorizontalDivider()

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

            HorizontalDivider()

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
