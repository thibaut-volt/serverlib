package com.volt.serverlib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.volt.server.HttpServer

class MainActivityViewModelFactory(private val httpServer: HttpServer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
            return MainActivityViewModel(httpServer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}


class MainActivityViewModel(httpServer: HttpServer): ViewModel() {

    init {
        httpServer.start(8080)
    }

}
