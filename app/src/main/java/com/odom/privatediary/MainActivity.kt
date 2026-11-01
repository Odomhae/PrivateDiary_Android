package com.odom.privatediary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odom.privatediary.bench.ModelPaths
import com.odom.privatediary.lock.AppLock
import com.odom.privatediary.lock.LockScreen
import com.odom.privatediary.lock.LockState
import com.odom.privatediary.ui.AppNavHost
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

// BiometricPrompt가 FragmentActivity를 요구한다(ComponentActivity 하위 클래스라 Compose는 그대로).
class MainActivity : FragmentActivity() {
    private val lock by lazy { AppLock.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val paths = ModelPaths.from(this)
        val start = if (paths.whisperReady() && paths.gemmaReady()) "home" else "onboarding"
        setContent {
            PrivateDiaryTheme {
                val lockState by lock.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    // 잠금 화면은 위에 덮어서 내비게이션·녹음 상태를 유지한다.
                    // 잠긴 동안에는 접근성 서비스에도 아래 내용을 노출하지 않는다.
                    // 하드웨어 키보드·D-pad 포커스도 막는다.
                    val hidden = if (lockState == LockState.Unlocked) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {}
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    }
                    Box(hidden.fillMaxSize()) { AppNavHost(startDestination = start) }
                    when (lockState) {
                        LockState.Loading -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                        LockState.Locked -> LockScreen(lock)
                        LockState.Unlocked -> Unit
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lock.onBackground()
    }
}
