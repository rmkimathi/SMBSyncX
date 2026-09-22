package io.github.rmkimathi.smbsyncx

import io.github.rmkimathi.smbsyncx.model.ComparisonMode
import io.github.rmkimathi.smbsyncx.model.SmbConnection
import io.github.rmkimathi.smbsyncx.model.SyncProfile
import io.github.rmkimathi.smbsyncx.repository.SmbRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SmbRepositoryTest {

    @Test
    fun testDataModels() {
        val connection = SmbConnection(
            host = "192.168.1.100",
            port = 445,
            domain = "WORKGROUP",
            username = "user",
            password = "password"
        )
        assertEquals("192.168.1.100", connection.host)
        assertEquals(445, connection.port)
        assertEquals("WORKGROUP", connection.domain)
        assertEquals("user", connection.username)
        assertEquals("password", connection.password)

        val profile = SyncProfile(
            name = "Backup",
            sourceFolder = "/local/path",
            targetFolder = "Share/remote/path",
            comparisonMode = ComparisonMode.BOTH
        )
        assertEquals("Backup", profile.name)
        assertEquals("/local/path", profile.sourceFolder)
        assertEquals("Share/remote/path", profile.targetFolder)
        assertEquals(ComparisonMode.BOTH, profile.comparisonMode)
    }

    @Test
    fun testValidateConnection_withInvalidHost_returnsFalse() = runTest {
        val repository = SmbRepository()
        val connection = SmbConnection(
            host = "invalid_host_name_xyz",
            port = 445,
            username = "user",
            password = "password"
        )
        val result = repository.validateConnection(connection)
        assertFalse(result)
    }

    @Test
    fun testListRemoteFiles_withEmptyPath_returnsEmptyList() = runTest {
        val repository = SmbRepository()
        val connection = SmbConnection(
            host = "192.168.1.100",
            username = "user",
            password = "password"
        )
        val result = repository.listRemoteFiles(connection, "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testListRemoteFiles_withInvalidHost_returnsEmptyList() = runTest {
        val repository = SmbRepository()
        val connection = SmbConnection(
            host = "invalid_host_name_xyz",
            username = "user",
            password = "password"
        )
        val result = repository.listRemoteFiles(connection, "share/path")
        assertTrue(result.isEmpty())
    }
}
