package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

// 扁平化列表项：部门标题 或 选题记录
data class TopicQueryEntry(
    val depName: String = "",
    val count: Int = 0,
    val topic: TopicItem? = null
) {
    val isHeader: Boolean get() = topic == null
}

class TopicQueryActivity : AppCompatActivity() {

    private lateinit var rvTopics: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var cbAudited: CheckBox
    private lateinit var cbUnaudited: CheckBox
    private lateinit var cbRejected: CheckBox
    private lateinit var spinnerMonth: Spinner
    private lateinit var btnQuery: Button
    private lateinit var btnRefresh: ImageButton
    private lateinit var progressBar: ProgressBar

    private val flatList = mutableListOf<TopicQueryEntry>()

    @Volatile
    private var isQuerying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_query)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topic_query_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { doQuery() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        cbAudited = findViewById(R.id.cb_audited)
        cbUnaudited = findViewById(R.id.cb_unaudited)
        cbRejected = findViewById(R.id.cb_rejected)
        spinnerMonth = findViewById(R.id.spinner_month)
        btnQuery = findViewById(R.id.btn_query)
        btnRefresh = findViewById(R.id.btn_refresh)
        rvTopics = findViewById(R.id.rv_topics)
        tvEmpty = findViewById(R.id.tv_empty)
        tvCount = findViewById(R.id.tv_count)
        progressBar = findViewById(R.id.progress_bar)

        rvTopics.layoutManager = LinearLayoutManager(this)

        val months = mutableListOf("全部")
        for (i in 1..12) months.add("${i}月")
        spinnerMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        spinnerMonth.setSelection(currentMonth)

        cbAudited.isChecked = true
        cbUnaudited.isChecked = true
        cbRejected.isChecked = true

        btnQuery.setOnClickListener { doQuery() }
        btnRefresh.setOnClickListener { doQuery() }
    }

    override fun onResume() {
        super.onResume()
        if (::rvTopics.isInitialized) {
            doQuery()
        }
    }

    private fun doQuery() {
        if (isQuerying) return
        isQuerying = true
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val spinnerPos = spinnerMonth.selectedItemPosition
        val checkedAudited = cbAudited.isChecked
        val checkedUnaudited = cbUnaudited.isChecked
        val checkedRejected = cbRejected.isChecked

        Thread {
            try {
                Db.withConnection { conn ->
                    val whereClauses = mutableListOf<String>()

                    val isAdmin = MyApp.loginDeaprt == "080001"
                    if (!isAdmin) {
                        whereClauses.add("id_dep = '${MyApp.loginDeaprt}'")
                    }

                    if (spinnerPos > 0) {
                        val monthValue = String.format("%02d", spinnerPos)
                        whereClauses.add("SUBSTR(id_com, 3, 2) = '$monthValue'")
                    }

                    val statusClauses = mutableListOf<String>()
                    if (checkedAudited) statusClauses.add("vet = 1")
                    if (checkedUnaudited) statusClauses.add("(vet = 0 OR vet IS NULL)")
                    if (checkedRejected) statusClauses.add("vet = 2")

                    if (statusClauses.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "请至少选择一个审核状态"
                            tvCount.text = "共 0 条"
                            isQuerying = false
                        }
                        return@withConnection
                    }
                    whereClauses.add("(${statusClauses.joinToString(" OR ")})")

                    val whereSql = "WHERE ${whereClauses.joinToString(" AND ")}"
                    val sql = "SELECT id_com, com_title, com_summary, id_dep, " +
                        "CASE WHEN vet = 1 THEN '已审核' WHEN vet = 2 THEN '未通过' ELSE '待审核' END AS audit_status " +
                        "FROM commission_summary $whereSql ORDER BY id_dep ASC, id_com DESC"

                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    if (rows.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            tvCount.text = "共 0 条"
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "无符合条件的数据"
                            isQuerying = false
                        }
                        return@withConnection
                    }

                    // 缓存部门名（避免重复查询）
                    val depNameCache = mutableMapOf<String, String>()

                    val list = rows.map { row ->
                        val idDep = row.get(3).toString().removeSurrounding("[", "]").trim()
                        val depName = depNameCache.getOrPut(idDep) {
                            val escIdDep = idDep.replace("'", "''")
                            val rsDep = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep' LIMIT 1")
                            val depRows = rsDep.toList()
                            if (depRows.isNotEmpty()) {
                                depRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            } else {
                                idDep
                            }
                        }
                        TopicItem(
                            idCom = row.get(0).toString().removeSurrounding("[", "]"),
                            comTitle = row.get(1).toString().removeSurrounding("[", "]"),
                            comSummary = row.get(2).toString().removeSurrounding("[", "]"),
                            idDep = idDep,
                            auditStatus = row.get(4).toString().removeSurrounding("[", "]")
                        ).also {
                            // 临时把 depName 存到一个 map（用 idDep 作为 key 关联）
                            // 因为 TopicItem 没有 depName 字段，我们用 idDepMap 维护映射
                            idDepMap[it.idCom] = depName
                        }
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvCount.text = "共 ${list.size} 条"

                        if (list.isEmpty()) {
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "无符合条件的数据"
                        } else {
                            rvTopics.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            buildFlatList(list)
                        }
                        isQuerying = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvCount.text = "查询出错"
                    AlertDialog.Builder(this@TopicQueryActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isQuerying = false
                }
            }
        }.start()
    }

    // 临时映射：idCom -> depName（因为 TopicItem 没有 depName 字段）
    private val idDepMap = mutableMapOf<String, String>()

    private fun buildFlatList(list: List<TopicItem>) {
        flatList.clear()

        // 按部门名分组（排序）
        val grouped = list.groupBy { idDepMap[it.idCom] ?: it.idDep }.toSortedMap()
        for ((depName, items) in grouped) {
            flatList.add(TopicQueryEntry(depName = depName, count = items.size))
            for (item in items) {
                flatList.add(TopicQueryEntry(topic = item))
            }
        }

        rvTopics.adapter = TopicQueryListAdapter(flatList) { item ->
            val intent = android.content.Intent(this, TopicEditActivity::class.java)
            intent.putExtra("id_com", item.idCom)
            startActivity(intent)
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}

// === 扁平化 Adapter ===
class TopicQueryListAdapter(
    private val items: List<TopicQueryEntry>,
    private val onClick: (TopicItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class HeaderVH(val root: android.widget.LinearLayout) : RecyclerView.ViewHolder(root) {
        val tvName: TextView = root.findViewById(R.id.tv_department_name)
        val tvCount: TextView = root.findViewById(R.id.tv_count)
        val ivExpand: ImageView = root.findViewById(R.id.iv_expand)
        val card: MaterialCardView = root.findViewById(R.id.card_department)
    }

    inner class ItemVH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun getItemViewType(position: Int): Int =
        if (items[position].isHeader) TopicQueryActivity.TYPE_HEADER else TopicQueryActivity.TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TopicQueryActivity.TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_department_group, parent, false)
            HeaderVH(v as android.widget.LinearLayout)
        } else {
            val v = inflater.inflate(R.layout.item_topic_card, parent, false)
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
            val item = entry.topic!!
            val h = holder as ItemVH
            val ctx = h.card.context

            h.card.findViewById<TextView>(R.id.tv_month).text = "${item.month}月"
            h.card.findViewById<TextView>(R.id.tv_title).text = item.comTitle

            val tvStatus = h.card.findViewById<TextView>(R.id.tv_status)
            tvStatus.text = item.auditStatus
            val badge = tvStatus.background as GradientDrawable
            when (item.auditStatus) {
                "已审核" -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
                "未通过" -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
                else -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
            }

            h.card.findViewById<MaterialCardView>(R.id.card_root).setOnClickListener {
                onClick(item)
            }
        }
    }

    override fun getItemCount() = items.size
}