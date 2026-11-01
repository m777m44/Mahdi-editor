package com.mahdi.ext

import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * أمر مسجَّل يمكن للإضافة إضافته إلى لوحة الأوامر (3.0.0).
 *
 * الأمر لا ينفّذ كودًا غريبًا داخل العملية: هو إمّا تنقّل إلى شاشة داخلية (`route`)
 * أو إجراء Kotlin محلي (`action`) تقدّمه الإضافة المدمجة.
 */
data class ExtensionCommand(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val extensionId: String,
    val route: String? = null,
    val action: (() -> Unit)? = null,
)

/** عنصر في شريط الحالة السفلي تكتبه إضافة (عدد الكلمات، فرع Git، حالة الشبكة…). */
data class StatusBarItem(
    val id: String,
    val textAr: String,
    val textEn: String,
    val extensionId: String,
    val priority: Int = 0,
)

/** نتيجة فحص ملف `manifest.json`. */
data class ManifestReport(
    val ok: Boolean,
    val errors: List<String> = emptyList(),
    val id: String? = null,
    val name: String? = null,
    val version: String? = null,
)

/**
 * `ExtensionAPI` — الواجهة التي تتحدّث بها الإضافات إلى التطبيق (3.0.0).
 *
 * ثلاث قنوات فقط، وكلها تُحفظ في الذاكرة بلا صلاحيات إضافية:
 * 1. **الأوامر** [registerCommand] — تظهر في لوحة الأوامر وتُستدعى بتنقّل أو إجراء.
 * 2. **شريط الحالة** [addStatusBarItem] — نصّ قصير يظهر أسفل المحرّر.
 * 3. **فحص البيان** [validateManifest] — التحقّق من `manifest.json` وفق المخطّط [manifestSchema].
 *
 * الإضافات المدمجة [BuiltinExtensions] تسجّل نفسها تلقائيًا عند أول قراءة، وتعمل بلا إنترنت.
 */
object ExtensionApi {
    const val SCHEMA_VERSION: Int = 3

    /** أصغر نسخة تطبيق تقبل الإضافات المبنية على هذا المخطّط. */
    const val MIN_APP_VERSION: Int = 15

    /**
     * مخطّط `manifest.json` بصيغة JSON Schema (مسجَّل داخل التطبيق للتوثيق والفحص).
     *
     * الحقول الإلزامية: `id`, `name`, `version`, `mainClass`.
     */
    val manifestSchema: String =
        """
        {
          "title": "Mahdi-Editor extension manifest",
          "type": "object",
          "required": ["id", "name", "version", "mainClass"],
          "properties": {
            "id": { "type": "string", "pattern": "^[a-z0-9][a-z0-9._-]{2,63}$" },
            "name": { "type": "string", "minLength": 1 },
            "version": { "type": "string", "pattern": "^[0-9]+\\.[0-9]+\\.[0-9]+$" },
            "mainClass": { "type": "string", "minLength": 1 },
            "description": { "type": "string" },
            "author": { "type": "object" },
            "license": { "type": "string" },
            "minAppVersion": { "type": "integer" },
            "type": { "type": "string", "enum": ["extension", "theme", "icon_pack", "snippet_pack"] },
            "commands": { "type": "array", "items": { "type": "object" } },
            "statusBar": { "type": "array", "items": { "type": "object" } },
            "dependencies": { "type": "array", "items": { "type": "string" } }
          }
        }
        """.trimIndent()

    private val _commands = MutableStateFlow<List<ExtensionCommand>>(emptyList())

    /** كل الأوامر المسجّلة الآن (المدمجة + أي إضافة خارجية). */
    val commands: StateFlow<List<ExtensionCommand>> = _commands.asStateFlow()

    private val _statusBarItems = MutableStateFlow<List<StatusBarItem>>(emptyList())

    /** عناصر شريط الحالة الحالية مرتّبة بالأولوية. */
    val statusBarItems: StateFlow<List<StatusBarItem>> = _statusBarItems.asStateFlow()

