package com.example.ironfocus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ironfocus.data.LockRepository
import com.example.ironfocus.domain.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ongoing specialUse Foreground Service that:
 * 1. Displays a persistent, non-dismissible notification showing remaining lock time.
 * 2. Persists remainingMillis to Device Protected SharedPreferences every 30 seconds
 *    using monotonic elapsedRealtime so reboots or clock tampering cannot bypass the lock.
 */
class LockCountdownService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private lateinit var repository: LockRepository
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repository = LockRepository.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = repository.refreshLiveState()
        if (!state.isLockActive || state.remainingMillis <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildOngoingNotification(
            remainingMillis = state.remainingMillis,
            blockedAppCount = state.blockedPackages.size,
            blockedSiteCount = state.blockedDomains.size
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_ACTIVE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_ACTIVE, notification)
        }

        startCountdownLoop()
        return START_STICKY
    }

    private fun startCountdownLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var secondsSincePersist = 0
            while (isActive) {
                val state = repository.refreshLiveState()
                if (!state.isLockActive || state.remainingMillis <= 0L) {
                    notifyLockCompleted()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                if (secondsSincePersist >= CHECKPOINT_INTERVAL_SECONDS) {
                    repository.persistCheckpoint()
                    secondsSincePersist = 0
                }

                notificationManager.notify(
                    NOTIFICATION_ID_ACTIVE,
                    buildOngoingNotification(
                        remainingMillis = state.remainingMillis,
                        blockedAppCount = state.blockedPackages.size,
                        blockedSiteCount = state.blockedDomains.size
                    )
                )

                delay(1_000L)
                secondsSincePersist += 1
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        repository.persistCheckpoint()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        repository.persistCheckpoint()
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activeChannel = NotificationChannel(
                CHANNEL_ID_ACTIVE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(activeChannel)
        }
    }

    private fun buildOngoingNotification(
        remainingMillis: Long,
        blockedAppCount: Int,
        blockedSiteCount: Int
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remainingText = TimeFormatter.formatNotificationRemaining(remainingMillis)
        val targetsSummary = "$blockedAppCount apps • $blockedSiteCount websites blocked"

        return NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(getString(R.string.notification_title_active))
            .setContentText("$remainingText • $targetsSummary")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$remainingText\n$targetsSummary")
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun notifyLockCompleted() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneNotification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(getString(R.string.notification_title_completed))
            .setContentText(getString(R.string.notification_body_completed))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID_COMPLETED, doneNotification)
    }

    companion object {
        private const val CHANNEL_ID_ACTIVE = "ironfocus_strict_lock_channel"
        private const val NOTIFICATION_ID_ACTIVE = 4101
        private const val NOTIFICATION_ID_COMPLETED = 4102
        private const val CHECKPOINT_INTERVAL_SECONDS = 30

        fun start(context: Context) {
            val intent = Intent(context, LockCountdownService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Gracefully avoid crash if system restricts start in edge cases
            }
        }
    }
}
