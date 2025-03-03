import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import xyz.block.gosling.R
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.text.TextStyle
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import xyz.block.gosling.Agent
import xyz.block.gosling.ui.theme.LocalGoslingColors


@Composable
fun GoslingUI(
    context: Context,
    modifier: Modifier = Modifier,
    startVoice: Boolean = false
) {
    var isVoiceMode by remember { mutableStateOf(startVoice) }
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isOutputMode by remember { mutableStateOf(false) }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val scope = rememberCoroutineScope()

    fun requestAudioPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    fun executeCommand(input: String) {
        scope.launch {
            isOutputMode = true
            outputText = "Thinking..."
            val response = async {
                Agent.processCommand(input, context) { status ->
                    outputText = status
                    Log.d("Agent", "Status update: $status")
                }
            }.await()
            outputText += "\n" + response
            inputText = ""

            kotlinx.coroutines.delay(500)
            isOutputMode = false
        }
    }

    LaunchedEffect(isVoiceMode) {
        if (isVoiceMode) {
            val activity = context as? Activity ?: return@LaunchedEffect
            requestAudioPermission(activity) { granted ->
                if (!granted) {
                    isVoiceMode = false
                    return@requestAudioPermission
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val voiceCommand =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                        if (!voiceCommand.isNullOrEmpty()) {
                            inputText = voiceCommand
                            executeCommand(voiceCommand)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                        if (!partial.isNullOrEmpty()) {
                            inputText = partial
                        }
                    }

                    override fun onError(error: Int) {
                        inputText = "Error: $error"
                        isVoiceMode = false
                    }

                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
                speechRecognizer = recognizer
            }
        } else {
            speechRecognizer?.destroy()
        }
    }

    val goslingColors = LocalGoslingColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(goslingColors.primaryBackground)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isVoiceMode = !isVoiceMode },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = goslingColors.secondaryButton,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isVoiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                            contentDescription = if (isVoiceMode) "Listen Mode" else "Type Mode",
                            tint = goslingColors.primaryText
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.gosling),
                        contentDescription = "Gosling Icon",
                        modifier = Modifier.size(64.dp)
                    )
                }

                if (isOutputMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(goslingColors.inputBackground)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = outputText,
                            color = goslingColors.primaryText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    if (isVoiceMode) {
                        Text(
                            inputText.ifEmpty { "Listening..." },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = goslingColors.primaryText
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(goslingColors.inputBackground)
                                .padding(12.dp)
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.Black)
                            )
                        }

                        Button(
                            onClick = { executeCommand(inputText) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = goslingColors.secondaryButton),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Submit", color = goslingColors.primaryText)
                        }
                    }
                }
            }
        }
    }
}

