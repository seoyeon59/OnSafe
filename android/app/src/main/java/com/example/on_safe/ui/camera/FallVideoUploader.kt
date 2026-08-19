package com.example.on_safe.ui.camera

import android.util.Log
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * 3-call 업로드 시퀀스: upload-url 발급 → GCS signed PUT URL 업로드 → video-complete 콜백.
 * signed URL은 우리 백엔드가 아닌 GCS로 직접 나가므로 인증 인터셉터 없는 별도 OkHttpClient를 쓴다.
 * 실패 시 재시도 없이 로그만 남김 (범위: 백엔드 reconciliation job이 안전망 역할).
 * 재시도가 없으므로 실패한 로컬 클립 파일을 남겨둬도 다시 쓸 곳이 없다 — 카메라 기기가
 * 장시간 켜져 있는 용도라 그대로 두면 cacheDir에 계속 쌓이므로, 실패가 확정되는 지점마다 정리한다.
 */
class FallVideoUploader {

    companion object {
        private const val TAG = "FallVideoUploader"
    }

    private val plainHttpClient = OkHttpClient.Builder().build()

    suspend fun upload(userId: String, logId: String, clipFile: File) {
        try {
            val uploadUrlResponse = ApiClient.api.getUploadUrl(userId, logId)
            val uploadUrlBody = uploadUrlResponse.body()
            val uploadUrl = uploadUrlBody?.data?.get("upload_url")
            if (!uploadUrlResponse.isSuccessful || uploadUrlBody?.success != true || uploadUrl.isNullOrBlank()) {
                Log.w(TAG, "업로드 URL 발급 실패 (logId=$logId): ${uploadUrlResponse.errorBody()?.string()}")
                clipFile.delete()
                return
            }

            val requestBody = clipFile.asRequestBody("video/mp4".toMediaType())
            val putRequest = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .addHeader("Content-Type", "video/mp4")
                .build()

            val putSuccessful = withContext(Dispatchers.IO) {
                plainHttpClient.newCall(putRequest).execute().use { it.isSuccessful }
            }
            if (!putSuccessful) {
                Log.w(TAG, "GCS 업로드 실패 (logId=$logId)")
                clipFile.delete()
                return
            }

            val completeResponse = ApiClient.api.completeVideoUpload(userId, logId)
            if (completeResponse.isSuccessful && completeResponse.body()?.success == true) {
                clipFile.delete()
            } else {
                // GCS 업로드 자체는 끝났으니 서버에 실제 파일은 남아있다 — video-complete 콜백만
                // 실패한 상태라 로컬 클립을 지워도 데이터 유실은 아니다. 재시도가 없으므로 정리한다.
                Log.w(TAG, "업로드 완료 콜백 실패 (logId=$logId): ${completeResponse.errorBody()?.string()}")
                clipFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "영상 업로드 파이프라인 실패 (logId=$logId)", e)
            clipFile.delete()
        }
    }
}
