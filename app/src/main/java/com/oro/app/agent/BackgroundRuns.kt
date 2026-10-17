package com.oro.app.agent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oro.app.MainActivity
import com.oro.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Agent runs that outlive the screen that started them.
 *
 * A run is launched in the application scope rather than the editor's view
 * model, so leaving the editor, switching apps or locking the phone does not
 * cancel it. While any run is live, [AgentRunService] holds a foreground
 * notification, which is what keeps Android from killing the process on a
 * low-memory phone mid-edit. When the user is not looking, the run's ending
 * (or a question it is waiting on) is posted as a notification that opens
 * the app.
 */
object BackgroundRuns {

    data class Run(val projectId: String, val sessionId: String, val projectName: String, val job: Job?)

    private val _active = MutableStateFlow<Map<String, Run>>(emptyMap())
    /** Live runs by project id. */
    val active: StateFlow<Map<String, Run>> = _active

    fun begin(context: Context, run: Run) {
        _active.update { it + (run.projectId to run) }
        AgentRunService.sync(context.applicationContext, _active.value.values.map { it.projectName })
    }

    fun end(context: Context, projectId: String) {
        _active.update { it - projectId }
        AgentRunService.sync(context.applicationContext, _active.value.values.map { it.projectName })
    }

    /** Stop a run started by another (since recreated) screen. */
    fun cancel(projectId: String) {
        _active.value[projectId]?.job?.cancel()
    }
}

/** Notifications about runs the user is not watching. */
object AgentNotifications {
    const val CHANNEL_RUNS = "agent_runs"
    const val CHANNEL_UPDATES = "agent_updates"
    private const val ID_ONGOING = 4100

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNS, "Orobi working", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Orobi runs in the background"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Orobi updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When Orobi finishes, fails, or has a question"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun ongoing(context: Context, projects: List<String>): Notification {
        ensureChannels(context)
        val text = when (projects.size) {
            0, 1 -> "Working on ${projects.firstOrNull() ?: "your project"}"
            else -> "Working on ${projects.size} projects"
        }
        return NotificationCompat.Builder(context, CHANNEL_RUNS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Orobi is working")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Post only when the app is not on screen; the chat already shows it otherwise. */
    fun update(context: Context, projectId: String, title: String, text: String) {
        if (com.oro.app.OroApplication.getInstance().isInForeground || !canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(projectId.hashCode(), n) }
    }

    internal fun ongoingId() = ID_ONGOING
}

/** Foreground keep-alive while at least one agent run is live. */
class AgentRunService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projects = intent?.getStringArrayListExtra(EXTRA_PROJECTS).orEmpty()
        if (projects.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = AgentNotifications.ongoing(this, projects)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(AgentNotifications.ongoingId(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(AgentNotifications.ongoingId(), n)
            }
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_PROJECTS = "projects"

        /** Start, update or stop the service to match the live runs. */
        fun sync(context: Context, projects: List<String>) {
            val intent = Intent(context, AgentRunService::class.java)
            if (projects.isEmpty()) {
                context.stopService(intent)
                return
            }
            intent.putStringArrayListExtra(EXTRA_PROJECTS, ArrayList(projects))
            // Starting a foreground service from the background is refused on
            // Android 12+; runs always start from the visible editor, and a
            // refusal only costs the keep-alive, never the run itself.
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
