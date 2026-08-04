package com.sumitrack.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sumitrack.android.data.local.converters.BigDecimalConverter
import com.sumitrack.android.data.local.converters.InstantConverter
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.CreditBalanceEntity
import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.PaymentEntity
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.ProductVariantEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SaleItemEntity
import com.sumitrack.android.data.local.entities.SettingsEntity

@Database(
    entities = [
        SettingsEntity::class,
        ClientEntity::class,
        SaleEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        SaleItemEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        CreditBalanceEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(BigDecimalConverter::class, InstantConverter::class)
abstract class SumitrackDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun clientDao(): ClientDao
    abstract fun saleDao(): SaleDao
    abstract fun productDao(): ProductDao
    abstract fun productVariantDao(): ProductVariantDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun paymentDao(): PaymentDao
    abstract fun creditBalanceDao(): CreditBalanceDao
}
