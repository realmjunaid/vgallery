package com.vgallery.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
            val notes = state.release.notes?.takeIf { it.isNotBlank() }
            if (notes == null) {
                Text(
                    text = "The download starts right away; Android asks for one last confirmation " +
                        "before installing.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // Just the change list: the dialog says what is new and nothing else. The whole note
                // is there, scrolling inside a fixed box, because the eight-line cut it used to have
                // hid the part worth reading behind an ellipsis.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "What's new",
                        color = Violet400,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = releaseNotes(notes),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
            title = if (state.phase == UpdateState.Failed.Phase.CHECK) "Check failed" else "Update failed",
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
    is UpdateState.Failed ->
        if (state.phase == UpdateState.Failed.Phase.CHECK) "Check failed" else "Update failed"
}

/**
 * The release note as the dialog shows it, and the reason it is not shown raw.
 *
 * GitHub's markdown is reduced to what fits a phone: `**` becomes bold, `-` becomes a real bullet, a
 * heading loses its `#`, single `*` and backticks go away. The preamble paragraphs a release note
 * usually opens with are left out too, so the prompt is the change list rather than the announcement
 * — and a note that has no list at all is still shown, cleaned, instead of being hidden.
 */
internal fun releaseNotes(notes: String): AnnotatedString = buildAnnotatedString {
    val lines = notes.lines().map { it.trim() }
    val shown = lines.filter { isBullet(it) }.ifEmpty { lines.filter { it.isNotEmpty() } }
    var written = false
    shown.forEach { raw ->
        val line = raw.trimStart('#').trim()
        val bullet = isBullet(line)
        val body = (if (bullet) line.drop(2).trim() else line).replace(" - ", " \u2014 ")
        // Cleaned before anything is appended, so a line that reduces to nothing leaves no blank.
        val pieces = body.split("**").mapIndexedNotNull { index, part ->
            val bold = index % 2 == 1
            val text = (if (bold) part else part.replace("*", "")).replace("`", "")
            if (text.isBlank()) null else (if (bold) text.trim() else text) to bold
        }
        if (pieces.isEmpty()) return@forEach
        if (written) append('\n')
        written = true
        if (bullet) append("\u2022 ")
        pieces.forEach { (text, bold) ->
            if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) { append(text) }
            } else {
                append(text)
            }
        }
    }
}

/** A markdown list item: `- x`, `* x`, `• x` or `1. x`. */
private fun isBullet(line: String): Boolean =
    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("\u2022 ") ||
        (line.length > 2 && line[0].isDigit() && line[1] == '.')
