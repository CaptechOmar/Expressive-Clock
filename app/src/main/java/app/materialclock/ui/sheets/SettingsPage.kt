package app.materialclock.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The settings look shared by every settings page: a full-screen page with a back arrow and a
 * large title, then sections of rounded cards. Cards in a section are separated by a hairline gap,
 * the first and last get large outer corners and the ones between get small ones.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SheetTitle(title)
            content()
        }
    }
}

@Composable
fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable (Shape) -> Unit>()

    /** One card. The shape depends on where it ends up in the group, so it is handed in. */
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

@Composable
fun SettingsGroup(content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEachIndexed { i, row ->
            val big = 26.dp
            val small = 5.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) big else small,
                topEnd = if (i == 0) big else small,
                bottomStart = if (i == rows.lastIndex) big else small,
                bottomEnd = if (i == rows.lastIndex) big else small,
            )
            row(shape)
        }
    }
}

@Composable
fun SettingsRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accentSubtitle: Boolean = false,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .let { if (onClick != null) it.clickable(role = role, onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accentSubtitle) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}

@Composable
fun SettingsSwitch(
    shape: Shape,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(
        shape, icon, title, subtitle,
        role = Role.Switch,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        },
    )
}

/** Shows the current value in the accent colour and opens a radio dialog to change it. */
@Composable
fun <T> SettingsChoice(
    shape: Shape,
    icon: ImageVector,
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(shape, icon, title, label(value), accentSubtitle = true, onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            // Not the platform default width, which is nearly edge to edge on a phone.
            modifier = Modifier.fillMaxWidth(0.8f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            title = { Text(title) },
            text = {
                Column(
                    Modifier.selectableGroup().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    options.forEachIndexed { i, option ->
                        val picked = option == value
                        val big = 20.dp
                        val small = 5.dp
                        val shape = RoundedCornerShape(
                            topStart = if (i == 0) big else small,
                            topEnd = if (i == 0) big else small,
                            bottomStart = if (i == options.lastIndex) big else small,
                            bottomEnd = if (i == options.lastIndex) big else small,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (picked) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                                .selectable(
                                    selected = picked,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(option)
                                        open = false
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = picked,
                                onClick = null,
                                colors = if (picked) {
                                    androidx.compose.material3.RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                } else {
                                    androidx.compose.material3.RadioButtonDefaults.colors()
                                },
                            )
                            Text(
                                label(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (picked) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}
