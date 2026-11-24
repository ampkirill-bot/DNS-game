package ru.dks.obhod.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings WHERE gpaId = :gpaId ORDER BY timestamp DESC")
    fun forGpa(gpaId: String): Flow<List<Reading>>

    @Query(
        "SELECT r.* FROM readings r INNER JOIN " +
            "(SELECT gpaId AS g, MAX(timestamp) AS mt FROM readings GROUP BY gpaId) m " +
            "ON r.gpaId = m.g AND r.timestamp = m.mt"
    )
    fun latestPerGpa(): Flow<List<Reading>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: Reading): Long

    @Delete
    suspend fun delete(r: Reading)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun all(): Flow<List<Reading>>

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    suspend fun snapshot(): List<Reading>

    @Query("SELECT * FROM readings WHERE syncId = :syncId LIMIT 1")
    suspend fun bySyncId(syncId: String): Reading?
}

@Dao
interface HangarDao {
    @Query("SELECT * FROM hangar_photos WHERE gpaId = :gpaId ORDER BY timestamp DESC")
    fun photos(gpaId: String): Flow<List<HangarPhoto>>

    @Insert
    suspend fun insertPhoto(p: HangarPhoto): Long

    @Update
    suspend fun updatePhoto(p: HangarPhoto)

    @Delete
    suspend fun deletePhoto(p: HangarPhoto)

    @Query("SELECT * FROM hangar_notes WHERE gpaId = :gpaId")
    suspend fun note(gpaId: String): HangarNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(n: HangarNote)

    @Query("SELECT * FROM hangar_photos ORDER BY timestamp DESC")
    suspend fun photosSnapshot(): List<HangarPhoto>

    @Query("SELECT * FROM hangar_photos WHERE syncId = :syncId LIMIT 1")
    suspend fun photoBySyncId(syncId: String): HangarPhoto?

    @Query("SELECT * FROM hangar_notes")
    suspend fun notesSnapshot(): List<HangarNote>
}

@Dao
abstract class RemarkDao {
    @Query("SELECT * FROM remarks WHERE gpaId = :gpaId ORDER BY resolved ASC, timestamp DESC")
    abstract fun forGpa(gpaId: String): Flow<List<Remark>>

    @Query("SELECT * FROM remark_photos WHERE remarkId IN (SELECT id FROM remarks WHERE gpaId = :gpaId) ORDER BY id")
    abstract fun photosForGpa(gpaId: String): Flow<List<RemarkPhoto>>

    @Query("SELECT gpaId, COUNT(*) AS cnt FROM remarks WHERE resolved = 0 GROUP BY gpaId")
    abstract fun openCounts(): Flow<List<GpaCount>>

    @Query("SELECT * FROM remarks ORDER BY resolved ASC, timestamp DESC")
    abstract fun all(): Flow<List<Remark>>

    @Query("SELECT * FROM remarks ORDER BY resolved ASC, timestamp DESC")
    abstract suspend fun snapshot(): List<Remark>

    @Query("SELECT * FROM remark_photos ORDER BY id")
    abstract fun allPhotos(): Flow<List<RemarkPhoto>>

    @Query("SELECT * FROM remark_photos ORDER BY id")
    abstract suspend fun photosSnapshot(): List<RemarkPhoto>

    @Query("SELECT * FROM remarks WHERE id = :id")
    abstract suspend fun get(id: Long): Remark?

    @Query("SELECT * FROM remark_photos WHERE remarkId = :remarkId ORDER BY id")
    abstract suspend fun photos(remarkId: Long): List<RemarkPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(r: Remark): Long

    @Delete
    abstract suspend fun delete(r: Remark)

    @Query("UPDATE remarks SET resolved = :resolved, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setResolved(id: Long, resolved: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM remarks WHERE syncId = :syncId LIMIT 1")
    abstract suspend fun bySyncId(syncId: String): Remark?

    @Query("SELECT * FROM remark_photos WHERE syncId = :syncId LIMIT 1")
    abstract suspend fun photoBySyncId(syncId: String): RemarkPhoto?

    @Query("DELETE FROM remark_photos WHERE remarkId = :remarkId")
    abstract suspend fun clearPhotos(remarkId: Long)

    @Insert
    abstract suspend fun insertPhotos(p: List<RemarkPhoto>)

    @Transaction
    open suspend fun save(remark: Remark, paths: List<String>): Long {
        val id = upsert(remark)
        clearPhotos(id)
        insertPhotos(paths.map { RemarkPhoto(remarkId = id, path = it) })
        return id
    }
}
