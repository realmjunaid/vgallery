package com.vgallery.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vgallery.app.BuildConfig
import com.vgallery.app.data.gallery.AUTOPLAY_DEFAULT_SECONDS
import com.vgallery.app.data.gallery.AUTOPLAY_MAX_SECONDS
import com.vgallery.app.data.gallery.AUTOPLAY_MIN_SECONDS
import com.vgallery.app.data.preferences.AccentChoice
import com.vgallery.app.data.preferences.ThemeMode
import com.vgallery.app.data.preferences.ThemeSettings
import com.vgallery.app.data.update.UPDATE_REPO
import com.vgallery.app.ui.update.UpdateViewModel
import com.vgallery.app.ui.update.updateStateSummary
import com.vgallery.app.ui.theme.Black
import com.vgallery.app.ui.theme.LocalGalleryColors
import com.vgallery.app.ui.theme.OnAccent
import com.vgallery.app.ui.theme.SurfaceElevated
import com.vgallery.app.ui.theme.SurfaceVariant
import com.vgallery.app.ui.theme.TextDisabled
import com.vgallery.app.ui.theme.TextMuted
import com.vgallery.app.ui.theme.TextPrimary
import com.vgallery.app.ui.theme.TextSecondary
import com.vgallery.app.ui.theme.Violet400
import com.vgallery.app.ui.theme.Violet500
import com.vgallery.app.ui.theme.galleryColors

/** Which About page Settings is currently showing. */
private enum class AboutPage { APP, DEVELOPER }

/** The public project: GitHub holds the releases the updater reads and the issue tracker. */
private const val SOURCE_REPO_URL = "https://github.com/" + UPDATE_REPO

/** The developer's own site, linked from About Developer. */
private const val DEVELOPER_WEBSITE_URL = "https://mjunaid.me"

/** Hands a link to whatever app handles it, and says so rather than crashing when nothing does. */
private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Nothing can open $url", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Settings: appearance, playback pace, the library's own switches, the updater and the app's About
 * pages. Everything tuned here is stored once and read back by the screens that use it — the
 * gallery draws with the grouping switch and the autoplay speed, and the theme repaints the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTrashClick: () -> Unit = {},
    viewModel: UpdateViewModel = hiltViewModel(),
    themeViewModel: ThemeSettingsViewModel = hiltViewModel(),
    libraryViewModel: LibrarySettingsViewModel = hiltViewModel(),
) {
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val appearance by themeViewModel.theme.collectAsStateWithLifecycle()
    val groupSimilarFolders by libraryViewModel.groupSimilarFolders.collectAsStateWithLifecycle()
    val autoplaySeconds by libraryViewModel.autoplaySeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        onBack = onBack,
        updateSummary = updateStateSummary(updateState, viewModel.currentVersionName),
        autoCheckEnabled = autoCheck,
        onCheckNow = viewModel::checkNow,
        onAutoCheckChange = viewModel::setAutoCheckEnabled,
        onOpenLink = { openLink(context, it) },
        appearance = appearance,
        onThemeModeChange = themeViewModel::setMode,
        onAccentChange = themeViewModel::setAccent,
        onAmoledChange = themeViewModel::setAmoledBlack,
        onTrashClick = onTrashClick,
        groupSimilarFolders = groupSimilarFolders,
        onGroupSimilarFoldersChange = libraryViewModel::setGroupSimilarFolders,
        autoplaySeconds = autoplaySeconds,
        onAutoplaySecondsChange = libraryViewModel::setAutoplaySeconds,
    )
}

