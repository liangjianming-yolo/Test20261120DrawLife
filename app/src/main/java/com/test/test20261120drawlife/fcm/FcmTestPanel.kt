package com.test.test20261120drawlife.fcm

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.test.test20261120drawlife.R

@Composable
internal fun FcmTestPanel() {
    val activity = LocalActivity.current ?: return
    if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

    Text(stringResource(R.string.fcm_title), style = MaterialTheme.typography.titleLarge)
    val configured = remember(activity) {
        FirebaseApp.getApps(activity).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
    }
    if (!configured) {
        Text(stringResource(R.string.fcm_not_configured))
        return
    }

    TextButton(onClick = { copyConsoleTestToken(activity) }) {
        Text(stringResource(R.string.fcm_copy_token))
    }
}

@Suppress("DEPRECATION") // The Firebase Console test sender accepts an FCM registration token.
private fun copyConsoleTestToken(activity: Activity) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener(activity) { result ->
        if (!result.isSuccessful) {
            Toast.makeText(activity, R.string.fcm_token_failed, Toast.LENGTH_SHORT).show()
            return@addOnCompleteListener
        }

        val clipboard = requireNotNull(activity.getSystemService(ClipboardManager::class.java))
        val clip = ClipData.newPlainText("FCM Token", result.result)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, R.string.fcm_token_copied, Toast.LENGTH_SHORT).show()
    }
}
