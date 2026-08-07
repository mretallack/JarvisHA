package uk.org.retallack.jarvis.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "aliases",
    foreignKeys = [
        ForeignKey(
            entity = HaEntityDb::class,
            parentColumns = ["entityId"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entityId")],
)
data class AliasDb(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String,
    val alias: String,
)
