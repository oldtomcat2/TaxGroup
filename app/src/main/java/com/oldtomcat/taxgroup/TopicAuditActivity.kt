package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// 待审核选题项(id_com 隐藏,用于跨页面传递)
data class AuditTopicItem(
    val idCom: String,       // 隐藏值,传参用(业务审核模式下:join_topical.id_com)
    val comTitle: String,    // 标题
    val month: String,       // 月份(id_com 第3-4位)
    val idDep: String = "",  // 部门ID(脚本审核用,赋值前为"")
    val vetStatus: Int = Int.MIN_VALUE,  // dep_vet.vet,不适用时取 Int.MIN_VALUE
    val idDetail: String = "",  // 脚本详情用 ID(topical_detail.id_detail)
    val idJoinedDep: String = ""  // 报送单位 ID(topical_detail.id_joined_dep)
)

// 部门分组数据
data class DepartmentGroup(
    val idDep: String,       // 部门ID
    val nameDep: String,     // 部门名称
    val topics: List<AuditTopicItem>,  // 该部门的待审核项
    var isExpanded: Boolean = false,    // 是否展开
    val isScriptAudit: Boolean = false, // 是否脚本审核模式(用于决定点击行为)
    val isDeptAudit: Boolean = false    // 是否部门审核模式(level>1 业务科室)
)

// Tab 类型
enum class AuditTab { TOPIC, SCRIPT }

// 脚本审核月内模式
enum class ScriptAuditMode { PROMOTE, BUSINESS }

// vet 状态筛选(level>1 适用)
enum class VetFilter { ALL, PASSED, REJECTED, PENDING }

class TopicAuditActivity : AppCompatActivity() {

    private lateinit var rvDepartments: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnTabTopic: Button
    private lateinit var btnTabScript: Button
    private lateinit var tvHint: TextView
    private lateinit var llAuditFilter: LinearLayout
    private lateinit var rgAuditMode: RadioGroup
    private lateinit var rbAuditPromote: RadioButton
    private lateinit var rbAuditBusiness: RadioButton
    private lateinit var rgVetStatus: RadioGroup
    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner

    // 查询锁:防并发访问数据库(HTTP)
    @Volatile
    private var isLoading = false

    // 当前 Tab
    private var currentTab: AuditTab = AuditTab.TOPIC

    // 脚本审核模式下选中的年/月
    private var currentYear: String = "26"
    private var currentMonth: String = "01"
    private var currentAuditMode: ScriptAuditMode = ScriptAuditMode.PROMOTE
    private var currentVetFilter: VetFilter = VetFilter.ALL

    // 年份范围("全部" + 年后两位)
    private val yearList = listOf("全部", "22", "23", "24", "25", "26","27")
    private val monthList = listOf("全部", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")

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
        btnTabTopic = findViewById(R.id.btn_tab_topic)
        btnTabScript = findViewById(R.id.btn_tab_script)
        tvHint = findViewById(R.id.tv_hint)
        llAuditFilter = findViewById(R.id.ll_audit_filter)
        rgAuditMode = findViewById(R.id.rg_audit_mode)
        rbAuditPromote = findViewById(R.id.rb_audit_promote)
        rbAuditBusiness = findViewById(R.id.rb_audit_business)
        rgVetStatus = findViewById(R.id.rg_vet_status)
        spYear = findViewById(R.id.sp_year)
        spMonth = findViewById(R.id.sp_month)

        // 初始化 Spinner
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spYear.adapter = yearAdapter
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthList)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMonth.adapter = monthAdapter

        // 默认:年份=当前年后两位、月份=当前月
        val (defYear, defMonth) = currentYearMonth()
        currentYear = defYear
        currentMonth = defMonth
        spYear.setSelection(yearList.indexOf(defYear).coerceAtLeast(0))
        spMonth.setSelection(monthList.indexOf(defMonth).coerceAtLeast(0))

