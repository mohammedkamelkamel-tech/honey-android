package com.alkamel.billing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackupScheduler.schedule(this)

        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(BackupBridge(this), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("https://wa.me/") || url.startsWith("whatsapp://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                    return true
                }
                return false
            }
        }
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }
}

class BackupBridge(private val context: Context) {
    @JavascriptInterface
    fun saveData(json: String) {
        try {
            val file = File(context.filesDir, "current_backup.json")
            file.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    @JavascriptInterface
    fun scheduleDailyBackup() { BackupScheduler.schedule(context) }

    @JavascriptInterface
    fun runBackupNow() { BackupWorker.runNow(context) }
}

object BackupScheduler {
    private const val WORK_NAME = "honey_daily_backup"
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val source = File(applicationContext.filesDir, "current_backup.json")
            if (!source.exists()) return Result.success()
            val backupDir = File(applicationContext.getExternalFilesDir(null), "Backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val target = File(backupDir, "honey-billing-backup-$stamp.json")
            source.copyTo(target, overwrite = true)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
