# 변경 이력

OnSafe Android 클라이언트. 최신순.

분류 — `보안` 취약점·노출 경로 / `수정` 결함 / `변경` 동작·구조 / `추가` 신규 / `제거` 삭제

---

## 2026-09-04

연결 상태 세분화, 최종 리뷰 차수.

### 수정
- **낙상 영상 유실** — 클립 업로드를 `lifecycleScope`로 실행해, 합성 중 화면을 벗어나면
  취소되며 파일까지 삭제. 사고 증거라 `AppScope`로 이관 (`CameraModeActivity`)
- **컴파일 실패** — `NotificationAdapter`가 `bindingAdapterPosition`(RecyclerView 1.2.0+)을 참조하나
  해석 버전은 transitive 1.1.0. 동작이 같은 `adapterPosition`으로 교체
- 영상 다운로드 중 화면 이탈이 "저장에 실패했습니다"로 오표시 (`AccidentHistoryActivity`)
- 서버가 빈 문자열 `message`를 보내면 빈 토스트 출력 (`ApiResult.errorMessage`)

### 변경
- **연결 상태 판정 재설계** — 프레임 도착(`deviceSeenAt`)과 추론 성공(`updatedAt`)을 분리해
  "프레임 끊김"·"추론 실패"·"처리 지연"을 각각 구분. 기존엔 셋 다 `STANDBY`("카메라 기기 연결 필요")로
  뭉쳐, 감지 기능이 죽은 상황이 "아직 안 켰나보다"로 감춰짐
  - `ConnectionState`에 `INFERENCE_ERROR` · `SLOW` · `RECONNECTING` 추가
  - **현재 `INFERENCE_ERROR`만 활성** — 나머지는 `HEARTBEAT_COVERS_IDLE=false`로 보류.
    하트비트가 WS 프레임 수신에 묶여 있고 앱은 관절이 잡힐 때만 프레임을 보내, 빈 방에서
    `deviceSeenAt`이 멈춰 촬영 종료로 오판됨 (`1ef5a50`에서 되돌린 문제와 동일)
  - 정체·오류 상태에서 직전 점수 제거 — 낡은 값이 현재 안전 상태로 오독됨
  - 임계값: 10초 `RECONNECTING` / 15초 `SLOW` / 30초 `STANDBY`.
    하트비트 간격도 5초라 1틱은 지터만으로 겹쳐 최소 2틱부터 정체로 판정
  - `deviceSeenAt`·`updatedAt` 미제공 시 점수 표시로 폴백 (구버전 서버 호환)
  - `RiskScoreResponse.level`을 nullable로 정정 — Gson이 생성자를 건너뛰어 응답 누락 시 null 유입
  - 백엔드 `main` 대조 완료 — `deviceSeenAt` 미존재·`level`에 "오류" 미포함 상태라
    현재 서버에서는 기존과 동일하게 동작 (신규 분기 전부 비활성)
- 장황한 주석 8곳 축약 — 서술형 종결을 명사형으로 통일

### 추가
- `RiskScoreResponse.deviceSeenAt` — 원시 프레임 도착 시각 (nullable)

---

## 2026-09-03

문서 및 저장소 정리.

### 변경
- `PROGRESS.md` 재구성 — 중복 항목 통합, 미해결 사항을 결정 주체별(팀 결정 / 외부 대기 / 앱 작업)로 분리.
  257줄 → 126줄
- 결정 완료된 항목을 미해결 목록에서 제거 — 홈 화면 상단 표시, 알림 보관 기간
- 주소 검색 승인키 만료 사실을 코드 TODO와 `PROGRESS.md`에 반영.
  키가 만료돼 주소 검색이 현재 동작하지 않음

### 추가
- `CHANGELOG.md`
- 루트 `.gitignore` — OS 생성 파일과 IDE 기기·세션별 상태만 대상.
  기존 추적 파일에는 영향 없음(공유용 `.idea` 설정 유지)

### 제거
- `BACKEND_REQUEST.md`
- 루트의 빈 `git` 파일 — 2026-03-29 `b8730ee`에서 실수로 생성된 0바이트 파일, 참조 없음

---

## 2026-09-02 — 전체 코드 리뷰 차수

`ui/login` · `util` · `ui/notification` · `ui/tutorial` · `network` 순회 리뷰.
전체 80개 Kotlin 파일 중 55개 완료, 레이아웃 33개 주석 정리.
**미검증** — 작업 환경에 Android SDK가 없어 컴파일 확인은 하지 못함.

### 보안
- 암호화 인증 저장소(`auth_secure.xml`)를 백업·기기이전 대상에서 제외.
  Keystore 마스터 키는 백업되지 않아 복원해도 복호화 불가
- 디버그 로깅에서 `Authorization` · `Refresh-Token` 헤더 마스킹
- 릴리즈 logcat에 서버·GCS 응답 본문과 WS 원문이 남던 4개 지점을 디버그 전용으로 제한
  (`FallVideoUploader` 3곳, `LandmarkStreamClient` 1곳)
- 알림 조회 실패 시 네트워크 예외 원문을 토스트로 노출하던 것을 고정 문구로 대체.
  백엔드 호스트·포트가 사용자 화면에 드러나던 문제

### 수정
- **백업 복원 후 앱 즉시 종료** — `EncryptedSharedPreferences.create()` 실패가 처리되지 않아
  토큰 접근 경로가 전부 크래시. 손상 파일 폐기 후 재생성하도록 방어 (`TokenManager`)
