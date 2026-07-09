package com.example.on_safe.ui.history

import android.Manifest
import android.app.AlertDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.ui.FullscreenActivity
import com.example.on_safe.data.fake.FakeAccidentHistoryRepository
import com.example.on_safe.data.repository.AccidentHistoryRepository

// 사고 이력 화면 (전체/낙상/경고 필터, 영상 보기, 다운로드, 삭제)
class AccidentHistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory:       RecyclerView
    private lateinit var tvEmpty:         TextView
    private lateinit var tabAll:          LinearLayout
    private lateinit var tabFall:         LinearLayout
    private lateinit var tabWarning:      LinearLayout
    private lateinit var tvTabAllLabel:   TextView
    private lateinit var tvTabAllCount:   TextView
    private lateinit var tvTabFallLabel:  TextView
    private lateinit var tvTabFallCount:  TextView
    private lateinit var tvTabWarnLabel:  TextView
    private lateinit var tvTabWarnCount:  TextView

    private lateinit var adapter: AccidentHistoryAdapter

    // 권한 승인 후 재시도할 다운로드 항목
    private var pendingDownloadEntry: HistoryListItem.HistoryEntry? = null

    // 미디어 권한 요청 → 승인 시 performDownload, 거부 시 안내
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

    // TODO: API 연동 시 Real 구현체로 교체
    private val historyRepository: AccidentHistoryRepository = FakeAccidentHistoryRepository()
    private val rawEntries: MutableList<HistoryListItem.HistoryEntry> by lazy {
        historyRepository.getHistoryEntries()
    }

    private var currentFilter = FilterType.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accident_history)

        initViews()
        initRecyclerView()
        setupTabListeners()
        setupNavListeners()
        applyFilter(FilterType.ALL)
    }

    private fun initViews() {
        rvHistory       = findViewById(R.id.rvHistory)
        tvEmpty         = findViewById(R.id.tvEmpty)
        tabAll          = findViewById(R.id.tabAll)
        tabFall         = findViewById(R.id.tabFall)
        tabWarning      = findViewById(R.id.tabWarning)
        tvTabAllLabel   = findViewById(R.id.tvTabAllLabel)
        tvTabAllCount   = findViewById(R.id.tvTabAllCount)
        tvTabFallLabel  = findViewById(R.id.tvTabFallLabel)
        tvTabFallCount  = findViewById(R.id.tvTabFallCount)
        tvTabWarnLabel  = findViewById(R.id.tvTabWarningLabel)
        tvTabWarnCount  = findViewById(R.id.tvTabWarningCount)

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

    private fun setupTabListeners() {
        tabAll.setOnClickListener     { applyFilter(FilterType.ALL) }
        tabFall.setOnClickListener    { applyFilter(FilterType.FALL) }
        tabWarning.setOnClickListener { applyFilter(FilterType.WARNING) }
    }

    private fun setupNavListeners() {
        findViewById<View>(R.id.tabHome).setOnClickListener {
            // 홈은 스택에 이미 있으므로 clear top으로 복귀
            val intent = Intent(this, com.example.on_safe.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, com.example.on_safe.ui.settings.SettingsActivity::class.java))
        }
    }

    private fun applyFilter(filter: FilterType) {
        currentFilter = filter
        adapter.submitFilteredList(rawEntries, filter)
        updateTabUi(filter)
        updateCountBadges()
        tvEmpty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (adapter.isEmpty()) View.GONE else View.VISIBLE
    }

    // active 탭: 색상 pill + 흰 텍스트 / inactive: 회색 pill + ink_500 텍스트
    private fun updateTabUi(active: FilterType) {
        val tabs = mapOf(
            FilterType.ALL     to Triple(tabAll,     tvTabAllLabel,  tvTabAllCount),
            FilterType.FALL    to Triple(tabFall,    tvTabFallLabel, tvTabFallCount),
            FilterType.WARNING to Triple(tabWarning, tvTabWarnLabel, tvTabWarnCount)
        )

        val activeColor = when (active) {
            FilterType.ALL     -> ContextCompat.getColor(this, R.color.primary_blue)
            FilterType.FALL    -> ContextCompat.getColor(this, R.color.status_danger)
            FilterType.WARNING -> ContextCompat.getColor(this, R.color.status_warning)
        }
        val inactiveColor = ContextCompat.getColor(this, R.color.ink_500)
        val white         = ContextCompat.getColor(this, R.color.surface_white)
        val grayBg        = 0xFFF1F1F3.toInt()

        tabs.forEach { (type, triple) ->
            val (container, labelTv, _) = triple
            val isActive = type == active

            // 배경 pill
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.getDimension(R.dimen.tab_pill_radius)
                setColor(if (isActive) activeColor else grayBg)
            }
            container.background = bg

            // 텍스트 색
            val textColor = if (isActive) white else inactiveColor
            labelTv.setTextColor(textColor)
        }

        updateBadgeColors(active, activeColor, inactiveColor, white)
    }

    private fun updateBadgeColors(
        active: FilterType,
        activeColor: Int,
        inactiveColor: Int,
        white: Int
    ) {
        val badgePillRadius = resources.getDimension(R.dimen.tab_pill_radius)

        // active 뱃지: 반투명 흰색 25% (#40FFFFFF), inactive: 70% (#B3FFFFFF)
        // GradientDrawable을 직접 사용 — backgroundTintList는 solid 위에서 semi-transparent 처리 불가
        fun styleBadge(tv: TextView, isActive: Boolean) {
            val bgColor = if (isActive) Color.argb(0x40, 0xFF, 0xFF, 0xFF)
                          else          Color.argb(0xB3, 0xFF, 0xFF, 0xFF)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = badgePillRadius
                setColor(bgColor)
            }
            tv.background = bg
            tv.setTextColor(if (isActive) white else inactiveColor)
        }

        styleBadge(tvTabAllCount,  active == FilterType.ALL)
        styleBadge(tvTabFallCount, active == FilterType.FALL)
        styleBadge(tvTabWarnCount, active == FilterType.WARNING)
    }

    private fun updateCountBadges() {
        val total   = rawEntries.size
        val falls   = rawEntries.count { it.type == HistoryType.FALL }
        val warnings = rawEntries.count { it.type == HistoryType.WARNING }

        tvTabAllCount.text  = total.toString()
        tvTabFallCount.text = falls.toString()
        tvTabWarnCount.text = warnings.toString()
    }

    private fun handleWatchVideo(entry: HistoryListItem.HistoryEntry) {
        // TODO: API 연동 후 실제 영상 URI가 채워지면 아래 null 체크는 자동으로 통과됨
        if (entry.videoUri.isNullOrEmpty()) {
            Toast.makeText(this, "재생할 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, FullscreenActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_URI, entry.videoUri)
        })
    }

    // TODO: 파일 복사 로직은 Coroutine IO 스레드로 이동
    private fun handleDownload(entry: HistoryListItem.HistoryEntry) {
        if (entry.videoUri.isNullOrEmpty()) {
            Toast.makeText(this, "저장 가능한 영상이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // API 29+(Q)부터는 MediaStore 삽입에 외부 저장소 권한 불필요 (Scoped Storage)
        // API 28 이하에서만 READ_EXTERNAL_STORAGE 권한 확인
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
        try {
            saveVideoToGallery(uri, entry.id)
            Toast.makeText(this, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMediaPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 설정 필요")
            .setMessage("사진/영상 권한이 '다시 묻지 않음'으로 거부되었습니다.\n앱 설정에서 직접 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openMediaSettings.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("취소", null)
            .show()
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
        updateCountBadges()
        tvEmpty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (adapter.isEmpty()) View.GONE else View.VISIBLE
        Toast.makeText(this, "이력이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun saveVideoToGallery(sourceUri: String, entryId: String) {
        val fileName = "onsafe_${entryId}_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OnSafe")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val destUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore URI 생성 실패")

        // 실제 파일 복사 (sourceUri가 content:// 혹은 file:// 형태일 경우)
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
