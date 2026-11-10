package app.materialclock.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.materialclock.core.ClockTimer
import app.materialclock.core.TimerPreset
import app.materialclock.core.TimerState
import app.materialclock.ui.theme.ClockFace
import app.materialclock.ui.theme.Numerals
import java.time.Duration

/**
 * The timer, in two states.
 *
 * **Setting** shows the entered time on one line, a ten-digit keypad, and a winder below it. The
 * winder is a drum seen edge-on rather than a slider; see [Winder] for why a slider was the wrong
 * shape for it. It and the keypad drive the same value, so you can type an exact 90 seconds or
 * wind roughly to twenty minutes, whichever the moment calls for.
 *
 * **Running** is a plain circular ring with the remaining time inside it, and three controls:
 * pause, +10 s and cancel. The ring and the digits follow Google's own timer: a thin arc that
 * shrinks smoothly toward 12 o'clock, and light, narrow numerals.
 *
 * A null timer *is* the setting state. There is no mode flag to fall out of step with the UI.
 */
private const val WIND_MAX_MINUTES = 60
private const val RING_SIZE_DP = 300

@Composable
fun TimersScreen(
    timer: ClockTimer?,
    draft: Duration,
    presets: List<TimerPreset>,
    nowElapsedMillis: Long,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onWind: (Int) -> Unit,
    onStart: () -> Unit,
    onPreset: (TimerPreset) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onPauseResume: () -> Unit,
    onAddTen: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = timer != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "timer-state",
        modifier = modifier.fillMaxSize().padding(contentPadding),
    ) { running ->
        if (running && timer != null) {
            RunningTimer(timer, nowElapsedMillis, onPauseResume, onAddTen, onCancel)
        } else {
            SetTimer(draft, presets, onDigit, onBackspace, onWind, onStart, onPreset, onEditPreset)
        }
    }
}

/* ── Setting ───────────────────────────────────────────────────────────────────────────────── */

