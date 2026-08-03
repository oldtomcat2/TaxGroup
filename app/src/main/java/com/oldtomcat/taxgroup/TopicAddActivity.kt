package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TopicAddActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "topic_draft"
        private const val KEY_TITLE = "draft_title"
        private const val KEY_CONTENT = "draft_content"
    }

    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var llImportantTypes: LinearLayout
    private lateinit var llDirectionTypes: LinearLayout

    // 复选框 (topical_type, type_name)
    private val importantChecks = mutableListOf<Pair<CheckBox, String>>()
    private val directionChecks = mutableListOf<Pair<CheckBox, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_add)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topic_add_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        etTitle = findViewById(R.id.et_title)
        etContent = findViewById(R.id.et_content)
        llImportantTypes = findViewById(R.id.ll_important_types)
        llDirectionTypes = findViewById(R.id.ll_direction_types)

        // 返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 刷新按钮（重新加载草稿）
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadDraft()
            Toast.makeText(this, "已重新加载草稿", Toast.LENGTH_SHORT).show()
        }

        // 主页按钮
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 加载本地草稿
        loadDraft()

        // 保存（暂存到本地）
        findViewById<View>(R.id.btn_save).setOnClickListener { saveDraft() }

        // 提交（写入数据库）
        findViewById<View>(R.id.btn_submit).setOnClickListener { submitTopic() }

        // 加载复选框数据
        loadTopicalTypes()
    }

    private fun loadTopicalTypes() {
        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query("SELECT topical_type, type_name, level FROM topical_type ORDER BY level, topical_type")
                    val rows = rs.toList()
                    runOnUiThread {
                        llImportantTypes.removeAllViews()
                        llDirectionTypes.removeAllViews()
                        importantChecks.clear()
                        directionChecks.clear()

                        for (row in rows) {
                            val typeCode = row.get(0).toString().removeSurrounding("[", "]")
                            val typeName = row.get(1).toString().removeSurrounding("[", "]")
                            val level = row.get(2).toString().removeSurrounding("[", "]")

                            val cb = CheckBox(this).apply {
                                text = typeName
                                textSize = 15f
                                setTextColor(android.graphics.Color.parseColor("#333333"))
                                tag = typeCode
                            }
                            val targetList = if (level == "1") importantChecks else directionChecks
                            val targetLayout = if (level == "1") llImportantTypes else llDirectionTypes
                            targetList.add(cb to typeCode)
                            targetLayout.addView(cb)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "加载类型数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadDraft() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "") ?: ""
        val content = prefs.getString(KEY_CONTENT, "") ?: ""
        if (title.isNotEmpty() || content.isNotEmpty()) {
            etTitle.setText(title)
            etContent.setText(content)
            Toast.makeText(this, "已读取上次保存的草稿", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDraft() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_CONTENT, content)
            .apply()

        Toast.makeText(this, "草稿已保存", Toast.LENGTH_SHORT).show()
    }

    private fun submitTopic() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "请输入选题标题", Toast.LENGTH_SHORT).show()
            return
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入主题内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 收集选中的方向（按表中顺序排列）
        val selectedTypes = mutableListOf<String>()
        // 先重要选题（level=1）
        for ((cb, code) in importantChecks) {
            if (cb.isChecked) selectedTypes.add(code)
        }
        // 再选题方向（level=2）
        for ((cb, code) in directionChecks) {
            if (cb.isChecked) selectedTypes.add(code)
        }

        if (selectedTypes.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("至少选择一个选题方向！")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val typeList = selectedTypes.joinToString("")

        Thread {
            try {
                Db.withConnection { conn ->
                    val now = LocalDateTime.now()
                    val yymmddhhmm = now.format(DateTimeFormatter.ofPattern("yyMMddHHmm"))
                    val yymmddhhmmss = now.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                    val deptPadded = MyApp.loginDeaprt.padStart(5, '0')
                    val idCom = "$yymmddhhmm$deptPadded"

                    val titleEsc = title.replace("'", "''")
                    val contentEsc = content.replace("'", "''")
                    val idComEsc = idCom.replace("'", "''")
                    val idDepEsc = MyApp.loginDeaprt.replace("'", "''")
                    val idUserEsc = MyApp.loginId.replace("'", "''")
                    val typeListEsc = typeList.replace("'", "''")

                    // 1. 写入 commission_summary（所有 PK 字段都需填）
                    val sqlInsertTopic = "INSERT INTO commission_summary " +
                        "(id_com, com_title, com_summary, id_dep, id_user, vet, reject_memo, " +
                        "last_mod_user, last_mod_date, date_join_end, date_type_end) " +
                        "VALUES ('$idComEsc', '$titleEsc', '$contentEsc', '$idDepEsc', '$idUserEsc', 0, '', " +
                        "'$idUserEsc', '$yymmddhhmm', '', '')"
                    conn.execute(sqlInsertTopic)

                    // 2. 写入 joined_topical（失败则回滚）
                    val idJoinedDepEsc = MyApp.loginDeaprt.replace("'", "''")
                    val sqlInsertJoin = "INSERT INTO joined_topical " +
                        "(id_com, id_joined_dep, id_joined, type_list) " +
                        "VALUES ('$idComEsc', '$idJoinedDepEsc', '$yymmddhhmmss', '$typeListEsc')"
                    
                    try {
                        conn.execute(sqlInsertJoin)
                    } catch (e: Exception) {
                        // 第二步失败 → 回滚第一步
                        try {
                            conn.execute("DELETE FROM commission_summary WHERE id_com = '$idComEsc'")
                        } catch (_: Exception) {}
                        throw RuntimeException("写入 joined_topical 失败，已撤销选题提交: ${e.message}")
                    }
                }
                runOnUiThread {
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                    AlertDialog.Builder(this@TopicAddActivity)
                        .setTitle("提交成功")
                        .setMessage("选题已提交，进入待审核队列")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TopicAddActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }
}
