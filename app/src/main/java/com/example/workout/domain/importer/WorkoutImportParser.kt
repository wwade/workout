package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.WorkoutDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class WorkoutImportParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(rawJson: String): List<WorkoutDraft> {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { error ->
                throw WorkoutImportException("The selected file is not valid workout JSON.", error)
            }

        val payload = runCatching {
            if (root.containsKey("workouts")) {
                json.decodeFromJsonElement<WorkoutImportPayloadDto>(root)
            } else {
                WorkoutImportPayloadDto(workouts = listOf(json.decodeFromJsonElement(root)))
            }
        }.getOrElse { error ->
            val message = when (error) {
                is SerializationException -> "Workout JSON does not match the import schema."
                else -> "Unable to read the workout JSON."
            }
            throw WorkoutImportException(message, error)
        }

        if (payload.workouts.isEmpty()) {
            throw WorkoutImportException("Workout JSON must include at least one workout.")
        }

        return payload.workouts.mapIndexed { index, workout ->
            workout.toDraft(index)
        }
    }
}

@Serializable
private data class WorkoutImportPayloadDto(
    val workouts: List<WorkoutImportWorkoutDto> = emptyList(),
)

@Serializable
private data class WorkoutImportWorkoutDto(
    val name: String = "",
    val circuits: List<WorkoutImportCircuitDto>? = null,
) {
    fun toDraft(index: Int): WorkoutDraft {
        return WorkoutDraft(
            name = name.trim(),
            circuits = circuits?.map { it.toDraft() }
                ?: throw WorkoutImportException("Workout ${index + 1} is missing circuits."),
        )
    }
}

@Serializable
private data class WorkoutImportCircuitDto(
    val name: String = "",
    val exercises: List<WorkoutImportExerciseDto>? = null,
) {
    fun toDraft(): CircuitDraft {
        return CircuitDraft(
            name = name.trim(),
            exercises = exercises?.map { it.toDraft() }
                ?: throw WorkoutImportException("Circuit \"${name.ifBlank { "Unnamed" }}\" is missing exercises."),
        )
    }
}

@Serializable
private data class WorkoutImportExerciseDto(
    val name: String = "",
    val guidance: String = "",
    val repMin: Int = 6,
    val repMax: Int = 8,
    val loadKind: LoadKind = LoadKind.WEIGHT,
    val loadMin: Double = 0.0,
    val loadMax: Double = 0.0,
    val loadUnit: LoadUnit = LoadUnit.LB,
    val restTimeSeconds: Int = 60,
    val setCount: Int = 3,
) {
    fun toDraft(): ExerciseDraft {
        return ExerciseDraft(
            name = name.trim(),
            guidance = guidance.trim(),
            repMin = repMin,
            repMax = repMax,
            loadKind = loadKind,
            loadMin = loadMin,
            loadMax = loadMax,
            loadUnit = loadUnit,
            restTimeSeconds = restTimeSeconds,
            setCount = setCount,
        )
    }
}
