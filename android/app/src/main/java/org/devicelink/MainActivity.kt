package org.devicelink

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.devicelink.core.peer.PeerService

/**
 * Temporary controls for the Android peer service lifecycle.
 * Device discovery and peer connections are not implemented yet.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private var startAfterPermissionGrant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "DeviceLink"
            textSize = 26f
        }
        val explanation = TextView(this).apply {
            text = "Servizio locale in fase iniziale.\n\nL'avvio mostra una notifica persistente. Non cerca ancora altri dispositivi e non crea connessioni."
            textSize = 16f
        }
        status = TextView(this).apply {
            textSize = 15f
        }
        val startButton = Button(this).apply {
            text = "Avvia servizio"
            setOnClickListener { requestNotificationPermissionAndStart() }
        }
        val stopButton = Button(this).apply {
            text = "Arresta servizio"
            setOnClickListener {
                val intent = Intent(this@MainActivity, PeerService::class.java)
                    .setAction(PeerService.ACTION_STOP)
                startService(intent)
                status.text = "Richiesta di arresto inviata."
            }
        }
        val settingsButton = Button(this).apply {
            text = "Impostazioni notifiche"
            setOnClickListener { openNotificationSettings() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 32)
            addView(title)
            addView(explanation)
            addView(status)
            addView(startButton)
            addView(stopButton)
            addView(settingsButton)
        }
        setContentView(content)
        updateNotificationStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateNotificationStatus()
    }

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startAfterPermissionGrant = true
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
            return
        }
        startServiceIfNotificationsEnabled()
    }

    private fun startServiceIfNotificationsEnabled() {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            status.text = "Le notifiche di DeviceLink sono disattivate. Abilita nelle impostazioni e riprova."
            openNotificationSettings()
            return
        }
        val intent = Intent(this, PeerService::class.java)
            .setAction(PeerService.ACTION_START)
        startForegroundService(intent)
        status.text = "Servizio avviato. La notifica DeviceLink dovrebbe essere visibile."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return

        val shouldStart = startAfterPermissionGrant
        startAfterPermissionGrant = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            status.text = "Permesso notifiche concesso."
            if (shouldStart) startServiceIfNotificationsEnabled()
        } else {
            status.text = "Permesso notifiche negato. DeviceLink non può mostrare la notifica nel pannello."
        }
    }

    private fun updateNotificationStatus() {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled()
        status.text = when {
            !permissionGranted -> "Notifiche non autorizzate: premi «Avvia servizio» per concedere il permesso."
            !notificationsEnabled -> "Notifiche disattivate nelle impostazioni di Android."
            else -> "Permesso notifiche attivo."
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 4101
    }
}
