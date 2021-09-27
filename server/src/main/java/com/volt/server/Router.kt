package com.volt.server

import android.net.Uri
import com.volt.server.interfaces.IRequest
import com.volt.server.interfaces.IResponse
import com.volt.server.models.Route

private data class RegisteredRoute(
        val pathParams: List<String>,
        val callback: suspend (request: IRequest, response: IResponse) -> Unit
)

abstract class Router {

    private val routes = HashMap<Regex, RegisteredRoute>()

    fun onGet(url: String, callback: suspend (request: IRequest, response: IResponse) -> Unit)
            = addRoute("GET", url, callback)

    fun onPost(url: String, callback: suspend (request: IRequest, response: IResponse) -> Unit)
            = addRoute("POST", url, callback)

    fun onPut(url: String, callback: suspend (request: IRequest, response: IResponse) -> Unit)
            = addRoute("PUT", url, callback)

    fun onDelete(url: String, callback: suspend (request: IRequest, response: IResponse) -> Unit)
            = addRoute("DELETE", url, callback)

    private fun addRoute(
            method: String,
            url: String,
            callback: suspend (request: IRequest, response: IResponse) -> Unit
    ) {
        val params = Regex(":[^/]+").findAll(url)
        val paramNames = params.map { it.value.substring(1) }.toList()

        routes[Regex("$method//$url".replace(Regex(":[^/]+"), "([^\\/]+)"))] = RegisteredRoute(paramNames, callback)
    }

    fun route(
            method: String,
            url: String,
    ): Route? {
        val argumentsIndex = url.indexOf("?")
        val urlWithoutArguments = if (argumentsIndex > 0) {
            url.replaceRange(argumentsIndex, url.length,  "")
        } else {
            url
        }

        for(route in routes) {
            val matches = route.key.matchEntire("$method//$urlWithoutArguments") ?: continue
            val paramsValues = matches.groups.filterNotNull().map { it.value }.drop(1)
            val params = HashMap<String, String>()
            val arguments = HashMap<String, String>()

            paramsValues.forEachIndexed { index, value ->
                params[route.value.pathParams[index]] = value
            }

            if (argumentsIndex > 0) {
                var key: String
                var value: String
                val uri = Uri.parse(url)
                val keyNamesList = uri.queryParameterNames
                val iterator = keyNamesList.iterator()

                while (iterator.hasNext()) {
                    key = iterator.next() as String
                    value = uri.getQueryParameter(key) as String
                    arguments[key] = value
                }
            }

            return Route(route.value.callback, params, arguments)
        }

        return null
    }

}