package cz.jwo.kisctecka.io.net.www

import android.util.Log
import cz.jwo.kisctecka.io.net.lowlevel.CardData
import cz.jwo.kisctecka.io.net.lowlevel.MessageCodeFromReader
import cz.jwo.kisctecka.io.net.lowlevel.MessageCodeToReader
import cz.jwo.kisctecka.io.net.lowlevel.RawDataPacket
import cz.jwo.kisctecka.service.ClientCommandReceiver
import cz.jwo.kisctecka.service.ReaderMode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

const val TAG = "WebServer"

class WebServer(private val commandReceiver: ClientCommandReceiver) {
    private var server: NettyApplicationEngine = embeddedServer(Netty, port = 8080) {
        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 60_000
            maxFrameSize = Int.MAX_VALUE.toLong()
            masking = false
        }

        routing {
            get("/") {
                call.respondText("KIS Čtečka")
            }
            webSocket("/") {
                wsSession?.close(CloseReason(CloseReason.Codes.NORMAL, "another client connected"))
                wsSession = null
                handleWebSocket()
            }
        }
    }

    private var wsSession: DefaultWebSocketServerSession? by Delegates.observable(null) { _, old, new ->
        if (old == null && new != null) {
            commandReceiver.connectionStateChanged(open=true)
        } else if (old != null && new==null) {
            commandReceiver.connectionStateChanged(open=false)
        }
    }

    val isConnectionOpen: Boolean = wsSession!=null

    fun start(wait: Boolean) {
        server.start(wait)
    }

    private suspend fun DefaultWebSocketServerSession.handleWebSocket() {
        Log.d(TAG, "WebSocket opened.")

        wsSession = this

        for (frame in incoming) {
            try {
                Log.d(TAG, "WS: Received frame ${frame.frameType}")

                Log.d(TAG, "WS: Incoming data")
                Log.d(TAG, "WS: Data length: ${frame.buffer.limit()} (should be 35)")
                val packet = RawDataPacket.deserialize(frame.data)
                Log.d(TAG, "WS: Message type: ${packet.type}")

                // Process the packet.
                when (packet.type) {
                    MessageCodeToReader.PING -> processPing(packet)
                    MessageCodeToReader.SINGLE_READ -> processSingleRead()
                    MessageCodeToReader.CONTINUOUS_READ -> processContinuousRead()
                    MessageCodeToReader.PRINT_TEXT -> processPrintText()
                    MessageCodeToReader.STOP -> processStop()
                    MessageCodeToReader.SINGLE_AUTH_ID -> processSingleAuthId()
                    MessageCodeToReader.SINGLE_AUTH_KEY -> processSingleAuthKey()
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Uncaught exception while processing WS frame.", exc)
            }
        }

        Log.d(TAG, "WS: Connection closed")
        wsSession = null
    }


    private suspend fun DefaultWebSocketServerSession.processPing(packet: RawDataPacket) {
        Log.d(TAG, "RX: Ping")
        send(RawDataPacket(MessageCodeFromReader.PONG, packet.data).serialize())
    }

    private fun processSingleAuthKey() {
        Log.d(TAG, "RX: Single Auth Key")
        commandReceiver.switchReaderMode(ReaderMode.AuthUseKey)
    }

    private fun processSingleAuthId() {
        Log.d(TAG, "RX: Single Auth Id")
        commandReceiver.switchReaderMode(ReaderMode.SingleReadAuth)
    }

    private fun processStop() {
        Log.d(TAG, "RX: Stop")
        commandReceiver.switchReaderMode(ReaderMode.Idle)
    }

    private fun processPrintText() {
        Log.d(TAG, "RX: Print Text")
        // TODO
    }

    private fun processContinuousRead() {
        Log.d(TAG, "RX: Continuous Read")
        commandReceiver.switchReaderMode(ReaderMode.ContinuousRead)
    }

    private fun processSingleRead() {
        Log.d(TAG, "RX: Single Read")
        commandReceiver.switchReaderMode(ReaderMode.SingleRead)
    }


    private fun sendPacket(dataPacket: RawDataPacket) {
        CoroutineScope(Dispatchers.IO).launch {
            wsSession?.let { wsSession ->
                Log.d(TAG, "WS: Sending")
                wsSession.send(dataPacket.serialize())
            }
        }
    }

    fun sendCardData(readerMode: ReaderMode, cardData: CardData) {
        // Build the card data struct.
        val data = ByteArray(32) { 0 }
        data[0] = cardData.uid.size.toByte()
        cardData.uid.copyInto(data, 1, 0)
        cardData.content.copyInto(data, 16, 0)

        // Send it.
        Log.d(TAG, "TX: Card Data")
        when (readerMode) {
            ReaderMode.SingleRead -> {
                Log.d(TAG, "    (single)")
                sendPacket(RawDataPacket(MessageCodeFromReader.SINGLE_ID, data))
            }

            ReaderMode.ContinuousRead -> {
                Log.d(TAG, "    (continuous)")
                sendPacket(RawDataPacket(MessageCodeFromReader.AUTO_ID, data))
            }

            ReaderMode.SingleReadAuth -> {
                Log.d(TAG, "    (auth)")
                sendPacket(RawDataPacket(MessageCodeFromReader.SINGLE_ID, data))
            }

            else -> throw AssertionError("Invalid reader mode $readerMode for sending card data.")
        }
    }

    fun stop() {
        Log.i(TAG, "Forcefully stopping the HTTP server.")
        CoroutineScope(Dispatchers.IO).launch {
            server.stop()
        }
    }

}