/**
 * The screen itself, with everything hoisted: the Settings destination fills it from the updater,
 * the appearance store and the library store, and tests drive it with plain values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    updateSummary: String,
    autoCheckEnabled: Boolean,
    onCheckNow: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit = {},
    appearance: ThemeSettings = ThemeSettings(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAccentChange: (AccentChoice) -> Unit = {},
    onAmoledChange: (Boolean) -> Unit = {},
    onTrashClick: () -> Unit = {},
    groupSimilarFolders: Boolean = false,
    onGroupSimilarFoldersChange: (Boolean) -> Unit = {},
    autoplaySeconds: Int = AUTOPLAY_DEFAULT_SECONDS,
    onAutoplaySecondsChange: (Int) -> Unit = {},
) {
    var page by remember { mutableStateOf<AboutPage?>(null) }

    // Back inside an About page returns to Settings instead of leaving it.
    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (page) {
                            null -> "Settings"
                            AboutPage.APP -> "About"
                            AboutPage.DEVELOPER -> "About Developer"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (page == null) onBack() else page = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (page == null) "Back" else "Back to settings",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when (page) {
                null -> SettingsMenu(
                    updateSummary = updateSummary,
                    autoCheckEnabled = autoCheckEnabled,
                    onCheckNow = onCheckNow,
                    onAutoCheckChange = onAutoCheckChange,
                    onOpen = { page = it },
                    appearance = appearance,
                    onThemeModeChange = onThemeModeChange,
                    onAccentChange = onAccentChange,
                    onAmoledChange = onAmoledChange,
                    onTrashClick = onTrashClick,
                    groupSimilarFolders = groupSimilarFolders,
                    onGroupSimilarFoldersChange = onGroupSimilarFoldersChange,
                    autoplaySeconds = autoplaySeconds,
                    onAutoplaySecondsChange = onAutoplaySecondsChange,
                )
                AboutPage.APP -> AboutAppPage()
                AboutPage.DEVELOPER -> AboutDeveloperPage(onOpenLink = onOpenLink)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VGallery",
                        color = Violet500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        color = TextDisabled,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsMenu(
    updateSummary: String,
    autoCheckEnabled: Boolean,
    onCheckNow: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onOpen: (AboutPage) -> Unit,
    appearance: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentChoice) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onTrashClick: () -> Unit,
    groupSimilarFolders: Boolean,
    onGroupSimilarFoldersChange: (Boolean) -> Unit,
    autoplaySeconds: Int,
    onAutoplaySecondsChange: (Int) -> Unit,
) {
    SettingsSectionHeader("Appearance")

    SettingsCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SettingsRowTitle(
                icon = Icons.Filled.Contrast,
                title = "Theme",
                subtitle = "Light, dark, or whatever your phone is set to"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ThemeModeRow(selected = appearance.mode, onSelect = onThemeModeChange)
        }

        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SettingsRowTitle(
                icon = Icons.Filled.Palette,
                title = "Accent",
                subtitle = "Monochrome is VGallery's own look; System uses your wallpaper"
            )
            Spacer(modifier = Modifier.height(12.dp))
            AccentRow(selected = appearance.accent, onSelect = onAccentChange)
        }

        // Meaningless on a light background, so it is only offered where it does something.
        if (appearance.mode != ThemeMode.LIGHT) {
            HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)

            SettingsSwitchItem(
                icon = Icons.Filled.DarkMode,
                title = "Pure black",
                subtitle = "AMOLED: the darkest background the screen can draw",
                checked = appearance.amoledBlack,
                onCheckedChange = onAmoledChange
            )
        }
    }

    SettingsSectionHeader("Playback")

    SettingsCard {
        SettingsStepperItem(
            icon = Icons.Filled.PlayArrow,
            title = "Autoplay speed",
            subtitle = "Seconds each photo stays before autoplay glides on. " +
                "A video always plays to its own end.",
            value = "${autoplaySeconds}s",
            canDecrease = autoplaySeconds > AUTOPLAY_MIN_SECONDS,
            canIncrease = autoplaySeconds < AUTOPLAY_MAX_SECONDS,
            decreaseContentDescription = "Faster autoplay",
            increaseContentDescription = "Slower autoplay",
            onDecrease = { onAutoplaySecondsChange(autoplaySeconds - 1) },
            onIncrease = { onAutoplaySecondsChange(autoplaySeconds + 1) },
        )
    }

    SettingsSectionHeader("Library")

    SettingsCard {
        SettingsSwitchItem(
            icon = Icons.Filled.Folder,
            title = "Group similar folders",
            subtitle = "Home: folders whose names match open under one heading",
            checked = groupSimilarFolders,
            onCheckedChange = onGroupSimilarFoldersChange
        )

        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)

        SettingsClickItem(
            icon = Icons.Filled.Delete,
            title = "Trash",
            subtitle = "Deleted items, restorable for 30 days",
            onClick = onTrashClick
        )
    }

    SettingsSectionHeader("Updates")

    SettingsCard {
        SettingsClickItem(
            icon = Icons.Filled.Update,
            title = "Check for updates",
            subtitle = updateSummary,
            onClick = onCheckNow
        )

        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)

        SettingsSwitchItem(
            icon = Icons.Filled.Autorenew,
            title = "Automatic update check",
            subtitle = "Asks GitHub for a new release when the app opens",
            checked = autoCheckEnabled,
            onCheckedChange = onAutoCheckChange
        )
    }

    SettingsSectionHeader("About")

    SettingsCard {
        SettingsClickItem(
            icon = Icons.Filled.PhotoLibrary,
            title = "About VGallery",
            subtitle = "Version, features and privacy",
            onClick = { onOpen(AboutPage.APP) }
        )

        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)

        SettingsClickItem(
            icon = Icons.Filled.Person,
            title = "About Developer",
            subtitle = "Who makes VGallery",
            onClick = { onOpen(AboutPage.DEVELOPER) }
        )
    }
}

/** Icon + title + one-line explanation: the head of an Appearance row. */
@Composable
private fun SettingsRowTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun AccentChoice.label(): String = when (this) {
    AccentChoice.MONOCHROME -> "Monochrome"
    AccentChoice.VIOLET -> "Violet"
    AccentChoice.BLUE -> "Blue"
    AccentChoice.GREEN -> "Green"
    AccentChoice.AMBER -> "Amber"
    AccentChoice.PINK -> "Pink"
    AccentChoice.SYSTEM -> "System"
}

