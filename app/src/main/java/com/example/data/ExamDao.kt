package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    // Exams
    @Query("SELECT * FROM exams ORDER BY examDate ASC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getExamById(id: Int): Exam?

    @Query("SELECT * FROM exams WHERE id = :id")
    fun getExamByIdFlow(id: Int): Flow<Exam?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Delete
    suspend fun deleteExam(exam: Exam)

    // Study Tasks
    @Query("SELECT * FROM study_tasks WHERE examId = :examId ORDER BY id ASC")
    fun getTasksForExam(examId: Int): Flow<List<StudyTask>>

    @Query("SELECT * FROM study_tasks")
    fun getAllTasks(): Flow<List<StudyTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTask): Long

    @Update
    suspend fun updateTask(task: StudyTask)

    @Delete
    suspend fun deleteTask(task: StudyTask)

    @Query("DELETE FROM study_tasks WHERE examId = :examId")
    suspend fun deleteTasksByExamId(examId: Int)

    @Query("DELETE FROM exams")
    suspend fun deleteAllExams()
}
