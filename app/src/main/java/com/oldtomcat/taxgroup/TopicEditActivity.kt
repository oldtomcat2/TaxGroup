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
    private lateinit var llImportantTypes: LinearLayout
    private lateinit var llDirectionTypes: LinearLayout
    private lateinit var llJointDeps: LinearLayout
    private lateinit var llScriptAudit: LinearLayout
    private lateinit var btnSubmit: Button
    private lateinit var btnDelete: Button
    private lateinit var btnBackBottom: Button
    private lateinit var progressBar: ProgressBar

    private var idCom: String = ""
    private var idUser: String = ""  // 选题作者 id
    private var idDep: String = ""  // 选题发起部门 id
    private var vet: Int = 0  // 当前审核状态:0未审核 1已审核 2未通过

    // 查询锁:防并发访问数据库(HTTP)
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
            Toast.makeText(this, "参数错误:未指定记录", Toast.LENGTH_SHORT).show()
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
        llImportantTypes = findViewById(R.id.ll_important_types)
        llDirectionTypes = findViewById(R.id.ll_direction_types)
        btnSubmit = findViewById(R.id.btn_submit)
        btnDelete = findViewById(R.id.btn_delete)
        btnBackBottom = findViewById(R.id.btn_back_bottom)
        progressBar = findViewById(R.id.progress_bar)
        llJointDeps = findViewById(R.id.ll_joint_deps)
        llScriptAudit = findViewById(R.id.ll_script_audit)

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
                        "name_user, a.last_mod_user, a.last_mod_date, a.id_dep " +
                        "FROM commission_summary a, User b " +
                        "WHERE (a.id_user = b.id_user) AND a.id_com = '$idCom'"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    if (rows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("提示")
                                .setMessage("未找到该记录(id_com=$idCom)")
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
                    val idDep = row.get(9).toString().removeSurrounding("[", "]")
                    this.idDep = idDep
                    this.idDep = idDep

                    // 最后修改人:用 last_mod_user 关联 User 表查 name_user
                    var modUserName = "无"
                    if (lastModUser.isNotEmpty() && lastModUser != "null") {
                        try {
                            val rs2 = conn.query("SELECT name_user FROM User WHERE id_user = '$lastModUser'")
                            val rows2 = rs2.toList()
                            if (rows2.isNotEmpty()) {
                                modUserName = rows2[0].get(0).toString().removeSurrounding("[", "]")
                            }
                        } catch (_: Exception) {
                            // 忽略,保持"无"
                        }
                    }

                    // 最后修改日期:取 月(第3-4位) + 日(第5-6位),格式 MM月dd日
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

                    // 加载选题类型(只读展示):
                    // 1. 从 joined_topical 取 type_list
                    // 2. 从 topical_type 按 level=1/2 分别取选项
                    // 3. type_list 单字符匹配 topical_type,勾选默认项
                    var typeListStr = ""
                    val typeListL1: MutableList<Pair<String, String>> = mutableListOf()
                    val typeListL2: MutableList<Pair<String, String>> = mutableListOf()
                    try {
     //                   var ss = "SELECT type_list FROM joined_topical WHERE id_com = '$idCom' AND id_joined_dep = '${MyApp.loginDeaprt}'"
                        val rsJt = conn.query(
                            "SELECT type_list FROM joined_topical WHERE id_com = '$idCom' AND id_joined_dep = '$idDep'"
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

                    // 部门联动情况：查 id_com 下的非本部门联动部门
                    // joined_topical (id_com, id_joined_dep) → LEFT JOIN Department (id_dep=name_dep)
                    val jointDeps: MutableList<Pair<String, String>> = mutableListOf() // (id_dep, name_dep)
                    try {
                        val escIdDep = idDep.replace("'", "''")
                        val escIdCom = idCom.replace("'", "''")
                        val rsJoint = conn.query(
                            "SELECT jt.id_joined_dep, d.name_dep " +
                                "FROM joined_topical jt " +
                                "LEFT JOIN Department d ON d.id_dep = jt.id_joined_dep " +
                                "WHERE jt.id_com = '$escIdCom' " +
                                "AND jt.id_joined_dep <> '$escIdDep' " +
                                "ORDER BY jt.id_joined_dep"
                        )
                        val jRows = rsJoint.toList()
                        for (jr in jRows) {
                            val jid = jr.get(0).toString().removeSurrounding("[", "]").trim()
                            val jname = jr.get(1).toString().removeSurrounding("[", "]").trim()
                            jointDeps.add(Pair(jid, if (jname.isEmpty() || jname == "null") jid else jname))
                        }
                    } catch (_: Exception) { }

                    // 脚本审核情况：查本部门 id_joined → topical_detail.id_detail 前12位
                    // 返回 (id_type, type_name, id_detail, vet_statue) 列表
                    data class ScriptAuditItem(val idType: String, val typeName: String, val idDetail: String, val vet: Int)
                    val scriptAuditList: MutableList<ScriptAuditItem> = mutableListOf()
                    try {
                        val escIdDep2 = idDep.replace("'", "''")
                        val escIdCom2 = idCom.replace("'", "''")
                        // 1. 取本部门 id_joined
                        val rsSelf = conn.query(
                            "SELECT id_joined FROM joined_topical " +
                                "WHERE id_com = '$escIdCom2' AND id_joined_dep = '$escIdDep2' LIMIT 1"
                        )
                        val sRows = rsSelf.toList()
                        if (sRows.isNotEmpty()) {
                            val idJoined = sRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            if (idJoined.isNotEmpty()) {
                                // 2. 查 topical_detail 前 12 位匹配的脚本（取 id_type）
                                val escIdJ = idJoined.replace("'", "''")
                                val rsTd = conn.query(
                                    "SELECT id_type, id_detail, vet_statue FROM topical_detail " +
                                        "WHERE substr(id_detail, 1, 12) = '$escIdJ' " +
                                        "AND id_joined_dep = '$escIdDep2' " +
                                        "ORDER BY id_type"
                                )
                                val tdRows = rsTd.toList()
                                for (tr in tdRows) {
                                    val idType = tr.get(0).toString().removeSurrounding("[", "]").trim()
                                    val idDetail = tr.get(1).toString().removeSurrounding("[", "]").trim()
                                    val vetStatue = tr.get(2).toString().removeSurrounding("[", "]").toIntOrNull() ?: 0
                                    // 3. 联查 type_name
                                    var typeName = idType
                                    if (idType.isNotEmpty()) {
                                        val escIdT = idType.replace("'", "''")
                                        val rsTn = conn.query(
                                            "SELECT type_name FROM topical_type WHERE topical_type = '$escIdT' LIMIT 1"
                                        )
                                        val tnRows = rsTn.toList()
                                        if (tnRows.isNotEmpty()) {
                                            typeName = tnRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                                        }
                                    }
                                    scriptAuditList.add(ScriptAuditItem(idType, typeName, idDetail, vetStatue))
                                }
                            }
                        }
                    } catch (_: Exception) { }

                    // type_list 格式可能是 "abc" 或 "[\"a\",\"b\"]" 等,统一处理成单个字符的字符串集合
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

                        // 渲染部门联动情况
                        llJointDeps.removeAllViews()
                        if (jointDeps.isEmpty()) {
                            val tv = TextView(this@TopicEditActivity)
                            tv.text = "无联动合作单位"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            tv.setPadding(0, 4, 0, 4)
                            llJointDeps.addView(tv)
                        } else {
                            for ((idx, dep) in jointDeps.withIndex()) {
                                val (jid, jname) = dep
                                val row = LinearLayout(this@TopicEditActivity)
                                row.orientation = LinearLayout.HORIZONTAL
                                row.gravity = android.view.Gravity.CENTER_VERTICAL
                                row.setPadding(0, 6, 0, 6)
                                val nameTv = TextView(this@TopicEditActivity)
                                nameTv.text = "${idx + 1}. $jname"
                                nameTv.setTextColor(0xFF333333.toInt())
                                nameTv.textSize = 14f
                                nameTv.layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                )
                                row.addView(nameTv)
                                val viewBtn = Button(this@TopicEditActivity)
                                viewBtn.text = "查看脚本"
                                viewBtn.textSize = 12f
                                viewBtn.setTextColor(0xFFFFFFFF.toInt())
                                viewBtn.setBackgroundColor(0xFF1976D2.toInt())
                                viewBtn.setPadding(20, 6, 20, 6)
                                viewBtn.setOnClickListener {
                                    loadAndOpenScriptForJointDep(jid, jname)
                                }
                                row.addView(viewBtn)
                                llJointDeps.addView(row)
                            }
                        }

                        // 渲染脚本审核情况
                        llScriptAudit.removeAllViews()
                        if (scriptAuditList.isEmpty()) {
                            val tv = TextView(this@TopicEditActivity)
                            tv.text = "本部门未提交脚本"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            tv.setPadding(0, 4, 0, 4)
                            llScriptAudit.addView(tv)
                        } else {
                            for (item in scriptAuditList) {
                                val row = LinearLayout(this@TopicEditActivity)
                                row.orientation = LinearLayout.HORIZONTAL
                                row.gravity = android.view.Gravity.CENTER_VERTICAL
                                row.setPadding(0, 6, 0, 6)
                                val infoTv = TextView(this@TopicEditActivity)
                                val vetText = when (item.vet) {
                                    1 -> "已审核"
                                    3 -> "业务部门已审核"
                                    2 -> "已提交业务部门审核"
                                    4 -> "选题作废"
                                    else -> "未提交"
                                }
                                infoTv.text = "${item.typeName}：$vetText"
                                infoTv.setTextColor(when (item.vet) {
                                    1 -> 0xFF2E7D32.toInt()
                                    3 -> 0xFF6A1B9A.toInt()
                                    2 -> 0xFFEF6C00.toInt()
                                    4 -> 0xFFC62828.toInt()
                                    else -> 0xFF999999.toInt()
                                })
                                infoTv.textSize = 14f
                                infoTv.layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                )
                                row.addView(infoTv)
                                val viewBtn = Button(this@TopicEditActivity)
                                viewBtn.text = "脚本查看"
                                viewBtn.textSize = 12f
                                viewBtn.setTextColor(0xFFFFFFFF.toInt())
                                viewBtn.setBackgroundColor(0xFF2E7D32.toInt())
                                viewBtn.setPadding(20, 6, 20, 6)
                                viewBtn.setOnClickListener {
                                    val intent = android.content.Intent(this@TopicEditActivity, ScriptViewActivity::class.java)
                                    intent.putExtra(ScriptViewActivity.EXTRA_ID_DETAIL, item.idDetail)
                                    startActivity(intent)
                                }
                                row.addView(viewBtn)
                                llScriptAudit.addView(row)
                            }
                        }
                        if (hasMemo) {
                            llRejectMemo.visibility = View.VISIBLE
                            tvRejectMemo.text = rejectMemo
                        } else {
                            llRejectMemo.visibility = View.GONE
                        }

                        // 渲染重要选题(只读)
                        llImportantTypes.removeAllViews()
                        if (typeListL1.isEmpty()) {
                            val tv = TextView(this@TopicEditActivity)
                            tv.text = "无重要选题"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            llImportantTypes.addView(tv)
                        } else {
                            typeListL1.forEach { (code, name) ->
                                val cb = CheckBox(this@TopicEditActivity)
                                cb.text = name
                                cb.tag = code  // 保存code用于提交时收集
                                cb.isChecked = checkedCodes.contains(code)
                                cb.setTextColor(0xFF333333.toInt())
                                llImportantTypes.addView(cb)
                            }
                        }

                        // 渲染选题方向(只读)
                        llDirectionTypes.removeAllViews()
                        if (typeListL2.isEmpty()) {
                            val tv = TextView(this@TopicEditActivity)
                            tv.text = "无选题方向"
                            tv.setTextColor(0xFF999999.toInt())
                            tv.textSize = 13f
                            llDirectionTypes.addView(tv)
                        } else {
                            typeListL2.forEach { (code, name) ->
                                val cb = CheckBox(this@TopicEditActivity)
                                cb.text = name
                                cb.tag = code  // 保存code用于提交时收集
                                cb.isChecked = checkedCodes.contains(code)
                                cb.setTextColor(0xFF333333.toInt())
                                llDirectionTypes.addView(cb)
                            }
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
                        .setMessage("加载失败:${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    /**
     * 联动部门"查看脚本"：
     * 1. 通过 id_com + 联动部门 id_joined_dep 查 joined_topical.id_joined
     * 2. 用 id_joined 作为前缀查 topical_detail.id_detail（前 12 位匹配）
     * 3. 拿到第 13 位字母（id_type），联查 topical_type.type_name
     * 4. 找到则启动 ScriptViewActivity；多个则弹选择
     */
    private fun loadAndOpenScriptForJointDep(jointDepId: String, jointDepName: String) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        val escIdCom = idCom.replace("'", "''")
        val escJointDep = jointDepId.replace("'", "''")

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 联动部门的 id_joined
                    val rsJ = conn.query(
                        "SELECT id_joined FROM joined_topical " +
                            "WHERE id_com = '$escIdCom' AND id_joined_dep = '$escJointDep' LIMIT 1"
                    )
                    val jRows = rsJ.toList()
                    if (jRows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            isLoading = false
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("提示")
                                .setMessage("「$jointDepName」未加入此选题，无法查看脚本")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                        return@withConnection
                    }
                    val idJoined = jRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                    if (idJoined.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            isLoading = false
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("提示")
                                .setMessage("「$jointDepName」未提交脚本")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                        return@withConnection
                    }

                    // 2. 查 topical_detail 中前 12 位匹配的所有脚本
                    val escJ = idJoined.replace("'", "''")
                    val rsTd = conn.query(
                        "SELECT id_type, id_detail FROM topical_detail " +
                            "WHERE substr(id_detail, 1, 12) = '$escJ' " +
                            "AND id_joined_dep = '$escJointDep' " +
                            "ORDER BY id_type"
                    )
                    val tdRows = rsTd.toList()
                    if (tdRows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            isLoading = false
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("提示")
                                .setMessage("「$jointDepName」未提交脚本")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                        return@withConnection
                    }

                    // 3. 对每行用第 13 位字母查 type_name
                    data class ScriptChoice(val idDetail: String, val typeName: String)
                    val choices = mutableListOf<ScriptChoice>()
                    for (tr in tdRows) {
                        val idType = tr.get(0).toString().removeSurrounding("[", "]").trim()
                        val idDetail = tr.get(1).toString().removeSurrounding("[", "]").trim()
                        // 第 13 位字母作为 id_type（也可能直接用 id_type 字段）
                        val typeLetter = if (idDetail.length >= 13) idDetail.substring(12, 13) else idType
                        var typeName = typeLetter
                        if (typeLetter.isNotEmpty()) {
                            val escL = typeLetter.replace("'", "''")
                            val rsTn = conn.query(
                                "SELECT type_name FROM topical_type WHERE topical_type = '$escL' LIMIT 1"
                            )
                            val tnRows = rsTn.toList()
                            if (tnRows.isNotEmpty()) {
                                typeName = tnRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                                if (typeName.isEmpty()) typeName = typeLetter
                            }
                        }
                        choices.add(ScriptChoice(idDetail, typeName))
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        isLoading = false
                        if (choices.size == 1) {
                            openScriptView(choices[0].idDetail, choices[0].typeName, jointDepName)
                        } else {
                            // 多个脚本，弹选择
                            val labels = choices.map { it.typeName }.toTypedArray()
                            AlertDialog.Builder(this@TopicEditActivity)
                                .setTitle("选择脚本")
                                .setItems(labels) { _, which ->
                                    val c = choices[which]
                                    openScriptView(c.idDetail, c.typeName, jointDepName)
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("加载失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun openScriptView(idDetail: String, typeName: String, depName: String) {
        val intent = android.content.Intent(this, ScriptViewActivity::class.java)
        intent.putExtra(ScriptViewActivity.EXTRA_ID_DETAIL, idDetail)
        intent.putExtra("type_name", typeName)
        intent.putExtra("dep_name", depName)
        startActivity(intent)
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
            .setMessage("确定要保存修改吗?")
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
        val escLoginDep = idDep.replace("'", "''")
        // 当前时间：年(2位)+月(2位)+日(2位)+时(2位)+分(2位) → 如 2607231113（yyMMddHHmm，10位）
        val sdf = java.text.SimpleDateFormat("yyMMddHHmm", java.util.Locale.getDefault())
        val lastModDate = sdf.format(java.util.Date())

        // 收集选中的复选框code（先level1后level2，保持顺序）
        val selectedCodes = mutableListOf<String>()
        for (i in 0 until llImportantTypes.childCount) {
            val child = llImportantTypes.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                child.tag?.toString()?.let { selectedCodes.add(it) }
            }
        }
        for (i in 0 until llDirectionTypes.childCount) {
            val child = llDirectionTypes.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                child.tag?.toString()?.let { selectedCodes.add(it) }
            }
        }
        val typeListStr = selectedCodes.joinToString("")

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 更新 commission_summary
                    val sql = "UPDATE commission_summary SET " +
                        "com_title = '$escTitle', " +
                        "com_summary = '$escContent', " +
                        "last_mod_user = '$escLoginId', " +
                        "last_mod_date = '$lastModDate' " +
                        "WHERE id_com = '$escIdCom'"
                    conn.execute(sql)

                    // 2. 更新 joined_topical.type_list
                    val sql2 = "UPDATE joined_topical SET " +
                        "type_list = '${typeListStr.replace("'", "''")}' " +
                        "WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                    conn.execute(sql2)
                }
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("成功")
                        .setMessage("修改已保存\n提交时间:$lastModDate")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TopicEditActivity)
                        .setTitle("错误")
                        .setMessage("提交失败:${e.javaClass.simpleName}\n${e.message}")
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
            .setMessage("删除后不可恢复,确定要删除该选题吗?")
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
                        .setMessage("删除失败:${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }
}
