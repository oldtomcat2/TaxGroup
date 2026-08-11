package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TopicAuditDetailActivity : AppCompatActivity() {

    private lateinit var tvDate: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvIdDep: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvContent: TextView
    private lateinit var rgAuditStatus: RadioGroup
    private lateinit var rbPass: RadioButton
    private lateinit var rbReject: RadioButton
    private lateinit var llRejectMemo: LinearLayout
    private lateinit var etRejectMemo: EditText
    private lateinit var llJointPub: LinearLayout
    private lateinit var tvJointEndDate: TextView
    private lateinit var llImportantTypes: LinearLayout
    private lateinit var llDirectionTypes: LinearLayout
    private lateinit var btnBackBottom: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    private var idCom: String = ""
    private var idDep: String = ""
    private var jointEndDateStr: String = "" // yyMMdd 格式

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_audit_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.audit_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        idCom = intent.getStringExtra("id_com") ?: ""
        if (idCom.isEmpty()) {
            finish()
            return
        }

        // 绑定控件
        tvDate = findViewById(R.id.tv_date)
        tvAuthor = findViewById(R.id.tv_author)
        tvDepartment = findViewById(R.id.tv_department)
        tvIdDep = findViewById(R.id.tv_id_dep)
        tvTitle = findViewById(R.id.tv_title)
        tvContent = findViewById(R.id.tv_content)
        rgAuditStatus = findViewById(R.id.rg_audit_status)
        rbPass = findViewById(R.id.rb_pass)
        rbReject = findViewById(R.id.rb_reject)
        llRejectMemo = findViewById(R.id.ll_reject_memo)
        etRejectMemo = findViewById(R.id.et_reject_memo)
        llJointPub = findViewById(R.id.ll_joint_pub)
        tvJointEndDate = findViewById(R.id.tv_joint_end_date)
        llImportantTypes = findViewById(R.id.ll_audit_important_types)
        llDirectionTypes = findViewById(R.id.ll_audit_direction_types)
        btnBackBottom = findViewById(R.id.btn_back_bottom)
        btnSubmit = findViewById(R.id.btn_submit)
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

        // 单选按钮：切换显示/隐藏
        rgAuditStatus.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_pass -> {
                    llRejectMemo.visibility = View.GONE
                    llJointPub.visibility = View.VISIBLE
                    // 默认设置为当前+7天
                    setDefaultJointDate()
                }
                R.id.rb_reject -> {
                    llRejectMemo.visibility = View.VISIBLE
                    llJointPub.visibility = View.GONE
                    // 默认填入"不符题意"
                    if (etRejectMemo.text.isNullOrBlank()) {
                        etRejectMemo.setText("不符题意")
                    }
                }
            }
        }

        // 日期选择点击
        tvJointEndDate.setOnClickListener {
            showDatePicker()
        }

        // 底部按钮事件
        btnBackBottom.setOnClickListener { finish() }
        btnSubmit.setOnClickListener { doSubmit() }

        // 初始加载
        loadData()
    }

    private fun setDefaultJointDate() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 7)
        jointEndDateStr = formatDateToYyMMdd(calendar.time)
        tvJointEndDate.text = formatDateToDisplay(calendar.time)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        // 尝试解析当前设置的日期
        try {
            val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val date = sdf.parse(jointEndDateStr)
            if (date != null) {
                calendar.time = date
            }
        } catch (_: Exception) {
            // 使用默认+7天
            calendar.add(Calendar.DAY_OF_MONTH, 7)
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth)
                jointEndDateStr = formatDateToYyMMdd(selectedCal.time)
                tvJointEndDate.text = formatDateToDisplay(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDateToYyMMdd(date: java.util.Date): String {
        val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
        return sdf.format(date)
    }

    private fun formatDateToDisplay(date: java.util.Date): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        return sdf.format(date)
    }

    // 生成id_joined: yyMMddHHmmss（12位）
    private fun generateIdJoined(): String {
        val sdf = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        Thread {
            try {
                Db.withConnection { conn ->
                    // 第一步：取主记录（不连接 Department 表，避免报错）
                    val sql = "SELECT a.id_com, a.com_title, a.com_summary, a.id_dep, " +
                        "c.name_user " +
                        "FROM commission_summary a, User c " +
                        "WHERE a.id_user = c.id_user " +
                        "AND a.id_com = '$idCom'"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    if (rows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            AlertDialog.Builder(this@TopicAuditDetailActivity)
                                .setTitle("提示")
                                .setMessage("未找到该记录")
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
                    val idDepValue = row.get(3).toString().removeSurrounding("[", "]")
                    this.idDep = idDepValue
                    val nameUser = row.get(4).toString().removeSurrounding("[", "]")

                    // 第二步：单独查部门名称
                    val escIdDep = idDepValue.replace("'", "''")
                    val depRs = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep'")
                    val depRows = depRs.toList()
                    val nameDep = if (depRows.isNotEmpty()) {
                        depRows[0].get(0).toString().removeSurrounding("[", "]")
                    } else {
                        idDepValue
                    }

                    // 录入日期：从 id_com 取月、日
                    val month = if (idComValue.length >= 4) idComValue.substring(2, 4) else "--"
                    val day = if (idComValue.length >= 6) idComValue.substring(4, 6) else "--"
                    val dateText = "${month}月${day}日"

                    // 加载选题类型（只读）—— id_joined_dep 用 id_com 里的 id_dep（选题发起部门）
                    var typeListStr = ""
                    val typeListL1: MutableList<Pair<String, String>> = mutableListOf()
                    val typeListL2: MutableList<Pair<String, String>> = mutableListOf()
                    try {
                        val rsJt = conn.query(
                            "SELECT type_list FROM joined_topical WHERE id_com = '$idComValue' AND id_joined_dep = '$idDepValue'"
                        )
                        val jtRows = rsJt.toList()
                        if (jtRows.isNotEmpty()) {
                            typeListStr = jtRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    } catch (_: Exception) { }
                    try {
                        val rsT = conn.query("SELECT topical_type, type_name, level FROM topical_type ORDER BY level, topical_type")
                        val rowsT = rsT.toList()
                        for (row in rowsT) {
                            val code = row.get(0).toString().removeSurrounding("[", "]")
                            val name = row.get(1).toString().removeSurrounding("[", "]")
                            val lv = row.get(2).toString().removeSurrounding("[", "]").toIntOrNull() ?: 0
                            if (lv == 1) typeListL1.add(Pair(code, name))
                            else if (lv == 2) typeListL2.add(Pair(code, name))
                        }
                    } catch (_: Exception) { }
                    val checkedCodes = typeListStr
                        .replace("[", "").replace("]", "")
                        .replace("\"", "").replace("'", "")
                        .replace(",", "").replace(" ", "")
                        .filter { it.isLetterOrDigit() }
                        .map { it.toString() }
                        .toSet()

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvDate.text = dateText
                        tvAuthor.text = nameUser
                        tvDepartment.text = nameDep
                        tvIdDep.text = idDepValue
                        tvTitle.text = comTitle
                        tvContent.text = comSummary

                        // 渲染重要选题（只读）
                        llImportantTypes.removeAllViews()
                        if (typeListL1.isEmpty()) {
                            val tv = TextView(this@TopicAuditDetailActivity)
                            tv.text = "无重要选题"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            llImportantTypes.addView(tv)
                        } else {
                            typeListL1.forEach { (code, name) ->
                                val cb = CheckBox(this@TopicAuditDetailActivity)
                                cb.text = name
                                cb.tag = code
                                cb.isChecked = checkedCodes.contains(code)
                                cb.isEnabled = true
                                cb.setTextColor(0xFF333333.toInt())
                                llImportantTypes.addView(cb)
                            }
                        }

                        // 渲染选题方向（只读）
                        llDirectionTypes.removeAllViews()
                        if (typeListL2.isEmpty()) {
                            val tv = TextView(this@TopicAuditDetailActivity)
                            tv.text = "无选题方向"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            llDirectionTypes.addView(tv)
                        } else {
                            typeListL2.forEach { (code, name) ->
                                val cb = CheckBox(this@TopicAuditDetailActivity)
                                cb.text = name
                                cb.tag = code
                                cb.isChecked = checkedCodes.contains(code)
                                cb.isEnabled = true
                                cb.setTextColor(0xFF333333.toInt())
                                llDirectionTypes.addView(cb)
                            }
                        }

                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@TopicAuditDetailActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 统一提交审核
    private fun doSubmit() {
        if (isLoading) return

        // 读取审核状态
        val vetStatus = when (rgAuditStatus.checkedRadioButtonId) {
            R.id.rb_pass -> 1
            R.id.rb_reject -> 2
            else -> {
                android.widget.Toast.makeText(this, "请选择审核状态", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 读取理由（只有不通过时需要）
        var memo = etRejectMemo.text.toString().trim()
        if (vetStatus == 2 && memo.isEmpty()) {
            android.widget.Toast.makeText(this, "请填写不予通过理由", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (vetStatus == 1) {
            // 通过时理由置空
            memo = ""
        }

        val confirmMsg = if (vetStatus == 1) "确定要通过该选题吗？" else "确定要不通过该选题吗？"
        val confirmTitle = if (vetStatus == 1) "确认通过" else "确认不通过"

        AlertDialog.Builder(this)
            .setTitle(confirmTitle)
            .setMessage(confirmMsg)
            .setPositiveButton("确定") { _, _ -> executeAudit(vetStatus, memo) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeAudit(vetStatus: Int, memo: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        val escMemo = memo.replace("'", "''")
        val escIdCom = idCom.replace("'", "''")
        val escJointDate = jointEndDateStr.replace("'", "''")
        // 获取所属部门id_dep（从成员变量 idDep）
        val escIdDep = idDep.replace("'", "''")
        // 收集当前 CheckBox 选中项的 code（先 level=1 后 level=2），code 存在 CheckBox.tag 里
        val sb = StringBuilder()
        for (i in 0 until llImportantTypes.childCount) {
            val v = llImportantTypes.getChildAt(i)
            if (v is CheckBox && v.isChecked && v.tag != null) sb.append(v.tag.toString())
        }
        for (i in 0 until llDirectionTypes.childCount) {
            val v = llDirectionTypes.getChildAt(i)
            if (v is CheckBox && v.isChecked && v.tag != null) sb.append(v.tag.toString())
        }
        val typeListStr = sb.toString()
        val escTypeList = typeListStr.replace("'", "''")
        // 生成id_joined: yyMMddHHmmss（12位）
        val idJoined = generateIdJoined()

        Thread {
            try {
                Db.withConnection { conn ->
                    val sql = if (vetStatus == 1) {
                        // 通过审核：保存联动截止日期
                        "UPDATE commission_summary SET " +
                        "vet = $vetStatus, " +
                        "reject_memo = '$escMemo', " +
                        "date_join_end = '$escJointDate' " +
                        "WHERE id_com = '$escIdCom'"
                    } else {
                        // 不通过：不保存联动日期
                        "UPDATE commission_summary SET " +
                        "vet = $vetStatus, " +
                        "reject_memo = '$escMemo' " +
                        "WHERE id_com = '$escIdCom'"
                    }
                    conn.execute(sql)

                    // 通过审核时，插入joined_topical记录
                    if (vetStatus == 1) {
//                        val insertSql = "INSERT INTO joined_topical (id_com, id_joined_dep, id_joined, type_list) VALUES (" +
//                            "'$escIdCom', '$escIdDep', '$idJoined', '$escTypeList' )"
                        val insertSql = "UPDATE joined_topical SET ps = 1 WHERE id_com = '$escIdCom'"
                        conn.execute(insertSql)
                    }
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    val msg = if (vetStatus == 1) "已通过审核" else "已标记为不通过"
                    AlertDialog.Builder(this@TopicAuditDetailActivity)
                        .setTitle("操作成功")
                        .setMessage(msg)
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@TopicAuditDetailActivity)
                        .setTitle("错误")
                        .setMessage("操作失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }
}
