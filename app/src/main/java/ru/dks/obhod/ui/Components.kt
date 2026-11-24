package ru.dks.obhod.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
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
private val singleDigit = Regex("^-?\\d$")

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorText: String? = null,
    autoComma: Boolean = false,
    placeholder: String? = null,
) {
    // Храним позицию курсора; при внешнем изменении текста курсор ставится в конец.
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    // Подпись над полем (а не внутри), чтобы серое прошлое значение было видно всегда.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    OutlinedTextField(
        value = shown,
        onValueChange = { new ->
            val filtered = filterNumberInput(new.text, allowNegative) ?: return@OutlinedTextField
            var text = filtered
            var sel = new.selection
            // Автозапятая: после первой цифры сразу ставится «,» (только при вводе, не при стирании).
            if (autoComma && text.length > shown.text.length && singleDigit.matches(text)) {
                text += ","
                sel = TextRange(text.length)
            }
            tfv = new.copy(text = text, selection = sel)
            onValueChange(text)
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
        } else null,
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
        modifier = Modifier.fillMaxWidth(),
    )
    }
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

/** Полноэкранный просмотр: свайп, двойное касание и масштабирование пальцами. */
@Composable
fun PhotoViewer(
    path: String,
    paths: List<String> = listOf(path),
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    caption: String? = null,
    onCaptionChange: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    val galleryPaths = paths.ifEmpty { listOf(path) }
    val initialPage = remember(path, galleryPaths) { galleryPaths.indexOf(path).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { galleryPaths.size }
    val currentPath = galleryPaths.getOrElse(pagerState.currentPage) { path }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var captionText by remember(currentPath, caption) { mutableStateOf(caption ?: "") }

    LaunchedEffect(currentPath) {
        scale = 1f
        offset = Offset.Zero
        onNavigate?.invoke(currentPath)
    }

    fun saveCaption() {
        if (onCaptionChange != null && captionText != (caption ?: "")) onCaptionChange(captionText)
    }
    fun savePhoto() {
        if (saving) return
        saving = true
        scope.launch {
            val saved = app.photos.saveToGallery(currentPath)
            saving = false
            Toast.makeText(
                ctx,
                if (saved) "Фото сохранено в галерею" else "Не удалось сохранить фото",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) savePhoto()
        else Toast.makeText(ctx, "Разрешите доступ к хранилищу, чтобы сохранить фото", Toast.LENGTH_SHORT).show()
    }
    fun requestSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) savePhoto() else storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    Dialog(
        onDismissRequest = { saveCaption(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale == 1f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pagePath = galleryPaths[page]
                AsyncImage(
                    model = File(pagePath),
                    contentDescription = "Фото ${page + 1} из ${galleryPaths.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pagePath) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2.5f
                                offset = Offset.Zero
                            })
                        }
                        .pointerInput(pagePath) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed > 1 || scale > 1f) {
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                        offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y,
                        ),
                )
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { saveCaption(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
                }
                if (galleryPaths.size > 1) {
                    Text("${pagerState.currentPage + 1} / ${galleryPaths.size}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Отметить дефект", tint = Color.White)
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Удалить", tint = Color.White)
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding().imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = ::requestSave,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saving) "Сохраняю…" else "Сохранить на телефон")
                }
                if (onCaptionChange != null) {
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
