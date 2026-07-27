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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import dev.financemate.AppContainer
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.ui.import.ImportScreen
import dev.financemate.ui.import.ImportViewModel
import dev.financemate.ui.navigation.Routes
import dev.financemate.ui.savings.MerchantTagSheet
import dev.financemate.ui.savings.SavingsScreen
import dev.financemate.ui.savings.SavingsViewModel
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.FinanceMateTheme

@Composable
fun FinanceMateApp(container: AppContainer) {
    FinanceMateTheme {
        AppScaffold(container)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(container: AppContainer) {
    val navController = rememberNavController()
    var taggingSubscription by remember { mutableStateOf<Subscription?>(null) }

    val savingsViewModel: SavingsViewModel = viewModel { SavingsViewModel(container) }
    val importViewModel: ImportViewModel = viewModel { ImportViewModel(container) }

    val savingsState by savingsViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val inImportFlow = backStackEntry?.destination?.inGraph<Routes.Import>() == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (inImportFlow) "Import a statement" else "Savings",
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
                importActive = inImportFlow,
                onSavings = { navController.toSavings() },
                onImport = { navController.navigate(Routes.Import) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Main,
            modifier = Modifier.padding(innerPadding),
        ) {
            navigation<Routes.Main>(startDestination = Routes.Savings) {
                composable<Routes.Savings> {
                    // Re-analyse each time this becomes current. Without it the
                    // screen keeps whatever it computed when the ViewModel was
                    // created, so returning from an import shows the empty state
                    // over a full ledger.
                    LaunchedEffect(Unit) { savingsViewModel.refresh() }
                    SavingsScreen(
                        state = savingsState,
                        onTagMerchant = { taggingSubscription = it },
                    )
                }
            }

            // Import is its own graph: a multi-step task with internal back
            // behaviour, entered and left as a unit rather than a place.
            navigation<Routes.Import>(startDestination = Routes.ImportSource) {
                composable<Routes.ImportSource> {
                    ImportScreen(
                        state = importState,
                        onFilePicked = { uri ->
                            importViewModel.onFilePicked(container.appContext, uri)
                        },
                        onConfirm = { preview -> importViewModel.confirm(preview) },
                        onReset = { importViewModel.reset() },
                        onDone = {
                            importViewModel.reset()
                            navController.toSavings()
                        },
                    )
                }
            }
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
 * Returns to savings without stacking duplicates.
 *
 * `launchSingleTop` plus popping back to the graph start means repeatedly
 * finishing imports cannot build a tower of savings screens the user has to
 * press back through.
 */
private fun NavHostController.toSavings() {
    navigate(Routes.Savings) {
        popUpTo(Routes.Main) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * True when [T] is this destination or any graph containing it.
 *
 * The bar needs to know "are we somewhere inside import", not "are we on the
 * source screen", so it asks about the whole parent chain.
 */
private inline fun <reified T : Any> NavDestination.inGraph(): Boolean =
    hierarchy.any { it.hasRoute<T>() }

/**
 * The navigation bar.
 *
 * The active tab is the only orange *text*, and the import square the only
 * filled orange. That is the whole colour budget for this component. The first
 * version filled the active tab too, which put two solid orange blocks side by
 * side and made neither of them mean anything — hence the screenshot test.
 *
 * Only Savings is shown because only Savings exists. Budget and Accounts appear
 * as they are built — a dead tab that navigates nowhere is worse than a bar with
 * one entry.
 */
@Composable
internal fun BottomBar(
    importActive: Boolean,
    onSavings: () -> Unit,
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
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(4.dp),
        ) {
            Text(
                text = "Savings",
                style = MaterialTheme.typography.labelMedium,
                color = if (!importActive) {
                    colours.foundMoney
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .background(
                        if (!importActive) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(onClick = onSavings)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
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
