package app.materialclock.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.materialclock.alarm.Notifications
import app.materialclock.core.Alarm
import app.materialclock.core.TimerPreset
import app.materialclock.ui.screens.AlarmsScreen
import app.materialclock.ui.screens.StopwatchScreen
import app.materialclock.ui.screens.TimersScreen
import app.materialclock.ui.screens.WorldClockScreen
import app.materialclock.ui.sheets.AddCitySheet
import app.materialclock.ui.sheets.AlarmEditSheet
import app.materialclock.ui.sheets.AlarmSettingsSheet
import app.materialclock.ui.sheets.PresetSheet
import app.materialclock.ui.sheets.StopwatchSettingsSheet
import app.materialclock.ui.sheets.TimerSettingsSheet
import app.materialclock.ui.sheets.WorldSettingsSheet
import app.materialclock.ui.theme.ClockTheme

/** Breathing room between the dock and the navigation bar. */
private val DOCK_CLEARANCE = 20.dp

/**
 * How much of the bottom of every screen the floating dock is standing on.
 *
 * Derived from [DOCK_HEIGHT] rather than restated as a number. It was written out as a literal 96
 * once ("64 of pill, 20 under it, 12 above the last row"), and the moment the pill grew to 72 the
 * comment was still true and the constant was silently 8 dp short, which shows up as the last row
 * of every list tucked a little too far under the dock. There is nothing to notice and nothing to
 * fail; it just quietly looks wrong.
 */
private val DOCK_RESERVE = DOCK_HEIGHT + DOCK_CLEARANCE + 12.dp

enum class Tab(val label: String, val icon: ImageVector, val key: String) {
    ALARMS("Alarms", Icons.Outlined.Alarm, Notifications.TAB_ALARMS),
    WORLD("World Clock", Icons.Outlined.Public, "world"),
    TIMERS("Timers", Icons.Outlined.HourglassEmpty, Notifications.TAB_TIMERS),
    STOPWATCH("Stopwatch", Icons.Outlined.Timer, Notifications.TAB_STOPWATCH),
}

/**
 * The app shell.
 *
 * Navigation is [ClockDock], a floating pill with a detached add button and no navigation bar, so
 * the alarm grid and the world-clock list run full-bleed to the bottom edge. That component owes
 * the accessibility contract a `ShortNavigationBar` would have supplied for free; see its own
 * documentation for what it pays and why.
 *
 * ## Where the settings are
 *
 * In the title. Tapping "Alarms" opens the alarm settings, tapping "World Clock" opens that tab's,
 * and so on; Stopwatch has none and is inert. There is no gear and no ripple. See
 * [app.materialclock.ui.sheets.AlarmSettingsSheet] for why an unmarked affordance is the right
 * trade for preferences you set twice a year.
 */
