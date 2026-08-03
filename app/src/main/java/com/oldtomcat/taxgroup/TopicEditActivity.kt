package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class TopicEditActivity : AppCompatActivity() {

    private lateinit var tvDate: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvModUser: TextView
    private lateinit var tvModDate: TextView
    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var tvAuditStatus: TextView
    private lateinit var llRejectMemo: LinearLayout
    private lateinit var tvRejectMemo: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnDelete: Button
    private lateinit var btnBackBottom: Button
    private lateinit var progressBar: ProgressBar

    private var idCom: String = ""
    private var idUser: String = ""  // 选题作者 id
    private var vet: Int = 0  // 当前审核状态：0未审核 1已审核 2未通过

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_edit)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topic_edit_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        idCom = intent.getStringExtra("id_com") ?: ""
//        idCom = "2607201608080001"
        if (idCom.isEmpty()) {
            Toast.makeText(this, "参数错误：未指定记录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 绑定控件
        tvDate = findViewById(R.id.tv_date)
        tvAuthor = findViewById(R.id.tv_author)
        tvModUser = findViewById(R.id.tv_mod_user)
        tvModDate = findViewById(R.id.tv_mod_date)
        etTitle = findViewById(R.id.et_title)
        etContent = findViewById(R.id.et_content)
        tvAuditStatus = findViewById(R.id.tv_audit_status)
        llRejectMemo = findViewById(R.id.ll_reject_memo)
        tvRejectMemo = findViewById(R.id.tv_reject_memo)
        btnSubmit = findViewById(R.id.btn_submit)
        btnDelete = findViewById(R.id.btn_delete)
        btnBackBottom = findViewById(R.id.btn_back_bottom)
        progressBar = findViewById(R.id.progress_bar)

        // 顶部返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 顶部刷新
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadData()
        }

        // 顶部主页
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 底部按钮事件
        btnBackBottom.setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submitChanges() }
        btnDelete.setOnClickListener { confirmDelete() }

        // 初始加载
        loadData()
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        Thread {
            try {
                Db.withConnection { conn ->
                    val sql = "SELECT id_com, com_title, com_summary, vet, reject_memo, b.id_dep, " +
                        "name_user, a.last_mod_user, a.last_mod_date " +
                        "FROM commission_summary a, User b " +
                        "WHERE (a.id_user = b.id_user) AND a.id_com = '$idCom'"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    if (rows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("提示")
                                .setMessage("未找到该记录（id_com=$idCom）")
                                .setPositiveButton("确定") { _, _ -> finish() }
                                .show()
                            isLoading = false
                        }
                        return@withConnection
                    }

                    val row = rows[0]
                    val idComValue = row.get(0).toString().removeSurrounding("[", "]")
                    val comTitle = row.get(1).toString().removeSurrounding("[", "]")
                    val comSummary = row.get(2).toString().removeSurrounding("[", "]")
                    val vetValue = row.get(3).toString().removeSurrounding("[", "]")
                    val rejectMemo = row.get(4).toString().removeSurrounding("[", "]")
                    val idUserValue = row.get(5).toString().removeSurrounding("[", "]")
                    val nameUser = row.get(6).toString().removeSurrounding("[", "]")
                    val lastModUser = row.get(7).toString().removeSurrounding("[", "]")
                    val lastModDate = row.get(8).toString().removeSurrounding("[", "]")

                    // 最后修改人：用 last_mod_user 关联 User 表查 name_user
                    var modUserName = "无"
                    if (lastModUser.isNotEmpty() && lastModUser != "null") {
                        try {
                            val rs2 = conn.query("SELECT name_user FROM User WHERE id_user = '$lastModUser'")
                            val rows2 = rs2.toList()
                            if (rows2.isNotEmpty()) {
                                modUserName = rows2[0].get(0).toString().removeSurrounding("[", "]")
                            }
                        } catch (_: Exception) {
                            // 忽略，保持“无”
                        }
                    }

                    // 最后修改日期：取 月(第3-4位) + 日(第5-6位)，格式 MM月dd日
                    val modDateText = if (lastModDate.isNotEmpty() && lastModDate != "null") {
                        val s = lastModDate.padStart(8, '0')
                        val month = s.substring(2, 4)
                        val day = s.substring(4, 6)
                        "${month}月${day}日"
                    } else {
                        "无"
                    }

                    idUser = idUserValue

                    vet = when {
                        vetValue == "1" -> 1
                        vetValue == "2" -> 2
                        else -> 0
                    }

                    val month = if (idComValue.length >= 6) idComValue.substring(2, 4) else "--"
                    val day = if (idComValue.length >= 6) idComValue.substring(4, 6) else "--"
                    val dateText = "${month}月${day}日"

                    val statusText = when (vet) {
                        1 -> "已审核"
                        2 -> "未通过"
                        else -> "未审核"
                    }

                    val hasMemo = rejectMemo.isNotEmpty() && rejectMemo != "null" && rejectMemo != "[]"

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvDate.text = dateText
                        tvAuthor.text = nameUser
                        tvModUser.text = modUserName
                        tvModDate.text = modDateText
                        etTitle.setText(comTitle)
                        etContent.setText(comSummary)
                        tvAuditStatus.text = statusText
                        tvAuditStatus.setTextColor(
                            when (vet) {
                                1 -> android.graphics.Color.parseColor("#2E7D32")
                                2 -> android.graphics.Color.parseColor("#C62828")
                                else -> android.graphics.Color.parseColor("#EF6C00")
                            }
                        )
                        if (hasMemo) {
                            llRejectMemo.visibility = View.VISIBLE
                            tvRejectMemo.text = rejectMemo
                        } else {
                            llRejectMemo.visibility = View.GONE
                        }
                        if (vet == 0) {
                            btnSubmit.isEnabled = true
                            btnSubmit.alpha = 1f
                            btnDelete.isEnabled = true
                            btnDelete.alpha = 1f
                        } else {
                            btnSubmit.isEnabled = false
                            btnSubmit.alpha = 0.5f
                            btnDelete.isEnabled = false
                            btnDelete.alpha = 0.5f
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun submitChanges() {
        if (vet != 0) {
            Toast.makeText(this, "已审核或未通过的选题不能修改", Toast.LENGTH_SHORT).show()
            return
        }

        val newTitle = etTitle.text.toString().trim()
        val newContent = etContent.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (newContent.isEmpty()) {
            Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("确认提交")
            .setMessage("确定要保存修改吗？")
            .setPositiveButton("确定") { _, _ -> doSubmit(newTitle, newContent) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doSubmit(newTitle: String, newContent: String) {
        if (isLoading) return
        isLoading = true
        val escTitle = newTitle.replace("'", "''")
        val escContent = newContent.replace("'", "''")
        val escLoginId = MyApp.loginId.replace("'", "''")
        val escIdCom = idCom.replace("'", "''")
        // 当前时间：年(2位)+月(2位)+日(2位)+时(2位)+分(2位) → 如 2607231113（yyMMddHHmm，10位）
        // 与字段名 last_mod_date 语义一致，且结果页按“MM月dd日”显示需含“日”
        val sdf = java.text.SimpleDateFormat("yyMMddHHmm", java.util.Locale.getDefault())
        val lastModDate = sdf.format(java.util.Date())
        Thread {
            try {
                Db.withConnection { conn ->
                    val sql = "UPDATE commission_summary SET " +
                        "com_title = '$escTitle', " +
                        "com_summary = '$escContent', " +
                        "last_mod_user = '$escLoginId', " +
                        "last_mod_date = '$lastModDate' " +
                        "WHERE id_com = '$escIdCom'"
                    conn.execute(sql)
                }
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("成功")
                        .setMessage("修改已保存\n提交时间：$lastModDate")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("错误")
                        .setMessage("提交失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun confirmDelete() {
        if (vet != 0) {
            Toast.makeText(this, "已审核或未通过的选题不能删除。", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证是否作者本人
        if (idUser.isNotEmpty() && idUser != MyApp.loginId) {
            Toast.makeText(this, "只有作者本人可以删除记录", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("删除后不可恢复，确定要删除该选题吗？")
            .setPositiveButton("删除") { _, _ -> doDelete() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doDelete() {
        if (isLoading) return
        isLoading = true
        Thread {
            try {
                Db.withConnection { conn ->
                    val sql = "DELETE FROM commission_summary WHERE id_com = '$idCom'"
                    conn.execute(sql)
                }
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("成功")
                        .setMessage("记录已删除")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("错误")
                        .setMessage("删除失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }
}
