package com.ninepointlabs.quill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ninepointlabs.quill.theme.QuillTheme
import com.ninepointlabs.quill.data.DataRepository
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DataRepository.initDb(applicationContext, lifecycleScope)

    enableEdgeToEdge()
    setContent {
      QuillTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    DataRepository.closeDb()
  }
}
