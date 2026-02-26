package com.droidlink.app.presentation.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidlink.app.domain.model.DeviceApp
import com.droidlink.app.domain.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppsViewModel(
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<DeviceApp>>(emptyList())
    val apps: StateFlow<List<DeviceApp>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _apps.value = adbRepository.listPackages()
            } catch (e: Exception) {
                _statusMessage.value = "Failed to load apps: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun uninstallApp(app: DeviceApp) {
        viewModelScope.launch {
            val success = adbRepository.uninstallApp(app.packageName)
            _statusMessage.value = if (success) "Uninstalled ${app.packageName}" else "Failed to uninstall"
            if (success) loadApps()
        }
    }

    fun clearAppData(app: DeviceApp) {
        viewModelScope.launch {
            val success = adbRepository.clearAppData(app.packageName)
            _statusMessage.value = if (success) "Cleared data for ${app.packageName}" else "Failed to clear data"
        }
    }

    fun disableApp(app: DeviceApp) {
        viewModelScope.launch {
            val success = adbRepository.disableApp(app.packageName)
            _statusMessage.value = if (success) "Disabled ${app.packageName}" else "Failed to disable"
            if (success) loadApps()
        }
    }

    fun enableApp(app: DeviceApp) {
        viewModelScope.launch {
            val success = adbRepository.enableApp(app.packageName)
            _statusMessage.value = if (success) "Enabled ${app.packageName}" else "Failed to enable"
            if (success) loadApps()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