        // 监听
        rgAuditMode.setOnCheckedChangeListener { _, checkedId ->
            currentAuditMode = if (checkedId == R.id.rb_audit_business)
                ScriptAuditMode.BUSINESS else ScriptAuditMode.PROMOTE
            loadData()
        }
        rgVetStatus.setOnCheckedChangeListener { _, checkedId ->
            currentVetFilter = when (checkedId) {
                R.id.rb_vet_pass -> VetFilter.PASSED
                R.id.rb_vet_reject -> VetFilter.REJECTED
                R.id.rb_vet_pending -> VetFilter.PENDING
                else -> VetFilter.ALL
            }
            loadData()
        }
        spYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newYear = yearList[position]
                if (newYear != currentYear) {
                    currentYear = newYear
                    loadData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newMonth = monthList[position]
                if (newMonth != currentMonth) {
                    currentMonth = newMonth
                    loadData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 部门审核(业务科室,level>1):将"脚本审核"Tab 动态改名为"部门审核"
        btnTabScript.text = if (MyApp.loginDepLevel > 1) "部门审核" else "脚本审核"

        // Tab 切换
        btnTabTopic.setOnClickListener {
            if (currentTab != AuditTab.TOPIC) {
                currentTab = AuditTab.TOPIC
                updateTabStyle()
                loadData()
            }
        }
        btnTabScript.setOnClickListener {
            if (currentTab != AuditTab.SCRIPT) {
                currentTab = AuditTab.SCRIPT
                updateTabStyle()
                loadData()
            }
        }

        updateTabStyle()
        loadData()
    }

    private fun updateTabStyle() {
        when (currentTab) {
            AuditTab.TOPIC -> {
                btnTabTopic.setBackgroundColor(android.graphics.Color.parseColor("#1976D2"))
                btnTabTopic.setTextColor(android.graphics.Color.WHITE)
                btnTabScript.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                btnTabScript.setTextColor(android.graphics.Color.parseColor("#666666"))
                tvHint.visibility = View.VISIBLE
                tvHint.text = "点击部门名称展开/收缩列表"
                llAuditFilter.visibility = View.GONE
            }
            AuditTab.SCRIPT -> {
                btnTabScript.setBackgroundColor(android.graphics.Color.parseColor("#1976D2"))
                btnTabScript.setTextColor(android.graphics.Color.WHITE)
                btnTabTopic.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                btnTabTopic.setTextColor(android.graphics.Color.parseColor("#666666"))
                tvHint.visibility = View.GONE
                llAuditFilter.visibility = View.VISIBLE
                // level>1(部门审核 Tab)下:用 vet 状态筛选代替宣传/业务
                if (MyApp.loginDepLevel > 1) {
                    rgAuditMode.visibility = View.GONE
                    rgVetStatus.visibility = View.VISIBLE
                } else {
                    rgAuditMode.visibility = View.VISIBLE
                    rgVetStatus.visibility = View.GONE
                }
            }
        }
    }

    // 获取当前年(后两位)和月(两位)
    private fun currentYearMonth(): Pair<String, String> {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR) % 100
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val yy = year.toString().padStart(2, '0')
        val mm = month.toString().padStart(2, '0')
        return if (yearList.contains(yy)) yy to mm else yearList.last() to "01"
    }

