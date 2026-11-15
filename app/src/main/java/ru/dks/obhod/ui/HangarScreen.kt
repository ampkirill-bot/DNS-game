package ru.dks.obhod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.HangarNote
import ru.dks.obhod.data.HangarPhoto
import ru.dks.obhod.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(gpaId: String, onBack: () -> Unit) {
    val gpa = remember(gpaId) { GpaList.byId(gpaId) }
    val app = app()
    val photos by remember(gpaId) { app.db.hangar().photos(gpaId) }.collectAsStateWithLifecycle(emptyList())

    // Примечания: загружаем один раз, сохраняем автоматически.
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gpaId) { note = app.db.hangar().note(gpaId)?.text ?: "" }
    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        delay(700)
        app.db.hangar().saveNote(HangarNote(gpaId, n))
    }
    val latestNote by rememberUpdatedState(note)
    DisposableEffect(gpaId) {
        onDispose {
            latestNote?.let { n -> app.scope.launch { app.db.hangar().saveNote(HangarNote(gpaId, n)) } }
        }
    }

    var viewing by remember { mutableStateOf<HangarPhoto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ангар · ${gpa.fullTitle}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = note ?: "",
                        onValueChange = { note = it },
                        enabled = note != null,
                        label = { Text("Примечания (что лежит в ангаре)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PhotoButtons(onAdded = { paths ->
                        app.scope.launch {
                            val now = System.currentTimeMillis()
                            paths.forEachIndexed { i, p ->
                                app.db.hangar().insertPhoto(HangarPhoto(gpaId = gpaId, path = p, timestamp = now + i))
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (photos.isEmpty()) "Фото пока нет" else "Фото: ${photos.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(photos, key = { it.id }) { p ->
                Column {
                    Thumb(p.path, Modifier.fillMaxWidth()) { viewing = p }
                    Text(
                        p.caption.ifBlank { formatDateTime(p.timestamp) },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    viewing?.let { p ->
        PhotoViewer(
            path = p.path,
            onDismiss = { viewing = null },
            onDelete = {
                viewing = null
                app.scope.launch {
                    app.db.hangar().deletePhoto(p)
                    app.photos.delete(p.path)
                }
            },
            caption = p.caption,
            onCaptionChange = { c -> app.scope.launch { app.db.hangar().updatePhoto(p.copy(caption = c)) } },
        )
    }
}
