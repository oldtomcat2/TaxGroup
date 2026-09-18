package com.oldtomcat.taxgroup

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar

class WorkUploadEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID_COM = "extra_id_com"
        const val EXTRA_ID_DETAIL = "extra_id_detail"
    }

    private lateinit var tvComTitle: TextView
    private lateinit var tvComSummary: TextView
    private lateinit var tvScript: TextView
    private lateinit var cardAttachmentReadonly: MaterialCardView
    private lateinit var llAttachmentReadonly: View
    private lateinit var tvAttachmentName: TextView
    private lateinit var btnPreviewAttachment: Button
    private lateinit var etFinalTitle: EditText
    private lateinit var btnSelectAttachment: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var llSelectRow: View
    private lateinit var llPreviewRow: View
    private lateinit var tvFinalFileName: TextView
    private lateinit var imgThumbnail: ImageView
    private lateinit var btnPreviewFinal: Button
    private lateinit var btnReselectAttachment: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var progress: ProgressBar

    private var idCom: String = ""
    private var idDetail: String = ""
    private var currentAttachmentUrl: String = ""
    private var currentAttachmentName: String = ""
    private var currentFinalWorkUrl: String = ""
    private var currentFinalWorkName: String = ""
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""

    private val selectFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = getFileNameFromUri(uri)
            tvSelectedFile.text = selectedFileName
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_upload_edit)

        idCom = intent.getStringExtra(EXTRA_ID_COM) ?: ""
        idDetail = intent.getStringExtra(EXTRA_ID_DETAIL) ?: ""

        tvComTitle = findViewById(R.id.tv_com_title)
        tvComSummary = findViewById(R.id.tv_com_summary)
        tvScript = findViewById(R.id.tv_script)
        cardAttachmentReadonly = findViewById(R.id.card_attachment_readonly)
        llAttachmentReadonly = findViewById(R.id.ll_attachment_readonly)
        tvAttachmentName = findViewById(R.id.tv_attachment_name)
        btnPreviewAttachment = findViewById(R.id.btn_preview_attachment)
        etFinalTitle = findViewById(R.id.et_final_title)
        btnSelectAttachment = findViewById(R.id.btn_select_attachment)
        tvSelectedFile = findViewById(R.id.tv_selected_file)
        llSelectRow = findViewById(R.id.ll_select_row)
        llPreviewRow = findViewById(R.id.ll_preview_row)
        tvFinalFileName = findViewById(R.id.tv_final_file_name)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        btnPreviewFinal = findViewById(R.id.btn_preview_final)
        btnReselectAttachment = findViewById(R.id.btn_reselect_attachment)
        btnSubmit = findViewById(R.id.btn_submit)
        btnCancel = findViewById(R.id.btn_cancel)
        progress = findViewById(R.id.progress)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)
        imgThumbnail = findViewById(R.id.img_thumbnail)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }

        btnSelectAttachment.setOnClickListener {
            selectFileLauncher.launch("*/*")
        }
        btnReselectAttachment.setOnClickListener {
            selectFileLauncher.launch("*/*")
        }
        btnPreviewFinal.setOnClickListener {
            previewAttachment(currentFinalWorkUrl, currentFinalWorkName)
        }

        btnPreviewAttachment.setOnClickListener {
            previewAttachment(currentAttachmentUrl, currentAttachmentName)
        }

        btnSubmit.setOnClickListener {
            submit()
        }

        if (idDetail.isEmpty()) {
            Toast.makeText(this, "缺少 id_detail 参数", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loadData()
    }

    private fun loadData() {
        progress.visibility = View.VISIBLE
        Thread {
            try {
                var comTitle = ""
                var comSummary = ""
                var script = ""
                var attachmentUrl = ""
                var attachmentName = ""
                var finalTitle = ""
                var finalWork = ""

                Db.withConnection { conn ->
                    // 1. 选题信息（按 id_com 查）
                    val escIdCom = idCom.replace("'", "''")
                    val rsCs = conn.query("SELECT com_title, com_summary FROM commission_summary WHERE id_com = '$escIdCom' LIMIT 1")
                    val csRows = rsCs.toList()
                    if (csRows.isNotEmpty()) {
                        comTitle = csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        comSummary = csRows[0].get(1).toString().removeSurrounding("[", "]").trim()
                    }

                    // 2. 脚本/附件/final_title/final_work（按 id_detail 查）
                    val escIdDetail = idDetail.replace("'", "''")
                    val rsTd = conn.query("SELECT script, attachment_url, attachment_name, final_title, final_work FROM topical_detail WHERE id_detail = '$escIdDetail' LIMIT 1")
                    val tdRows = rsTd.toList()
                    if (tdRows.isNotEmpty()) {
                        script = tdRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        attachmentUrl = tdRows[0].get(1).toString().removeSurrounding("[", "]").trim()
                        attachmentName = tdRows[0].get(2).toString().removeSurrounding("[", "]").trim()
                        finalTitle = tdRows[0].get(3).toString().removeSurrounding("[", "]").trim()
                        finalWork = tdRows[0].get(4).toString().removeSurrounding("[", "]").trim()
                    }
                }

                currentAttachmentUrl = attachmentUrl
                currentAttachmentName = attachmentName
                currentFinalWorkUrl = finalWork
                currentFinalWorkName = if (finalWork.isNotEmpty()) finalWork.substringAfterLast('/') else ""
                currentFinalWorkUrl = finalWork
                currentFinalWorkName = finalWork.substringAfterLast('/')

                runOnUiThread {
                    progress.visibility = View.GONE
                    tvComTitle.text = comTitle.ifEmpty { "[无选题标题]" }
                    tvComSummary.text = comSummary.ifEmpty { "[无选题内容]" }
                    tvScript.text = script.ifEmpty { "[无脚本内容]" }

                    if (attachmentUrl.isNotEmpty()) {
                        cardAttachmentReadonly.visibility = View.VISIBLE
                        tvAttachmentName.text = attachmentName.ifEmpty { attachmentUrl.substringAfterLast('/') }
                    } else {
                        cardAttachmentReadonly.visibility = View.GONE
                    }

                    etFinalTitle.setText(finalTitle)

                    // “上传附件”区：已上传 → “预览附件 + 重新上传”；未上传 → “选择文件”
                    if (finalWork.isNotEmpty()) {
                        llSelectRow.visibility = View.GONE
                        llPreviewRow.visibility = View.VISIBLE
                        tvFinalFileName.text = currentFinalWorkName.ifEmpty { finalWork }
                        loadThumbnailForUrl(finalWork)
                    } else {
                        llSelectRow.visibility = View.VISIBLE
                        llPreviewRow.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    Toast.makeText(this, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** 智能预览附件：图片直开，Office 走微软 Online */
    private fun previewAttachment(url: String, name: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "附件为空", Toast.LENGTH_SHORT).show()
            return
        }
        val ext = url.substringAfterLast('.', "").lowercase()
        val previewUrl = when (ext) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> url
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf" ->
                "https://view.officeapps.live.com/op/view.aspx?src=" + URLEncoder.encode(url, "UTF-8")
            else -> url
        }
        val intent = Intent(this, WebViewPreviewActivity::class.java)
        intent.putExtra(WebViewPreviewActivity.EXTRA_URL, previewUrl)
        intent.putExtra(WebViewPreviewActivity.EXTRA_TITLE, name.ifEmpty { url.substringAfterLast('/') })
        startActivity(intent)
    }

    private fun submit() {
        val finalTitle = etFinalTitle.text.toString().trim()
        if (finalTitle.isEmpty()) {
            Toast.makeText(this, "请填写作品标题", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        progress.visibility = View.VISIBLE

        if (selectedFileUri != null) {
            // 有新选附件：先上传到七牛，写入 final_title + final_work + final_date
            uploadAndSave(finalTitle)
        } else {
            // 没选附件：只更新 final_title + final_date，final_work 保留原值
            saveToDb(finalTitle, null)
        }
    }

    private fun uploadAndSave(finalTitle: String) {
        val uri = selectedFileUri ?: return
        val key = "${idDetail}_${System.currentTimeMillis()}"
        Log.d("WorkUpload", "开始上传附件: $selectedFileName, key=$key")

        fetchUploadToken(key) { token, host, retKey, err ->
            if (token == null) {
                runOnUiThread {
                    btnSubmit.isEnabled = true
                    progress.visibility = View.GONE
                    Toast.makeText(this, "获取上传凭证失败: $err", Toast.LENGTH_LONG).show()
                }
                return@fetchUploadToken
            }
            uploadToQiniu(uri, token, retKey ?: key, host ?: "https://upload.qiniup.com") { url, uploadErr ->
                if (url == null) {
                    runOnUiThread {
                        btnSubmit.isEnabled = true
                        progress.visibility = View.GONE
                        Toast.makeText(this, "上传失败: $uploadErr", Toast.LENGTH_LONG).show()
                    }
                    return@uploadToQiniu
                }
                saveToDb(finalTitle, url)
            }
        }
    }

    private fun saveToDb(finalTitle: String, finalWorkUrl: String?) {
        Thread {
            try {
                val escTitle = finalTitle.replace("'", "''")
                val escIdDetail = idDetail.replace("'", "''")
                val escIdCom = idCom.replace("'", "''")
                // final_date 默认取 yymmdd
                val cal = Calendar.getInstance()
                val yy = String.format("%02d", cal.get(Calendar.YEAR) % 100)
                val mm = String.format("%02d", cal.get(Calendar.MONTH) + 1)
                val dd = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
                val finalDate = "$yy$mm$dd"

                var success = false
                Db.withConnection { conn ->
                    val sqlTd = if (finalWorkUrl != null) {
                        val escWork = finalWorkUrl.replace("'", "''")
                        "UPDATE topical_detail SET final_title = '$escTitle', final_work = '$escWork', final_date = '$finalDate' WHERE id_detail = '$escIdDetail'"
                    } else {
                        "UPDATE topical_detail SET final_title = '$escTitle', final_date = '$finalDate' WHERE id_detail = '$escIdDetail'"
                    }
                    conn.execute(sqlTd)
                    Log.d("WorkUpload", "UPDATE topical_detail OK, rows=$finalTitle")

                    val sqlCs = "UPDATE commission_summary SET vet = 6 WHERE id_com = '$escIdCom'"
                    conn.execute(sqlCs)
                    Log.d("WorkUpload", "UPDATE commission_summary OK, vet=6")
                    success = true
                }

                runOnUiThread {
                    progress.visibility = View.GONE
                    if (success) {
                        Toast.makeText(this, "提交成功", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        btnSubmit.isEnabled = true
                        Toast.makeText(this, "提交失败", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    btnSubmit.isEnabled = true
                    Log.e("WorkUpload", "save error: ${e.message}", e)
                    Toast.makeText(this, "提交失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun fetchUploadToken(key: String, cb: (String?, String?, String?, String?) -> Unit) {
        Thread {
            try {
                val urlStr = "${QiniuConfig.WORKER_BASE}${QiniuConfig.TOKEN_PATH}?key=${URLEncoder.encode(key, "UTF-8")}"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                } else {
                    conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                if (code in 200..299) {
                    val json = JSONObject(resp)
                    val token = json.optString("uploadToken")
                    val host = json.optString("uploadHost", "https://upload.qiniup.com")
                    val retKey = json.optString("key", key)
                    cb(token, host, retKey, null)
                } else {
                    cb(null, null, null, "HTTP $code: $resp")
                }
            } catch (e: Exception) {
                cb(null, null, null, "Exception: ${e.message}")
            }
        }.start()
    }

    private fun uploadToQiniu(fileUri: Uri, token: String, key: String, host: String, onResult: (String?, String?) -> Unit) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(fileUri)
                    ?: throw RuntimeException("无法读取文件")
                val boundary = "----Boundary${System.currentTimeMillis()}"
                val conn = URL(host).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                val os = conn.outputStream
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(os, StandardCharsets.UTF_8))
                fun writeLine(s: String) { writer.write(s); writer.write("\r\n") }
                writeLine("--$boundary")
                writeLine("Content-Disposition: form-data; name=\"token\"")
                writeLine("")
                writeLine(token)
                writeLine("--$boundary")
                writeLine("Content-Disposition: form-data; name=\"key\"")
                writeLine("")
                writeLine(key)
                writeLine("--$boundary")
                val safeFileName = selectedFileName.replace("\"", "_")
                writeLine("Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"")
                writeLine("Content-Type: application/octet-stream")
                writeLine("")
                writer.flush()
                inputStream.copyTo(os)
                os.flush()
                writeLine("")
                writeLine("--$boundary--")
                writer.flush()
                writer.close()
                inputStream.close()
                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                } else {
                    conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                if (code in 200..299) {
                    val json = JSONObject(resp)
                    val returnedKey = json.optString("key", key)
                    val url = "${QiniuConfig.CUSTOM_DOMAIN.trimEnd('/')}/$returnedKey"
                    onResult(url, null)
                } else {
                    onResult(null, "HTTP $code: $resp")
                }
            } catch (e: Exception) {
                onResult(null, "Exception: ${e.message}")
            }
        }.start()
    }

    /** 按文件扩展名加载缩略图：图片下载、视频走七牛 vframe 截帧、文档用图标 */
    private fun loadThumbnailForUrl(url: String) {
        // 默认先展示文档图标，下载成功后再覆盖
        imgThumbnail.setImageResource(R.drawable.ic_doc)
        val ext = url.substringAfterLast('.', "").lowercase()
        val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        val isVideo = ext in listOf("mp4", "mov", "avi", "mkv", "flv", "wmv", "m4v")
        if (!isImage && !isVideo) {
            // 文档类：直接用 ic_doc，不下载
            return
        }
        val downloadUrl = if (isVideo) {
            // 七牛 Kodo 视频截帧参数：取第0秒，宽240，高180
            "$url?vframe/jpg/offset/0/w/240/h/180"
        } else {
            url
        }
        Thread {
            try {
                val conn = URL(downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    return@Thread
                }
                val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
                if (bitmap != null) {
                    runOnUiThread {
                        imgThumbnail.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.w("WorkUpload", "缩略图加载失败: ${e.message}")
            }
        }.start()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = ""
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: ""
                }
            }
        } catch (_: Exception) { }
        if (name.isEmpty()) name = "file_${System.currentTimeMillis()}"
        return name
    }
}