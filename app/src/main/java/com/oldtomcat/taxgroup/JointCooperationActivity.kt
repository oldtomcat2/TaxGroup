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
    data class DepartmentHeader(val depName: String, val count: Int, val isExpanded: Boolean) : ListEntry()
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
    // 璁板綍姣忎釜閮ㄩ棬鐨勫睍寮€/鏀剁缉鐘舵€?Map<depName, Boolean>
    private val expandState = mutableMapOf<String, Boolean>()

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
                tvListTitle.text = "鍙ゅ弬涓洪€夐鍒楄〃"
            }
            MODE_JOINED -> {
                btnAvailable.setBackgroundResource(R.drawable.bg_topic_btn_gray)
                btnJoined.setBackgroundResource(R.drawable.bg_topic_btn_blue)
                tvListTitle.text = "鏌ヨ缁存姢鍒楄〃"
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

                    // 2. 閮ㄩ棬鍚嶆槧灏勶紙缂撳瓨閬垮厤閮ㄩ棬琛嶅璋冪敤 SQL锛?
                    val escLoginDep = MyApp.loginDeaprt.replace("'", "''")
                    val joinedList = mutableListOf<JointTopicItem>()
                    val availableList = mutableListOf<JointTopicItem>()
                    val depNameCache = mutableMapOf<String, String>()

                    for ((idCom, comTitle, pair) in allTopics) {
                        val (idDep, dateJoinEnd) = pair

                        // 鍒嗙粍 */
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
                        val isExpanded = expandState.getOrDefault(depName, true)
                        newFlatList.add(ListEntry.DepartmentHeader(depName, items.size, isExpanded))
                        if (isExpanded) {
                            for (it in items) newFlatList.add(ListEntry.Topic(it))
                        }
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
                                onTopicClick = { item -> onItemClick(item) },
                                onHeaderClick = { depName, expanded -> toggleDep(depName, expanded) }
                            )
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvCount.text = "鏌ヨ鍑洪敊"
                    AlertDialog.Builder(this@JointCooperationActivity)
                        .setTitle("閿欒")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("纭畦", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    private fun toggleDep(depName: String, currentExpanded: Boolean) {
        val newState = !currentExpanded
        expandState[depName] = newState
        // 閲嶅缓 flatList
        val headerIdx = flatList.indexOfFirst { it is ListEntry.DepartmentHeader && it.depName == depName }
        if (headerIdx < 0) return
        val header = flatList[headerIdx] as ListEntry.DepartmentHeader
        val newList = mutableListOf<ListEntry>()
        newList.add(header.copy(isExpanded = newState))
        if (newState) {
            // 鎻掑叆璇ラ儴闂ㄤ笅鐨勯€夐
            for (i in headerIdx + 1 until flatList.size) {
                if (flatList[i] is ListEntry.Topic) newList.add(flatList[i]) else break
            }
        }
        flatList.clear()
        flatList.addAll(newList)
        rvTopics.adapter?.notifyDataSetChanged()
    }

    private fun getTodayYyMMdd(): String {
        val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    private fun onItemClick(item: JointTopicItem) {
        val intent = android.content.Intent(this, JointDetailActivity::class.java)
        intent.putExtra("id_com", item.idCom)
        intent.putExtra("mode", currentMode) // 0=鍙ゅ弬涓? 1=鏌ヨ缁存姢
        startActivity(intent)
    }
}

// === Adapter ===
class JointTopicAdapter(
    private val items: List<ListEntry>,
    private val mode: Int,
    private val onTopicClick: (JointTopicItem) -> Unit,
    private val onHeaderClick: (depName: String, currentExpanded: Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TOPIC = 1
    }

    inner class HeaderVH(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        val tvName: TextView = card.findViewById(R.id.tv_department_name)
        val tvCount: TextView = card.findViewById(R.id.tv_count)
        val ivExpand: ImageView = card.findViewById(R.id.iv_expand)
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
            return HeaderVH(v.findViewById(R.id.card_department))
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
                h.ivExpand.rotation = if (entry.isExpanded) 45f else 0f
                h.card.setOnClickListener { onHeaderClick(entry.depName, entry.isExpanded) }
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
                h.card.findViewById<MaterialCardView>(R.id.card_root).setOnClickListener {
                    onTopicClick(item)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
