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

object Markdown : ScriptedLspServer() {
    override val id: String = "markdown"
    override val languageName: String = "Markdown"
    override val serverName = "vscode-markdown-language-server"
    override val supportedExtensions = BuiltinFileType.MARKDOWN.extensions
    override val icon = BuiltinFileType.MARKDOWN.icon

    override val installScript = localBinDir().child("lsp/markdown")
    override val installId = "Markdown language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun hasUpdate(context: Context): Boolean {
        return NpmUtils.hasUpdate("vscode-langservers-extracted")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
