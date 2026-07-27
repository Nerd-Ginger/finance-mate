package dev.financemate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.financemate.ui.onboarding.EgressProofScreen
import dev.financemate.ui.onboarding.EgressProofViewModel
import dev.financemate.ui.onboarding.OnboardingStore
import dev.financemate.ui.onboarding.WelcomeScreen
import dev.financemate.ui.savings.MerchantTagSheet
import dev.financemate.ui.savings.SavingsScreen
import dev.financemate.ui.savings.SavingsViewModel
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.FinanceMateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FinanceMateApp(container: AppContainer) {
    FinanceMateTheme {
        val store = remember { OnboardingStore(container.appContext) }

        // Read **once**, with `first()` rather than by collecting the flow.
        //
        // This is not a micro-optimisation, it is a correctness requirement.
        // Finishing onboarding writes the flag, and if this were a live
        // subscription that write would flip the value, change the NavHost's
        // start destination, and rebuild the graph — discarding the back stack
        // mid-navigation and dumping the user on the start destination instead
        // of where they were going. Found exactly that way on the device.
        var startWithOnboarding by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) { startWithOnboarding = !store.hasCompleted.first() }

        // `null` means "not read yet". Rendering the welcome screen on that first
        // frame and then swapping it out would flash the introduction at every
        // returning user, which is worse than a blank frame nobody notices.
        when (val onboarding = startWithOnboarding) {
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            else -> AppScaffold(container, store, startWithOnboarding = onboarding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    container: AppContainer,
    store: OnboardingStore,
    startWithOnboarding: Boolean,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var taggingSubscription by remember { mutableStateOf<Subscription?>(null) }

    val savingsViewModel: SavingsViewModel = viewModel { SavingsViewModel(container) }
    val importViewModel: ImportViewModel = viewModel { ImportViewModel(container) }

    val savingsState by savingsViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val inImportFlow = destination?.inGraph<Routes.Import>() == true
    val inOnboarding = destination?.inGraph<Routes.Onboarding>() == true

    /**
     * Leaves onboarding for the import flow, permanently.
     *
     * `popUpTo(Onboarding) { inclusive = true }` is what makes it a one-way door:
     * somebody who has just imported their first statement must not be able to
     * press back into the welcome screen.
     *
     * Two navigations rather than one, deliberately. Going straight to Import
     * while popping onboarding leaves it alone on the back stack, so the first
     * back press quits the app from the middle of an import. Landing on Main
     * first gives the stack a floor to return to.
     */
    fun leaveOnboardingForImport() {
        scope.launch { store.markCompleted() }
        navController.navigate(Routes.Main) {
            popUpTo(Routes.Onboarding) { inclusive = true }
        }
        navController.navigate(Routes.Import)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Onboarding draws its own chrome. A bar reading "Savings" above the
            // welcome screen would name a place the user has not been yet.
            if (!inOnboarding) {
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
            }
        },
        bottomBar = {
            if (!inOnboarding) {
                BottomBar(
                    importActive = inImportFlow,
                    onSavings = { navController.toSavings() },
                    onImport = { navController.navigate(Routes.Import) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) Routes.Onboarding else Routes.Main,
            modifier = Modifier.padding(innerPadding),
        ) {
            navigation<Routes.Onboarding>(startDestination = Routes.Welcome) {
                composable<Routes.Welcome> {
                    WelcomeScreen(
                        // Straight into the import flow, and onboarding is done
                        // the moment they commit to it. Making them finish an
                        // import first would leave a user who backed out stuck
                        // seeing the welcome screen again.
                        onImport = { leaveOnboardingForImport() },
                        onSeeEgress = { navController.navigate(Routes.EgressProof) },
                    )
                }

                composable<Routes.EgressProof> {
                    val proofViewModel: EgressProofViewModel =
                        viewModel { EgressProofViewModel(container) }
                    val proofState by proofViewModel.state.collectAsStateWithLifecycle()

                    EgressProofScreen(
                        state = proofState,
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.popBackStack() },
                    )
                }
            }

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
