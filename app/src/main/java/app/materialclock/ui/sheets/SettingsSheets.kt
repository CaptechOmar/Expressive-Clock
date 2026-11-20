package app.materialclock.ui.sheets

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.materialclock.data.ClockSettings
import app.materialclock.data.DarkMode
import app.materialclock.data.HourFormat
import app.materialclock.data.WeekStart
import app.materialclock.ui.theme.Palette
import app.materialclock.widget.WidgetEntryPoints

/**
 * One settings page per tab, opened from that tab's own title. Every page uses the shared
 * [SettingsPage] look (see SettingsPage.kt); the Stopwatch tab's page holds the app-wide
 * appearance, widget and About entries.
 */
@Composable
fun AlarmSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Re-read on every recomposition: the user grants this in system settings and comes back.
    val needsFullScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.canUseFullScreenIntent() == false

    SettingsPage("Alarm settings", onDismiss) {
        SettingsLabel("Ringing")
        SettingsGroup {
            row { s ->
                SettingsChoice(
                    s, Icons.Rounded.TimerOff, "Silence after",
                    value = settings.alarms.silenceAfterMinutes,
                    // 0 is "Never" (it rings until dismissed), last so nobody scrolls into it.
                    options = listOf(1, 5, 10, 15, 20, 25, 30, 0),
                    label = ::minutesLabel,
                    onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(silenceAfterMinutes = v)) } },
                )
            }
            row { s ->
                SettingsChoice(
                    s, Icons.Rounded.Snooze, "Snooze length",
                    value = settings.alarms.snoozeMinutes,
                    options = listOf(1, 5, 10, 15, 20, 30),
                    label = ::minutesLabel,
                    onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(snoozeMinutes = v)) } },
                )
            }
            if (needsFullScreen) {
                row { s ->
                    SettingsRow(
                        s, Icons.Rounded.Fullscreen, "Full-screen alarms",
                        "Not allowed, so a ringing alarm will show as a banner instead",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                )
                            }
                        },
                    )
                }
            }
        }
        SettingsLabel("Calendar")
        SettingsGroup {
            row { s ->
                SettingsChoice(
                    s, Icons.Rounded.CalendarMonth, "Start week on",
                    value = settings.alarms.weekStart,
                    options = WeekStart.entries,
                    label = { it.label },
                    onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(weekStart = v)) } },
                )
            }
        }
    }
}

/**
 * Appearance, widgets and About, on the Stopwatch tab: they are app-wide, and the stopwatch has no
 * settings of its own.
 */
@Composable
fun StopwatchSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var about by remember { mutableStateOf(false) }

    SettingsPage("Appearance", onDismiss) {
        SettingsLabel("App looks")
        SettingsGroup {
            row { s ->
                SettingsChoice(
                    s, Icons.Rounded.DarkMode, "Theme",
                    value = settings.theme.darkMode,
                    options = DarkMode.entries,
                    label = { it.label },
                    onSelect = { v -> onChange { it.copy(theme = it.theme.copy(darkMode = v)) } },
                )
            }
            row { s ->
                SettingsSwitch(
                    s, Icons.Rounded.Contrast, "Pure black",
                    "True black surfaces in dark mode, for OLED screens",
                    checked = settings.theme.amoledBlack,
                    onChange = { v -> onChange { it.copy(theme = it.theme.copy(amoledBlack = v)) } },
                )
            }
            row { s ->
                // Always on show, with the wallpaper as the first swatch rather than as a switch.
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(s)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    PaletteRow(
                        dynamic = settings.theme.dynamicColor,
                        selected = settings.theme.palette,
                        onDynamic = { onChange { it.copy(theme = it.theme.copy(dynamicColor = true)) } },
                        onSelect = { v ->
                            onChange { it.copy(theme = it.theme.copy(palette = v, dynamicColor = false)) }
                        },
                    )
                }
            }
        }

        SettingsLabel("Widgets")
        SettingsGroup {
            row { s ->
                if (WidgetEntryPoints.canPin(context)) {
                    SettingsRow(
                        s, Icons.Rounded.Widgets, "Add a clock widget",
                        "Long-press it on the home screen to change its look",
                        onClick = { WidgetEntryPoints.requestPin(context) },
                    )
                } else {
                    SettingsRow(
                        s, Icons.Rounded.Widgets, "Clock widget",
                        "Add one from your home screen's widget picker",
                    )
                }
            }
        }

        SettingsLabel("Additional")
        SettingsGroup {
            row { s ->
                SettingsRow(
                    s, Icons.Rounded.Info, "About app",
                    "Privacy, permissions, licences and version",
                    onClick = { about = true },
                )
            }
        }
    }
    if (about) AboutPage(onBack = { about = false })
}

