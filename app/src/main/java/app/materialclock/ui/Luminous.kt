package app.materialclock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A press that you can feel with your eyes: a soft light follows the finger across the surface, a
 * ripple spreads from where it landed, and the whole shape leans toward the touch (stretching a
 * little on that side, shifting a couple of dp) and springs back past its rest on release.
 *
 * Ported from the dial in samuelbeckman's fork of this app (MIT, same as this project), with the
 * click handling reduced to `clickable` and an optional [role] and [onClickLabel] for
 * accessibility. The pointer is watched on the initial pass, so it sees the finger without
 * consuming it, and a slide across the surface keeps the light moving.
 */
@Composable
fun Modifier.luminousElasticClickable(
    shape: Shape,
    maxStretchPercent: Float = 0.092f,
    maxTranslateDp: Dp = 2.6.dp,
    luminousRadiusScale: Float = 1.0f,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    var rawTouchPosition by remember { mutableStateOf<Offset?>(null) }
    var lastTouchPosition by remember { mutableStateOf<Offset?>(null) }
    var lastRippleOrigin by remember { mutableStateOf<Offset?>(null) }
    val rippleProgress = remember { Animatable(1f) }

    var targetDx by remember { mutableFloatStateOf(0f) }
    var targetDy by remember { mutableFloatStateOf(0f) }

    val isPressed = rawTouchPosition != null || isInteractionPressed

    val follow = if (isPressed) {
        spring<Float>(stiffness = 550f, dampingRatio = 0.82f)
    } else {
        spring<Float>(stiffness = 350f, dampingRatio = 0.52f)
    }
    val animatedDx by animateFloatAsState(targetDx, follow, label = "elasticDx")
    val animatedDy by animateFloatAsState(targetDy, follow, label = "elasticDy")

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(stiffness = 420f, dampingRatio = 0.62f)
        },
        label = "luminousElasticScale",
    )

    val luminousAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1.0f else 0.0f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 120, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        },
        label = "luminousAlpha",
    )

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary

    val centerColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.48f * luminousAlpha)
    } else {
        primaryColor.copy(alpha = 0.28f * luminousAlpha)
    }
    val outerColor = if (isDarkTheme) {
        Color(0xFF80D8FF).copy(alpha = 0.22f * luminousAlpha)
    } else {
        primaryColor.copy(alpha = 0.11f * luminousAlpha)
    }

    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.pressed) {
                        val pos = change.position
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        if (rawTouchPosition == null) {
                            lastRippleOrigin = pos
                            scope.launch {
                                rippleProgress.snapTo(0f)
                                rippleProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                            }
                        }
                        rawTouchPosition = pos
                        lastTouchPosition = pos
                        if (cx > 0f && cy > 0f) {
                            targetDx = ((pos.x - cx) / cx).coerceIn(-1f, 1f)
                            targetDy = ((pos.y - cy) / cy).coerceIn(-1f, 1f)
                        }
                    } else {
                        rawTouchPosition = null
                        targetDx = 0f
                        targetDy = 0f
                    }
                }
            }
        }
        .graphicsLayer {
            val maxTranslatePx = maxTranslateDp.toPx()
            val stretchX = 1f + maxStretchPercent * abs(animatedDx) - 0.03f * abs(animatedDy)
            val stretchY = 1f + (maxStretchPercent * 1.06f) * abs(animatedDy) - 0.03f * abs(animatedDx)
            scaleX = animatedScale * stretchX
            scaleY = animatedScale * stretchY
            transformOrigin = TransformOrigin(
                (0.5f - 0.30f * animatedDx).coerceIn(0.15f, 0.85f),
                (0.5f - 0.30f * animatedDy).coerceIn(0.15f, 0.85f),
            )
            translationX = animatedDx * maxTranslatePx
            translationY = animatedDy * maxTranslatePx
            clip = true
            this.shape = shape
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .clip(shape)
        .drawWithContent {
            drawContent()

            val w = size.width
            val h = size.height
            val touch = rawTouchPosition ?: lastTouchPosition ?: Offset(w / 2f, h / 2f)

            if (luminousAlpha > 0.01f) {
                val baseRadius = if (isDarkTheme) {
                    size.maxDimension * 0.75f
                } else {
                    size.minDimension * 1.15f + size.maxDimension * 0.22f
                }
                val radius = baseRadius * luminousRadiusScale
                val brush = if (isDarkTheme) {
                    Brush.radialGradient(
                        colors = listOf(centerColor, outerColor, Color.Transparent),
                        center = touch,
                        radius = radius,
                    )
                } else {
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to centerColor,
                            0.38f to primaryColor.copy(alpha = 0.15f * luminousAlpha),
                            0.72f to outerColor.copy(alpha = 0.04f * luminousAlpha),
                            1.00f to Color.Transparent,
                        ),
                        center = touch,
                        radius = radius,
                    )
                }
                fillOutline(shape, brush)
            }

            val p = rippleProgress.value
            val rippleAlpha = (1f - p) * (if (isPressed) 1f else luminousAlpha)
            val origin = lastRippleOrigin
            if (p < 1f && rippleAlpha > 0.01f && origin != null) {
                val maxR = size.maxDimension * 0.90f
                val minR = 12.dp.toPx()
                val brush = Brush.radialGradient(
                    colors = listOf(
                        centerColor.copy(alpha = centerColor.alpha * 0.85f * rippleAlpha),
                        outerColor.copy(alpha = outerColor.alpha * 0.45f * rippleAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(origin.x.coerceIn(0f, w), origin.y.coerceIn(0f, h)),
                    radius = minR + (maxR - minR) * p,
                )
                fillOutline(shape, brush)
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

private fun DrawScope.fillOutline(shape: Shape, brush: Brush) {
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> drawRoundRect(
            brush = brush,
            cornerRadius = outline.roundRect.topLeftCornerRadius,
        )
        is Outline.Generic -> drawPath(outline.path, brush)
        is Outline.Rectangle -> drawRect(brush)
    }
}
