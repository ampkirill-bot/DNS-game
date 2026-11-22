package ru.dks.obhod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dks.obhod.data.Gpa
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.Plant
import ru.dks.obhod.data.PositionList
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.knownValues
import ru.dks.obhod.data.isCylinderDeviation
import ru.dks.obhod.util.formatNumber
import ru.dks.obhod.report.PdfGroup
import ru.dks.obhod.report.PdfRemark
import ru.dks.obhod.ui.theme.DeviationRed
import ru.dks.obhod.ui.theme.OkGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onTransfer: () -> Unit, onPosition: (String) -> Unit) {
    val app = app()
    val all by remember { app.db.readings().all() }.collectAsStateWithLifecycle(emptyList())
    val openRemarks by remember { app.db.remarks().openCounts() }.collectAsStateWithLifecycle(emptyList())
    val allRemarks by remember { app.db.remarks().all() }.collectAsStateWithLifecycle(emptyList())
    val allRemarkPhotos by remember { app.db.remarks().allPhotos() }.collectAsStateWithLifecycle(emptyList())
    val pdfReport = rememberPdfReportController()
    var showAllCylinders by remember { mutableStateOf(false) }
    var showValanzhinPositions by remember { mutableStateOf(false) }
    var showGlobalReport by remember { mutableStateOf(false) }
    var selectedReportRemarks by remember { mutableStateOf(setOf<Long>()) }
    val byGpa = remember(all) { all.groupBy { it.gpaId } }   // внутри — от новых к старым
    val latestMap = byGpa.mapValues { it.value.first() }
    val devMap = remember(byGpa) { byGpa.mapValues { knownValues(it.value).hasDeviation() } }
    val remarksMap = openRemarks.associate { it.gpaId to it.cnt }
    val globalReportGroups = remember(allRemarks, allRemarkPhotos) {
        val locations = GpaList.all.map { it.id to it.fullTitle } +
            PositionList.all.map { it.id to it.fullTitle }
        locations.mapNotNull { (id, title) ->
            val items = allRemarks.filter { it.gpaId == id }
                .sortedWith(compareBy({ it.resolved }, { -it.timestamp }))
                .map { rm ->
                    PdfRemark(rm, allRemarkPhotos.filter { it.remarkId == rm.id }.map { it.path })
                }
            if (items.isEmpty()) null else PdfGroup(title, items)
        }
    }

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
                actions = {
                    TextButton(
                        onClick = {
                            selectedReportRemarks = globalReportGroups.flatMap { group ->
                                group.remarks.map { it.remark.id }
                            }.toSet()
                            showGlobalReport = true
                        },
                        enabled = !pdfReport.busy && globalReportGroups.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Icon(Icons.Default.PictureAsPdf, "Общий отчёт PDF")
                        Spacer(Modifier.width(5.dp))
                        Text(if (pdfReport.busy) "Создаю…" else "Отчёт PDF")
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { showAllCylinders = true }, modifier = Modifier.weight(1f)) {
                        Text("Все баллоны")
                    }
                    Button(onClick = onTransfer, modifier = Modifier.weight(1f)) {
                        Text("Передача данных")
                    }
                }
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
                    GpaTile(gpa, latestMap[gpa.id], devMap[gpa.id] == true, remarksMap[gpa.id] ?: 0) { onOpen(gpa.id) }
                }
                if (plant == Plant.VALANZHIN) {
                    item(key = "valanzhin-positions") {
                        PositionTile("Позиции Валанжин", 0) { showValanzhinPositions = true }
                    }
                } else {
                    PositionList.byPlant(plant).forEach { position ->
                        item(key = position.id) {
                            PositionTile(position.title, remarksMap[position.id] ?: 0) { onPosition(position.id) }
                        }
                    }
                }
            }
        }
    }

    if (showGlobalReport) {
        val totalRemarks = globalReportGroups.sumOf { it.remarks.size }
        Dialog(
            onDismissRequest = { showGlobalReport = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Общий отчёт PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Снимите галочки с замечаний, которые не нужно включать.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedReportRemarks = globalReportGroups.flatMap { group ->
                                    group.remarks.map { it.remark.id }
                                }.toSet()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Выбрать все") }
                        TextButton(
                            onClick = { selectedReportRemarks = emptySet() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Снять все") }
                    }
                    Text(
                        "Выбрано: ${selectedReportRemarks.size} из $totalRemarks",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        globalReportGroups.forEach { group ->
                            item(key = "header-${group.title}") {
                                val selectedInGroup = group.remarks.count { selectedReportRemarks.contains(it.remark.id) }
                                Text(
                                    "${group.title} · $selectedInGroup/${group.remarks.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(group.remarks.size, key = { index -> group.remarks[index].remark.id }) { index ->
                                val item = group.remarks[index]
                                val remark = item.remark
                                val checked = selectedReportRemarks.contains(remark.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedReportRemarks = if (checked) {
                                                selectedReportRemarks - remark.id
                                            } else {
                                                selectedReportRemarks + remark.id
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            remark.text.ifBlank { "(без описания)" },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "${reportDate(remark.timestamp)} · ${if (remark.resolved) "Устранено" else "Открыто"}" +
                                                if (item.photos.isNotEmpty()) " · фото: ${item.photos.size}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showGlobalReport = false },
                            modifier = Modifier.weight(1f),
                        ) { Text("Отмена") }
                        Button(
                            onClick = {
                                val selectedGroups = globalReportGroups.mapNotNull { group ->
                                    val items = group.remarks.filter { selectedReportRemarks.contains(it.remark.id) }
                                    if (items.isEmpty()) null else PdfGroup(group.title, items)
                                }
                                showGlobalReport = false
                                pdfReport.start(
                                    "Общий отчёт по замечаниям",
                                    selectedGroups,
                                    "Otchet_zamechaniya_obshchiy",
                                )
                            },
                            enabled = selectedReportRemarks.isNotEmpty() && !pdfReport.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Создать PDF (${selectedReportRemarks.size})") }
                    }
                }
            }
        }
    }

    if (showValanzhinPositions) {
        Dialog(
            onDismissRequest = { showValanzhinPositions = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Позиции Валанжин", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    ) {
                        items(PositionList.byPlant(Plant.VALANZHIN), key = { it.id }) { position ->
                            PositionTile(position.title, remarksMap[position.id] ?: 0) {
                                showValanzhinPositions = false
                                onPosition(position.id)
                            }
                        }
                    }
                    TextButton(
                        onClick = { showValanzhinPositions = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }

    if (showAllCylinders) {
        Dialog(
            onDismissRequest = { showAllCylinders = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("Все баллоны", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Валанжин · последние показания", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ГПА", Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
                        for (i in 1..5) Text("№$i", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    GpaList.byPlant(Plant.VALANZHIN).forEach { gpa ->
                        val values = byGpa[gpa.id]?.let { knownValues(it).cyl } ?: List(5) { null }
                        Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${gpa.number}", Modifier.width(48.dp), fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            values.forEach { value ->
                                Text(
                                    value?.let { formatNumber(it) } ?: "—",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCylinderDeviation(value)) DeviationRed else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCylinderDeviation(value)) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showAllCylinders = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Закрыть")
                    }
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
private fun GpaTile(gpa: Gpa, last: Reading?, dev: Boolean, openRemarks: Int, onClick: () -> Unit) {
    val today = last != null && isToday(last.timestamp)
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

@Composable
private fun PositionTile(title: String, openRemarks: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 100.dp)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ПОЗИЦИИ", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("Замечания и фото", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun reportDate(ts: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(ts))
