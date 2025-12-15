package com.example.robotarm

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.robotarm.common.RobotCamera
import com.example.robotarm.common.UsbServer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

@Composable
fun ObjectTrackingScreen(onBack: () -> Unit) {

    LaunchedEffect(Unit) {
        UsbServer.start()
    }

    // 2. Cleanup when screen closes
    DisposableEffect(Unit) {
        onDispose { UsbServer.stop() }
    }

    // --- CONFIGURATION ---
    val deadZoneSize = 60f // In Camera Pixels

    // 1. Setup ML Kit
    val options = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .enableTracking()
            .build()
    }
    val detector = remember { FaceDetection.getClient(options) }

    // 2. UI State
    var faceRect by remember { mutableStateOf<Rect?>(null) }
    var trackingError by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var trackingStatus by remember { mutableStateOf("Scanning...") }

    // Layout State
    var cameraRatio by remember { mutableFloatStateOf(0.75f) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var sourceWidth by remember { mutableStateOf(480f) }
    var sourceHeight by remember { mutableStateOf(640f) }

    // isLocked is true ONLY if BOTH are 0 (Visual feedback)
    var isLocked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Aspect Ratio Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cameraRatio)
        ) {
            // --- CAMERA LAYER ---
            RobotCamera(
                targetWidth = 320,
                targetHeight = 240
            ) { bitmap, rotation, isFront, ratio ->
                cameraRatio = ratio
                isFrontCamera = isFront

                val (camW, camH) = if (rotation == 90 || rotation == 270) {
                    Pair(bitmap.height.toFloat(), bitmap.width.toFloat())
                } else {
                    Pair(bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                sourceWidth = camW
                sourceHeight = camH

                val image = InputImage.fromBitmap(bitmap, rotation)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            faceRect = face.boundingBox

                            // --- ROBOT LOGIC ---
                            val camCenterX = camW / 2
                            val camCenterY = camH / 2
                            val faceCenterX = face.boundingBox.centerX().toFloat()
                            val faceCenterY = face.boundingBox.centerY().toFloat()

                            var errX = faceCenterX - camCenterX
                            val errY = faceCenterY - camCenterY

                            // If mirrored (Front Cam), flip X
                            if (isFront) errX = -errX

                            trackingError = Pair(errX, errY)

                            // --- FIX: INDEPENDENT AXIS CHECK ---
                            val threshold = deadZoneSize / 2

                            // 1. Check X independently
                            val outputX = if (abs(errX) < threshold) 0f else errX

                            // 2. Check Y independently
                            val outputY = if (abs(errY) < threshold) 0f else errY

                            // 3. Send the calculated values
                            UsbServer.sendData(outputX, outputY)

                            // 4. Update UI Status
                            // We consider it "LOCKED" (Green) only if both are sending 0
                            if (outputX == 0f && outputY == 0f) {
                                isLocked = true
                                trackingStatus = "LOCKED"
                            } else {
                                isLocked = false
                                trackingStatus = "Tracking: X:${outputX.toInt()} Y:${outputY.toInt()}"
                            }

                        } else {
                            faceRect = null
                            trackingError = null
                            trackingStatus = "Scanning..."
                        }
                    }
            }

            // --- VISUAL DEBUG LAYER ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.width / sourceWidth
                val cx = size.width / 2
                val cy = size.height / 2

                val scaledDeadZone = deadZoneSize * scale
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(cx - scaledDeadZone / 2, cy - scaledDeadZone / 2),
                    size = Size(scaledDeadZone, scaledDeadZone),
                    style = Stroke(width = 4f)
                )

                drawCircle(Color.White, radius = 8f, center = Offset(cx, cy))

                trackingError?.let { (errX, errY) ->
                    val vectorX = errX * scale
                    val vectorY = errY * scale
                    val faceX = cx + vectorX
                    val faceY = cy + vectorY

                    val lineColor = if (isLocked) Color.Green else Color.Red

                    drawLine(
                        color = lineColor,
                        start = Offset(cx, cy),
                        end = Offset(faceX, faceY),
                        strokeWidth = 6f
                    )
                    drawCircle(lineColor, radius = 12f, center = Offset(faceX, faceY))
                }
            }
        }

        // --- OVERLAY CONTROLS ---
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = trackingStatus,
                color = if (isLocked) Color.Green else Color.Yellow,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }
    }
}