package uk.org.retallack.jarvis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.org.retallack.jarvis.data.db.entity.AliasDb

@Dao
interface AliasDao {

    @Query("SELECT * FROM aliases WHERE entityId = :entityId ORDER BY alias")
    fun getAliasesForEntity(entityId: String): Flow<List<AliasDb>>

    @Query("SELECT * FROM aliases ORDER BY alias")
    fun getAllAliases(): Flow<List<AliasDb>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: AliasDb): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(aliases: List<AliasDb>)

    @Query("DELETE FROM aliases WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM aliases WHERE entityId = :entityId")
    suspend fun deleteAllForEntity(entityId: String)

    @Query("DELETE FROM aliases")
    suspend fun deleteAll()

    @Query("SELECT * FROM aliases WHERE entityId = :entityId AND alias = :alias LIMIT 1")
    suspend fun findAlias(entityId: String, alias: String): AliasDb?
}
