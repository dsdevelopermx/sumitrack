package com.sumitrack.android.sync.workers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumitrack.android.MainActivity
import com.sumitrack.android.R
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.sync.reminders.CHANNEL_PAYMENT_REMINDERS
import com.sumitrack.android.sync.reminders.ReminderContentBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

// Solo lee Room — cero dependencia de red (AC-5). Un recordatorio fallido NO reintenta
// (Result.success() siempre): un retry lo dispararía tarde y potencialmente duplicado.
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val installmentDao: InstallmentDao,
    private val saleDao: SaleDao,
    private val clientDao: ClientDao,
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // areNotificationsEnabled() se verifica justo antes de notify()
    override suspend fun doWork(): Result {
        return try {
            val installmentId = inputData.getString(KEY_INSTALLMENT_ID) ?: return Result.success()
            val installment = installmentDao.getById(installmentId)
            val sale = installment?.let { saleDao.getById(it.fkSale, it.fkTenant) }
            val client = sale?.let { clientDao.getById(it.fkClient) }

            val content = ReminderContentBuilder.build(installment, sale, client?.name) ?: return Result.success()

            val manager = NotificationManagerCompat.from(applicationContext)
            // Permiso POST_NOTIFICATIONS denegado o notificaciones apagadas: se omite sin crash (AC-6).
            if (!manager.areNotificationsEnabled()) return Result.success()

            // Sin deep link, todos los recordatorios abren la app igual: un solo PendingIntent
            // (requestCode 0) basta. El ID de notificación es el par (tag = id de la parcialidad, 0) en
            // vez de un hashCode() de 32 bits, que podía colisionar y pisar el recordatorio de otra.
            val openApp = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_PAYMENT_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            // Tag determinista: re-postear el mismo recordatorio actualiza en vez de apilar.
            manager.notify("payment-reminder:$installmentId", 0, notification)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        const val KEY_INSTALLMENT_ID = "installment_id"
    }
}
