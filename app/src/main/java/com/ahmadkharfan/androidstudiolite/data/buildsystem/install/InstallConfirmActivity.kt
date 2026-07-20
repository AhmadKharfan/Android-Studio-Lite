package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.ahmadkharfan.androidstudiolite.MainActivity

/**
 * Transparent trampoline launched from a notification tap (user gesture → allowed to start
 * activities). Shows the PackageInstaller confirmation sheet over other apps.
 */
class InstallConfirmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val confirm = PendingInstallPrompt.peek()
        if (confirm != null) {
            PendingInstallPrompt.recordLaunchAttempt()
            val launched = runCatching {
                startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) {
                InstallPromptNotifier(applicationContext).cancel()
            }
        } else {
            // Build-result notification tapped before install was ready, or prompt already consumed.
            val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
            startActivity(MainActivity.openProjectIntent(this, projectId))
        }
        finish()
    }

    companion object {
        const val EXTRA_APK_LABEL = "apk_label"
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
