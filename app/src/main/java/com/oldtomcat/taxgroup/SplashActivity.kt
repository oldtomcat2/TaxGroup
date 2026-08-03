package com.oldtomcat.taxgroup

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sp: SharedPreferences = getSharedPreferences("app_data", MODE_PRIVATE)
        val isFirst = sp.getBoolean("first_open", true)

        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Handler(Looper.getMainLooper()).postDelayed({

            if (isFirst) {
                // 第一次：去引导页
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // 非第一次：直接进主页
                startActivity(Intent(this, MainActivity::class.java))
            }

            // 销毁启动页，无法返回
            finish()

        }, 1500)
    }
}