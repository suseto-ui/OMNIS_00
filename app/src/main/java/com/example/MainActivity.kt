package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.OmnisMainScreen
import com.example.ui.OmnisViewModel
import com.example.ui.PdfExportEngine
import com.example.ui.SpeechController
import com.example.ui.theme.OmnisTheme

class MainActivity : ComponentActivity() {
    private lateinit var speechController: SpeechController
    private val incomingIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingIntentState.value = intent

        speechController = SpeechController(this)
        speechController.initialize()
        com.example.ui.chat.ChatDisplayPreferences.init(this)
        com.example.api.OmnisGeminiClient.init(this)
        com.example.data.SyncLogManager.init(this)
        com.example.data.OmnisBatchSyncScheduler.scheduleNetworkRecoverySync(this)

        setContent {
            OmnisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: OmnisViewModel = viewModel()
                    val currentIntent = incomingIntentState.value

                    LaunchedEffect(currentIntent) {
                        viewModel.handleIncomingShareIntent(currentIntent)
                    }

                    OmnisMainScreen(
                        viewModel = viewModel,
                        onSpeak = { text ->
                            speechController.speak(text)
                        },
                        onExportPdf = { record ->
                            PdfExportEngine.exportToPdf(this, record, packageName)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentState.value = intent
    }

    override fun onDestroy() {
        super.onDestroy()
        speechController.shutdown()
    }
}
