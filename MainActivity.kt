package com.example.vidsafe

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private var files: List<File> = emptyList()
    private var activeCount = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }
        root.addView(TextView(this).apply { text = "VidSafe - Private Videos"; textSize = 22f })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val browse = Button(this).apply { text = "Browser" }
        val paste = Button(this).apply { text = "Link paste" }
        row.addView(browse, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(paste, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        browse.setOnClickListener { startActivity(Intent(this, BrowserActivity::class.java)) }
        paste.setOnClickListener { pasteDialog() }
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - activeCount
            if (i >= 0) startActivity(Intent(this, PlayerActivity::class.java).putExtra("path", files[i].absolutePath))
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val i = pos - activeCount
            if (i >= 0) AlertDialog.Builder(this)
                .setMessage("Delete ${files[i].name}?")
                .setPositiveButton("Delete") { _, _ -> files[i].delete(); refresh() }
                .setNegativeButton("Cancel", null).show()
            true
        }
    }

    private fun pasteDialog() {
        val et = EditText(this).apply { hint = "https://.../video.mp4 ya .m3u8" }
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString()
        if (clip != null && clip.startsWith("http")) et.setText(clip)
        AlertDialog.Builder(this).setTitle("Video link").setView(et)
            .setPositiveButton("Download") { _, _ ->
                val u = et.text.toString().trim()
                if (u.startsWith("http")) Downloader.start(this, u)
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onResume() {
        super.onResume()
        Downloader.onUpdate = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Downloader.onUpdate = null
    }

    private fun refresh() {
        val act = Downloader.active.entries.map { "⬇ ${it.key}   ${it.value}%" }
        activeCount = act.size
        files = Downloader.dir(this).listFiles { f -> !f.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        val labels = act + files.map { "${it.name}\n" + String.format("%.1f MB", it.length() / 1048576.0) }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }
}
