package uk.org.retallack.jarvis.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.AreaDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.AreaDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.ha.HaWebSocketClient
import uk.org.retallack.jarvis.data.ha.model.HaEntityState

class EntityRepositoryTest {

    private lateinit var entityDao: EntityDao
    private lateinit var areaDao: AreaDao
    private lateinit var aliasDao: AliasDao
    private lateinit var haClient: HaClient
    private lateinit var webSocketClient: HaWebSocketClient
    private lateinit var connectionRepository: ConnectionRepository
    private lateinit var repository: EntityRepository

    @BeforeEach
    fun setup() {
        entityDao = mockk(relaxed = true)
        areaDao = mockk(relaxed = true)
        aliasDao = mockk(relaxed = true)
        haClient = mockk(relaxed = true)
        webSocketClient = mockk(relaxed = true)
        connectionRepository = mockk(relaxed = true)
        
        every { webSocketClient.connectionState } returns kotlinx.coroutines.flow.MutableStateFlow(uk.org.retallack.jarvis.data.ha.WsConnectionState.CONNECTED)

        repository = EntityRepository(entityDao, areaDao, aliasDao, haClient, webSocketClient, connectionRepository)
    }

    @Test
    fun `setFavourite updates entity favourite status`() = runTest {
        repository.setFavourite("light.living_room", true)
        coVerify { entityDao.setFavourite("light.living_room", true) }
    }

    @Test
    fun `setFavourite can unstar entity`() = runTest {
        repository.setFavourite("light.living_room", false)
        coVerify { entityDao.setFavourite("light.living_room", false) }
    }

    @Test
    fun `addAlias inserts alias when not existing`() = runTest {
        coEvery { aliasDao.findAlias("light.living_room", "lounge light") } returns null
        coEvery { aliasDao.insert(any()) } returns 1L

        val id = repository.addAlias("light.living_room", "lounge light")
        assertEquals(1L, id)
        coVerify { aliasDao.insert(match { it.entityId == "light.living_room" && it.alias == "lounge light" }) }
    }

    @Test
    fun `addAlias returns existing id when alias already exists`() = runTest {
        val existingAlias = AliasDb(id = 42, entityId = "light.living_room", alias = "lounge light")
        coEvery { aliasDao.findAlias("light.living_room", "lounge light") } returns existingAlias

        val id = repository.addAlias("light.living_room", "lounge light")
        assertEquals(42L, id)
        coVerify(exactly = 0) { aliasDao.insert(any()) }
    }

    @Test
    fun `removeAlias deletes by id`() = runTest {
        repository.removeAlias(5L)
        coVerify { aliasDao.delete(5L) }
    }

    @Test
    fun `pushAliasToHa sends WebSocket command`() = runTest {
        coEvery { webSocketClient.sendCommand(any(), any()) } returns buildJsonObject {
            put("success", true)
        }

        repository.pushAliasToHa("light.living_room", listOf("lounge", "main light"))
        coVerify {
            webSocketClient.sendCommand(
                "config/entity_registry/update",
                match {
                    it["entity_id"] == "light.living_room" &&
                        it["aliases"] == listOf("lounge", "main light")
                },
            )
        }
    }

    @Test
    fun `syncEntitiesFromHa fetches states and inserts into database`() = runTest {
        val states = listOf(
            HaEntityState(
                entityId = "light.living_room",
                state = "on",
                attributes = mapOf("friendly_name" to JsonPrimitive("Living Room")),
            ),
            HaEntityState(
                entityId = "switch.kitchen",
                state = "off",
                attributes = mapOf("friendly_name" to JsonPrimitive("Kitchen Switch")),
            ),
        )
        coEvery { haClient.getAllStates() } returns states
        coEvery { entityDao.getEntity(any()) } returns null
        coEvery { entityDao.getEntityCount() } returns 0

        repository.syncEntitiesFromHa()

        coVerify {
            entityDao.insertAll(match { entities ->
                entities.size == 2 &&
                    entities[0].entityId == "light.living_room" &&
                    entities[0].domain == "light" &&
                    entities[0].friendlyName == "Living Room" &&
                    entities[1].entityId == "switch.kitchen"
            })
        }
    }

    @Test
    fun `syncEntitiesFromHa preserves favourite status`() = runTest {
        val states = listOf(
            HaEntityState(
                entityId = "light.living_room",
                state = "on",
                attributes = mapOf("friendly_name" to JsonPrimitive("Living Room")),
            ),
        )
        val existingEntity = HaEntityDb(
            entityId = "light.living_room",
            domain = "light",
            friendlyName = "Living Room",
            state = "off",
            areaId = null,
            isFavourite = true,
        )
        coEvery { haClient.getAllStates() } returns states
        coEvery { entityDao.getEntity("light.living_room") } returns existingEntity
        coEvery { entityDao.getEntityCount() } returns 1

        repository.syncEntitiesFromHa()

        coVerify {
            entityDao.insertAll(match { entities ->
                entities.size == 1 && entities[0].isFavourite
            })
        }
    }

    @Test
    fun `syncAreasFromHa parses and inserts areas`() = runTest {
        val response = buildJsonObject {
            put("id", 1)
            put("type", "result")
            put("success", true)
            put("result", buildJsonArray {
                add(buildJsonObject {
                    put("area_id", "living_room")
                    put("name", "Living Room")
                    put("icon", "mdi:sofa")
                })
                add(buildJsonObject {
                    put("area_id", "kitchen")
                    put("name", "Kitchen")
                })
            })
        }
        coEvery { webSocketClient.sendCommand("config/area_registry/list") } returns response

        repository.syncAreasFromHa()

        coVerify {
            areaDao.insertAll(match { areas ->
                areas.size == 2 &&
                    areas[0].areaId == "living_room" &&
                    areas[0].name == "Living Room" &&
                    areas[0].icon == "mdi:sofa" &&
                    areas[1].areaId == "kitchen"
            })
        }
    }

    @Test
    fun `handleStateChanged updates entity state in database`() = runTest {
        val event = buildJsonObject {
            put("type", "event")
            put("event", buildJsonObject {
                put("data", buildJsonObject {
                    put("entity_id", "light.living_room")
                    put("new_state", buildJsonObject {
                        put("state", "off")
                        put("entity_id", "light.living_room")
                    })
                })
            })
        }

        repository.handleStateChanged(event)

        coVerify { entityDao.updateState("light.living_room", "off", any()) }
    }

    @Test
    fun `handleStateChanged ignores events without required fields`() = runTest {
        val event = buildJsonObject {
            put("type", "event")
            put("event", buildJsonObject {
                put("data", buildJsonObject { })
            })
        }

        repository.handleStateChanged(event)

        coVerify(exactly = 0) { entityDao.updateState(any(), any(), any()) }
    }
}
