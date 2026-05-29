package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val examId = intent.getIntExtra("exam_id", 0)
        val examTitle = intent.getStringExtra("exam_title") ?: "Exam Reminder"
        val message = intent.getStringExtra("reminder_message") ?: "Your exam is approaching!"
        
        // Use a unique notification ID based on examId + a hash of message to avoid over-writes
        val notificationId = examId + message.hashCode()

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(
            id = notificationId,
            title = examTitle,
            message = message
        )
    }
}
