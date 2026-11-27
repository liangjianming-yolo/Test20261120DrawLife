package com.test.test20261120drawlife

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.core.content.ContextCompat
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.background.AlarmScheduleStatus
import com.drawlife.backgroundtoolkit.background.DailyAlarmPlan
import com.test.test20261120drawlife.fcm.FcmTestPanel
import com.test.test20261120drawlife.ui.theme.Test20261120DrawLifeTheme
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class TestReminder(val key: String?, @StringRes val title: Int, @StringRes val message: Int) {
    WATER("water", R.string.alarm_water_title, R.string.alarm_water_message),
    HEART_RATE("heart_rate", R.string.alarm_heart_rate_title, R.string.alarm_heart_rate_message),
    DEFAULT(null, R.string.alarm_notification_title, R.string.alarm_notification_text)
}

class MainActivity : ComponentActivity() {
    private var entryLabel by mutableIntStateOf(R.string.test_entry_home)
    private var notificationsEnabled by mutableStateOf(false)
    private var reminderNotificationsEnabled by mutableStateOf(false)
    private var exactAlarmAllowed by mutableStateOf(false)
    private var alarmPlans by mutableStateOf(emptyMap<String?, DailyAlarmPlan>())
    private var alarmStatuses by mutableStateOf(emptyMap<String?, AlarmScheduleStatus>())
    private var alarmPlanSubscription: AutoCloseable? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(LOG_TAG, "POST_NOTIFICATIONS granted=$granted")
        updateNotificationState()
        if (granted) {
            BackgroundToolkit.refreshToolbar(this, source = "notification_permission")
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
                        alarmPlans = alarmPlans,
                        alarmStatuses = alarmStatuses,
                        exactAlarmAllowed = exactAlarmAllowed,
                        reminderNotificationsEnabled = reminderNotificationsEnabled,
                        onSelectAlarmTime = ::showAlarmTimePicker,
                        onScheduleSameMinute = ::scheduleSameMinute,
                        onAlarmPermissionSettings = ::openAlarmPermissionSettings,
                        onCancelAlarm = ::cancelReminder,
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
        alarmPlanSubscription = BackgroundToolkit.listenForDailyReminderChanges(this) {
            alarmPlans = BackgroundToolkit.readDailyReminders(this)
        }
    }

