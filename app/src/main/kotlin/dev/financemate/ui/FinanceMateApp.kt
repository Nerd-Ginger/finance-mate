package dev.financemate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.financemate.ui.theme.FinanceMateTheme

@Composable
fun FinanceMateApp() {
    FinanceMateTheme {
        HomeScaffold()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("FinanceMate") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Set-up in progress",
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusCard(
                title = "On-device by default",
                body = "Statement parsing, categorisation, and every savings " +
                    "analysis run locally. Nothing leaves this device unless you " +
                    "explicitly turn on an AI feature.",
            )
            StatusCard(
                title = "Next up",
                body = "Import pipeline, budgeting, then the savings engine that " +
                    "finds duplicate subscriptions, price rises, and avoidable fees.",
            )
            StatusCard(
                title = if (AiStatus.isConfigured) "AI enabled" else "AI off",
                body = buildString {
                    append(
                        if (AiStatus.isConfigured) {
                            "Requests use your own API key. "
                        } else {
                            "Add your own Anthropic API key to switch on the " +
                                "optional AI features. Nothing is sent until you do. "
                        },
                    )
                    append("Model: ${AiStatus.selectedModel.displayName} ")
                    append(
                        "($${AiStatus.selectedModel.inputPricePerMTokUsd} in / " +
                            "$${AiStatus.selectedModel.outputPricePerMTokUsd} out per million tokens).",
                    )
                },
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    FinanceMateTheme(dynamicColor = false) { HomeScaffold() }
}
