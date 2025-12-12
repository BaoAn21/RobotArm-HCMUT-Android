package com.example.robotarm.common

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Universal Robot Camera Component.
 * * Features:
 * - Auto-Permissions handling.
 * - Auto-Hardware management (Legacy/Modern support).
 * - "Zero Math" Aspect Ratio reporting.
 * - Reactive Resolution switching.
 * - Built-in Camera Switching (Selfie/Back).
 */
@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RobotCamera(
    modifier: Modifier = Modifier,
    // Default to QVGA (320x240) for max FPS on old phones.
    // Increase to 640x480 if you need more detail.
    targetWidth: Int = 320,
    targetHeight: Int = 240,
    // THE UNIVERSAL CALLBACK:
    // Returns: The Image (Bitmap), Rotation (Int), Mirror Flag (Boolean), and Aspect Ratio (Float)
    onFrame: (Bitmap, Int, Boolean, Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    // 1. Permission Check
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Box(modifier = modifier.fillMaxSize()) {

            // 2. Create the View (The "TV Screen")
            val previewView = remember {
                PreviewView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // CRITICAL: Matches the UI shape to the Video shape to prevent cropping/drift.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            }

            // 3. Connect the Hardware (The "Electrician")
            // Re-runs whenever you change lens or resolution target.
            LaunchedEffect(lensFacing, targetWidth, targetHeight) {
                val cameraProvider = context.getCameraProvider()

                // Clean slate
                cameraProvider.unbindAll()

                // A. Resolution Strategy (The "Safety Net")
                // Asks for 320x240. If hardware fails, asks for next closest thing.
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(targetWidth, targetHeight),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                // B. Preview Pipe (What you see)
                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // C. Analysis Pipe (What the AI sees)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    // Drop frames if AI is too slow (Prevents Lag)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // NOTE: Removed setOutputImageFormat to fix Soyes XS16 Crash
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    // Convert YUV to Bitmap safely on CPU
                    val bitmap = imageProxy.toBitmap()
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT)

                    // Calculate Ratio for "Zero Math" UI
                    val ratio = if (rotation == 90 || rotation == 270) {
                        imageProxy.height.toFloat() / imageProxy.width.toFloat()
                    } else {
                        imageProxy.width.toFloat() / imageProxy.height.toFloat()
                    }

                    // Send data to your AI
                    onFrame(bitmap, rotation, isFront, ratio)

                    // Close frame so next one can arrive
                    imageProxy.close()
                }

                // D. Bind everything to LifeCycle
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("RobotCamera", "Binding failed", e)
                }
            }

            // 4. Render the View
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 5. Switch Button Overlay
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // Fallback UI
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Camera Permission Required", color = MaterialTheme.colorScheme.error)
        }
    }
}

// Suspend wrapper for CameraProvider
private suspend fun android.content.Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            continuation.resume(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }