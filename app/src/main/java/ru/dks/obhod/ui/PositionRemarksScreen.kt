package ru.dks.obhod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.dks.obhod.data.PositionList
import ru.dks.obhod.report.PdfGroup
import ru.dks.obhod.report.PdfRemark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionRemarksScreen(positionId: String, onBack: () -> Unit, onRemark: (Long) -> Unit) {
    val position = remember(positionId) { PositionList.byId(positionId) }
    val app = app()
    val remarks by remember(positionId) { app.db.remarks().forGpa(positionId) }
        .collectAsStateWithLifecycle(emptyList())
    val remarkPhotos by remember(positionId) { app.db.remarks().photosForGpa(positionId) }
        .collectAsStateWithLifecycle(emptyList())
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val pdfReport = rememberPdfReportController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(position.title, fontWeight = FontWeight.Bold)
                        Text(position.plant.title, style = MaterialTheme.typography.bodySmall)
                    }
                },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onRemark(0L) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Замечание") },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            item {
                FilledTonalButton(
                    onClick = {
                        val items = remarks.map { rm ->
                            PdfRemark(rm, remarkPhotos.filter { it.remarkId == rm.id }.map { it.path })
                        }
                        pdfReport.start(
                            title = "Отчёт по замечаниям · ${position.fullTitle}",
                            groups = listOf(PdfGroup(position.fullTitle, items)),
                            filePrefix = "Otchet_zamechaniya_${position.id}",
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
            items(remarks, key = { it.id }) { remark ->
                RemarkCard(
                    remark,
                    photos = remarkPhotos.filter { it.remarkId == remark.id }.map { it.path },
                    onClick = { onRemark(remark.id) },
                    onToggle = { resolved -> app.scope.launch { app.db.remarks().setResolved(remark.id, resolved) } },
                    onPhoto = { viewPhoto = it },
                )
            }
        }
    }

    viewPhoto?.let { PhotoViewer(it, onDismiss = { viewPhoto = null }) }
}
