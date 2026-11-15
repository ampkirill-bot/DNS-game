package ru.dks.obhod

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import ru.dks.obhod.data.AppDatabase
import ru.dks.obhod.data.PhotoStorage

class App : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var photos: PhotoStorage
        private set

    /** Для записи в БД, которая не должна прерываться при уходе с экрана. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        photos = PhotoStorage(this)
    }
}
