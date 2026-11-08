package org.devicelink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
        val startButton = Button(this).apply {
            text = "Avvia servizio"
            setOnClickListener {
                val intent = Intent(this@MainActivity, PeerService::class.java)
                    .setAction(PeerService.ACTION_START)
                startForegroundService(intent)
            }
        }
        val stopButton = Button(this).apply {
            text = "Arresta servizio"
            setOnClickListener {
                val intent = Intent(this@MainActivity, PeerService::class.java)
                    .setAction(PeerService.ACTION_STOP)
                startService(intent)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 32)
            addView(title)
            addView(explanation)
            addView(startButton)
            addView(stopButton)
        }
        setContentView(content)
    }
}
