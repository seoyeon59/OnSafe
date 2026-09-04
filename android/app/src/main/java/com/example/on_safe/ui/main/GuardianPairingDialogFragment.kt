package com.example.on_safe.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.on_safe.R
import com.example.on_safe.network.ApiClient
import com.example.on_safe.network.dto.PairRequest
import com.example.on_safe.network.errorMessage
import com.example.on_safe.network.isOk
import com.example.on_safe.util.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 보호자 홈 진입 직후 표시되는 페어링 코드 입력 모달.
 * 이미 페어링된 계정에는 표시하지 않으므로(호출부 [MainActivity]에서 getWards로 판별),
 * 사용자는 반드시 코드를 입력해야 홈에 진입할 수 있다 (cancelable=false).
 * 성공 시 dismiss + 홈 재렌더는 호출부 responsibility.
 */
class GuardianPairingDialogFragment : DialogFragment() {

    // 페어링 성공 시 호출자에게 알려서 홈 초기 로드/재조회를 트리거하기 위한 콜백
    var onPaired: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_guardian_pair, null, false)

        val etCode = view.findViewById<EditText>(R.id.etPairingCode)
        val btnPair = view.findViewById<Button>(R.id.btnPair)
        val tvError = view.findViewById<TextView>(R.id.tvPairingError)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbPairingLoading)

        btnPair.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length != 6) {
                tvError.text = "6자리 코드를 정확히 입력해주세요."
                tvError.isVisible = true
                return@setOnClickListener
            }
            submitPairing(code, btnPair, pbLoading, tvError)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }

    private fun submitPairing(
        code: String,
        btnPair: Button,
        pbLoading: ProgressBar,
        tvError: TextView
    ) {
        val userId = TokenManager.getUserId(requireContext())
        if (userId.isBlank()) {
            tvError.text = "로그인 상태를 확인해주세요."
            tvError.isVisible = true
            return
        }

        btnPair.isEnabled = false
        pbLoading.isVisible = true
        tvError.isVisible = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.pairGuardian(userId, PairRequest(code = code))
                if (response.isOk) {
                    onPaired?.invoke()
                    dismissAllowingStateLoss()
                } else {
                    // 서버 ErrorCode 메시지가 이미 사용자 친화적이라 그대로 노출.
                    // (PAIRING_CODE_INVALID / SELF_PAIRING_NOT_ALLOWED / PAIRING_ALREADY_EXISTS 등)
                    tvError.text = response.errorMessage("연결에 실패했어요. 잠시 후 다시 시도해주세요.")
                    tvError.isVisible = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                tvError.text = "네트워크 오류가 발생했어요."
                tvError.isVisible = true
            } finally {
                btnPair.isEnabled = true
                pbLoading.isVisible = false
            }
        }
    }
}