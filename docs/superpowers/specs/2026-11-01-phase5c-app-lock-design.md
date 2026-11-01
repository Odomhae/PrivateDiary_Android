# 5c단계 앱 잠금 + 설정 화면 — 설계 문서

- 작성일: 2026-11-01
- 대상: `voice-diary-spec.md` §5.1 앱 잠금, §5.3 설정의 상담·진단 안내, §7-8 설정 화면(잠금 항목만)
- 선행: 5b(주간 회고) 완료, 클라우드 백업 제외 적용(`319bcc0`)
- 상태: 구현 완료(기기 확인 대기)

## 1. 목적
`BiometricPrompt`(지문·얼굴·기기 PIN/패턴)로 앱을 잠근다. 설정 화면에서 켜고 끈다.

## 2. 결정 사항
- 인증 방식: `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` — API 26~29에서도 지원되는 조합, 기기 PIN 폴백 포함
- 기본값: **꺼짐**. 켤 때 인증 1회 성공해야 켜짐(잠금 해제 불가 상태 방지). 끌 때는 인증 없음(이미 잠금 해제된 상태)
- 잠기는 시점: 콜드 스타트, 그리고 백그라운드에 **30초 이상** 있다가 돌아올 때. 회전(`isChangingConfigurations`)은 무시
- 인증 중(PIN 화면이 별도 Activity로 떠서 onStop 발생)에는 백그라운드 전환을 기록하지 않음 → 잠금 반복 방지
- 기기 화면 잠금이 없어져 인증이 불가능하면 잠그지 않고 설정 화면에 경고(잠금 해제 불가로 일기를 잃는 것보다 낫고, 기기 자체가 무방비라 앱 잠금 의미도 없음)
- 잠금 화면은 NavHost 위 **불투명 오버레이** — 내비게이션·녹음 ViewModel 상태 유지. 설정 로딩 전에는 빈 화면(내용 깜빡임 방지)
- 저장: DataStore Preferences(`settings`, `lock_enabled`). `files/datastore`라 클라우드 백업 제외 대상
- 설정 화면 범위: 앱 잠금 토글 + "상담·진단을 하지 않음 / 109" 안내. 알림·음성 보관·모델 관리·내보내기·전체 삭제는 6단계 이후
- 리뷰 반영:
  - 설정 파일 손상·읽기 실패 시 잠금 켜짐으로 처리(fail-closed, 앱 크래시 방지)
  - 잠긴 상태에서 기기 화면 잠금이 사라지면 복귀 시 해제(강제 종료 없이는 못 푸는 상태 방지)
  - "인증 중" 표시는 2분만 유효(콜백 유실 시 재잠금이 영구히 꺼지는 것 방지). Fragment 상태 저장 후에는 프롬프트를 띄우지 않음
  - 잠긴 동안 가려진 화면의 포커스·키보드 차단, 뒤로 가기는 앱을 내림
  - 인증 오류 문구는 `AppLock`에 보관(회전 후 유지)
- 제외: 최근 앱 화면 가리기(`FLAG_SECURE`는 사용자 스크린샷까지 막음, 대안 `setRecentsScreenshotEnabled(false)`는 API 33+) — 별도 결정

## 3. 구성 (`com.odom.privatediary.lock`, `settings`)
- `lock/LockPolicy` (순수, JVM 테스트): 상태 `Loading | Locked | Unlocked`
  - `onSettingsLoaded(enabled, available)`: 첫 로드면 `enabled && available`이면 Locked
  - `onBackground(now)` / `onForeground(now, enabled, available)`: 30초 이상 + 인증 중 아님 → Locked
  - `onAuthStarted()` / `onAuthFinished(success)`: 성공이면 Unlocked, 백그라운드 기록 초기화
  - 잠금을 끄면 즉시 Unlocked
- `lock/AppLock` (앱 범위 싱글턴): `LockPolicy` + 설정 Flow + `BiometricManager` 가용성, `state: StateFlow<LockState>`
- `lock/BiometricAuth`: `FragmentActivity`에서 프롬프트 실행, 결과 콜백
- `lock/LockScreen`: 진입 시 자동 프롬프트 1회 + "잠금 해제" 버튼, 오류 문구
- `settings/SettingsStore`: DataStore 래퍼
- `ui/SettingsScreen` + `SettingsViewModel`: 홈 "설정" 버튼 → `settings` 경로
- `MainActivity`: `FragmentActivity`로 변경, onStart/onStop을 `AppLock`에 전달

## 4. 의존성
- `androidx.biometric:biometric:1.1.0`, `androidx.datastore:datastore-preferences:1.2.1`, `androidx.fragment:fragment-ktx:1.9.1`
- 권한 추가 없음(`USE_BIOMETRIC`은 biometric 라이브러리 매니페스트에서 병합)

## 5. 테스트
- `LockPolicyTest`: 콜드 스타트 잠금/미잠금, 30초 미만 복귀 미잠금, 30초 이상 잠금, 인증 중 onStop 무시, 인증 성공 후 해제, 가용성 없음 → 미잠금, 잠금 끄기 → 해제, 잠긴 상태에서 기기 잠금 제거 → 해제, 오래된 인증 표시 무시
- 기기 확인 항목: 지문/PIN 프롬프트, API 29 이하 PIN 폴백, PIN 화면 왕복 후 재잠금 없음, 회전
