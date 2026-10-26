package com.darahz.volumecontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darahz.volumecontrol.ui.theme.VolumeControlTheme

class MainActivity : ComponentActivity() {
    private var accessGranted by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var musicApps by mutableStateOf(emptyList<MusicApp>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPrefs(this)
        val openAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        setContent {
            var setupDone by remember { mutableStateOf(prefs.setupDone) }
            VolumeControlTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (setupDone) {
                        SettingsScreen(
                            prefs = prefs,
                            accessGranted = accessGranted,
                            onGrantAccess = ::openNotificationAccessSettings,
                            accessibilityEnabled = accessibilityEnabled,
                            onEnableAccessibility = openAccessibilitySettings,
                            musicApps = musicApps,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        SetupWizard(
                            accessGranted = accessGranted,
                            onGrantAccess = ::openNotificationAccessSettings,
                            accessibilityEnabled = accessibilityEnabled,
                            onEnableAccessibility = openAccessibilitySettings,
                            onFinish = { prefs.setupDone = true; setupDone = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time, since the user grants access in the system settings screen
        accessGranted = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent())
        accessibilityEnabled = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.name == VolumeKeyAccessibilityService::class.java.name }
        musicApps = musicApps()
    }

    private fun openNotificationAccessSettings() {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent().flattenToString())
        )
    }

    private fun listenerComponent() = ComponentName(this, VolumeKeyService::class.java)
}
