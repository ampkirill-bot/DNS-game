package ru.dks.obhod.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.Remark
import ru.dks.obhod.data.cylinders
import ru.dks.obhod.data.engineFilterDiff
import ru.dks.obhod.data.hasDeviation
import ru.dks.obhod.data.isCylinderDeviation
import ru.dks.obhod.ui.theme.DeviationRed
import ru.dks.obhod.ui.theme.OkGreen
import ru.dks.obhod.util.formatDateTime
import ru.dks.obhod.util.formatNumber
import ru.dks.obhod.util.parseNumber

// Индексы полей формы
private const val F_MSK_OIL = 5
private const val F_MSK_DIFF = 6
private const val F_ENG_OIL = 7
private const val F_P_BEFORE = 8
private const val F_P_AFTER = 9
private const val F_NOTE = 10
private const val FIELD_COUNT = 11

private val formSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

private fun Reading.toForm(): List<String> =
    cylinders().map { formatNumber(it) } + listOf(
        formatNumber(mskOilLevel), formatNumber(mskFilterDiff),
        formatNumber(engineOilLevel), formatNumber(pressureBefore), formatNumber(pressureAfter),
        note,
    )

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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val readings by remember(gpaId) { app.db.readings().forGpa(gpaId) }.collectAsStateWithLifecycle(emptyList())
    val remarks by remember(gpaId) { app.db.remarks().forGpa(gpaId) }.collectAsStateWithLifecycle(emptyList())
    val remarkPhotos by remember(gpaId) { app.db.remarks().photosForGpa(gpaId) }.collectAsStateWithLifecycle(emptyList())

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var form by rememberSaveable(stateSaver = formSaver) { mutableStateOf(List(FIELD_COUNT) { "" }) }
    var editingId by rememberSaveable { mutableLongStateOf(0L) }
    var editingTs by rememberSaveable { mutableLongStateOf(0L) }
    var confirmLeave by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reading?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val dirty = form.any { it.isNotBlank() }
    fun set(i: Int, v: String) { form = form.toMutableList().also { it[i] = v } }
    fun clearForm() { form = List(FIELD_COUNT) { "" }; editingId = 0; editingTs = 0 }

    fun save() {
        val nums = form.take(F_NOTE).mapIndexed { i, t -> if (i < 5 && !gpa.hasCylinders) null else parseNumber(t) }
        if (nums.all { it == null } && form[F_NOTE].isBlank()) {
            Toast.makeText(ctx, "Нет данных для сохранения", Toast.LENGTH_SHORT).show()
            return
        }
        val r = Reading(
            id = editingId,
            gpaId = gpaId,
            timestamp = if (editingId != 0L) editingTs else System.currentTimeMillis(),
            cyl1 = nums[0], cyl2 = nums[1], cyl3 = nums[2], cyl4 = nums[3], cyl5 = nums[4],
            mskOilLevel = nums[F_MSK_OIL], mskFilterDiff = nums[F_MSK_DIFF],
            engineOilLevel = nums[F_ENG_OIL], pressureBefore = nums[F_P_BEFORE], pressureAfter = nums[F_P_AFTER],
            note = form[F_NOTE].trim(),
        )
        app.scope.launch { app.db.readings().upsert(r) }
        Toast.makeText(ctx, if (editingId != 0L) "Запись обновлена" else "Сохранено", Toast.LENGTH_SHORT).show()
        clearForm()
    }

    BackHandler(enabled = dirty && tab == 0) { confirmLeave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gpa.fullTitle) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmLeave = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
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
        bottomBar = {
            if (tab == 0) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (editingId != 0L || dirty) {
                            OutlinedButton(onClick = ::clearForm, modifier = Modifier.height(54.dp)) {
                                Text(if (editingId != 0L) "Отмена" else "Очистить")
                            }
                        }
                        Button(onClick = ::save, modifier = Modifier.weight(1f).height(54.dp)) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (editingId != 0L) "Сохранить изменения" else "Сохранить запись",
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
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
                Tab(
                    selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(if (openCount > 0) "Замечания ($openCount)" else "Замечания") },
                )
            }
            if (tab == 0) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (editingId != 0L) item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Редактирование записи от ${formatDateTime(editingTs)}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    item { ReadingForm(form, gpa.hasCylinders, ::set) }
                    item {
                        Text(
                            "История записей",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (readings.isEmpty()) Text("Записей пока нет", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(readings, key = { it.id }) { r ->
                        ReadingCard(
                            r,
                            showCylinders = gpa.hasCylinders,
                            highlighted = r.id == editingId,
                            onEdit = {
                                form = r.toForm(); editingId = r.id; editingTs = r.timestamp
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            onDelete = { toDelete = r },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
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

    if (confirmLeave) {
        ConfirmDialog(
            text = "Есть несохранённые показания. Выйти без сохранения?",
            confirmText = "Выйти",
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
    toDelete?.let { r ->
        ConfirmDialog(
            text = "Удалить запись от ${formatDateTime(r.timestamp)}?",
            onConfirm = {
                toDelete = null
                if (editingId == r.id) clearForm()
                app.scope.launch { app.db.readings().delete(r) }
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

@Composable
private fun CylinderField(i: Int, form: List<String>, set: (Int, String) -> Unit, modifier: Modifier) {
    val dev = isCylinderDeviation(parseNumber(form[i]))
    NumberField(
        value = form[i],
        onValueChange = { set(i, it) },
        label = "Баллон ${i + 1}",
        allowNegative = true,
        autoComma = true,
        isError = dev,
        errorText = if (dev) "ОТКЛОНЕНИЕ" else null,
        modifier = modifier,
    )
}

@Composable
private fun ReadingForm(form: List<String>, hasCylinders: Boolean, set: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasCylinders) {
            Section("Пожарные баллоны", Icons.Default.LocalFireDepartment) {
                val bad = (0 until 5).filter { isCylinderDeviation(parseNumber(form[it])) }
                Text(
                    if (bad.isEmpty()) "Норма: от −2,8 до 2,8"
                    else "Отклонение: баллон " + bad.joinToString(", ") { "№${it + 1}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else DeviationRed,
                    fontWeight = if (bad.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                )
                for (row in listOf(0 to 1, 2 to 3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CylinderField(row.first, form, set, Modifier.weight(1f))
                        CylinderField(row.second, form, set, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CylinderField(4, form, set, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("Компрессор (МСК)", Icons.Default.Opacity) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form[F_MSK_OIL], { set(F_MSK_OIL, it) }, "Уровень масла",
                    modifier = Modifier.weight(1f))
                NumberField(form[F_MSK_DIFF], { set(F_MSK_DIFF, it) }, "Перепад фильтр.",
                    allowNegative = true, modifier = Modifier.weight(1f))
            }
        }
        Section("Двигатель", Icons.Default.Speed) {
            NumberField(form[F_ENG_OIL], { set(F_ENG_OIL, it) }, "Уровень в маслобаке двигателя",
                modifier = Modifier.fillMaxWidth())
            Text("Давление на фильтрах", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(form[F_P_BEFORE], { set(F_P_BEFORE, it) }, "До", modifier = Modifier.weight(1f))
                NumberField(form[F_P_AFTER], { set(F_P_AFTER, it) }, "После", modifier = Modifier.weight(1f))
                val b = parseNumber(form[F_P_BEFORE])
                val a = parseNumber(form[F_P_AFTER])
                val diff = if (a != null && b != null) b - a else null
                Column(
                    Modifier.weight(1f).padding(top = 8.dp)
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
private fun ReadingCard(
    r: Reading,
    showCylinders: Boolean,
    highlighted: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier,
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
                if (r.hasDeviation()) DeviationBadge()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Изменить") }
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
                            "№${i + 1}: ${if (v == null) "—" else formatNumber(v)}",
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
            Text("МСК: уровень ${v(r.mskOilLevel)}, перепад на фильтрах ${v(r.mskFilterDiff)}")
            Text("Двигатель: уровень ${v(r.engineOilLevel)}")
            Text(
                "Фильтры: до ${v(r.pressureBefore)} / после ${v(r.pressureAfter)} → перепад ${v(r.engineFilterDiff())}",
                fontWeight = FontWeight.Medium,
            )
            if (r.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Примечание: ${r.note}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun v(d: Double?): String = if (d == null) "—" else formatNumber(d)

@Composable
private fun RemarkCard(
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
