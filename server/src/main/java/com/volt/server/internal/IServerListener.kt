package com.volt.server.internal

import java.net.Socket

interface IServerListener {
    fun onStarted(port: Int)
    fun onStopped()
    suspend fun onClientConnected(client: Socket)
}
