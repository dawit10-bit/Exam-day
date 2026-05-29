package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.Exam

class ExamAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleAlarmsForExam(exam: Exam) {
        // Cancel existing pending alarms to avoid duplicating them
        cancelAlarmsForExam(exam)

        if (exam.isCompleted) return // Do not notify for already completed exams

        val offsetsInDays = listOf(30, 14, 7, 3, 1, 0)
        val currentTime = System.currentTimeMillis()

        offsetsInDays.forEachIndexed { index, days ->
            val alarmTimeMs = exam.examDate - (days * 24L * 60 * 60 * 1000)
            
            if (alarmTimeMs > currentTime) {
                val label = when (days) {
                    0 -> "is TODAY! Good luck!"
                    1 -> "is TOMORROW! Keep up the final review."
                    else -> "is in $days days!"
                }
                val message = "Your exam \"${exam.title}\" $label"
                scheduleAlarm(exam.id, alarmTimeMs, index, exam.title, message)
            }
        }

        // Custom Reminder (If set and in the future)
        exam.customReminderTime?.let { customTime ->
            if (customTime > currentTime) {
                val message = "Custom Study Reminder for \"${exam.title}\"!"
                scheduleAlarm(exam.id, customTime, 99, exam.title, message)
            }
        }
    }

    private fun scheduleAlarm(examId: Int, triggerAtMs: Long, requestCodeOffset: Int, examTitle: String, message: String) {
        if (alarmManager == null) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("exam_id", examId)
            putExtra("exam_title", examTitle)
            putExtra("reminder_message", message)
        }

        // Create a unique request code for each specific offset of this examId
        val requestCode = examId * 100 + requestCodeOffset

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelAlarmsForExam(exam: Exam) {
        if (alarmManager == null) return

        val offsetsInDays = listOf(30, 14, 7, 3, 1, 0)
        offsetsInDays.forEachIndexed { index, _ ->
            val requestCode = exam.id * 100 + index
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        // Delete Custom Reminder
        val requestCode = exam.id * 100 + 99
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
