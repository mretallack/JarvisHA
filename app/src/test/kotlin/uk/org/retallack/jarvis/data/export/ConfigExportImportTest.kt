package uk.org.retallack.jarvis.data.export

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.repository.ConnectionRepository

class ConfigExportImportTest {

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
    fun `serialization round-trip preserves data`() {
        val schema = ExportSchema(
            schemaVersion = 1,
            exportTimestamp = 1234567890L,
            appVersion = "1.0.0",
            connection = ExportConnection(url = "https://ha.example.com:8123"),
            favourites = listOf("light.living_room", "switch.kitchen"),
            aliases = listOf(
                ExportAlias("light.living_room", "lounge light"),
                ExportAlias("switch.kitchen", "kitchen switch"),
            ),
            settings = ExportSettings(
                wakeWordEnabled = true,
                sensitivity = 0.7f,
                quietHoursEnabled = true,
                quietHoursStart = "23:00",
                quietHoursEnd = "06:00",
                biometricEnabled = true,
            ),
        )

        val jsonString = exporter.exportToString(schema)
        val result = importer.validate(jsonString)

        assertTrue(result is ImportResult.Success)
        val imported = (result as ImportResult.Success).schema

        assertEquals(schema.schemaVersion, imported.schemaVersion)
        assertEquals(schema.connection?.url, imported.connection?.url)
        assertEquals(schema.favourites, imported.favourites)
        assertEquals(schema.aliases.size, imported.aliases.size)
        assertEquals("lounge light", imported.aliases[0].alias)
        assertEquals(schema.settings.sensitivity, imported.settings.sensitivity)
        assertEquals(schema.settings.quietHoursStart, imported.settings.quietHoursStart)
    }

    @Test
    fun `export excludes access token`() {
        val schema = ExportSchema(
            connection = ExportConnection(url = "https://ha.example.com:8123"),
        )

        val jsonString = exporter.exportToString(schema)

        // Token should not appear in export
        assertFalse(jsonString.contains("token"))
        assertFalse(jsonString.contains("access_token"))
    }

    @Test
    fun `validate rejects unsupported schema version`() {
        val jsonString = """{"schemaVersion": 99, "exportTimestamp": 0, "appVersion": "2.0.0"}"""
        val result = importer.validate(jsonString)

        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.contains("Unsupported schema version"))
    }

    @Test
    fun `validate rejects invalid JSON`() {
        val result = importer.validate("not valid json {{{")
        assertTrue(result is ImportResult.Error)
    }

    @Test
    fun `validate rejects empty string`() {
        val result = importer.validate("")
        assertTrue(result is ImportResult.Error)
    }

    @Test
    fun `validate accepts minimal valid schema`() {
        val jsonString = """{"schemaVersion": 1}"""
        val result = importer.validate(jsonString)
        assertTrue(result is ImportResult.Success)
    }

    @Test
    fun `apply restores favourites`() = runTest {
        val schema = ExportSchema(
            favourites = listOf("light.living_room", "switch.kitchen"),
        )

        importer.apply(schema)

        coVerify { entityDao.setFavourite("light.living_room", true) }
        coVerify { entityDao.setFavourite("switch.kitchen", true) }
    }

    @Test
    fun `apply restores aliases`() = runTest {
        val schema = ExportSchema(
            aliases = listOf(
                ExportAlias("light.living_room", "lounge"),
                ExportAlias("light.living_room", "main light"),
            ),
        )

        importer.apply(schema, mergeAliases = false)

        coVerify { aliasDao.deleteAll() }
        coVerify(exactly = 2) { aliasDao.insert(any()) }
    }

    @Test
    fun `apply with merge does not delete existing aliases`() = runTest {
        val schema = ExportSchema(
            aliases = listOf(ExportAlias("light.living_room", "lounge")),
        )

        importer.apply(schema, mergeAliases = true)

        coVerify(exactly = 0) { aliasDao.deleteAll() }
        coVerify { aliasDao.insert(any()) }
    }

    @Test
    fun `apply restores connection URL without token`() = runTest {
        val schema = ExportSchema(
            connection = ExportConnection(url = "https://ha.example.com:8123"),
        )

        importer.apply(schema)

        coVerify { connectionRepository.saveConnectionConfig("https://ha.example.com:8123", "") }
    }

    @Test
    fun `apply with null connection skips connection restore`() = runTest {
        val schema = ExportSchema(connection = null)

        importer.apply(schema)

        coVerify(exactly = 0) { connectionRepository.saveConnectionConfig(any(), any()) }
    }
}
