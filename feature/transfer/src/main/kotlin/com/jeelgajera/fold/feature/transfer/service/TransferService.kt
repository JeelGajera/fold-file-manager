package com.jeelgajera.fold.feature.transfer.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jeelgajera.fold.feature.transfer.R
import com.jeelgajera.fold.feature.transfer.TransferRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the LAN server alive while the app is in the background.
 *
 * A foreground service is the only honest way to run a server on Android: the
 * process must stay alive, and the platform requires the user to be able to see
 * that it is. The notification is not a formality -- it is the off switch, and it
 * says the address and the state so someone who forgot they were sharing finds
 * out from their lock screen.
 *
 * ### The wake lock
 *
 * Held only while a transfer is actually moving bytes, and released the moment it
 * is not. A server that holds a partial wake lock for its entire lifetime will
 * flatten a phone overnight, and "I left it on by accident" is the normal case
 * rather than the exception. The idle auto-stop is the second half of that
 * defence.
 */
@AndroidEntryPoint
class TransferService : Service() {

    @Inject
    lateinit var repository: TransferRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { repository.stopServer() }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.transfer_notification_starting), null),
            foregroundType(),
        )

        observer = scope.launch {
            repository.state.collectLatest { state ->
                if (!state.isRunning) {
                    stopSelf()
                    return@collectLatest
                }
                updateWakeLock(state.activeTransfer != null)
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = getString(R.string.transfer_notification_sharing),
                        body = state.url,
                    ),
                )
            }
        }

        // START_NOT_STICKY: if the system kills this to reclaim memory, the server
        // stays down. Silently restarting a file server after a kill the user did
        // not see is worse than stopping.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- notification -----------------------------------------------------

    private fun buildNotification(title: String, body: String?): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, TransferService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Visible on the lock screen: someone who left sharing on should not
            // have to unlock the phone to find that out.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(R.string.transfer_notification_stop),
                stopIntent,
            )
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.transfer_channel_description)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // dataSync is the correct declaration for a user-initiated file
            // transfer. Declaring it accurately is also what the Play Console
            // justification has to match.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    // --- wake lock --------------------------------------------------------

    private fun updateWakeLock(transferActive: Boolean) {
        if (transferActive) acquireWakeLock() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            // A timeout as a backstop: a transfer that stalls without reporting
            // completion must not hold the CPU awake indefinitely.
            acquire(WAKE_TIMEOUT_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.jeelgajera.fold.transfer.START"
        const val ACTION_STOP = "com.jeelgajera.fold.transfer.STOP"

        private const val CHANNEL_ID = "fold.transfer"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_STOP = 1
        private const val WAKE_TAG = "FOLD:transfer"
        private const val WAKE_TIMEOUT_MILLIS = 30 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, TransferService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TransferService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
