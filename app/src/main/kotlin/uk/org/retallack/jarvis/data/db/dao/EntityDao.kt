package uk.org.retallack.jarvis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb

@Dao
interface EntityDao {

    @Query("SELECT * FROM entities ORDER BY domain, friendlyName")
    fun getAllEntities(): Flow<List<HaEntityDb>>

    @Query("SELECT * FROM entities WHERE entityId = :entityId")
    suspend fun getEntity(entityId: String): HaEntityDb?

    @Query("SELECT * FROM entities WHERE isFavourite = 1 ORDER BY friendlyName")
    fun getFavourites(): Flow<List<HaEntityDb>>

    @Query("SELECT * FROM entities WHERE domain = :domain ORDER BY friendlyName")
    fun getEntitiesByDomain(domain: String): Flow<List<HaEntityDb>>

    @Query("SELECT * FROM entities WHERE areaId = :areaId ORDER BY friendlyName")
    fun getEntitiesByArea(areaId: String): Flow<List<HaEntityDb>>

    @Query("SELECT * FROM entities WHERE friendlyName LIKE '%' || :query || '%' OR entityId LIKE '%' || :query || '%'")
    fun searchEntities(query: String): Flow<List<HaEntityDb>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HaEntityDb>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HaEntityDb)

    @Update
    suspend fun update(entity: HaEntityDb)

    @Query("UPDATE entities SET state = :state, lastUpdated = :lastUpdated WHERE entityId = :entityId")
    suspend fun updateState(entityId: String, state: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE entities SET isFavourite = :isFavourite WHERE entityId = :entityId")
    suspend fun setFavourite(entityId: String, isFavourite: Boolean)

    @Query("DELETE FROM entities")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM entities")
    suspend fun getEntityCount(): Int
}
