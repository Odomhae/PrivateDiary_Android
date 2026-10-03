package com.odom.privatediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odom.privatediary.pipeline.PipelineScreen
import com.odom.privatediary.pipeline.PipelineViewModel
import com.odom.privatediary.ui.theme.PrivateDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateDiaryTheme {
                val vm: PipelineViewModel = viewModel()
                PipelineScreen(vm)
            }
        }
    }
}
