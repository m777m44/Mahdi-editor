package com.mahdi.runner.runners.web.html

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.activities.settings.settingsNavController
import com.mahdi.file.BuiltinFileType
import com.mahdi.file.FileObject
import com.mahdi.icons.Icon
import com.mahdi.resources.getFilledString
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.FileRunner
import com.mahdi.runner.runners.web.HttpServer
import com.mahdi.runner.runners.web.PreviewBrowserActivity
import com.mahdi.settings.Settings
import com.mahdi.utils.toast
import java.net.BindException

object HtmlRunner : FileRunner() {

    var httpServer: HttpServer? = null

    override val id = "html_preview"
    override val label = strings.html_preview.getString()
    override val description = strings.html_preview_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        val htmlExtensions = BuiltinFileType.HTML.extensions.joinToString("|")
        return Regex(".*\\.($htmlExtensions|svg)$").matches(fileObject.getName())
    }

    override val onConfigure: () -> Unit = { settingsNavController.get()?.navigate(SettingsRoutes.HtmlRunner.route) }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        stop()

        val port = Settings.http_server_port
        try {
            httpServer = HttpServer(activity, port, fileObject.getParentFile() ?: fileObject)
        } catch (_: BindException) {
            toast(strings.http_server_port_error.getFilledString(port))
            return
        }

        val address = "http://localhost:$port"
        toast(strings.http_server_at.getFilledString(address))

        val url = "$address/${fileObject.getName()}"
        // 2.8.0: المعاينة تُفتح داخل التطبيق في «متصفح المعاينة» افتراضيًا،
        // ويبقى خيار فتحها في متصفح النظام متاحًا من الإعدادات.
        if (Settings.launch_in_browser) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            activity.startActivity(intent)
            return
        }
        PreviewBrowserActivity.start(activity, url)
    }

    override fun getIcon(context: Context): Icon? {
        return BuiltinFileType.HTML.icon
    }

    override suspend fun isRunning(): Boolean = httpServer?.isAlive == true

    override suspend fun stop() {
        if (isRunning()) {
            httpServer?.closeAllConnections()
            httpServer?.stop()
        }
        httpServer = null
    }
}
