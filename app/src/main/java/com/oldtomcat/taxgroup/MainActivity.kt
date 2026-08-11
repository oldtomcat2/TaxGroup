package com.oldtomcat.taxgroup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    lateinit var user_name: EditText
    lateinit var user_pass: EditText
    lateinit var bt1: Button
    lateinit var btn_register: Button
    private val sp: SharedPreferences by lazy { getSharedPreferences("login_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        user_name = findViewById(R.id.Uname)
        user_pass = findViewById(R.id.Upass)
        bt1 = findViewById(R.id.Bt1)
        btn_register = findViewById(R.id.btn_register)

        // 显示当前版本号（从 BuildConfig 读取）
        findViewById<TextView>(R.id.tv_app_version).text = "v${BuildConfig.VERSION_NAME}"

        // 默认填充：用户名为上次成功登录的用户名，密码留空
        val lastUsername = sp.getString("last_username", "") ?: ""
        if (lastUsername.isNotEmpty()) {
            user_name.setText(lastUsername)
            user_name.setSelection(lastUsername.length)
        }

        //注册按钮
        btn_register.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        //按钮的监听程序
        bt1.setOnClickListener {
            val Uname = user_name.text.toString().trim()
            val Upass = user_pass.text.toString().trim()
            if (Uname.isEmpty() || Upass.isEmpty()) {
                Toast.makeText(this@MainActivity, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    Db.withConnection { conn ->
                        val getName = conn.query(
                            "select a.id_user,a.name_user,a.id_dep,b.name_dep,b.level from User a,Department b where a.id_user='$Uname' and a.password='$Upass' and a.id_dep = b.id_dep"
                        )
                        val rowlist = getName.toList()
                        runOnUiThread {
                            if (rowlist.isEmpty()) {
                                android.app.AlertDialog.Builder(this@MainActivity)
                                    .setTitle("提示")
                                    .setMessage("用户名或密码错误")
                                    .setPositiveButton("确定", null)
                                    .show()
                            } else {
                                // 登录成功：记住本次用户名（密码不保存）
                                sp.edit().putString("last_username", Uname).apply()
                                MyApp.loginId = rowlist[0].get(0).toString()
                                MyApp.loginName = rowlist[0].get(1).toString()
                                MyApp.loginDeaprt = rowlist[0].get(2).toString()
                                MyApp.loginDeaprtName = rowlist[0].get(3).toString()
                                MyApp.loginDepLevel = try {
                                    rowlist[0].get(4).toString().removeSurrounding("[", "]").trim().toInt()
                                } catch (_: Exception) { 0 }
                                val intent = Intent(this@MainActivity, HomeMenuActivity::class.java)
                                startActivity(intent)
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("错误")
                            .setMessage("${e.javaClass.simpleName}: ${e.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }.start()
        }
   }
}



