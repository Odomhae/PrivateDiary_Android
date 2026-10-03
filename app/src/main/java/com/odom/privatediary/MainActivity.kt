package com.odom.privatediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odom.privatediary.bench.BenchScreen
import com.odom.privatediary.bench.BenchViewModel
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateDiaryTheme {
                val vm: BenchViewModel = viewModel()
                BenchScreen(vm)
            }
        }
    }
}
