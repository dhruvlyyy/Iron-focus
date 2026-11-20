package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ironfocus.ui.IronFocusUiState
import com.example.ironfocus.ui.MainViewModel
import com.example.ironfocus.ui.SetupTab
import com.example.ironfocus.ui.components.obsidianAtmosphere
import com.example.ironfocus.ui.screens.ActiveLockScreen
import com.example.ironfocus.ui.screens.AppsPickerScreen
import com.example.ironfocus.ui.screens.DashboardScreen
import com.example.ironfocus.ui.screens.SchedulesScreen
import com.example.ironfocus.ui.screens.WebsitesManagerScreen
import com.example.ui.theme.IronFocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            IronFocusTheme {
                IronFocusApp()
            }
        }
    }
}

@Composable
private fun IronFocusApp(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.engageStrictLock()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onConfirmEngageLock: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.engageStrictLock()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.engageStrictLock()
        }
    }

    val atmosphereAccent = if (uiState.lockState.isLockActive) {
        MaterialTheme.colorScheme.primary
    } else if (uiState.permissions.canStartStrictLock) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    if (uiState.lockState.isLockActive) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .obsidianAtmosphere(accentColor = atmosphereAccent, intensity = 0.20f),
            containerColor = ComposeColor.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                ActiveLockScreen(
                    lockState = uiState.lockState,
                    installedApps = uiState.installedApps,
                    onExtendTimer = { d, h, m -> viewModel.extendActiveLock(d, h, m) },
                    onAddApps = { packages -> viewModel.addAppsDuringLock(packages) },
                    onAddWebsite = { raw -> viewModel.addWebsiteDuringLock(raw) }
                )
            }
        }
    } else {
        if (uiState.activeTab != SetupTab.DASHBOARD) {
            BackHandler {
                viewModel.selectTab(SetupTab.DASHBOARD)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .obsidianAtmosphere(accentColor = atmosphereAccent, intensity = 0.14f)
        ) {
            val useNavigationRail = maxWidth >= 600.dp

            if (useNavigationRail) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ComposeColor.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        IronFocusNavigationRail(
                            activeTab = uiState.activeTab,
                            onSelectTab = viewModel::selectTab
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            SetupTabContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                onConfirmEngageLock = onConfirmEngageLock
                            )
                        }
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ComposeColor.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        IronFocusBottomBar(
                            activeTab = uiState.activeTab,
                            onSelectTab = viewModel::selectTab
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SetupTabContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onConfirmEngageLock = onConfirmEngageLock
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupTabContent(
    uiState: IronFocusUiState,
    viewModel: MainViewModel,
    onConfirmEngageLock: () -> Unit
) {
    Crossfade(
        targetState = uiState.activeTab,
        animationSpec = tween(durationMillis = 200),
        label = "setup_tab_transition"
    ) { tab ->
        when (tab) {
            SetupTab.DASHBOARD -> {
                DashboardScreen(
                    uiState = uiState,
                    onNavigateTab = viewModel::selectTab,
                    onDurationChange = viewModel::updateDuration,
                    onConfirmEngageLock = onConfirmEngageLock
                )
            }
            SetupTab.SCHEDULES -> {
                SchedulesScreen(
                    schedules = uiState.schedules,
                    nextScheduledLock = uiState.nextScheduledLock,
                    exactAlarmsAllowed = uiState.permissions.exactAlarmsAllowed,
                    onSaveSchedule = viewModel::saveSchedule,
                    onToggleSchedule = viewModel::toggleScheduleEnabled,
                    onDeleteSchedule = viewModel::deleteSchedule
                )
            }
            SetupTab.APPS -> {
                AppsPickerScreen(
                    installedApps = uiState.installedApps,
                    isLoading = uiState.isLoadingApps,
                    blockedPackages = uiState.lockState.blockedPackages,
                    searchQuery = uiState.appSearchQuery,
                    showSelectedOnly = uiState.showSelectedAppsOnly,
                    onSearchQueryChange = viewModel::updateAppSearchQuery,
                    onShowSelectedOnlyChange = viewModel::setShowSelectedAppsOnly,
                    onToggleApp = viewModel::toggleAppBlocked
                )
            }
            SetupTab.WEBSITES -> {
                WebsitesManagerScreen(
                    blockedDomains = uiState.lockState.blockedDomains,
                    customDomains = uiState.lockState.customDomains,
                    websiteInput = uiState.websiteInput,
                    websiteInputError = uiState.websiteInputError,
                    normalizedPreview = viewModel.normalizePreview(uiState.websiteInput),
                    onWebsiteInputChange = viewModel::updateWebsiteInput,
                    onAddWebsite = { viewModel.submitWebsiteInput() },
                    onToggleDomain = viewModel::toggleDomainBlocked,
                    onRemoveCustomDomain = viewModel::removeCustomDomain
                )
            }
        }
    }
}

@Composable
private fun IronFocusBottomBar(
    activeTab: SetupTab,
    onSelectTab: (SetupTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NavigationBarItem(
            selected = activeTab == SetupTab.DASHBOARD,
            onClick = { onSelectTab(SetupTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.DASHBOARD) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = stringResource(R.string.nav_dashboard)
                )
            },
            label = { Text(stringResource(R.string.nav_dashboard)) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_dashboard")
        )
        NavigationBarItem(
            selected = activeTab == SetupTab.SCHEDULES,
            onClick = { onSelectTab(SetupTab.SCHEDULES) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.SCHEDULES) Icons.Filled.EventRepeat else Icons.Outlined.EventRepeat,
                    contentDescription = stringResource(R.string.nav_schedules)
                )
            },
            label = { Text(stringResource(R.string.nav_schedules)) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_schedules")
        )
        NavigationBarItem(
            selected = activeTab == SetupTab.APPS,
            onClick = { onSelectTab(SetupTab.APPS) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.APPS) Icons.Filled.Apps else Icons.Outlined.Apps,
                    contentDescription = stringResource(R.string.nav_apps)
                )
            },
            label = { Text(stringResource(R.string.nav_apps)) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_apps")
        )
        NavigationBarItem(
            selected = activeTab == SetupTab.WEBSITES,
            onClick = { onSelectTab(SetupTab.WEBSITES) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.WEBSITES) Icons.Filled.Language else Icons.Outlined.Language,
                    contentDescription = stringResource(R.string.nav_websites)
                )
            },
            label = { Text(stringResource(R.string.nav_websites)) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_websites")
        )
    }
}

