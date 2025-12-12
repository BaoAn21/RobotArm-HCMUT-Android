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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

@Composable
fun ObjectTrackingScreen(onBack: () -> Unit) {

    // --- CONFIGURATION ---
    val deadZoneSize = 60f // In Camera Pixels (e.g. 100px error is "close enough")

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

    // RAW ERROR STATE (Use this for Robot Logic!)
    // Format: Pair(ErrorX, ErrorY). 0 means centered.
    var trackingError by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var trackingStatus by remember { mutableStateOf("Scanning...") }

    // Layout State
    var cameraRatio by remember { mutableFloatStateOf(0.75f) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var sourceWidth by remember { mutableStateOf(480f) }
    var sourceHeight by remember { mutableStateOf(640f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Aspect Ratio Box (Zero Math Container)
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

                // 1. Determine "Upright" Camera Dimensions
                val (camW, camH) = if (rotation == 90 || rotation == 270) {
                    Pair(bitmap.height.toFloat(), bitmap.width.toFloat())
                } else {
                    Pair(bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                sourceWidth = camW
                sourceHeight = camH

                // 2. Run Detection
                val image = InputImage.fromBitmap(bitmap, rotation)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            faceRect = face.boundingBox

                            // --- ROBOT LOGIC (RAW CAMERA MATH) ---
                            // Calculate center of the camera frame
                            val camCenterX = camW / 2
                            val camCenterY = camH / 2

                            // Calculate center of the face
                            val faceCenterX = face.boundingBox.centerX().toFloat()
                            val faceCenterY = face.boundingBox.centerY().toFloat()

                            // Calculate Offset (Vector)
                            // Negative = Face is to the LEFT/TOP
                            // Positive = Face is to the RIGHT/BOTTOM
                            var errX = faceCenterX - camCenterX
                            val errY = faceCenterY - camCenterY

                            // If mirrored (Front Cam), flip the X error
                            if (isFront) errX = -errX

                            trackingError = Pair(errX, errY)

                            // Status Text logic
                            val absX = abs(errX)
                            val absY = abs(errY)
                            if (absX < deadZoneSize/2 && absY < deadZoneSize/2) {
                                trackingStatus = "LOCKED (Center)"
                            } else {
                                trackingStatus = "Adjusting: X:${errX.toInt()} Y:${errY.toInt()}"
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

                // 1. Draw The "Dead Zone" (Threshold Box)
                // This is the target center.
                val scaledDeadZone = deadZoneSize * scale
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(cx - scaledDeadZone / 2, cy - scaledDeadZone / 2),
                    size = Size(scaledDeadZone, scaledDeadZone),
                    style = Stroke(width = 4f)
                )

                // Draw Center Dot
                drawCircle(Color.White, radius = 8f, center = Offset(cx, cy))


                // 2. Draw The Tracking Vector
                trackingError?.let { (errX, errY) ->
                    // Convert raw error back to screen coordinates for drawing
                    // Note: We don't need "left/right" rect math here, just simple vectors.
                    val vectorX = errX * scale
                    val vectorY = errY * scale

                    // The "Face Dot" position on screen
                    // If front camera, we already flipped the logic in 'trackingError',
                    // so we add it directly to center.
                    val faceX = cx + vectorX
                    val faceY = cy + vectorY

                    // Decide Color: Green if inside box, Red if outside
                    val isLocked = abs(errX) < deadZoneSize/2 && abs(errY) < deadZoneSize/2
                    val lineColor = if (isLocked) Color.Green else Color.Red

                    // Draw Line from Center to Face
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, cy),
                        end = Offset(faceX, faceY),
                        strokeWidth = 6f
                    )

                    // Draw Face Dot
                    drawCircle(lineColor, radius = 12f, center = Offset(faceX, faceY))
                }
            }
        }

        // --- OVERLAY CONTROLS ---
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = trackingStatus,
                color = if (trackingStatus.startsWith("LOCKED")) Color.Green else Color.Yellow,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
            )
        }
    }
}