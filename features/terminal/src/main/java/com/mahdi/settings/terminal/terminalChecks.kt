package com.mahdi.settings.terminal

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.mahdi.exec.isTerminalInstalled
import com.mahdi.exec.ubuntuProcess
import com.mahdi.file.child
import com.mahdi.file.sandboxDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.resources.strings
import com.mahdi.utils.application
import com.mahdi.utils.getTempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * فحوص صحة الطرفية (4.3.0) — أُعيد بناؤها بالكامل.
 *
 * سبب الخلل القديم: كل فحص كان ينفّذ `Process.waitFor()` **بلا مهلة** على الخيط
 * الرئيسي. فحص الشبكة عند تعذّر DNS كان ينتظر إلى الأبد، فيبقى مؤشّره دائريًا
 * أزرق، وكل الفحوص التالية تبقى «؟» لأنها لم تبدأ أصلًا. وأي فحص بطيء كان
 * يجمّد الواجهة كلها.
 *
 * القواعد الآن:
 * 1. **كل فحص له مهلة صريحة** — إن تجاوزها يُقتل العملية ويُعلَن الفحص فاشلًا
 *    مع رسالة واضحة بدل الانتظار الأبدي.
 * 2. **التشغيل على خيط IO** — الواجهة لا تتجمّد أبدًا.
 * 3. **فحص تابع لا يوقف الذي بعده** — حتى لو فشل، تُكمَل بقية الفحوص.
 * 4. **سبب الفشل مكتوب** داخل سجلّ الفحص، مع خطوة الإصلاح المقترحة.
 */
