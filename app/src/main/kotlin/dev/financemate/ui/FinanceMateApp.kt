package dev.financemate.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.financemate.AppContainer
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.ui.import.ImportScreen
import dev.financemate.ui.import.ImportUiState
import dev.financemate.ui.import.ImportViewModel
import dev.financemate.ui.savings.MerchantTagSheet
import dev.financemate.ui.savings.SavingsScreen
import dev.financemate.ui.savings.SavingsViewModel
import dev.financemate.ui.theme.FinanceMateTheme

private enum class Destination(val label: String, val icon: ImageVector) {
    SAVINGS("Savings", Icons.Filled.Savings),
    IMPORT("Import", Icons.Filled.Upload),
}

@Composable
public fun FinanceMateApp(container: AppContainer) {
    FinanceMateTheme {
        AppScaffold(container)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(container: AppContainer) {
    var destination by remember { mutableStateOf(Destination.SAVINGS) }
    var taggingSubscription by remember { mutableStateOf<Subscription?>(null) }

    val savingsViewModel: SavingsViewModel = viewModel { SavingsViewModel(container) }
    val importViewModel: ImportViewModel = viewModel { ImportViewModel(container) }

    val savingsState by savingsViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    // Re-analyse whenever the savings tab is shown.
    //
    // Without this the screen keeps whatever it computed when the ViewModel was
    // created, so importing a statement and switching straight to Savings shows
    // the empty state over a full ledger — the app looking broken at exactly the
    // moment it should be proving its worth. Analysis is cheap; stale financial
    // figures are not.
    LaunchedEffect(destination) {
        if (destination == Destination.SAVINGS) savingsViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (destination) {
                            Destination.SAVINGS -> "Savings"
                            Destination.IMPORT -> "Import a statement"
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            Destination.SAVINGS -> SavingsScreen(
                state = savingsState,
                onTagMerchant = { taggingSubscription = it },
                modifier = Modifier.padding(innerPadding),
            )

            Destination.IMPORT -> ImportScreen(
                state = importState,
                onFilePicked = { uri -> importViewModel.onFilePicked(container.appContext, uri) },
                onConfirm = { preview -> importViewModel.confirm(preview) },
                onReset = {
                    importViewModel.reset()
                    // Re-analyse after an import so the savings figures reflect
                    // what was just added rather than showing stale numbers.
                    savingsViewModel.refresh()
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    taggingSubscription?.let { subscription ->
        MerchantTagSheet(
            subscription = subscription,
            onSelect = { serviceClass ->
                savingsViewModel.tagMerchant(subscription.merchantKey, serviceClass)
                taggingSubscription = null
            },
            onClearTag = {
                savingsViewModel.clearTag(subscription.merchantKey)
                taggingSubscription = null
            },
            onDismiss = { taggingSubscription = null },
        )
    }
}
