package dev.wwade.workout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WorkoutTemplateEntity::class,
        CircuitTemplateEntity::class,
        ExerciseTemplateEntity::class,
        WorkoutSessionEntity::class,
        CircuitSessionEntity::class,
        ExerciseSessionEntity::class,
        SetEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
