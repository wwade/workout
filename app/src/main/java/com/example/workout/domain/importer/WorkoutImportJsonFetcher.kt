package dev.wwade.workout.domain.importer

interface WorkoutImportJsonFetcher {
    suspend fun fetch(url: String): String
}

