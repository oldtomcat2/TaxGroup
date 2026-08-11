package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ScriptItem(
    val idJoined: String,     // 隐藏传递用
    val idJoinedList: String, // id_joined + 字母（显示用，调试用）
    val idCom: String,
    val comTitle: String,
    val isSubmitted: Boolean, // true=已提交，false=未提交
    val typeName: String?,    // 类型名称（从 topical_type 查，level>1 时显示）
    val idDetail: String = "" // topical_detail.id_detail主键（已提交时已存在；未提交时为空）
)

class ScriptEditActivity : AppCompatActivity() {

    private lateinit var btnScriptEdit: Button
    private lateinit var btnScriptQuery: Button
    private lateinit var tvHint: TextView
    private lateinit var tvSubmittedCount: TextView
    private lateinit var tvDraftCount: TextView
    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar

    private var currentMode: Int = MODE_EDIT  // 0=脚本编辑，1=脚本查询
    private var submittedList: List<ScriptItem> = emptyList()
    private var draftList: List<ScriptItem> = emptyList()

    @Volatile
    private var isLoading = false

    companion object {
        const val MODE_EDIT = 0
        const val MODE_QUERY = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_edit)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_edit_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 初始化控件
        btnScriptEdit = findViewById(R.id.btn_script_edit)
        btnScriptQuery = findViewById(R.id.btn_script_query)
        tvHint = findViewById(R.id.tv_hint)
        tvSubmittedCount = findViewById(R.id.tv_submitted_count)
        tvDraftCount = findViewById(R.id.tv_draft_count)
        rvScripts = findViewById(R.id.rv_scripts)
        tvEmpty = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progress_bar)

        rvScripts.layoutManager = LinearLayoutManager(this)

        // 顶部按钮
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { loadData() }

        // 功能按钮
        btnScriptEdit.setOnClickListener {
            currentMode = MODE_EDIT
            updateButtonStyle()
            displayList()
        }
        btnScriptQuery.setOnClickListener {
            currentMode = MODE_QUERY
            updateButtonStyle()
            displayList()
        }

        // 初始加载
        updateButtonStyle()
        loadData()
    }

    private fun updateButtonStyle() {
        when (currentMode) {
            MODE_EDIT -> {
                btnScriptEdit.setBackgroundResource(R.drawable.bg_topic_btn_blue)
                btnScriptQuery.setBackgroundResource(R.drawable.bg_topic_btn_gray)
                tvHint.visibility = View.VISIBLE
            }
            MODE_QUERY -> {
                btnScriptEdit.setBackgroundResource(R.drawable.bg_topic_btn_gray)
                btnScriptQuery.setBackgroundResource(R.drawable.bg_topic_btn_blue)
                tvHint.visibility = View.GONE
            }
        }
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvScripts.visibility = View.GONE

        val level = MyApp.loginDepLevel
        val dep = MyApp.loginDeaprt

        Thread {
            try {
                val submitted = mutableListOf<ScriptItem>()
                val draft = mutableListOf<ScriptItem>()

                Db.withConnection { conn ->
                    // 1. 查 joined_topical 中 ps 为1 （选题已审核，脚本待编辑）的记录
                    //    loginDepLevel<3 查所有，level>=3 查本部门（再按 id_joined_dep 过滤）
                    val escDep = dep.replace("'", "''")
                    val sqlJt = if (level >= 3) {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND id_joined_dep = '$escDep'"
                    } else {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 "
                    }
                    val rsJt = conn.query(sqlJt)
                    val jtRows = rsJt.toList()

                    if (jtRows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvSubmittedCount.text = "0"
                            tvDraftCount.text = "0"
                            isLoading = false
                        }
                        return@withConnection
                    }

                    // 2. 将 type_list 分解成单个字母，id_joined+字母 -> id_joined_list
                    for (jtRow in jtRows) {
                        val idJoined = jtRow.get(0).toString().removeSurrounding("[", "]").trim()
                        val idCom = jtRow.get(1).toString().removeSurrounding("[", "]").trim()
                        val idJoinedDep = jtRow.get(2).toString().removeSurrounding("[", "]").trim()
                        val typeListStr = jtRow.get(3).toString().removeSurrounding("[", "]").trim()

                        // type_list 分解成单个字母
                        val letters = typeListStr.replace("\"", "").replace("'", "").replace("[", "").replace("]", "")
                            .trim().split("").filter { it.isNotBlank() }

                        for (letter in letters) {
                            val idJoinedList = idJoined + letter
                            val escIdCom = idCom.replace("'", "''")
                            val escIdDep = idJoinedDep.replace("'", "''")
                            val escIdJoinedList = idJoinedList.replace("'", "''")

                            // 3. 通过 id_com 查 commission_summary 的 com_title
                            val rsCs = conn.query("SELECT com_title FROM commission_summary WHERE id_com = '$escIdCom'  AND vet = 1")
                            val csRows = rsCs.toList()
                            val comTitle = if (csRows.isNotEmpty()) {
                                csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                ""
                            }

                            // 4. 从 topical_detail 查前13位 id_detail，条件 id_joined_dep 相同
                            val idDetailPrefix = idJoinedList.take(13)
                            val rsTd = conn.query(
                                "SELECT id_detail FROM topical_detail WHERE id_detail LIKE '$idDetailPrefix%' AND id_joined_dep = '$escIdDep' LIMIT 1"
                            )
                            val tdRows = rsTd.toList()
                            val isSubmitted = tdRows.isNotEmpty()
                            val idDetail = if (isSubmitted) {
                                tdRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                ""
                            }

                            // 5. 查 topical_type：字母对应，level>1 时取 type_name
                            val escLetter = letter.replace("'", "''")
                            val rsType = conn.query(
                                "SELECT type_name FROM topical_type WHERE topical_type = '$escLetter' AND level > 1 LIMIT 1"
                            )
                            val typeRows = rsType.toList()
                            val typeName = if (typeRows.isNotEmpty()) {
                                typeRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                null
                            }

                            // level < 2 的不显示（typeName 为 null 时跳过）
                            if (typeName == null) continue

                            val item = ScriptItem(
                                idJoined = idJoined,
                                idJoinedList = idJoinedList,
                                idCom = idCom,
                                comTitle = comTitle,
                                isSubmitted = isSubmitted,
                                typeName = typeName,
                                idDetail = idDetail
                            )

                            if (isSubmitted) submitted.add(item) else draft.add(item)
                        }
                    }
                }

                submittedList = submitted
                draftList = draft

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvSubmittedCount.text = submitted.size.toString()
                    tvDraftCount.text = draft.size.toString()
                    displayList()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@ScriptEditActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun displayList() {
        val allItems = when (currentMode) {
            MODE_EDIT -> draftList + submittedList  // 脚本编辑：未提交在前
            MODE_QUERY -> submittedList + draftList  // 脚本查询：已提交在前
            else -> emptyList()
        }

        if (allItems.isEmpty()) {
            rvScripts.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            rvScripts.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            rvScripts.adapter = ScriptItemAdapter(allItems) { item ->
                val intent = android.content.Intent(this@ScriptEditActivity, ScriptDetailActivity::class.java)
                    .putExtra(ScriptDetailActivity.EXTRA_ID_COM, item.idCom)
                    .putExtra(ScriptDetailActivity.EXTRA_ID_JOINED, item.idJoined)
                    .putExtra(ScriptDetailActivity.EXTRA_ID_JOINED_LIST, item.idJoinedList)
                    .putExtra(ScriptDetailActivity.EXTRA_TYPE_NAME, item.typeName ?: "")
                    .putExtra(ScriptDetailActivity.EXTRA_IS_SUBMITTED, item.isSubmitted)
                    .putExtra(ScriptDetailActivity.EXTRA_ID_DETAIL, item.idDetail)
                startActivity(intent)
            }
        }
    }

    // ==================== RecyclerView Adapter ====================
    inner class ScriptItemAdapter(
        private val items: List<ScriptItem>,
        private val onItemClick: (ScriptItem) -> Unit
    ) : RecyclerView.Adapter<ScriptItemAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val statusIndicator: View = itemView.findViewById(R.id.status_indicator)
            val tvScriptTitle: TextView = itemView.findViewById(R.id.tv_script_title)
            val tvTypeTags: TextView = itemView.findViewById(R.id.tv_type_tags)
            val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_script_topic, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvScriptTitle.text = item.comTitle.ifEmpty { "[无标题]" }
            // typeName 不为空时显示 typeName，否则显示编号
            val displayTag = item.typeName ?: "编号：${item.idJoinedList}"
            holder.tvTypeTags.text = displayTag

            if (item.isSubmitted) {
                holder.statusIndicator.setBackgroundColor(0xFF1976D2.toInt())
                holder.tvStatus.text = "已提交"
                holder.tvStatus.setBackgroundColor(0xFF1976D2.toInt())
                holder.tvStatus.setTextColor(0xFFFFFFFF.toInt())
            } else {
                holder.statusIndicator.setBackgroundColor(0xFFFF6F00.toInt())
                holder.tvStatus.text = "未提交"
                holder.tvStatus.setBackgroundColor(0xFFFF6F00.toInt())
                holder.tvStatus.setTextColor(0xFFFFFFFF.toInt())
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
