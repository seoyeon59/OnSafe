package com.example.on_safe.ui.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.LruCache
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 사고 이력 목록 썸네일 — 사고 영상에서 위험 점수가 가장 높았던 시점([HistoryListItem.HistoryEntry.peakOffsetMs])의
 * 프레임을 보호자 폰이 직접 뽑는다. 서버에는 영상만 있고 썸네일 파일은 없다.
 *
 * - 영상 signed URL을 받아 MediaMetadataRetriever가 필요한 부분만 범위 요청으로 읽는다(영상 전체를 받지 않음)
 * - 한 번 뽑은 썸네일은 메모리·캐시 폴더(history_thumbs)에 두어 목록을 다시 열 때 영상을 또 읽지 않는다
 * - 피보호자가 찍힌 사진이라 목록에서 사라진 기록(삭제·보관 기간 만료)의 썸네일은 [retainOnly]로 지운다
 */
internal object HistoryThumbnails {

    private const val TAG = "HistoryThumbnails"
    private const val THUMB_WIDTH_PX = 360

    // 클립 = 사고 전(최대 약 2분) + 사고 후 약 2분 — 시점 값이 없는 이전 기록은 끝에서 2분 앞(사고 순간 근처)
    private const val POST_EVENT_MS = 120_000L

    // 가장 가까운 키프레임(사고 영상은 1초마다 키프레임) — 키프레임이 아닌 정확한 프레임(OPTION_CLOSEST)을
    // 원격 영상에서 뽑으면 삼성 기기에서 초록 화면이 나왔다(PC 디코딩으로는 같은 시점이 정상)
    private const val FRAME_OPTION = MediaMetadataRetriever.OPTION_CLOSEST_SYNC

    // 동시에 영상을 여는 수 — 목록을 빠르게 넘겨도 네트워크·디코더를 몰아 쓰지 않게
    private val gate = Semaphore(2)

    private val memory = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.cacheDir, "history_thumbs").apply { mkdirs() }
    }

    fun cached(logId: String): Bitmap? = memory.get(logId)

    /** 썸네일을 돌려준다. 영상이 없거나 뽑지 못하면 null */
    suspend fun load(wardUserId: String, logId: String, peakOffsetMs: Long?): Bitmap? {
        memory.get(logId)?.let { return it }
        val cacheDir = dir ?: return null
        val file = File(cacheDir, "$logId.jpg")
        return withContext(Dispatchers.IO) {
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path)?.also { memory.put(logId, it) }?.let { return@withContext it }
            }
            gate.withPermit {
                val bitmap = extract(wardUserId, logId, peakOffsetMs) ?: return@withPermit null
                memory.put(logId, bitmap)
                runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                bitmap
            }
        }
    }

    /** 목록에 없는 기록의 썸네일을 지운다 — 삭제·만료된 사고의 사진이 폰에 남지 않게 */
    fun retainOnly(logIds: Set<String>) {
        val cacheDir = dir ?: return
        cacheDir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension
            if (id !in logIds) {
                f.delete()
                memory.remove(id)
            }
        }
    }

    private suspend fun extract(wardUserId: String, logId: String, peakOffsetMs: Long?): Bitmap? {
        val signedUrl = try {
            ApiClient.api.getFallLogVideo(wardUserId, logId).body()?.data?.signedUrl
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(signedUrl, emptyMap())
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val atMs = (peakOffsetMs ?: durationMs?.let { it - POST_EVENT_MS } ?: 0L)
                .coerceIn(0L, durationMs ?: Long.MAX_VALUE)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (width != null && height != null && width > 0) {
                    retriever.getScaledFrameAtTime(
                        atMs * 1000, FRAME_OPTION,
                        THUMB_WIDTH_PX, THUMB_WIDTH_PX * height / width
                    )
                } else {
                    retriever.getFrameAtTime(atMs * 1000, FRAME_OPTION)
                }
            } else {
                retriever.getFrameAtTime(atMs * 1000, FRAME_OPTION)?.let { full ->
                    val scaled = Bitmap.createScaledBitmap(full, THUMB_WIDTH_PX, THUMB_WIDTH_PX * full.height / full.width, true)
                    if (scaled !== full) full.recycle()
                    scaled
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "썸네일 추출 logId=$logId at=${atMs}ms peak=$peakOffsetMs duration=$durationMs ok=${frame != null}")
            frame
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "썸네일 추출 실패 logId=$logId", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
