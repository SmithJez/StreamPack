package io.github.thibaultbee.streampack.ext.srt.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SrtStatsBus {
    private val _sendRateBps = MutableStateFlow(0L)
    val sendRateBps: StateFlow<Long> = _sendRateBps

    internal fun updateSendRate(bps: Long) { _sendRateBps.value = bps }
}