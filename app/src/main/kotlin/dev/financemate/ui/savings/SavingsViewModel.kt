package dev.financemate.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.financemate.AppContainer
import dev.financemate.core.model.MerchantKey
import dev.financemate.feature.insight.subscription.ServiceClass
import dev.financemate.feature.insight.subscription.SubscriptionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the savings screen.
 *
 * The analysis is re-run rather than cached because it is cheap — a few tens of
 * thousands of rows grouped and sorted — and because a stale savings figure is
 * actively misleading. If the user has just tagged a merchant or imported a
 * statement, the numbers must reflect it.
 */
public class SavingsViewModel(
    /**
     * Takes the container rather than the repository, because obtaining one
     * means opening the encrypted database — a suspending operation that cannot
     * happen while the ViewModel is being constructed on the main thread.
     */
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow<SavingsUiState>(SavingsUiState.Loading)
    public val state: StateFlow<SavingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            _state.update { SavingsUiState.Loading }
            runCatching {
                val repository = container.savingsRepository()
                val report = repository.analyse()
                val untagged = repository.untaggedRecurringMerchants()
                SavingsUiState.Loaded(report = report, untaggedMerchantCount = untagged.size)
            }.onSuccess { loaded ->
                _state.update { loaded }
            }.onFailure { error ->
                _state.update {
                    SavingsUiState.Failed(
                        // Never surface the raw message: it can contain merchant
                        // names and amounts, and this string may reach a log.
                        message = "Could not analyse your transactions (${error.javaClass.simpleName}).",
                    )
                }
            }
        }
    }

    public fun tagMerchant(merchant: MerchantKey, serviceClass: ServiceClass?) {
        viewModelScope.launch {
            container.savingsRepository().setClassification(merchant, serviceClass)
            refresh()
        }
    }

    public fun clearTag(merchant: MerchantKey) {
        viewModelScope.launch {
            container.savingsRepository().clearClassification(merchant)
            refresh()
        }
    }
}

public sealed interface SavingsUiState {

    public data object Loading : SavingsUiState

    public data class Loaded(
        val report: SubscriptionReport,
        val untaggedMerchantCount: Int,
    ) : SavingsUiState {
        val hasAnyData: Boolean get() = report.subscriptions.isNotEmpty()
    }

    public data class Failed(val message: String) : SavingsUiState
}
