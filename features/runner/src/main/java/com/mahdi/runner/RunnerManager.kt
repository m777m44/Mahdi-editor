package com.mahdi.runner

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.mahdi.DefaultScope
import com.mahdi.events.Events
import com.mahdi.extension.api.MahdiExtensionPoint
import com.mahdi.motivation.Motivation as Motiv
import com.mahdi.file.FileObject
import com.mahdi.icons.Icon
import com.mahdi.runner.runners.MahdiProjectRunner
import com.mahdi.runner.runners.web.html.HtmlRunner
import com.mahdi.runner.runners.web.markdown.MarkdownRunner
import com.mahdi.settings.Settings
import com.mahdi.utils.errorDialog
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

object RunnerManager {

    private val _extensionRunners = mutableStateListOf<Runner>()

    val extensionRunners: List<Runner>
        get() = _extensionRunners.toList()

    private val _builtinRunners = mutableStateListOf(HtmlRunner, MarkdownRunner, MahdiProjectRunner)
    val builtinRunners: List<Runner>
        get() = _builtinRunners.toList()

    @MahdiExtensionPoint
    fun registerRunner(runner: Runner) {
        if (!_extensionRunners.contains(runner)) {
            _extensionRunners.add(runner)
        }
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun addBuiltInRunner(vararg servers: Runner) {
        _builtinRunners.addAll(servers)
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun removeBuiltInRunner(vararg servers: Runner) {
        _builtinRunners.removeAll(servers.toSet())
    }

    @MahdiExtensionPoint
    fun unregisterRunner(runner: Runner) {
        _extensionRunners.remove(runner)
    }

    fun isRunnable(fileObject: FileObject?, projectRoot: FileObject?): Boolean {
        return getAvailableRunners(fileObject, projectRoot).isNotEmpty()
    }

    fun getAvailableRunners(fileObject: FileObject?, projectRoot: FileObject?): List<Runner> {
        val result = mutableListOf<Runner>()

        val runners = builtinRunners + extensionRunners + ShellBasedRunners.runners
        runners.forEach { runner ->
            if (runner.isEnabled()) {
                when (runner) {
                    is FileRunner if fileObject != null && runner.matcher(fileObject) -> {
                        result.add(runner)
                    }

                    is ProjectRunner if projectRoot != null && runner.matcher(projectRoot) -> {
                        result.add(runner)
                    }
                }
            }
        }

        return result
    }

    fun run(
        activity: Activity,
        fileObject: FileObject?,
        projectRoot: FileObject?,
        forceSelection: Boolean = false,
        beforeRun: () -> Unit = {},
        onMultipleRunners: (List<RunnableOption>) -> Unit,
    ) {
        val availableRunners = getAvailableRunners(fileObject, projectRoot)

        if (availableRunners.isEmpty()) {
            errorDialog(activity, msg = "No runners available")
            return
        }

        if (availableRunners.size == 1 && !forceSelection) {
            DefaultScope.launch {
                beforeRun()
                val runner = availableRunners.first()
                if (runner is FileRunner && fileObject != null) {
                    runner.run(activity, fileObject)
                } else if (runner is ProjectRunner && projectRoot != null) {
                    runner.run(activity, projectRoot)
                }
                Settings.runs += 1
                runCatching { Motiv.onRun() }
                Events.publish(RunnerEvent.RunnerRun(runner))
            }
        } else {
            val options = availableRunners.map { runner ->
                object : RunnableOption {
                    override val label: String = runner.label

                    override fun getIcon(context: Context): Icon? = runner.getIcon(context)

                    override fun run(activity: Activity) {
                        DefaultScope.launch {
                            beforeRun()
                            if (runner is FileRunner && fileObject != null) {
                                runner.run(activity, fileObject)
                            } else if (runner is ProjectRunner && projectRoot != null) {
                                runner.run(activity, projectRoot)
                            }
                            Settings.runs += 1
                            runCatching { Motiv.onRun() }
                            Events.publish(RunnerEvent.RunnerRun(runner))
                        }
                    }
                }
            }
            onMultipleRunners.invoke(options)
        }
    }
}
