package com.example.vidsafe

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        pv = PlayerView(this)
        setContentView(pv)
    }

    override fun onStart() {
        super.onStart()
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        player = ExoPlayer.Builder(this).build().also {
            pv.player = it
            it.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            it.prepare()
            it.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release(); player = null
    }
}
