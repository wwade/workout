package dev.wwade.workout.data.repository

import android.content.Context
import dev.wwade.workout.domain.backup.DriveBackupSettings
import dev.wwade.workout.domain.backup.DriveBackupSettingsRepository
import dev.wwade.workout.domain.backup.DriveBackupSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesDriveBackupSettingsRepository(
    context: Context,
) : DriveBackupSettingsRepository {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readSettings())

    override fun observeSettings(): Flow<DriveBackupSettings> = settings.asStateFlow()

    override suspend fun getSettings(): DriveBackupSettings = settings.value

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyEnabled, enabled)
            .apply()
        settings.value = readSettings()
    }

    override suspend fun recordBackupSuccess(snapshot: DriveBackupSnapshot) {
        preferences.edit()
            .putLong(KeyLastSuccessAt, snapshot.modifiedTime)
            .putString(KeyLastSuccessFileName, snapshot.fileName)
            .remove(KeyLastFailureMessage)
            .apply()
        settings.value = readSettings()
    }

    override suspend fun recordBackupFailure(message: String) {
        preferences.edit()
            .putString(KeyLastFailureMessage, message)
            .apply()
        settings.value = readSettings()
    }

    private fun readSettings(): DriveBackupSettings {
        val lastSuccessAt = preferences.getLong(KeyLastSuccessAt, MissingLong).takeIf { it != MissingLong }
        return DriveBackupSettings(
            enabled = preferences.getBoolean(KeyEnabled, false),
            lastSuccessAt = lastSuccessAt,
            lastSuccessFileName = preferences.getString(KeyLastSuccessFileName, null),
            lastFailureMessage = preferences.getString(KeyLastFailureMessage, null),
        )
    }

    private companion object {
        const val PreferencesName = "drive-backup"
        const val KeyEnabled = "driveBackupEnabled"
        const val KeyLastSuccessAt = "lastSuccessfulBackupAt"
        const val KeyLastSuccessFileName = "lastSuccessfulBackupFileName"
        const val KeyLastFailureMessage = "lastBackupFailureMessage"
        const val MissingLong = -1L
    }
}
