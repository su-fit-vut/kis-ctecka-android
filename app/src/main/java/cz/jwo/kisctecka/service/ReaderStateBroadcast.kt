package cz.jwo.kisctecka.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import cz.jwo.kisctecka.io.nfc.TagReadingException
import kotlinx.parcelize.Parcelize

sealed class ReaderStateBroadcast : Parcelable {
    fun makeIntent(context: Context): Intent = Intent().apply {
        `package` = context.packageName
        action = ACTION
        putExtra(EXTRA_EVENT, this@ReaderStateBroadcast)
    }

    @Parcelize
    object ReaderInitStart : ReaderStateBroadcast()

    @Parcelize
    object ReaderInitDone : ReaderStateBroadcast()

    @Parcelize
    data class ServerStartupError(val message: String) : ReaderStateBroadcast()

    sealed class CardReadStatus : ReaderStateBroadcast() {
        @Parcelize
        class CardReadingError(val cause: TagReadingException) : CardReadStatus()

        @Parcelize
        object CardReadingSuccess : CardReadStatus()

        @Parcelize
        object CardNotExpected : CardReadStatus()
    }

    @Parcelize
    data class ReaderModeChanged(val readerMode: ReaderMode) : ReaderStateBroadcast()

    @Parcelize
    data class ConnectionStateChange(val open: Boolean) : ReaderStateBroadcast()

    companion object {
        const val ACTION = "cz.jwo.kisctecka.ReaderStateBroadcast.EVENT"
        const val EXTRA_EVENT = "cz.jwo.kisctecka.ReaderStateBroadcast.EXTRA_EVENT"

        fun fromIntent(intent: Intent): ReaderStateBroadcast? =
            intent.getParcelableExtra(EXTRA_EVENT)
    }
}

fun Context.sendBroadcast(broadcast: ReaderStateBroadcast) {
    sendBroadcast(broadcast.makeIntent(this))
}