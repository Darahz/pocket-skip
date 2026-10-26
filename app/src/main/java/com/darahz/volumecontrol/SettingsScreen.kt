package com.darahz.volumecontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private enum class Page(val title: String) { Controls("Controls"), Apps("Apps"), Setup("Setup"), About("About") }

@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    musicApps: List<MusicApp>,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(Page.Controls) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        )
        PrimaryTabRow(selectedTabIndex = page.ordinal) {
            Page.entries.forEach { tab ->
                Tab(
                    selected = page == tab,
                    onClick = { page = tab },
                    text = {
                        if (tab == Page.Setup && !accessGranted) {
                            BadgedBox(badge = { Badge() }) { Text(tab.title) }
                        } else {
                            Text(tab.title)
                        }
                    },
                )
            }
        }
        // Each page keeps its own scroll position
        key(page) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    Page.Controls -> ControlsPage(prefs, accessGranted, onOpenSetup = { page = Page.Setup })
                    Page.Apps -> AppsPage(prefs, musicApps, accessibilityEnabled)
                    Page.Setup -> SetupPage(accessGranted, onGrantAccess, accessibilityEnabled, onEnableAccessibility)
                    Page.About -> AboutPage()
                }
            }
        }
    }
}

@Composable
private fun ControlsPage(prefs: AppPrefs, accessGranted: Boolean, onOpenSetup: () -> Unit) {
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var holdMs by remember { mutableFloatStateOf(prefs.holdMs.toFloat()) }
    var swap by remember { mutableStateOf(prefs.swapDirections) }

    if (!accessGranted) {
        Card(
            onClick = onOpenSetup,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                Text("Pocket Skip needs notification access before it can do anything. Tap to open Setup.")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = if (enabled) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch) { enabled = it; prefs.enabled = it }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (enabled) "On" else "Off", style = MaterialTheme.typography.displaySmall)
                Text(
                    when {
                        !enabled -> "Volume buttons work as normal"
                        prefs.startApp == null -> "Hold a volume button to skip tracks"
                        else -> "Hold a volume button to skip tracks or start music"
                    }
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }

    SectionLabel("Hold")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hold time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${holdMs.roundToInt()} ms",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = holdMs,
                onValueChange = { holdMs = it },
                onValueChangeFinished = { prefs.holdMs = holdMs.roundToInt() },
                valueRange = AppPrefs.MIN_HOLD_MS.toFloat()..AppPrefs.MAX_HOLD_MS.toFloat(),
                steps = (AppPrefs.MAX_HOLD_MS - AppPrefs.MIN_HOLD_MS) / 100 - 1,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingRow(
                title = "Swap directions",
                subtitle = if (swap) "Hold up: previous · Hold down: next" else "Hold up: next · Hold down: previous",
                checked = swap,
                onCheckedChange = { swap = it; prefs.swapDirections = it },
            )
        }
    }

    Text(
        "A short press changes the volume as usual. Hold a button for the hold time to skip, " +
            "and you'll feel a short vibration.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun AppsPage(prefs: AppPrefs, musicApps: List<MusicApp>, accessibilityEnabled: Boolean) {
    var allowedApps by remember { mutableStateOf(prefs.allowedApps) }
    var startApp by remember { mutableStateOf(prefs.startApp) }

    SectionLabel("Skip tracks in")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                if (allowedApps.isEmpty()) {
                    "Every app. Tick apps to only skip in those."
                } else {
                    "Only the ticked apps."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (musicApps.isEmpty()) {
                Text("No music apps found.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            musicApps.forEach { app ->
                val checked = app.packageName in allowedApps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, role = Role.Checkbox) {
                            allowedApps = if (it) allowedApps + app.packageName else allowedApps - app.packageName
                            prefs.allowedApps = allowedApps
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(app.label, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    SectionLabel("Start music")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hold either button while nothing is playing to start this app.")
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(musicApps.firstOrNull { it.packageName == startApp }?.label ?: startApp ?: "Off")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Off") },
                        onClick = { startApp = null; prefs.startApp = null; menuOpen = false },
                    )
                    musicApps.forEach { app ->
                        DropdownMenuItem(
                            text = { Text(app.label) },
                            onClick = { startApp = app.packageName; prefs.startApp = app.packageName; menuOpen = false },
                        )
                    }
                }
            }
            if (startApp != null && !accessibilityEnabled) {
                Text(
                    "Only works with the screen off until you turn on screen-on mode in Setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun SetupPage(
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
) {
    PermissionCard(
        title = "Notification access",
        granted = accessGranted,
        missingLabel = "Needed",
        text = if (accessGranted) {
            "The app can see and control what's playing."
        } else {
            "Needed so the app can skip tracks in your music app."
        },
        buttonText = "Grant access",
        onClick = onGrantAccess,
    )
    PermissionCard(
        title = "Screen-on mode",
        granted = accessibilityEnabled,
        missingLabel = "Recommended",
        text = if (accessibilityEnabled) {
            "You get the normal volume slider while the screen is on."
        } else {
            "Without it, the volume panel shows a cast-style slider while the screen is on. " +
                "Open Installed apps (or Downloaded apps) > Pocket Skip and turn it on."
        },
        buttonText = "Open accessibility settings",
        onClick = onEnableAccessibility,
    )
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    missingLabel: String,
    text: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(if (granted) "On" else missingLabel, ok = granted)
            }
            Text(text)
            if (!granted) Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text("Version $version", style = MaterialTheme.typography.labelLarge)
            Text(
                "Made so you can change tracks in your favorite music app during the cold winters, " +
                    "without taking your gloves off or your phone out of your pocket."
            )
            Text("Created with the help of Claude Code.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    SectionLabel("Permissions")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(
                "Notification access",
                "Android puts music controls under this permission. Pocket Skip uses it to see what's playing " +
                    "and to skip tracks.",
            )
            InfoRow(
                "Accessibility (screen-on mode)",
                "The only way for an app to catch volume button presses while the screen is on. " +
                    "Pocket Skip uses it for the volume buttons only.",
            )
            InfoRow("Vibration", "For the short buzz when a track is skipped.")
            InfoRow("Installed music apps", "To list the apps you can pick on the Apps tab.")
        }
    }

    SectionLabel("Privacy")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Nothing is collected. The app has no internet access, so it can't send anything anywhere: " +
                    "no tracking, no ads, no analytics."
            )
            Text(
                "Your settings are stored on your phone, and in your own Android backup if you have that turned on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusPill(text: String, ok: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
