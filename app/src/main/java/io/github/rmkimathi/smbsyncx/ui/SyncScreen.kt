package io.github.rmkimathi.smbsyncx.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import io.github.rmkimathi.smbsyncx.R
import io.github.rmkimathi.smbsyncx.model.ComparisonMode
import io.github.rmkimathi.smbsyncx.model.SmbFile
import io.github.rmkimathi.smbsyncx.model.SyncProfile
import io.github.rmkimathi.smbsyncx.model.SyncState
import io.github.rmkimathi.smbsyncx.ui.theme.SMBSyncXTheme
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey

@Serializable
object SyncListRoute : NavKey

@Serializable
data class SyncExecutionRoute(val name: String) : NavKey

@Serializable
data class DesignerRoute(val name: String? = null) : NavKey

@Serializable
object AboutRoute : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backStack = remember { mutableStateListOf<NavKey>(SyncListRoute) }
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        viewModel.onPermissionResult(allGranted)
    }

    fun checkAndRequestPermissions(onPermissionsGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.onPermissionResult(true)
                onPermissionsGranted()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            viewModel.onPermissionResult(true)
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = listDetailStrategy,
        modifier = modifier,
        entryProvider = { key ->
            when (key) {
                is SyncListRoute -> NavEntry(
                    key = key,
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Select a profile or create a new one to view details",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "SMBSync supports adaptive screen sizes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    )
                ) {
                    val profiles by viewModel.profilesList.collectAsState()
                    SyncListPane(
                        profiles = profiles,
                        onCreateProfile = {
                            viewModel.loadProfileForEditing(null)
                            backStack.add(DesignerRoute(null))
                        },
                        onEditProfile = { profileName ->
                            viewModel.loadProfileForEditing(profileName)
                            backStack.add(DesignerRoute(profileName))
                        },
                        onStartSync = { profileName ->
                            checkAndRequestPermissions {
                                viewModel.startSync(profileName)
                                backStack.add(SyncExecutionRoute(profileName))
                            }
                        },
                        onDeleteProfile = viewModel::deleteProfile,
                        onAboutClick = { backStack.add(AboutRoute) }
                    )
                }

                is AboutRoute -> NavEntry(
                    key = key,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    AboutPane(onBack = { backStack.removeLastOrNull() })
                }

                is SyncExecutionRoute -> NavEntry(
                    key = key,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    val syncState by viewModel.syncState.collectAsState()
                    SyncExecutionPane(
                        profileName = key.name,
                        syncState = syncState,
                        onCancelSync = {
                            viewModel.cancelSync()
                            backStack.removeLastOrNull()
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is DesignerRoute -> NavEntry(
                    key = key,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    val host by viewModel.host.collectAsState()
                    val port by viewModel.port.collectAsState()
                    val domain by viewModel.domain.collectAsState()
                    val username by viewModel.username.collectAsState()
                    val password by viewModel.password.collectAsState()
                    val path by viewModel.path.collectAsState()
                    val profileName by viewModel.profileName.collectAsState()
                    val localFolder by viewModel.localFolder.collectAsState()
                    val comparisonMode by viewModel.comparisonMode.collectAsState()
                    val isValidating by viewModel.isValidating.collectAsState()
                    val validationResult by viewModel.validationResult.collectAsState()
                    val isListing by viewModel.isListing.collectAsState()
                    val remoteFiles by viewModel.remoteFiles.collectAsState()
                    val isLivePreview by viewModel.isLivePreview.collectAsState()

                    DesignerModePane(
                        isEditMode = key.name != null,
                        host = host,
                        port = port,
                        domain = domain,
                        username = username,
                        password = password,
                        path = path,
                        profileName = profileName,
                        localFolder = localFolder,
                        comparisonMode = comparisonMode,
                        isValidating = isValidating,
                        validationResult = validationResult,
                        isListing = isListing,
                        remoteFiles = remoteFiles,
                        isLivePreview = isLivePreview,
                        onHostChanged = viewModel::onHostChanged,
                        onPortChanged = viewModel::onPortChanged,
                        onDomainChanged = viewModel::onDomainChanged,
                        onUsernameChanged = viewModel::onUsernameChanged,
                        onPasswordChanged = viewModel::onPasswordChanged,
                        onPathChanged = viewModel::onPathChanged,
                        onProfileNameChanged = viewModel::onProfileNameChanged,
                        onLocalFolderChanged = viewModel::onLocalFolderChanged,
                        onComparisonModeChanged = viewModel::onComparisonModeChanged,
                        onToggleLivePreview = viewModel::setLivePreview,
                        onValidateConnection = viewModel::validateConnection,
                        onRefreshRemoteFiles = {
                            checkAndRequestPermissions {
                                viewModel.listRemoteFiles()
                            }
                        },
                        onSaveProfile = {
                            viewModel.saveProfile()
                            backStack.removeLastOrNull()
                        },
                        onCancel = { backStack.removeLastOrNull() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncListPane(
    profiles: List<SyncProfile>,
    onCreateProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    onStartSync: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMBSyncX", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_about)) },
                            onClick = {
                                showMenu = false
                                onAboutClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Info, contentDescription = null)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProfile,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Profile", modifier = Modifier.size(36.dp))
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No profiles found.\nTap + to create a new sync profile.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles) { profile ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditProfile(profile.name) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    IconButton(onClick = { onEditProfile(profile.name) }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteProfile(profile.name) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = "Local Root", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local: ${profile.sourceFolder}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudQueue, contentDescription = "Remote Target", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remote: ${profile.targetFolder}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onStartSync(profile.name) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Sync Now")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synchronize Now")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPane(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/about.html")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncExecutionPane(
    profileName: String,
    syncState: SyncState,
    onCancelSync: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Executing Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider()

                    when (syncState) {
                        is SyncState.Idle -> {
                            Icon(Icons.Rounded.HourglassEmpty, contentDescription = "Idle", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("Initializing synchronization...", style = MaterialTheme.typography.bodyLarge)
                        }
                        is SyncState.Progress -> {
                            val overallProgress = (syncState.processedFiles.toFloat() + syncState.progress) / syncState.totalFiles.toFloat()
                            
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { syncState.progress },
                                    modifier = Modifier.size(80.dp),
                                    strokeWidth = 6.dp
                                )
                                Text(
                                    text = "${(syncState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Syncing: ${syncState.currentFile}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            
                            LinearProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Text(
                                text = "Total Progress: ${syncState.processedFiles} / ${syncState.totalFiles} files (${(overallProgress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is SyncState.Completed -> {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Sync completed successfully!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        is SyncState.Error -> {
                            Icon(Icons.Rounded.Error, contentDescription = "Error", modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Sync failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(syncState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onCancelSync,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (syncState is SyncState.Completed || syncState is SyncState.Error) "Close" else "Cancel Sync")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignerModePane(
    isEditMode: Boolean,
    host: String,
    port: String,
    domain: String,
    username: String,
    password: String,
    path: String,
    profileName: String,
    localFolder: String,
    comparisonMode: ComparisonMode,
    isValidating: Boolean,
    validationResult: Boolean?,
    isListing: Boolean,
    remoteFiles: List<SmbFile>,
    isLivePreview: Boolean,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onDomainChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPathChanged: (String) -> Unit,
    onProfileNameChanged: (String) -> Unit,
    onLocalFolderChanged: (String) -> Unit,
    onComparisonModeChanged: (ComparisonMode) -> Unit,
    onToggleLivePreview: (Boolean) -> Unit,
    onValidateConnection: () -> Unit,
    onRefreshRemoteFiles: () -> Unit,
    onSaveProfile: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Sync Profile" else "Create Sync Profile") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = onSaveProfile) {
                        Text("Save", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = if (isLivePreview) 1 else 0) {
                Tab(
                    selected = !isLivePreview,
                    onClick = { onToggleLivePreview(false) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Configuration")
                        }
                    }
                )
                Tab(
                    selected = isLivePreview,
                    onClick = {
                        onToggleLivePreview(true)
                        onRefreshRemoteFiles()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Live Preview")
                        }
                    }
                )
            }

            if (!isLivePreview) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Profile Basics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = onProfileNameChanged,
                            label = { Text("Profile Name") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null) }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = localFolder,
                            onValueChange = onLocalFolderChanged,
                            label = { Text("Source Folder (Local Path)") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) }
                        )
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "SMB Connection Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = host,
                            onValueChange = onHostChanged,
                            label = { Text("Host Server IP/Name") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) }
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = port,
                                onValueChange = onPortChanged,
                                label = { Text("Port") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = domain,
                                onValueChange = onDomainChanged,
                                label = { Text("Domain (Optional)") },
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = username,
                            onValueChange = onUsernameChanged,
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChanged,
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) }
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = path,
                            onValueChange = onPathChanged,
                            label = { Text("Target Remote Share/Path") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Cloud, contentDescription = null) }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onValidateConnection,
                                enabled = !isValidating
                            ) {
                                if (isValidating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Validate Connection")
                                }
                            }

                            validationResult?.let { success ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (success) "Valid connection!" else "Failed connection!",
                                        color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Comparison Sync Engine Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Column(modifier = Modifier.selectableGroup()) {
                            ComparisonMode.entries.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .selectable(
                                            selected = (comparisonMode == mode),
                                            onClick = { onComparisonModeChanged(mode) },
                                            role = Role.RadioButton
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = (comparisonMode == mode), onClick = null)
                                    Text(
                                        text = when (mode) {
                                            ComparisonMode.SIZE -> "Size Configuration Only"
                                            ComparisonMode.DATE_TIME -> "Timestamp Modification Date Only"
                                            ComparisonMode.BOTH -> "Smart Comparison (Both Size & Timestamp)"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = onSaveProfile,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Sync Profile Settings")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remote Target Files Structure",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onRefreshRemoteFiles, enabled = !isListing) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isListing) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (remoteFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No files or directories found on the target remote path, or connection is not validated.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(remoteFiles) { file ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = "${file.size} bytes | Mod: ${file.lastModified}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Composable
fun SyncScreenAppPreview() {
    SMBSyncXTheme {
        SyncListPane(
            profiles = listOf(
                SyncProfile("Backup Pics", "/dcim", "pics", ComparisonMode.BOTH),
                SyncProfile("Documents backup", "/docs", "remote_docs", ComparisonMode.DATE_TIME)
            ),
            onCreateProfile = {},
            onEditProfile = {},
            onStartSync = {},
            onDeleteProfile = {},
            onAboutClick = {}
        )
    }
}
