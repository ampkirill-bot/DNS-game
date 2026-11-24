package ru.dks.obhod.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dks.obhod.sync.NearbyTransferController
import ru.dks.obhod.sync.TransferSelection
import ru.dks.obhod.sync.TransferStage

private enum class TransferUiMode { SEND, RECEIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(onBack: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val controller = remember { NearbyTransferController(app, context) }
    val state by controller.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(TransferUiMode.SEND) }
    var readings by rememberSaveable { mutableStateOf(true) }
    var hangar by rememberSaveable { mutableStateOf(true) }
    var remarks by rememberSaveable { mutableStateOf(true) }
    var permissionError by rememberSaveable { mutableStateOf("") }

    val active = state.stage in setOf(
        TransferStage.PREPARING, TransferStage.SEARCHING, TransferStage.CONNECTING,
        TransferStage.TRANSFERRING, TransferStage.IMPORTING,
    )

    fun currentSelection() = TransferSelection(readings, hangar, remarks)

    fun startNow() {
        permissionError = ""
        if (mode == TransferUiMode.SEND) controller.startSending(currentSelection())
        else controller.startReceiving(currentSelection())
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) startNow()
        else permissionError = "Для прямой передачи нужно разрешить поиск устройств рядом"
    }

    fun requestStart() {
        val permissions = nearbyPermissions()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startNow() else permissionLauncher.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) { onDispose { controller.close() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Передача данных") },
                navigationIcon = {
                    IconButton(onClick = { controller.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Откройте этот экран на обоих Android-телефонах. Они найдут друг друга рядом без интернета.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == TransferUiMode.SEND) {
                    Button(onClick = {}, enabled = !active, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Upload, null); Text("Отправить")
                    }
                } else {
                    OutlinedButton(
                        onClick = { controller.stop(); mode = TransferUiMode.SEND },
                        enabled = !active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Upload, null); Text("Отправить")
                    }
                }
                if (mode == TransferUiMode.RECEIVE) {
                    Button(onClick = {}, enabled = !active, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, null); Text("Принять")
                    }
                } else {
                    OutlinedButton(
                        onClick = { controller.stop(); mode = TransferUiMode.RECEIVE },
                        enabled = !active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, null); Text("Принять")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        if (mode == TransferUiMode.SEND) "Что передать" else "Что разрешить принять",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    SelectionRow("Показания", readings, !active) { readings = it }
                    SelectionRow("Ангары: примечания и фото", hangar, !active) { hangar = it }
                    SelectionRow("Замечания, включая фото", remarks, !active) { remarks = it }
                }
            }

            if (mode == TransferUiMode.RECEIVE) {
                Text(
                    "Новые записи будут добавлены. Более новые данные на этом телефоне не будут заменены старыми.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (permissionError.isNotEmpty()) {
                Text(permissionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.stage == TransferStage.IDLE) {
                Button(
                    onClick = ::requestStart,
                    enabled = currentSelection().any,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Default.Sync, null)
                    Text(if (mode == TransferUiMode.SEND) "Найти получателя" else "Найти отправителя")
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.stage) {
                            TransferStage.ERROR -> MaterialTheme.colorScheme.errorContainer
                            TransferStage.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        if (state.code.isNotEmpty()) {
                            Text(
                                state.code,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (active) {
                            val progress = state.progress
                            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            if (progress != null) Text("${(progress * 100).toInt()}%")
                        }
                        if (active) {
                            OutlinedButton(onClick = { controller.stop() }) { Text("Отменить") }
                        } else {
                            Button(onClick = { controller.stop() }) { Text("Новая передача") }
                        }
                    }
                }
            }
            Text(
                "Не закрывайте приложение до завершения. Данные передаются напрямую, без сервера и облака.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun nearbyPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= 33 -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )
    Build.VERSION.SDK_INT >= 31 -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
