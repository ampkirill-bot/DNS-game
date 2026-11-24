package ru.dks.obhod.sync

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.dks.obhod.App
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

private const val SERVICE_ID = "ru.dks.obhod.ANDROID_SYNC_V1"

private enum class TransferMode { NONE, SEND, RECEIVE }

enum class TransferStage {
    IDLE, PREPARING, SEARCHING, CONNECTING, TRANSFERRING, IMPORTING, SUCCESS, ERROR,
}

data class TransferUiState(
    val stage: TransferStage = TransferStage.IDLE,
    val message: String = "",
    val code: String = "",
    val peer: String = "",
    val progress: Float? = null,
)

class NearbyTransferController(private val app: App, context: Context) {
    private val context = context.applicationContext
    private val connections: ConnectionsClient = Nearby.getConnectionsClient(this.context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val packager = SyncPackage(app)
    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    private var mode = TransferMode.NONE
    private var selection = TransferSelection()
    private var packageFile: File? = null
    private var outgoingPayload: Payload? = null
    private var connectedEndpoint: String? = null
    private var filePayloadId: Long? = null
    private var searchTimeout: Job? = null
    private var peerName = ""
    private val incoming = mutableMapOf<Long, Payload>()
    private val processing = mutableSetOf<Long>()
    private val localName = Build.MODEL?.take(32)?.ifBlank { null } ?: "Android"

    fun startSending(selection: TransferSelection) {
        if (!selection.any || _state.value.stage !in setOf(TransferStage.IDLE, TransferStage.ERROR, TransferStage.SUCCESS)) return
        resetConnection()
        mode = TransferMode.SEND
        this.selection = selection
        _state.value = TransferUiState(TransferStage.PREPARING, message = "Подготовка данных и фотографий…")
        scope.launch {
            try {
                packageFile = packager.create(selection)
                _state.value = TransferUiState(TransferStage.SEARCHING, message = "Ожидание получателя…")
                connections.startAdvertising(
                    localName, SERVICE_ID, connectionCallback,
                    AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
                ).addOnFailureListener {
                    if (mode == TransferMode.SEND) fail("Не удалось начать поиск: ${it.userMessage()}")
                }
                startSearchTimeout()
            } catch (e: Throwable) {
                fail("Не удалось подготовить данные: ${e.userMessage()}")
            }
        }
    }

    fun startReceiving(selection: TransferSelection) {
        if (!selection.any || _state.value.stage !in setOf(TransferStage.IDLE, TransferStage.ERROR, TransferStage.SUCCESS)) return
        resetConnection()
        mode = TransferMode.RECEIVE
        this.selection = selection
        _state.value = TransferUiState(TransferStage.SEARCHING, message = "Поиск отправителя рядом…")
        connections.startDiscovery(
            SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
        ).addOnFailureListener {
            if (mode == TransferMode.RECEIVE) fail("Не удалось начать поиск: ${it.userMessage()}")
        }
        startSearchTimeout()
    }

    fun stop() {
        resetConnection()
        mode = TransferMode.NONE
        _state.value = TransferUiState()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (mode == TransferMode.NONE) {
                connections.rejectConnection(endpointId)
                return
            }
            val existing = connectedEndpoint
            if (existing != null && existing != endpointId) {
                connections.rejectConnection(endpointId)
                return
            }
            connectedEndpoint = endpointId
            peerName = info.endpointName
            _state.value = TransferUiState(
                TransferStage.CONNECTING,
                message = "Проверьте одинаковый код на обоих телефонах",
                code = info.authenticationDigits,
                peer = info.endpointName,
            )
            connections.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (mode == TransferMode.NONE) return
            if (!result.status.isSuccess) {
                fail("Не удалось установить защищённое соединение")
                return
            }
            when (mode) {
                TransferMode.SEND -> sendPackage(endpointId)
                TransferMode.RECEIVE -> _state.value = TransferUiState(
                    TransferStage.TRANSFERRING, message = "Телефоны подключены. Ожидание данных…", peer = peerName,
                )
                TransferMode.NONE -> Unit
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (mode != TransferMode.NONE && _state.value.stage != TransferStage.SUCCESS) fail("Соединение разорвано")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (mode != TransferMode.RECEIVE || connectedEndpoint != null) return
            connectedEndpoint = endpointId
            peerName = info.endpointName
            _state.value = TransferUiState(
                TransferStage.CONNECTING, message = "Подключение к ${info.endpointName}…", peer = info.endpointName,
            )
            connections.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener {
                    connectedEndpoint = null
                    fail("Не удалось подключиться: ${it.userMessage()}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            if (connectedEndpoint == null && _state.value.stage == TransferStage.SEARCHING) {
                _state.value = TransferUiState(TransferStage.SEARCHING, message = "Поиск отправителя рядом…")
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (mode == TransferMode.RECEIVE && payload.type == Payload.Type.STREAM) {
                incoming[payload.id] = payload
                // Поток нужно читать сразу: Nearby не сохраняет его в Download.
                handleIncoming(payload.id)
            } else if (mode == TransferMode.RECEIVE && payload.type == Payload.Type.FILE) {
                // Совместимость с предыдущей версией, если она выдала системный дескриптор/URI.
                incoming[payload.id] = payload
            } else if (mode == TransferMode.SEND && payload.type == Payload.Type.BYTES) {
                val message = payload.asBytes()?.toString(Charsets.UTF_8).orEmpty()
                if (message.startsWith("OK:")) {
                    _state.value = TransferUiState(
                        TransferStage.SUCCESS,
                        message = "Данные переданы и приняты. ${message.removePrefix("OK:")}",
                        progress = 1f,
                    )
                    runCatching { outgoingPayload?.close() }
                    outgoingPayload = null
                    packageFile?.delete()
                    connections.disconnectFromEndpoint(endpointId)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // После успешного импорта Nearby может сообщить о завершении служебного
            // подтверждения. Оно не является новым входящим файлом и не должно менять итог.
            if (mode == TransferMode.NONE || _state.value.stage == TransferStage.SUCCESS) return
            if (mode == TransferMode.RECEIVE && _state.value.stage == TransferStage.IMPORTING) return
            val knownIncoming = mode == TransferMode.RECEIVE && incoming.containsKey(update.payloadId)
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS && update.totalBytes > 0) {
                _state.value = _state.value.copy(
                    stage = if (mode == TransferMode.RECEIVE) TransferStage.TRANSFERRING else _state.value.stage,
                    message = if (mode == TransferMode.RECEIVE) "Получение данных и фотографий…" else "Отправка данных и фотографий…",
                    progress = update.bytesTransferred.toFloat() / update.totalBytes.toFloat(),
                )
            }
            if (update.status == PayloadTransferUpdate.Status.FAILURE || update.status == PayloadTransferUpdate.Status.CANCELED) {
                val relevant = when (mode) {
                    TransferMode.RECEIVE -> knownIncoming
                    TransferMode.SEND -> update.payloadId == filePayloadId
                    TransferMode.NONE -> false
                }
                if (relevant) fail("Передача прервана")
                return
            }
            if (update.status != PayloadTransferUpdate.Status.SUCCESS) return
            if (knownIncoming) {
                handleIncoming(update.payloadId)
            } else if (mode == TransferMode.SEND && update.payloadId == filePayloadId) {
                runCatching { outgoingPayload?.close() }
                outgoingPayload = null
                _state.value = _state.value.copy(
                    message = "Файл передан. Ожидание импорта на втором телефоне…", progress = 1f,
                )
            }
        }
    }

    private fun sendPackage(endpointId: String) {
        val file = packageFile
        if (file == null || !file.isFile) {
            fail("Пакет данных не найден")
            return
        }
        try {
            val payload = Payload.fromStream(FileInputStream(file))
            outgoingPayload = payload
            filePayloadId = payload.id
            _state.value = TransferUiState(TransferStage.TRANSFERRING, message = "Отправка данных и фотографий…", progress = null)
            connections.sendPayload(endpointId, payload)
                .addOnFailureListener { fail("Не удалось начать передачу: ${it.userMessage()}") }
        } catch (e: Throwable) {
            fail("Не удалось открыть пакет: ${e.userMessage()}")
        }
    }

    private fun handleIncoming(payloadId: Long) {
        if (_state.value.stage == TransferStage.SUCCESS) {
            incoming.remove(payloadId)?.let { runCatching { it.close() } }
            return
        }
        if (!processing.add(payloadId)) return
        val payload = incoming[payloadId]
        if (payload == null) {
            processing.remove(payloadId)
            if (mode == TransferMode.RECEIVE) fail("Не удалось получить пакет данных")
            return
        }
        _state.value = TransferUiState(TransferStage.IMPORTING, message = "Объединение с данными этого телефона…", progress = 1f)
        scope.launch {
            var copiedFile: File? = null
            try {
                // STREAM читается сразу в приватный cache и вообще не использует Download/.nearby.
                // FILE оставлен только для совместимости с предыдущей версией отправителя.
                val receivedFile = withContext(Dispatchers.IO) { copyIncomingPayload(payload) }
                copiedFile = receivedFile
                val stats = packager.importPackage(receivedFile, selection)
                if (mode != TransferMode.RECEIVE) return@launch
                _state.value = TransferUiState(
                    TransferStage.SUCCESS,
                    message = "Данные приняты. ${stats.message()}",
                    progress = 1f,
                )
                connectedEndpoint?.let { endpoint ->
                    connections.sendPayload(endpoint, Payload.fromBytes("OK:${stats.message()}".toByteArray(Charsets.UTF_8)))
                }
            } catch (e: Throwable) {
                if (mode == TransferMode.RECEIVE) fail("Не удалось импортировать данные: ${e.userMessage()}")
            } finally {
                copiedFile?.delete()
                incoming.remove(payloadId)
                processing.remove(payloadId)
                runCatching { payload.close() }
            }
        }
    }

    private fun copyIncomingPayload(payload: Payload): File {
        val directory = File(context.cacheDir, "transfers").apply { mkdirs() }
        val output = File(directory, "received-${UUID.randomUUID()}.zip")
        try {
            when (payload.type) {
                Payload.Type.STREAM -> {
                    val input = payload.asStream()?.asInputStream()
                        ?: error("Nearby не предоставил поток принятых данных")
                    input.use { FileOutputStream(output).use { outputStream -> it.copyTo(outputStream) } }
                }
                Payload.Type.FILE -> {
                    val source = payload.asFile() ?: error("Nearby не предоставил принятый файл")
                    val descriptor = source.asParcelFileDescriptor()
                    val uri = source.asUri()
                    when {
                        descriptor != null -> ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                            FileOutputStream(output).use { input.copyTo(it) }
                        }
                        uri != null -> {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("Android не предоставил доступ к принятому файлу")
                            input.use { FileOutputStream(output).use { outputStream -> it.copyTo(outputStream) } }
                        }
                        else -> error("Эта версия Android не предоставила безопасный доступ к файлу. Обновите приложение на обоих телефонах")
                    }
                }
                else -> error("Неподдерживаемый формат передачи")
            }
            if (output.length() == 0L) error("Принят пустой пакет данных")
            return output
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    private fun startSearchTimeout() {
        searchTimeout?.cancel()
        searchTimeout = scope.launch {
            delay(120_000)
            if (_state.value.stage == TransferStage.SEARCHING) fail("Телефон не найден. Держите оба приложения открытыми и повторите")
        }
    }

    private fun clearIncoming() {
        incoming.values.forEach { runCatching { it.close() } }
        incoming.clear()
        processing.clear()
    }

    private fun fail(message: String) {
        mode = TransferMode.NONE
        searchTimeout?.cancel()
        connections.stopAdvertising()
        connections.stopDiscovery()
        connections.stopAllEndpoints()
        connectedEndpoint = null
        peerName = ""
        filePayloadId = null
        runCatching { outgoingPayload?.close() }
        outgoingPayload = null
        packageFile?.delete()
        packageFile = null
        clearIncoming()
        _state.value = TransferUiState(TransferStage.ERROR, message = message)
    }

    private fun resetConnection() {
        mode = TransferMode.NONE
        searchTimeout?.cancel()
        connections.stopAdvertising()
        connections.stopDiscovery()
        connections.stopAllEndpoints()
        connectedEndpoint = null
        peerName = ""
        filePayloadId = null
        runCatching { outgoingPayload?.close() }
        outgoingPayload = null
        packageFile?.delete()
        packageFile = null
        clearIncoming()
    }

    private fun Throwable.userMessage(): String = localizedMessage?.take(160) ?: javaClass.simpleName
}
