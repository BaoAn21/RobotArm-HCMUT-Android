package com.example.robotarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.robotarm.common.RobotCamera
import com.example.robotarm.common.VideoServer

@Composable
fun CameraStreamScreen(onBack: () -> Unit) {
    // 1. Start/Stop Video Server when entering/leaving screen
    LaunchedEffect(Unit) {
        VideoServer.start()
    }
    DisposableEffect(Unit) {
        onDispose { VideoServer.stop() }
    }

    // 2. State for status text
    // We can update this from inside VideoServer if we want,
    // but for now, we just show that we are transmitting.
    val statusText = "Streaming to Port 6001..."

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- CAMERA ---
        // We reuse your RobotCamera, but we don't do any tracking logic.
        // We just get the bitmap and send it.
        RobotCamera(targetWidth = 640, targetHeight = 480) { bitmap, _, _, _ ->
            // Send directly to the Video Server
            VideoServer.sendFrame(bitmap)
        }

        // --- OVERLAY ---
        Box(modifier = Modifier.fillMaxSize()) {
            // Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Status Badge
            Text(
                text = statusText,
                color = Color.Green,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            )
        }
    }
}