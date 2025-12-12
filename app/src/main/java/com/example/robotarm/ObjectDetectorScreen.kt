package com.example.robotarm

import android.graphics.RectF
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.robotarm.common.RobotCamera
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import kotlin.math.abs

private const val TAG = "ObjectDetectorScreen"

@Composable
fun ObjectDetectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val deadZoneSize = 60f

    // --- 1. SETUP TFLITE DETECTOR ---
    val detector = remember {
        val baseOptions = BaseOptions.builder()
            .setNumThreads(2) // Keep at 2 for Soyes XS16 stability
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(5)       // See top 5 results
            .setScoreThreshold(0.3f) // Lower threshold to 30% for easier detection
            .build()

        try {
            ObjectDetector.createFromFileAndOptions(
                context,
                "mobilenet.tflite", // Make sure this matches your assets file exactly
                options
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            null
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose { detector?.close() }
    }

    // --- 2. STATES ---
    var detectedRect by remember { mutableStateOf<RectF?>(null) }
    var detectedLabel by remember { mutableStateOf("Scanning...") }
    var trackingError by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // UI Layout State
    var cameraRatio by remember { mutableFloatStateOf(0.75f) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var sourceWidth by remember { mutableStateOf(320f) }
    var sourceHeight by remember { mutableStateOf(240f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Zero-Math Aspect Ratio Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cameraRatio)
        ) {
            // --- CAMERA ---
            RobotCamera(
                targetWidth = 320,
                targetHeight = 240
            ) { bitmap, rotation, isFront, ratio ->
                cameraRatio = ratio
                isFrontCamera = isFront

                // Calculate "Upright" dimensions for the UI
                val (camW, camH) = if (rotation == 90 || rotation == 270) {
                    Pair(bitmap.height.toFloat(), bitmap.width.toFloat())
                } else {
                    Pair(bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                sourceWidth = camW
                sourceHeight = camH

                if (detector == null) return@RobotCamera

                // --- TFLITE LOGIC ---
                // 1. Prepare Image
                val imageProcessor = ImageProcessor.Builder().build()
                val tensorImage = TensorImage.fromBitmap(bitmap)
                val processedImage = imageProcessor.process(tensorImage)

                // 2. Prepare Rotation Options (THE FIX)
                // This tells TFLite to rotate the image internally before detecting
                val imageProcessingOptions = ImageProcessingOptions.builder()
                    .setOrientation(getTfliteOrientation(rotation))
                    .build()

                // 3. Run Inference with Rotation
                val results = detector.detect(processedImage, imageProcessingOptions)

                // 4. Debug Logs
                if (results.isNotEmpty()) {
                    for (obj in results) {
                        Log.i(TAG, "AI Saw: '${obj.categories[0].label}' (${obj.categories[0].score})")
                    }
                }

                // 5. Process Result (Filter for specifics)
                // Use this to filter. If nothing matches, take the first high-confidence item?
                var targetObject = results.find {
                    val label = it.categories[0].label
                    label == "sports ball" || label == "bottle" || label == "cup"
                }

                // FALLBACK: If we didn't find specific items, just grab the best object for testing
                if (targetObject == null) {
                    targetObject = results.firstOrNull()
                }

                if (targetObject != null) {
                    // Since we used ImageProcessingOptions, this box is already "Upright"!
                    val box = targetObject.boundingBox
                    detectedRect = box
                    detectedLabel = targetObject.categories[0].label

                    // Calculate Error from Center
                    val centerX = box.centerX()
                    val centerY = box.centerY()

                    // Simple Center Math (No rotation needed here anymore)
                    var errX = centerX - (camW / 2)
                    val errY = centerY - (camH / 2)

                    if (isFront) errX = -errX
                    trackingError = Pair(errX, errY)

                } else {
                    detectedRect = null
                    trackingError = null
                    detectedLabel = "Scanning..."
                }
            }

            // --- DRAWING ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.width / sourceWidth
                val cx = size.width / 2
                val cy = size.height / 2

                // Draw Dead Zone
                val scaledDeadZone = deadZoneSize * scale
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(cx - scaledDeadZone / 2, cy - scaledDeadZone / 2),
                    size = Size(scaledDeadZone, scaledDeadZone),
                    style = Stroke(width = 4f)
                )

                // Draw Bounding Box
                detectedRect?.let { rect ->
                    // Just simple scaling now, because TFLite handled the rotation!
                    var left = rect.left * scale
                    var top = rect.top * scale
                    var right = rect.right * scale
                    var bottom = rect.bottom * scale

                    if (isFrontCamera) {
                        val newLeft = size.width - right
                        val newRight = size.width - left
                        left = newLeft
                        right = newRight
                    }

                    drawRect(
                        color = Color.Cyan,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 6f)
                    )
                }

                // Draw Vector Line
                trackingError?.let { (errX, errY) ->
                    val vectorX = errX * scale
                    val vectorY = errY * scale
                    val targetX = cx + vectorX
                    val targetY = cy + vectorY

                    val isLocked = abs(errX) < deadZoneSize/2 && abs(errY) < deadZoneSize/2
                    val color = if(isLocked) Color.Green else Color.Red

                    drawLine(
                        color = color,
                        start = Offset(cx, cy),
                        end = Offset(targetX, targetY),
                        strokeWidth = 6f
                    )
                    drawCircle(color, radius = 10f, center = Offset(targetX, targetY))
                }
            }
        }

        // --- OVERLAY CONTROLS ---
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Target: $detectedLabel",
                color = Color.Yellow,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            )

            Text(
                text = "ERR: ${trackingError?.first?.toInt() ?: 0}, ${trackingError?.second?.toInt() ?: 0}",
                color = Color.Red,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
            )
        }
    }
}

// --- HELPER FUNCTION FOR ROTATION ---
fun getTfliteOrientation(rotation: Int): ImageProcessingOptions.Orientation {
    return when (rotation) {
        0 -> ImageProcessingOptions.Orientation.TOP_LEFT
        90 -> ImageProcessingOptions.Orientation.RIGHT_TOP
        180 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
        270 -> ImageProcessingOptions.Orientation.LEFT_BOTTOM
        else -> ImageProcessingOptions.Orientation.TOP_LEFT
    }
}