package com.oldtomcat.taxgroup

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 通过 Turso HTTP API 访问云端数据库。
 *
 * 完全不依赖任何原生库（libsql 的 .so），因此不会在华为等设备的
 * 原生层崩溃（SIGSEGV）。所有数据库操作走标准 HttpURLConnection。
 *
 * 为兼容旧代码，提供 Db.withConnection { conn -> conn.query(...).toList() / conn.execute(...) }
 * 的写法，活动里只需把 MyApp.db.connect().use { conn -> 换成 Db.withConnection { conn ->。
 */
object Db {

    private const val BASE_URL =
        "https://taxpoints-oldtomcat2.aws-ap-northeast-1.turso.io/v2/pipeline"
    private const val TOKEN =
        "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3NzYzMzg0NDUsImlkIjoiMDE5ZDhhZTMtYWQwMS03Yjk2LTk1YTEtZDBkYjJkNTRkZjkzIiwicmlkIjoiN2RkZTc4Y2MtOTMwYS00NGQzLWJhOWMtNzM5MWM0M2E2Njk5In0.oKk-JwyDTv3omuVrlFXzroxDUZkMdg8LQXBhNqT3wH3XP12dmnsRL0hwJYAk3u-u-qUfKVqjzkOePHdCCSIACQ"

    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 20000

    // ===== 兼容旧代码的入口 =====
    fun withConnection(block: (Conn) -> Unit) {
        block(Conn)
    }

    object Conn {
        fun query(sql: String): QueryResult = QueryResult(httpQuery(sql))
        fun execute(sql: String): Long = httpExecute(sql)
    }

    class QueryResult(private val rows: List<DbRow>) {
        fun toList(): List<DbRow> = rows
    }

    // ===== 实际 HTTP 实现 =====

    private fun httpQuery(sql: String): List<DbRow> {
        val resp = postSql(sql)
        checkOk(resp)
        return parseRows(resp)
    }

    private fun httpExecute(sql: String): Long {
        val resp = postSql(sql)
        checkOk(resp)
        return parseAffected(resp)
    }

    private fun postSql(sql: String): String {
        val url = URL(BASE_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $TOKEN")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            doInput = true
        }
        val body = buildBody(sql)
        conn.outputStream.use { os ->
            os.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        val code = conn.responseCode
        if (code < 200 || code >= 300) {
            val err = try {
                conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: ""
            } catch (_: Exception) {
                ""
            }
            conn.disconnect()
            throw RuntimeException("数据库请求失败 (HTTP $code): $err")
        }
        val resp = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        conn.disconnect()
        return resp
    }

    // 抽取 execute 结果并检查错误（避免误判为成功）
    private fun checkOk(resp: String) {
        val root = JSONObject(resp)
        val results = root.optJSONArray("results") ?: return
        if (results.length() == 0) return
        val first = results.optJSONObject(0) ?: return
        if (first.optString("type") == "error") {
            val err = first.optJSONObject("error")
            val msg = err?.optString("message") ?: "未知错误"
            val code = err?.optString("code") ?: ""
            throw RuntimeException("SQL执行失败 [$code]: $msg")
        }
    }

    private fun buildBody(sql: String): String {
        // JSONObject.quote 负责正确转义 SQL 中的引号、反斜杠等
        return """{"requests":[{"type":"execute","stmt":{"sql":${JSONObject.quote(sql)}}},{"type":"close"}]}"""
    }

    private fun parseRows(resp: String): List<DbRow> {
        val root = JSONObject(resp)
        val results = root.optJSONArray("results") ?: return emptyList()
        if (results.length() == 0) return emptyList()
        val first = results.optJSONObject(0) ?: return emptyList()
        val response = first.optJSONObject("response") ?: return emptyList()
        val result = response.optJSONObject("result") ?: return emptyList()
        val rows = result.optJSONArray("rows") ?: JSONArray()
        val list = ArrayList<DbRow>(rows.length())
        for (i in 0 until rows.length()) {
            val rowArr = rows.optJSONArray(i) ?: continue
            val values = ArrayList<Any?>(rowArr.length())
            for (j in 0 until rowArr.length()) {
                val cell = rowArr.optJSONObject(j)
                values.add(if (cell == null) null else parseCell(cell))
            }
            list.add(DbRow(values))
        }
        return list
    }

    private fun parseAffected(resp: String): Long {
        val root = JSONObject(resp)
        val results = root.getJSONArray("results")
        val first = results.getJSONObject(0)
        // 容错：某些情况下 response 可能没有 result 字段（如 DDL 错误）
        val response = first.optJSONObject("response") ?: return 0L
        val result = response.optJSONObject("result") ?: return 0L
        val lastIdRaw = result.opt("last_insert_rowid")
        val lastId = when (lastIdRaw) {
            is Number -> lastIdRaw.toLong()
            is String -> lastIdRaw.toLongOrNull() ?: 0L
            else -> 0L
        }
        if (lastId != 0L) return lastId
        val affRaw = result.opt("affected_row_count")
        return when (affRaw) {
            is Number -> affRaw.toLong()
            is String -> affRaw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun parseCell(cell: JSONObject): Any? {
        if (cell.isNull("type") || cell.isNull("value")) {
            return null
        }
        return when {
            cell.opt("type") == null -> null
            else -> when (cell.optString("type", "null")) {
                "null" -> null
                "integer" -> {
                    val v = cell.opt("value")
                    when (v) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                }
                "float" -> {
                    val v = cell.opt("value")
                    when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                }
                "text", "blob" -> cell.optString("value", "")
                else -> cell.optString("value", "")
            }
        }
    }
}

class DbRow(private val values: List<Any?>) {
    fun get(index: Int): Any? = values.getOrNull(index)
    val size: Int get() = values.size
}
