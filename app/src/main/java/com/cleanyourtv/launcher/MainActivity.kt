package com.cleanyourtv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One launchable app on the TV. */
class AppEntry(
    val label: String,
    val packageName: String,
    val launchIntent: Intent,
    private val bitmap: Bitmap,
) {
    val icon: ImageBitmap = bitmap.asImageBitmap()

    /** Average colour of the icon, darkened, used to tint the wallpaper. */
    val accent: Color by lazy {
        val px = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val r = ((px shr 16) and 0xFF) * 0.45f / 255f
        val g = ((px shr 8) and 0xFF) * 0.45f / 255f
        val b = (px and 0xFF) * 0.45f / 255f
        Color(r, g, b)
    }
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
    val seen = HashSet<String>()
    val result = ArrayList<AppEntry>()
    for (cat in categories) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN).apply {
                addCategory(cat)
                setClassName(pkg, ri.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            result += AppEntry(
                label = ri.loadLabel(pm).toString(),
                packageName = pkg,
                launchIntent = launch,
                bitmap = ri.loadIcon(pm).toBitmap(192, 192),
            )
        }
    }
    return result.sortedBy { it.label.lowercase() }
}

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A launcher must not close itself on Back.
        onBackPressedDispatcher.addCallback(this) { }
        setContent {
            HomeScreen(apps) { startActivity(it.launchIntent) }
        }
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps(this) // picks up installed/removed apps
    }
}

@Composable
fun HomeScreen(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    var focused by remember { mutableStateOf<AppEntry?>(null) }
    val target = focused?.accent ?: Color(0xFF14141C)
    val tint by animateColorAsState(target, tween(700), label = "tint")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, Color(0xFF050507))))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Header()
            Spacer(Modifier.weight(1f))
            Text(
                text = focused?.label ?: "",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            AppRow(apps, onFocus = { focused = it }, onLaunch = onLaunch)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun Header() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
    val day = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("CleanYourTV", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text(day, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
        }
    }
}

@Composable
fun AppRow(apps: List<AppEntry>, onFocus: (AppEntry) -> Unit, onLaunch: (AppEntry) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) runCatching { first.requestFocus() }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { index, app ->
            AppCard(
                app = app,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                onFocus = { onFocus(app) },
                onClick = { onLaunch(app) },
            )
        }
    }
}

@Composable
fun AppCard(app: AppEntry, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.2f else 1f, spring(), label = "scale")
    Column(
        modifier = modifier
            .width(144.dp)
            .padding(vertical = 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(28.dp),
                )
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.alpha(if (focused) 1f else 0.6f),
        )
    }
}
