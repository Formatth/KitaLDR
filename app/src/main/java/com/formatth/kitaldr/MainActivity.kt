package com.formatth.kitaldr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formatth.kitaldr.data.KitaLdrRepository
import com.formatth.kitaldr.data.PairInfo
import com.formatth.kitaldr.data.PushTokenRegistrar
import com.formatth.kitaldr.data.RemoteAction
import com.formatth.kitaldr.data.RemoteActionService
import kotlinx.coroutines.delay

private enum class Screen { Welcome, NameSetup, PairChoice, CreatePair, JoinPair, Home }

private val Pink = Color(0xFFFF6678)
private val Rose = Color(0xFFFF9AA8)
private val Lavender = Color(0xFF8E7CF4)
private val Ink = Color(0xFF231F2B)

private val KitaLdrColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2E7),
    onPrimaryContainer = Color(0xFF5B1824),
    secondary = Lavender,
    secondaryContainer = Color(0xFFE9E4FF),
    onSecondaryContainer = Color(0xFF2D255E),
    tertiary = Color(0xFF52B59A),
    tertiaryContainer = Color(0xFFDDF5EC),
    background = Color(0xFFFFF9FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF6F0F4),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF746C76),
    outline = Color(0xFFE1D7DE),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = KitaLdrRepository(this)
        val actionService = RemoteActionService(this)
        setContent {
            MaterialTheme(colorScheme = KitaLdrColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KitaLdrApp(repository, actionService)
                }
            }
        }
    }
}

