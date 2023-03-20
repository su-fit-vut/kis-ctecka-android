package cz.jwo.kisctecka.io.nfc

import kotlinx.parcelize.Parcelize

@Parcelize
class UnsupportedTagException(override val message: String = "unsupported tag type") : TagReadingException(message)
