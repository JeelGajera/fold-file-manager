package com.jeelgajera.fold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldAppBar
import com.jeelgajera.fold.core.design.component.FoldDock
import com.jeelgajera.fold.core.design.component.FoldDockTab
import com.jeelgajera.fold.core.design.component.FoldDrawer
import com.jeelgajera.fold.core.design.component.FoldDrawerItem
import com.jeelgajera.fold.core.design.component.FoldScaffold
import com.jeelgajera.fold.core.design.component.FoldSegmentedControl
import com.jeelgajera.fold.core.design.icon.FoldIconPaths
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.permission.StorageAccessLevel
import com.jeelgajera.fold.core.storage.provider.SafDocumentProvider
import com.jeelgajera.fold.core.storage.prefs.ThemeMode
import com.jeelgajera.fold.feature.browser.BrowseScreen
import com.jeelgajera.fold.feature.browser.BrowserViewModel
import com.jeelgajera.fold.feature.browser.HiddenFilesScreen
import com.jeelgajera.fold.feature.browser.HomeScreen
import com.jeelgajera.fold.feature.browser.LimitedAccessScreen
import com.jeelgajera.fold.feature.browser.OnboardingScreen
import com.jeelgajera.fold.feature.browser.SettingsScreen
import com.jeelgajera.fold.feature.browser.SettingsViewModel
import com.jeelgajera.fold.feature.glyph.GlyphDetection
import com.jeelgajera.fold.feature.glyph.GlyphHardware
import com.jeelgajera.fold.feature.glyph.ui.GlyphSettingsScreen
import com.jeelgajera.fold.feature.search.SearchScreen
import com.jeelgajera.fold.feature.transfer.ShareSheet
import com.jeelgajera.fold.feature.transfer.ui.ShareScreen
import com.jeelgajera.fold.feature.vault.VaultScreen
import com.jeelgajera.fold.feature.browser.R as BrowserR

/** Every place the app can be. */
private enum class Destination(val isDockTab: Boolean = false) {
    HOME(isDockTab = true),
    BROWSE(isDockTab = true),
    SHARE(isDockTab = true),
    VAULT(isDockTab = true),
    SEARCH,
    HIDDEN,
    LIMITED,
    SETTINGS,
    GLYPH,
    ONBOARDING,
    ;

    /** Which dock tab lights up while this destination is showing. */
    val dockIndex: Int
        get() = when (this) {
            HOME -> 0
            BROWSE, SEARCH, HIDDEN, LIMITED -> 1
            SHARE -> 2
            VAULT -> 3
            SETTINGS, GLYPH, ONBOARDING -> -1
        }
}

/**
 * The shell: app bar, floating dock, drawer, and whichever screen is showing.
 *
 * Navigation is a single enum rather than a `NavHost`. That is a deliberate
 * choice for a ten-destination app with no deep links between screens: a nav
 * graph would add a serialisation layer, an argument-passing convention and a
 * back-stack model to solve problems this app does not have. Back is handled
 * explicitly below, which is the one thing a nav graph would have given for free.
 */
