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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.data.repository.AccidentHistoryRepository
import com.example.on_safe.data.repository.RealAccidentHistoryRepository
import com.example.on_safe.network.ApiClient
import com.example.on_safe.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // 미디어 권한 요청 → 승인 시 handleDownload 재시도(signed URL 새로 발급), 거부 시 안내
    private val requestMediaPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val entry = pendingDownloadEntry ?: return@registerForActivityResult
            pendingDownloadEntry = null
            if (granted) {
                handleDownload(entry)
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

    private val rawEntries: MutableList<HistoryListItem.HistoryEntry> = mutableListOf()

    private val accidentHistoryRepository: AccidentHistoryRepository = RealAccidentHistoryRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accident_history)

        initViews()
        initRecyclerView()
        setupSortChips()
        setupNavListeners()
        loadHistory()
    }

    // AccidentHistoryRepository 경유로 사고 이력 조회 후 현재 정렬 순서로 표시
    private fun loadHistory() {
        val userId = TokenManager.getUserId(this)
        if (userId.isBlank()) {
            applySort(currentSort)
            return
        }
        lifecycleScope.launch {
            try {
                val entries = accidentHistoryRepository.getHistoryEntries(userId)
                rawEntries.clear()
                rawEntries.addAll(entries)
            } catch (e: IllegalStateException) {
                Toast.makeText(this@AccidentHistoryActivity, e.message ?: "사고 이력을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@AccidentHistoryActivity, "네트워크 오류로 사고 이력을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
            applySort(currentSort)
        }
    }

    private fun initViews() {
        rvHistory      = findViewById(R.id.rvHistory)
        tvEmpty        = findViewById(R.id.tvEmpty)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        chipNewest     = findViewById(R.id.chipNewest)
        chipOldest     = findViewById(R.id.chipOldest)

        // btnBack은 레이아웃에서 제거됨 (바텀탭으로 이동하는 구조이므로 불필요)
        // findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }
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
        // 탭 화면끼리는 항상 finish()로 이전 탭을 정리 → 뒤로가기 눌러도 다른 탭이 쌓여있지 않음
        findViewById<View>(R.id.tabHome).setOnClickListener {
            val intent = Intent(this, com.example.on_safe.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
        // 설정: 홈을 건너뛰고 이동하므로 더 빠른 전환으로 "스쳐 지나가는" 느낌을 줌
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.settings.SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right_fast, R.anim.slide_out_left_fast)
            finish()
        }
    }

    private fun applySort(sort: SortOrder) {
        currentSort = sort
        adapter.submitList(rawEntries, sort)
        updateSortChipUi(sort)
        updateCountDisplay()
        tvEmpty.visibility    = if (adapter.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility  = if (adapter.isEmpty()) View.GONE  else View.VISIBLE
        // 정렬 변경 시 DiffUtil이 이전 스크롤 위치를 그대로 두는 경우가 있어 항상 맨 위로 리셋
        // 순간이동 대신 부드럽게 스크롤해서 "위로 올라갔다"는 게 눈에 보이도록 함
        rvHistory.smoothScrollToPosition(0)
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
        if (!entry.hasVideo) {
            Toast.makeText(this, "재생할 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        fetchVideoUrl(entry) { signedUrl ->
            startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, signedUrl)
            })
            overridePendingTransition(R.anim.fullscreen_enter, R.anim.fullscreen_exit)
        }
    }

    private fun handleDownload(entry: HistoryListItem.HistoryEntry) {
        if (!entry.hasVideo) {
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
        fetchVideoUrl(entry) { signedUrl -> performDownload(signedUrl, entry.id) }
    }

    // GET .../video 로 signed URL 발급 (1시간 TTL — 재생/다운로드 시점마다 새로 요청)
    private fun fetchVideoUrl(entry: HistoryListItem.HistoryEntry, onSuccess: (String) -> Unit) {
        val userId = TokenManager.getUserId(this)
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getFallLogVideo(userId, entry.id)
                val body = response.body()
                val signedUrl = body?.data?.get("signed_url")
                if (response.isSuccessful && body?.success == true && signedUrl != null) {
                    onSuccess(signedUrl)
                } else {
                    val message = ApiClient.parseErrorMessage(response.errorBody(), "영상을 불러올 수 없습니다.")
                    Toast.makeText(this@AccidentHistoryActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccidentHistoryActivity, "네트워크 오류로 영상을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performDownload(videoUrl: String, entryId: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { saveVideoToGallery(videoUrl, entryId) }
                Toast.makeText(this@AccidentHistoryActivity, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
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
        val userId = TokenManager.getUserId(this)
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteFallLog(userId, entry.id)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    rawEntries.removeAll { it.id == entry.id }
                    adapter.removeItem(entry.id)
                    updateCountDisplay()
                    tvEmpty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
                    rvHistory.visibility = if (adapter.isEmpty()) View.GONE else View.VISIBLE
                    Toast.makeText(this@AccidentHistoryActivity, "이력이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val message = ApiClient.parseErrorMessage(response.errorBody(), "삭제에 실패했습니다.")
                    Toast.makeText(this@AccidentHistoryActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccidentHistoryActivity, "네트워크 오류로 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
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

    // sourceUrl은 GCS signed URL(https)이므로 ContentResolver가 아닌 직접 네트워크 스트림으로 복사한다.
    // 호출부(performDownload)에서 Dispatchers.IO 위에서 실행됨.
    private fun saveVideoToGallery(sourceUrl: String, entryId: String) {
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
            java.net.URL(sourceUrl).openStream().use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
        }
    }
}
