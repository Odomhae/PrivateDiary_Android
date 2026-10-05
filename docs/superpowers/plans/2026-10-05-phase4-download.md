# 4단계 모델 다운로드 Implementation Plan

> Inline execution. Pure logic TDD via `./gradlew :app:testDebugUnitTest`; Downloader/Worker/screens via compile. Branch `feat/phase4-download`.

**Goal:** R2에서 모델을 이어받기·체크섬·Wi-Fi 옵션·진행률로 받아 `filesDir/models`에 저장하고 온보딩에 연결한다.

## Global Constraints
- 패키지 `com.odom.privatediary.download`.
- 매니페스트 `name`은 `ModelPaths` 파일명과 일치(`ggml-small.bin`, `gemma-3n-E2B-it.litertlm`).
- R2 `BASE_URL`은 플레이스홀더(코드에 교체 필요 명시).
- OkHttp/WorkManager는 애너테이션 프로세서 불필요. 커밋 trailer 포함.

---

### Task 1: 빌드 — OkHttp + WorkManager + 권한
- versions: `okhttp = "4.12.0"`, `workManager = "2.10.0"` (해석 실패 시 교정).
- libs: `okhttp = {group="com.squareup.okhttp3", name="okhttp", version.ref="okhttp"}`, `androidx-work-runtime-ktx = {group="androidx.work", name="work-runtime-ktx", version.ref="workManager"}`.
- build.gradle deps: `implementation(libs.okhttp)`, `implementation(libs.androidx.work.runtime.ktx)`.
- AndroidManifest: `<uses-permission android:name="android.permission.INTERNET" />`, `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- 검증: `:app:dependencies`. Commit.

### Task 2: 순수 로직 + 테스트
`download/Manifest.kt`:
```kotlin
package com.odom.privatediary.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class ModelFile(val name: String, val path: String, val bytes: Long, val sha256: String)
@Serializable data class ModelManifest(val version: Int, val models: List<ModelFile>)

object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): ModelManifest = json.decodeFromString(ModelManifest.serializer(), text)
}
```
`download/Sha256.kt`:
```kotlin
package com.odom.privatediary.download

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(file: File, expectedHex: String): Boolean = hex(file).equals(expectedHex, ignoreCase = true)
}
```
`download/DownloadPlan.kt`:
```kotlin
package com.odom.privatediary.download

object DownloadPlan {
    fun rangeHeader(existingBytes: Long): String = "bytes=$existingBytes-"
    fun isComplete(existingBytes: Long, totalBytes: Long): Boolean = totalBytes > 0 && existingBytes >= totalBytes
}
```
`download/StorageCheck.kt`:
```kotlin
package com.odom.privatediary.download

object StorageCheck {
    fun hasSpaceFor(requiredBytes: Long, freeBytes: Long): Boolean = freeBytes >= requiredBytes
}
```
`download/R2Config.kt`:
```kotlin
package com.odom.privatediary.download

object R2Config {
    // 릴리스 전 실제 R2 공개 base URL로 교체할 것(플레이스홀더).
    const val BASE_URL = "https://REPLACE-ME.r2.example/models"
    const val MANIFEST_PATH = "manifest.json"
    fun url(path: String): String = BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    fun manifestUrl(): String = url(MANIFEST_PATH)
}
```
Tests (`download/...Test.kt`): ManifestParser(파싱 필드), Sha256("abc"→ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad), DownloadPlan(range/완료), StorageCheck, R2Config(url 조합).

### Task 3: ModelDownloader (스캐폴드, OkHttp Range)
`download/ModelDownloader.kt` — `*.part`에 이어받기, 206이면 append, 완료 후 rename. `DownloadPlan.rangeHeader` 사용.

### Task 4: ModelDownloadWorker (스캐폴드, WorkManager)
`download/ModelDownloadWorker.kt` — CoroutineWorker: 매니페스트 fetch → 모델별 download+`Sha256.verify` → setForeground 알림/`setProgress`. 제약은 enqueue 측에서.

### Task 5: DownloadViewModel + OnboardingScreen + 게이트 (스캐폴드)
`download/DownloadViewModel.kt`(WorkManager enqueue + WorkInfo 관찰 + wifiOnly 제약), `download/OnboardingScreen.kt`(진행률·Wi-Fi 토글·시작), `MainActivity`/`AppNavHost` 게이트(`ModelPaths.ready()` 아니면 `onboarding` 시작).

전체 검증: `./gradlew :app:testDebugUnitTest`. 성공 시 커밋.

## Self-Review
- §3.3 매니페스트·이어받기·체크섬·Wi-Fi·진행률·온보딩 → T2(로직)/T3/T4/T5. 권한 T1. 순수 로직만 JVM 테스트; 네트워크·포그라운드는 기기.
