package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** Asks every polling flow (alliances, chat, marches, battles, own empire) to refresh now. */
object GameSignals {
    val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    fun poke() { refresh.tryEmit(Unit) }
}

/** Fetch immediately, then again every [intervalMs] or whenever something pokes [GameSignals]. */
fun <T> pollingFlow(tag: String, intervalMs: Long, fetch: suspend () -> T): Flow<T> = channelFlow {
    while (isActive) {
        runCatching { fetch() }
            .onSuccess { send(it) }
            .onFailure { Log.w(tag, "Refresh failed", it) }
        withTimeoutOrNull(intervalMs) { GameSignals.refresh.first() }
    }
}
