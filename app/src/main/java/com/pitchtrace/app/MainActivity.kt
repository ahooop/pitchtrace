package com.pitchtrace.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pitchtrace.app.ui.PitchTraceScreen
import com.pitchtrace.app.ui.theme.PitchTraceTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: PitchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 起 targetSdk 35 强制全屏沉浸，这里显式统一行为，
        // 由 Compose 侧用 statusBarsPadding / navigationBarsPadding 处理安全区。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            PitchTraceTheme {
                val state by viewModel.ui.collectAsState()
                val context = LocalContext.current
                val view = LocalView.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        viewModel.startListening()
                    } else {
                        viewModel.reportError("需要麦克风权限才能采集音高")
                    }
                }

                val requestMic: () -> Unit = remember(context) {
                    {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }

                // 退到后台就停止采集：既省电，也避免用户以为没在录音
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) viewModel.stopListening()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // 采集时保持屏幕常亮，这是调音类工具的默认预期
                DisposableEffect(state.mode) {
                    val window = (view.context as? Activity)?.window
                    if (state.mode == Mode.LIVE) {
                        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { }
                }

                // 错误提示自动消失
                LaunchedEffect(state.error) {
                    if (state.error != null) {
                        delay(3200)
                        viewModel.consumeError()
                    }
                }

                PitchTraceScreen(
                    state = state,
                    onMicClick = {
                        if (state.mode == Mode.LIVE) viewModel.stopListening() else requestMic()
                    },
                    onPlayClick = viewModel::togglePlayback,
                    onClearClick = viewModel::clearTake,
                    onA4Change = viewModel::setA4,
                    onRangeChange = viewModel::setRange,
                    onDismissError = viewModel::consumeError,
                )
            }
        }
    }
}
