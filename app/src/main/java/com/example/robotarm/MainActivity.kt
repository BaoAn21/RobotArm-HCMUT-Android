package com.example.robotarm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotarm.ui.theme.RobotArmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RobotArmTheme {
                // Navigation State
                // Options: "menu", "face_tracking", "object_detection"
                var currentScreen by remember { mutableStateOf("menu") }

                when (currentScreen) {
                    "menu" -> RobotArmMenu(
                        onFaceTrackingClick = { currentScreen = "face_tracking" },
                        onObjectDetectionClick = { currentScreen = "object_detection" }, // <--- THE NEW LINK
                        onColorTrackingClick = { currentScreen = "color_tracking" }
                    )

                    // Route 1: The Face Tracker (ML Kit)
                    "face_tracking" -> ObjectTrackingScreen(
                        onBack = { currentScreen = "menu" }
                    )

                    // Route 2: The Object Detector (TFLite)
                    "object_detection" -> ObjectDetectorScreen(
                        onBack = { currentScreen = "menu" }
                    )
                    "color_tracking" -> ColorTrackingScreen(
                        onBack = { currentScreen = "menu" }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotArmMenu(
    onFaceTrackingClick: () -> Unit,
    onObjectDetectionClick: () -> Unit, // <--- Add this parameter
    onColorTrackingClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Robot Arm Controller") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Select Mode",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // BUTTON 1: FACE TRACKING (ML Kit)
            MenuButton(
                text = "Face Tracking (ML Kit)",
                onClick = onFaceTrackingClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // BUTTON 2: OBJECT DETECTION (TFLite) -- NEW!
            MenuButton(
                text = "Object Detection (Ball/Bottle)",
                onClick = onObjectDetectionClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuButton(
                text = "Color Tracking",
                onClick = {
                    onColorTrackingClick()
                }
            )
        }
    }
}

// Reusable Button (Unchanged)
@Composable
fun MenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        elevation = ButtonDefaults.buttonElevation(8.dp)
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}