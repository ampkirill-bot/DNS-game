package ru.dks.obhod.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.dks.obhod.data.PhotoCircle
import kotlin.math.min
import kotlin.math.roundToInt

private fun photoFitRect(width: Float, height: Float, bitmap: Bitmap): Rect {
    val scale = min(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    return Rect(Offset((width - w) / 2f, (height - h) / 2f), Size(w, h))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAnnotationDialog(path: String, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val app = app()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    var circles by remember(path) { mutableStateOf(emptyList<PhotoCircle>()) }
    var radius by rememberSaveable(path) { mutableFloatStateOf(0.12f) }
    var saving by remember { mutableStateOf(false) }

    if (bitmap == null) {
        onDismiss()
        return
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text("Отметить дефект") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !saving) {
                            Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
                )
            },
            // Отдельная нижняя панель не обрезается областью фотографии.
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Размер круга: ${(radius * 100).roundToInt()}%", color = Color.White)
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 0.03f..0.35f,
                        enabled = !saving,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { circles = circles.dropLast(1) },
                            enabled = circles.isNotEmpty() && !saving,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null)
                            Text("Удалить последний")
                        }
                        Text("Кругов: ${circles.size}", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val saved = app.photos.annotate(path, circles)
                                saving = false
                                if (saved != null) onSaved(saved)
                                else Toast.makeText(ctx, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = circles.isNotEmpty() && !saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.Save, null)
                        Text(if (saving) "Сохранение…" else "Сохранить фото")
                    }
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                Text(
                    "Выберите размер и коснитесь фото. Можно добавить несколько кругов.",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Canvas(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(bitmap) {
                        detectTapGestures { point ->
                            val rect = photoFitRect(size.width.toFloat(), size.height.toFloat(), bitmap)
                            if (rect.contains(point)) {
                                circles = circles + PhotoCircle(
                                    x = ((point.x - rect.left) / rect.width).coerceIn(0f, 1f),
                                    y = ((point.y - rect.top) / rect.height).coerceIn(0f, 1f),
                                    radius = radius,
                                )
                            }
                        }
                    },
                ) {
                    val rect = photoFitRect(size.width, size.height, bitmap)
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                    )
                    val minSide = min(rect.width, rect.height)
                    circles.forEach { c ->
                        drawCircle(
                            color = Color.Red,
                            center = Offset(rect.left + c.x * rect.width, rect.top + c.y * rect.height),
                            radius = c.radius * minSide,
                            style = Stroke(width = (minSide * 0.006f).coerceAtLeast(4f)),
                        )
                    }
                }
            }
        }
    }
}
