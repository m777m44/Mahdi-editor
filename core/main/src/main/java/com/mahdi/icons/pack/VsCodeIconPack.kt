package com.mahdi.icons.pack

import android.content.Context
import com.mahdi.file.child
import com.mahdi.utils.application
import com.mahdi.utils.logError
import java.io.File

/**
 * 9.8.0 — حزمة أيقونات VS Code المدمجة (‏26 أيقونة: أنواع ملفات + مجلّد + مجلّد VS Code).
 *
 * الأيقونات في `assets/vscode/icons/` يُولّدها
 * `tools/vscode-import/import_assets.py` من أصول حزمة `vs-code-heroes`.
 * تُنسخ مرّة واحدة إلى `local/icon_pack/vscode-heroes/` ليعملها **نفس**
 * نظام حزم الأيقونات القائم ([IconPack]) بلا مسار موازٍ: نفس الفهرس، نفس
 * الإعداد (`Settings.icon_pack`)، نفس شاشة الحزم.
 *
 * لماذا النسخ لا القراءة من الأصول مباشرة؟ لأن `IconPack.getIconFileForName`
 * يعمل على `File` حقيقي ويفحص `exists()`، وCoil يحتاج ملفًا/‏Uri. النسخ مرّة
 * يجعل الحزمة أيضًا **قابلة للنسخ الاحتياطي والتصدير** كأي حزمة أخرى.
 */
object VsCodeIconPack {

    const val ID = "vscode-heroes"
    private const val ASSET_DIR = "vscode/icons"
    private const val MANIFEST = "manifest.json"
    private const val MARKER = ".builtin"

    /** هل النسخة المحلية موجودة ومكتملة؟ */
    fun isInstalled(): Boolean = installDir().resolve(MANIFEST).exists()

    /** مجلد التثبيت القياسي (تحت مجلد حزم الأيقونات نفسه). */
    fun installDir(): File = iconPackDir.child(ID)

    /**
     * ينسخ الأيقونات من الأصول إلى مجلد الحزم **إن لم تكن منسوخة**.
     *
     * آمنة للنداء المتكرّر: تتحقّق من ملف علامة مكتوب في نهاية النسخ، فلا
     * تُعاد الكتابة في كل إقلاع. تُرجع true إن كانت الحزمة جاهزة بعده.
     */
    fun ensureInstalled(context: Context = application ?: return false): Boolean {
        val dir = installDir()
        val marker = dir.resolve(MARKER)
        if (marker.exists() && dir.resolve(MANIFEST).exists()) return true

        return runCatching {
                // نسخة قد تكون جزئية من محاولة سابقة
                if (dir.exists()) dir.deleteRecursively()
                dir.mkdirs()

                val names = context.assets.list(ASSET_DIR).orEmpty()
                if (names.isEmpty()) return@runCatching false

                names.forEach { name ->
                    // لا ننسخ إلا JSON وSVG — أي شيء آخر في الأصول لا يخصّنا
                    if (!name.endsWith(".json") && !name.endsWith(".svg")) return@forEach
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        dir.resolve(name).outputStream().use { output -> input.copyTo(output) }
                    }
                }

                // علامة النسخ تُكتب أخيرًا: وجودها يعني أن النسخ اكتمل
                marker.writeText(ID)
                dir.resolve(MANIFEST).exists()
            }
            .onFailure { logError(it, "Failed to install built-in VS Code icon pack") }
            .getOrDefault(false)
    }

    /**
     * يقرأ `manifest.json` بصيغة حزم الأيقونات القياسية ([IconPackManifest]).
     *
     * الملف المُولَّد من `tools/vscode-import/import_assets.py` **هو نفس** صيغة
     * أي حزمة أيقونات مثبَّتة، فلا تحويل ولا لفّ: يُفكّ بـkotlinx.serialization
     * كما يفعل [IconPackManager.indexLocalPacks]. هذا يضمن أن الحزمة تعمل
     * بنفس الكود المُجرَّب بلا فرع خاص.
     */
    fun manifest(dir: File = installDir()): IconPackManifest? {
        val json = dir.resolve(MANIFEST)
        if (!json.exists()) return null
        return runCatching {
                SERIALIZER.decodeFromString<IconPackManifest>(json.readText())
            }
            .onFailure { logError(it, "Failed to read built-in icon pack manifest") }
            .getOrNull()
    }

    /** [LocalIconPack] جاهز للتسجيل في [currentIconPack] وتطبيقه فورًا. */
    fun localPack(dir: File = installDir()): LocalIconPack? {
        val parsed = manifest(dir) ?: return null
        return LocalIconPack(
            manifest = parsed,
            installPath = dir.absolutePath,
            createdAt = dir.resolve(MARKER).takeIf { it.exists() }?.lastModified(),
            updatedAt = System.currentTimeMillis(),
            initSize = null,
        )
    }

    /** هل الأصول موجودة في التطبيق أصلًا؟ (تُستدعى في اختبار الأصول). */
    fun assetNames(context: Context): List<String> = context.assets.list(ASSET_DIR)?.toList().orEmpty()

    private val SERIALIZER =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            allowTrailingComma = true
        }
}
