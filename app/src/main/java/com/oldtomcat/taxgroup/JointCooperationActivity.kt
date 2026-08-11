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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class JointTopicItem(
    val idCom: String,
    val comTitle: String,
    val departName: String,
    val deadline: String // 鏄剧ず鐢?yyyy-MM-dd
)

// 鍒嗙粍甯冨眬鐨勫钩鍖栬嚜瀹氫箟绫?
sealed class ListEntry {
    data class DepartmentHeader(val depName: String, val count: Int) : ListEntry()
    data class Topic(val item: JointTopicItem) : ListEntry()
}

class JointCooperationActivity : AppCompatActivity() {

    private lateinit var rvTopics: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvListTitle: TextView
    private lateinit var btnAvailable: Button
    private lateinit var btnJoined: Button
    private lateinit var progressBar: ProgressBar

    private var currentMode: Int = MODE_AVAILABLE // 0=鍙ゅ弬涓? 1=宸插弬涓?

    // 瀛樺偍鍒嗙粍鍚庣殑“扁平化”鍒楄〃
    private val flatList = mutableListOf<ListEntry>()

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

        // 涓や釜鍔熻兘鎸夐挳
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

    // 璇诲彇鍘熷鏁版嵁骞舵寜閮ㄩ棬鍒嗙粍
    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. load all topics
                    val today = getTodayYyMMdd()
                    val sqlBase = "SELECT id_com, com_title, id_dep, date_join_end FROM commission_summary " +
                        "WHERE vet = 1 AND date_join_end IS NOT NULL AND date_join_end > '$today' " +
                        "ORDER BY id_dep ASC, id_com DESC"

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

                    // 2. 部门名映射(缓存避免部门重复调用SQL)
                    val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
                    val joinedList = mutableListOf<JointTopicItem>()
                    val availableList = mutableListOf<JointTopicItem>()
                    val depNameCache = mutableMapOf<String, String>()

                    for ((idCom, comTitle, pair) in allTopics) {
                        val (idDep, dateJoinEnd) = pair

                        // 鍒嗙粍 */
//                        val escIdCom = idCom.replace("'", "''")
                        val escIdCom = idCom.replace("'", "''")
                        val joinSql = "SELECT 1 FROM joined_topical WHERE id_com = '$escIdCom' AND id_joined_dep = '$escLoginDep'"
                        val joinRs = conn.query(joinSql)
                        val isJoined = joinRs.toList().isNotEmpty()

                        val depName = depNameCache.getOrPut(idDep) {
                            val escIdDep = idDep.replace("'", "''")
                            val depRs = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escIdDep'")
                            val depRows = depRs.toList()
                            if (depRows.isNotEmpty()) {
                                depRows[0].get(0).toString().removeSurrounding("[", "]")
                            } else {
                                idDep
                            }
                        }

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
                        if (isJoined) joinedList.add(item) else availableList.add(item)
                    }

                    // 3. 鏍规嵁褰撳墠妯″紡鍒嗙粍骞跺钩鍖栬嚦 flatList
                    val sourceList = if (currentMode == MODE_AVAILABLE) availableList else joinedList
                    val grouped = sourceList.groupBy { it.departName }
                        .toSortedMap()
                    val newFlatList = mutableListOf<ListEntry>()
                    for ((depName, items) in grouped) {
                        newFlatList.add(ListEntry.DepartmentHeader(depName, items.size))
                        for (it in items) newFlatList.add(ListEntry.Topic(it))
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        flatList.clear()
                        flatList.addAll(newFlatList)
                        tvCount.text = "共 ${sourceList.size} 条"
                        if (flatList.isEmpty()) {
                            rvTopics.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "暂无记录"
                        } else {
                            rvTopics.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            rvTopics.adapter = JointTopicAdapter(flatList, currentMode,
                                onTopicClick = { item -> onItemClick(item) }
                            )
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
        intent.putExtra("mode", currentMode) // 0=可参与 1=查询维护
        startActivity(intent)
    }
}

// === Adapter ===
class JointTopicAdapter(
    private val items: List<ListEntry>,
    private val mode: Int,
    private val onTopicClick: (JointTopicItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TOPIC = 1
    }

    inner class HeaderVH(val root: android.widget.LinearLayout) : RecyclerView.ViewHolder(root) {
        val tvName: TextView = root.findViewById(R.id.tv_department_name)
        val tvCount: TextView = root.findViewById(R.id.tv_count)
        val ivExpand: ImageView = root.findViewById(R.id.iv_expand)
        val card: MaterialCardView = root.findViewById(R.id.card_department)
    }

    inner class TopicVH(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        val tvTitle: TextView = card.findViewById(R.id.tv_title)
        val tvDepart: TextView = card.findViewById(R.id.tv_department)
        val tvIdCom: TextView = card.findViewById(R.id.tv_id_com)
        val tvDeadline: TextView = card.findViewById(R.id.tv_deadline)
        val llDeadline: View = card.findViewById(R.id.ll_deadline)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListEntry.DepartmentHeader -> TYPE_HEADER
        is ListEntry.Topic -> TYPE_TOPIC
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_department_group, parent, false)
            return HeaderVH(v as android.widget.LinearLayout)
        } else {
            val v = inflater.inflate(R.layout.item_joint_topic, parent, false)
            return TopicVH(v as MaterialCardView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = items[position]) {
            is ListEntry.DepartmentHeader -> {
                val h = holder as HeaderVH
                h.tvName.text = "${entry.depName}"
                h.tvCount.text = "${entry.count} 条"
                h.ivExpand.rotation = 0f
                h.card.setOnClickListener(null)
            }
            is ListEntry.Topic -> {
                val item = entry.item
                val h = holder as TopicVH
                h.tvTitle.text = item.comTitle
                h.tvDepart.text = item.departName
                h.tvIdCom.text = item.idCom
                if (mode == JointCooperationActivity.MODE_AVAILABLE) {
                    h.llDeadline.visibility = View.VISIBLE
                    h.tvDeadline.text = item.deadline
                } else {
                    h.llDeadline.visibility = View.GONE
                }
                h.card.setOnClickListener {
                    onTopicClick(item)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
