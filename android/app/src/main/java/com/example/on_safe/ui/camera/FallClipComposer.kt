package com.example.on_safe.ui.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 같은 인코딩 프로파일(H.264 720p 1.5Mbps, [RollingVideoBufferManager]가 보장)의
 * 세그먼트 mp4 파일들을 재인코딩 없이 트랙 샘플만 이어붙여 하나의 mp4로 합성한다.
 * 세그먼트 경계가 항상 키프레임이라 세그먼트 단위 이어붙이기로 충분하다.
 */
object FallClipComposer {

    fun compose(segments: List<File>, outputFile: File) {
        require(segments.isNotEmpty()) { "합성할 세그먼트가 없습니다." }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var started = false
        var videoOffsetUs = 0L
        var audioOffsetUs = 0L

        try {
            segments.forEach { segment ->
                val extractor = MediaExtractor()
                extractor.setDataSource(segment.absolutePath)

                val videoSrcIndex = findTrack(extractor, "video/")
                val audioSrcIndex = findTrack(extractor, "audio/")

                if (!started) {
                    if (videoSrcIndex >= 0) {
                        videoTrackIndex = muxer.addTrack(extractor.getTrackFormat(videoSrcIndex))
                    }
                    if (audioSrcIndex >= 0) {
                        audioTrackIndex = muxer.addTrack(extractor.getTrackFormat(audioSrcIndex))
                    }
                    muxer.start()
                    started = true
                }

                if (videoSrcIndex >= 0) {
                    videoOffsetUs += copyTrack(extractor, videoSrcIndex, muxer, videoTrackIndex, videoOffsetUs)
                }
                if (audioSrcIndex >= 0) {
                    audioOffsetUs += copyTrack(extractor, audioSrcIndex, muxer, audioTrackIndex, audioOffsetUs)
                }
                extractor.release()
            }
        } finally {
            if (started) muxer.stop()
            muxer.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    // 트랙 샘플을 그대로 복사하며 PTS에 offsetUs를 더한다. 반환값: 이 세그먼트에서 복사한 구간 길이(다음 오프셋 계산용)
    private fun copyTrack(
        extractor: MediaExtractor,
        srcTrackIndex: Int,
        muxer: MediaMuxer,
        dstTrackIndex: Int,
        offsetUs: Long
    ): Long {
        extractor.selectTrack(srcTrackIndex)
        val buffer = ByteBuffer.allocate(1 shl 20) // 1MB
        val bufferInfo = MediaCodec.BufferInfo()
        var maxSampleTimeUs = 0L

        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            val sampleTimeUs = extractor.sampleTime
            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else 0

            bufferInfo.set(0, size, sampleTimeUs + offsetUs, flags)
            muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)

            maxSampleTimeUs = maxOf(maxSampleTimeUs, sampleTimeUs)
            extractor.advance()
        }
        extractor.unselectTrack(srcTrackIndex)
        return maxSampleTimeUs
    }
}
