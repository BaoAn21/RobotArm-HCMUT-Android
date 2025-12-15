#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <vector>

#define TAG "NativeYellow"
// Macro for easy C++ logging to Android Logcat
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jfloatArray JNICALL
// MAKE SURE THIS NAME MATCHES YOUR PACKAGE!
Java_com_example_robotarm_common_CameraColor_detectYellow(JNIEnv *env, jobject thiz, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels;

    // 1. Lock Bitmap
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    cv::Mat img(info.height, info.width, CV_8UC4, pixels);
    cv::Mat hsv, mask;

    // 2. Convert RGBA -> HSV
    cv::cvtColor(img, hsv, cv::COLOR_RGBA2RGB);
    cv::cvtColor(hsv, hsv, cv::COLOR_RGB2HSV);

    // 3. Threshold for YELLOW (Tune these numbers if needed!)
    // Hue is 0-180. Yellow is ~20-35.
    cv::Scalar lower(20, 100, 100);
    cv::Scalar upper(35, 255, 255);
    cv::inRange(hsv, lower, upper, mask);

    // 4. Unlock Bitmap early
    AndroidBitmap_unlockPixels(env, bitmap);

    // 5. Find Contours (Blobs)
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(mask, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    // Result array: [found(0/1), left, top, right, bottom]
    float result[5] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    // Find largest blob
    double maxArea = 0;
    int maxAreaIdx = -1;
    for(int i = 0; i < contours.size(); i++) {
        double area = cv::contourArea(contours[i]);
        if (area > maxArea) {
            maxArea = area;
            maxAreaIdx = i;
        }
    }

    // Filter noise (area must be > 500 pixels)
    if (maxArea > 500 && maxAreaIdx != -1) {
        // Get bounding rect of largest blob
        cv::Rect rect = cv::boundingRect(contours[maxAreaIdx]);
        result[0] = 1.0f; // Found!
        result[1] = (float)rect.x;          // Left
        result[2] = (float)rect.y;          // Top
        result[3] = (float)(rect.x + rect.width);  // Right
        result[4] = (float)(rect.y + rect.height); // Bottom
        // LOGD("Found yellow rect: L:%.0f T:%.0f R:%.0f B:%.0f", result[1], result[2], result[3], result[4]);
    }

    // 6. Return 5 floats to Kotlin
    jfloatArray outArray = env->NewFloatArray(5);
    env->SetFloatArrayRegion(outArray, 0, 5, result);
    return outArray;
}