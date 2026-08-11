package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ScriptDetailActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvDepart: TextView
    private lateinit var tvComTitle: TextView
    private lateinit var tvComSummary: TextView
    private lateinit var tvTypeName: TextView
    private lateinit var etScript: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var btnSave: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    // 从 Intent 传入的参数
    private var idCom: String = ""
    private var idJoined: String = ""
    private var idJoinedList: String = ""
    private var typeName: String = ""

    // 数据库加载的数据
    private var comTitle: String = ""
    private var comSummary: String = ""
    private var idType: String = ""  // 最后一位字母

    @Volatile
    private var isLoading = false

    // SharedPreferences 用于暂存
    private val PREFS_NAME = "ScriptDraft"
    private val KEY_SCRIPT_PREFIX = "script_"

    companion object {
        const val EXTRA_ID_COM = "id_com"
        const val EXTRA_ID_JOINED = "id_joined"
        const val EXTRA_ID_JOINED_LIST = "id_joined_list"
        const val EXTRA_TYPE_NAME = "type_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 获取传入参数
        idCom = intent.getStringExtra(EXTRA_ID_COM) ?: ""
        idJoined = intent.getStringExtra(EXTRA_ID_JOINED) ?: ""
        idJoinedList = intent.getStringExtra(EXTRA_ID_JOINED_LIST) ?: ""
        typeName = intent.getStringExtra(EXTRA_TYPE_NAME) ?: ""

        // 提取最后一位字母作为 id_type
        idType = if (idJoinedList.isNotEmpty()) idJoinedList.last().toString() else ""

        // 初始化控件
        tvUserName = findViewById(R.id.tv_user_name)
        tvDepart = findViewById(R.id.tv_depart)
        tvComTitle = findViewById(R.id.tv_com_title)
        tvComSummary = findViewById(R.id.tv_com_summary)
        tvTypeName = findViewById(R.id.tv_type_name)
        etScript = findViewById(R.id.et_script)
        tvCharCount = findViewById(R.id.tv_char_count)
        btnSave = findViewById(R.id.btn_save)
        btnSubmit = findViewById(R.id.btn_submit)
        progressBar = findViewById(R.id.progress_bar)

        // 顶部状态栏
        tvUserName.text = MyApp.loginName
        tvDepart.text = MyApp.loginDeaprtName

        // 顶部按钮
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 字数统计
        etScript.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len/1000"
            }
        })

        // 保存按钮 - 暂存到手机
        btnSave.setOnClickListener { saveDraft() }

        // 提交按钮 - 插入 topical_detail
        btnSubmit.setOnClickListener { submitScript() }

        // 加载数据
        loadData()
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 查 commission_summary 获取 com_title, com_summary
                    val escIdCom = idCom.replace("'", "''")
                    val rsCs = conn.query(
                        "SELECT com_title, com_summary FROM commission_summary WHERE id_com = '$escIdCom'"
                    )
                    val csRows = rsCs.toList()
                    if (csRows.isNotEmpty()) {
                        comTitle = csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        comSummary = csRows[0].get(1).toString().removeSurrounding("[", "]").trim()
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvComTitle.text = comTitle.ifEmpty { "[无标题]" }
                    tvComSummary.text = comSummary.ifEmpty { "[无内容]" }
                    tvTypeName.text = typeName

                    // 尝试加载暂存内容
                    loadDraft()

                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 暂存到 SharedPreferences
    private fun saveDraft() {
        val script = etScript.text.toString()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_SCRIPT_PREFIX$idJoinedList", script).apply()

        AlertDialog.Builder(this)
            .setTitle("保存成功")
            .setMessage("脚本内容已暂存到本地")
            .setPositiveButton("确定", null)
            .show()
    }

    // 加载暂存内容
    private fun loadDraft() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val draft = prefs.getString("$KEY_SCRIPT_PREFIX$idJoinedList", "")
        if (!draft.isNullOrEmpty()) {
            etScript.setText(draft)
            tvCharCount.text = "${draft.length}/1000"
        }
    }

    // 提交到 topical_detail
    private fun submitScript() {
        val script = etScript.text.toString().trim()
        if (script.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("脚本内容不能为空")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 计算 id_detail
                    // id_detail = 脚本编号(13位) + 序号(2位) = 15位
                    val prefix = idJoinedList  // 13位
                    val escPrefix = prefix.replace("'", "''")

                    // 查询该前缀已有多少条记录
                    val rsCount = conn.query(
                        "SELECT COUNT(*) FROM topical_detail WHERE id_detail LIKE '$escPrefix%'"
                    )
                    val countRows = rsCount.toList()
                    val existingCount = if (countRows.isNotEmpty()) {
                        countRows[0].get(0).toString().removeSurrounding("[", "]").trim().toIntOrNull() ?: 0
                    } else {
                        0
                    }

                    // 序号 = 现有数量 + 1，不足两位前面补0
                    val seq = (existingCount + 1).toString().padStart(2, '0')
                    val idDetail = prefix + seq  // 15位

                    // 2. 插入记录
                    val escIdDetail = idDetail.replace("'", "''")
                    val escIdEditer = MyApp.loginId.replace("'", "''")
                    val escIdType = idType.replace("'", "''")
                    val escDetailUrl = "".replace("'", "''")  // 默认为空
                    val escIdJoinedDep = MyApp.loginDeaprt.replace("'", "''")
                    val escScript = script.replace("'", "''")

                    val insertSql = "INSERT INTO topical_detail " +
                        "(id_Editer_user, id_type, detail_url, id_detail, id_joined_dep, script) VALUES (" +
                        "'$escIdEditer', '$escIdType', '$escDetailUrl', '$escIdDetail', '$escIdJoinedDep', '$escScript')"

                    conn.execute(insertSql)
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false

                    // 清除暂存
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().remove("$KEY_SCRIPT_PREFIX$idJoinedList").apply()

                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交成功")
                        .setMessage("脚本已提交")
                        .setPositiveButton("确定") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }
}
