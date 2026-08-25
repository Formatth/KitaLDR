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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.formatth.kitaldr.data.KitaLdrRepository
import com.formatth.kitaldr.data.PairInfo
import com.formatth.kitaldr.data.PushTokenRegistrar
import com.formatth.kitaldr.data.RemoteAction
import com.formatth.kitaldr.data.RemoteActionService
import kotlinx.coroutines.delay

private enum class Screen { Welcome, NameSetup, PairChoice, CreatePair, JoinPair, Home }

private val Pink = Color(0xFFFF6678)
private val Lavender = Color(0xFF8E7CF4)
private val Ink = Color(0xFF231F2B)
private val Colors = lightColorScheme(
    primary = Pink,
    primaryContainer = Color(0xFFFFE2E7),
    secondary = Lavender,
    secondaryContainer = Color(0xFFE9E4FF),
    background = Color(0xFFFFF9FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF6F0F4),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF746C76)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = KitaLdrRepository(this)
        val actions = RemoteActionService(this)
        setContent { MaterialTheme(colorScheme = Colors) { KitaLdrApp(repository, actions) } }
    }
}

@Composable
private fun KitaLdrApp(repository: KitaLdrRepository, actions: RemoteActionService) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var ready by rememberSaveable { mutableStateOf(repository.isConfigured) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var createdAt by rememberSaveable { mutableStateOf(0L) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var pair by remember { mutableStateOf<PairInfo?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<RemoteAction?>(null) }

    fun error(t: Throwable) { busy = false; actionBusy = false; message = t.message ?: "Something went wrong." }
    fun loadPair() {
        repository.loadCurrentPair { r -> r.onSuccess { p -> pair = p; busy = false; screen = if (p?.status == "active") Screen.Home else Screen.PairChoice }.onFailure(::error) }
    }
    fun afterLogin() {
        PushTokenRegistrar.ensureTokenRegistered()
        repository.loadCurrentDisplayName { r -> r.onSuccess { n -> name = n; if (n.isBlank() || n.equals("My Love", true)) { busy = false; screen = Screen.NameSetup } else loadPair() }.onFailure(::error) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        if (!ready) return@LaunchedEffect
        busy = true
        if (repository.currentUid() != null) afterLogin() else repository.signIn { r -> r.onSuccess { afterLogin() }.onFailure(::error) }
    }

    when (screen) {
        Screen.Welcome -> Welcome(ready, busy, message) {
            if (!repository.isConfigured) { ready = false; message = "Firebase belum terhubung." }
            else { busy = true; message = ""; repository.signIn { r -> r.onSuccess { afterLogin() }.onFailure(::error) } }
        }
        Screen.NameSetup -> NameSetup(name, busy, message, { name = it.take(30); message = "" }) {
            busy = true
            repository.setDisplayName(name) { r -> r.onSuccess { loadPair() }.onFailure(::error) }
        }
        Screen.PairChoice -> PairChoice(busy, { screen = Screen.CreatePair }, { screen = Screen.JoinPair }) { screen = Screen.Welcome }
        Screen.CreatePair -> CreatePair(busy, message, code, createdAt, {
            busy = true; message = ""
            repository.createPairingCode { r -> r.onSuccess { code = it; createdAt = System.currentTimeMillis(); busy = false }.onFailure(::error) }
        }) { code = ""; createdAt = 0L; screen = Screen.PairChoice }
        Screen.JoinPair -> JoinPair(busy, message, joinCode, { joinCode = KitaLdrRepository.normalizeCode(it).take(9) }, {
            busy = true; message = ""
            repository.joinPairingCode(joinCode) { r -> r.onSuccess { loadPair() }.onFailure(::error) }
        }) { joinCode = ""; screen = Screen.PairChoice }
        Screen.Home -> Home(pair, actionBusy, message, incoming, { type ->
            pair?.coupleId?.let { id ->
                actionBusy = true; message = ""
                actions.sendAction(id, type) { r -> r.onSuccess { actionBusy = false; message = "Sent with love ❤️" }.onFailure { actionBusy = false; message = it.message ?: "Could not send action." } }
            }
        }, { incoming = null }, { newName ->
            busy = true
            repository.setDisplayName(newName) { r -> r.onSuccess { name = newName.trim(); loadPair() }.onFailure(::error) }
        }) {
            busy = true
            repository.disconnect { r -> r.onSuccess { busy = false; pair = null; screen = Screen.PairChoice }.onFailure(::error) }
        }
    }

    val coupleId = pair?.coupleId
    if (coupleId != null) {
        DisposableEffect(coupleId) {
            val status = repository.listenForCoupleStatus(coupleId) { pair = null; screen = Screen.PairChoice; message = "Your pair was disconnected." }
            val listener = actions.listenForActions(coupleId) { incoming = it }
            onDispose { status?.remove(); listener?.remove() }
        }
    }
    if (code.isNotBlank()) {
        DisposableEffect(code) {
            val listener = repository.listenForPairingAcceptance(code) { acceptedId ->
                repository.loadCurrentPair { r -> r.onSuccess { p -> if (p?.coupleId == acceptedId) { pair = p; code = ""; createdAt = 0L; screen = Screen.Home } } }
            }
            onDispose { listener?.remove() }
        }
    }
}

@Composable
private fun Welcome(ready: Boolean, busy: Boolean, message: String, start: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFEEF3), Color(0xFFFFF8EA), Color(0xFFEFF7FF))))) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Logo(100); Spacer(Modifier.height(18.dp)); Text("KitaLDR", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold); Text("Closer, even from afar ❤️", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("A private little space for two.", fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Stay close, share moments, and send tiny reminders that you care.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) } }
            Spacer(Modifier.height(18.dp)); Text(if (ready) "● Firebase connected" else "● Firebase setup needed", color = if (ready) Color(0xFF3BA776) else Color(0xFFE2A33A), fontSize = 12.sp); Spacer(Modifier.height(18.dp))
            Button(onClick = start, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("Begin your story ❤️", fontWeight = FontWeight.Bold) }
            if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun NameSetup(current: String, busy: Boolean, message: String, change: (String) -> Unit, save: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp)); Logo(80); Spacer(Modifier.height(18.dp)); Text("Let's make it personal", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text("What should your person see when you send a poke or notification?", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(26.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) { Column(Modifier.padding(20.dp)) { Text("Your name", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(current, change, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Charles") }); Spacer(Modifier.height(12.dp)); Button(save, enabled = !busy && current.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save my name") } } }
        if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@Composable
private fun PairChoice(busy: Boolean, create: () -> Unit, join: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Connect your two", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("One person creates a code. The other joins it.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Choice("01", "Create a pair", "Generate a private code.", Pink, create, !busy); Spacer(Modifier.height(12.dp)); Choice("02", "Join a pair", "Enter your person's code.", Lavender, join, !busy); Spacer(Modifier.weight(1f)); OutlinedButton(back, Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") } }
}

@Composable
private fun Choice(n: String, title: String, sub: String, color: Color, click: () -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth().clickable(enabled, onClick = click), shape = RoundedCornerShape(26.dp)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text(n, color = color, fontWeight = FontWeight.ExtraBold) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", fontSize = 28.sp, color = color) } }
}

@Composable
private fun CreatePair(busy: Boolean, message: String, code: String, createdAt: Long, generate: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var left by rememberSaveable(code) { mutableIntStateOf(600) }
    LaunchedEffect(code, createdAt) { if (code.isNotBlank()) while (left > 0) { left = (600 - ((System.currentTimeMillis() - createdAt) / 1000L).toInt()).coerceAtLeast(0); delay(1000) } }
    val expired = code.isNotBlank() && left == 0
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Create your pair", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Text("Share this code with your person.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EE))) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Logo(62); Spacer(Modifier.height(12.dp)); Text(if (code.isBlank()) "---- ----" else code, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp); Spacer(Modifier.height(8.dp)); Text(if (code.isBlank()) "Generate a code to begin." else if (expired) "Code expired." else "Waiting for your person…", color = MaterialTheme.colorScheme.onSurfaceVariant); if (code.isNotBlank() && !expired) Text("Expires in %02d:%02d".format(left / 60, left % 60), color = Pink, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton({ copy(context, code) }, enabled = code.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Copy") }; Button({ share(context, code) }, enabled = code.isNotBlank() && !expired, modifier = Modifier.weight(1f)) { Text("Share") } } } }; Spacer(Modifier.height(18.dp)); Button(generate, enabled = !busy && (code.isBlank() || expired), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (code.isBlank()) "Generate pairing code" else "Generate new code") }; if (message.isNotBlank()) Text(message, modifier = Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error); Spacer(Modifier.weight(1f)); OutlinedButton(back, Modifier.fillMaxWidth()) { Text("Back") } }
}

@Composable
private fun JoinPair(busy: Boolean, message: String, code: String, change: (String) -> Unit, join: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Join your person", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); Text("Enter the code they created for you.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(26.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) { Column(Modifier.padding(20.dp)) { Text("Pairing code", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(code, change, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, placeholder = { Text("ABCD-2345") }); Spacer(Modifier.height(12.dp)); Button(join, enabled = !busy && code.length == 9, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Join this pair ❤️") } } }; if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error); Spacer(Modifier.weight(1f)); OutlinedButton(back, Modifier.fillMaxWidth()) { Text("Back") } }
}

@Composable
private fun Home(pair: PairInfo?, actionBusy: Boolean, message: String, incoming: RemoteAction?, action: (String) -> Unit, dismiss: () -> Unit, saveName: (String) -> Unit, disconnect: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var edit by rememberSaveable { mutableStateOf(pair?.selfName ?: "") }
    Scaffold(bottomBar = { Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) { TextButton({ tab = 0 }) { Text("⌂ Home") }; TextButton({ tab = 2 }) { Text("♥ Love") }; TextButton({ tab = 3 }) { Text("□ Calendar") }; TextButton({ tab = 4 }) { Text("⋯ More") } } }) { pad ->
        when (tab) {
            0 -> Dashboard(pair?.selfName ?: "You", pair?.partnerName ?: "My Love", actionBusy, message, action, Modifier.padding(pad))
            2 -> LoveTab(pair?.partnerName ?: "My Love", actionBusy, action, Modifier.padding(pad))
            3 -> SimpleTab("Shared calendar", "Dates, reminders, and milestones.", Modifier.padding(pad))
            4 -> Settings(pair?.selfName ?: "You", pair?.partnerName ?: "My Love", edit, { edit = it.take(30) }, { saveName(edit) }, disconnect, Modifier.padding(pad))
            else -> SimpleTab("Private chat", "Chat is the next feature.", Modifier.padding(pad))
        }
    }
    if (incoming?.type == RemoteActionService.ACTION_POKE) {
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EE))) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("📳", fontSize = 44.sp); Text("${pair?.partnerName ?: "My Love"} poked you!", fontSize = 20.sp, fontWeight = FontWeight.Bold); Button(dismiss) { Text("Got it ❤️") } } }
    }
}

