package app.materialclock.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.materialclock.core.TimerPreset
import java.time.Duration

/**
 * Add and edit a saved timer, in one sheet, the same way [AlarmEditSheet] does it: a preset
 * arrives with `id == 0` when it is new, and Delete is offered only when it is not.
 *
 * ## Saving
 *
 * Unlike the alarm sheet, only **Save** commits. Dragging the sheet away or tapping the scrim
 * discards. An alarm sheet can afford to commit on every exit because it opens on a sensible time;
 * this one opens from a plus button, and committing on a stray scrim tap would leave a nameless
 * preset in the list that you never meant to make.
 *
 * ## Duration
 *
 * Three plain number fields rather than a picker. A preset is an exact length ("1 min 30 s" for
 * an egg), and the fields start filled with whatever was already typed on the timer's keypad, so
 * the usual path is: key in 25:00, tap +, name it, Save.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PresetSheet(
    initial: TimerPreset,
    onDismiss: () -> Unit,
    onSave: (TimerPreset) -> Unit,
    onDelete: ((Long) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val secs = initial.duration.seconds
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var hours by rememberSaveable(initial.id) { mutableStateOf((secs / 3600).toString()) }
    var minutes by rememberSaveable(initial.id) { mutableStateOf(((secs % 3600) / 60).toString()) }
    var seconds by rememberSaveable(initial.id) { mutableStateOf((secs % 60).toString()) }

    // An empty or half-typed field counts as zero, which keeps Save honest without an error state.
    val total = Duration.ofSeconds(
        (
            (hours.toLongOrNull() ?: 0L) * 3600 +
                (minutes.toLongOrNull() ?: 0L) * 60 +
                (seconds.toLongOrNull() ?: 0L)
            ).coerceAtMost(MAX_PRESET_SECONDS)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 20.dp)) {
            SheetTitle(if (onDelete == null) "New preset" else "Edit preset")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE),
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EDGE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DurationField("Hours", hours, { hours = it }, Modifier.weight(1f))
                DurationField("Min", minutes, { minutes = it }, Modifier.weight(1f))
                DurationField("Sec", seconds, { seconds = it }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EDGE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    FilledTonalButton(
                        onClick = { onDelete(initial.id); onDismiss() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).height(ROW_H),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete preset", Modifier.size(20.dp))
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1.2f).height(ROW_H),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLargeEmphasized)
                }
                Button(
                    enabled = !total.isZero,
                    onClick = {
                        val saved = initial.copy(name = name.trim(), duration = total)
                        // A preset with no name is named after its length, so the pill is never blank.
                        onSave(saved.copy(name = saved.name.ifEmpty { saved.durationLabel }))
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(if (onDelete != null) 1.8f else 2.4f).height(ROW_H),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

@Composable
private fun DurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        modifier = modifier,
    )
}

private val EDGE = 24.dp
private val ROW_H = 56.dp
private const val MAX_NAME = 24

/** 99:59:59, the most the keypad itself can express. */
private const val MAX_PRESET_SECONDS = 99L * 3600 + 59 * 60 + 59
