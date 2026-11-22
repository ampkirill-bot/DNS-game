package ru.dks.obhod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.KnownValues
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.Remark
import ru.dks.obhod.data.cylinders
import ru.dks.obhod.data.engineFilterDiff
import ru.dks.obhod.data.hasDeviation
import ru.dks.obhod.data.isCylinderDeviation
import ru.dks.obhod.data.knownValues
import ru.dks.obhod.data.openRecord
import ru.dks.obhod.report.PdfGpaGroup
import ru.dks.obhod.report.PdfRemark
import ru.dks.obhod.ui.theme.DeviationRed
import ru.dks.obhod.ui.theme.OkGreen
import ru.dks.obhod.util.formatDateTime
import ru.dks.obhod.util.formatNumber
import ru.dks.obhod.util.formatShort
import ru.dks.obhod.util.parseNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Индексы полей формы
private const val F_MSK_OIL = 5
private const val F_MSK_DIFF = 6
private const val F_SGU_LEAK_1 = 7
private const val F_SGU_LEAK_2 = 8
private const val F_ENG_OIL = 9
private const val F_P_BEFORE = 10
private const val F_P_AFTER = 11
private const val F_NOTE = 12
private const val FIELD_COUNT = 13

private val formSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

private fun Reading.toForm(): List<String> =
    cylinders().map { formatNumber(it) } + listOf(
        formatNumber(mskOilLevel), formatNumber(mskFilterDiff),
        formatNumber(sguLeak1), formatNumber(sguLeak2),
        formatNumber(engineOilLevel), formatNumber(pressureBefore), formatNumber(pressureAfter),
        note,
    )

/** Прошлое значение для поля формы (серым в пустой ячейке). */
private fun KnownValues.at(i: Int): Double? = when (i) {
    in 0..4 -> cyl[i]
    F_MSK_OIL -> mskOilLevel
    F_MSK_DIFF -> mskFilterDiff
    F_SGU_LEAK_1 -> sguLeak1
    F_SGU_LEAK_2 -> sguLeak2
    F_ENG_OIL -> engineOilLevel
    F_P_BEFORE -> pressureBefore
    F_P_AFTER -> pressureAfter
    else -> null
}

