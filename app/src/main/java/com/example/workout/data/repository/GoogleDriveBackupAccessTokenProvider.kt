package dev.wwade.workout.data.repository

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.wwade.workout.domain.backup.DRIVE_APPDATA_SCOPE
import dev.wwade.workout.domain.backup.DriveBackupAccessTokenProvider
import kotlinx.coroutines.tasks.await

class GoogleDriveBackupAccessTokenProvider(
    private val context: Context,
) : DriveBackupAccessTokenProvider {
    override suspend fun getAccessTokenOrNull(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = runCatching {
            Identity.getAuthorizationClient(context).authorize(request).await()
        }.getOrNull() ?: return null
        return if (result.hasResolution()) null else result.accessToken
    }
}
