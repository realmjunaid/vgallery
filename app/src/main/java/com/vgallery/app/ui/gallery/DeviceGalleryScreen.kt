package com.vgallery.app.ui.gallery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.DrawableImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.decode.BitmapFactoryDecoder
import kotlin.math.abs
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.DeviceFolderTarget
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.gallery.formatBytes
import com.vgallery.app.data.gallery.filterAlbumsByName
import com.vgallery.app.data.gallery.homeSummary
import com.vgallery.app.data.gallery.formatMediaDate
import com.vgallery.app.data.gallery.formatResolution
import com.vgallery.app.data.gallery.hasDeviceGalleryPermission
import com.vgallery.app.ui.theme.Black
import com.vgallery.app.ui.theme.ErrorRed
import com.vgallery.app.ui.theme.NewFolderDot
import com.vgallery.app.ui.theme.OnAccent
import com.vgallery.app.ui.theme.SurfaceCard
import com.vgallery.app.ui.theme.SurfaceElevated
import com.vgallery.app.ui.theme.SurfaceVariant
import com.vgallery.app.ui.theme.TextDisabled
import com.vgallery.app.ui.theme.TextMuted
import com.vgallery.app.ui.theme.TextPrimary
import com.vgallery.app.ui.theme.TextSecondary
import com.vgallery.app.ui.theme.Violet400
import com.vgallery.app.ui.theme.Violet500
import com.vgallery.app.ui.theme.DarkViewerTheme
import com.vgallery.app.ui.theme.ViewerBackdrop

