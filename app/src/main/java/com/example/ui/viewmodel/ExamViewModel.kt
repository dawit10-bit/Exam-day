package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Exam
import com.example.data.ExamRepository
import com.example.data.StudyTask
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExamRepository
    
    // UI State variables
    val allExams: StateFlow<List<Exam>>
    val allTasks: StateFlow<List<StudyTask>>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.NEAREST) // NEAREST, SUBJECT, PRIORITY
    val sortBy = _sortBy.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExamRepository(database.examDao(), application)
        allExams = repository.allExams.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    enum class SortOption {
        NEAREST, SUBJECT, PRIORITY
    }

    // Filtered & Sorted Exams Flow
    val filteredExams: StateFlow<List<Exam>> = combine(allExams, searchQuery, sortBy) { list, query, sort ->
        var result = list.filter {
            it.title.contains(query, ignoreCase = true) || 
            it.subject.contains(query, ignoreCase = true) ||
            it.notes.contains(query, ignoreCase = true)
        }

        result = when (sort) {
            SortOption.NEAREST -> result.sortedBy { it.examDate }
            SortOption.SUBJECT -> result.sortedBy { it.subject.lowercase() }
            SortOption.PRIORITY -> result.sortedBy {
                when (it.priority) {
                    "High" -> 0
                    "Medium" -> 1
                    else -> 2
                }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Actions
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortBy.value = option
    }

    fun addExam(
        title: String,
        subject: String,
        examDate: Long,
        examTime: String,
        priority: String,
        notes: String,
        customReminderTime: Long?,
        initialTasks: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val exam = Exam(
                title = title,
                subject = subject,
                examDate = examDate,
                examTime = examTime,
                priority = priority,
                notes = notes,
                customReminderTime = customReminderTime
            )
            val insertedId = repository.insertExam(exam)
            initialTasks.forEach { taskName ->
                if (taskName.isNotBlank()) {
                    repository.insertTask(StudyTask(examId = insertedId, taskName = taskName))
                }
            }
        }
    }

    fun updateExam(exam: Exam) {
        viewModelScope.launch {
            repository.updateExam(exam)
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            repository.deleteExam(exam)
        }
    }

    // Tasks Management
    fun getTasksForExam(examId: Int): Flow<List<StudyTask>> = repository.getTasksForExam(examId)

    fun addTask(examId: Int, taskName: String) {
        viewModelScope.launch {
            if (taskName.isNotBlank()) {
                repository.insertTask(StudyTask(examId = examId, taskName = taskName))
            }
        }
    }

    fun toggleTaskCompletion(task: StudyTask) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: StudyTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Complete / Archive Exams
    fun toggleExamCompletion(exam: Exam) {
        viewModelScope.launch {
            repository.updateExam(exam.copy(isCompleted = !exam.isCompleted))
        }
    }

    // JSON Backup / Restore
    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportBackup()
                onResult(json)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun importBackup(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importBackup(json)
            onResult(success)
        }
    }

    // Export Exams To standard PDF (Offline, Native PdfDocument)
    fun exportExamsToPDF(onCompleted: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val exams = allExams.value
                val tasks = allTasks.value

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size Portrait
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Setup paints
                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                
                val subtitlePaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 14f
                    isAntiAlias = true
                }

                val bodyPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }

                val dividerPaint = Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                }

                var y = 50f
                canvas.drawText("EXAM DAYS COUNTER - SUMMARY", 50f, y, titlePaint)
                y += 25f
                val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                canvas.drawText("Generated on: $dateStr", 50f, y, subtitlePaint)
                y += 30f
                canvas.drawLine(50f, y, 545f, y, dividerPaint)
                y += 30f

                if (exams.isEmpty()) {
                    canvas.drawText("No upcoming exams registered yet.", 50f, y, bodyPaint)
                } else {
                    exams.forEachIndexed { index, exam ->
                        if (y > 750f) {
                            // Simple text limit wrap (could do multi-page, but for single overview export, keeping it within 1-2 pages is perfect. Let's write page 1 first)
                            return@forEachIndexed
                        }
                        
                        val examDateFormated = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exam.examDate))
                        val statusText = if (exam.isCompleted) "[COMPLETED] " else ""
                        canvas.drawText("${index + 1}. $statusText${exam.title} - ${exam.subject}", 50f, y, Paint(bodyPaint).apply { isFakeBoldText = true })
                        y += 18f
                        
                        canvas.drawText("Date: $examDateFormated at ${exam.examTime} | Priority: ${exam.priority}", 65f, y, subtitlePaint)
                        y += 18f
                        
                        if (exam.notes.isNotBlank()) {
                            canvas.drawText("Notes: ${exam.notes}", 65f, y, subtitlePaint)
                            y += 18f
                        }

                        // Print corresponding checklists
                        val examTasks = tasks.filter { it.examId == exam.id }
                        if (examTasks.isNotEmpty()) {
                            val completedCount = examTasks.count { it.isCompleted }
                            canvas.drawText("Study Tasks: $completedCount/${examTasks.size} completed", 65f, y, subtitlePaint)
                            y += 18f
                        }
                        
                        y += 15f
                        canvas.drawLine(50f, y, 545f, y, dividerPaint)
                        y += 25f
                    }
                }

                pdfDocument.finishPage(page)

                // Save PDF to downloads directory or external cache directory
                val context = getApplication<Application>()
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Exam_Days_Counter_Summary.pdf")
                val fos = FileOutputStream(file)
                pdfDocument.writeTo(fos)
                pdfDocument.close()
                fos.close()

                onCompleted(file)
            } catch (e: Exception) {
                e.printStackTrace()
                onCompleted(null)
            }
        }
    }
}
