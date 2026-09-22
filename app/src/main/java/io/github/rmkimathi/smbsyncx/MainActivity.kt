package io.github.rmkimathi.smbsyncx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.rmkimathi.smbsyncx.ui.theme.SMBSyncXTheme

import io.github.rmkimathi.smbsyncx.ui.SyncScreen
import io.github.rmkimathi.smbsyncx.ui.SyncViewModel

import io.github.rmkimathi.smbsyncx.repository.ProfileRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileRepository = ProfileRepository(applicationContext)
        val viewModel = SyncViewModel(profileRepository = profileRepository)
        setContent {
            SMBSyncXTheme {
                SyncScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}