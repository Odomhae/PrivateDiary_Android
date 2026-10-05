package com.odom.privatediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.odom.privatediary.bench.ModelPaths
import com.odom.privatediary.ui.AppNavHost
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val paths = ModelPaths.from(this)
        val start = if (paths.whisperReady() && paths.gemmaReady()) "home" else "onboarding"
        setContent {
            PrivateDiaryTheme {
                AppNavHost(startDestination = start)
            }
        }
    }
}
