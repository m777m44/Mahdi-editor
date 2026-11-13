package com.mahdi.activities.main.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.mahdi.file.FileObject
import com.mahdi.file.FileWrapper
import com.mahdi.filetree.FileTreeTab
import com.mahdi.filetree.getAppropriateName
import com.mahdi.icons.MahdiIcon
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.session.RecentFile
import com.mahdi.session.RecentFiles
import com.mahdi.tabs.editor.EditorTab
import com.mahdi.text.bidiNumbers
import com.mahdi.ui.components.AppLogo
import com.mahdi.update.AppUpdateChecker
import com.mahdi.update.UpdateDownloader
import com.mahdi.text.FileNameText
import com.mahdi.utils.dialogRes
import com.mahdi.utils.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
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
    // 9.2.0: مسارات الملفات المفتوحة حاليًا — تُعرض حالتها في «آخر الملفات»
    // ويُغلق الملف من الرئيسية نفسها بضغطة ✕ بلا تنقّل إلى المحرّر.
    var openPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reloadRecent() {
        val list = RecentFiles.recent(6)
        val open = tabsOpenPaths()
        openPaths = open
        recentFiles = list
        lastFile = list.firstOrNull { it.path !in open }
    }

    LaunchedEffect(Unit) {
        reloadRecent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp).widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 6.1.0: بدل شعار النحلة/الثلج المتجه الرمادي، تظهر **أيقونة التطبيق
            // الحقيقية** التي اختارها المستخدم (شخصية كرتونية ثلاثية الأبعاد بحرف M)
            // بظلّ خفيف على شكل البطاقة — نفس ما يراه على الشاشة الرئيسية للهاتف.
            AppLogo(
                size = 74.dp,
                cornerRadius = 20.dp,
                modifier = Modifier.padding(bottom = 12.dp),
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

            // 9.0.0: «مساحة العمل» — ملفات المشروع الحالي في الصفحة نفسها، بجانب
            // «آخر الملفات» وأزرار التشغيل والطرفية: كل ما يحتاجه المستخدم لبدء
            // العمل في مكان واحد، بلا تنقّل بين ثلاث شاشات.
            currentProject?.let { project ->
                Spacer(Modifier.height(10.dp))
                ProjectFilesRow(root = project)
            }

            if (recentFiles.size > 1) {
                Spacer(Modifier.height(12.dp))
                RecentFilesRow(
                    files = recentFiles,
                    isArabic = isArabic,
                    openPaths = openPaths,
                    onOpen = { path ->
                        scope.launch {
                            runCatching {
                                MainActivity.instance?.viewModel?.editorManager?.openFile(FileWrapper(File(path)))
                            }
                        }
                    },
                    // 9.2.0: ✕ بجانب كل ملف في الرئيسية يُغلقه ويُزيله من القائمة.
                    onClose = { path ->
                        closeFromHome(path, scope) { scope.launch { reloadRecent() } }
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
                        // 9.2.0: المتصفّح الداخلي — فتح رابط أو بحث داخل التطبيق
                        CommandProvider.getForId("global.open_link"),
                        CommandProvider.getForId("global.store"),
                        // 9.0.0: زرّ «التوثيق» أُزيل من الواجهة الرئيسية كما طُلب.
                        // الدليل الكامل يبقى في «الإعدادات» (مكان واحد، آخر عنصر).
                        CommandProvider.SettingsCommand,
                    )
            )
        }

        // 6.1.0: شريط التحديث في أسفل الشاشة الرئيسية — بأيقونة التطبيق وزرّي
        // «تحديث» و«إلغاء» بدل إشعار عابر في الأعلى.
        HomeUpdateBar(modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp))
    }
}

/**
 * شريط «تحديث متاح» في أسفل الرئيسية (6.1.0).
 *
 * يظهر فقط عند وجود إصدار أحدث من GitHub (يقرأه [AppUpdateChecker] عند الإقلاع)،
 * وفيه:
 * - **أيقونة التطبيق** بجانب النص (نفس أيقونة المستخدم الحالية عبر [AppLogo]).
 * - **تحديث** — ينزّل APK داخل التطبيق بنسبة تقدّم ثم يفتح المُثبّت.
 * - **إلغاء** — يُخفي الشريط لهذا الإصدار بلا إزعاج.
 */
