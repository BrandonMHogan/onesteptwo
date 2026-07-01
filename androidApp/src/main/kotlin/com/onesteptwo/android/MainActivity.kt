package com.onesteptwo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.onesteptwo.android.navigation.AppNavigation
import com.onesteptwo.android.ui.theme.OneStepTwoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Allow the Compose content to draw behind the system bars so that
        // imePadding() on auth forms can push content above the software keyboard
        // (UI-SPEC Keyboard Behaviour — "Forms scroll above the keyboard").
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OneStepTwoTheme {
                Surface {
                    AppNavigation()
                }
            }
        }
    }
}
