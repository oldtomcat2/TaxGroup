package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class TaskSection(
    val idTask: String,
    val taskName: String,
    val items: List<String>  // task_title 列表
)

class HomeActivity : AppCompatActivity() {

    private lateinit var rvSections: RecyclerView

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 刷新按钮
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadSections()
        }

        // 主页按钮
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        rvSections = findViewById(R.id.rv_sections)
        rvSections.layoutManager = LinearLayoutManager(this)

        loadSections()
    }

    private fun loadSections() {
        if (isLoading) return
        isLoading = true
        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query("select id_task, task_name from Task_type")
                    val rawList = rs.toList()

                    if (rawList.isEmpty()) {
                        runOnUiThread {
                            AlertDialog.Builder(this@HomeActivity)
                                .setTitle("提示")
                                .setMessage("暂无板块数据")
                                .setPositiveButton("确定") { _, _ -> finish() }
                                .show()
                            isLoading = false
                        }
                        return@withConnection
                    }

                    val sections = mutableListOf<TaskSection>()
                    for (row in rawList) {
                        val idTask = row.get(0).toString()
                        val taskName = row.get(1).toString().removeSurrounding("[", "]")

                        val itemsRs = conn.query(
                            "select task_NO, task_title from task_list where id_task = '$idTask' order by task_NO desc limit 5"
                        )
                        val items = itemsRs.toList().map { r ->
                            r.get(1).toString().removeSurrounding("[", "]")
                        }
                        sections.add(TaskSection(idTask, taskName, items))
                    }

                    runOnUiThread {
                        rvSections.adapter = SectionAdapter(sections)
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@HomeActivity)
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
    class SectionAdapter(private val sections: List<TaskSection>) :
        RecyclerView.Adapter<SectionAdapter.VH>() {

        class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_section, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val section = sections[position]
            val tvTitle = holder.view.findViewById<TextView>(R.id.tv_section_title)
            val llItems = holder.view.findViewById<ViewGroup>(R.id.ll_items)
            val tvEmpty = holder.view.findViewById<TextView>(R.id.tv_empty)

            tvTitle.text = section.taskName

            // 清空旧的子项
            llItems.removeAllViews()

            if (section.items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                tvEmpty.visibility = View.GONE
                for (item in section.items) {
                    val tv = LayoutInflater.from(holder.view.context)
                        .inflate(R.layout.item_task_row, llItems, false) as TextView
                    tv.text = item
                    llItems.addView(tv)
                }
            }

            // 点击板块跳转详情页
            holder.view.setOnClickListener {
                val context = holder.view.context
                val intent = Intent(context, TaskDetailActivity::class.java)
                intent.putExtra("id_task", section.idTask)
                intent.putExtra("task_name", section.taskName)
                context.startActivity(intent)
            }
        }

        override fun getItemCount() = sections.size
    }
}