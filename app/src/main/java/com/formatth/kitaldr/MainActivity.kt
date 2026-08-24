package com.formatth.kitaldr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formatth.kitaldr.data.KitaLdrRepository
import com.formatth.kitaldr.data.PairInfo
import kotlinx.coroutines.delay

private enum class Screen { Welcome, PairChoice, CreatePair, JoinPair, Home }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = KitaLdrRepository(this)
        setContent {
            KitaLdrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KitaLdrApp(repository)
                }
            }
        }
    }
}

@Composable
private fun KitaLdrApp(repository: KitaLdrRepository) {
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var firebaseReady by rememberSaveable { mutableStateOf(repository.isConfigured) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var generatedCode by rememberSaveable { mutableStateOf("") }
    var generatedAt by rememberSaveable { mutableStateOf(0L) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var pairInfo by remember { mutableStateOf<PairInfo?>(null) }

    fun showError(error: Throwable) {
        busy = false
        message = error.message ?: "Something went wrong."
    }

    fun openAfterSignIn() {
        repository.loadCurrentPair { pairResult ->
            pairResult.onSuccess { pair ->
                pairInfo = pair
                if (pair != null && pair.status == "active") {
                    screen = Screen.Home
                } else {
                    screen = Screen.PairChoice
                }
                busy = false
            }.onFailure(::showError)
        }
    }

    LaunchedEffect(Unit) {
        if (!firebaseReady) return@LaunchedEffect
        if (repository.currentUid() != null) {
            openAfterSignIn()
            return@LaunchedEffect
        }

        busy = true
        repository.signIn { result ->
            result.onSuccess { openAfterSignIn() }
                .onFailure(::showError)
        }
    }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(firebaseReady, busy, message) {
            if (!repository.isConfigured) {
                firebaseReady = false
                message = "Firebase belum terhubung."
            } else {
                busy = true
                message = ""
                repository.signIn { result ->
                    result.onSuccess { openAfterSignIn() }
                        .onFailure(::showError)
                }
            }
        }

        Screen.PairChoice -> PairChoiceScreen(
            busy = busy,
            onCreate = { message = ""; screen = Screen.CreatePair },
            onJoin = { message = ""; screen = Screen.JoinPair },
            onBack = { screen = Screen.Welcome }
        )

        Screen.CreatePair -> CreatePairScreen(
            busy = busy,
            message = message,
            generatedCode = generatedCode,
            generatedAt = generatedAt,
            onGenerate = {
                busy = true
                message = ""
                repository.createPairingCode { result ->
                    result.onSuccess { code ->
                        generatedCode = code
                        generatedAt = System.currentTimeMillis()
                        busy = false
                    }.onFailure(::showError)
                }
            },
            onBack = {
                generatedCode = ""
                generatedAt = 0L
                message = ""
                screen = Screen.PairChoice
            }
        )

        Screen.JoinPair -> JoinPairScreen(
            busy = busy,
            message = message,
            joinCode = joinCode,
            onJoinCodeChange = {
                joinCode = KitaLdrRepository.normalizeCode(it).take(9)
                message = ""
            },
            onPair = {
                busy = true
                message = ""
                repository.joinPairingCode(joinCode) { result ->
                    result.onSuccess {
                        repository.loadCurrentPair { pairResult ->
                            pairResult.onSuccess { pair ->
                                pairInfo = pair
                                busy = false
                                if (pair != null && pair.status == "active") screen = Screen.Home
                            }.onFailure(::showError)
                        }
                    }.onFailure(::showError)
                }
            },
            onBack = {
                joinCode = ""
                message = ""
                screen = Screen.PairChoice
            }
        )

        Screen.Home -> HomeScreen(
            pairInfo = pairInfo,
            busy = busy,
            onDisconnect = {
                busy = true
                message = ""
                repository.disconnect { result ->
                    result.onSuccess {
                        busy = false
                        pairInfo = null
                        generatedCode = ""
                        generatedAt = 0L
                        joinCode = ""
                        screen = Screen.PairChoice
                    }.onFailure(::showError)
                }
            }
        )
    }

    if (generatedCode.isNotBlank()) {
        DisposableEffect(generatedCode) {
            val registration = repository.listenForPairingAcceptance(generatedCode) { coupleId ->
                repository.loadCurrentPair { result ->
                    result.onSuccess { pair ->
                        pairInfo = pair
                        if (pair?.coupleId == coupleId) {
                            message = "Connected! ❤️"
                            screen = Screen.Home
                        }
                    }
                }
            }
            onDispose { registration?.remove() }
        }
    }
}

@Composable
private fun WelcomeScreen(firebaseReady: Boolean, busy: Boolean, message: String, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("❤️", fontSize = 68.sp)
        Spacer(Modifier.height(18.dp))
        Text("KitaLDR", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("A tiny private space for two people who are far apart.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        StatusPill(if (firebaseReady) "🟢 Firebase connected" else "🟡 Firebase setup needed")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp)
            else Text("Get started ❤️", fontWeight = FontWeight.Bold)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Private pairing • one active partner", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PairChoiceScreen(busy: Boolean, onCreate: () -> Unit, onJoin: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Connect", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Choose how you want to connect with your person.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        ChoiceCard("✨", "Create a pair", "Generate a code and wait for your person.", onCreate, !busy)
        Spacer(Modifier.height(16.dp))
        ChoiceCard("🔗", "Join a pair", "Enter the pairing code your person shared.", onJoin, !busy)
        Spacer(Modifier.weight(1f))
        Text("One person creates the code. The other person joins it.\nNo second code is needed.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun ChoiceCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 28.sp) }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CreatePairScreen(busy: Boolean, message: String, generatedCode: String, generatedAt: Long, onGenerate: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var secondsLeft by rememberSaveable(generatedCode) { mutableIntStateOf(600) }

    LaunchedEffect(generatedCode, generatedAt) {
        if (generatedCode.isBlank() || generatedAt == 0L) return@LaunchedEffect
        while (true) {
            val elapsed = ((System.currentTimeMillis() - generatedAt) / 1000L).toInt()
            val remaining = (600 - elapsed).coerceAtLeast(0)
            secondsLeft = remaining
            if (remaining == 0) break
            delay(1000)
        }
    }

    val expired = generatedCode.isNotBlank() && secondsLeft == 0
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Create a pair", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Create one code. Your person only needs to join it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOUR CODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(14.dp))
                Text(if (generatedCode.isBlank()) "---- ----" else generatedCode, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                Text(when {
                    generatedCode.isBlank() -> "Generate a code to start waiting."
                    expired -> "This code has expired. Generate a new one."
                    else -> "Waiting for your person…"
                }, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (generatedCode.isNotBlank() && !expired) {
                    Spacer(Modifier.height(8.dp))
                    Text("Expires in %02d:%02d".format(minutes, seconds), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { copyToClipboard(context, generatedCode) }, enabled = generatedCode.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Copy") }
                    Button(onClick = { shareCode(context, generatedCode) }, enabled = generatedCode.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Share") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGenerate, enabled = !busy && (generatedCode.isBlank() || expired), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (generatedCode.isBlank()) "Generate pairing code" else "Generate new code")
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun JoinPairScreen(busy: Boolean, message: String, joinCode: String, onJoinCodeChange: (String) -> Unit, onPair: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Join a pair", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Enter the code created by your person.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Text("Partner's pairing code", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = joinCode, onValueChange = onJoinCodeChange, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, placeholder = { Text("ABCD-2345") })
                Spacer(Modifier.height(14.dp))
                Button(onClick = onPair, enabled = !busy && joinCode.length == 9, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Join this pair ❤️", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("The code is short-lived and can only be used once.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun HomeScreen(pairInfo: PairInfo?, busy: Boolean, onDisconnect: () -> Unit) {
    val partnerName = pairInfo?.partnerName ?: "My Love"
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("KitaLDR", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("You two are connected ❤️", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Connected with $partnerName", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("❤️ $partnerName", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StatusPill("🟢 Pair active")
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("📳", "Poke", Modifier.weight(1f))
            ActionButton("🥺", "Miss You", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("😴", "Wake Up", Modifier.weight(1f))
            ActionButton("🍚", "Eat", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("Pairing is live on Firebase. Remote actions will be added next.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Disconnect partner") }
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActionButton(icon: String, label: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun copyToClipboard(context: Context, code: String) {
    if (code.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KitaLDR pairing code", code))
}

private fun shareCode(context: Context, code: String) {
    if (code.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join me on KitaLDR ❤️\nPairing code: $code")
    }
    context.startActivity(Intent.createChooser(intent, "Share pairing code"))
}

@Composable
private fun KitaLdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
