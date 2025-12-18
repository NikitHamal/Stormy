package com.codex.stormy.crash

import android.app.Application
import android.content.Intent
import android.os.Process
import com.codex.stormy.ui.screens.debug.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    private var application: Application? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(app: Application) {
        application = app
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(thread, throwable)
            launchCrashActivity(crashLog)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        printWriter.println("=== CRASH REPORT ===")
        printWriter.println()
        printWriter.println("Thread: ${thread.name}")
        printWriter.println("Time: ${System.currentTimeMillis()}")
        printWriter.println()
        printWriter.println("=== EXCEPTION ===")
        throwable.printStackTrace(printWriter)
        printWriter.println()

        var cause = throwable.cause
        var causeCount = 0
        while (cause != null && causeCount < 10) {
            printWriter.println("=== CAUSED BY ===")
            cause.printStackTrace(printWriter)
            printWriter.println()
            cause = cause.cause
            causeCount++
        }

        printWriter.println("=== DEVICE INFO ===")
        printWriter.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        printWriter.println("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        printWriter.println("App Version: ${getAppVersion()}")
        printWriter.println()

        printWriter.println("=== MEMORY INFO ===")
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        printWriter.println("Used: ${usedMemory}MB / Max: ${maxMemory}MB")

        printWriter.flush()
        return stringWriter.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val app = application ?: return "Unknown"
            val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun launchCrashActivity(crashLog: String) {
        val app = application ?: return
        val intent = Intent(app, CrashActivity::class.java).apply {
            putExtra(CrashActivity.EXTRA_CRASH_LOG, crashLog)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        app.startActivity(intent)
    }
}