- **사고이력 조회 전체 실패** — `FallLogResponse.videoStatus`에 Kotlin 기본값 `"none"`을 두었으나
  Gson은 생성자를 거치지 않아 미적용. 서버가 `video_status`를 생략하면 non-null 선언에도 null이 들어와
  `HistoryEntry` 생성자 널 검사에서 실패. nullable 정정 후 매핑 시점에 기본값 확정
- **위험 카드 테두리 미적용** — `progressFill` 부모 캐스팅 실패 시 중간 `return`이
  DANGER 테두리 로직까지 건너뛰던 구조 (`RiskScoreCardBinder`)
- 화면 이탈로 취소된 스코프에서 오류 상태·토스트가 실행되던 문제.
  `CancellationException` 재던지기 누락 (`NotificationViewModel`)
- 로그인 실패 문구가 소비되지 않아 화면 회전 시 재출력되던 문제 (`LoginViewModel`)
- 다이얼러가 없는 기기에서 119 버튼이 크래시하던 경로 (`NotificationActivity`)
- 진입 토스트가 쿨타임을 갱신하지 않아 중복 출력되던 문제 (`FullscreenActivity`)
- 재발송 응답이 진행 중인 다른 요청의 로딩 표시를 끄던 문제 (`FindId`/`FindPwViewModel`)
- `String.format` 로케일 미지정 — 일부 언어에서 타이머 숫자가 깨짐 (`VerificationCodeTimer`)
- 외부 저장소 미탑재 시 크래시 로그가 상대 경로로 떨어져 쓰기 실패하던 문제 (`CrashLogger`)

### 변경
- 알림 목록을 `ListAdapter` + `DiffUtil`로 전환. `notifyDataSetChanged()` 전체 재그리기 제거
- 표시 규칙을 `NotificationType` enum으로 이관 — 어댑터 분기 28줄 → 8줄
- 회원가입 Step1 동의 항목 4쌍의 매직넘버(`isCheck1/2/3/5`)를 리스트로 대체
- 모드 선택의 `selectedMode: Int` 0/1/2를 enum으로 교체
- `RegisterStep2ViewModel`의 완료 조건 9개가 두 벌 있던 것을 `firstMissingRequirement`로 통합
- 발송·재발송 중복 본문 통합 (`RegisterStep2` · `FindId` · `FindPw`)
- 튜토리얼 완료·이탈 중복 블록을 `exitTutorial(completed)`로 통합
- `ApiResponse.message`를 nullable로 정정 — Gson이 생성자를 건너뛰므로 타입과 실제 불일치
- `FieldValidation`을 루트 패키지에서 `util`로 이동
- 레이아웃 주석 정리 — 장식 구분선, 남은 태스크 번호 접두사(`2c:` `2e:`), 설계도구 흔적 제거.
  주석이 없던 3개 파일에 용도 명시

### 추가
- 공통 유틸 — `ApiResult`(응답 판정) · `ViewState`(활성+알파) · `InputBorder`(검증 테두리) ·
  `PasswordToggle` · `TermsLinks` · `Toasts` · `FieldValidation`
- `PhoneField.bindPhoneFormatting()` — 하이픈 자동 삽입의 재진입 가드 유틸화
- `ResetPasswordActivity.EXTRA_USER_ID` · `EXTRA_MODE` — 인텐트 키 상수화
- `TutorialActivity.resetShownFlag()` — prefs 파일명 하드코딩 제거

### 제거
- 읽는 곳이 없던 `putExtra("selected_mode", ...)` (`ModeSelectActivity`)
- 중복 구현 — 눈 아이콘 토글 3벌, 검증 테두리 3벌, 약관 URL 상수 6개,
  `Toast.makeText` 25곳, `visibility` 삼항 분기 33곳, 응답 판정·오류 문구 조립 14곳

---

## 2026-09-02 — 데이터 레이어

- 보호자 홈의 더미 데이터 제거, 실제 기기 ID 연동 (`d8b409d`)
- 사고이력·알림의 공통 조회를 `FallLogSource`로 통합, Fake 저장소 2개 제거
- Python AI 서버 전용 `AiApiService` 분리 — 응답 형식·401 정책이 달라 클라이언트 분리
- `.idea` 기기 선택 캐시 2개 추적 해제 (`c9e79c8`)

## 2026-08-26

- 사고이력 영상 상태(`videoStatus`)로 "준비 중" 문구 분기 (`99254a7`)
- 무인 감시 중 빈 방을 촬영 종료로 오판하던 연결 상태 판정 되돌림 (`1ef5a50`)

## 2026-08-25

- 화면 회전 시 카메라 방향 미반영, 촬영 종료 후 카메라 미해제 수정 (`af0390f`)

## 2026-08-24

- 알림 뱃지 미갱신, 촬영 상태 표시 오류 수정. 입력 검증 보강 (`f55aacb`)

## 2026-08-22

- 비밀번호 변경·로그아웃 요청 필드 수정, 서버 오류 문구 처리 개선 (`70a5625`)
- 과도한 주석 정리 (`3323845`)

## 2026-08-18

- ViewModel 전면 분리, 카메라 회전 대응 (`b38b34e`)
- 크래시 발생 시 로그 파일 생성 (`3104a44`)

## 2026-08-11

- 튜토리얼 애니메이션, 로고 폰트 통일, 사고이력 에러뷰 개선 (`933db3d`)

## 2026-08-10

- 로그인·회원가입·찾기·주소검색 화면 ViewModel 분리 (`35957ae` `a500c79` `2acf287`)
- UI 아이콘 오류, 설정 토글, 자동 로그인 세션 만료 버그 수정 (`040113b`)

## 2026-08-08

- 회원가입 요청 DTO에 `marketingConsent` 필드 추가 (`ff38cfd`)

## 2026-08-05

- 아이콘 세트 전면 교체 (`56dabad`)
