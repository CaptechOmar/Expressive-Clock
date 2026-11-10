package app.materialclock.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.alignByBaseline
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import app.materialclock.data.HourFormat
import app.materialclock.data.WorldClockSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.materialclock.ui.theme.ClockFace
import app.materialclock.core.WorldCity
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The world clock.
 *
 * Its hero is the concept's own idea and, as far as I can find, unique to it: **one dial with a
 * hand per city**, each hand tipped with a capsule carrying that city's name. Where every other
 * world clock makes you read six rows to answer "who is awake", this answers it as a shape. The
 * cities clustered near the bottom of the dial are the ones in the small hours.
 *
 * Its honest failure mode is on screen too: two cities on the same offset put their capsules in
 * exactly the same place. London and Algiers do it for most of the year. The dial draws them in
 * order and the list underneath is the fallback, which is why the list is not optional.
 */
private const val ROW_HEIGHT_DP = 128

/** Gap between rows. The pitch a dragged row has to travel to swap places is height + gap. */
private const val ROW_GAP_DP = 2

private val DAY_BADGE = Color(0xFFFFD54F)
private val NIGHT_BADGE = Color(0xFF7D83FD)

/**
 * The list is reorderable: press and hold a city, then drag it to a new place.
 *
 * ## How the drag works
 *
 * Every row is the same height, so the whole thing is arithmetic on one number, the **pitch** (row
 * plus gap). The dragged row keeps its slot in a working copy of the list and is drawn displaced by
 * [dragOffset]; when its centre crosses into a neighbour's slot the two swap in the working list
 * and the offset is reduced by one pitch, so the row stays exactly under the finger while the
 * neighbour animates into the space it left. No measured positions are read for the swap, which is
 * what keeps a fast drag from acting on a layout that is a frame out of date.
 *
 * The order is written to the store once, on release. Until the store answers, [pending] holds the
 * new order, so the list does not flash back to the old one for a frame.
 */
@Composable
fun WorldClockScreen(
    cities: List<WorldCity>,
    home: ZoneId,
    nowUtcMillis: Long,
    settings: WorldClockSettings,
    onRemove: (WorldCity) -> Unit,
    onReorder: (List<WorldCity>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val use24h = when (settings.hourFormat) {
        HourFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }
    val measurer = rememberTextMeasurer()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val pitchPx = with(LocalDensity.current) { (ROW_HEIGHT_DP + ROW_GAP_DP).dp.toPx() }

    // The row being dragged, how far it is displaced from its slot, and whether a finger is still
    // down. The key outlives the finger by the length of the settle animation.
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var fingerDown by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(cities) }
    var pending by remember { mutableStateOf<List<WorldCity>?>(null) }

    val shown = if (draggedKey != null) working else (pending ?: cities)

    // The store has answered, so what it holds is now the truth. The timeout covers a write that
    // was refused and so never produces a new list.
    LaunchedEffect(cities) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(1500)
            pending = null
        }
    }

    /** Swaps the dragged row with whichever slot its centre is now over. */
    fun settle() {
        val key = draggedKey ?: return
        val from = working.indexOfFirst { it.zone.id == key }
        if (from < 0) return
        val centre = from * pitchPx + dragOffset + pitchPx / 2f
        val to = floor(centre / pitchPx).toInt().coerceIn(0, working.lastIndex)
        if (to != from) {
            working = working.toMutableList().apply { add(to, removeAt(from)) }
            dragOffset -= (to - from) * pitchPx
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun startDrag(key: String) {
        if (draggedKey != null) return
        working = pending ?: cities
        dragOffset = 0f
        draggedKey = key
        fingerDown = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        if (draggedKey == null || !fingerDown) return
        dragOffset += dy
        settle()
    }

    fun endDrag() {
        if (draggedKey == null || !fingerDown) return
        fingerDown = false
        scope.launch {
            // Glide into the slot rather than snapping the last half-row.
            animate(initialValue = dragOffset, targetValue = 0f, animationSpec = settleSpec) { v, _ ->
                dragOffset = v
            }
            val result = working
            draggedKey = null
            if (result.map { it.zone.id } != cities.map { it.zone.id }) {
                pending = result
                onReorder(result)
            }
        }
    }

    /** The same move without a gesture, for a screen reader's custom actions. */
    fun nudge(city: WorldCity, by: Int) {
        val list = pending ?: cities
        val from = list.indexOfFirst { it.zone.id == city.zone.id }
        val to = from + by
        if (from < 0 || to !in list.indices) return
        val moved = list.toMutableList().apply { add(to, removeAt(from)) }
        pending = moved
        onReorder(moved)
    }

    // Edge scrolling. A drag has to be able to carry a row further than the screen shows, and it
    // has to keep going while the finger is held still at the edge, so this runs on frames, not on
    // touch events. The list moving under a stationary finger is a displacement the row has to
    // make up, hence the offset grows by whatever the scroll consumed.
    LaunchedEffect(draggedKey) {
        val key = draggedKey ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!fingerDown) continue
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = item.offset + dragOffset
            val bottom = top + item.size
            val low = info.viewportStartOffset + info.beforeContentPadding
            val high = info.viewportEndOffset - info.afterContentPadding
            val margin = pitchPx * 0.25f
            val step = pitchPx * 0.08f
            val delta = when {
                top < low + margin -> -step
                bottom > high - margin -> step
                else -> 0f
            }
            if (delta != 0f) {
                dragOffset += listState.dispatchRawDelta(delta)
                settle()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp),
    ) {
        item {
            CityDial(
                cities = shown,
                nowUtcMillis = nowUtcMillis,
                measurer = measurer,
                modifier = Modifier
                    .fillMaxWidth()
                    // Not a full-width circle: at 130 dp per row a 372 dp dial leaves room for
                    // barely one city, and the list is the dial's fallback for colliding offsets.
                    .padding(start = 54.dp, end = 54.dp, top = 4.dp, bottom = 18.dp)
                    .aspectRatio(1f),
            )
        }
        items(shown, key = { it.zone.id }) { city ->
            val dragging = city.zone.id == draggedKey
            CityRow(
                city = city,
                home = home,
                nowUtcMillis = nowUtcMillis,
                use24h = use24h,
                showSeconds = settings.showSeconds,
                onRemove = { onRemove(city) },
                dragging = dragging,
                onDragStart = { startDrag(city.zone.id) },
                onDrag = { dy -> dragBy(dy) },
                onDragEnd = { endDrag() },
                onNudge = { by -> nudge(city, by) },
                // The dragged row is placed by hand, so it must not also be animated into place:
                // the two would fight, and the row would lag behind the finger.
                modifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer {
                        translationY = dragOffset
                        scaleX = 1.03f
                        scaleY = 1.03f
                    }
                } else {
                    Modifier.animateItem()
                },
            )
        }
    }
}

