package ru.dks.obhod.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.dks.obhod.report.PdfGpaGroup
import ru.dks.obhod.report.RemarkPdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportController internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var busy by mutableStateOf(false)
        private set

    fun start(title: String, groups: List<PdfGpaGroup>, filePrefix: String) {
        if (busy) return
        if (groups.none { it.remarks.isNotEmpty() }) {
            Toast.makeText(context, "Нет замечаний для отчёта", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            busy = true
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = RemarkPdfExporter.export(context, title, groups, "${filePrefix}_$stamp.pdf")
                RemarkPdfExporter.share(context, file, title)
            } catch (t: Throwable) {
                Toast.makeText(context, "Не удалось создать PDF: ${t.message ?: "ошибка"}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberPdfReportController(): PdfReportController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { PdfReportController(context, scope) }
}
