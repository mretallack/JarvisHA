package uk.org.retallack.jarvis.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domains that require biometric authentication before control.
 */
@Singleton
class SensitiveDomainChecker @Inject constructor() {

    private val defaultSensitiveDomains = setOf(
        "lock",
        "alarm_control_panel",
        "cover",
    )

    private var customSensitiveDomains: Set<String> = emptySet()

    /**
     * Check if an entity requires biometric auth.
     * @param entityId full entity ID (e.g., "lock.front_door")
     */
    fun isSensitive(entityId: String): Boolean {
        val domain = entityId.substringBefore(".")
        return domain in getSensitiveDomains()
    }

    /**
     * Check if a domain is marked as sensitive.
     */
    fun isDomainSensitive(domain: String): Boolean {
        return domain in getSensitiveDomains()
    }

    /**
     * Get all currently configured sensitive domains.
     */
    fun getSensitiveDomains(): Set<String> {
        return defaultSensitiveDomains + customSensitiveDomains
    }

    /**
     * Set custom sensitive domains (in addition to defaults).
     */
    fun setCustomDomains(domains: Set<String>) {
        customSensitiveDomains = domains
    }

    /**
     * Add a domain to the sensitive list.
     */
    fun addDomain(domain: String) {
        customSensitiveDomains = customSensitiveDomains + domain
    }

    /**
     * Remove a domain from the custom sensitive list.
     * Cannot remove default sensitive domains.
     */
    fun removeDomain(domain: String) {
        customSensitiveDomains = customSensitiveDomains - domain
    }
}
