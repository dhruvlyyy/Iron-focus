package com.example.ironfocus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ironfocus.domain.TimeFormatter
import com.example.ironfocus.system.PermissionsHelper
import com.example.ironfocus.system.PermissionsStatus
import com.example.ironfocus.ui.IronFocusUiState
import com.example.ironfocus.ui.SetupTab
import com.example.ironfocus.ui.components.DurationPickerSection

@Composable
fun DashboardScreen(
    uiState: IronFocusUiState,
    onNavigateTab: (SetupTab) -> Unit,
    onDurationChange: (days: Int, hours: Int, minutes: Int) -> Unit,
    onConfirmEngageLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            IronFocusHeader(permissions = uiState.permissions)
        }

        item {
            PermissionsChecklistCard(
                permissions = uiState.permissions,
                onOpenAccessibility = { PermissionsHelper.openAccessibilitySettings(context) },
                onOpenDeviceAdmin = { PermissionsHelper.openDeviceAdminSettings(context) },
                onOpenBattery = { PermissionsHelper.openIgnoreBatteryOptimizations(context) },
                onOpenOverlay = { PermissionsHelper.openOverlayPermissionSettings(context) }
            )
        }

        item {
            BlocklistQuickSummaryRow(
                blockedAppsCount = uiState.lockState.blockedPackages.size,
                blockedSitesCount = uiState.lockState.blockedDomains.size,
                onAppsClick = { onNavigateTab(SetupTab.APPS) },
                onWebsitesClick = { onNavigateTab(SetupTab.WEBSITES) }
            )
        }

        item {
            SchedulePreviewCard(
                enabledCount = uiState.enabledSchedulesCount,
                nextScheduleLabel = uiState.nextScheduledLock?.let {
                    "${it.scheduleName} • ${it.formattedTrigger}"
                },
                onClick = { onNavigateTab(SetupTab.SCHEDULES) }
            )
        }

        item {
            DurationSetupCard(
                days = uiState.selectedDays,
                hours = uiState.selectedHours,
                minutes = uiState.selectedMinutes,
                durationMillis = uiState.selectedDurationMillis,
                onDurationChange = onDurationChange
            )
        }

        item {
            EngageStrictLockFooter(
                uiState = uiState,
                onRequestEngage = { showConfirmDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showConfirmDialog) {
        StrictLockConfirmationDialog(
            durationMillis = uiState.selectedDurationMillis,
            blockedAppsCount = uiState.lockState.blockedPackages.size,
            blockedSitesCount = uiState.lockState.blockedDomains.size,
            onDismiss = { showConfirmDialog = false },
            onConfirm = {
                showConfirmDialog = false
                onConfirmEngageLock()
            }
        )
    }
}

@Composable
private fun IronFocusHeader(permissions: PermissionsStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ironfocus_mark_1795142341604),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.tagline_strict),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val ready = permissions.canStartStrictLock
        val badgeColor = if (ready) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
        Surface(
            color = badgeColor.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
        ) {
            Text(
                text = if (ready) "ARMED ${permissions.enabledCount}/4" else "SETUP ${permissions.enabledCount}/4",
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PermissionsChecklistCard(
    permissions: PermissionsStatus,
    onOpenAccessibility: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_checklist_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionChecklistRow(
                icon = Icons.Default.AccessibilityNew,
                title = stringResource(R.string.perm_accessibility_title),
                description = stringResource(R.string.perm_accessibility_desc),
                isEnabled = permissions.accessibilityEnabled,
                isRequired = true,
                onActionClick = onOpenAccessibility,
                testTag = "perm_accessibility_button"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PermissionChecklistRow(
                icon = Icons.Default.AdminPanelSettings,
                title = stringResource(R.string.perm_device_admin_title),
                description = stringResource(R.string.perm_device_admin_desc),
                isEnabled = permissions.deviceAdminEnabled,
                isRequired = true,
                onActionClick = onOpenDeviceAdmin,
                testTag = "perm_device_admin_button"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PermissionChecklistRow(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                isEnabled = permissions.batteryOptimizationIgnored,
                isRequired = false,
                onActionClick = onOpenBattery,
                testTag = "perm_battery_button"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PermissionChecklistRow(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                isEnabled = permissions.overlayPermissionGranted,
                isRequired = false,
                onActionClick = onOpenOverlay,
                testTag = "perm_overlay_button"
            )
        }
    }
}

@Composable
private fun PermissionChecklistRow(
    icon: ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    isRequired: Boolean,
    onActionClick: () -> Unit,
    testTag: String
) {
    val statusColor = when {
        isEnabled -> MaterialTheme.colorScheme.tertiary
        isRequired -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.padding(9.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isEnabled) {
                        stringResource(R.string.status_enabled)
                    } else {
                        stringResource(R.string.status_missing)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isRequired) {
                        stringResource(R.string.perm_badge_required)
                    } else {
                        stringResource(R.string.perm_badge_recommended)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onActionClick,
            modifier = Modifier
                .height(48.dp)
                .testTag(testTag),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                1.dp,
                if (isEnabled) MaterialTheme.colorScheme.outline else statusColor.copy(alpha = 0.7f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else statusColor
            )
        ) {
            Text(
                text = if (isEnabled) {
                    stringResource(R.string.action_configure)
                } else {
                    stringResource(R.string.action_enable)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun BlocklistQuickSummaryRow(
    blockedAppsCount: Int,
    blockedSitesCount: Int,
    onAppsClick: () -> Unit,
    onWebsitesClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryTargetTile(
            icon = Icons.Default.Apps,
            count = blockedAppsCount,
            label = "Blocked Apps",
            onClick = onAppsClick,
            testTag = "summary_apps_tile",
            modifier = Modifier.weight(1f)
        )
        SummaryTargetTile(
            icon = Icons.Default.Language,
            count = blockedSitesCount,
            label = "Blocked Sites",
            onClick = onWebsitesClick,
            testTag = "summary_websites_tile",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryTargetTile(
    icon: ImageVector,
    count: Int,
    label: String,
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Manage $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SchedulePreviewCard(
    enabledCount: Int,
    nextScheduleLabel: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("dashboard_schedules_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (enabledCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EventRepeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (enabledCount > 0) {
                        "$enabledCount Weekly Schedule${if (enabledCount == 1) "" else "s"} Armed"
                    } else {
                        "Weekly Auto-Schedules"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = nextScheduleLabel ?: "Tap to automate recurring focus windows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Schedules",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DurationSetupCard(
    days: Int,
    hours: Int,
    minutes: Int,
    durationMillis: Long,
    onDurationChange: (days: Int, hours: Int, minutes: Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.duration_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = TimeFormatter.formatDurationHuman(durationMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.duration_minimum_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DurationPickerSection(
                days = days,
                hours = hours,
                minutes = minutes,
                onDurationChange = onDurationChange
            )
        }
    }
}

@Composable
private fun EngageStrictLockFooter(
    uiState: IronFocusUiState,
    onRequestEngage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!uiState.permissions.canStartStrictLock) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.lock_prereq_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (!uiState.hasAnyTargets) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.lock_empty_targets_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Button(
            onClick = onRequestEngage,
            enabled = uiState.canEngageLock,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("engage_strict_lock_button"),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${stringResource(R.string.btn_engage_lock)} • ${TimeFormatter.formatDurationHuman(uiState.selectedDurationMillis)}",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StrictLockConfirmationDialog(
    durationMillis: Long,
    blockedAppsCount: Int,
    blockedSitesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.confirm_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.confirm_dialog_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Duration: ${TimeFormatter.formatDurationHuman(durationMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$blockedAppsCount apps • $blockedSitesCount websites locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("confirm_lock_now_button")
            ) {
                Text(text = stringResource(R.string.confirm_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_lock_cancel_button")
            ) {
                Text(
                    text = stringResource(R.string.confirm_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
