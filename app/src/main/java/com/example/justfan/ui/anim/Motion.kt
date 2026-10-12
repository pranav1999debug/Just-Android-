package com.example.justfan.ui.anim

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.ui.theme.DangerRed
import kotlinx.coroutines.delay

/**
 * Returns true if system animation duration scale is 0 or if the user disabled in-app animations.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val systemDisabled = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val sp = remember {
        context.getSharedPreferences("justfan_auth_prefs", Context.MODE_PRIVATE)
    }
    var appAnimEnabled by remember {
        mutableStateOf(sp.getBoolean("animations_enabled", true))
    }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "animations_enabled") {
                appAnimEnabled = sp.getBoolean("animations_enabled", true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return systemDisabled || !appAnimEnabled
}

/**
 * Persist in-app animation preference.
 */
fun setInAppAnimationsEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("justfan_auth_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("animations_enabled", enabled)
        .apply()
}

fun isInAppAnimationsEnabled(context: Context): Boolean {
    return context.getSharedPreferences("justfan_auth_prefs", Context.MODE_PRIVATE)
        .getBoolean("animations_enabled", true)
}

/**
 * Spring-based press scale for clickable items and cards (scales to 0.96f).
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource = remember { MutableInteractionSource() },
    targetScale: Float = 0.96f
): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return this

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Shimmer effect for placeholder skeleton loading states.
 */
@Composable
fun Modifier.shimmer(
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    highlightColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        return this.background(highlightColor)
    }

    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return this.drawBehind {
        val width = size.width
        val height = size.height
        val startX = translateAnim * width
        val brush = Brush.linearGradient(
            colors = listOf(
                highlightColor,
                color,
                highlightColor
            ),
            start = Offset(startX - width, 0f),
            end = Offset(startX, height)
        )
        drawRect(brush = brush)
    }
}

/**
 * Staggered entrance animation: fade-in + 24dp slide up with index delay.
 * Capped at first 12 items, runs once per item key.
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    key: Any? = null
): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return this

    val cappedIndex = index.coerceIn(0, 12)
    var visible by remember(key ?: index) { mutableStateOf(false) }

    LaunchedEffect(key ?: index) {
        delay(cappedIndex * 40L)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "staggerTransY"
    )

    return this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY * density
    }
}

/**
 * Animated thin glowing rotating border for premium elements.
 */
@Composable
fun Modifier.glowBorder(
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 1.5.dp,
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        return this.border(strokeWidth, color.copy(alpha = 0.5f), shape)
    }

    val transition = rememberInfiniteTransition(label = "glowBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAngle"
    )

    val brush = Brush.sweepGradient(
        colors = listOf(
            color.copy(alpha = 0.15f),
            color,
            color.copy(alpha = 0.15f),
            color.copy(alpha = 0.9f),
            color.copy(alpha = 0.15f)
        )
    )

    return this.border(
        width = strokeWidth,
        brush = brush,
        shape = shape
    )
}

/**
 * Rolling digits animated counter.
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        Text(text = count.toString(), style = style, color = color, modifier = modifier)
        return
    }

    Row(modifier = modifier) {
        val countString = count.toString()
        for (i in countString.indices) {
            val char = countString[i]
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it }
                        ) + fadeIn(tween(180))) togetherWith
                                (slideOutVertically(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetOffsetY = { it }
                                ) + fadeOut(tween(180)))
                    } else {
                        (slideInVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            initialOffsetY = { it }
                        ) + fadeIn(tween(180))) togetherWith
                                (slideOutVertically(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -it }
                                ) + fadeOut(tween(180)))
                    }
                },
                label = "digit_$i"
            ) { digit ->
                Text(
                    text = digit.toString(),
                    style = style,
                    color = color
                )
            }
        }
    }
}

/**
 * Horizontal shake animation on invalid field / error.
 */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion || trigger == 0) return this

    val animOffset = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        animOffset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 350
                0f at 0
                -16f at 50
                16f at 100
                -12f at 150
                12f at 200
                -6f at 250
                6f at 300
                0f at 350
            }
        )
    }

    return this.graphicsLayer {
        translationX = animOffset.value * density
    }
}

/**
 * Gently floating vertical translation for empty state icons.
 */
@Composable
fun Modifier.floatingEffect(): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return this

    val transition = rememberInfiniteTransition(label = "floating")
    val offsetY by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingY"
    )

    return this.graphicsLayer {
        translationY = offsetY * density
    }
}

/**
 * Favorite bounce icon button with spring scale (1 -> 1.4 -> 1) and color animation.
 */
@Composable
fun FavoriteBounceIcon(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = DangerRed,
    inactiveColor: Color = Color.White.copy(alpha = 0.85f)
) {
    val reducedMotion = rememberReducedMotion()
    var bounceTrigger by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isFavorite) {
        if (bounceTrigger > 0 && !reducedMotion) {
            scale.animateTo(
                targetValue = 1.35f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val iconColor by animateColorAsState(
        targetValue = if (isFavorite) activeColor else inactiveColor,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "favColor"
    )

    IconButton(
        onClick = {
            bounceTrigger++
            onToggle()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
            tint = iconColor
        )
    }
}

/**
 * Subtle ambient moving radial gradient depth behind content.
 */
@Composable
fun Modifier.ambientBackgroundDepth(
    accentColor: Color,
    enabled: Boolean = true
): Modifier {
    val reducedMotion = rememberReducedMotion()
    if (!enabled || reducedMotion) return this

    val transition = rememberInfiniteTransition(label = "ambientDepth")
    val shiftX by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientShiftX"
    )
    val shiftY by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientShiftY"
    )

    return this.drawBehind {
        val center = Offset(size.width * shiftX, size.height * shiftY)
        val radius = size.maxDimension * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

/**
 * Screen transition specifications for AnimatedContent.
 */
fun bottomTabTransition(forward: Boolean = true): ContentTransform {
    val slideOffset = 60
    return if (forward) {
        (slideInHorizontally(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            initialOffsetX = { slideOffset }
        ) + fadeIn(animationSpec = tween(durationMillis = 200))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    targetOffsetX = { -slideOffset }
                ) + fadeOut(animationSpec = tween(durationMillis = 180)))
    } else {
        (slideInHorizontally(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            initialOffsetX = { -slideOffset }
        ) + fadeIn(animationSpec = tween(durationMillis = 200))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    targetOffsetX = { slideOffset }
                ) + fadeOut(animationSpec = tween(durationMillis = 180)))
    }
}

fun subScreenEnterTransition(): ContentTransform {
    return (slideInHorizontally(
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        initialOffsetX = { it }
    ) + fadeIn(animationSpec = tween(durationMillis = 240))) togetherWith
            (slideOutHorizontally(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                targetOffsetX = { -it / 3 }
            ) + fadeOut(animationSpec = tween(durationMillis = 200)))
}

fun subScreenExitTransition(): ContentTransform {
    return (slideInHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        initialOffsetX = { -it / 3 }
    ) + fadeIn(animationSpec = tween(durationMillis = 220))) togetherWith
            (slideOutHorizontally(
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                targetOffsetX = { it }
            ) + fadeOut(animationSpec = tween(durationMillis = 200)))
}

/**
 * Splash intro screen for cold start: logo scales 0.8 to 1 with fade and soft glow pulse (1.2s), then fades into the app.
 * Automatically skips when reduced motion is enabled.
 */
@Composable
fun SplashIntroScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1200)
        onFinished()
    }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .glowBorder(MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "JUSTFAN",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Trending Creator Gallery & Media Hub",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
