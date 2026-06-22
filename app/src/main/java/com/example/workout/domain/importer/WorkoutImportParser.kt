package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.repository.normalizeExerciseName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        return when (val parsedImport = parseImport(rawPayload)) {
            is ParsedWorkoutImport.TemplateWorkouts -> parsedImport.workouts
            is ParsedWorkoutImport.FullBackup -> {
                throw WorkoutImportException("Full backup JSON must be imported as workout data.")
            }
        }
    }

    fun parseImport(rawPayload: String): ParsedWorkoutImport {
        val payload = if (rawPayload.trimStart().startsWith("{")) {
            parseJson(rawPayload)
        } else {
            ParsedWorkoutImport.TemplateWorkouts(parseWorkoutPayload(parseYaml(rawPayload)))
        }

        return payload
    }

    private fun parseWorkoutPayload(payload: WorkoutImportPayloadDto): List<WorkoutDraft> {
        if (payload.workouts.isEmpty()) {
            throw WorkoutImportException("Workout import must include at least one workout.")
        }

        return payload.workouts.mapIndexed { index, workout ->
            workout.toDraft(index)
        }
    }

    private fun parseJson(rawJson: String): ParsedWorkoutImport {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { error ->
                throw WorkoutImportException("The selected file is not valid workout JSON.", error)
            }

        if (root.containsKey("schemaVersion")) {
            return ParsedWorkoutImport.FullBackup(parseFullBackup(root))
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
        return ParsedWorkoutImport.TemplateWorkouts(parseWorkoutPayload(payload))
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

    private fun parseFullBackup(
        root: kotlinx.serialization.json.JsonObject,
    ): WorkoutDataRestoreSnapshot {
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: throw WorkoutImportException("Workout data backup JSON is missing schemaVersion.")

        return when (schemaVersion) {
            1 -> parseLegacyFullBackup(root)
            2 -> parseCurrentFullBackup(root)
            else -> throw WorkoutImportException("Unsupported workout data backup schemaVersion $schemaVersion.")
        }
    }

    private fun parseCurrentFullBackup(
        root: kotlinx.serialization.json.JsonObject,
    ): WorkoutDataRestoreSnapshot {
        val requiredCollections = listOf("exerciseDefinitions", "workouts", "sessions")
        val missingCollection = requiredCollections.firstOrNull { !root.containsKey(it) }
        if (missingCollection != null) {
            throw WorkoutImportException("Workout data backup JSON is missing $missingCollection.")
        }

        val dto = runCatching {
            json.decodeFromJsonElement<WorkoutDataImportDto>(root)
        }.getOrElse { error ->
            val message = when (error) {
                is SerializationException -> "Workout data backup JSON does not match the export schema."
                else -> "Unable to read the workout data backup JSON."
            }
            throw WorkoutImportException(message, error)
        }

        return dto.toSnapshot()
            .withBackfilledSessionExerciseDefinitions(dto.exportedAt)
            .also { it.validate() }
    }

    private fun parseLegacyFullBackup(
        root: kotlinx.serialization.json.JsonObject,
    ): WorkoutDataRestoreSnapshot {
        val requiredCollections = listOf("workouts", "sessions")
        val missingCollection = requiredCollections.firstOrNull { !root.containsKey(it) }
        if (missingCollection != null) {
            throw WorkoutImportException("Workout data backup JSON is missing $missingCollection.")
        }

        val dto = runCatching {
            json.decodeFromJsonElement<LegacyWorkoutDataImportDto>(root)
        }.getOrElse { error ->
            val message = when (error) {
                is SerializationException -> "Workout data backup JSON does not match the export schema."
                else -> "Unable to read the workout data backup JSON."
            }
            throw WorkoutImportException(message, error)
        }

        return dto.toSnapshot().also { it.validate() }
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

private fun WorkoutDataRestoreSnapshot.validate() {
    val exerciseDefinitionIds = exerciseDefinitions.map { it.id }.toSet()
    requireUniqueIds("exercise definitions", "exercise definition", exerciseDefinitions.map { it.id })

    requireUniqueIds("workouts", "workout", workouts.map { it.id })

    val circuits = workouts.flatMap { workout ->
        workout.circuits.onEach { circuit ->
            if (circuit.workoutId != workout.id) {
                throw WorkoutImportException(
                    "Circuit ${circuit.id} references workout ${circuit.workoutId}, expected ${workout.id}.",
                )
            }
        }
    }
    requireUniqueIds("circuits", "circuit", circuits.map { it.id })

    val exerciseTemplates = circuits.flatMap { circuit ->
        circuit.exercises.onEach { exercise ->
            if (exercise.circuitId != circuit.id) {
                throw WorkoutImportException(
                    "Exercise template ${exercise.id} references circuit ${exercise.circuitId}, expected ${circuit.id}.",
                )
            }
            if (exercise.exerciseDefinitionId !in exerciseDefinitionIds) {
                throw WorkoutImportException(
                    "Exercise template ${exercise.id} references missing exercise definition ${exercise.exerciseDefinitionId}.",
                )
            }
        }
    }
    requireUniqueIds("exercise templates", "exercise template", exerciseTemplates.map { it.id })

    requireUniqueIds("sessions", "session", sessions.map { it.sessionId })
    val circuitSessions = sessions.flatMap { it.circuits }
    requireUniqueIds("circuit sessions", "circuit session", circuitSessions.map { it.circuitSessionId })

    val exerciseSessions = circuitSessions.flatMap { circuit ->
        circuit.exercises.onEach { exercise ->
            exercise.exerciseDefinitionId?.let { exerciseDefinitionId ->
                if (exerciseDefinitionId !in exerciseDefinitionIds) {
                    throw WorkoutImportException(
                        "Exercise session ${exercise.exerciseSessionId} references missing exercise definition $exerciseDefinitionId.",
                    )
                }
            }
        }
    }
    val exerciseSessionIds = exerciseSessions.map { it.exerciseSessionId }.toSet()
    requireUniqueIds("exercise sessions", "exercise session", exerciseSessions.map { it.exerciseSessionId })

    val setEntries = exerciseSessions.flatMap { exercise ->
        exercise.sets.onEach { set ->
            if (set.exerciseSessionId != exercise.exerciseSessionId) {
                throw WorkoutImportException(
                    "Set entry ${set.id} references exercise session ${set.exerciseSessionId}, expected ${exercise.exerciseSessionId}.",
                )
            }
            if (set.exerciseSessionId !in exerciseSessionIds) {
                throw WorkoutImportException("Set entry ${set.id} references missing exercise session ${set.exerciseSessionId}.")
            }
        }
    }
    requireUniqueIds("set entries", "set entry", setEntries.map { it.id })
}

private fun requireUniqueIds(label: String, singularLabel: String, ids: List<Long>) {
    val duplicate = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
    if (duplicate != null) {
        throw WorkoutImportException("Workout data backup has duplicate $singularLabel id ${duplicate.key}.")
    }
    val invalid = ids.firstOrNull { it <= 0 }
    if (invalid != null) {
        throw WorkoutImportException("Workout data backup has invalid $label id $invalid.")
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

@Serializable
private data class WorkoutDataImportDto(
    val schemaVersion: Int,
    val exportedAt: Long,
    val exerciseDefinitions: List<ExerciseDefinitionImportDto>,
    val workouts: List<WorkoutTemplateImportDto>,
    val sessions: List<WorkoutSessionImportDto>,
) {
    fun toSnapshot(): WorkoutDataRestoreSnapshot {
        return WorkoutDataRestoreSnapshot(
            exerciseDefinitions = exerciseDefinitions.map { it.toRow() },
            workouts = workouts.map { it.toRow() },
            sessions = sessions.map { it.toRow() },
        )
    }
}

private fun WorkoutDataRestoreSnapshot.withBackfilledSessionExerciseDefinitions(
    timestamp: Long,
): WorkoutDataRestoreSnapshot {
    val templateDefinitionIds = workouts
        .flatMap { workout -> workout.circuits }
        .flatMap { circuit -> circuit.exercises }
        .associate { exercise -> exercise.id to exercise.exerciseDefinitionId }

    val definitionsByNormalizedName = linkedMapOf<String, ExerciseDefinitionRestoreRow>()
    exerciseDefinitions.forEach { definition ->
        definitionsByNormalizedName.putIfAbsent(normalizeExerciseName(definition.name), definition)
    }
    val addedDefinitions = mutableListOf<ExerciseDefinitionRestoreRow>()
    var nextDefinitionId = (exerciseDefinitions.maxOfOrNull { it.id } ?: 0L) + 1L

    fun definitionIdFor(exercise: ExerciseSessionRestoreRow): Long {
        exercise.exerciseTemplateId?.let { exerciseTemplateId ->
            templateDefinitionIds[exerciseTemplateId]?.let { return it }
        }

        val normalizedName = normalizeExerciseName(exercise.name)
        definitionsByNormalizedName[normalizedName]?.let { return it.id }

        val definition = ExerciseDefinitionRestoreRow(
            id = nextDefinitionId++,
            name = exercise.name.trim(),
            defaultGuidance = exercise.guidance.trim(),
            archived = false,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        definitionsByNormalizedName[normalizedName] = definition
        addedDefinitions += definition
        return definition.id
    }

    val backfilledSessions = sessions.map { session ->
        session.copy(
            circuits = session.circuits.map { circuit ->
                circuit.copy(
                    exercises = circuit.exercises.map { exercise ->
                        if (exercise.exerciseDefinitionId != null) {
                            exercise
                        } else {
                            exercise.copy(exerciseDefinitionId = definitionIdFor(exercise))
                        }
                    },
                )
            },
        )
    }

    return copy(
        exerciseDefinitions = exerciseDefinitions + addedDefinitions,
        sessions = backfilledSessions,
    )
}

@Serializable
private data class LegacyWorkoutDataImportDto(
    val schemaVersion: Int,
    val exportedAt: Long,
    val workouts: List<LegacyWorkoutTemplateImportDto>,
    val sessions: List<LegacyWorkoutSessionImportDto>,
) {
    fun toSnapshot(): WorkoutDataRestoreSnapshot {
        val definitions = LegacyExerciseDefinitionBuilder(exportedAt)
        val workouts = workouts.map { it.toRow(definitions) }
        val sessions = sessions.map { it.toRow(definitions) }
        return WorkoutDataRestoreSnapshot(
            exerciseDefinitions = definitions.rows(),
            workouts = workouts,
            sessions = sessions,
        )
    }
}

@Serializable
private data class LegacyWorkoutTemplateImportDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val circuits: List<LegacyCircuitTemplateImportDto>,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): WorkoutTemplateRestoreRow {
        return WorkoutTemplateRestoreRow(
            id = id,
            name = name,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
            circuits = circuits.map { it.toRow(definitions) },
        )
    }
}

@Serializable
private data class LegacyCircuitTemplateImportDto(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
    val exercises: List<LegacyExerciseTemplateImportDto>,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): CircuitTemplateRestoreRow {
        return CircuitTemplateRestoreRow(
            id = id,
            workoutId = workoutId,
            name = name,
            sortOrder = sortOrder,
            exercises = exercises.map { it.toRow(definitions) },
        )
    }
}

@Serializable
private data class LegacyExerciseTemplateImportDto(
    val id: Long,
    val circuitId: Long,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val setCount: Int,
    val sortOrder: Int,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): ExerciseTemplateRestoreRow {
        return ExerciseTemplateRestoreRow(
            id = id,
            circuitId = circuitId,
            exerciseDefinitionId = definitions.idFor(name, guidance),
            guidance = guidance,
            repMin = repMin,
            repMax = repMax,
            loadKind = loadKind,
            loadMin = loadMin,
            loadMax = loadMax,
            loadUnit = loadUnit,
            restTimeSeconds = restTimeSeconds,
            setCount = setCount,
            sortOrder = sortOrder,
        )
    }
}

@Serializable
private data class LegacyWorkoutSessionImportDto(
    val sessionId: Long,
    val workoutTemplateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: dev.wwade.workout.domain.model.SessionStatus,
    val circuits: List<LegacyCircuitSessionImportDto>,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): WorkoutSessionRestoreRow {
        return WorkoutSessionRestoreRow(
            sessionId = sessionId,
            workoutTemplateId = workoutTemplateId,
            workoutName = workoutName,
            startedAt = startedAt,
            completedAt = completedAt,
            status = status,
            circuits = circuits.map { it.toRow(definitions) },
        )
    }
}

@Serializable
private data class LegacyCircuitSessionImportDto(
    val circuitSessionId: Long,
    val circuitTemplateId: Long?,
    val name: String,
    val sortOrder: Int,
    val setCount: Int,
    val exercises: List<LegacyExerciseSessionImportDto>,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): CircuitSessionRestoreRow {
        return CircuitSessionRestoreRow(
            circuitSessionId = circuitSessionId,
            circuitTemplateId = circuitTemplateId,
            name = name,
            sortOrder = sortOrder,
            setCount = setCount,
            exercises = exercises.map { it.toRow(definitions) },
        )
    }
}

