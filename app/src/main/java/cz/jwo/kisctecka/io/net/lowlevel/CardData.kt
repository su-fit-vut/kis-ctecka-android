package cz.jwo.kisctecka.io.net.lowlevel

data class CardData(val uid: ByteArray, val content: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CardData

        if (!uid.contentEquals(other.uid)) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid.contentHashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}