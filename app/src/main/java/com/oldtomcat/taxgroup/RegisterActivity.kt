package com.oldtomcat.taxgroup

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    lateinit var etPhone: EditText
    lateinit var etName: EditText
    lateinit var spDepartment: Spinner
    lateinit var etPassword: EditText
    lateinit var etPasswordConfirm: EditText
    lateinit var btnSubmit: Button
    lateinit var btnBack: Button

    // 部门列表：显示名 → id_dep
    private val departmentMap = mutableMapOf<String, String>()
    private var selectedDeptId: String? = null
    private var deptLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etPhone       = findViewById(R.id.et_phone)
        etName        = findViewById(R.id.et_name)
        spDepartment  = findViewById(R.id.sp_department)
        etPassword    = findViewById(R.id.et_password)
        etPasswordConfirm = findViewById(R.id.et_password_confirm)
        btnSubmit     = findViewById(R.id.btn_submit)
        btnBack       = findViewById(R.id.btn_back)

        loadDepartments()

        btnSubmit.setOnClickListener { doSubmit() }
        btnBack.setOnClickListener { finish() }
    }

    private fun loadDepartments() {
        Thread {
            try {
                Db.withConnection { conn ->
                    val rows = conn.query(
                        "SELECT id_dep, name_dep FROM Department ORDER BY id_dep"
                    ).toList()

                    runOnUiThread {
                        if (rows.isEmpty()) {
                            Toast.makeText(this, "部门列表为空", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        departmentMap.clear()
                        val names = mutableListOf<String>()
                        for (row in rows) {
                            val idDep  = row.get(0).toString()
                            val nameDep = row.get(1).toString()
                            departmentMap[nameDep] = idDep
                            names.add(nameDep)
                        }

                        val adapter = ArrayAdapter(
                            this@RegisterActivity,
                            android.R.layout.simple_spinner_item,
                            names
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        spDepartment.adapter = adapter
                        spDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: AdapterView<*>?, view: View?, position: Int, id: Long
                            ) {
                                val selectedName = names[position]
                                selectedDeptId = departmentMap[selectedName]
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {
                                selectedDeptId = null
                            }
                        }
                        // 默认选中第一项，触发监听
                        if (names.isNotEmpty()) {
                            spDepartment.setSelection(0)
                        }
                        deptLoaded = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "加载部门失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun doSubmit() {
        val phone    = etPhone.text.toString().trim()
        val name     = etName.text.toString().trim()
        val pwd      = etPassword.text.toString()
        val pwdConf  = etPasswordConfirm.text.toString()
        val deptId   = selectedDeptId

        // 校验1：手机号不得少于11位
        if (phone.length < 11) {
            showError("手机号不得少于11位")
            return
        }

        // 校验2：姓名不得为空
        if (name.isEmpty()) {
            showError("用户姓名不能为空")
            return
        }

        // 校验3：部门必须选择
        if (deptId == null || deptId.isEmpty()) {
            showError("请选择所在部门")
            return
        }

        // 校验4：两次密码必须一致
        if (pwd != pwdConf) {
            showError("初始密码和确认密码不一致")
            return
        }

        // 校验5：密码不能为空
        if (pwd.isEmpty()) {
            showError("密码不能为空")
            return
        }

        // 校验6：查重（手机号是否已存在）
        Thread {
            try {
                Db.withConnection { conn ->
                    val rows = conn.query(
                        "SELECT id_user FROM User WHERE id_user='$phone'"
                    ).toList()

                    runOnUiThread {
                        if (rows.isNotEmpty()) {
                            showError("该手机号已注册，请直接登录")
                            return@runOnUiThread
                        }
                        // 通过全部校验，执行插入
                        insertUser(phone, name, deptId, pwd)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("校验失败：${e.message}")
                }
            }
        }.start()
    }

    private fun insertUser(phone: String, name: String, deptId: String, pwd: String) {
        btnSubmit.isEnabled = false
        btnSubmit.text = "注册中…"

        Thread {
            try {
                Db.withConnection { conn ->
                    conn.execute(
                        "INSERT INTO User (id_user, name_user, id_dep, password) " +
                        "VALUES ('$phone', '$name', '$deptId', '$pwd')"
                    )
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this@RegisterActivity)
                            .setTitle("注册成功")
                            .setMessage("账号注册成功，请使用手机号和密码登录。")
                            .setPositiveButton("确定") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "提 交"
                    showError("注册失败：${e.message}")
                }
            }
        }.start()
    }

    private fun showError(msg: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage(msg)
            .setPositiveButton("确定", null)
            .show()
    }
}
