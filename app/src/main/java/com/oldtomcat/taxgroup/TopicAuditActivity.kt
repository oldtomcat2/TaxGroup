package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// 待审核选题项（id_com 隐藏，用于跨页面传递）
data class AuditTopicItem(
    val idCom: String,       // 隐藏值，传参用
    val comTitle: String,    // 标题
    val month: String        // 月份（id_com 第3-4位）
)

// 部门分组数据
data class DepartmentGroup(
    val idDep: String,       // 部门ID
    val nameDep: String,     // 部门名称
    val topics: List<AuditTopicItem>,  // 该部门的待审核选题
    var isExpanded: Boolean = false    // 是否展开
)

class TopicAuditActivity : AppCompatActivity() {

    private lateinit var rvDepartments: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_audit)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.audit_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 刷新按钮
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadData()
        }

        // 主页按钮
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        rvDepartments = findViewById(R.id.rv_departments)
        rvDepartments.layoutManager = LinearLayoutManager(this)
        tvEmpty = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progress_bar)

        loadData()
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        rvDepartments.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 查询所有未审核选题，按部门分组
                    // vet = 0 或 NULL 表示未审核
                    val sql = "SELECT a.id_com, a.com_title, a.id_dep, b.name_dep " +
                        "FROM commission_summary a, Department b " +
                        "WHERE a.id_dep = b.id_dep AND (a.vet = 0 OR a.vet IS NULL) " +
                        "ORDER BY b.name_dep, a.id_com DESC"
                    val rs = conn.query(sql)
                    val rows = rs.toList()

                    // 按部门分组
                    val groupMap = mutableMapOf<String, MutableList<AuditTopicItem>>()
                    val depNameMap = mutableMapOf<String, String>()

                    for (row in rows) {
                        val idCom = row.get(0).toString().removeSurrounding("[", "]")
                        val comTitle = row.get(1).toString().removeSurrounding("[", "]")
                        val idDep = row.get(2).toString().removeSurrounding("[", "]")
                        val nameDep = row.get(3).toString().removeSurrounding("[", "]")

                        // 取月份（id_com 第3-4位）
                        val month = if (idCom.length >= 4) idCom.substring(2, 4) else "--"

                        val item = AuditTopicItem(idCom, comTitle, month)
                        if (!groupMap.containsKey(idDep)) {
                            groupMap[idDep] = mutableListOf()
                            depNameMap[idDep] = nameDep
                        }
                        groupMap[idDep]!!.add(item)
                    }

                    // 转为列表
                    val groups = groupMap.keys.sorted().map { idDep ->
                        DepartmentGroup(
                            idDep = idDep,
                            nameDep = depNameMap[idDep] ?: idDep,
                            topics = groupMap[idDep] ?: emptyList()
                        )
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        if (groups.isEmpty()) {
                            rvDepartments.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            rvDepartments.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            rvDepartments.adapter = DepartmentAdapter(groups) { item ->
                                openAuditDetail(item)
                            }
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@TopicAuditActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 点击条目进入审核详情页
    private fun openAuditDetail(item: AuditTopicItem) {
        val intent = android.content.Intent(this, TopicAuditDetailActivity::class.java)
        intent.putExtra("id_com", item.idCom)
        startActivity(intent)
    }
}

// === 部门分组 Adapter ===
class DepartmentAdapter(
    private val groups: List<DepartmentGroup>,
    private val onTopicClick: (AuditTopicItem) -> Unit
) : RecyclerView.Adapter<DepartmentAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val cardDepartment: MaterialCardView = view.findViewById(R.id.card_department)
        val ivExpand: ImageView = view.findViewById(R.id.iv_expand)
        val tvDepartmentName: TextView = view.findViewById(R.id.tv_department_name)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
        val llTopics: LinearLayout = view.findViewById(R.id.ll_topics)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_department_group, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val group = groups[position]
        val ctx = holder.view.context

        // 部门名称
        holder.tvDepartmentName.text = group.nameDep

        // 待审核数量
        holder.tvCount.text = "(${group.topics.size}条待审核)"

        // 展开/收缩状态
        updateExpandState(holder, group)

        // 点击部门卡片：展开/收缩
        holder.cardDepartment.setOnClickListener {
            group.isExpanded = !group.isExpanded
            updateExpandState(holder, group)
        }

        // 填充子选题列表
        holder.llTopics.removeAllViews()
        for (topic in group.topics) {
            val topicView = LayoutInflater.from(ctx)
                .inflate(R.layout.item_audit_topic, holder.llTopics, false)

            topicView.findViewById<TextView>(R.id.tv_month).text = "${topic.month}月"
            topicView.findViewById<TextView>(R.id.tv_title).text = topic.comTitle

            topicView.setOnClickListener { onTopicClick(topic) }
            holder.llTopics.addView(topicView)
        }
    }

    private fun updateExpandState(holder: VH, group: DepartmentGroup) {
        if (group.isExpanded) {
            holder.ivExpand.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            holder.llTopics.visibility = View.VISIBLE
        } else {
            holder.ivExpand.setImageResource(android.R.drawable.ic_menu_add)
            holder.llTopics.visibility = View.GONE
        }
    }

    override fun getItemCount() = groups.size
}
