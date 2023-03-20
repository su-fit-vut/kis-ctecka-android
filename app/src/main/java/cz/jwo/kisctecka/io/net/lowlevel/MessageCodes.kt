package cz.jwo.kisctecka.io.net.lowlevel

object MessageCodeToReader {
    const val PING: Byte = 0
    const val CONTINUOUS_READ: Byte = 1
    const val SINGLE_READ: Byte = 2
    const val STOP: Byte = 3
    const val SINGLE_AUTH_ID: Byte = 4
    const val SINGLE_AUTH_KEY: Byte = 5
    const val PRINT_TEXT: Byte = 6
}

@Suppress("unused")
object MessageCodeFromReader {
    const val PONG: Byte = 8
    const val AUTO_ID: Byte = 9
    const val SINGLE_ID: Byte = 10
    const val SINGLE_ID_SEND_KEY: Byte = 11
    const val VERIFICATION_CODE: Byte = 12
}