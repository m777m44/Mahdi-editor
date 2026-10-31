package com.mahdi.commands.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mahdi.DefaultScope
import com.mahdi.commands.EditorFileActionContext
import com.mahdi.commands.EditorFileCommand
import com.mahdi.file.FileWrapper
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.utils.toast
import kotlinx.coroutines.launch

class ShareCommand : EditorFileCommand() {
    override val id: String = "editor.share"

    override fun getLabel(): String = strings.share.getString()

    override fun execute(context: EditorFileActionContext) {
        val activity = context.currentActivity
        val file = context.file

        DefaultScope.launch {
            if (file.getAbsolutePath().contains(activity.filesDir.parentFile!!.absolutePath)) {
                toast(strings.permission_denied)
                return@launch
            }

            val fileUri =
                if (file is FileWrapper) {
                    FileProvider.getUriForFile(activity as Context, "${activity.packageName}.fileprovider", file.file)
                } else {
                    file.toUri()
                }

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = activity.contentResolver.getType(fileUri) ?: "*/*"
                    setDataAndType(fileUri, activity.contentResolver.getType(fileUri) ?: "*/*")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

            activity.startActivity(Intent.createChooser(intent, "Share file"))
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.send)
}
