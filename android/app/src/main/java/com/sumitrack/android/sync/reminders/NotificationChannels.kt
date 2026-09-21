package com.sumitrack.android.sync.reminders

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

const val CHANNEL_PAYMENT_REMINDERS = "payment_reminders"

// Idempotente: crear un canal que ya existe no lo modifica. minSdk = 26, así que los canales son
// obligatorios siempre (no hace falta guardar por versión de SDK).
fun ensurePaymentReminderChannel(context: Context) {
    val channel = NotificationChannelCompat.Builder(
        CHANNEL_PAYMENT_REMINDERS,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
    )
        .setName("Recordatorios de cobro")
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}
