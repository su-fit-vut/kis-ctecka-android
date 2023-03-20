package cz.jwo.kisctecka.io.nfc

import kotlinx.parcelize.Parcelize

@Parcelize
class TagCommunicationException : TagReadingException("tag communication has failed")
