package com.example.voiceassistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val info = TextView(this).apply {
            text = "Say \"Hey buddy\", then a command like:\ncall mom / what's the time / open YouTube"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        val start = Button(this).apply { text = "Start listening" }
        val stop = Button(this).apply { text = "Stop listening" }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info)
            addView(start)
            addView(stop)
        })

        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(perms.toTypedArray())

        start.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                info.text = "Please allow microphone permission, then tap Start again."
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            } else if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, AssistantService::class.java)
                )
            }
        }

        stop.setOnClickListener {
            stopService(Intent(this, AssistantService::class.java))
        }
    }
}
