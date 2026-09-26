package com.liquidos.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Store.reloadApps(this@MainActivity.applicationContext)
        }
    }

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Store.refreshAccent(this@MainActivity.applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Store.setup(this)

        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)

        ContextCompat.registerReceiver(
            this, wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )

        setContent { LauncherRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Store.goHome()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        try {
            unregisterReceiver(wallpaperReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