/**
 * Auto device gallery (Google Photos style): no manual folder pick.
 * Level 1 = folder-wise albums from MediaStore buckets.
 * Level 2 = photo grid of one album (vertical scroll). Tap a photo to open the
 * full view: a Drive-PDF style continuous vertical feed, every photo full-width.
 *
 * When [isHome] is true this is the app's home screen: no back button,
 * VGallery branding + settings action in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGalleryScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    isHome: Boolean = false,
    viewModel: DeviceGalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var viewerIndex by remember { mutableIntStateOf(-1) }
    // One continuous feed for everything: photos, GIFs, animated WebPs, videos.
    // Search narrows the open album; grid and viewer both show the same filtered list.
    val visiblePhotos = viewModel.visiblePhotos(uiState.photos, uiState.searchQuery)

    // Multi-select state: long-press folders on home, long-press photos in an album.
    var selectedFolders by remember { mutableStateOf(setOf<String>()) }
    var selectedPhotoIds by remember { mutableStateOf(setOf<Long>()) }
    // Home search (icon toggles the field): matches photos across every folder.
    var showHomeSearch by remember { mutableStateOf(false) }
    var homeQuery by remember { mutableStateOf("") }
    // Folder view mode: the default grid, or a line-by-line list that also shows name and size.
    var listMode by rememberSaveable { mutableStateOf(false) }
    // One grid state per home tab, owned here — not inside AlbumGrid. Opening a folder removes
    // that grid from composition; a state created inside it would be thrown away and Back would
    // land at the top. rememberSaveable also keeps the offset across rotation and process death.
    val photosAlbumGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val videosAlbumGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val albumGridState = if (uiState.selectedTab == HomeTab.VIDEOS) {
        videosAlbumGridState
    } else {
        photosAlbumGridState
    }
    // Search results are a different, shorter list. Sharing the tab's state would clamp it and
    // lose the place the user left the real folder grid.
    val searchAlbumGridState = remember(uiState.selectedTab) { LazyGridState() }
    // Per open folder, so a full-view overlay (and a rotation) does not throw the grid away.
    val folderGridState = rememberSaveable(uiState.selectedAlbum, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val folderListState = rememberSaveable(uiState.selectedAlbum, saver = LazyListState.Saver) {
        LazyListState()
    }
    // Shared delete targets (grid, folders, albums) with one confirm dialog.
    var deleteTargets by remember { mutableStateOf<List<DevicePhotoRow>?>(null) }
    var infoTargets by remember { mutableStateOf<List<DevicePhotoRow>?>(null) }
    // Items queued for "Move to folder", plus the destinations once they finish loading.
    var moveItems by remember { mutableStateOf<List<DevicePhotoRow>?>(null) }
    var moveFolderOptions by remember { mutableStateOf<List<DeviceFolderTarget>?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun selectedAlbumRows(): List<DevicePhotoRow> =
        uiState.photos.filter { it.id in selectedPhotoIds }

    /** Whatever is currently selected: whole folders on home, single items inside a folder. */
    fun selectionRows(): List<DevicePhotoRow> =
        if (selectedFolders.isNotEmpty()) viewModel.photosInFolders(selectedFolders)
        else selectedAlbumRows()

    fun performDelete(targets: List<DevicePhotoRow>) {
        viewModel.moveToTrash(targets) { ok, msg ->
            toast(msg)
            if (ok) {
                selectedPhotoIds = emptySet()
                selectedFolders = emptySet()
                viewModel.refreshAfterDelete()
            }
        }
    }

    fun startMove(targets: List<DevicePhotoRow>) {
        if (targets.isEmpty()) return
        moveItems = targets
        moveFolderOptions = null
        viewModel.loadMoveTargets { moveFolderOptions = it }
    }

    fun performMove(targets: List<DevicePhotoRow>, target: DeviceFolderTarget) {
        viewModel.moveToFolder(targets, target) { ok, msg ->
            toast(msg)
            if (ok) {
                selectedPhotoIds = emptySet()
                selectedFolders = emptySet()
                moveItems = null
                moveFolderOptions = null
                viewModel.refreshAfterDelete()
            }
        }
    }

    // System consent dialog for moves (Android 10+): launched here, answered in the VM.
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

    // Viewer source: album-filtered list normally, global search results from home search.
    var searchViewerPhotos by remember { mutableStateOf<List<DevicePhotoRow>?>(null) }
    val viewerPhotos = searchViewerPhotos ?: visiblePhotos

    // If a delete shrinks the list under the open viewer, close it instead of crashing.
    LaunchedEffect(viewerPhotos.size) {
        if (viewerIndex >= viewerPhotos.size) viewerIndex = -1
    }

    fun dismissViewer() {
        viewerIndex = -1
        searchViewerPhotos = null
    }

    // System back: close viewer first, then clear selections, then back to albums.
    BackHandler(enabled = viewerIndex >= 0) {
        viewerIndex = -1
    }
    BackHandler(enabled = viewerIndex < 0 && selectedPhotoIds.isNotEmpty()) {
        selectedPhotoIds = emptySet()
    }
    BackHandler(enabled = viewerIndex < 0 && selectedPhotoIds.isEmpty() && selectedFolders.isNotEmpty()) {
        selectedFolders = emptySet()
    }
    BackHandler(enabled = viewerIndex < 0 && selectedPhotoIds.isEmpty() && selectedFolders.isEmpty() && uiState.selectedAlbum != null) {
        viewModel.backToAlbums()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // The result map can carry a partial grant — Android 13 lets one of the two granular
        // permissions be denied, and Android 14's "Select photos" grants only
        // READ_MEDIA_VISUAL_USER_SELECTED — so ask the device what it actually allowed instead of
        // reading the map, and show whatever it can read rather than nothing at all.
        viewModel.refresh(hasDeviceGalleryPermission(context))
    }

    LaunchedEffect(Unit) {
        val has = hasDeviceGalleryPermission(context)
        if (!has) {
            permissionLauncher.launch(viewModel.requiredPermissions())
        }
        viewModel.refresh(has)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Black,
        topBar = {
            // A selection replaces the app bar instead of being stacked under it: two bars
            // pushed the grid down and left a big empty gap at the top.
            val foldersSelected = selectedFolders.isNotEmpty()
            if (foldersSelected || selectedPhotoIds.isNotEmpty()) {
                SelectionBar(
                    count = if (foldersSelected) selectedFolders.size else selectedPhotoIds.size,
                    noun = if (foldersSelected) "folders" else "items",
                    onShare = { sharePhotos(context, selectionRows()) },
                    onMove = { startMove(selectionRows()) },
                    onInfo = { infoTargets = selectionRows() },
                    onDelete = { deleteTargets = selectionRows() },
                    onClear = {
                        selectedFolders = emptySet()
                        selectedPhotoIds = emptySet()
                    }
                )
            } else {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isHome) {
                                uiState.selectedAlbum ?: "VGallery"
                            } else {
                                uiState.selectedAlbum ?: "Device Photos"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.selectedAlbum == null && uiState.totalCount > 0) {
                            // Items, folders, then how much space all of it takes — the aggregate
                            // the folder cards show one by one, so the home header is a real total
                            // instead of a mode label.
                            Text(
                                text = homeSummary(
                                    items = uiState.totalCount,
                                    folders = uiState.albums.size,
                                    totalBytes = uiState.albums.sumOf { it.sizeBytes }
                                ),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isHome) {
                        IconButton(onClick = {
                            if (uiState.selectedAlbum != null) viewModel.backToAlbums() else onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    if (isHome && uiState.selectedAlbum == null && selectedFolders.isEmpty()) {
                        IconButton(onClick = {
                            showHomeSearch = !showHomeSearch
                            if (!showHomeSearch) homeQuery = ""
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextSecondary)
                        }
                    }
                    if (isHome && selectedFolders.isEmpty()) {
                        // Grid <-> line-by-line list for the folder being viewed, next to Delete.
                        if (uiState.selectedAlbum != null) {
                            IconButton(onClick = { listMode = !listMode }) {
                                Icon(
                                    imageVector = if (listMode) {
                                        Icons.Filled.GridView
                                    } else {
                                        Icons.AutoMirrored.Filled.ViewList
                                    },
                                    contentDescription = if (listMode) "Show as grid" else "Show as list",
                                    tint = TextSecondary
                                )
                            }
                        }
                        IconButton(onClick = onTrashClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Trash",
                                tint = TextSecondary
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                !uiState.hasPermission && !uiState.isLoading -> PermissionPrompt(
                    onGrant = { permissionLauncher.launch(viewModel.requiredPermissions()) }
                )
                // A refresh must not swap the grid for a spinner: that unmounts it, and even a
                // remembered scroll state cannot show through an empty frame.
                uiState.isLoading && (
                    (uiState.selectedAlbum == null && uiState.albums.isEmpty()) ||
                        (uiState.selectedAlbum != null && uiState.photos.isEmpty())
                    ) -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Violet500)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Scanning device photos...", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                uiState.selectedAlbum == null -> {
                    // Tabs stay visible even when a tab has nothing, so an empty Photos
                    // tab can never trap you away from the Videos tab.
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isHome) {
                            HomeTabs(
                                selected = uiState.selectedTab,
                                onSelect = { viewModel.selectTab(it) }
                            )
                        }
                        if (uiState.albums.isEmpty()) {
                            EmptyState(
                                message = "No ${uiState.selectedTab.label.lowercase()} found on this device"
                            )
                        } else {
                            if (showHomeSearch) {
                                OutlinedTextField(
                                    value = homeQuery,
                                    onValueChange = { homeQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    placeholder = {
                                        Text(
                                            text = "Search folders",
                                            color = TextMuted
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted)
                                    },
                                    trailingIcon = {
                                        if (homeQuery.isNotEmpty()) {
                                            IconButton(onClick = { homeQuery = "" }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                            if (homeQuery.isBlank()) {
                                AlbumGrid(
                                    albums = uiState.albums,
                                    newFolders = uiState.newFolders,
                                    state = albumGridState,
                                    selected = selectedFolders,
                                    onAlbumClick = { album ->
                                        if (selectedFolders.isNotEmpty()) {
                                            selectedFolders = selectedFolders.toggle(album.name)
                                        } else {
                                            selectedPhotoIds = emptySet()
                                            viewModel.openAlbum(album.name)
                                        }
                                    },
                                    onAlbumLongClick = { album ->
                                        selectedFolders = selectedFolders + album.name
                                    }
                                )
                            } else {
                                // A query on home looks for *folders*, never loose files: this
                                // screen is a folder picker, so "IMG_2024" finds nothing on purpose
                                // and a folder name opens straight into its contents.
                                val matchingFolders = filterAlbumsByName(uiState.albums, homeQuery)
                                if (matchingFolders.isEmpty()) {
                                    EmptyState(message = "No folders match \"${homeQuery.trim()}\"")
                                } else {
                                    AlbumGrid(
                                        albums = matchingFolders,
                                        newFolders = uiState.newFolders,
                                        state = searchAlbumGridState,
                                        selected = selectedFolders,
                                        onAlbumClick = { album ->
                                            if (selectedFolders.isNotEmpty()) {
                                                selectedFolders = selectedFolders.toggle(album.name)
                                            } else {
                                                selectedPhotoIds = emptySet()
                                                viewModel.openAlbum(album.name)
                                            }
                                        },
                                        onAlbumLongClick = { album ->
                                            selectedFolders = selectedFolders + album.name
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.photos.isEmpty()) {
                        EmptyState(message = "No media in this folder")
                    } else {
                        // Grid or line-by-line list: whichever the folder's toggle last chose.
                        val onPhotoClick: (Int) -> Unit = { gridIndex ->
                            val id = visiblePhotos.getOrNull(gridIndex)?.id
                            if (id != null) {
                                if (selectedPhotoIds.isNotEmpty()) {
                                    selectedPhotoIds = selectedPhotoIds.toggle(id)
                                } else {
                                    searchViewerPhotos = null
                                    viewerIndex = gridIndex
                                }
                            }
                        }
                        val onPhotoLongClick: (Int) -> Unit = { gridIndex ->
                            visiblePhotos.getOrNull(gridIndex)?.let {
                                selectedPhotoIds = selectedPhotoIds + it.id
                            }
                        }
                        if (listMode) {
                            MediaList(
                                photos = visiblePhotos,
                                state = folderListState,
                                selectedIds = selectedPhotoIds,
                                onPhotoClick = onPhotoClick,
                                onPhotoLongClick = onPhotoLongClick
                            )
                        } else {
                            PhotoGrid(
                                photos = visiblePhotos,
                                state = folderGridState,
                                selectedIds = selectedPhotoIds,
                                onPhotoClick = onPhotoClick,
                                onPhotoLongClick = onPhotoLongClick
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Full view: continuous vertical feed with everything, overlaid on the gallery.
    // A dark island: the viewer is black in every theme, because a photograph judged against a
    // light backdrop reads wrong — but it keeps the accent the user chose.
    if (viewerIndex >= 0 && viewerIndex < viewerPhotos.size) {
        DarkViewerTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ViewerBackdrop)
            ) {
                DevicePhotoViewer(
                    photos = viewerPhotos,
                    startIndex = viewerIndex,
                    title = uiState.selectedAlbum ?: "",
                    onDismiss = { dismissViewer() }
                )
            }
        }
    }

    // Shared delete confirmation for grid + folder selections.
    DeleteConfirmDialog(
        targets = deleteTargets,
        onConfirm = { targets ->
            deleteTargets = null
            performDelete(targets)
        },
        onDismiss = { deleteTargets = null }
    )

    // Folder picker for "Move to folder".
    moveItems?.let { items ->
        MoveFolderDialog(
            itemCount = items.size,
            targets = moveFolderOptions,
            onPick = { target -> performMove(items, target) },
            onDismiss = {
                moveItems = null
                moveFolderOptions = null
            }
        )
    }

    // Shared details sheet for grid + folder selections.
    infoTargets?.let { infos ->
        ModalBottomSheet(
            onDismissRequest = { infoTargets = null },
            containerColor = SurfaceVariant
        ) {
            MediaDetailsSheet(photos = infos)
        }
    }
    } // root Box
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Allow photo access",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "VGallery will auto-show all device photos folder-wise, like Google Photos. No manual folder pick needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Violet500)
            ) {
                Text("Allow access", color = OnAccent)
            }
            // Android stops showing the dialog after "Don't ask again", and a device policy can block
            // it from the start — without this the only way out would be reinstalling.
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { openAppSettings(context) }) {
                Text("Open app settings", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

/** Sends the user to this app's system settings page, the fallback when the dialog won't show. */
internal fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * A thumbnail that never animates: GIF/animated WebP stays frozen on its first
 * frame (only the full-view middle image plays). Saves battery in grids.
 */
@Composable
internal fun StaticGalleryImage(
    uri: android.net.Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isVideo: Boolean = false
) {
    // Images: BitmapFactoryDecoder decodes the first frame only, so GIF/animated
    // WebP can never animate here (the stop() below is just belt-and-braces).
    // Videos: the default chain decodes a frame thumbnail via VideoFrameDecoder.
    val context = LocalContext.current
    val request = remember(uri, isVideo) {
        ImageRequest.Builder(context)
            .data(uri)
            .apply { if (!isVideo) decoderFactory(BitmapFactoryDecoder.Factory()) }
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    // NOTE: painter.state is a StateFlow — it must be collected, plain reads never update.
    val painterState by painter.state.collectAsState()
    LaunchedEffect(painterState) {
        ((painterState as? AsyncImagePainter.State.Success)?.result?.image as? DrawableImage)
            ?.drawable?.let { (it as? Animatable)?.stop() }
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
internal fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = TextMuted)
        }
    }
}

private val HomeTab.label: String
    get() = when (this) {
        HomeTab.PHOTOS -> "Photos"
        HomeTab.VIDEOS -> "Videos"
    }

private val HomeTab.icon: ImageVector
    get() = when (this) {
        HomeTab.PHOTOS -> Icons.Filled.PhotoLibrary
        HomeTab.VIDEOS -> Icons.Filled.Videocam
    }

/**
 * Home's two tabs: Photos (still images) and Videos (videos, GIFs, animated WebPs).
 * Switching happens from the cached scan, so there is no rescan and no spinner.
 */
@Composable
internal fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = Color.Transparent,
        contentColor = Violet400
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Tab(
                selected = isSelected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = Violet400,
                unselectedContentColor = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumGrid(
    albums: List<DeviceAlbum>,
    newFolders: Set<String>,
    onAlbumClick: (DeviceAlbum) -> Unit,
    selected: Set<String>,
    onAlbumLongClick: (DeviceAlbum) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.name }) { album ->
            val isSelected = album.name in selected
            Column(
                modifier = Modifier
                    .animateItem()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Violet500, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = { onAlbumClick(album) },
                        onLongClick = { onAlbumLongClick(album) }
                    )
                    .padding(12.dp)
            ) {
                Box {
                    StaticGalleryImage(
                        uri = album.coverUri,
                        isVideo = album.coverIsVideo,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariant)
                    )
                    // A folder added since the last scan carries a green dot until it is opened.
                    if (album.name in newFolders) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NewFolderDot)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Violet400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = album.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "${album.count} items", color = TextMuted, fontSize = 12.sp)
                    if (album.sizeBytes > 0L) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatBytes(album.sizeBytes),
                            color = TextMuted.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoGrid(
    photos: List<DevicePhotoRow>,
    onPhotoClick: (Int) -> Unit,
    selectedIds: Set<Long>,
    onPhotoLongClick: (Int) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(photos.size, key = { photos[it].id }) { index ->
            val photo = photos[index]
            val isSelected = photo.id in selectedIds
            Box(
                modifier = Modifier
                    .animateItem()
                    .aspectRatio(1f)
                    .background(SurfaceVariant)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Violet500) else Modifier
                    )
                    .combinedClickable(
                        onClick = { onPhotoClick(index) },
                        onLongClick = { onPhotoLongClick(index) }
                    )
            ) {
                StaticGalleryImage(
                    uri = photo.uri,
                    isVideo = photo.isVideo,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (photo.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                    if (photo.durationMs > 0) {
                        Text(
                            text = formatGridDuration(photo.durationMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        )
                    }
                }
                // GIF / animated-WebP chip: proves the app recognized it as playable.
                if (!photo.isVideo && (photo.mimeType == "image/gif" || photo.isAnimatedWebp)) {                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "GIF",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * The same folder contents as [PhotoGrid], one file per line: a thumbnail, the file's title and
 * its size, so a folder reads like a file manager. Tapping and long-pressing behave exactly like
 * the grid, so selection, Trash and Move work the same here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaList(
    photos: List<DevicePhotoRow>,
    onPhotoClick: (Int) -> Unit,
    selectedIds: Set<Long>,
    onPhotoLongClick: (Int) -> Unit,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            val isSelected = photo.id in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) Violet500.copy(alpha = 0.18f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onPhotoClick(index) },
                        onLongClick = { onPhotoLongClick(index) }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant)
                ) {
                    StaticGalleryImage(
                        uri = photo.uri,
                        isVideo = photo.isVideo,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (photo.isVideo) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatBytes(photo.size),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Violet500,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        }
    }
}

private fun formatGridDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * MediaStore's aspect ratio (width / height), used to lay a feed item out *before* its bitmap
 * decodes. Without it every item starts at zero height and the whole list re-measures as the
 * images land — that reflow is what made long flings stutter. Clamped so a bogus row can never
 * produce an absurd item; null when the device reports no usable size.
 */
private fun knownAspectRatio(width: Int, height: Int): Float? {
    if (width <= 0 || height <= 0) return null
    return (width.toFloat() / height.toFloat()).coerceIn(0.25f, 4f)
}

/** Autoplay: how long a still photo (or an animation) holds the screen before the feed moves on. */
private const val AUTO_PLAY_PHOTO_MS = 3_500L

/** Autoplay: safety net for a video that never reports its end (a codec the device can't finish). */
private const val AUTO_PLAY_VIDEO_FALLBACK_MS = 30_000L

/**
 * Global feed gesture, Google Drive PDF-viewer style.
 *
 * Runs on the Initial pass so it decides before the item's tap/play detector and before the
 * feed's scroll. Two fingers pinch the whole feed about the centroid; while zoomed, a sideways
 * drag pans. Vertical drags are never consumed, so the feed keeps scrolling through every item —
 * including during a diagonal drag, which pans and scrolls at the same time. At 1x only
 * two-finger pinches are handled, so one-finger drags stay plain list scrolling.
 */
private fun Modifier.feedZoomGesture(state: FeedZoomState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                state.pinch(event.calculateZoom(), event.calculateCentroid().x)
                event.changes.forEach { it.consume() }
            } else if (pressed == 1 && state.isZoomed) {
                event.changes.firstOrNull { it.pressed }?.let { change ->
                    // Read raw positions: a diagonal drag may already be consumed by the feed's
                    // scroll, and positionChange() would then report zero and stop the pan.
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    if (dx != 0f) {
                        state.panBy(dx)
                        // Consume only a sideways drag; a diagonal drag still scrolls vertically.
                        if (abs(dx) > abs(dy)) change.consume()
                    }
                }
            }
        }
    }
}

/**
 * One video inside the continuous feed: full-width like every photo, as many
 * per screen as fit. A poster frame shows until the item is tapped; tapping binds
 * the shared player and plays, everything else stays paused.
 */
@Composable
private fun FeedVideoItem(
    photo: DevicePhotoRow,
    isPlaying: Boolean,
    uiVisible: Boolean,
    exoPlayer: ExoPlayer,
    videoAspects: MutableMap<Long, Float>,
    onTogglePlay: () -> Unit,
    onDoubleTapChrome: () -> Unit
) {
    // MediaStore's own shape first, so the box is the right size before anything decodes and the
    // feed never re-measures mid-fling; the player's report takes over once it plays.
    val aspect = videoAspects[photo.id]
        ?: knownAspectRatio(photo.width, photo.height)
        ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black)
            .clipToBounds()
    ) {
        // Poster frame underneath, always visible: it shows while the video loads and whenever
        // this is not the item being played.
        StaticGalleryImage(
            uri = photo.uri,
            isVideo = true,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (!isPlaying) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            )
            if (photo.durationMs > 0) {
                Text(
                    text = formatGridDuration(photo.durationMs),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }
        // The player exists only while this is the item being played. An idle PlayerView drew its
        // own black shutter over the poster — which is why videos stayed black — and a stack of
        // them made long flings stutter.
        if (isPlaying) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // A SurfaceView inside a scrolling list can lag behind the view hierarchy;
                        // this is media3's own workaround for exactly that when hosted in Compose.
                        setEnableComposeSurfaceSyncWorkaround(true)
                        // No black shutter: the poster stays visible until the first frame lands.
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView -> playerView.player = exoPlayer },
                onRelease = { playerView -> playerView.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        // Transparent tap layer above the player (Android views swallow touches,
        // so without this, taps on a video never toggle playback).
        // Double-tap anywhere toggles the chrome via the feed's own detector.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onTap = { onTogglePlay() },
                        onDoubleTap = { onDoubleTapChrome() }
                    )
                }
        )
        // While this is the item playing, the time left counts down in the corner — the length
        // itself is already on the poster before playback starts, so repeating it here would just
        // take room away from the picture.
        if (isPlaying) {
            var positionMs by remember { mutableLongStateOf(0L) }
            var reportedMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(exoPlayer, photo.id) {
                while (true) {
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val reported = exoPlayer.duration
                    reportedMs = if (reported > 0L) reported else 0L
                    delay(200)
                }
            }
            val totalMs = if (reportedMs > 0L) reportedMs else photo.durationMs
            if (totalMs > 0L) {
                Text(
                    text = formatGridDuration((totalMs - positionMs).coerceAtLeast(0L)),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }
        if (uiVisible) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePhotoViewer(
    photos: List<DevicePhotoRow>,
    startIndex: Int,
    title: String,
    onDismiss: () -> Unit,
    viewModel: DeviceGalleryViewModel = hiltViewModel()
) {
    // Drive-PDF style: one continuous vertical feed, every photo full-width back to back,
    // starting at the tapped photo. Square photos stack several per screen.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    )
    // Side counter + center detection: the item closest to the viewport middle.
    // Only that item plays (animated WebP/GIF); the rest stay paused.
    val currentIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: 0
        }
    }
    // Decode at 2x screen width (aspect kept): visually identical on-screen,
    // but a fraction of the memory — this is what makes the feed butter smooth.
    // Full originals stay untouched in storage; nothing is compressed there.
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = remember(configuration, density) {
        with(density) { (configuration.screenWidthDp.dp * 2).roundToPx() }.coerceAtLeast(1)
    }
    // Double-tap toggles all chrome (top bar + bottom title) for pure-image viewing.
    var uiVisible by remember { mutableStateOf(true) }
    // One global pinch-zoom (1x-10x) owned by the feed, Google Drive PDF-viewer style.
    val feedZoom = remember { FeedZoomState() }
    // derivedStateOf so the feed only recomposes when the zoom *starts or ends*, never on every
    // pinch frame; at 1x the list then needs no render layer at all.
    val zoomed by remember { derivedStateOf { feedZoom.isZoomed } }
    // Fresh toggle inside gesture detectors (pointerInput blocks outlive recompositions).
    val toggleChrome = rememberUpdatedState({ uiVisible = !uiVisible })
    // Playback follows the tapped item (Google Photos style) unless Autoplay is running, which
    // walks the whole feed: videos to their end, stills on a timer.
    var playingIndex by remember {
        mutableIntStateOf(startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    }
    // Autoplay: plays the feed one item after another — videos to their end, stills and
    // animations on a timer — centring each item as it goes.
    var autoPlay by remember { mutableStateOf(false) }
    // Delete / info UI state.
    var pendingDelete by remember { mutableStateOf<DevicePhotoRow?>(null) }
    var infoPhoto by remember { mutableStateOf<DevicePhotoRow?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /** Moves items to Trash and toasts the outcome. */
    fun performDelete(targets: List<DevicePhotoRow>) {
        viewModel.moveToTrash(targets) { ok, msg ->
            toast(msg)
            if (ok) viewModel.refreshAfterDelete()
        }
    }

    fun confirmDelete(photo: DevicePhotoRow) {
        pendingDelete = photo
    }

    /** Autoplay step: hand playback to the next item (the feed then centres it). */
    fun advanceAutoPlay() {
        if (!autoPlay) return
        val next = playingIndex + 1
        if (next > photos.lastIndex) {
            autoPlay = false
            toast("Autoplay finished")
            return
        }
        playingIndex = next
    }

    /** Tap on one item: play it, or pause it — pausing also stops an autoplay run. */
    fun togglePlay(index: Int) {
        if (index == playingIndex) {
            playingIndex = -1
            autoPlay = false
        } else {
            playingIndex = index
        }
    }

    /** The autoplay button: start from whatever the feed is showing now, or stop the run. */
    fun toggleAutoPlay() {
        if (autoPlay) {
            autoPlay = false
            return
        }
        if (photos.isNotEmpty()) playingIndex = currentIndex
        autoPlay = true
    }

    // The player callback outlives recomposition, so it always runs the newest step.
    val advanceAutoPlayRef = rememberUpdatedState(newValue = { advanceAutoPlay() })
    // One shared player for inline videos: the tapped video binds and plays,
    // everything else stays paused. Aspect is learned per video once it plays.
    val videoAspects = remember { mutableStateMapOf<Long, Float>() }
    var playingVideoId by remember { mutableLongStateOf(-1L) }
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspects[playingVideoId] =
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // A finished video hands over to the next item while autoplay runs
                // (advanceAutoPlay is a no-op when it is off).
                if (playbackState == Player.STATE_ENDED) advanceAutoPlayRef.value()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
    LaunchedEffect(playingIndex) {
        val item = photos.getOrNull(playingIndex)
        if (item?.isVideo == true) {
            playingVideoId = item.id
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            playingVideoId = -1L
            exoPlayer.pause()
        }
    }
    // Autoplay pacing: a still (or an animation) holds the screen for a moment, while a video is
    // advanced by the player the instant it ends. The video timer below is only a safety net for
    // one that never reports its end — a codec the device cannot decode to the last frame.
    LaunchedEffect(autoPlay, playingIndex) {
        if (!autoPlay) return@LaunchedEffect
        val item = photos.getOrNull(playingIndex) ?: return@LaunchedEffect
        val startedAt = playingIndex
        val waitMs = if (item.isVideo) {
            (item.durationMs.takeIf { it > 0L } ?: AUTO_PLAY_VIDEO_FALLBACK_MS) + 2_000L
        } else {
            AUTO_PLAY_PHOTO_MS
        }
        delay(waitMs)
        // Still on the same item after the wait: nothing ended on its own, so move on.
        if (playingIndex == startedAt) advanceAutoPlay()
    }
    /** Scrolls the rest of the way until [index] sits centred, or does nothing when it already is. */
    suspend fun glideToCentre(index: Int) {
        val info = listState.layoutInfo
        val viewport = info.viewportSize.height
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val delta = (viewport - item.size) / 2 - item.offset
        if (abs(delta) > 1) listState.animateScrollBy(delta.toFloat())
    }

    /**
     * Brings [index] to the middle of the screen in one movement. The old version scrolled the item
     * to the top first and then walked it down to the centre, which read as a jolt in the middle of
     * playback — the whole point here is that nothing moves unless it is toward its final place.
     */
    suspend fun centrePlayingItem(index: Int) {
        val info = listState.layoutInfo
        val viewport = info.viewportSize.height
        if (viewport <= 0) return
        // Already laid out — the item can be measured, so the distance to the centre is exact.
        if (info.visibleItemsInfo.any { it.index == index }) {
            glideToCentre(index)
            return
        }
        // Off screen: an item's shape says where it will land, which is good to within a pixel for
        // photos. A portrait photo is taller than the screen, so this is the ordinary case, not an
        // edge case — and any residue the shape got wrong is smoothed out right after.
        val width = info.viewportSize.width
        val photo = photos.getOrNull(index)
        val aspect = photo?.let {
            if (it.isVideo) videoAspects[it.id] ?: knownAspectRatio(it.width, it.height)
            else knownAspectRatio(it.width, it.height)
        }
        val height = if (aspect != null && aspect > 0f && width > 0) (width / aspect).toInt() else 0
        if (height in 1 until viewport) {
            listState.animateScrollToItem(index, -((viewport - height) / 2))
            glideToCentre(index)
        } else {
            listState.animateScrollToItem(index)
        }
    }
    // Autoplay's presentation position: whatever is playing sits in the middle of the screen.
    // Separate from the pacing above so the wait starts with the scroll, not after it.
    LaunchedEffect(autoPlay, playingIndex) {
        if (autoPlay && playingIndex >= 0) centrePlayingItem(playingIndex)
    }
    // Immersive mode: hiding the UI also hides status + navigation bars,
    // so the whole display shows only images. Restored on dismiss.
    val view = LocalView.current
    LaunchedEffect(uiVisible) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, view)
        if (uiVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            (view.context as? Activity)?.let { activity ->
                WindowInsetsControllerCompat(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title.ifBlank { "Photos" },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${photos.size} photos • scroll vertically",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Autoplay sits with the per-item actions. It is the one that looks
                        // different while it is on — pause bar in the accent colour — so a feed
                        // that is walking itself is never a mystery.
                        if (photos.isNotEmpty()) {
                            IconButton(onClick = { toggleAutoPlay() }) {
                                Icon(
                                    imageVector = if (autoPlay) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    contentDescription = if (autoPlay) "Stop autoplay" else "Autoplay",
                                    tint = if (autoPlay) Violet400 else Color.White
                                )
                            }
                        }
                        photos.getOrNull(currentIndex)?.let { current ->
                            IconButton(onClick = { sharePhotos(context, listOf(current)) }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { infoPhoto = current }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Details",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { confirmDelete(current) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f))
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .clipToBounds()
                .feedZoomGesture(feedZoom)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { toggleChrome.value() })
                }
        ) {
            LazyColumn(
                state = listState,
                // One global pinch-zoom drives the whole feed: the scaled layer below zooms every
                // item together while this list keeps handling vertical scrolling. The layer is
                // only attached while zoomed — an identity layer would still be paid for on
                // every frame of a normal scroll.
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { feedZoom.onViewport(it.width) }
                    .then(
                        if (zoomed) {
                            Modifier.graphicsLayer {
                                scaleX = feedZoom.scale
                                scaleY = feedZoom.scale
                                translationX = feedZoom.offsetX
                                translationY = 0f
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    val isPlaying = index == playingIndex
                    if (photo.isVideo) {
                        FeedVideoItem(
                            photo = photo,
                            isPlaying = isPlaying,
                            uiVisible = uiVisible,
                            exoPlayer = exoPlayer,
                            videoAspects = videoAspects,
                            onTogglePlay = { togglePlay(index) },
                            onDoubleTapChrome = { toggleChrome.value() }
                        )
                    } else {
                    // Animated formats decode at 1x screen width: every frame is
                    // CPU-decoded, so a lighter decode is what makes playback smooth.
                    // Static photos keep 2x for sharp 10x zooming.
                    // INEXACT precision forces the fast power-of-2 decode path
                    // instead of a slow full-size decode + software scale-down.
                    val decodeWidth = if (photo.needsLightDecode) targetWidthPx / 2 else targetWidthPx
                    val request = remember(photo.id, decodeWidth) {
                        ImageRequest.Builder(context)
                            .data(photo.uri)
                            .size(Dimension.Pixels(decodeWidth), Dimension.Undefined)
                            .precision(Precision.INEXACT)
                            .crossfade(false)
                            .build()
                    }
                    val painter = rememberAsyncImagePainter(model = request)
                    // NOTE: painter.state is a StateFlow — it must be collected.
                    val painterState by painter.state.collectAsState()
                    // Only the opened/tapped photo plays; scrolling never auto-starts others.
                    LaunchedEffect(painterState, isPlaying) {
                        val drawable = ((painterState as? AsyncImagePainter.State.Success)
                            ?.result?.image as? DrawableImage)
                            ?.drawable as? Animatable
                            ?: return@LaunchedEffect
                        if (isPlaying) {
                            if (!drawable.isRunning) drawable.start()
                        } else {
                            drawable.stop()
                        }
                    }
                    // Direct platform decode for animated formats: ImageDecoder always
                    // returns a real AnimatedImageDrawable (no Coil pipeline ambiguity).
                    // Static photos keep the Coil painter above.
                    val useDirectDecode = Build.VERSION.SDK_INT >= 28 &&
                        (photo.mimeType == "image/gif" || photo.isAnimatedWebp)
                    var directDrawable by remember(photo.id) { mutableStateOf<Drawable?>(null) }
                    LaunchedEffect(photo.id, useDirectDecode) {
                        directDrawable = null
                        if (!useDirectDecode) return@LaunchedEffect
                        directDrawable = withContext(Dispatchers.IO) {
                            try {
                                val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
                                ImageDecoder.decodeDrawable(source)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    LaunchedEffect(directDrawable, isPlaying) {
                        val anim = directDrawable as? Animatable ?: return@LaunchedEffect
                        if (isPlaying) {
                            if (!anim.isRunning) anim.start()
                        } else {
                            anim.stop()
                        }
                    }
                    DisposableEffect(directDrawable) {
                        onDispose { (directDrawable as? Animatable)?.stop() }
                    }
                    // Hold the item's MediaStore shape until its pixels (or its animation) are
                    // ready: an unresolved image measures to no height, so the whole feed used to
                    // re-measure as bitmaps landed — that churn is what made long flings stutter.
                    val placeholderAspect = remember(photo.id) {
                        knownAspectRatio(photo.width, photo.height)
                    }
                    val contentReady = painterState is AsyncImagePainter.State.Success ||
                        directDrawable != null
                    // Tap a photo to play it (GIF/WebP/video). Chrome toggles
                    // with double-tap (also here: child taps never reach the feed).
                    val tapModifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray)
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onTap = { togglePlay(index) },
                                onDoubleTap = { toggleChrome.value() }
                            )
                        }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!contentReady && placeholderAspect != null) {
                                    Modifier.aspectRatio(placeholderAspect)
                                } else {
                                    Modifier
                                }
                            )
                            .clipToBounds()
                    ) {
                    // The image layer; the feed zooms it along with every other item.
                    Box(modifier = Modifier.fillMaxWidth()) {
                    if (directDrawable != null) {
                        // Animated GIF/WebP in a platform ImageView: animation
                        // callbacks are native, so playback just works.
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    adjustViewBounds = true
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            update = { view ->
                                if (view.drawable !== directDrawable) {
                                    view.setImageDrawable(directDrawable)
                                }
                            },
                            modifier = tapModifier
                        )
                    } else {
                        Image(
                            painter = painter,
                            contentDescription = photo.name,
                            contentScale = ContentScale.FillWidth,
                            modifier = tapModifier
                        )
                    }
                    }
                    // Explicit play/pause for animated photos (tapping the GIF itself
                    // also plays it). Visible only with the chrome.
                    if (uiVisible && (photo.mimeType == "image/gif" || photo.isAnimatedWebp)) {
                        val playing = index == playingIndex
                        IconButton(
                            onClick = { togglePlay(index) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    }
                    }
                }
            }
            // Small side counter, plain text, no background — e.g. 23/100.
            if (photos.isNotEmpty()) {
                Text(
                    text = "${(currentIndex + 1).coerceAtMost(photos.size)} / ${photos.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                )
            }
            // The current item's name, bottom centre; it fades with the chrome.
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                photos.getOrNull(currentIndex)?.let { current ->
                    Text(
                        text = current.name,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(1f, 1f),
                                blurRadius = 3f
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation.
    pendingDelete?.let { target ->
        DeleteConfirmDialog(
            targets = listOf(target),
            onConfirm = { performDelete(listOf(target)) },
            onDismiss = { pendingDelete = null }
        )
    }

    // Details bottom sheet: size, resolution, date and more.
    infoPhoto?.let { info ->
        ModalBottomSheet(
            onDismissRequest = { infoPhoto = null },
            containerColor = SurfaceVariant
        ) {
            MediaDetailsSheet(photos = listOf(info))
        }
    }
}

/** System share sheet for one or many photos/videos. */
internal fun sharePhotos(context: android.content.Context, photos: List<DevicePhotoRow>) {
    if (photos.isEmpty()) return
    val mimes = photos.map { it.mimeType.ifBlank { "*/*" } }.toSet()
    val send = if (photos.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimes.single()
            putExtra(Intent.EXTRA_STREAM, photos.single().uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (mimes.size == 1) mimes.single() else "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val title = if (photos.size == 1) "Share via" else "Share ${photos.size} items via"
    // A bare device can have no share sheet at all; the button must not take the app down with it.
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
        .onFailure { Toast.makeText(context, "No app can share this", Toast.LENGTH_SHORT).show() }
}

/** Folder picker for "Move to folder": every destination is a folder the gallery already shows. */
@Composable
private fun MoveFolderDialog(
    itemCount: Int,
    targets: List<DeviceFolderTarget>?,
    onPick: (DeviceFolderTarget) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = {
            Text(
                text = if (itemCount > 1) "Move $itemCount items to" else "Move to folder",
                color = TextPrimary
            )
        },
        text = {
            when {
                targets == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Violet400,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading folders\u2026",
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                targets.isEmpty() -> Text("No folders found on this device.", color = TextSecondary)
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(targets, key = { it.relativePath }) { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(target) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = Violet400)
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = target.displayName,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (target.count == 1) "1 item" else "${target.count} items",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Shared delete confirmation for viewer, grid and folder selections. */
@Composable
private fun DeleteConfirmDialog(
    targets: List<DevicePhotoRow>?,
    onConfirm: (List<DevicePhotoRow>) -> Unit,
    onDismiss: () -> Unit
) {
    targets?.takeIf { it.isNotEmpty() }?.let { list ->
        val title = if (list.size == 1) {
            val one = list.single()
            "Move this ${if (one.isVideo) "video" else "photo"} to trash?"
        } else {
            "Move ${list.size} items to trash?"
        }
        val message = if (list.size == 1) {
            "\"${list.single().name}\" will be moved to Trash. You can restore it within 30 days."
        } else {
            "The selected items will be moved to Trash. You can restore them within 30 days."
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = SurfaceVariant,
            title = { Text(title, color = TextPrimary) },
            text = { Text(message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onConfirm(list) }) {
                    Text("Move to trash", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/** Contextual bar for multi-select: count + share/info/delete/clear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBar(
    count: Int,
    noun: String,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$count $noun selected",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear selection",
                    tint = TextPrimary
                )
            }
        },
        actions = {
            IconButton(onClick = onMove) {
                Icon(Icons.Filled.Folder, contentDescription = "Move", tint = TextPrimary)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = TextPrimary)
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, contentDescription = "Details", tint = TextPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

@Composable
private fun MediaDetailsSheet(photos: List<DevicePhotoRow>) {
    if (photos.size == 1) {
        SingleMediaDetailsSheet(photo = photos.single())
        return
    }
    val totalSize = photos.sumOf { it.size }
    val videos = photos.count { it.isVideo }
    val images = photos.size - videos
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "${photos.size} items selected",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        MediaDetailRow(label = "Photos", value = images.toString())
        MediaDetailRow(label = "Videos", value = videos.toString())
        MediaDetailRow(label = "Total size", value = formatBytes(totalSize))
    }
}

@Composable
private fun SingleMediaDetailsSheet(photo: DevicePhotoRow) {
    val kind = when {
        photo.isVideo -> "Video"
        photo.mimeType == "image/gif" -> "GIF"
        photo.isAnimatedWebp -> "Animated WebP"
        photo.mimeType.startsWith("image/") -> "Photo"
        else -> "Media"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = photo.name,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        MediaDetailRow(label = "Type", value = kind)
        MediaDetailRow(label = "Size", value = formatBytes(photo.size))
        MediaDetailRow(label = "Resolution", value = formatResolution(photo.width, photo.height))
        if (photo.isVideo && photo.durationMs > 0) {
            MediaDetailRow(label = "Duration", value = formatGridDuration(photo.durationMs))
        }
        MediaDetailRow(label = "Date", value = formatMediaDate(photo.dateModified))
        MediaDetailRow(label = "Folder", value = photo.bucketName.ifBlank { "Unknown" })
    }
}

@Composable
private fun MediaDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
