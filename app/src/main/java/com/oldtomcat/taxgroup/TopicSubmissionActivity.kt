package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class TopicItem(
    val idCom: String,
    val comTitle: String,
    val comSummary: String,  // 主题内容
    val idDep: String,       // 报送单位
    val auditStatus: String  // "待审核" or "已审核"
) {
    // 从 id_com 第3-4位取月份 (id_com: yyMMddHHmmxxxxx)
    val month: String
        get() = if (idCom.length >= 4) idCom.substring(2, 4) else "--"
}

class TopicSubmissionActivity : AppCompatActivity() {

    private lateinit var rvTopics: RecyclerView
    private lateinit var tvEmpty: TextView

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_submission)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topic_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 主页：返回 HomeMenuActivity
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 新增选题
        findViewById<View>(R.id.btn_add).setOnClickListener {
            startActivity(android.content.Intent(this, TopicAddActivity::class.java))
        }

        // 查询编辑
        findViewById<View>(R.id.btn_search).setOnClickListener {
            startActivity(android.content.Intent(this, TopicQueryActivity::class.java))
        }

        // 刷新按钮
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadTopics()
            debugCheckAllData()
        }

        rvTopics = findViewById(R.id.rv_topics)
        rvTopics.layoutManager = LinearLayoutManager(this)
        tvEmpty = findViewById(R.id.tv_empty)

        loadTopics()
    }

    private fun loadTopics() {
        if (isLoading) return
        isLoading = true
        Thread {
            try {
                Db.withConnection { conn ->
                    val sql = "SELECT id_com, com_title, com_summary, id_dep, " +
                        "CASE WHEN vet = 0 OR vet IS NULL THEN '待审核' ELSE '已审核' END AS audit_status " +
                        "FROM commission_summary " +
                        "WHERE id_dep = '${MyApp.loginDeaprt}' " +
                        "ORDER BY id_com DESC LIMIT 10"
                    android.util.Log.d("TopicSub", "查询 SQL: $sql")
                    android.util.Log.d("TopicSub", "loginDeaprt='${MyApp.loginDeaprt}' (len=${MyApp.loginDeaprt.length})")
                    val rs = conn.query(sql)
                    val list = rs.toList().map { row ->
                        TopicItem(
                            idCom = row.get(0).toString(),
                            comTitle = row.get(1).toString().removeSurrounding("[", "]"),
                            comSummary = row.get(2).toString().removeSurrounding("[", "]"),
                            idDep = row.get(3).toString().removeSurrounding("[", "]"),
                            auditStatus = row.get(4).toString().removeSurrounding("[", "]")
                        )
                    }

                    android.util.Log.d("TopicSub", "查询到 ${list.size} 条记录")
                    runOnUiThread {
                        if (list.isEmpty()) {
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "无符合条件的数据 (id_dep=${MyApp.loginDeaprt})"
                        } else {
                            rvTopics.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            rvTopics.adapter = TopicAdapter(list) { item ->
                                showDetailDialog(item)
                            }
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TopicSubmissionActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun debugCheckAllData() {
        Thread {
            try {
                Db.withConnection { conn ->
                    val rs1 = conn.query("SELECT COUNT(*) FROM commission_summary")
                    val totalCount = rs1.toList()[0].get(0).toString()
                    android.util.Log.d("TopicSub", "commission_summary 总记录数: $totalCount")
                    val rs2 = conn.query("SELECT DISTINCT id_dep FROM commission_summary")
                    val distinctDeps = rs2.toList().map { it.get(0).toString().removeSurrounding("[","]") }
                    android.util.Log.d("TopicSub", "commission_summary 中存在的 id_dep: $distinctDeps")
                }
            } catch (e: Exception) {
                android.util.Log.e("TopicSub", "调试查询失败", e)
            }
        }.start()
    }

    // 展示详情弹窗（立体卡片风格）
    private fun showDetailDialog(item: TopicItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_topic_detail, null)
        view.findViewById<TextView>(R.id.tv_detail_title).text = item.comTitle
        view.findViewById<TextView>(R.id.tv_detail_content).text = item.comSummary

        // 先显示id_dep，同时查询部门名称
        val tvDepart = view.findViewById<TextView>(R.id.tv_detail_depart)
        tvDepart.text = item.idDep

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(view)
            .setCancelable(true)
            .create()

        // 设置窗口背景透明，让立体感更强
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes = attributes.apply {
                width = (resources.displayMetrics.widthPixels * 0.92).toInt()
            }
        }

        view.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        dialog.show()

        // 异步查询部门名称并更新
        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query(
                        "SELECT name_dep FROM Department WHERE id_dep='${item.idDep}'"
                    )
                    val rows = rs.toList()
                    val deptName = if (rows.isNotEmpty()) {
                        rows[0].get(0).toString().removeSurrounding("[", "]")
                    } else {
                        item.idDep // 查不到时回退显示代码值
                    }
                    runOnUiThread {
                        if (dialog.isShowing) {
                            tvDepart.text = deptName
                        }
                    }
                }
            } catch (e: Exception) {
                // 查询失败保持原样，不阻断弹窗
                android.util.Log.e("TopicSub", "查询部门名称失败", e)
            }
        }.start()
    }
}

// === Adapter ===
class TopicAdapter(
    private val items: List<TopicItem>,
    private val onClick: (TopicItem) -> Unit
) : RecyclerView.Adapter<TopicAdapter.VH>() {

    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topic_card, parent, false) as MaterialCardView
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.card.context

        // 月份 (id_com 第3-4位)
        holder.card.findViewById<TextView>(R.id.tv_month).text = "${item.month}月"

        // 标题
        holder.card.findViewById<TextView>(R.id.tv_title).text = item.comTitle

        // 状态
        val tvStatus = holder.card.findViewById<TextView>(R.id.tv_status)
        tvStatus.text = item.auditStatus
        val badge = tvStatus.background as GradientDrawable
        if (item.auditStatus == "已审核") {
            badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
        } else {
            badge.setColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
        }

        // 点击事件
        holder.card.findViewById<MaterialCardView>(R.id.card_root).setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount() = items.size
}