@Serializable
private data class LegacyExerciseSessionImportDto(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val sortOrder: Int,
    val sets: List<SetEntryImportDto>,
) {
    fun toRow(definitions: LegacyExerciseDefinitionBuilder): ExerciseSessionRestoreRow {
        return ExerciseSessionRestoreRow(
            exerciseSessionId = exerciseSessionId,
            exerciseTemplateId = exerciseTemplateId,
            exerciseDefinitionId = definitions.idFor(name, guidance),
            name = name,
            guidance = guidance,
            repMin = repMin,
            repMax = repMax,
            loadKind = loadKind,
            loadMin = loadMin,
            loadMax = loadMax,
            loadUnit = loadUnit,
            restTimeSeconds = restTimeSeconds,
            sortOrder = sortOrder,
            sets = sets.map { it.toRow() },
        )
    }
}

private class LegacyExerciseDefinitionBuilder(
    private val timestamp: Long,
) {
    private val definitionsByName = linkedMapOf<String, ExerciseDefinitionRestoreRow>()

    fun idFor(name: String, guidance: String): Long {
        val normalizedName = normalizeExerciseName(name)
        definitionsByName[normalizedName]?.let { return it.id }

        val id = definitionsByName.size.toLong() + 1L
        definitionsByName[normalizedName] = ExerciseDefinitionRestoreRow(
            id = id,
            name = name.trim(),
            defaultGuidance = guidance.trim(),
            archived = false,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        return id
    }

    fun rows(): List<ExerciseDefinitionRestoreRow> = definitionsByName.values.toList()
}

@Serializable
private data class ExerciseDefinitionImportDto(
    val id: Long,
    val name: String,
    val defaultGuidance: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toRow(): ExerciseDefinitionRestoreRow {
        return ExerciseDefinitionRestoreRow(
            id = id,
            name = name,
            defaultGuidance = defaultGuidance,
            archived = archived,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
private data class WorkoutTemplateImportDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val circuits: List<CircuitTemplateImportDto>,
) {
    fun toRow(): WorkoutTemplateRestoreRow {
        return WorkoutTemplateRestoreRow(
            id = id,
            name = name,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
            circuits = circuits.map { it.toRow() },
        )
    }
}

@Serializable
private data class CircuitTemplateImportDto(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
    val exercises: List<ExerciseTemplateImportDto>,
) {
    fun toRow(): CircuitTemplateRestoreRow {
        return CircuitTemplateRestoreRow(
            id = id,
            workoutId = workoutId,
            name = name,
            sortOrder = sortOrder,
            exercises = exercises.map { it.toRow() },
        )
    }
}

@Serializable
private data class ExerciseTemplateImportDto(
    val id: Long,
    val circuitId: Long,
    val exerciseDefinitionId: Long,
    val name: String,
    val guidance: String,
    val guidanceOverride: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val setCount: Int,
    val sortOrder: Int,
) {
    fun toRow(): ExerciseTemplateRestoreRow {
        return ExerciseTemplateRestoreRow(
            id = id,
            circuitId = circuitId,
            exerciseDefinitionId = exerciseDefinitionId,
            guidance = guidanceOverride.ifBlank { guidance },
            repMin = repMin,
            repMax = repMax,
            loadKind = loadKind,
            loadMin = loadMin,
            loadMax = loadMax,
            loadUnit = loadUnit,
            restTimeSeconds = restTimeSeconds,
            setCount = setCount,
            sortOrder = sortOrder,
        )
    }
}

@Serializable
private data class WorkoutSessionImportDto(
    val sessionId: Long,
    val workoutTemplateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: dev.wwade.workout.domain.model.SessionStatus,
    val circuits: List<CircuitSessionImportDto>,
) {
    fun toRow(): WorkoutSessionRestoreRow {
        return WorkoutSessionRestoreRow(
            sessionId = sessionId,
            workoutTemplateId = workoutTemplateId,
            workoutName = workoutName,
            startedAt = startedAt,
            completedAt = completedAt,
            status = status,
            circuits = circuits.map { it.toRow() },
        )
    }
}

@Serializable
private data class CircuitSessionImportDto(
    val circuitSessionId: Long,
    val circuitTemplateId: Long?,
    val name: String,
    val sortOrder: Int,
    val setCount: Int,
    val exercises: List<ExerciseSessionImportDto>,
) {
    fun toRow(): CircuitSessionRestoreRow {
        return CircuitSessionRestoreRow(
            circuitSessionId = circuitSessionId,
            circuitTemplateId = circuitTemplateId,
            name = name,
            sortOrder = sortOrder,
            setCount = setCount,
            exercises = exercises.map { it.toRow() },
        )
    }
}

@Serializable
private data class ExerciseSessionImportDto(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val exerciseDefinitionId: Long?,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val sortOrder: Int,
    val sets: List<SetEntryImportDto>,
) {
    fun toRow(): ExerciseSessionRestoreRow {
        return ExerciseSessionRestoreRow(
            exerciseSessionId = exerciseSessionId,
            exerciseTemplateId = exerciseTemplateId,
            exerciseDefinitionId = exerciseDefinitionId,
            name = name,
            guidance = guidance,
            repMin = repMin,
            repMax = repMax,
            loadKind = loadKind,
            loadMin = loadMin,
            loadMax = loadMax,
            loadUnit = loadUnit,
            restTimeSeconds = restTimeSeconds,
            sortOrder = sortOrder,
            sets = sets.map { it.toRow() },
        )
    }
}

@Serializable
private data class SetEntryImportDto(
    val id: Long,
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
) {
    fun toRow(): SetEntryRestoreRow {
        return SetEntryRestoreRow(
            id = id,
            exerciseSessionId = exerciseSessionId,
            setIndex = setIndex,
            repsActual = repsActual,
            loadActual = loadActual,
            notes = notes,
            skipped = skipped,
        )
    }
}
