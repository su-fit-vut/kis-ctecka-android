package cz.jwo.kisctecka

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import cz.jwo.kisctecka.service.ReaderMode
import cz.jwo.kisctecka.service.ReaderService
import cz.jwo.kisctecka.service.ReaderServiceCommand
import cz.jwo.kisctecka.service.ReaderStateBroadcast
import kotlinx.coroutines.*
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.*


private const val TAG = "MainActivity"
private const val PROXIMITY_WAKE_LOCK_TAG = "cz.jwo.kisctecka:MainActivity"

class MainActivity : AppCompatActivity() {
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var blackThemeUsed: Boolean = false
    private val defaultTemporaryStatusTimeout: Long = 3000
    private var temporaryStatusJob: Job? = null
    private var lastPermanentStatus: CharSequence? = null
    private lateinit var logView: TextView
    private val readerStateBroadcastReceiver = ReaderStateBroadcastReceiver()

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var statusTextView: TextView

    private lateinit var restartButton: Button

    private lateinit var appPreferences: AppPreferences

    private lateinit var cameraManager: CameraManager
    private lateinit var powerManager: PowerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate")

        appPreferences = AppPreferences(this)

        blackThemeUsed = appPreferences.useBlackTheme
        if (blackThemeUsed) {
            setTheme(R.style.Theme_KISCtecka_OLed)
        }

        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        registerReceiver(
            readerStateBroadcastReceiver,
            IntentFilter(
                ReaderStateBroadcast.ACTION,
            )
        )

        logView = findViewById(R.id.logView)!!
        statusTextView = findViewById(R.id.statusTextView)!!
        restartButton = findViewById(R.id.restartButton)!!

        restartButton.isVisible = false
        restartButton.setOnClickListener {
            showPermanentStatus(getString(R.string.status_retrying))
            restartService()
        }

        findViewById<Button>(R.id.repeat_card_button).setOnClickListener {
            repeatCard()
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        NfcAdapter.getDefaultAdapter(this).takeUnless { resources.getBoolean(R.bool.pretend_having_no_nfc) }.let {
            if (it != null) {
                nfcAdapter = it
            } else {
                finish()
                startActivity(Intent(this@MainActivity, NfcNotAvailableActivity::class.java))
                return
            }
        }
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

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

        packageManager.getPackageInfo(packageName, 0).let { myPackage ->
            findViewById<TextView>(R.id.appVersion).text = getString(
                R.string.brief_version_information,
                myPackage.versionName,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                    .format(Date(myPackage.lastUpdateTime)),
            )
        }
    }

    private fun repeatCard() {
        startService(ReaderServiceCommand.RepeatCard().makeIntent(this@MainActivity))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                return true
            }

            else -> return super.onKeyUp(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (appPreferences.repeatOnVolumeUp) {
                    repeatCard()
                }
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (appPreferences.repeatOnVolumeDown) {
                    repeatCard()
                }
                return true
            }

            else -> return super.onKeyUp(keyCode, event)
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
        nfcAdapter?.disableForegroundDispatch(this)

        proximityWakeLock?.release()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume")

        if (blackThemeUsed != appPreferences.useBlackTheme) {
            restartActivity()
        }

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (VERSION.SDK_INT >= VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )

        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            null,
            arrayOf(arrayOf(MifareUltralight::class.java.name), arrayOf(MifareClassic::class.java.name))
        )

        startService(Intent(this, ReaderService::class.java))
        Log.d(TAG, "Enabling foreground NFC dispatch.")

        @SuppressLint("WakelockTimeout")
        proximityWakeLock = if (appPreferences.useProximitySensor) {
            powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, PROXIMITY_WAKE_LOCK_TAG).also {
                it.acquire()
            }
        } else {
            null
        }

        findViewById<View>(R.id.repeat_card_button).visibility =
            if (appPreferences.showRepeatButton) View.VISIBLE else View.INVISIBLE

        window.setFlags(
            if (appPreferences.keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun restartActivity() {
        intent.let {
            overridePendingTransition(0, 0)
            finish()
            startActivity(it)
        }
    }

    private fun restartService() {
        stopService(Intent(this, ReaderService::class.java))
        startService(Intent(this, ReaderService::class.java))
    }

    private fun logMessage(message: CharSequence) {
        if (!logView.text.endsWith("\n$message")) {
            @SuppressLint("SetTextI18n")
            logView.text = (logView.text.lines() + listOf(message)).takeLast(5).joinToString("\n")
        }
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

    fun onReaderInitStart() {
        showTemporaryStatus(getString(R.string.status_initializing))
        restartButton.isVisible = false
    }

    private fun onReaderInitDone() {
        showReaderAddress()
        showTemporaryStatus(getString(R.string.status_reader_init_done))
    }

    fun onServerStartError(broadcast: ReaderStateBroadcast.ServerStartupError) {
        showPermanentStatus(getString(R.string.status_start_failure, broadcast.message))
        restartButton.isVisible = true
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
                is ReaderStateBroadcast.ReaderInitStart -> onReaderInitStart()
                is ReaderStateBroadcast.ReaderInitDone -> onReaderInitDone()
                is ReaderStateBroadcast.CardReadStatus -> onCardStatusChange(broadcast)
                is ReaderStateBroadcast.ReaderModeChanged -> onReaderModeChanged(broadcast.readerMode)
                is ReaderStateBroadcast.ConnectionStateChange -> onConnectionStateChange(broadcast)
                is ReaderStateBroadcast.ServerStartupError -> onServerStartError(broadcast)
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
            || !appPreferences.flashOnlyOnSuccess
        ) {
            if (appPreferences.flashOnRead) {
                flashTorch()
            }
        }
    }

    private fun flashTorch() {
        val level = appPreferences.flashBrightness.toFloat() / 65535.0
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
                        appPreferences.flashDuration.toLong()
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

        fun getTorchBrightnessRegulationAvailable(
            context: Context,
            cameraManager: CameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager,
            cameraId: String,
        ) =
            (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU && context.resources.getBoolean(R.bool.camera_brightness_regulation_enabled))
                    && cameraManager.getCameraCharacteristics(cameraId)
                .let { cameraCharacteristics ->
                    (cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1) > 1
                }

        fun getTorchBrightnessRegulationAvailable(context: Context): Boolean {
            return (context.getSystemService(CAMERA_SERVICE) as CameraManager).let { cameraManager ->
                cameraManager.cameraIdList
                    .any { cameraId -> getTorchBrightnessRegulationAvailable(context, cameraManager, cameraId) }
            }
        }
    }
}