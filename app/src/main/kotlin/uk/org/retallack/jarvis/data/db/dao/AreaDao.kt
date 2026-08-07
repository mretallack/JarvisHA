package uk.org.retallack.jarvis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.org.retallack.jarvis.data.db.entity.AreaDb

@Dao
interface AreaDao {

    @Query("SELECT * FROM areas ORDER BY name")
    fun getAllAreas(): Flow<List<AreaDb>>

    @Query("SELECT * FROM areas WHERE areaId = :areaId")
    suspend fun getArea(areaId: String): AreaDb?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(areas: List<AreaDb>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(area: AreaDb)

    @Query("DELETE FROM areas")
    suspend fun deleteAll()
}
