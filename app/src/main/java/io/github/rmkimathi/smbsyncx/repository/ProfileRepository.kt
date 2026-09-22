package io.github.rmkimathi.smbsyncx.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rmkimathi.smbsyncx.model.SyncProfileData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles_settings")

class ProfileRepository(private val context: Context) {
    private val PROFILES_KEY = stringPreferencesKey("profiles_list")
    private val gson = Gson()

    val profiles: Flow<List<SyncProfileData>> = context.dataStore.data
        .map { preferences ->
            val profilesJson = preferences[PROFILES_KEY] ?: "[]"
            try {
                val type = object : TypeToken<List<SyncProfileData>>() {}.type
                gson.fromJson<List<SyncProfileData>>(profilesJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveProfiles(profiles: List<SyncProfileData>) {
        val profilesJson = gson.toJson(profiles)
        context.dataStore.edit { preferences ->
            preferences[PROFILES_KEY] = profilesJson
        }
    }
}
