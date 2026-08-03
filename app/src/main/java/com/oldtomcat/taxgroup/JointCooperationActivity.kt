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
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class JointTopicItem(
    val idCom: String,
    val comTitle: String,
    val departName: String,
    val deadline: String // 显示用 yyyy-MM-dd
)

class JointCooperationActivity : AppCompatActivity() {

    private lateinit var rvTopics: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvListTitle: TextView
    private lateinit var btnAvailable: Button
    private lateinit var btnJoined: Button
    private lateinit var progressBar: ProgressBar

    private var currentMode: Int = MODE_AVAILABLE // 0=可参与, 1=已参与

    @Volatile
    private var isLoading = false

    companion object {
        const val MODE_AVAILABLE = 0
        const val MODE_JOINED = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joint_cooperation)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.joint_coop_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 初始化控件
        rvTopics = findViewById(R.id.rv_topics)
        tvEmpty = findViewById(R.id.tv_empty)
        tvCount = findViewById(R.id.tv_count)
        tvListTitle = findViewById(R.id.tv_list_title)
        btnAvailable = findViewById(R.id.btn_available)
        btnJoined = findViewById(R.id.btn_joined)
        progressBar = findViewById(R.id.progress_bar)

        rvTopics.layoutManager = LinearLayoutManager(this)

        // 顶部按钮
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.btn_refresh_top).setOnClickListener { loadData() }

        // 三个功能按钮
        btnAvailable.setOnClickListener {
            currentMode = MODE_AVAILABLE
            updateButtonStyle()
            loadData()
        }
        btnJoined.setOnClickListener {
            currentMode = MODE_JOINED
            updateButtonStyle()
            loadData()
        }

        // 初始加载
        updateButtonStyle()
        loadData()
    }

    private fun updateButtonStyle() {
        when (currentMode) {
            MODE_AVAILABLE -> {
                btnAvailable.setBackgroundResource(R.drawable.bg_topic_btn_blue)
                btnJoined.setBackgroundResource(R.drawable.bg_topic_btn_gray)
                tvListTitle.text = "可参与选题列表"
            }
            MODE_JOINED -> {
                btnAvailable.setBackgroundResource(R.drawable.bg_topic_btn_gray)
                btnJoined.setBackgroundResource(R.drawable.bg_topic_btn_blue)
                tvListTitle.text = "查询维护列表"
            }
        }
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 获取所有 date_join_end > 当前日期 且 vet=1(已通过审核) 的记录
                    val today = getTodayYyMMdd()
                    val sqlBase = "SELECT id_com, com_title, id_dep, date_join_end FROM commission_summary " +
                        "WHERE vet = 1 AND date_join_end IS NOT NULL AND date_join_end > '$today' " +
                        "ORDER BY id_com DESC"
                    
                    val rs = conn.query(sqlBase)
                    val allTopics = rs.toList().map { row ->
                        val idCom = row.get(0).toString().removeSurrounding("[", "]")
                        val comTitle = row.get(1).toString().removeSurrounding("[", "]")
                        val idDep = row.get(2).toString().removeSurrounding("[", "]")
                        val dateJoinEnd = row.get(3).toString().removeSurrounding("[", "]")
                        Triple(idCom, comTitle, idDep to dateJoinEnd)
                    }

                    if (allTopics.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "暂无记录"
                            tvCount.text = "共 0 条"
                            isLoading = false
                        }
                        return@withConnection
                    }

                    // 2. 分类：已参与 vs 可参与
                    val joinedList = mutableListOf<JointTopicItem>()
                    val availableList = mutableListOf<JointTopicItem>()

                    for ((idCom, comTitle, pair) in allTopics) {
                        val (idDep, dateJoinEnd) = pair
                        val escIdCom = idCom.replace("'", "''")
                        val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
                        
                        // 查询 joined_topical 表
                        val joinSql = "SELECT 1 FROM joined_topical WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                        val joinRs = conn.query(joinSql)
                        val isJoined = joinRs.toList().isNotEmpty()

                        // 查询出题部门名称
                        val escIdDep = idDep.replace("'", "''")
                        val depRs = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep'")
                        val depRows = depRs.toList()
                        val depName = if (depRows.isNotEmpty()) {
                            depRows[0].get(0).toString().removeSurrounding("[", "]")
                        } else {
                            idDep
                        }

                        // 转换日期格式 yyMMdd -> yyyy-MM-dd
                        val displayDate = try {
                            if (dateJoinEnd.length == 6) {
                                val year = "20${dateJoinEnd.substring(0, 2)}"
                                val month = dateJoinEnd.substring(2, 4)
                                val day = dateJoinEnd.substring(4, 6)
                                "$year-$month-$day"
                            } else {
                                dateJoinEnd
                            }
                        } catch (_: Exception) {
                            dateJoinEnd
                        }

                        val item = JointTopicItem(idCom, comTitle, depName, displayDate)
                        
                        if (isJoined) {
                            joinedList.add(item)
                        } else {
                            availableList.add(item)
                        }
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        
                        val displayList = if (currentMode == MODE_AVAILABLE) availableList else joinedList
                        
                        tvCount.text = "共 ${displayList.size} 条"
                        
                        if (displayList.isEmpty()) {
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "暂无记录"
                        } else {
                            rvTopics.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            rvTopics.adapter = JointTopicAdapter(displayList, currentMode) { item ->
                                onItemClick(item)
                            }
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvCount.text = "查询出错"
                    AlertDialog.Builder(this@JointCooperationActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun getTodayYyMMdd(): String {
        val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    private fun onItemClick(item: JointTopicItem) {
        val intent = android.content.Intent(this, JointDetailActivity::class.java)
        intent.putExtra("id_com", item.idCom)
        intent.putExtra("mode", currentMode) // 0=可参与, 1=查询维护
        startActivity(intent)
    }

    private fun doJoinTopic(item: JointTopicItem) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdCom = item.idCom.replace("'", "''")
                    val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
                    val escLoginId = MyApp.loginId.replace("'", "''")
                    
                    // 插入 joined_topical 表
                    val insertSql = "INSERT INTO joined_topical (id_com, id_dep, join_user, join_date) VALUES (" +
                        "'$escIdCom', '$escLoginDep', '$escLoginId', datetime('now'))"
                    conn.execute(insertSql)
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@JointCooperationActivity)
                        .setTitle("成功")
                        .setMessage("已成功加入选题")
                        .setPositiveButton("确定") { _, _ -> loadData() }
                        .show()
                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@JointCooperationActivity)
                        .setTitle("错误")
                        .setMessage("加入失败：${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }
}

// === Adapter ===
class JointTopicAdapter(
    private val items: List<JointTopicItem>,
    private val mode: Int,
    private val onClick: (JointTopicItem) -> Unit
) : RecyclerView.Adapter<JointTopicAdapter.VH>() {

    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_joint_topic, parent, false) as MaterialCardView
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        holder.card.findViewById<TextView>(R.id.tv_title).text = item.comTitle
        holder.card.findViewById<TextView>(R.id.tv_department).text = item.departName
        holder.card.findViewById<TextView>(R.id.tv_id_com).text = item.idCom
        
        // 截止日期显示
        val llDeadline = holder.card.findViewById<View>(R.id.ll_deadline)
        if (mode == JointCooperationActivity.MODE_AVAILABLE) {
            llDeadline.visibility = View.VISIBLE
            holder.card.findViewById<TextView>(R.id.tv_deadline).text = item.deadline
        } else {
            llDeadline.visibility = View.GONE
        }

        holder.card.findViewById<MaterialCardView>(R.id.card_root).setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount() = items.size
}
