package com.sumitrack.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sumitrack.android.data.local.converters.BigDecimalConverter
import com.sumitrack.android.data.local.converters.InstantConverter
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.SettingsEntity

@Database(
    entities = [SettingsEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(BigDecimalConverter::class, InstantConverter::class)
abstract class SumitrackDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}
