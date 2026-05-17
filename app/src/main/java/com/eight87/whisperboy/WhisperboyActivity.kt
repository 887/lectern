package com.eight87.whisperboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.eight87.whisperboy.theme.WhisperboyTheme

class WhisperboyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val graph = (application as WhisperboyApplication).graph
    setContent {
      WhisperboyTheme(
        themeSettings = graph.themeSettings,
        nowPlayingState = graph.nowPlayingState,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { WhisperboyApp() }
      }
    }
  }
}
