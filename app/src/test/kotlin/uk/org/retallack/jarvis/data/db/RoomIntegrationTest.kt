package uk.org.retallack.jarvis.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RoomIntegrationTest {

    private lateinit var database: JarvisDatabase
    private lateinit var entityDao: EntityDao
    private lateinit var aliasDao: AliasDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        entityDao = database.entityDao()
        aliasDao = database.aliasDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // EntityDao Tests

    @Test
    fun `insert and retrieve entity`() = runBlocking {
        val entity = createEntity("light.living_room", "light", "Living Room Light")
        entityDao.insert(entity)

        val retrieved = entityDao.getEntity("light.living_room")
        assertNotNull(retrieved)
        assertEquals("light.living_room", retrieved!!.entityId)
        assertEquals("Living Room Light", retrieved.friendlyName)
        assertEquals("on", retrieved.state)
    }

    @Test
    fun `insert all entities`() = runBlocking {
        val entities = listOf(
            createEntity("light.living_room", "light", "Living Room"),
            createEntity("switch.kitchen", "switch", "Kitchen Switch"),
            createEntity("light.bedroom", "light", "Bedroom Light"),
        )
        entityDao.insertAll(entities)

        assertEquals(3, entityDao.getEntityCount())
    }

    @Test
    fun `get entity returns null for non-existent entity`() = runBlocking {
        assertNull(entityDao.getEntity("non_existent"))
    }

    @Test
    fun `getAllEntities returns flow sorted by domain then name`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("switch.z_switch", "switch", "Z Switch"),
                createEntity("light.a_light", "light", "A Light"),
                createEntity("light.b_light", "light", "B Light"),
            ),
        )

        entityDao.getAllEntities().test {
            val entities = awaitItem()
            assertEquals(3, entities.size)
            assertEquals("light.a_light", entities[0].entityId)
            assertEquals("light.b_light", entities[1].entityId)
            assertEquals("switch.z_switch", entities[2].entityId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFavourite updates favourite status`() = runBlocking {
        entityDao.insert(createEntity("light.living_room", "light", "Living Room"))

        entityDao.setFavourite("light.living_room", true)
        val updated = entityDao.getEntity("light.living_room")
        assertTrue(updated!!.isFavourite)
    }

    @Test
    fun `getFavourites returns only favourited entities`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("light.b", "light", "B"),
                createEntity("light.c", "light", "C"),
            ),
        )
        entityDao.setFavourite("light.a", true)
        entityDao.setFavourite("light.c", true)

        entityDao.getFavourites().test {
            val favs = awaitItem()
            assertEquals(2, favs.size)
            assertEquals("light.a", favs[0].entityId)
            assertEquals("light.c", favs[1].entityId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEntitiesByDomain filters correctly`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("switch.b", "switch", "B"),
                createEntity("light.c", "light", "C"),
            ),
        )

        entityDao.getEntitiesByDomain("light").test {
            val lights = awaitItem()
            assertEquals(2, lights.size)
            assertTrue(lights.all { it.domain == "light" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEntitiesByArea filters correctly`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A", areaId = "living_room"),
                createEntity("light.b", "light", "B", areaId = "kitchen"),
                createEntity("light.c", "light", "C", areaId = "living_room"),
            ),
        )

        entityDao.getEntitiesByArea("living_room").test {
            val entities = awaitItem()
            assertEquals(2, entities.size)
            assertTrue(entities.all { it.areaId == "living_room" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchEntities matches by friendlyName`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.living_room", "light", "Living Room Light"),
                createEntity("switch.kitchen", "switch", "Kitchen Switch"),
                createEntity("light.bedroom", "light", "Bedroom Light"),
            ),
        )

        entityDao.searchEntities("Light").test {
            val results = awaitItem()
            assertEquals(2, results.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchEntities matches by entityId`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.living_room", "light", "Living Room"),
                createEntity("switch.kitchen", "switch", "Kitchen"),
            ),
        )

        entityDao.searchEntities("kitchen").test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("switch.kitchen", results[0].entityId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateState changes state for entity`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A"))

        entityDao.updateState("light.a", "off", 999L)
        val updated = entityDao.getEntity("light.a")
        assertEquals("off", updated!!.state)
        assertEquals(999L, updated.lastUpdated)
    }

    @Test
    fun `deleteAll removes all entities`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("light.b", "light", "B"),
            ),
        )
        assertEquals(2, entityDao.getEntityCount())

        entityDao.deleteAll()
        assertEquals(0, entityDao.getEntityCount())
    }

    @Test
    fun `insert with conflict replaces entity`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A", state = "on"))
        entityDao.insert(createEntity("light.a", "light", "A Updated", state = "off"))

        val entity = entityDao.getEntity("light.a")
        assertEquals("A Updated", entity!!.friendlyName)
        assertEquals("off", entity.state)
        assertEquals(1, entityDao.getEntityCount())
    }

    // AliasDao Tests

    @Test
    fun `insert and retrieve alias`() = runBlocking {
        entityDao.insert(createEntity("light.living_room", "light", "Living Room"))
        val alias = AliasDb(entityId = "light.living_room", alias = "lounge light")
        val id = aliasDao.insert(alias)

        assertTrue(id > 0)

        aliasDao.getAliasesForEntity("light.living_room").test {
            val aliases = awaitItem()
            assertEquals(1, aliases.size)
            assertEquals("lounge light", aliases[0].alias)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insert multiple aliases for entity`() = runBlocking {
        entityDao.insert(createEntity("light.living_room", "light", "Living Room"))
        aliasDao.insert(AliasDb(entityId = "light.living_room", alias = "lounge light"))
        aliasDao.insert(AliasDb(entityId = "light.living_room", alias = "main light"))

        aliasDao.getAliasesForEntity("light.living_room").test {
            val aliases = awaitItem()
            assertEquals(2, aliases.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllAliases returns all aliases sorted`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("light.b", "light", "B"),
            ),
        )
        aliasDao.insert(AliasDb(entityId = "light.a", alias = "z alias"))
        aliasDao.insert(AliasDb(entityId = "light.b", alias = "a alias"))

        aliasDao.getAllAliases().test {
            val aliases = awaitItem()
            assertEquals(2, aliases.size)
            assertEquals("a alias", aliases[0].alias)
            assertEquals("z alias", aliases[1].alias)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete alias by id`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A"))
        val id = aliasDao.insert(AliasDb(entityId = "light.a", alias = "test"))

        aliasDao.delete(id)

        aliasDao.getAliasesForEntity("light.a").test {
            val aliases = awaitItem()
            assertTrue(aliases.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAllForEntity removes only that entity aliases`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("light.b", "light", "B"),
            ),
        )
        aliasDao.insert(AliasDb(entityId = "light.a", alias = "alias a"))
        aliasDao.insert(AliasDb(entityId = "light.b", alias = "alias b"))

        aliasDao.deleteAllForEntity("light.a")

        aliasDao.getAliasesForEntity("light.a").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        aliasDao.getAliasesForEntity("light.b").test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll removes all aliases`() = runBlocking {
        entityDao.insertAll(
            listOf(
                createEntity("light.a", "light", "A"),
                createEntity("light.b", "light", "B"),
            ),
        )
        aliasDao.insert(AliasDb(entityId = "light.a", alias = "a1"))
        aliasDao.insert(AliasDb(entityId = "light.b", alias = "b1"))

        aliasDao.deleteAll()

        aliasDao.getAllAliases().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findAlias returns matching alias`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A"))
        aliasDao.insert(AliasDb(entityId = "light.a", alias = "test alias"))

        val found = aliasDao.findAlias("light.a", "test alias")
        assertNotNull(found)
        assertEquals("test alias", found!!.alias)
    }

    @Test
    fun `findAlias returns null when not found`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A"))
        val found = aliasDao.findAlias("light.a", "non existent")
        assertNull(found)
    }

    @Test
    fun `cascade delete removes aliases when entity deleted`() = runBlocking {
        entityDao.insert(createEntity("light.a", "light", "A"))
        aliasDao.insert(AliasDb(entityId = "light.a", alias = "test"))

        entityDao.deleteAll()

        aliasDao.getAllAliases().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createEntity(
        entityId: String,
        domain: String,
        friendlyName: String,
        state: String = "on",
        areaId: String? = null,
    ) = HaEntityDb(
        entityId = entityId,
        domain = domain,
        friendlyName = friendlyName,
        state = state,
        areaId = areaId,
        isFavourite = false,
        lastUpdated = System.currentTimeMillis(),
    )
}
