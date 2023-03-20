package cz.jwo.kisctecka.io.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log
import cz.jwo.kisctecka.io.net.lowlevel.CardData
import cz.jwo.kisctecka.service.TAG
import java.io.IOException

class TagReader(tag: Tag) {
    private val tag = cleanupTag(tag)

    private val nfcA = NfcA.get(this.tag)
    private val mifareClassic = MifareClassic.get(this.tag)
    private val mifareUltralight = MifareUltralight.get(this.tag)

    fun readTag(): CardData? {
        logInfo()

        // Process the card information.
        return if (mifareClassic != null) {
            processMifareClassic()
        } else if (mifareUltralight != null) {
            processMifareUltralight()
        } else {
            Log.e(TAG, "Technology not supported.")
            throw UnsupportedTagException()
        }
    }

    private fun processMifareUltralight(): CardData {
        Log.d(TAG, "Processing MIFARE ULTRALIGHT card.")
        Log.d(TAG, "Connecting…")
        try {
            mifareUltralight.connect()
        } catch (exc: IOException) {
            Log.e(TAG, "Connection failed.")
            throw TagCommunicationException()
        }

        val rawCardData = try {
            // Read 4 (really!) 4Byte pages.
            mifareUltralight.readPages(0)
        } catch (exc: IOException) {
            Log.e(TAG, "Read failed.", exc)
            throw TagCommunicationException()
        }

        // Mask all bytes except the serial number.
        listOf(0xff, 0xfe, 0xfd, 0xfc, 0xfb, 0xfa).map { it.toByte() }.toByteArray()
            .copyInto(rawCardData, 10, 0)

        try {
            mifareUltralight.close()
        } catch (exc: IOException) {
            Log.e(TAG, "Failed closing the tag", exc)
        }

        return CardData(tag.id, rawCardData)
    }

    private fun processMifareClassic(): CardData? {
        Log.d(TAG, "Processing MIFARE CLASSIC card.")
        Log.d(TAG, "Connecting…")
        try {
            mifareClassic.connect()
        } catch (exc: IOException) {
            Log.e(TAG, "Connection failed.")
            throw TagCommunicationException()
        }

        Log.d(TAG, "Authenticating to the sect. 0.")
        // ↓Throws IOException when cancelled – ignoring that.
        val authSuccess = mifareClassic.authenticateSectorWithKeyA(0, ByteArray(6) { 0xff.toByte() })
        Log.d(TAG, "  success: $authSuccess")
        if (!authSuccess) {
            Log.e(TAG, "Auth failed.")
            throw UnsupportedTagException("authorization failed")
        }

        Log.d(TAG, "Reading sect. 0.")
        val rawCardData = try {
            mifareClassic.readBlock(0)
        } catch (exc: IOException) {
            Log.e(TAG, "Read failed.", exc)
            // TODO Report
            return null
        }
        Log.d(TAG, "Sector 0 bytes: ${rawCardData.toList()}")

        Log.d(TAG, "Tag UID: ${tag.id.toList()}")

        try {
            mifareClassic.close()
        } catch (exc: IOException) {
            Log.e(TAG, "Failed closing the tag", exc)
        }

        return CardData(tag.id, rawCardData)
    }

    private fun logInfo() {
        Log.d(TAG, "Card technology info: $tag")

        // Try getting various info:
        nfcA?.use {
            Log.d(TAG, "NFC-A")
            Log.d(TAG, "  • SAK: ${it.sak}")
        }
        mifareClassic?.use {
            Log.d(TAG, "Mifare Classic")
            Log.d(TAG, "  • ${it.blockCount} blocks")
        }
        mifareUltralight?.use {
            Log.d(TAG, "Mifare Ultralight")
            Log.d(TAG, "  • type: ${it.type}")
        }
    }
}