@Composable
private fun CityDial(
    cities: List<WorldCity>,
    nowUtcMillis: Long,
    measurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val face = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant

    val hourStyle = MaterialTheme.typography.headlineMedium.copy(color = ghost.copy(alpha = .45f))
    val pillStyle = MaterialTheme.typography.labelSmall.copy(
        color = onAccent,
        fontWeight = FontWeight.SemiBold,
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(face, radius = r, center = c)

        // 12 / 3 / 6 / 9 set large and ghosted; the rest are dots. Straight from the render.
        for (i in 0 until 12) {
            val a = (i / 12f) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val p = Offset(c.x + cos(a) * (r - 44.dp.toPx()), c.y + sin(a) * (r - 44.dp.toPx()))
            if (i % 3 == 0) {
                val label = if (i == 0) "12" else i.toString()
                val m = measurer.measure(label, hourStyle)
                drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
            } else {
                drawCircle(accent.copy(alpha = .45f), radius = 3.5.dp.toPx(), center = p)
            }
        }

        cities.forEach { city ->
            val local = city.timeAt(nowUtcMillis)
            val hours12 = (local.hour % 12) + local.minute / 60f
            val angle = hours12 / 12f * 360f - 90f
            val rad = angle * PI.toFloat() / 180f
            val len = r - 62.dp.toPx()
            val tip = Offset(c.x + cos(rad) * len, c.y + sin(rad) * len)

            drawLine(accent, start = c, end = tip, strokeWidth = 2.dp.toPx())

            val m = measurer.measure(city.city, pillStyle)
            val padH = 7.dp.toPx()
            val w = m.size.width + padH * 2
            val h = m.size.height + 5.dp.toPx()
            // Flip through 180° in the lower half so a name is never upside down, which is what
            // the render does: its upper-left hands read normally rather than mirrored.
            val flip = if (angle.mod(360f) in 90f..270f) 180f else 0f
            rotate(degrees = angle + flip, pivot = tip) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(tip.x - w / 2f, tip.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                )
                drawText(
                    m,
                    topLeft = Offset(tip.x - m.size.width / 2f, tip.y - m.size.height / 2f),
                )
            }
        }
        drawCircle(accent, radius = 7.dp.toPx(), center = c)
    }
}

/**
 * A city row.
 *
 * Measured, and much larger than a list item: **128 dp tall on a 2 dp gap**, so the pitch is 130.
 * The right end is a true semicircle (the corner radius is exactly half the row height), not a
 * 24-28 dp rounded corner. Two earlier passes had these at 56 and then 88 dp, and both read as an
 * ordinary list; at 128 the time can be set at display size, which is the point. The time is the
 * content and the city is its label, not the other way round.
 */
