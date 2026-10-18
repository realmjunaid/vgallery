package com.vgallery.app.ui.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vgallery.app.data.gallery.TRASH_TTL_MS
import com.vgallery.app.data.gallery.expiredTrashUris
import com.vgallery.app.ui.theme.TextSecondary

/**
 * Asks, once per launch, before permanently deleting trash that has sat for 30 days.
 *
 * Android will not delete another app's files from a background job, so the daily worker only
 * tidies entries whose files are already gone. The actual deletion happens here, and only after
 * the user says so — the system consent sheet follows.
 *
 * Draws nothing unless something has actually expired, and stays quiet while an update prompt is
 * up so the two never stack.
 */
@Composable
fun TrashPurgePrompt(
    enabled: Boolean,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val trashed by viewModel.trashed.collectAsStateWithLifecycle()
    val expired = remember(trashed) {
        expiredTrashUris(trashed, System.currentTimeMillis(), TRASH_TTL_MS)
    }
    var dismissed by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.answerConsent(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        viewModel.consentRequests.collect { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    if (!enabled || dismissed || expired.isEmpty()) return

    val count = expired.size
    val noun = if (count == 1) "item has" else "items have"
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Trash is due") },
        text = {
            Text(
                text = "$count $noun been in Trash for over 30 days. Delete them permanently? This cannot be undone.",
                color = TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                viewModel.purgeExpired()
            }) { Text("Delete now") }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Later") }
        },
    )
}

/** The same prompt with the decision already made, so a screenshot can show it without a store. */
@Composable
internal fun TrashPurgePromptContent(
    count: Int,
    onDelete: () -> Unit,
    onLater: () -> Unit,
) {
    val noun = if (count == 1) "item has" else "items have"
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Trash is due") },
        text = {
            Text(
                text = "$count $noun been in Trash for over 30 days. Delete them permanently? This cannot be undone.",
                color = TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete now") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Later") }
        },
    )
}
