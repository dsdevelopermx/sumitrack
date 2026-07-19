package com.sumitrack.android.di

import android.content.Context
import androidx.room.Room
import com.sumitrack.android.data.local.Migrations
import com.sumitrack.android.data.local.RoomTransactionRunner
import com.sumitrack.android.data.local.SumitrackDatabase
import com.sumitrack.android.data.local.TransactionRunner
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SumitrackDatabase =
        Room.databaseBuilder(context, SumitrackDatabase::class.java, "sumitrack_01")
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun provideSettingsDao(db: SumitrackDatabase): SettingsDao = db.settingsDao()

    @Provides
    @Singleton
    fun provideClientDao(db: SumitrackDatabase): ClientDao = db.clientDao()

    @Provides
    @Singleton
    fun provideSaleDao(db: SumitrackDatabase): SaleDao = db.saleDao()

    @Provides
    @Singleton
    fun provideProductDao(db: SumitrackDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideProductVariantDao(db: SumitrackDatabase): ProductVariantDao = db.productVariantDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: SumitrackDatabase): TransactionRunner = RoomTransactionRunner(db)
}
