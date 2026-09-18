package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

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
    val departName: String = "",
    val idJoinedDep: String = "",
    val vetStatue: Int = 0
)

class ScriptEditActivity : AppCompatActivity() {

    private lateinit var tvHint: TextView
    private lateinit var tvSubmittedCount: TextView
    private lateinit var tvDraftCount: TextView
    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var rgFilter: RadioGroup
    private lateinit var rbDraft: RadioButton
    private lateinit var rbSubmitted: RadioButton
    private lateinit var rbAll: RadioButton
    private lateinit var btnExport: Button

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var currentFilter: Int = FILTER_DRAFT
    private var submittedList: List<ScriptItem> = emptyList()
    private var draftList: List<ScriptItem> = emptyList()
    private val flatList = mutableListOf<ScriptListEntry>()

    @Volatile
    private var isLoading = false
    @Volatile
    private var isExporting = false

    companion object {
        const val FILTER_DRAFT = 0
        const val FILTER_SUBMITTED = 1
        const val FILTER_ALL = 2
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

        tvHint = findViewById(R.id.tv_hint)
        tvSubmittedCount = findViewById(R.id.tv_submitted_count)
        tvDraftCount = findViewById(R.id.tv_draft_count)
        rvScripts = findViewById(R.id.rv_scripts)
        tvEmpty = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progress_bar)
        spYear = findViewById(R.id.sp_year)
        spMonth = findViewById(R.id.sp_month)
        rgFilter = findViewById(R.id.rg_filter)
        rbDraft = findViewById(R.id.rb_draft)
        rbSubmitted = findViewById(R.id.rb_submitted)
        rbAll = findViewById(R.id.rb_all)
        btnExport = findViewById(R.id.btn_export)

        rvScripts.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = Intent(this, HomeMenuActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { loadData() }

        setupFilters()

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

        val yearPos = years.indexOf(thisYear.toString())
        val monthPos = thisMonth - 1
        if (yearPos >= 0) spYear.setSelection(yearPos, false)
        spMonth.setSelection(monthPos, false)

        spYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newYear = years[position].toInt()
                if (newYear != currentYear) {
                    currentYear = newYear
                    loadData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newMonth = position + 1
                if (newMonth != currentMonth) {
                    currentMonth = newMonth
                    loadData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                    buildFlatList()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@ScriptEditActivity)
                        .setTitle("错误")
                        .setMessage("加载失败: ${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun buildFlatList  () {
        flatList.clear()

        val sourceItems = when (currentFilter) {
            FILTER_DRAFT -> draftList
            FILTER_SUBMITTED -> submittedList
            FILTER_ALL -> draftList + submittedList
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

        val grouped = sourceItems.groupBy { it.departName }.toSortedMap()
        for ((depName, items) in grouped) {
            flatList.add(ScriptListEntry(depName = depName, count = items.size))
            for (item in items) {
                flatList.add(ScriptListEntry(scriptItem = item))
            }
        }

        rvScripts.adapter = ScriptListAdapter(flatList) { item ->
            val intent = Intent(this@ScriptEditActivity, ScriptDetailActivity::class.java)
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
        if (isExporting) return
        if (submittedList.isEmpty() && draftList.isEmpty()) {
            Toast.makeText(this, "当前无数据可导出", Toast.LENGTH_SHORT).show()
            return
        }
        isExporting = true
        btnExport.isEnabled = false
        btnExport.text = "导出中..."

        val level = MyApp.loginDepLevel
        val dep = MyApp.loginDeaprt
        val yy = String.format("%02d", currentYear)
        val mm = String.format("%02d", currentMonth)
        val ym = yy + mm
        val timestamp = System.currentTimeMillis()
        val fileName = "脚本导出_${ym}_${timestamp}.csv"

        Thread {
            try {
                val csvContent = StringBuilder()
                csvContent.append("\uFEFF")
                csvContent.append("部门名称,选题名称,选题描述,选题形式,选题脚本\n")

                Db.withConnection { conn ->
                    val escDep = dep.replace("'", "''")
                    val sqlJt = if (level >= 2) {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND id_joined_dep = '$escDep' AND substr(id_joined, 1, 4) = '$ym'"
                    } else {
                        "SELECT id_joined, id_com, id_joined_dep, type_list FROM joined_topical " +
                        "WHERE ps = 1 AND substr(id_joined, 1, 4) = '$ym'"
                    }
                    val rsJt = conn.query(sqlJt)
                    val jtRows = rsJt.toList()
                    val depNameCache = mutableMapOf<String, String>()

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

                        val escIdCom = idCom.replace("'", "''")
                        val escIdDep = idJoinedDep.replace("'", "''")
                        val rsCs = conn.query("SELECT com_title, com_summary FROM commission_summary WHERE id_com = '$escIdCom' AND vet = 1")
                        val csRows = rsCs.toList()
                        val comTitle = if (csRows.isNotEmpty()) {
                            csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        } else {
                            ""
                        }
                        val comDesc = if (csRows.isNotEmpty()) {
                            csRows[0].get(1).toString().removeSurrounding("[", "]").trim()
                        } else {
                            ""
                        }

                        val letters = typeListStr.replace("\"", "").replace("'", "").replace("[", "").replace("]", "")
                            .trim().split("").filter { it.isNotBlank() }

                        for (letter in letters) {
                            val idJoinedList = idJoined + letter
                            val idDetailPrefix = idJoinedList.take(13)
                            val rsTd = conn.query(
                                "SELECT script FROM topical_detail WHERE id_detail LIKE '$idDetailPrefix%' AND id_joined_dep = '$escIdDep' LIMIT 1"
                            )
                            val tdRows = rsTd.toList()
                            val script = if (tdRows.isNotEmpty()) {
                                tdRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                ""
                            }

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
                            if (typeName.isNullOrEmpty()) continue

                            val scriptDisplay = if (script.isNotEmpty()) script else "脚本未编写"
                            csvContent.append("${escapeCsv(depName)},${escapeCsv(comTitle)},${escapeCsv(comDesc)},${escapeCsv(typeName)},${escapeCsv(scriptDisplay)}\n")
                        }
                    }
                }

                val exportsDir = java.io.File(getExternalFilesDir(null), "Exports")
                if (!exportsDir.exists()) exportsDir.mkdirs()
                val file = java.io.File(exportsDir, fileName)
                file.writeText(csvContent.toString(), Charsets.UTF_8)

                runOnUiThread {
                    Toast.makeText(this, "Export OK: " + file.absolutePath, Toast.LENGTH_LONG).show()
                    try {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/csv")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(openIntent)
                    } catch (e: Exception) {
                        try {
                            val uri = FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                file
                            )
                            val excelIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.ms-excel")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(excelIntent)
                        } catch (e2: Exception) {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    this,
                                    "${packageName}.fileprovider",
                                    file
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(sendIntent, "Open exported file"))
                            } catch (e3: Exception) {
                                Toast.makeText(this, "File: " + file.absolutePath, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Export Failed")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } finally {
                runOnUiThread {
                    isExporting = false
                    btnExport.isEnabled = true
                    btnExport.text = "Export Excel"
                }
            }
        }.start()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

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
                h.tvCount.text = "${entry.count}"
                h.ivExpand.rotation = 0f
                h.card.setOnClickListener(null)
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
                    h.tvStatus.setBackgroundColor(0xFFFF6F00.toInt())
                    h.tvStatus.setTextColor(0xFFFFFFFF.toInt())
                }

                h.card.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount() = items.size
    }
}
