package com.volt.server.exceptions

import java.lang.Exception

class RouteException(val code: Int, message: String) : Exception(message)
