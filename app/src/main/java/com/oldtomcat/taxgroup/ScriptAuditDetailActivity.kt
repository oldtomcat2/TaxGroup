package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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
import com.google.android.material.card.MaterialCardView

/**
 * 脚本审核详情页（宣传中心审核）
 *
 * 接收 id_detail（topical_detail 主键）和 vet_status，根据状态显示不同界面：
 * - vet=0（待审核）：可编辑，显示审核状态单选
 * - vet=3（业务已审核）：可编辑，显示审核状态单选 + 业务审核情况组
 * - vet=1/2/4：只读模式
 *
 * 操作：
 * - 保存修改：UPDATE topical_detail.script
 * - 确定提交：根据审核状态更新 vet_statue
 */
class ScriptAuditDetailActivity : AppCompatActivity() {

    private lateinit var tvIdJoinedDep: TextView
    private lateinit var tvEditorUser: TextView
    private lateinit var tvComTitle: TextView
    private lateinit var tvTypeName: TextView
    private lateinit var etScript: EditText
    private lateinit var rgAuditStatus: RadioGroup
    private lateinit var rbPass: RadioButton
    private lateinit var rbSubmitDept: RadioButton
    private lateinit var cardDeptSelect: MaterialCardView
    private lateinit var llDeptCheckboxes: LinearLayout
    private lateinit var rbStop: RadioButton
    private lateinit var cardStopReason: MaterialCardView
    private lateinit var etStopReason: EditText
    private lateinit var tvStopReasonCount: TextView
    private lateinit var btnSave: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: View
    private lateinit var progressBar: ProgressBar
    
    // 业务审核情况组（vet=3 时显示）
    private lateinit var cardBusinessAudit: MaterialCardView
    private lateinit var llBusinessAuditList: LinearLayout

    // 从 Intent 传入
    private var idDetail: String = ""
    private var idJoinedDep: String = ""
    private var vetStatus: Int = 0  // 当前记录的 vet_statue
    private var idJoined: String = ""  // id_detail 前 12 位
    private var typeLetter: String = ""  // id_detail 第 13 位字母
    private var editorUser: String = ""  // 作者账号

    // 部门数据
    private val deptMap = mutableMapOf<String, String>()
    private val deptCheckboxes = mutableListOf<CheckBox>()

    @Volatile
    private var isLoading = false

    companion object {
        const val EXTRA_ID_DETAIL = "id_detail"
        const val EXTRA_ID_JOINED_DEP = "id_joined_dep"
        const val EXTRA_VET_STATUS = "vet_status"
        const val STOP_REASON_MAX = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_audit_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_audit_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        initViews()

        // 接收参数
        idDetail = intent.getStringExtra(EXTRA_ID_DETAIL) ?: ""
        idJoinedDep = intent.getStringExtra(EXTRA_ID_JOINED_DEP) ?: ""
        vetStatus = intent.getIntExtra(EXTRA_VET_STATUS, 0)

        // 计算 id_joined 和 typeLetter
        if (idDetail.length >= 13) {
            idJoined = idDetail.substring(0, 12)
            typeLetter = idDetail.substring(12, 13)
        }

        // 根据 vet_status 设置编辑状态
        setupEditMode()

        loadData()
    }

