package ru.dks.obhod.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reading::class, HangarPhoto::class, HangarNote::class, Remark::class, RemarkPhoto::class],
    version = 2,
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "obhod.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
