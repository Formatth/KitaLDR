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
import androidx.compose.material3.CircularProgressIndicator
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val CalendarRose = Color(0xFFFF5F78)
private val CalendarRoseSoft = Color(0xFFFFE8ED)
private val CalendarLavender = Color(0xFF8C79F2)
private val CalendarInk = Color(0xFF28242B)
private val CalendarMuted = Color(0xFF817A83)
private val CalendarPale = Color(0xFFFFFBFC)

private val relationshipDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
private val memoryDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))

private data class CalendarMemory(
    val id: String,
    val title: String,
    val note: String,
    val date: LocalDate,
)

@Composable
fun RelationshipCalendarScreen(coupleId: String) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val today = LocalDate.now()
    var relationshipDateText by rememberSaveable(coupleId) { mutableStateOf("") }
    var memories by remember(coupleId) { mutableStateOf<List<CalendarMemory>>(emptyList()) }
    var message by rememberSaveable(coupleId) { mutableStateOf("") }
    var loading by rememberSaveable(coupleId) { mutableStateOf(true) }
    var saving by rememberSaveable(coupleId) { mutableStateOf(false) }
    var showAddMemory by rememberSaveable(coupleId) { mutableStateOf(false) }

    DisposableEffect(coupleId) {
        if (coupleId.isBlank()) {
            loading = false
            onDispose { }
        } else {
            val coupleRef = db.collection("couples").document(coupleId)
            val relationshipListener = coupleRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loading = false
                    message = error.message ?: "Could not load calendar."
                } else {
                    loading = false
                    relationshipDateText = snapshot?.getString("relationshipStartDate").orEmpty()
                }
            }
            val memoryListener = coupleRef.collection("memories")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message = error.message ?: "Could not load memories."
                    } else {
                        memories = snapshot?.documents?.mapNotNull { doc ->
                            val date = doc.getString("date")?.toLocalDateOrNull() ?: return@mapNotNull null
                            CalendarMemory(
                                id = doc.id,
                                title = doc.getString("title").orEmpty(),
                                note = doc.getString("note").orEmpty(),
                                date = date,
                            )
                        }.orEmpty()
                    }
                }
            onDispose {
                relationshipListener.remove()
                memoryListener.remove()
            }
        }
    }

    val relationshipDate = relationshipDateText.toLocalDateOrNull()
    val streak = relationshipDate?.let { if (it <= today) ChronoUnit.DAYS.between(it, today) + 1 else 0L } ?: 0L
    val nextAnniversary = relationshipDate?.let { anniversaryFor(it, today) }
    val countdown = nextAnniversary?.let { ChronoUnit.DAYS.between(today, it) } ?: 0L

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Calendar", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your dates, anniversaries and memories — just for the two of you.", color = CalendarMuted, lineHeight = 20.sp)

        if (loading) {
            CalendarPanel { CircularProgressIndicator(Modifier.size(24.dp), color = CalendarRose, strokeWidth = 2.dp) }
        } else if (relationshipDate == null) {
            CalendarPanel(background = CalendarRoseSoft) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = CalendarRose, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("When did your story begin?", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Set your real relationship date. It won't be generated automatically.", color = CalendarMuted, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                CalendarPrimaryButton(if (saving) "Saving…" else "Set relationship date", !saving && coupleId.isNotBlank()) {
                    showDatePicker(context, today) { selected ->
                        saving = true
                        db.collection("couples").document(coupleId)
                            .update("relationshipStartDate", selected.toString())
                            .addOnSuccessListener {
                                saving = false
                                message = "Relationship date saved"
                                relationshipDateText = selected.toString()
                            }
                            .addOnFailureListener {
                                saving = false
                                message = it.message ?: "Could not save relationship date."
                            }
                    }
                }
            }
        } else {
            CalendarPanel {
                Text("Your relationship", color = CalendarMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(relationshipDate.format(relationshipDateFormatter), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
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
                            Text(nextAnniversary!!.format(relationshipDateFormatter), color = CalendarMuted, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    showDatePicker(context, relationshipDate) { selected ->
                        saving = true
                        db.collection("couples").document(coupleId)
                            .update("relationshipStartDate", selected.toString())
                            .addOnSuccessListener {
                                saving = false
                                message = "Relationship date updated"
                                relationshipDateText = selected.toString()
                            }
                            .addOnFailureListener {
                                saving = false
                                message = it.message ?: "Could not update relationship date."
                            }
                    }
                }, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Text(if (saving) "Saving…" else "Change date")
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, Modifier.fillMaxWidth(), color = CalendarRose, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        CalendarPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Memories", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Little moments worth keeping.", color = CalendarMuted, fontSize = 13.sp)
                }
                Box(
                    Modifier.size(40.dp).background(CalendarRoseSoft, CircleShape).clickable(enabled = !saving) { showAddMemory = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = CalendarRose)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (memories.isEmpty()) {
                CalendarPanel(background = CalendarPale, radius = 17.dp) {
                    Text("No memories yet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Add your first little moment together.", color = CalendarMuted, fontSize = 13.sp)
                }
            } else {
                memories.forEach { memory ->
                    MemoryRow(memory) {
                        db.collection("couples").document(coupleId).collection("memories").document(memory.id)
                            .delete()
                            .addOnFailureListener { message = it.message ?: "Could not delete memory." }
                    }
                    if (memory != memories.last()) Spacer(Modifier.height(9.dp))
                }
            }
        }
    }

    if (showAddMemory) {
        AddMemoryDialog(
            context = context,
            busy = saving,
            onDismiss = { if (!saving) showAddMemory = false },
            onSave = { title, note, date ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    message = "You are not signed in."
                    return@AddMemoryDialog
                }
                saving = true
                val data = hashMapOf<String, Any>(
                    "title" to title,
                    "note" to note,
                    "date" to date,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "createdBy" to uid,
                )
                db.collection("couples").document(coupleId).collection("memories")
                    .add(data)
                    .addOnSuccessListener {
                        saving = false
                        showAddMemory = false
                        message = "Memory saved"
                    }
                    .addOnFailureListener {
                        saving = false
                        message = it.message ?: "Could not save memory."
                    }
            },
        )
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
private fun MemoryRow(memory: CalendarMemory, onDelete: () -> Unit) {
    CalendarPanel(background = CalendarPale, radius = 17.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).background(CalendarRoseSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = CalendarRose, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(memory.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(memory.date.format(memoryDateFormatter), color = CalendarMuted, fontSize = 11.sp)
                if (memory.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(memory.note, color = CalendarInk, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete memory", tint = CalendarMuted, modifier = Modifier.size(20.dp).clickable(onClick = onDelete))
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
                    onClick = { showDatePicker(context, selectedDate) { selected -> dateText = selected.toString() } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(selectedDate.format(memoryDateFormatter))
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
        Column(Modifier.padding(18.dp)) { content() }
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
    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
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

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

@Composable
private fun CalendarMemoryPreview() {}
