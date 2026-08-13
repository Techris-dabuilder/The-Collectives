package com.app.localtiktok

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheCollectivesTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun TheCollectivesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainContainer() {
    var showSplash by remember { mutableStateOf(true) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (showSplash) {
            AppleHelloSplash(onFinished = { showSplash = false })
        } else {
            val permissionsState = rememberMultiplePermissionsState(
                permissions = if (Build.VERSION.SDK_INT >= 33) {
                    listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
            LaunchedEffect(Unit) {
                permissionsState.launchMultiplePermissionRequest()
            }
            if (permissionsState.allPermissionsGranted) {
                VideoFeedScreen()
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Storage access required to load local feed.", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ==========================================
// 1. APPLE "HELLO" SPLASH — FIXED CANVAS
// ==========================================
@Composable
fun AppleHelloSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(1800, easing = LinearOutSlowInEasing))
        textAlpha.animateTo(1f, animationSpec = tween(600))
        delay(600)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cursive "hello" using fractional coordinates
            Canvas(modifier = Modifier.size(240.dp, 100.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    // Use fractions of width/height so it scales properly
                    moveTo(0.1f * w, 0.6f * h)
                    cubicTo(0.1f * w, 0.1f * h, 0.25f * w, 0.1f * h, 0.25f * w, 0.6f * h) // h
                    cubicTo(0.25f * w, 0.8f * h, 0.2f * w, 0.8f * h, 0.3f * w, 0.6f * h)
                    cubicTo(0.4f * w, 0.4f * h, 0.4f * w, 0.8f * h, 0.5f * w, 0.6f * h) // e
                    cubicTo(0.5f * w, 0.1f * h, 0.6f * w, 0.1f * h, 0.6f * w, 0.7f * h) // l
                    cubicTo(0.6f * w, 0.1f * h, 0.7f * w, 0.1f * h, 0.7f * w, 0.7f * h) // l
                    cubicTo(0.7f * w, 0.4f * h, 0.85f * w, 0.4f * h, 0.8f * w, 0.6f * h) // o
                }
                val pathMeasure = PathMeasure().apply { setPath(path, false) }
                val length = pathMeasure.length
                val partialPath = Path()
                pathMeasure.getSegment(0f, length * progress.value, partialPath, true)
                drawPath(
                    path = partialPath,
                    color = Color.White,
                    style = Stroke(width = 6f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The collectives",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}

// ==========================================
// 2. MAIN FEED & PROPER PLAYER POOL
// ==========================================
@Composable
fun VideoFeedScreen() {
    val context = LocalContext.current
    var videoList by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val favorites = remember { mutableStateListOf<Uri>() }
    var videoToDelete by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        videoList = fetchLocalVideos(context).shuffled()
    }

    if (videoList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No local videos found.", color = Color.Gray)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { videoList.size })

    // Proper player pool: only the active player is prepared and playing,
    // the others are stopped and released when out of window.
    // We'll maintain a map of page to player, but we only keep 3 players.
    // For simplicity, we'll keep a pool of 3 players and swap media items.
    // We'll create a class to manage this, but we'll do inline.

    // Instead of holding players in remember and relying on side effects,
    // we'll create a pool manager that holds players for current, previous, next.
    // We'll use a keyed map and release when outside window.

    // We'll use a state holder for current player and two preloaders.
    // But a cleaner approach: create a custom class PlayerPool.
    // To keep code self-contained, we'll manage it inside a LaunchedEffect.

    // We'll use a remember with a mutable map of page to player, but limit to 3.
    // However, the recommended pattern: only keep players for the current page
    // and the adjacent ones.

    // We'll implement a simple solution: for each page we create a player if needed,
    // but we release players when they are not in the window [current-1, current+1].
    // To avoid recreation on every recomposition, we'll cache players in a map.

    val playerCache = remember { mutableMapOf<Int, ExoPlayer>() }
    val currentPage = pagerState.currentPage

    // Release players outside the window
    DisposableEffect(currentPage) {
        onDispose {
            // Clean up all players when composable leaves
            playerCache.values.forEach { it.release() }
            playerCache.clear()
        }
    }

    // We'll handle player creation and release in the VerticalPager item.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Only render if the page is within our window (±1)
            val shouldRender = kotlin.math.abs(page - currentPage) <= 1
            if (shouldRender) {
                // Get or create player for this page
                val player = playerCache.getOrPut(page) {
                    ExoPlayer.Builder(context).build().apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        val uri = videoList[page]
                        setMediaItem(MediaItem.fromUri(uri))
                        prepare()
                        // Do not play yet; we'll control playback based on visibility
                    }
                }

                // When this page becomes current, start playing; when it loses, pause
                LaunchedEffect(page, currentPage) {
                    if (page == currentPage) {
                        player.playWhenReady = true
                    } else {
                        player.playWhenReady = false
                    }
                }

                val uri = videoList[page]
                val isLiked = favorites.contains(uri)

                VideoPlayerItem(
                    player = player,
                    isLiked = isLiked,
                    onSingleTap = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onDoubleTap = {
                        if (isLiked) favorites.remove(uri) else favorites.add(uri)
                    },
                    onTripleTap = {
                        videoToDelete = uri
                    }
                )
            } else {
                // Page outside window: release player if exists
                playerCache.remove(page)?.release()
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        // iOS-style Delete Confirmation Dialog
        videoToDelete?.let { uri ->
            IosDeleteConfirmationDialog(
                onConfirm = {
                    deleteVideo(context, uri) { success ->
                        if (success) {
                            videoList = videoList.filter { it != uri }
                            Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                    }
                    videoToDelete = null
                },
                onDismiss = { videoToDelete = null }
            )
        }
    }
}

// ==========================================
// 3. VIDEO ITEM WITH FIXED GESTURE DEBOUNCE
// ==========================================
@Composable
fun VideoPlayerItem(
    player: ExoPlayer,
    isLiked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture overlay with debounce
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 500) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = currentTime

                        // Cancel any previous scheduled job
                        debounceJob?.cancel()
                        debounceJob = coroutineScope.launch {
                            delay(400) // Wait 400ms after last tap
                            // Execute callback based on tapCount
                            when (tapCount) {
                                1 -> onSingleTap()
                                2 -> onDoubleTap()
                                3 -> onTripleTap()
                            }
                            tapCount = 0
                        }
                    }
                }
        )

        // Floating Like Heart
        if (isLiked) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint = Color(0xFFFF3B30),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .alpha(0.85f)
            )
        }
    }
}

// ==========================================
// 4. iOS-STYLE DELETE CONFIRMATION
// ==========================================
@Composable
fun IosDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Delete Video?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "This action will permanently delete this video file from your phone storage.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete Video", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

// ==========================================
// 5. HELPER UTILS (MediaStore & Delete)
// ==========================================

// Fetch all video URIs quickly via MediaStore
fun fetchLocalVideos(context: Context): List<Uri> {
    val uriList = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(queryUri, id)
            uriList.add(contentUri)
        }
    }
    return uriList
}

// Delete video using the right API for Android version
fun deleteVideo(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ : use MediaStore.createDeleteRequest() which shows a system confirmation dialog.
        // We need an Activity reference. We'll cast context to Activity if possible.
        val activity = context as? Activity
        if (activity != null) {
            val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            val launcher = activity.registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                onResult(result.resultCode == Activity.RESULT_OK)
            }
            launcher.launch(deleteRequest.intentSender)
        } else {
            // Fallback: use contentResolver.delete (requires WRITE_EXTERNAL_STORAGE for API <=28, but on Q+ it might work if app has permission)
            val deleted = context.contentResolver.delete(uri, null, null) > 0
            onResult(deleted)
        }
    } else {
        // Older Android: directly delete via contentResolver (WRITE_EXTERNAL_STORAGE required)
        val deleted = context.contentResolver.delete(uri, null, null) > 0
        onResult(deleted)
    }
}
