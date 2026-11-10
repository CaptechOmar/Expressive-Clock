package app.materialclock.ui.sheets

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One row in the picker. [value] uses the same encoding the store does: null default, [SILENT], or a URI. */
private data class SoundRow(val title: String, val value: String?)

/**
 * The in-app "Alarm sound" screen, laid out like Google Clock's: a "Your sounds" section with an
 * "Add new" button for files from storage, then the "Device sounds" list with Silent and Default on
 * top. Tapping a row selects it and plays a preview; nothing leaves the app.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundPickerDialog(
    title: String,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    val custom = remember { mutableStateListOf<SoundRow>().apply { addAll(loadCustom(context)) } }
    val device = remember { loadDeviceSounds(context) }

    var preview by remember { mutableStateOf<Ringtone?>(null) }
    fun play(value: String?) {
        runCatching { preview?.stop() }
        preview = null
        if (value == SILENT) return
        val uri = value?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        preview = runCatching { RingtoneManager.getRingtone(context, uri)?.also { it.play() } }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { runCatching { preview?.stop() } } }

    val addNew = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val s = uri.toString()
            if (custom.none { it.value == s }) {
                custom.add(SoundRow(displayName(context, uri), s))
                saveCustom(context, custom.mapNotNull { it.value })
            }
            selected = s
            play(s)
        }
    }

    fun finish() {
        onPick(selected)
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::finish,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::finish) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Header("Your sounds") }
                    items(custom) { row ->
                        SoundItem(row.title, selected == row.value) { selected = row.value; play(row.value) }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { addNew.launch(arrayOf("audio/*")) },
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Add new")
                        }
                    }
                    item { Header("Device sounds") }
                    items(device) { row ->
                        SoundItem(row.title, selected == row.value) { selected = row.value; play(row.value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SoundItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(56.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        } else {
            Box(Modifier.size(24.dp))
        }
    }
}

private fun loadDeviceSounds(context: Context): List<SoundRow> {
    val rows = mutableListOf(
        SoundRow("Silent", SILENT),
        SoundRow(
            "Default (" + (runCatching {
                RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    ?.getTitle(context)
            }.getOrNull() ?: "alarm") + ")",
            null,
        ),
    )
    runCatching {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        manager.cursor.use { c ->
            while (c.moveToNext()) {
                rows += SoundRow(
                    c.getString(RingtoneManager.TITLE_COLUMN_INDEX),
                    manager.getRingtoneUri(c.position).toString(),
                )
            }
        }
    }
    return rows
}

private const val PREFS = "custom_sounds"

private fun loadCustom(context: Context): List<SoundRow> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("uris", emptySet())
        .orEmpty().sorted().map { SoundRow(displayName(context, Uri.parse(it)), it) }

private fun saveCustom(context: Context, uris: List<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("uris", uris.toSet()).apply()
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "Custom sound"

/** Human name for a stored sound value; shared by the alarm sheet and the timer settings. */
fun soundName(context: Context, value: String?): String = when (value) {
    null -> "Default sound"
    SILENT -> "Silent"
    else -> runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: displayName(context, Uri.parse(value))
}
