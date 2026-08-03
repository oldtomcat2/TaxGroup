package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class TaskItem(
    val taskNo: String,
    val taskTitle: String,
    val bDate: String,
    val eDate: String
)

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var idTask: String
    private lateinit var taskName: String
    private val pageSize = 10
    private var currentPage = 1
    private var totalPages = 1
    private lateinit var adapter: TaskListAdapter
    private lateinit var rvList: RecyclerView

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        idTask = intent.getStringExtra("id_task") ?: ""
        taskName = intent.getStringExtra("task_name") ?: ""

        // 顶部栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName
        findViewById<TextView>(R.id.tv_section_title).text = taskName

        // 返回
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 刷新按钮
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadPage(currentPage)
        }

        // 主页按钮
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 列表
        rvList = findViewById(R.id.rv_task_list)
        rvList.layoutManager = LinearLayoutManager(this)
        adapter = TaskListAdapter(emptyList())
        rvList.adapter = adapter

        // 翻页按钮
        findViewById<Button>(R.id.btn_prev).setOnClickListener { goToPage(currentPage - 1) }
        findViewById<Button>(R.id.btn_next).setOnClickListener { goToPage(currentPage + 1) }

        loadPage(1)
    }

    private fun goToPage(page: Int) {
        if (page < 1 || page > totalPages) return
        loadPage(page)
    }

    private fun loadPage(page: Int) {
        if (isLoading) return
        isLoading = true
        val offset = (page - 1) * pageSize
        Thread {
            try {
                Db.withConnection { conn ->
                    val countRs = conn.query("select count(*) from task_list where id_task = '$idTask'")
                    val totalCount = countRs.toList().firstOrNull()?.get(0).toString().toIntOrNull() ?: 0
                    totalPages = if (totalCount == 0) 1 else (totalCount + pageSize - 1) / pageSize

                    val rs = conn.query(
                        "select task_NO, task_title, b_date, e_date from task_list where id_task = '$idTask' " +
                            "order by task_NO desc limit $pageSize offset $offset"
                    )
                    val items = rs.toList().map { r ->
                        TaskItem(
                            taskNo = r.get(0).toString(),
                            taskTitle = r.get(1).toString().removeSurrounding("[", "]"),
                            bDate = r.get(2).toString().removeSurrounding("[", "]"),
                            eDate = r.get(3).toString().removeSurrounding("[", "]")
                        )
                    }

                    currentPage = page

                    runOnUiThread {
                        adapter = TaskListAdapter(items)
                        rvList.adapter = adapter

                        val llPager = findViewById<View>(R.id.ll_pager)
                        val btnPrev = findViewById<Button>(R.id.btn_prev)
                        val btnNext = findViewById<Button>(R.id.btn_next)
                        val tvPageInfo = findViewById<TextView>(R.id.tv_page_info)

                        if (totalPages <= 1) {
                            llPager.visibility = View.GONE
                        } else {
                            llPager.visibility = View.VISIBLE
                            btnPrev.isEnabled = currentPage > 1
                            btnNext.isEnabled = currentPage < totalPages
                            tvPageInfo.text = "第 $currentPage / $totalPages 页"
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@TaskDetailActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // === Adapter ===
    class TaskListAdapter(private val items: List<TaskItem>) :
        RecyclerView.Adapter<TaskListAdapter.VH>() {

        class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_detail, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.view.findViewById<TextView>(R.id.tv_item_title).text = item.taskTitle

            val dates = holder.view.findViewById<TextView>(R.id.tv_item_dates)
            dates.text = if (item.bDate.isNotEmpty() || item.eDate.isNotEmpty()) {
                "${item.bDate} ~ ${item.eDate}"
            } else {
                ""
            }
            dates.visibility = if (dates.text.isEmpty()) View.GONE else View.VISIBLE
        }

        override fun getItemCount() = items.size
    }
}