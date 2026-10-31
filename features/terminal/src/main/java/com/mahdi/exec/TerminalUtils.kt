package com.mahdi.exec

import android.app.Activity
import android.content.Intent
import com.mahdi.file.child
import com.mahdi.file.localDir
import com.mahdi.file.sandboxDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isTerminalInstalled(): Boolean {
    val rootfs =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    return localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
}

suspend fun isTerminalWorking(): Boolean =
    withContext(Dispatchers.IO) {
        val process = ubuntuProcess(command = arrayOf("true"))
        return@withContext process.waitFor() == 0
    }

fun launchTerminal(activity: Activity, terminalCommand: TerminalCommand) {
    pendingCommand = terminalCommand
    try {
        val intent = Intent().setClassName(activity, "com.mahdi.activities.terminal.Terminal")
        activity.startActivity(intent)
    } catch (_: Exception) {
        toast("Terminal feature is not available in this build")
    }
}
