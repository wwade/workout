package dev.wwade.workout.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "circuit_templates",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class CircuitTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "exercise_templates",
    foreignKeys = [
        ForeignKey(
            entity = CircuitTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["circuitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("circuitId")],
)
data class ExerciseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateId: Long?,
    val workoutNameSnapshot: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: SessionStatus,
)

@Entity(
    tableName = "circuit_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutSessionId")],
)
data class CircuitSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutSessionId: Long,
    val circuitTemplateId: Long?,
    val circuitNameSnapshot: String,
    val sortOrder: Int,
    val setCount: Int,
)

@Entity(
    tableName = "exercise_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CircuitSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["circuitSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("circuitSessionId"), Index("exerciseTemplateId")],
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val circuitSessionId: Long,
    val exerciseTemplateId: Long?,
    val exerciseNameSnapshot: String,
    val guidanceSnapshot: String,
    val repMinSnapshot: Int,
    val repMaxSnapshot: Int,
    val loadKindSnapshot: LoadKind,
    val loadMinSnapshot: Double,
    val loadMaxSnapshot: Double,
    val loadUnitSnapshot: LoadUnit,
    val restTimeSecondsSnapshot: Int,
    val sortOrder: Int,
)

@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exerciseSessionId", "setIndex"], unique = true)],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

data class ExerciseSetHistoryProjection(
    val workoutSessionId: Long,
    val workoutName: String,
    val completedAt: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

data class CircuitTemplateWithExercises(
    @Embedded val circuit: CircuitTemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "circuitId",
    )
    val exercises: List<ExerciseTemplateEntity>,
)

data class WorkoutTemplateWithChildren(
    @Embedded val workout: WorkoutTemplateEntity,
    @Relation(
        entity = CircuitTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val circuits: List<CircuitTemplateWithExercises>,
)

data class ExerciseSessionWithSets(
    @Embedded val exercise: ExerciseSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseSessionId",
    )
    val sets: List<SetEntryEntity>,
)

data class CircuitSessionWithExercises(
    @Embedded val circuit: CircuitSessionEntity,
    @Relation(
        entity = ExerciseSessionEntity::class,
        parentColumn = "id",
        entityColumn = "circuitSessionId",
    )
    val exercises: List<ExerciseSessionWithSets>,
)

data class WorkoutSessionWithChildren(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = CircuitSessionEntity::class,
        parentColumn = "id",
        entityColumn = "workoutSessionId",
    )
    val circuits: List<CircuitSessionWithExercises>,
)
