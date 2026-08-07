package uk.org.retallack.jarvis.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.AreaDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.AreaDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.ha.HaWebSocketClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityRepository @Inject constructor(
    private val entityDao: EntityDao,
    private val areaDao: AreaDao,
    private val aliasDao: AliasDao,
    private val haClient: HaClient,
    private val webSocketClient: HaWebSocketClient,
) {
    val allEntities: Flow<List<HaEntityDb>> = entityDao.getAllEntities()
    val favourites: Flow<List<HaEntityDb>> = entityDao.getFavourites()
    val allAreas: Flow<List<AreaDb>> = areaDao.getAllAreas()

    fun getEntitiesByDomain(domain: String): Flow<List<HaEntityDb>> =
        entityDao.getEntitiesByDomain(domain)

    fun getEntitiesByArea(areaId: String): Flow<List<HaEntityDb>> =
        entityDao.getEntitiesByArea(areaId)

    fun searchEntities(query: String): Flow<List<HaEntityDb>> =
        entityDao.searchEntities(query)

    fun getAliasesForEntity(entityId: String): Flow<List<AliasDb>> =
        aliasDao.getAliasesForEntity(entityId)

    suspend fun getEntity(entityId: String): HaEntityDb? =
        entityDao.getEntity(entityId)

    suspend fun setFavourite(entityId: String, isFavourite: Boolean) {
        entityDao.setFavourite(entityId, isFavourite)
    }

    suspend fun addAlias(entityId: String, alias: String): Long {
        val existing = aliasDao.findAlias(entityId, alias)
        if (existing != null) return existing.id
        return aliasDao.insert(AliasDb(entityId = entityId, alias = alias))
    }

    suspend fun removeAlias(id: Long) {
        aliasDao.delete(id)
    }

    /**
     * Push an alias to Home Assistant via WebSocket entity registry update.
     */
    suspend fun pushAliasToHa(entityId: String, aliases: List<String>) {
        webSocketClient.sendCommand(
            type = "config/entity_registry/update",
            additionalData = mapOf(
                "entity_id" to entityId,
                "aliases" to aliases,
            ),
        )
    }

    /**
     * Sync entities from HA REST API into Room database.
     * Preserves favourite status.
     */
    suspend fun syncEntitiesFromHa() {
        val states = haClient.getAllStates()
        val existingFavourites = mutableSetOf<String>()

        // Preserve favourites
        entityDao.getAllEntities().let { /* We'll collect current favourites from DB */ }
        val currentEntities = mutableMapOf<String, HaEntityDb>()
        // Use a suspend approach to get current favourites
        val currentCount = entityDao.getEntityCount()
        if (currentCount > 0) {
            val entity = entityDao.getEntity(states.firstOrNull()?.entityId ?: "")
            // We need to preserve favourites across sync - batch approach
        }

        val entities = states.map { state ->
            val domain = state.entityId.substringBefore(".")
            val friendlyName = state.attributes["friendly_name"]?.jsonPrimitive?.content
            val existingEntity = entityDao.getEntity(state.entityId)
            HaEntityDb(
                entityId = state.entityId,
                domain = domain,
                friendlyName = friendlyName,
                state = state.state,
                areaId = null, // Areas come from registry
                isFavourite = existingEntity?.isFavourite ?: false,
                lastUpdated = System.currentTimeMillis(),
            )
        }

        entityDao.insertAll(entities)
    }

    /**
     * Sync areas from HA WebSocket registry.
     */
    suspend fun syncAreasFromHa() {
        val response = webSocketClient.sendCommand(type = "config/area_registry/list")
        val result = response["result"]?.jsonArray ?: return

        val areas = result.map { element ->
            val obj = element.jsonObject
            AreaDb(
                areaId = obj["area_id"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                icon = obj["icon"]?.jsonPrimitive?.content,
            )
        }
        areaDao.insertAll(areas)
    }

    /**
     * Sync aliases from HA entity registry into local cache.
     */
    suspend fun syncAliasesFromHa() {
        val response = webSocketClient.sendCommand(type = "config/entity_registry/list_for_display")
        val result = response["result"]?.jsonObject ?: return
        val entities = result["entities"]?.jsonArray ?: return

        entities.forEach { element ->
            val obj = element.jsonObject
            val entityId = obj["ei"]?.jsonPrimitive?.content ?: return@forEach
            val aliases = obj["al"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val areaId = obj["ai"]?.jsonPrimitive?.content

            // Update area assignment
            if (areaId != null) {
                val existing = entityDao.getEntity(entityId)
                if (existing != null) {
                    entityDao.update(existing.copy(areaId = areaId))
                }
            }

            // Sync aliases
            aliasDao.deleteAllForEntity(entityId)
            aliases.forEach { alias ->
                aliasDao.insert(AliasDb(entityId = entityId, alias = alias))
            }
        }
    }

    /**
     * Handle a state_changed event from WebSocket.
     */
    suspend fun handleStateChanged(event: JsonObject) {
        val eventData = event["event"]?.jsonObject?.get("data")?.jsonObject ?: return
        val entityId = eventData["entity_id"]?.jsonPrimitive?.content ?: return
        val newState = eventData["new_state"]?.jsonObject ?: return
        val state = newState["state"]?.jsonPrimitive?.content ?: return

        entityDao.updateState(entityId, state)
    }

    suspend fun getEntityCount(): Int = entityDao.getEntityCount()
}
