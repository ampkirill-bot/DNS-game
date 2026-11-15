package ru.dks.obhod.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Reading::class, HangarPhoto::class, HangarNote::class, Remark::class, RemarkPhoto::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun hangar(): HangarDao
    abstract fun remarks(): RemarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "obhod.db").build()
    }
}
