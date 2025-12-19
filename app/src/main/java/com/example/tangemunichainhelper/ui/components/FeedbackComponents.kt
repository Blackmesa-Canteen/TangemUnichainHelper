package com.example.tangemunichainhelper.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tangemunichainhelper.ui.ErrorInfo
import com.example.tangemunichainhelper.ui.ErrorType
import com.example.tangemunichainhelper.ui.TransactionResult
import java.math.RoundingMode

/**
 * Displays error information with categorization and helpful suggestions.
 */
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

/**
 * Displays successful transaction information with copy functionality.
 */
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
                value = "${result.gasFee.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} ETH"
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

/**
 * A row displaying a label and value with copy functionality.
 */
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

/**
 * A simple row displaying a label and value.
 */
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
