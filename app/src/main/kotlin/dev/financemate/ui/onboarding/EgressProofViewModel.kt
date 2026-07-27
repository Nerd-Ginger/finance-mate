package dev.financemate.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.financemate.AppContainer
import dev.financemate.egress.EgressLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the proof screen shows.
 *
 * [requestCount] is `null` only while the ledger is being opened. The screen
 * says "Checking…" rather than "0 requests" in that moment, because showing a
 * reassuring zero the app has not actually verified would undermine the exact
 * thing this screen is for.
 */
public data class EgressProofUiState(
    val requestCount: Int? = null,
    val entries: List<EgressLogEntry> = emptyList(),
) {
    val summaryLine: String
        get() = when (requestCount) {
            null -> "Checking…"
            0 -> "0 requests since install"
            1 -> "1 request since install"
            else -> "$requestCount requests since install"
        }
}

public class EgressProofViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(EgressProofUiState())
    public val state: StateFlow<EgressProofUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            val log = container.egressLog()
            _state.value = EgressProofUiState(
                requestCount = log.requestCount(),
                entries = log.recent(),
            )
        }
    }
}
