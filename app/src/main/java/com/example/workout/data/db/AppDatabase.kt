package dev.wwade.workout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseDefinitionEntity::class,
        WorkoutTemplateEntity::class,
        CircuitTemplateEntity::class,
        ExerciseTemplateEntity::class,
        WorkoutSessionEntity::class,
        CircuitSessionEntity::class,
        ExerciseSessionEntity::class,
        SetEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDefinitionDao(): ExerciseDefinitionDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
