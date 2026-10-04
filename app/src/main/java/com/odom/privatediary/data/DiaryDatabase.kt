package com.odom.privatediary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EntryEntity::class, WeeklyReportEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun weeklyReportDao(): WeeklyReportDao

    companion object {
        @Volatile private var instance: DiaryDatabase? = null

        fun get(context: Context): DiaryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, DiaryDatabase::class.java, "diary.db",
            ).build().also { instance = it }
        }
    }
}
