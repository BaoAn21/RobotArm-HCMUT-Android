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

    // Start USB Server
    LaunchedEffect(Unit) { UsbServer.start() }
    DisposableEffect(Unit) { onDispose { UsbServer.stop() } }

    // --- 2. STATES ---
    var isMirrored by remember { mutableStateOf(false) }

    // Tracking Data
    var detectedRect by remember { mutableStateOf<RectF?>(null) }
    var trackingError by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var trackingStatus by remember { mutableStateOf("Scanning...") }

    // Visual Lock State (True only if BOTH are 0)
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

                // A. DETECT (Native C++)
                val rawRect = CameraColor.findYellowRect(bitmap)

                // B. ROTATE
                var finalRect = if (rawRect != null) {
                    rotateRect(rawRect, rotation, bitmap.width.toFloat(), bitmap.height.toFloat())
                } else {
                    null
                }

                // C. MIRROR (If button pressed)
                if (isMirrored && finalRect != null) {
                    val newLeft = sourceWidth - finalRect.right
                    val newRight = sourceWidth - finalRect.left
                    finalRect = RectF(newLeft, finalRect.top, newRight, finalRect.bottom)
                }

                detectedRect = finalRect

                // D. CALCULATE ERROR
                if (finalRect != null) {
                    val centerX = finalRect.centerX()
                    val centerY = finalRect.centerY()

                    // Raw Error (Distance from center)
                    val errX = centerX - (sourceWidth / 2)
                    val errY = centerY - (sourceHeight / 2)

                    trackingError = Pair(errX, errY)

                    // E. FIX: INDEPENDENT AXIS CHECK
                    val threshold = deadZoneSize / 2

                    // 1. Check X independently
                    val outputX = if (abs(errX) < threshold) 0f else errX

                    // 2. Check Y independently
                    val outputY = if (abs(errY) < threshold) 0f else errY

                    // 3. Send Data
                    UsbServer.sendData(outputX, outputY)

                    // 4. Update UI Status
                    if (outputX == 0f && outputY == 0f) {
                        isLocked = true
                        trackingStatus = "LOCKED (Center)"
                    } else {
                        isLocked = false
                        trackingStatus = "Tracking: ${outputX.toInt()}, ${outputY.toInt()}"
                    }

                } else {
                    trackingError = null
                    isLocked = false
                    trackingStatus = "Scanning..."
                    // Optional: Send 0,0 when lost to stop robot?
                    // UsbServer.sendData(0f, 0f)
                }
            }

            // --- 4. VISUAL DEBUG OVERLAY ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / sourceWidth
                val scaleY = size.height / sourceHeight
                val cx = size.width / 2
                val cy = size.height / 2

                // A. Draw Dead Zone (The White Box in Center)
                val scaledDeadZoneW = deadZoneSize * scaleX
                val scaledDeadZoneH = deadZoneSize * scaleY

                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(cx - scaledDeadZoneW/2, cy - scaledDeadZoneH/2),
                    size = Size(scaledDeadZoneW, scaledDeadZoneH),
                    style = Stroke(width = 4f)
                )

                // B. Draw Detected Object Box (Yellow)
                detectedRect?.let { rect ->
                    drawRect(
                        color = Color.Yellow,
                        topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                        size = Size(
                            (rect.right - rect.left) * scaleX,
                            (rect.bottom - rect.top) * scaleY
                        ),
                        style = Stroke(width = 6f)
                    )
                }

                // C. Draw Vector Line (Center -> Object)
                trackingError?.let { (errX, errY) ->
                    val vectorX = errX * scaleX
                    val vectorY = errY * scaleY
                    val targetX = cx + vectorX
                    val targetY = cy + vectorY

                    val lineColor = if (isLocked) Color.Green else Color.Red

                    drawLine(
                        color = lineColor,
                        start = Offset(cx, cy),
                        end = Offset(targetX, targetY),
                        strokeWidth = 5f
                    )
                    drawCircle(lineColor, radius = 10f, center = Offset(targetX, targetY))
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

            // Mirror Toggle Button
            IconButton(
                onClick = { isMirrored = !isMirrored },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 80.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward, // Using Arrow as placeholder for Flip
                    contentDescription = "Mirror",
                    tint = if (isMirrored) Color.Yellow else Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Status Text
            Text(
                text = trackingStatus,
                color = if (isLocked) Color.Green else Color.Yellow,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
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