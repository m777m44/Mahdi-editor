package com.mahdi.terminal

import com.mahdi.file.child
import com.mahdi.file.createFileIfNot
import com.mahdi.file.localBinDir
import com.mahdi.file.localDir
import com.mahdi.file.sandboxDir
import com.mahdi.utils.application

fun setupTerminalFiles() {
    if (sandboxDir().exists().not() || localBinDir().exists().not()) return

    with(localDir().child("stat")) {
        if (exists().not()) {
            createFileIfNot()
        }
    }

    with(localDir().child("vmstat")) {
        if (exists().not()) {
            createFileIfNot()
        }
    }

    with(localBinDir().child("termux-x11")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(application!!.assets.open("terminal/termux-x11.sh").bufferedReader().use { it.readText() })
        }
    }

    val internalFiles = listOf("init", "sandbox", "setup", "utils")
    internalFiles.forEach { setupAssetFile(it) }

    application!!.assets.list("terminal/lsp")?.forEach { setupLspFile(it.removeSuffix(".sh")) }
}

fun setupLspFile(fileName: String) = setupAssetFile("lsp/$fileName")

fun setupAssetFile(fileName: String) {
    with(localBinDir().child(fileName)) {
        parentFile?.mkdir()
        if (exists().not()) {
            createFileIfNot()
            writeText(application!!.assets.open("terminal/$fileName.sh").bufferedReader().use { it.readText() })
        }
    }
}
