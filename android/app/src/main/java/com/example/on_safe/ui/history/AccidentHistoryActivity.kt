package com.example.on_safe.ui.history

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.on_safe.R
import com.example.on_safe.ui.FullscreenActivity

// 사고 이력 화면 (위험 이력만 표시 / 최신순·오래된순 정렬 / 영상 보기·다운로드·삭제)
class AccidentHistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory:      RecyclerView
    private lateinit var tvEmpty:        TextView
    private lateinit var tvHistoryCount: TextView  // 건수 숫자 ("N건")
    private lateinit var chipNewest:     TextView
    private lateinit var chipOldest:     TextView

    private lateinit var adapter: AccidentHistoryAdapter

    private var currentSort = SortOrder.NEWEST_FIRST

    // 권한 승인 후 재시도할 다운로드 항목
    private var pendingDownloadEntry: HistoryListItem.HistoryEntry? = null

    private val requestMediaPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val entry = pendingDownloadEntry ?: return@registerForActivityResult
            pendingDownloadEntry = null
            if (granted) {
                performDownload(entry)
            } else {
                val permanentlyDenied = !shouldShowRequestPermissionRationale(mediaPermission)
                if (permanentlyDenied) {
                    showMediaPermissionSettingsDialog()
                } else {
                    Toast.makeText(this, "영상 저장을 위해 사진/영상 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val openMediaSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val mediaPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    // TODO: GET /accident/history API로 교체 (현재 더미 데이터 — 위험 타입만 포함)
    private val rawEntries: MutableList<HistoryListItem.HistoryEntry> = mutableListOf(
        HistoryListItem.HistoryEntry("1", HistoryType.FALL, "14:32", "2025.01.15"),
        HistoryListItem.HistoryEntry("3", HistoryType.FALL, "22:05", "2025.01.14"),
        HistoryListItem.HistoryEntry("5", HistoryType.FALL, "07:30", "2025.01.13"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accident_history)

        initViews()
        initRecyclerView()
        setupSortChips()
        setupNavListeners()
        applySort(SortOrder.NEWEST_FIRST)
    }

    private fun initViews() {
        rvHistory      = findViewById(R.id.rvHistory)
        tvEmpty        = findViewById(R.id.tvEmpty)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        chipNewest     = findViewById(R.id.chipNewest)
        chipOldest     = findViewById(R.id.chipOldest)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun initRecyclerView() {
        adapter = AccidentHistoryAdapter(
            onWatchVideo = ::handleWatchVideo,
            onDownload   = ::handleDownload,
            onDelete     = ::handleDeleteRequest
        )
        rvHistory.apply {
            layoutManager = LinearLayoutManager(this@AccidentHistoryActivity)
            adapter = this@AccidentHistoryActivity.adapter
            setHasFixedSize(false)
        }
    }

    private fun setupSortChips() {
        chipNewest.setOnClickListener { applySort(SortOrder.NEWEST_FIRST) }
        chipOldest.setOnClickListener { applySort(SortOrder.OLDEST_FIRST) }
    }

    private fun setupNavListeners() {
        // 홈: 사고이력은 왼쪽 탭 → 홈으로 돌아갈 때 오른쪽으로 슬라이드 아웃
        findViewById<View>(R.id.tabHome).setOnClickListener {
            val intent = Intent(this, com.example.on_safe.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        // 설정: 오른쪽 탭 → 오른쪽에서 슬라이드 인
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.settings.SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun applySort(sort: SortOrder) {
        currentSort = sort
        adapter.submitList(rawEntries, sort)
        updateSortChipUi(sort)
        updateCountDisplay()
        tvEmpty.visibility    = if (adapter.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility  = if (adapter.isEmpty()) View.GONE  else View.VISIBLE
    }

    // active 칩: 파란 pill + 흰 텍스트 / inactive: 회색 pill + ink_500 텍스트
    private fun updateSortChipUi(active: SortOrder) {
        val activeColor   = ContextCompat.getColor(this, R.color.primary_blue)
        val inactiveColor = ContextCompat.getColor(this, R.color.ink_500)
        val white         = ContextCompat.getColor(this, R.color.surface_white)
        val grayBg        = 0xFFF1F1F3.toInt()
        val pillRadius    = resources.getDimension(R.dimen.tab_pill_radius)

        fun styleChip(chip: TextView, isActive: Boolean) {
            chip.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = pillRadius
                setColor(if (isActive) activeColor else grayBg)
            }
            chip.setTextColor(if (isActive) white else inactiveColor)
        }

        styleChip(chipNewest, active == SortOrder.NEWEST_FIRST)
        styleChip(chipOldest, active == SortOrder.OLDEST_FIRST)
    }

    private fun updateCountDisplay() {
        val count = rawEntries.count { it.type == HistoryType.FALL }
        tvHistoryCount.text = "${count}건"
    }

    // ──────────────────────────────────────────────
    // 영상 보기 / 다운로드 / 삭제
    // ──────────────────────────────────────────────

    private fun handleWatchVideo(entry: HistoryListItem.HistoryEntry) {
        if (entry.videoUri.isNullOrEmpty()) {
            Toast.makeText(this, "재생할 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, FullscreenActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_URI, entry.videoUri)
        })
    }

    private fun handleDownload(entry: HistoryListItem.HistoryEntry) {
        if (entry.videoUri.isNullOrEmpty()) {
            Toast.makeText(this, "저장 가능한 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, mediaPermission) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownloadEntry = entry
            requestMediaPermission.launch(mediaPermission)
            return
        }
        performDownload(entry)
    }

    private fun performDownload(entry: HistoryListItem.HistoryEntry) {
        val uri = entry.videoUri ?: return
        // 파일 복사는 IO 스레드에서, 완료 후 토스트는 Main 스레드에서 표시
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    saveVideoToGallery(uri, entry.id)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                Toast.makeText(this@AccidentHistoryActivity, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AccidentHistoryActivity, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDeleteRequest(entry: HistoryListItem.HistoryEntry) {
        showDeleteDialog(entry)
    }

    private fun showDeleteDialog(entry: HistoryListItem.HistoryEntry) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_delete_history)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnDeleteConfirm).setOnClickListener {
            dialog.dismiss()
            deleteEntry(entry)
        }
        dialog.show()
    }

    private fun deleteEntry(entry: HistoryListItem.HistoryEntry) {
        rawEntries.removeAll { it.id == entry.id }
        adapter.removeItem(entry.id)
        updateCountDisplay()
        tvEmpty.visibility   = if (adapter.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (adapter.isEmpty()) View.GONE  else View.VISIBLE
        Toast.makeText(this, "이력이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 미디어 권한 팝업도 앱 내 공통 다이얼로그 스타일로 통일
    private fun showMediaPermissionSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_permission_settings)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(false)

        dialog.findViewById<TextView>(R.id.tvPermDialogMessage).text =
            "사진/영상 권한이 '다시 묻지 않음'으로\n거부되었습니다. 앱 설정에서 직접 허용해주세요."

        dialog.findViewById<TextView>(R.id.btnPermDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnPermDialogConfirm).setOnClickListener {
            dialog.dismiss()
            openMediaSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
        dialog.show()
    }

    private fun saveVideoToGallery(sourceUri: String, entryId: String) {
        val fileName = "neulbom_${entryId}_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/늘봄")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val destUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore URI 생성 실패")

        resolver.openOutputStream(destUri)?.use { out ->
            val src = android.net.Uri.parse(sourceUri)
            resolver.openInputStream(src)?.use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
    }
}
