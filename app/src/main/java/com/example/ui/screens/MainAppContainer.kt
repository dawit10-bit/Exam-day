package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Exam
import com.example.data.StudyTask
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExamViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    SPLASH, MAIN
}

enum class MainTab {
    COUNTDOWN, CALENDAR, PLANNER, STATS, SETTINGS
}

@Composable
fun MainAppContainer(viewModel: ExamViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var selectedTab by remember { mutableStateOf(MainTab.COUNTDOWN) }
    
    // Slide-up sheet models for adding & editing
    var isAddSheetOpen by remember { mutableStateOf(false) }
    var examToEdit by remember { mutableStateOf<Exam?>(null) }
    var selectedExamForDetail by remember { mutableStateOf<Exam?>(null) }

    // Automated running timer ticker (updates every second for precision countdown display)
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    when (currentScreen) {
        AppScreen.SPLASH -> {
            SplashScreen(onFinished = { currentScreen = AppScreen.MAIN })
        }
        AppScreen.MAIN -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        AppleFrostedNavigationBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "tab_animation"
                        ) { tab ->
                            when (tab) {
                                MainTab.COUNTDOWN -> {
                                    CountdownTab(
                                        viewModel = viewModel,
                                        now = currentTimeMillis,
                                        onAddClick = { isAddSheetOpen = true },
                                        onEditExam = { examToEdit = it },
                                        onViewDetail = { selectedExamForDetail = it }
                                    )
                                }
                                MainTab.CALENDAR -> {
                                    CalendarTab(
                                        viewModel = viewModel,
                                        onExamClick = { selectedExamForDetail = it }
                                    )
                                }
                                MainTab.PLANNER -> {
                                    PlannerTab(
                                        viewModel = viewModel
                                    )
                                }
                                MainTab.STATS -> {
                                    StatsTab(
                                        viewModel = viewModel
                                    )
                                }
                                MainTab.SETTINGS -> {
                                    SettingsTab(
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }

                        // Floating Add Button on the Home/Countdown screen
                        if (selectedTab == MainTab.COUNTDOWN) {
                            FloatingActionButton(
                                onClick = { isAddSheetOpen = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .testTag("add_exam_fab"),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Exam",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Add Exam slide-up modal bottom sheet
                if (isAddSheetOpen) {
                    AddEditExamDialog(
                        onDismiss = { isAddSheetOpen = false },
                        onSave = { title, sub, date, time, priority, notes, reminder ->
                            viewModel.addExam(title, sub, date, time, priority, notes, reminder)
                            isAddSheetOpen = false
                        }
                    )
                }

                // Edit Exam dialog
                if (examToEdit != null) {
                    AddEditExamDialog(
                        existingExam = examToEdit,
                        onDismiss = { examToEdit = null },
                        onSave = { title, sub, date, time, priority, notes, reminder ->
                            examToEdit?.let {
                                viewModel.updateExam(
                                    it.copy(
                                        title = title,
                                        subject = sub,
                                        examDate = date,
                                        examTime = time,
                                        priority = priority,
                                        notes = notes,
                                        customReminderTime = reminder
                                    )
                                )
                            }
                            examToEdit = null
                        }
                    )
                }

                // Exam Detail sheet (Study Planner & Counts detail)
                if (selectedExamForDetail != null) {
                    ExamDetailDialog(
                        exam = selectedExamForDetail!!,
                        viewModel = viewModel,
                        onDismiss = { selectedExamForDetail = null }
                    )
                }
            }
        }
    }
}

// ------------------------------------
// 1. SPLASH SCREEN
// ------------------------------------
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var animationTrigger by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_anim"
    )

    val opacity by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(1200),
        label = "opacity_anim"
    )

    LaunchedEffect(Unit) {
        animationTrigger = true
        delay(2200) // Beautiful splash duration
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D11), Color(0xFF020202))
                )
            )
            .clickable { onFinished() } // Allow skip splash
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant Apple-style icon: calendar background with countdown "7"
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E1E2C), Color(0xFF0F0F14))
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .border(1.5.dp, Color(0xFF3A3A3D).copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = "Logo Calendar",
                        tint = AppleBlueDark,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "7",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Exam Days Counter",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.scale(scale)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Premium Countdown & Planner",
                fontSize = 15.sp,
                color = Color.Gray.copy(alpha = opacity),
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

// ------------------------------------
// SLEEK APPLE BOTTOM BAR
// ------------------------------------
@Composable
fun AppleFrostedNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF101012).copy(alpha = 0.85f) else Color(0xFFF9F9F9).copy(alpha = 0.90f)
    
    Column {
        Divider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationBarItem(
                selected = selectedTab == MainTab.COUNTDOWN,
                onClick = { onTabSelected(MainTab.COUNTDOWN) },
                icon = Icons.Rounded.HourglassTop,
                label = "Counter",
                tag = "tab_countdown"
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.CALENDAR,
                onClick = { onTabSelected(MainTab.CALENDAR) },
                icon = Icons.Rounded.CalendarToday,
                label = "Calendar",
                tag = "tab_calendar"
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.PLANNER,
                onClick = { onTabSelected(MainTab.PLANNER) },
                icon = Icons.Rounded.AssignmentTurnedIn,
                label = "Planner",
                tag = "tab_planner"
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.STATS,
                onClick = { onTabSelected(MainTab.STATS) },
                icon = Icons.Rounded.Analytics,
                label = "Stats",
                tag = "tab_stats"
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.SETTINGS,
                onClick = { onTabSelected(MainTab.SETTINGS) },
                icon = Icons.Rounded.SettingsBackupRestore,
                label = "Backup",
                tag = "tab_settings"
            )
        }
    }
}

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    tag: String
) {
    val activeColor = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight
    val inactiveColor = Color.Gray

    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(if (selected) 1.15f else 1f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else inactiveColor
            )
        }
    }
}

