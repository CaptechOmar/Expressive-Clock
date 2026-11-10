package app.materialclock.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.materialclock.ui.theme.ClockFace
import app.materialclock.ui.theme.Numerals
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The stopwatch face: an oval (stadium) dial with a sweeping hand and the time in the middle.
 *
 * The behaviour follows the dial in samuelbeckman's fork, in a stadium shape like the alarm
 * picker's clock instead of a circle:
 *
 *  - **Pop.** Starting or stopping squashes the whole dial to 95% and springs it back, slightly
 *    under-damped so it overshoots a touch.
 *  - **Tap to toggle.** Touching the dial starts or stops the stopwatch, same as the main button.
 *  - **Reset bounce.** When the time returns to zero the hand does not jump to 12. It swings back,
 *    overshoots past 12 and settles, in two eased phases.
 *
 * The hand makes one turn a minute. Because the dial is not round, both the hand and the 60 tick
 * marks are placed by casting a ray from the centre at the right angle until it meets the
 * stadium outline, so the hand always lands exactly on its tick.
 */
@Composable
fun StopwatchDial(
    elapsedMillis: Long,
    running: Boolean,
    text: String,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val turn = (safeMillis % 60_000L) / 60_000f

    val pop = remember { Animatable(1f) }
    var previousRunning by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(running) {
        val before = previousRunning
        previousRunning = running
        if (before != null && before != running) {
            pop.snapTo(0.95f)
            pop.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 380f))
        }
    }

    // Follows the clock while there is time on it; on reset it swings home with an overshoot.
    val hand = remember { Animatable(turn) }
    LaunchedEffect(safeMillis, running) {
        if (running || safeMillis > 0L) {
            hand.snapTo(turn)
        } else {
            val start = (hand.value % 1f + 1f) % 1f
            if (start > 0.001f && start < 0.999f) {
                val overshoot = if (start <= 0.5f) -0.038f else 1.038f
                val rest = if (start <= 0.5f) 0f else 1f
                hand.snapTo(start)
                hand.animateTo(overshoot, tween(340, easing = FastOutSlowInEasing))
                hand.animateTo(rest, tween(220, easing = FastOutSlowInEasing))
            }
            hand.snapTo(0f)
        }
    }
    val handTurn = (hand.value % 1f + 1f) % 1f

    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val dialWidth = min(maxWidth.value, DIAL_MAX_WIDTH.value).dp

        // Fitted like the timer readout: full size if it fits inside the oval, scaled down if not.
        val refCap = 54.dp
        val refStyle = ClockFace.numerals(
            capHeight = refCap,
            width = ClockFace.READOUT_WIDTH,
            weight = ClockFace.READOUT_WEIGHT,
            roundness = ClockFace.READOUT_ROUNDNESS,
        )
        val refWidth = with(density) { measurer.measure("00:00.00", refStyle).size.width.toDp() }
        val maxText = dialWidth * 0.70f
        val cap = if (refWidth <= maxText) refCap else refCap * (maxText / refWidth)
        val textWidth = refWidth * (cap / refCap)

        Box(
            Modifier
                .width(dialWidth)
                .aspectRatio(DIAL_ASPECT)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(shape)
                .background(scheme.surfaceContainerHigh, shape)
                .border(1.25.dp, scheme.outline.copy(alpha = 0.12f), shape)
                .semantics { contentDescription = label }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (running) "Stop" else "Start",
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val quarter = scheme.primary.copy(alpha = 0.65f)
            val minor = scheme.onSurface.copy(alpha = 0.16f)
            val handColor = scheme.primary

            Canvas(Modifier.matchParentSize()) {
                val a = size.width / 2f
                val b = size.height / 2f
                val twelve = (PI / 2).toFloat()
                val full = (2 * PI).toFloat()

                val outerInset = 7.dp.toPx()
                for (i in 0 until 60) {
                    val angle = i / 60f * full - twelve
                    val big = i % 5 == 0
                    val len = if (big) 12.dp.toPx() else 6.dp.toPx()
                    val outer = stadiumRadius(angle, a - outerInset, b - outerInset)
                    val inner = stadiumRadius(angle, a - outerInset - len, b - outerInset - len)
                    drawLine(
                        color = if (big) quarter else minor,
                        start = Offset(a + cos(angle) * inner, b + sin(angle) * inner),
                        end = Offset(a + cos(angle) * outer, b + sin(angle) * outer),
                        strokeWidth = if (big) 2.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                val angle = handTurn * full - twelve
                val dx = cos(angle)
                val dy = sin(angle)
                val tip = stadiumRadius(angle, a - 26.dp.toPx(), b - 26.dp.toPx())

                // Start the hand where it leaves the box around the readout, so it never crosses
                // the digits.
                val halfW = textWidth.toPx() / 2f + 14.dp.toPx()
                val halfH = cap.toPx() / 2f + 14.dp.toPx()
                val tx = if (abs(dx) > 1e-4f) halfW / abs(dx) else Float.MAX_VALUE
                val ty = if (abs(dy) > 1e-4f) halfH / abs(dy) else Float.MAX_VALUE
                val start = min(tx, ty)

                if (tip > start) {
                    drawLine(
                        color = handColor,
                        start = Offset(a + dx * start, b + dy * start),
                        end = Offset(a + dx * tip, b + dy * tip),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(handColor, 5.dp.toPx(), Offset(a + dx * tip, b + dy * tip))
            }

            Numerals(
                text = text,
                capHeight = cap,
                color = scheme.primary,
                width = ClockFace.READOUT_WIDTH,
                weight = ClockFace.READOUT_WEIGHT,
                roundness = ClockFace.READOUT_ROUNDNESS,
            )
        }
    }
}

/**
 * Distance from the centre to the outline of a stadium with half-width [a] and half-height [b]
 * (so end caps of radius [b]), along [angle] in radians. Insetting both by the same amount gives
 * another stadium with the same straight section, which is how the tick rings are nested.
 */
private fun stadiumRadius(angle: Float, a: Float, b: Float): Float {
    val bb = b.coerceAtLeast(1f)
    val aa = a.coerceAtLeast(bb)
    val c = aa - bb
    val dx = abs(cos(angle))
    val dy = abs(sin(angle))
    // Straight top or bottom edge.
    if (dy > 1e-6f) {
        val t = bb / dy
        if (t * dx <= c) return t
    }
    // Semicircular end cap, centred (c, 0): |t·d − (c, 0)| = b.
    val dc = dx * c
    val disc = dc * dc - (c * c - bb * bb)
    return dc + sqrt(disc.coerceAtLeast(0f))
}

private val DIAL_MAX_WIDTH = 420.dp
private const val DIAL_ASPECT = 1.5f
