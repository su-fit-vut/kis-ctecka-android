package cz.jwo.kisctecka.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.IBinder
import android.util.Log
import cz.jwo.kisctecka.io.net.www.WebServer
import cz.jwo.kisctecka.io.nfc.TagReader
import cz.jwo.kisctecka.io.nfc.TagReadingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.properties.Delegates

const val TAG = "ReaderService"

class ReaderService : Service(), ClientCommandReceiver {
    private var readerMode: ReaderMode by Delegates.observable(ReaderMode.Idle) { _, old, new ->
        Log.d(TAG, "Changing mode $old → $new")
        sendBroadcast(ReaderStateBroadcast.ReaderModeChanged(readerMode))
    }

    private var webServer: WebServer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        sendBroadcast(ReaderStateBroadcast.ReaderInitStart)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun switchReaderMode(readerMode: ReaderMode) {
        this.readerMode = readerMode
    }

    override fun connectionStateChanged(open: Boolean) {
        sendBroadcast(ReaderStateBroadcast.ConnectionStateChange(open = open))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ReaderServiceCommand.ACTION) {
            val command = ReaderServiceCommand.fromIntent(intent)
            if (command == null) {
                Log.e(TAG, "Missing command data.")
            } else {
                Log.d(TAG, "Received command: $command")
                onCommand(command)
            }
        } else {
            Log.d(TAG, "Received start request: $intent")
            if (webServer == null) {
                Log.d(TAG, "Starting the web server…")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        webServer = WebServer(this@ReaderService)
                            .also { it.start(wait = false) }

                        reportInitDone()
                    } catch (exc: IOException) {
                        sendBroadcast(
                            ReaderStateBroadcast.ServerStartupError(
                                exc.localizedMessage ?: exc.message ?: "no message"
                            )
                        )
                    }
                }
            } else {
                reportInitDone()
                connectionStateChanged(webServer?.isConnectionOpen ?: false)
            }
        }
        return START_STICKY
    }

    private fun reportInitDone() {
        sendBroadcast(ReaderStateBroadcast.ReaderInitDone)
    }

    override fun onDestroy() {
        super.onDestroy()

        webServer?.stop()
    }

    private fun onCommand(command: ReaderServiceCommand) {
        when (command) {
            is ReaderServiceCommand.CardDetected ->
                onCardDetected(command)
        }
    }

    private fun onCardDetected(command: ReaderServiceCommand.CardDetected) {
        Log.d(TAG, "Card detected.")

        // Run card processing (on new thread).
        Thread {
            val reader = TagReader(command.nfcIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG)!!)
            val cardData =
                try {
                    reader.readTag()
                } catch (exc: TagReadingException) {
                    sendBroadcast(ReaderStateBroadcast.CardReadStatus.CardReadingError(exc))
                    null
                }
            if (cardData != null) {
                Log.d(TAG, "Read card data.")
                webServer.let {
                    if (it != null) {
                        if (readerMode != ReaderMode.Idle) {
                            Log.d(TAG, "Sending it.")
                            it.sendCardData(readerMode, cardData)
                            sendBroadcast(ReaderStateBroadcast.CardReadStatus.CardReadingSuccess)
                        } else {
                            Log.d(TAG, "We did not expect a card. Ignoring it.")
                            sendBroadcast(ReaderStateBroadcast.CardReadStatus.CardNotExpected)
                        }
                    } else {
                        Log.d(TAG, "Web server is not running. Ignoring it.")
                    }
                }
            }
        }.start()
    }


    private inner class NdefBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            TODO("Not yet implemented")
        }

    }
}