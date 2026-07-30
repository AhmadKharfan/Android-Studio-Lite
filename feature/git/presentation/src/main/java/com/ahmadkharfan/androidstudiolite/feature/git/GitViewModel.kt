package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.feature.git.api.gitErrorMessage
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class GitViewModel<T, E>(
    initialState: T,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<T, E>(initialState, defaultDispatcher) {
    protected abstract fun T.withGitError(message: String): T

    protected fun gitErrorHandler(): (Throwable) -> Unit = {
        updateState { withGitError(gitErrorMessage(it)) }
    }
}
