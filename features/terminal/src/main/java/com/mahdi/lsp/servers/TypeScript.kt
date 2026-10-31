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

object TypeScript : ScriptedLspServer() {
    override val id: String = "typescript"
    override val languageName: String = "TypeScript"
    override val serverName = "tsc"
    override val supportedExtensions =
        BuiltinFileType.JAVASCRIPT.extensions +
            BuiltinFileType.TYPESCRIPT.extensions +
            BuiltinFileType.JSX.extensions +
            BuiltinFileType.TSX.extensions
    override val icon = BuiltinFileType.TYPESCRIPT.icon

    override val installScript = localBinDir().child("lsp/typescript")
    override val installId = "TypeScript language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun hasUpdate(context: Context): Boolean {
        return NpmUtils.hasUpdate("typescript")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/$serverName", "--lsp", "--stdio"))
    }
}
