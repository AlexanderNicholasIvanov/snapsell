package com.alexivanov.snapsell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alexivanov.snapsell.ui.nav.SnapSellNavHost
import com.alexivanov.snapsell.ui.theme.SnapSellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SnapSellApp).container
        setContent {
            SnapSellTheme {
                SnapSellNavHost(container = container)
            }
        }
    }
}
