package com.example.on_safe.ui.history

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R
import com.example.on_safe.util.toast

// 낙상 이력 영상 재생 — 호출부에서 받은 signed URL(1시간 TTL) 스트리밍
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        if (videoUrl.isNullOrEmpty()) {
            toast("재생할 영상이 없습니다.")
            finish()
            return
        }

        val progressLoading = findViewById<ProgressBar>(R.id.progressLoading)
        videoView = findViewById(R.id.videoView)

        videoView.setMediaController(MediaController(this).apply { setAnchorView(videoView) })
        videoView.setVideoURI(Uri.parse(videoUrl))
        videoView.setOnPreparedListener {
            progressLoading.visibility = View.GONE
            it.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            progressLoading.visibility = View.GONE
            toast("영상을 재생할 수 없습니다.")
            finish()
            true
        }

        findViewById<ImageButton>(R.id.btnBackVideoPlayer).setOnClickListener { finish() }
    }

    // VideoView는 화면 이탈 시 자동 정지하지 않음 — 소리만 계속 재생되는 문제 방지
    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized) videoView.pause()
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }
}
