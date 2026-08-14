package com.app.localtiktok

import androidx.compose.foundation.ExperimentalFoundationApi
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // True OLED fullscreen — kills status bar and nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            TheCollectivesTheme { MainContainer() }
        }
    }
}

// ── THEME ─────────────────────────────────────────────────────────────

@Composable
fun TheCollectivesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface    = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface    = Color.White
        ),
        content = content
    )
}

// ── ROOT ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainContainer() {
    var showSplash by remember { mutableStateOf(true) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (showSplash) {
            AppleHelloSplash(onFinished = { showSplash = false })
        } else {
            val perms = rememberMultiplePermissionsState(
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                else
                    listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            )
            LaunchedEffect(Unit) { perms.launchMultiplePermissionRequest() }

            if (perms.allPermissionsGranted) {
                VideoFeedScreen()
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Storage permission required.", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── 1. SPLASH ─────────────────────────────────────────────────────────
// FIX: Original used raw float pixel coords on a dp-sized Canvas.
//      On 3x density screens those 20f/60f coords rendered as 20px in a 720px canvas — tiny smear.
//      Fix: all path coords are fractions of size.width/size.height, scales to any density.
// FIX: Original had multiple moveTo() calls breaking the Path into disconnected subpaths.
//      PathMeasure only measures the first subpath — animation was incomplete on the rest.
//      Fix: single continuous stroke (no moveTo after the first), PathMeasure covers the whole word.

@Composable
fun AppleHelloSplash(onFinished: () -> Unit) {
    val strokeProgress  = remember { Animatable(0f) }
    val subtitleAlpha   = remember { Animatable(0f) }
    val containerAlpha  = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        strokeProgress.animateTo(1f, tween(1900, easing = LinearOutSlowInEasing))
        subtitleAlpha.animateTo(1f, tween(500))
        delay(450)
        containerAlpha.animateTo(0f, tween(380))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(containerAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Canvas(modifier = Modifier.size(280.dp, 110.dp)) {
                val w = size.width
                val h = size.height

                // Single continuous cursive "hello" — fractional coords → density-independent
                val path = Path().apply {
                    // h: ascender up
                    moveTo(0.06f * w, 0.90f * h)
                    cubicTo(0.06f * w, 0.55f * h, 0.06f * w, 0.28f * h, 0.06f * w, 0.10f * h)
                    // h: arch right and back down
                    cubicTo(0.07f * w, 0.42f * h, 0.13f * w, 0.40f * h, 0.17f * w, 0.48f * h)
                    cubicTo(0.20f * w, 0.58f * h, 0.21f * w, 0.74f * h, 0.21f * w, 0.90f * h)
                    // baseline: h → e
                    cubicTo(0.23f * w, 0.90f * h, 0.27f * w, 0.84f * h, 0.28f * w, 0.72f * h)
                    // e: upper loop
                    cubicTo(0.29f * w, 0.52f * h, 0.31f * w, 0.44f * h, 0.37f * w, 0.44f * h)
                    cubicTo(0.45f * w, 0.44f * h, 0.46f * w, 0.56f * h, 0.44f * w, 0.63f * h)
                    // e: midline
                    cubicTo(0.40f * w, 0.67f * h, 0.31f * w, 0.67f * h, 0.28f * w, 0.66f * h)
                    // e: bottom arc and exit
                    cubicTo(0.28f * w, 0.82f * h, 0.38f * w, 0.95f * h, 0.47f * w, 0.82f * h)
                    // l1: ascender up
                    cubicTo(0.49f * w, 0.74f * h, 0.51f * w, 0.42f * h, 0.51f * w, 0.10f * h)
                    // l1: back down
                    cubicTo(0.51f * w, 0.44f * h, 0.51f * w, 0.68f * h, 0.51f * w, 0.88f * h)
                    // l1 → l2 connector
                    cubicTo(0.53f * w, 0.88f * h, 0.57f * w, 0.80f * h, 0.59f * w, 0.70f * h)
                    // l2: ascender up
                    cubicTo(0.61f * w, 0.44f * h, 0.63f * w, 0.22f * h, 0.63f * w, 0.10f * h)
                    // l2: back down
                    cubicTo(0.63f * w, 0.42f * h, 0.63f * w, 0.66f * h, 0.63f * w, 0.88f * h)
                    // l2 → o connector
                    cubicTo(0.65f * w, 0.90f * h, 0.69f * w, 0.90f * h, 0.71f * w, 0.78f * h)
                    // o: left arc up
                    cubicTo(0.71f * w, 0.54f * h, 0.74f * w, 0.42f * h, 0.80f * w, 0.42f * h)
                    // o: right arc down
                    cubicTo(0.88f * w, 0.42f * h, 0.92f * w, 0.58f * h, 0.90f * w, 0.74f * h)
                    // o: bottom close
                    cubicTo(0.88f * w, 0.92f * h, 0.73f * w, 0.96f * h, 0.71f * w, 0.84f * h)
                }

                val measure = PathMeasure()
                measure.setPath(path, false)
                val partial = Path()
                measure.getSegment(0f, measure.length * strokeProgress.value, partial, true)

                drawPath(
                    path = partial,
                    color = Color.White,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "The collectives",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}

// ── 2. FEED ───────────────────────────────────────────────────────────
// FIX: All remember/LaunchedEffect/rememberLauncher calls happen BEFORE the early-return guard.
//      Compose rules: remember call order must be consistent across recompositions.
// FIX: activePlayer.stop() called before every new MediaItem — original had no stop(), causing
//      audio from the previous video to bleed through during page transitions.
// FIX: deleteVideo() now handles API 28 / 29 / 30+ correctly.
//      Original: contentResolver.delete() silently fails on any phone running Android 10+ (API 29+).

@Composable
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFeedScreen() {
    val context = LocalContext.current
    var videoList     by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val favorites      = remember { mutableStateListOf<Uri>() }
    var videoToDelete by remember { mutableStateOf<Uri?>(null) }

    val activePlayer  = remember { ExoPlayer.Builder(context).build() }
    val preloadPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            activePlayer.release()
            preloadPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        videoList = fetchLocalVideos(context).shuffled()
    }

    // Must be called unconditionally — declared before any early return
    val pagerState = rememberPagerState(pageCount = { videoList.size })

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            videoToDelete?.let { uri ->
                videoList = videoList.filter { it != uri }
                Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
            }
            videoToDelete = null
        }
    }

    LaunchedEffect(pagerState.currentPage, videoList) {
        if (videoList.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage >= videoList.size) return@LaunchedEffect

        activePlayer.stop()
        activePlayer.setMediaItem(MediaItem.fromUri(videoList[pagerState.currentPage]))
        activePlayer.repeatMode = Player.REPEAT_MODE_ONE
        activePlayer.prepare()
        activePlayer.playWhenReady = true

        val nextIndex = (pagerState.currentPage + 1) % videoList.size
        preloadPlayer.stop()
        preloadPlayer.setMediaItem(MediaItem.fromUri(videoList[nextIndex]))
        preloadPlayer.prepare()
        preloadPlayer.playWhenReady = false
    }

    // Early return AFTER all remembered calls — safe to Compose rules
    if (videoList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No local videos found.", color = Color.Gray, fontSize = 16.sp)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == pagerState.currentPage) {
                val uri = videoList[page]
                VideoPlayerItem(
                    player     = activePlayer,
                    isLiked    = favorites.contains(uri),
                    onSingleTap = {
                        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
                    },
                    onDoubleTap = {
                        if (favorites.contains(uri)) favorites.remove(uri) else favorites.add(uri)
                    },
                    onTripleTap = { videoToDelete = uri }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        videoToDelete?.let { uri ->
            IosDeleteConfirmationDialog(
                onConfirm = {
                    val immediate = deleteVideo(context, uri, deleteMediaLauncher)
                    videoToDelete = null
                    if (immediate) {
                        videoList = videoList.filter { it != uri }
                        Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                    }
                    // API 29+: list update and toast happen in the launcher result callback above
                },
                onDismiss = { videoToDelete = null }
            )
        }
    }
}

// ── 3. VIDEO ITEM ─────────────────────────────────────────────────────
// FIX: Original spawned a new coroutine on EVERY tap, all running in parallel.
//      They all read the same tapCount at different times → multiple callbacks fired at once.
//      Fix: tapJob?.cancel() kills the previous debounce before launching a new one.
//      Only the coroutine from the LAST tap in a window ever fires.

@Composable
fun VideoPlayerItem(
    player: ExoPlayer,
    isLiked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit
) {
    var tapCount       by remember { mutableStateOf(0) }
    var tapJob         by remember { mutableStateOf<Job?>(null) }
    var showHeartPulse by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {

        // Video surface — RESIZE_MODE_ZOOM = fill screen, crop edges (TikTok-style)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player   = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        // Tap overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        tapCount++
                        tapJob?.cancel()              // ← kill previous debounce
                        tapJob = scope.launch {
                            delay(350)                // collect window
                            val count = tapCount
                            tapCount = 0              // reset before firing
                            when (count) {
                                1    -> onSingleTap()
                                2    -> { onDoubleTap(); showHeartPulse = true }
                                else -> onTripleTap()
                            }
                        }
                    }
                }
        )

        // Double-tap heart burst
        AnimatedVisibility(
            visible  = showHeartPulse,
            enter    = scaleIn(initialScale = 0.2f, animationSpec = tween(200)) + fadeIn(tween(150)),
            exit     = scaleOut(targetScale = 1.5f, animationSpec = tween(300)) + fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showHeartPulse) {
                if (showHeartPulse) { delay(700); showHeartPulse = false }
            }
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(90.dp)
            )
        }

        // Persistent liked badge (bottom-right)
        if (isLiked) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint = Color(0xFFFF3B30),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(26.dp)
            )
        }
    }
}

