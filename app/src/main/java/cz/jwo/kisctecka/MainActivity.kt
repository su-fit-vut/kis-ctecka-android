package cz.jwo.kisctecka

import android.app.PendingIntent
import android.content.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import cz.jwo.kisctecka.service.ReaderMode
import cz.jwo.kisctecka.service.ReaderService
import cz.jwo.kisctecka.service.ReaderServiceCommand
import cz.jwo.kisctecka.service.ReaderStateBroadcast
import kotlinx.coroutines.*
import java.net.NetworkInterface


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private val defaultTemporaryStatusTimeout: Long = 3000
    private var temporaryStatusJob: Job? = null
    private var lastPermanentStatus: CharSequence? = null
    private lateinit var logView: TextView
    private val readerStateBroadcastReceiver = ReaderStateBroadcastReceiver()

    private lateinit var nfcAdapter: NfcAdapter

    private lateinit var statusTextView: TextView

    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var cameraManager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        Log.d(TAG, "onCreate")

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        registerReceiver(
            readerStateBroadcastReceiver,
            IntentFilter(
                ReaderStateBroadcast.ACTION,
            )
        )

        logView = findViewById(R.id.logView)!!
        statusTextView = findViewById(R.id.statusTextView)!!

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        val savedLog = savedInstanceState?.getCharSequence(STATE_LOG)
        val savedMessage = savedInstanceState?.getCharSequence(STATE_MESSAGE)
        Log.d(TAG, savedInstanceState.toString())
        if (savedMessage != null) {
            showPermanentStatus(savedMessage)
        } else {
            showPermanentStatus(getString(R.string.status_initializing))
        }
        if (savedLog != null) {
            logView.text = savedLog
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putCharSequence(STATE_MESSAGE, lastPermanentStatus ?: statusTextView.text)
        outState.putCharSequence(STATE_LOG, logView.text)
    }

    override fun onPause() {
        super.onPause()

        Log.d(TAG, "Disabling foreground NFC dispatch.")
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume")

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

        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            if (sharedPrefs.getBoolean(
                    PREFERENCE_KEEP_SCREEN_ON,
                    false
                )
            ) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0
        )
    }

    private fun logMessage(message: CharSequence) {
        logView.text = "${logView.text.lines().takeLast(4).joinToString("\n")}\n$message"
    }

    private fun showPermanentStatus(message: CharSequence, log: Boolean = true) {
        showStatus(message, log = log)
        lastPermanentStatus = message
        temporaryStatusJob?.cancel()
    }

    private fun showTemporaryStatus(message: CharSequence, timeoutMillis: Long = defaultTemporaryStatusTimeout) {
        showStatus(message)
        temporaryStatusJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMillis)
            showLastPermanentStatus()
        }
    }

    private fun showLastPermanentStatus() {
        lastPermanentStatus?.let { showPermanentStatus(it, log = false) }
    }

    private fun showStatus(message: CharSequence, log: Boolean = true) {
        statusTextView.text = message
        if (log) {
            logMessage(message)
        }
    }

    fun onReaderInit() {
        showReaderAddress()
        showTemporaryStatus(getString(R.string.status_reader_init_done))
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
                is ReaderStateBroadcast.ReaderModeChanged -> onReaderModeChanged(broadcast.readerMode)
                is ReaderStateBroadcast.ConnectionStateChange -> onConnectionStateChange(broadcast)
            }
        }
    }

    private fun onConnectionStateChange(connectionStateChange: ReaderStateBroadcast.ConnectionStateChange) {
        when (connectionStateChange.open) {
            true -> {
                showPermanentStatus(getString(R.string.status_idle))
            }

            false -> {
                showReaderAddress()
                showTemporaryStatus(getString(R.string.status_disconnected))
            }
        }
    }

    private fun showReaderAddress() {
        showPermanentStatus(
            createCurrentAddress()?.let { addr ->
                getString(R.string.status_reader_name, addr)
            } ?: getString(R.string.status_disconnected_no_address)
        )
    }

    private fun createCurrentAddress(): String? {
        Log.d(TAG, "Trying to get current address.")
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()

        // Try to get STUDKLUB address (shown together with reverse proxy URL address).
        networkInterfaces.forEach { networkInterface ->
            Log.d(TAG, "  • if: ${networkInterface.name}")
            networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                val addressString = interfaceAddress.address.hostAddress ?: interfaceAddress.address.toString()
                Log.d(TAG, "      • addr: $addressString")
                if (addressString.startsWith("10.10.20.")) {
                    return getString(
                        R.string.status_disconnected_studklub_address,
                        getString(R.string.studklub_address, addressString.split('.').last().toString()),
                        addressString
                    )
                }
            }
        }

        // If we do not have STUDKLUB address, provide addresses of all network interfaces.
        return networkInterfaces
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .map { networkInterface ->
                Pair(
                    networkInterface,
                    networkInterface.interfaceAddresses
                        .map { it.address }
                        .filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
                        .filter { it.address.size == 4 } // IPv4 is currently used on all our networks.
                )
            }
            .filter { (_, interestingAddresses) -> interestingAddresses.isNotEmpty() }
            .run { takeIf { isNotEmpty() } }
            ?.joinToString("\n") { (networkInterface, interestingAddresses) ->
                networkInterface.displayName + ": " +
                        (interestingAddresses
                            .joinToString(", ") { addr ->
                                addr.hostAddress ?: addr.toString()
                            })
            }
    }

    private fun onReaderModeChanged(readerMode: ReaderMode) {
        showPermanentStatus(
            getString(
                when (readerMode) {
                    ReaderMode.SingleRead -> R.string.status_reading
                    ReaderMode.ContinuousRead -> R.string.status_reading
                    ReaderMode.SingleReadAuth -> R.string.status_reading
                    ReaderMode.AuthUseKey -> R.string.status_reading
                    ReaderMode.Idle -> R.string.status_idle
                }
            )
        )
    }

    private fun onCardStatusChange(cardReadStatus: ReaderStateBroadcast.CardReadStatus) {
        showTemporaryStatus(
            getString(
                when (cardReadStatus) {
                    ReaderStateBroadcast.CardReadStatus.CardNotExpected -> R.string.status_card_not_expected
                    is ReaderStateBroadcast.CardReadStatus.CardReadingError -> R.string.status_card_reading_error
                    ReaderStateBroadcast.CardReadStatus.CardReadingSuccess -> R.string.status_card_reading_success
                }
            )
        )

        if (cardReadStatus == ReaderStateBroadcast.CardReadStatus.CardReadingSuccess
            || !sharedPrefs.getBoolean(PREFERENCE_FLASH_ONLY_ON_SUCCESS, true)
        ) {
            if (sharedPrefs.getBoolean(PREFERENCE_FLASH_ON_READ, false)) {
                flashTorch()
            }
        }
    }

    private fun flashTorch() {
        val level = sharedPrefs.getInt(PREFERENCE_FLASH_BRIGHTNESS, 65535).toFloat() / 65535.0
        cameraManager.cameraIdList.forEach { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            CoroutineScope(Dispatchers.Default).launch {
                val regulationAvailable =
                    getTorchBrightnessRegulationAvailable(this@MainActivity, cameraId = cameraId)
                try {
                    val maxLevel =
                        if (regulationAvailable) {
                            if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                                characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                            } else {
                                throw AssertionError()
                            }
                        } else {
                            null
                        }
                    if (regulationAvailable) {
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, (level * (maxLevel ?: 1)).toInt())
                    } else {
                        cameraManager.setTorchMode(cameraId, true)
                    }
                    delay(
                        sharedPrefs.getInt(
                            PREFERENCE_FLASH_DURATION,
                            resources.getInteger(R.integer.default_flash_duration)
                        ).toLong()
                    )
                    cameraManager.setTorchMode(cameraId, false)
                } catch (exc: CameraAccessException) {
                    Log.e(TAG, "Failed to access camera (torch mode)", exc)
                } catch (exc: IllegalArgumentException) {
                    Log.w(TAG, "Failed to flash torch.", exc)
                }
            }
        }
    }

    companion object {
        const val STATE_MESSAGE = "cz.jwo.kisctecka.MainActivity.STATE_MESSAGE"
        const val STATE_LOG = "cz.jwo.kisctecka.MainActivity.STATE_LOG"

        const val PREFERENCE_FLASH_ON_READ = "flash_on_read"
        const val PREFERENCE_FLASH_BRIGHTNESS = "flash_brightness"
        const val PREFERENCE_FLASH_DURATION = "flash_duration"
        const val PREFERENCE_FLASH_ONLY_ON_SUCCESS = "flash_only_on_success"
        const val PREFERENCE_KEEP_SCREEN_ON = "keep_screen_on"

        fun getTorchBrightnessRegulationAvailable(
            context: Context,
            cameraManager: CameraManager? = null,
            cameraId: String,
        ) =
            VERSION.SDK_INT >= VERSION_CODES.TIRAMISU
                    && context.resources.getBoolean(R.bool.camera_brightness_regulation_enabled)
                    && (cameraManager
                ?: context.getSystemService(CAMERA_SERVICE) as CameraManager)?.let { cameraManager ->
                cameraManager.getCameraCharacteristics(cameraId)?.let { cameraCharacteristics ->
                    (cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                        ?: 1) > 1
                } == true
            } == true

        fun getTorchBrightnessRegulationAvailable(context: Context): Boolean {
            return (context.getSystemService(CAMERA_SERVICE) as CameraManager)?.let { cameraManager ->
                cameraManager.cameraIdList
                    ?.any { cameraId -> getTorchBrightnessRegulationAvailable(context, cameraManager, cameraId) }
            }
                ?: false
        }
    }
}