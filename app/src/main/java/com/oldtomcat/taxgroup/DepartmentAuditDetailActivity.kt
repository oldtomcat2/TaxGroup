package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 部门审核详情页（新增）
 *
 * 仅在登录部门 level > 1（业务科室）时，由 TopicAuditActivity 的“部门审核”入口进入。
 * 信息展示与“脚本审核”详情页（ScriptAuditDetailActivity）一致，但“审核状态”单选替换为：
 *   按要求修改后可通过(值1) / 非本部门业务退回(值2) / 不建议对外宣传(值3)
 * 并在其下方增加“修改意见”文本编辑框（上限 500 字）。
 *
 * 操作：
 *  - 保存修改：UPDATE topical_detail SET script=?（仅更新脚本内容）
 *  - 确定提交：UPDATE topical_detail SET script=?, vet_statue=3；
 *              UPDATE dep_vet SET vet=?, memo=?（vet=单选值, memo=修改意见）
 *  - 应用层回滚：Db 走 Turso HTTP，每条语句各自自动提交、无事务。
 *    因此“确定提交”前先读取旧值，若任一写入失败，则将涉及的记录写回旧值。
 *    单一语句的“保存修改”失败即无副作用，无需额外回滚。
 */
class DepartmentAuditDetailActivity : AppCompatActivity() {

    private lateinit var tvIdJoinedDep: TextView
    private lateinit var tvEditorUser: TextView
    private lateinit var tvComTitle: TextView
    private lateinit var tvTypeName: TextView
    private lateinit var etScript: EditText
    private lateinit var rgAuditStatus: RadioGroup
    private lateinit var rbModifyPass: RadioButton   // 按要求修改后可通过 = 1
    private lateinit var rbReturn: RadioButton        // 非本部门业务退回 = 2
    private lateinit var rbNotPromote: RadioButton    // 不建议对外宣传 = 3
    private lateinit var etMemo: EditText
    private lateinit var tvMemoCount: TextView
    private lateinit var btnSave: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: View
    private lateinit var progressBar: ProgressBar

    // 从 Intent 传入
    private var idDetail: String = ""
    private var idJoinedDep: String = ""
    private var idDepVet: String = ""   // 审核部门（登录部门）
    private var idJoined: String = ""   // id_detail 前 12 位
    private var typeLetter: String = "" // id_detail 第 13 位字母
    private var editorUser: String = ""

    @Volatile
    private var isLoading = false

