package io.github.rmkimathi.smbsyncx.model

data class SyncProfile(
    val name: String,
    val sourceFolder: String,
    val targetFolder: String,
    val comparisonMode: ComparisonMode
)