@Composable
fun FoldApp(
    onRequestAllFilesAccess: () -> Unit,
    onPickFolder: () -> Unit,
    onVaultVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val accessLevel = remember { StorageAccess.level(context) }
    var destination by remember {
        mutableStateOf(
            // A phone with nothing granted has exactly one useful screen.
            if (accessLevel == StorageAccessLevel.NONE) Destination.ONBOARDING else Destination.HOME
        )
    }
    var drawerOpen by remember { mutableStateOf(false) }

    // FLAG_SECURE follows the vault screen in and out, rather than being set for
    // the whole app.
    DisposableEffect(destination) {
        onVaultVisibilityChanged(destination == Destination.VAULT)
        onDispose { }
    }

    // Back walks up the folder tree first, then out of a sub-screen, then closes
    // the drawer -- in the order a user expects rather than the order the enum
    // happens to be declared in.
    BackHandler(enabled = drawerOpen || destination != Destination.HOME) {
        when {
            drawerOpen -> drawerOpen = false
            destination == Destination.BROWSE -> if (!browserViewModel.goUp()) {
                destination = Destination.HOME
            }
            destination == Destination.HOME -> Unit
            else -> destination = Destination.HOME
        }
    }

    FoldScaffold(
        appBar = {
            FoldAppBar(
                meta = destination.headerMeta(),
                onSearch = { destination = Destination.SEARCH },
                onMenu = { drawerOpen = true },
                searchContentDescription = stringResource(BrowserR.string.app_bar_search),
                menuContentDescription = stringResource(BrowserR.string.app_bar_menu),
            )
        },
        dock = {
            FoldDock(
                tabs = dockTabs(),
                selectedIndex = destination.dockIndex.coerceAtLeast(0),
                onSelect = { index ->
                    destination = when (index) {
                        0 -> Destination.HOME
                        1 -> Destination.BROWSE
                        2 -> Destination.SHARE
                        else -> Destination.VAULT
                    }
                },
            )
        },
        drawer = {
            AppDrawer(
                open = drawerOpen,
                current = destination,
                onSelect = { next ->
                    destination = next
                    drawerOpen = false
                },
                onDismiss = { drawerOpen = false },
            )
        },
    ) {
        when (destination) {
            Destination.HOME -> HomeScreen(
                onOpenCategory = { destination = Destination.BROWSE },
                onOpenPath = { path ->
                    browserViewModel.open(path)
                    destination = Destination.BROWSE
                },
                onBrowseAll = { destination = Destination.BROWSE },
                viewModel = browserViewModel,
            )

            Destination.BROWSE -> BrowseScreen(
                onOpenFile = { entry -> openWithSystem(context, entry) },
                onShareSelection = { paths -> share(context, paths) },
                onOpenHidden = { destination = Destination.HIDDEN },
                viewModel = browserViewModel,
            )

            Destination.SEARCH -> SearchScreen(
                onOpenPath = { path ->
                    browserViewModel.open(path)
                    destination = Destination.BROWSE
                },
            )

            Destination.SHARE -> ShareScreen(
                onQuickShare = { share(context, browserViewModel.selectedPaths()) },
            )

            Destination.VAULT -> VaultScreen(
                onMoveFilesIn = { destination = Destination.BROWSE },
            )

            Destination.HIDDEN -> HiddenFilesScreen(viewModel = browserViewModel)

            Destination.LIMITED -> LimitedAccessScreen(
                roots = remember { SafDocumentProvider.persistedRoots(context) },
                canRequestAllFiles = StorageAccess.canRequestAllFilesAccess(context),
                onRequestAllFiles = onRequestAllFilesAccess,
                onAddFolder = onPickFolder,
            )

            Destination.SETTINGS -> SettingsScreen(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toString(),
                onOpenAllFilesAccess = onRequestAllFilesAccess,
                onOpenLimited = { destination = Destination.LIMITED },
                onOpenHidden = { destination = Destination.HIDDEN },
                onOpenGlyph = { destination = Destination.GLYPH },
                onOpenWidgets = { drawerOpen = true },
                onOpenTheme = { drawerOpen = true },
                viewModel = settingsViewModel,
            )

            Destination.GLYPH -> GlyphSettingsScreen()

            Destination.ONBOARDING -> OnboardingScreen(
                canRequestAllFiles = StorageAccess.canRequestAllFilesAccess(context),
                onRequestAllFiles = onRequestAllFilesAccess,
                onPickFolders = onPickFolder,
                onSkip = { destination = Destination.LIMITED },
            )
        }
    }
}

