package com.mahdi.runner.runners.web.markdown

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.mahdi.file.BuiltinFileType
import com.mahdi.file.FileObject
import com.mahdi.icons.Icon
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.FileRunner
import com.mahdi.runner.runners.web.html.HtmlRunner
import java.lang.ref.WeakReference

var mdViewerRef = WeakReference<MDViewer?>(null)
var toPreviewFile: FileObject? = null

object MarkdownRunner : FileRunner() {

    override val id = "markdown_preview"
    override val label = strings.markdown_preview.getString()
    override val description = strings.markdown_preview_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        val markdownExtensions = BuiltinFileType.MARKDOWN.extensions.joinToString("|")
        return Regex(".*\\.($markdownExtensions)$").matches(fileObject.getName())
    }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        val intent = Intent(activity, MDViewer::class.java)
        toPreviewFile = fileObject
        activity.startActivity(intent)
    }

    override fun getIcon(context: Context): Icon? {
        return BuiltinFileType.MARKDOWN.icon
    }

    override suspend fun isRunning(): Boolean {
        return mdViewerRef.get() != null
    }

    override suspend fun stop() {
        HtmlRunner.httpServer?.let {
            it.closeAllConnections()
            if (it.isAlive) {
                it.stop()
            }
        }
        HtmlRunner.httpServer = null
        mdViewerRef.get()?.finish()
        mdViewerRef = WeakReference(null)
    }
}
