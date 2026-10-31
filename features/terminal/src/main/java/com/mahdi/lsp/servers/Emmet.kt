package com.mahdi.lsp.servers

import android.content.Context
import com.mahdi.exec.NpmUtils
import com.mahdi.exec.isTerminalInstalled
import com.mahdi.file.BuiltinFileType
import com.mahdi.file.child
import com.mahdi.file.localBinDir
import com.mahdi.file.sandboxDir
import com.mahdi.lsp.LspConnectionConfig
import com.mahdi.lsp.ScriptedLspServer

object Emmet : ScriptedLspServer() {
    override val id: String = "emmet"
    override val languageName: String = "Emmet"
    override val serverName = "emmet-language-server"
    override val supportedExtensions = BuiltinFileType.HTML.extensions + BuiltinFileType.HTMX.extensions
    override val icon = BuiltinFileType.HTML.icon

    override val installScript = localBinDir().child("lsp/emmet")
    override val installId = "Emmet language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun hasUpdate(context: Context): Boolean {
        return NpmUtils.hasUpdate("@olrtg/emmet-language-server")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
