package svechnikov.mynalabs

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Encoder(private val path: String, width: Int, height: Int) {

    val surface: Surface

    private val executor = Executors.newSingleThreadExecutor()

    private val encoder: MediaCodec

    private val bufferInfo = MediaCodec.BufferInfo()

    private var muxer: MediaMuxer? = null

    @Volatile
    private var shutdown = false

    private var trackPosition = -1

    init {
        val format = MediaFormat.createVideoFormat(FORMAT, width, height)

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        encoder = MediaCodec.createEncoderByType(FORMAT)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = encoder.createInputSurface()
        encoder.start()
    }

    fun process() {
        if (shutdown) {
            return
        }
        executor.execute {
            while (!shutdown) {
                when (val index = encoder.dequeueOutputBuffer(bufferInfo, 0)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also {
                            trackPosition = it.addTrack(encoder.outputFormat)
                            it.start()
                        }
                    }
                    else -> {
                        if (index < 0) {
                            continue
                        }
                        val data = encoder.getOutputBuffer(index) ?: throw RuntimeException()
                        val muxer = muxer ?: throw RuntimeException()

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0) {
                            muxer.writeSampleData(trackPosition, data, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
    }

    fun shutdown() {
        shutdown = true
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        muxer?.stop()
        muxer?.release()
        encoder.stop()
        encoder.release()

    }

    private companion object {
        const val FORMAT = "video/avc"
        const val BIT_RATE = 6_000_000
        // seems no way to get current frame rate from camerax
        // need to investigate further, for now 30 is ok
        const val FRAME_RATE = 30
    }
}