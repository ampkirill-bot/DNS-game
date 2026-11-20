package ru.dks.obhod.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reading::class, HangarPhoto::class, HangarNote::class, Remark::class, RemarkPhoto::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun hangar(): HangarDao
    abstract fun remarks(): RemarkDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN sguLeak1 REAL")
                db.execSQL("ALTER TABLE readings ADD COLUMN sguLeak2 REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("readings", "hangar_photos", "remarks", "remark_photos").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("UPDATE $table SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_syncId ON $table(syncId)")
                }
                listOf("readings", "hangar_photos", "remarks").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $table SET updatedAt = timestamp WHERE updatedAt = 0")
                }
                db.execSQL("ALTER TABLE hangar_notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE hangar_notes SET updatedAt = ${System.currentTimeMillis()} WHERE updatedAt = 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "obhod.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
