package com.volt.server.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApiCall(val id: Int,
                   val requestTime: Long,
                   val requestIp: String,
                   val requestType: String,
                   val requestUrl: String,
                   val connection: String = "HTTP",
                   val responseTime: Long? = null,
                   val responseCode: Int? = null,
                   val responseData: String? = null): Parcelable {

    fun eq(newValue: ApiCall, lazy: Boolean): Boolean {
        if (id != newValue.id) {
            return false
        }

        if (lazy) {
            return true
        }

        return requestTime == newValue.requestTime &&
                requestIp == newValue.requestIp &&
                requestType == newValue.requestType &&
                requestUrl == newValue.requestUrl &&
                connection == newValue.connection &&
                responseTime == newValue.responseTime &&
                responseCode == newValue.responseCode &&
                responseData == newValue.responseData
    }

}

