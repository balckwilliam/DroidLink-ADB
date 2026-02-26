package com.droidlink.app.presentation.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidlink.app.domain.model.FileEntry
import com.droidlink.app.domain.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilesViewModel(
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _files = MutableStateFlow<List<FileEntry>>(emptyList())
    val files: StateFlow<List<FileEntry>> = _files.asStateFlow()

    private val _currentPath = MutableStateFlow("/sdcard")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun loadFiles(path: String = _currentPath.value) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentPath.value = path
            try {
                _files.value = adbRepository.listFiles(path)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to list files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigateTo(entry: FileEntry) {
        if (entry.isDirectory) {
            loadFiles(entry.path)
        }
    }

    fun navigateUp() {
        val parent = _currentPath.value.substringBeforeLast("/")
        if (parent.isNotEmpty()) {
            loadFiles(parent)
        } else {
            loadFiles("/")
        }
    }

    fun deleteFile(entry: FileEntry) {
        viewModelScope.launch {
            try {
                val flag = if (entry.isDirectory) "-rf" else ""
                adbRepository.executeShell("rm $flag ${entry.path}")
                _statusMessage.value = "Deleted ${entry.name}"
                loadFiles()
            } catch (e: Exception) {
                _statusMessage.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
