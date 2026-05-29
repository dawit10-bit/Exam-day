package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val subject: String,
    val examDate: Long, // timestamp in ms
    val examTime: String, // format "HH:mm"
    val priority: String, // "High", "Medium", "Low"
    val notes: String = "",
    val isCompleted: Boolean = false,
    val customReminderTime: Long? = null // custom timestamp in ms (optional)
) : Serializable
