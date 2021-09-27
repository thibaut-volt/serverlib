package com.volt.server.internal

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

internal fun getLocalIpAddress(context: Context) = try {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    ipToString(wifiManager.connectionInfo.ipAddress)
} catch (ex: Exception) {
    Log.e("IP Address", ex.toString())
    null
}

private fun ipToString(i: Int): String {
    return (i and 0xFF).toString() + "." + (i shr 8 and 0xFF) + "." + (i shr 16 and 0xFF) + "." + (i shr 24 and 0xFF)
}
