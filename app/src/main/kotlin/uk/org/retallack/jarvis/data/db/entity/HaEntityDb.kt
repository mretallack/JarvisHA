package uk.org.retallack.jarvis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entities")
data class HaEntityDb(
    @PrimaryKey val entityId: String,
    val domain: String,
    val friendlyName: String?,
    val state: String,
    val areaId: String?,
    val isFavourite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
)
