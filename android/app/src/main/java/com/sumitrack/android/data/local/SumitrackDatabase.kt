package com.sumitrack.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sumitrack.android.data.local.converters.BigDecimalConverter
import com.sumitrack.android.data.local.converters.InstantConverter
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.ProductVariantEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SettingsEntity

@Database(
    entities = [
        SettingsEntity::class,
        ClientEntity::class,
        SaleEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(BigDecimalConverter::class, InstantConverter::class)
abstract class SumitrackDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun clientDao(): ClientDao
    abstract fun saleDao(): SaleDao
    abstract fun productDao(): ProductDao
    abstract fun productVariantDao(): ProductVariantDao
}
