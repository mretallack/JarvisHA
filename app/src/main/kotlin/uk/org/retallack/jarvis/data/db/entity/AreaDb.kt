package uk.org.retallack.jarvis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "areas")
data class AreaDb(
    @PrimaryKey val areaId: String,
    val name: String,
    val icon: String? = null,
)
