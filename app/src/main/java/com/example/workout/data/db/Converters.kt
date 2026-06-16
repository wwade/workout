package dev.wwade.workout.data.db

import androidx.room.TypeConverter
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus

class Converters {
    @TypeConverter
    fun toLoadKind(value: String): LoadKind = LoadKind.valueOf(value)

    @TypeConverter
    fun fromLoadKind(value: LoadKind): String = value.name

    @TypeConverter
    fun toLoadUnit(value: String): LoadUnit = LoadUnit.valueOf(value)

    @TypeConverter
    fun fromLoadUnit(value: LoadUnit): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name
}
