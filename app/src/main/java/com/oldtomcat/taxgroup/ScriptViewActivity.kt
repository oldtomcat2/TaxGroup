package com.oldtomcat.taxgroup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 脚本查看弹窗（只读）
 * - 通过 Intent extra 接收 id_detail
 * - 查 topical_detail.script 显示
 * - 提供"复制全部"按钮
 */
class ScriptViewActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progressBar: ProgressBar

    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_view_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val idDetail = intent.getStringExtra(EXTRA_ID_DETAIL) ?: ""
        if (idDetail.isEmpty()) {
            Toast.makeText(this, "参数错误:未指定脚本", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvContent = findViewById(R.id.tv_script_content)
        tvSubtitle = findViewById(R.id.tv_script_subtitle)
        progressBar = findViewById(R.id.progress_bar)

        // 副标题：类型 - 部门
        val typeName = intent.getStringExtra("type_name") ?: ""
        val depName = intent.getStringExtra("dep_name") ?: ""
        tvSubtitle.text = when {
            typeName.isNotEmpty() && depName.isNotEmpty() -> "$typeName · $depName"
            typeName.isNotEmpty() -> typeName
            depName.isNotEmpty() -> depName
            else -> ""
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_back_bottom).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_copy).setOnClickListener { copyToClipboard() }

        loadScript(idDetail)
    }

    private fun loadScript(idDetail: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        val escIdDetail = idDetail.replace("'", "''")

        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query(
                        "SELECT script FROM topical_detail WHERE id_detail = '$escIdDetail' LIMIT 1"
                    )
                    val rows = rs.toList()
                    val scriptText = if (rows.isNotEmpty()) {
                        rows[0].get(0).toString().removeSurrounding("[", "]").trim()
                    } else {
                        "（未找到脚本内容）"
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvContent.text = scriptText
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvContent.text = "加载失败：${e.javaClass.simpleName}\n${e.message}"
                    isLoading = false
                }
            }
        }.start()
    }

    private fun copyToClipboard() {
        val text = tvContent.text.toString()
        if (text.isEmpty() || text == "加载失败：...") {
            Toast.makeText(this, "暂无内容可复制", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("script", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ID_DETAIL = "id_detail"
    }
}
