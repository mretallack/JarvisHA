package uk.org.retallack.jarvis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb

@Dao
interface ConversationMessageDao {

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<ConversationMessageDb>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationMessageDb>>

    @Insert
    suspend fun insert(message: ConversationMessageDb): Long

    @Query("DELETE FROM conversation_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversation_messages")
    suspend fun getCount(): Int
}
