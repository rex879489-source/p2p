package com.example.p2pchat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var client: WebRtcClient

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored; mic simply won't work if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Screen { HOME, CREATE_OFFER, JOIN_WITH_OFFER, CHAT }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val username = remember { Identity.getOrCreateUsername(context) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var connectionState by remember { mutableStateOf("NEW") }
    val messages = remember { mutableStateListOf<String>() }
    var micOn by remember { mutableStateOf(true) }

    var localCode by remember { mutableStateOf("") }   // SDP text to send to the other person
    var pastedCode by remember { mutableStateOf("") }   // SDP text pasted from the other person

    val client = remember {
        WebRtcClient(
            context = context,
            onMessage = { msg -> messages.add("Them: $msg") },
            onStateChange = { state ->
                connectionState = state
                if (state == "CONNECTED") screen = Screen.CHAT
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("P2P Chat", style = MaterialTheme.typography.headlineMedium)
        Text("You are: $username", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text("Status: $connectionState", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))

        when (screen) {
            Screen.HOME -> HomeScreen(
                onStartConnection = {
                    client.createOffer { sdp ->
                        localCode = encode(username, sdp)
                    }
                    screen = Screen.CREATE_OFFER
                },
                onJoinConnection = { screen = Screen.JOIN_WITH_OFFER }
            )

            Screen.CREATE_OFFER -> CreateOfferScreen(
                code = localCode,
                pastedAnswer = pastedCode,
                onPastedAnswerChange = { pastedCode = it },
                onSubmitAnswer = {
                    val (_, sdp) = decode(pastedCode)
                    client.setRemoteAnswer(sdp)
                },
                onBack = { screen = Screen.HOME }
            )

            Screen.JOIN_WITH_OFFER -> JoinScreen(
                pastedOffer = pastedCode,
                onPastedOfferChange = { pastedCode = it },
                answerCode = localCode,
                onCreateAnswer = {
                    val (_, sdp) = decode(pastedCode)
                    client.createAnswer(sdp) { answerSdp ->
                        localCode = encode(username, answerSdp)
                    }
                },
                onBack = { screen = Screen.HOME }
            )

            Screen.CHAT -> ChatScreen(
                messages = messages,
                micOn = micOn,
                onToggleMic = {
                    micOn = !micOn
                    client.setMicEnabled(micOn)
                },
                onSend = { text ->
                    client.sendMessage(text)
                    messages.add("You: $text")
                }
            )
        }
    }
}

@Composable
fun HomeScreen(onStartConnection: () -> Unit, onJoinConnection: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("No accounts. No servers. Connect directly with one other person.")
        Button(onClick = onStartConnection, modifier = Modifier.fillMaxWidth()) {
            Text("Start a new connection (I'll create a code)")
        }
        Button(onClick = onJoinConnection, modifier = Modifier.fillMaxWidth()) {
            Text("Join with a code someone sent me")
        }
    }
}

@Composable
fun CreateOfferScreen(
    code: String,
    pastedAnswer: String,
    onPastedAnswerChange: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Step 1: Send this code to your friend (any app: WhatsApp, SMS, etc.)")
        OutlinedTextField(
            value = code,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Button(onClick = { copyToClipboard(context, code) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (code.isBlank()) "Generating code..." else "Copy code")
        }

        Spacer(Modifier.height(8.dp))
        Text("Step 2: Paste the reply code your friend sends back")
        OutlinedTextField(
            value = pastedAnswer,
            onValueChange = onPastedAnswerChange,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Button(onClick = onSubmitAnswer, modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun JoinScreen(
    pastedOffer: String,
    onPastedOfferChange: (String) -> Unit,
    answerCode: String,
    onCreateAnswer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Step 1: Paste the code your friend sent you")
        OutlinedTextField(
            value = pastedOffer,
            onValueChange = onPastedOfferChange,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Button(onClick = onCreateAnswer, modifier = Modifier.fillMaxWidth()) {
            Text("Generate reply code")
        }

        if (answerCode.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Step 2: Send this reply code back to your friend")
            OutlinedTextField(
                value = answerCode,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Button(onClick = { copyToClipboard(context, answerCode) }, modifier = Modifier.fillMaxWidth()) {
                Text("Copy reply code")
            }
            Text("Once they enter it, you'll connect automatically.")
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun ChatScreen(
    messages: List<String>,
    micOn: Boolean,
    onToggleMic: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Connected", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onToggleMic) {
                Text(if (micOn) "Mute mic" else "Unmute mic")
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                Text(msg, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                placeholder = { Text("Message") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank()) {
                    onSend(input)
                    input = ""
                }
            }) { Text("Send") }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("p2pchat-code", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

// Simple envelope so the pasted blob carries the sender's username + raw SDP.
// Format: "USERNAME||<sdp text>"
private fun encode(username: String, sdp: String): String = "$username||$sdp"

private fun decode(blob: String): Pair<String, String> {
    val parts = blob.split("||", limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else "" to blob
}
