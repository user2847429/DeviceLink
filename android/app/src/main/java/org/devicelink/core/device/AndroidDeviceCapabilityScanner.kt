package org.devicelink.core.device

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult

/**
 * Reads only capabilities explicitly exposed by Android's package manager and Wi-Fi framework.
 * It does not infer support from the connected network or from nearby devices.
 */
data class DeviceCapability(
    val category: String,
    val name: String,
    val supported: Boolean?,
    val detail: String
)

object AndroidDeviceCapabilityScanner {

    fun scan(context: Context): List<DeviceCapability> {
        val pm = context.packageManager
        val result = mutableListOf<DeviceCapability>()

        fun feature(category: String, name: String, feature: String, detail: String = "") {
            val supported = pm.hasSystemFeature(feature)
            result += DeviceCapability(
                category = category,
                name = name,
                supported = supported,
                detail = detail.ifBlank {
                    if (supported) "Funzionalità dichiarata da Android." else
                        "Android non dichiara questa funzionalità."
                }
            )
        }

        feature("Rete", "Wi-Fi", PackageManager.FEATURE_WIFI)
        feature("Rete", "Wi-Fi Direct (P2P)", PackageManager.FEATURE_WIFI_DIRECT)
        feature("Bluetooth", "Bluetooth Classic", PackageManager.FEATURE_BLUETOOTH)
        feature("Bluetooth", "Bluetooth Low Energy (BLE)", PackageManager.FEATURE_BLUETOOTH_LE)
        feature("Connettività", "NFC", PackageManager.FEATURE_NFC)
        feature("Hardware", "USB host", PackageManager.FEATURE_USB_HOST)
        feature("Hardware", "USB accessory", PackageManager.FEATURE_USB_ACCESSORY)
        feature("Hardware", "Fotocamera", PackageManager.FEATURE_CAMERA_ANY)
        feature("Posizione", "GPS", PackageManager.FEATURE_LOCATION_GPS)

        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wifi != null && pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            result += DeviceCapability(
                "Wi-Fi", "Banda 2,4 GHz", wifi.is24GHzBandSupported,
                if (wifi.is24GHzBandSupported) "Supportata dal chipset Wi-Fi." else
                    "Non supportata dal chipset Wi-Fi."
            )
            result += DeviceCapability(
                "Wi-Fi", "Banda 5 GHz", wifi.is5GHzBandSupported,
                if (wifi.is5GHzBandSupported) "Supportata dal framework Wi-Fi." else
                    "Non dichiarata come supportata dal framework Wi-Fi."
            )
            result += DeviceCapability(
                "Wi-Fi", "Banda 6 GHz", wifi.is6GHzBandSupported,
                if (wifi.is6GHzBandSupported) "Supportata dal framework Wi-Fi." else
                    "Non dichiarata come supportata dal framework Wi-Fi."
            )
            result += DeviceCapability(
                "Wi-Fi", "Banda 60 GHz (WiGig)", wifi.is60GHzBandSupported,
                if (wifi.is60GHzBandSupported) "Supportata dal framework Wi-Fi." else
                    "Non dichiarata come supportata dal framework Wi-Fi."
            )
            listOf(
                ScanResult.WIFI_STANDARD_11N to "IEEE 802.11n (Wi-Fi 4)",
                ScanResult.WIFI_STANDARD_11AC to "IEEE 802.11ac (Wi-Fi 5)",
                ScanResult.WIFI_STANDARD_11AX to "IEEE 802.11ax (Wi-Fi 6)",
                ScanResult.WIFI_STANDARD_11AD to "IEEE 802.11ad (WiGig)",
                ScanResult.WIFI_STANDARD_11BE to "IEEE 802.11be (Wi-Fi 7)"
            ).forEach { (standard, label) ->
                val supported = wifi.isWifiStandardSupported(standard)
                result += DeviceCapability(
                    "Wi-Fi", label, supported,
                    if (supported) "Standard dichiarato supportato dal chipset/framework." else
                        "Standard non dichiarato come supportato."
                )
            }
            result += DeviceCapability(
                "Wi-Fi", "Concorrenza Wi-Fi STA + hotspot AP",
                wifi.isStaApConcurrencySupported,
                "Supporto dichiarato per usare il dispositivo come client Wi-Fi e hotspot contemporaneamente."
            )
            result += DeviceCapability(
                "Wi-Fi", "Concorrenza Wi-Fi STA + rete locale secondaria",
                wifi.isStaConcurrencyForLocalOnlyConnectionsSupported,
                "Supporto Android per una seconda connessione Wi-Fi locale tramite WifiNetworkSpecifier; non equivale a garantire STA + Wi-Fi Direct."
            )
        }

        return result
    }
}