@Composable
fun terminalChecks(): SnapshotStateList<Check> {
    val checkProot = stringResource(strings.check_proot)
    val checkSystemShell = stringResource(strings.check_system_shell)
    val checkStoragePermissions = stringResource(strings.check_storage_permissions)
    val checkUbuntu = stringResource(strings.check_ubuntu)
    val checkNetworkAccess = stringResource(strings.check_network_access)
    val checkAbnormalities = stringResource(strings.check_abnormalities)

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    /** نصّ ثنائي اللغة لسجلّ الفحص. */
    fun t(ar: String, en: String): String = if (isArabic) ar else en

    return remember {
        mutableStateListOf(
            Check(
                label = checkProot,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        val libproot = File(application!!.applicationInfo.nativeLibraryDir, "libproot.so")
                        val prootloader = File(application!!.applicationInfo.nativeLibraryDir, "libloader.so")
                        val prootloader32 = File(application!!.applicationInfo.nativeLibraryDir, "libloader32.so")

                        printLog(t("ملف PRoot: ${libproot.exists()}", "PRoot exists: ${libproot.exists()}"))
                        printLog(t("قابل للقراءة: ${libproot.canRead()}", "PRoot readable: ${libproot.canRead()}"))
                        printLog(t("قابل للتنفيذ: ${libproot.canExecute()}", "PRoot executable: ${libproot.canExecute()}"))
                        printLog(t("محمّل 64-بت: ${prootloader.exists()}", "PRoot loader: ${prootloader.exists()}"))
                        printLog(t("محمّل 32-بت: ${prootloader32.exists()}", "32bit loader: ${prootloader32.exists()}"))

                        if (!libproot.exists()) {
                            printLog(t("✘ PRoot غير موجود — أعد تثبيت التطبيق", "✘ PRoot missing — reinstall the app"))
                            return@withContext false
                        }

                        var exitCode: Int? = null
                        try {
                            printLog(t("تشغيل بيئة مؤقتة للاختبار…", "Starting a temporary sandbox…"))
                            val process =
                                ProcessBuilder(libproot.absolutePath, "-0", "-r", "/", "true")
                                    .apply {
                                        environment()["PROOT_TMP_DIR"] = getTempDir().absolutePath
                                        environment()["PROOT_LOADER"] = prootloader.absolutePath
                                        environment()["PROOT_LOADER_32"] = prootloader32.absolutePath
                                    }
                                    .start()

                            exitCode = process.awaitExit(PROOT_TIMEOUT_MS)
                            if (exitCode == null) {
                                printLog(t("✘ لم يستجب خلال ${PROOT_TIMEOUT_MS / 1000} ثانية", "✘ Timed out after ${PROOT_TIMEOUT_MS / 1000}s"))
                            } else {
                                printLog(t("رمز الخروج: $exitCode", "Exit code: $exitCode"))
                            }
                        } catch (e: Exception) {
                            printLog(t("خطأ أثناء تشغيل PRoot: ${e.message}", "PRoot error: ${e.message}"))
                        }

                        libproot.exists() && exitCode == 0
                    }
                },
            ),
            Check(
                label = checkSystemShell,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        val shell = File("/system/bin/sh")
                        val shell1 = File("/bin/sh")
                        printLog(t("‏/system/bin/sh موجود: ${shell.exists()}", "/system/bin/sh exists: ${shell.exists()}"))
                        printLog(t("‏/bin/sh موجود: ${shell1.exists()}", "/bin/sh exists: ${shell1.exists()}"))
                        printLog(t("قابل للتنفيذ: ${shell.canExecute()}", "Shell executable: ${shell.canExecute()}"))

                        val exitcode =
                            try {
                                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "true"))
                                val code = process.awaitExit(SHELL_TIMEOUT_MS)
                                printLog(t("رمز الخروج: ${code ?: "مهلة"}", "Exit code: ${code ?: "timeout"}"))
                                code
                            } catch (e: Exception) {
                                printLog(t("تعذّر تشغيل الصدفة: ${e.message}", "Shell failed: ${e.message}"))
                                null
                            }

                        exitcode == 0 && shell.exists() && shell1.exists() && shell.canExecute()
                    }
                },
            ),
            Check(
                label = checkStoragePermissions,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        val filesDir = application!!.filesDir
                        val totalSpace = filesDir.totalSpace / (1024 * 1024)
                        val freeSpace = filesDir.freeSpace / (1024 * 1024)
                        printLog(t("المساحة الكلية: $totalSpace م.ب", "Total space: $totalSpace MB"))
                        printLog(t("المساحة الحرة: $freeSpace م.ب", "Free space: $freeSpace MB"))
                        printLog(t("مجلد التطبيق قابل للكتابة: ${filesDir.canWrite()}", "Files dir writable: ${filesDir.canWrite()}"))

                        val sandboxHome = sandboxHomeDir()
                        printLog(t("مجلد المنزل: ${sandboxHome.absolutePath}", "Home: ${sandboxHome.absolutePath}"))
                        printLog(t("قابل للكتابة: ${sandboxHome.canWrite()}", "Home writable: ${sandboxHome.canWrite()}"))

                        if (freeSpace < 500) {
                            printLog(t("⚠ المساحة الحرة أقل من ٥٠٠ م.ب — قد يفشل تثبيت الحزم", "⚠ Less than 500 MB free — installs may fail"))
                        }

                        freeSpace > 100 && filesDir.canWrite() && sandboxHome.canWrite()
                    }
                },
            ),
            Check(
                label = checkUbuntu,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        if (!isTerminalInstalled()) {
                            printLog(t("أوبنتو غير مثبّتة — تخطٍّ", "Ubuntu not installed — skipped"))
                            return@withContext true
                        }

                        val rootfs = sandboxDir()
                        printLog(t("مسار الصورة: ${rootfs.absolutePath}", "RootFS: ${rootfs.absolutePath}"))

                        val bash = rootfs.child("bin/bash")
                        printLog(t("‏bash موجود: ${bash.exists()} · قابل للتنفيذ: ${bash.canExecute()}", "bash exists: ${bash.exists()} · executable: ${bash.canExecute()}"))

                        val osRelease = rootfs.child("etc/os-release")
                        if (osRelease.exists()) {
                            printLog(t("معلومات النظام:", "OS Release:"))
                            osRelease.readLines().take(4).forEach { printLog("  $it") }
                        }

                        printLog(t("اختبار تنفيذ أمر داخل أوبنتو…", "Testing Ubuntu execution…"))
                        var working = false
                        try {
                            val process = ubuntuProcess(command = listOf("/bin/sh", "-c", "echo MAHDI_OK && uname -s"))
                            val code = process.awaitExit(UBUNTU_TIMEOUT_MS)
                            if (code == null) {
                                printLog(t("✘ لم تستجب أوبنتو خلال ${UBUNTU_TIMEOUT_MS / 1000} ثانية", "✘ Ubuntu timed out after ${UBUNTU_TIMEOUT_MS / 1000}s"))
                            } else {
                                printLog(t("رمز الخروج: $code", "Exit code: $code"))
                                working = code == 0
                            }
                        } catch (e: Exception) {
                            printLog(t("فشل التنفيذ: ${e.message}", "Execution failed: ${e.message}"))
                        }

                        working
                    }
                },
            ),
            Check(
                label = checkNetworkAccess,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        if (!isTerminalInstalled()) {
                            printLog(t("أوبنتو غير مثبّتة — تخطٍّ", "Ubuntu not installed — skipped"))
                            return@withContext true
                        }

                        val deviceOnline = deviceHasNetwork()
                        printLog(
                            t(
                                "اتصال الجهاز بالإنترنت: ${if (deviceOnline) "متصل" else "غير متصل"}",
                                "Device network: ${if (deviceOnline) "online" else "offline"}",
                            )
                        )

                        // 1) حلّ الأسماء داخل أوبنتو — بمهلة، لا انتظار أبدي
                        printLog(t("فحص حلّ الأسماء (github.com)…", "Checking DNS (github.com)…"))
                        var dnsOk = false
                        try {
                            val dnsProcess = ubuntuProcess(command = listOf("getent", "hosts", "github.com"))
                            val code = dnsProcess.awaitExit(DNS_TIMEOUT_MS)
                            when {
                                code == null ->
                                    printLog(
                                        t(
                                            "✘ لم ينتهِ فحص DNS خلال ${DNS_TIMEOUT_MS / 1000} ثانية — أُوقف",
                                            "✘ DNS check timed out after ${DNS_TIMEOUT_MS / 1000}s — stopped",
                                        )
                                    )
                                code == 0 -> {
                                    dnsOk = true
                                    printLog(t("✓ حلّ الأسماء يعمل", "✓ DNS resolution works"))
                                }
                                else -> printLog(t("✘ فشل حلّ الأسماء (رمز $code)", "✘ DNS failed (code $code)"))
                            }
                        } catch (e: Exception) {
                            printLog(t("تعذّر فحص الشبكة: ${e.message}", "Network check failed: ${e.message}"))
                        }

                        // 2) ملفات الإعداد دائمًا تُفحص — هي السبب الأشيع
                        val resolvConf = sandboxDir().child("etc/resolv.conf")
                        if (resolvConf.exists()) {
                            val lines = runCatching { resolvConf.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
                            if (lines.isEmpty()) {
                                printLog(t("✘ ‏/etc/resolv.conf فارغ", "✘ /etc/resolv.conf is empty"))
                            } else {
                                printLog(t("‏/etc/resolv.conf:", "/etc/resolv.conf:"))
                                lines.take(3).forEach { printLog("  $it") }
                            }
                        } else {
                            printLog(t("✘ ‏/etc/resolv.conf غير موجود", "✘ /etc/resolv.conf is MISSING"))
                        }

                        if (!dnsOk) {
                            printLog(
                                t(
                                    "→ الحل: اضغط «إصلاح تلقائي» لإعادة كتابة إعدادات الشبكة",
                                    "→ Fix: tap “Auto repair” to rewrite the network config",
                                )
                            )
                        }
                        dnsOk
                    }
                },
            ),
            Check(
                label = checkAbnormalities,
                run = { printLog ->
                    withContext(Dispatchers.IO) {
                        if (!isTerminalInstalled()) {
                            printLog(t("أوبنتو غير مثبّتة — تخطٍّ", "Ubuntu not installed — skipped"))
                            return@withContext true
                        }

                        var problems = 0

                        val probe: suspend (String, String, String) -> Unit = { nameAr, nameEn, command ->
                            val label = t(nameAr, nameEn)
                            try {
                                val process = ubuntuProcess(command = listOf("/bin/sh", "-c", command))
                                val ok = process.awaitExit(PROBE_TIMEOUT_MS) == 0
                                printLog((if (ok) "✓ " else "✘ ") + label)
                                if (!ok) problems++
                            } catch (e: Exception) {
                                printLog("✘ $label — ${e.message}")
                                problems++
                            }
                        }

                        probe("‏/tmp قابل للكتابة", "/tmp writable", "touch /tmp/.mahdi_probe && rm -f /tmp/.mahdi_probe")
                        probe(
                            "مجلد المنزل قابل للكتابة",
                            "Home writable",
                            "touch ~/.mahdi_probe 2>/dev/null || touch /root/.mahdi_probe; rm -f ~/.mahdi_probe /root/.mahdi_probe",
                        )
                        probe("ملفات الشبكة سليمة", "Network files present", "test -s /etc/resolv.conf && test -s /etc/hosts")
                        probe(
                            "مدير الحزم غير مقفول",
                            "Package manager unlocked",
                            "test ! -e /var/lib/dpkg/lock-frontend || ! fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1",
                        )

                        // مساحة حرة داخل الصورة
                        val freeMb = sandboxDir().usableSpace / (1024 * 1024)
                        printLog(t("المساحة المتاحة داخل الصورة: $freeMb م.ب", "Space available for the image: $freeMb MB"))
                        if (freeMb < 300) {
                            printLog(t("⚠ المساحة ضيّقة — نظّف من «مساحة التخزين»", "⚠ Low space — clean up from “Storage”"))
                            problems++
                        }

                        if (problems == 0) {
                            printLog(t("✓ لا توجد حالات شاذة", "✓ No abnormalities detected"))
                        } else {
                            printLog(t("عدد الحالات الشاذة: $problems", "Abnormalities found: $problems"))
                            printLog(t("→ جرّب «إصلاح تلقائي»", "→ Try “Auto repair”"))
                        }

                        problems == 0
                    }
                },
            ),
        )
    }
}

