package uk.org.retallack.jarvis.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.org.retallack.jarvis.data.db.dao.AliasDao
import uk.org.retallack.jarvis.data.db.dao.AreaDao
import uk.org.retallack.jarvis.data.db.dao.ConversationMessageDao
import uk.org.retallack.jarvis.data.db.dao.EntityDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JarvisDatabase {
        return Room.databaseBuilder(
            context,
            JarvisDatabase::class.java,
            "jarvis_database",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideEntityDao(database: JarvisDatabase): EntityDao = database.entityDao()

    @Provides
    fun provideAreaDao(database: JarvisDatabase): AreaDao = database.areaDao()

    @Provides
    fun provideAliasDao(database: JarvisDatabase): AliasDao = database.aliasDao()

    @Provides
    fun provideConversationMessageDao(database: JarvisDatabase): ConversationMessageDao = database.conversationMessageDao()
}
