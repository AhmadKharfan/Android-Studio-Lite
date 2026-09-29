package com.ahmadkharfan.androidstudiolite.feature.buildrun.install

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether one of this app's activities is resumed, i.e. nothing else is on top of it.
 *
 * Process importance can't answer that: a dialog-style activity from another app (such as the system
 * install confirmation) leaves this process "foreground" while covering it, but it does pause our
 * activity. Tracking starts when [install] is first called; until an activity resumes after that, the
 * answer is false, which errs on the side of waiting.
 */
internal object ResumedActivities : Application.ActivityLifecycleCallbacks {

    private val resumed = AtomicInteger(0)

    @Volatile private var installed = false

    val anyResumed: Boolean get() = resumed.get() > 0

    fun install(application: Application) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            application.registerActivityLifecycleCallbacks(this)
            installed = true
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed.incrementAndGet()
    }

    override fun onActivityPaused(activity: Activity) {
        resumed.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
