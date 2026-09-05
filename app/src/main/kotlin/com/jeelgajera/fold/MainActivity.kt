package com.jeelgajera.fold

import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jeelgajera.fold.core.crypto.VaultRepository
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.FoldThemeMode
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.core.storage.prefs.ThemeMode
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import com.jeelgajera.fold.core.storage.provider.SafDocumentProvider
import com.jeelgajera.fold.core.storage.index.IndexWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single activity.
 *
 * `FragmentActivity` rather than `ComponentActivity` because `BiometricPrompt`
 * hosts a fragment; that is a hard requirement of the vault, not a preference.
 *
 * Two lifecycle responsibilities live here and nowhere else:
 *
 * - **Permission drift.** All Files Access can be revoked from system settings
 *   while FOLD is in the background. `onResume` re-reads it and swaps the
 *   provider, so the app degrades to SAF mid-session rather than throwing
 *   SecurityExceptions out of screens that assumed raw access.
 * - **The vault's foreground rule.** The vault locks the moment the app stops
 *   being visible. "I switched apps for a second" is precisely when someone else
 *   picks the phone up.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var providerFactory: FileSystemProviderFactory

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var vault: VaultRepository

    /** Tracks the applied FLAG_SECURE state so the window is not re-flagged on every recomposition. */
    private var secureFlagApplied = false

    /**
     * The SAF folder picker, for limited-access mode.
     *
     * The grant is persisted immediately -- without that it dies with the
     * process, and asking again on every launch is the single most irritating
     * thing a SAF-based file manager does.
     */
    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching { SafDocumentProvider.persistGrant(this, uri) }
            providerFactory.refresh()
        }
    }

    /**
     * The All Files Access round trip.
     *
     * The system screen returns no result, so the grant is re-read on resume
     * rather than from a callback.
     */
    private val requestAllFiles = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (providerFactory.refresh() && StorageAccess.hasAllFilesAccess()) {
            // Newly granted: build the index now rather than waiting for the next
            // idle-and-charging window, so search works straight away.
            IndexWorker.runNow(this)
            IndexWorker.schedulePeriodic(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeFlow = remember { settings.settings.map { it.themeMode } }
            val themeMode by themeFlow.collectAsStateWithLifecycle(ThemeMode.SYSTEM)

            FoldTheme(
                mode = when (themeMode) {
                    ThemeMode.LIGHT -> FoldThemeMode.LIGHT
                    ThemeMode.DARK -> FoldThemeMode.DARK
                    ThemeMode.SYSTEM -> FoldThemeMode.SYSTEM
                },
                reducedMotion = isReducedMotion(),
            ) {
                FoldApp(
                    onRequestAllFilesAccess = ::openAllFilesAccess,
                    onPickFolder = { pickFolder.launch(null) },
                    onVaultVisibilityChanged = ::applyVaultSecureFlag,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission can change while the app is backgrounded, and the whole
        // provider abstraction exists so this is a swap rather than a crash.
        providerFactory.refresh()
    }

    override fun onStop() {
        super.onStop()
        // The vault closes when the app is no longer in front. Not on a timer, not
        // on a back press -- on losing the foreground.
        vault.lock()
    }

    /**
     * Screenshot and recents protection for the vault.
     *
     * `FLAG_SECURE` blocks screenshots, screen recording and the recents
     * thumbnail in one move. It is applied only while a vault screen is showing,
     * because applying it app-wide would break legitimate screenshots of a folder
     * listing and train people to work around it.
     */
    private fun applyVaultSecureFlag(visible: Boolean) {
        if (secureFlagApplied == visible) return
        secureFlagApplied = visible
        if (visible) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun openAllFilesAccess() {
        val primary = StorageAccess.allFilesAccessIntent(this)
        val intent = if (primary.resolveActivity(packageManager) != null) {
            primary
        } else {
            StorageAccess.allFilesAccessFallbackIntent()
        }
        if (intent.resolveActivity(packageManager) != null) {
            requestAllFiles.launch(intent)
            lifecycleScope.launch { settings.setAllFilesAccessAsked(true) }
        }
    }

    /**
     * Whether the system has been asked to remove animations.
     *
     * Read from `ANIMATOR_DURATION_SCALE` rather than from a Compose API because
     * there is no Compose API for it. A scale of zero is the platform's "remove
     * animations" accessibility setting.
     */
    private fun isReducedMotion(): Boolean = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Settings.SettingNotFoundException) {
        false
    }
}
