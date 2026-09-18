package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class JointDetailActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvContent: TextView
    private lateinit var tvDeadline: TextView
    private lateinit var btnBackBottom: Button
    private lateinit var btnAction1: Button
    private lateinit var btnAction2: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var llImportantTypes: LinearLayout
    private lateinit var llImportantSection: LinearLayout
    private lateinit var llDirectionTypes: LinearLayout

    private var idCom: String = ""
    private var mode: Int = JointCooperationActivity.MODE_AVAILABLE // 0=可参与, 1=查询维护
    private var idDepCreator: String = "" // 出题部门ID

    // 复选框 (topical_type, type_name)
    private val importantChecks = mutableListOf<CheckBox>()
    private val directionChecks = mutableListOf<CheckBox>()

    @Volatile
    private var isLoading = false
    @Volatile
    private var isJoined = false // 是否已加入

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joint_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.joint_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        idCom = intent.getStringExtra("id_com") ?: ""
        mode = intent.getIntExtra("mode", JointCooperationActivity.MODE_AVAILABLE)
        if (idCom.isEmpty()) {
            finish()
            return
        }

        // 绑定控件
        tvTitle = findViewById(R.id.tv_title)
        tvDepartment = findViewById(R.id.tv_department)
        tvContent = findViewById(R.id.tv_content)
        tvDeadline = findViewById(R.id.tv_deadline)
        btnBackBottom = findViewById(R.id.btn_back_bottom)
        btnAction1 = findViewById(R.id.btn_action1)
        btnAction2 = findViewById(R.id.btn_action2)
        progressBar = findViewById(R.id.progress_bar)
        llImportantTypes = findViewById(R.id.ll_important_types)
        llImportantSection = findViewById(R.id.ll_important_section)
        llDirectionTypes = findViewById(R.id.ll_direction_types)

        // 部门级别 <= 2 才显示"重要选题"部分
        if (MyApp.loginDepLevel > 2) {
            llImportantSection.visibility = View.GONE
        }

        // 顶部返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 顶部主页
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 底部按钮
        btnBackBottom.setOnClickListener { finish() }
        btnAction1.setOnClickListener { onAction1Click() }
        btnAction2.setOnClickListener { onAction2Click() }

        // 加载复选框数据
        loadTopicalTypes()

        // 初始加载数据
        loadData()
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
                            if (level == "1") {
                                importantChecks.add(cb)
                                llImportantTypes.addView(cb)
                            } else {
                                directionChecks.add(cb)
                                llDirectionTypes.addView(cb)
                            }
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

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 第一步：查询选题详情
                    val escIdCom = idCom.replace("'", "''")
                    val sql = "SELECT com_title, id_dep, com_summary, date_join_end " +
                        "FROM commission_summary WHERE id_com = '$escIdCom'"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    if (rows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            AlertDialog.Builder(this@JointDetailActivity)
                                .setTitle("提示")
                                .setMessage("未找到该选题")
                                .setPositiveButton("确定") { _, _ -> finish() }
                                .show()
                            isLoading = false
                        }
                        return@withConnection
                    }

                    val row = rows[0]
                    val comTitle = row.get(0).toString().removeSurrounding("[", "]")
                    val idDep = row.get(1).toString().removeSurrounding("[", "]")
                    val comSummary = row.get(2).toString().removeSurrounding("[", "]")
                    val dateJoinEnd = row.get(3).toString().removeSurrounding("[", "]")
                    idDepCreator = idDep

                    // 第二步：查询出题部门名称
                    val escIdDep = idDep.replace("'", "''")
                    val depRs = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep'")
                    val depRows = depRs.toList()
                    val nameDep = if (depRows.isNotEmpty()) {
                        depRows[0].get(0).toString().removeSurrounding("[", "]")
                    } else {
                        idDep
                    }

                    // 第三步：查询当前用户是否已加入
                    val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
                    val checkSql = "SELECT 1 FROM joined_topical WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                    val checkRs = conn.query(checkSql)
                    val alreadyJoined = checkRs.toList().isNotEmpty()

                    // 第四步：查询已加入的 type_list（如已加入则回显勾选）
                    val typeListRs = conn.query("SELECT type_list FROM joined_topical WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'")
                    val typeListRows = typeListRs.toList()
                    val typeList = if (typeListRows.isNotEmpty()) {
                        typeListRows[0].get(0).toString().removeSurrounding("[", "]")
                    } else {
                        ""
                    }

                    // 转换截止日期格式 yyMMdd -> yyyy-MM-dd
                    val displayDate = try {
                        if (dateJoinEnd.length == 6) {
                            val year = "20${dateJoinEnd.substring(0, 2)}"
                            val month = dateJoinEnd.substring(2, 4)
                            val day = dateJoinEnd.substring(4, 6)
                            "$year-$month-$day"
                        } else {
                            dateJoinEnd
                        }
                    } catch (_: Exception) {
                        dateJoinEnd
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvTitle.text = comTitle
                        tvDepartment.text = nameDep
                        tvContent.text = comSummary
                        tvDeadline.text = displayDate
                        isJoined = alreadyJoined

                        // 根据模式和是否已加入设置按钮
                        updateButtons()

                        // 回显勾选
                        for (cb in importantChecks) {
                            cb.isChecked = typeList.contains(cb.tag.toString())
                        }
                        for (cb in directionChecks) {
                            cb.isChecked = typeList.contains(cb.tag.toString())
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun updateButtons() {
        when (mode) {
            JointCooperationActivity.MODE_AVAILABLE -> {
                // 可参与模式：显示"加入选题"按钮
                btnAction1.visibility = View.GONE
                btnAction2.visibility = View.VISIBLE
                btnAction2.text = if (isJoined) "已加入" else "加入选题"
                btnAction2.isEnabled = !isJoined
            }
            JointCooperationActivity.MODE_JOINED -> {
                // 查询维护模式：显示"提交修改"和"退出选题"
                btnAction1.visibility = View.VISIBLE
                btnAction2.visibility = View.VISIBLE
                btnAction1.text = "提交修改"
                btnAction2.text = "退出选题"
                btnAction1.isEnabled = true
                btnAction2.isEnabled = true
            }
        }
    }

    private fun onAction1Click() {
        if (mode == JointCooperationActivity.MODE_JOINED) {
            // 提交修改
            doUpdateTypeList()
        }
    }

    private fun onAction2Click() {
        when (mode) {
            JointCooperationActivity.MODE_AVAILABLE -> {
                // 加入选题
                doJoin()
            }
            JointCooperationActivity.MODE_JOINED -> {
                // 退出选题
                doQuit()
            }
        }
    }

    private fun doJoin() {
        if (isLoading || isJoined) return

        // 收集选中的类型（按表中顺序：先重要选题，再选题方向）
        val selectedTypes = mutableListOf<String>()
        for (cb in importantChecks) {
            if (cb.isChecked) selectedTypes.add(cb.tag.toString())
        }
        for (cb in directionChecks) {
            if (cb.isChecked) selectedTypes.add(cb.tag.toString())
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

        // 确认对话框
        AlertDialog.Builder(this)
            .setTitle("加入选题")
            .setMessage("确定要加入选题「${tvTitle.text}」吗？\n已选类型：$typeList")
            .setPositiveButton("确定") { _, _ -> executeJoin(typeList) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeJoin(typeList: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        btnAction2.isEnabled = false

        val escIdCom = idCom.replace("'", "''")
        val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
        val typeListEsc = typeList.replace("'", "''")
        val idJoined = generateIdJoined()

        Thread {
            try {
                Db.withConnection { conn ->
                    val insertSql = "INSERT INTO joined_topical (id_com, id_joined_dep, id_joined, type_list, ps) VALUES (" +
                        "'$escIdCom', '$escLoginDep', '$idJoined', '$typeListEsc', 1  )"
                    conn.execute(insertSql)
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isJoined = true
                    updateButtons()
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("成功")
                        .setMessage("已成功加入选题")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAction2.isEnabled = true
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("错误")
                        .setMessage("加入失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun doUpdateTypeList() {
        // 收集选中的类型（按表中顺序：先重要选题，再选题方向）
        val selectedTypes = mutableListOf<String>()
        for (cb in importantChecks) {
            if (cb.isChecked) selectedTypes.add(cb.tag.toString())
        }
        for (cb in directionChecks) {
            if (cb.isChecked) selectedTypes.add(cb.tag.toString())
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

        // 确认对话框
        AlertDialog.Builder(this)
            .setTitle("提交修改")
            .setMessage("确定要更新选题方向吗？\n新类型：$typeList")
            .setPositiveButton("确定") { _, _ -> executeUpdateTypeList(typeList) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeUpdateTypeList(typeList: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        btnAction1.isEnabled = false

        val escIdCom = idCom.replace("'", "''")
        val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
        val typeListEsc = typeList.replace("'", "''")

        Thread {
            try {
                Db.withConnection { conn ->
                    val updateSql = "UPDATE joined_topical SET type_list = '$typeListEsc' " +
                        "WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                    conn.execute(updateSql)
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAction1.isEnabled = true
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("成功")
                        .setMessage("选题方向已更新")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAction1.isEnabled = true
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("错误")
                        .setMessage("更新失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun doQuit() {
        AlertDialog.Builder(this)
            .setTitle("退出选题")
            .setMessage("是否需要退出该选题？")
            .setPositiveButton("确定") { _, _ -> executeQuit() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeQuit() {
        if (isLoading) return

        // 先验证：本单位创建选题，不得删除
        if (idDepCreator == MyApp.loginDeaprt) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("本单位创建选题，不得删除")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        isLoading = true
        progressBar.visibility = View.VISIBLE
        btnAction2.isEnabled = false

        val escIdCom = idCom.replace("'", "''")
        val escLoginDep = MyApp.loginDeaprt.replace("'", "''")

        Thread {
            try {
                Db.withConnection { conn ->
                    val deleteSql = "DELETE FROM joined_topical " +
                        "WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                    conn.execute(deleteSql)
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isJoined = false
                    updateButtons()
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("成功")
                        .setMessage("已退出该选题")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAction2.isEnabled = true
                    AlertDialog.Builder(this@JointDetailActivity)
                        .setTitle("错误")
                        .setMessage("退出失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 生成 id_joined: yyMMddHHmmss（12位）
    private fun generateIdJoined(): String {
        val sdf = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }
}
