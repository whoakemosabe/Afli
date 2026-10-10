package app.afli.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.afli.MainActivity
import app.afli.R
import app.afli.data.Repo
import app.afli.data.Trip
import app.afli.loadLang
import app.afli.model.Fish
import app.afli.model.Water
import app.afli.t
import app.afli.ui.duration
import app.afli.ui.spotLabel
import java.util.concurrent.TimeUnit

/**
 * While a trip runs, a quiet notification stays up with buttons for his two likeliest fish and
 * End, so he can log a catch from the lock screen with wet hands. Each tap buzzes once and the
 * count updates. If a trip is still running after 4 hours, a reminder asks if he's still fishing.
 */
object TripNotice {
    private const val CH = "trip_live"
    private const val CH_REMIND = "trip_remind"
    private const val ID = 11
    private const val ID_REMIND = 12
    private const val REMIND = "afli-trip-reminder"
    const val ACTION_CATCH = "app.afli.action.CATCH"
    const val ACTION_END = "app.afli.action.END"
    const val EXTRA_SPECIES = "species"

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // The first test build used a silent "trip" channel; remove it so settings show one.
        if (nm.getNotificationChannel("trip") != null) nm.deleteNotificationChannel("trip")
        nm.createNotificationChannel(NotificationChannel(CH, t("Trip in progress", "Ferð í gangi"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            // Default importance so it shows on the lock screen with its buttons; it never makes a sound.
            setSound(null, null)
            enableVibration(false)
            description = t("Log fish from the lock screen while you're fishing", "Skráðu fiska af lásskjánum á meðan þú veiðir")
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, t("Trip reminders", "Áminningar um ferðir"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("Asks if you're still fishing after a long trip", "Spyr hvort þú sért enn að veiða eftir langa ferð")
        })
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The two fish on the buttons: fixed when the trip started, so they never swap places. */
    private fun quickFish(trip: Trip): List<String> {
        if (trip.quick.isNotEmpty()) return trip.quick
        val water = Repo.state.value.spots.firstOrNull { it.id == trip.spotId }?.water ?: Water.SEA
        return Fish.forWater(water).map { it.id }.take(2)
    }

    /** Shows or refreshes the notification for the running trip, or clears it if none. */
    fun update(context: Context) {
        loadLang(context)
        val trip = Repo.state.value.activeTrip
        val nm = NotificationManagerCompat.from(context)
        if (trip == null) {
            nm.cancel(ID)
            nm.cancel(ID_REMIND)
            WorkManager.getInstance(context).cancelUniqueWork(REMIND)
            return
        }
        if (!allowed(context)) return
        channels(context)
        val open = PendingIntent.getActivity(
            context, 21, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN_LOG, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = trip.catches.size
        val b = NotificationCompat.Builder(context, CH)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(t("Fishing at ", "Á veiðum: ") + spotLabel(trip.spotId, trip.spotName))
            .setContentText(app.afli.ui.fishCount(n) + t(" · started ", " · byrjaði ") + app.afli.ui.clock(trip.start))
            .setWhen(trip.start)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        quickFish(trip).forEachIndexed { i, id ->
            val f = Fish.byId(id) ?: return@forEachIndexed
            val count = trip.catches.count { it.species == id }
            b.addAction(0, "+ ${f.name}" + if (count > 0) " ($count)" else "", action(context, ACTION_CATCH, 30 + i, id))
        }
        b.addAction(0, t("End trip", "Ljúka ferð"), action(context, ACTION_END, 40, null))
        runCatching { nm.notify(ID, b.build()) }
        scheduleReminder(context, trip)
    }

    const val EXTRA_OPEN_LOG = "app.afli.extra.OPEN_LOG"

    private fun action(context: Context, act: String, code: Int, species: String?) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, TripActionReceiver::class.java).setAction(act).apply { species?.let { putExtra(EXTRA_SPECIES, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleReminder(context: Context, trip: Trip) {
        val due = trip.start + 4 * 3_600_000L - System.currentTimeMillis()
        if (due <= 0) return
        val req = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(due, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.workDataOf("trip" to trip.id))
            .build()
        // Replace, so a new trip never inherits an old trip's reminder time.
        WorkManager.getInstance(context).enqueueUniqueWork(REMIND, ExistingWorkPolicy.REPLACE, req)
    }

    fun remind(context: Context, tripId: String?) {
        Repo.init(context)
        loadLang(context)
        val trip = Repo.state.value.activeTrip ?: return
        if (tripId != null && trip.id != tripId) return
        if (!allowed(context)) return
        channels(context)
        val open = PendingIntent.getActivity(
            context, 22, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN_LOG, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mins = ((System.currentTimeMillis() - trip.start) / 60_000).toInt()
        val n = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(t("Still fishing?", "Enn að veiða?"))
            .setContentText(t("Your trip has been running for ${duration(mins)}. End it if you've left.", "Ferðin hefur staðið í ${duration(mins)}. Ljúktu henni ef þú ert farinn."))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, t("End trip", "Ljúka ferð"), action(context, ACTION_END, 41, null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_REMIND, n) }
    }

    /** One firm buzz for a fish logged from the notification. */
    fun buzz(context: Context) {
        if (!app.afli.Prefs.haptics) return
        val v = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        if (v?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
}

/** Handles the notification's catch and End buttons, even with the app closed. */
class TripActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        app.afli.Prefs.load(context)
        when (intent.action) {
            TripNotice.ACTION_CATCH -> intent.getStringExtra(TripNotice.EXTRA_SPECIES)?.let {
                if (Repo.addCatch(it)) TripNotice.buzz(context)
            }
            TripNotice.ACTION_END -> {
                Repo.endTrip()
                NotificationManagerCompat.from(context).cancel(12)
            }
        }
        TripNotice.update(context)
    }
}

class TripReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { TripNotice.remind(applicationContext, inputData.getString("trip")) }
        return Result.success()
    }
}
