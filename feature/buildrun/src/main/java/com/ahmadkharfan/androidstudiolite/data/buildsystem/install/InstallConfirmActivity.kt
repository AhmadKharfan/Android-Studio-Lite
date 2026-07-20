package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class InstallConfirmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            finish()
            return
        }
        val token = intent.getStringExtra(EXTRA_REQUEST_TOKEN).orEmpty()
        val embedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIRMATION_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIRMATION_INTENT)
        }
        val confirm = PendingInstallConfirmation.claim(token) ?: embedded?.takeIf {
            InstallPromptClaimRegistry.claim(token)
        }
        if (confirm != null) {
            val launched = runCatching {
                startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) {
                InstallPromptNotifier(applicationContext).cancel(token)
            } else {
                InstallPromptClaimRegistry.clear(token)
            }
        } else {
            val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
            packageManager.getLaunchIntentForPackage(packageName)?.let { launcherIntent ->
                startActivity(
                    launcherIntent.putExtra(
                        "com.ahmadkharfan.androidstudiolite.OPEN_PROJECT_ID",
                        projectId,
                    ),
                )
            }
        }

        finish()
    }

    companion object {
        const val EXTRA_APK_LABEL = "apk_label"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_CONFIRMATION_INTENT = "confirmation_intent"
        const val EXTRA_REQUEST_TOKEN = "request_token"
    }
}
