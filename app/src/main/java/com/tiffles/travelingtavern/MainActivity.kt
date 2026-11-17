package com.tiffles.travelingtavern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tiffles.travelingtavern.ui.navigation.TravelingTavernNavHost
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelingTavernTheme {
                TravelingTavernNavHost()
            }
        }
    }
}