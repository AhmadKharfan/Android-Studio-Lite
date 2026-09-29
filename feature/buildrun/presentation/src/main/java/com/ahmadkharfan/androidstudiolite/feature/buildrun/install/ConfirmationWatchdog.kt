package com.ahmadkharfan.androidstudiolite.feature.buildrun.install

/**
 * Notices when the system's install confirmation disappears without an answer.
 *
 * The confirmation screen belongs to another app. If it crashes (seen on emulators when the package
 * installer starts cold), no result is ever broadcast and the install would wait forever. The prompt is
 * considered gone once one of this app's activities has been resumed for [graceTicks] consecutive checks
 * while the session is still pending: while the prompt shows, it covers (pauses) this app's activity.
 * Each disappearance re-opens the prompt, up to [maxReopens] times, after which the install gives up.
 */
internal class ConfirmationWatchdog(
    private val graceTicks: Int = GRACE_TICKS,
    private val maxReopens: Int = MAX_REOPENS,
) {
    sealed interface Action {
        data object Wait : Action
        data object Reopen : Action
        data object GiveUp : Action
    }

    private var resumedTicks = 0
    private var reopens = 0

    /** Called once per check; [sessionPending] is false once the install has an outcome. */
    fun check(appResumed: Boolean, sessionPending: Boolean): Action {
        if (!sessionPending || !appResumed) {
            resumedTicks = 0
            return Action.Wait
        }
        if (++resumedTicks < graceTicks) return Action.Wait
        resumedTicks = 0
        return if (reopens < maxReopens) {
            reopens++
            Action.Reopen
        } else {
            Action.GiveUp
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 1_000L
        const val GIVE_UP_MESSAGE = "The system installer closed without an answer. Tap Run to try again."
        private const val GRACE_TICKS = 3
        private const val MAX_REOPENS = 2
    }
}
