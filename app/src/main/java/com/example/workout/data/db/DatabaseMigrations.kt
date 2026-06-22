package dev.wwade.workout.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.wwade.workout.domain.repository.normalizeExerciseName

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_definitions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                defaultGuidance TEXT NOT NULL,
                archived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_definitions_normalizedName ON exercise_definitions(normalizedName)",
        )

        val now = System.currentTimeMillis()
        val definitionIds = mutableMapOf<String, Long>()
        db.query("SELECT id, name, guidance FROM exercise_templates ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val guidance = cursor.getString(cursor.getColumnIndexOrThrow("guidance"))
                val normalizedName = normalizeExerciseName(name)
                if (!definitionIds.containsKey(normalizedName)) {
                    db.execSQL(
                        """
                        INSERT INTO exercise_definitions(name, normalizedName, defaultGuidance, archived, createdAt, updatedAt)
                        VALUES (?, ?, ?, 0, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any>(name.trim(), normalizedName, guidance.trim(), now, now),
                    )
                    db.query("SELECT last_insert_rowid()").use { idCursor ->
                        idCursor.moveToFirst()
                        definitionIds[normalizedName] = idCursor.getLong(0)
                    }
                }
            }
        }

        db.execSQL(
            """
            CREATE TABLE exercise_templates_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                circuitId INTEGER NOT NULL,
                exerciseDefinitionId INTEGER NOT NULL,
                guidance TEXT NOT NULL,
                repMin INTEGER NOT NULL,
                repMax INTEGER NOT NULL,
                loadKind TEXT NOT NULL,
                loadMin REAL NOT NULL,
                loadMax REAL NOT NULL,
                loadUnit TEXT NOT NULL,
                restTimeSeconds INTEGER NOT NULL,
                setCount INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                FOREIGN KEY(circuitId) REFERENCES circuit_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(exerciseDefinitionId) REFERENCES exercise_definitions(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )

        db.query("SELECT * FROM exercise_templates ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val definitionId = definitionIds.getValue(normalizeExerciseName(name))
                db.execSQL(
                    """
                    INSERT INTO exercise_templates_new(
                        id, circuitId, exerciseDefinitionId, guidance, repMin, repMax, loadKind,
                        loadMin, loadMax, loadUnit, restTimeSeconds, setCount, sortOrder
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("circuitId")),
                        definitionId,
                        cursor.getString(cursor.getColumnIndexOrThrow("guidance")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("repMin")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("repMax")),
                        cursor.getString(cursor.getColumnIndexOrThrow("loadKind")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("loadMin")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("loadMax")),
                        cursor.getString(cursor.getColumnIndexOrThrow("loadUnit")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("restTimeSeconds")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("setCount")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("sortOrder")),
                    ),
                )
            }
        }

        db.execSQL("DROP TABLE exercise_templates")
        db.execSQL("ALTER TABLE exercise_templates_new RENAME TO exercise_templates")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_templates_circuitId ON exercise_templates(circuitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_templates_exerciseDefinitionId ON exercise_templates(exerciseDefinitionId)")

        db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN exerciseDefinitionId INTEGER")
        db.execSQL(
            """
            UPDATE exercise_sessions
            SET exerciseDefinitionId = (
                SELECT exerciseDefinitionId
                FROM exercise_templates
                WHERE exercise_templates.id = exercise_sessions.exerciseTemplateId
            )
            WHERE exerciseTemplateId IS NOT NULL
            """.trimIndent(),
        )
        backfillMissingSessionDefinitionIds(db, now)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sessions_exerciseDefinitionId ON exercise_sessions(exerciseDefinitionId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        backfillMissingSessionDefinitionIds(db, System.currentTimeMillis())
    }
}

private fun backfillMissingSessionDefinitionIds(db: SupportSQLiteDatabase, now: Long) {
    val definitionIds = mutableMapOf<String, Long>()
    db.query("SELECT id, normalizedName FROM exercise_definitions").use { cursor ->
        while (cursor.moveToNext()) {
            definitionIds[cursor.getString(cursor.getColumnIndexOrThrow("normalizedName"))] =
                cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        }
    }

    data class MissingSessionDefinition(
        val sessionExerciseId: Long,
        val name: String,
        val guidance: String,
    )

    val missingDefinitions = mutableListOf<MissingSessionDefinition>()
    db.query(
        """
        SELECT id, exerciseNameSnapshot, guidanceSnapshot
        FROM exercise_sessions
        WHERE exerciseDefinitionId IS NULL
        ORDER BY id ASC
        """.trimIndent(),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            missingDefinitions += MissingSessionDefinition(
                sessionExerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("exerciseNameSnapshot")),
                guidance = cursor.getString(cursor.getColumnIndexOrThrow("guidanceSnapshot")),
            )
        }
    }

    missingDefinitions.forEach { missingDefinition ->
        val normalizedName = normalizeExerciseName(missingDefinition.name)
        val definitionId = definitionIds.getOrPut(normalizedName) {
            db.execSQL(
                """
                INSERT INTO exercise_definitions(name, normalizedName, defaultGuidance, archived, createdAt, updatedAt)
                VALUES (?, ?, ?, 0, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    missingDefinition.name.trim(),
                    normalizedName,
                    missingDefinition.guidance.trim(),
                    now,
                    now,
                ),
            )
            db.query("SELECT last_insert_rowid()").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
        db.execSQL(
            "UPDATE exercise_sessions SET exerciseDefinitionId = ? WHERE id = ?",
            arrayOf<Any>(definitionId, missingDefinition.sessionExerciseId),
        )
    }
}
