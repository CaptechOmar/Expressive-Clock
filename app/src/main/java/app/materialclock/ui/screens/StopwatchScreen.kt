package app.materialclock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.materialclock.core.Lap
import app.materialclock.core.Stopwatch
import app.materialclock.core.stopwatchParts
import app.materialclock.ui.theme.ClockFace

/**
 * The stopwatch.
 *
 * Top to bottom: the oval dial (see [StopwatchDial]), a horizontal strip of lap cards that only
 * exists once there is a lap, and the buttons. Nothing scrolls vertically. The buttons take
 * whatever height the dial and the lap strip leave, so everything always fits on the screen.
 *
 * The buttons follow Google's Clock:
 *
 *  - **Idle:** one big pill, *Start*, in the tertiary colour.
 *  - **Running:** a big, squarer, red *Stop* with *Reset* and *Lap* under it.
 *  - **Stopped:** *Start* again, with *Reset* under it, and no *Lap*.
 *
 * The three slots never move: the big button is always the same size and in the same place, and
 * Reset and Lap fade into their own fixed slots.
 */
@Composable
fun StopwatchScreen(
    stopwatch: Stopwatch,
    nowElapsedMillis: Long,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val elapsed = stopwatch.elapsed(nowElapsedMillis)
    val (a, b, c) = elapsed.stopwatchParts()
    val hasLaps = stopwatch.laps.isNotEmpty()

    // A tick for every press. Start and Stop (and a tap on the dial, which does the same thing) are
    // an "on" and an "off", Lap is a click, and Reset is the heavier one.
    val haptics = LocalHapticFeedback.current
    val toggle: () -> Unit = {
        haptics.performHapticFeedback(
            if (stopwatch.running) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
        )
        onToggle()
    }
    val lap: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onLap()
    }
    val reset: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onReset()
    }

    // The strip's band opens and closes, and the buttons below take up the difference.
    val band by animateDpAsState(
        targetValue = if (hasLaps) LAP_CARD_HEIGHT + SECTION_GAP else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "lapBand",
    )

    // With only the Start button on screen, it sits lower, further from the dial. It is an offset
    // rather than a bigger gap so the button keeps its size and the other states do not change.
    val idle = !stopwatch.running && elapsed.isZero
    val drop = when {
        idle -> IDLE_DROP
        // Stopped with Start and Reset: nearly as far from the dial as when only Start shows, and
        // a little further from the laps when there are some.
        !stopwatch.running && !hasLaps -> STOPPED_DROP
        !stopwatch.running -> STOPPED_LAPS_DROP
        // Running with no laps: Stop is the smaller one, so it sits a touch lower.
        !hasLaps -> RUNNING_DROP
        else -> 0.dp
    }
    val idleDrop by animateDpAsState(
        targetValue = drop,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "idleDrop",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        StopwatchDial(
            elapsedMillis = elapsed.toMillis(),
            running = stopwatch.running,
            text = if (elapsed.toHours() > 0) "$a:$b:$c" else "$a:$b.$c",
            label = "Stopwatch, $a minutes $b seconds $c hundredths" +
                if (stopwatch.running) ", running" else ", stopped",
            onToggle = toggle,
        )

        Spacer(Modifier.height(SECTION_GAP))
        Box(Modifier.fillMaxWidth().height(band).clipToBounds()) {
            LapStrip(stopwatch)
        }

        StopwatchButtons(
            running = stopwatch.running,
            idle = idle,
            compact = !hasLaps,
            onToggle = toggle,
            onLap = lap,
            onReset = reset,
            modifier = Modifier.fillMaxWidth().weight(1f).offset(y = idleDrop),
        )
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Start/Stop, Reset and Lap, sized from the height they are given.
 *
 * The big button gets [MAIN_WEIGHT] parts and each of the other two gets one, which reproduces
 * Google's proportions (about 137 dp over two 94 dp pills) on a tall screen and shrinks evenly
 * on a short one. The sizes are worked out for the full three-button stack, so the big button
 * stays put whether or not Reset and Lap are showing.
 */
@Composable
private fun StopwatchButtons(
    running: Boolean,
    idle: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val unit = ((maxHeight - BUTTON_GAP * 2) / (MAIN_WEIGHT + 2f)).coerceAtLeast(24.dp)
        val fullHeight = unit * MAIN_WEIGHT
        // Running with no laps there is room to spare, so Stop is a little smaller.
        val mainHeight by animateDpAsState(
            if (running && compact) fullHeight * RUNNING_NO_LAPS_SCALE else fullHeight,
            spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "mainHeight",
        )

        val container by animateColorAsState(
            if (running) STOP_RED else scheme.tertiaryContainer,
            tween(200),
            label = "mainContainer",
        )
        val content by animateColorAsState(
            if (running) STOP_RED_ON else scheme.onTertiaryContainer,
            tween(200),
            label = "mainContent",
        )
        // Fully round while it says Start; squarer while it says Stop.
        val corner by animateDpAsState(
            if (running) 30.dp else mainHeight / 2,
            spring(dampingRatio = 0.75f, stiffness = 500f),
            label = "mainCorner",
        )

        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            WidePill(
                text = if (running) "Stop" else "Start",
                onClick = onToggle,
                container = container,
                content = content,
                shape = RoundedCornerShape(corner),
                height = mainHeight,
            )
            Slot(unit, visible = !idle) {
                WidePill(
                    text = "Reset",
                    onClick = onReset,
                    container = scheme.surfaceContainerHigh,
                    content = scheme.onSurface,
                    height = unit,
                )
            }
            Slot(unit, visible = running) {
                WidePill(
                    text = "Lap",
                    onClick = onLap,
                    container = scheme.surfaceContainerHigh,
                    content = scheme.onSurface,
                    height = unit,
                )
            }
        }
    }
}

