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
import com.mahdi.commands.ToolbarConfiguration
import com.mahdi.commands.KeybindingsManager
import com.mahdi.crashhandler.CrashHandler
import com.mahdi.editor.CodeHighlighter
import com.mahdi.editor.FontCache
import com.mahdi.editor.KeywordManager
import com.mahdi.editor.LanguageManager
import com.mahdi.extension.ActivityProvider
import com.mahdi.icons.pack.IconPackManager
import com.mahdi.icons.pack.currentIconPack
import com.mahdi.lsp.FileIconProvider
import com.mahdi.lsp.LspPersistence
import com.mahdi.lsp.MarkdownImageProvider
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

        runCatching { LspPersistence.restoreServers() }.onFailure { logError(it, "LSP restore") }

        MarkdownImageProvider.register()
        FileIconProvider.register()

        runCatching { CommandProvider.buildCommands() }.onFailure { logError(it, "buildCommands") }
        // 4.2.0: إضافات النظام (11 قدرة + 24 أداة = 35) تُسجَّل في لوحة الأوامر منذ الإقلاع
        runCatching { com.mahdi.ext.BuiltinExtensions.registerCommands() }
        runCatching { KeybindingsManager.loadKeybindings() }.onFailure { logError(it, "keybindings") }
        // 4.1.0: زرّ «الترميز» في شريط المحرّر (ترحيل لمرّة واحدة لمن لم يعدّل شريطه)
        runCatching { ToolbarConfiguration.migrateEncodingCommand() }

        // ثيمات المستخدم المخصّصة (Color Wheel) تُدمج في القائمة قبل بناء الواجهة
        runCatching { themeManager.registerCustomThemes() }.onFailure { logError(it, "custom themes") }

        // 4.3.1: أيقونة التطبيق تُصلَح **على خيط IO** لا على الخيط الرئيسي.
        // كان هذا الاستدعاء يكتب في PackageManager عشر مرات متزامنة بعد تغيير
        // معرّفات الأيقونات، فيتجاوز مهلة ANR ويقتل النظام التطبيق عند الإقلاع.
        // 5.0.0: الشعار الداخلي (الشريط العلوي/البداية) يقرأ نفس الأيقونة المختارة
        runCatching { com.mahdi.appicon.AppIconSwitcher.syncSelected(this) }
            .onFailure { logError(it, "App icon state") }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { com.mahdi.appicon.AppIconSwitcher.restore(this@App) }
                .onFailure { logError(it, "App icon restore") }
        }

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)
        // نصوص خارج Compose (النوافذ المنبثقة، الرسائل السريعة) تتبع اللغة المختارة أيضًا
        Res.updateLocale(currentLocale)

        // المخزون: قراءة اللغات المثبَّتة فعليًا داخل الـ sandbox (2.7.0)
        runCatching { com.mahdi.lang.LanguageInstallStore.refresh() }

        // 4.3.1: كل تهيئة خلفية محميّة على حدة.
        // قبل ذلك كان أي استثناء في أي `launch` (شبكة المتجر، جلسة قديمة تالفة،
        // خط محفوظ مفقود…) يُسقط العملية كلها بلا رسالة — فيرى المستخدم التطبيق
        // يُغلق وحده بعد ثانية من فتحه.
        fun guarded(name: String, block: suspend () -> Unit) {
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { block() }.onFailure { logError(it, "startup: $name") }
            }
        }

        guarded("icon packs") {
            iconPackManager.indexLocalPacks()
            // 2.5.0: أُزيلت «الألوان الديناميكية» (Monet) — نُبطلها دائمًا حتى تبقى الثيمات الأربعة فقط
            if (Settings.monet) Settings.monet = false

            // حزمة أيقونات محذوفة في الإعدادات → نرجع إلى أيقونات النظام بدل حالة معلّقة
            if (Settings.icon_pack.isNotEmpty() && iconPackManager.localIconPacks.value[Settings.icon_pack] == null) {
                Settings.icon_pack = ""
                currentIconPack.value = null
            }

            iconPackManager.indexStoreIconPacks()
        }

        guarded("themes") {
            themeManager.indexLocalThemes()
            themeManager.indexStoreThemes()
        }

        guarded("grammars") { LanguageManager.initGrammarRegistry() }

        guarded("keywords") { KeywordManager.initKeywordRegistry(this@App) }

        guarded("markdown") { CodeHighlighter.registerMarkdownCodeHighlighter(this@App) }

        // استعادة الجلسة: ملف تالف أو تبويب لملف محذوف لا يجب أن يُسقط التطبيق
        guarded("session") { SessionManager.preloadSession() }

        guarded("fonts") {
            val editorFontPath = Settings.editor_font_path.ifEmpty { DEFAULT_EDITOR_FONT_PATH }
            val isEditorAsset = if (editorFontPath.isNotEmpty()) Settings.is_editor_font_asset else true

            val appFontPath = Settings.app_font_path.ifEmpty { DEFAULT_APP_FONT_PATH }
            val isAppAsset = if (editorFontPath.isNotEmpty()) Settings.is_app_font_asset else true

            val terminalFontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
            val isTerminalAsset = if (terminalFontPath.isNotEmpty()) Settings.is_terminal_font_asset else true

            runCatching { FontCache.loadFont(this@App, editorFontPath, isEditorAsset) }
            runCatching { FontCache.loadFont(this@App, appFontPath, isAppAsset) }
            runCatching { FontCache.loadFont(this@App, terminalFontPath, isTerminalAsset) }
        }

        guarded("preferences") { Preference.preloadAllSettings() }

        guarded("document provider") { DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir) }

        guarded("temp dir") {
            getTempDir().apply {
                if (exists() && listFiles().isNullOrEmpty().not()) {
                    deleteRecursively()
                }
            }
        }

        // 5.0.0: فحص إصدارات GitHub Releases (لا الالتزامات) — مرة كل ٦ ساعات،
        // ونتيجته تظهر كإشعار داخل التطبيق لا كنافذة تحجب العمل.
        guarded("update check") {
            // النتيجة تُخزَّن في AppUpdateChecker.pending فتظهر كإشعار في الواجهة
            com.mahdi.update.AppUpdateChecker.check(this@App, force = false)
        }

        guarded("update manager") { UpdateManager.inspect() }

        guarded("theme flipper") { startThemeFlipperIfNotRunning() }

        guarded("logcat") {
            if (Settings.enable_logcat) {
                LogcatService.start(this@App)
            }
        }

        // 4.3.1: تقرير آخر انهيار (إن وُجد) يظهر في «سجلّات التطبيق» ليعرف المستخدم السبب
        guarded("crash report") { CrashHandler.surfaceLastCrash(this@App) }

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
