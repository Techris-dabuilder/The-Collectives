@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalPermissionsApi::class
)

package com.app.localtiktok

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

// ── ACTIVITY ──────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { TheCollectivesTheme { MainContainer() } }
    }
}

// ── THEME ─────────────────────────────────────────────────────────────

@Composable
fun TheCollectivesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background   = Color.Black,
            surface      = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface    = Color.White
        ),
        content = content
    )
}

// ── ROOT ──────────────────────────────────────────────────────────────

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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = "Storage permission required.",
                        color    = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// ── 1. SPLASH ─────────────────────────────────────────────────────────

@Composable
fun AppleHelloSplash(onFinished: () -> Unit) {
    val strokeProgress = remember { Animatable(0f) }
    val subtitleAlpha  = remember { Animatable(0f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        strokeProgress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 1900, easing = LinearOutSlowInEasing)
        )
        subtitleAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 500))
        delay(450)
        containerAlpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 380))
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

                val path = Path().apply {
                    moveTo(0.06f * w, 0.90f * h)
                    cubicTo(0.06f * w, 0.55f * h, 0.06f * w, 0.28f * h, 0.06f * w, 0.10f * h)
                    cubicTo(0.07f * w, 0.42f * h, 0.13f * w, 0.40f * h, 0.17f * w, 0.48f * h)
                    cubicTo(0.20f * w, 0.58f * h, 0.21f * w, 0.74f * h, 0.21f * w, 0.90f * h)
                    cubicTo(0.23f * w, 0.90f * h, 0.27f * w, 0.84f * h, 0.28f * w, 0.72f * h)
                    cubicTo(0.29f * w, 0.52f * h, 0.31f * w, 0.44f * h, 0.37f * w, 0.44f * h)
                    cubicTo(0.45f * w, 0.44f * h, 0.46f * w, 0.56f * h, 0.44f * w, 0.63f * h)
                    cubicTo(0.40f * w, 0.67f * h, 0.31f * w, 0.67f * h, 0.28f * w, 0.66f * h)
                    cubicTo(0.28f * w, 0.82f * h, 0.38f * w, 0.95f * h, 0.47f * w, 0.82f * h)
                    cubicTo(0.49f * w, 0.74f * h, 0.51f * w, 0.42f * h, 0.51f * w, 0.10f * h)
                    cubicTo(0.51f * w, 0.44f * h, 0.51f * w, 0.68f * h, 0.51f * w, 0.88f * h)
                    cubicTo(0.53f * w, 0.88f * h, 0.57f * w, 0.80f * h, 0.59f * w, 0.70f * h)
                    cubicTo(0.61f * w, 0.44f * h, 0.63f * w, 0.22f * h, 0.63f * w, 0.10f * h)
                    cubicTo(0.63f * w, 0.42f * h, 0.63f * w, 0.66f * h, 0.63f * w, 0.88f * h)
                    cubicTo(0.65f * w, 0.90f * h, 0.69f * w, 0.90f * h, 0.71f * w, 0.78f * h)
                    cubicTo(0.71f * w, 0.54f * h, 0.74f * w, 0.42f * h, 0.80f * w, 0.42f * h)
                    cubicTo(0.88f * w, 0.42f * h, 0.92f * w, 0.58f * h, 0.90f * w, 0.74f * h)
                    cubicTo(0.88f * w, 0.92f * h, 0.73f * w, 0.96f * h, 0.71f * w, 0.84f * h)
                }

                val measure = PathMeasure()
                measure.setPath(path, false)
                val partial = Path()
                measure.getSegment(
                    startDistance  = 0f,
                    stopDistance   = measure.length * strokeProgress.value,
                    destination    = partial,
                    startWithMoveTo = true
                )
                drawPath(
                    path  = partial,
                    color = Color.White,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap   = StrokeCap.Round,
                        join  = StrokeJoin.Round
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text       = "The collectives",
                color      = Color.White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                modifier   = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}

// ── 2. FEED ───────────────────────────────────────────────────────────

@Composable
fun VideoFeedScreen() {
    val context       = LocalContext.current
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

    // All remember/LaunchedEffect calls must be unconditional — declared before any early return
    val pagerState = rememberPagerState(pageCount = { videoList.size })

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
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

    // Safe early return — all remember calls are above this point
    if (videoList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No local videos found.", color = Color.Gray, fontSize = 16.sp)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == pagerState.currentPage) {
                val uri = videoList[page]
                VideoPlayerItem(
                    player      = activePlayer,
                    isLiked     = favorites.contains(uri),
                    onSingleTap = {
                        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
                    },
                    onDoubleTap = {
                        if (favorites.contains(uri)) favorites.remove(uri)
                        else favorites.add(uri)
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
                },
                onDismiss = { videoToDelete = null }
            )
        }
    }
}

// ── 3. VIDEO ITEM ─────────────────────────────────────────────────────

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
    val scope           = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player   = player
                }
            },
            update   = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        tapCount++
                        tapJob?.cancel()
                        tapJob = scope.launch {
                            delay(350)
                            val count = tapCount
                            tapCount = 0
                            when (count) {
                                1    -> onSingleTap()
                                2    -> { onDoubleTap(); showHeartPulse = true }
                                else -> onTripleTap()
                            }
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible  = showHeartPulse,
            enter    = scaleIn(
                initialScale  = 0.2f,
                animationSpec = tween(durationMillis = 200)
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit     = scaleOut(
                targetScale   = 1.5f,
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showHeartPulse) {
                if (showHeartPulse) {
                    delay(700)
                    showHeartPulse = false
                }
            }
            Icon(
                imageVector        = Icons.Filled.Favorite,
                contentDescription = null,
                tint               = Color(0xFFFF3B30),
                modifier           = Modifier.size(90.dp)
            )
        }

        if (isLiked) {
            Icon(
                imageVector        = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint               = Color(0xFFFF3B30),
                modifier           = Modifier
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
            Text(
                text       = "Delete Video?",
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "This will permanently remove this video from your device storage.",
                color    = Color(0xFF8E8E93),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick  = onConfirm,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text       = "Delete Video",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick  = onDismiss,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Cancel", color = Color.White)
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

@SuppressLint("NewApi")
fun deleteVideo(
    context: Context,
    uri: Uri,
    launcher: ActivityResultLauncher<IntentSenderRequest>
): Boolean {
    return try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val pending = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                launcher.launch(
                    IntentSenderRequest.Builder(pending.intentSender).build()
                )
                false
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    context.contentResolver.delete(uri, null, null)
                    true
                } catch (e: android.app.RecoverableSecurityException) {
                    launcher.launch(
                        IntentSenderRequest.Builder(
                            e.userAction.actionIntent.intentSender
                        ).build()
                    )
                    false
                }
            }
            else -> {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
