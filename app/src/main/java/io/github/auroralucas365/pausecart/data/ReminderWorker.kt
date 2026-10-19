package io.github.auroralucas365.pausecart.data

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.github.auroralucas365.pausecart.MainActivity
import io.github.auroralucas365.pausecart.R
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val item = CartDatabase.get(ctx).items().get(inputData.getLong("id", -1)) ?: return Result.success()
        if (!Preferences(ctx).reminders || item.status != "WAITING") return Result.success()
        if (System.currentTimeMillis() < item.dueAt) { ReminderScheduler.schedule(ctx, item); return Result.success() }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("cooldown", "冷静清单提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java)
        val pending = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice = NotificationCompat.Builder(ctx, "cooldown").setSmallIcon(R.drawable.ic_pausecart)
            .setContentTitle("可以重新考虑这次购买了")
            .setContentText("冷静期已结束，你也可以继续等待。打开缓购查看清单。")
            .setContentIntent(pending).setAutoCancel(true).build()
        // A shared notification ID combines simultaneous due items instead of repeatedly prompting shopping.
        NotificationManagerCompat.from(ctx).notify(100, notice)
        return Result.success()
    }
}
object ReminderScheduler {
    fun schedule(context: Context, item: SavedItem) {
        if (!Preferences(context).reminders || item.status != "WAITING") return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf("id" to item.id))
            .setInitialDelay((item.dueAt - System.currentTimeMillis()).coerceAtLeast(1000), TimeUnit.MILLISECONDS)
            .addTag("cooldowns").build()
        WorkManager.getInstance(context).enqueueUniqueWork("item_${item.id}", ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, id: Long) { WorkManager.getInstance(context).cancelUniqueWork("item_$id") }
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("cooldowns")
        NotificationManagerCompat.from(context).cancel(100)
    }
}