@Composable
private fun SetTimer(
    draft: Duration,
    presets: List<TimerPreset>,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onWind: (Int) -> Unit,
    onStart: () -> Unit,
    onPreset: (TimerPreset) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
) {
    val total = draft.seconds
    val hh = "%02d".format(total / 3600)
    val mm = "%02d".format((total % 3600) / 60)
    val ss = "%02d".format(total % 60)
    val armed = total > 0

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        DraftReadout(hh, mm, ss)
        Spacer(Modifier.height(22.dp))
        Keypad(onDigit = onDigit, onBackspace = onBackspace)
        Spacer(Modifier.height(10.dp))
        // Below the keypad and above Start: you type an exact value or wind to a rough one, and
        // the winder sits next to the button it feeds.
        Winder(
            value = (total / 60).toInt().coerceAtMost(WIND_MAX_MINUTES),
            range = 0..WIND_MAX_MINUTES,
            onValueChange = onWind,
        )
        Spacer(Modifier.height(10.dp))
        WidePill(
            text = "Start",
            icon = Icons.Rounded.PlayArrow,
            onClick = onStart,
            height = 76.dp,
            container = if (armed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            content = if (armed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // Saved timers live under Start. They are added with the dock's plus button.
        if (presets.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PresetRow(presets, onPreset, onEditPreset)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Saved timers as pills, scrolling sideways when there are more than fit.
 *
 * Tap starts the timer under the preset's name; long-press opens it for editing or deleting.
 */
@Composable
private fun PresetRow(
    presets: List<TimerPreset>,
    onPreset: (TimerPreset) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { p ->
            PresetPill(p, onClick = { onPreset(p) }, onLongClick = { onEditPreset(p) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetPill(preset: TimerPreset, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = shape,
        modifier = Modifier
            .clip(shape)
            .combinedClickable(
                onClickLabel = "Start ${preset.name}",
                onClick = onClick,
                onLongClickLabel = "Edit ${preset.name}",
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minWidth = 88.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                preset.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preset.durationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

/**
 * `00:25:00` on one line, with the digits not yet reached dimmed.
 *
 * Two runs rather than a per-character colour, because each numeral run is one ink-tight box.
 * Dimming the lead-in is what makes the keypad feel like it is filling from the right rather than
 * replacing a whole field.
 */
@Composable
private fun DraftReadout(hh: String, mm: String, ss: String) {
    val text = "$hh:$mm:$ss"
    val firstReal = text.indexOfFirst { it in '1'..'9' }.let { if (it < 0) text.length - 1 else it }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Google's stopwatch digits are wide, so the readout is fitted to the width it has rather
        // than fixed: full size if it fits, scaled down if not.
        val refCap = 56.dp
        val refStyle = ClockFace.numerals(
            capHeight = refCap,
            width = ClockFace.READOUT_WIDTH,
            weight = ClockFace.READOUT_WEIGHT,
            roundness = ClockFace.READOUT_ROUNDNESS,
        )
        val refWidth = with(density) { measurer.measure(text, refStyle).size.width.toDp() }
        val cap = if (refWidth <= maxWidth) refCap else refCap * (maxWidth / refWidth)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "$hh hours, $mm minutes, $ss seconds"
            },
        ) {
            if (firstReal > 0) {
                Numerals(
                    text = text.take(firstReal),
                    capHeight = cap,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    width = ClockFace.READOUT_WIDTH,
                    weight = ClockFace.READOUT_WEIGHT,
                    roundness = ClockFace.READOUT_ROUNDNESS,
                )
            }
            Numerals(
                text = text.drop(firstReal),
                capHeight = cap,
                color = MaterialTheme.colorScheme.onSurface,
                width = ClockFace.READOUT_WIDTH,
                weight = ClockFace.READOUT_WEIGHT,
                roundness = ClockFace.READOUT_ROUNDNESS,
            )
        }
    }
}

/** Ten digits and a backspace on a 3 × 4 grid. */
@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> DigitKey(c, onDigit, Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            DigitKey('0', onDigit, Modifier.weight(1f))
            KeyBox(
                modifier = Modifier.weight(1f),
                onClick = onBackspace,
                container = Color.Transparent,
                label = "Delete",
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, onDigit: (Char) -> Unit, modifier: Modifier = Modifier) {
    KeyBox(modifier = modifier, onClick = { onDigit(digit) }, label = digit.toString()) {
        Text(
            digit.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = ClockFace.family(opticalSize = 28f, width = 100f, weight = 500),
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KeyBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = container,
        shape = CircleShape,
        modifier = modifier
            .height(58.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/* ── Running ───────────────────────────────────────────────────────────────────────────────── */

@Composable
private fun RunningTimer(
    timer: ClockTimer,
    nowElapsedMillis: Long,
    onPauseResume: () -> Unit,
    onAddTen: () -> Unit,
    onCancel: () -> Unit,
) {
    val remaining = timer.remaining(nowElapsedMillis)
    val secs = remaining.seconds
    val text = if (secs >= 3600) {
        "%d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
    } else {
        "%02d:%02d".format((secs % 3600) / 60, secs % 60)
    }
    val running = timer.state == TimerState.RUNNING

    // The ring moves in whole-second steps that match the readout, each one a short tween so it
    // reads as a tick rather than a jump, instead of sweeping continuously.
    val stepFraction = (secs.toFloat() / timer.total.seconds.coerceAtLeast(1L)).coerceIn(0f, 1f)
    val ringFraction by animateFloatAsState(stepFraction, tween(450, easing = LinearOutSlowInEasing), label = "ringStep")

    // Digits go thin while paused and thicken again on resume, as in Google's timer.
    val digitWeight by animateIntAsState(
        if (running) ClockFace.COUNTDOWN_WEIGHT else ClockFace.COUNTDOWN_PAUSED_WEIGHT,
        tween(300),
        label = "digitWeight",
    )

    // The readout is fitted, not fixed. "25:00" and "1:05:00" are different widths and the second
    // one ran straight out of the ring: a timer set past an hour is exactly when you would not be
    // watching it closely, so it must not be the case that breaks. Measured against the chord
    // available inside the stroke, then scaled down only if it does not fit, so short times keep
    // the full 62 dp.
    val measurer = rememberTextMeasurer()
    val maxTextWidth = RING_SIZE_DP.dp * 0.62f
    val capHeight = with(LocalDensity.current) {
        val ref = 62.dp
        val refStyle = ClockFace.numerals(
            capHeight = ref,
            width = ClockFace.COUNTDOWN_WIDTH,
            weight = ClockFace.COUNTDOWN_WEIGHT,
            roundness = ClockFace.COUNTDOWN_ROUNDNESS,
        )
        val measured = measurer.measure(text, refStyle).size.width.toDp()
        if (measured <= maxTextWidth) ref else ref * (maxTextWidth / measured)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            // A plain ring, like Google's own timer: the bright arc is the time that is left,
            // anchored at 12 o'clock and running clockwise, and the faded arc is the time already
            // used, with a small gap and round ends where the two meet. The sweep is continuous,
            // not stepped once a second, because `fractionLeft` is read every frame.
            //
            // The earlier ring was a wavy one with its own drifting wave. It was the busiest thing
            // on the screen for a readout that is supposed to be glanced at, and a thin quiet arc
            // lets the shrinking itself be the movement.
            CircularProgressIndicator(
                progress = { ringFraction },
                modifier = Modifier.size(RING_SIZE_DP.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                strokeWidth = 11.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 8.dp,
            )
            Numerals(
                text = text,
                capHeight = capHeight,
                // Softer than the ring, as in the recording: the arc is the loud part and the
                // digits are read against it.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                width = ClockFace.COUNTDOWN_WIDTH,
                weight = digitWeight,
                roundness = ClockFace.COUNTDOWN_ROUNDNESS,
            )
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                WidePill(
                    text = if (running) "Pause" else "Resume",
                    icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onPauseResume,
                    height = 78.dp,
                )
            }
            Box(Modifier.weight(1f)) {
                WidePill(
                    text = "+10s",
                    icon = Icons.Rounded.Add,
                    onClick = onAddTen,
                    height = 78.dp,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WidePill(
            text = "Cancel",
            icon = Icons.Rounded.Close,
            onClick = onCancel,
            // The same colours as the stopwatch's Stop button, so the two "end this" actions read
            // alike: Google's fixed red with a squarer corner, like its Stop button.
            container = Color(0xFFFF716C),
            content = Color(0xFF5C0A0A),
            shape = RoundedCornerShape(30.dp),
            height = 78.dp,
        )
        Spacer(Modifier.height(10.dp))
    }
}