private fun hhmm(ts: Long) = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaScreen(
    gpaId: String,
    initialTab: Int,
    onBack: () -> Unit,
    onHangar: () -> Unit,
    onRemark: (Long) -> Unit,
) {
    val gpa = remember(gpaId) { GpaList.byId(gpaId) }
    val app = app()
    val dao = app.db.readings()

    val readings by remember(gpaId) { dao.forGpa(gpaId) }.collectAsStateWithLifecycle(null)
    val remarks by remember(gpaId) { app.db.remarks().forGpa(gpaId) }.collectAsStateWithLifecycle(emptyList())
    val remarkPhotos by remember(gpaId) { app.db.remarks().photosForGpa(gpaId) }.collectAsStateWithLifecycle(emptyList())

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var form by rememberSaveable(stateSaver = formSaver) { mutableStateOf(List(FIELD_COUNT) { "" }) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var curId by rememberSaveable { mutableLongStateOf(0L) }       // id текущей записи обхода (0 — ещё нет)
    var curTs by rememberSaveable { mutableLongStateOf(0L) }
    var curSyncId by rememberSaveable { mutableStateOf("") }
    var roundStart by rememberSaveable { mutableLongStateOf(0L) }  // момент нажатия «Новая запись»
    var editVersion by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reading?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val pdfReport = rememberPdfReportController()
    val mutex = remember { Mutex() }

    // Первая загрузка: в ячейки — текущая запись обхода (если моложе часа).
    LaunchedEffect(readings) {
        val list = readings ?: return@LaunchedEffect
        if (!loaded) {
            val o = openRecord(list, roundStart)
            if (o != null) { form = o.toForm(); curId = o.id; curTs = o.timestamp; curSyncId = o.syncId }
            loaded = true
        }
    }

    // Сохранение текущей формы в БД (в фоне приложения, чтобы не прерывалось при уходе с экрана).
    fun commit(snapshot: List<String>) {
        pending = false
        app.scope.launch {
            mutex.withLock {
                val n = snapshot.take(F_NOTE).mapIndexed { i, t -> if (i < 5 && !gpa.hasCylinders) null else parseNumber(t) }
                val note = snapshot[F_NOTE].trim()
                val any = n.any { it != null } || note.isNotEmpty()
                val base = Reading(
                    gpaId = gpaId, timestamp = 0,
                    cyl1 = n[0], cyl2 = n[1], cyl3 = n[2], cyl4 = n[3], cyl5 = n[4],
                    mskOilLevel = n[F_MSK_OIL], mskFilterDiff = n[F_MSK_DIFF],
                    sguLeak1 = n[F_SGU_LEAK_1], sguLeak2 = n[F_SGU_LEAK_2],
                    engineOilLevel = n[F_ENG_OIL], pressureBefore = n[F_P_BEFORE], pressureAfter = n[F_P_AFTER],
                    note = note,
                )
                if (curId != 0L) {
                    if (any) dao.upsert(base.copy(
                        id = curId,
                        timestamp = curTs,
                        syncId = curSyncId.ifBlank { base.syncId },
                        updatedAt = System.currentTimeMillis(),
                    ))
                    else { dao.deleteById(curId); curId = 0; curTs = 0; curSyncId = "" }
                } else if (any) {
                    val ts = System.currentTimeMillis()
                    curSyncId = base.syncId
                    curId = dao.upsert(base.copy(timestamp = ts, updatedAt = ts)); curTs = ts
                }
            }
        }
    }

    // Автосохранение через полсекунды после ввода.
    LaunchedEffect(editVersion) {
        if (editVersion == 0) return@LaunchedEffect
        delay(500)
        commit(form)
    }
    val latestForm by rememberUpdatedState(form)
    val latestPending by rememberUpdatedState(pending)
    DisposableEffect(Unit) {
        onDispose { if (latestPending) commit(latestForm) }
    }

    fun set(i: Int, v: String) {
        if (form[i] == v) return
        form = form.toMutableList().also { it[i] = v }
        pending = true
        editVersion++
    }

    val list = readings ?: emptyList()
    val prev = remember(list, curId) { knownValues(list.filter { it.id != curId }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ГПА-${gpa.number}", fontWeight = FontWeight.Bold)
                        Text(gpa.plant.title, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    FilledTonalButton(onClick = onHangar, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Inventory, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ангар")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (tab == 2) {
                ExtendedFloatingActionButton(
                    onClick = { onRemark(0L) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Замечание") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val openCount = remarks.count { !it.resolved }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Показания") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(if (list.isNotEmpty()) "История (${list.size})" else "История", maxLines = 1) })
                Tab(selected = tab == 2, onClick = { tab = 2 },
                    text = { Text(if (openCount > 0) "Замечания ($openCount)" else "Замечания", maxLines = 1) })
            }
            when (tab) {
                0 -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    pending -> "Сохраняю…"
                                    curId != 0L -> "Запись обхода от ${hhmm(curTs)} · сохраняется автоматически"
                                    prev.timestamp != null -> "Серым — показания от ${formatShort(prev.timestamp)}. Введите новые — сохранятся сами."
                                    else -> "Введите показания — они сохранятся автоматически."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (curId != 0L) {
                                TextButton(onClick = { confirmNew = true }) { Text("Новая запись") }
                            }
                        }
                    }
                    item { ReadingForm(form, prev, gpa.hasCylinders, ::set) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                1 -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (list.isEmpty()) item {
                        Text("Записей пока нет", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp))
                    }
                    items(list, key = { it.id }) { r ->
                        ReadingCard(r, showCylinders = gpa.hasCylinders, isCurrent = r.id == curId,
                            onDelete = { toDelete = r })
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                val items = remarks.map { rm ->
                                    PdfRemark(rm, remarkPhotos.filter { it.remarkId == rm.id }.map { it.path })
                                }
                                pdfReport.start(
                                    title = "Отчёт по замечаниям · ${gpa.fullTitle}",
                                    groups = listOf(PdfGpaGroup(gpa, items)),
                                    filePrefix = "Otchet_zamechaniya_${gpa.id}",
                                )
                            },
                            enabled = remarks.isNotEmpty() && !pdfReport.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (pdfReport.busy) "Создаю PDF…" else "Создать отчёт PDF")
                        }
                    }
                    if (remarks.isEmpty()) item {
                        Text("Замечаний нет. Нажмите «Замечание», чтобы добавить.", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(remarks, key = { it.id }) { rm ->
                        RemarkCard(
                            rm,
                            photos = remarkPhotos.filter { it.remarkId == rm.id }.map { it.path },
                            onClick = { onRemark(rm.id) },
                            onToggle = { v -> app.scope.launch { app.db.remarks().setResolved(rm.id, v) } },
                            onPhoto = { viewPhoto = it },
                        )
                    }
                }
            }
        }
    }

    if (confirmNew) {
        ConfirmDialog(
            text = "Начать новую запись обхода? Текущая останется в истории.",
            confirmText = "Начать",
            onConfirm = {
                confirmNew = false
                if (pending) commit(form)
                roundStart = System.currentTimeMillis() + 1
                form = List(FIELD_COUNT) { "" }; curId = 0; curTs = 0; curSyncId = ""
            },
            onDismiss = { confirmNew = false },
        )
    }
    toDelete?.let { r ->
        ConfirmDialog(
            text = "Удалить запись от ${formatDateTime(r.timestamp)}?",
            onConfirm = {
                toDelete = null
                if (r.id == curId) { form = List(FIELD_COUNT) { "" }; curId = 0; curTs = 0; curSyncId = ""; pending = false }
                app.scope.launch { mutex.withLock { dao.delete(r) } }
            },
            onDismiss = { toDelete = null },
        )
    }
    viewPhoto?.let { PhotoViewer(it, onDismiss = { viewPhoto = null }) }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** Значение для расчётов: введённое сейчас, иначе последнее известное. */
