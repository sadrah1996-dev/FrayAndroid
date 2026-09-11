package com.example.frayandroid

import android.R.drawable
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import vpncore.Vpncore
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("VpnServicePolicy")
class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        val isRunning = MutableStateFlow(false)
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        // 1. Post notification immediately
        startForeground(NOTIFICATION_ID, createNotification())
//        isRunning.value = true

        val uuid = intent?.getStringExtra("UUID") ?: ""
//        val fProtocol = "kcp"
        val protocol = intent?.getStringExtra("xPROTOCOL") ?: "vmess"
        val server = intent?.getStringExtra("SERVER") ?: "DE01"

        // 2. Start VPN interface & Go core
        serviceScope.launch {
            startVpn(uuid, "kcp", server, protocol)
        }

        return START_STICKY
    }

    private fun startVpn(uid: String, fProtocol: String, server: String, xProtocol: String) {
        try {
            // Establish the Android TUN interface
            vpnInterface = Builder()
                .setSession("FrayVPN")
                .addAddress("172.19.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .addDisallowedApplication(packageName)
                .establish()

            val fd = vpnInterface?.fd ?: return

            try {
                Vpncore.start(
                    fd.toLong(),
                    uid,
                    fProtocol,
                    server,
                    xProtocol,
                )
                isRunning.value = true
                serviceScope.launch {
                    kotlinx.coroutines.delay(1500.milliseconds)
                    val result = checkRealInternetViaProxy(localProxyPort = 30808)

                    if (result.isConnected) {
                        updateNotification(
                            "Connected: ${result.publicIp}",
                            drawable.presence_online
                        )
                    } else {
                        updateNotification(
                            "⚠️ No Internet Access",
                            drawable.presence_busy
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning.value = false
            }

        } catch (e: Exception) {
            isRunning.value = false
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(drawable.presence_away)
            .setContentTitle("VPN Connected")
            .setContentText("Your connection is secured.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()


    }

    private fun updateNotification(statusText: String, statusIcon: Int = drawable.presence_away) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(statusIcon)
            .setContentTitle("VPN Status")
            .setContentText(statusText)
            .setOngoing(true)
            .addAction(
                drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, MyVpnService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        manager.notify(NOTIFICATION_ID, updatedNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        Vpncore.stop()
        isRunning.value = false
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}