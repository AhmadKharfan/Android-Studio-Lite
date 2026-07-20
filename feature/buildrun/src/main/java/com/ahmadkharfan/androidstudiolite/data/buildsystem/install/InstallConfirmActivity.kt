package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class InstallConfirmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            finish()
            return
        }
        val confirm = PendingInstallPrompt.claimForLaunch()
        if (confirm != null) {
            val launched = runCatching {
                startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) {
                InstallPromptNotifier(applicationContext).cancel()
            }
        } else if (!PendingInstallPrompt.hasPending()) {

            val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
            packageManager.getLaunchIntentForPackage(packageName)?.let { launcherIntent ->
                startActivity(launcherIntent.putExtra("open_project_id", projectId))
            }
        }

        finish()
    }

    companion object {
        const val EXTRA_APK_LABEL = "apk_label"
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
