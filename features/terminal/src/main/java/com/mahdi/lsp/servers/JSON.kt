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
 * خادم لغة JSON: تحقّق من المخططات، إكمال المفاتيح، والتشخيص الفوري.
 *
 * يعمل من داخل الطرفية عبر `vscode-langservers-extracted` (الحزمة نفسها التي تضم خوادم
 * HTML وCSS)، فيتحقّق من `manifest.json` و`theme.json` وغيرها من ملفات JSON مباشرة
 * في المحرّر، ويظهر الخطأ تحت السطر قبل التثبيت أو النشر.
 */
object JSON : ScriptedLspServer() {
    override val id: String = "json"
    override val languageName: String = "JSON"
    override val serverName = "vscode-json-language-server"
    override val supportedExtensions = BuiltinFileType.JSON.extensions
    override val icon = BuiltinFileType.JSON.icon

    override val installScript = localBinDir().child("lsp/json")
    override val installId = "JSON language server"

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
