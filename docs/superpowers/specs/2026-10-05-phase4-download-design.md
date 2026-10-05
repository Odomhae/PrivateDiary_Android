# 4단계 모델 다운로드 — 설계 문서

- 작성일: 2026-10-05
- 대상: `voice-diary-spec.md` §8 4단계 + §3.3
- 선행: 1~3단계 완료
- 상태: 승인됨

## 1. 목적
첫 실행 시 Cloudflare R2에서 모델 2개(whisper ggml, gemma litertlm)를 이어받기·SHA-256 검증·Wi-Fi 옵션·진행률과 함께 받아 `filesDir/models`에 저장하고, 온보딩에 포함한다. R2 URL은 플레이스홀더로 두고 추후 채운다.

## 2. 범위
### 포함
- 모델 매니페스트(JSON) 파싱, SHA-256 검증, Range 이어받기 바이트 계산, 저장 공간 확인(순수 로직)
- OkHttp 다운로더(Range, `*.part`→검증→rename)
- WorkManager 포그라운드 Worker(알림, Wi-Fi 전용 제약, 모델별 다운로드+검증)
- 온보딩/다운로드 화면 + 진행률, 모델 미준비 시 진입 게이트
- `INTERNET`, `POST_NOTIFICATIONS` 권한

### 제외 (후속)
- 모델 버전 업그레이드 마이그레이션, 재다운로드/삭제 UI 디테일
- 실제 R2 버킷/URL(플레이스홀더 설정; 사용자가 채움)

## 3. 매니페스트 포맷 (R2의 `manifest.json`)
```json
{
  "version": 1,
  "models": [
    {"name": "ggml-small.bin", "path": "whisper/ggml-small.bin", "bytes": 487000000, "sha256": "<hex>"},
    {"name": "gemma-3n-E2B-it.litertlm", "path": "gemma/gemma-3n-E2B-it.litertlm", "bytes": 3700000000, "sha256": "<hex>"}
  ]
}
```
`name`은 `ModelPaths`의 파일명(`ggml-small.bin`, `gemma-3n-E2B-it.litertlm`)과 일치해야 한다.

## 4. 구성 (패키지 `com.odom.privatediary.download`)
**순수 로직 (JVM TDD):**
- `ModelManifest`/`ModelFile` (`@Serializable`) + `ManifestParser.parse(text)`
- `Sha256` — `hex(File)`, `verify(File, expectedHex)` (MessageDigest)
- `DownloadPlan` — `rangeHeader(existing)`, `isComplete(existing, total)`
- `StorageCheck` — `hasSpaceFor(required, free)`
- `R2Config` — base URL(플레이스홀더)·매니페스트 경로·URL 조합

**스캐폴드 (컴파일 + 기기/네트워크 검증):**
- `ModelDownloader` (OkHttp Range)
- `ModelDownloadWorker` (WorkManager CoroutineWorker, 포그라운드 알림, 제약)
- `DownloadViewModel` + `OnboardingScreen`
- 진입 게이트: `MainActivity`가 `ModelPaths.ready()`로 start 라우트 결정(`onboarding`/`home`)

## 5. 데이터 흐름
온보딩 → `DownloadViewModel.start(wifiOnly)` → WorkManager enqueue(제약: wifiOnly면 UNMETERED) → Worker가 매니페스트 fetch → 모델별 `ModelDownloader.download`(이어받기) → `Sha256.verify` → 완료. ViewModel은 `WorkInfo` 진행 관찰. 모든 모델 준비되면 `home`으로.

## 6. 테스트
- JVM TDD: `ManifestParser`(파싱), `Sha256`(known-hash "abc"), `DownloadPlan`(range 문자열·완료 판정), `StorageCheck`, `R2Config`(URL 조합).
- 스캐폴드: Downloader/Worker/화면은 `compileDebugKotlin` 검증. 실제 다운로드·이어받기·알림·제약은 R2+기기(사용자).

## 7. 빌드
- `androidx.work:work-runtime-ktx`, `com.squareup.okhttp3:okhttp` 추가(해석 교정). 둘 다 애너테이션 프로세서 불필요.
- `AndroidManifest`: `INTERNET`, `POST_NOTIFICATIONS`.

## 8. 성공 기준
1. 순수 로직(매니페스트·체크섬·range·공간·URL)이 JVM 테스트로 검증됨.
2. Downloader/Worker/화면/게이트가 컴파일되고 배선됨.
3. R2 `BASE_URL`만 채우면 기기에서 다운로드가 돌 수 있는 상태(검증은 사용자).

## 9. 리스크
- OkHttp/WorkManager 버전 정렬 — Task 1 해석 교정.
- 실제 네트워크·포그라운드·제약은 미검증(기기+R2 필요).
- R2 `BASE_URL`은 플레이스홀더 — 릴리스 전 반드시 교체(코드에 명시).