@Composable
private fun HomeUpdateBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by AppUpdateChecker.pending.collectAsStateWithLifecycle()
    val status by UpdateDownloader.status.collectAsStateWithLifecycle()
    val progress by UpdateDownloader.progress.collectAsStateWithLifecycle()

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    // يُخفى لكل إصدار على حدة: إصدار جديد = شريط جديد ("" = لم يُخفَ شيء)
    var hiddenVersion by rememberSaveable { mutableStateOf("") }

    // 6.1.0: لا نفتح المُثبّت تلقائيًا إلا إذا بدأ التنزيل من هذه الشاشة الآن.
    // بدون هذا الشرط تُفتح حزمة قديمة نُزّلت في جلسة سابقة بمجرّد فتح الرئيسية.
    var startedHere by remember { mutableStateOf(false) }

    val info = update ?: return
    if (hiddenVersion == info.version) return

    val downloading = status is UpdateDownloader.Status.Downloading
    val failed = status as? UpdateDownloader.Status.Failed

    // عند اكتمال التنزيل: افتح مُثبّت الحزمة مباشرة (بلا متصفح)
    LaunchedEffect(status) {
        val done = status as? UpdateDownloader.Status.Done ?: return@LaunchedEffect
        if (!startedHere) return@LaunchedEffect
        startedHere = false
        if (!UpdateDownloader.install(context, done.apk)) {
            toast(
                if (isArabic) "امنح الإذن «تثبيت تطبيقات من هذا المصدر» ثم أعد المحاولة"
                else "Allow installing apps from this source, then try again"
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 34.dp)

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(strings.home_update_available),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            when {
                                downloading -> "${(progress * 100).toInt()}%".bidiNumbers()
                                failed != null ->
                                    if (isArabic) "تعذّر التنزيل — أعد المحاولة" else "Download failed — retry"
                                else ->
                                    if (isArabic) {
                                        "الإصدار ${info.version} · ${info.sizeText} م.ب".bidiNumbers()
                                    } else {
                                        "Version ${info.version} · ${info.sizeText} MB".bidiNumbers()
                                    }
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (downloading) {
                            startedHere = false
                            UpdateDownloader.reset()
                        } else {
                            startedHere = true
                            scope.launch { UpdateDownloader.download(context, info) }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text =
                            if (downloading) {
                                if (isArabic) "إيقاف" else "Stop"
                            } else {
                                if (isArabic) "تحديث" else "Update"
                            },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                TextButton(
                    onClick = {
                        hiddenVersion = info.version
                        startedHere = false
                        UpdateDownloader.reset()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
private fun RecentFilesRow(
    files: List<RecentFile>,
    isArabic: Boolean,
    openPaths: Set<String>,
    onOpen: (String) -> Unit,
    onClose: (String) -> Unit,
) {
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
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 9.2.0: نقطة زرقاء = الملف مفتوح الآن في المحرّر
                        if (file.path in openPaths) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier =
                                    Modifier.size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8)),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // ✕ يُغلق الملف ويُزيله من الرئيسية
                        Box(
                            modifier =
                                Modifier.size(20.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = { onClose(file.path) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(strings.close),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ملفات المشروع الحالي (9.0.0) — «شجرة مصغّرة» في الرئيسية: أول عشرة ملفات في
 * جذر المشروع، بلمسة واحدة تُفتح في المحرّر.
 *
 * لا مجلدات ولا مستويات هنا (الشجرة الكاملة في الدُرج)؛ الهدف أن يرى المستخدم
 * ما في مشروعه فور فتح التطبيق، وأن يبدأ بلمسة، بلا أي شرح نصّي إضافي.
 */
@Composable
private fun ProjectFilesRow(root: FileObject) {
    val scope = rememberCoroutineScope()
    var files by remember(root) { mutableStateOf<List<FileObject>>(emptyList()) }

    LaunchedEffect(root) {
        files =
            runCatching {
                    root.listFiles()
                        .filter { it.isFile() }
                        .sortedBy { it.getName().lowercase() }
                        .take(10)
                }
                .getOrDefault(emptyList())
    }

    if (files.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(strings.workspace_project_files),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(files.size) { index ->
                val file = files[index]
                Card(
                    modifier =
                        Modifier.widthIn(max = 200.dp)
                            .clickable {
                                scope.launch {
                                    runCatching {
                                        MainActivity.instance?.viewModel?.editorManager?.openFile(file)
                                    }
                                        .onFailure { toast(it.message ?: "تعذّر فتح الملف") }
                                }
                            },
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF00E5FF),
                        )
                        Spacer(Modifier.width(6.dp))
                        FileNameText(name = file.getName(), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * أزرار الرئيسية (9.2.0) — صفوف بطاقات ملوّنة بدل قائمة نصّية رمادية.
 *
 * كانت الأزرار `TextButton`s بيضاء متراصّة عموديًا: لا لون، لا حدود، ولا تمييز
 * بين «تشغيل» و«طرفية» و«إعدادات» إلا الأيقونة الصغيرة. الآن: شبكة من عمودين،
 * كل بطاقة بخلفية ملوّنة شفّافة وحدود بلون الميزة وأيقونة مشعّة باللون نفسه،
 * فيُقرأ التطبيق من النظرة الأولى.
 */
@Composable
private fun WelcomeActions(commands: List<Command>) {
    val visible = commands.filter { it.isSupported() && it.isEnabled() }
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.chunked(2).forEach { rowCommands ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowCommands.forEach { command ->
                    WelcomeActionButton(
                        command = command,
                        modifier = Modifier.weight(1f),
                    )
                }
                // بطاقة يتيمة في الصفّ الأخير: نُبقيها بنصف العرض بجانب فراغ متوازن
                if (rowCommands.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * لون الميزة — يُشتقّ من معرّف الأمر نفسه، فيبقى ثابتًا في كل جلسة:
 * تشغيل = نعناعي، طرفية = سماوي، متصفّح = بنفسجي، بحث = ذهبي، إعدادات = رمادي.
 */
private fun commandAccent(id: String): Color {
    return when {
        id.contains("run") -> Color(0xFF2DD4BF)
        id.contains("terminal") -> Color(0xFF38BDF8)
        id.contains("open_link") || id.contains("browse") -> Color(0xFFA78BFA)
        id.contains("new_file") -> Color(0xFF4ADE80)
        id.contains("search") -> Color(0xFFF5D76E)
        id.contains("settings") -> Color(0xFFFB7185)
        else -> Color(0xFF38BDF8)
    }
}

@Composable
private fun WelcomeActionButton(command: Command, modifier: Modifier = Modifier) {
    if (!command.isSupported() || !command.isEnabled()) return
    val activity = LocalActivity.current
    val accent = commandAccent(command.id)

    Surface(
        modifier = modifier.clickable { activity?.let { command.execute(ActionContext(it)) } },
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MahdiIcon(
                icon = command.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accent,
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = command.getLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 9.2.0 — مسارات التبويبات المفتوحة حاليًا في المحرّر.
 *
 * تُستخدم في الرئيسية لتمييز الملف المفتوح، ولإغلاقه من مكانه بضغطة ✕
 * بدل الذهاب إلى المحرّر ثم البحث عن التبويب.
 */
private fun tabsOpenPaths(): Set<String> {
    return MainActivity.instance?.viewModel?.tabs
        ?.filterIsInstance<EditorTab>()
        ?.mapNotNull { it.file?.getAbsolutePath() }
        ?.toSet()
        ?: emptySet()
}

/**
 * 9.2.0 — إغلاق ملف من الرئيسية: يُزال تبويبه (بتأكيد واحد فقط إن كان
 * معدَّلًا وغير محفوظ) ثم يُنسى من «آخر الملفات» فلا يعود إلى الرئيسية.
 */
private fun closeFromHome(path: String, scope: CoroutineScope, onDone: () -> Unit) {
    val viewModel = MainActivity.instance?.viewModel
    val tab =
        viewModel?.tabs
            ?.filterIsInstance<EditorTab>()
            ?.firstOrNull { it.file?.getAbsolutePath() == path }

    // `RecentFiles.forget` معلَّقة (suspend) — نُشغّلها على نطاق الشاشة نفسه
    val finish = {
        scope.launch {
            runCatching { RecentFiles.forget(path) }
            onDone()
        }
        Unit
    }

    if (viewModel == null || tab == null) {
        finish()
        return
    }

    val removeTab = {
        val index = viewModel.tabs.indexOf(tab)
        if (index != -1) viewModel.tabManager.removeTab(index)
        finish()
    }

    if (tab.editorState.isDirty) {
        dialogRes(
            title = strings.file_unsaved.getString(),
            msg = strings.ask_unsaved.getString(),
            onOk = { removeTab() },
            onCancel = {},
            okRes = strings.discard,
        )
    } else {
        removeTab()
    }
}
