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
                return
            }

            val completeResponse = ApiClient.api.completeVideoUpload(userId, logId)
            if (completeResponse.isSuccessful && completeResponse.body()?.success == true) {
                clipFile.delete()
            } else {
                Log.w(TAG, "업로드 완료 콜백 실패 (logId=$logId): ${completeResponse.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "영상 업로드 파이프라인 실패 (logId=$logId)", e)
        }
    }
}
