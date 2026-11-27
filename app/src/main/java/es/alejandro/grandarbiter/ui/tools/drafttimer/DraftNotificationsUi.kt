package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.home.UpdatedLabel

/**
 * Whether the draft timer's notification can be seen. The timer runs either way; this only decides
 * whether its notification shows. [OFF]: the permission can still be asked for. [BLOCKED]: only
 * the system settings can change it (refused twice, notifications turned off there, or Android 12
 * and older, which has no dialog).
 */
enum class NotificationAccess { ON, OFF, BLOCKED }

class NotificationAccessState(
    val access: NotificationAccess,
    /** OFF: the permission dialog. BLOCKED or ON: the app's notification settings. */
    val change: () -> Unit,
)

/** Re-read every time the screen comes back (the user may have changed it in the system settings). */
@Composable
fun rememberNotificationAccess(asked: Boolean, onAsked: () -> Unit): NotificationAccessState {
    val context = LocalContext.current
    var access by remember { mutableStateOf(notificationAccess(context, asked)) }
    var launchedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = notificationAccess(context, asked = true)
        // Refused with no dialog shown (turned off in the system settings before the app ever asked):
        // the answer comes back at once. Go straight to the settings instead of a tap that does nothing.
        if (!granted && access == NotificationAccess.BLOCKED &&
            SystemClock.elapsedRealtime() - launchedAt < NO_DIALOG_MS) openNotificationSettings(context)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, asked) {
        access = notificationAccess(context, asked)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = notificationAccess(context, asked)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return NotificationAccessState(access) {
        if (access == NotificationAccess.OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onAsked()
            launchedAt = SystemClock.elapsedRealtime()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }
}

fun notificationAccess(context: Context, asked: Boolean): NotificationAccess {
    val manager = NotificationManagerCompat.from(context)
    val channelOff = manager.getNotificationChannel(DraftNotification.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE
    if (manager.areNotificationsEnabled() && !channelOff) return NotificationAccess.ON
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || channelOff) return NotificationAccess.BLOCKED
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (granted) return NotificationAccess.BLOCKED   // allowed, but turned off in the system settings
    val activity = context.findActivity()
    val canAsk = !asked || (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
    return if (canAsk) NotificationAccess.OFF else NotificationAccess.BLOCKED
}

/** A refusal faster than this can't have come from a person reading the dialog. */
private const val NO_DIALOG_MS = 500L

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Notifications": what the timer's notification does now, and the way to change it. Not a
 * switch: the system decides, so the row shows what is true and leads to where it changes.
 * ON and BLOCKED: the whole row opens the system settings (↗, it leaves the app). OFF: a "Turn on"
 * button, which asks.
 */
@Composable
fun NotificationsRow(state: NotificationAccessState, titleStyle: TextStyle, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val leaves = state.access != NotificationAccess.OFF
    Row(
        modifier.fillMaxWidth()
            .then(if (leaves) Modifier.clickable(role = Role.Button, onClick = state.change) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.draft_notif_row_title), style = titleStyle, color = colors.onSurface)
            Text(
                stringResource(if (state.access == NotificationAccess.ON) R.string.draft_notif_row_on else R.string.draft_notif_row_off),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
        }
        if (leaves) {
            Text(stringResource(if (state.access == NotificationAccess.ON) R.string.draft_notif_on else R.string.draft_notif_off),
                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        } else {
            OutlinedButton(onClick = state.change, border = BorderStroke(1.dp, colors.outline)) {
                Text(stringResource(R.string.draft_notif_turn_on), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        }
    }
}

/** The first draft setup after the update: the timer now runs in the background; turn on its notification? */
@Composable
fun NotificationsCard(onSetUp: () -> Unit, onNotNow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DraftCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.draft_notif_card_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface,
                modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.padding(top = 2.dp)) { UpdatedLabel(R.string.label_new) }
        }
        Text(stringResource(R.string.draft_notif_card_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.draft_notif_not_now), style = MaterialTheme.typography.labelLarge) }
            Button(onClick = onSetUp) { Text(stringResource(R.string.draft_notif_set_up), style = MaterialTheme.typography.labelLarge) }
        }
    }
}
