package com.formatth.kitaldr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formatth.kitaldr.data.KitaLdrRepository
import com.formatth.kitaldr.data.PairInfo

private enum class Screen { Welcome, Pairing, Home }

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
    var joinCode by rememberSaveable { mutableStateOf("") }
    var pairInfo by remember { mutableStateOf<PairInfo?>(null) }

    fun showError(error: Throwable) {
        busy = false
        message = error.message ?: "Something went wrong."
    }

    if (firebaseReady && repository.currentUid() == null) {
        repository.signIn { result ->
            result.onSuccess {
                busy = false
                repository.loadCurrentPair { pairResult ->
                    pairResult.onSuccess { pair ->
                        pairInfo = pair
                        if (pair != null && pair.status == "active") screen = Screen.Home
                    }.onFailure(::showError)
                }
            }.onFailure { error ->
                busy = false
                message = error.message ?: "Firebase sign-in failed."
            }
        }
        busy = true
    }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(
            firebaseReady = firebaseReady,
            busy = busy,
            message = message,
            onStart = {
                if (!repository.isConfigured) {
                    firebaseReady = false
                    message = "Firebase belum terhubung. Tambahkan google-services.json terlebih dahulu."
                    return@WelcomeScreen
                }
                busy = true
                message = ""
                repository.signIn { result ->
                    result.onSuccess {
                        busy = false
                        screen = Screen.Pairing
                        repository.loadCurrentPair { pairResult ->
                            pairResult.onSuccess { pair ->
                                pairInfo = pair
                                if (pair != null && pair.status == "active") screen = Screen.Home
                            }.onFailure(::showError)
                        }
                    }.onFailure(::showError)
                }
            }
        )

        Screen.Pairing -> PairingScreen(
            firebaseReady = firebaseReady,
            busy = busy,
            message = message,
            generatedCode = generatedCode,
            joinCode = joinCode,
            onJoinCodeChange = {
                joinCode = KitaLdrRepository.normalizeCode(it).take(9)
                message = ""
            },
            onGenerate = {
                busy = true
                message = ""
                repository.createPairingCode { result ->
                    result.onSuccess { code ->
                        busy = false
                        generatedCode = code
                        message = "Kode aktif selama 10 menit."
                    }.onFailure(::showError)
                }
            },
            onPair = {
                busy = true
                message = ""
                repository.joinPairingCode(joinCode) { result ->
                    result.onSuccess {
                        busy = false
                        repository.loadCurrentPair { pairResult ->
                            pairResult.onSuccess { pair ->
                                pairInfo = pair
                                if (pair != null) screen = Screen.Home
                            }.onFailure(::showError)
                        }
                    }.onFailure(::showError)
                }
            },
            onBack = { screen = Screen.Welcome }
        )

        Screen.Home -> HomeScreen(
            pairInfo = pairInfo,
            onDisconnect = {
                busy = true
                repository.disconnect { result ->
                    result.onSuccess {
                        busy = false
                        pairInfo = null
                        generatedCode = ""
                        joinCode = ""
                        message = "Partner disconnected. Kamu bisa pairing lagi dengan kode baru."
                        screen = Screen.Pairing
                    }.onFailure(::showError)
                }
            },
            busy = busy,
        )
    }

    // The creator's device waits for the other device to consume its code.
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
private fun WelcomeScreen(
    firebaseReady: Boolean,
    busy: Boolean,
    message: String,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("❤️", fontSize = 64.sp)
        Spacer(Modifier.height(20.dp))
        Text("KitaLDR", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A tiny private space for two people who are far apart.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        StatusPill(
            text = if (firebaseReady) "🟢 Firebase ready" else "🟡 Firebase setup needed",
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onStart,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Connect with your person ❤️")
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Real Firebase pairing • short-lived codes • one active partner",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PairingScreen(
    firebaseReady: Boolean,
    busy: Boolean,
    message: String,
    generatedCode: String,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onPair: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Connect", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (firebaseReady) "Pair directly with one person. Your code expires after 10 minutes."
            else "Firebase belum siap.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Create a pairing code", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (generatedCode.isBlank()) "---- ----" else generatedCode,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Share this code privately with your partner.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onGenerate, enabled = firebaseReady && !busy) {
                    Text("Generate new code")
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Or enter your partner's code", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = joinCode,
            onValueChange = onJoinCodeChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = firebaseReady && !busy,
            singleLine = true,
            placeholder = { Text("ABCD-2345") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPair,
            enabled = firebaseReady && !busy && joinCode.length == 9,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Pair this device ❤️")
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Back")
        }
    }
}

@Composable
private fun HomeScreen(
    pairInfo: PairInfo?,
    onDisconnect: () -> Unit,
    busy: Boolean,
) {
    val partnerName = pairInfo?.partnerName ?: "My Love"
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("KitaLDR", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("You two are connected ❤️", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Connected with $partnerName", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("❤️ $partnerName", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
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
        Text(
            "Remote actions come next. Pairing is now real Firebase data.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Disconnect partner")
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ActionButton(icon: String, label: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KitaLdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
