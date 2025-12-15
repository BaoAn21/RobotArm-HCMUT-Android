package com.example.robotarm.common

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log

object CameraColor {
    // Load the C++ library listed in CMakeLists.txt
    init {
        try {
            System.loadLibrary("robot_arm_native")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("CameraColor", "Could not load native library!", e)
        }
    }

    /**
     * Native C++ function.
     * Returns FloatArray size 5: [found(0.0/1.0), left, top, right, bottom]
     */
    private external fun detectYellow(bitmap: Bitmap): FloatArray

    /**
     * Helper function used by UI.
     * Returns a RectF if yellow is found, or null if not.
     */
    fun findYellowRect(bitmap: Bitmap): RectF? {
        return try {
            // Call C++
            val result = detectYellow(bitmap)

            // Check flag (result[0])
            if (result[0] > 0.5f) {
                // Convert remaining 4 floats to RectF
                RectF(
                    result[1], // left
                    result[2], // top
                    result[3], // right
                    result[4]  // bottom
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("CameraColor", "Native call failed", e)
            null
        }
    }
}