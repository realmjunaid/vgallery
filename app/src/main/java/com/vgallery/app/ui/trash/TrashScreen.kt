package com.vgallery.app.ui.trash

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.ui.gallery.StaticGalleryImage

/** One trashed item rendered as a grid tile. */
@Composable
private fun TrashTile(
    item: TrashedItem,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        StaticGalleryImage(
            uri = Uri.parse(item.uri),
            isVideo = item.isVideo,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        // Restore / Delete-forever overlay buttons.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Restore",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDeleteForever,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = "Delete forever",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val viewModel: TrashViewModel = hiltViewModel()
    val trashed by viewModel.trashed.collectAsState()
    var previewIndex by remember { mutableIntStateOf(-1) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmSingleDelete by remember { mutableStateOf<TrashedItem?>(null) }

    // System consent launcher for delete-forever (Android 10+).
    val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.answerConsent(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        viewModel.consentRequests.collect { sender ->
            consentLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trash",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        if (trashed.isNotEmpty()) {
                            Text(
                                text = "${trashed.size} items • auto-deletes after 30 days",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = "Empty trash",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (trashed.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Trash is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = "Deleted photos and videos stay here for 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(trashed, key = { _, item -> item.uri }) { index, item ->
                        TrashTile(
                            item = item,
                            onClick = { previewIndex = index },
                            onRestore = {
                                viewModel.restore(listOf(item.uri)) { _, _ -> }
                            },
                            onDeleteForever = { confirmSingleDelete = item }
                        )
                    }
                }
            }

            // Full-screen preview.
            if (previewIndex in trashed.indices) {
                val item = trashed[previewIndex]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { previewIndex = -1 }
                ) {
                    StaticGalleryImage(
                        uri = Uri.parse(item.uri),
                        isVideo = item.isVideo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.isVideo) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                        )
                    }

                    // Bottom action bar.
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                viewModel.restore(listOf(item.uri)) { _, _ -> }
                                previewIndex = -1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Restore", modifier = Modifier.padding(start = 6.dp))
                        }
                        Button(
                            onClick = { confirmSingleDelete = item },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Delete forever", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            // Empty-trash confirmation.
            if (confirmEmpty) {
                AlertDialog(
                    onDismissRequest = { confirmEmpty = false },
                    title = { Text("Empty trash?") },
                    text = { Text("All ${trashed.size} items will be permanently deleted. This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmEmpty = false
                                viewModel.emptyTrash { _, _ -> }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("Empty trash") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmEmpty = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Single delete-forever confirmation.
            confirmSingleDelete?.let { target ->
                AlertDialog(
                    onDismissRequest = { confirmSingleDelete = null },
                    title = { Text("Delete forever?") },
                    text = { Text("\"${target.name}\" will be permanently deleted. This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmSingleDelete = null
                                viewModel.deleteForever(listOf(Uri.parse(target.uri))) { _, _ -> }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("Delete forever") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmSingleDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
