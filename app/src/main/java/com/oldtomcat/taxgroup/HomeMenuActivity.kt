package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class MenuItem(
    val idMenu: String,
    val menuName: String
)

class HomeMenuActivity : AppCompatActivity() {

    private lateinit var rvMenu: RecyclerView

    // 查询锁：防并发访问数据库（HTTP）
    @Volatile
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_menu)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_menu_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 顶部状态栏
        findViewById<TextView>(R.id.tv_user_name).text = MyApp.loginName
        findViewById<TextView>(R.id.tv_depart).text = MyApp.loginDeaprtName

        // 返回按钮
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 刷新按钮
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            loadMenu()
        }

        // 菜单网格（2列）
        rvMenu = findViewById(R.id.rv_menu)
        rvMenu.layoutManager = GridLayoutManager(this, 2)

        loadMenu()
    }

    private fun loadMenu() {
        if (isLoading) return
        isLoading = true
        Thread {
            try {
                Db.withConnection { conn ->
                    val rs = conn.query("select id_menu, menu_name from Menu_1")
                    val menuList = rs.toList().map { row ->
                        MenuItem(
                            idMenu = row.get(0).toString(),
                            menuName = row.get(1).toString().removeSurrounding("[", "]")
                        )
                    }

                    runOnUiThread {
                        if (menuList.isEmpty()) {
                            AlertDialog.Builder(this@HomeMenuActivity)
                                .setTitle("提示")
                                .setMessage("暂无菜单数据")
                                .setPositiveButton("确定", null)
                                .show()
                        } else {
                            rvMenu.adapter = MenuAdapter(menuList)
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@HomeMenuActivity)
                        .setTitle("错误")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }
}

// === 图标加载工具 ===
object MenuIcons {
    private val defaultIcon = android.R.drawable.ic_menu_more

    fun forId(context: Context, idMenu: String): Int {
        // 尝试加载 id_menu 对应的 PNG，如 "1" -> R.drawable.icon_1
        val resourceName = "icon_${idMenu}"
        val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (resId != 0) resId else defaultIcon
    }
}

// === Adapter ===
class MenuAdapter(private val menus: List<MenuItem>) :
    RecyclerView.Adapter<MenuAdapter.VH>() {

    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val density = parent.resources.displayMetrics.density
        val card = MaterialCardView(parent.context).apply {
            layoutParams = GridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // 卡片间距放大，更舒展
                setMargins(
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt()
                )
            }
            // 立体感：白色半透 + 金色描边 + 大圆角 + 阴影
            radius = 28f * density                         // 28dp 圆角
            cardElevation = 10f * density                  // 10dp 立体阴影
            // 白色 60% alpha 背景，明显从水墨背景"浮"出来
            setCardBackgroundColor(0x99FFFFFF.toInt())
            // 金色 80% alpha 描边
            strokeWidth = (2 * density).toInt()
            setStrokeColor(ColorStateList.valueOf(0xCCE5C97A.toInt()))
        }
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val menu = menus[position]
        val ctx: Context = holder.card.context

        val view = LayoutInflater.from(ctx).inflate(R.layout.item_menu_icon, holder.card, false)
        holder.card.removeAllViews()
        holder.card.addView(view)

        view.findViewById<ImageView>(R.id.iv_icon).setImageResource(MenuIcons.forId(ctx, menu.idMenu))
        view.findViewById<TextView>(R.id.tv_menu_name).text = menu.menuName

        holder.card.setOnClickListener {
            when (menu.menuName) {
                "选题报送" -> {
                    val intent = android.content.Intent(ctx, TopicSubmissionActivity::class.java)
                    ctx.startActivity(intent)
                }
                "联动合作" -> {
                    val intent = android.content.Intent(ctx, JointCooperationActivity::class.java)
                    ctx.startActivity(intent)
                }
                "脚本编辑" -> {
                    val intent = android.content.Intent(ctx, ScriptEditActivity::class.java)
                    ctx.startActivity(intent)
                }
                "重点选题" -> {
                    val intent = android.content.Intent(ctx, KeyTopicActivity::class.java)
                    ctx.startActivity(intent)
                }
                "作品上传" -> {
                    val intent = android.content.Intent(ctx, WorkUploadActivity::class.java)
                    ctx.startActivity(intent)
                }
                "版本更新" -> {
                    UpdateManager.checkForUpdate(ctx)
                }
                "选题审核" -> {
                    // 权限检查：部门 level 必须为 1（已登录时从 MyApp.loginDepLevel 取）
                    val level = MyApp.loginDepLevel
                    if (level < 3) {
                        val intent = android.content.Intent(ctx, TopicAuditActivity::class.java)
                        ctx.startActivity(intent)
                    } else {
                        AlertDialog.Builder(ctx)
                            .setTitle("权限不足")
                            .setMessage("您的权限不够！")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
                else -> {
                    AlertDialog.Builder(ctx)
                        .setTitle(menu.menuName)
                        .setMessage("功能开发中，敬请期待")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

    override fun getItemCount() = menus.size
}