    /**
     * 构建年月过滤 SQL 子句:" AND substr(id_detail, 1, 4) = 'YYMM' "
     * - 年="全部" 且 月≠"全部" → 只按月
     * - 年≠"全部" 且 月="全部" → 只按年(前2位)
     * - 年≠"全部" 且 月≠"全部" → 年+月(前4位)
     * - 均为"全部" → 空字符串(不过滤)
     */
    private fun buildYmFilter(col: String = "id_detail"): String {
        return when {
            currentYear == "全部" && currentMonth == "全部" -> ""
            currentYear == "全部" -> " AND substr($col, 3, 2) = '$currentMonth'"
            currentMonth == "全部" -> " AND substr($col, 1, 2) = '$currentYear'"
            else -> " AND substr($col, 1, 4) = '$currentYear$currentMonth'"
        }
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
                    val groups = when (currentTab) {
                        AuditTab.TOPIC -> loadTopicAuditGroups(conn)
                        AuditTab.SCRIPT -> {
                            // level>1 在"部门审核"Tab下,vet 单选组代替宣传/业务,必须走 BUSINESS
                            val mode = if (MyApp.loginDepLevel > 1)
                                ScriptAuditMode.BUSINESS else currentAuditMode
                            when (mode) {
                                ScriptAuditMode.PROMOTE -> loadPromoteAuditGroups(conn)
                                ScriptAuditMode.BUSINESS -> loadBusinessAuditGroups(conn)
                            }
                        }
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        if (groups.isEmpty()) {
                            rvDepartments.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = when {
                                currentTab == AuditTab.TOPIC -> "暂无待审核选题"
                                currentAuditMode == ScriptAuditMode.BUSINESS -> "暂无该月业务审核记录"
                                MyApp.loginDepLevel > 1 -> "暂无待审核业务"
                                else -> "暂无待审核脚本"
                            }
                        } else {
                            rvDepartments.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            rvDepartments.adapter = DepartmentAdapter(groups) { item ->
                                if (currentTab == AuditTab.TOPIC) {
                                    openAuditDetail(item)
                                } else {
                                    openScriptAuditDetail(item)
                                }
                            }
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    val emsg = "${e.javaClass.simpleName}\n${e.message}"
                    AlertDialog.Builder(this@TopicAuditActivity)
                        .setTitle("错误")
                        .setMessage("加载失败:$emsg")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 选题审核:vet=0/null 的 commission_summary,按 id_dep 分组
    private fun loadTopicAuditGroups(conn: Db.Conn): List<DepartmentGroup> {
        val sql = "SELECT a.id_com, a.com_title, a.id_dep, b.name_dep " +
            "FROM commission_summary a, Department b " +
            "WHERE a.id_dep = b.id_dep AND (a.vet = 0 OR a.vet IS NULL) " +
            "ORDER BY b.name_dep, a.id_com DESC"
        val rs = conn.query(sql)
        val rows = rs.toList()

        val groupMap = mutableMapOf<String, MutableList<AuditTopicItem>>()
        val depNameMap = mutableMapOf<String, String>()

        for (row in rows) {
            val idCom = row.get(0).toString().removeSurrounding("[", "]")
            val comTitle = row.get(1).toString().removeSurrounding("[", "]")
            val idDep = row.get(2).toString().removeSurrounding("[", "]")
            val nameDep = row.get(3).toString().removeSurrounding("[", "]")

            val month = if (idCom.length >= 4) idCom.substring(2, 4) else "--"

            val item = AuditTopicItem(idCom, comTitle, month, idDep)
            if (!groupMap.containsKey(idDep)) {
                groupMap[idDep] = mutableListOf()
                depNameMap[idDep] = nameDep
            }
            groupMap[idDep]!!.add(item)
        }

        return groupMap.keys.sorted().map { idDep ->
            DepartmentGroup(
                idDep = idDep,
                nameDep = depNameMap[idDep] ?: idDep,
                topics = groupMap[idDep] ?: emptyList(),
                isScriptAudit = false
            )
        }
    }

    // 脚本审核 · 宣传审核模式:根据年月筛选所有 topical_detail 记录
    // 状态显示: vet_statue=null/0=待审核, 1=审核完毕, 3=业务已审核, 4=业务不通过
    private fun loadPromoteAuditGroups(conn: Db.Conn): List<DepartmentGroup> {
        // 年月过滤("全部"时不加条件)
        val ymFilter = buildYmFilter()
        // 查所有记录,带上 vet_statue 字段
        val sql = "SELECT id_detail, id_joined_dep, id_Editer_user, vet_statue FROM topical_detail " +
            "WHERE 1=1$ymFilter ORDER BY id_joined_dep, id_detail"
        val rows = conn.query(sql).toList()
        val groupMap = mutableMapOf<String, MutableList<AuditTopicItem>>()
        val depNameCache = mutableMapOf<String, String>()
        for (row in rows) {
            val idDetail = row.get(0).toString().removeSurrounding("[", "]")
            val idJoinedDep = row.get(1).toString().removeSurrounding("[", "]")
            val rawVet = row.get(3).toString().removeSurrounding("[", "]")
            val vetStatus = rawVet.toIntOrNull() ?: 0  // null 转为 0(待审核)
            val idJoined = if (idDetail.length >= 12) idDetail.substring(0, 12) else ""
            val displayTitle = if (idJoined.isNotEmpty())
                fetchComTitle(conn, idJoined) else idDetail
            val item = AuditTopicItem(
                idCom = idDetail,
                comTitle = displayTitle,
                month = currentMonth,
                idDep = idJoinedDep,
                vetStatus = vetStatus,
                idDetail = idDetail,
                idJoinedDep = idJoinedDep
            )
            if (!groupMap.containsKey(idJoinedDep)) {
                groupMap[idJoinedDep] = mutableListOf()
                val escD = idJoinedDep.replace("'", "''")
                val rsD = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escD' LIMIT 1")
                val dRows = rsD.toList()
                depNameCache[idJoinedDep] = if (dRows.isNotEmpty())
                    dRows[0].get(0).toString().removeSurrounding("[", "]") else idJoinedDep
            }
            groupMap[idJoinedDep]!!.add(item)
        }
        return groupMap.keys.sorted().map { idDep ->
            DepartmentGroup(
                idDep = idDep,
                nameDep = depNameCache[idDep] ?: idDep,
                topics = groupMap[idDep] ?: emptyList(),
                isScriptAudit = true,
                isDeptAudit = MyApp.loginDepLevel > 1
            )
        }
    }

    // 通过 id_joined 查 joined_topical.id_com → commission_summary.com_title
    private fun fetchComTitle(conn: Db.Conn, idJoined: String): String {
        val escIdJoined = idJoined.replace("'", "''")
        val rsJt = conn.query(
            "SELECT id_com FROM joined_topical WHERE id_joined = '$escIdJoined' LIMIT 1"
        )
        val jtRows = rsJt.toList()
        if (jtRows.isEmpty()) return idJoined
        val idCom = jtRows[0].get(0).toString().removeSurrounding("[", "]")
        val escIdCom = idCom.replace("'", "''")
        val rsCs = conn.query("SELECT com_title FROM commission_summary WHERE id_com = '$escIdCom' LIMIT 1")
        val csRows = rsCs.toList()
        return if (csRows.isNotEmpty())
            csRows[0].get(0).toString().removeSurrounding("[", "]") else idCom
    }

    // 脚本审核 · 业务审核模式:单条 JOIN 查询
    //  流程:dep_vet 月份 + vet → JOIN topical_detail(取 id_joined_dep)
    //      → JOIN joined_topical(按 id_detail 前 12 位 = id_joined,取 id_com)
    //      → JOIN commission_summary(按 id_com,取 com_title)
    //      → JOIN Department(取 id_dep_vet 名称)
    //  结果按 id_dep_vet 分组,每条存 id_com、id_detail、vet
    //  level>1 用户仅看自己部门(id_dep_vet = loginDep);level<=1 看全部
    private fun loadBusinessAuditGroups(conn: Db.Conn): List<DepartmentGroup> {
        val ymFilter = buildYmFilter(col = "dv.id_detail")
        // vet 过滤(默认 ALL 不过滤)
        val vetCond = when (currentVetFilter) {
            VetFilter.ALL -> ""
            VetFilter.PASSED -> " AND dv.vet = 1"
            VetFilter.REJECTED -> " AND dv.vet = 2"
            VetFilter.PENDING -> " AND dv.vet = 0"
        }
        // level>1(部门审核 Tab)下仅看登录部门;level<=1 看全部
        val depVetFilter = if (MyApp.loginDepLevel > 1) {
            val escDep = MyApp.loginDeaprt.replace("'", "''")
            " AND dv.id_dep_vet = '$escDep'"
        } else ""
        // 年月过滤同时加到 dep_vet.id_detail 前 4 位 和 commission_summary.id_com 前 4 位
        val idComYmFilter = buildIdComYmFilter()
        // dep_dep 表别名 dd 区分源部门(id_dep_vet 所在部门)
        val sql = """
            SELECT
                dv.id_detail AS id_detail,
                dv.vet AS vet,
                dv.id_dep_vet AS id_dep_vet,
                td.id_joined_dep AS id_joined_dep,
                COALESCE(cs.com_title, jt.id_joined, dv.id_detail) AS com_title,
                COALESCE(jt.id_com, '') AS id_com,
                dd.name_dep AS name_dep
            FROM dep_vet dv
            JOIN topical_detail td ON td.id_detail = dv.id_detail
            JOIN joined_topical jt ON jt.id_joined = substr(dv.id_detail, 1, 12)
            LEFT JOIN commission_summary cs ON cs.id_com = jt.id_com$idComYmFilter
            LEFT JOIN Department dd ON dd.id_dep = dv.id_dep_vet
            WHERE 1=1$ymFilter$vetCond$depVetFilter
            ORDER BY dv.id_dep_vet, dv.id_detail
        """.trimIndent()
        val rows = conn.query(sql).toList()
        if (rows.isEmpty()) return emptyList()

        val items = mutableListOf<AuditTopicItem>()
        val depNameCache = mutableMapOf<String, String>()

        for (row in rows) {
            val idDetail = row.get(0).toString().removeSurrounding("[", "]")
            val rawVet = row.get(1).toString().removeSurrounding("[", "]")
            val vet = rawVet.toIntOrNull() ?: Int.MIN_VALUE
            val idDepVet = row.get(2).toString().removeSurrounding("[", "]")   // 审核部门 id
            val idJoinedDep = row.get(3).toString().removeSurrounding("[", "]") // 报送单位 id
            val comTitle = row.get(4).toString().removeSurrounding("[", "]")
            val idCom = row.get(5).toString().removeSurrounding("[", "]")
            val nameDep = row.get(6).toString().removeSurrounding("[", "]")

            items.add(
                AuditTopicItem( 
                    idCom = idCom.ifEmpty { idDetail },
                    comTitle = comTitle,
                    month = currentMonth,
                    idDep = idDepVet,         // 分组键:审核部门(id_dep_vet)
                    vetStatus = vet,
                    idDetail = idDetail,      // 脚本详情用
                    idJoinedDep = idJoinedDep // 报送单位 id
                )
            )
            if (idDepVet.isNotEmpty() && !depNameCache.containsKey(idDepVet)) {
                depNameCache[idDepVet] = nameDep.ifEmpty { idDepVet }
            }
        }

        if (items.isEmpty()) return emptyList()
        // 按 id_dep_vet 分组
        val groupMap = items.groupBy { it.idDep }
        return groupMap.keys.sorted().map { idDep ->
            DepartmentGroup(
                idDep = idDep,
                nameDep = depNameCache[idDep] ?: idDep,
                topics = groupMap[idDep] ?: emptyList(),
                isScriptAudit = true,
                isDeptAudit = true
            )
        }
    }

    /**
     * 对 id_com 的年月过滤(与 id_detail 的月份可能不同)。
     * 仅当年月都不是"全部"且月都在可过滤范围时生效,避免 LEFT JOIN 被过度过滤变 INNER。
     */
    private fun buildIdComYmFilter(): String {
        if (currentYear == "全部" && currentMonth == "全部") return ""
        if (currentYear == "全部") return " AND substr(jt.id_com, 3, 2) = '$currentMonth'"
        if (currentMonth == "全部") return " AND substr(jt.id_com, 1, 2) = '$currentYear'"
        return " AND substr(jt.id_com, 1, 4) = '$currentYear$currentMonth'"
    }

    // 脚本审核(兼容原函数。保留以便未来以其他方式调用)
    @Deprecated("已拆为 loadPromoteAuditGroups / loadBusinessAuditGroups")
    private fun loadScriptAuditGroups(conn: Db.Conn): List<DepartmentGroup> {
        val idDetailList: List<String>

        if (MyApp.loginDepLevel != 1) {
            // 非宣传中心:查 dep_vet 中 id_dep_vet=登录部门 且 vet=0 的 id_detail
            val escDep = MyApp.loginDeaprt.replace("'", "''")
            val sqlVet = "SELECT id_detail FROM dep_vet " +
                "WHERE id_dep_vet = '$escDep' AND vet = 0"
            val rsVet = conn.query(sqlVet)
            idDetailList = rsVet.toList().map { row ->
                row.get(0).toString().removeSurrounding("[", "]")
            }
            if (idDetailList.isEmpty()) {
                return emptyList()
            }
        } else {
            idDetailList = emptyList()
        }

        // 根据 id_detail 列表查 topical_detail 其他信息
        val sql = if (MyApp.loginDepLevel == 1) {
            "SELECT id_detail, id_joined_dep, id_Editer_user " +
                "FROM topical_detail WHERE vet_statue = 1 " +
                "ORDER BY id_joined_dep, id_detail"
        } else {
            val placeholders = idDetailList.joinToString(",") { "'${it.replace("'", "''")}'" }
            "SELECT id_detail, id_joined_dep, id_Editer_user " +
                "FROM topical_detail WHERE id_detail IN ($placeholders) " +
                "ORDER BY id_joined_dep, id_detail"
        }
        val rs = conn.query(sql)
        val rows = rs.toList()

        // 按 id_joined_dep 分组
        val groupMap = mutableMapOf<String, MutableList<AuditTopicItem>>()
        val depNameCache = mutableMapOf<String, String>()

        for (row in rows) {
            val idDetail = row.get(0).toString().removeSurrounding("[", "]")
            val idJoinedDep = row.get(1).toString().removeSurrounding("[", "]")
            // val editorUser = row.get(2).toString().removeSurrounding("[", "]")

            // 取 id_com 前缀作为展示标题的"标记":用 id_detail 前 12 位
            // 通过 id_detail 前 12 位查 joined_topical.id_joined,再查 id_com
            val idJoined = if (idDetail.length >= 12) idDetail.substring(0, 12) else ""
            var displayTitle = idDetail
            if (idJoined.isNotEmpty()) {
                val escIdJoined = idJoined.replace("'", "''")
                val rsJt = conn.query(
                    "SELECT id_com FROM joined_topical WHERE id_joined = '$escIdJoined' LIMIT 1"
                )
                val jtRows = rsJt.toList()
                if (jtRows.isNotEmpty()) {
                    val idCom = jtRows[0].get(0).toString().removeSurrounding("[", "]")
                    // 进一步查 com_title
                    val escIdCom = idCom.replace("'", "''")
                    val rsCs = conn.query(
                        "SELECT com_title FROM commission_summary WHERE id_com = '$escIdCom' LIMIT 1"
                    )
                    val csRows = rsCs.toList()
                    val comTitle = if (csRows.isNotEmpty()) {
                        csRows[0].get(0).toString().removeSurrounding("[", "]")
                    } else idCom
                    displayTitle = comTitle
                }
            }

            val item = AuditTopicItem(idCom = idDetail, comTitle = displayTitle, month = "", idDep = idJoinedDep)
            if (!groupMap.containsKey(idJoinedDep)) {
                groupMap[idJoinedDep] = mutableListOf()
                // 查部门名
                val escDep = idJoinedDep.replace("'", "''")
                val rsD = conn.query("SELECT name_dep FROM Department WHERE id_dep = '$escDep' LIMIT 1")
                val dRows = rsD.toList()
                depNameCache[idJoinedDep] = if (dRows.isNotEmpty()) {
                    dRows[0].get(0).toString().removeSurrounding("[", "]")
                } else idJoinedDep
            }
            groupMap[idJoinedDep]!!.add(item)
        }

        return groupMap.keys.sorted().map { idDep ->
            DepartmentGroup(
                idDep = idDep,
                nameDep = depNameCache[idDep] ?: idDep,
                topics = groupMap[idDep] ?: emptyList(),
                isScriptAudit = true,
                isDeptAudit = MyApp.loginDepLevel > 1
            )
        }
    }

    // 点击选题审核条目进入审核详情页
    private fun openAuditDetail(item: AuditTopicItem) {
        val intent = android.content.Intent(this, TopicAuditDetailActivity::class.java)
        intent.putExtra("id_com", item.idCom)
        startActivity(intent)
    }

    // 点击脚本审核/部门审核条目进入对应详情页
    private fun openScriptAuditDetail(item: AuditTopicItem) {
        // item.idDetail = topical_detail.id_detail（详情查询主键）
        // item.idJoinedDep = 报送单位 id；item.idDep = id_dep_vet（审核部门）
        val goDept = MyApp.loginDepLevel > 1 || currentAuditMode == ScriptAuditMode.BUSINESS
        if (goDept) {
            // 业务科室 或 业务审核模式：进入"部门审核"详情页
            val intent = android.content.Intent(this, DepartmentAuditDetailActivity::class.java)
            intent.putExtra(DepartmentAuditDetailActivity.EXTRA_ID_DETAIL, item.idDetail)
            intent.putExtra(DepartmentAuditDetailActivity.EXTRA_ID_JOINED_DEP, item.idJoinedDep)
            intent.putExtra(DepartmentAuditDetailActivity.EXTRA_ID_DEP_VET, item.idDep)
            // level>1（部门审核Tab）可编辑；level<=1（业务审核模式）只读
            intent.putExtra(DepartmentAuditDetailActivity.EXTRA_READONLY, MyApp.loginDepLevel <= 1)
            startActivity(intent)
        } else {
            // 宣传中心 且 宣传审核模式：根据 vet_statue 决定页面状态
            // 0=待审核(可编辑), 3=业务已审核(可编辑+显示业务审核情况), 1/2/4=只读
            val intent = android.content.Intent(this, ScriptAuditDetailActivity::class.java)
            intent.putExtra(ScriptAuditDetailActivity.EXTRA_ID_DETAIL, item.idDetail)
            intent.putExtra(ScriptAuditDetailActivity.EXTRA_ID_JOINED_DEP, item.idJoinedDep)
            intent.putExtra(ScriptAuditDetailActivity.EXTRA_VET_STATUS, item.vetStatus)
            startActivity(intent)
        }
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

    // Topic 子项 VH
    class TopicVH(val view: View) {
        val tvMonth: TextView = view.findViewById(R.id.tv_month)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvStatusBadge: TextView = view.findViewById(R.id.tv_status_badge)
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

        // 待审核数量(部门审核/脚本审核/选题审核区分文案)
        val prefix = when {
            group.isDeptAudit -> "业务"
            group.isScriptAudit -> "脚本"
            else -> "选题"
        }
        holder.tvCount.text = "(${group.topics.size}条待审$prefix)"

        // 展开/收缩状态
        updateExpandState(holder, group)

        // 点击部门卡片:展开/收缩
        holder.cardDepartment.setOnClickListener {
            group.isExpanded = !group.isExpanded
            updateExpandState(holder, group)
        }

        // 填充子条目列表
        holder.llTopics.removeAllViews()
        for (topic in group.topics) {
            val topicView = LayoutInflater.from(ctx)
                .inflate(R.layout.item_audit_topic, holder.llTopics, false)
            val tvMonth = topicView.findViewById<TextView>(R.id.tv_month)
            val tvTitle = topicView.findViewById<TextView>(R.id.tv_title)
            val tvBadge = topicView.findViewById<TextView>(R.id.tv_status_badge)

            if (group.isScriptAudit) {
                // 业务审核模式:根据 vet 显示状态
                if (group.isDeptAudit && topic.vetStatus != Int.MIN_VALUE) {
                    when (topic.vetStatus) {
                        0 -> {
                            tvMonth.text = "待审"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#1976D2"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                        }
                        1 -> {
                            tvMonth.text = "审核通过"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                        }
                        2 -> {
                            tvMonth.text = "不建议宣传"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#C62828"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                        }
                        3 -> {
                            tvMonth.text = "慎过"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#E65100"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                        }
                        else -> {
                            tvMonth.text = "脚本"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#FF6F00"))
                        }
                    }
                } else {
                    // 宣传审核模式:根据 vet_statue 显示状态
                    // 0/null=待审核, 1=审核完毕, 2=业务正在审核, 3=业务已审核, 4=业务不通过
                    when (topic.vetStatus) {
                        0 -> {
                            tvMonth.text = "待审核"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#1976D2"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                        }
                        1 -> {
                            tvMonth.text = "审核完毕"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                        }
                        2 -> {
                            tvMonth.text = "业务正在审核"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#F57C00"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                        }
                        3 -> {
                            tvMonth.text = "业务已审核"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#7B1FA2"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#F3E5F5"))
                        }
                        4 -> {
                            tvMonth.text = "业务不通过"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#C62828"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                        }
                        else -> {
                            tvMonth.text = "待审核"
                            tvMonth.setTextColor(android.graphics.Color.parseColor("#1976D2"))
                            tvMonth.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                        }
                    }
                }
            } else {
                tvMonth.text = "${topic.month}月"
                tvMonth.setTextColor(android.graphics.Color.parseColor("#FF6F00"))
                tvMonth.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            tvTitle.text = topic.comTitle

            // 业务审核 vet 徽章
            if (group.isDeptAudit && topic.vetStatus != Int.MIN_VALUE) {
                when (topic.vetStatus) {
                    0 -> {
                        tvBadge.text = "待审核"
                        tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"))
                        tvBadge.visibility = View.VISIBLE
                    }
                    1 -> {
                        tvBadge.text = "可以宣传"
                        tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"))
                        tvBadge.visibility = View.VISIBLE
                    }
                    2 -> {
                        tvBadge.text = "不宣传"
                        tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
                        tvBadge.visibility = View.VISIBLE
                    }
                    3 -> {
                        tvBadge.text = "不建议宣传"
                        tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#E65100"))
                        tvBadge.visibility = View.VISIBLE
                    }
                    else -> tvBadge.visibility = View.GONE
                }
            } else {
                tvBadge.visibility = View.GONE
            }

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