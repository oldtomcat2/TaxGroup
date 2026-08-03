package com.oldtomcat.taxgroup

import android.app.Application

class MyApp : Application() {
    companion object {
        var loginId: String = ""
        var loginName: String = ""
        var loginDeaprt: String = ""
        var loginDeaprtName: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        // 数据库访问已改为 Db 对象（HTTP API），无需在此初始化原生库
    }
}