// ── 4. DELETE DIALOG ─────────────────────────────────────────────────

@Composable
fun IosDeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Delete Video?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "This will permanently remove this video from your device storage.",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape   = RoundedCornerShape(14.dp)
            ) {
                Text("Delete Video", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape   = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

// ── 5. HELPERS ────────────────────────────────────────────────────────

fun fetchLocalVideos(context: Context): List<Uri> {
    val uris = mutableListOf<Uri>()
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID),
        null, null, null
    )?.use { cursor ->
        val col = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        while (cursor.moveToNext()) {
            uris.add(
                ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(col)
                )
            )
        }
    }
    return uris
}

/**
 * Returns true  → deletion is immediate (API ≤ 28), update list in caller.
 * Returns false → system dialog pending (API 29+), list update arrives via launcher callback.
 */
@SuppressLint("NewApi")
fun deleteVideo(
    context: Context,
    uri: Uri,
    launcher: ActivityResultLauncher<IntentSenderRequest>
): Boolean {
    return try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // API 30+: OS shows its own delete confirmation dialog
                val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                false
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                // API 29: direct delete, but OS may throw a recoverable permission wall
                try {
                    context.contentResolver.delete(uri, null, null)
                    true
                } catch (e: android.app.RecoverableSecurityException) {
                    launcher.launch(
                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    )
                    false
                }
            }
            else -> {
                // API 28 and below: WRITE_EXTERNAL_STORAGE in manifest covers this
                context.contentResolver.delete(uri, null, null) > 0
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
