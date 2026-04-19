package com.example.mdworkspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mdworkspace.ui.navigation.AppNavGraph
import com.example.mdworkspace.ui.theme.MarkdownWorkspaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarkdownWorkspaceTheme {
                AppNavGraph()
            }
        }
    }
}
