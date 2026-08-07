package uk.org.retallack.jarvis.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SensitiveDomainCheckerTest {

    private lateinit var checker: SensitiveDomainChecker

    @BeforeEach
    fun setup() {
        checker = SensitiveDomainChecker()
    }

    @Test
    fun `lock domain is sensitive by default`() {
        assertTrue(checker.isSensitive("lock.front_door"))
    }

    @Test
    fun `alarm_control_panel is sensitive by default`() {
        assertTrue(checker.isSensitive("alarm_control_panel.home"))
    }

    @Test
    fun `cover domain is sensitive by default`() {
        assertTrue(checker.isSensitive("cover.garage_door"))
    }

    @Test
    fun `light domain is not sensitive by default`() {
        assertFalse(checker.isSensitive("light.living_room"))
    }

    @Test
    fun `switch domain is not sensitive by default`() {
        assertFalse(checker.isSensitive("switch.kitchen"))
    }

    @Test
    fun `isDomainSensitive works for domains`() {
        assertTrue(checker.isDomainSensitive("lock"))
        assertFalse(checker.isDomainSensitive("light"))
    }

    @Test
    fun `custom domain can be added`() {
        assertFalse(checker.isSensitive("climate.living_room"))
        checker.addDomain("climate")
        assertTrue(checker.isSensitive("climate.living_room"))
    }

    @Test
    fun `custom domain can be removed`() {
        checker.addDomain("climate")
        assertTrue(checker.isSensitive("climate.living_room"))
        checker.removeDomain("climate")
        assertFalse(checker.isSensitive("climate.living_room"))
    }

    @Test
    fun `cannot remove default sensitive domains via removeDomain`() {
        checker.removeDomain("lock")
        // Still sensitive because it's a default, not custom
        assertTrue(checker.isSensitive("lock.front_door"))
    }

    @Test
    fun `setCustomDomains replaces custom list`() {
        checker.addDomain("climate")
        checker.setCustomDomains(setOf("camera"))
        assertFalse(checker.isSensitive("climate.living_room"))
        assertTrue(checker.isSensitive("camera.front"))
        // Defaults still work
        assertTrue(checker.isSensitive("lock.front_door"))
    }

    @Test
    fun `getSensitiveDomains returns defaults and custom`() {
        checker.addDomain("camera")
        val domains = checker.getSensitiveDomains()
        assertTrue(domains.contains("lock"))
        assertTrue(domains.contains("alarm_control_panel"))
        assertTrue(domains.contains("cover"))
        assertTrue(domains.contains("camera"))
    }
}
