package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.context.GlobalContext

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_REQUEST_TOKEN) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.confirmationIntentCompat()
            if (confirmation != null) {
                val labeled = confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.isProcessForeground()) {
                    if (InstallPromptClaimRegistry.claim(token)) {
                        runCatching { context.startActivity(labeled) }
                            .onFailure {
                                InstallPromptClaimRegistry.clear(token)
                                PendingInstallConfirmation.hold(token, labeled)
                                InstallPromptNotifier(context).notifyReady(
                                    intent.getStringExtra(EXTRA_APK_LABEL).orEmpty(), token, labeled,
                                )
                            }
                    }
                } else {
                    PendingInstallConfirmation.hold(token, labeled)
                    InstallPromptNotifier(context).notifyReady(
                        intent.getStringExtra(EXTRA_APK_LABEL).orEmpty(), token, labeled,
                    )
                }
            }
        } else {
            InstallPromptClaimRegistry.clear(token)
            PendingInstallConfirmation.clear(token)
            InstallPromptNotifier(context).cancel(token)
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val koin = runCatching { GlobalContext.get() }.getOrNull()
                    val success = status == PackageInstaller.STATUS_SUCCESS
                    runCatching { koin?.get<BuildRunCoordinator>() }
                        .getOrNull()
                        ?.onInstallTerminal(token, success)
                    val store = runCatching { koin?.get<ActiveBuildRepository>() }.getOrNull()
                    store?.get()?.takeIf { it.operationId == token }?.let { store.clear(it.buildId) }
                } finally {
                    pending.finish()
                }
            }
        }
        InstallStatusRelay.publish(intent)
    }

    companion object {
        const val EXTRA_REQUEST_TOKEN = "install_request_token"
        const val EXTRA_APK_LABEL = "install_apk_label"
    }
}

internal object InstallStatusRelay {
    private val events = MutableSharedFlow<Intent>(extraBufferCapacity = 32)
    val flow = events.asSharedFlow()
    fun publish(intent: Intent) { events.tryEmit(Intent(intent)) }
}

internal object InstallPromptClaimRegistry {
    private val claims = ConcurrentHashMap.newKeySet<String>()
    fun claim(token: String): Boolean = claims.add(token)
    fun clear(token: String) { claims.remove(token) }
}

object PendingInstallConfirmation {
    private val lock = Any()
    private val confirmations = LinkedHashMap<String, Intent>()

    fun hold(token: String, intent: Intent) = synchronized(lock) {
        confirmations[token] = Intent(intent)
    }

    fun claim(token: String): Intent? = synchronized(lock) {
        val intent = confirmations.remove(token) ?: return null
        if (!InstallPromptClaimRegistry.claim(token)) return null
        Intent(intent)
    }

    fun claimNext(): Intent? = synchronized(lock) {
        while (confirmations.isNotEmpty()) {
            val entry = confirmations.entries.first()
            confirmations.remove(entry.key)
            if (InstallPromptClaimRegistry.claim(entry.key)) return Intent(entry.value)
        }
        null
    }

    fun clear(token: String) = synchronized(lock) { confirmations.remove(token) }
}

private fun Context.isProcessForeground(): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return manager.runningAppProcesses.orEmpty().any {
        it.processName == packageName &&
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}

@Suppress("DEPRECATION")
private fun Intent.confirmationIntentCompat(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_INTENT)
    }
