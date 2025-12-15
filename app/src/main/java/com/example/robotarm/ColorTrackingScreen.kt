package com.example.robotarm

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.robotarm.common.CameraColor
import com.example.robotarm.common.RobotCamera
import com.example.robotarm.common.UsbServer
import kotlin.math.abs

@Composable
fun ColorTrackingScreen(onBack: () -> Unit) {
    // --- 1. SETUP & CONFIG ---
    val deadZoneSize = 60f // Pixels (The "Close Enough" box)

    // AREA THRESHOLDS (Percentage)
    val areaMin = 6.0f
    val areaMax = 10.0f

    // Start USB Server
    LaunchedEffect(Unit) { UsbServer.start() }
    DisposableEffect(Unit) { onDispose { UsbServer.stop() } }

    // --- 2. STATES ---
    var isMirrored by remember { mutableStateOf(false) }

    // Tracking Data
    var detectedRect by remember { mutableStateOf<RectF?>(null) }
    var trackingError by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var trackingStatus by remember { mutableStateOf("Scanning...") }

    // New: Area Percentage Text
    var areaInfo by remember { mutableStateOf("Area: 0%") }
    var depthStatus by remember { mutableStateOf("") } // Visual debug for Fwd/Back

    // Visual Lock State
    var isLocked by remember { mutableStateOf(false) }

    // Layout Data
    var cameraRatio by remember { mutableFloatStateOf(0.75f) }
    var sourceWidth by remember { mutableFloatStateOf(320f) }
    var sourceHeight by remember { mutableFloatStateOf(240f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // --- 3. CAMERA BOX ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cameraRatio)
        ) {
            RobotCamera(targetWidth = 320, targetHeight = 240) { bitmap, rotation, isFront, ratio ->
                cameraRatio = ratio

                // Swap dimensions if rotated (90/270)
                if (rotation == 90 || rotation == 270) {
                    sourceWidth = bitmap.height.toFloat()
                    sourceHeight = bitmap.width.toFloat()
                } else {
                    sourceWidth = bitmap.width.toFloat()
                    sourceHeight = bitmap.height.toFloat()
                }

                // A. DETECT
                val rawRect = CameraColor.findYellowRect(bitmap)

                // B. ROTATE
                var finalRect = if (rawRect != null) {
                    rotateRect(rawRect, rotation, bitmap.width.toFloat(), bitmap.height.toFloat())
                } else {
                    null
                }

                // C. MIRROR
                if (isMirrored && finalRect != null) {
                    val newLeft = sourceWidth - finalRect.right
                    val newRight = sourceWidth - finalRect.left
                    finalRect = RectF(newLeft, finalRect.top, newRight, finalRect.bottom)
                }

                detectedRect = finalRect

                // D. CALCULATE ERROR & AREA
                if (finalRect != null) {
                    // --- 1. Position Math (X/Y) ---
                    val centerX = finalRect.centerX()
                    val centerY = finalRect.centerY()
                    val errX = centerX - (sourceWidth / 2)
                    val errY = centerY - (sourceHeight / 2)
                    trackingError = Pair(errX, errY)

                    // --- 2. Area Math (Z / Depth) ---
                    val rectArea = finalRect.width() * finalRect.height()
                    val totalArea = sourceWidth * sourceHeight
                    val percentage = if (totalArea > 0) (rectArea / totalArea) * 100 else 0f
                    areaInfo = "Area: ${"%.1f".format(percentage)}%"

                    // --- 3. Depth Logic (Forward/Backward) ---
                    // 1.0 = Forward, -1.0 = Backward, 0.0 = Stop
                    var depthCommand = 0f

                    if (percentage < areaMin) {
                        depthCommand = 1.0f // Too small -> Move Forward
                        depthStatus = "FWD"
                    } else if (percentage > areaMax) {
                        depthCommand = -1.0f // Too big -> Move Backward
                        depthStatus = "BCK"
                    } else {
                        depthCommand = 0.0f // Perfect -> Stop
                        depthStatus = "OK"
                    }

                    // --- 4. Deadzone Logic (X/Y) ---
                    val threshold = deadZoneSize / 2
                    val outputX = if (abs(errX) < threshold) 0f else errX
                    val outputY = if (abs(errY) < threshold) 0f else errY

                    // --- 5. SEND DATA (X, Y, Z_Command) ---
                    // Make sure UsbServer.sendData accepts 3 floats!
                    UsbServer.sendData(outputX, outputY, depthCommand)

                    if (outputX == 0f && outputY == 0f && depthCommand == 0f) {
                        isLocked = true
                        trackingStatus = "LOCKED (All Axes)"
                    } else {
                        isLocked = false
                        trackingStatus = "X:${outputX.toInt()} Y:${outputY.toInt()} Z:$depthStatus"
                    }

                } else {
                    // No object detected
                    trackingError = null
                    isLocked = false
                    trackingStatus = "Scanning..."
                    areaInfo = "Area: 0%"
                    depthStatus = ""
                    // Send 0,0,0 to stop robot
                    UsbServer.sendData(0f, 0f, 0f)
                }
            }

            // --- 4. VISUAL DEBUG OVERLAY ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / sourceWidth
                val scaleY = size.height / sourceHeight
                val cx = size.width / 2
                val cy = size.height / 2

                val scaledDeadZoneW = deadZoneSize * scaleX
                val scaledDeadZoneH = deadZoneSize * scaleY

                // Draw Deadzone Box
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(cx - scaledDeadZoneW/2, cy - scaledDeadZoneH/2),
                    size = Size(scaledDeadZoneW, scaledDeadZoneH),
                    style = Stroke(width = 4f)
                )

                detectedRect?.let { rect ->
                    // Color code the box based on distance
                    val boxColor = when (depthStatus) {
                        "FWD" -> Color.Blue   // Blue = Too Far
                        "BCK" -> Color.Red    // Red = Too Close
                        else -> Color.Green   // Green = Good
                    }

                    drawRect(
                        color = boxColor,
                        topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                        size = Size(
                            (rect.right - rect.left) * scaleX,
                            (rect.bottom - rect.top) * scaleY
                        ),
                        style = Stroke(width = 6f)
                    )
                }

                trackingError?.let { (errX, errY) ->
                    val vectorX = errX * scaleX
                    val vectorY = errY * scaleY
                    val targetX = cx + vectorX
                    val targetY = cy + vectorY

                    drawLine(
                        color = Color.Magenta,
                        start = Offset(cx, cy),
                        end = Offset(targetX, targetY),
                        strokeWidth = 5f
                    )
                    drawCircle(Color.Magenta, radius = 10f, center = Offset(targetX, targetY))
                }
            }
        }

        // --- 5. UI CONTROLS ---
        Box(modifier = Modifier.fillMaxSize()) {
            // Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Mirror Toggle
            IconButton(
                onClick = { isMirrored = !isMirrored },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 80.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Mirror",
                    tint = if (isMirrored) Color.Yellow else Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Status Text (Top)
            Text(
                text = trackingStatus,
                color = if (isLocked) Color.Green else Color.Yellow,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
            )

            // Area Percentage (Bottom)
            Text(
                text = "$areaInfo ($depthStatus)",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

// Helper (Same as before)
fun rotateRect(rect: RectF, rotation: Int, imgW: Float, imgH: Float): RectF {
    val newRect = RectF()
    when (rotation) {
        90 -> {
            newRect.left = imgH - rect.bottom
            newRect.top = rect.left
            newRect.right = imgH - rect.top
            newRect.bottom = rect.right
        }
        270 -> {
            newRect.left = rect.top
            newRect.top = imgW - rect.right
            newRect.right = rect.bottom
            newRect.bottom = imgW - rect.left
        }
        180 -> {
            newRect.left = imgW - rect.right
            newRect.top = imgH - rect.bottom
            newRect.right = imgW - rect.left
            newRect.bottom = imgH - rect.top
        }
        else -> return rect
    }
    return newRect
}