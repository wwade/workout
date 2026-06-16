package dev.wwade.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.wwade.workout.ui.navigation.WorkoutNavHost
import dev.wwade.workout.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme {
                WorkoutApp()
            }
        }
    }
}

@Composable
private fun WorkoutApp() {
    val application = LocalContext.current.applicationContext as WorkoutApplication
    WorkoutNavHost(container = application.container)
}
