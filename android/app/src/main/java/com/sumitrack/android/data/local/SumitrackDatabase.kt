package com.sumitrack.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sumitrack.android.data.local.converters.BigDecimalConverter
import com.sumitrack.android.data.local.converters.InstantConverter
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SettingsEntity

@Database(
    entities = [SettingsEntity::class, ClientEntity::class, SaleEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(BigDecimalConverter::class, InstantConverter::class)
abstract class SumitrackDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun clientDao(): ClientDao
    abstract fun saleDao(): SaleDao
}
