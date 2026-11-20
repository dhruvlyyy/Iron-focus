package com.example.ironfocus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ironfocus.domain.FocusSchedule
import com.example.ironfocus.domain.ScheduleTimeCalculator
import com.example.ironfocus.domain.TimeFormatter
import com.example.ironfocus.system.PermissionsHelper
import com.example.ironfocus.ui.NextScheduledLockInfo
import com.example.ironfocus.ui.components.DayTimelineBar
import java.util.UUID

private data class ScheduleTemplate(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val days: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

private val QUICK_TEMPLATES = listOf(
    ScheduleTemplate(
        title = "Deep Work",
        subtitle = "Weekdays • 9:00 AM – 5:00 PM",
        icon = Icons.Default.WorkOutline,
        days = ScheduleTimeCalculator.WEEKDAYS_ISO,
        startHour = 9,
        startMinute = 0,
        endHour = 17,
        endMinute = 0
    ),
    ScheduleTemplate(
        title = "Sleep Guard",
        subtitle = "Every day • 10:30 PM – 6:30 AM",
        icon = Icons.Default.Bedtime,
        days = ScheduleTimeCalculator.ALL_DAYS_ISO.toSet(),
        startHour = 22,
        startMinute = 30,
        endHour = 6,
        endMinute = 30
    ),
    ScheduleTemplate(
        title = "Morning Focus",
        subtitle = "Every day • 6:00 AM – 9:00 AM",
        icon = Icons.Default.WbSunny,
        days = ScheduleTimeCalculator.ALL_DAYS_ISO.toSet(),
        startHour = 6,
        startMinute = 0,
        endHour = 9,
        endMinute = 0
    )
)

@Composable
fun SchedulesScreen(
    schedules: List<FocusSchedule>,
    nextScheduledLock: NextScheduledLockInfo?,
    exactAlarmsAllowed: Boolean,
    onSaveSchedule: (FocusSchedule) -> Unit,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editorDraft by remember { mutableStateOf<FocusSchedule?>(null) }
    var isEditingExisting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedules_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.schedules_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        isEditingExisting = false
                        editorDraft = FocusSchedule(
                            id = UUID.randomUUID().toString(),
                            name = "Deep Work",
                            daysOfWeek = ScheduleTimeCalculator.WEEKDAYS_ISO,
                            startHour = 9,
                            startMinute = 0,
                            endHour = 17,
                            endMinute = 0,
                            repeatWeekly = true,
                            isEnabled = true
                        )
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_schedule_button"),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (!exactAlarmsAllowed) {
            item {
                ExactAlarmPermissionBanner(
                    onGrantClick = { PermissionsHelper.openExactAlarmSettings(context) }
                )
            }
        }

        item {
            NextAutoLockHeroCard(
                nextScheduledLock = nextScheduledLock,
                enabledCount = schedules.count { it.isEnabled }
            )
        }

        if (schedules.isEmpty()) {
            item {
                EmptySchedulesCard()
            }
        } else {
            items(
                items = schedules,
                key = { it.id }
            ) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onClickEdit = {
                        isEditingExisting = true
                        editorDraft = schedule
                    },
                    onToggleEnabled = { enabled -> onToggleSchedule(schedule.id, enabled) },
                    onDelete = { onDeleteSchedule(schedule.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.schedules_templates_header),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_TEMPLATES.forEach { template ->
                    QuickTemplateRow(
                        template = template,
                        onSelect = {
                            isEditingExisting = false
                            editorDraft = FocusSchedule(
                                id = UUID.randomUUID().toString(),
                                name = template.title,
                                daysOfWeek = template.days,
                                startHour = template.startHour,
                                startMinute = template.startMinute,
                                endHour = template.endHour,
                                endMinute = template.endMinute,
                                repeatWeekly = true,
                                isEnabled = true
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    editorDraft?.let { draft ->
        ScheduleEditorDialog(
            initialSchedule = draft,
            isEditing = isEditingExisting,
            onDismiss = { editorDraft = null },
            onSave = { saved ->
                onSaveSchedule(saved)
                editorDraft = null
            }
        )
    }
}

@Composable
private fun ExactAlarmPermissionBanner(onGrantClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.schedules_exact_alarm_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onGrantClick,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Allow",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NextAutoLockHeroCard(
    nextScheduledLock: NextScheduledLockInfo?,
    enabledCount: Int
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_schedule_hero_card"),
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        border = BorderStroke(
            1.dp,
            if (nextScheduledLock != null) primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            primary.copy(alpha = if (nextScheduledLock != null) 0.14f else 0.04f),
                            surfaceColor
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.4f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventRepeat,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.padding(11.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (nextScheduledLock != null) "NEXT AUTOMATIC LOCK" else "AUTOMATION STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (nextScheduledLock != null) primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nextScheduledLock?.formattedTrigger ?: "No upcoming schedule",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (nextScheduledLock != null) {
                            "${nextScheduledLock.scheduleName} • $enabledCount active schedule${if (enabledCount == 1) "" else "s"}"
                        } else {
                            "Create a weekly schedule to lock distractions automatically"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySchedulesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = stringResource(R.string.schedules_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.schedules_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: FocusSchedule,
    onClickEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (schedule.isEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClickEdit)
            .testTag("schedule_card_${schedule.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Name, Weekly Badge, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = schedule.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (schedule.isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = if (schedule.repeatWeekly) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (schedule.repeatWeekly) {
                                    stringResource(R.string.schedules_badge_weekly)
                                } else {
                                    stringResource(R.string.schedules_badge_once)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (schedule.repeatWeekly) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val durationText = TimeFormatter.formatDurationHuman(schedule.durationMillis)
                    val overnightSuffix = if (schedule.isOvernight) " • Overnight" else ""
                    Text(
                        text = "${ScheduleTimeCalculator.formatDaysSummary(schedule.daysOfWeek)} • $durationText$overnightSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.testTag("schedule_toggle_${schedule.id}")
                )
            }

            // Tabular Start -> End Time Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = ScheduleTimeCalculator.formatTime12h(schedule.startHour, schedule.startMinute),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                    ),
                    color = if (schedule.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = ScheduleTimeCalculator.formatTime12h(schedule.endHour, schedule.endMinute),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                    ),
                    color = if (schedule.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 24-Hour Visual Window Bar
            DayTimelineBar(
                startTotalMinutes = schedule.startTotalMinutes,
                endTotalMinutes = schedule.endTotalMinutes,
                isEnabled = schedule.isEnabled
            )

            // 7-Day Pill Matrix + Delete action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScheduleTimeCalculator.ALL_DAYS_ISO.forEach { day ->
                        val active = day in schedule.daysOfWeek
                        Surface(
                            shape = CircleShape,
                            color = when {
                                active && schedule.isEnabled -> MaterialTheme.colorScheme.primaryContainer
                                active -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    active && schedule.isEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ScheduleTimeCalculator.dayShortLetter(day),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        active && schedule.isEnabled -> MaterialTheme.colorScheme.primary
                                        active -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_schedule_${schedule.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete schedule ${schedule.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTemplateRow(
    template: ScheduleTemplate,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .testTag("schedule_template_${template.title}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = template.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = template.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add ${template.title}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorDialog(
    initialSchedule: FocusSchedule,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (FocusSchedule) -> Unit
) {
    var name by remember(initialSchedule) { mutableStateOf(initialSchedule.name) }
    var selectedDays by remember(initialSchedule) { mutableStateOf(initialSchedule.daysOfWeek) }
    var startHour by remember(initialSchedule) { mutableIntStateOf(initialSchedule.startHour) }
    var startMinute by remember(initialSchedule) { mutableIntStateOf(initialSchedule.startMinute) }
    var endHour by remember(initialSchedule) { mutableIntStateOf(initialSchedule.endHour) }
    var endMinute by remember(initialSchedule) { mutableIntStateOf(initialSchedule.endMinute) }
    var repeatWeekly by remember(initialSchedule) { mutableStateOf(initialSchedule.repeatWeekly) }

    var pickingStartTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }

    val durationMinutes = ScheduleTimeCalculator.computeDurationMinutes(
        startHour, startMinute, endHour, endMinute
    )
    val isOvernight = (endHour * 60 + endMinute) <= (startHour * 60 + startMinute)
    val canSave = selectedDays.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.schedules_edit_title)
                } else {
                    stringResource(R.string.schedules_create_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.schedules_name_label)) },
                    placeholder = { Text(stringResource(R.string.schedules_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_name_input"),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Days of Week Selector
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.schedules_days_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ScheduleTimeCalculator.ALL_DAYS_ISO.forEach { day ->
                            val isSelected = day in selectedDays
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        selectedDays = if (isSelected) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    }
                                    .testTag("schedule_day_toggle_$day")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = ScheduleTimeCalculator.dayShortLetter(day),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DayPresetChip(
                            label = "Weekdays",
                            selected = selectedDays == ScheduleTimeCalculator.WEEKDAYS_ISO,
                            onClick = { selectedDays = ScheduleTimeCalculator.WEEKDAYS_ISO }
                        )
                        DayPresetChip(
                            label = "Weekends",
                            selected = selectedDays == ScheduleTimeCalculator.WEEKENDS_ISO,
                            onClick = { selectedDays = ScheduleTimeCalculator.WEEKENDS_ISO }
                        )
                        DayPresetChip(
                            label = "Every Day",
                            selected = selectedDays.size == 7,
                            onClick = { selectedDays = ScheduleTimeCalculator.ALL_DAYS_ISO.toSet() }
                        )
                    }
                }

                // Start & End Time Window Selector
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.schedules_time_window_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val durLabel = TimeFormatter.formatDurationHuman(durationMinutes * 60_000L)
                        Text(
                            text = if (isOvernight) "$durLabel • Overnight" else durLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeSelectorTile(
                            label = stringResource(R.string.schedules_start_time),
                            timeText = ScheduleTimeCalculator.formatTime12h(startHour, startMinute),
                            onClick = { pickingStartTime = true },
                            testTag = "schedule_start_time_button",
                            modifier = Modifier.weight(1f)
                        )
                        TimeSelectorTile(
                            label = stringResource(R.string.schedules_end_time),
                            timeText = ScheduleTimeCalculator.formatTime12h(endHour, endMinute),
                            onClick = { pickingEndTime = true },
                            testTag = "schedule_end_time_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Repeat Every Week Toggle Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (repeatWeekly) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repeatWeekly = !repeatWeekly }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.schedules_repeat_weekly_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.schedules_repeat_weekly_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = repeatWeekly,
                            onCheckedChange = { repeatWeekly = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("schedule_repeat_weekly_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialSchedule.copy(
                            name = name.trim().ifEmpty { "Focus Block" },
                            daysOfWeek = selectedDays,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            repeatWeekly = repeatWeekly
                        )
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("schedule_save_button")
            ) {
                Text(text = stringResource(R.string.schedules_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.confirm_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (pickingStartTime) {
        IronFocusTimePickerDialog(
            title = "Start Time",
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { pickingStartTime = false },
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                pickingStartTime = false
            }
        )
    }

    if (pickingEndTime) {
        IronFocusTimePickerDialog(
            title = "End Time",
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { pickingEndTime = false },
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                pickingEndTime = false
            }
        )
    }
}

@Composable
private fun DayPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@Composable
private fun TimeSelectorTile(
    label: String,
    timeText: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IronFocusTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(state.hour, state.minute) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.confirm_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
