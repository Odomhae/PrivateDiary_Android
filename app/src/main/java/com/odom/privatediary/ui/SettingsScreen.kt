package com.odom.privatediary.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odom.privatediary.lock.AuthResult

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    val enabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val available by vm.lockAvailable.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshAvailability() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("설정")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("앱 잠금")
                Text(
                    "지문·얼굴 인식 또는 기기 PIN으로 잠금을 풉니다. 30초 넘게 나가 있으면 다시 잠깁니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = enabled,
                enabled = available || enabled,
                onCheckedChange = { on ->
                    error = null
                    if (!on) {
                        vm.setLockEnabled(false)
                    } else {
                        // 켜기 전에 한 번 인증해 실제로 풀 수 있는지 확인한다.
                        vm.lock.authenticate(activity, "앱 잠금 켜기") { r ->
                            when (r) {
                                AuthResult.Success -> vm.setLockEnabled(true)
                                is AuthResult.Error -> error = r.message
                            }
                        }
                    }
                },
            )
        }
        if (!available) {
            Text(
                "기기에 화면 잠금(PIN·패턴·비밀번호)이 없어 앱 잠금을 쓸 수 없어요. 기기 설정에서 화면 잠금을 먼저 설정하세요.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("이 앱은 상담이나 진단을 하지 않습니다. 위급할 때는 자살예방 상담전화 $CRISIS_LINE(24시간)로 연락하세요.")
        Text(
            "일기·녹음·분석 결과는 이 기기 안에만 저장되며 Google 클라우드 백업에도 포함되지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onBack) { Text("뒤로") }
    }
}
