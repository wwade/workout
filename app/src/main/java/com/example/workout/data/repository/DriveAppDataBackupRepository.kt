package dev.wwade.workout.data.repository

import dev.wwade.workout.domain.backup.DRIVE_BACKUP_FILE_PREFIX
import dev.wwade.workout.domain.backup.DriveBackupRepository
import dev.wwade.workout.domain.backup.DriveBackupSnapshot
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DriveAppDataBackupRepository(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DriveBackupRepository {
    override suspend fun uploadSnapshot(
        accessToken: String,
        fileName: String,
        exportedAt: Long,
        json: String,
    ): DriveBackupSnapshot = withContext(Dispatchers.IO) {
        val metadata = DriveFileMetadataUploadDto(
            name = fileName,
            parents = listOf(AppDataFolder),
            appProperties = mapOf("exportedAt" to exportedAt.toString()),
        )
        val response = request(
            url = "$DriveUploadBase/files?uploadType=multipart&fields=${encode(DriveFileFields)}",
            accessToken = accessToken,
            method = "POST",
            contentType = "multipart/related; boundary=$MultipartBoundary",
            body = multipartBody(
                metadataJson = this@DriveAppDataBackupRepository.json.encodeToString(
                    DriveFileMetadataUploadDto.serializer(),
                    metadata,
                ),
                fileJson = json,
            ),
        )
        this@DriveAppDataBackupRepository.json.decodeFromString(DriveFileDto.serializer(), response).toSnapshot()
    }

    override suspend fun listSnapshots(accessToken: String): List<DriveBackupSnapshot> = withContext(Dispatchers.IO) {
        val snapshots = mutableListOf<DriveBackupSnapshot>()
        var pageToken: String? = null
        do {
            val query = "name contains '$DRIVE_BACKUP_FILE_PREFIX' and trashed = false"
            val url = buildString {
                append("$DriveApiBase/files")
                append("?spaces=$AppDataFolder")
                append("&q=${encode(query)}")
                append("&fields=${encode("nextPageToken,files($DriveFileFields)")}")
                pageToken?.let { append("&pageToken=${encode(it)}") }
            }
            val response = request(
                url = url,
                accessToken = accessToken,
                method = "GET",
            )
            val page = this@DriveAppDataBackupRepository.json.decodeFromString(DriveFileListDto.serializer(), response)
            snapshots += page.files.map { it.toSnapshot() }
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())
        snapshots
    }

    override suspend fun downloadSnapshot(accessToken: String, snapshotId: String): String = withContext(Dispatchers.IO) {
        request(
            url = "$DriveApiBase/files/${encode(snapshotId)}?alt=media",
            accessToken = accessToken,
            method = "GET",
        )
    }

    override suspend fun deleteSnapshot(accessToken: String, snapshotId: String): Unit = withContext(Dispatchers.IO) {
        request(
            url = "$DriveApiBase/files/${encode(snapshotId)}",
            accessToken = accessToken,
            method = "DELETE",
        )
        Unit
    }

    private fun request(
        url: String,
        accessToken: String,
        method: String,
        contentType: String? = null,
        body: ByteArray? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
            }
        }

        if (body != null) {
            connection.outputStream.use { it.write(body) }
        }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Drive request failed ($responseCode): ${responseBody.ifBlank { "No response body." }}")
        }
        return responseBody
    }

    private fun multipartBody(metadataJson: String, fileJson: String): ByteArray {
        val output = ByteArrayOutputStream()
        fun write(value: String) {
            output.write(value.toByteArray(Charsets.UTF_8))
        }

        write("--$MultipartBoundary\r\n")
        write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        write(metadataJson)
        write("\r\n--$MultipartBoundary\r\n")
        write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        write(fileJson)
        write("\r\n--$MultipartBoundary--\r\n")
        return output.toByteArray()
    }

    private fun DriveFileDto.toSnapshot(): DriveBackupSnapshot {
        return DriveBackupSnapshot(
            id = id,
            fileName = name,
            exportedAt = appProperties?.get("exportedAt")?.toLongOrNull() ?: name.exportedAtFromFileName(),
            modifiedTime = modifiedTime?.let { Instant.parse(it).toEpochMilli() } ?: 0L,
            sizeBytes = size?.toLongOrNull(),
        )
    }

    private fun String.exportedAtFromFileName(): Long {
        return removePrefix(DRIVE_BACKUP_FILE_PREFIX)
            .removeSuffix(".json")
            .toLongOrNull()
            ?: 0L
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val AppDataFolder = "appDataFolder"
        const val DriveApiBase = "https://www.googleapis.com/drive/v3"
        const val DriveUploadBase = "https://www.googleapis.com/upload/drive/v3"
        const val DriveFileFields = "id,name,modifiedTime,size,appProperties"
        const val MultipartBoundary = "WorkoutTrackerDriveBackupBoundary"
        const val TimeoutMillis = 30_000
    }
}

@Serializable
private data class DriveFileMetadataUploadDto(
    val name: String,
    val parents: List<String>,
    val appProperties: Map<String, String>,
)

@Serializable
private data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class DriveFileDto(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val size: String? = null,
    @SerialName("appProperties")
    val appProperties: Map<String, String>? = null,
)
