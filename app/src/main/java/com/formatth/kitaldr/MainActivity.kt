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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
private val Rose = Color(0xFFFF5F78)
private val RoseSoft = Color(0xFFFFE8ED)
private val RosePale = Color(0xFFFFF6F8)
private val Lavender = Color(0xFF8C79F2)
private val LavenderSoft = Color(0xFFF1EEFF)
private val Ink = Color(0xFF28242B)
private val Muted = Color(0xFF817A83)
private val Line = Color(0xFFEDE7EB)
private val AppBackground = Color(0xFFFFFBFC)

private val KitaColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    secondary = Lavender,
    background = AppBackground,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    outline = Line,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = KitaLdrRepository(this)
        val actions = RemoteActionService(this)
        setContent { MaterialTheme(colorScheme = KitaColors) { KitaLdrApp(repository, actions) } }
    }
}

@Composable
private fun KitaLdrApp(repository: KitaLdrRepository, actions: RemoteActionService) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var createdAt by rememberSaveable { mutableStateOf(0L) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var pair by remember { mutableStateOf<PairInfo?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<RemoteAction?>(null) }

    fun error(t: Throwable) {
        busy = false
        actionBusy = false
        message = t.message ?: "Something went wrong."
    }

    fun loadPair() {
        repository.loadCurrentPair { result ->
            result.onSuccess { current ->
                pair = current
                busy = false
                screen = if (current?.status == "active") Screen.Home else Screen.PairChoice
            }.onFailure(::error)
        }
    }

    fun afterLogin() {
        PushTokenRegistrar.ensureTokenRegistered()
        repository.loadCurrentDisplayName { result ->
            result.onSuccess { displayName ->
                name = displayName
                if (displayName.isBlank() || displayName.equals("My Love", true)) {
                    busy = false
                    screen = Screen.NameSetup
                } else {
                    loadPair()
                }
            }.onFailure(::error)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        if (!repository.isConfigured) return@LaunchedEffect
        busy = true
        if (repository.currentUid() != null) afterLogin()
        else repository.signIn { result -> result.onSuccess { afterLogin() }.onFailure(::error) }
    }

    when (screen) {
        Screen.Welcome -> Welcome(busy, message) {
            if (!repository.isConfigured) {
                message = "Please finish the app connection first."
            } else {
                busy = true
                message = ""
                repository.signIn { result -> result.onSuccess { afterLogin() }.onFailure(::error) }
            }
        }
        Screen.NameSetup -> NameSetup(name, busy, message, { name = it.take(30); message = "" }) {
            busy = true
            repository.setDisplayName(name) { result -> result.onSuccess { loadPair() }.onFailure(::error) }
        }
        Screen.PairChoice -> PairChoice(busy, { screen = Screen.CreatePair }, { screen = Screen.JoinPair }) { screen = Screen.Welcome }
        Screen.CreatePair -> CreatePair(busy, message, code, createdAt, {
            busy = true
            message = ""
            repository.createPairingCode { result ->
                result.onSuccess { code = it; createdAt = System.currentTimeMillis(); busy = false }
                    .onFailure(::error)
            }
        }) { code = ""; createdAt = 0L; screen = Screen.PairChoice }
        Screen.JoinPair -> JoinPair(busy, message, joinCode, { joinCode = KitaLdrRepository.normalizeCode(it).take(9) }, {
            busy = true
            message = ""
            repository.joinPairingCode(joinCode) { result -> result.onSuccess { loadPair() }.onFailure(::error) }
        }) { joinCode = ""; screen = Screen.PairChoice }
        Screen.Home -> Home(
            pair = pair,
            actionBusy = actionBusy,
            message = message,
            incoming = incoming,
            action = { type ->
                pair?.coupleId?.let { coupleId ->
                    actionBusy = true
                    message = ""
                    actions.sendAction(coupleId, type) { result ->
                        result.onSuccess { actionBusy = false; message = "Sent ❤️" }
                            .onFailure { actionBusy = false; message = it.message ?: "Could not send." }
                    }
                }
            },
            dismissIncoming = { incoming = null },
            saveName = { newName ->
                busy = true
                repository.setDisplayName(newName) { result ->
                    result.onSuccess { name = newName.trim(); loadPair() }.onFailure(::error)
                }
            },
            disconnect = {
                busy = true
                repository.disconnect { result ->
                    result.onSuccess { busy = false; pair = null; screen = Screen.PairChoice }
                        .onFailure(::error)
                }
            },
        )
    }

    val coupleId = pair?.coupleId
    if (coupleId != null) {
        DisposableEffect(coupleId) {
            val status = repository.listenForCoupleStatus(coupleId) {
                pair = null
                screen = Screen.PairChoice
                message = "Your connection has ended."
            }
            val listener = actions.listenForActions(coupleId) { incoming = it }
            onDispose { status?.remove(); listener?.remove() }
        }
    }

    if (code.isNotBlank()) {
        DisposableEffect(code) {
            val listener = repository.listenForPairingAcceptance(code) { acceptedId ->
                repository.loadCurrentPair { result ->
                    result.onSuccess { current ->
                        if (current?.coupleId == acceptedId) {
                            pair = current
                            code = ""
                            createdAt = 0L
                            screen = Screen.Home
                        }
                    }
                }
            }
            onDispose { listener?.remove() }
        }
    }
}

@Composable
private fun Welcome(busy: Boolean, message: String, start: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFEEF3), Color(0xFFFFFBFC), Color(0xFFF5F1FF)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(92)
            Spacer(Modifier.height(22.dp))
            Text("KitaLDR", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(4.dp))
            Text("A little space for the two of you.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(34.dp))
            SurfacePanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Closer, even from afar", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Share tiny moments, send a little love, and keep your connection close.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = start,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Begin your story", fontWeight = FontWeight.Bold)
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message, color = Rose, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NameSetup(current: String, busy: Boolean, message: String, change: (String) -> Unit, save: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(42.dp))
        BrandMark(72)
        Spacer(Modifier.height(20.dp))
        Text("Let's make it personal", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("This is the name your person will see.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Your name", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = change,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. Rohmat") },
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Continue", !busy && current.trim().isNotEmpty(), save)
            }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, color = Rose, fontSize = 12.sp) }
    }
}

