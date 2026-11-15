package ru.dks.obhod.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.dks.obhod.App
import ru.dks.obhod.ui.theme.DeviationRed
import ru.dks.obhod.util.filterNumberInput
import ru.dks.obhod.util.toggleSign
import java.io.File

@Composable
fun app(): App = LocalContext.current.applicationContext as App

/**
 * Поле ввода числа: только цифры, запятая и минус. Кнопка «±» меняет знак
 * (на многих цифровых клавиатурах нет минуса).
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> filterNumberInput(raw, allowNegative)?.let(onValueChange) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        isError = isError,
        supportingText = if (errorText != null) {
            { Text(errorText, color = DeviationRed, fontWeight = FontWeight.Bold) }
        } else null,
        trailingIcon = if (allowNegative) {
            {
                TextButton(onClick = { onValueChange(toggleSign(value)) }) {
                    Text("±", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.titleMedium,
        colors = if (isError) OutlinedTextFieldDefaults.colors(
            errorContainerColor = DeviationRed.copy(alpha = 0.10f),
        ) else OutlinedTextFieldDefaults.colors(),
        modifier = modifier,
    )
}

@Composable
fun DeviationBadge(modifier: Modifier = Modifier, text: String = "ОТКЛОНЕНИЕ") {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DeviationRed)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Кнопки «Камера» и «Галерея». Результат — пути к файлам во внутренней памяти. */
@Composable
fun PhotoButtons(onAdded: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath ?: return@rememberLauncherForActivityResult
        pendingPath = null
        val f = File(path)
        if (ok && f.exists() && f.length() > 0) {
            scope.launch {
                app.photos.normalize(f)
                onAdded(listOf(path))
            }
        } else {
            f.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val paths = uris.mapNotNull { app.photos.importFromUri(it) }
            if (paths.isNotEmpty()) onAdded(paths)
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = {
                val f = app.photos.newFile()
                pendingPath = f.absolutePath
                try {
                    camera.launch(app.photos.uriFor(f))
                } catch (e: Exception) {
                    pendingPath = null
                    Toast.makeText(ctx, "Камера недоступна", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Default.PhotoCamera, null)
            Spacer(Modifier.width(6.dp))
            Text("Фото")
        }
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.width(6.dp))
            Text("Галерея")
        }
    }
}

@Composable
fun Thumb(path: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

@Composable
fun ThumbRow(paths: List<String>, onClick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(paths) { p -> Thumb(p, Modifier.size(72.dp)) { onClick(p) } }
    }
}

/** Полноэкранный просмотр фото с масштабированием пальцами. */
@Composable
fun PhotoViewer(
    path: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    caption: String? = null,
    onCaptionChange: ((String) -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var captionText by remember(path) { mutableStateOf(caption ?: "") }

    Dialog(
        onDismissRequest = {
            if (onCaptionChange != null && captionText != (caption ?: "")) onCaptionChange(captionText)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                    ),
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {
                    if (onCaptionChange != null && captionText != (caption ?: "")) onCaptionChange(captionText)
                    onDismiss()
                }) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Удалить", tint = Color.White)
                    }
                }
            }
            if (onCaptionChange != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding().imePadding().padding(12.dp),
                ) {
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        label = { Text("Подпись к фото") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White, unfocusedLabelColor = Color.LightGray,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            text = "Удалить это фото?",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmText: String = "Удалить") {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = DeviationRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
