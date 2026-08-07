package uk.org.retallack.jarvis.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportSchema(
    val schemaVersion: Int = 1,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val connection: ExportConnection? = null,
    val favourites: List<String> = emptyList(),
    val aliases: List<ExportAlias> = emptyList(),
    val settings: ExportSettings = ExportSettings(),
)

@Serializable
data class ExportConnection(
    val url: String,
    // Token is intentionally EXCLUDED from export for security
)

@Serializable
data class ExportAlias(
    val entityId: String,
    val alias: String,
)

@Serializable
data class ExportSettings(
    val wakeWordEnabled: Boolean = true,
    val sensitivity: Float = 0.5f,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val biometricEnabled: Boolean = false,
)

/**
 * Exports app configuration to JSON format.
 * NOTE: Access token is intentionally excluded for security.
 */
@Singleton
class ConfigExporter @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val entityDao: EntityDao,
    private val aliasDao: AliasDao,
    private val json: Json,
) {
    suspend fun export(): String {
        val config = connectionRepository.getConnectionConfig()

        // Get favourites
        val favouriteEntities = mutableListOf<String>()
        // We need a non-flow way to get favourites for export
        // Use the entity DAO directly

        val aliases = mutableListOf<ExportAlias>()
        val schema = ExportSchema(
            connection = config?.let { ExportConnection(url = it.url) },
            favourites = favouriteEntities,
            aliases = aliases,
        )

        return json.encodeToString(schema)
    }

    fun exportToString(schema: ExportSchema): String {
        return json.encodeToString(schema)
    }
}

/**
 * Result of an import validation.
 */
sealed class ImportResult {
    data class Success(val schema: ExportSchema) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Imports app configuration from JSON.
 */
@Singleton
class ConfigImporter @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val entityDao: EntityDao,
    private val aliasDao: AliasDao,
    private val json: Json,
) {
    /**
     * Validate JSON string and parse into ExportSchema.
     */
    fun validate(jsonString: String): ImportResult {
        return try {
            val schema = json.decodeFromString<ExportSchema>(jsonString)
            if (schema.schemaVersion > 1) {
                ImportResult.Error("Unsupported schema version: ${schema.schemaVersion}")
            } else {
                ImportResult.Success(schema)
            }
        } catch (e: Exception) {
            ImportResult.Error("Invalid format: ${e.message}")
        }
    }

    /**
     * Apply an imported config.
     * @param schema validated export schema
     * @param mergeAliases if true, merge with existing aliases; if false, replace
     */
    suspend fun apply(schema: ExportSchema, mergeAliases: Boolean = true) {
        // Restore connection URL (token must be re-entered)
        schema.connection?.let { conn ->
            connectionRepository.saveConnectionConfig(conn.url, "")
        }

        // Restore favourites
        schema.favourites.forEach { entityId ->
            entityDao.setFavourite(entityId, true)
        }

        // Restore aliases
        if (!mergeAliases) {
            aliasDao.deleteAll()
        }
        schema.aliases.forEach { exportAlias ->
            aliasDao.insert(
                AliasDb(entityId = exportAlias.entityId, alias = exportAlias.alias),
            )
        }
    }
}
