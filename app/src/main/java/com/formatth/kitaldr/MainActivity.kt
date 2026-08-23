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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private enum class Screen { Welcome, Pairing, Home }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KitaLdrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KitaLdrApp()
                }
            }
        }
    }
}

@Composable
private fun KitaLdrApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var generatedCode by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var partnerName by rememberSaveable { mutableStateOf("My Love") }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(onStart = { screen = Screen.Pairing })
        Screen.Pairing -> PairingScreen(
            generatedCode = generatedCode,
            joinCode = joinCode,
            onJoinCodeChange = { joinCode = it.uppercase() },
            onGenerate = { generatedCode = generatePairCode() },
            onPair = {
                if (joinCode.replace("-", "").length == 8) {
                    partnerName = "My Love"
                    screen = Screen.Home
                }
            },
            onBack = { screen = Screen.Welcome }
        )
        Screen.Home -> HomeScreen(
            partnerName = partnerName,
            onDisconnect = {
                generatedCode = ""
                joinCode = ""
                screen = Screen.Pairing
            }
        )
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
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
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("Connect with your person ❤️")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "MVP build • pairing is currently a UI prototype",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PairingScreen(
    generatedCode: String,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onPair: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Connect", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Testing mode: codes are local for now. Firebase pairing will be added next.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Create a pairing code", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (generatedCode.isBlank()) "Generate a temporary code" else generatedCode,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGenerate) { Text("Generate code") }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Or enter your partner's code", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = joinCode,
            onValueChange = { onJoinCodeChange(it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(9)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("ABCD-1234") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPair,
            enabled = joinCode.replace("-", "").length == 8,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) { Text("Pair this device ❤️") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun HomeScreen(partnerName: String, onDisconnect: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("KitaLDR", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Hi, Domath ❤️", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Connected with $partnerName", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("❤️ $partnerName", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("🟢 Connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Remote actions are UI-only in this first build.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect partner")
        }
    }
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

private fun generatePairCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val raw = buildString { repeat(8) { append(alphabet[Random.nextInt(alphabet.length)]) } }
    return "${raw.substring(0, 4)}-${raw.substring(4)}"
}

@Composable
private fun KitaLdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