@Composable
private fun IronFocusNavigationRail(
    activeTab: SetupTab,
    onSelectTab: (SetupTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val itemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NavigationRailItem(
            selected = activeTab == SetupTab.DASHBOARD,
            onClick = { onSelectTab(SetupTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.DASHBOARD) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = stringResource(R.string.nav_dashboard)
                )
            },
            label = { Text(stringResource(R.string.nav_dashboard)) },
            colors = itemColors
        )
        NavigationRailItem(
            selected = activeTab == SetupTab.SCHEDULES,
            onClick = { onSelectTab(SetupTab.SCHEDULES) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.SCHEDULES) Icons.Filled.EventRepeat else Icons.Outlined.EventRepeat,
                    contentDescription = stringResource(R.string.nav_schedules)
                )
            },
            label = { Text(stringResource(R.string.nav_schedules)) },
            colors = itemColors
        )
        NavigationRailItem(
            selected = activeTab == SetupTab.APPS,
            onClick = { onSelectTab(SetupTab.APPS) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.APPS) Icons.Filled.Apps else Icons.Outlined.Apps,
                    contentDescription = stringResource(R.string.nav_apps)
                )
            },
            label = { Text(stringResource(R.string.nav_apps)) },
            colors = itemColors
        )
        NavigationRailItem(
            selected = activeTab == SetupTab.WEBSITES,
            onClick = { onSelectTab(SetupTab.WEBSITES) },
            icon = {
                Icon(
                    imageVector = if (activeTab == SetupTab.WEBSITES) Icons.Filled.Language else Icons.Outlined.Language,
                    contentDescription = stringResource(R.string.nav_websites)
                )
            },
            label = { Text(stringResource(R.string.nav_websites)) },
            colors = itemColors
        )
    }
}