@Composable
private fun Dashboard(self: String, partner: String, busy: Boolean, message: String, action: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Good morning, $self! ✨", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); Text("A little closer to $partner today.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }; Logo(48) }; Spacer(Modifier.height(16.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0E7))) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(self, Pink, 56); Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("Together", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("♥", fontSize = 28.sp, color = Pink); Text("Connected", fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Avatar(partner, Lavender, 56) } }; Spacer(Modifier.height(14.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp)) { Text("Love streak", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("178 days", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(10.dp)); LinearProgressIndicator({ .72f }, Modifier.fillMaxWidth(), color = Pink) } }; Spacer(Modifier.height(18.dp)); Text("Quick actions", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Action("♥", "Poke", Pink, Modifier.weight(1f), !busy) { action(RemoteActionService.ACTION_POKE) }; Action("✦", "Miss you", Lavender, Modifier.weight(1f), false) {} }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Action("◌", "Wake up", Color(0xFF4FAE97), Modifier.weight(1f), false) {}; Action("⌁", "Eat", Color(0xFFE4A33A), Modifier.weight(1f), false) {} }; if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp) }; Spacer(Modifier.height(18.dp)); Text("Shared goals", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Text("Plan your next date, dream trip, or little promise together.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
}

@Composable
private fun LoveTab(partner: String, busy: Boolean, action: (String) -> Unit, modifier: Modifier) { Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Logo(90); Spacer(Modifier.height(14.dp)); Text("Send a little love", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("Tiny actions for $partner", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(22.dp)); Button({ action(RemoteActionService.ACTION_POKE) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Poke $partner ❤️") } }
}

@Composable
private fun Settings(self: String, partner: String, edit: String, change: (String) -> Unit, save: () -> Unit, disconnect: () -> Unit, modifier: Modifier) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Text("Make KitaLDR feel like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Avatar(self, Pink, 82); Spacer(Modifier.height(10.dp)); Text("Connected with $partner", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); Text("Your display name", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); OutlinedTextField(edit, change, Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp)); Button(save, Modifier.fillMaxWidth()) { Text("Save name") }; Spacer(Modifier.height(18.dp)); OutlinedButton(disconnect, Modifier.fillMaxWidth()) { Text("Disconnect partner") } }
}

@Composable
private fun SimpleTab(title: String, subtitle: String, modifier: Modifier) { Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("♡", fontSize = 58.sp, color = Pink); Text(title, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun Action(icon: String, label: String, color: Color, modifier: Modifier, enabled: Boolean, click: () -> Unit) { Card(modifier.clickable(enabled, onClick = click), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (enabled) .11f else .05f))) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(icon, fontSize = 28.sp, color = color); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }

@Composable
private fun Avatar(name: String, color: Color, size: Int) { Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(name.trim().firstOrNull()?.uppercase() ?: "♥", fontSize = (size * .34f).sp, fontWeight = FontWeight.ExtraBold, color = color) } }

@Composable
private fun Logo(size: Int) { Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .25f).dp)).background(Brush.linearGradient(listOf(Pink, Lavender))), contentAlignment = Alignment.Center) { Text("♥", fontSize = (size * .38f).sp, color = Color.White) } }

private fun copy(context: Context, text: String) { if (text.isBlank()) return; (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("KitaLDR pairing code", text)) }
private fun share(context: Context, code: String) { if (code.isBlank()) return; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "KitaLDR pairing code: $code") }, "Share pairing code")) }
