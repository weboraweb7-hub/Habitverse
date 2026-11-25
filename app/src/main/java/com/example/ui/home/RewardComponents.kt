package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.DailyReward
import com.example.domain.model.FlameSkin
import com.example.domain.model.Quote
import com.example.domain.usecase.BadgeDefinition
import com.example.domain.usecase.ChestRollResult
import com.example.domain.usecase.XpProgress
import com.example.ui.theme.NewsreaderFont
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun XpLevelStrip(
    xpProgress: XpProgress,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateIntAsState(
        targetValue = xpProgress.level,
        animationSpec = tween(durationMillis = 350),
        label = "xp_level"
    )
    val animatedXp by animateIntAsState(
        targetValue = xpProgress.totalXp,
        animationSpec = tween(durationMillis = 350),
        label = "xp_total"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Level $animatedLevel, $animatedXp total XP"
            }
            .testTag("xp_level_strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Lv.$animatedLevel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        LinearProgressIndicator(
            progress = { xpProgress.progressFraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "$animatedXp XP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EvolvingStreakFlame(
    streak: Int,
    skin: FlameSkin,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val pulse = if (reducedMotion) {
        1f
    } else {
        val inf = rememberInfiniteTransition(label = "flame_pulse")
        inf.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flame_scale"
        ).value
    }

    val tier = when {
        streak >= 365 -> 6
        streak >= 100 -> 5
        streak >= 30 -> 4
        streak >= 14 -> 3
        streak >= 7 -> 2
        streak >= 3 -> 1
        else -> 0
    }

    val (outerColor, innerColor, coreColor) = when (skin) {
        FlameSkin.CLASSIC -> Triple(Color(0xFFEA580C), Color(0xFFF59E0B), Color(0xFFFEF3C7))
        FlameSkin.COBALT -> Triple(Color(0xFF1D4ED8), Color(0xFF38BDF8), Color(0xFFE0F2FE))
        FlameSkin.EMERALD -> Triple(Color(0xFF059669), Color(0xFF34D399), Color(0xFFD1FAE5))
        FlameSkin.GOLD -> Triple(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFFFBEB))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "Current streak $streak days, ${skin.title}"
            }
            .testTag("evolving_streak_flame")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val scale = (0.85f + tier * 0.03f) * pulse
                val cx = w / 2f
                val bottomY = h * 0.9f
                val topY = h * (0.32f - tier * 0.035f).coerceAtLeast(0.08f)

                if (tier >= 2) {
                    drawCircle(
                        color = innerColor.copy(alpha = 0.25f),
                        radius = w * 0.48f * scale,
                        center = Offset(cx, h * 0.56f)
                    )
                }

                val outerPath = Path().apply {
                    moveTo(cx, topY)
                    cubicTo(
                        w * (0.88f), h * 0.35f,
                        w * (0.85f), h * 0.85f,
                        cx, bottomY
                    )
                    cubicTo(
                        w * (0.15f), h * 0.85f,
                        w * (0.12f), h * 0.35f,
                        cx, topY
                    )
                    close()
                }
                drawPath(
                    path = outerPath,
                    brush = Brush.verticalGradient(listOf(innerColor, outerColor))
                )

                val innerPath = Path().apply {
                    moveTo(cx, h * 0.42f)
                    cubicTo(w * 0.7f, h * 0.55f, w * 0.68f, h * 0.85f, cx, h * 0.86f)
                    cubicTo(w * 0.32f, h * 0.85f, w * 0.3f, h * 0.55f, cx, h * 0.42f)
                    close()
                }
                drawPath(path = innerPath, color = coreColor)

                if (tier >= 4) {
                    drawCircle(
                        color = coreColor,
                        radius = 1.6.dp.toPx(),
                        center = Offset(w * 0.22f, h * 0.28f)
                    )
                    drawCircle(
                        color = coreColor,
                        radius = 1.4.dp.toPx(),
                        center = Offset(w * 0.8f, h * 0.24f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "${streak}d",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private data class ConfettiParticle(
    val xFraction: Float,
    val vx: Float,
    val vy: Float,
    val sizeDp: Float,
    val color: Color,
    val spinSpeed: Float
)

@Composable
fun FullScreenConfettiOverlay(
    triggerId: Int,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    if (triggerId <= 0 || reducedMotion) return

    val progress = remember { Animatable(0f) }
    val particles = remember(triggerId) {
        val rng = Random(triggerId * 9973)
        val palette = listOf(
            Color(0xFF0D9488),
            Color(0xFFF59E0B),
            Color(0xFF0EA5E9),
            Color(0xFFEC4899),
            Color(0xFF8B5CF6),
            Color(0xFF10B981)
        )
        List(80) {
            ConfettiParticle(
                xFraction = 0.15f + rng.nextFloat() * 0.7f,
                vx = (rng.nextFloat() - 0.5f) * 520f,
                vy = -(220f + rng.nextFloat() * 520f),
                sizeDp = 5f + rng.nextFloat() * 6f,
                color = palette[rng.nextInt(palette.size)],
                spinSpeed = (rng.nextFloat() - 0.5f) * 540f
            )
        }
    }

    LaunchedEffect(triggerId) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1700))
    }

    val p = progress.value
    if (p < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val t = p * 1.7f
            val gravity = 980f
            val alpha = (1f - ((p - 0.65f) / 0.35f).coerceIn(0f, 1f))
            particles.forEach { particle ->
                val startX = size.width * particle.xFraction
                val startY = size.height * 0.38f
                val x = startX + particle.vx * t
                val y = startY + particle.vy * t + 0.5f * gravity * t * t
                if (y <= size.height + 40f) {
                    val sz = particle.sizeDp.dp.toPx()
                    rotate(degrees = particle.spinSpeed * t, pivot = Offset(x, y)) {
                        drawRoundRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(x - sz / 2f, y - sz * 0.35f),
                            size = Size(sz, sz * 0.65f),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComebackBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("comeback_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🪃", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Never miss twice",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Yesterday was a pause — complete today's habits for a +25 XP comeback boost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun DailyChestCard(
    alreadyOpenedReward: DailyReward?,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val glowAlpha = if (alreadyOpenedReward != null || reducedMotion) {
        0.55f
    } else {
        val inf = rememberInfiniteTransition(label = "chest_glow")
        inf.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "chest_alpha"
        ).value
    }

    Card(
        onClick = { if (alreadyOpenedReward == null) onOpenClick() },
        enabled = alreadyOpenedReward == null,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alreadyOpenedReward == null) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = glowAlpha)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_chest_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TreasureChestGlyph(
                isOpen = alreadyOpenedReward != null,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (alreadyOpenedReward == null) {
                        "Daily Reward Chest Ready!"
                    } else {
                        "Daily Reward Claimed"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (alreadyOpenedReward == null) {
                        "All habits complete — tap to open today's surprise"
                    } else {
                        "Come back tomorrow after completing all habits"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alreadyOpenedReward != null) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Opened",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Open chest",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun TreasureChestGlyph(
    isOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val gold = Color(0xFFF59E0B)
    val wood = Color(0xFF92400E)
    val woodLight = Color(0xFFB45309)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lidOffset = if (isOpen) -h * 0.14f else 0f
        drawRoundRect(
            color = wood,
            topLeft = Offset(w * 0.12f, h * 0.45f),
            size = Size(w * 0.76f, h * 0.44f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = woodLight,
            topLeft = Offset(w * 0.10f, h * 0.22f + lidOffset),
            size = Size(w * 0.80f, h * 0.25f),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawRoundRect(
            color = gold,
            topLeft = Offset(w * 0.42f, h * 0.39f + lidOffset * 0.4f),
            size = Size(w * 0.16f, h * 0.18f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@Composable
fun ChestOpenDialog(
    result: ChestRollResult,
    rareQuote: Quote?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TreasureChestGlyph(isOpen = true, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = result.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rareQuote != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "\"${rareQuote.text}\"",
                                fontFamily = NewsreaderFont,
                                fontStyle = FontStyle.Italic,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rareQuote.book} · ${rareQuote.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("chest_dialog_claim_button")
            ) {
                Text("Awesome!")
            }
        }
    )
}

@Composable
fun BadgeUnlockToast(
    badge: BadgeDefinition?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = badge != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(onClick = onDismiss)
                    .testTag("badge_unlock_toast")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = badge.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Badge Unlocked: ${badge.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss badge notification",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowSparkleEffect(
    isDone: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return

    val anim = remember { Animatable(1f) }
    val prevDone = remember { androidx.compose.runtime.mutableStateOf(isDone) }
    LaunchedEffect(isDone) {
        if (!prevDone.value && isDone) {
            anim.snapTo(0f)
            anim.animateTo(1f, animationSpec = tween(durationMillis = 520))
        }
        prevDone.value = isDone
    }

    val p = anim.value
    if (p < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val cx = size.width - 24.dp.toPx()
            val cy = size.height / 2f
            for (i in 0 until 8) {
                val angle = (i * 45.0) * Math.PI / 180.0
                val dist = p * 26.dp.toPx()
                val x = cx + (cos(angle) * dist).toFloat()
                val y = cy + (sin(angle) * dist).toFloat()
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.85f),
                    radius = 3.dp.toPx() * (1f - p * 0.4f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

private data class MoodOption(val value: Int, val emoji: String, val label: String)

private val MOOD_OPTIONS = listOf(
    MoodOption(1, "😫", "Drained"),
    MoodOption(2, "😕", "Low"),
    MoodOption(3, "😐", "Okay"),
    MoodOption(4, "🙂", "Good"),
    MoodOption(5, "🤩", "Energized")
)

@Composable
fun MoodEnergySelectorRow(
    selectedMood: Int?,
    onSelectMood: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mood_energy_selector")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (selectedMood == null) "How's your energy?" else "Today's energy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MOOD_OPTIONS.forEach { option ->
                    val isSelected = selectedMood == option.value
                    Surface(
                        onClick = { onSelectMood(option.value) },
                        shape = CircleShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Mood ${option.label}" }
                            .testTag("mood_option_${option.value}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = option.emoji,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
