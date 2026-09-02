package com.example.on_safe.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 화면 생명주기보다 오래 살아야 하는 fire-and-forget 작업용 스코프.
 *
 * 로그아웃 서버 통보처럼 화면이 닫힌 뒤에도 끝까지 보내야 하는 호출에만 사용한다.
 * 화면 상태를 갱신하는 작업은 lifecycleScope·viewModelScope를 쓸 것 —
 * 여기서 UI를 건드리면 소멸된 화면을 참조하게 된다.
 */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
