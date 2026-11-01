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

    // auto_deps: محرّك التعرّف التلقائي على مكتبات السكربتات وتثبيتها قبل التشغيل
    val internalFiles = listOf("init", "sandbox", "setup", "utils", "auto_deps", "mahdi_dl", "progress", "install_runtime", "banner", "aliases")
    internalFiles.forEach { setupAssetFile(it) }

    // 3.0.0: شريط التقدّم الأحادي السطر + مثبّت بيئات التشغيل + شعار MAHDI
    // (تُنسَخ عبر internalFiles أعلاه، ويُعاد كتابتها تلقائيًا عند تغيّر محتواها)

    // مثبّت حزم بايثون الذكي (يعرض نسبة تقدّم حقيقية في التطبيق)
    setupPythonFile("mahdi_pip")

    application!!.assets.list("terminal/lsp")?.forEach { setupLspFile(it.removeSuffix(".sh")) }
}

fun setupLspFile(fileName: String) = setupAssetFile("lsp/$fileName")

/** ينسخ ملف بايثون من الأصول إلى مجلد أوامر الطرفية (مثل `mahdi_pip.py`). */
fun setupPythonFile(fileName: String) = setupAssetFile(fileName, "$fileName.py")

fun setupAssetFile(fileName: String, assetPath: String = "$fileName.sh") {
    val assetText =
        runCatching {
            application!!.assets.open("terminal/$assetPath").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

    with(localBinDir().child(fileName)) {
        parentFile?.mkdir()
        // يُكتب الملف إن كان غائبًا أو تغيّر محتواه، حتى تصل إصلاحات السكربتات إلى التثبيتات القائمة
        val currentText = if (exists()) runCatching { readText() }.getOrNull() else null
        if (currentText != assetText) {
            createFileIfNot()
            writeText(assetText)
        }
    }
}
