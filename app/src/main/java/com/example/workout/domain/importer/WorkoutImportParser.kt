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
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class WorkoutImportParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
    private val yaml = Load(
        LoadSettings.builder()
            .setLabel("workout import")
            .build(),
    )

    fun parse(rawPayload: String): List<WorkoutDraft> {
        val payload = if (rawPayload.trimStart().startsWith("{")) {
            parseJson(rawPayload)
        } else {
            parseYaml(rawPayload)
        }

        if (payload.workouts.isEmpty()) {
            throw WorkoutImportException("Workout import must include at least one workout.")
        }

        return payload.workouts.mapIndexed { index, workout ->
            workout.toDraft(index)
        }
    }

    private fun parseJson(rawJson: String): WorkoutImportPayloadDto {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { error ->
                throw WorkoutImportException("The selected file is not valid workout JSON.", error)
            }

        return runCatching {
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
    }

    private fun parseYaml(rawYaml: String): WorkoutImportPayloadDto {
        val root = runCatching { yaml.loadFromString(rawYaml) }
            .getOrElse { error ->
                throw WorkoutImportException("The selected file is not valid workout YAML.", error)
            }

        val rootMap = root as? Map<*, *>
            ?: throw WorkoutImportException("Workout YAML must be a mapping with workout fields.")

        return runCatching {
            if (rootMap.containsKey("workouts")) {
                WorkoutImportPayloadDto(
                    workouts = rootMap.requiredList("workouts").mapIndexed { index, workout ->
                        workout.asMap("workouts[${index + 1}]").toWorkoutDto(index)
                    },
                )
            } else {
                WorkoutImportPayloadDto(workouts = listOf(rootMap.toWorkoutDto(index = 0)))
            }
        }.getOrElse { error ->
            if (error is WorkoutImportException) {
                throw error
            }
            throw WorkoutImportException("Workout YAML does not match the import schema.", error)
        }
    }
}

private fun Map<*, *>.toWorkoutDto(index: Int): WorkoutImportWorkoutDto {
    return WorkoutImportWorkoutDto(
        name = optionalString("name"),
        circuits = requiredList("circuits").mapIndexed { circuitIndex, circuit ->
            circuit.asMap("workout ${index + 1} circuit ${circuitIndex + 1}").toCircuitDto()
        },
    )
}

private fun Map<*, *>.toCircuitDto(): WorkoutImportCircuitDto {
    return WorkoutImportCircuitDto(
        name = optionalString("name"),
        exercises = requiredList("exercises").mapIndexed { exerciseIndex, exercise ->
            exercise.asMap("exercise ${exerciseIndex + 1}").toExerciseDto()
        },
    )
}

private fun Map<*, *>.toExerciseDto(): WorkoutImportExerciseDto {
    return WorkoutImportExerciseDto(
        name = optionalString("name"),
        guidance = optionalString("guidance"),
        repMin = optionalInt("repMin", default = 6),
        repMax = optionalInt("repMax", default = 8),
        loadKind = optionalEnum("loadKind", LoadKind.WEIGHT),
        loadMin = optionalDouble("loadMin", default = 0.0),
        loadMax = optionalDouble("loadMax", default = 0.0),
        loadUnit = optionalEnum("loadUnit", LoadUnit.LB),
        restTimeSeconds = optionalInt("restTimeSeconds", default = 60),
        setCount = optionalInt("setCount", default = 3),
    )
}

private fun Map<*, *>.requiredList(key: String): List<*> {
    val value = this[key] ?: throw WorkoutImportException("Workout YAML is missing $key.")
    return value as? List<*>
        ?: throw WorkoutImportException("Workout YAML field $key must be a list.")
}

private fun Any?.asMap(label: String): Map<*, *> {
    return this as? Map<*, *>
        ?: throw WorkoutImportException("Workout YAML $label must be a mapping.")
}

private fun Map<*, *>.optionalString(key: String): String {
    return this[key]?.toString().orEmpty()
}

private fun Map<*, *>.optionalInt(key: String, default: Int): Int {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    } ?: throw WorkoutImportException("Workout YAML field $key must be a number.")
}

private fun Map<*, *>.optionalDouble(key: String, default: Double): Double {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    } ?: throw WorkoutImportException("Workout YAML field $key must be a number.")
}

private inline fun <reified T : Enum<T>> Map<*, *>.optionalEnum(key: String, default: T): T {
    val value = this[key]?.toString()?.trim() ?: return default
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw WorkoutImportException("Workout YAML field $key has an unknown value.")
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
