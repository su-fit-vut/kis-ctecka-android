package cz.jwo.kisctecka.io.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.Parcel

/**
 * Some magic function that makes [Tag]s usable on some devices.
 *
 * From this project: https://github.com/nadam/nfc-reader
 *
 * License: Apache 2.0 (see https://f-droid.org/packages/se.anyro.nfc_reader/)
 *
 * Converted to Kotlin and further modified by Jiří Wolker.
 */
fun fixTagInformation(tag: Tag): Tag {
    val techList = tag.techList
    val tagParcel = Parcel.obtain().also {
        tag.writeToParcel(it, 0)
        it.setDataPosition(0)
    }
    val len = tagParcel.readInt()
    var id: ByteArray? = null
    if (len >= 0) {
        id = ByteArray(len)
        tagParcel.readByteArray(id)
    }
    val newTechList = IntArray(tagParcel.readInt())
    tagParcel.readIntArray(newTechList)
    val techExtras = tagParcel.createTypedArray(Bundle.CREATOR)
    val serviceHandle = tagParcel.readInt()
    val isMock = tagParcel.readInt()
    val tagService = if (isMock == 0) {
        tagParcel.readStrongBinder()
    } else {
        null
    }
    tagParcel.recycle()
    var nfcaIdx = -1
    var mcIdx = -1
    var sak: Short = 0
    var newSak: Short = 0
    for (idx in techList.indices) {
        if (techList[idx] == NfcA::class.java.name) {
            if (nfcaIdx == -1) {
                nfcaIdx = idx
                if (techExtras!![idx] != null && techExtras[idx]!!.containsKey("sak")) {
                    sak = techExtras[idx]!!.getShort("sak")
                    newSak = sak
                }
            } else {
                if (techExtras!![idx] != null && techExtras[idx]!!.containsKey("sak")) {
                    newSak = (newSak.toInt() or techExtras[idx]!!.getShort("sak").toInt()).toShort()
                }
            }
        } else if (techList[idx] == MifareClassic::class.java.name) {
            mcIdx = idx
        }
    }
    var modified = false
    if (sak != newSak) {
        techExtras!![nfcaIdx]!!.putShort("sak", newSak)
        modified = true
    }
    if (nfcaIdx != -1 && mcIdx != -1 && techExtras!![mcIdx] == null) {
        techExtras[mcIdx] = techExtras[nfcaIdx]
        modified = true
    }
    if (!modified) {
        return tag
    }
    Parcel.obtain().let { outputParcel ->
        outputParcel.apply {
            writeInt(id!!.size)
            writeByteArray(id)
            writeInt(newTechList.size)
            writeIntArray(newTechList)
            writeTypedArray(techExtras, 0)
            writeInt(serviceHandle)
            writeInt(isMock)
            if (isMock == 0) {
                writeStrongBinder(tagService)
            }
            setDataPosition(0)
        }
        return Tag.CREATOR.createFromParcel(outputParcel)
            .also { outputParcel.recycle() }
    }
}
