package com.example.on_safe.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 화면이 닫힌 뒤에도 끝까지 보내야 하는 fire-and-forget 작업용 스코프.
 * UI 갱신에는 사용 금지 — 소멸된 화면 참조 위험. 그쪽은 lifecycleScope·viewModelScope.
 */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
