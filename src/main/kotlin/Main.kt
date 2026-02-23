package com.jimandreas

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jimandreas.ui.App
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(800.dp, 660.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "ScanWithOCRtoPDF",
        state = windowState
    ) {
        DisposableEffect(Unit) {
            val listener = object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    window.extendedState = Frame.NORMAL
                    window.isAlwaysOnTop = true
                    window.toFront()
                    window.requestFocus()
                    window.isAlwaysOnTop = false
                }
            }
            window.addWindowListener(listener)
            onDispose { window.removeWindowListener(listener) }
        }
        App()
    }
}
