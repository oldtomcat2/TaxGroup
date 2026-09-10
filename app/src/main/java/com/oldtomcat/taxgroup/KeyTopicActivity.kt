package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

/**
 * 重点选题 - 复用 ScriptEditActivity 的逻辑，但 topical_type 筛选条件改为 level = 1
 * 布局与脚本编辑页共用：activity_script_edit.xml
 */
class KeyTopicActivity : AppCompatActivity() {

    companion object {
        private const val FILTER_DRAFT = 0
        private const val FILTER_SUBMITTED = 1
        private const val FILTER_ALL = 2
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        // 区别于 ScriptEditActivity 的关键筛选：level = 1（不是 level > 1）
        private const val TOPICAL_TYPE_LEVEL_FILTER = "level = 1"
    }

    private lateinit var rvScripts: RecyclerView
    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var tvEmpty: TextView
    private lateinit var tvSubmittedCount: TextView
    private lateinit var tvDraftCount: TextView
    private lateinit var tvHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnExport: android.widget.Button

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var currentFilter: Int = FILTER_DRAFT
    private var isLoading = false
    private var isExporting = false

    private var submittedList = mutableListOf<ScriptItem>()
    private var draftList = mutableListOf<ScriptItem>()
    private var flatList = mutableListOf<ScriptListEntry>()
    private var expandedGroups = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_edit) // 复用脚本编辑布局

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_edit_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏：用户名 + 部门（用 tv_user_name 当页头显示"重点选题"）
        findViewById<TextView>(R.id.tv_user_name).text = "重点选题"
        findViewById<TextView>(R.id.tv_depart).text = "重点类型选题"
        // 隐藏脚本编辑页的右侧固定标题"脚本编辑"（重点选题页不需要）
        findViewById<TextView>(R.id.tv_page_title).visibility = View.GONE
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = Intent(this, HomeMenuActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        rvScripts = findViewById(R.id.rv_scripts)
        spYear = findViewById(R.id.sp_year)
        spMonth = findViewById(R.id.sp_month)
        tvEmpty = findViewById(R.id.tv_empty)
        tvSubmittedCount = findViewById(R.id.tv_submitted_count)
        tvDraftCount = findViewById(R.id.tv_draft_count)
        tvHint = findViewById(R.id.tv_hint)
        progressBar = findViewById(R.id.progress_bar)
        btnExport = findViewById(R.id.btn_export)

        rvScripts.layoutManager = LinearLayoutManager(this)

        setupFilters()
        setupFilterRadios()

        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { loadData() }
        btnExport.setOnClickListener { exportToExcel() }

        loadData()
    }

    private fun setupFilters() {
        val cal = Calendar.getInstance()
        val thisYear = cal.get(Calendar.YEAR) % 100
        val thisMonth = cal.get(Calendar.MONTH) + 1

        val years = (22..(thisYear + 1)).map { it.toString() }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spYear.adapter = yearAdapter

        val months = (1..12).map { "${it}月" }
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMonth.adapter = monthAdapter

        currentYear = thisYear
        currentMonth = thisMonth
        spYear.setSelection(years.indexOf(thisYear.toString()).coerceAtLeast(0))
        spMonth.setSelection(thisMonth - 1)

        spYear.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newYear = years[position].toInt()
                if (newYear != currentYear) {
                    currentYear = newYear
                    loadData()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        spMonth.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newMonth = position + 1
                if (newMonth != currentMonth) {
                    currentMonth = newMonth
                    loadData()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupFilterRadios() {
        val rgFilter = findViewById<android.widget.RadioGroup>(R.id.rg_filter)
        rgFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.rb_draft -> FILTER_DRAFT
                R.id.rb_submitted -> FILTER_SUBMITTED
                R.id.rb_all -> FILTER_ALL
                else -> FILTER_DRAFT
            }
            tvHint.visibility = if (currentFilter == FILTER_DRAFT) View.VISIBLE else View.GONE
            buildFlatList()
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
        val yy = String.format("%02d", currentYear)
        val mm = String.format("%02d", currentMonth)
        val ym = yy + mm

        Thread {
            try {
                val submitted = mutableListOf<ScriptItem>()
                val draft = mutableListOf<ScriptItem>()
                val depNameCache = mutableMapOf<String, String>()

                Db.withConnection { conn ->
                    val escDep = dep.replace("'", "''")
                    val sqlJt = if (level >= 3) {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND id_joined_dep = '$escDep' AND substr(id_joined, 1, 4) = '$ym'"
                    } else {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND substr(id_joined, 1, 4) = '$ym'"
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

                            // ★ 关键区别：用 level = 1（不是 level > 1）
                            val rsType = conn.query(
                                "SELECT type_name FROM topical_type WHERE topical_type = '$escLetter' AND $TOPICAL_TYPE_LEVEL_FILTER LIMIT 1"
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
                                departName = depName,
                                idJoinedDep = idJoinedDep
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

                    if (submitted.isEmpty() && draft.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvScripts.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rvScripts.visibility = View.VISIBLE
                        buildFlatList()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    isLoading = false
                    AlertDialog.Builder(this@KeyTopicActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    private fun buildFlatList() {
        val list = when (currentFilter) {
            FILTER_SUBMITTED -> submittedList
            FILTER_DRAFT -> draftList
            else -> submittedList + draftList
        }
        val grouped = list.groupBy { it.departName }
        val newFlat = mutableListOf<ScriptListEntry>()
        for ((depName, items) in grouped) {
            newFlat.add(ScriptListEntry(depName = depName, count = items.size))
            if (expandedGroups.contains(depName)) {
                for (item in items) {
                    newFlat.add(ScriptListEntry(scriptItem = item))
                }
            }
        }
        flatList = newFlat
        rvScripts.adapter = KeyTopicAdapter(newFlat) { item ->
            val intent = Intent(this@KeyTopicActivity, ScriptDetailActivity::class.java)
                .putExtra(ScriptDetailActivity.EXTRA_ID_COM, item.idCom)
                .putExtra(ScriptDetailActivity.EXTRA_ID_JOINED, item.idJoined)
                .putExtra(ScriptDetailActivity.EXTRA_ID_JOINED_LIST, item.idJoinedList)
                .putExtra(ScriptDetailActivity.EXTRA_TYPE_NAME, item.typeName ?: "")
                .putExtra(ScriptDetailActivity.EXTRA_IS_SUBMITTED, item.isSubmitted)
                .putExtra(ScriptDetailActivity.EXTRA_ID_DETAIL, item.idDetail)
                .putExtra(ScriptDetailActivity.EXTRA_ID_JOINED_DEP, item.idJoinedDep)
            startActivity(intent)
        }
    }

    private fun exportToExcel() {
        Toast.makeText(this, "重点选题暂不支持导出Excel，请使用脚本编辑页", Toast.LENGTH_SHORT).show()
    }

    inner class KeyTopicAdapter(
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
                h.tvCount.text = "${entry.count}"
                h.ivExpand.rotation = 0f
                h.card.setOnClickListener {
                    if (expandedGroups.contains(entry.depName)) {
                        expandedGroups.remove(entry.depName)
                    } else {
                        expandedGroups.add(entry.depName)
                    }
                    buildFlatList()
                }
            } else {
                val item = entry.scriptItem!!
                val h = holder as ItemVH
                h.tvScriptTitle.text = item.comTitle.ifEmpty { "[No Title]" }
                val displayTag = item.typeName ?: "Code: ${item.idJoinedList}"
                h.tvTypeTags.text = displayTag

                if (item.isSubmitted) {
                    h.statusIndicator.setBackgroundColor(0xFF1976D2.toInt())
                    h.tvStatus.text = "已提交"
                    h.tvStatus.setBackgroundColor(0xFF1976D2.toInt())
                    h.tvStatus.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    h.statusIndicator.setBackgroundColor(0xFFFF6F00.toInt())
                    h.tvStatus.text = "未提交"
                    h.statusIndicator.setBackgroundColor(0xFFFF6F00.toInt())
                    h.tvStatus.setTextColor(0xFFFFFFFF.toInt())
                }

                h.card.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount() = items.size
    }
}