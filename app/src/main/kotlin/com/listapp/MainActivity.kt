package com.listapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.listapp.ui.FileBlobStore
import com.listapp.ui.ListAppRoot
import com.listapp.ui.SharedPrefsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val kv = SharedPrefsStore(applicationContext)
        val blobs = FileBlobStore(applicationContext)
        setContent { ListAppRoot(kv, blobs) }
    }
}
