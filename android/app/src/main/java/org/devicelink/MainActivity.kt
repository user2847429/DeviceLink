package org.devicelink

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Temporary application entry point.
 *
 * The UI is intentionally minimal until the peer lifecycle and onboarding
 * flows have been designed.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = "DeviceLink\n\nEarly development build"
            textSize = 20f
            setPadding(32, 48, 32, 48)
        }
        setContentView(message)
    }
}
