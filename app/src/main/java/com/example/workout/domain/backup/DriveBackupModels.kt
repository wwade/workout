package dev.wwade.workout.domain.backup

import kotlinx.coroutines.flow.Flow

const val DRIVE_APPDATA_SCOPE: String = "https://www.googleapis.com/auth/drive.appdata"
const val DRIVE_BACKUP_FILE_PREFIX: String = "workout-tracker-backup-"
const val DRIVE_BACKUP_LIMIT: Int = 5

data class DriveBackupSnapshot(
    val id: String,
    val fileName: String,
    val exportedAt: Long,
    val modifiedTime: Long,
    val sizeBytes: Long?,
)

data class DriveBackupSettings(
    val enabled: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastSuccessFileName: String? = null,
    val lastFailureMessage: String? = null,
)

interface DriveBackupRepository {
    suspend fun uploadSnapshot(
        accessToken: String,
        fileName: String,
        exportedAt: Long,
        json: String,
    ): DriveBackupSnapshot

    suspend fun listSnapshots(accessToken: String): List<DriveBackupSnapshot>
    suspend fun downloadSnapshot(accessToken: String, snapshotId: String): String
    suspend fun deleteSnapshot(accessToken: String, snapshotId: String)
}

interface DriveBackupSettingsRepository {
    fun observeSettings(): Flow<DriveBackupSettings>
    suspend fun getSettings(): DriveBackupSettings
    suspend fun setEnabled(enabled: Boolean)
    suspend fun recordBackupSuccess(snapshot: DriveBackupSnapshot)
    suspend fun recordBackupFailure(message: String)
}

interface DriveBackupAccessTokenProvider {
    suspend fun getAccessTokenOrNull(): String?
}
