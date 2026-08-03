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

    // 查询锁：防止 onCreate / onResume / 按钮 同时触发并发查询
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

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 顶部刷新按钮
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { doQuery() }

        // 顶部主页按钮
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 初始化控件
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

        // 设置月份下拉列表 (全部 + 1-12月)
        val months = mutableListOf("全部")
        for (i in 1..12) months.add("${i}月")
        spinnerMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        // 默认选中当前月份（位置 = 当前月份）
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        spinnerMonth.setSelection(currentMonth)  // position 1=1月, 7=7月

        // 默认全部选中
        cbAudited.isChecked = true
        cbUnaudited.isChecked = true
        cbRejected.isChecked = true

        // 查询按钮
        btnQuery.setOnClickListener { doQuery() }

        // 刷新按钮
        btnRefresh.setOnClickListener { doQuery() }

        // 初始加载：延迟到 onResume 中处理，避免和 onResume 重复查询
    }

    override fun onResume() {
        super.onResume()
        // 统一在这里查询：避免 onCreate + onResume 双重查询
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
                        "FROM commission_summary $whereSql ORDER BY id_com DESC"

                    val rs = conn.query(sql)
                    val list = rs.toList().map { row ->
                        TopicItem(
                            idCom = row.get(0).toString().removeSurrounding("[", "]"),
                            comTitle = row.get(1).toString().removeSurrounding("[", "]"),
                            comSummary = row.get(2).toString().removeSurrounding("[", "]"),
                            idDep = row.get(3).toString().removeSurrounding("[", "]"),
                            auditStatus = row.get(4).toString().removeSurrounding("[", "]")
                        )
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
                            rvTopics.adapter = TopicQueryAdapter(list) { item ->
                                openEditPage(item)
                            }
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

    // 点击记录：直接进入编辑页
    private fun openEditPage(item: TopicItem) {
        val intent = android.content.Intent(this, TopicEditActivity::class.java)
        intent.putExtra("id_com", item.idCom)
        startActivity(intent)
    }
}

// === Adapter ===
class TopicQueryAdapter(
    private val items: List<TopicItem>,
    private val onClick: (TopicItem) -> Unit
) : RecyclerView.Adapter<TopicQueryAdapter.VH>() {

    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topic_card, parent, false) as MaterialCardView
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.card.context

        // 月份
        holder.card.findViewById<TextView>(R.id.tv_month).text = "${item.month}月"

        // 标题
        holder.card.findViewById<TextView>(R.id.tv_title).text = item.comTitle

        // 状态颜色
        val tvStatus = holder.card.findViewById<TextView>(R.id.tv_status)
        tvStatus.text = item.auditStatus
        val badge = tvStatus.background as GradientDrawable
        when (item.auditStatus) {
            "已审核" -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
            "未通过" -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
            else -> badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
        }

        // 点击
        holder.card.findViewById<MaterialCardView>(R.id.card_root).setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount() = items.size
}