package com.oldtomcat.taxgroup

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.google.android.material.card.MaterialCardView
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 七牛云附件上传配置。下面三个值需要你按实际填（部署完 Worker 后）。
 * AK/SK 不在 APP 里，Worker 负责签名。
 */
object QiniuConfig {
    // 腾讯云 SCF 函数 URL（替代 Cloudflare Worker，国内可达）
    const val WORKER_BASE = "https://1420431702-93ncyovss7.ap-nanjing.tencentscf.com"  // TODO: 改成你的 SCF 函数 URL
    const val TOKEN_PATH = "/api/qiniu-token"
    // 七牛绑定的自定义域名（已备案的腾讯域名，去掉结尾斜杠）
    const val CUSTOM_DOMAIN = "https://files.wxc-tomcat.com.cn"      // TODO: 改成你的备案域名
}

class ScriptDetailActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvDepart: TextView
    private lateinit var tvComTitle: TextView
    private lateinit var tvComSummary: TextView
    private lateinit var tvTypeName: TextView
    private lateinit var etScript: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var btnSave: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnAudit: Button
    private lateinit var btnBackBottom: Button
    private lateinit var progressBar: ProgressBar

    // dep_vet 业务审核模式控件
    private lateinit var cardDepVetAudit: MaterialCardView
    private lateinit var etMemo: EditText
    private lateinit var tvMemoCount: TextView
    private lateinit var rgVetChoice: RadioGroup
    private lateinit var rbPromote: RadioButton
    private lateinit var rbNotMyDept: RadioButton
    private lateinit var rbNotRecommend: RadioButton
    private lateinit var btnDepVetSubmit: Button

    // 附件（七牛云）控件
    private lateinit var btnSelectAttachment: Button
    private lateinit var llAttachmentInfo: LinearLayout
    private lateinit var tvAttachmentName: TextView
    // private lateinit var btnUploadAttachment: Button // 已移除, 提交时统一上传
    private lateinit var btnPreviewAttachment: Button
    private lateinit var btnRemoveAttachment: Button
    private lateinit var pbAttachment: ProgressBar

    // 审核通过横幅
    private lateinit var tvAuditPassedBanner: TextView

    // 从 Intent 传入的参数
    private var idCom: String = ""
    private var idJoined: String = ""
    private var idJoinedList: String = ""
    private var typeName: String = ""
    private var isSubmittedMode: Boolean = false  // true=已提交（只可改script），false=未提交（INSERT）
    private var idDetail: String = ""              // 已提交时的主键值
    private var fromDepVet: Boolean = false        // true=从业务待审（dep_vet）列表进入
    private var idJoinedDep: String = ""           // 该脚本归属的部门 id（用于判断是否本部门）

    // 数据库加载的数据
    private var comTitle: String = ""
    private var comSummary: String = ""
    private var idType: String = ""  // 最后一位字母
    private var existingScript: String = ""  // 已提交时从 topical_detail 读到的原 script
    private var vetStatue: Int = 0  // 当前 vet_statue（0/null=未审，1=提交宣传主管部门，2=提交业务主管部门）

    // 附件（七牛云）相关状态
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var currentAttachmentUrl: String = ""
    private var currentAttachmentName: String = ""

    // 文件选择 launcher（现代 Activity Result API，targetSdk 36 推荐）
    private val pickFileLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) handlePickedFile(uri) else {
                android.widget.Toast.makeText(this, "未选择文件", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

    @Volatile
    private var isLoading = false

    // SharedPreferences 用于暂存
    private val PREFS_NAME = "ScriptDraft"
    private val KEY_SCRIPT_PREFIX = "script_"

    companion object {
        const val EXTRA_ID_COM = "id_com"
        const val EXTRA_ID_JOINED = "id_joined"
        const val EXTRA_ID_JOINED_LIST = "id_joined_list"
        const val EXTRA_TYPE_NAME = "type_name"
        const val EXTRA_IS_SUBMITTED = "is_submitted"
        const val EXTRA_ID_DETAIL = "id_detail"
        const val EXTRA_FROM_DEP_VET = "from_dep_vet"
        const val EXTRA_ID_JOINED_DEP = "id_joined_dep"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.script_detail_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 获取传入参数
        idCom = intent.getStringExtra(EXTRA_ID_COM) ?: ""
        idJoined = intent.getStringExtra(EXTRA_ID_JOINED) ?: ""
        idJoinedList = intent.getStringExtra(EXTRA_ID_JOINED_LIST) ?: ""
        typeName = intent.getStringExtra(EXTRA_TYPE_NAME) ?: ""
        isSubmittedMode = intent.getBooleanExtra(EXTRA_IS_SUBMITTED, false)
        idDetail = intent.getStringExtra(EXTRA_ID_DETAIL) ?: ""
        fromDepVet = intent.getBooleanExtra(EXTRA_FROM_DEP_VET, false)
        idJoinedDep = intent.getStringExtra(EXTRA_ID_JOINED_DEP) ?: ""

        // 提取最后一位字母作为 id_type
        idType = if (idJoinedList.isNotEmpty()) idJoinedList.last().toString() else ""

        // 处理从其他 App (如微信) 分享过来的文件
        handleSharedIntent(intent)

        // 初始化控件
        tvUserName = findViewById(R.id.tv_user_name)
        tvDepart = findViewById(R.id.tv_depart)
        tvComTitle = findViewById(R.id.tv_com_title)
        tvComSummary = findViewById(R.id.tv_com_summary)
        tvTypeName = findViewById(R.id.tv_type_name)
        etScript = findViewById(R.id.et_script)
        tvCharCount = findViewById(R.id.tv_char_count)
        btnSave = findViewById(R.id.btn_save)
        btnSubmit = findViewById(R.id.btn_submit)
        btnAudit = findViewById(R.id.btn_audit)
        progressBar = findViewById(R.id.progress_bar)
        btnBackBottom = findViewById(R.id.btn_back_bottom)

        // dep_vet 业务审核面板
        cardDepVetAudit = findViewById(R.id.card_dep_vet_audit)
        etMemo = findViewById(R.id.et_memo)
        tvMemoCount = findViewById(R.id.tv_memo_count)
        rgVetChoice = findViewById(R.id.rg_vet_choice)
        rbPromote = findViewById(R.id.rb_promote)
        rbNotMyDept = findViewById(R.id.rb_not_my_dept)
        rbNotRecommend = findViewById(R.id.rb_not_recommend)
        btnDepVetSubmit = findViewById(R.id.btn_dep_vet_submit)

        // 附件（七牛云）相关控件
        btnSelectAttachment = findViewById(R.id.btn_select_attachment)
        llAttachmentInfo = findViewById(R.id.ll_attachment_info)
        tvAttachmentName = findViewById(R.id.tv_attachment_name)
        // btnUploadAttachment = findViewById(R.id.btn_upload_attachment) // 已移除
        btnPreviewAttachment = findViewById(R.id.btn_preview_attachment)
        btnRemoveAttachment = findViewById(R.id.btn_remove_attachment)
        pbAttachment = findViewById(R.id.pb_attachment)
        tvAuditPassedBanner = findViewById(R.id.tv_audit_passed_banner)

        btnSelectAttachment.setOnClickListener { pickFileLauncher.launch("*/*") }
        // btnUploadAttachment.setOnClickListener { uploadAttachment() } // 已移除, 提交时统一上传
        btnPreviewAttachment.setOnClickListener { previewAttachment() }
        btnRemoveAttachment.setOnClickListener { removeAttachment() }

        // 修改意见字数统计
        etMemo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvMemoCount.text = "$len/100"
            }
        })

        btnDepVetSubmit.setOnClickListener { submitDepVetAudit() }

        // 顶部状态栏
        tvUserName.text = MyApp.loginName
        tvDepart.text = MyApp.loginDeaprtName

        // 顶部按钮
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = android.content.Intent(this, HomeMenuActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 字数统计
        etScript.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len/1000"
            }
        })

        // 保存按钮 - 未提交模式暂存到本地；已提交模式隐藏
        btnSave.setOnClickListener { saveDraft() }

        // 提交按钮 - 已提交模式下 UPDATE；未提交模式下 INSERT
        btnSubmit.setOnClickListener {
            if (isSubmittedMode) updateScript() else submitScript()
        }

        // 提交审核按钮 - UPDATE topical_detail.vet_statue = 1
        btnAudit.setOnClickListener { submitAudit() }

        // 返回按钮 - 仅在“非本部门只读”分支可见
        btnBackBottom.setOnClickListener { finish() }

        // 已提交模式：隐藏保存按钮，仅保留“提交修改”入库
        if (isSubmittedMode) {
            btnSave.visibility = View.GONE
            btnSubmit.text = "提交修改"
        } else {
            // 未提交模式：隐藏提交审核按钮（还未入库，无 vet_statue）
            btnAudit.visibility = View.GONE
        }

        // dep_vet 业务审核模式：隐藏原“保存/提交/提交审核”按钮，显示审核面板
        if (fromDepVet) {
            btnSave.visibility = View.GONE
            btnSubmit.visibility = View.GONE
            btnAudit.visibility = View.GONE
            btnBackBottom.visibility = View.GONE
        }

        // 非本部门脚本：隐藏保存/提交/提交审核，仅保留“返回”
        // （fromDepVet 走审核面板，不进入此分支）
        if (!fromDepVet && idJoinedDep.isNotEmpty() && idJoinedDep != MyApp.loginDeaprt) {
            btnSave.visibility = View.GONE
            btnSubmit.visibility = View.GONE
            btnAudit.visibility = View.GONE
            btnBackBottom.visibility = View.VISIBLE
            etScript.isEnabled = false
            etScript.setBackgroundColor(0xFFF5F5F5.toInt())
        } else {
            btnBackBottom.visibility = View.GONE
        }

        if (fromDepVet) {
            cardDepVetAudit.visibility = View.VISIBLE
            // dep_vet 模式不修改 script，禁用 script 编辑
            etScript.isEnabled = false
            etScript.setBackgroundColor(0xFFF5F5F5.toInt())
        }

        // 加载数据
        loadData()
    }

    private fun loadData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    // 1. 查 commission_summary 获取 com_title, com_summary
                    val escIdCom = idCom.replace("'", "''")
                    val rsCs = conn.query(
                        "SELECT com_title, com_summary FROM commission_summary WHERE id_com = '$escIdCom'"
                    )
                    val csRows = rsCs.toList()
                    if (csRows.isNotEmpty()) {
                        comTitle = csRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        comSummary = csRows[0].get(1).toString().removeSurrounding("[", "]").trim()
                    }

                    // 2. 已提交模式：从 topical_detail 查出原 script 和 vet_statue
                    if (isSubmittedMode && idDetail.isNotEmpty()) {
                        val escIdDetail = idDetail.replace("'", "''")
                        val rsTd = conn.query(
                            "SELECT script, vet_statue, attachment_url, attachment_name FROM topical_detail WHERE id_detail = '$escIdDetail' LIMIT 1"
                        )
                        val tdRows = rsTd.toList()
                        if (tdRows.isNotEmpty()) {
                            existingScript = tdRows[0].get(0).toString().removeSurrounding("[", "]").trim()
                            vetStatue = tdRows[0].get(1).toString().toIntOrNull() ?: 0
                            currentAttachmentUrl = tdRows[0].get(2).toString().removeSurrounding("[", "]").trim()
                            currentAttachmentName = tdRows[0].get(3).toString().removeSurrounding("[", "]").trim()
                        }
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvComTitle.text = comTitle.ifEmpty { "[无标题]" }
                    tvComSummary.text = comSummary.ifEmpty { "[无内容]" }
                    tvTypeName.text = typeName

                    if (isSubmittedMode && existingScript.isNotEmpty()) {
                        // 已提交模式：回填原 script 到编辑框
                        etScript.setText(existingScript)
                        tvCharCount.text = "${existingScript.length}/1000"
                    } else {
                        // 未提交模式：尝试加载暂存内容
                        loadDraft()
                    }

                    // 附件区刷新（含只读判断）
                    refreshAttachmentUi()

                    // 更新提交审核按钮状态
                    updateAuditButtonState()

                    // 审核通过锁定（顶部横幅 + 脚本不可编辑 + 删除附件不可用）
                    applyAuditPassedLock()

                    isLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("错误")
                        .setMessage("加载失败：${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                    isLoading = false
                }
            }
        }.start()
    }

    // 暂存到 SharedPreferences
    private fun saveDraft() {
        val script = etScript.text.toString()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_SCRIPT_PREFIX$idJoinedList", script).apply()

        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("成功保存在本地手机，暂未提交")
            .setPositiveButton("确定", null)
            .show()
    }

    // 加载暂存内容
    private fun loadDraft() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val draft = prefs.getString("$KEY_SCRIPT_PREFIX$idJoinedList", "")
        if (!draft.isNullOrEmpty()) {
            etScript.setText(draft)
            tvCharCount.text = "${draft.length}/1000"
        }
    }

    // 提交到 topical_detail
    private fun submitScript() {
        val script = etScript.text.toString().trim()
        if (script.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("脚本内容不能为空")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                // ========== 第一步: 插入数据库 (attachment_url/name 临时为空) ==========
                var newIdDetail = ""
                Db.withConnection { conn ->
                    // 1. 计算 id_detail = 脚本编号(13位) + 序号(2位) = 15位
                    val prefix = idJoinedList
                    val escPrefix = prefix.replace("'", "''")
                    val rsCount = conn.query(
                        "SELECT COUNT(*) FROM topical_detail WHERE id_detail LIKE '$escPrefix%'"
                    )
                    val countRows = rsCount.toList()
                    val existingCount = if (countRows.isNotEmpty()) {
                        countRows[0].get(0).toString().removeSurrounding("[", "]").trim().toIntOrNull() ?: 0
                    } else {
                        0
                    }
                    val seq = (existingCount + 1).toString().padStart(2, '0')
                    newIdDetail = prefix + seq

                    // 2. 插入记录
                    val escIdDetail = newIdDetail.replace("'", "''")
                    val escIdEditer = MyApp.loginId.replace("'", "''")
                    val escIdType = idType.replace("'", "''")
                    val escIdJoinedDep = MyApp.loginDeaprt.replace("'", "''")
                    val escScript = script.replace("'", "''")

                    val insertSql = "INSERT INTO topical_detail " +
                        "(id_Editer_user, id_type, detail_url, id_detail, id_joined_dep, script, attachment_url, attachment_name) VALUES (" +
                        "'$escIdEditer', '$escIdType', '', '$escIdDetail', '$escIdJoinedDep', '$escScript', '', '')"
                    conn.execute(insertSql)
                }

                // ========== 第二步: 验证保存成功 ==========
                var verified = false
                Db.withConnection { conn ->
                    val rs = conn.query("SELECT id_detail FROM topical_detail WHERE id_detail = '${newIdDetail.replace("'", "''")}'")
                    verified = rs.toList().isNotEmpty()
                }
                if (!verified) {
                    throw Exception("数据库验证失败: id_detail=$newIdDetail 未查询到记录")
                }

                // ========== 第三步: 如果有附件, 上传到七牛 ==========
                val pendingUri = selectedFileUri
                val pendingFileName = selectedFileName
                if (pendingUri != null) {
                    val baseKey = newIdDetail
                    val uploadResult = uploadAttachmentBlocking(pendingUri, baseKey, pendingFileName)
                    if (uploadResult.first == null) {
                        throw Exception("附件上传失败: ${uploadResult.third}")
                    }
                    val url = uploadResult.first!!
                    val name = uploadResult.second!!

                    // ========== 第四步: 上传成功后, 更新记录写入附件 URL/name ==========
                    Db.withConnection { conn ->
                        val escIdDetail = newIdDetail.replace("'", "''")
                        val escUrl = url.replace("'", "''")
                        val escName = name.replace("'", "''")
                        conn.execute("UPDATE topical_detail SET attachment_url='$escUrl', attachment_name='$escName' WHERE id_detail='$escIdDetail'")
                    }
                    currentAttachmentUrl = url
                    currentAttachmentName = name
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false

                    // 清除暂存
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().remove("$KEY_SCRIPT_PREFIX$idJoinedList").apply()

                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交成功")
                        .setMessage("脚本已提交" + if (currentAttachmentUrl.isNotEmpty()) "\n附件已上传" else "")
                        .setPositiveButton("确定") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    /** 同步上传附件到七牛, 返回 (url, name, err) */
    private fun uploadAttachmentBlocking(uri: Uri, baseKey: String, fileName: String): Triple<String?, String?, String?> {
        var resultUrl: String? = null
        var resultName: String? = null
        var resultErr: String? = null
        val done = java.util.concurrent.CountDownLatch(1)
        runOnUiThread {
            uploadAttachmentWithCallback(uri, baseKey, fileName) { url, name, err ->
                resultUrl = url
                resultName = name
                resultErr = err
                done.countDown()
            }
        }
        done.await(60, java.util.concurrent.TimeUnit.SECONDS)
        return Triple(resultUrl, resultName, resultErr)
    }

    // 已提交模式下的“提交修改”逻辑：UPDATE topical_detail.script
    private fun updateScript() {
        val script = etScript.text.toString().trim()
        if (script.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("脚本内容不能为空")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                // ========== 第一步: 更新数据库 (attachment_url/name 临时为空) ==========
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escScript = script.replace("'", "''")
                    val updateSql = "UPDATE topical_detail SET script = '$escScript', " +
                        "attachment_url = '', attachment_name = '' " +
                        "WHERE id_detail = '$escIdDetail'"
                    conn.execute(updateSql)
                }

                // ========== 第二步: 验证保存成功 ==========
                var verified = false
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val rs = conn.query("SELECT script FROM topical_detail WHERE id_detail = '$escIdDetail'")
                    val rows = rs.toList()
                    if (rows.isNotEmpty()) {
                        val savedScript = rows[0].get(0).toString().removeSurrounding("[", "]").trim()
                        verified = savedScript == script
                    }
                }
                if (!verified) {
                    throw Exception("数据库验证失败: id_detail=$idDetail 未查询到更新的脚本")
                }

                // ========== 第三步: 如果有新选附件, 上传到七牛 ==========
                val pendingUri = selectedFileUri
                val pendingFileName = selectedFileName
                if (pendingUri != null) {
                    val baseKey = idDetail
                    val uploadResult = uploadAttachmentBlocking(pendingUri, baseKey, pendingFileName)
                    if (uploadResult.first == null) {
                        throw Exception("附件上传失败: ${uploadResult.third}")
                    }
                    val url = uploadResult.first!!
                    val name = uploadResult.second!!

                    // ========== 第四步: 上传成功后, 更新记录写入附件 URL/name ==========
                    Db.withConnection { conn ->
                        val escIdDetail = idDetail.replace("'", "''")
                        val escUrl = url.replace("'", "''")
                        val escName = name.replace("'", "''")
                        conn.execute("UPDATE topical_detail SET attachment_url='$escUrl', attachment_name='$escName' WHERE id_detail='$escIdDetail'")
                    }
                    currentAttachmentUrl = url
                    currentAttachmentName = name
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    existingScript = script

                    // 清除暂存
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().remove("$KEY_SCRIPT_PREFIX$idJoinedList").apply()

                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交成功")
                        .setMessage("脚本已修改并提交到数据库" + if (currentAttachmentUrl.isNotEmpty()) "\n附件已上传" else "")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    // 提交审核：UPDATE topical_detail.vet_statue = 1
    private fun submitAudit() {
        if (idDetail.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("未找到脚本主键 id_detail，无法提交审核")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val updateSql = "UPDATE topical_detail SET vet_statue = 1 " +
                        "WHERE id_detail = '$escIdDetail'"
                    conn.execute(updateSql)
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    vetStatue = 1
                    updateAuditButtonState()

                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交成功")
                        .setMessage("已提交宣传主管部门审核")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    // 更新提交审核按钮显示与状态
    private fun updateAuditButtonState() {
        when (vetStatue) {
            1 -> {
                btnAudit.text = "提交审核(已提交宣传)"
                btnAudit.isEnabled = false
                btnAudit.alpha = 0.5f
            }
            2 -> {
                btnAudit.text = "提交审核(已提交业务)"
                btnAudit.isEnabled = false
                btnAudit.alpha = 0.5f
            }
            else -> {
                // 0 或 null：未审核
                btnAudit.text = "提交审核(待审)"
                btnAudit.isEnabled = true
                btnAudit.alpha = 1.0f
            }
        }
    }

    // dep_vet 业务审核：UPDATE dep_vet SET vet=?, memo=? WHERE id_detail=? AND id_dep_vet=?
    private fun submitDepVetAudit() {
        if (idDetail.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("未找到脚本主键 id_detail，无法提交业务审核")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        // 必须勾选其中一个
        if (!rbPromote.isChecked && !rbNotMyDept.isChecked && !rbNotRecommend.isChecked) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("请选择业务审核结果")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val memo = etMemo.text.toString().trim()
        val vetValue = when {
            rbPromote.isChecked -> 1      // 可以宣传
            rbNotMyDept.isChecked -> 2    // 非本部门业务
            else -> 3                     // 不建议宣传
        }
        val vetLabel = when (vetValue) {
            1 -> "可以宣传"
            2 -> "非本部门业务"
            else -> "不建议宣传"
        }

        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        btnDepVetSubmit.isEnabled = false

        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escIdDepVet = MyApp.loginDeaprt.replace("'", "''")
                    val escMemo = memo.replace("'", "''")
                    conn.execute(
                        "UPDATE dep_vet SET vet = $vetValue, memo = '$escMemo' " +
                        "WHERE id_detail = '$escIdDetail' AND id_dep_vet = '$escIdDepVet'"
                    )
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    btnDepVetSubmit.isEnabled = true
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交成功")
                        .setMessage("业务审核结果：$vetLabel")
                        .setPositiveButton("确定") { _, _ -> finish() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    btnDepVetSubmit.isEnabled = true
                    AlertDialog.Builder(this@ScriptDetailActivity)
                        .setTitle("提交失败")
                        .setMessage("${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }.start()
    }

    // ============================================================
    // 附件（七牛云）相关逻辑
    // ============================================================

    /** 附件区是否只读：业务审核页(fromDepVet) 或 非本部门脚本 都不可改附件 */
    private fun isAttachmentReadOnly(): Boolean {
        return fromDepVet
            || (!fromDepVet && idJoinedDep.isNotEmpty() && idJoinedDep != MyApp.loginDeaprt)
            || vetStatue == 1 || vetStatue == 3
    }

    /** vet=1（宣传通过）或 vet=3（业务已审核视为通过）→ 锁定编辑 */
    private fun applyAuditPassedLock() {
        if (vetStatue != 1 && vetStatue != 3) return
        // 顶部横幅
        tvAuditPassedBanner.visibility = View.VISIBLE
        // 脚本内容锁定
        etScript.isEnabled = false
        etScript.setBackgroundColor(0xFFF5F5F5.toInt())
        // 「提交修改」按钮不可用
        btnSubmit.isEnabled = false
        btnSubmit.alpha = 0.5f
        // 附件「删除」按钮不可用
        btnRemoveAttachment.isEnabled = false
        btnRemoveAttachment.alpha = 0.5f
    }

    /** 选择文件回调 */
    private fun handlePickedFile(uri: Uri) {
        selectedFileUri = uri
        selectedFileName = getFileNameFromUri(uri)
        refreshAttachmentUi()
    }

    /**
     * 处理其他 App (如微信) 分享过来的文件
     * Intent.action == ACTION_SEND, EXTRA_STREAM 是文件 Uri
     */
    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (uri == null) return

        // 申请持久化权限, 才能在后面读取 Uri 上传
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w("ShareReceive", "无法申请持久化权限: ${e.message}")
        }

        Toast.makeText(this, "已从分享接收到文件", Toast.LENGTH_SHORT).show()
        handlePickedFile(uri)
    }

    /**
     * 单任务模式下, 复用实例时调用
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    /** 根据当前状态刷新附件区 UI */
    private fun refreshAttachmentUi() {
        val readOnly = isAttachmentReadOnly()
        when {
            currentAttachmentUrl.isNotEmpty() -> {
                // 已上传：显示名称 + 预览；只读则不显示删除
                llAttachmentInfo.visibility = View.VISIBLE
                tvAttachmentName.text = currentAttachmentName.ifEmpty { currentAttachmentUrl }
                btnPreviewAttachment.visibility = View.VISIBLE
                btnSelectAttachment.visibility = View.GONE
                btnRemoveAttachment.visibility = if (readOnly) View.GONE else View.VISIBLE
            }
            selectedFileUri != null -> {
                // 已选未传：显示名称 + 删除（提交时统一上传）
                llAttachmentInfo.visibility = View.VISIBLE
                tvAttachmentName.text = selectedFileName
                btnSelectAttachment.visibility = View.GONE
                btnPreviewAttachment.visibility = View.GONE
                btnRemoveAttachment.visibility = if (readOnly) View.GONE else View.VISIBLE
            }
            else -> {
                // 无附件：仅显示选择（只读时连选择都隐藏）
                llAttachmentInfo.visibility = View.GONE
                btnSelectAttachment.visibility = if (readOnly) View.GONE else View.VISIBLE
                btnPreviewAttachment.visibility = View.GONE
                btnRemoveAttachment.visibility = View.GONE
            }
        }
    }

    /** 上传附件到七牛云 (用于提交流程) */
    private fun uploadAttachmentWithCallback(uri: Uri, baseKey: String, fileName: String, onResult: (String?, String?, String?) -> Unit) {
        val key = "${baseKey}_${System.currentTimeMillis()}"
        runOnUiThread { pbAttachment.visibility = View.VISIBLE }
        btnRemoveAttachment.isEnabled = false

        fetchUploadToken(key) { token, host, retKey, err ->
            if (token == null || host == null) {
                runOnUiThread {
                    pbAttachment.visibility = View.GONE
                    btnRemoveAttachment.isEnabled = true
                    val msg = err?.let { "获取上传凭证失败: $it" } ?: "获取上传凭证失败"
                    Log.e("QiniuUpload", msg)
                    onResult(null, null, msg)
                }
                return@fetchUploadToken
            }
            uploadToQiniu(uri, token, retKey ?: key, host) { url, uploadErr ->
                runOnUiThread {
                    pbAttachment.visibility = View.GONE
                    btnRemoveAttachment.isEnabled = true
                    if (url == null) {
                        val msg = uploadErr?.let { "上传失败: $it" } ?: "上传失败"
                        Log.e("QiniuUpload", msg)
                        onResult(null, null, msg)
                    } else {
                        onResult(url, fileName, null)
                    }
                }
            }
        }
    }

    /** 预览附件：按扩展名智能分发，图片直开，Office/PDF 走微软预览 */
    private fun previewAttachment() {
        if (currentAttachmentUrl.isEmpty()) {
            Toast.makeText(this, "暂无附件可预览", Toast.LENGTH_SHORT).show()
            return
        }
        val name = currentAttachmentName.ifEmpty { "附件预览" }
        val ext = currentAttachmentName.substringAfterLast('.', "").lowercase()
        val previewUrl = when (ext) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> currentAttachmentUrl
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf" -> {
                val encoded = URLEncoder.encode(currentAttachmentUrl, "UTF-8")
                "https://view.officeapps.live.com/op/view.aspx?src=$encoded"
            }
            else -> currentAttachmentUrl
        }
        val intent = Intent(this, WebViewPreviewActivity::class.java)
        intent.putExtra(WebViewPreviewActivity.EXTRA_URL, previewUrl)
        intent.putExtra(WebViewPreviewActivity.EXTRA_TITLE, name)
        startActivity(intent)
    }

    /** 删除附件（本地状态 + 已入库则回写空） */
    private fun removeAttachment() {
        val hadUploaded = currentAttachmentUrl.isNotEmpty()
        selectedFileUri = null
        selectedFileName = ""
        currentAttachmentUrl = ""
        currentAttachmentName = ""
        if (idDetail.isNotEmpty() && hadUploaded) persistAttachmentToDb()
        refreshAttachmentUi()
    }

    /** 已入库时把附件信息写回 topical_detail */
    private fun persistAttachmentToDb() {
        if (idDetail.isEmpty()) return
        Thread {
            try {
                Db.withConnection { conn ->
                    val escIdDetail = idDetail.replace("'", "''")
                    val escUrl = currentAttachmentUrl.replace("'", "''")
                    val escName = currentAttachmentName.replace("'", "''")
                    conn.execute(
                        "UPDATE topical_detail SET attachment_url = '$escUrl', attachment_name = '$escName' " +
                        "WHERE id_detail = '$escIdDetail'"
                    )
                }
            } catch (_: Exception) { }
        }.start()
    }

    /** 向 Cloudflare Worker 请求七牛上传 token */
    private fun fetchUploadToken(key: String, cb: (String?, String?, String?, String?) -> Unit) {
        Thread {
            try {
                val urlStr = "${QiniuConfig.WORKER_BASE}${QiniuConfig.TOKEN_PATH}?key=${URLEncoder.encode(key, "UTF-8")}"
                Log.d("QiniuUpload", "GET token URL: $urlStr")
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                } else {
                    conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                Log.d("QiniuUpload", "GET token response: code=$code body=$resp")
                if (code in 200..299) {
                    val json = JSONObject(resp)
                    val token = json.optString("uploadToken")
                    val host = json.optString("uploadHost", "https://upload.qiniup.com")
                    val retKey = json.optString("key", key)
                    cb(token, host, retKey, null)
                } else {
                    cb(null, null, null, "HTTP $code: $resp")
                }
            } catch (e: Exception) {
                Log.e("QiniuUpload", "GET token exception: ${e.message}", e)
                cb(null, null, null, "Exception: ${e.message}")
            }
        }.start()
    }

    /** 七牛直传：multipart/form-data POST 到 uploadHost */
    private fun uploadToQiniu(fileUri: Uri, token: String, key: String, host: String, onResult: (String?, String?) -> Unit) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(fileUri)
                    ?: throw RuntimeException("无法读取文件")
                val boundary = "----Boundary${System.currentTimeMillis()}"
                Log.d("QiniuUpload", "POST upload URL: $host, boundary=$boundary, key=$key")
                val conn = URL(host).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                val os = conn.outputStream
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(os, StandardCharsets.UTF_8))
                fun writeLine(s: String) { writer.write(s); writer.write("\r\n") }

                writeLine("--$boundary")
                writeLine("Content-Disposition: form-data; name=\"token\"")
                writeLine("")
                writeLine(token)

                writeLine("--$boundary")
                writeLine("Content-Disposition: form-data; name=\"key\"")
                writeLine("")
                writeLine(key)

                writeLine("--$boundary")
                val safeFileName = selectedFileName.replace("\"", "_")
                writeLine("Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"")
                writeLine("Content-Type: application/octet-stream")
                writeLine("")
                writer.flush()
                // 写入文件二进制
                inputStream.copyTo(os)
                os.flush()
                writeLine("")
                writeLine("--$boundary--")
                writer.flush()
                writer.close()
                inputStream.close()

                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                } else {
                    conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                Log.d("QiniuUpload", "POST upload response: code=$code body=$resp")
                if (code in 200..299) {
                    val json = JSONObject(resp)
                    val returnedKey = json.optString("key", key)
                    val url = "${QiniuConfig.CUSTOM_DOMAIN.trimEnd('/')}/$returnedKey"
                    onResult(url, null)
                } else {
                    onResult(null, "HTTP $code: $resp")
                }
            } catch (e: Exception) {
                Log.e("QiniuUpload", "POST upload exception: ${e.message}", e)
                onResult(null, "Exception: ${e.message}")
            }
        }.start()
    }

    /** 从 Uri 取文件名 */
    private fun getFileNameFromUri(uri: Uri): String {
        var name = ""
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: ""
                }
            }
        } catch (_: Exception) { }
        if (name.isEmpty()) name = "file_${System.currentTimeMillis()}"
        return name
    }
}
