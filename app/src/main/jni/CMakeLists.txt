cmake_minimum_required(VERSION 3.4.1)

project("robot_arm_native")

# 1. Setup OpenCV Mobile
# Make sure this path matches EXACTLY where you extracted the folder
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-2.4.13.7-android/sdk/native/jni)
find_package(OpenCV REQUIRED)

# 2. Define YOUR library (The C++ file)
add_library(
        robot_arm_native
        SHARED
        native-lib.cpp) # We will create this file next

# 3. Link libraries
target_link_libraries(
        robot_arm_native
        ${OpenCV_LIBS}   # Link OpenCV
        jnigraphics      # Link Android Bitmap support
        log)             # Link Logging