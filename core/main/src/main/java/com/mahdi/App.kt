package com.mahdi

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import com.github.anrwatchdog.ANRWatchDog
import com.mahdi.activities.main.session.SessionManager
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.KeybindingsManager
import com.mahdi.crashhandler.CrashHandler
import com.mahdi.editor.CodeHighlighter
import com.mahdi.editor.FontCache
import com.mahdi.editor.KeywordManager
import com.mahdi.editor.LanguageManager
import com.mahdi.extension.ActivityProvider
import com.mahdi.icons.pack.IconPackManager
import com.mahdi.lsp.FileIconProvider
import com.mahdi.lsp.LspPersistence
import com.mahdi.lsp.MarkdownImageProvider
import com.mahdi.motivation.Motivation
import com.mahdi.resources.Res
import com.mahdi.settings.Preference
import com.mahdi.settings.Settings
import com.mahdi.settings.debugOptions.LogcatService
import com.mahdi.settings.debugOptions.startThemeFlipperIfNotRunning
import com.mahdi.settings.editor.DEFAULT_APP_FONT_PATH
import com.mahdi.settings.editor.DEFAULT_EDITOR_FONT_PATH
import com.mahdi.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.mahdi.theme.ThemeManager
import com.mahdi.utils.application
import com.mahdi.utils.getTempDir
import com.mahdi.utils.logError
import com.mahdi.utils.logInfo
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
open class App : Application() {
    companion object {
        val versionCode: Long by lazy {
            val app = application ?: throw IllegalStateException("Application is not initialized yet")
            PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        }

        private var _iconPackManager: IconPackManager? = null
        val iconPackManager: IconPackManager
            get() {
                if (_iconPackManager == null) {
                    _iconPackManager = IconPackManager(application!!)
                }

                return _iconPackManager!!
            }

        private var _themeManager: ThemeManager? = null
        val themeManager: ThemeManager
            get() {
                if (_themeManager == null) {
                    _themeManager = ThemeManager()
                }

                return _themeManager!!
            }
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this

        logInfo("App.onCreate")
        registerActivityLifecycleCallbacks(ActivityProvider)

        LspPersistence.restoreServers()

        MarkdownImageProvider.register()
        FileIconProvider.register()

        CommandProvider.buildCommands()
        KeybindingsManager.loadKeybindings()

        // ثيمات المستخدم المخصّصة (Color Wheel) تُدمج في القائمة قبل بناء الواجهة
        themeManager.registerCustomThemes()

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)
        // نصوص خارج Compose (النوافذ المنبثقة، الرسائل السريعة) تتبع اللغة المختارة أيضًا
        Res.updateLocale(currentLocale)

        // تحفيز: قراءة سلسلة الأيام والهدف اليومي عند الإقلاع
        runCatching { Motivation.refresh() }

        // المخزون: قراءة اللغات المثبَّتة فعليًا داخل الـ sandbox (2.7.0)
        runCatching { com.mahdi.lang.LanguageInstallStore.refresh() }

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                iconPackManager.indexLocalPacks()
                // 2.5.0: أُزيلت «الألوان الديناميكية» (Monet) — نُبطلها دائمًا حتى تبقى الثيمات الأربعة فقط
                if (Settings.monet) Settings.monet = false

                iconPackManager.indexStoreIconPacks()
            }
            launch(Dispatchers.IO) {
                themeManager.indexLocalThemes()
                themeManager.indexStoreThemes()
            }

            launch { LanguageManager.initGrammarRegistry() }

            launch { KeywordManager.initKeywordRegistry(this@App) }

            launch { CodeHighlighter.registerMarkdownCodeHighlighter(this@App) }

            launch(Dispatchers.IO) { SessionManager.preloadSession() }

            launch(Dispatchers.IO) {
                val editorFontPath = Settings.editor_font_path.ifEmpty { DEFAULT_EDITOR_FONT_PATH }
                val isEditorAsset = if (editorFontPath.isNotEmpty()) Settings.is_editor_font_asset else true

                val appFontPath = Settings.app_font_path.ifEmpty { DEFAULT_APP_FONT_PATH }
                val isAppAsset = if (editorFontPath.isNotEmpty()) Settings.is_app_font_asset else true

                val terminalFontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
                val isTerminalAsset = if (terminalFontPath.isNotEmpty()) Settings.is_terminal_font_asset else true

                FontCache.loadFont(this@App, editorFontPath, isEditorAsset)
                FontCache.loadFont(this@App, appFontPath, isAppAsset)
                FontCache.loadFont(this@App, terminalFontPath, isTerminalAsset)
            }

            launch(Dispatchers.IO) { Preference.preloadAllSettings() }

            launch { DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir) }

            launch(Dispatchers.IO) {
                getTempDir().apply {
                    if (exists() && listFiles().isNullOrEmpty().not()) {
                        deleteRecursively()
                    }
                }
            }

            launch { runCatching { UpdateChecker.checkForUpdates("main") } }

            // wait until UpdateManager is done, it should only take few milliseconds
            UpdateManager.inspect()

            // debug options
            startThemeFlipperIfNotRunning()
            if (Settings.enable_logcat) {
                LogcatService.start(this@App)
            }
        }

        if (Settings.anr_watchdog) {
            ANRWatchDog().start()
        }

        if (Settings.strict_mode) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .apply {
                        detectAll()
                        penaltyLog()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                                logError(violation)
                                violation.cause?.let { throw it }
                            }
                        }
                    }
                    .build()
            )
        }
    }
}
