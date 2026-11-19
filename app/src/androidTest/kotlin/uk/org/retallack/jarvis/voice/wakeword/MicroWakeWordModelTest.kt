package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.org.retallack.jarvis.microwakeword.MicroWakeWord
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Instrumented tests for the microWakeWord native engine.
 *
 * These are the only tests that actually exercise the JNI/TFLite-Micro path - a JVM unit
 * test cannot load an arm64 shared library. Mirrors Home Assistant's
 * `MicroWakeWordModelTest`.
 */
@RunWith(AndroidJUnit4::class)
class MicroWakeWordModelTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun loadModelBuffer(assetPath: String): ByteBuffer {
        val afd = appContext.assets.openFd(assetPath)
        return afd.use {
            val fis = FileInputStream(it.fileDescriptor)
            val mapped = fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                it.startOffset,
                it.declaredLength,
            )
            fis.close()
            mapped.order(ByteOrder.nativeOrder())
        }
    }

    @Test
    fun model_assets_are_present_and_parse() {
        assertTrue(
            "wake word assets missing from the APK",
            ModelPaths.verifyModelsExist(appContext),
        )

        val config = MicroWakeWordModelConfig.load(appContext, ModelPaths.WAKE_WORD_MANIFEST)
        assertTrue(config.wakeWord.isNotBlank())
        assertTrue(config.model.endsWith(".tflite"))
        assertTrue(config.micro.probabilityCutoff in 0f..1f)
        assertTrue(config.micro.slidingWindowSize > 0)
        assertTrue(config.micro.featureStepSize > 0)
    }

    @Test
    fun native_engine_loads_and_runs_without_crashing() {
        val config = MicroWakeWordModelConfig.load(appContext, ModelPaths.WAKE_WORD_MANIFEST)
        val buffer = loadModelBuffer(config.modelAssetPath(ModelPaths.WAKEWORD_ASSET_DIR))
        assertNotNull(buffer)

        MicroWakeWord(
            modelBuffer = buffer,
            featureStepSizeMs = config.micro.featureStepSize,
            probabilityCutoff = config.micro.probabilityCutoff,
            slidingWindowSize = config.micro.slidingWindowSize,
        ).use { detector ->
            // One 10 ms chunk of silence; must not crash and must not detect.
            assertFalse(detector.processAudio(ShortArray(160)))
        }
    }

    @Test
    fun silence_does_not_trigger_a_detection() {
        val config = MicroWakeWordModelConfig.load(appContext, ModelPaths.WAKE_WORD_MANIFEST)
        val buffer = loadModelBuffer(config.modelAssetPath(ModelPaths.WAKEWORD_ASSET_DIR))

        MicroWakeWord(
            modelBuffer = buffer,
            featureStepSizeMs = config.micro.featureStepSize,
            probabilityCutoff = config.micro.probabilityCutoff,
            slidingWindowSize = config.micro.slidingWindowSize,
        ).use { detector ->
            // 30 s of silence (3000 x 10 ms). The engine ignores its first slices, so this
            // also proves the warm-up window does not leak detections.
            val silent = ShortArray(160)
            repeat(3000) { index ->
                assertFalse("silence triggered a detection at chunk $index", detector.processAudio(silent))
            }
        }
    }

    @Test
    fun reset_and_close_are_safe_to_repeat() {
        val config = MicroWakeWordModelConfig.load(appContext, ModelPaths.WAKE_WORD_MANIFEST)
        val buffer = loadModelBuffer(config.modelAssetPath(ModelPaths.WAKEWORD_ASSET_DIR))

        val detector = MicroWakeWord(
            modelBuffer = buffer,
            featureStepSizeMs = config.micro.featureStepSize,
            probabilityCutoff = config.micro.probabilityCutoff,
            slidingWindowSize = config.micro.slidingWindowSize,
        )
        detector.processAudio(ShortArray(160))
        detector.reset()
        detector.reset()
        detector.processAudio(ShortArray(160))
        detector.close()
        detector.close()
    }
}
