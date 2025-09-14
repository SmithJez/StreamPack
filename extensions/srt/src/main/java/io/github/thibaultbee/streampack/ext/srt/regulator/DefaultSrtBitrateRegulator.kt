// streampack/ext/srt/regulator/DefaultSrtBitrateRegulator.kt
package io.github.thibaultbee.streampack.ext.srt.regulator

import io.github.thibaultbee.srtdroid.core.models.Stats
import io.github.thibaultbee.streampack.core.configuration.BitrateRegulatorConfig
import io.github.thibaultbee.streampack.ext.srt.stats.SrtLiveStats   // <-- add this import
import kotlin.math.max
import kotlin.math.min

class DefaultSrtBitrateRegulator(
    bitrateRegulatorConfig: BitrateRegulatorConfig,
    onVideoTargetBitrateChange: ((Int) -> Unit),
    onAudioTargetBitrateChange: ((Int) -> Unit)
) : SrtBitrateRegulator(
    bitrateRegulatorConfig,
    onVideoTargetBitrateChange,
    onAudioTargetBitrateChange
) {
    companion object {
        const val MINIMUM_DECREASE_THRESHOLD = 100000 // b/s
        const val MAXIMUM_INCREASE_THRESHOLD = 200000 // b/s
        const val SEND_PACKET_THRESHOLD = 1000
    }

    override fun update(stats: Stats, currentVideoBitrate: Int, currentAudioBitrate: Int) {
        // NEW: publish live stats every tick
        try {
            SrtLiveStats.publish(stats)

            val estimatedBandwidth = (stats.mbpsBandwidth * 1_000_000).toInt()

            if (currentVideoBitrate > bitrateRegulatorConfig.videoBitrateRange.lower) {
                val newVideoBitrate = when {
                    stats.pktSndLoss > 0 -> {
                        currentVideoBitrate - max(
                            currentVideoBitrate * 20 / 100,
                            MINIMUM_DECREASE_THRESHOLD
                        )
                    }
                    stats.pktSndBuf > SEND_PACKET_THRESHOLD -> {
                        currentVideoBitrate - max(
                            currentVideoBitrate * 10 / 100,
                            MINIMUM_DECREASE_THRESHOLD
                        )
                    }
                    (currentVideoBitrate + currentAudioBitrate) > estimatedBandwidth -> {
                        estimatedBandwidth - currentAudioBitrate
                    }
                    else -> 0
                }

                if (newVideoBitrate != 0) {
                    onVideoTargetBitrateChange(
                        max(newVideoBitrate, bitrateRegulatorConfig.videoBitrateRange.lower)
                    )
                    return
                }
            }

            if (currentVideoBitrate < bitrateRegulatorConfig.videoBitrateRange.upper) {
                val newVideoBitrate = when {
                    (currentVideoBitrate + currentAudioBitrate) < estimatedBandwidth -> {
                        currentVideoBitrate + min(
                            (bitrateRegulatorConfig.videoBitrateRange.upper - currentVideoBitrate) * 50 / 100,
                            MAXIMUM_INCREASE_THRESHOLD
                        )
                    }
                    else -> 0
                }

                if (newVideoBitrate != 0) {
                    onVideoTargetBitrateChange(
                        max(newVideoBitrate, bitrateRegulatorConfig.videoBitrateRange.lower)
                    )
                    return
                }
            }
        }
        catch (ex: Exception) {

        }

    }

    class Factory : SrtBitrateRegulator.Factory {
        override fun newBitrateRegulator(
            bitrateRegulatorConfig: BitrateRegulatorConfig,
            onVideoTargetBitrateChange: ((Int) -> Unit),
            onAudioTargetBitrateChange: ((Int) -> Unit)
        ) = DefaultSrtBitrateRegulator(
            bitrateRegulatorConfig,
            onVideoTargetBitrateChange,
            onAudioTargetBitrateChange
        )
    }
}
