package io.github.rmkimathi.smbsyncx.model

sealed class SyncState {
    object Idle : SyncState()
    data class Progress(
        val currentFile: String,
        val progress: Float,
        val processedFiles: Int,
        val totalFiles: Int
    ) : SyncState()
    object Completed : SyncState()
    data class Error(val message: String) : SyncState()
}
