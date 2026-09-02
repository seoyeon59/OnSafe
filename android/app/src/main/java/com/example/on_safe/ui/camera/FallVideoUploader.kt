package com.example.on_safe.ui.camera

import android.util.Log
import com.example.on_safe.BuildConfig
import com.example.on_safe.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * 업로드 3단계: upload-url 발급 → GCS signed PUT → video-complete 콜백.
 * GCS 직접 통신이라 인증 인터셉터 없는 별도 OkHttpClient 사용.
 * 재시도가 없어 실패 확정 시 로컬 클립 삭제 (미삭제 시 cacheDir 누적).
 */
class FallVideoUploader {

    companion object {
        private const val TAG = "FallVideoUploader"

        // 인스턴스마다 스레드풀·커넥션풀이 생기지 않도록 공유 (LandmarkStreamClient와 동일 방침)
        private val plainHttpClient = OkHttpClient.Builder().build()
    }

    suspend fun upload(userId: String, logId: String, clipFile: File) {
        try {
            val uploadUrlResponse = ApiClient.api.getUploadUrl(userId, logId)
            val uploadUrlBody = uploadUrlResponse.body()
            val uploadUrl = uploadUrlBody?.data?.get("upload_url")
            if (!uploadUrlResponse.isSuccessful || uploadUrlBody?.success != true || uploadUrl.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "업로드 URL 발급 실패 (logId=$logId): ${uploadUrlResponse.errorBody()?.string()}")
                else Log.w(TAG, "업로드 URL 발급 실패 (logId=$logId)")
                clipFile.delete()
                return
            }

            val requestBody = clipFile.asRequestBody("video/mp4".toMediaType())
            val putRequest = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .addHeader("Content-Type", "video/mp4")
                .build()

            // 원인 파악용 상태 코드·응답 본문 기록 (404=버킷 없음, 403=서명·Content-Type 불일치 등)
            val putResult = withContext(Dispatchers.IO) {
                plainHttpClient.newCall(putRequest).execute().use { res ->
                    Triple(res.isSuccessful, res.code, res.body?.string().orEmpty())
                }
            }
            if (!putResult.first) {
                if (BuildConfig.DEBUG) Log.w(TAG, "GCS 업로드 실패 (logId=$logId) code=${putResult.second} body=${putResult.third.take(300)}")
                else Log.w(TAG, "GCS 업로드 실패 (logId=$logId) code=${putResult.second}")
                clipFile.delete()
                return
            }

            val completeResponse = ApiClient.api.completeVideoUpload(userId, logId)
            if (completeResponse.isSuccessful && completeResponse.body()?.success == true) {
                clipFile.delete()
            } else {
                // GCS 업로드는 완료라 서버에 파일이 남아 있음 — 콜백만 실패한 상태이므로
                // 로컬 클립 삭제는 데이터 유실이 아님. 재시도가 없어 정리.
                if (BuildConfig.DEBUG) Log.w(TAG, "업로드 완료 콜백 실패 (logId=$logId): ${completeResponse.errorBody()?.string()}")
                else Log.w(TAG, "업로드 완료 콜백 실패 (logId=$logId)")
                clipFile.delete()
            }
        } catch (e: CancellationException) {
            // 화면 이탈로 취소된 경우 — 파일을 남겨 cacheDir 정리에 맡김
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "영상 업로드 파이프라인 실패 (logId=$logId)", e)
            clipFile.delete()
        }
    }
}
