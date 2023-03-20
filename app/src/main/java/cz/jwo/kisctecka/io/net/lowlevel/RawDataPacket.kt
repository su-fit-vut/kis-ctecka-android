package cz.jwo.kisctecka.io.net.lowlevel

data class RawDataPacket(
    val type: Byte,
    val data: ByteArray,
) {
    init {
        check(data.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawDataPacket

        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + data.contentHashCode()
        return result
    }

    fun serialize(): ByteArray = byteArrayOf(type, 0x6e.toByte(), *data, 0xe6.toByte())

    companion object {
        fun deserialize(bytes: ByteArray): RawDataPacket {
            check(bytes.size == 35) // TODO Throw proper error
            return RawDataPacket(
                bytes[0],
                bytes.copyOfRange(2, 34),
            )
        }
    }
}