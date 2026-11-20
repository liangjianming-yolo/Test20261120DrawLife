package com.test.test20261120drawlife

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.test.test20261120drawlife.background.AlarmReminderScheduler
import com.test.test20261120drawlife.background.AlarmScheduleStatus
import com.test.test20261120drawlife.background.BackgroundTaskScheduler
import com.test.test20261120drawlife.background.DailyAlarmPlan
import com.test.test20261120drawlife.background.DrawLifeForegroundService
import com.test.test20261120drawlife.background.ReminderNotification
import com.test.test20261120drawlife.background.ToolbarNotification
import com.test.test20261120drawlife.ui.theme.Test20261120DrawLifeTheme
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var entryLabel by mutableIntStateOf(R.string.test_entry_home)
    private var notificationsEnabled by mutableStateOf(false)
    private var reminderNotificationsEnabled by mutableStateOf(false)
    private var exactAlarmAllowed by mutableStateOf(false)
    private var alarmPlan by mutableStateOf<DailyAlarmPlan?>(null)
    private var alarmStatus by mutableStateOf(AlarmScheduleStatus.DISABLED)
    private val alarmPlanListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        alarmPlan = AlarmReminderScheduler.readPlan(this)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(BackgroundTaskScheduler.LOG_TAG, "POST_NOTIFICATIONS granted=$granted")
        updateNotificationState()
        if (granted) {
            DrawLifeForegroundService.start(
                this,
                source = "notification_permission",
                refreshNotification = true
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readToolbarEntry(intent)
        enableEdgeToEdge()
        setContent {
            Test20261120DrawLifeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ForegroundTestScreen(
                        entry = stringResource(entryLabel),
                        notificationsEnabled = notificationsEnabled,
                        alarmPlan = alarmPlan,
                        alarmStatus = alarmStatus,
                        exactAlarmAllowed = exactAlarmAllowed,
                        reminderNotificationsEnabled = reminderNotificationsEnabled,
                        onSelectAlarmTime = ::showAlarmTimePicker,
                        onAlarmPermissionSettings = ::openAlarmPermissionSettings,
                        onCancelAlarm = {
                            alarmStatus = if (AlarmReminderScheduler.cancel(this)) {
                                AlarmScheduleStatus.DISABLED
                            } else {
                                AlarmScheduleStatus.ERROR
                            }
                            alarmPlan = AlarmReminderScheduler.readPlan(this)
                        },
                        onNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            })
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (
            savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        getSharedPreferences(AlarmReminderScheduler.PREFERENCES_NAME, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(alarmPlanListener)
    }

    override fun onStop() {
        getSharedPreferences(AlarmReminderScheduler.PREFERENCES_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(alarmPlanListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationState()
        // 从特殊权限页面返回时重新检查；用户拒绝授权则保留计划。
        exactAlarmAllowed = AlarmReminderScheduler.canScheduleExactAlarms(this)
        alarmStatus = AlarmReminderScheduler.restore(this, source = "activity_resume")
        alarmPlan = AlarmReminderScheduler.readPlan(this)
        // 返回应用时补试服务启动，并恢复可能已被用户划掉的工具栏。
        DrawLifeForegroundService.start(this, source = "activity_resume", refreshNotification = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readToolbarEntry(intent)
    }

    private fun readToolbarEntry(intent: Intent) {
        val entry = intent.getStringExtra(ToolbarNotification.EXTRA_ENTRY)
        entryLabel = when (entry) {
            ToolbarNotification.ENTRY_TRACKER -> R.string.toolbar_tracker
            ToolbarNotification.ENTRY_INFO -> R.string.toolbar_info
            ToolbarNotification.ENTRY_SETTINGS -> R.string.toolbar_settings
            else -> R.string.test_entry_home
        }
        Log.i(BackgroundTaskScheduler.LOG_TAG, "Activity opened, toolbar entry=${entry ?: "launcher"}")
    }

    private fun updateNotificationState() {
        val manager = requireNotNull(getSystemService(NotificationManager::class.java))
        val channel = manager.getNotificationChannel(ToolbarNotification.CHANNEL_ID)
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
        reminderNotificationsEnabled = ReminderNotification.notificationsEnabled(this)
    }

    private fun showAlarmTimePicker() {
        val plan = AlarmReminderScheduler.readPlan(this)
        val suggestedTime = ZonedDateTime.now().plusMinutes(2)
        TimePickerDialog(
            this,
            { _, hour, minute ->
                alarmStatus = AlarmReminderScheduler.setDailyReminder(this, hour, minute)
                alarmPlan = AlarmReminderScheduler.readPlan(this)
                exactAlarmAllowed = AlarmReminderScheduler.canScheduleExactAlarms(this)
            },
            plan?.hour ?: suggestedTime.hour,
            plan?.minute ?: suggestedTime.minute,
            true
        ).show()
    }

    private fun openAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            startActivity(Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            ))
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, R.string.alarm_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun ForegroundTestScreen(
    entry: String,
    notificationsEnabled: Boolean,
    alarmPlan: DailyAlarmPlan?,
    alarmStatus: AlarmScheduleStatus,
    exactAlarmAllowed: Boolean,
    reminderNotificationsEnabled: Boolean,
    onSelectAlarmTime: () -> Unit,
    onAlarmPermissionSettings: () -> Unit,
    onCancelAlarm: () -> Unit,
    onNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.notification_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.test_entry_format, entry))
        Text(stringResource(R.string.test_toolbar_instructions))
        if (!notificationsEnabled) {
            Text(stringResource(R.string.test_notifications_disabled))
        }
        TextButton(onClick = onNotificationSettings) {
            Text(stringResource(R.string.test_notification_settings))
        }

        Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.alarm_instructions))
        if (alarmPlan != null) {
            val nextTime = Instant.ofEpochMilli(alarmPlan.nextTriggerAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            Text(stringResource(
                R.string.alarm_plan_format, alarmPlan.hour, alarmPlan.minute, nextTime
            ))
        }
        Text(stringResource(when (alarmStatus) {
            AlarmScheduleStatus.DISABLED -> R.string.alarm_disabled
            AlarmScheduleStatus.SCHEDULED -> R.string.alarm_scheduled
            AlarmScheduleStatus.NEEDS_PERMISSION -> R.string.alarm_permission_needed
            AlarmScheduleStatus.ERROR -> R.string.alarm_error
        }))
        if (!reminderNotificationsEnabled) {
            Text(stringResource(R.string.alarm_notifications_disabled))
        }
        TextButton(onClick = onSelectAlarmTime) {
            Text(stringResource(R.string.alarm_select_time))
        }
        if (!exactAlarmAllowed) {
            TextButton(onClick = onAlarmPermissionSettings) {
                Text(stringResource(R.string.alarm_permission_settings))
            }
        }
        if (alarmPlan != null) {
            TextButton(onClick = onCancelAlarm) {
                Text(stringResource(R.string.alarm_cancel))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForegroundTestPreview() {
    Test20261120DrawLifeTheme {
        ForegroundTestScreen(
            entry = stringResource(R.string.test_entry_home),
            notificationsEnabled = true,
            alarmPlan = null,
            alarmStatus = AlarmScheduleStatus.DISABLED,
            exactAlarmAllowed = true,
            reminderNotificationsEnabled = true,
            onSelectAlarmTime = {},
            onAlarmPermissionSettings = {},
            onCancelAlarm = {},
            onNotificationSettings = {}
        )
    }
}