@Composable
private fun KitaLdrApp(repository: KitaLdrRepository, actionService: RemoteActionService) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var firebaseReady by rememberSaveable { mutableStateOf(repository.isConfigured) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var generatedCode by rememberSaveable { mutableStateOf("") }
    var generatedAt by rememberSaveable { mutableStateOf(0L) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var pairInfo by remember { mutableStateOf<PairInfo?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var incomingAction by remember { mutableStateOf<RemoteAction?>(null) }

    fun showError(error: Throwable) {
        busy = false
        actionBusy = false
        message = error.message ?: "Something went wrong."
    }

    fun loadPairAfterName() {
        repository.loadCurrentPair { result ->
            result.onSuccess { pair ->
                pairInfo = pair
                screen = if (pair?.status == "active") Screen.Home else Screen.PairChoice
                busy = false
            }.onFailure(::showError)
        }
    }

    fun openAfterSignIn() {
        PushTokenRegistrar.ensureTokenRegistered()
        repository.loadCurrentDisplayName { result ->
            result.onSuccess { currentName ->
                name = currentName
                if (currentName.isBlank() || currentName.equals("My Love", ignoreCase = true)) {
                    busy = false
                    screen = Screen.NameSetup
                } else {
                    loadPairAfterName()
                }
            }.onFailure(::showError)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        if (!firebaseReady) return@LaunchedEffect
        if (repository.currentUid() != null) {
            openAfterSignIn()
        } else {
            busy = true
            repository.signIn { result -> result.onSuccess { openAfterSignIn() }.onFailure(::showError) }
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
                repository.signIn { result -> result.onSuccess { openAfterSignIn() }.onFailure(::showError) }
            }
        }
        Screen.NameSetup -> NameSetupScreen(name, busy, message, { name = it.take(30); message = "" }) {
            busy = true
            message = ""
            repository.setDisplayName(name) { result -> result.onSuccess { loadPairAfterName() }.onFailure(::showError) }
        }
        Screen.PairChoice -> PairChoiceScreen(busy, { message = ""; screen = Screen.CreatePair }, { message = ""; screen = Screen.JoinPair }) { screen = Screen.Welcome }
        Screen.CreatePair -> CreatePairScreen(busy, message, generatedCode, generatedAt, {
            busy = true
            message = ""
            repository.createPairingCode { result ->
                result.onSuccess { code -> generatedCode = code; generatedAt = System.currentTimeMillis(); busy = false }.onFailure(::showError)
            }
        }, {
            generatedCode = ""; generatedAt = 0L; message = ""; screen = Screen.PairChoice
        })
        Screen.JoinPair -> JoinPairScreen(busy, message, joinCode, {
            joinCode = KitaLdrRepository.normalizeCode(it).take(9); message = ""
        }, {
            busy = true
            message = ""
            repository.joinPairingCode(joinCode) { result ->
                result.onSuccess {
                    repository.loadCurrentPair { pairResult ->
                        pairResult.onSuccess { pair ->
                            pairInfo = pair
                            busy = false
                            if (pair?.status == "active") { joinCode = ""; screen = Screen.Home }
                        }.onFailure(::showError)
                    }
                }.onFailure(::showError)
            }
        }) { joinCode = ""; message = ""; screen = Screen.PairChoice }
        Screen.Home -> HomeScreen(
            pairInfo = pairInfo,
            busy = busy,
            actionBusy = actionBusy,
            message = message,
            incomingAction = incomingAction,
            onAction = { type ->
                val coupleId = pairInfo?.coupleId
                if (coupleId != null) {
                    actionBusy = true
                    message = ""
                    actionService.sendAction(coupleId, type) { result ->
                        result.onSuccess { actionBusy = false; message = "Sent with love ❤️" }
                            .onFailure { actionBusy = false; message = it.message ?: "Could not send action." }
                    }
                }
            },
            onDismissAction = { incomingAction = null },
            onSaveName = { newName ->
                busy = true
                repository.setDisplayName(newName) { result ->
                    result.onSuccess {
                        name = newName.trim()
                        repository.loadCurrentPair { pairResult -> pairResult.onSuccess { pairInfo = it; busy = false }.onFailure(::showError) }
                    }.onFailure(::showError)
                }
            },
            onDisconnect = {
                busy = true
                message = ""
                repository.disconnect { result ->
                    result.onSuccess {
                        busy = false; pairInfo = null; generatedCode = ""; generatedAt = 0L; joinCode = ""; incomingAction = null; screen = Screen.PairChoice
                    }.onFailure(::showError)
                }
            }
        )
    }

    val activeCoupleId = pairInfo?.coupleId
    if (activeCoupleId != null) {
        DisposableEffect(activeCoupleId) {
            val registration = repository.listenForCoupleStatus(activeCoupleId) {
                busy = false; actionBusy = false; pairInfo = null; generatedCode = ""; generatedAt = 0L; joinCode = ""; incomingAction = null; message = "Your pair was disconnected."; screen = Screen.PairChoice
            }
            onDispose { registration?.remove() }
        }
        DisposableEffect(activeCoupleId) {
            val registration = actionService.listenForActions(activeCoupleId) { incomingAction = it }
            onDispose { registration?.remove() }
        }
    }

    if (generatedCode.isNotBlank()) {
        DisposableEffect(generatedCode) {
            val registration = repository.listenForPairingAcceptance(generatedCode) { coupleId ->
                repository.loadCurrentPair { result ->
                    result.onSuccess { pair ->
                        pairInfo = pair
                        if (pair?.coupleId == coupleId && pair.status == "active") {
                            message = "Connected! ❤️"; generatedCode = ""; generatedAt = 0L; screen = Screen.Home
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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFEEF3), Color(0xFFFFF8EA), Color(0xFFEFF7FF))))) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LogoMark(104.dp)
            Spacer(Modifier.height(22.dp))
            Text("KitaLDR", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("Closer, Even From Afar ❤️", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(30.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .82f))) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A private little space for two.", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Stay close, share moments, and send tiny reminders that you care.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            StatusPill(if (firebaseReady) "Firebase connected" else "Firebase setup needed", if (firebaseReady) Color(0xFF3BA776) else Color(0xFFE2A33A))
            Spacer(Modifier.height(20.dp))
            Button(onClick = onStart, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = Color.White) else Text("Begin your story ❤️", fontWeight = FontWeight.Bold)
            }
            if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun NameSetupScreen(currentName: String, busy: Boolean, message: String, onNameChange: (String) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(44.dp)); LogoMark(84.dp); Spacer(Modifier.height(20.dp))
        Text("Let's make it personal", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp)); Text("What should your person see when you send a message, poke, or notification?", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Text("Your name", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = currentName, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Charles") }, supportingText = { Text("Shown across KitaLDR • ${currentName.length}/30") })
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSave, enabled = !busy && currentName.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Save my name") }
            }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(24.dp)); Text("You can change this later in Settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PairChoiceScreen(busy: Boolean, onCreate: () -> Unit, onJoin: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Connect your two", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("One person creates a code. The other joins it.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(28.dp))
        ChoiceCard("01", "Create a pair", "Generate a private code and wait for your person.", Pink, onCreate, !busy); Spacer(Modifier.height(14.dp))
        ChoiceCard("02", "Join a pair", "Enter the code your person shared with you.", Lavender, onJoin, !busy); Spacer(Modifier.weight(1f))
        Text("Your pair is private and limited to two people.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun ChoiceCard(number: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text(number, fontWeight = FontWeight.ExtraBold, color = accent) }
            Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", fontSize = 30.sp, color = accent)
        }
    }
}

@Composable
private fun CreatePairScreen(busy: Boolean, message: String, generatedCode: String, generatedAt: Long, onGenerate: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var secondsLeft by rememberSaveable(generatedCode) { mutableIntStateOf(600) }
    LaunchedEffect(generatedCode, generatedAt) {
        if (generatedCode.isBlank() || generatedAt == 0L) return@LaunchedEffect
        while (true) { val elapsed = ((System.currentTimeMillis() - generatedAt) / 1000L).toInt(); val remaining = (600 - elapsed).coerceAtLeast(0); secondsLeft = remaining; if (remaining == 0) break; delay(1000) }
    }
    val expired = generatedCode.isNotBlank() && secondsLeft == 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Create your pair", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("Share this little key with your person.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(26.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EE))) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(66.dp); Spacer(Modifier.height(14.dp)); Text("PAIRING CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp))
                Text(if (generatedCode.isBlank()) "---- ----" else generatedCode, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp); Spacer(Modifier.height(10.dp))
                Text(if (generatedCode.isBlank()) "Generate a code to start waiting." else if (expired) "This code has expired." else "Waiting for your person…", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (generatedCode.isNotBlank() && !expired) { Spacer(Modifier.height(8.dp)); Text("Expires in %02d:%02d".format(secondsLeft / 60, secondsLeft % 60), fontWeight = FontWeight.Bold, color = Pink) }
                Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { copyToClipboard(context, generatedCode) }, enabled = generatedCode.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Copy") }
                    Button(onClick = { shareCode(context, generatedCode) }, enabled = generatedCode.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Share") }
                }
            }
        }
        Spacer(Modifier.height(18.dp)); Button(onClick = onGenerate, enabled = !busy && (generatedCode.isBlank() || expired), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text(if (generatedCode.isBlank()) "Generate pairing code" else "Generate new code") }
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(32.dp)); OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun JoinPairScreen(busy: Boolean, message: String, joinCode: String, onJoinCodeChange: (String) -> Unit, onPair: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Join your person", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("Enter the code they created for you.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(22.dp)) { Text("Pairing code", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = joinCode, onValueChange = onJoinCodeChange, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, placeholder = { Text("ABCD-2345") }); Spacer(Modifier.height(14.dp)); Button(onClick = onPair, enabled = !busy && joinCode.length == 9, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Join this pair ❤️", fontWeight = FontWeight.Bold) } }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp)); Text("The code expires after 10 minutes and can only be used once.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(pairInfo: PairInfo?, busy: Boolean, actionBusy: Boolean, message: String, incomingAction: RemoteAction?, onAction: (String) -> Unit, onDismissAction: () -> Unit, onSaveName: (String) -> Unit, onDisconnect: () -> Unit) {
    val selfName = pairInfo?.selfName?.ifBlank { "You" } ?: "You"
    val partnerName = pairInfo?.partnerName?.ifBlank { "My Love" } ?: "My Love"
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNameSheet by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(selfName) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { BottomBar(tab) { tab = it } }) { padding ->
        when (tab) {
            0 -> HomeDashboard(selfName, partnerName, actionBusy, message, onAction, Modifier.padding(padding))
            1 -> PlaceholderTab("Chat", "Your private chat space is next.", "💬", Modifier.padding(padding))
            2 -> LoveCenter(partnerName, actionBusy, onAction, Modifier.padding(padding))
            3 -> CalendarTab(Modifier.padding(padding))
            else -> SettingsTab(selfName, partnerName, { editingName = selfName; showNameSheet = true }, onDisconnect, busy, Modifier.padding(padding))
        }
    }

    if (incomingAction?.type == RemoteActionService.ACTION_POKE) {
        ModalBottomSheet(onDismissRequest = onDismissAction, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(72.dp); Spacer(Modifier.height(12.dp)); Text("$partnerName poked you!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center); Spacer(Modifier.height(6.dp)); Text("A tiny hello from your person.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); Button(onClick = onDismissAction, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Got it ❤️") }; Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showNameSheet) {
        ModalBottomSheet(onDismissRequest = { showNameSheet = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
                Text("Your name", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("This is the name your person sees.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); OutlinedTextField(value = editingName, onValueChange = { editingName = it.take(30) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(14.dp)); Button(onClick = { showNameSheet = false; onSaveName(editingName) }, enabled = editingName.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Save name") }; Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HomeDashboard(selfName: String, partnerName: String, actionBusy: Boolean, message: String, onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Good morning, $selfName! ✨", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Text("A little closer to $partnerName today.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }; LogoMark(50.dp) }
        Spacer(Modifier.height(18.dp)); CoupleHero(selfName, partnerName); Spacer(Modifier.height(14.dp)); StreakCard(); Spacer(Modifier.height(14.dp)); SectionTitle("Today's little things", "See all"); Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF2FF))) { Column(Modifier.padding(18.dp)) { Text("A message for you 💌", fontWeight = FontWeight.Bold, color = Color(0xFF443A7D)); Spacer(Modifier.height(7.dp)); Text("“Jarak nggak bikin kita jauh. Yang penting kita tetap saling ingat.”", fontSize = 14.sp, color = Color(0xFF4F4961)) } }
        Spacer(Modifier.height(16.dp)); SectionTitle("Quick actions", null); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickAction("♥", "Poke", Pink, Modifier.weight(1f), !actionBusy) { onAction(RemoteActionService.ACTION_POKE) }; QuickAction("✦", "Miss you", Lavender, Modifier.weight(1f), false) {} }
        Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickAction("◌", "Wake up", Color(0xFF4FAE97), Modifier.weight(1f), false) {}; QuickAction("⌁", "Eat", Color(0xFFE4A33A), Modifier.weight(1f), false) {} }
        if (actionBusy) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pink) }
        if (message.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp)); SectionTitle("Shared goals", "See all"); Spacer(Modifier.height(8.dp)); GoalCard("Plan a dream vacation", "Target: Dec 2025", .60f, Pink); Spacer(Modifier.height(8.dp)); GoalCard("Save for our next date", "Target: Together", .40f, Lavender); Spacer(Modifier.height(20.dp)); Text("Poke is live ❤️ More remote actions are coming next.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CoupleHero(selfName: String, partnerName: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE0E7), Color(0xFFE9E2FF))), RoundedCornerShape(30.dp)).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Avatar(selfName, Pink, 58.dp); Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("Together", fontSize = 12.sp, color = Color(0xFF716778)); Text("♥", fontSize = 28.sp, color = Pink); Text("Connected", fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Avatar(partnerName, Lavender, 58.dp) }
        }
    }
}

