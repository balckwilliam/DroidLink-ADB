package com.droidlink.app.presentation.screens.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidlink.app.domain.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShellLine(
    val text: String,
    val isCommand: Boolean = false
)

class ShellViewModel(
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _outputLines = MutableStateFlow<List<ShellLine>>(emptyList())
    val outputLines: StateFlow<List<ShellLine>> = _outputLines.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun updateCommand(command: String) {
        _commandInput.value = command
    }

    fun executeCommand() {
        val command = _commandInput.value.trim()
        if (command.isBlank()) return

        _commandInput.value = ""
        _outputLines.value = _outputLines.value + ShellLine("$ $command", isCommand = true)

        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val output = adbRepository.executeShell(command)
                if (output.isNotBlank()) {
                    _outputLines.value = _outputLines.value + ShellLine(output)
                }
            } catch (e: Exception) {
                _outputLines.value = _outputLines.value +
                        ShellLine("Error: ${e.message}", isCommand = false)
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun clearOutput() {
        _outputLines.value = emptyList()
    }
}