@Composable
fun WorldSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPage("World clock settings", onDismiss) {
        SettingsLabel("Display")
        SettingsGroup {
            row { s ->
                SettingsSwitch(
                    s, Icons.Rounded.Schedule, "Display time with seconds",
                    checked = settings.world.showSeconds,
                    onChange = { v -> onChange { it.copy(world = it.world.copy(showSeconds = v)) } },
                )
            }
            row { s ->
                SettingsChoice(
                    s, Icons.Rounded.AccessTime, "Time format",
                    value = settings.world.hourFormat,
                    options = HourFormat.entries,
                    label = { it.label },
                    onSelect = { v -> onChange { it.copy(world = it.world.copy(hourFormat = v)) } },
                )
            }
        }
    }
}

@Composable
fun TimerSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showSounds by remember { mutableStateOf(false) }
    if (showSounds) {
        SoundPickerDialog(
            title = "Timer sound",
            current = settings.timers.soundUri,
            onPick = { v -> onChange { it.copy(timers = it.timers.copy(soundUri = v)) } },
            onDismiss = { showSounds = false },
        )
    }

    SettingsPage("Timer settings", onDismiss) {
        SettingsLabel("When time is up")
        SettingsGroup {
            row { s ->
                SettingsRow(
                    s, Icons.Rounded.MusicNote, "Timer sound",
                    soundName(context, settings.timers.soundUri),
                    accentSubtitle = true,
                    onClick = { showSounds = true },
                )
            }
            row { s ->
                SettingsSwitch(
                    s, Icons.Rounded.Vibration, "Timer vibrate",
                    checked = settings.timers.vibrate,
                    onChange = { v -> onChange { it.copy(timers = it.timers.copy(vibrate = v)) } },
                )
            }
        }
    }
}

/**
 * The palette picker: a swatch per scheme, showing the three accents it actually produces.
 *
 * A single dot would be a lie here: the Expressive variant's whole point is that secondary and
 * tertiary rotate away from the seed, so two palettes with similar primaries can be completely
 * different to use. The swatch is a three-stop gradient of primary, tertiary and secondary for
 * that reason.
 */
@Composable
private fun PaletteRow(
    dynamic: Boolean,
    selected: Palette,
    onDynamic: () -> Unit,
    onSelect: (Palette) -> Unit,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current
    val wallpaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        null
    }

    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wallpaper != null) {
            item(key = "wallpaper") {
                Swatch("Wallpaper", wallpaper, dynamic, onDynamic)
            }
        }
        items(Palette.entries, key = { it.name }) { palette ->
            Swatch(
                name = palette.displayName,
                scheme = if (dark) palette.dark else palette.light,
                selected = !dynamic && palette == selected,
                onClick = { onSelect(palette) },
            )
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to scheme.primary,
                        0.5f to scheme.tertiary,
                        1f to scheme.secondary,
                    )
                )
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = CircleShape,
                )
                .clickable(role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** 0 is never, 1 is singular. Both of these read wrong the moment they are not special-cased. */
private fun minutesLabel(minutes: Int): String = when (minutes) {
    0 -> "Never"
    1 -> "1 minute"
    else -> "$minutes minutes"
}
