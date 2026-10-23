package com.pitchtrace.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局动效开关。默认开；截图测试或“减少动效”场景下关掉后，
 * 呼吸圈之类的无限动画不再启动，Compose 才能进入空闲状态。
 */
val LocalMotionEnabled = staticCompositionLocalOf { true }