    override fun onStop() {
        alarmPlanSubscription?.close()
        alarmPlanSubscription = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationState()
        // 从特殊权限页面返回时重新检查；用户拒绝授权则保留计划。
        exactAlarmAllowed = BackgroundToolkit.canScheduleExactAlarms(this)
        alarmStatuses = BackgroundToolkit.restoreDailyReminders(this)
        alarmPlans = BackgroundToolkit.readDailyReminders(this)
        // 返回应用时补试服务启动，并恢复可能已被用户划掉的工具栏。
        BackgroundToolkit.refreshToolbar(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readToolbarEntry(intent)
    }

    private fun readToolbarEntry(intent: Intent) {
        val entry = BackgroundToolkit.toolbarEntry(intent)
        entryLabel = when (entry) {
            BackgroundToolkit.ToolbarEntry.TRACKER -> R.string.toolbar_tracker
            BackgroundToolkit.ToolbarEntry.INFO -> R.string.toolbar_info
            BackgroundToolkit.ToolbarEntry.SETTINGS -> R.string.toolbar_settings
            null -> R.string.test_entry_home
        }
        Log.i(LOG_TAG, "Activity opened, toolbar entry=${entry?.name?.lowercase() ?: "launcher"}")
    }

    private fun updateNotificationState() {
        notificationsEnabled = BackgroundToolkit.toolbarNotificationsEnabled(this)
        reminderNotificationsEnabled = BackgroundToolkit.reminderNotificationsEnabled(this)
    }

    private fun showAlarmTimePicker(reminder: TestReminder) {
        val plan = alarmPlans[reminder.key]
        val suggestedTime = ZonedDateTime.now().plusMinutes(2)
        TimePickerDialog(
            this,
            { _, hour, minute -> setReminderTime(reminder, hour, minute) },
            plan?.hour ?: suggestedTime.hour,
            plan?.minute ?: suggestedTime.minute,
            true
        ).apply { setTitle(reminder.title) }.show()
    }

    private fun scheduleSameMinute() {
        val time = ZonedDateTime.now().plusMinutes(2)
        setReminderTime(TestReminder.WATER, time.hour, time.minute)
        setReminderTime(TestReminder.HEART_RATE, time.hour, time.minute)
    }

    private fun setReminderTime(reminder: TestReminder, hour: Int, minute: Int) {
        val status = BackgroundToolkit.setDailyReminder(
            this, reminder.key, hour, minute, getString(reminder.title), getString(reminder.message)
        )
        alarmStatuses = alarmStatuses + (reminder.key to status)
        exactAlarmAllowed = BackgroundToolkit.canScheduleExactAlarms(this)
    }

    private fun cancelReminder(reminder: TestReminder) {
        val status = if (BackgroundToolkit.cancelDailyReminder(this, reminder.key)) {
            AlarmScheduleStatus.DISABLED
        } else {
            AlarmScheduleStatus.ERROR
        }
        alarmStatuses = alarmStatuses + (reminder.key to status)
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
    alarmPlans: Map<String?, DailyAlarmPlan>,
    alarmStatuses: Map<String?, AlarmScheduleStatus>,
    exactAlarmAllowed: Boolean,
    reminderNotificationsEnabled: Boolean,
    onSelectAlarmTime: (TestReminder) -> Unit,
    onScheduleSameMinute: () -> Unit,
    onAlarmPermissionSettings: () -> Unit,
    onCancelAlarm: (TestReminder) -> Unit,
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
        if (!reminderNotificationsEnabled) {
            Text(stringResource(R.string.alarm_notifications_disabled))
        }
        if (!exactAlarmAllowed) {
            TextButton(onClick = onAlarmPermissionSettings) {
                Text(stringResource(R.string.alarm_permission_settings))
            }
        }
        TextButton(onClick = onScheduleSameMinute) {
            Text(stringResource(R.string.alarm_schedule_same_minute))
        }
        for (reminder in TestReminder.entries) {
            AlarmTestItem(
                reminder = reminder,
                plan = alarmPlans[reminder.key],
                status = alarmStatuses[reminder.key] ?: AlarmScheduleStatus.DISABLED,
                onSelectTime = { onSelectAlarmTime(reminder) },
                onCancel = { onCancelAlarm(reminder) }
            )
        }

        FcmTestPanel()
    }
}

@Composable
private fun AlarmTestItem(
    reminder: TestReminder,
    plan: DailyAlarmPlan?,
    status: AlarmScheduleStatus,
    onSelectTime: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Text(stringResource(reminder.title), style = MaterialTheme.typography.titleMedium)
        if (plan != null) {
            val nextTime = Instant.ofEpochMilli(plan.nextTriggerAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            Text(stringResource(R.string.alarm_plan_format, plan.hour, plan.minute, nextTime))
        }
        Text(stringResource(when (status) {
            AlarmScheduleStatus.DISABLED -> R.string.alarm_disabled
            AlarmScheduleStatus.SCHEDULED -> R.string.alarm_scheduled
            AlarmScheduleStatus.NEEDS_PERMISSION -> R.string.alarm_permission_needed
            AlarmScheduleStatus.ERROR -> R.string.alarm_error
        }))
        Row {
            TextButton(onClick = onSelectTime) { Text(stringResource(R.string.alarm_select_time)) }
            if (plan != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.alarm_cancel)) }
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
            alarmPlans = emptyMap(),
            alarmStatuses = emptyMap(),
            exactAlarmAllowed = true,
            reminderNotificationsEnabled = true,
            onSelectAlarmTime = {},
            onScheduleSameMinute = {},
            onAlarmPermissionSettings = {},
            onCancelAlarm = {},
            onNotificationSettings = {}
        )
    }
}
