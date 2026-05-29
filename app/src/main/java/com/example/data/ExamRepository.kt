package com.example.data

import android.content.Context
import com.example.notification.ExamAlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class ExamRepository(
    private val examDao: ExamDao,
    private val context: Context
) {
    private val alarmScheduler = ExamAlarmScheduler(context)

    val allExams: Flow<List<Exam>> = examDao.getAllExams()
    val allTasks: Flow<List<StudyTask>> = examDao.getAllTasks()

    fun getExamByIdFlow(id: Int): Flow<Exam?> = examDao.getExamByIdFlow(id)
    fun getTasksForExam(examId: Int): Flow<List<StudyTask>> = examDao.getTasksForExam(examId)

    suspend fun getExamById(id: Int): Exam? = examDao.getExamById(id)

    suspend fun insertExam(exam: Exam): Int {
        val id = examDao.insertExam(exam).toInt()
        val savedExam = exam.copy(id = id)
        alarmScheduler.scheduleAlarmsForExam(savedExam)
        return id
    }

    suspend fun updateExam(exam: Exam) {
        examDao.updateExam(exam)
        alarmScheduler.scheduleAlarmsForExam(exam)
    }

    suspend fun deleteExam(exam: Exam) {
        alarmScheduler.cancelAlarmsForExam(exam)
        examDao.deleteTasksByExamId(exam.id)
        examDao.deleteExam(exam)
    }

    suspend fun insertTask(task: StudyTask) = examDao.insertTask(task)
    suspend fun updateTask(task: StudyTask) = examDao.updateTask(task)
    suspend fun deleteTask(task: StudyTask) = examDao.deleteTask(task)

    suspend fun clearAllData() {
        // Cancel all scheduled alarms before deletion
        val exams = allExams.first()
        exams.forEach { alarmScheduler.cancelAlarmsForExam(it) }
        examDao.deleteAllExams()
    }

    // Export complete backup as JSON String
    suspend fun exportBackup(): String {
        val exams = allExams.first()
        val tasks = allTasks.first()

        val rootJson = JSONObject()
        val examsArray = JSONArray()
        exams.forEach { exam ->
            val examObj = JSONObject().apply {
                put("id", exam.id)
                put("title", exam.title)
                put("subject", exam.subject)
                put("examDate", exam.examDate)
                put("examTime", exam.examTime)
                put("priority", exam.priority)
                put("notes", exam.notes)
                put("isCompleted", exam.isCompleted)
                put("customReminderTime", exam.customReminderTime ?: JSONObject.NULL)
            }
            examsArray.put(examObj)
        }
        rootJson.put("exams", examsArray)

        val tasksArray = JSONArray()
        tasks.forEach { task ->
            val taskObj = JSONObject().apply {
                put("id", task.id)
                put("examId", task.examId)
                put("taskName", task.taskName)
                put("isCompleted", task.isCompleted)
            }
            tasksArray.put(taskObj)
        }
        rootJson.put("tasks", tasksArray)

        return rootJson.toString(4)
    }

    // Clean restore database from JSON file
    suspend fun importBackup(backupJsonStr: String): Boolean {
        return try {
            val rootJson = JSONObject(backupJsonStr)
            val examsArray = rootJson.optJSONArray("exams") ?: JSONArray()
            val tasksArray = rootJson.optJSONArray("tasks") ?: JSONArray()

            // 1. Wipe existing state cleanly
            clearAllData()

            // 2. Re-import entries
            for (i in 0 until examsArray.length()) {
                val examObj = examsArray.getJSONObject(i)
                val exam = Exam(
                    title = examObj.getString("title"),
                    subject = examObj.getString("subject"),
                    examDate = examObj.getLong("examDate"),
                    examTime = examObj.getString("examTime"),
                    priority = examObj.getString("priority"),
                    notes = examObj.optString("notes", ""),
                    isCompleted = examObj.optBoolean("isCompleted", false),
                    customReminderTime = if (examObj.isNull("customReminderTime")) null else examObj.getLong("customReminderTime")
                )
                
                val newId = examDao.insertExam(exam).toInt()
                val savedExam = exam.copy(id = newId)
                alarmScheduler.scheduleAlarmsForExam(savedExam)
                
                // Map the original old tasks referencing oldExamId to our newly generated exam ID
                val oldExamId = examObj.optInt("id", -1)
                if (oldExamId != -1) {
                    for (j in 0 until tasksArray.length()) {
                        val taskObj = tasksArray.getJSONObject(j)
                        val taskExamId = taskObj.getInt("examId")
                        if (taskExamId == oldExamId) {
                            val task = StudyTask(
                                examId = newId,
                                taskName = taskObj.getString("taskName"),
                                isCompleted = taskObj.optBoolean("isCompleted", false)
                            )
                            examDao.insertTask(task)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