    /** يسجّل أمرًا (يستبدل أمرًا سابقًا بنفس المعرّف حتى لا تتكرّر العناصر). */
    fun registerCommand(command: ExtensionCommand) {
        _commands.value = _commands.value.filterNot { it.id == command.id } + command
    }

    fun unregisterCommand(id: String) {
        _commands.value = _commands.value.filterNot { it.id == id }
    }

    /** يزيل كل أوامر إضافة عند تعطيلها. */
    fun unregisterExtension(extensionId: String) {
        _commands.value = _commands.value.filterNot { it.extensionId == extensionId }
        _statusBarItems.value = _statusBarItems.value.filterNot { it.extensionId == extensionId }
    }

    fun addStatusBarItem(item: StatusBarItem) {
        _statusBarItems.value =
            (_statusBarItems.value.filterNot { it.id == item.id } + item).sortedByDescending { it.priority }
    }

    fun removeStatusBarItem(id: String) {
        _statusBarItems.value = _statusBarItems.value.filterNot { it.id == id }
    }

    fun commandById(id: String): ExtensionCommand? = _commands.value.firstOrNull { it.id == id }

    /** يمسح كل ما سجّلته الإضافات (يُستدعى عند إعادة تحميل الإضافات). */
    fun reset() {
        _commands.value = emptyList()
        _statusBarItems.value = emptyList()
    }

    /**
     * يفحص `manifest.json` نصًّا ويعيد تقريرًا عربيًا واضحًا.
     *
     * لا يفشل الاستدعاء أبدًا — أي استثناء تحليل يتحوّل إلى `ok = false` مع رسالة.
     */
    fun validateManifest(json: String): ManifestReport {
        if (json.isBlank()) return ManifestReport(ok = false, errors = listOf("الملف فارغ"))
        val root =
            runCatching { JsonParser.parseString(json) }
                .getOrElse { error -> return ManifestReport(ok = false, errors = listOf("JSON غير صالح: ${error.message}")) }
        if (!root.isJsonObject) return ManifestReport(ok = false, errors = listOf("الجذر يجب أن يكون كائن JSON"))
        val obj = root.asJsonObject
        val errors = mutableListOf<String>()

        fun str(key: String): String? = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString

        val id = str("id")
        val name = str("name")
        val version = str("version")
        val mainClass = str("mainClass")

        if (id.isNullOrBlank()) {
            errors += "الحقل id مطلوب"
        } else if (!Regex("^[a-z0-9][a-z0-9._-]{2,63}$").matches(id)) {
            errors += "الحقل id يجب أن يكون أحرفًا لاتينية صغيرة وأرقامًا ونقاطًا فقط (٣–٦٤ حرفًا)"
        }

        if (name.isNullOrBlank()) errors += "الحقل name مطلوب"
        if (version.isNullOrBlank()) {
            errors += "الحقل version مطلوب"
        } else if (!Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$").matches(version)) {
            errors += "الحقل version يجب أن يكون على صيغة 1.0.0"
        }
        if (mainClass.isNullOrBlank()) errors += "الحقل mainClass مطلوب"

        val minApp = obj.get("minAppVersion")?.takeIf { it.isJsonPrimitive }?.asInt ?: MIN_APP_VERSION
        if (minApp > MIN_APP_VERSION) {
            errors += "الإضافة تتطلّب نسخة تطبيق أحدث ($minApp)"
        }

        val type = str("type")
        val allowedTypes = listOf("extension", "theme", "icon_pack", "snippet_pack")
        if (type != null && type !in allowedTypes) {
            errors += "الحقل type يجب أن يكون واحدًا من: ${allowedTypes.joinToString(" / ")}"
        }

        return ManifestReport(
            ok = errors.isEmpty(),
            errors = errors,
            id = id,
            name = name ?: id,
            version = version,
        )
    }
}
