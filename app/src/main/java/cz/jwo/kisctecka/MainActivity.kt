package cz.jwo.kisctecka

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cz.jwo.kisctecka.service.ReaderService
import cz.jwo.kisctecka.service.ReaderServiceCommand
import cz.jwo.kisctecka.service.ReaderStateBroadcast


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private val readerStateBroadcastReceiver = ReaderStateBroadcastReceiver()

    private lateinit var nfcAdapter: NfcAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate")

        registerReceiver(
            readerStateBroadcastReceiver,
            IntentFilter(
                ReaderStateBroadcast.ACTION,
            )
        )

        logView = findViewById(R.id.logView)!!

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        logMessage("Started.")
    }

    override fun onPause() {
        super.onPause()

        Log.d(TAG, "onPause")
        logMessage("onPause")

        Log.d(TAG, "Disabling foreground NFC dispatch.")
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume")
        logMessage("onResume")

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (VERSION.SDK_INT >= VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        val ndef = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED).apply {
            try {
                addDataType("*/*")    /* Handles all MIME based dispatches.
                                 You should specify only the ones that you need. */
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }

        val intentFiltersArray = arrayOf(ndef)
        nfcAdapter.enableForegroundDispatch(
            this,
            pendingIntent,
            null,
            arrayOf(arrayOf(MifareUltralight::class.java.name), arrayOf(MifareClassic::class.java.name))
        )

        startService(Intent(this, ReaderService::class.java))
        Log.d(TAG, "Enabling foreground NFC dispatch.")
    }

    private fun logMessage(message: String) {
        logView.text = "${logView.text.lines().takeLast(4).joinToString("\n")}\n$message"
    }

    fun onReaderInit() {
        logMessage("Reader init.")
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            onTechDiscovered(intent)
        } else {
            super.onNewIntent(intent)
        }
    }

    private fun onTechDiscovered(intent: Intent) {
        Log.i(TAG, "Tech discovered")
        Log.i(TAG, intent.toString())
        logMessage("TECH")

        Log.d(TAG, "Starting the service with card info…")
        startService(ReaderServiceCommand.CardDetected(intent).makeIntent(this))
    }

    private inner class ReaderStateBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val broadcast = ReaderStateBroadcast.fromIntent(intent!!)!!
            Log.d(TAG, "Received broadcast event: $broadcast")

            when (broadcast) {
                is ReaderStateBroadcast.ReaderInit -> onReaderInit()
                is ReaderStateBroadcast.CardReadStatus -> onCardStatusChange(broadcast)
            }
        }
    }

    private fun onCardStatusChange(cardReadStatus: ReaderStateBroadcast.CardReadStatus) {
        logMessage(
            when (cardReadStatus) {
                ReaderStateBroadcast.CardReadStatus.CardNotExpected -> "Card not expected"
                is ReaderStateBroadcast.CardReadStatus.CardReadingError -> "Card reading error"
                ReaderStateBroadcast.CardReadStatus.CardReadingSuccess -> "Card reading success"
            }
        )
    }
}