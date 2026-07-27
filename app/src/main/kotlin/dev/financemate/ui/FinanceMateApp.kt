package dev.financemate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.financemate.AppContainer
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.ui.import.ImportScreen
import dev.financemate.ui.import.ImportViewModel
import dev.financemate.ui.savings.MerchantTagSheet
import dev.financemate.ui.savings.SavingsScreen
import dev.financemate.ui.savings.SavingsViewModel
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.FinanceMateTheme

/**
 * Where the user can be.
 *
 * **Import is deliberately not here.** It is a task you do occasionally, not a
 * place you return to, and giving it a permanent slot would spend a quarter of
 * the navigation bar on something used once a month. It lives as the orange
 * square instead — one tap away, but not competing with the places.
 *
 * Home, Budget and Accounts are the destinations still to be built; only Savings
 * exists today, so the bar currently shows what is real.
 */
private enum class Destination(val label: String) {
    SAVINGS("Savings"),
}

@Composable
fun FinanceMateApp(container: AppContainer) {
    FinanceMateTheme {
        AppScaffold(container)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(container: AppContainer) {
    var destination by remember { mutableStateOf(Destination.SAVINGS) }
    var showImport by remember { mutableStateOf(false) }
    var taggingSubscription by remember { mutableStateOf<Subscription?>(null) }

    val savingsViewModel: SavingsViewModel = viewModel { SavingsViewModel(container) }
    val importViewModel: ImportViewModel = viewModel { ImportViewModel(container) }

    val savingsState by savingsViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    // Re-analyse whenever the savings view is shown, including on returning from
    // an import. Without this the screen keeps whatever it computed when the
    // ViewModel was created, so importing a statement and coming back shows the
    // empty state over a full ledger — the app looking broken at exactly the
    // moment it should be proving its worth.
    LaunchedEffect(destination, showImport) {
        if (!showImport) savingsViewModel.refresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showImport) "Import a statement" else destination.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            BottomBar(
                current = destination,
                importActive = showImport,
                onSelect = {
                    destination = it
                    showImport = false
                },
                onImport = { showImport = true },
            )
        },
    ) { innerPadding ->
        if (showImport) {
            ImportScreen(
                state = importState,
                onFilePicked = { uri -> importViewModel.onFilePicked(container.appContext, uri) },
                onConfirm = { preview -> importViewModel.confirm(preview) },
                onReset = { importViewModel.reset() },
                onDone = {
                    importViewModel.reset()
                    showImport = false
                },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            SavingsScreen(
                state = savingsState,
                onTagMerchant = { taggingSubscription = it },
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

/**
 * The navigation bar.
 *
 * The active tab is the only orange text on the bar, and the import square is
 * the only filled orange. That is the whole colour budget for this component —
 * roughly 5% of the screen, spent on where you are and what you can add.
 */
@Composable
private fun BottomBar(
    current: Destination,
    importActive: Boolean,
    onSelect: (Destination) -> Unit,
    onImport: () -> Unit,
) {
    val colours = FinanceMate.colours

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(12.dp),
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Destination.entries.forEach { entry ->
                val selected = entry == current && !importActive
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .background(
                            if (selected) colours.foundMoney else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (importActive) colours.foundMoneyBorder else colours.foundMoney,
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onImport),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Import a statement",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
