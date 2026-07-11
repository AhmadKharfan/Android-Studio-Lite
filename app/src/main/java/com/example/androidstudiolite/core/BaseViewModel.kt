package com.example.androidstudiolite.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<T, E>(
    initialState: T,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<T> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<E>()
    val effect: SharedFlow<E> = _effect.asSharedFlow()

    protected fun updateState(updater: T.() -> T) {
        _state.update(updater)
    }

    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effect.emit(effect) }
    }

    protected fun <R> tryToExecute(
        onStart: () -> Unit = {},
        block: suspend () -> R,
        onSuccess: (R) -> Unit = {},
        onError: (Throwable) -> Unit = {},
        dispatcher: CoroutineDispatcher = defaultDispatcher,
    ) {
        onStart()
        viewModelScope.launch(dispatcher) {
            try {
                onSuccess(block())
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    protected fun <R> tryToCollect(
        onStart: () -> Unit = {},
        block: suspend () -> Flow<R>,
        onCollect: suspend (R) -> Unit,
        onError: (Throwable) -> Unit = {},
        dispatcher: CoroutineDispatcher = defaultDispatcher,
    ) {
        onStart()
        viewModelScope.launch(dispatcher) {
            try {
                block().collectLatest { onCollect(it) }
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }
}
