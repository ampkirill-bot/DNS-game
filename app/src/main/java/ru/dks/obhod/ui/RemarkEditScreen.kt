package ru.dks.obhod.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.Remark
import ru.dks.obhod.util.formatDateTime

private val pathsSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarkEditScreen(gpaId: String, remarkId: Long, onBack: () -> Unit) {
    val gpa = remember(gpaId) { GpaList.byId(gpaId) }
    val app = app()
    val ctx = LocalContext.current

    var loaded by rememberSaveable { mutableStateOf(remarkId == 0L) }
    var text by rememberSaveable { mutableStateOf("") }
    var resolved by rememberSaveable { mutableStateOf(false) }
    var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var paths by rememberSaveable(stateSaver = pathsSaver) { mutableStateOf(emptyList<String>()) }
    // Файлы, добавленные в этом сеансе (удаляются, если выйти без сохранения)
    var added by rememberSaveable(stateSaver = pathsSaver) { mutableStateOf(emptyList<String>()) }
    // Существующие файлы, помеченные на удаление (удаляются после сохранения)
    var removed by rememberSaveable(stateSaver = pathsSaver) { mutableStateOf(emptyList<String>()) }
    var original by remember { mutableStateOf<Remark?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(remarkId) {
        if (remarkId != 0L) {
            val r = app.db.remarks().get(remarkId)
            original = r
            if (!loaded && r != null) {
                text = r.text; resolved = r.resolved; timestamp = r.timestamp
                paths = app.db.remarks().photos(remarkId).map { it.path }
                loaded = true
            }
        }
    }

    val dirty = added.isNotEmpty() || removed.isNotEmpty() ||
        (remarkId == 0L && text.isNotBlank()) ||
        (original != null && (original!!.text != text || original!!.resolved != resolved))

    fun discardAndLeave() {
        added.forEach { app.photos.delete(it) }
        onBack()
    }

    fun save() {
        if (text.isBlank() && paths.isEmpty()) {
            Toast.makeText(ctx, "Добавьте описание или фото", Toast.LENGTH_SHORT).show()
            return
        }
        val r = Remark(id = remarkId, gpaId = gpaId, timestamp = timestamp, text = text.trim(), resolved = resolved)
        val finalPaths = paths
        val toRemove = removed
        app.scope.launch {
            app.db.remarks().save(r, finalPaths)
            toRemove.forEach { app.photos.delete(it) }
        }
        Toast.makeText(ctx, "Замечание сохранено", Toast.LENGTH_SHORT).show()
        onBack()
    }

    BackHandler { if (dirty) confirmLeave = true else discardAndLeave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (remarkId == 0L) "Новое замечание" else "Замечание") },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmLeave = true else discardAndLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (remarkId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${gpa.fullTitle} · ${formatDateTime(timestamp)}", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Описание замечания") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = resolved, onCheckedChange = { resolved = it })
                Text("Устранено")
            }
            PhotoButtons(onAdded = { new ->
                paths = paths + new
                added = added + new
            }, modifier = Modifier.fillMaxWidth())
            if (paths.isNotEmpty()) {
                Text("Фото: ${paths.size}", style = MaterialTheme.typography.titleSmall)
                ThumbRow(paths) { viewing = it }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить")
            }
        }
    }

    viewing?.let { p ->
        PhotoViewer(
            path = p,
            onDismiss = { viewing = null },
            onDelete = {
                viewing = null
                paths = paths - p
                if (p in added) { added = added - p; app.photos.delete(p) } else removed = removed + p
            },
            onEdit = { viewing = null; editing = p },
        )
    }
    editing?.let { p ->
        PhotoAnnotationDialog(p, onDismiss = { editing = null }, onSaved = { newPath ->
            editing = null
            paths = paths.map { if (it == p) newPath else it }
            if (p in added) {
                added = added - p + newPath
                app.photos.delete(p)
            } else {
                removed = removed + p
                added = added + newPath
            }
        })
    }
    if (confirmLeave) {
        ConfirmDialog(
            text = "Изменения не сохранены. Выйти без сохранения?",
            confirmText = "Выйти",
            onConfirm = { confirmLeave = false; discardAndLeave() },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = "Удалить это замечание вместе с фото?",
            onConfirm = {
                confirmDelete = false
                val r = original
                val all = paths + removed
                if (r != null) app.scope.launch {
                    app.db.remarks().delete(r)
                    all.forEach { app.photos.delete(it) }
                }
                added.forEach { app.photos.delete(it) }
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
