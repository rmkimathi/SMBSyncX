package io.github.rmkimathi.smbsyncx.repository

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileAllInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import io.github.rmkimathi.smbsyncx.model.SmbConnection
import io.github.rmkimathi.smbsyncx.model.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import java.util.Date

class SmbRepository {

    suspend fun validateConnection(connection: SmbConnection): Boolean = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(connection.host, connection.port ?: 445).use { conn ->
                val ac = AuthenticationContext(
                    connection.username,
                    connection.password.toCharArray(),
                    connection.domain
                )
                val session = conn.authenticate(ac)
                session != null
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listRemoteFiles(connection: SmbConnection, path: String): List<SmbFile> = withContext(Dispatchers.IO) {
        val client = SMBClient()
        val cleanPath = path.trim('\\', '/').replace('\\', '/')
        
        val parts = cleanPath.split('/')
        val shareName = parts.first()
        val folderPath = parts.drop(1).joinToString("/")

        try {
            client.connect(connection.host, connection.port ?: 445).use { conn ->
                val ac = AuthenticationContext(
                    connection.username,
                    connection.password.toCharArray(),
                    connection.domain
                )
                val session = conn.authenticate(ac)
                (session.connectShare(shareName) as DiskShare).use { share ->
                    if (!share.folderExists(folderPath)) return@withContext emptyList()
                    
                    share.list(folderPath)
                        .filter { info -> info.fileName != "." && info.fileName != ".." }
                        .map { info ->
                            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0L
                            SmbFile(
                                name = info.fileName,
                                path = if (folderPath.isEmpty()) "$shareName/${info.fileName}" else "$shareName/$folderPath/${info.fileName}",
                                isDirectory = isDir,
                                size = info.endOfFile,
                                lastModified = info.lastWriteTime?.toEpochMillis() ?: 0L
                            )
                        }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadFile(
        connection: SmbConnection,
        remotePath: String,
        inputStream: InputStream,
        onProgress: suspend (Long) -> Unit
    ) {
        val cleanPath = remotePath.trim('\\', '/').replace('\\', '/')
        val parts = cleanPath.split('/')
        val shareName = parts.first()
        val filePath = parts.drop(1).joinToString("/")

        useDiskShare(connection, shareName) { share ->
            uploadFileToShare(share, filePath, inputStream, onProgress)
        }
    }

    suspend fun <T> useDiskShare(
        connection: SmbConnection,
        shareName: String,
        block: suspend (DiskShare) -> T
    ): T {
        val client = SMBClient()

        client.connect(connection.host, connection.port ?: 445).use { conn ->
            val ac = AuthenticationContext(
                connection.username,
                connection.password.toCharArray(),
                connection.domain
            )

            val session = conn.authenticate(ac)

            (session.connectShare(shareName) as DiskShare).use { share ->
                return block(share)
            }
        }
    }

    suspend fun uploadFileToShare(
        share: DiskShare,
        filePath: String,
        inputStream: InputStream,
        onProgress: suspend (Long) -> Unit
    ) {
        // Ensure parent directories exist
        val parentPath = filePath.substringBeforeLast('/', "")
        if (parentPath.isNotEmpty()) {
            createDirectories(share, parentPath)
        }

        val file = share.openFile(
            filePath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )

        file.use {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesWritten = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                file.write(buffer, totalBytesWritten, 0, bytesRead)
                totalBytesWritten += bytesRead
                onProgress(totalBytesWritten)
            }
        }
    }

    fun setLastModified(
        share: DiskShare,
        filePath: String,
        lastModified: Long
    ) {
        val current = share.getFileInformation(
            filePath,
            FileBasicInformation::class.java
        )

        val updated = FileBasicInformation(
            current.creationTime,
            current.lastAccessTime,
            FileTime.ofEpochMillis(lastModified),
            current.changeTime,
            current.fileAttributes
        )

        share.setFileInformation(filePath, updated)
    }

    private fun createDirectories(share: DiskShare, path: String) {
        val folders = path.split('/')
        var currentPath = ""
        for (folder in folders) {
            currentPath = if (currentPath.isEmpty()) folder else "$currentPath/$folder"
            if (!share.folderExists(currentPath)) {
                share.mkdir(currentPath)
            }
        }
    }
}
