package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本更新管理器（GitHub Releases）
 *
 * 使用流程：
 * 1. GitHub 上传 APK 到 release
 * 2. 调用 checkForUpdate() 检测版本
 * 3. 用户点"立即更新" → 下载 → 安装
 *
 * ⚠ 修改 CONFIG 后请重新编译
 */
object UpdateManager {

    // ====== 【配置】GitHub 仓库信息 ======
    // 1. raw URL：指向 version.json（放在仓库根目录或 release 附件）
    //    示例：https://raw.githubusercontent.com/你的用户名/你的仓库名/main/version.json
    private const val VERSION_JSON_URL =
        "https://gitee.com/oldtomcat/TaxGroup/raw/main/version.json"

    // 2. APK 下载 URL（也可以从 version.json 中读取）
    private const val DEFAULT_APK_URL =
        "https://gitee.com/oldtomcat/TaxGroup/releases/download/v1.0.0-beta3/app-debug.apk"

    // 3. APK 文件名（下载保存用）
    private const val APK_FILE_NAME = "TaxGroup-update.apk"

    // =====================

    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    )

    /**
     * 检测版本更新
     */
    fun checkForUpdate(context: Context) {
        Thread {
            try {
                val info = fetchVersionInfo()
                if (info == null) {
                    showResultOnMain(context, "检查失败", "无法读取版本信息")
                    return@Thread
                }

                val currentCode = getCurrentVersionCode(context)
                Handler(Looper.getMainLooper()).post {
                    if (info.versionCode > currentCode) {
                        showUpdateDialog(context, info)
                    } else {
                        showResultOnMain(context, "已是最新版",
                            "当前版本 ${getCurrentVersionName(context)}\n无需更新")
                    }
                }
            } catch (e: Exception) {
                showResultOnMain(context, "检查失败",
                    "${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }

    /**
     * 从 version.json 读取最新版本
     */
    private fun fetchVersionInfo(): VersionInfo? {
        val url = URL(VERSION_JSON_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "TaxGroup-Android/1.0")
        conn.setRequestProperty("Accept", "application/json")

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            android.util.Log.w("UpdateManager", "version.json HTTP $code")
            // JSON 拉不到时回退到默认 APK URL
            return VersionInfo(
                versionCode = 0,
                versionName = "unknown",
                apkUrl = DEFAULT_APK_URL,
                changelog = "暂无更新日志"
            )
        }

        val text = BufferedReader(InputStreamReader(conn.inputStream))
            .use { it.readText() }
        conn.disconnect()

        android.util.Log.d("UpdateManager", "version.json content=$text")

        // 简单 JSON 解析（不引入额外依赖）
        val versionCode = extractJsonInt(text, "versionCode")
        val versionName = extractJsonString(text, "versionName")
        val apkUrl = extractJsonString(text, "apkUrl")
        val changelog = extractJsonString(text, "changelog")

        android.util.Log.d("UpdateManager", "parsed: code=$versionCode name=$versionName apkUrl=$apkUrl")

        return VersionInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = if (apkUrl.isNotEmpty()) apkUrl else DEFAULT_APK_URL,
            changelog = if (changelog.isNotEmpty()) changelog else "暂无更新日志"
        )
    }

    private fun extractJsonString(json: String, key: String): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"(.*?)\"")
        return regex.find(json)?.groupValues?.getOrNull(1)?.replace("\\n", "\n") ?: ""
    }

    private fun extractJsonInt(json: String, key: String): Int {
        val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    /**
     * 当前安装版本号（BuildConfig.versionCode）
     */
    fun getCurrentVersionCode(context: Context): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    } catch (_: Exception) { 0 }

    fun getCurrentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    /**
     * 显示更新对话框
     */
    private fun showUpdateDialog(context: Context, info: VersionInfo) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage("更新内容：\n${info.changelog}\n\n是否立即更新？")
            .setPositiveButton("立即更新") { _, _ ->
                downloadAndInstall(context, info.apkUrl)
            }
            .setNegativeButton("稍后", null)
            .setCancelable(true)
            .show()
    }

    private fun showResultOnMain(context: Context, title: String, msg: String) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ====== 下载与安装 ======

    @Volatile
    private var downloadId: Long = -1L

    /**
     * 使用系统 DownloadManager 下载 APK（带进度通知、断点续传）
     * 下载到 App 私有目录（避免 Android 6.0+ 外部存储权限问题）
     */
    private fun downloadAndInstall(context: Context, apkUrl: String) {
        try {
            // 使用 App 私有目录作为下载路径，不需要 WRITE_EXTERNAL_STORAGE 权限
            val privateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
            if (!privateDir.exists()) privateDir.mkdirs()
            val target = File(privateDir, APK_FILE_NAME)
            if (target.exists()) target.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("古楚轩船 更新中...")
                .setDescription("正在下载新版本")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(android.net.Uri.fromFile(target))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            // 注册下载完成监听
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            installApk(ctx, target)
                        }
                    }
                },
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )

            showResultOnMain(context, "开始下载", "新版本正在下载，完成后将提示安装")
        } catch (e: Exception) {
            showResultOnMain(context, "下载失败",
                "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 调起系统安装器
     */
    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            showResultOnMain(context, "安装失败", "下载文件不存在")
            return
        }

        try {
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 必须用 FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showResultOnMain(context, "安装失败",
                "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}