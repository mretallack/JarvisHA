package uk.org.retallack.jarvis.voice.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.speech.RecognitionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents an installed speech recognition service.
 */
data class SttServiceInfo(
    val packageName: String,
    val label: String,
    val componentName: ComponentName,
)

/**
 * Discovers installed speech recognition services on the device.
 * Filters out Google-specific services when Play Services are not available.
 */
@Singleton
class SttServiceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private val GOOGLE_PACKAGES = setOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.tts",
            "com.google.android.apps.speechservices",
        )
    }

    /**
     * Returns all available STT services installed on the device.
     * Filters out Google services if Play Services are not present.
     */
    fun getAvailableServices(): List<SttServiceInfo> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val resolveInfos = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_META_DATA,
        )

        val hasPlayServices = isPlayServicesAvailable()

        return resolveInfos
            .filter { resolveInfo ->
                val pkg = resolveInfo.serviceInfo.packageName
                // Include if not a Google package, or if Play Services are available
                !GOOGLE_PACKAGES.contains(pkg) || hasPlayServices
            }
            .map { resolveInfo -> resolveInfo.toSttServiceInfo() }
    }

    /**
     * Check if Google Play Services are installed and available.
     */
    private fun isPlayServicesAvailable(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun ResolveInfo.toSttServiceInfo(): SttServiceInfo {
        val serviceInfo = this.serviceInfo
        val label = this.loadLabel(context.packageManager).toString()
        val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
        return SttServiceInfo(
            packageName = serviceInfo.packageName,
            label = label,
            componentName = componentName,
        )
    }
}