@Composable
private fun PairChoice(busy: Boolean, create: () -> Unit, join: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Bring you two together", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
        Spacer(Modifier.height(7.dp))
        Text("Connect once, then your little space is private to the two of you.", color = Muted, lineHeight = 20.sp)
        Spacer(Modifier.height(28.dp))
        PairOption("01", "Create a pair", "Generate a private code", Rose, create, !busy)
        Spacer(Modifier.height(12.dp))
        PairOption("02", "Join a pair", "Enter your person's code", Lavender, join, !busy)
        Spacer(Modifier.weight(1f))
        TextButton(back, Modifier.fillMaxWidth(), enabled = !busy) { Text("Back") }
    }
}

@Composable
private fun PairOption(number: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit, enabled: Boolean) {
    SurfacePanel(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Text(number, color = accent, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            Text("›", color = accent, fontSize = 30.sp)
        }
    }
}

@Composable
private fun CreatePair(busy: Boolean, message: String, code: String, createdAt: Long, generate: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var left by rememberSaveable(code) { mutableIntStateOf(600) }
    LaunchedEffect(code, createdAt) {
        if (code.isNotBlank()) while (left > 0) {
            left = (600 - ((System.currentTimeMillis() - createdAt) / 1000L).toInt()).coerceAtLeast(0)
            delay(1000)
        }
    }
    val expired = code.isNotBlank() && left == 0
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Create your pair", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("Share the code with your person to connect.", color = Muted)
        Spacer(Modifier.height(26.dp))
        SurfacePanel(Modifier.fillMaxWidth(), background = RosePale) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(58)
                Spacer(Modifier.height(16.dp))
                Text(if (code.isBlank()) "— — — —" else code, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (code.isBlank()) "Generate a code to begin"
                    else if (expired) "This code has expired"
                    else "Waiting for your person…",
                    color = Muted,
                    fontSize = 13.sp,
                )
                if (code.isNotBlank() && !expired) {
                    Spacer(Modifier.height(6.dp))
                    Text("Expires in %02d:%02d".format(left / 60, left % 60), color = Rose, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ copy(context, code) }, enabled = code.isNotBlank() && !expired, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) { Text("Copy") }
                    Button({ share(context, code) }, enabled = code.isNotBlank() && !expired, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) { Text("Share") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (code.isBlank() || expired) "Generate code" else "Waiting…", !busy && (code.isBlank() || expired), generate)
        if (message.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Rose, fontSize = 12.sp) }
        Spacer(Modifier.weight(1f))
        TextButton(back, Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun JoinPair(busy: Boolean, message: String, code: String, change: (String) -> Unit, join: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Join your person", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("Enter the private code they created for you.", color = Muted)
        Spacer(Modifier.height(28.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Pairing code", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = change,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    placeholder = { Text("ABCD-2345") },
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Join pair", !busy && code.length == 9, join)
            }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Rose, fontSize = 12.sp) }
        Spacer(Modifier.weight(1f))
        TextButton(back, Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun Home(
    pair: PairInfo?,
    actionBusy: Boolean,
    message: String,
    incoming: RemoteAction?,
    action: (String) -> Unit,
    dismissIncoming: () -> Unit,
    saveName: (String) -> Unit,
    disconnect: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var edit by rememberSaveable { mutableStateOf(pair?.selfName ?: "") }
    val self = pair?.selfName?.takeIf { it.isNotBlank() } ?: "You"
    val partner = pair?.partnerName?.takeIf { it.isNotBlank() } ?: "Your person"

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            PremiumNav(tab) { tab = it }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeDashboard(self, partner, actionBusy, message, action)
                1 -> LoveScreen(partner, actionBusy, action)
                2 -> CalendarScreen()
                3 -> MoreScreen(self, partner, edit, { edit = it.take(30) }, { saveName(edit) }, disconnect)
            }
            if (incoming?.type == RemoteActionService.ACTION_POKE) {
                PokeOverlay(partner, dismissIncoming)
            }
        }
    }
}