@Composable
fun ClockApp(startTab: String? = null, vm: ClockViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    ClockTheme(settings.theme) {
        var tab by rememberSaveable { mutableStateOf(startTab?.let { k -> Tab.entries.firstOrNull { it.key == k } } ?: Tab.ALARMS) }
        var editing by remember { mutableStateOf<Alarm?>(null) }
        var showSettings by remember { mutableStateOf(false) }
        var addingCity by remember { mutableStateOf(false) }
        var editingPreset by remember { mutableStateOf<TimerPreset?>(null) }
        // Read here as well as inside the Timers tab: the dock's add button has to know whether
        // the setting screen (where presets live) is the one being shown.
        val activeTimer by vm.timer.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Asked once, on first composition. Denying it costs the notifications and nothing else.
        // The alarm still rings, because the ringer is a foreground service and audio, not a post.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        val interaction = remember { MutableInteractionSource() }
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.headlineSmall,
                            // No indication: the point is that it does not announce itself.
                            modifier = Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
    
                                onClickLabel = "Open ${tab.label.lowercase()} settings",
                            ) { showSettings = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            // Keep the bars' insets but let each screen own its horizontal margin, so a grid
            // and a full-bleed dial can differ without fighting the Scaffold.
            //
            // The dock floats over the content rather than displacing it, so the space it occupies
            // has to be reserved here by hand; otherwise the last alarm tile sits under the pill.
            val ld = LocalLayoutDirection.current
            val underDock = padding.calculateBottomPadding() + DOCK_RESERVE
            val body = PaddingValues(
                start = padding.calculateStartPadding(ld) + 16.dp,
                end = padding.calculateEndPadding(ld) + 16.dp,
                top = padding.calculateTopPadding(),
                bottom = underDock,
            )
            val edgeToEdge = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = underDock,
            )

          Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.ALARMS -> {
                        val alarms by vm.alarms.collectAsStateWithLifecycle()
                        AlarmsScreen(
                            alarms = alarms,
                            weekStart = settings.alarms.weekStart,
                            onToggle = vm::toggleAlarm,
                            onEdit = { editing = it },
                            contentPadding = body,
                        )
                    }

                    Tab.WORLD -> {
                        val cities by vm.cities.collectAsStateWithLifecycle()
                        val now by rememberWallTicker()
                        WorldClockScreen(
                            cities = cities,
                            home = vm.homeZone,
                            nowUtcMillis = now,
                            settings = settings.world,
                            // Swiping a city away is instant and undoable, rather than instant and
                            // final or safe and nagging. The snackbar is the spec's own mitigation
                            // for a destructive swipe, and it costs one tap instead of one per
                            // deletion the way a confirmation dialog did.
                            onRemove = { city ->
                                vm.removeCity(city.zone)
                                scope.launch {
                                    val r = snackbar.showSnackbar(
                                        message = "Removed ${city.city}",
                                        actionLabel = "Undo",
                                        // Short, explicitly. With an action label the default is
                                        // Indefinite, which leaves a bar you have to dismiss by
                                        // hand sitting over the list after every swipe, and the
                                        // separate dismiss affordance it needs then crowds the
                                        // action itself. An undo that expires is the normal one.
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (r == SnackbarResult.ActionPerformed) vm.addCity(city)
                                }
                            },
                            onReorder = vm::reorderCities,
                            contentPadding = edgeToEdge,
                        )
                    }

                    Tab.TIMERS -> {
                        val timer by vm.timer.collectAsStateWithLifecycle()
                        // Only tick while a timer exists; the setting screen has nothing moving.
                        val now by rememberElapsedTicker(active = timer != null)
                        val presets by vm.presets.collectAsStateWithLifecycle()
                        TimersScreen(
                            timer = timer,
                            draft = vm.draftDuration,
                            presets = presets,
                            nowElapsedMillis = now,
                            onDigit = vm::pressDigit,
                            onBackspace = vm::backspace,
                            onWind = vm::windToMinutes,
                            onStart = { vm.startTimer() },
                            onPreset = vm::startPreset,
                            onEditPreset = { editingPreset = it },
                            onPauseResume = { vm.pauseOrResumeTimer() },
                            onAddTen = { vm.addTenSeconds() },
                            onCancel = { vm.cancelTimer() },
                            contentPadding = edgeToEdge,
                        )
                    }

                    Tab.STOPWATCH -> {
                        val sw by vm.stopwatch.collectAsStateWithLifecycle()
                        val now by rememberElapsedTicker(active = sw.running)
                        StopwatchScreen(
                            stopwatch = sw,
                            nowElapsedMillis = now,
                            onToggle = { vm.toggleStopwatch() },
                            onLap = { vm.lap() },
                            onReset = { vm.resetStopwatch() },
                            contentPadding = edgeToEdge,
                        )
                    }
                }
            }

            ClockDock(
                destinations = Tab.entries,
                selected = tab,
                onSelect = { tab = it },
                // Alarms, cities and timer presets are lists you add to. Stopwatch has nothing to
                // add, and neither does a timer that is already running (the presets are on the
                // setting screen), so the button leaves rather than sitting there inert.
                showAdd = tab == Tab.ALARMS || tab == Tab.WORLD ||
                    (tab == Tab.TIMERS && activeTimer == null),
                addLabel = when (tab) {
                    Tab.WORLD -> "Add city"
                    Tab.TIMERS -> "Add timer preset"
                    else -> "Add alarm"
                },
                onAdd = {
                    when (tab) {
                        Tab.WORLD -> addingCity = true
                        Tab.TIMERS -> editingPreset = vm.presetFromDraft()
                        else -> editing = vm.blankAlarm()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = DOCK_CLEARANCE),
            )
          }
        }

        editing?.let { draft ->
            AlarmEditSheet(
                initial = draft,
                weekStart = settings.alarms.weekStart,
                onDismiss = { editing = null },
                onSave = { vm.saveAlarm(it) },
                onDelete = if (draft.id == 0L) null else ({ id -> vm.deleteAlarm(id) }),
            )
        }

        editingPreset?.let { draft ->
            PresetSheet(
                initial = draft,
                onDismiss = { editingPreset = null },
                onSave = { vm.savePreset(it) },
                onDelete = if (draft.id == 0L) null else ({ id -> vm.deletePreset(id) }),
            )
        }

        if (addingCity) {
            val cities by vm.cities.collectAsStateWithLifecycle()
            val now by rememberWallTicker(60_000L)
            AddCitySheet(
                existing = cities,
                nowUtcMillis = now,
                onAdd = vm::addCity,
                onDismiss = { addingCity = false },
            )
        }

        if (showSettings) {
            when (tab) {
                Tab.ALARMS -> AlarmSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.WORLD -> WorldSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.TIMERS -> TimerSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.STOPWATCH ->
                    StopwatchSettingsSheet(settings, vm::updateSettings) { showSettings = false }
            }
        }
    }
}
