package dev.wwade.workout.domain.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class JavaNetWorkoutImportJsonFetcher : WorkoutImportJsonFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = runCatching {
            URL(url).openConnection() as HttpURLConnection
        }.getOrElse { error ->
            throw WorkoutImportException("Enter a valid direct JSON or YAML URL.", error)
        }

        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json, application/yaml, text/yaml, text/*;q=0.8")

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw WorkoutImportException("Import URL returned HTTP $statusCode.")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: WorkoutImportException) {
            throw error
        } catch (error: IOException) {
            throw WorkoutImportException("Unable to download the workout file.", error)
        } finally {
            connection.disconnect()
        }
    }
}
