package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private var androidContext: Context? = null

fun initNetworkMonitor(context: Context) {
    androidContext = context
}

actual class NetworkMonitor {
    actual suspend fun isActuallyConnected(): Boolean = withContext(Dispatchers.IO) {
        val context = androidContext ?: return@withContext false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@withContext false
            
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 2000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
