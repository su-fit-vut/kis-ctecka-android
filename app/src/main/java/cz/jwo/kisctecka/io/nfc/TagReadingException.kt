package cz.jwo.kisctecka.io.nfc

import android.os.Parcelable
import java.io.IOException

abstract class TagReadingException(message: String, cause: Exception? = null) : IOException(message, cause), Parcelable