private fun eff(form: List<String>, prev: KnownValues, i: Int): Double? = parseNumber(form[i]) ?: prev.at(i)

@Composable
private fun Field(
    i: Int, label: String, form: List<String>, prev: KnownValues, set: (Int, String) -> Unit,
    modifier: Modifier, neg: Boolean = false, cylinder: Boolean = false,
    autoComma: Boolean = cylinder,
) {
    val dev = cylinder && isCylinderDeviation(eff(form, prev, i))
    NumberField(
        value = form[i],
        onValueChange = { set(i, it) },
        label = label,
        allowNegative = neg,
        autoComma = autoComma,
        isError = dev,
        errorText = if (dev) "ОТКЛОНЕНИЕ" else null,
        placeholder = prev.at(i)?.let { formatNumber(it) },
        modifier = modifier,
    )
}

@Composable
private fun ReadingForm(form: List<String>, prev: KnownValues, hasCylinders: Boolean, set: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasCylinders) {
            Section("Пожарные баллоны", Icons.Default.LocalFireDepartment) {
                val bad = (0 until 5).filter { isCylinderDeviation(eff(form, prev, it)) }
                Text(
                    if (bad.isEmpty()) "Норма: от −2,8 до 2,8"
                    else "Отклонение: баллон " + bad.joinToString(", ") { "№${it + 1}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else DeviationRed,
                    fontWeight = if (bad.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                )
                for (row in listOf(0 to 1, 2 to 3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(row.first, "Баллон ${row.first + 1}", form, prev, set, Modifier.weight(1f), neg = true, cylinder = true)
                        Field(row.second, "Баллон ${row.second + 1}", form, prev, set, Modifier.weight(1f), neg = true, cylinder = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(4, "Баллон 5", form, prev, set, Modifier.weight(1f), neg = true, cylinder = true)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("Компрессор (МСК)", Icons.Default.Opacity) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(F_MSK_OIL, "Уровень масла", form, prev, set, Modifier.weight(1f))
                Field(F_MSK_DIFF, "Перепад фильтр.", form, prev, set, Modifier.weight(1f), neg = true, autoComma = true)
            }
            if (hasCylinders) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(F_SGU_LEAK_1, "Утечки СГУ-1", form, prev, set, Modifier.weight(1f))
                    Field(F_SGU_LEAK_2, "Утечки СГУ-2", form, prev, set, Modifier.weight(1f))
                }
            }
        }
        Section("Двигатель", Icons.Default.Speed) {
            Field(F_ENG_OIL, "Уровень в маслобаке двигателя", form, prev, set, Modifier.fillMaxWidth())
            if (hasCylinders) {
                Text("Давление на фильтрах", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    Field(F_P_BEFORE, "До", form, prev, set, Modifier.weight(1f), autoComma = true)
                    Field(F_P_AFTER, "После", form, prev, set, Modifier.weight(1f), autoComma = true)
                    val b = eff(form, prev, F_P_BEFORE)
                    val a = eff(form, prev, F_P_AFTER)
                    val diff = if (a != null && b != null) b - a else null
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Перепад", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            if (diff != null) formatNumber(diff) else "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        Section("Примечание", Icons.Default.EditNote) {
            OutlinedTextField(
                value = form[F_NOTE],
                onValueChange = { set(F_NOTE, it) },
                placeholder = { Text("Необязательно") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingCard(r: Reading, showCylinders: Boolean, isCurrent: Boolean, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (r.hasDeviation()) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(r.timestamp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (isCurrent) {
                    Text("текущая", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (r.hasDeviation()) DeviationBadge()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить") }
            }
            if (showCylinders) {
                Text("Баллоны:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.cylinders().forEachIndexed { i, v ->
                        val dev = isCylinderDeviation(v)
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (dev) DeviationRed else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "№${i + 1}: ${dash(v)}",
                                color = if (dev) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (dev) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
            }
            Spacer(Modifier.height(4.dp))
            Text("МСК: уровень ${dash(r.mskOilLevel)}, перепад на фильтрах ${dash(r.mskFilterDiff)}")
            if (showCylinders) {
                Text("Утечки СГУ: №1 ${dash(r.sguLeak1)}, №2 ${dash(r.sguLeak2)}")
            }
            Text("Двигатель: уровень ${dash(r.engineOilLevel)}")
            if (showCylinders) {
                Text(
                    "Фильтры: до ${dash(r.pressureBefore)} / после ${dash(r.pressureAfter)} → перепад ${dash(r.engineFilterDiff())}",
                    fontWeight = FontWeight.Medium,
                )
            }
            if (r.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Примечание: ${r.note}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun dash(d: Double?): String = if (d == null) "—" else formatNumber(d)

@Composable
internal fun RemarkCard(
    rm: Remark,
    photos: List<String>,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPhoto: (String) -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (rm.resolved) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(rm.timestamp), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    if (rm.resolved) "Устранено" else "Открыто",
                    color = if (rm.resolved) OkGreen else DeviationRed,
                    fontWeight = FontWeight.Bold,
                )
                Checkbox(checked = rm.resolved, onCheckedChange = onToggle)
            }
            Text(rm.text.ifBlank { "(без описания)" }, style = MaterialTheme.typography.bodyLarge)
            if (photos.isNotEmpty()) ThumbRow(photos, onPhoto)
        }
    }
}
