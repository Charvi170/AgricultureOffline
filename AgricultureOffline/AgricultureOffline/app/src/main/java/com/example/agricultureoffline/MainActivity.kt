package com.example.agricultureoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.agricultureoffline.ScreenUI.Navigation
import com.example.agricultureoffline.i18n.LocalizedApp
import com.example.agricultureoffline.sms.SmsQueueManager
import com.example.agricultureoffline.ui.theme.AgricultureOfflineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SmsQueueManager.init(applicationContext)
        setContent {
            LocalizedApp {
                AgricultureOfflineTheme {
                    Navigation()
                }
            }
        }
    }
}
