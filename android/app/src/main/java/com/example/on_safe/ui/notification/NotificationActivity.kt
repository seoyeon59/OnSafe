package com.example.on_safe.ui.notification

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.util.NotificationPermissionBanner
import com.example.on_safe.util.RiskScoreCardBinder
import com.example.on_safe.util.TokenManager
import com.example.on_safe.util.toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationActivity : AppCompatActivity() {

    private val viewModel: NotificationViewModel by viewModels()

    private lateinit var adapter: NotificationAdapter
    private lateinit var rvNotifications: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var btnRetry: TextView

    private var alertDialog: BottomSheetDialog? = null
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        NotificationPermissionBanner.setup(this)

        rvNotifications = findViewById(R.id.rv_notifications)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnRetry = findViewById(R.id.btnRetry)

        adapter = NotificationAdapter { item -> showFallAlertDialog(item) }
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = adapter

        userId = TokenManager.getUserId(this)
        btnRetry.setOnClickListener { viewModel.load(userId) }

        observeViewModel()
        viewModel.load(userId)
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            adapter.submitList(state.items)
            updateEmptyState(state.items.isEmpty(), state.loadFailed)
        }
        viewModel.toastEvent.observe(this) { event ->
            if (event != null) {
                toast(event.message)
                viewModel.onToastHandled()
            }
        }
    }

    // 조회 실패는 오류 문구 + 재시도 버튼, 실제로 알림이 없으면 안내 문구만
    // (사고이력 화면과 동일 패턴 — 실패를 "알림 없음"으로 오해하지 않도록)
    private fun updateEmptyState(isEmpty: Boolean, loadFailed: Boolean) {
        layoutEmptyState.isVisible = isEmpty
        rvNotifications.isVisible = !isEmpty
        if (!isEmpty) return

        tvEmpty.text = if (loadFailed) "알림 내역을 불러오지 못했습니다." else "최근 7일간 감지된 알림이 없습니다."
        btnRetry.isVisible = loadFailed
    }

    override fun onResume() {
        super.onResume()
        NotificationPermissionBanner.refresh(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 모달이 열린 채로 Activity가 소멸되면 WindowLeak 발생 → 명시적으로 dismiss
        alertDialog?.dismiss()
        alertDialog = null
    }

    // 화면 종료 시 미읽음 여부를 MainActivity로 전달
    override fun finish() {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_HAS_UNREAD, viewModel.hasUnreadItems()))
        super.finish()
        // 뒤로가기·닫기 모두 이 finish()를 거치므로 여기 한 곳에서만 처리하면
        // 진입할 때(detail_enter/exit)와 대칭되는 복귀 전환이 항상 적용됨
        overridePendingTransition(R.anim.detail_pop_enter, R.anim.detail_pop_exit)
    }

    // FALL 항목 클릭 시 위험 감지 모달 표시 — 119 또는 확인했습니다를 눌러야 읽음 처리
    private fun showFallAlertDialog(item: NotificationItem) {
        val dialog = BottomSheetDialog(this)
        alertDialog = dialog
        val view = layoutInflater.inflate(R.layout.bottom_sheet_fall_alert, null)

        view.findViewById<TextView>(R.id.tvDetectedTime).text =
            "감지 시각 · ${TIME_FORMAT.format(Date(item.detectedAtMillis))}"

        // 점수 카드 바인딩 (색상·배지·메시지·프로그레스·stroke 일괄 처리)
        RiskScoreCardBinder.bind(view.findViewById(R.id.alertRiskScoreCard), item.riskScore)

        // item.id(서버 logId) 기준으로 읽음 처리 후 dismiss
        val markReadAndDismiss = {
            viewModel.markFallItemRead(userId, item.id)
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btn119Alert).setOnClickListener {
            // 다이얼러가 없는 기기(태블릿 등)에서 크래시 방지
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
            } catch (_: ActivityNotFoundException) {
                toast("전화 앱을 찾을 수 없습니다.")
            }
            markReadAndDismiss()
        }
        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener { markReadAndDismiss() }

        dialog.setContentView(view)
        dialog.setOnShowListener {
            // Material BottomSheetDialog 기본 둥근 배경 제거 → XML drawable 곡률만 표시
            (view.parent as? View)?.background = null
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_HAS_UNREAD = "extra_has_unread"

        // 모달을 열 때마다 재생성하지 않도록 상수화 (메인 스레드 전용)
        private val TIME_FORMAT = SimpleDateFormat("a hh:mm", Locale.KOREAN)
    }
}
