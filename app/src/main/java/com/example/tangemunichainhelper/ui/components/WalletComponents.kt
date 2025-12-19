package com.example.tangemunichainhelper.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tangemunichainhelper.core.CardInfo
import com.example.tangemunichainhelper.core.Chain
import com.example.tangemunichainhelper.core.ChainRegistry
import com.example.tangemunichainhelper.core.Token
import java.math.BigDecimal

/**
 * Section for scanning Tangem NFC cards.
 */
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

/**
 * Dropdown menu for selecting the blockchain network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainSelector(
    selectedChain: Chain,
    onChainSelected: (Chain) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Network",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedChain.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ChainRegistry.allChains.forEach { chain ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chain.name)
                                    if (chain.isTestnet) {
                                        Text(
                                            text = "(Testnet)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onChainSelected(chain)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Show chain info
            Text(
                text = "Chain ID: ${selectedChain.chainId} • ${selectedChain.nativeCurrencySymbol}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Legacy balances section showing ETH and USDC.
 */
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

/**
 * Dynamic balances section that shows available tokens based on the selected chain.
 */
@Composable
fun BalancesSectionDynamic(
    chain: Chain,
    tokenBalances: Map<String, BigDecimal>,
    availableTokens: List<Token>,
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
                availableTokens.forEach { token ->
                    val balance = tokenBalances[token.symbol] ?: BigDecimal.ZERO
                    val displaySymbol = if (token is Token.Native) {
                        chain.nativeCurrencySymbol
                    } else {
                        token.symbol
                    }
                    BalanceRow(displaySymbol, balance.stripTrailingZeros().toPlainString())
                }

                if (availableTokens.isEmpty()) {
                    Text(
                        text = "No tokens configured for ${chain.shortName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * A row showing token symbol and balance.
 */
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
