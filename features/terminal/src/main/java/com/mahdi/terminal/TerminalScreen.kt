package com.mahdi.terminal

import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.activities.terminal.Terminal
import com.mahdi.animations.NavigationAnimationTransitions
import com.mahdi.components.SingleInputDialog
import com.mahdi.editor.FontCache
import com.mahdi.exec.pendingCommand
import com.mahdi.file.child
import com.mahdi.file.sandboxDir
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.lsp.LspMemoryGuard
import com.mahdi.utils.logError
import com.mahdi.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.mahdi.settings.editor.TerminalFontScreen
import com.mahdi.settings.terminal.DEFAULT_TERMINAL_EXTRA_KEYS
import com.mahdi.settings.terminal.SettingsTerminalScreen
import com.mahdi.settings.terminal.TerminalCheckScreen
import com.mahdi.settings.terminal.TerminalExtraKeys
import com.mahdi.terminal.virtualkeys.VirtualKeysConstants
import com.mahdi.terminal.virtualkeys.VirtualKeysInfo
import com.mahdi.terminal.virtualkeys.VirtualKeysListener
import com.mahdi.terminal.virtualkeys.VirtualKeysView
import com.mahdi.theme.LocalThemeHolder
import com.mahdi.theme.ThemeHolder
import com.mahdi.utils.dpToPx
import com.mahdi.utils.toast
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.lang.ref.WeakReference
import java.util.Properties
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)

@Composable
fun TerminalScreen(modifier: Modifier = Modifier, terminalActivity: Terminal) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "terminal",
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {
        composable("terminal") {
            TerminalScreenInternal(terminalActivity = terminalActivity, navController = navController)
        }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.TerminalFontScreen.route) { TerminalFontScreen() }
        composable(SettingsRoutes.TerminalExtraKeys.route) { TerminalExtraKeys() }
        composable(SettingsRoutes.TerminalCheck.route) { TerminalCheckScreen() }
    }
}

/**
 * شريط علوي مصغّر للطرفية (8.0.0).
 *
 * استُبدل `TopAppBar` القياسي (٦٤dp) بصفّ بارتفاع ٤٦dp: أيقونة القائمة ثم اسم
 * الشاشة، بلا عنوان كبير ولا مساحات مهدورة — فتكسب الطرفية ١٨dp من الارتفاع
 * في كل لقطة، ويبقى الشريط محترمًا لمساحة شريط الحالة عبر statusBarsPadding.
 */