private const val PROOT_TIMEOUT_MS = 15_000L
private const val SHELL_TIMEOUT_MS = 8_000L
private const val UBUNTU_TIMEOUT_MS = 30_000L
private const val DNS_TIMEOUT_MS = 20_000L
private const val PROBE_TIMEOUT_MS = 12_000L

/**
 * ينتظر انتهاء العملية بمهلة، ويقتلها إن تجاوزتها.
 *
 * @return رمز الخروج، أو `null` إن انتهت المهلة أو تعذّر الانتظار.
 */
internal fun Process.awaitExit(timeoutMs: Long): Int? {
    return try {
        if (waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            exitValue()
        } else {
            runCatching { destroyForcibly() }
            null
        }
    } catch (e: Exception) {
        runCatching { destroyForcibly() }
        null
    }
}

/** هل الجهاز متصل بشبكة فعلًا؟ (حتى لا نتّهم أوبنتو بشبكة مقطوعة عند المستخدم). */
private fun deviceHasNetwork(): Boolean {
    return runCatching {
        val cm = application!!.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
        .getOrDefault(false)
}

/**
 * إصلاح تلقائي لأشيع أعطال الطرفية (4.3.0).
 *
 * يُعيد كتابة `resolv.conf` و`hosts`، يضبط أذونات `/tmp`، يزيل أقفال dpkg
 * المعلّقة، ويُنشئ المجلدات المفقودة — كل ذلك داخل أوبنتو وبمهلة.
 */
object TerminalRepair {

    /** @return عدد الإصلاحات التي نجحت. */
    suspend fun repair(log: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            if (!isTerminalInstalled()) {
                log("Ubuntu not installed")
                return@withContext 0
            }

            val script =
                """
                set -e
                ok=0
                mkdir -p /tmp /var/tmp /etc/apt/apt.conf.d /var/cache/apt/archives /root /home
                chmod 1777 /tmp /var/tmp 2>/dev/null || true
                ok=$((ok+1))

                printf 'nameserver 8.8.8.8\nnameserver 8.8.4.4\nnameserver 1.1.1.1\n' > /etc/resolv.conf
                ok=$((ok+1))

                printf '127.0.0.1   localhost.localdomain localhost\n::1         localhost.localdomain localhost ip6-localhost ip6-loopback\n' > /etc/hosts
                ok=$((ok+1))

                rm -f /var/lib/dpkg/lock /var/lib/dpkg/lock-frontend /var/cache/apt/archives/lock 2>/dev/null || true
                ok=$((ok+1))

                [ -x /bin/bash ] || { echo "BASH_MISSING"; }
                [ -x /usr/bin/apt ] || { echo "APT_MISSING"; }

                echo "REPAIR_OK ${'$'}ok"
                """.trimIndent()

            try {
                val process = ubuntuProcess(command = listOf("/bin/bash", "-c", script))
                val code = process.awaitExit(60_000L)
                if (code == null) {
                    log("✘ Repair timed out")
                    return@withContext 0
                }
                log("exit=$code")
                if (code == 0) 4 else 0
            } catch (e: Exception) {
                log("✘ ${e.message}")
                0
            }
        }
}
