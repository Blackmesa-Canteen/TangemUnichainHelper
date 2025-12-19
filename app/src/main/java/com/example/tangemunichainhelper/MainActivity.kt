package com.example.tangemunichainhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tangemunichainhelper.core.TangemManager
import com.example.tangemunichainhelper.ui.MainViewModel
import com.example.tangemunichainhelper.ui.components.*
import com.example.tangemunichainhelper.ui.theme.TangemUnichainTheme
import timber.log.Timber

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

            // Network Selection
            ChainSelector(
                selectedChain = uiState.selectedChain,
                onChainSelected = { viewModel.selectChain(it) }
            )

            // Card Scan Section
            CardScanSection(
                cardInfo = uiState.cardInfo,
                isLoading = uiState.isLoading,
                onScanCard = { viewModel.scanCard() }
            )

            // Balances Section
            if (uiState.cardInfo != null) {
                BalancesSectionDynamic(
                    chain = uiState.selectedChain,
                    tokenBalances = uiState.tokenBalances,
                    availableTokens = uiState.availableTokens,
                    isLoading = uiState.isLoadingBalances,
                    onRefresh = { viewModel.loadBalances() }
                )

                // Transfer Section
                val transferParams = uiState.transferParams
                if (transferParams == null) {
                    TransferSection(
                        isLoading = uiState.isLoading,
                        availableTokens = uiState.availableTokens,
                        tokenBalances = uiState.tokenBalances,
                        nativeCurrencySymbol = uiState.selectedChain.nativeCurrencySymbol,
                        onPrepareTransfer = { address, amount, token ->
                            viewModel.prepareTransfer(address, amount, token)
                        },
                        onCalculateMax = { token ->
                            viewModel.calculateMaxTransferAmount(token)
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
