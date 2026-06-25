package bikefitstudio.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bikefitstudio.fit.FitSummary

/** Renders the [FitSummary] produced by [BikeFitAnalyzer]: grade, then prioritized recommendations. */
@Composable
fun FitReportScreen(summary: FitSummary, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Button(onClick = onBack) { Text("Back to home") }
        Spacer(Modifier.height(16.dp))
        Text("Fit report", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Grade: ${summary.grade}    •    Cycles analyzed: ${summary.cycleCount}    " +
                "•    Issues: ${summary.totalIssueCount} (${summary.highSeverityCount} high)"
        )
        Spacer(Modifier.height(16.dp))

        if (summary.recommendations.isEmpty()) {
            Text(
                if (summary.cycleCount == 0)
                    "Not enough complete pedal cycles were detected. Use a side-on clip of several " +
                        "full pedal strokes with the bottom bracket and spindle clearly visible."
                else
                    "No fit issues detected for the analyzed cycles."
            )
        } else {
            summary.recommendations.forEach { rec ->
                Text("[${rec.severity}]  ${rec.title}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(rec.description)
                Spacer(Modifier.height(2.dp))
                Text("→ ${rec.action}")
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