    companion object {
        const val EXTRA_ID_DETAIL = "id_detail"
        const val EXTRA_ID_JOINED_DEP = "id_joined_dep"
        const val EXTRA_ID_DEP_VET = "id_dep_vet"
        const val EXTRA_READONLY = "readonly"  // true=只读模式（从业务审核进入）
        const val MEMO_MAX = 500
        const val VET_MODIFY_PASS = 1   // 按要求修改后可通过
        const val VET_RETURN = 2        // 非本部门业务退回
        const val VET_NOT_PROMOTE = 3   // 不建议对外宣传
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_department_audit_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dept_audit_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvIdJoinedDep = findViewById(R.id.tv_id_joined_dep)
        tvEditorUser = findViewById(R.id.tv_editor_user)
        tvComTitle = findViewById(R.id.tv_com_title)
        tvTypeName = findViewById(R.id.tv_type_name)
        etScript = findViewById(R.id.et_script)
        rgAuditStatus = findViewById(R.id.rg_audit_status)
        rbModifyPass = findViewById(R.id.rb_modify_pass)
        rbReturn = findViewById(R.id.rb_return)
        rbNotPromote = findViewById(R.id.rb_not_promote)
        etMemo = findViewById(R.id.et_memo)
        tvMemoCount = findViewById(R.id.tv_memo_count)
        btnSave = findViewById(R.id.btn_save)
        btnSubmit = findViewById(R.id.btn_submit)
        btnBack = findViewById(R.id.btn_back)
        progressBar = findViewById(R.id.progress_bar)

        btnBack.setOnClickListener { finish() }
        // 底部返回按钮（与顶部返回按钮功能相同）
        findViewById<View>(R.id.btn_back_bottom)?.setOnClickListener { finish() }

        // 接收参数
        idDetail = intent.getStringExtra(EXTRA_ID_DETAIL) ?: ""
        idJoinedDep = intent.getStringExtra(EXTRA_ID_JOINED_DEP) ?: ""
        idDepVet = intent.getStringExtra(EXTRA_ID_DEP_VET) ?: MyApp.loginDeaprt
        val readonly = intent.getBooleanExtra(EXTRA_READONLY, false)

        if (idDetail.length >= 13) {
            idJoined = idDetail.substring(0, 12)
            typeLetter = idDetail.substring(12, 13)
        }

        // 只读模式：禁用编辑、隐藏操作按钮
        if (readonly) {
            etScript.isEnabled = false
            etScript.isFocusable = false
            for (i in 0 until rgAuditStatus.childCount) {
                rgAuditStatus.getChildAt(i).isEnabled = false
            }
            etMemo.isEnabled = false
            etMemo.isFocusable = false
            btnSave.visibility = View.GONE
            btnSubmit.visibility = View.GONE
        } else {
            // 可编辑模式：设置监听器
            etMemo.filters = arrayOf(InputFilter.LengthFilter(MEMO_MAX))
            etMemo.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    tvMemoCount.text = "${s?.length ?: 0} / $MEMO_MAX"
                }
            })
            btnSave.setOnClickListener { doSaveScript() }
            btnSubmit.setOnClickListener { doSubmit() }
        }

        loadData()
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

                    // 1. 查 topical_detail 主记录（含 script, id_Editer_user）
                    if (idDetail.isNotEmpty()) {
                        val escIdDetail = idDetail.replace("'", "''")
                        val rs = conn.query(
                            "SELECT id_Editer_user, script FROM topical_detail " +
                                "WHERE id_detail = '$escIdDetail' LIMIT 1"
                        )
                        val rows = rs.toList()
                        if (rows.isNotEmpty()) {
                            editorUser = rows[0].get(0).toString().removeSurrounding("[", "]")
                            val scriptVal = rows[0].get(1).toString().removeSurrounding("[", "]")
                            scriptContent = if (scriptVal == "null" || scriptVal.isEmpty()) "" else scriptVal
                        }
                    }

                    // 2. 按作者账号查 User.name_user（作者姓名）
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

                    // 3. 通过 id_joined 查 joined_topical.id_com
                    var idCom = ""
                    if (idJoined.isNotEmpty()) {
                        val escIdJoined = idJoined.replace("'", "''")
                        val rsJt = conn.query(
                            "SELECT id_com FROM joined_topical " +
                                "WHERE id_joined = '$escIdJoined' LIMIT 1"
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
                            "SELECT com_title FROM commission_summary " +
                                "WHERE id_com = '$escIdCom' LIMIT 1"
                        )
                        val csRows = rsCs.toList()
                        if (csRows.isNotEmpty()) {
                            comTitle = csRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 5. 查 topical_type.type_name（按 letter）
                    if (typeLetter.isNotEmpty()) {
                        val escLetter = typeLetter.replace("'", "''")
                        val rsTt = conn.query(
                            "SELECT type_name FROM topical_type " +
                                "WHERE topical_type = '$escLetter' AND level > 1 LIMIT 1"
                        )
                        val ttRows = rsTt.toList()
                        if (ttRows.isNotEmpty()) {
                            typeName = ttRows[0].get(0).toString().removeSurrounding("[", "]")
                        }
                    }

                    // 6. 查 Department.name_dep（当前报送单位）
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

                                        // 7. 查 dep_vet 回填审核状态和修改意见（按 id_detail + id_dep_vet）
                    var vetValue = Int.MIN_VALUE
                    var memoValue = ""
                    if (idDetail.isNotEmpty() && idDepVet.isNotEmpty()) {
                        val escIdDetail2 = idDetail.replace("'", "''")
                        val escIdDepVet2 = idDepVet.replace("'", "''")
                        val rsDv = conn.query(
                            "SELECT vet, memo FROM dep_vet " +
                                "WHERE id_detail = '$escIdDetail2' AND id_dep_vet = '$escIdDepVet2' LIMIT 1"
                        )
                        val dvRows = rsDv.toList()
                        if (dvRows.isNotEmpty()) {
                            val v = dvRows[0].get(0).toString().removeSurrounding("[", "]")
                            vetValue = v.toIntOrNull() ?: Int.MIN_VALUE
                            val m = dvRows[0].get(1).toString().removeSurrounding("[", "]")
                            memoValue = if (m == "null") "" else m
                        }
                    }

                    // 判断是否已审核（vet != 0 表示已审核）
                    val isAudited = vetValue != 0 && vetValue != Int.MIN_VALUE

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvIdJoinedDep.text = if (depName.isNotEmpty()) "$idJoinedDep ($depName)" else idJoinedDep
                        tvEditorUser.text = if (editorName.isNotEmpty()) "$editorUser ($editorName)" else editorUser
                        tvComTitle.text = comTitle.ifEmpty { "[无标题]" }
                        tvTypeName.text = if (typeName.isNotEmpty()) typeName else "[无类型]"
                        etScript.setText(scriptContent)

                        // 回填审核状态单选
                        when (vetValue) {
                            VET_MODIFY_PASS -> rgAuditStatus.check(R.id.rb_modify_pass)
                            VET_RETURN -> rgAuditStatus.check(R.id.rb_return)
                            VET_NOT_PROMOTE -> rgAuditStatus.check(R.id.rb_not_promote)
                            else -> rgAuditStatus.clearCheck()
                        }
                        // 回填修改意见
                        etMemo.setText(memoValue)
                        tvMemoCount.text = "${memoValue.length} / $MEMO_MAX"

                        // 已审核条目：转为只读模式
                        if (isAudited) {
                            etScript.isEnabled = false
                            etScript.isFocusable = false
                            for (i in 0 until rgAuditStatus.childCount) {
                                rgAuditStatus.getChildAt(i).isEnabled = false
                            }
                            etMemo.isEnabled = false
                            etMemo.isFocusable = false
                            btnSave.visibility = View.GONE
                            btnSubmit.visibility = View.GONE
                        }

                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@DepartmentAuditDetailActivity)
                        .setTitle("加载失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun getSelectedVet(): Int? {
        return when (rgAuditStatus.checkedRadioButtonId) {
            R.id.rb_modify_pass -> VET_MODIFY_PASS
            R.id.rb_return -> VET_RETURN
            R.id.rb_not_promote -> VET_NOT_PROMOTE
            else -> null
        }
    }

    // ===== 保存修改：仅更新 topical_detail.script =====
    private fun doSaveScript() {
        if (isLoading) return
        if (idDetail.isEmpty()) {
            Toast.makeText(this, "缺少 id_detail", Toast.LENGTH_SHORT).show()
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
                        "UPDATE topical_detail SET script = '$escScript' " +
                            "WHERE id_detail = '$escIdDetail'"
                    )
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    Toast.makeText(this@DepartmentAuditDetailActivity, "原脚本更新成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    AlertDialog.Builder(this@DepartmentAuditDetailActivity)
                        .setTitle("保存失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    // ===== 确定提交：弹确认框 =====
    private fun doSubmit() {
        if (isLoading) return
        if (idDetail.isEmpty()) {
            Toast.makeText(this, "缺少 id_detail", Toast.LENGTH_SHORT).show()
            return
        }
        val vetValue = getSelectedVet()
        if (vetValue == null) {
            Toast.makeText(this, "请选择审核结论", Toast.LENGTH_SHORT).show()
            return
        }
        val memo = etMemo.text.toString()

        AlertDialog.Builder(this)
            .setTitle("确认提交")
            .setMessage("确定提交吗？\n审核结论将写入业务审核记录，脚本状态将更新为已审。")
            .setPositiveButton("确定") { _, _ -> performSubmit(vetValue, memo) }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== 确定提交：更新脚本 + dep_vet，失败则应用层回滚 =====
    private fun performSubmit(vetValue: Int, memo: String) {
        isLoading = true
        progressBar.visibility = View.VISIBLE
        setButtonsEnabled(false)

        Thread {
            // 1) 读取旧值，用于失败回滚
            var oldScript = ""
            var oldVetStatue = 0
            var oldVet = 0
            var oldMemo = ""
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escIdDepVet = idDepVet.replace("'", "''")
                    val rs1 = conn.query(
                        "SELECT script, vet_statue FROM topical_detail " +
                            "WHERE id_detail = '$escIdDetail' LIMIT 1"
                    )
                    val r1 = rs1.toList()
                    if (r1.isNotEmpty()) {
                        val sv = r1[0].get(0).toString().removeSurrounding("[", "]")
                        oldScript = if (sv == "null") "" else sv
                        val vs = r1[0].get(1).toString().removeSurrounding("[", "]")
                        oldVetStatue = vs.toIntOrNull() ?: 0
                    }
                    val rs2 = conn.query(
                        "SELECT vet, memo FROM dep_vet " +
                            "WHERE id_detail = '$escIdDetail' AND id_dep_vet = '$escIdDepVet' LIMIT 1"
                    )
                    val r2 = rs2.toList()
                    if (r2.isNotEmpty()) {
                        val v = r2[0].get(0).toString().removeSurrounding("[", "]")
                        oldVet = v.toIntOrNull() ?: 0
                        val m = r2[0].get(1).toString().removeSurrounding("[", "]")
                        oldMemo = if (m == "null") "" else m
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showSubmitError(e) }
                return@Thread
            }

            // 2) 执行写入
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escIdDepVet = idDepVet.replace("'", "''")
                    val escScript = etScript.text.toString().replace("'", "''")
                    val escMemo = memo.replace("'", "''")

                    conn.execute(
                        "UPDATE topical_detail SET script = '$escScript', vet_statue = 3 " +
                            "WHERE id_detail = '$escIdDetail'"
                    )
                    conn.execute(
                        "UPDATE dep_vet SET vet = $vetValue, memo = '$escMemo' " +
                            "WHERE id_detail = '$escIdDetail' AND id_dep_vet = '$escIdDepVet'"
                    )
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    setButtonsEnabled(true)
                    AlertDialog.Builder(this@DepartmentAuditDetailActivity)
                        .setTitle("成功")
                        .setMessage("部门审核已提交")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                }
            } catch (e: Exception) {
                // 3) 回滚：将涉及的记录写回旧值
                try {
                    Db.withConnection { conn ->
                        val escIdDetail = idDetail.replace("'", "''")
                        val escIdDepVet = idDepVet.replace("'", "''")
                        val escOldScript = oldScript.replace("'", "''")
                        val escOldMemo = oldMemo.replace("'", "''")
                        conn.execute(
                            "UPDATE topical_detail SET script = '$escOldScript', " +
                                "vet_statue = $oldVetStatue WHERE id_detail = '$escIdDetail'"
                        )
                        conn.execute(
                            "UPDATE dep_vet SET vet = $oldVet, memo = '$escOldMemo' " +
                                "WHERE id_detail = '$escIdDetail' AND id_dep_vet = '$escIdDepVet'"
                        )
                    }
                } catch (_: Exception) {
                    // 回滚本身失败也继续上报原错误
                }
                runOnUiThread { showSubmitError(e) }
            }
        }.start()
    }

    private fun showSubmitError(e: Exception) {
        progressBar.visibility = View.GONE
        isLoading = false
        setButtonsEnabled(true)
        AlertDialog.Builder(this@DepartmentAuditDetailActivity)
            .setTitle("提交失败（已回滚）")
            .setMessage("${e.javaClass.simpleName}\n${e.message}")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSave.isEnabled = enabled
        btnSubmit.isEnabled = enabled
    }
}
