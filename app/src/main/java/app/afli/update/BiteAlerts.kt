package app.afli.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.afli.MainActivity
import app.afli.Prefs
import app.afli.R
import app.afli.data.Repo
import app.afli.loadLang
import app.afli.model.Model
import app.afli.t
import app.afli.ui.clock
import app.afli.ui.dayWord
import app.afli.ui.label
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Bite alerts: every three hours, in the background, Afli scores the next 12 hours at his spot
 * (the one he last chose, else the one he last fished) and sends one notification when a Great
 * stretch (60 or more, never Stay home) is coming. One alert per stretch, nothing between 23:00
 * and 07:00. Tapping it opens Now on that hour. Switched on in Settings.
 */
object BiteAlerts {
    private const val CH = "bites"
    private const val ID = 7
    private const val WORK = "afli-bite-alerts"
    const val EXTRA_JUMP = "app.afli.extra.JUMP_TO"
    const val EXTRA_SPOT = "app.afli.extra.SPOT"
    const val GREAT = 60

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CH, t("Bite alerts", "Tökuviðvaranir"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("When a great time to fish is coming up at your spot", "Þegar frábær veiðitími er fram undan á staðnum þínum")
            },
        )
    }

    /** Turns the background check on or off to match the setting. */
    fun sync(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Prefs.alerts) {
            val req = PeriodicWorkRequestBuilder<BiteWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    suspend fun check(context: Context) {
        Prefs.load(context)
        if (!Prefs.alerts) return
        loadLang(context)
        val hour = Instant.now().atZone(ZoneId.systemDefault()).hour
        if (hour >= 23 || hour < 7) return
        val spot = run { Repo.init(context); Repo.alertSpot() }
        val scores = Repo.scoreInBackground(context, spot)
        val now = System.currentTimeMillis()
        val w = Model.nextWindow(scores, now, 12) ?: return
        if (w.peak < GREAT) return
        // One alert per stretch: skip while we're still inside the last one we sent for this spot.
        val p = context.getSharedPreferences("afli", Context.MODE_PRIVATE)
        if (p.getString("lastBiteSpot", "") == spot.id && w.start < p.getLong("lastBiteEnd", 0L)) return
        p.edit().putString("lastBiteSpot", spot.id).putLong("lastBiteEnd", w.end).apply()
        val best = scores.firstOrNull { it.t >= w.start && it.score == w.peak }?.best
        post(context, spot.id, spot.label, w.start, w.end, w.peak, best?.name)
    }

    private fun post(context: Context, spotId: String, spotName: String, start: Long, end: Long, peak: Int, fish: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_JUMP, start).putExtra(EXTRA_SPOT, spotId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val day = dayWord(start).lowercase(app.afli.L.locale)
        val n = NotificationCompat.Builder(context, CH)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(t("Great bite at $spotName", "Frábærar tökulíkur: $spotName"))
            .setContentText(
                t("$day ${clock(start)}–${clock(end)} · score $peak", "$day kl. ${clock(start)}–${clock(end)} · líkur $peak") +
                    (fish?.let { " · $it" } ?: ""),
            )
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}

class BiteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try { BiteAlerts.check(applicationContext) } catch (e: Exception) { }
        return Result.success()
    }
}