/** System / Light / Dark, as three equal pills: the chosen one is filled with the accent. */
@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Violet500 else SurfaceElevated)
                    .clickable { onSelect(mode) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${mode.label()} theme"
                        this.selected = active
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) OnAccent else TextSecondary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * One dot per accent, each drawn in the colour it would actually use in this theme, so the choice
 * is visible before it is made.
 */
@Composable
private fun AccentRow(selected: AccentChoice, onSelect: (AccentChoice) -> Unit) {
    val dark = LocalGalleryColors.current.isDark
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccentChoice.entries.forEach { choice ->
            val palette = galleryColors(dark = dark, amoled = true, accent = choice)
            val active = choice == selected
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .border(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) TextPrimary else TextDisabled,
                        shape = CircleShape
                    )
                    .clickable { onSelect(choice) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${choice.label()} accent"
                        this.selected = active
                    },
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutAppPage() {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = Violet400,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VGallery",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }

    SettingsSectionHeader("What's inside")

    SettingsCard {
        AboutBullet(
            icon = Icons.Filled.Folder,
            title = "Folder-wise gallery",
            body = "Every album on the device, newest folders first, with item count and folder size. " +
                "Photos and Videos tabs keep each kind one tap away, and every folder opens as a grid or a list."
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.PlayArrow,
            title = "Continuous viewer",
            body = "Scroll every photo and video in one feed, pinch to zoom 1x-10x, and tap Autoplay " +
                "to watch the whole feed: each item glides in a screen at a time, and how long a " +
                "photo holds is yours to set in Settings."
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.PhotoLibrary,
            title = "Trash and move",
            body = "Deleting goes to a Google Photos style Trash first, restorable for 30 days — " +
                "the Trash lives in Settings. Items can also be moved to another folder without " +
                "leaving the app."
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.Update,
            title = "Updates from inside the app",
            body = "When a new release is out, VGallery says so and can download and install it " +
                "without a browser. Android asks for one confirmation before installing."
        )
    }

    SettingsSectionHeader("Privacy")

    SettingsCard {
        AboutBullet(
            icon = Icons.Filled.WifiOff,
            title = "Online for updates only",
            body = "The only network call VGallery makes is the update check: it asks GitHub for " +
                "the newest release tag and, if you tap Update now, downloads that APK. No photo, " +
                "file name or identifier is ever sent, and there is no account, ad or analytics. " +
                "Turn off Automatic update check and the app makes no network call at all."
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.Lock,
            title = "Your photos stay yours",
            body = "The app only reads the media on this device through Android's MediaStore. " +
                "Albums, Trash and settings are stored locally, and uninstalling removes them."
        )
    }
}

@Composable
private fun AboutDeveloperPage(onOpenLink: (String) -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Violet400,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "M Junaid",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Android developer \u2022 mjunaid.me",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }

    SettingsSectionHeader("About the developer")

    SettingsCard {
        Text(
            text = "VGallery is made by one developer who wanted a gallery that simply shows " +
                "your photos and videos. No account, no ads, no feed, no uploads \u2014 just the " +
                "albums already on the device, opened fast.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        Text(
            text = "Ideas and bug reports from real use shape what gets built next: folder-wise " +
                "albums, the Trash, move-to-folder, the continuous viewer, pinch zoom and autoplay " +
                "all came from that feedback. If something feels wrong, tell me and it gets fixed " +
                "in the next build.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }

    SettingsSectionHeader("Developer")

    SettingsCard {
        SettingsClickItem(
            icon = Icons.Filled.Language,
            title = "mjunaid.me",
            subtitle = "Website \u2014 opens in your browser",
            onClick = { onOpenLink(DEVELOPER_WEBSITE_URL) }
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        SettingsClickItem(
            icon = Icons.Filled.Code,
            title = "Source code",
            subtitle = "github.com/$UPDATE_REPO \u2014 opens on GitHub",
            onClick = { onOpenLink(SOURCE_REPO_URL) }
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        SettingsClickItem(
            icon = Icons.Filled.Email,
            title = "Contact",
            subtitle = "Bug reports and ideas on the issue tracker",
            onClick = { onOpenLink("$SOURCE_REPO_URL/issues") }
        )
    }
}

@Composable
private fun AboutBullet(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Violet400,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Black,
                checkedTrackColor = Violet500,
                checkedBorderColor = Violet500,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceElevated,
                uncheckedBorderColor = TextDisabled
            )
        )
    }
}

@Composable
private fun SettingsStepperItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = decreaseContentDescription,
                tint = if (canDecrease) TextPrimary else TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 34.dp)
        )
        IconButton(
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = increaseContentDescription,
                tint = if (canIncrease) TextPrimary else TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(20.dp)
        )
    }
}
