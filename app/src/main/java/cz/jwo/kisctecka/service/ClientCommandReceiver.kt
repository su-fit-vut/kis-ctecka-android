package cz.jwo.kisctecka.service

interface ClientCommandReceiver {
    fun switchReaderMode(readerMode: ReaderMode)
    fun connectionStateChanged(open: Boolean)
}
