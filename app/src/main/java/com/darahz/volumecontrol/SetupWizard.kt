package com.darahz.volumecontrol

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class Step { Welcome, Notifications, Accessibility, Done }

/** First-run walkthrough that gets the permissions sorted, then hands over to [SettingsScreen]. */
@Composable
fun SetupWizard(
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    BackHandler(enabled = step != Step.Welcome) { step = Step.entries[step.ordinal - 1] }
    val next = { if (step == Step.Done) onFinish() else step = Step.entries[step.ordinal + 1] }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / Step.entries.size },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "step") { shown ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                when (shown) {
                    Step.Welcome -> StepText(
                        title = "Pocket Skip",
                        text = "Skip tracks by holding the volume buttons, even with the screen off. " +
                            "A short press still changes the volume.",
                    )
                    Step.Notifications -> {
                        StepText(
                            title = "Notification access",
                            text = "Pocket Skip needs this to see what's playing and to skip tracks in your music app.",
                        )
                        if (accessGranted) {
                            StatusPill("Granted", ok = true)
                        } else {
                            FilledTonalButton(onClick = onGrantAccess) { Text("Grant access") }
                        }
                    }
                    Step.Accessibility -> {
                        StepText(
                            title = "Screen-on mode",
                            text = "Recommended. Without it, the volume panel shows a cast-style slider while the " +
                                "screen is on. Open Installed apps (or Downloaded apps) > Pocket Skip and turn it on.",
                        )
                        if (accessibilityEnabled) {
                            StatusPill("On", ok = true)
                        } else {
                            FilledTonalButton(onClick = onEnableAccessibility) { Text("Open accessibility settings") }
                        }
                    }
                    Step.Done -> StepText(
                        title = "You're all set",
                        text = "Hold a volume button to skip tracks. Pick apps, change the hold time and more in the app.",
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != Step.Welcome) {
                TextButton(onClick = { step = Step.entries[step.ordinal - 1] }) { Text("Back") }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = next, enabled = step != Step.Notifications || accessGranted) {
                Text(
                    when {
                        step == Step.Welcome -> "Get started"
                        step == Step.Accessibility && !accessibilityEnabled -> "Skip for now"
                        step == Step.Done -> "Done"
                        else -> "Next"
                    }
                )
            }
        }
    }
}

@Composable
private fun StepText(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