@Composable
private fun HomeDashboard(self: String, partner: String, busy: Boolean, message: String, action: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Good morning, $self", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp)
            }
            Avatar(self, Rose, 46)
        }
        Spacer(Modifier.height(20.dp))
        TogetherCard(self, partner)
        Spacer(Modifier.height(20.dp))
        Text("A little love", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        PokeButton(partner, busy) { action(RemoteActionService.ACTION_POKE) }
        Spacer(Modifier.height(20.dp))
        Text("Quick moments", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MomentTile("✦", "Miss you", Lavender, Modifier.weight(1f))
            MomentTile("☀", "Wake up", Color(0xFF4FAE97), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MomentTile("♡", "Thinking of you", Rose, Modifier.weight(1f))
            MomentTile("⌁", "Eat well", Color(0xFFE0A04B), Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        SurfacePanel(Modifier.fillMaxWidth(), background = Color(0xFFF8F5FF)) {
            Column(Modifier.padding(18.dp)) {
                Text("Your space for two", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Dates, memories, little promises and more — we'll build it together.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TogetherCard(self: String, partner: String) {
    SurfacePanel(Modifier.fillMaxWidth(), background = RoseSoft) {
        Column(Modifier.padding(18.dp)) {
            Text("Together", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Avatar(self, Rose, 62)
                    Spacer(Modifier.height(7.dp))
                    Text(self, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♥", color = Rose, fontSize = 27.sp)
                    Text("connected", color = Muted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Avatar(partner, Lavender, 62)
                    Spacer(Modifier.height(7.dp))
                    Text(partner, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PokeButton(partner: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).clickable(enabled = !busy, onClick = onClick).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Rose), contentAlignment = Alignment.Center) {
                Text("♥", color = Color.White, fontSize = 23.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(if (busy) "Sending a little love…" else "Send a little love", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Poke $partner", color = Color(0xFFCFC8CE), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("›", color = Color.White, fontSize = 28.sp)
        }
    }
}

@Composable
private fun MomentTile(icon: String, label: String, accent: Color, modifier: Modifier) {
    SurfacePanel(modifier, background = accent.copy(alpha = .075f)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Text(icon, color = accent, fontSize = 25.sp)
            Spacer(Modifier.height(12.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LoveScreen(partner: String, busy: Boolean, action: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Love", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text("Small things can feel big from far away.", color = Muted)
        Spacer(Modifier.height(28.dp))
        SurfacePanel(Modifier.fillMaxWidth(), background = RoseSoft) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", color = Rose, fontSize = 52.sp)
                Spacer(Modifier.height(10.dp))
                Text("Thinking of $partner?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Send them a tiny reminder that you're here.", color = Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Poke $partner", !busy) { action(RemoteActionService.ACTION_POKE) }
            }
        }
    }
}

@Composable
private fun CalendarScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Calendar", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text("Keep the moments you don't want to forget.", color = Muted)
        Spacer(Modifier.height(24.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("Your shared calendar", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Dates, anniversaries and little plans will live here.", color = Muted, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                Text("Coming soon", color = Rose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MoreScreen(self: String, partner: String, edit: String, change: (String) -> Unit, save: () -> Unit, disconnect: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("More", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text("Your account and connection.", color = Muted)
        Spacer(Modifier.height(24.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(self, Rose, 68)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(self, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("Connected with $partner", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Display name", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(edit, change, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Save changes", edit.trim().isNotEmpty(), save)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(disconnect, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Disconnect") }
        Spacer(Modifier.height(12.dp))
        Text("More settings will be added later.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PokeOverlay(partner: String, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)), contentAlignment = Alignment.BottomCenter) {
        SurfacePanel(Modifier.fillMaxWidth().navigationBarsPadding(), background = Color.White, radius = 30.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(RoseSoft), contentAlignment = Alignment.Center) { Text("♥", color = Rose, fontSize = 30.sp) }
                Spacer(Modifier.height(12.dp))
                Text("$partner poked you", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text("Someone is thinking about you.", color = Muted)
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Got it ❤️", true, dismiss)
            }
        }
    }
}

@Composable
private fun PremiumNav(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("⌂", "Home", selected == 0) { onSelect(0) }
        NavItem("♥", "Love", selected == 1) { onSelect(1) }
        NavItem("□", "Calendar", selected == 2) { onSelect(2) }
        NavItem("•••", "More", selected == 3) { onSelect(3) }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = if (selected) Rose else Muted, fontSize = if (icon == "•••") 15.sp else 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) Ink else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SurfacePanel(modifier: Modifier, background: Color = Color.White, radius: Dp = 22.dp, content: @Composable () -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(radius), colors = CardDefaults.cardColors(containerColor = background), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), content = content)
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Avatar(name: String, accent: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "♥", color = accent, fontSize = (size * .34f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .28f).dp)).background(RoseSoft), contentAlignment = Alignment.Center) {
        Text("♥", color = Rose, fontSize = (size * .43f).sp)
    }
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "A little closer today"
    in 12..17 -> "Hope your day is going well"
    else -> "A little love before tonight"
}

private fun copy(context: Context, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KitaLDR pairing code", value))
}

private fun share(context: Context, value: String) {
    if (value.isBlank()) return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "My KitaLDR pairing code is $value")
    }, "Share pairing code"))
}
