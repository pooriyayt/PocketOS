package app.pocketos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.pocketos.core.security.LockState
import app.pocketos.data.prefs.AppLanguage
import app.pocketos.data.prefs.AppSettings
import app.pocketos.ui.PocketApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Single activity. It is a FragmentActivity (via AppCompat) because
 * BiometricPrompt and per-app language switching on older Android versions
 * require it.
 */
class MainActivity : AppCompatActivity() {

    private val deepLinks = MutableStateFlow<Uri?>(null)
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as PocketOsApp).container
        splash.setKeepOnScreenCondition { !settingsLoaded || container.appLock.state.value == LockState.UNKNOWN }
        deepLinks.value = intent?.data

        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                settingsLoaded = true
                applyWindowSecurity(s)
                applyLanguage(s.language)
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s: AppSettings = settings ?: return@setContent
            LaunchedEffect(Unit) { settingsLoaded = true }
            PocketApp(container, s, deepLinks)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { deepLinks.value = it }
    }

    /** Hides content from screenshots and the recent-apps preview when the user asks for it. */
    private fun applyWindowSecurity(s: AppSettings) {
        if (s.appLockEnabled && s.hideInRecents) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val desired = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