// ------------------------------------
// TAB 1: COUNTDOWN (HOME VIEW)
// ------------------------------------
@Composable
fun CountdownTab(
    viewModel: ExamViewModel,
    now: Long,
    onAddClick: () -> Unit,
    onEditExam: (Exam) -> Unit,
    onViewDetail: (Exam) -> Unit
) {
    val exams by viewModel.filteredExams.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    val currentOnwardExams = exams.filter { !it.isCompleted }
    val archExams = exams.filter { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Large iOS Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Exam Days Counter",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                val todayText = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
                Text(
                    text = todayText,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${currentOnwardExams.size} Exams Left",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Nearest Exam Highlighted Hero Card
        if (currentOnwardExams.isNotEmpty()) {
            val nearestExam = currentOnwardExams.first()
            val themeProgress = getExamProgress(nearestExam.id, allTasks)
            
            NearestHeroCard(
                exam = nearestExam,
                now = now,
                progress = themeProgress,
                onClick = { onViewDetail(nearestExam) }
            )
        } else if (exams.isEmpty()) {
            // Elegant Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = "Empty",
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No exams registered yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the '+' button below to add your first exam",
                        fontSize = 14.sp,
                        color = Color.Gray.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        if (exams.isNotEmpty()) {
            // Search Input Block
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("exam_search_field"),
                placeholder = { Text("Search title, subject, notes...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                    focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF121214) else Color(0xFFF2F2F5),
                    unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF121214) else Color(0xFFF2F2F5)
                )
            )

            // Filtering Options Box
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort by:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )
                SortingChip(
                    label = "Nearest",
                    selected = sortBy == ExamViewModel.SortOption.NEAREST,
                    onClick = { viewModel.setSortOption(ExamViewModel.SortOption.NEAREST) }
                )
                SortingChip(
                    label = "Subject",
                    selected = sortBy == ExamViewModel.SortOption.SUBJECT,
                    onClick = { viewModel.setSortOption(ExamViewModel.SortOption.SUBJECT) }
                )
                SortingChip(
                    label = "Priority",
                    selected = sortBy == ExamViewModel.SortOption.PRIORITY,
                    onClick = { viewModel.setSortOption(ExamViewModel.SortOption.PRIORITY) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Exams List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ongoing header if archive exists
                if (archExams.isNotEmpty()) {
                    item {
                        Text(
                            text = "ACTIVE EXAMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                val listToShow = currentOnwardExams.drop(if (currentOnwardExams.isNotEmpty()) 1 else 0)
                if (listToShow.isEmpty() && currentOnwardExams.isNotEmpty() && archExams.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Only 1 nearest exam left. Good prep! ⚡",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                items(currentOnwardExams) { exam ->
                    // Skip the hero exam in list unless query matches (to prevent duplicate on standard view)
                    if (exam == currentOnwardExams.first() && searchQuery.isEmpty()) return@items

                    val examProgress = getExamProgress(exam.id, allTasks)
                    ExamCard(
                        exam = exam,
                        now = now,
                        progress = examProgress,
                        onCardClick = { onViewDetail(exam) },
                        onEdit = { onEditExam(exam) },
                        onDelete = { viewModel.deleteExam(exam) },
                        onCompleteToggle = { viewModel.toggleExamCompletion(exam) }
                    )
                }

                if (archExams.isNotEmpty()) {
                    item {
                        Text(
                            text = "COMPLETED ARCHIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleGreen,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(archExams) { exam ->
                        ExamCard(
                            exam = exam,
                            now = now,
                            progress = 1.0f,
                            onCardClick = { onViewDetail(exam) },
                            onEdit = { onEditExam(exam) },
                            onDelete = { viewModel.deleteExam(exam) },
                            onCompleteToggle = { viewModel.toggleExamCompletion(exam) }
                        )
                    }
                }
                
                // Add padding at the bottom of the scroll
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun SortingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val activeBg = if (isSystemInDarkTheme()) AppleBlueDark.copy(alpha = 0.2f) else AppleBlueLight.copy(alpha = 0.15f)
    val activeBorder = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight
    val activeText = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight

    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .background(if (selected) activeBg else Color.Transparent, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) activeBorder else Color.Gray.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeText else Color.Gray
        )
    }
}

// ------------------------------------
// NEAREST HERO CARD COMPONENT
// ------------------------------------
@Composable
fun NearestHeroCard(
    exam: Exam,
    now: Long,
    progress: Float,
    onClick: () -> Unit
) {
    val durationMs = exam.examDate - now
    val countdown = calculateCountdown(durationMs)
    val priorityColor = getPriorityColor(exam.priority)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isSystemInDarkTheme()) {
                        listOf(Color(0xFF2C2C32), Color(0xFF141416))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFF0F4FC))
                    }
                )
            )
            .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
            .testTag("hero_exam_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(priorityColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = exam.subject.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(priorityColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${exam.priority} Priority",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = priorityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = exam.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateStr = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(exam.examDate))
            Text(
                text = "$dateStr @ ${exam.examTime}",
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // THE GIANT COUNTDOWN TIMER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                val daysLeft = (durationMs / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                Text(
                    text = "$daysLeft",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = priorityColor,
                    lineHeight = 54.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DAYS LEFT",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Hours, Minutes Ticker Breakdown row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownTimeBlock(value = countdown.months, label = "mo")
                CountdownTimeBlock(value = countdown.weeks, label = "wk")
                CountdownTimeBlock(value = countdown.daysLeftInWeek, label = "day")
                CountdownTimeBlock(value = countdown.hours, label = "hr")
                CountdownTimeBlock(value = countdown.minutes, label = "min")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Revision completion bars (animated progress)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Revision Progress",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            
            // Premium linear progress indicators
            val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(500), label = "hero_progress")
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray.copy(alpha = 0.15f)
            )

            if (exam.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = exam.notes,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(
                        Color.Gray.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    ).padding(8.dp).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CountdownTimeBlock(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
    }
}

// ------------------------------------
// REGULAR LIST EXAM CARD COMPONENT
// ------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExamCard(
    exam: Exam,
    now: Long,
    progress: Float,
    onCardClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCompleteToggle: () -> Unit
) {
    val durationMs = exam.examDate - now
    val daysRemaining = (durationMs / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
    val priorityColor = getPriorityColor(exam.priority)
    var isExpandedOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            )
            .border(1.dp, Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = { isExpandedOptions = !isExpandedOptions }
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (exam.isCompleted) AppleGreen else priorityColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = exam.subject.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = exam.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (exam.isCompleted) Color.Gray else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (exam.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val formattedDate = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(exam.examDate))
                Text(
                    text = "$formattedDate • ${exam.examTime}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Mini Right Side Countdown Box
            Box(
                modifier = Modifier
                    .background(
                        if (exam.isCompleted) AppleGreen.copy(alpha = 0.12f) else priorityColor.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (exam.isCompleted) "✓" else "$daysRemaining",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (exam.isCompleted) AppleGreen else priorityColor
                    )
                    Text(
                        text = if (exam.isCompleted) "Done" else "Days",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (exam.isCompleted) AppleGreen else priorityColor
                    )
                }
            }
        }

        // Progress bar mini meter
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val animatedProg by animateFloatAsState(targetValue = progress, label = "bar_progress")
            LinearProgressIndicator(
                progress = { animatedProg },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape),
                color = if (exam.isCompleted) AppleGreen else MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        // Expandable swift controls for iOS click feedback items
        AnimatedVisibility(visible = isExpandedOptions) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TextButton(onClick = onCompleteToggle) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (exam.isCompleted) Icons.Rounded.Close else Icons.Rounded.CheckCircle,
                                contentDescription = "Toggle Complete",
                                tint = if (exam.isCompleted) Color.Gray else AppleGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (exam.isCompleted) "Active" else "Complete",
                                color = if (exam.isCompleted) Color.Gray else AppleGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(onClick = onEdit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = AppleRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = AppleRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------
// TAB 2: CALENDAR VIEW
// ------------------------------------
@Composable
fun CalendarTab(
    viewModel: ExamViewModel,
    onExamClick: (Exam) -> Unit
) {
    val exams by viewModel.allExams.collectAsStateWithLifecycle()
    
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = remember(calendar) {
        val tempCal = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val day = tempCal.get(Calendar.DAY_OF_WEEK)
        // Convert to 0-indexed offset (0 for Sunday or Monday depending on setting, sunday here = day - 1)
        day - 1
    }

    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)

    // Filter exams on selected day
    val examsOnSelectedDay = exams.filter { exam ->
        val examCal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
        examCal.get(Calendar.YEAR) == selectedDayCalendar.get(Calendar.YEAR) &&
        examCal.get(Calendar.MONTH) == selectedDayCalendar.get(Calendar.MONTH) &&
        examCal.get(Calendar.DAY_OF_MONTH) == selectedDayCalendar.get(Calendar.DAY_OF_MONTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Calendar View",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Find where exams cluster on your scheduled calendar monthly",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Month Selector row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newCal = calendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        calendar = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Prev Month", tint = MaterialTheme.colorScheme.primary)
                    }

                    Text(
                        text = monthName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    IconButton(onClick = {
                        val newCal = calendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        calendar = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next Month", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days of week header
                val weekHeader = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekHeader.forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Monthly dates matrix grid view
                val totalSlots = firstDayOfWeek + daysInMonth
                val totalRows = (totalSlots + 6) / 7
                
                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in 0 until 7) {
                            val slotIdx = row * 7 + col
                            val dayNum = slotIdx - firstDayOfWeek + 1
                            val isValid = dayNum in 1..daysInMonth

                            if (isValid) {
                                // Check if this day has exams scheduled
                                val slotDayCal = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNum) }
                                val hasExams = exams.any { exam ->
                                    val examCal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
                                    examCal.get(Calendar.YEAR) == slotDayCal.get(Calendar.YEAR) &&
                                    examCal.get(Calendar.MONTH) == slotDayCal.get(Calendar.MONTH) &&
                                    examCal.get(Calendar.DAY_OF_MONTH) == slotDayCal.get(Calendar.DAY_OF_MONTH)
                                }

                                val isSel = selectedDayCalendar.get(Calendar.YEAR) == slotDayCal.get(Calendar.YEAR) &&
                                            selectedDayCalendar.get(Calendar.MONTH) == slotDayCal.get(Calendar.MONTH) &&
                                            selectedDayCalendar.get(Calendar.DAY_OF_MONTH) == dayNum

                                val activeMark = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSel -> activeMark
                                                hasExams -> activeMark.copy(alpha = 0.15f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            selectedDayCalendar = slotDayCal
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 14.sp,
                                            fontWeight = if (isSel || hasExams) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSel -> Color.White
                                                hasExams -> activeMark
                                                else -> MaterialTheme.colorScheme.onBackground
                                            }
                                        )
                                        if (hasExams && !isSel) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .background(AppleRed, CircleShape)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Day Exams Detail List
        val selectedFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(selectedDayCalendar.time)
        Text(
            text = "Exams on $selectedFormat",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (examsOnSelectedDay.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No exams scheduled on this day.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(examsOnSelectedDay) { exam ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExamClick(exam) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        ),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = exam.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${exam.subject.uppercase()} • ${exam.examTime} | Priority: ${exam.priority}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "View detail",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------
// TAB 3: STUDY PLANNER checkboard list
// ------------------------------------
@Composable
fun PlannerTab(viewModel: ExamViewModel) {
    val exams by viewModel.allExams.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    var activeExamIndex by remember { mutableStateOf<Int?>(null) }
    var newTaskNames by remember { mutableStateOf(mutableMapOf<Int, String>()) }

    val onwardExams = exams.filter { !it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Study Planner",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Outline core revision units per exam and track progress",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (onwardExams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add some upcoming active exams to configure study checklists!",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(onwardExams) { exam ->
                    val examTasks = allTasks.filter { it.examId == exam.id }
                    val progress = getExamProgress(exam.id, allTasks)
                    val isExpanded = activeExamIndex == exam.id

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        ),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Click row to Expand
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeExamIndex = if (isExpanded) null else exam.id
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exam.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = exam.subject,
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Expand",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Revision Percentage bar matching user spec
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Revision Checklist",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}% completed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress_meter")
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Gray.copy(alpha = 0.15f)
                            )

                            // Checklist tasks block visible when expanded
                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(color = Color.Gray.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Display Tasks items
                                    if (examTasks.isEmpty()) {
                                        Text(
                                            text = "No study tasks listed yet. Fill in a goal below!",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        examTasks.forEach { task ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = task.isCompleted,
                                                    onCheckedChange = { viewModel.toggleTaskCompletion(task) },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                                Text(
                                                    text = task.taskName,
                                                    fontSize = 14.sp,
                                                    color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.onBackground,
                                                    textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.deleteTask(task) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Delete task",
                                                        tint = AppleRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Fast Task Inline Add Box
                                    val currentTextValue = newTaskNames[exam.id] ?: ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = currentTextValue,
                                            onValueChange = {
                                                val m = newTaskNames.toMutableMap()
                                                m[exam.id] = it
                                                newTaskNames = m
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp),
                                            placeholder = { Text("e.g. Chapter 1 review...", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                viewModel.addTask(exam.id, currentTextValue)
                                                val m = newTaskNames.toMutableMap()
                                                m[exam.id] = ""
                                                newTaskNames = m
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Add", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to extract progress percentage safely
fun getExamProgress(examId: Int, allTasks: List<StudyTask>): Float {
    val filtered = allTasks.filter { it.examId == examId }
    if (filtered.isEmpty()) return 0.0f
    val completed = filtered.count { it.isCompleted }
    return completed.toFloat() / filtered.size
}

// ------------------------------------
// TAB 4: STATISTICS DASHBOARD
// ------------------------------------
@Composable
fun StatsTab(viewModel: ExamViewModel) {
    val exams by viewModel.allExams.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    val totalCount = exams.size
    val completedCount = exams.count { it.isCompleted }
    val upcomingCount = exams.count { !it.isCompleted }

    // Calculating average days left for onboarding exams
    val upcomingExamsList = exams.filter { !it.isCompleted }
    val avgDaysString = remember(upcomingExamsList) {
        if (upcomingExamsList.isEmpty()) "0"
        else {
            val totalLeftMs = upcomingExamsList.sumOf { (it.examDate - System.currentTimeMillis()).coerceAtLeast(0) }
            val avgMs = totalLeftMs / upcomingExamsList.size
            val days = avgMs / (24L * 60 * 60 * 1000)
            "$days"
        }
    }

    // Task completions analytics
    val totalTasks = allTasks.size
    val completedTasks = allTasks.count { it.isCompleted }
    val taskCompletionPct = remember(totalTasks, completedTasks) {
        if (totalTasks == 0) 0f
        else completedTasks.toFloat() / totalTasks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Statistics",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Overview of your exam preparedness and counters",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Large Premium Arc/Circle task complete rate
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REVISION COMPLETION RATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { taskCompletionPct },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 10.dp,
                        color = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight,
                        trackColor = Color.Gray.copy(alpha = 0.15f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(taskCompletionPct * 100).toInt()}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$completedTasks/$totalTasks tasks",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Highlight Grid Counters
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                SingleStatBox(label = "Total Exams", count = "$totalCount", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                SingleStatBox(label = "Upcoming", count = "$upcomingCount", color = AppleOrange)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                SingleStatBox(label = "Completed", count = "$completedCount", color = AppleGreen)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                SingleStatBox(label = "Avg Days Till", count = avgDaysString, color = if (isSystemInDarkTheme()) AppleBlueDark else AppleBlueLight)
            }
        }
    }
}

@Composable
fun SingleStatBox(label: String, count: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        ),
        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

// ------------------------------------
// TAB 5: BACKUP & RESTORE / EXPORTER SETTINGS
// ------------------------------------
@Composable
fun SettingsTab(viewModel: ExamViewModel) {
    val context = LocalContext.current
    var jsonBackupRawText by remember { mutableStateOf("") }
    var isBackupReadyDisplay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Data & Backup",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Secure local backup/restore and professional layout exporter",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        // PDF EXPORT ROW
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            ),
            border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Exams to PDF",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Generate a formatted print summary of study targets offline.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        viewModel.exportExamsToPDF { file ->
                            if (file != null && file.exists()) {
                                Toast.makeText(context, "PDF saved to downloads directory!", Toast.LENGTH_LONG).show()
                                try {
                                    // Trigger simple view intent
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.fromFile(file), "application/pdf")
                                        flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open PDF"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No PDF viewer available inside", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Failed to create PDF offline.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // JSON BACKUP SYSTEM
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            ),
            border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Offline SQLite Database Backup",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Extract your offline state as a JSON string to copy, or restore previous entries.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.exportBackup { json ->
                                if (json != null) {
                                    jsonBackupRawText = json
                                    isBackupReadyDisplay = true
                                    
                                    // Auto copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ExamCountdownBackup", json)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to extract backup.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("1. Code Backup", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (jsonBackupRawText.isNotBlank()) {
                                viewModel.importBackup(jsonBackupRawText) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Data success restore!", Toast.LENGTH_LONG).show()
                                        jsonBackupRawText = ""
                                        isBackupReadyDisplay = false
                                    } else {
                                        Toast.makeText(context, "Invalid JSON data structure.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please paste or generate a backup above.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleGreen)
                    ) {
                        Text("2. Run Restore", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Paste or view textbox
                OutlinedTextField(
                    value = jsonBackupRawText,
                    onValueChange = { jsonBackupRawText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    label = { Text("Backup Raw Text Container") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Paste restoration data block here...", fontSize = 11.sp) }
                )
            }
        }
    }
}

// ------------------------------------
// POPUP DIALOG: ADD & EDIT EXAM
// ------------------------------------
@Composable
fun AddEditExamDialog(
    existingExam: Exam? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String, String, Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingExam?.title ?: "") }
    var subject by remember { mutableStateOf(existingExam?.subject ?: "") }
    var examDateSelected by remember { mutableStateOf(existingExam?.examDate ?: System.currentTimeMillis()) }
    var examTimeStr by remember { mutableStateOf(existingExam?.examTime ?: "09:00") }
    var priority by remember { mutableStateOf(existingExam?.priority ?: "Medium") }
    var notes by remember { mutableStateOf(existingExam?.notes ?: "") }
    
    // Custom absolute study reminder time
    var customReminderTime by remember { mutableStateOf(existingExam?.customReminderTime) }

    val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(examDateSelected))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(enabled = false) {}, // Prevent clicks closing dialogue inside bounds
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1F) else Color(0xFFFFFFFF)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (existingExam == null) "New Countdown" else "Modifier Details",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Exam Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam / Test Name") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_title_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subject Name
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Physics, History)") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_subject_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Priority Row Picker
                Text("Select Priority", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val listOptions = listOf("High", "Medium", "Low")
                    listOptions.forEach { opt ->
                        val isSelected = priority == opt
                        val activeCol = getPriorityColor(opt)
                        Button(
                            onClick = { priority = opt },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) activeCol else Color.Gray.copy(alpha = 0.1f),
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        ) {
                            Text(opt, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pick Date row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Exam Date", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(formattedDate, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = examDateSelected }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    calendar.set(year, month, day)
                                    examDateSelected = calendar.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Date", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pick Time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Exam Start Time", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(examTimeStr, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val parts = examTimeStr.split(":")
                            val initHr = parts.getOrNull(0)?.toIntOrNull() ?: 9
                            val initMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, min ->
                                    examTimeStr = String.format("%02d:%02d", hour, min)
                                },
                                initHr,
                                initMin,
                                true
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Time", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Extra Reminder Config
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Reminders", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        val remLabel = if (customReminderTime != null) {
                            SimpleDateFormat("dd MMM @ HH:mm", Locale.getDefault()).format(Date(customReminderTime!!))
                        } else "None set"
                        Text(remLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val nowCal = Calendar.getInstance()
                            DatePickerDialog(context, { _, year, month, day ->
                                nowCal.set(Calendar.YEAR, year)
                                nowCal.set(Calendar.MONTH, month)
                                nowCal.set(Calendar.DAY_OF_MONTH, day)
                                
                                TimePickerDialog(context, { _, hr, mn ->
                                    nowCal.set(Calendar.HOUR_OF_DAY, hr)
                                    nowCal.set(Calendar.MINUTE, mn)
                                    customReminderTime = nowCal.timeInMillis
                                }, 12, 0, true).show()
                            }, nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH), nowCal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Add Alert", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Extra Notes Form
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Exams Notes & Venue (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Save Actions Block
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.onBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && subject.isNotBlank()) {
                                onSave(title, subject, examDateSelected, examTimeStr, priority, notes, customReminderTime)
                            } else {
                                Toast.makeText(context, "Please fill in Name and Subject titles.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("dialog_save_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Details")
                    }
                }
            }
        }
    }
}

// ------------------------------------
// BOTTOM SHEET DIALOG: EXAMS DETAIL DRAWER
// ------------------------------------
@Composable
fun ExamDetailDialog(
    exam: Exam,
    viewModel: ExamViewModel,
    onDismiss: () -> Unit
) {
    val examTasks by viewModel.getTasksForExam(exam.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var newTaskText by remember { mutableStateOf("") }
    val formattedDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(exam.examDate))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF161619) else Color(0xFFFFFFFF)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                // Header Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exam.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = exam.subject.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Info
                Text("Schedule: $formattedDate at ${exam.examTime}", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text("Priority Level: ${exam.priority}", fontSize = 13.sp, color = getPriorityColor(exam.priority), fontWeight = FontWeight.Bold)
                
                if (exam.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Notes:\n${exam.notes}",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Task Checklist inner component
                Text("REVISION TARGET CHIPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Target text input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("detail_task_input"),
                        placeholder = { Text("e.g. Practice Chapter MCQ questions...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newTaskText.isNotBlank()) {
                                viewModel.addTask(exam.id, newTaskText)
                                newTaskText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("detail_task_add")
                    ) {
                        Text("Add")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tasks list scrolling container
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(examTasks) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSystemInDarkTheme()) Color(0xFF232328) else Color(0xFFF2F2F7)
                            ),
                            border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { viewModel.toggleTaskCompletion(task) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = task.taskName,
                                    fontSize = 13.sp,
                                    color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.onBackground,
                                    textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.deleteTask(task) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete task", tint = AppleRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------
// UTILITY HELPERS
// ------------------------------------
data class CountdownDisplay(
    val months: Long,
    val weeks: Long,
    val daysLeftInWeek: Long,
    val hours: Long,
    val minutes: Long
)

fun calculateCountdown(durationMs: Long): CountdownDisplay {
    if (durationMs <= 0) return CountdownDisplay(0, 0, 0, 0, 0)
    
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    val months = days / 30
    val remainingDaysAfterMonths = days % 30
    val weeks = remainingDaysAfterMonths / 7
    val daysLeftInWeek = remainingDaysAfterMonths % 7

    val remainingHours = hours % 24
    val remainingMinutes = minutes % 60

    return CountdownDisplay(
        months = months,
        weeks = weeks,
        daysLeftInWeek = daysLeftInWeek,
        hours = remainingHours,
        minutes = remainingMinutes
    )
}

fun getPriorityColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high" -> AppleRed
        "medium" -> AppleOrange
        else -> AppleGreen
    }
}
