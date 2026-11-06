package com.mahdi.terminal

import android.content.Context
import android.net.ConnectivityManager
import com.mahdi.file.localDir
import java.io.File

/**
 * كتابة `resolv.conf` الحقيقي في كل جلسة (6.1.0).
 *
 * قبل 6.1.0 كان `resolv.conf` يُكتب مرة واحدة فقط داخل `setup.sh`، و`setup.sh`
 * لا يعمل إلا عند فكّ ضغط الجذر لأول مرة. أي مستخدم عنده الجذر مفكوكًا مسبقًا
 * كان يبقى على خادم أسماء واحد (8.8.8.8) قد يكون محجوبًا أو تغيّر بعد تبديل
 * الشبكة/الواي‑فاي — فتفشل pip بـ:
 *
 *   WARNING: Retrying … Temporary failure in name resolution
 *
 * الحل: نقرأ خوادم DNS من النظام (`LinkProperties`) قبل كل جلسة ونكتبها،
 * مع خوادم احتياطية عامة، ومع `options timeout:2 attempts:2` لتقليل الانتظار.
 *
 * تنبيه مقصود: لا نُنشئ `local/sandbox` ولا `sandbox/etc` من هنا. تطبيق
 * يقرّر «هل فُكّ ضغط الجذر؟» من قائمة محتويات `sandbox/`، فإنشاء مجلد مسبقًا
 * يُفسد قرار فكّ الضغط عند المستخدم الجديد. نكتب فقط إذا كان `etc` موجودًا.
 */
object TerminalDns {

    /** خوادم احتياطية تُضاف دائمًا حتى لو تعذّرت قراءة خوادم النظام. */
    private val FALLBACK = listOf("8.8.8.8", "1.1.1.1", "8.8.4.4")

    fun refresh(context: Context) {
        runCatching { write(context) }
    }

    private fun write(context: Context) {
        val etc = File(localDir(context), "sandbox/etc")
        // الجذر لم يُفكّ بعد: لا نلمس شيئًا (setup.sh سيكتب الملف عند الفكّ)
        if (!etc.isDirectory) return

        val servers = (systemDns(context) + FALLBACK).filter { it.isNotBlank() }.distinct()

        val content =
            buildString {
                servers.forEach { append("nameserver ").append(it).append('\n') }
                append("options timeout:2 attempts:2 single-request-reopen\n")
            }

        val target = File(etc, "resolv.conf")
        val current = runCatching { if (target.exists()) target.readText() else "" }.getOrDefault("")
        if (current == content) return

        val tmp = File(etc, "resolv.conf.tmp")
        runCatching {
                tmp.writeText(content)
                if (!tmp.renameTo(target)) {
                    target.writeText(content)
                    tmp.delete()
                }
            }
            .onFailure { runCatching { tmp.delete() } }
    }

    /** خوادم DNS الحقيقية من الشبكة النشطة، ثم من كل شبكة متصلة كاحتياط. */
    private fun systemDns(context: Context): List<String> {
        return runCatching {
                val cm =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        ?: return emptyList()

                val active = cm.activeNetwork
                val fromActive =
                    active?.let { cm.getLinkProperties(it)?.dnsServers?.mapNotNull { s -> s.hostAddress } }
                        ?: emptyList()

                if (fromActive.isNotEmpty()) {
                    fromActive
                } else {
                    cm.allNetworks
                        .flatMap { n ->
                            cm.getLinkProperties(n)?.dnsServers?.mapNotNull { s -> s.hostAddress }
                                ?: emptyList()
                        }
                        .filter { it.isNotBlank() }
                }
            }
            .getOrDefault(emptyList())
    }
}
