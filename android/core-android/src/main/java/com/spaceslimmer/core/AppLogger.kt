package com.spaceslimmer.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

object AppLogger {

    enum class Level(val priority: Int, val tagLetter: String) {
        VERBOSE(2, "V"),
        DEBUG(3, "D"),
        INFO(4, "I"),
        WARN(5, "W"),
        ERROR(6, "E"),
    }

    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2MB per file
    private const val MAX_BACKUP_FILES = 3
    private const val LOG_DIR_NAME = "logs"
    private const val CURRENT_LOG_NAME = "app.log"

    @Volatile
    private var initialized = false
    private var logDir: File? = null
    private var currentLogFile: File? = null
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLogger-Writer").apply { isDaemon = true }
    }

    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val baseDir = File(context.filesDir, LOG_DIR_NAME)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            logDir = baseDir
            currentLogFile = File(baseDir, CURRENT_LOG_NAME)
            initialized = true

            // Install uncaught exception handler
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    e("UncaughtException", "Fatal crash on thread [${thread.name}]", throwable)
                    flushSync()
                } catch (_: Throwable) {
                } finally {
                    originalHandler?.uncaughtException(thread, throwable)
                }
            }

            i(
                "AppLogger",
                "Logger initialized. Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                    "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
            )
        }
    }

    fun v(tag: String, msg: String, tr: Throwable? = null) = log(Level.VERBOSE, tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null) = log(Level.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = log(Level.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Level.ERROR, tag, msg, tr)

    fun log(level: Level, tag: String, msg: String, tr: Throwable? = null) {
        // 1. Output to Android Logcat
        when (level) {
            Level.VERBOSE -> if (tr != null) Log.v(tag, msg, tr) else Log.v(tag, msg)
            Level.DEBUG -> if (tr != null) Log.d(tag, msg, tr) else Log.d(tag, msg)
            Level.INFO -> if (tr != null) Log.i(tag, msg, tr) else Log.i(tag, msg)
            Level.WARN -> if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
            Level.ERROR -> if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }

        // 2. Format line for file
        val time = dateFormat.get()?.format(Date()) ?: System.currentTimeMillis().toString()
        val threadName = Thread.currentThread().name
        val sb = java.lang.StringBuilder()
        sb.append(time)
            .append(" [")
            .append(level.tagLetter)
            .append("] [")
            .append(threadName)
            .append("] [")
            .append(tag)
            .append("] ")
            .append(msg)
        if (tr != null) {
            sb.append("\n").append(stackTraceToString(tr))
        }
        sb.append("\n")

        val formatted = sb.toString()

        // 3. Queue write
        if (initialized) {
            writeExecutor.submit {
                writeToFile(formatted)
            }
        }
    }

    private fun writeToFile(line: String) {
        val target = currentLogFile ?: return
        try {
            if (target.exists() && target.length() >= MAX_FILE_SIZE_BYTES) {
                rotateLogFiles()
            }
            FileWriter(target, true).use { fw ->
                fw.write(line)
            }
        } catch (_: Throwable) {
        }
    }

    private fun rotateLogFiles() {
        val dir = logDir ?: return
        try {
            // Delete oldest backup
            val oldest = File(dir, "app.${MAX_BACKUP_FILES - 1}.log")
            if (oldest.exists()) {
                oldest.delete()
            }

            // Shift backups
            for (i in MAX_BACKUP_FILES - 2 downTo 1) {
                val f = File(dir, "app.$i.log")
                if (f.exists()) {
                    val next = File(dir, "app.${i + 1}.log")
                    f.renameTo(next)
                }
            }

            // Shift current
            val current = File(dir, CURRENT_LOG_NAME)
            if (current.exists()) {
                val firstBackup = File(dir, "app.1.log")
                current.renameTo(firstBackup)
            }

            currentLogFile = File(dir, CURRENT_LOG_NAME)
        } catch (_: Throwable) {
        }
    }

    fun flushSync() {
        val future = writeExecutor.submit {
            // No-op, ensures previous queued tasks are executed
        }
        runCatching {
            future.get(2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    fun getLogFiles(context: Context): List<File> {
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("app") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun readRecentLogs(context: Context, maxLines: Int = 1000): List<String> {
        val current = File(File(context.filesDir, LOG_DIR_NAME), CURRENT_LOG_NAME)
        if (!current.exists()) return emptyList()
        val lines = mutableListOf<String>()
        runCatching {
            current.useLines { seq ->
                lines.addAll(seq.toList())
            }
        }
        return if (lines.size > maxLines) lines.takeLast(maxLines) else lines
    }

    fun clearLogs(context: Context) {
        writeExecutor.submit {
            val dir = File(context.filesDir, LOG_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            currentLogFile = File(dir, CURRENT_LOG_NAME)
        }
    }

    private fun stackTraceToString(tr: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        tr.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