@Composable
private fun AppDrawer(
    open: Boolean,
    current: Destination,
    onSelect: (Destination) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = FoldTheme.colors

    // The glyph entry is hidden entirely on hardware that has none, rather than
    // shown greyed out. A control for something the device cannot do is noise.
    val hasGlyph = remember { GlyphDetection.potentialHardware(context) != GlyphHardware.NONE }

    val items = buildList {
        add(FoldDrawerItem(stringResource(BrowserR.string.drawer_settings), "", Destination.SETTINGS.name))
        if (hasGlyph) {
            add(FoldDrawerItem(stringResource(BrowserR.string.drawer_glyph), "", Destination.GLYPH.name))
        }
        add(FoldDrawerItem(stringResource(BrowserR.string.drawer_share), "", Destination.SHARE.name))
        add(FoldDrawerItem(stringResource(BrowserR.string.drawer_vault), "", Destination.VAULT.name))
        add(FoldDrawerItem(stringResource(BrowserR.string.drawer_hidden), "", Destination.HIDDEN.name))
        add(FoldDrawerItem(stringResource(BrowserR.string.drawer_limited), "", Destination.LIMITED.name))
        add(
            FoldDrawerItem(
                stringResource(BrowserR.string.drawer_onboarding),
                stringResource(BrowserR.string.drawer_replay),
                Destination.ONBOARDING.name,
            )
        )
    }

    FoldDrawer(
        open = open,
        items = items,
        selectedKey = current.name,
        onSelect = { key -> onSelect(Destination.valueOf(key)) },
        onDismiss = onDismiss,
        scrimContentDescription = stringResource(BrowserR.string.app_bar_close_menu),
        header = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "FOLD ${BuildConfig.VERSION_NAME}",
                    style = FoldTheme.typography.label,
                    color = colors.onBackground.copy(alpha = 0.5f),
                )
            }
        },
        footer = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(BrowserR.string.drawer_theme),
                    style = FoldTheme.typography.labelS,
                    color = colors.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                ThemeControl()
            }
        },
    )
}

@Composable
private fun ThemeControl(viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    FoldSegmentedControl(
        options = listOf(
            stringResource(BrowserR.string.drawer_theme_light),
            stringResource(BrowserR.string.drawer_theme_dark),
            stringResource(BrowserR.string.drawer_theme_system),
        ),
        selectedIndex = when (preferences.themeMode) {
            ThemeMode.LIGHT -> 0
            ThemeMode.DARK -> 1
            ThemeMode.SYSTEM -> 2
        },
        onSelect = { index ->
            viewModel.setThemeMode(
                when (index) {
                    0 -> ThemeMode.LIGHT
                    1 -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            )
        },
    )
}

@Composable
private fun dockTabs(): List<FoldDockTab> = listOf(
    FoldDockTab(stringResource(BrowserR.string.tab_home), FoldIconPaths.Home),
    FoldDockTab(stringResource(BrowserR.string.tab_browse), FoldIconPaths.Browse),
    FoldDockTab(stringResource(BrowserR.string.tab_share), FoldIconPaths.Share),
    FoldDockTab(
        stringResource(BrowserR.string.tab_vault),
        FoldIconPaths.Vault,
        FoldIconPaths.VaultFilled,
    ),
)

@Composable
private fun Destination.headerMeta(): String = when (this) {
    Destination.HOME -> "INTERNAL"
    Destination.BROWSE -> "BROWSE"
    Destination.SEARCH -> "SEARCH"
    Destination.SHARE -> "WI-FI"
    Destination.VAULT -> "VAULT"
    Destination.HIDDEN -> "DOT FILES"
    Destination.LIMITED -> "LIMITED"
    Destination.SETTINGS -> "SETTINGS"
    Destination.GLYPH -> "GLYPH"
    Destination.ONBOARDING -> "SETUP"
}

/**
 * Hands a file to whatever app can open it.
 *
 * The MIME type is resolved by FOLD -- sniffing the content when the extension is
 * unknown -- which is exactly what makes a `.md` note open in a note app instead
 * of producing "no app can perform this action".
 */
private fun openWithSystem(context: android.content.Context, entry: FsEntry) {
    val intent = ShareSheet.viewIntent(context, entry.path) ?: return
    runCatching { context.startActivity(intent) }
}

private fun share(context: android.content.Context, paths: List<FsPath>) {
    if (paths.isEmpty()) return
    val intent = ShareSheet.forFiles(context, paths) ?: return
    runCatching { context.startActivity(intent) }
}
