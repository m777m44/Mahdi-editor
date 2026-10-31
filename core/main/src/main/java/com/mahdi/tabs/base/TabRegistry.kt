package com.mahdi.tabs.base

import com.mahdi.activities.main.MainViewModel
import com.mahdi.extension.api.MahdiExtensionPoint
import com.mahdi.file.BuiltinFileType
import com.mahdi.file.FileObject
import com.mahdi.file.FileTypeManager
import com.mahdi.tabs.image.ImageTab

@MahdiExtensionPoint
fun interface TabFactory {
    fun createTab(file: FileObject, projectRoot: FileObject?, scopeRoot: FileObject?, viewModel: MainViewModel): Tab
}

object TabRegistry {
    private val registeredTabs = mutableMapOf<String, TabFactory>()

    @MahdiExtensionPoint
    fun registerTab(tabFactory: TabFactory, fileExtensions: List<String>) {
        fileExtensions.forEach { registeredTabs[it] = tabFactory }
    }

    @MahdiExtensionPoint
    fun unregisterTab(tabFactory: TabFactory) {
        registeredTabs.values.remove(tabFactory)
    }

    fun getTab(
        file: FileObject,
        projectRoot: FileObject?,
        scopeRoot: FileObject?,
        viewModel: MainViewModel,
        readOnly: Boolean,
        customTitle: String?,
    ): Tab {
        val ext = file.getExtension()
        val type = FileTypeManager.fromExtension(ext)

        if (registeredTabs.containsKey(ext)) {
            return registeredTabs[ext]!!.createTab(file, projectRoot, scopeRoot, viewModel)
        }

        return when (type) {
            BuiltinFileType.IMAGE -> ImageTab(file, projectRoot, scopeRoot)
            else ->
                viewModel.editorManager.createEditorTab(
                    file = file,
                    projectRoot = projectRoot,
                    scopeRoot = scopeRoot,
                    isReadOnly = readOnly,
                    customTitle = customTitle,
                )
        }
    }
}