    private fun initViews() {
        tvIdJoinedDep = findViewById(R.id.tv_id_joined_dep)
        tvEditorUser = findViewById(R.id.tv_editor_user)
        tvComTitle = findViewById(R.id.tv_com_title)
        tvTypeName = findViewById(R.id.tv_type_name)
        etScript = findViewById(R.id.et_script)
        rgAuditStatus = findViewById(R.id.rg_audit_status)
        rbPass = findViewById(R.id.rb_pass)
        rbSubmitDept = findViewById(R.id.rb_submit_dept)
        cardDeptSelect = findViewById(R.id.card_dept_select)
        llDeptCheckboxes = findViewById(R.id.ll_dept_checkboxes)
        rbStop = findViewById(R.id.rb_stop)
        cardStopReason = findViewById(R.id.card_stop_reason)
        etStopReason = findViewById(R.id.et_stop_reason)
        tvStopReasonCount = findViewById(R.id.tv_stop_reason_count)
        btnSave = findViewById(R.id.btn_save)
        btnSubmit = findViewById(R.id.btn_submit)
        btnBack = findViewById(R.id.btn_back)
        progressBar = findViewById(R.id.progress_bar)
        
        // 业务审核情况组（动态创建或从布局找）
        cardBusinessAudit = findViewById(R.id.card_business_audit) ?: createBusinessAuditCard()
        llBusinessAuditList = cardBusinessAudit.findViewById(R.id.ll_business_audit_list) 
            ?: LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 顶部返回按钮
        btnBack.setOnClickListener { finish() }
        
        // 底部返回按钮
        val btnBackBottom: Button = findViewById(R.id.btn_back_bottom)
        btnBackBottom.setOnClickListener { finish() }

        // 终止理由字数限制
        etStopReason.filters = arrayOf(InputFilter.LengthFilter(STOP_REASON_MAX))
        etStopReason.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tvStopReasonCount.text = "${s?.length ?: 0} / $STOP_REASON_MAX"
            }
        })

        // 审核状态单选监听
        rgAuditStatus.setOnCheckedChangeListener { _, checkedId ->
            cardDeptSelect.visibility = if (checkedId == R.id.rb_submit_dept) View.VISIBLE else View.GONE
            cardStopReason.visibility = if (checkedId == R.id.rb_stop) View.VISIBLE else View.GONE
        }
    }

    private fun createBusinessAuditCard(): MaterialCardView {
        // 如果布局中没有，动态创建
        val card = MaterialCardView(this)
        card.id = R.id.card_business_audit
        // 添加到根布局（需要在布局文件中预留位置或使用代码添加）
        return card
    }

    private fun setupEditMode() {
        // vet=0（待审核）或 vet=3（业务已审核）→ 可编辑
        // vet=1/2/4 → 只读
        val editable = (vetStatus == 0 || vetStatus == 3)
        
        if (editable) {
            // 可编辑模式
            etScript.isEnabled = true
            etScript.isFocusableInTouchMode = true
            
            for (i in 0 until rgAuditStatus.childCount) {
                rgAuditStatus.getChildAt(i).isEnabled = true
            }
            
            etStopReason.isEnabled = true
            etStopReason.isFocusableInTouchMode = true
            
            btnSave.visibility = View.VISIBLE
            btnSubmit.visibility = View.VISIBLE
            
            btnSave.setOnClickListener { doSaveScript() }
            btnSubmit.setOnClickListener { doSubmit() }
            
            // vet=3 时显示业务审核情况
            cardBusinessAudit.visibility = if (vetStatus == 3) View.VISIBLE else View.GONE
        } else {
            // 只读模式
            etScript.isEnabled = false
            etScript.isFocusable = false
            
            for (i in 0 until rgAuditStatus.childCount) {
                rgAuditStatus.getChildAt(i).isEnabled = false
            }
            
            cardDeptSelect.visibility = View.GONE
            cardStopReason.visibility = View.GONE
            etStopReason.isEnabled = false
            etStopReason.isFocusable = false
            
            btnSave.visibility = View.GONE
            btnSubmit.visibility = View.GONE
            
            cardBusinessAudit.visibility = View.GONE
        }
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    var comTitle = ""
                    var typeName = ""
                    editorUser = ""
                    var editorName = ""
                    var depName = ""
                    var scriptContent = ""
                    var currentVetStatus = 0

                    // 1. 查 topical_detail 主记录
                    if (idDetail.isNotEmpty()) {
                        val escIdDetail = idDetail.replace("'", "''")
                        val rs = conn.query(
                            "SELECT id_Editer_user, script, vet_statue FROM topical_detail " +
                                "WHERE id_detail = '$escIdDetail' LIMIT 1"
                        )
                        val rows = rs.toList()
                        if (rows.isNotEmpty()) {
                            editorUser = rows[0].get(0).toString().removeSurrounding("[", "]")
                            val scriptVal = rows[0].get(1).toString().removeSurrounding("[", "]")
                            scriptContent = if (scriptVal == "null" || scriptVal.isEmpty()) "" else scriptVal
                            val vetVal = rows[0].get(2).toString().removeSurrounding("[", "]")
                            currentVetStatus = vetVal.toIntOrNull() ?: 0
                        }
                    }

                    // 2. 查作者姓名
                    if (editorUser.isNotEmpty()) {
                        val escEditor = editorUser.replace("'", "''")
                        val rsNu = conn.query(
                            "SELECT name_user FROM User WHERE id_user = '$escEditor' LIMIT 1"
                        )
                        val nuRows = rsNu.toList()
                        if (nuRows.isNotEmpty()) {
                            editorName = nuRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 3. 查 joined_topical.id_com
                    var idCom = ""
                    if (idJoined.isNotEmpty()) {
                        val escIdJoined = idJoined.replace("'", "''")
                        val rsJt = conn.query(
                            "SELECT id_com FROM joined_topical WHERE id_joined = '$escIdJoined' LIMIT 1"
                        )
                        val jtRows = rsJt.toList()
                        if (jtRows.isNotEmpty()) {
                            idCom = jtRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 4. 查 commission_summary.com_title
                    if (idCom.isNotEmpty()) {
                        val escIdCom = idCom.replace("'", "''")
                        val rsCs = conn.query(
                            "SELECT com_title FROM commission_summary WHERE id_com = '$escIdCom' LIMIT 1"
                        )
                        val csRows = rsCs.toList()
                        if (csRows.isNotEmpty()) {
                            comTitle = csRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 5. 查 topical_type.type_name
                    if (typeLetter.isNotEmpty()) {
                        val escLetter = typeLetter.replace("'", "''")
                        val rsTt = conn.query(
                            "SELECT type_name FROM topical_type WHERE topical_type = '$escLetter' AND level > 1 LIMIT 1"
                        )
                        val ttRows = rsTt.toList()
                        if (ttRows.isNotEmpty()) {
                            typeName = ttRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 6. 查 Department.name_dep（报送单位）
                    if (idJoinedDep.isNotEmpty()) {
                        val escDep = idJoinedDep.replace("'", "''")
                        val rsD = conn.query(
                            "SELECT name_dep FROM Department WHERE id_dep = '$escDep' LIMIT 1"
                        )
                        val dRows = rsD.toList()
                        if (dRows.isNotEmpty()) {
                            depName = dRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 6b. 查 level=2 的部门列表（用于"提交业务部门审核"复选框）
                    val level2Depts = mutableListOf<Pair<String, String>>() // (id_dep, name_dep)
                    val rsLevel2 = conn.query(
                        "SELECT id_dep, name_dep FROM Department WHERE level = 2 ORDER BY id_dep"
                    )
                    for (row in rsLevel2.toList()) {
                        val dId = row.get(0).toString().removeSurrounding("[", "]")
                        val dName = row.get(1).toString().removeSurrounding("[", "]")
                        level2Depts.add(Pair(dId, dName))
                    }

                    // 7. 如果 vet=3，加载业务审核情况（从 dep_vet 表）
                    val businessAuditList = mutableListOf<Triple<String, String, Int>>() // (部门名, 部门id, vet)
                    if (vetStatus == 3 && idDetail.isNotEmpty()) {
                        val escIdDetail = idDetail.replace("'", "''")
                        val rsDv = conn.query(
                            "SELECT id_dep_vet, vet FROM dep_vet WHERE id_detail = '$escIdDetail' ORDER BY id_dep_vet"
                        )
                        val dvRows = rsDv.toList()
                        for (row in dvRows) {
                            val depVetId = row.get(0).toString().removeSurrounding("[", "]")
                            val vetVal = row.get(1).toString().removeSurrounding("[", "]").toIntOrNull() ?: 0
                            
                            // 查部门名
                            val escDepVet = depVetId.replace("'", "''")
                            val rsDep = conn.query(
                                "SELECT name_dep FROM Department WHERE id_dep = '$escDepVet' LIMIT 1"
                            )
                            val depRows = rsDep.toList()
                            val depNameVet = if (depRows.isNotEmpty()) {
                                depRows[0].get(0).toString().removeSurrounding("[", "]")
                            } else depVetId
                            
                            businessAuditList.add(Triple(depNameVet, depVetId, vetVal))
                        }
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvIdJoinedDep.text = if (depName.isNotEmpty()) "$idJoinedDep ($depName)" else idJoinedDep
                        tvEditorUser.text = if (editorName.isNotEmpty()) "$editorUser ($editorName)" else editorUser
                        tvComTitle.text = comTitle.ifEmpty { "[无标题]" }
                        tvTypeName.text = if (typeName.isNotEmpty()) typeName else "[无类型]"
                        etScript.setText(scriptContent)

                        // 回填审核状态单选
                        when (currentVetStatus) {
                            1 -> rgAuditStatus.check(R.id.rb_pass)
                            2 -> rgAuditStatus.check(R.id.rb_submit_dept)
                            3 -> rgAuditStatus.check(R.id.rb_pass)  // 业务已审核视为通过
                            4 -> rgAuditStatus.check(R.id.rb_stop)
                            else -> rgAuditStatus.clearCheck()
                        }

                        // 显示业务审核情况
                        if (vetStatus == 3) {
                            showBusinessAuditList(businessAuditList)
                        }

                        // 创建部门复选框（level=2 的部门）
                        createDeptCheckboxes(level2Depts)

                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@ScriptAuditDetailActivity)
                        .setTitle("加载失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun showBusinessAuditList(list: List<Triple<String, String, Int>>) {
        llBusinessAuditList.removeAllViews()
        
        for ((depName, depId, vet) in list) {
            val itemView = layoutInflater.inflate(R.layout.item_business_audit, llBusinessAuditList, false)
            val tvDeptName = itemView.findViewById<TextView>(R.id.tv_dept_name)
            val tvStatus = itemView.findViewById<TextView>(R.id.tv_status)
            
            tvDeptName.text = depName
            
            val statusText = when (vet) {
                0 -> "待审核"
                1 -> "审核通过"
                2 -> "非本部门业务"
                3 -> "不建议宣传"
                else -> "未知"
            }
            tvStatus.text = statusText
            
            // 根据状态设置颜色
            when (vet) {
                0 -> tvStatus.setTextColor(android.graphics.Color.parseColor("#1976D2"))
                1 -> tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                2, 3 -> tvStatus.setTextColor(android.graphics.Color.parseColor("#C62828"))
                else -> tvStatus.setTextColor(android.graphics.Color.parseColor("#757575"))
            }
            
            llBusinessAuditList.addView(itemView)
        }
    }

    private fun createDeptCheckboxes(depts: List<Pair<String, String>>) {
        llDeptCheckboxes.removeAllViews()
        deptCheckboxes.clear()
        deptMap.clear()
        
        for ((depId, depName) in depts) {
            val checkBox = CheckBox(this).apply {
                text = depName
                tag = depId
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            deptCheckboxes.add(checkBox)
            deptMap[depId] = depName
            llDeptCheckboxes.addView(checkBox)
        }
    }

    private fun doSaveScript() {
        if (isLoading) return
        if (idDetail.isEmpty()) {
            android.widget.Toast.makeText(this, "缺少 id_detail", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val newScript = etScript.text.toString()
        isLoading = true
        progressBar.visibility = View.VISIBLE
        setButtonsEnabled(false)

        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escScript = newScript.replace("'", "''")
                    conn.execute(
                        "UPDATE topical_detail SET script = '$escScript' WHERE id_detail = '$escIdDetail'"
                    )
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    android.widget.Toast.makeText(this, "脚本内容已保存", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    AlertDialog.Builder(this)
                        .setTitle("保存失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun doSubmit() {
        if (isLoading) return
        if (idDetail.isEmpty()) {
            android.widget.Toast.makeText(this, "缺少 id_detail", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val selectedId = rgAuditStatus.checkedRadioButtonId
        if (selectedId == -1) {
            android.widget.Toast.makeText(this, "请选择审核结论", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 确认提交
        AlertDialog.Builder(this)
            .setTitle("确认提交")
            .setMessage("确定提交审核结果吗？")
            .setPositiveButton("确定") { _, _ -> performSubmit(selectedId) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performSubmit(selectedId: Int) {
        isLoading = true
        progressBar.visibility = View.VISIBLE
        setButtonsEnabled(false)

        val newScript = etScript.text.toString()
        val stopReason = etStopReason.text.toString()

        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escScript = newScript.replace("'", "''")
                    val escReason = stopReason.replace("'", "''")

                    when (selectedId) {
                        R.id.rb_pass -> {
                            // 宣传中心修改通过 → vet_statue=1
                            conn.execute(
                                "UPDATE topical_detail SET script = '$escScript', vet_statue = 1 " +
                                    "WHERE id_detail = '$escIdDetail'"
                            )
                        }
                        R.id.rb_submit_dept -> {
                            // 提交业务部门审核 → vet_statue=2
                            conn.execute(
                                "UPDATE topical_detail SET script = '$escScript', vet_statue = 2 " +
                                    "WHERE id_detail = '$escIdDetail'"
                            )
                            // 插入 dep_vet 记录（选中的部门）
                            val selectedDepts = deptCheckboxes.filter { it.isChecked }.map { it.tag.toString() }
                            for (deptId in selectedDepts) {
                                val escDept = deptId.replace("'", "''")
                                conn.execute(
                                    "INSERT INTO dep_vet (id_detail, id_dep_vet, vet, memo, id_dep_joined) " +
                                        "VALUES ('$escIdDetail', '$escDept', 0, '', '$idJoinedDep')"
                                )
                            }
                        }
                        R.id.rb_stop -> {
                            // 选题不适合宣传 → vet_statue=4，写入 memo
                            conn.execute(
                                "UPDATE topical_detail SET script = '$escScript', vet_statue = 4, memo = '$escReason' " +
                                    "WHERE id_detail = '$escIdDetail'"
                            )
                        }
                    }
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    AlertDialog.Builder(this)
                        .setTitle("成功")
                        .setMessage("审核已提交")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    AlertDialog.Builder(this)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSave.isEnabled = enabled
        btnSubmit.isEnabled = enabled
    }
}
