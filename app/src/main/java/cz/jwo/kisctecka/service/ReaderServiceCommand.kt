package cz.jwo.kisctecka.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class ReaderServiceCommand : Parcelable {
    fun makeIntent(context: Context): Intent = Intent(context, ReaderService::class.java).apply {
        action = ACTION
        putExtra(EXTRA_COMMAND, this@ReaderServiceCommand)
    }

    @Parcelize
    data class CardDetected(val nfcIntent: Intent) : ReaderServiceCommand()

    companion object {
        const val ACTION = "cz.jwo.kisctecka.ReaderServiceCommand.COMMAND"
        const val EXTRA_COMMAND = "cz.jwo.kisctecka.ReaderServiceCommand.EXTRA_COMMAND"

        fun fromIntent(intent: Intent): ReaderServiceCommand? =
            intent.getParcelableExtra(EXTRA_COMMAND)
    }
}