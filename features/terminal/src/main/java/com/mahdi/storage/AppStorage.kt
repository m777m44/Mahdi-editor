package com.mahdi.storage

import com.mahdi.exec.isTerminalInstalled
import com.mahdi.exec.ubuntuProcess
import com.mahdi.file.localDir
import com.mahdi.file.sandboxDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.settings.terminal.awaitExit
import com.mahdi.utils.application
import com.mahdi.utils.getTempDir
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * حساب مساحة التطبيق وتنظيفها (4.3.0).
 *
 * سبب حجم ٣٫٣ غيغابايت الذي يراه المستخدم في «معلومات التطبيق» ليس التطبيق نفسه
 * (الـAPK ~٣٠ م.ب)، بل **بياناته**:
 *
 * - صورة أوبنتو المستخرجة (~٤٠٠ م.ب),
 * - ما ثبّته المستخدم داخلها (Python/Node/JDK/GCC + حزم pip/npm/apt),
 * - نسخ المكتبات في المخازن (`~/.mahdi/cache`),
 * - بقايا `apt` و`pip` و`npm` وملفات `/tmp` والسجلّات.
 *
 * هذه الشاشة تعرض التفصيل الحقيقي من القرص وتوفّر تنظيفًا آمنًا لا يمسّ ملفات
 * المستخدم ولا الحزم المخزّنة للعمل بلا إنترنت.
 */
object AppStorage {

    data class Entry(
        val labelAr: String,
        val labelEn: String,
        val size: Long,
    ) {
        fun label(isArabic: Boolean) = if (isArabic) labelAr else labelEn
    }

    data class Report(
        val total: Long,
        val free: Long,
        val deviceFree: Long,
        val deviceTotal: Long,
        val groups: List<Entry>,
        val sandboxTop: List<Entry>,
    )

    /** يقيس كل مجلدات البيانات. العملية ثقيلة نسبيًا، لذا تُنفَّذ على خيط IO. */
    suspend fun report(onProgress: (String) -> Unit = {}): Report =
        withContext(Dispatchers.IO) {
            val dataDir = application!!.filesDir.parentFile ?: application!!.filesDir
            val local = localDir()
            val sandbox = sandboxDir()
            val home = sandboxHomeDir()
            val tempDir = getTempDir()
            val cacheDir = application!!.cacheDir

            onProgress("total")
            val total = sizeOf(dataDir)

            onProgress("sandbox")
            val sandboxSize = sizeOf(sandbox)

            onProgress("home")
            val homeSize = sizeOf(home)

            onProgress("tools")
            val toolsSize = sizeOf(File(local, "bin")) + sizeOf(File(local, "lib"))

            onProgress("assets")
            val assetsSize =
                sizeOf(File(local, "icon_pack")) +
                    sizeOf(File(local, "themes")) +
                    sizeOf(File(local, "extensions"))

            onProgress("cache")
            val appCache = sizeOf(cacheDir)
            val temp = sizeOf(tempDir)

            onProgress("top")
            val groups =
                listOf(
                    Entry("صورة أوبنتو (النظام)", "Ubuntu image (system)", sandboxSize),
                    Entry("ملفاتك داخل الطرفية", "Your terminal files", homeSize),
                    Entry("بيئات التشغيل والأدوات", "Runtimes & tools", toolsSize),
                    Entry("حزم الأيقونات والثيمات", "Icon packs & themes", assetsSize),
                    Entry("ذاكرة التخزين المؤقتة", "App cache", appCache),
                    Entry("ملفات مؤقتة", "Temporary files", temp),
                )

            val known = groups.sumOf { it.size }
            val other = (total - known).coerceAtLeast(0)
            val allGroups = groups + Entry("بقية البيانات", "Everything else", other)

            val sandboxTop =
                (sandbox.listFiles() ?: emptyArray())
                    .filter { it.isDirectory && !isSymlink(it) }
                    .mapNotNull { dir ->
                        val size = sizeOf(dir)
                        if (size <= 0) null else Entry(dir.name, dir.name, size)
                    }
                    .sortedByDescending { it.size }
                    .take(7)

            Report(
                total = total,
                free = dataDir.usableSpace,
                deviceFree = dataDir.usableSpace,
                deviceTotal = dataDir.totalSpace,
                groups = allGroups.sortedByDescending { it.size },
                sandboxTop = sandboxTop,
            )
        }

    /**
     * تنظيف آمن: لا يمسّ ملفات المستخدم ولا الحزم المخزّنة (`~/.mahdi/cache/wheels`).
     *
     * يشمل: ذاكرة التطبيق، الملفات المؤقتة، ذاكرة pip، ذاكرة npm، أرشيف apt،
     * قوائم apt، `/tmp`، وملفات السجلّات المضغوطة.
     *
     * @return الحجم الموفَّر بالبايت.
     */
    suspend fun clean(onLog: (String) -> Unit = {}): Long =
        withContext(Dispatchers.IO) {
            val before = sizeOf(application!!.filesDir.parentFile ?: application!!.filesDir)

            // 1) ذاكرة التطبيق وملفاته المؤقتة
            runCatching { application!!.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { getTempDir().listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { File(localDir(), "tmp").deleteRecursively() }

            // 2) مجلدات التشغيل المؤقتة في الطرفية
            runCatching {
                File(sandboxHomeDir(), "mahdi-run")
                    .listFiles()
                    ?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 3L * 24 * 3600 * 1000 }
                    ?.forEach { it.delete() }
            }

            // 3) داخل أوبنتو
            if (isTerminalInstalled()) {
                val script =
                    """
                    set +e
                    apt-get clean
                    rm -rf /var/lib/apt/lists/* 2>/dev/null
                    rm -rf /tmp/* /var/tmp/* 2>/dev/null
                    rm -rf /var/cache/apt/archives/*.deb 2>/dev/null
                    rm -rf /root/.cache/* 2>/dev/null
                    rm -rf /home/.mahdi/cache/pip 2>/dev/null
                    for d in /home/*/.cache /home/*/.npm/_cacache; do rm -rf "${'$'}d" 2>/dev/null; done
                    rm -f /root/.bash_history 2>/dev/null
                    echo MAHDI_CLEAN_OK
                    """.trimIndent()

                runCatching {
                    val process = ubuntuProcess(command = listOf("/bin/bash", "-c", script))
                    val code = process.awaitExit(90_000L)
                    onLog("clean exit=${code ?: "timeout"}")
                }
                    .onFailure { onLog("clean failed: ${it.message}") }
            }

            val after = sizeOf(application!!.filesDir.parentFile ?: application!!.filesDir)
            (before - after).coerceAtLeast(0)
        }

    /** حجم ملف أو مجلد بالبايت، متجاهلًا الروابط الرمزية لتجنّب الدوران اللانهائي. */
    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0
        if (isSymlink(file)) return 0
        if (file.isFile) return file.length()

        var total = 0L
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.addLast(file)

        while (stack.isNotEmpty() && visited < MAX_ENTRIES) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                visited++
                if (visited >= MAX_ENTRIES) break
                if (isSymlink(child)) continue
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** صياغة مقروءة: بايت → ك.ب/م.ب/غ.ب. */
    fun format(bytes: Long): String {
        val locale = java.util.Locale.US
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(locale, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(locale, "%.1f MB", mb)
        return String.format(locale, "%.2f GB", mb / 1024.0)
    }

    private const val MAX_ENTRIES = 400_000
}
