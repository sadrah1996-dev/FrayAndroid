@file:Suppress("DEPRECATION")

package com.example.frayandroid

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VpnTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            MyVpnService.isRunning.collectLatest { running ->
                updateTileState(running)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onClick() {
        super.onClick()
        val isRunning = MyVpnService.isRunning.value

        if (isRunning) {
            val stopIntent = Intent(this, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                // VPN permission is required, open MainActivity
                launchMainActivity()
            } else {
                startVpnWithSavedPrefs()
            }
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun startVpnWithSavedPrefs() {
        val prefs = getSharedPreferences("FrayVpnPrefs", MODE_PRIVATE)
        val uuid = prefs.getString("saved_uuid", "42c8f2ff-7c4f-4d2c-9e82-ecf59c6c0216") ?: ""
        val protocolId = prefs.getInt("saved_protocol_id", R.id.radioVmess)
        val serverId = prefs.getInt("saved_server_id", R.id.radioDE01)

        val protocol = when (protocolId) {
            R.id.radioVless -> "vless"
            else -> "vmess"
        }
        val server = when (serverId) {
            R.id.radioDE01 -> "DE01"
            else -> "NL01"
        }

        val startIntent = Intent(this, MyVpnService::class.java).apply {
            putExtra("UUID", uuid)
            putExtra("xPROTOCOL", protocol)
            putExtra("SERVER", server)
        }

        ContextCompat.startForegroundService(this, startIntent)
    }

    private fun updateTileState(isRunning: Boolean) {
        val tile = qsTile ?: return
        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "FRay VPN"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Connected"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "FRay VPN"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Disconnected"
            }
        }
        tile.updateTile()
    }
}