package com.formatth.kitaldr.data

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CalendarRose = Color(0xFFFF5F78)
private val CalendarRoseSoft = Color(0xFFFFE8ED)
private val CalendarLavender = Color(0xFF8C79F2)
private val CalendarInk = Color(0xFF28242B)
private val CalendarMuted = Color(0xFF817A83)
private val CalendarLine = Color(0xFFEDE7EB)

private val relationshipDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
private val memoryDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))
private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
fun RelationshipCalendarScreen(coupleId: String) {
    val context = LocalContext.current
    val store = remember { RelationshipDataStore(context) }
    val today = LocalDate.now()
    var relationshipDateText by rememberSaveable(coupleId) { mutableStateOf("") }
    var memories by remember(coupleId) { mutableStateOf<List<RelationshipMemory>>(emptyList()) }
    var message by rememberSaveable(coupleId) { mutableStateOf("") }
    var showMemoryDialog by rememberSaveable(coupleId) { mutableStateOf(false) }
    var memoryBusy by rememberSaveable(coupleId) { mutableStateOf(false) }

    DisposableEffect(coupleId) {
        if (coupleId.isBlank()) {
            onDispose { }
        } else {
            val relationshipListener = store.listenRelationship(coupleId) { date, error ->
                if (error != null) message = error.message ?: "Could not load relationship data."
                else relationshipDateText = date.orEmpty()
            }
            val memoriesListener = store.listenMemories(coupleId) { items, error ->
                if (error != null) message = error.message ?: "Could not load memories."
                else memories = items
            }
            onDispose {
                relationshipListener?.remove()
                memoriesListener?.remove()
            }
        }
    }

    val relationshipDate = relationshipDateText.toLocalDateOrNull()
    val streak = relationshipDate?.let { if (it <= today) ChronoUnit.DAYS.between(it, today) + 1 else 0L }
    val nextAnniversary = relationshipDate?.let { anniversaryFor(it, today) }
    val countdown = nextAnniversary?.let { ChronoUnit.DAYS.between(today, it) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Calendar", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your dates, anniversaries and memories — just for the two of you.", color = CalendarMuted, lineHeight = 20.sp)

        if (relationshipDate == null) {
            RelationshipSetupCard(
                onPickDate = {
                    showDatePicker(context, today) { selected ->
                        memoryBusy = true
                        store.saveRelationshipStartDate(coupleId, selected) { result ->
                            memoryBusy = false
                            result.onSuccess {
                                message = "Relationship date saved"
                                relationshipDateText = selected.toString()
                            }.onFailure { message = it.message ?: "Could not save relationship date." }
                        }
                    }
                },
                busy = memoryBusy,
            )
        } else {
            RelationshipDateCard(
                date = relationshipDate,
                streak = streak ?: 0L,
                nextAnniversary = nextAnniversary,
                countdown = countdown ?: 0L,
                onChangeDate = {
                    showDatePicker(context, relationshipDate) { selected ->
                        memoryBusy = true
                        store.saveRelationshipStartDate(coupleId, selected) { result ->
                            memoryBusy = false
                            result.onSuccess {
                                message = "Relationship date updated"
                                relationshipDateText = selected.toString()
                            }.onFailure { message = it.message ?: "Could not update relationship date." }
                        }
                    }
                },
                busy = memoryBusy,
            )
        }

        if (message.isNotBlank()) {
            Text(message, Modifier.fillMaxWidth(), color = CalendarRose, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        MemoriesCard(
            memories = memories,
            onAdd = { showMemoryDialog = true },
            onDelete = { memory ->
                store.deleteMemory(coupleId, memory.id) { result ->
                    result.onFailure { message = it.message ?: "Could not delete memory." }
                }
            },
        )
    }

    if (showMemoryDialog) {
        AddMemoryDialog(
            context = context,
            busy = memoryBusy,
            onDismiss = { if (!memoryBusy) showMemoryDialog = false },
            onSave = { title, note, date ->
                memoryBusy = true
                store.addMemory(coupleId, title, note, date) { result ->
                    memoryBusy = false
                    result.onSuccess {
                        showMemoryDialog = false
                        message = "Memory saved"
                    }.onFailure { message = it.message ?: "Could not save memory." }
                }
            },
        )
    }
}

@Composable
private fun RelationshipSetupCard(onPickDate: () -> Unit, busy: Boolean) {
    CalendarPanel(background = CalendarRoseSoft) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = CalendarRose, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("When did your story begin?", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Set your real relationship date. It won't be generated automatically.", color = CalendarMuted, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        CalendarPrimaryButton(if (busy) "Saving…" else "Set relationship date", !busy, onPickDate)
    }
}

@Composable
private fun RelationshipDateCard(
    date: LocalDate,
    streak: Long,
    nextAnniversary: LocalDate,
    countdown: Long,
    onChangeDate: () -> Unit,
    busy: Boolean,
) {
    CalendarPanel {
        Text("Your relationship", color = CalendarMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(date.format(relationshipDateFormatter), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard(Icons.Filled.LocalFireDepartment, "Love streak", "$streak days", CalendarRose, Modifier.weight(1f))
            StatCard(Icons.Filled.Timer, "Next anniversary", "$countdown days", CalendarLavender, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        CalendarPanel(background = Color(0xFFF8F5FF), radius = 17.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = CalendarLavender, modifier = Modifier.size(23.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Next anniversary", fontWeight = FontWeight.Bold)
                    Text(nextAnniversary.format(relationshipDateFormatter), color = CalendarMuted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChangeDate, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
            Text(if (busy) "Saving…" else "Change date")
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .075f)), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, color = CalendarMuted, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun MemoriesCard(memories: List<RelationshipMemory>, onAdd: () -> Unit, onDelete: (RelationshipMemory) -> Unit) {
    CalendarPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Memories", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Little moments worth keeping.", color = CalendarMuted, fontSize = 13.sp)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(CalendarRoseSoft).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = CalendarRose)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (memories.isEmpty()) {
            CalendarPanel(background = Color(0xFFFFFBFC), radius = 17.dp) {
                Text("No memories yet", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Add your first little moment together.", color = CalendarMuted, fontSize = 13.sp)
            }
        } else {
            memories.forEach { memory ->
                MemoryRow(memory, onDelete)
                if (memory != memories.last()) Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun MemoryRow(memory: RelationshipMemory, onDelete: (RelationshipMemory) -> Unit) {
    CalendarPanel(background = Color(0xFFFFFBFC), radius = 17.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(CalendarRoseSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = CalendarRose, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(memory.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(formatMemoryDate(memory.date), color = CalendarMuted, fontSize = 11.sp)
                if (memory.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(memory.note, color = CalendarInk, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete memory", tint = CalendarMuted, modifier = Modifier.size(20.dp).clickable { onDelete(memory) })
        }
    }
}

@Composable
private fun AddMemoryDialog(
    context: Context,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val today = LocalDate.now()
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = dateText.toLocalDateOrNull() ?: today

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a memory", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("What happened?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        showDatePicker(context, selectedDate) { selected -> dateText = selected.toString() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(formatMemoryDate(dateText))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), note.trim(), dateText) }, enabled = !busy && title.trim().isNotEmpty()) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun CalendarPanel(background: Color = Color.White, radius: androidx.compose.ui.unit.Dp = 22.dp, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), colors = CardDefaults.cardColors(containerColor = background), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun CalendarPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = CalendarInk)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

private fun showDatePicker(context: Context, initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

private fun anniversaryFor(start: LocalDate, today: LocalDate): LocalDate {
    var year = today.year
    var day = minOf(start.dayOfMonth, YearMonth.of(year, start.monthValue).lengthOfMonth())
    var candidate = LocalDate.of(year, start.monthValue, day)
    if (candidate.isBefore(today)) {
        year += 1
        day = minOf(start.dayOfMonth, YearMonth.of(year, start.monthValue).lengthOfMonth())
        candidate = LocalDate.of(year, start.monthValue, day)
    }
    return candidate
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this, isoDateFormatter) }.getOrNull()

private fun formatMemoryDate(value: String): String = value.toLocalDateOrNull()?.format(memoryDateFormatter) ?: value

private fun formatMemoryDate(date: LocalDate): String = date.format(memoryDateFormatter)

private class RelationshipDataStore(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull()
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val db = app?.let { FirebaseFirestore.getInstance(it) }

    fun listenRelationship(coupleId: String, onResult: (String?, Throwable?) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        return firestore.collection("couples").document(coupleId).addSnapshotListener { snapshot, error ->
            if (error != null) onResult(null, error)
            else onResult(snapshot?.getString("relationshipStartDate"), null)
        }
    }

    fun saveRelationshipStartDate(coupleId: String, date: LocalDate, onResult: (Result<Unit>) -> Unit) {
        val firestore = db
        val uid = auth?.currentUser?.uid
        if (firestore == null || uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        val clean = date.toString()
        if (date.isAfter(LocalDate.now())) {
            onResult(Result.failure(IllegalArgumentException("Relationship date cannot be in the future.")))
            return
        }
        firestore.collection("couples").document(coupleId).update(
            mapOf("relationshipStartDate" to clean)
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenMemories(coupleId: String, onResult: (List<RelationshipMemory>, Throwable?) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        return firestore.collection("couples").document(coupleId).collection("memories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(emptyList(), error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    RelationshipMemory(
                        id = doc.id,
                        title = title,
                        note = doc.getString("note").orEmpty(),
                        date = doc.getString("date").orEmpty(),
                        createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                    )
                }.sortedWith(compareByDescending<RelationshipMemory> { it.date }.thenByDescending { it.createdAtMillis })
                onResult(items, null)
            }
    }

    fun addMemory(coupleId: String, title: String, note: String, date: String, onResult: (Result<Unit>) -> Unit) {
        val firestore = db
        val uid = auth?.currentUser?.uid
        val parsed = date.toLocalDateOrNull()
        if (firestore == null || uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        if (title.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Memory title cannot be empty.")))
            return
        }
        if (parsed == null || parsed.isAfter(LocalDate.now())) {
            onResult(Result.failure(IllegalArgumentException("Memory date is invalid.")))
            return
        }
        firestore.collection("couples").document(coupleId).collection("memories").add(
            mapOf(
                "title" to title.take(80),
                "note" to note.take(300),
                "date" to parsed.toString(),
                "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
            )
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun deleteMemory(coupleId: String, memoryId: String, onResult: (Result<Unit>) -> Unit) {
        val firestore = db
        if (firestore == null) {
            onResult(Result.failure(IllegalStateException("Firebase is not configured.")))
            return
        }
        firestore.collection("couples").document(coupleId).collection("memories").document(memoryId).delete()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}

data class RelationshipMemory(
    val id: String,
    val title: String,
    val note: String,
    val date: String,
    val createdAtMillis: Long,
)
