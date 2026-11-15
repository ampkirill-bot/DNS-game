package ru.dks.obhod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dks.obhod.data.Gpa
import ru.dks.obhod.data.GpaList
import ru.dks.obhod.data.Plant
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.hasDeviation
import ru.dks.obhod.ui.theme.OkGreen
import ru.dks.obhod.util.formatShort
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val app = app()
    val latest by remember { app.db.readings().latestPerGpa() }.collectAsStateWithLifecycle(emptyList())
    val openRemarks by remember { app.db.remarks().openCounts() }.collectAsStateWithLifecycle(emptyList())
    val latestMap = latest.associateBy { it.gpaId }
    val remarksMap = openRemarks.associate { it.gpaId to it.cnt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обход ДКС") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            for (plant in Plant.entries) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        plant.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(GpaList.byPlant(plant), key = { it.id }) { gpa ->
                    GpaTile(gpa, latestMap[gpa.id], remarksMap[gpa.id] ?: 0) { onOpen(gpa.id) }
                }
            }
        }
    }
}

private fun isToday(ts: Long): Boolean {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = ts }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun GpaTile(gpa: Gpa, last: Reading?, openRemarks: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(gpa.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (last == null) {
                Text("нет записей", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    formatShort(last.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isToday(last.timestamp)) OkGreen else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = if (isToday(last.timestamp)) FontWeight.Bold else FontWeight.Normal,
                )
                if (last.hasDeviation()) DeviationBadge()
            }
            if (openRemarks > 0) {
                Row {
                    Text(
                        "Замечаний: $openRemarks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
