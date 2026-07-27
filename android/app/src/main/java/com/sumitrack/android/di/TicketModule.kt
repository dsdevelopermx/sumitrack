package com.sumitrack.android.di

import android.content.Context
import com.sumitrack.android.data.bluetooth.AndroidBluetoothTicketPrinter
import com.sumitrack.android.data.bluetooth.BluetoothTicketPrinter
import com.sumitrack.android.ui.screens.orders.AndroidTicketFileWriter
import com.sumitrack.android.ui.screens.orders.TicketFileWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TicketModule {

    @Provides
    @Singleton
    fun provideBluetoothTicketPrinter(@ApplicationContext context: Context): BluetoothTicketPrinter =
        AndroidBluetoothTicketPrinter(context)

    @Provides
    @Singleton
    fun provideTicketFileWriter(@ApplicationContext context: Context): TicketFileWriter =
        AndroidTicketFileWriter(context)
}
