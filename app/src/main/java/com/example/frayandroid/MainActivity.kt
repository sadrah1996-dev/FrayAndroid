package com.example.frayandroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var inputUuid: EditText
    private lateinit var radioGroupProtocol: RadioGroup
    private lateinit var radioGroupServer: RadioGroup
    private lateinit var radioVMESS: RadioButton
    private lateinit var radioVLESS: RadioButton
    private lateinit var radioDE01: RadioButton
    private lateinit var radioNL01: RadioButton

    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var textVersion: TextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        checkVpnPermissionAndStart()
    }

    // Handles the system VPN permission dialog result
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputUuid = findViewById(R.id.inputUuid)
        radioGroupProtocol = findViewById(R.id.radioGroupProtocol)
        radioVMESS = findViewById(R.id.radioVmess)
        radioVLESS = findViewById(R.id.radioVless)
        radioGroupServer = findViewById(R.id.radioGroupServer)
        radioDE01 = findViewById(R.id.radioDE01)
        radioNL01 = findViewById(R.id.radioNL01)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        textVersion = findViewById(R.id.textVersion)

        setupVersionLabel()

        val prefs = getSharedPreferences("FrayVpnPrefs", MODE_PRIVATE)
        inputUuid.setText(prefs.getString("saved_uuid", "42c8f2ff-7c4f-4d2c-9e82-ecf59c6c0216"))

        // Use -1 as default to check if a preference exists, otherwise default to a specific radio ID
        val savedProtocolId = prefs.getInt("saved_protocol_id", -1)
        radioGroupProtocol.check(if (savedProtocolId != -1) savedProtocolId else R.id.radioVmess)

        val savedServerId = prefs.getInt("saved_server_id", -1)
        radioGroupServer.check(if (savedServerId != -1) savedServerId else R.id.radioDE01)

        // Save changes immediately when UI elements are modified
        inputUuid.doAfterTextChanged { text ->
            prefs.edit { putString("saved_uuid", text.toString()) }
        }

        radioGroupProtocol.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit { putInt("saved_protocol_id", checkedId) }
        }

        radioGroupServer.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit { putInt("saved_server_id", checkedId) }
        }

        lifecycleScope.launch {
            MyVpnService.isRunning.collectLatest { running ->
                setUiState(running)
            }
        }

        btnConnect.setOnClickListener {
            checkNotificationPermissionsAndStart()
        }

        btnDisconnect.setOnClickListener {
            val stopIntent = Intent(this, MyVpnService::class.java)
            stopIntent.action = "STOP"
            startService(stopIntent)
        }
    }

    private fun setupVersionLabel() {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

        val versionName = packageInfo.versionName ?: "0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        textVersion.text = getString(R.string.version_format, versionName, versionCode)
    }

    private fun checkNotificationPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkVpnPermissionAndStart()
    }

    private fun checkVpnPermissionAndStart() {
        // Check if we already have VPN permission
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Ask user for permission
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
            startVpnService()
        }
    }

    private fun setUiState(isRunning: Boolean) {
        val controlsEnabled = !isRunning
        radioGroupProtocol.children.forEach { it.isEnabled = controlsEnabled }
        radioGroupServer.children.forEach { it.isEnabled = controlsEnabled }
        if (::btnConnect.isInitialized) {
            btnConnect.isEnabled = controlsEnabled
        }
    }

    private fun startVpnService() {
        val startIntent = Intent(this, MyVpnService::class.java).apply {
            val protocol = when (radioGroupProtocol.checkedRadioButtonId) {
                R.id.radioVless -> "vless"
                else -> "vmess"
            }
            val server = when (radioGroupServer.checkedRadioButtonId) {
                R.id.radioDE01 -> "DE01"
                else -> "NL01"
            }

            putExtra("UUID", inputUuid.text.toString())
            putExtra("xPROTOCOL", protocol)
            putExtra("SERVER", server)
        }

        ContextCompat.startForegroundService(this, startIntent)
    }
}