package com.volt.server

import android.content.Context
import android.util.Log
import com.volt.server.enums.HttpCode
import com.volt.server.exceptions.RouteException
import com.volt.server.interfaces.IRequest
import com.volt.server.interfaces.IResponse
import com.volt.server.internal.*
import com.volt.server.internal.HttpResponse
import com.volt.server.internal.getLocalIpAddress
import com.volt.server.internal.parseHttpUrlHeader
import com.volt.server.models.ApiCall
import com.volt.server.models.Route
import com.volt.server.utils.StreamBufferReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Socket
import kotlinx.serialization.Serializable

private const val TAG = "HttpServer"

@Serializable
data class RouteError(val code: Int, val message: String)

class HttpServer(
    private val context: Context,
    private val router: Router,
    private val scope: CoroutineScope
) {
    private var nextApiCall = 0
    private val _devApiCallFlow = MutableStateFlow<ApiCall?>(null)

    val devApiCallFlow : StateFlow<ApiCall?> = _devApiCallFlow

    private val server = Server(scope, {
        Log.d(TAG, "Http Server listening at: ${getLocalIpAddress(context)}:$it")
    }, {
        Log.d(TAG, "Http Server stopped")
    }, {
        processHttpRequest(it)
    })

    fun start(port: Int) {
        if (server.isServerStarted()) {
            return
        }

        Log.d(TAG, "Starting Http server on port: $port")
        server.start(port)
    }

    suspend fun stop() {
        if (!server.isServerStarted()) {
            return
        }

        Log.d(TAG, "Stopping Http server")
        server.stop()
    }

    suspend fun restart(port: Int) {
        stop()
        start(port)
    }

    fun getPort() = server.port

    private suspend fun processHttpRequest(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val localScope = scope + Dispatchers.IO
        @Suppress("BlockingMethodInNonBlockingContext")
        val streamReader = StreamBufferReader(BufferedInputStream(clientSocket.getInputStream()), localScope)
        @Suppress("BlockingMethodInNonBlockingContext")
        val outputStream = clientSocket.getOutputStream()
        val response = createResponse(outputStream)
        var apiRequestCall: ApiCall? = null

        try {
            val httpUrlHeader = parseHttpUrlHeader(streamReader)
            val apiCall = ++nextApiCall

            Log.d(TAG, "Received request: ${httpUrlHeader.method} ${httpUrlHeader.url} (Client: ${clientSocket.inetAddress.hostAddress})")

            apiRequestCall = ApiCall(
                apiCall,
                System.currentTimeMillis(),
                clientSocket.inetAddress.hostAddress,
                httpUrlHeader.method,
                httpUrlHeader.url
            )

            // Notify developer listener of request
            _devApiCallFlow.value = apiRequestCall

            router.route(httpUrlHeader.method, httpUrlHeader.url)?.let {
                it.callback(createRequest(streamReader, it), response)
            }

            // Notify developer listener of response
            _devApiCallFlow.value = apiRequestCall.withResponse(response.getHttpCode()?.code ?: 0, response.getData())
        } catch (e: RouteException) {
            Log.d(TAG, "RouteException", e)

            try {
                response.sendJson(
                    RouteError(e.code, e.message ?: "No details"),
                    RouteError.serializer(),
                    HttpCode.FORBIDDEN
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot write route error response", e2)
            }
        } catch(e: Exception) {
            Log.d(TAG, "Request Exception", e)

            // Notify developer UI
            apiRequestCall?.let {
                _devApiCallFlow.value = it.withResponse(
                    -1,
                    "Invalid Http Request"
                )
            }

            try {
                response.sendInvalidResponse("Invalid Http Request")
            } catch(e2: Exception) {
                Log.e(TAG, "Cannot write http response", e2)
            }
        } finally {
            @Suppress("BlockingMethodInNonBlockingContext")
            outputStream.flush()
            streamReader.close()
        }
    }
}

suspend fun createRequest(streamReader: StreamBufferReader, route: Route): IRequest = HttpRequest(
    parseHttpHeaders(streamReader),
    route.params,
    route.arguments,
    streamReader
)

fun createResponse(
    outputStream: OutputStream,
    overrideHeader: ((code: HttpCode) -> String)? = null
): IResponse = HttpResponse(outputStream, overrideHeader)
