package ru.dks.obhod.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Одна запись обхода по ГПА. */
@Entity(tableName = "readings", indices = [Index("gpaId"), Index(value = ["syncId"], unique = true)])
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gpaId: String,
    val timestamp: Long,
    val cyl1: Double?,
    val cyl2: Double?,
    val cyl3: Double?,
    val cyl4: Double?,
    val cyl5: Double?,
    val mskOilLevel: Double?,
    val mskFilterDiff: Double?,
    val sguLeak1: Double?,
    val sguLeak2: Double?,
    val engineOilLevel: Double?,
    val pressureBefore: Double?,
    val pressureAfter: Double?,
    val note: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "hangar_photos", indices = [Index("gpaId"), Index(value = ["syncId"], unique = true)])
data class HangarPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gpaId: String,
    val path: String,
    val timestamp: Long,
    val caption: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "hangar_notes")
data class HangarNote(
    @PrimaryKey val gpaId: String,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "remarks", indices = [Index("gpaId"), Index(value = ["syncId"], unique = true)])
data class Remark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gpaId: String,
    val timestamp: Long,
    val text: String,
    val resolved: Boolean = false,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "remark_photos",
    indices = [Index("remarkId"), Index(value = ["syncId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Remark::class,
            parentColumns = ["id"],
            childColumns = ["remarkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RemarkPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remarkId: Long,
    val path: String,
    val syncId: String = java.util.UUID.randomUUID().toString(),
)

data class GpaCount(val gpaId: String, val cnt: Int)
