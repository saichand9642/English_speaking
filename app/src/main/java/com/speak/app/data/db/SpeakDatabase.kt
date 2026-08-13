package com.speak.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        TurnEntity::class,
        MistakeEntity::class,
        DrillCardEntity::class,
        PronunciationEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SpeakDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun turns(): TurnDao
    abstract fun mistakes(): MistakeDao
    abstract fun drillCards(): DrillCardDao
    abstract fun pronunciation(): PronunciationDao

    companion object {
        private const val NAME = "speak.db"

        @Volatile
        private var instance: SpeakDatabase? = null

        fun get(context: Context): SpeakDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeakDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
