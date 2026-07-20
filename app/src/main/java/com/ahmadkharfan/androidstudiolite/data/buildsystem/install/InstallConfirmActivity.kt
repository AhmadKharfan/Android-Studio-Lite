package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.ahmadkharfan.androidstudiolite.MainActivity

/**
 * Transparent trampoline launched from a notification tap (user gesture → allowed to start
 * activities). Shows the PackageInstaller confirmation sheet over other apps.
 *
 * Uses [PendingInstallPrompt.claimForLaunch] so a racing MainActivity.onResume cannot open a
 * second copy of the same system install dialog.
 */
class InstallConfirmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Avoid re-running after rotation / process restore of this transparent activity.
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
            // Nothing to install — open the project editor instead.
            val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
            startActivity(MainActivity.openProjectIntent(this, projectId))
        }
        // If hasPending but claim failed, another launcher already showed the sheet — do nothing.
        finish()
    }

    companion object {
        const val EXTRA_APK_LABEL = "apk_label"
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