@Composable
private fun CompactTopBar(
    title: String,
    onMenu: () -> Unit,
    onHome: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
) {
    // 8.2.0: شريط نحيف كما هو (٤٦dp) لكن بهويّة بصرية واضحة: شريط متدرّج
    // بثلاثة ألوان أسفلَه، ونقطة «متصل» بلون التركوازي. لا عناوين كبيرة ولا
    // نصّ شرح — الطرفية تبقى ملكًا للمخرجات.
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMenu, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = title,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier.size(width = 3.dp, height = 18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF2DD4BF))
                                )
                            ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier.size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(strings.terminal_status_connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                // 9.0.0: زرّا «الرئيسية» و«إغلاق الطرفية» داخل الطرفية نفسها —
                // كان الخروج من الطرفية يتطلّب «رجوع» مرتين أو الخروج من التطبيق.
                onHome?.let { home ->
                    IconButton(onClick = home, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = stringResource(strings.home_action),
                            modifier = Modifier.size(19.dp),
                            tint = Color(0xFF38BDF8),
                        )
                    }
                }
                onExit?.let { exit ->
                    IconButton(onClick = exit, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(strings.terminal_close_session),
                            modifier = Modifier.size(19.dp),
                            tint = Color(0xFFFB7185),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF2DD4BF),
                                    Color(0xFFA78BFA),
                                    Color(0xFFF5D76E),
                                )
                            )
                        ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, terminalActivity: Terminal, navController: NavController) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentTheme = LocalThemeHolder.current

    DisposableEffect(Unit) { onDispose { keyboardController?.hide() } }

    // 9.1.0: «حارس الذاكرة» — أثناء فتح الطرفية تتوقّف خوادم اللغة مؤقتًا.
    //
    // اللقطة أظهرت `[Process completed (signal 9)]` بعد ٩ ثوانٍ من تشغيل سكربت
    // بايثون ثقيل. الإشارة ٩ هي SIGKILL من قاتل الذاكرة في أندرويد، وأكبر مستهلك
    // في التطبيق ليس السكربت بل Pyright/Node في الخلفية (مئات الميغابايتات).
    // الطرفية هي المكان الذي تُشغَّل فيه الأحمال الثقيلة، فإيقاف الخوادم هنا
    // (لا حذفها ولا تعطيلها) يترك ذاكرةً كافية للسكربت. تعود تلقائيًا عند فتح
    // ملف في المحرّر — بلا رسالة ولا إعداد.
    LaunchedEffect(Unit) {
        runCatching { LspMemoryGuard.freezeForRun() }
            .onFailure { logError(it) }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { LspMemoryGuard.release() }
        }
    }

    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { TerminalDrawer(drawerWidth, terminalActivity, navController) },
            content = {
                Scaffold(
                    // 8.0.0: شريط علوي نحيف (٤٦dp بدل ٦٤) — مساحة أكبر للطرفية
                    // وسطور أهدأ، وهو ما طلبه المستخدم: «شريط علوي نحيف جدًا».
                    topBar = {
                        CompactTopBar(
                            title = stringResource(strings.terminal),
                            onMenu = { scope.launch { drawerState.open() } },
                            onHome = {
                                // الرئيسية فورًا: إنهاء نشاط الطرفية والعودة للمحرّر
                                runCatching { terminalActivity.finish() }
                                    .onFailure { logError(it) }
                            },
                            onExit = {
                                // إغلاق الجلسة الحالية كاملةً (العملية والطرفية) بلا سؤال
                                runCatching {
                                        terminalView.get()?.currentSession?.finishIfRunning()
                                    }
                                    .onFailure { logError(it) }
                                runCatching { terminalActivity.finish() }
                                    .onFailure { logError(it) }
                            },
                        )
                    }
                ) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        // 6.1.0: بطاقة التقدّم صارت صغيرة ومثبّتة **داخل** منطقة الطرفية
                        // (كانت سابقًا شريطًا بعرض الشاشة يغطّي أسفلها وشريط المفاتيح).
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            TerminalView(
                                isDarkMode = isDarkMode,
                                currentTheme = currentTheme,
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                terminalActivity = terminalActivity,
                                modifier = Modifier.fillMaxSize(),
                            )

                            val downloadProgress = rememberDownloadProgress()
                            DownloadProgressDock(
                                progress = downloadProgress,
                                modifier =
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .zIndex(3f),
                            )
                        }

                        // شريط الأدوات السريع (2.6.0): أوامر جاهزة بلمسة واحدة
                        TerminalQuickBar()

                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(75.dp)) { page ->
                            when (page) {
                                0 -> {
                                    terminalView.get()?.requestFocus()
                                    AndroidView(
                                        factory = { context ->
                                            VirtualKeysView(context, null).apply {
                                                virtualKeysView = WeakReference(this)
                                                virtualKeysViewClient =
                                                    terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }

                                                buttonTextColor = onSurfaceColor

                                                runCatching {
                                                    reload(
                                                        VirtualKeysInfo(
                                                            Settings.terminal_extra_keys,
                                                            "",
                                                            VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                        )
                                                    )
                                                }
                                                    .onFailure {
                                                        toast(strings.invalid_terminal_extra_keys)
                                                        reload(
                                                            VirtualKeysInfo(
                                                                DEFAULT_TERMINAL_EXTRA_KEYS,
                                                                "",
                                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                            )
                                                        )
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(75.dp),
                                    )
                                }

                                1 -> {
                                    var text by rememberSaveable { mutableStateOf("") }
                                    val focusRequester = remember { FocusRequester() }

                                    TextField(
                                        value = text,
                                        onValueChange = { text = it },
                                        maxLines = 1,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions =
                                            KeyboardActions(
                                                onDone = {
                                                    if (text.isEmpty()) {
                                                        // Dispatch enter key events if text is empty
                                                        val eventDown =
                                                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                                                        val eventUp =
                                                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                                                        terminalView.get()?.dispatchKeyEvent(eventDown)
                                                        terminalView.get()?.dispatchKeyEvent(eventUp)
                                                    } else {
                                                        terminalView.get()?.currentSession?.write(text)
                                                        text = ""
                                                    }
                                                }
                                            ),
                                        modifier = Modifier.fillMaxWidth().height(75.dp).focusRequester(focusRequester),
                                    )

                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun TerminalView(
    isDarkMode: Boolean,
    currentTheme: ThemeHolder,
    surfaceColor: Int,
    onSurfaceColor: Int,
    terminalActivity: Terminal,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            TerminalView(context, null).apply {
                val terminalColors =
                    if (isDarkMode) {
                        currentTheme.darkTerminalColors
                    } else {
                        currentTheme.lightTerminalColors
                    }
                applyTerminalColors(
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor,
                    terminalColors = terminalColors,
                )

                terminalView = WeakReference(this)
                terminalActivity.handleIntent(terminalActivity.intent)
                setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                val client = TerminalBackEnd()

                val session =
                    if (pendingCommand != null) {
                        terminalActivity.sessionBinder?.get()!!.getService().currentSession.value = pendingCommand!!.id
                        terminalActivity.sessionBinder?.get()!!.getSession(pendingCommand!!.id)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(pendingCommand!!.id, client, terminalActivity)
                                .session
                    } else {
                        terminalActivity.sessionBinder
                            ?.get()!!
                            .getSession(terminalActivity.sessionBinder?.get()!!.getService().currentSession.value)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(
                                    terminalActivity.sessionBinder?.get()!!.getService().currentSession.value,
                                    client,
                                    terminalActivity,
                                )
                                .session
                    }

                session.updateTerminalSessionClient(client)
                attachSession(session)
                setTerminalViewClient(client)

                // Legacy behavior
                val fontFile = sandboxDir().child("etc/font.ttf")
                if (fontFile.exists()) {
                    setTypeface(Typeface.createFromFile(fontFile))
                } else {
                    val fontPath = Settings.terminal_font_path
                    val font =
                        if (fontPath.isNotEmpty()) {
                            FontCache.getTypeface(context, fontPath, Settings.is_terminal_font_asset)
                                ?: FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        } else {
                            FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        }

                    setTypeface(font)
                }

                addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val widthChanged = (right - left) != (oldRight - oldLeft)
                    val heightChanged = (bottom - top) != (oldBottom - oldTop)

                    if (widthChanged || heightChanged) {
                        val terminalColors =
                            if (isDarkMode) {
                                currentTheme.darkTerminalColors
                            } else {
                                currentTheme.lightTerminalColors
                            }
                        terminalView
                            .get()
                            ?.applyTerminalColors(
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                terminalColors = terminalColors,
                            )
                    }
                }

                post {
                    keepScreenOn = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            }
        },
        modifier = modifier,
        update = { terminalView ->
            val terminalColors =
                if (isDarkMode) {
                    currentTheme.darkTerminalColors
                } else {
                    currentTheme.lightTerminalColors
                }

            terminalView.applyTerminalColors(
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                terminalColors = terminalColors,
            )
        },
    )
}

@Composable
private fun TerminalDrawer(drawerWidth: Dp, terminalActivity: Terminal, navController: NavController) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }

    val service = terminalActivity.sessionBinder?.get()?.getService()
    val sessionList =
        service?.sessionList?.collectAsStateWithLifecycle(initialValue = emptyList())?.value ?: emptyList()
    val currentSession = service?.currentSession?.collectAsStateWithLifecycle(initialValue = "main")?.value ?: "main"

    if (showRenameDialog) {
        SingleInputDialog(
            title = stringResource(strings.rename_session),
            inputLabel = stringResource(strings.name),
            inputValue = renameValue,
            errorMessage = renameError,
            onInputValueChange = {
                renameValue = it
                renameError =
                    if (it.isBlank()) {
                        strings.name_empty_err.getString()
                    } else if (it != sessionToRename && sessionList.contains(it)) {
                        strings.session_name_exists.getString()
                    } else null
            },
            onConfirm = {
                if (renameError == null && renameValue.isNotBlank() && renameValue != sessionToRename) {
                    terminalActivity.sessionBinder?.get()?.renameSession(sessionToRename, renameValue)
                }
            },
            onFinish = { showRenameDialog = false },
        )
    }

    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(strings.sessions), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = {
                            fun generateUniqueString(existingStrings: List<String>): String {
                                var index = 1
                                var newString: String

                                do {
                                    newString = "main #$index"
                                    index++
                                } while (newString in existingStrings)

                                return newString
                            }
                            terminalView.get()?.let {
                                val client = TerminalBackEnd()
                                terminalActivity.sessionBinder
                                    ?.get()!!
                                    .createSession(
                                        generateUniqueString(
                                            terminalActivity.sessionBinder?.get()!!.getService().sessionList.value
                                        ),
                                        client,
                                        terminalActivity,
                                    )
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(strings.add_session))
                    }

                    IconButton(onClick = { navController.navigate(SettingsRoutes.TerminalSettings.route) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(strings.settings),
                        )
                    }
                }
            }

            if (service != null && sessionList.isNotEmpty()) {
                LazyColumn {
                    items(sessionList) { sessionId ->
                        val isSelected = sessionId == currentSession
                        NavigationDrawerItem(
                            label = { Text(text = sessionId) },
                            selected = isSelected,
                            onClick = { terminalActivity.changeSession(sessionId) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            sessionToRename = sessionId
                                            renameValue = sessionId
                                            renameError = null
                                            showRenameDialog = true
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(strings.rename),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isSelected) {
                                                val index = sessionList.indexOf(sessionId)
                                                val sessionBefore = sessionList.getOrNull(index - 1)
                                                val sessionAfter = sessionList.getOrNull(index + 1)
                                                val neighborSession = sessionBefore ?: sessionAfter
                                                neighborSession?.let { terminalActivity.changeSession(it) }
                                            }

                                            terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)

                                            if (sessionList.isEmpty()) {
                                                terminalActivity.finish()
                                                service.actionExit()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(strings.delete),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun Terminal.changeSession(sessionId: String) {
    val terminalView = terminalView.get() ?: return
    val binder = sessionBinder!!.get()!!

    val client = TerminalBackEnd()
    val session = binder.getSession(sessionId) ?: binder.createSession(sessionId, client, this).session

    session.updateTerminalSessionClient(client)
    terminalView.attachSession(session)
    terminalView.setTerminalViewClient(client)

    terminalView.apply {
        post {
            keepScreenOn = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }
    virtualKeysView.get()?.apply { virtualKeysViewClient = VirtualKeysListener(terminalView.mTermSession) }

    binder.getService().currentSession.value = sessionId
}

private fun TerminalView.applyTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: Properties) {
    this.onScreenUpdated()

    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(terminalColors)

    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }

    invalidate()
}

/**
 * شريط أدوات احترافي فوق لوحة المفاتيح: أوامر الطرفية الشائعة بلمسة واحدة،
 * مع لصق من الحافظة وتكبير/تصغير فوري للخط.
 */
@Composable
private fun TerminalQuickBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun send(text: String) {
        terminalView.get()?.currentSession?.write(text)
    }

    // 3.3.0: مستويات ثابتة لا خطوة عشوائية — التكبير والتصغير يمرّان على الدرجات نفسها
    // دائمًا، فلا تتراكم فروق غريبة بين الجهاز والإعدادات، و«إعادة الضبط» تعود إلى 12.
    fun applySize(size: Int) {
        val newSize = size.coerceIn(8, 32)
        Settings.terminal_font_size = newSize
        terminalView.get()?.setTextSize(dpToPx(newSize.toFloat(), context))
    }

    fun zoomToNextLevel(up: Boolean) {
        val levels = listOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32)
        val current = Settings.terminal_font_size
        val target =
            if (up) {
                levels.firstOrNull { it > current } ?: levels.last()
            } else {
                levels.lastOrNull { it < current } ?: levels.first()
            }
        applySize(target)
    }

    // 8.2.0: كل مجموعة بلونها، فتُقرأ الأزرار بالعين قبل قراءة نصّها:
    // مرجاني للإيقاف، ذهبي للأوامر، بنفسجي للتنقّل، سماوي للأدوات.
    val danger = Color(0xFFFB7185)
    val control = Color(0xFFF5D76E)
    val navigate = Color(0xFFA78BFA)
    val utility = Color(0xFF38BDF8)
    val accent = Color(0xFF2DD4BF)

    // 9.0.0: شريط مدمج كما في Pydroid/Termux — الأزرار التي تُستخدم فعلًا فقط،
    // بترتيب واحد ثابت: مقاطعة · تحكّم · حركة · إدخال · عرض. أُزيلت أزرار
    // «Ctrl+D» و«إعادة ضبط الخط» و«mh» لأنها تشغل مكانًا بلا حاجة يومية.
    val items: List<Triple<String, () -> Unit, Color>> =
        listOf(
            Triple("Ctrl+C", { send("\u0003") }, danger),
            Triple("Esc", { send("\u001b") }, control),
            Triple("Tab", { send("\t") }, control),
            Triple("/", { send("/") }, control),
            Triple("-", { send("-") }, control),
            Triple("←", { send("\u001b[D") }, navigate),
            Triple("→", { send("\u001b[C") }, navigate),
            Triple("↑", { send("\u001b[A") }, navigate),
            Triple("↓", { send("\u001b[B") }, navigate),
            Triple(stringResource(strings.terminal_tool_clear), { send("\u000c") }, utility),
            Triple(
                stringResource(strings.terminal_tool_paste),
                { clipboard.getText()?.text?.let { text -> if (text.isNotEmpty()) send(text) } },
                utility,
            ),
            Triple(stringResource(strings.terminal_tool_zoom_in), { zoomToNextLevel(true) }, accent),
            Triple(stringResource(strings.terminal_tool_zoom_out), { zoomToNextLevel(false) }, accent),
        )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, action, tint) ->
            AssistChip(
                onClick = { action() },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = tint.copy(alpha = 0.12f),
                    ),
            )
        }
    }
}
