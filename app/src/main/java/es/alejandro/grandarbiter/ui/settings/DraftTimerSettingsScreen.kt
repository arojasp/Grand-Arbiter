package es.alejandro.grandarbiter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.draft.BeepVolume
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.VibrationStrength
import es.alejandro.grandarbiter.ui.tools.drafttimer.NotificationsRow
import es.alejandro.grandarbiter.ui.tools.drafttimer.BatteryRow
import es.alejandro.grandarbiter.ui.tools.drafttimer.rememberBatteryUnrestricted
import es.alejandro.grandarbiter.ui.tools.drafttimer.rememberNotificationAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Settings › Draft timer › Alerts and notifications: whether a step end vibrates and beeps, how,
 * and whether the timer shows in the notifications. The same switches as the draft setup (one
 * stored value), so the app can be prepared without starting a draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftTimerSettingsScreen(onBack: () -> Unit, viewModel: DraftTimerSettingsViewModel = koinViewModel()) {
    val colors = MaterialTheme.colorScheme
    val vibrate by viewModel.vibrate.collectAsState()
    val strength by viewModel.vibrationStrength.collectAsState()
    val beep by viewModel.beep.collectAsState()
    val volume by viewModel.beepVolume.collectAsState()
    val muted by viewModel.beepMuted.collectAsState()
    val asked by viewModel.notificationPermissionAsked.collectAsState()
    val notifications = rememberNotificationAccess(asked, viewModel::setNotificationPermissionAsked)
    val battery = rememberBatteryUnrestricted()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.settings_draft_alerts), style = MaterialTheme.typography.titleLarge, color = colors.primary)
                        Text(stringResource(R.string.settings_draft_timer), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsPanel {
                    SwitchRow(stringResource(R.string.draft_vibrate), stringResource(R.string.draft_vibrate_hint), vibrate, viewModel::setVibrate)
                    PanelDivider()
                    LevelRow(stringResource(R.string.settings_vibration_strength), VibrationStrength.entries, strength,
                        { stringResource(it.label()) }, viewModel::setVibrationStrength,
                        stringResource(R.string.settings_test_vibration), viewModel::testVibration)
                }
            }
            item {
                SettingsPanel {
                    SwitchRow(stringResource(R.string.draft_beep), stringResource(R.string.settings_draft_beep_hint), beep, viewModel::setBeep)
                    PanelDivider()
                    LevelRow(stringResource(R.string.settings_beep_volume), BeepVolume.entries, volume,
                        { stringResource(it.label()) }, viewModel::setBeepVolume,
                        stringResource(R.string.settings_test_beep), viewModel::testBeep,
                        notice = stringResource(R.string.settings_beep_alarm_muted).takeIf { muted })
                }
            }
            item {
                SettingsPanel {
                    NotificationsRow(notifications, MaterialTheme.typography.titleMedium)
                    PanelDivider()
                    BatteryRow(battery, MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

class DraftTimerSettingsViewModel(private val settings: SettingsDataStore, private val alerts: DraftAlerts) : ViewModel() {
    val vibrate: StateFlow<Boolean> = settings.draftVibrate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val beep: StateFlow<Boolean> = settings.draftBeep.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val vibrationStrength: StateFlow<VibrationStrength> = settings.draftVibrationStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VibrationStrength.DEFAULT)
    val beepVolume: StateFlow<BeepVolume> = settings.draftBeepVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeepVolume.DEFAULT)
    val beepMuted: StateFlow<Boolean> = alerts.beepMuted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notificationPermissionAsked: StateFlow<Boolean> = settings.notificationPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setVibrate(on: Boolean) = viewModelScope.launch { settings.setDraftVibrate(on) }
    fun setBeep(on: Boolean) = viewModelScope.launch { settings.setDraftBeep(on) }
    fun setVibrationStrength(strength: VibrationStrength) = viewModelScope.launch { settings.setDraftVibrationStrength(strength) }
    fun setBeepVolume(volume: BeepVolume) = viewModelScope.launch { settings.setDraftBeepVolume(volume) }
    fun testVibration() = alerts.vibrate(vibrationStrength.value)
    fun testBeep() = alerts.beep(beepVolume.value)
    fun setNotificationPermissionAsked() = viewModelScope.launch { settings.setNotificationPermissionAsked() }
}
