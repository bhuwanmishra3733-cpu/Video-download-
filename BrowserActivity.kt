package com.example.vidsafe

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var btn: Button
    private lateinit var et: EditText
    private val found = LinkedHashSet<String>()
    private val exts = listOf(".mp4", ".m3u8", ".webm", ".mkv", ".mov")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 48, 0, 0) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        et = EditText(this).apply {
            hint = "URL ya search"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val go = Button(this).apply { text = "Go" }
        bar.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(go)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                val p = u.substringBefore('?').lowercase()
                if (exts.any { p.endsWith(it) }) runOnUiThread {
                    if (found.add(u)) btn.text = "Videos mili: ${found.size} (dekhein)"
                }
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                found.clear(); btn.text = "Videos mili: 0"; et.setText(url)
            }
        }

        btn = Button(this).apply { text = "Videos mili: 0" }
        root.addView(bar)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(btn)
        setContentView(root)

        go.setOnClickListener { load(et.text.toString().trim()) }
        et.setOnEditorActionListener { _, _, _ -> load(et.text.toString().trim()); true }
        btn.setOnClickListener { showFound() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
        load("https://www.google.com")
    }

    private fun load(s: String) {
        if (s.isEmpty()) return
        val u = when {
            s.startsWith("http") -> s
            s.contains(" ") || !s.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(s)
            else -> "https://$s"
        }
        web.loadUrl(u)
    }

    private fun showFound() {
        if (found.isEmpty()) {
            Toast.makeText(this, "Pehle page par video play karo, phir yahan aao", Toast.LENGTH_LONG).show()
            return
        }
        val urls = found.toList()
        AlertDialog.Builder(this).setTitle("Download karo")
            .setItems(urls.toTypedArray()) { _, i ->
                val u = urls[i]
                Downloader.start(this, u, web.settings.userAgentString, web.url, CookieManager.getInstance().getCookie(u))
                Toast.makeText(this, "Download list mein dekho (back dabao)", Toast.LENGTH_SHORT).show()
            }.show()
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
