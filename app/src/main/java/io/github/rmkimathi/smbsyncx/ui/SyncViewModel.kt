package io.github.rmkimathi.smbsyncx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rmkimathi.smbsyncx.model.ComparisonMode
import io.github.rmkimathi.smbsyncx.model.SmbConnection
import io.github.rmkimathi.smbsyncx.model.SmbFile
import io.github.rmkimathi.smbsyncx.model.SyncProfile
import io.github.rmkimathi.smbsyncx.model.SyncState
import io.github.rmkimathi.smbsyncx.repository.ProfileRepository
import io.github.rmkimathi.smbsyncx.repository.SmbRepository
import io.github.rmkimathi.smbsyncx.model.SyncProfileData
import io.github.rmkimathi.smbsyncx.sync.SyncEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

class SyncViewModel(
    private val smbRepository: SmbRepository = SmbRepository(),
    private val profileRepository: ProfileRepository? = null
) : ViewModel() {

    private val syncEngine = SyncEngine(smbRepository)

    init {
        viewModelScope.launch {
            profileRepository?.profiles?.first()?.let { savedData ->
                if (savedData.isNotEmpty()) {
                    _profilesList.value = savedData.map { it.profile }
                    profileConnections.clear()
                    savedData.forEach { data ->
                        profileConnections[data.profile.name] = data.connection
                    }
                }
            }
        }
    }

    private val _host = MutableStateFlow("192.168.1.100")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow("445")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _domain = MutableStateFlow("")
    val domain: StateFlow<String> = _domain.asStateFlow()

    private val _username = MutableStateFlow("admin")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _path = MutableStateFlow("SharedFolder")
    val path: StateFlow<String> = _path.asStateFlow()

    private val _profileName = MutableStateFlow("Default Backup")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _localFolder = MutableStateFlow("/storage/emulated/0/Download")
    val localFolder: StateFlow<String> = _localFolder.asStateFlow()

    private val _comparisonMode = MutableStateFlow(ComparisonMode.BOTH)
    val comparisonMode: StateFlow<ComparisonMode> = _comparisonMode.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _validationResult = MutableStateFlow<Boolean?>(null)
    val validationResult: StateFlow<Boolean?> = _validationResult.asStateFlow()

    private val _isListing = MutableStateFlow(false)
    val isListing: StateFlow<Boolean> = _isListing.asStateFlow()

    private val _remoteFiles = MutableStateFlow<List<SmbFile>>(emptyList())
    val remoteFiles: StateFlow<List<SmbFile>> = _remoteFiles.asStateFlow()

    // Designer Mode view toggle: true for Live Preview, false for Configuration Form
    private val _isLivePreview = MutableStateFlow(false)
    val isLivePreview: StateFlow<Boolean> = _isLivePreview.asStateFlow()

    // Sync Engine Real-Time State
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Active/Selected profile name for Sync Execution
    private val _activeProfileName = MutableStateFlow<String?>(null)
    val activeProfileName: StateFlow<String?> = _activeProfileName.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _showPermissionRationale = MutableStateFlow(false)
    val showPermissionRationale: StateFlow<Boolean> = _showPermissionRationale.asStateFlow()

    // Manage a list of sync profiles in-memory
    private val _profilesList = MutableStateFlow<List<SyncProfile>>(
        listOf(
            SyncProfile("Default Backup", "/storage/emulated/0/Download", "SharedFolder", ComparisonMode.BOTH),
            SyncProfile("Media Sync", "/storage/emulated/0/DCIM", "SharedFolder/Media", ComparisonMode.DATE_TIME)
        )
    )
    val profilesList: StateFlow<List<SyncProfile>> = _profilesList.asStateFlow()

    // Keep track of connection properties for each profile name
    private val profileConnections = mutableMapOf<String, SmbConnection>().apply {
        put("Default Backup", SmbConnection("192.168.1.100", 445, "", "admin", ""))
        put("Media Sync", SmbConnection("192.168.1.100", 445, "", "admin", ""))
    }

    private var syncJob: Job? = null

    fun onHostChanged(value: String) { _host.value = value }
    fun onPortChanged(value: String) { _port.value = value }
    fun onDomainChanged(value: String) { _domain.value = value }
    fun onUsernameChanged(value: String) { _username.value = value }
    fun onPasswordChanged(value: String) { _password.value = value }
    fun onPathChanged(value: String) { _path.value = value }
    fun onProfileNameChanged(value: String) { _profileName.value = value }
    fun onLocalFolderChanged(value: String) { _localFolder.value = value }
    fun onComparisonModeChanged(value: ComparisonMode) { _comparisonMode.value = value }
    fun setLivePreview(value: Boolean) { _isLivePreview.value = value }

    private fun buildConnection(): SmbConnection {
        return SmbConnection(
            host = _host.value,
            port = _port.value.toIntOrNull(),
            domain = _domain.value.ifBlank { null },
            username = _username.value,
            password = _password.value
        )
    }

    fun validateConnection() {
        viewModelScope.launch {
            _isValidating.value = true
            _validationResult.value = null
            val connection = buildConnection()
            val result = smbRepository.validateConnection(connection)
            _validationResult.value = result
            _isValidating.value = false
        }
    }

    fun listRemoteFiles() {
        viewModelScope.launch {
            _isListing.value = true
            val connection = buildConnection()
            val files = smbRepository.listRemoteFiles(connection, _path.value)
            _remoteFiles.value = files
            _isListing.value = false
        }
    }

    fun loadProfileForEditing(name: String?) {
        _validationResult.value = null
        _isLivePreview.value = false
        if (name == null) {
            // Setup for a new profile
            _profileName.value = ""
            _localFolder.value = "/storage/emulated/0/"
            _path.value = "SharedFolder"
            _comparisonMode.value = ComparisonMode.BOTH
            _host.value = "192.168.1.100"
            _port.value = "445"
            _domain.value = ""
            _username.value = "admin"
            _password.value = ""
            _remoteFiles.value = emptyList()
        } else {
            val profile = _profilesList.value.find { it.name == name }
            if (profile != null) {
                _profileName.value = profile.name
                _localFolder.value = profile.sourceFolder
                _path.value = profile.targetFolder
                _comparisonMode.value = profile.comparisonMode
                
                val conn = profileConnections[name] ?: SmbConnection("192.168.1.100", 445, "", "admin", "")
                _host.value = conn.host
                _port.value = (conn.port ?: 445).toString()
                _domain.value = conn.domain ?: ""
                _username.value = conn.username
                _password.value = conn.password
                _remoteFiles.value = emptyList()
            }
        }
    }

    fun saveProfile() {
        if (_profileName.value.isBlank()) return
        
        val newProfile = SyncProfile(
            name = _profileName.value,
            sourceFolder = _localFolder.value,
            targetFolder = _path.value,
            comparisonMode = _comparisonMode.value
        )
        val newConnection = buildConnection()

        // Update list
        val currentList = _profilesList.value.toMutableList()
        val index = currentList.indexOfFirst { it.name == _profileName.value }
        if (index >= 0) {
            currentList[index] = newProfile
        } else {
            currentList.add(newProfile)
        }
        _profilesList.value = currentList
        profileConnections[_profileName.value] = newConnection
        persistProfiles()
    }

    private fun persistProfiles() {
        viewModelScope.launch {
            val dataList = _profilesList.value.map { profile ->
                SyncProfileData(profile, profileConnections[profile.name] ?: SmbConnection(""))
            }
            profileRepository?.saveProfiles(dataList)
        }
    }

    fun deleteProfile(name: String) {
        _profilesList.value = _profilesList.value.filter { it.name != name }
        profileConnections.remove(name)
        persistProfiles()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun setShowPermissionRationale(show: Boolean) {
        _showPermissionRationale.value = show
    }

    fun startSync(name: String) {
        _activeProfileName.value = name
        val profile = _profilesList.value.find { it.name == name }
        val connection = profileConnections[name]

        if (profile == null || connection == null) {
            _syncState.value = SyncState.Error("Profile configuration not found")
            return
        }

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _syncState.value = SyncState.Idle
            syncEngine.sync(profile, connection).collect { state ->
                _syncState.value = state
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        _syncState.value = SyncState.Idle
    }
}
