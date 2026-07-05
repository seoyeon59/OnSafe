# 변경 사항

(더 자세한 리스트는 노션 '프론트 피그마' 페이지 하 확인)

---

## 인증 / 플로우

- 온보딩 권한 요청 플로우 재구성 — 기존 모드 선택 → 권한 순서를 **튜토리얼 → 권한 → 모드 선택** 순서로 변경 (`TutorialActivity` → `PermissionActivity` → `ModeSelectActivity`)
- 자동 로그인 세션 만료 체크 추가 — 마지막 로그인으로부터 30일 경과 시 자동 로그인 건너뜀 (`TokenManager` + `LoginActivity`)
- 자동 로그인 디버그 빌드 비활성화 — `FLAG_DEBUGGABLE` 기준으로 디버그 빌드에서는 매번 로그인 화면 표시, 출시 시 조건 제거 예정 (`LoginActivity`)
- 디버그 로그인 버튼 온보딩 연결 — 누를 때마다 튜토리얼 표시 여부 초기화 후 온보딩 전체 플로우(튜토리얼 → 권한 → 모드 선택)로 진입 (`LoginActivity`)
- 회원가입 2단계 상세주소 필수 조건 제거 — 상세주소는 선택 입력, 미기입 시에도 가입 완료 버튼 활성 (`RegisterStep2Activity`)
- 회원가입 1단계 진입 시 버튼 비활성 상태 누락 수정 — `refreshUI()` 초기 호출로 XML 기본값에 의존하지 않도록 수정 (`RegisterStep1Activity`)
- 약관별 URL 연결 슬롯 추가 — 각 약관 화살표 버튼에 URL 슬롯 연결, 미입력·오류 시 공통 안내 메시지 표시 (`RegisterStep1Activity`)
- 비밀번호 유효성 검사 공통 유틸 추출 — `PasswordValidator` object로 정규식·메시지 상수 분리 (`RegisterStep2Activity`, `ResetPasswordActivity`)

---

## 사고 이력

- 영상 저장 시 불필요한 권한 요청 제거 — API 29(Q)+ 에서는 Scoped Storage 적용으로 외부 저장소 권한 불필요, 28 이하에서만 요청하도록 수정 (`AccidentHistoryActivity`)
- 영상 URI 없을 때 빈 화면 진입 차단 — `videoUri` null·빈 값 체크 추가, Toast로 안내 후 `FullscreenActivity` 진입 막음 (`AccidentHistoryActivity`)

---

## 설정

- 알림·소리·진동 토글 상태 저장/복원 — `SharedPreferences("settings")`에 저장, 앱 재시작 후에도 상태 유지 (`SettingsActivity`)
- 시스템 알림 권한 취소 시 토글 자동 동기화 — `onResume()`에서 시스템 권한 상태를 확인하여 알림 토글 강제 OFF 처리 (`SettingsActivity`)

---

## 알림 내역

- 알림 권한 배너 요청 방식 변경 — 일시 거부 상태일 경우 앱 화면을 벗어나지 않고 시스템 다이얼로그로 직접 요청, 영구 거부 시에만 시스템 설정으로 이동 (`NotificationPermissionBanner`)

---

## 카메라 모드

- 뒤로 가기 버튼 deprecated 처리 교체 — `onBackPressed()` 오버라이드 제거, `OnBackPressedCallback`으로 교체. 패널 비표시 상태에서 뒤로가기 시 패널 복귀, 패널 표시 상태에서는 Activity 종료 (`CameraModeActivity`)

---

## 리팩토링

- 위험 지수 카드 바인딩 공통 유틸 추출 — `RiskScoreCardBinder` object로 색상·배지·프로그레스·메시지·stroke 일괄 처리 분리 (`MainActivity`, `NotificationActivity`)
- 위험 감지 모달 Window Leak 수정 — `onDestroy()`에서 `alertDialog?.dismiss()` 호출로 Activity 소멸 시 팝업 미해제 문제 수정 (`MainActivity`)

---

## 참고

- FALL 항목 읽음 처리 시 `position` 캡처 시점 스탈 문제 — 실시간 API 연동 후 목록이 동적으로 갱신되면 position 불일치 가능성 있음, API 연동 시 `item.id` 기준으로 수정 예정 (TODO 마킹 완료)
- 설정 토글 상태는 현재 기기 공용 `SharedPreferences("settings")`에 임시 저장 — 추후 서버 API에서 사용자 ID별로 관리하게 되면 키를 `settings_${userId}` 형태로 분리 예정 (TODO 마킹 완료)
- 약관 URL은 빈 슬롯으로만 준비 완료 — 서비스 URL 확정 후 `RegisterStep1Activity` 상단 상수에 입력 필요
