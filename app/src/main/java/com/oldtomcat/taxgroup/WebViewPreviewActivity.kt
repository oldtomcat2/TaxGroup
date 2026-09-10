package com.oldtomcat.taxgroup

import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 通用 WebView 预览页，用于打开腾讯文档预览链接（Word / WPS 等）。
 * 通过 Intent.putExtra("url", ...) / putExtra("title", ...) 传入。
 */
class WebViewPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_preview)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.webview_preview_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "文档预览"

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // 在 WebView 内打开
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        if (url.isNotEmpty()) {
            webView.loadUrl(url)
        } else {
            webView.loadUrl("about:blank")
        }

        // 返回键：WebView 内能后退则后退，否则关闭页面
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            })
    }
}
