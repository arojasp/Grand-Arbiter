package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.alejandro.grandarbiter.R

/** Whether the app is exempt from battery optimization. Re-read every time the screen comes back. */
@Composable
fun rememberBatteryUnrestricted(): Boolean {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(batteryUnrestricted(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) unrestricted = batteryUnrestricted(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return unrestricted
}

fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** The app's system info page, where its battery setting is; the battery list if that can't open. */
fun openBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(details) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * Battery status in Settings › Alerts and notifications. Some phones (vivo, 2026-11-27) freeze the app
 * with the screen off during a long step, foreground service or not, unless its battery use is
 * unrestricted. The app can't change it: the whole row opens the system page.
 */
@Composable
fun BatteryRow(unrestricted: Boolean, titleStyle: TextStyle, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth()
            .clickable(role = Role.Button) { openBatterySettings(context) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.draft_battery_row_title), style = titleStyle, color = colors.onSurface)
            Text(
                stringResource(if (unrestricted) R.string.draft_battery_row_unrestricted else R.string.draft_battery_row_optimized),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
        }
        Text(stringResource(if (unrestricted) R.string.draft_battery_unrestricted else R.string.draft_battery_optimized),
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
