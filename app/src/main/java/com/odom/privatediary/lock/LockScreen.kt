package com.odom.privatediary.lock

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** NavHost 위를 덮는 불투명 잠금 화면. 처음 보일 때 한 번 자동으로 인증을 띄운다. */
@Composable
fun LockScreen(lock: AppLock) {
    val activity = LocalActivity.current as FragmentActivity
    val error by lock.authError.collectAsStateWithLifecycle()
    var prompted by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val unlock = { lock.authenticate(activity, "음성 일기 잠금 해제") }

    // 가려진 화면의 포커스·키보드를 거두고, 뒤로 가기는 가려진 백스택 대신 앱을 내린다.
    LaunchedEffect(Unit) {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }
    BackHandler { activity.moveTaskToBack(true) }

    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            unlock()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("음성 일기가 잠겨 있어요")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = unlock) { Text("잠금 해제") }
        }
    }
}
