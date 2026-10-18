package com.vgallery.app.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vgallery.app.BuildConfig
import com.vgallery.app.ui.update.UpdateViewModel
import com.vgallery.app.ui.update.updateStateSummary
import com.vgallery.app.ui.theme.Black
import com.vgallery.app.ui.theme.SurfaceElevated
import com.vgallery.app.ui.theme.SurfaceVariant
import com.vgallery.app.ui.theme.TextDisabled
import com.vgallery.app.ui.theme.TextMuted
import com.vgallery.app.ui.theme.TextPrimary
import com.vgallery.app.ui.theme.TextSecondary
import com.vgallery.app.ui.theme.Violet400
import com.vgallery.app.ui.theme.Violet500

/** Which About page Settings is currently showing. */
private enum class AboutPage { APP, DEVELOPER }

/**
 * Settings: the updater, then the app's own About pages. Every playback preference the app ships
 * with is chosen for the common case, so there is nothing to tune — see
 * [com.vgallery.app.data.preferences.AppPreferences] for the stored defaults the rest of the app
 * still reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    SettingsScreen(
        onBack = onBack,
        updateSummary = updateStateSummary(updateState, viewModel.currentVersionName),
        autoCheckEnabled = autoCheck,
        onCheckNow = viewModel::checkNow,
        onAutoCheckChange = viewModel::setAutoCheckEnabled,
    )
}

/**
 * The screen itself, with everything hoisted: the Settings destination fills it from the updater,
 * and tests drive it with plain values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    updateSummary: String,
    autoCheckEnabled: Boolean,
    onCheckNow: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
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
                    onOpen = { page = it }
                )
                AboutPage.APP -> AboutAppPage()
                AboutPage.DEVELOPER -> AboutDeveloperPage()
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
) {
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
                "to play the whole feed one item after another."
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.PhotoLibrary,
            title = "Trash and move",
            body = "Deleting goes to a Google Photos style Trash first, restorable for 30 days. " +
                "Items can also be moved to another folder without leaving the app."
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
private fun AboutDeveloperPage() {
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
                    text = "realmjunaid",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Android developer \u2022 VGallery",
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

    SettingsSectionHeader("Project")

    SettingsCard {
        AboutBullet(
            icon = Icons.Filled.Code,
            title = "Source code",
            body = "The whole app is open: github.com/realmjunaid/vgallery \u2022 " +
                "gitlab.com/realmjunaid/vgallery"
        )
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        AboutBullet(
            icon = Icons.Filled.Person,
            title = "Contact",
            body = "Follow or reach out through the same account: realmjunaid on GitHub and GitLab. " +
                "Feature requests and bug reports are welcome there."
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
