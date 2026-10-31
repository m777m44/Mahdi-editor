package com.mahdi.project

import android.content.Intent
import com.mahdi.DefaultScope
import com.mahdi.file.FileObject
import com.mahdi.filetree.FileAction
import com.mahdi.filetree.FileActionContext
import com.mahdi.filetree.FileActionType
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import kotlinx.coroutines.launch

object NewProjectAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.folder_managed)
    override val title = strings.new_project.getString()

    override suspend fun execute(context: FileActionContext) {
        DefaultScope.launch {
            val intent =
                Intent(context.context, ProjectCreatorActivity::class.java).apply { putExtra("root", context.file) }
            context.context.startActivity(intent)
        }
    }

    override suspend fun isSupported(file: FileObject, root: FileObject?): Boolean {
        return ProjectTemplateRegistry.categories.value.any { it.templates.isNotEmpty() }
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
