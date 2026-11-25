package com.example.agricultureoffline.sms

import com.example.agricultureoffline.BuildConfig
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

data class OtpApiResult(
    val ok: Boolean,
    val code: String,
    val message: String
)

class OtpRemoteDataSource {

    fun sendLoginSuccessNotification(
        countryCode: String,
        nationalNumber: String,
        date: String,
        time: String,
        requestId: String? = null
    ): OtpApiResult {
        val payload = JSONObject().apply {
            put("countryCode", countryCode)
            put("phone", nationalNumber)
            put("date", date)
            put("time", time)
            if (!requestId.isNullOrBlank()) {
                put("requestId", requestId)
            }
        }
        val res = post("/notify/welcome", payload)
        if (res.code == "not_found") {
            return post("/notify/login-success", payload)
        }
        return res
    }

    private fun post(path: String, body: JSONObject): OtpApiResult {
        val base = BuildConfig.OTP_BASE_URL.trim().trimEnd('/')
        if (base.isBlank()) {
            return OtpApiResult(false, "not_configured", "SMS backend URL is not set.")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            OtpApiResult(
                ok = json.optBoolean("ok", status in 200..299),
                code = json.optString("code", if (status == 404) "not_found" else if (status in 200..299) "ok" else "error"),
                message = json.optString("message", connection.responseMessage ?: "")
            )
        } catch (_: UnknownHostException) {
            OtpApiResult(false, "network", "Cannot reach SMS backend.")
        } catch (error: Exception) {
            OtpApiResult(false, "network", error.message ?: "SMS request failed.")
        } finally {
            connection?.disconnect()
        }
    }
}
