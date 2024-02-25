package it.decoder.music.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.decoder.music.Database
import it.decoder.music.DatabaseInitializer
import it.decoder.music.LocalPlayerServiceBinder
import it.decoder.music.R
import it.decoder.music.preferences.AppearancePreferences
import it.decoder.music.preferences.DataPreferences
import it.decoder.music.preferences.PlayerPreferences
import it.decoder.music.query
import it.decoder.music.service.PlayerMediaBrowserService
import it.decoder.music.ui.components.themed.SecondaryTextButton
import it.decoder.music.ui.components.themed.SliderDialog
import it.decoder.music.ui.screens.Route
import it.decoder.music.utils.findActivity
import it.decoder.music.utils.isAtLeastAndroid12
import it.decoder.music.utils.isAtLeastAndroid6
import it.decoder.music.utils.isIgnoringBatteryOptimizations
import it.decoder.music.utils.smoothScrollToBottom
import it.decoder.music.utils.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@SuppressLint("BatteryLife")
@Route
@Composable
fun OtherSettings() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val uriHandler = LocalUriHandler.current

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isAndroidAutoEnabled by remember {
        val component = ComponentName(context, PlayerMediaBrowserService::class.java)
        val disabledFlag = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val enabledFlag = PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        mutableStateOf(
            value = context.packageManager.getComponentEnabledSetting(component) == enabledFlag,
            policy = object : SnapshotMutationPolicy<Boolean> {
                override fun equivalent(a: Boolean, b: Boolean): Boolean {
                    context.packageManager.setComponentEnabledSetting(
                        component,
                        if (b) enabledFlag else disabledFlag,
                        PackageManager.DONT_KILL_APP
                    )
                    return a == b
                }
            }
        )
    }

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations }
    )

    val queriesCount by remember {
        Database.queriesCount().distinctUntilChanged()
    }.collectAsState(initial = 0)

    SettingsCategoryScreen(
        title = stringResource(R.string.other),
        scrollState = scrollState
    ) {
        SettingsGroup(title = stringResource(R.string.android_auto)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.android_auto),
                text = stringResource(R.string.android_auto_description),
                isChecked = isAndroidAutoEnabled,
                onCheckedChange = { isAndroidAutoEnabled = it }
            )

            AnimatedVisibility(visible = isAndroidAutoEnabled) {
                SettingsDescription(text = stringResource(R.string.android_auto_warning))
            }
        }
        SettingsGroup(title = stringResource(R.string.search_history)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.pause_search_history),
                text = stringResource(R.string.pause_search_history_description),
                isChecked = DataPreferences.pauseSearchHistory,
                onCheckedChange = { DataPreferences.pauseSearchHistory = it }
            )

            AnimatedVisibility(visible = !(DataPreferences.pauseSearchHistory && queriesCount == 0)) {
                SettingsEntry(
                    title = stringResource(R.string.clear_search_history),
                    text = if (queriesCount > 0) stringResource(
                        R.string.format_clear_search_history_amount,
                        queriesCount
                    )
                    else stringResource(R.string.empty_history),
                    onClick = { query(Database::clearQueries) },
                    isEnabled = queriesCount > 0
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.built_in_playlists)) {
            IntSettingsEntry(
                title = stringResource(R.string.top_list_length),
                text = stringResource(R.string.top_list_length_description),
                currentValue = DataPreferences.topListLength,
                setValue = { DataPreferences.topListLength = it },
                defaultValue = 10,
                range = 1..500
            )
        }
        SettingsGroup(title = stringResource(R.string.quick_picks)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.quick_picks_source),
                selectedValue = DataPreferences.quickPicksSource,
                onValueSelected = { DataPreferences.quickPicksSource = it },
                valueText = { it.displayName() }
            )
        }
        SettingsGroup(title = stringResource(R.string.dynamic_thumbnails)) {
            var selectingThumbnailSize by remember { mutableStateOf(false) }
            SettingsEntry(
                title = stringResource(R.string.max_dynamic_thumbnail_size),
                text = stringResource(R.string.max_dynamic_thumbnail_size_description),
                onClick = {
                    selectingThumbnailSize = true
                }
            )
            if (selectingThumbnailSize) SliderDialog(
                onDismiss = { selectingThumbnailSize = false },
                title = stringResource(R.string.max_dynamic_thumbnail_size),
                provideState = {
                    remember(AppearancePreferences.maxThumbnailSize) {
                        mutableFloatStateOf(AppearancePreferences.maxThumbnailSize.toFloat())
                    }
                },
                onSlideCompleted = { AppearancePreferences.maxThumbnailSize = it.roundToInt() },
                min = 16f,
                max = 2160f,
                toDisplay = {
                    stringResource(
                        R.string.format_px,
                        it.roundToInt()
                    )
                }
            )
        }
        SettingsGroup(title = stringResource(R.string.service_lifetime)) {
            AnimatedVisibility(visible = !isIgnoringBatteryOptimizations) {
                SettingsDescription(
                    text = stringResource(R.string.service_lifetime_warning),
                    important = true
                )
            }

            if (isAtLeastAndroid12) SettingsDescription(
                text = stringResource(R.string.service_lifetime_warning_android_12)
            )

            SettingsEntry(
                title = stringResource(R.string.ignore_battery_optimizations),
                text = if (isIgnoringBatteryOptimizations) stringResource(R.string.ignoring_battery_optimizations)
                else stringResource(R.string.ignore_battery_optimizations_action),
                onClick = {
                    if (!isAtLeastAndroid6) return@SettingsEntry

                    try {
                        activityResultLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } catch (e: ActivityNotFoundException) {
                        try {
                            activityResultLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            context.toast(context.getString(R.string.no_battery_optimization_settings_found))
                        }
                    }
                },
                isEnabled = !isIgnoringBatteryOptimizations
            )

            AnimatedVisibility(!isAtLeastAndroid12 || isIgnoringBatteryOptimizations) {
                SwitchSettingsEntry(
                    title = stringResource(R.string.invincible_service),
                    text = stringResource(R.string.invincible_service_description),
                    isChecked = PlayerPreferences.isInvincibilityEnabled,
                    onCheckedChange = { PlayerPreferences.isInvincibilityEnabled = it }
                )
            }

            SettingsEntry(
                title = stringResource(R.string.need_help),
                text = stringResource(R.string.need_help_description),
                onClick = {
                    uriHandler.openUri("https://dontkillmyapp.com/")
                }
            )

            SettingsDescription(text = stringResource(R.string.service_lifetime_report_issue))
        }

        var showTroubleshoot by rememberSaveable { mutableStateOf(false) }

        AnimatedContent(showTroubleshoot, label = "") { show ->
            if (show) SettingsGroup(
                title = stringResource(R.string.troubleshooting),
                description = stringResource(R.string.troubleshooting_warning),
                important = true
            ) {
                val troubleshootScope = rememberCoroutineScope()
                var reloading by rememberSaveable { mutableStateOf(false) }

                SecondaryTextButton(
                    text = stringResource(R.string.reload_app_internals),
                    onClick = {
                        if (!reloading) troubleshootScope.launch {
                            reloading = true
                            binder?.restartForegroundOrStop()
                            DatabaseInitializer.reload()
                            reloading = false
                        }
                    },
                    enabled = !reloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryTextButton(
                    text = stringResource(R.string.kill_app),
                    onClick = {
                        binder?.stopRadio()
                        binder?.invincible = false
                        context.findActivity().finishAndRemoveTask()
                        binder?.restartForegroundOrStop()
                        troubleshootScope.launch {
                            delay(500L)
                            Handler(Looper.getMainLooper()).postAtFrontOfQueue { exitProcess(0) }
                        }
                    },
                    enabled = !reloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .padding(horizontal = 16.dp)
                )
            } else SecondaryTextButton(
                text = stringResource(R.string.show_troubleshoot_section),
                onClick = {
                    coroutineScope.launch {
                        delay(500)
                        scrollState.smoothScrollToBottom()
                    }
                    showTroubleshoot = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}
