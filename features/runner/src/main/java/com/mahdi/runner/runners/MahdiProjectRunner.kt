package com.mahdi.runner.runners

import android.app.Activity
import android.content.Context
import com.mahdi.TerminalLauncher
import com.mahdi.file.FileObject
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.ProjectRunner
import com.mahdi.workspace.MahdiManager
import kotlinx.coroutines.runBlocking

object MahdiProjectRunner : ProjectRunner() {
    override val id: String = "mahdi_project_runner"
    override val label: String = strings.project_runner.getString()

    override val description = strings.project_runner_desc.getString()

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }

    override fun matcher(projectRoot: FileObject): Boolean {
        return runBlocking { MahdiManager.getRunScript(projectRoot) != null }
    }

    override suspend fun run(activity: Activity, projectRoot: FileObject) {
        val runScript = MahdiManager.getRunScript(projectRoot) ?: return

        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf(runScript.getAbsolutePath()),
            id = strings.project_runner.getString(),
            workingDir = projectRoot.getAbsolutePath(),
        )
    }
}
