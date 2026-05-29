package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_tasks",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StudyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val examId: Int,
    val taskName: String,
    val isCompleted: Boolean = false
)