@Composable
private fun StreakCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
        Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Love streak", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("178 days", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }; Text("♥", fontSize = 34.sp, color = Pink) }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, day -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(28.dp).clip(CircleShape).background(if (index < 6) Pink.copy(alpha = .16f) else Color(0xFFF2EDF0)), contentAlignment = Alignment.Center) { if (index < 6) Text("♥", fontSize = 11.sp, color = Pink) }; Spacer(Modifier.height(3.dp)); Text(day, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
    }
}

@Composable
private fun GoalCard(title: String, subtitle: String, progress: Float, accent: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("♥", color = accent, fontSize = 18.sp) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(7.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = accent, trackColor = accent.copy(alpha = .12f)) }; Spacer(Modifier.width(12.dp)); Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent) }
    }
}

@Composable
private fun QuickAction(icon: String, label: String, accent: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Card(modifier.clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = if (enabled) .10f else .06f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp, color = accent) }; Spacer(Modifier.height(6.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (enabled) Ink else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LoveCenter(partnerName: String, actionBusy: Boolean, onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(28.dp)); LogoMark(92.dp); Spacer(Modifier.height(16.dp)); Text("Send a little love", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("Tiny actions for $partnerName", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(28.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EE))) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("♥", fontSize = 70.sp, color = Pink); Text("Poke", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Send a vibration-worthy hello.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Button(onClick = { onAction(RemoteActionService.ACTION_POKE) }, enabled = !actionBusy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Poke $partnerName ❤️") } } }; Spacer(Modifier.height(18.dp)); Text("More actions will unlock as we build them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CalendarTab(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text("Shared calendar", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("A soft place for dates, reminders, and milestones.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); Spacer(Modifier.height(18.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("October 2026", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("♥", color = Pink, fontSize = 24.sp) }; Spacer(Modifier.height(18.dp)); val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { days.forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Spacer(Modifier.height(12.dp)); repeat(5) { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repeat(7) { day -> val n = week * 7 + day - 3; Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Text(if (n > 0 && n <= 31) n.toString() else "", fontSize = 12.sp, color = if (n == 16) Pink else Ink, fontWeight = if (n == 16) FontWeight.Bold else FontWeight.Normal) } } } } } }; Spacer(Modifier.height(16.dp)); SectionTitle("Upcoming", "See all"); Spacer(Modifier.height(8.dp)); ReminderCard("Anniversary Dinner", "16 Oct • 19:00", Pink); Spacer(Modifier.height(8.dp)); ReminderCard("Movie night", "25 Oct • 20:00", Lavender) }
}

@Composable
private fun ReminderCard(title: String, subtitle: String, accent: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text("♥", color = accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun SettingsTab(selfName: String, partnerName: String, onEditName: () -> Unit, onDisconnect: () -> Unit, busy: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("Make KitaLDR feel like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Avatar(selfName, Pink, 82.dp); Spacer(Modifier.height(10.dp)); Text(selfName, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold); Text("Connected with $partnerName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Spacer(Modifier.height(14.dp)); SettingsRow("Profile & name", "Change the name your person sees", "✦", onEditName); Spacer(Modifier.height(8.dp)); SettingsRow("Notifications", "High-priority love alerts", "♥", {}); Spacer(Modifier.height(8.dp)); SettingsRow("Appearance", "Soft pastel theme", "◐", {}); Spacer(Modifier.height(8.dp)); SettingsRow("Privacy & security", "Your pair stays private", "◇", {}); Spacer(Modifier.height(24.dp)); OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Disconnect partner") } }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFEFF2)), contentAlignment = Alignment.Center) { Text(icon, color = Pink) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun PlaceholderTab(title: String, subtitle: String, icon: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(icon, fontSize = 58.sp); Spacer(Modifier.height(12.dp)); Text(title, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(5.dp)); Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(shadowElevation = 8.dp, color = Color.White) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { BottomItem("⌂", "Home", selected == 0, Modifier.weight(1f)) { onSelect(0) }; BottomItem("◌", "Chat", selected == 1, Modifier.weight(1f)) { onSelect(1) }; Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Rose))).clickable { onSelect(2) }, contentAlignment = Alignment.Center) { Text("♥", fontSize = 26.sp, color = Color.White) } }; BottomItem("□", "Calendar", selected == 3, Modifier.weight(1f)) { onSelect(3) }; BottomItem("⋯", "More", selected == 4, Modifier.weight(1f)) { onSelect(4) } } }
}

@Composable
private fun BottomItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(icon, fontSize = 22.sp, color = if (selected) Pink else MaterialTheme.colorScheme.onSurfaceVariant); Text(label, fontSize = 9.sp, color = if (selected) Pink else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun SectionTitle(title: String, action: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); if (action != null) TextButton(onClick = {}) { Text(action, fontSize = 11.sp) } }
}

@Composable
private fun Avatar(name: String, accent: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(name.trim().firstOrNull()?.uppercase() ?: "♥", fontSize = (size.value * .34f).sp, fontWeight = FontWeight.ExtraBold, color = accent) }
}

@Composable
private fun LogoMark(size: Dp) {
    Image(painter = painterResource(id = R.drawable.ic_kitaldr_logo), contentDescription = "KitaLDR", modifier = Modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
private fun StatusPill(text: String, dotColor: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .78f)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor)); Spacer(Modifier.width(7.dp)); Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KitaLDR pairing code", text))
}

private fun shareCode(context: Context, code: String) {
    if (code.isBlank()) return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "KitaLDR pairing code: $code") }, "Share pairing code"))
}
