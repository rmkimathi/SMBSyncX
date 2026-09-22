package io.github.rmkimathi.smbsyncx.model

data class SmbConnection(
    val host: String,
    val port: Int? = null,
    val domain: String? = null,
    val username: String = "",
    val password: String = ""
)
