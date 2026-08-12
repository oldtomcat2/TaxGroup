package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// 扁平化列表项：部门标题 或 脚本项
data class ScriptListEntry(
    val depName: String = "",
    val count: Int = 0,
    val scriptItem: ScriptItem? = null
) {
    val isHeader: Boolean get() = scriptItem == null
}

data class ScriptItem(
    val idJoined: String,
    val idJoinedList: String,
    val idCom: String,
    val comTitle: String,
    val isSubmitted: Boolean,
    val typeName: String?,
    val idDetail: String = "",
    val departName: String = ""  // 部门名称（从 Department 表查）
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

    private var currentMode: Int = MODE_EDIT
    private var submittedList: List<ScriptItem> = emptyList()
    private var draftList: List<ScriptItem> = emptyList()
    private val flatList = mutableListOf<ScriptListEntry>()

    @Volatile
    private var isLoading = false

    companion object {
        const val MODE_EDIT = 0
        const val MODE_QUERY = 1
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_edit)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_edit_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        btnScriptEdit = findViewById(R.id.btn_script_edit)
        btnScriptQuery = findViewById(R.id.btn_script_query)
        tvHint = findViewById(R.id.tv_hint)
        tvSubmittedCount = findViewById(R.id.tv_submitted_count)
        tvDraftCount = findViewById(R.id.tv_draft_count)
        rvScripts = findViewById(R.id.rv_scripts)
        tvEmpty = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progress_bar)

        rvScripts.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { loadData() }

        btnScriptEdit.setOnClickListener {
            currentMode = MODE_EDIT
            updateButtonStyle()
            buildFlatList()
        }
        btnScriptQuery.setOnClickListener {
            currentMode = MODE_QUERY
            updateButtonStyle()
            buildFlatList()
        }

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
                val depNameCache = mutableMapOf<String, String>()

                Db.withConnection { conn ->
                    val escDep = dep.replace("'", "''")
                    val sqlJt = if (level >= 3) {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND id_joined_dep = '$escDep'"
                    } else {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1"
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

                    for (jtRow in jtRows) {
                        val idJoined = jtRow.get(0).toString().removeSurrounding("[", "]").trim()
                        val idCom = jtRow.get(1).toString().removeSurrounding("[", "]").trim()
                        val idJoinedDep = jtRow.get(2).toString().removeSurrounding("[", "]").trim()
                        val typeListStr = jtRow.get(3).toString().removeSurrounding("[", "]").trim()

                        // 查部门名称（缓存）
                        val depName = depNameCache.getOrPut(idJoinedDep) {
                            val escIdDep = idJoinedDep.replace("'", "''")
                            val rsDep = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep' LIMIT 1")
                            val depRows = rsDep.toList()
                            if (depRows.isNotEmpty()) {
                                depRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                idJoinedDep
                            }
                        }

                        val letters = typeListStr.replace("\"", "").replace("'", "").replace("[", "").replace("]", "")
                            .trim().split("").filter { it.isNotBlank() }

                        for (letter in letters) {
                            val idJoinedList = idJoined + letter
                            val escIdCom = idCom.replace("'", "''")
                            val escIdDep = idJoinedDep.replace("'", "''")
                            val escLetter = letter.replace("'", "''")

                            val rsCs = conn.query("SELECT com_title FROM commission_summary WHERE id_com = '$escIdCom' AND vet = 1")
                            val csRows = rsCs.toList()
                            val comTitle = if (csRows.isNotEmpty()) {
                                csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                ""
                            }

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

                            val rsType = conn.query(
                                "SELECT type_name FROM topical_type WHERE topical_type = '$escLetter' AND level > 1 LIMIT 1"
                            )
                            val typeRows = rsType.toList()
                            val typeName = if (typeRows.isNotEmpty()) {
                                typeRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                null
                            }

                            if (typeName == null) continue

                            val item = ScriptItem(
                                idJoined = idJoined,
                                idJoinedList = idJoinedList,
                                idCom = idCom,
                                comTitle = comTitle,
                                isSubmitted = isSubmitted,
                                typeName = typeName,
                                idDetail = idDetail,
                                departName = depName
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
                    buildFlatList()
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

    private fun buildFlatList() {
        flatList.clear()

        val sourceItems = when (currentMode) {
            MODE_EDIT -> draftList + submittedList
            MODE_QUERY -> submittedList + draftList
            else -> emptyList()
        }

        if (sourceItems.isEmpty()) {
            rvScripts.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            rvScripts.adapter = null
            return
        }

        rvScripts.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        // 按部门分组并平化
        val grouped = sourceItems.groupBy { it.departName }.toSortedMap()
        for ((depName, items) in grouped) {
            flatList.add(ScriptListEntry(depName = depName, count = items.size))
            for (item in items) {
                flatList.add(ScriptListEntry(scriptItem = item))
            }
        }

        rvScripts.adapter = ScriptListAdapter(flatList) { item ->
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

    // ==================== RecyclerView Adapter ====================
    inner class ScriptListAdapter(
        private val items: List<ScriptListEntry>,
        private val onItemClick: (ScriptItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class HeaderVH(val root: android.widget.LinearLayout) : RecyclerView.ViewHolder(root) {
            val tvName: TextView = root.findViewById(R.id.tv_department_name)
            val tvCount: TextView = root.findViewById(R.id.tv_count)
            val ivExpand: ImageView = root.findViewById(R.id.iv_expand)
            val card: MaterialCardView = root.findViewById(R.id.card_department)
        }

        inner class ItemVH(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
            val statusIndicator: View = card.findViewById(R.id.status_indicator)
            val tvScriptTitle: TextView = card.findViewById(R.id.tv_script_title)
            val tvTypeTags: TextView = card.findViewById(R.id.tv_type_tags)
            val tvStatus: TextView = card.findViewById(R.id.tv_status)
        }

        override fun getItemViewType(position: Int): Int =
            if (items[position].isHeader) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val v = inflater.inflate(R.layout.item_department_group, parent, false)
                HeaderVH(v as android.widget.LinearLayout)
            } else {
                val v = inflater.inflate(R.layout.item_script_topic, parent, false)
                ItemVH(v as MaterialCardView)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entry = items[position]
            if (entry.isHeader) {
                val h = holder as HeaderVH
                h.tvName.text = entry.depName
                h.tvCount.text = "${entry.count} 条"
                h.ivExpand.rotation = 0f
                h.card.setOnClickListener(null)
            } else {
                val item = entry.scriptItem!!
                val h = holder as ItemVH
                h.tvScriptTitle.text = item.comTitle.ifEmpty { "[无标题]" }
                val displayTag = item.typeName ?: "编号：${item.idJoinedList}"
                h.tvTypeTags.text = displayTag

                if (item.isSubmitted) {
                    h.statusIndicator.setBackgroundColor(0xFF1976D2.toInt())
                    h.tvStatus.text = "已提交"
                    h.tvStatus.setBackgroundColor(0xFF1976D2.toInt())
                    h.tvStatus.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    h.statusIndicator.setBackgroundColor(0xFFFF6F00.toInt())
                    h.tvStatus.text = "未提交"
                    h.tvStatus.setBackgroundColor(0xFFFF6F00.toInt())
                    h.tvStatus.setTextColor(0xFFFFFFFF.toInt())
                }

                h.card.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount() = items.size
    }
}