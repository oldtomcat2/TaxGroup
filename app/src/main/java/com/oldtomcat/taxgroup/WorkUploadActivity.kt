package com.oldtomcat.taxgroup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class WorkUploadActivity : AppCompatActivity() {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var rgStatus: RadioGroup
    private lateinit var rvWorks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: ProgressBar

    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth: Int = 0  // 0 表示全部月份
    private var isUploaded: Boolean = false  // false=未上传，true=已上传

    private var depNameCache = mutableMapOf<String, String>()
    private val items = mutableListOf<WorkListEntry>()
    private lateinit var adapter: WorkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_upload)

        findViewById<android.widget.Button>(R.id.btn_back).setOnClickListener { finish() }

        spYear = findViewById(R.id.sp_year)
        spMonth = findViewById(R.id.sp_month)
        rgStatus = findViewById(R.id.rg_status)
        rvWorks = findViewById(R.id.rv_works)
        tvEmpty = findViewById(R.id.tv_empty)
        progress = findViewById(R.id.progress)

        // 年份：从 2020 到当前年+1
        val years = mutableListOf<String>()
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        for (y in 2020..thisYear + 1) years.add(y.toString())
        spYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spYear.setSelection(years.indexOf(currentYear.toString()).coerceAtLeast(0))
        spYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentYear = years[position].toIntOrNull() ?: currentYear
                reload()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 月份：全部 + 1~12
        val months = mutableListOf("全部月份")
        for (m in 1..12) months.add(m.toString().padStart(2, '0'))
        spMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        spMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMonth = if (position == 0) 0 else position
                reload()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // tab 切换
        rgStatus.setOnCheckedChangeListener { _, checkedId ->
            isUploaded = (checkedId == R.id.rb_sent)
            reload()
        }

        adapter = WorkAdapter()
        rvWorks.layoutManager = LinearLayoutManager(this)
        rvWorks.adapter = adapter

        reload()
    }

    private fun reload() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvWorks.visibility = View.GONE

        Thread {
            try {
                val level = MyApp.loginDepLevel
                val dep = MyApp.loginDeaprt
                val yy = String.format("%02d", currentYear % 100)
                val mm = if (currentMonth == 0) "" else String.format("%02d", currentMonth)
                val ymPrefix = yy + mm

                val loaded = mutableListOf<WorkListEntry>()
                val depCache = mutableMapOf<String, String>()

                Db.withConnection { conn ->
                    // 构造 topical_detail 条件
                    val titleCond = if (isUploaded) {
                        "AND td.final_title IS NOT NULL AND td.final_title != ''"
                    } else {
                        "AND (td.final_title IS NULL OR td.final_title = '')"
                    }
                    val ymCond = if (currentMonth == 0) {
                        "AND substr(td.id_detail, 1, 2) = '$yy'"
                    } else {
                        "AND substr(td.id_detail, 1, 4) = '$ymPrefix'"
                    }
                    val depCond = if (level > 2) {
                        val escDep = dep.replace("'", "''")
                        "AND td.id_joined_dep = '$escDep'"
                    } else ""

                    val sql = """
                        SELECT td.id_detail, td.id_joined_dep, td.vet_statue, td.final_title,
                               jt.id_com, cs.com_title
                        FROM topical_detail td
                        INNER JOIN joined_topical jt ON substr(td.id_detail, 1, 12) = jt.id_joined
                        INNER JOIN commission_summary cs ON jt.id_com = cs.id_com
                        WHERE td.vet_statue = 1
                          $titleCond
                          $ymCond
                          $depCond
                        ORDER BY td.id_joined_dep ASC, td.id_detail ASC
                    """.trimIndent()

                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    // 按 id_joined_dep 分组
                    val grouped = linkedMapOf<String, MutableList<WorkItem>>()
                    for (row in rows) {
                        val idDetail = row.get(0).toString().removeSurrounding("[", "]").trim()
                        val idJoinedDep = row.get(1).toString().removeSurrounding("[", "]").trim()
                        // row index: 0=id_detail, 1=id_joined_dep, 2=vet_statue, 3=final_title, 4=id_com, 5=com_title
                        val finalTitle = row.get(3).toString().removeSurrounding("[", "]").trim()
                        val idCom = row.get(4).toString().removeSurrounding("[", "]").trim()
                        val comTitle = row.get(5).toString().removeSurrounding("[", "]").trim()

                        // 部门名查询
                        val depName = if (idJoinedDep.isNotEmpty()) {
                            if (depCache.containsKey(idJoinedDep)) {
                                depCache[idJoinedDep]!!
                            } else {
                                val escD = idJoinedDep.replace("'", "''")
                                val rsDep = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escD' LIMIT 1")
                                val depRows = rsDep.toList()
                                val name = if (depRows.isNotEmpty()) {
                                    depRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                                } else idJoinedDep
                                depCache[idJoinedDep] = name
                                name
                            }
                        } else "未分类"

                        val item = WorkItem(
                            idDetail = idDetail,
                            idJoinedDep = idJoinedDep,
                            idCom = idCom,
                            comTitle = comTitle.ifEmpty { "[无标题]" },
                            finalTitle = finalTitle,
                            depName = depName
                        )
                        grouped.getOrPut(depName) { mutableListOf() }.add(item)
                    }

                    // 展开为 header + item 列表
                    for ((depName, list) in grouped) {
                        loaded.add(WorkListEntry(header = depName, count = list.size))
                        for (it in list) loaded.add(WorkListEntry(item = it))
                    }
                }

                runOnUiThread {
                    items.clear()
                    items.addAll(loaded)
                    adapter.notifyDataSetChanged()
                    progress.visibility = View.GONE
                    if (items.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvWorks.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rvWorks.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    Toast.makeText(this@WorkUploadActivity, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    data class WorkItem(
        val idDetail: String,
        val idJoinedDep: String,
        val idCom: String,
        val comTitle: String,
        val finalTitle: String,
        val depName: String
    )

    data class WorkListEntry(
        val header: String? = null,
        val count: Int = 0,
        val item: WorkItem? = null
    ) {
        val isHeader: Boolean get() = item == null
    }

    inner class WorkAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_HEADER = 0
        private val TYPE_ITEM = 1

        override fun getItemViewType(position: Int): Int =
            if (items[position].isHeader) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(inflater.inflate(R.layout.item_dep_header, parent, false) as LinearLayout)
            } else {
                ItemVH(inflater.inflate(R.layout.item_work_upload, parent, false) as com.google.android.material.card.MaterialCardView)
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entry = items[position]
            if (holder is HeaderVH) {
                holder.tvDepHeader.text = entry.header ?: ""
                holder.tvDepCount.text = "${entry.count} 条"
            } else if (holder is ItemVH) {
                val item = entry.item!!
                holder.tvTitle.text = item.comTitle
                // 仅显示选题标题；id_com/id_detail 作为隐藏值通过 Intent 传给编辑页
                holder.tvSubtitle.visibility = View.GONE
                holder.card.setOnClickListener {
                    val intent = android.content.Intent(
                        this@WorkUploadActivity,
                        WorkUploadEditActivity::class.java
                    )
                    intent.putExtra(WorkUploadEditActivity.EXTRA_ID_COM, item.idCom)
                    intent.putExtra(WorkUploadEditActivity.EXTRA_ID_DETAIL, item.idDetail)
                    startActivity(intent)
                }
            }
        }

        inner class HeaderVH(v: LinearLayout) : RecyclerView.ViewHolder(v) {
            val tvDepHeader: TextView = v.findViewById(R.id.tv_dep_header)
            val tvDepCount: TextView = v.findViewById(R.id.tv_dep_count)
        }

        inner class ItemVH(val card: com.google.android.material.card.MaterialCardView) : RecyclerView.ViewHolder(card) {
            val tvTitle: TextView = card.findViewById(R.id.tv_title)
            val tvSubtitle: TextView = card.findViewById(R.id.tv_subtitle)
        }
    }
}