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
    private lateinit var rbTransfer: RadioButton
    private lateinit var llRejectMemo: LinearLayout
    private lateinit var etRejectMemo: EditText
    private lateinit var llJointPub: LinearLayout
    private lateinit var llTransferDep: LinearLayout
    private lateinit var llDepartmentCheckboxes: LinearLayout
    private lateinit var tvJointEndDate: TextView
    private lateinit var llImportantTypes: LinearLayout
    private lateinit var llDirectionTypes: LinearLayout
    private lateinit var btnBackBottom: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    private var idCom: String = ""
    private var idDep: String = ""
    private var idReviewDep: String = "" // 选题审核推送时的审核部门ID
    private var jointEndDateStr: String = "" // yyMMdd 格式
    private var currentVet: Int = 0 // 当前选题的vet状态
    private var isFromTopicReview: Boolean = false // 是否来自选题审核推送
    private var isReadOnly: Boolean = false // 是否只读（level>=2 且 来自选题审核推送时）

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
        idReviewDep = intent.getStringExtra("id_review_dep") ?: ""
        isFromTopicReview = intent.getBooleanExtra("is_from_topic_review", false)

        // 只读判断：从"选题审核"Tab进入（isFromTopicReview=false 的项） 且 level >= 2 时不可编辑/提交
        // 业务部门从"部门审核"Tab进入的选题项（isFromTopicReview=true）始终可编辑
        isReadOnly = !isFromTopicReview && MyApp.loginDepLevel >= 2

        // 设置页面标题
        val tvPageTitle = findViewById<TextView>(R.id.tv_page_title)
        tvPageTitle.text = if (isFromTopicReview) "选题审核" else "选题审核详情"

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
        rbTransfer = findViewById(R.id.rb_transfer)
        llRejectMemo = findViewById(R.id.ll_reject_memo)
        etRejectMemo = findViewById(R.id.et_reject_memo)
        llJointPub = findViewById(R.id.ll_joint_pub)
        llTransferDep = findViewById(R.id.ll_transfer_dep)
        llDepartmentCheckboxes = findViewById(R.id.ll_department_checkboxes)
        tvJointEndDate = findViewById(R.id.tv_joint_end_date)

        // 如果是来自选题审核推送的记录，调整单选按钮文字
        if (isFromTopicReview) {
            rbPass.text = "通过"
            rbReject.text = "不通过"
            rbTransfer.text = "非本部门退回"
            // 隐藏联动日期区域
            llJointPub.visibility = View.GONE
            // 隐藏转业务部门区域（不需要）
            llTransferDep.visibility = View.GONE
        }
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
                    // 来自选题审核推送时，不显示联动日期
                    llJointPub.visibility = if (isFromTopicReview) View.GONE else View.VISIBLE
                    llTransferDep.visibility = View.GONE
                    // 默认设置为当前+7天
                    if (!isFromTopicReview) setDefaultJointDate()
                }
                R.id.rb_reject -> {
                    llRejectMemo.visibility = View.VISIBLE
                    llJointPub.visibility = View.GONE
                    llTransferDep.visibility = View.GONE
                    // 默认填入"不符题意"
                    if (etRejectMemo.text.isNullOrBlank()) {
                        etRejectMemo.setText("不符题意")
                    }
                }
                R.id.rb_transfer -> {
                    llRejectMemo.visibility = if (isFromTopicReview) View.VISIBLE else View.GONE
                    llJointPub.visibility = View.GONE
                    // 来自选题审核推送时，rb_transfer 是"非本部门退回"，不显示转业务部门
                    llTransferDep.visibility = if (isFromTopicReview) View.GONE else View.VISIBLE
                    // 加载部门列表（仅普通模式下需要）
                    if (!isFromTopicReview) loadDepartmentList()
                    // 来自选题审核推送时，默认填入"非本部门事项"
                    if (isFromTopicReview && etRejectMemo.text.isNullOrBlank()) {
                        etRejectMemo.setText("非本部门事项")
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

        // 应用只读模式
        applyReadOnlyMode()

        // 初始加载
        loadData()
    }

    // 应用只读模式：禁用所有编辑控件
    private fun applyReadOnlyMode() {
        if (!isReadOnly) return

        // 禁用审核单选按钮
        rbPass.isEnabled = false
        rbReject.isEnabled = false
        rbTransfer.isEnabled = false

        // 禁用理由输入框
        etRejectMemo.isEnabled = false
        etRejectMemo.setTextColor(0xFF999999.toInt())

        // 禁用联动日期
        tvJointEndDate.isEnabled = false
        tvJointEndDate.setTextColor(0xFF999999.toInt())

        // 禁用重要分类、方向分类 checkbox
        for (i in 0 until llImportantTypes.childCount) {
            val v = llImportantTypes.getChildAt(i)
            if (v is CheckBox) {
                v.isEnabled = false
                v.setTextColor(0xFF999999.toInt())
            }
        }
        for (i in 0 until llDirectionTypes.childCount) {
            val v = llDirectionTypes.getChildAt(i)
            if (v is CheckBox) {
                v.isEnabled = false
                v.setTextColor(0xFF999999.toInt())
            }
        }

        // 隐藏提交按钮
        btnSubmit.visibility = View.GONE
    }

    // 存储加载的部门列表
    private var departmentList: MutableList<Pair<String, String>> = mutableListOf()

    private fun loadDepartmentList() {
        // 如果已经加载过，不再重复加载
        if (departmentList.isNotEmpty()) return

        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query("SELECT id_dep, name_dep FROM Department WHERE level = 2 ORDER BY id_dep")
                    val rows = rs.toList()
                    departmentList.clear()
                    for (row in rows) {
                        val idDep = row.get(0).toString().removeSurrounding("[", "]")
                        val nameDep = row.get(1).toString().removeSurrounding("[", "]")
                        departmentList.add(Pair(idDep, nameDep))
                    }

                    runOnUiThread {
                        llDepartmentCheckboxes.removeAllViews()
                        if (departmentList.isEmpty()) {
                            val tv = TextView(this@TopicAuditDetailActivity)
                            tv.text = "无业务部门"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            llDepartmentCheckboxes.addView(tv)
                        } else {
                            departmentList.forEach { (idDep, nameDep) ->
                                val cb = CheckBox(this@TopicAuditDetailActivity)
                                cb.text = nameDep
                                cb.tag = idDep
                                cb.setTextColor(0xFF333333.toInt())
                                llDepartmentCheckboxes.addView(cb)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    llDepartmentCheckboxes.removeAllViews()
                    val tv = TextView(this@TopicAuditDetailActivity)
                    tv.text = "加载失败：${e.message}"
                    tv.setTextColor(0xFFC62828.toInt())
                    tv.textSize = 13f
                    llDepartmentCheckboxes.addView(tv)
                }
            }
        }.start()
    }

    // 加载部门审核状态（vet=4时显示）
    private fun loadDepartmentReviewStatus() {
        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdCom = idCom.replace("'", "''")
                    val sql = "SELECT tr.id_review_dep, tr.review_vet, tr.memo, d.name_dep " +
                        "FROM topical_review tr " +
                        "LEFT JOIN Department d ON tr.id_review_dep = d.id_dep " +
                        "WHERE tr.id_com = '$escIdCom' " +
                        "ORDER BY tr.id_review_dep"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    val reviewList = mutableListOf<Triple<String, String, String>>()
                    for (row in rows) {
                        val idReviewDep = row.get(0).toString().removeSurrounding("[", "]")
                        val reviewVet = row.get(1).toString().removeSurrounding("[", "]").toIntOrNull() ?: 0
                        val memo = row.get(2).toString().removeSurrounding("[", "]")
                        val nameDep = row.get(3)?.toString()?.removeSurrounding("[", "]") ?: idReviewDep

                        val statusText = when (reviewVet) {
                            0 -> "待审核"
                            1 -> "通过"
                            2 -> "不予通过"
                            3 -> "非本部门退回"
                            else -> "未知状态"
                        }
                        reviewList.add(Triple(nameDep, statusText, memo))
                    }

                    runOnUiThread {
                        // 在llTransferDep下方动态添加审核状态列表
                        val parentLayout = llTransferDep.parent as? LinearLayout
                        if (parentLayout != null && reviewList.isNotEmpty()) {
                            // 使用tag来标记和查找已存在的审核状态视图（避免重复）
                            val existingView = parentLayout.findViewWithTag<LinearLayout>("review_status_container")
                            existingView?.let { parentLayout.removeView(it) }

                            // 创建新的审核状态容器
                            val container = LinearLayout(this@TopicAuditDetailActivity)
                            container.tag = "review_status_container"
                            container.orientation = LinearLayout.VERTICAL
                            container.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )

                            // 标题
                            val titleTv = TextView(this@TopicAuditDetailActivity)
                            titleTv.text = "部门审核状态"
                            titleTv.textSize = 14f
                            titleTv.setTextColor(0xFF666666.toInt())
                            titleTv.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = (8 * resources.displayMetrics.density).toInt()
                            }
                            container.addView(titleTv)

                            // 每个部门的审核状态
                            reviewList.forEach { (deptName, status, memo) ->
                                val card = com.google.android.material.card.MaterialCardView(this@TopicAuditDetailActivity)
                                card.layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    bottomMargin = (8 * resources.displayMetrics.density).toInt()
                                }
                                card.radius = 8f
                                card.cardElevation = 1f
                                card.setCardBackgroundColor(0xFFFFFFFF.toInt())

                                val innerLayout = LinearLayout(this@TopicAuditDetailActivity)
                                innerLayout.orientation = LinearLayout.VERTICAL
                                innerLayout.setPadding(
                                    (12 * resources.displayMetrics.density).toInt(),
                                    (12 * resources.displayMetrics.density).toInt(),
                                    (12 * resources.displayMetrics.density).toInt(),
                                    (12 * resources.displayMetrics.density).toInt()
                                )

                                // 部门名称和状态
                                val headerLayout = LinearLayout(this@TopicAuditDetailActivity)
                                headerLayout.orientation = LinearLayout.HORIZONTAL
                                headerLayout.layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )

                                val deptTv = TextView(this@TopicAuditDetailActivity)
                                deptTv.text = deptName
                                deptTv.textSize = 15f
                                deptTv.setTextColor(0xFF333333.toInt())
                                deptTv.layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                                headerLayout.addView(deptTv)

                                val statusTv = TextView(this@TopicAuditDetailActivity)
                                statusTv.text = status
                                statusTv.textSize = 13f
                                val statusColor = when {
                                    status.contains("通过") -> 0xFF2E7D32.toInt()
                                    status.contains("退回") -> 0xFFC62828.toInt()
                                    else -> 0xFF1976D2.toInt()
                                }
                                statusTv.setTextColor(statusColor)
                                headerLayout.addView(statusTv)

                                innerLayout.addView(headerLayout)

                                // 备注（如果有）
                                if (memo.isNotBlank()) {
                                    val memoTv = TextView(this@TopicAuditDetailActivity)
                                    memoTv.text = "备注：$memo"
                                    memoTv.textSize = 13f
                                    memoTv.setTextColor(0xFF666666.toInt())
                                    memoTv.layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = (4 * resources.displayMetrics.density).toInt()
                                    }
                                    innerLayout.addView(memoTv)
                                }

                                card.addView(innerLayout)
                                container.addView(card)
                            }

                            // 插入到llTransferDep之后
                            val index = parentLayout.indexOfChild(llTransferDep)
                            if (index >= 0 && index + 1 < parentLayout.childCount) {
                                parentLayout.addView(container, index + 1)
                            } else {
                                parentLayout.addView(container)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@TopicAuditDetailActivity,
                        "加载部门审核状态失败：${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
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

                    // 第二步：单独查部门名称和vet状态
                    val escIdDep = idDepValue.replace("'", "''")
                    val depRs = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep'")
                    val depRows = depRs.toList()
                    val nameDep = if (depRows.isNotEmpty()) {
                        depRows[0].get(0).toString().removeSurrounding("[", "]")
                    } else {
                        idDepValue
                    }

                    // 查commission_summary的vet状态
                    val vetRs = conn.query("SELECT vet FROM commission_summary WHERE id_com = '$idComValue'")
                    val vetRows = vetRs.toList()
                    currentVet = if (vetRows.isNotEmpty()) {
                        vetRows[0].get(0).toString().removeSurrounding("[", "]").toIntOrNull() ?: 0
                    } else {
                        0
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

                        // 如果vet=4 且 isFromTopicReview=false（选题审核页，审核员审核转业务项）
                        // 才禁用转业务部门选项；业务部门页中 vet=4 状态下的"非本部门退回"需可点
                        if (currentVet == 4) {
                            if (!isFromTopicReview) {
                                rbTransfer.isEnabled = false
                                rbTransfer.setTextColor(0xFF999999.toInt())
                            }
                            // 加载部门审核状态（可能存在）
                            loadDepartmentReviewStatus()
                        }

                        // 只读模式（仅查看）：应用后禁用动态加载的 checkbox
                        applyReadOnlyMode()

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
        // 来自选题审核推送：rb_transfer 映射为 3（非本部门退回）
        // 普通选题审核：rb_transfer 映射为 4（转业务部门审阅）
        val vetStatus = when (rgAuditStatus.checkedRadioButtonId) {
            R.id.rb_pass -> 1
            R.id.rb_reject -> 2
            R.id.rb_transfer -> if (isFromTopicReview) 3 else 4
            else -> {
                android.widget.Toast.makeText(this, "请选择审核状态", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 读取理由（只有不通过或非本部门退回时需要）
        var memo = etRejectMemo.text.toString().trim()
        if ((vetStatus == 2 || vetStatus == 3) && memo.isEmpty()) {
            val msg = if (vetStatus == 2) "请填写不予通过理由" else "请填写退回理由"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (vetStatus == 1 || vetStatus == 4) {
            // 通过或转业务时理由置空
            memo = ""
        }

        // 转业务部门审阅时，检查是否选择了部门
        val selectedDepIds = mutableListOf<String>()
        if (vetStatus == 4) {
            for (i in 0 until llDepartmentCheckboxes.childCount) {
                val v = llDepartmentCheckboxes.getChildAt(i)
                if (v is CheckBox && v.isChecked && v.tag != null) {
                    selectedDepIds.add(v.tag.toString())
                }
            }
            if (selectedDepIds.isEmpty()) {
                android.widget.Toast.makeText(this, "请选择至少一个业务部门", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 来自选题审核推送时，不应选择"转业务部门审阅"
        if (isFromTopicReview && vetStatus == 4) {
            android.widget.Toast.makeText(this, "部门审核不能转业务", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val confirmMsg = when (vetStatus) {
            1 -> if (isFromTopicReview) "确定要通过该选题吗？" else "确定要通过该选题吗？"
            2 -> if (isFromTopicReview) "确定要不通过该选题吗？" else "确定要不通过该选题吗？"
            3 -> "确定要将该选题非本部门退回吗？"
            4 -> "确定要转业务部门审阅吗？"
            else -> "确定要提交吗？"
        }
        val confirmTitle = when (vetStatus) {
            1 -> "确认通过"
            2 -> "确认不通过"
            3 -> "确认非本部门退回"
            4 -> "确认转业务部门审阅"
            else -> "确认提交"
        }

        AlertDialog.Builder(this)
            .setTitle(confirmTitle)
            .setMessage(confirmMsg)
            .setPositiveButton("确定") { _, _ -> executeAudit(vetStatus, memo, selectedDepIds) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeAudit(vetStatus: Int, memo: String, selectedDepIds: List<String> = emptyList()) {
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
            var success = false
            var errorMsg = ""
            try {
                Db.withConnection { conn ->
                    try {
                        if (isFromTopicReview) {
                            // 部门审核：更新topical_review表
                            // WHERE条件同时使用id_com和id_review_dep定位记录
                            val escIdReviewDep = idReviewDep.replace("'", "''")
                            val sql = "UPDATE topical_review SET " +
                                "review_vet = $vetStatus, " +
                                "memo = '$escMemo' " +
                                "WHERE id_com = '$escIdCom' AND id_review_dep = '$escIdReviewDep'"
                            conn.execute(sql)
                        } else {
                            val sql = when (vetStatus) {
                                1 -> {
                                    // 通过审核：保存联动截止日期
                                    "UPDATE commission_summary SET " +
                                    "vet = $vetStatus, " +
                                    "reject_memo = '$escMemo', " +
                                    "date_join_end = '$escJointDate' " +
                                    "WHERE id_com = '$escIdCom'"
                                }
                                4 -> {
                                    // 转业务部门审阅：vet=4，不保存联动日期
                                    "UPDATE commission_summary SET " +
                                    "vet = $vetStatus, " +
                                    "reject_memo = '$escMemo' " +
                                    "WHERE id_com = '$escIdCom'"
                                }
                                else -> {
                                    // 不通过：不保存联动日期
                                    "UPDATE commission_summary SET " +
                                    "vet = $vetStatus, " +
                                    "reject_memo = '$escMemo' " +
                                    "WHERE id_com = '$escIdCom'"
                                }
                            }
                            conn.execute(sql)

                            // 通过审核时，插入joined_topical记录
                            if (vetStatus == 1) {
                                val insertSql = "UPDATE joined_topical SET ps = 1 WHERE id_com = '$escIdCom'"
                                conn.execute(insertSql)
                            }

                            // 转业务部门审阅时，向topical_review插入记录
                            if (vetStatus == 4) {
                                selectedDepIds.forEach { depId ->
                                    val escDepId = depId.replace("'", "''")
                                    val insertReviewSql = "INSERT INTO topical_review (id_com, id_review_dep, review_vet, memo) VALUES (" +
                                        "'$escIdCom', '$escDepId', 0, '')"
                                    conn.execute(insertReviewSql)
                                }
                            }
                        }

                        success = true
                    } catch (e: Exception) {
                        // 操作失败回退
                        try {
                            if (isFromTopicReview) {
                                val escIdReviewDep = idReviewDep.replace("'", "''")
                                conn.execute("UPDATE topical_review SET review_vet = 0 WHERE id_com = '$escIdCom' AND id_review_dep = '$escIdReviewDep'")
                            } else {
                                conn.execute("UPDATE commission_summary SET vet = 0 WHERE id_com = '$escIdCom'")
                            }
                        } catch (_: Exception) { }
                        throw e
                    }
                }
            } catch (e: Exception) {
                errorMsg = "${e.javaClass.simpleName}: ${e.message}"
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (success) {
                    val msg = when (vetStatus) {
                        1 -> if (isFromTopicReview) "已通过审核" else "已通过审核"
                        2 -> if (isFromTopicReview) "已标记为不通过" else "已标记为不通过"
                        3 -> "已非本部门退回"
                        4 -> "已转业务部门审阅"
                        else -> "操作成功"
                    }
                    AlertDialog.Builder(this@TopicAuditDetailActivity)
                        .setTitle("操作成功")
                        .setMessage(msg)
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                } else {
                    AlertDialog.Builder(this@TopicAuditDetailActivity)
                        .setTitle("错误")
                        .setMessage("操作失败：$errorMsg")
                        .setPositiveButton("确定", null)
                        .show()
                }
                isLoading = false
            }
        }.start()
    }
}
