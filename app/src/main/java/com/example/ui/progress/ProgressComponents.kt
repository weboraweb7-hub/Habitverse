package com.example.ui.progress

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.usecase.BadgeDefinition
import com.example.domain.usecase.BadgeEvaluator
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.HabitStrength
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.sin

private val STAR_DATE_FMT = DateTimeFormatter.ofPattern("MMM d")

internal fun computeStarNormalizedPosition(index: Int, total: Int): Pair<Float, Float> {
    val cols = total.coerceAtLeast(1)
    val xFrac = if (cols == 1) 0.5f else 0.06f + (index.toFloat() / (cols - 1).toFloat()) * 0.88f
    // Organic constellation wave + deterministic per-day jitter
    val wave = 0.5f + 0.24f * sin(index * 0.35 + 0.6).toFloat()
    val jitter = (((index * 73 + 19) % 23) - 11) / 110f
    val yFrac = (wave + jitter).coerceIn(0.14f, 0.86f)
    return xFrac to yFrac
}

@Composable
fun ConsistencySkyCard(
    stars: List<HabitCalculator.SkyStar>,
    onOpenDayOnHome: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStar by remember(stars) {
        mutableStateOf(stars.lastOrNull())
    }
    val completedStarsCount = remember(stars) { stars.count { it.isStreakDay } }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E172A)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("consistency_sky_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Consistency Sky",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        text = "90 nights · $completedStarsCount bright stars",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8)
                    )
                }
                Text(
                    text = "Tap a star",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            var canvasSizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF080E1E), Color(0xFF172542))
                        )
                    )
                    .semantics {
                        contentDescription = "Consistency sky showing $completedStarsCount bright days across 90 days"
                    }
                    .pointerInput(stars) {
                        detectTapGestures { tapOffset ->
                            if (stars.isEmpty() || canvasSizePx.width <= 0f) return@detectTapGestures
                            val hitRadius = 26.dp.toPx()
                            var bestStar: HabitCalculator.SkyStar? = null
                            var bestDist = Float.MAX_VALUE
                            stars.forEachIndexed { idx, star ->
                                val (xf, yf) = computeStarNormalizedPosition(idx, stars.size)
                                val sx = xf * canvasSizePx.width
                                val sy = yf * canvasSizePx.height
                                val d = hypot(tapOffset.x - sx, tapOffset.y - sy)
                                if (d < hitRadius && d < bestDist) {
                                    bestDist = d
                                    bestStar = star
                                }
                            }
                            if (bestStar != null) {
                                selectedStar = bestStar
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSizePx = size
                    if (stars.isEmpty()) return@Canvas

                    val coords = stars.indices.map { idx ->
                        val (xf, yf) = computeStarNormalizedPosition(idx, stars.size)
                        Offset(xf * size.width, yf * size.height)
                    }

                    // Subtle nebula glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x332DD4BF), Color.Transparent),
                            center = Offset(size.width * 0.68f, size.height * 0.42f),
                            radius = size.width * 0.42f
                        ),
                        center = Offset(size.width * 0.68f, size.height * 0.42f),
                        radius = size.width * 0.42f
                    )

                    // Constellation lines between consecutive streak stars
                    for (idx in 1 until stars.size) {
                        if (stars[idx].connectedToPrev) {
                            drawLine(
                                color = Color(0x667DD3FC),
                                start = coords[idx - 1],
                                end = coords[idx],
                                strokeWidth = 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Individual day stars
                    stars.forEachIndexed { idx, star ->
                        val center = coords[idx]
                        val isSelected = selectedStar?.date == star.date
                        val baseRadius = when {
                            star.isStreakDay -> 3.8.dp.toPx()
                            star.ratio > 0f -> (2.0f + star.ratio * 1.4f).dp.toPx()
                            else -> 1.4.dp.toPx()
                        }
                        val starColor = when {
                            star.isStreakDay -> Color(0xFFFDE68A)
                            star.ratio > 0f -> Color(0xFF7DD3FC).copy(alpha = 0.45f + star.ratio * 0.45f)
                            else -> Color(0xFF64748B).copy(alpha = 0.45f)
                        }

                        if (star.isStreakDay || isSelected) {
                            drawCircle(
                                color = starColor.copy(alpha = if (isSelected) 0.42f else 0.22f),
                                radius = baseRadius * (if (isSelected) 2.7f else 2.1f),
                                center = center
                            )
                        }

                        drawCircle(
                            color = starColor,
                            radius = if (isSelected) baseRadius * 1.3f else baseRadius,
                            center = center
                        )

                        if (star.isStreakDay) {
                            val arm = baseRadius * 1.65f
                            drawLine(
                                color = Color(0xCCFEF3C7),
                                start = Offset(center.x - arm, center.y),
                                end = Offset(center.x + arm, center.y),
                                strokeWidth = 0.9.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xCCFEF3C7),
                                start = Offset(center.x, center.y - arm),
                                end = Offset(center.x, center.y + arm),
                                strokeWidth = 0.9.dp.toPx()
                            )
                        }
                    }
                }
            }

            val inspected = selectedStar
            if (inspected != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val pct = (inspected.ratio * 100f).toInt().coerceIn(0, 100)
                    Text(
                        text = "${inspected.date.format(STAR_DATE_FMT)} · ${inspected.completedCount}/${inspected.scheduledCount} ($pct%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFE2E8F0)
                    )
                    TextButton(
                        onClick = { onOpenDayOnHome(inspected.date) },
                        modifier = Modifier.testTag("sky_open_day_button")
                    ) {
                        Text("Open day", color = Color(0xFF5EEAD4))
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyRecapSection(
    recap: WeeklyRecapData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_recap_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly Recap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = recap.dateRangeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = createWeeklyRecapShareIntent(context, recap)
                        context.startActivity(Intent.createChooser(intent, "Share Weekly Recap"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("share_recap_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share weekly recap card",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Card")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weekly_recap_pager")
            ) { page ->
                RecapStoryCard(page = page, recap = recap)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { idx ->
                    val active = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapStoryCard(
    page: Int,
    recap: WeeklyRecapData
) {
    val containerBrush = when (page) {
        0 -> Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0D9488)))
        1 -> Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFD97706)))
        2 -> Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF6366F1)))
        else -> Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF9333EA)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerBrush)
            .padding(18.dp)
    ) {
        when (page) {
            0 -> {
                val deltaText = if (recap.weekDeltaPercent >= 0) {
                    "+${recap.weekDeltaPercent}% vs last week"
                } else {
                    "${recap.weekDeltaPercent}% vs last week"
                }
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "WEEKLY COMPLETION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "${recap.weekPercent}%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${recap.weekCompleted}/${recap.weekScheduled} check-ins · $deltaText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
            1 -> {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "STREAK MOMENTUM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "🔥 ${recap.currentStreak} days",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Personal best: ${recap.bestStreak} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
            2 -> {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "STRONGEST HABIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    val title = if (recap.topHabitName != null) {
                        "${recap.topHabitIcon.orEmpty()} ${recap.topHabitName}"
                    } else {
                        "Keep checking in!"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = recap.topHabitStrengthLabel?.let {
                            "${recap.topHabitStrengthPercent}% strength · $it"
                        } ?: "Daily reps build automaticity",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "LEVEL & ENERGY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    val moodSuffix = recap.averageMoodEmoji?.let { " · Mood $it" }.orEmpty()
                    Text(
                        text = "Lv ${recap.level} · ${recap.totalXp} XP",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${recap.unlockedBadgeCount} / ${BadgeEvaluator.allBadges.size} badges unlocked$moodSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}

fun renderWeeklyRecapBitmap(recap: WeeklyRecapData): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(0xFF0F172A.toInt(), 0xFF0D9488.toInt(), 0xFF1E1B4B.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
    }
    canvas.drawRoundRect(RectF(72f, 220f, width - 72f, height - 160f), 48f, 48f, cardPaint)

    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF99F6E4.toInt()
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("HABITWORKS WEEKLY RECAP", 96f, 130f, brandPaint)

    val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2E8F0.toInt()
        textSize = 36f
    }
    canvas.drawText(recap.dateRangeLabel, 96f, 182f, rangePaint)

    val bigNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 160f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("${recap.weekPercent}%", 120f, 420f, bigNumberPaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCFBF1.toInt()
        textSize = 40f
    }
    canvas.drawText("${recap.weekCompleted} of ${recap.weekScheduled} habit check-ins", 120f, 490f, subPaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCBD5E1.toInt()
        textSize = 34f
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 54f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    canvas.drawText("CURRENT STREAK", 120f, 620f, labelPaint)
    canvas.drawText("🔥 ${recap.currentStreak} days (Best ${recap.bestStreak}d)", 120f, 685f, valuePaint)

    canvas.drawText("STRONGEST HABIT", 120f, 810f, labelPaint)
    val topHabitLine = recap.topHabitName?.let { "${recap.topHabitIcon.orEmpty()} $it (${recap.topHabitStrengthPercent}%)" }
        ?: "Building momentum"
    canvas.drawText(topHabitLine.take(30), 120f, 875f, valuePaint)

    canvas.drawText("LEVEL & BADGES", 120f, 1000f, labelPaint)
    canvas.drawText("Lv ${recap.level} · ${recap.totalXp} XP · ${recap.unlockedBadgeCount} badges", 120f, 1065f, valuePaint)

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 32f
    }
    canvas.drawText("Small daily wins compound.", 120f, 1265f, footerPaint)

    return bitmap
}

fun createWeeklyRecapShareIntent(context: Context, recap: WeeklyRecapData): Intent {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val imageFile = File(exportDir, "habitworks_weekly_recap.png")
    val bitmap = renderWeeklyRecapBitmap(recap)
    FileOutputStream(imageFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
    }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "HabitWorks Weekly Recap (${recap.weekPercent}%)")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

@Composable
fun HabitStrengthRowCard(
    strength: HabitStrength,
    modifier: Modifier = Modifier
) {
    val accent = Color(strength.habit.colorHex)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_strength_${strength.habit.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = strength.habit.icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strength.habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${strength.label} · ${strength.scorePercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { strength.scorePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BadgeVaultSection(
    unlockedBadgesById: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val allBadges = BadgeEvaluator.allBadges
    val unlockedCount = allBadges.count { it.id in unlockedBadgesById }
    var inspectedBadge by remember { mutableStateOf<BadgeDefinition?>(null) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("badge_vault_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Badge Vault",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$unlockedCount / ${allBadges.size} unlocked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (inspectedBadge != null) {
                val badge = inspectedBadge!!
                val unlockedDate = unlockedBadgesById[badge.id]
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (unlockedDate != null) {
                            "${badge.emoji} ${badge.title}: ${badge.description} (Unlocked $unlockedDate)"
                        } else {
                            "🔒 ${badge.title}: ${badge.description}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            allBadges.chunked(3).forEach { rowBadges ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowBadges.forEach { badge ->
                        val unlocked = badge.id in unlockedBadgesById
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (unlocked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 76.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { inspectedBadge = badge }
                                .testTag("badge_cell_${badge.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (unlocked) {
                                    Text(
                                        text = badge.emoji,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Locked badge",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = badge.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (unlocked) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    repeat(3 - rowBadges.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
