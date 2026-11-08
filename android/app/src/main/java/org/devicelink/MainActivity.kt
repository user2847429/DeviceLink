package org.devicelink

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.devicelink.core.peer.PeerService

/**
 * Initial DeviceLink controls and mDNS peer discovery UI. Peer connections are not implemented yet.
 */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var startAfterPermissionGrant = false
    private var showingPermissionScreen = false
    private var peerReceiverRegistered = false
    private val discoveredPeers = linkedMapOf<String, String>()
    private var devicesList: LinearLayout? = null
    private var devicesStatus: TextView? = null

    private val peerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PeerService.ACTION_PEER_FOUND -> {
                    val name = intent.getStringExtra(PeerService.EXTRA_SERVICE_NAME) ?: return
                    val host = intent.getStringExtra(PeerService.EXTRA_HOST).orEmpty()
                    val port = intent.getIntExtra(PeerService.EXTRA_PORT, 0)
                    discoveredPeers[name] = name + "\n" + host + ":" + port
                    renderDiscoveredPeers()
                }
                PeerService.ACTION_PEER_LOST -> {
                    intent.getStringExtra(PeerService.EXTRA_SERVICE_NAME)?.let(discoveredPeers::remove)
                    renderDiscoveredPeers()
                }
                PeerService.ACTION_DISCOVERY_ERROR -> {
                    devicesStatus?.text = intent.getStringExtra(PeerService.EXTRA_MESSAGE)
                        ?: "Errore durante la ricerca."
                }
                PeerService.ACTION_DISCOVERY_STATUS -> {
                    if (discoveredPeers.isEmpty()) {
                        devicesStatus?.text = intent.getStringExtra(PeerService.EXTRA_MESSAGE)
                            ?: "Ricerca in corso…"
                    }
                }
            }
        }
    }

    private val preferences by lazy {
        getSharedPreferences("devicelink_setup", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
        requestFirstLaunchNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) {
            if (showingPermissionScreen) showPermissionScreen() else updateHomeStatus()
        }
    }

    private fun createRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(32, 48, 32, 32)
    }

    private fun addText(parent: LinearLayout, value: String, size: Float = 16f): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, 8, 0, 8)
            parent.addView(this)
        }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
    }

    private fun showHome() {
        unregisterPeerReceiver()
        showingPermissionScreen = false
        root = createRoot()
        addText(root, "DeviceLink", 26f)
        addText(
            root,
            "Servizio locale in fase iniziale.\n\nL'avvio mostra una notifica persistente. Puoi cercare altri dispositivi DeviceLink sulla rete locale; il collegamento sicuro arriverà in una fase successiva."
        )
        status = addText(root, "")
        addButton(root, "Avvia servizio") { requestNotificationPermissionAndStart() }
        addButton(root, "Trova dispositivi") { requestLocalNetworkPermissionAndDiscover() }
        addButton(root, "Arresta servizio") {
            val intent = Intent(this, PeerService::class.java).setAction(PeerService.ACTION_STOP)
            startService(intent)
            status.text = "Richiesta di arresto inviata."
        }
        addButton(root, "Impostazioni") { showSettings() }
        setContentView(root)
        updateHomeStatus()
    }

    private fun showSettings() {
        unregisterPeerReceiver()
        showingPermissionScreen = false
        root = createRoot()
        addText(root, "Impostazioni", 24f)
        addText(root, "Gestisci le autorizzazioni e le funzioni locali di DeviceLink.")
        addButton(root, "Controllo permessi") { showPermissionScreen() }
        addButton(root, "Dispositivi") { requestLocalNetworkPermissionAndDiscover() }
        addButton(root, "App installate") { showInstalledApps() }
        addButton(root, "Impostazioni Android") { openAppSettings() }
        addButton(root, "Torna alla schermata principale") { showHome() }
        setContentView(root)
    }

    private fun showInstalledApps() {
        unregisterPeerReceiver()
        showingPermissionScreen = false
        root = createRoot()
        addText(root, "App installate", 24f)
        addText(
            root,
            "Elenco delle app che Android rende visibili e avviabili. Cerca per nome o identificativo; tocca un risultato per aprirlo."
        )

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val search = EditText(this).apply {
            hint = "Cerca un'app"
            setSingleLine(true)
        }
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val count = addText(root, "")
        val scrollView = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(list)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        fun renderApps(filter: String) {
            list.removeAllViews()
            val matches = activities.filter { info ->
                val label = info.loadLabel(packageManager).toString()
                label.contains(filter, ignoreCase = true) ||
                    info.activityInfo.packageName.contains(filter, ignoreCase = true)
            }
            count.text = if (filter.isBlank()) {
                "${activities.size} app visibili"
            } else {
                "${matches.size} risultati su ${activities.size}"
            }

            if (matches.isEmpty()) {
                addText(list, "Nessuna app trovata.")
                return
            }

            matches.forEach { resolveInfo ->
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val packageName = resolveInfo.activityInfo.packageName
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    addButton(list, appName) {
                        try {
                            startActivity(launchIntent)
                        } catch (_: Exception) {
                            count.text = "Impossibile aprire $appName."
                        }
                    }
                    addText(list, packageName, 12f)
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty().trim())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderApps("")

        addButton(root, "Indietro") { showSettings() }
        setContentView(root)
    }

    private fun showPermissionScreen() {
        unregisterPeerReceiver()
        showingPermissionScreen = true
        root = createRoot()
        addText(root, "Controllo permessi", 24f)
        addText(root, "Qui puoi verificare le autorizzazioni di Android e concederle quando servono. DeviceLink non chiede accessi non ancora necessari.")
        addPermissionRow(
            "Notifiche",
            Manifest.permission.POST_NOTIFICATIONS,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            "Serve per mostrare lo stato del servizio in primo piano."
        )
        addPermissionRow(
            "Rete locale",
            "android.permission.ACCESS_LOCAL_NETWORK",
            Build.VERSION.SDK_INT >= 37,
            "Servirà per trovare e collegare gli altri dispositivi nella rete Wi-Fi o hotspot."
        )
        addText(root, "Le autorizzazioni possono essere revocate in qualsiasi momento dalle impostazioni Android.")
        addButton(root, "Impostazioni Android") { openAppSettings() }
        addButton(root, "Indietro") { showSettings() }
        setContentView(root)
    }


    private fun requestLocalNetworkPermissionAndDiscover() {
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf("android.permission.ACCESS_LOCAL_NETWORK"),
                REQUEST_LOCAL_NETWORK_PERMISSION
            )
            return
        }
        showDevicesScreen()
    }

    private fun showDevicesScreen() {
        showingPermissionScreen = false
        discoveredPeers.clear()
        root = createRoot()
        addText(root, "Dispositivi", 24f)
        addText(root, "Ricerca nella rete Wi-Fi o nell'hotspot. I dispositivi trovati non sono ancora collegati: pairing e cifratura saranno aggiunti dopo.")
        devicesStatus = addText(root, "Avvio del servizio di ricerca…")
        val scrollView = ScrollView(this)
        devicesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(devicesList)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        addButton(root, "Aggiorna ricerca") {
            startForegroundService(
                Intent(this, PeerService::class.java).setAction(PeerService.ACTION_DISCOVER)
            )
            devicesStatus?.text = "Riavvio della ricerca in corso…"
        }
        addButton(root, "Indietro") { showHome() }
        setContentView(root)
        registerPeerReceiver()
        startForegroundService(Intent(this, PeerService::class.java).setAction(PeerService.ACTION_DISCOVER))
    }

    private fun registerPeerReceiver() {
        if (peerReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(PeerService.ACTION_PEER_FOUND)
            addAction(PeerService.ACTION_PEER_LOST)
            addAction(PeerService.ACTION_DISCOVERY_ERROR)
            addAction(PeerService.ACTION_DISCOVERY_STATUS)
        }
        registerReceiver(peerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        peerReceiverRegistered = true
    }

    private fun unregisterPeerReceiver() {
        if (!peerReceiverRegistered) return
        unregisterReceiver(peerReceiver)
        peerReceiverRegistered = false
        devicesList = null
        devicesStatus = null
    }

    private fun renderDiscoveredPeers() {
        val list = devicesList ?: return
        list.removeAllViews()
        if (discoveredPeers.isEmpty()) {
            // Keep the latest diagnostic status while discovery is still running.
            return
        }
        devicesStatus?.text = "Trovati " + discoveredPeers.size + " dispositivi."
        discoveredPeers.values.forEach { details -> addText(list, details) }
    }

    override fun onDestroy() {
        unregisterPeerReceiver()
        super.onDestroy()
    }

    private fun addPermissionRow(
        title: String,
        permission: String,
        supported: Boolean,
        explanation: String
    ) {
        addText(root, title, 19f)
        addText(root, explanation, 14f)
        if (!supported) {
            addText(root, "Non richiesta da questa versione di Android.", 14f)
            return
        }

        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        addText(root, if (granted) "Stato: consentito" else "Stato: non consentito", 14f)
        if (!granted) {
            addButton(root, "Concedi: $title") {
                requestPermissions(arrayOf(permission), REQUEST_DASHBOARD_PERMISSION)
            }
        }
    }

    private fun requestFirstLaunchNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (preferences.getBoolean(KEY_FIRST_PERMISSION_CHECK, false)) return

        // Mark before showing the system dialog so a denial does not trigger a prompt on every launch.
        preferences.edit().putBoolean(KEY_FIRST_PERMISSION_CHECK, true).apply()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_FIRST_LAUNCH_PERMISSION
            )
        }
    }

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startAfterPermissionGrant = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_START_PERMISSION)
            return
        }
        startServiceIfNotificationsEnabled()
    }

    private fun startServiceIfNotificationsEnabled() {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            status.text = "Le notifiche di DeviceLink sono disattivate. Puoi riattivarle nelle impostazioni dell'app."
            openNotificationSettings()
            return
        }
        val intent = Intent(this, PeerService::class.java).setAction(PeerService.ACTION_START)
        startForegroundService(intent)
        status.text = "Servizio avviato. La notifica DeviceLink dovrebbe essere visibile."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            REQUEST_START_PERMISSION -> {
                val shouldStart = startAfterPermissionGrant
                startAfterPermissionGrant = false
                if (granted && shouldStart) startServiceIfNotificationsEnabled()
                else if (!granted) status.text = "Permesso notifiche negato. Puoi modificarlo da Impostazioni."
            }
            REQUEST_FIRST_LAUNCH_PERMISSION -> {
                if (::status.isInitialized) {
                    status.text = if (granted) "Permesso notifiche concesso." else
                        "Permesso notifiche non concesso. Puoi modificarlo da Impostazioni."
                }
            }
            REQUEST_DASHBOARD_PERMISSION -> showPermissionScreen()
            REQUEST_LOCAL_NETWORK_PERMISSION -> {
                if (granted) showDevicesScreen() else showPermissionScreen()
            }
        }
    }

    private fun updateHomeStatus() {
        if (!::status.isInitialized) return
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        status.text = when {
            !notificationPermissionGranted -> "Notifiche: autorizzazione non concessa."
            !notificationsEnabled -> "Notifiche: disattivate nelle impostazioni Android."
            else -> "Notifiche: autorizzate."
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    companion object {
        private const val KEY_FIRST_PERMISSION_CHECK = "first_permission_check_done"
        private const val REQUEST_FIRST_LAUNCH_PERMISSION = 4101
        private const val REQUEST_START_PERMISSION = 4102
        private const val REQUEST_DASHBOARD_PERMISSION = 4103
        private const val REQUEST_LOCAL_NETWORK_PERMISSION = 4104
    }
}
