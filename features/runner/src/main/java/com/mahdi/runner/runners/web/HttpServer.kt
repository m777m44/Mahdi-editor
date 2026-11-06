package com.mahdi.runner.runners.web

import android.content.Context
import com.mahdi.file.FileObject
import com.mahdi.file.resolve
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.utils.isDarkTheme
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLConnection
import kotlinx.coroutines.runBlocking

class HttpServer(
    val context: Context,
    port: Int,
    val root: FileObject,
    val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null,
) : NanoHTTPD(port) {
    init {
        start()
    }

    private companion object {
        /**
         * صفحات أكبر من هذا تُخدم كما هي (لا تُقرأ في الذاكرة) — المعاينة تبقى آمنة.
         * ملاحظة: `MIME_HTML` غير معرّف هنا عمدًا؛ فهو ثابت موروث من `NanoHTTPD`.
         */
        const val MAX_INJECT_BYTES = 4 * 1024 * 1024L
    }

    override fun useGzipWhenAccepted(r: Response?): Boolean {
        return false
    }

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val uri = session!!.uri

            if (root.isFile()) {
                if (!root.exists()) return@runBlocking notFoundError()
                return@runBlocking serveFile(root)
            }

            var file = root.resolve(uri) ?: return@runBlocking notFoundError()
            if (file.isDirectory()) {
                file = file.getChild("index.html") ?: return@runBlocking notFoundError()
            }

            // Hook override
            serveHook?.invoke(file, session)?.let {
                return@runBlocking it
            }

            if (!file.exists()) return@runBlocking notFoundError()

            return@runBlocking serveFile(file)
        }
    }

    private suspend fun serveFile(file: FileObject): Response {
        return if (Settings.inject_eruda) {
            serveFileWithEruda(file)
        } else {
            serveFileWithoutEruda(file)
        }
    }

    /**
     * 6.0.0: يجعل **كل** صفحة HTML يخدمها الخادم متجاوبة مع شاشة الهاتف.
     *
     * سبب المشكلة في الجهاز الحقيقي: صفحة بلا `<meta name="viewport">` تُخطَّط
     * بعرض افتراضي (980px) فلا تنكمش على شاشة ٤٠٠dp، فتُقطع من اليمين ولا يمكن
     * التمرير أفقياً (أو يظهر شريط أفقي). الحل هنا لا يعتمد على أن يكتب المستخدم
     * الوسم بنفسه:
     *
     * 1. يُحقن وسم viewport إن كان غائبًا.
     * 2. تُحقن قاعدة CSS تصغيرية تُبقي الصور والجداول داخل العرض.
     *
     * يتمّ كل ذلك على الخادم، فتستفيد منه المعاينة وأي متصفح خارجي يفتح العنوان.
     */
    private fun injectResponsive(html: String): String {
        if (html.length.toLong() > MAX_INJECT_BYTES) return html

        val viewport =
            """<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">"""
        val css =
            """<style id="mahdi-responsive">html,body{margin:0;padding:0;max-width:100%;overflow-x:hidden;box-sizing:border-box}*,*::before,*::after{box-sizing:inherit}img,video,iframe,svg,table,pre{max-width:100%;height:auto}pre{overflow-x:auto}</style>"""

        val needsViewport = !html.contains("name=\"viewport\"", ignoreCase = true)
        val needsCss = !html.contains("id=\"mahdi-responsive\"")
        if (!needsViewport && !needsCss) return html

        val head = buildString {
            if (needsViewport) append(viewport)
            if (needsCss) append(css)
        }

        val headIndex = html.indexOf("</head>", ignoreCase = true)
        if (headIndex >= 0) return html.substring(0, headIndex) + head + html.substring(headIndex)

        val htmlTag = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlTag != null) {
            val end = htmlTag.range.last + 1
            return html.substring(0, end) + head + html.substring(end)
        }

        return head + html
    }

    private suspend fun serveFileWithEruda(file: FileObject): Response {
        return try {
            val mime = URLConnection.guessContentTypeFromName(file.getName()) ?: "application/octet-stream"
            if (mime != MIME_HTML) {
                return serveFileWithoutEruda(file)
            }

            val darkTheme = isDarkTheme(context)
            val erudaTheme = if (Settings.amoled && darkTheme) "AMOLED" else if (darkTheme) "Dark" else "Light"
            val erudaScript =
                """
                <!-- Injected code, this is not present in original code -->
                <script src="https://cdn.jsdelivr.net/npm/eruda"></script>
                <script>eruda.init({ theme: '$erudaTheme' });</script>
                <!-- Injected code, this is not present in original code -->
                """
                    .trimIndent()

            val html = file.getInputStream().bufferedReader().use { it.readText() }
            val withResponsive = injectResponsive(html)
            val injected =
                if (withResponsive.contains("<head>", ignoreCase = true)) {
                    withResponsive.replace("<head>", "<head>$erudaScript", ignoreCase = true)
                } else {
                    erudaScript + withResponsive
                }

            newFixedLengthResponse(Status.OK, mime, injected)
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private suspend fun serveFileWithoutEruda(file: FileObject): Response {
        return try {
            val mime = URLConnection.guessContentTypeFromName(file.getName()) ?: "application/octet-stream"

            // صفحات HTML: تُقرأ وتُحقن ثم تُرسل، لضمان التجاوب مع كل شاشة (6.0.0)
            if (mime == MIME_HTML && file.length() <= MAX_INJECT_BYTES) {
                val html = file.getInputStream().bufferedReader().use { it.readText() }
                return newFixedLengthResponse(Status.OK, mime, injectResponsive(html))
            }

            newFixedLengthResponse(
                Status.OK,
                mime,
                file.getInputStream(),
                file.length(),
            )
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private fun notFoundError(): Response = newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not found")

    private fun forbiddenError(file: FileObject): Response =
        newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "403 Forbidden: Cannot read file ${file.getName()}")

    private fun internalError(e: Exception): Response =
        newFixedLengthResponse(
            Status.INTERNAL_ERROR,
            "text/plain",
            "500 Internal server error: ${e.localizedMessage ?: strings.unknown.getString()}",
        )
}
