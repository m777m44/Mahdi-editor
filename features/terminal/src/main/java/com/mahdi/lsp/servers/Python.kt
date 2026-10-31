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

/**
 * خادم لغة بايثون: **Pyright** (خادم Microsoft الرسمي لـ Python، مكتوب بـTypeScript).
 *
 * يُثبَّت من داخل الطرفية عبر `npm` مثل بقية الخوادم، فيعمل الإكمال والتشخيص والانتقال إلى
 * التعريف دون الحاجة إلى أي تنزيل خارجي إضافي.
 */
object Python : ScriptedLspServer() {
    override val id: String = "python"
    override val languageName: String = "Python"
    override val serverName = "pyright-langserver"
    override val supportedExtensions = BuiltinFileType.PYTHON.extensions
    override val icon = BuiltinFileType.PYTHON.icon

    override val installScript = localBinDir().child("lsp/python")
    override val installId = "Python language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun hasUpdate(context: Context): Boolean {
        return NpmUtils.hasUpdate("pyright")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
