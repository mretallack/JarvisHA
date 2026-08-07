package uk.org.retallack.jarvis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.AreaDao
import uk.org.retallack.jarvis.data.db.dao.ConversationMessageDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.AreaDb
import uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb

@Database(
    entities = [HaEntityDb::class, AreaDb::class, AliasDb::class, ConversationMessageDb::class],
    version = 1,
    exportSchema = true,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun entityDao(): EntityDao
    abstract fun areaDao(): AreaDao
    abstract fun aliasDao(): AliasDao
    abstract fun conversationMessageDao(): ConversationMessageDao
}
