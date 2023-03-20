package cz.jwo.kisctecka.io.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel

/**
 * Some magic function that makes [Tag]s usable on some devices.
 *
 * From this project: https://github.com/nadam/nfc-reader
 *
 * License: Apache 2.0 (see https://f-droid.org/packages/se.anyro.nfc_reader/)
 */
fun cleanupTag(oTag: Tag): Tag {
    val sTechList = oTag.techList
    val oParcel = Parcel.obtain()
    oTag.writeToParcel(oParcel, 0)
    oParcel.setDataPosition(0)
    val len = oParcel.readInt()
    var id: ByteArray? = null
    if (len >= 0) {
        id = ByteArray(len)
        oParcel.readByteArray(id)
    }
    val oTechList = IntArray(oParcel.readInt())
    oParcel.readIntArray(oTechList)
    val oTechExtras = oParcel.createTypedArray(Bundle.CREATOR)
    val serviceHandle = oParcel.readInt()
    val isMock = oParcel.readInt()
    val tagService: IBinder?
    tagService = if (isMock == 0) {
        oParcel.readStrongBinder()
    } else {
        null
    }
    oParcel.recycle()
    var nfca_idx = -1
    var mc_idx = -1
    var oSak: Short = 0
    var nSak: Short = 0
    for (idx in sTechList.indices) {
        if (sTechList[idx] == NfcA::class.java.name) {
            if (nfca_idx == -1) {
                nfca_idx = idx
                if (oTechExtras!![idx] != null && oTechExtras[idx]!!.containsKey("sak")) {
                    oSak = oTechExtras[idx]!!.getShort("sak")
                    nSak = oSak
                }
            } else {
                if (oTechExtras!![idx] != null && oTechExtras[idx]!!.containsKey("sak")) {
                    nSak = (nSak.toInt() or oTechExtras[idx]!!.getShort("sak").toInt()).toShort()
                }
            }
        } else if (sTechList[idx] == MifareClassic::class.java.name) {
            mc_idx = idx
        }
    }
    var modified = false
    if (oSak != nSak) {
        oTechExtras!![nfca_idx]!!.putShort("sak", nSak)
        modified = true
    }
    if (nfca_idx != -1 && mc_idx != -1 && oTechExtras!![mc_idx] == null) {
        oTechExtras[mc_idx] = oTechExtras[nfca_idx]
        modified = true
    }
    if (!modified) {
        return oTag
    }
    val nParcel = Parcel.obtain()
    nParcel.writeInt(id!!.size)
    nParcel.writeByteArray(id)
    nParcel.writeInt(oTechList.size)
    nParcel.writeIntArray(oTechList)
    nParcel.writeTypedArray(oTechExtras, 0)
    nParcel.writeInt(serviceHandle)
    nParcel.writeInt(isMock)
    if (isMock == 0) {
        nParcel.writeStrongBinder(tagService)
    }
    nParcel.setDataPosition(0)
    val nTag = Tag.CREATOR.createFromParcel(nParcel)
    nParcel.recycle()
    return nTag
}
