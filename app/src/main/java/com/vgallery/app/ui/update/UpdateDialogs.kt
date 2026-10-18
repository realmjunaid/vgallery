package com.vgallery.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.ui.theme.SurfaceVariant
import com.vgallery.app.ui.theme.TextMuted
import com.vgallery.app.ui.theme.TextPrimary
import com.vgallery.app.ui.theme.TextSecondary
import com.vgallery.app.ui.theme.Violet400
import com.vgallery.app.ui.theme.Violet500
import kotlin.math.roundToInt

/**
 * The update prompt, rendered above everything so it can appear whichever screen the app opened on.
 *
 * "Update now" downloads and verifies the release, then opens Android's own installer — the one
 * confirmation the platform always asks for. "Cancel" on an automatic prompt is remembered for that
 * version, so the app asks once and then stays quiet until something newer exists.
 */
@Composable
fun UpdatePromptHost(
    state: UpdateState,
    currentVersionName: String,
    onUpdateNow: () -> Unit,
    onCancelPrompt: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    when (state) {
        UpdateState.Idle, UpdateState.Checking -> Unit

        is UpdateState.UpToDate -> UpdateDialog(
            onDismissRequest = onClose,
            title = "You're up to date",
            confirmLabel = "Close",
            onConfirm = onClose,
        ) {
            Text(
                text = "VGallery ${state.versionName} is the newest release.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is UpdateState.Available -> UpdateDialog(
            onDismissRequest = onCancelPrompt,
            title = "VGallery ${state.release.versionName} is available",
            confirmLabel = "Update now",
            onConfirm = onUpdateNow,
            dismissLabel = "Cancel",
            onDismiss = onCancelPrompt,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You have $currentVersionName. The download starts right away; Android " +
                        "asks for one last confirmation before installing.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                state.release.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        is UpdateState.Downloading -> UpdateDialog(
            onDismissRequest = onCancelDownload,
            title = "Downloading ${state.release.versionName}",
            confirmLabel = "Cancel",
            onConfirm = onCancelDownload,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.progress < 0f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet500,
                        trackColor = SurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet500,
                        trackColor = SurfaceVariant
                    )
                    Text(
                        text = "${(state.progress * 100f).roundToInt()}%",
                        color = Violet400,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "Keep VGallery open until the download finishes.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        is UpdateState.ReadyToInstall -> UpdateDialog(
            onDismissRequest = onClose,
            title = "Ready to install",
            confirmLabel = "Install now",
            onConfirm = onInstall,
            dismissLabel = "Later",
            onDismiss = onClose,
        ) {
            Text(
                text = "VGallery ${state.release.versionName} is downloaded and verified. Android " +
                    "will ask you to confirm the install.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is UpdateState.Failed -> UpdateDialog(
            onDismissRequest = onClose,
            title = "Update failed",
            confirmLabel = "Try again",
            onConfirm = onTryAgain,
            dismissLabel = "Close",
            onDismiss = onClose,
        ) {
            Text(
                text = state.message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** The one dialog shell every update step uses, so they all look like the rest of the app. */
@Composable
private fun UpdateDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(content = body)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Violet500)
            }
        },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, color = TextSecondary)
                }
            }
        } else {
            null
        }
    )
}

/** One line of the Settings row: what the updater is doing, in the user's words. */
fun updateStateSummary(state: UpdateState, currentVersionName: String): String = when (state) {
    UpdateState.Idle -> "Version $currentVersionName"
    UpdateState.Checking -> "Checking for updates\u2026"
    is UpdateState.UpToDate -> "Up to date"
    is UpdateState.Available -> "Version ${state.release.versionName} available"
    is UpdateState.Downloading -> {
        if (state.progress < 0f) "Downloading\u2026"
        else "Downloading\u2026 ${(state.progress * 100f).roundToInt()}%"
    }
    is UpdateState.ReadyToInstall -> "Ready to install"
    is UpdateState.Failed -> "Check failed"
}