/**
 * A city, removable by swiping it away to the left.
 *
 * `SwipeToDismissBox` is the component for this, and the spec's rule for a swipeable container is
 * "one swipe action", which is why only end-to-start is enabled and start-to-end is off, rather
 * than offering two directions that do the same thing. The revealed layer is the error container
 * with a trailing bin, which is the standard destructive treatment and reads before the row has
 * travelled far enough to commit.
 *
 * It replaced a confirmation dialog. The dialog was defensible while the gesture was invisible; with
 * a swipe, the gesture is deliberate enough to stand on its own, and the safety net moves to an
 * Undo snackbar. That is both the documented mitigation and less work to use than a dialog you
 * have to answer every time.
 *
 * Press and hold is the other gesture on the row: it picks the row up to be dragged to a new place
 * in the list. The swipe is switched off while that is happening.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CityRow(
    city: WorldCity,
    home: ZoneId,
    nowUtcMillis: Long,
    use24h: Boolean,
    showSeconds: Boolean,
    onRemove: () -> Unit,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture below is installed once per row, so it has to read the latest callbacks rather
    // than the ones from the composition that created it.
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val local = city.timeAt(nowUtcMillis)
    val night = city.isNight(nowUtcMillis)
    val clock = buildString {
        if (use24h) {
            append("%02d:%02d".format(local.hour, local.minute))
        } else {
            append("%d:%02d".format((local.hour % 12).takeIf { it != 0 } ?: 12, local.minute))
        }
        if (showSeconds) append(":%02d".format(local.second))
    }
    val meridiem = if (use24h) null else if (local.hour < 12) "AM" else "PM"

    // A plain `remember`, not `rememberSwipeToDismissBoxState`.
    //
    // That helper is *saveable* and LazyColumn restores saved state per item key, so a city that
    // is swiped away and then comes back, whether by Undo or by adding it again, lands on its old
    // saved `EndToStart` value. `SwipeToDismissBox` then sees a box that is already dismissed and
    // fires `onDismiss` on its first composition, deleting the row the instant it returns. The
    // symptom is baffling from the outside: the store logs the city restored and the screen stays
    // empty. Swipe offset is transient gesture state and has no business outliving the row.
    val state = remember(city.zone.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { distance: Float -> distance * 0.5f },
        )
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        // One swipe action per container, per the spec. Two directions doing the same thing is not
        // twice the affordance, it is a coin flip about whether the row came back.
        enableDismissFromStartToEnd = false,
        // Off while the row is being carried, so a drag that wanders sideways cannot also delete it.
        gesturesEnabled = !dragging,
        onDismiss = { onRemove() },
        backgroundContent = {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(ROW_HEIGHT_DP.dp),
            ) {
                Box(Modifier.fillMaxSize().padding(end = 32.dp), Alignment.CenterEnd) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(percent = 50),
            // Lifted while carried, which is what says it has been picked up.
            shadowElevation = if (dragging) 12.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(ROW_HEIGHT_DP.dp)
                .pointerInput(city.zone.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            currentDrag(amount.y)
                        },
                        onDragEnd = { currentEnd() },
                        onDragCancel = { currentEnd() },
                    )
                }
                .semantics {
                    // A swipe is a gesture, and a gesture is invisible to a screen reader. The same
                    // action has to exist as a verb it can announce and perform.
                    customActions = listOf(
                        CustomAccessibilityAction("Remove ${city.city}") { onRemove(); true },
                        CustomAccessibilityAction("Move ${city.city} up") { onNudge(-1); true },
                        CustomAccessibilityAction("Move ${city.city} down") { onNudge(1); true },
                    )
                },
        ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Day or night, not an initial. The initial says nothing you cannot read in the name
            // beside it; whether they are awake is the only thing a world clock is actually for.
            //
            // Fixed colours, not theme roles: the badge is the one thing on the row that should
            // read the same whatever wallpaper the palette was drawn from.
            Surface(
                color = if (night) NIGHT_BADGE else DAY_BADGE,
                shape = CircleShape,
                modifier = Modifier.size(51.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (night) Icons.Outlined.Bedtime else Icons.Outlined.LightMode,
                        contentDescription = if (night) "night" else "daytime",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 9.6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    city.city,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${city.region} · ${city.offsetLabel(home, nowUtcMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Google's clock face for the time, with AM/PM small beside it on the same baseline,
            // the way Google's own world clock sets it, rather than lower-case on the end.
            val digitStyle = (
                if (showSeconds) MaterialTheme.typography.titleLargeEmphasized
                else MaterialTheme.typography.headlineMediumEmphasized
                ).copy(
                fontFamily = ClockFace.family(
                    opticalSize = if (showSeconds) 22f else 28f,
                    width = ClockFace.READOUT_WIDTH,
                    weight = 500,
                    roundness = ClockFace.READOUT_ROUNDNESS,
                ),
            )
            val meridiemStyle = MaterialTheme.typography.labelLargeEmphasized.copy(
                fontFamily = ClockFace.family(
                    opticalSize = 14f,
                    width = 100f,
                    weight = 600,
                    roundness = ClockFace.READOUT_ROUNDNESS,
                ),
            )
            Row {
                Text(
                    clock,
                    style = digitStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                if (meridiem != null) {
                    Text(
                        meridiem,
                        style = meridiemStyle,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                    )
                }
            }
        }
    }
    }
}
