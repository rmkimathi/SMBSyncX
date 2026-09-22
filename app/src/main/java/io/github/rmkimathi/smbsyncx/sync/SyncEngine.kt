package io.github.rmkimathi.smbsyncx.sync

import com.hierynomus.smbj.share.DiskShare
import io.github.rmkimathi.smbsyncx.model.ComparisonMode
import io.github.rmkimathi.smbsyncx.model.SmbConnection
import io.github.rmkimathi.smbsyncx.model.SyncProfile
import io.github.rmkimathi.smbsyncx.model.SyncState
import io.github.rmkimathi.smbsyncx.repository.SmbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException

class SyncEngine(private val smbRepository: SmbRepository) {

    fun sync(
        profile: SyncProfile,
        connection: SmbConnection
    ): Flow<SyncState> = flow {

        val localRoot = File(profile.sourceFolder)

        if (!localRoot.exists() || !localRoot.isDirectory) {
            emit(
                SyncState.Error(
                    "Source folder does not exist or is not a directory"
                )
            )
            return@flow
        }

        val localFiles = localRoot.walkTopDown()
            .onEnter { directory ->
                // Ensure we process all directories, but the filter { it.isFile } below
                // will pick up files within them including hidden ones.
                true
            }
            .filter { it.isFile }
            .toList()

        val totalFiles = localFiles.size
        var processedFiles = 0

        val cleanTarget = profile.targetFolder.trim('\\', '/')
        val shareName = cleanTarget.split('/').first()
        val remoteRootPath = cleanTarget
            .split('/')
            .drop(1)
            .joinToString("/")

        smbRepository.useDiskShare(connection, shareName) { share ->

            for (localFile in localFiles) {

                val relativePath = localFile
                    .relativeTo(localRoot)
                    .path
                    .replace('\\', '/')

                val remoteFilePath =
                    if (remoteRootPath.isEmpty()) {
                        relativePath
                    } else {
                        "$remoteRootPath/$relativePath"
                    }

                emit(
                    SyncState.Progress(
                        localFile.name,
                        0f,
                        processedFiles,
                        totalFiles
                    )
                )

                try {
                    val needsSync = checkNeedsSync(
                        localFile,
                        remoteFilePath,
                        profile.comparisonMode,
                        share
                    )

                    if (needsSync) {
                        localFile.inputStream().use { inputStream ->
                            var lastEmitTime = 0L
                            smbRepository.uploadFileToShare(
                                share,
                                remoteFilePath,
                                inputStream
                            ) { bytesWritten ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastEmitTime > 200) {
                                    val currentProgress = bytesWritten.toFloat() / localFile.length()
                                    emit(
                                        SyncState.Progress(
                                            localFile.name,
                                            currentProgress,
                                            processedFiles,
                                            totalFiles
                                        )
                                    )
                                    lastEmitTime = currentTime
                                }
                            }
                        }

                        smbRepository.setLastModified(
                            share,
                            remoteFilePath,
                            localFile.lastModified()
                        )
                    }

                } catch (e: Exception) {

                    if (e is IOException) {
                        throw e
                    }

                    // Skip file if it is not a connection issue.
                }

                processedFiles++

                emit(
                    SyncState.Progress(
                        localFile.name,
                        1f,
                        processedFiles,
                        totalFiles
                    )
                )
            }
        }

        emit(SyncState.Completed)

    }
        .flowOn(Dispatchers.IO)
        .catch { e ->
            emit(
                SyncState.Error(
                    e.message ?: "Unknown error occurred during synchronization"
                )
            )
        }

    private suspend fun checkNeedsSync(
        localFile: File,
        remotePath: String,
        mode: ComparisonMode,
        share: DiskShare
    ): Boolean {

        if (!share.fileExists(remotePath)) {
            return true
        }

        val remoteInfo = share.getFileInformation(remotePath)
        val remoteSize = remoteInfo.standardInformation.endOfFile
        val remoteLastModified =
            remoteInfo.basicInformation.lastWriteTime.toEpochMillis()

        val localSize = localFile.length()
        val localLastModified = localFile.lastModified()

        return when (mode) {
            ComparisonMode.SIZE ->
                localSize != remoteSize

            ComparisonMode.DATE_TIME ->
                localLastModified > remoteLastModified

            ComparisonMode.BOTH ->
                localSize != remoteSize ||
                        localLastModified > remoteLastModified
        }
    }
}