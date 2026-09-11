package com.example.frayandroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class ConnectionResult(val isConnected: Boolean, val publicIp: String?)

suspend fun checkRealInternetViaProxy(localProxyPort: Int = 30808): ConnectionResult = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val url = URL("https://icanhazip.com")

        // Use SOCKS (or Proxy.Type.HTTP if your inbound is HTTP)
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localProxyPort))

        connection = (url.openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", "curl/7.88.1")
            setRequestProperty("Connection", "close")
        }

        if (connection.responseCode == 200) {
            val ip = connection.inputStream.bufferedReader().use { it.readLine()?.trim() }
            ConnectionResult(isConnected = !ip.isNullOrEmpty(), publicIp = ip)
        } else {
            ConnectionResult(isConnected = false, publicIp = null)
        }
    } catch (_: Exception) {
        ConnectionResult(isConnected = false, publicIp = null)
    } finally {
        connection?.disconnect()
    }
}