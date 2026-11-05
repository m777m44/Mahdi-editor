package com.mahdi.update

import android.content.Context
import android.content.pm.PackageManager
import com.mahdi.utils.logError
import com.mahdi.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * نظام التحديث الداخلي (5.0.0).
 *
 * قبل 5.0.0 كان «التحديث» يعني فتح صفحة GitHub في المتصفح، وكان الفحص يقرأ
 * **آخر الالتزامات (commits)** ويقارن تواريخها — وهذا لا يناسب مستخدمًا يريد
 * معرفة «هل هناك إصدار أحدث؟».
 *
 * الآن:
 * 1. الفحص يقرأ `releases/latest` من GitHub Releases API.
 * 2. المقارنة برقم الإصدار الدلالي (5.0.0 > 4.3.2) لا بالتواريخ.
 * 3. عند وجود إصدار أحدث يُخزَّن في [pending] فتظهر رسالة داخل التطبيق.
 * 4. التنزيل والتثبيت يتمّان داخل التطبيق عبر [UpdateDownloader].
 *
 * كل شيء محميّ: أي فشل شبكة أو JSON يُسجَّل ويعود `null` — لا يُسقط التطبيق.
 */
object AppUpdateChecker {

    private const val API_URL = "https://api.github.com/repos/m777m44/Mahdi-editor/releases/latest"

    /** رابط ثابت احتياطي إن لم يذكر الإصدار أصلًا بصيغة APK. */
    private const val FALLBACK_APK =
        "https://github.com/m777m44/Mahdi-editor/releases/latest/download/Mahdi-Editor.apk"

    private const val PREFS = "mahdi_update_center"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_NOTIFIED = "notified_version"

    /** لا نُزعج الشبكة أكثر من مرة كل ست ساعات في الفحص التلقائي. */
    private const val MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L

    /** آخر تحديث مكتشَف — تقرأه الواجهة لإظهار الإشعار. */
    val pending = MutableStateFlow<UpdateInfo?>(null)

    /**
     * هل فشل آخر فحص (شبكة/JSON)؟ تفرّق الواجهة بين «محدَّث» و«تعذّر الفحص»،
     * فلا تقول للمستخدم «أنت على أحدث إصدار» وهو بلا إنترنت.
     */
    @Volatile
    var lastCheckFailed: Boolean = false
        private set

    /** إصدار التطبيق المثبَّت حاليًا من نظام الحزم (مصدر الحقيقة). */
    fun currentVersion(context: Context): String =
        runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            }
            .getOrDefault("0")

    /**
     * يفحص وجود تحديث.
     *
     * @param force تجاهل مهلة الست ساعات (يُستخدم من زر «فحص الآن»).
     * @return التحديث إن وُجد، أو `null` إن كان التطبيق محدَّثًا أو تعذّر الفحص.
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            if (!force) {
                val last = prefs.getLong(KEY_LAST_CHECK, 0L)
                if (System.currentTimeMillis() - last < MIN_INTERVAL_MS) {
                    return@withContext pending.value
                }
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            lastCheckFailed = false

            try {
                val request =
                    Request.Builder()
                        .url(API_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Mahdi-Editor-App")
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logError("update check http ${response.code}")
                        lastCheckFailed = true
                        return@withContext null
                    }

                    val json = JSONObject(response.body.string())
                    val latest = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
                    if (latest.isBlank()) return@withContext null

                    val current = currentVersion(app)
                    if (!isNewer(latest, current)) {
                        pending.value = null
                        return@withContext null
                    }

                    var url = FALLBACK_APK
                    var size = 0L
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                url = asset.optString("browser_download_url", FALLBACK_APK)
                                size = asset.optLong("size", 0L)
                                // نفضّل الملف ذا الاسم الثابت، وهو أول أصل عادةً
                                if (name.equals("Mahdi-Editor.apk", ignoreCase = true)) break
                            }
                        }
                    }

                    val info =
                        UpdateInfo(
                            version = latest,
                            currentVersion = current,
                            downloadUrl = url,
                            sizeBytes = size,
                            notes = json.optString("body").orEmpty(),
                            publishedAt = json.optString("published_at").orEmpty(),
                        )

                    pending.value = info
                    info
                }
            } catch (t: Throwable) {
                logError(t, "update check failed")
                lastCheckFailed = true
                null
            }
        }

    /** هل ينبغي إظهار إشعار لهذا الإصدار؟ (مرة واحدة لكل إصدار) */
    fun shouldNotify(context: Context, version: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NOTIFIED, null) != version
    }

    fun markNotified(context: Context, version: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFIED, version)
            .apply()
    }

    /** مقارنة إصدارين دلاليين: `true` إن كان [latest] أحدث من [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** يستخرج الأرقام من `5.0.1-beta` ⇒ `[5, 0, 1]`. */
    private fun numbers(version: String): List<Int> =
        version
            .substringBefore('-')
            .substringBefore('+')
            .split('.', '_')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    /** هل يملك التطبيق صلاحية تثبيت الحزم من هذا المصدر؟ */
    fun canInstall(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        } else {
            true
        }

    /** يفتح شاشة السماح بالتثبيت من هذا المصدر. */
    fun openInstallPermission(context: Context) {
        runCatching {
            val intent =
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
            .onFailure { logError(it, "open install permission") }
    }

    /** حماية من نسخة بلا رقم إصدار صالح. */
    @Suppress("unused")
    private fun PackageManager.packageVersion(packageName: String): String =
        getPackageInfo(packageName, 0).versionName ?: "0"
}
