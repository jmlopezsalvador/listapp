package com.listapp

import androidx.compose.ui.window.ComposeUIViewController
import com.listapp.ui.DocumentsBlobStore
import com.listapp.ui.ListAppRoot
import com.listapp.ui.UserDefaultsStore
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    ListAppRoot(UserDefaultsStore(), DocumentsBlobStore())
}
