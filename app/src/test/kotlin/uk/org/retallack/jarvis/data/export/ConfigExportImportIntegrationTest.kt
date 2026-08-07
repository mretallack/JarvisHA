package uk.org.retallack.jarvis.data.export

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.repository.ConnectionConfig
import uk.org.retallack.jarvis.data.repository.ConnectionRepository

/**
 * Integration test: export configuration, import it back, verify state matches.
 */
class ConfigExportImportIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private lateinit var connectionRepository: ConnectionRepository
    private lateinit var entityDao: EntityDao
    private lateinit var aliasDao: AliasDao
    private lateinit var exporter: ConfigExporter
    private lateinit var importer: ConfigImporter

    @BeforeEach
    fun setup() {
        connectionRepository = mockk(relaxed = true)
        entityDao = mockk(relaxed = true)
        aliasDao = mockk(relaxed = true)
        exporter = ConfigExporter(connectionRepository, entityDao, aliasDao, json)
        importer = ConfigImporter(connectionRepository, entityDao, aliasDao, json)
    }

    @Test
    fun `full export then import round-trip produces identical state`() = runTest {
        // Create a complete schema with all fields populated
        val originalSchema = ExportSchema(
            schemaVersion = 1,
            exportTimestamp = 1700000000000L,
            appVersion = "1.0.0",
            connection = ExportConnection(url = "https://ha.myhouse.com:8123"),
            favourites = listOf("light.living_room", "switch.kitchen", "climate.upstairs"),
            aliases = listOf(
                ExportAlias("light.living_room", "lounge light"),
                ExportAlias("light.living_room", "main light"),
                ExportAlias("switch.kitchen", "kitchen plug"),
                ExportAlias("climate.upstairs", "upstairs heating"),
            ),
            settings = ExportSettings(
                wakeWordEnabled = true,
                sensitivity = 0.75f,
                quietHoursEnabled = true,
                quietHoursStart = "23:30",
                quietHoursEnd = "06:30",
                biometricEnabled = true,
            ),
        )

        // Export to JSON string
        val exportedJson = exporter.exportToString(originalSchema)

        // Validate JSON is well-formed
        assertFalse(exportedJson.isBlank())
        assertTrue(exportedJson.contains("ha.myhouse.com"))
        assertTrue(exportedJson.contains("lounge light"))

        // Import: validate the JSON
        val importResult = importer.validate(exportedJson)
        assertTrue(importResult is ImportResult.Success)

        val importedSchema = (importResult as ImportResult.Success).schema

        // Verify all fields match
        assertEquals(originalSchema.schemaVersion, importedSchema.schemaVersion)
        assertEquals(originalSchema.exportTimestamp, importedSchema.exportTimestamp)
        assertEquals(originalSchema.appVersion, importedSchema.appVersion)
        assertEquals(originalSchema.connection?.url, importedSchema.connection?.url)
        assertEquals(originalSchema.favourites, importedSchema.favourites)
        assertEquals(originalSchema.aliases.size, importedSchema.aliases.size)

        // Verify individual aliases
        for (i in originalSchema.aliases.indices) {
            assertEquals(originalSchema.aliases[i].entityId, importedSchema.aliases[i].entityId)
            assertEquals(originalSchema.aliases[i].alias, importedSchema.aliases[i].alias)
        }

        // Verify settings
        assertEquals(originalSchema.settings.wakeWordEnabled, importedSchema.settings.wakeWordEnabled)
        assertEquals(originalSchema.settings.sensitivity, importedSchema.settings.sensitivity)
        assertEquals(originalSchema.settings.quietHoursEnabled, importedSchema.settings.quietHoursEnabled)
        assertEquals(originalSchema.settings.quietHoursStart, importedSchema.settings.quietHoursStart)
        assertEquals(originalSchema.settings.quietHoursEnd, importedSchema.settings.quietHoursEnd)
        assertEquals(originalSchema.settings.biometricEnabled, importedSchema.settings.biometricEnabled)
    }

    @Test
    fun `full round-trip apply restores all state correctly`() = runTest {
        val schema = ExportSchema(
            schemaVersion = 1,
            exportTimestamp = 1700000000000L,
            appVersion = "1.0.0",
            connection = ExportConnection(url = "https://ha.local:8123"),
            favourites = listOf("light.living_room", "switch.kitchen"),
            aliases = listOf(
                ExportAlias("light.living_room", "lounge"),
                ExportAlias("switch.kitchen", "kettle plug"),
            ),
            settings = ExportSettings(
                wakeWordEnabled = false,
                sensitivity = 0.3f,
                quietHoursEnabled = false,
                biometricEnabled = false,
            ),
        )

        // Export
        val jsonString = exporter.exportToString(schema)

        // Import and validate
        val result = importer.validate(jsonString)
        assertTrue(result is ImportResult.Success)
        val importedSchema = (result as ImportResult.Success).schema

        // Apply the imported schema
        importer.apply(importedSchema, mergeAliases = false)

        // Verify connection was restored (URL only, no token)
        coVerify { connectionRepository.saveConnectionConfig("https://ha.local:8123", "") }

        // Verify favourites were restored
        coVerify { entityDao.setFavourite("light.living_room", true) }
        coVerify { entityDao.setFavourite("switch.kitchen", true) }

        // Verify aliases were cleared and re-inserted
        coVerify { aliasDao.deleteAll() }

        val aliasSlot = mutableListOf<AliasDb>()
        coVerify(exactly = 2) { aliasDao.insert(capture(aliasSlot)) }
        assertEquals("light.living_room", aliasSlot[0].entityId)
        assertEquals("lounge", aliasSlot[0].alias)
        assertEquals("switch.kitchen", aliasSlot[1].entityId)
        assertEquals("kettle plug", aliasSlot[1].alias)
    }

    @Test
    fun `export with empty state produces valid importable JSON`() = runTest {
        val emptySchema = ExportSchema(
            schemaVersion = 1,
            connection = null,
            favourites = emptyList(),
            aliases = emptyList(),
        )

        val jsonString = exporter.exportToString(emptySchema)
        val result = importer.validate(jsonString)

        assertTrue(result is ImportResult.Success)
        val imported = (result as ImportResult.Success).schema
        assertEquals(1, imported.schemaVersion)
        assertTrue(imported.favourites.isEmpty())
        assertTrue(imported.aliases.isEmpty())
        assertNotNull(imported.settings)
    }

    @Test
    fun `round-trip preserves special characters in aliases`() = runTest {
        val schema = ExportSchema(
            aliases = listOf(
                ExportAlias("light.living_room", "salon lumière"),
                ExportAlias("switch.kitchen", "küche schalter"),
                ExportAlias("light.office", "オフィスライト"),
                ExportAlias("light.garden", "luz del jardín"),
            ),
        )

        val jsonString = exporter.exportToString(schema)
        val result = importer.validate(jsonString)

        assertTrue(result is ImportResult.Success)
        val imported = (result as ImportResult.Success).schema
        assertEquals(4, imported.aliases.size)
        assertEquals("salon lumière", imported.aliases[0].alias)
        assertEquals("küche schalter", imported.aliases[1].alias)
        assertEquals("オフィスライト", imported.aliases[2].alias)
        assertEquals("luz del jardín", imported.aliases[3].alias)
    }

    @Test
    fun `round-trip with merge preserves existing aliases`() = runTest {
        val schema = ExportSchema(
            aliases = listOf(
                ExportAlias("light.new", "new alias"),
            ),
        )

        val jsonString = exporter.exportToString(schema)
        val result = importer.validate(jsonString)
        assertTrue(result is ImportResult.Success)

        importer.apply((result as ImportResult.Success).schema, mergeAliases = true)

        // Should NOT delete existing aliases
        coVerify(exactly = 0) { aliasDao.deleteAll() }
        // Should still insert new ones
        coVerify { aliasDao.insert(match { it.alias == "new alias" }) }
    }

    @Test
    fun `multiple export-import cycles produce consistent results`() = runTest {
        val schema = ExportSchema(
            schemaVersion = 1,
            exportTimestamp = 1700000000000L,
            appVersion = "1.0.0",
            connection = ExportConnection(url = "https://ha.test.com"),
            favourites = listOf("light.a", "light.b"),
            aliases = listOf(
                ExportAlias("light.a", "alias1"),
                ExportAlias("light.b", "alias2"),
            ),
        )

        // First cycle
        val json1 = exporter.exportToString(schema)
        val result1 = importer.validate(json1)
        assertTrue(result1 is ImportResult.Success)

        // Re-export
        val json2 = exporter.exportToString((result1 as ImportResult.Success).schema)
        val result2 = importer.validate(json2)
        assertTrue(result2 is ImportResult.Success)

        // Third cycle
        val json3 = exporter.exportToString((result2 as ImportResult.Success).schema)
        val result3 = importer.validate(json3)
        assertTrue(result3 is ImportResult.Success)

        // All should be identical in content
        val final = (result3 as ImportResult.Success).schema
        assertEquals(schema.connection?.url, final.connection?.url)
        assertEquals(schema.favourites, final.favourites)
        assertEquals(schema.aliases.size, final.aliases.size)
    }

    @Test
    fun `token is never included in exported JSON`() = runTest {
        // Even if connection config has a token, export should exclude it
        coEvery { connectionRepository.getConnectionConfig() } returns
            ConnectionConfig(url = "https://ha.test.com", token = "super-secret-token-12345")

        val schema = ExportSchema(
            connection = ExportConnection(url = "https://ha.test.com"),
        )
        val jsonString = exporter.exportToString(schema)

        assertFalse(jsonString.contains("super-secret-token"))
        assertFalse(jsonString.contains("token"))
    }
}