/** A fixed-height slot, so a button appearing never moves anything else. */
@Composable
private fun Slot(height: Dp, visible: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(100)),
        ) { content() }
    }
}

/**
 * The laps, oldest on the left, as a row of cards that scrolls sideways. Three fit across; a
 * fourth lap glides the row along, which is the only hint that it scrolls.
 */
@Composable
private fun LapStrip(stopwatch: Stopwatch) {
    val laps = stopwatch.laps.sortedBy { it.index }
    val listState = rememberLazyListState()
    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) listState.animateScrollToItem(laps.lastIndex)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - LAP_GAP * 2) / 3
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(LAP_CARD_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(LAP_GAP),
        ) {
            items(laps, key = { it.index }) { lap ->
                LapCard(
                    lap = lap,
                    isFastest = stopwatch.fastest == lap.index,
                    isSlowest = stopwatch.slowest == lap.index,
                    modifier = Modifier.width(cardWidth).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * One lap: index, split and running total. The fastest is a solid tertiary card, the slowest a
 * solid red one with dark text, and the rest the same dark colour as the Reset and Lap buttons.
 */
@Composable
private fun LapCard(lap: Lap, isFastest: Boolean, isSlowest: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        isFastest -> scheme.tertiaryContainer
        isSlowest -> STOP_RED
        else -> scheme.surfaceContainerHigh
    }
    val onContainer = when {
        isFastest -> scheme.onTertiaryContainer
        isSlowest -> STOP_RED_ON
        else -> scheme.onSurface
    }
    val (m, s, cs) = lap.split.stopwatchParts()
    val (tm, ts, tcs) = lap.total.stopwatchParts()
    // The same wide, round digits as the dial, so the laps read as part of the same display.
    val digits = ClockFace.family(
        opticalSize = 16f,
        width = ClockFace.READOUT_WIDTH,
        weight = 500,
        roundness = ClockFace.READOUT_ROUNDNESS,
    )

    Surface(
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "%02d".format(lap.index),
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFamily = digits),
            )
            Text(
                "$m:$s.$cs",
                style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = digits),
                maxLines = 1,
            )
            Text(
                "$tm:$ts.$tcs",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = digits),
                maxLines = 1,
            )
        }
    }
}

/** The same red as the timer's Cancel, and Google's Stop. Dark text on it. */
private val STOP_RED = Color(0xFFFF716C)
private val STOP_RED_ON = Color(0xFF5C0A0A)

/** Google's big button is about 1.46 times the height of the pills under it. */
private const val MAIN_WEIGHT = 1.45f

private val BUTTON_GAP = 8.dp
private val SECTION_GAP = 14.dp

/** How much lower the lone Start button sits than the full stack's big button. */
private val IDLE_DROP = 64.dp
private val STOPPED_DROP = 48.dp
private val STOPPED_LAPS_DROP = 20.dp
private val RUNNING_DROP = 14.dp

/** The big Stop button, when it is running with no laps, is this much of its full height. */
private const val RUNNING_NO_LAPS_SCALE = 0.85f
private val LAP_GAP = 8.dp
private val LAP_CARD_HEIGHT = 112.dp
