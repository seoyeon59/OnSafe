package com.example.on_safe.ui.history

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.on_safe.R

// 낙상 이력 영상 재생 (signed URL 스트리밍)
class VideoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        if (videoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "재생할 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val progressLoading = findViewById<ProgressBar>(R.id.progressLoading)
        val videoView = findViewById<VideoView>(R.id.videoView)

        videoView.setMediaController(MediaController(this).apply { setAnchorView(videoView) })
        videoView.setVideoURI(Uri.parse(videoUrl))
        videoView.setOnPreparedListener {
            progressLoading.visibility = View.GONE
            it.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            progressLoading.visibility = View.GONE
            Toast.makeText(this, "영상을 재생할 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            true
        }

        findViewById<ImageButton>(R.id.btnBackVideoPlayer).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }
}
