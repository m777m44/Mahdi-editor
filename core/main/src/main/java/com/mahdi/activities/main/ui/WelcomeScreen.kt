package com.mahdi.activities.main.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.main.MainActivity
import com.mahdi.commands.ActionContext
import com.mahdi.commands.Command
import com.mahdi.commands.CommandProvider
import com.mahdi.drawer.DrawerViewModel
import com.mahdi.file.FileWrapper
import com.mahdi.filetree.FileTreeTab
import com.mahdi.filetree.getAppropriateName
import com.mahdi.icons.MahdiIcon
import com.mahdi.motivation.Motivation
import com.mahdi.motivation.MotivationState
import com.mahdi.resources.drawables
import com.mahdi.session.RecentFile
import com.mahdi.session.RecentFiles
import com.mahdi.tabs.editor.EditorTab
import com.mahdi.utils.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(drawerViewModel: DrawerViewModel) {
    val drawerTabs by drawerViewModel.drawerTabs.collectAsStateWithLifecycle()
    val currentDrawerTabIndex by drawerViewModel.currentDrawerTabIndex.collectAsStateWithLifecycle()

    val currentDrawerTab = drawerTabs.getOrNull(currentDrawerTabIndex)
    val currentProject = (currentDrawerTab as? FileTreeTab)?.root

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    // ————— «آخر ملف مفتوح» —————
    var lastFile by remember { mutableStateOf<RecentFile?>(null) }
    var recentFiles by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var dismissed by remember { mutableStateOf(false) }

    suspend fun reloadRecent() {
        val list = RecentFiles.recent(6)
        val openPaths =
            MainActivity.instance?.viewModel?.tabs
                ?.filterIsInstance<EditorTab>()
                ?.mapNotNull { it.file?.getAbsolutePath() }
                ?.toSet()
                ?: emptySet()
        recentFiles = list
        lastFile = list.firstOrNull { it.path !in openPaths }
    }

    LaunchedEffect(Unit) {
        Motivation.refresh()
        reloadRecent()
    }
    val motivation by Motivation.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp).widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(drawables.mahdi_editor),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(56.dp).padding(bottom = 12.dp),
            )

            currentProject?.let { project ->
                Text(
                    text = project.getAppropriateName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = project.getAbsolutePath(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
            }

            Spacer(Modifier.height(18.dp))

            MotivationCard(
                state = motivation,
                isArabic = isArabic,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = lastFile != null && !dismissed,
                enter = slideInVertically { -it / 3 } + fadeIn(),
                exit = fadeOut(),
            ) {
                lastFile?.let { file ->
                    Spacer(Modifier.height(12.dp))
                    ResumeCard(
                        file = file,
                        isArabic = isArabic,
                        onResume = {
                            scope.launch {
                                runCatching {
                                    MainActivity.instance?.viewModel?.editorManager?.openFile(
                                        FileWrapper(File(file.path))
                                    )
                                }
                                    .onFailure { toast(it.message ?: "تعذّر فتح الملف") }
                            }
                        },
                        onDismiss = {
                            dismissed = true
                            scope.launch {
                                RecentFiles.forget(file.path)
                                reloadRecent()
                            }
                        },
                    )
                }
            }

            if (recentFiles.size > 1) {
                Spacer(Modifier.height(12.dp))
                RecentFilesRow(
                    files = recentFiles,
                    isArabic = isArabic,
                    onOpen = { path ->
                        scope.launch {
                            runCatching {
                                MainActivity.instance?.viewModel?.editorManager?.openFile(FileWrapper(File(path)))
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(22.dp))

            WelcomeActions(
                commands =
                    listOfNotNull(
                        CommandProvider.BrowseProjectCommand,
                        CommandProvider.NewFileCommand,
                        CommandProvider.SearchFileFolderCommand,
                        CommandProvider.getForId("editor.run"),
                        CommandProvider.getForId("global.terminal"),
                        CommandProvider.getForId("global.store"),
                        CommandProvider.DocumentationCommand,
                        CommandProvider.SettingsCommand,
                    )
            )
        }
    }
}

/** بطاقة التحفيز: السلسلة اليومية + هدف اليوم + عدد الإنجازات. */
@Composable
fun MotivationCard(state: MotivationState, isArabic: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val progress by animateFloatAsState(state.goalProgress, label = "goal")

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🔥", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "${state.streakDays} يوم متتالٍ" else "${state.streakDays}-day streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            if (isArabic) {
                                "أفضل سلسلة: ${state.bestStreak} يوم · إنجازات: ${state.unlocked.size}/${Motivation.catalog.size}"
                            } else {
                                "Best: ${state.bestStreak} days · Achievements: ${state.unlocked.size}/${Motivation.catalog.size}"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (state.goalReached) "🌟" else "${state.todaySaves}/${state.dailyGoal}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text =
                    if (state.goalReached) {
                        if (isArabic) "أنجزت هدف اليوم — أحسنت!" else "Daily goal complete — well done!"
                    } else {
                        if (isArabic) {
                            "هدف اليوم: ${state.todaySaves}/${state.dailyGoal} حفظ · ${state.todayLines} سطر"
                        } else {
                            "Today: ${state.todaySaves}/${state.dailyGoal} saves · ${state.todayLines} lines"
                        }
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** بطاقة «استئناف من حيث توقفت». */
@Composable
private fun ResumeCard(
    file: RecentFile,
    isArabic: Boolean,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "استئناف من حيث توقفت" else "Resume where you left off",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    buildString {
                        append(
                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(file.lastOpened))
                        )
                        if (file.line > 0) append(" · ${if (isArabic) "السطر" else "line"} ${file.line + 1}")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "متابعة" else "Continue")
                }
                TextButton(onClick = onDismiss) {
                    Text(if (isArabic) "بدء جديد" else "Start fresh")
                }
            }
        }
    }
}

/** شريط الملفات الأخيرة. */
@Composable
private fun RecentFilesRow(files: List<RecentFile>, isArabic: Boolean, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isArabic) "آخر الملفات" else "Recent files",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(files, key = { it.path }) { file ->
                Card(
                    modifier = Modifier.widthIn(max = 180.dp).clickable { onOpen(file.path) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeActions(commands: List<Command>) {
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        commands.forEach { command -> WelcomeActionButton(command = command, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun WelcomeActionButton(command: Command, modifier: Modifier = Modifier) {
    if (!command.isSupported() || !command.isEnabled()) return
    val activity = LocalActivity.current

    TextButton(modifier = modifier, onClick = { activity?.let { command.execute(ActionContext(it)) } }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MahdiIcon(
                icon = command.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = command.getLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
