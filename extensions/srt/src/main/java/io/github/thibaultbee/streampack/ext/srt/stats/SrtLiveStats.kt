package io.github.thibaultbee.streampack.ext.srt.stats

// in SrtLiveStats.kt
import android.os.SystemClock
import io.github.thibaultbee.srtdroid.core.models.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToLong

object SrtLiveStats {
    private val _sendBps = MutableStateFlow(0L)
    val sendBps: StateFlow<Long> = _sendBps

    private var lastBytes: Long? = null
    private var lastTsNs: Long? = null
    private var emaBps: Double? = null
    private const val EMA_ALPHA = 0.3 // smoothing

    fun publish(stats: Stats) {
        // 1) True send rate if available
        val directBps = runCatching {
            val mbps = Stats::class.java.getDeclaredField("mbpsSendRate")
                .apply { isAccessible = true }
                .get(stats) as Number
            (mbps.toDouble() * 1_000_000.0).roundToLong()
        }.getOrNull()

        val nowNs = SystemClock.elapsedRealtimeNanos()

        // 2) Otherwise compute from cumulative bytes (try a few common field names)
        val bytesTotal = directBps?.let { null } ?: runCatching {
            fun readLong(name: String): Long? =
                runCatching {
                    Stats::class.java.getDeclaredField(name)
                        .apply { isAccessible = true }
                        .get(stats) as Number
                }.getOrNull()?.toLong()

            // pick whichever exists in your Stats
            readLong("bytesSentTotal")
                ?: readLong("byteSentTotal")
                ?: readLong("bytesSentUnique")
                ?: readLong("byteSentUnique")
        }.getOrNull()

        val derivedBps = if (bytesTotal != null && lastBytes != null && lastTsNs != null) {
            val dtS = (nowNs - lastTsNs!!) / 1_000_000_000.0
            if (dtS > 0.2) { // only compute if we have a meaningful window
                val deltaBytes = (bytesTotal - lastBytes!!).coerceAtLeast(0L)
                (deltaBytes * 8.0 / dtS).roundToLong()
            } else null
        } else null

        // Update rollovers:
        if (bytesTotal != null) {
            lastBytes = bytesTotal
            lastTsNs = nowNs
        }

        // Choose best source and smooth
        val rawBps = directBps ?: derivedBps ?: return
        // (Optional) sanity filter: ignore absurd outliers > 200 Mbps for SD
        val saneBps = if (rawBps > 200_000_000) return else rawBps

        emaBps = if (emaBps == null) saneBps.toDouble()
        else EMA_ALPHA * saneBps + (1 - EMA_ALPHA) * emaBps!!

        _sendBps.value = emaBps!!.roundToLong()
    }
}
