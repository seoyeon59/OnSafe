# 백엔드 요청 사항 (Android 클라이언트 → On-safe-backend)

작성일: 2026-09-02
대상 저장소: `On-safe-backend-main`
배경: 보호자 홈 화면의 더미 데이터("김순자 님", "기기 ID: ONS-2024-0831")를 제거하고
실제 기기 ID를 표시하도록 연동하는 과정에서 확인된 사항입니다.

---

## 1. `POST /api/devices/{user_id}` — 같은 소유자의 재등록이 409를 반환합니다 (우선순위: 높음)

**위치:** `app/domain/devices/service.py` `register_device()`

**현상:**
```python
if doc.exists:
    existing = doc.to_dict()
    if existing.get("user_id") != user_id:
        ...  # 소유권 이전 → {"status": "updated"}
    raise conflict("이미 등록된 기기 ID입니다")   # ← 같은 소유자면 항상 409
```

동일 사용자가 같은 기기를 다시 등록하면 409가 납니다.
API 명세 v4.2에는 "upsert"로 기재되어 있어 문서와 구현이 어긋납니다.

**클라이언트 영향:**
Android가 카메라 모드 진입 시마다 이 API를 호출합니다(기기 등록 목적).
최초 1회만 성공하고 이후에는 매번 409 → `last_seen`이 갱신되지 않고 서버 로그에 409가 누적됩니다.
현재 앱은 실패를 무시하도록 되어 있어 사용자에게 보이는 오류는 없습니다.

**요청:**
같은 소유자의 재등록 시 `device_name` / `last_seen`을 갱신하고 200 `{"status": "updated"}` 반환.

---

## 2. `devices.status` / `last_seen`이 등록 이후 갱신되지 않습니다 (우선순위: 높음)

**현상:**
- `register_device()`가 `status: "inactive"`로 최초 기록
- 이후 이 값을 갱신하는 코드가 저장소 어디에도 없음
- `/ws/stream`(`app/domain/camera/router.py`)은 프레임을 처리하지만 `devices` 컬렉션에 쓰지 않음

**결과:**
- `GET /api/devices/{user_id}` → 카메라가 정상 동작 중이어도 항상 `"inactive"`
- `GET /api/camera/status/{device_id}` → 항상 `offline` 계열

**클라이언트 영향:**
앱은 이 `status`를 신뢰할 수 없어 사용하지 않고, 화면의 연결 상태는
`GET /api/camera/score/{userId}` 폴링 결과로 대체 판정하고 있습니다.

**요청:**
1. `/ws/stream`의 `init` 수신 시(또는 `process_frame` 처리 시)
   해당 `device_id`의 `status: "active"`, `last_seen: now` 갱신
2. `get_devices()` / `get_device_status()`에서 `last_seen`이 임계 시간
   (예: 30초)보다 오래되면 `offline`으로 내려주는 판정 추가

2번이 반영되면 앱의 연결 상태 판정을 폴링 기반에서 서버 값 기반으로 정확하게 바꿀 수 있습니다.
(`MainViewModel.applyFreshness()`에 남아 있는 협의 중 주석이 이 건입니다.)

---

## 3. `/api/devices/*`의 운영 라우팅 확인 요청 (우선순위: 중)

**현황:**
- Android `BASE_URL`은 Kotlin 서버(로컬 8080)를 가리킴
- Kotlin 서버에는 `/api/devices`가 없음 (명세 v4.2에서 "미사용 Kotlin `/api/devices` 중복 제거"로 삭제됨)
- 따라서 앱은 Python 서버(로컬 8000)의 `/api/devices/{user_id}`를 직접 호출하도록 구현했습니다

**확인 부탁:**
운영 환경에서 `https://api.neulbom.com/api/devices/*` 요청이 Python 서비스로
라우팅되는지 여부입니다.

- 게이트웨이에서 경로 기반으로 Python에 프록시된다면 → 앱은 수정 불필요
- Python이 별도 호스트/포트/경로 프리픽스를 쓴다면 → 정확한 주소를 알려주시면
  앱의 `AI_BASE_URL`을 맞추겠습니다 (현재는 Kotlin과 같은 호스트로 임시 설정 + TODO 표시)

---

## 4. `/ws/stream` 토큰이 쿼리스트링으로 전달됩니다 (우선순위: 중)

**위치:** `app/domain/camera/router.py` `ws_stream(websocket, token: str = Query(...))`

**현상:**
액세스 토큰이 URL 쿼리 파라미터로 전달됩니다.

```
ws://.../ws/stream?token=eyJhbGciOi...
```

URL은 리버스 프록시·로드밸런서·서버 접근 로그에 그대로 남는 것이 일반적이라,
토큰이 로그 파일에 평문으로 축적될 수 있습니다.

**요청:**
`Authorization: Bearer` 헤더 또는 `Sec-WebSocket-Protocol` 기반 인증 지원.
앱은 OkHttp를 쓰기 때문에 WS 핸드셰이크에 임의 헤더를 붙일 수 있습니다
(브라우저 클라이언트와 달리 제약이 없습니다).

서버가 지원하면 앱 쪽은 `LandmarkStreamClient.connect()` 한 곳만 바꾸면 됩니다.
현재는 해당 위치에 TODO로 표시해 두었습니다.

---

## 참고: 응답 형식 차이 (조치 불필요)

Python은 flat JSON, Kotlin은 `{success, message, data}` 래퍼를 쓰는 점은
명세 v3.0에 문서화되어 있어 앱에서 별도 DTO/Retrofit 클라이언트로 분리 대응했습니다.
서버 변경은 필요하지 않습니다.
