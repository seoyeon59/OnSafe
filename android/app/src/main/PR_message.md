# 변경 사항

## ViewModel 마이그레이션

화면 회전 등으로 Activity가 재생성돼도 데이터가 안 날아가도록, 네트워크 호출·타이머·로딩/에러 상태를 ViewModel로 분리하고 화면 그리기 관련 코드만 Activity에 남기는 작업.

- 로그인/회원가입 화면 6개 전환 완료: 아이디/비밀번호 찾기, 비밀번호 재설정, 회원가입 2단계, 주소 검색, 로그인 (`AddressSearchActivity`, `FindIdActivity`, `FindPwActivity`, `ResetPasswordActivity`, `RegisterStep2Activity`, `LoginActivity`)
- 남은 화면(홈, 사고이력, 설정, 카메라 모드)은 현재 다른 작업이 진행 중이라 보류
- 관련 gradle 의존성 추가 (`lifecycle-viewmodel-ktx`, `activity-ktx`)

## 버그 수정

- 모드 선택 화면: 카드 선택 시 아이콘 색상 미반영 수정
- 사고이력 하단 네비 아이콘 깨짐 수정 — 벡터 파일 자체가 손상되어 있었음 (`ic_history_filled.xml`)
- 설정 화면 소리/진동 아이콘 on/off 크기 불일치 수정
- 설정 화면: 알림 권한 허용 후에도 토글이 실제로 반영 안 되던 버그 수정
- 설정 화면: 토글 연속 클릭 시 저장 순서가 꼬일 수 있던 부분 방지 처리
- 회원가입 약관 화살표 아이콘 색상을 회색으로 변경
- 자동 로그인 30일 만료 정책이 사실상 무력화되던 문제 수정 — 조용한 토큰 갱신 때마다 로그인 시각이 덮어써지고 있었음 (`TokenManager`, `ApiClient`)

## 참고 — 다음에 확인 필요

- 약관 URL 미입력 (`LoginActivity`, `RegisterStep1Activity`)
- juso 주소 검색 API 키가 소스코드에 그대로 노출됨 (`AddressSearchViewModel`)
- 카메라 스트리밍 시작할 때마다 OkHttpClient가 새로 생성됨 — 재사용 필요 (`LandmarkStreamClient`)
- 낙상 클립 업로드 실패 시 로컬 파일이 삭제되지 않고 계속 쌓임 (`FallVideoUploader`)
