package com.volt.serverlib

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.volt.server.HttpServer
import com.volt.serverlib.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        val router = RouterTest()
        val httpServer = HttpServer(this, router, this)

        binding.viewModel = ViewModelProvider(this, MainActivityViewModelFactory(
            httpServer
        )).get(MainActivityViewModel::class.java)
    }

}
