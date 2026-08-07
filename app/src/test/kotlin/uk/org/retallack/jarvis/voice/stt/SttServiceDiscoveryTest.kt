package uk.org.retallack.jarvis.voice.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SttServiceDiscoveryTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var discovery: SttServiceDiscovery

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        discovery = SttServiceDiscovery(context)
    }

    @Test
    fun `getAvailableServices returns empty list when no services installed`() {
        every {
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns emptyList()

        val services = discovery.getAvailableServices()
        assertTrue(services.isEmpty())
    }

    @Test
    fun `getAvailableServices returns non-Google services`() {
        val resolveInfo = createResolveInfo("org.futo.voiceinput", "FutoVoiceService", "FUTO Voice Input")

        every {
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(resolveInfo)

        // No Play Services
        every {
            packageManager.getPackageInfo("com.google.android.gms", 0)
        } throws PackageManager.NameNotFoundException()

        val services = discovery.getAvailableServices()
        assertEquals(1, services.size)
        assertEquals("org.futo.voiceinput", services[0].packageName)
        assertEquals("FUTO Voice Input", services[0].label)
        assertEquals(
            ComponentName("org.futo.voiceinput", "FutoVoiceService"),
            services[0].componentName,
        )
    }

    @Test
    fun `getAvailableServices filters Google services when Play Services unavailable`() {
        val futoService = createResolveInfo("org.futo.voiceinput", "FutoVoiceService", "FUTO Voice Input")
        val googleService = createResolveInfo(
            "com.google.android.googlequicksearchbox",
            "GoogleRecognitionService",
            "Google Voice",
        )

        every {
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(futoService, googleService)

        // No Play Services
        every {
            packageManager.getPackageInfo("com.google.android.gms", 0)
        } throws PackageManager.NameNotFoundException()

        val services = discovery.getAvailableServices()
        assertEquals(1, services.size)
        assertEquals("org.futo.voiceinput", services[0].packageName)
    }

    @Test
    fun `getAvailableServices includes Google services when Play Services available`() {
        val futoService = createResolveInfo("org.futo.voiceinput", "FutoVoiceService", "FUTO Voice Input")
        val googleService = createResolveInfo(
            "com.google.android.googlequicksearchbox",
            "GoogleRecognitionService",
            "Google Voice",
        )

        every {
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(futoService, googleService)

        // Play Services available
        every {
            packageManager.getPackageInfo("com.google.android.gms", 0)
        } returns PackageInfo()

        val services = discovery.getAvailableServices()
        assertEquals(2, services.size)
    }

    @Test
    fun `getAvailableServices returns multiple non-Google services`() {
        val futoService = createResolveInfo("org.futo.voiceinput", "FutoVoiceService", "FUTO Voice Input")
        val voskService = createResolveInfo("org.vosk.demo", "VoskService", "Vosk STT")
        val whisperService = createResolveInfo("org.woheller69.whisper", "WhisperService", "Whisper")

        every {
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(futoService, voskService, whisperService)

        every {
            packageManager.getPackageInfo("com.google.android.gms", 0)
        } throws PackageManager.NameNotFoundException()

        val services = discovery.getAvailableServices()
        assertEquals(3, services.size)
        assertEquals("org.futo.voiceinput", services[0].packageName)
        assertEquals("org.vosk.demo", services[1].packageName)
        assertEquals("org.woheller69.whisper", services[2].packageName)
    }

    private fun createResolveInfo(
        packageName: String,
        serviceName: String,
        label: String,
    ): ResolveInfo {
        val si = ServiceInfo().apply {
            this.packageName = packageName
            this.name = serviceName
        }
        return ResolveInfo().apply {
            serviceInfo = si
            nonLocalizedLabel = label
        }
    }
}
