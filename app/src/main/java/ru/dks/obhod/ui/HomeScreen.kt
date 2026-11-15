package ru.dks.obhod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dks.obhod.data.Gpa
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.Plant
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.hasDeviation
import ru.dks.obhod.ui.theme.DeviationRed
import ru.dks.obhod.ui.theme.OkGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val app = app()
    val latest by remember { app.db.readings().latestPerGpa() }.collectAsStateWithLifecycle(emptyList())
    val openRemarks by remember { app.db.remarks().openCounts() }.collectAsStateWithLifecycle(emptyList())
    val latestMap = latest.associateBy { it.gpaId }
    val remarksMap = openRemarks.associate { it.gpaId to it.cnt }

    val doneToday = GpaList.all.count { g -> latestMap[g.id]?.let { isToday(it.timestamp) } == true }
    val deviations = GpaList.all.count { g -> latestMap[g.id]?.hasDeviation() == true }
    val remarksTotal = remarksMap.values.sum()
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Обход ДКС", fontWeight = FontWeight.Bold)
                        Text(today.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryCard(doneToday, GpaList.all.size, deviations, remarksTotal)
            }
            for (plant in Plant.entries) {
                val list = GpaList.byPlant(plant)
                val done = list.count { g -> latestMap[g.id]?.let { isToday(it.timestamp) } == true }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(plant.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text("сегодня $done / ${list.size}", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.id }) { gpa ->
                    GpaTile(gpa, latestMap[gpa.id], remarksMap[gpa.id] ?: 0) { onOpen(gpa.id) }
                }
            }
        }
    }
}

fun isToday(ts: Long): Boolean {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = ts }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun SummaryCard(done: Int, total: Int, deviations: Int, remarks: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(" из $total ГПА записано сегодня", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(bottom = 6.dp))
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = OkGreen,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip(Icons.Default.ReportProblem, "Отклонений: $deviations",
                    if (deviations > 0) DeviationRed else MaterialTheme.colorScheme.onPrimaryContainer)
                StatChip(Icons.Default.Schedule, "Замечаний: $remarks",
                    if (remarks > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GpaTile(gpa: Gpa, last: Reading?, openRemarks: Int, onClick: () -> Unit) {
    val today = last != null && isToday(last.timestamp)
    val dev = last?.hasDeviation() == true
    val stripe = when {
        dev -> DeviationRed
        today -> OkGreen
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 100.dp)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ГПА", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${gpa.number}", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    if (today && !dev) Icon(Icons.Default.CheckCircle, "Записано сегодня", tint = OkGreen)
                }
                Text(
                    when {
                        last == null -> "нет записей"
                        today -> "сегодня ${hhmm(last.timestamp)}"
                        else -> ru.dks.obhod.util.formatShort(last.timestamp)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (today) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                )
                if (dev) DeviationBadge()
                if (openRemarks > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                        Spacer(Modifier.width(5.dp))
                        Text("Замечаний: $openRemarks", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun hhmm(ts: Long) = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))
