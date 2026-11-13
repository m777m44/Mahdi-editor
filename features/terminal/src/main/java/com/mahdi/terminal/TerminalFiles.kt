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
    val internalFiles =
        listOf(
            "init",
            "sandbox",
            "setup",
            "utils",
            "auto_deps",
            "mahdi_dl",
            "progress",
            "install_runtime",
            "banner",
            "aliases",
            // 5.5.0: محرّك «ثبّت المكتبة الناقصة وأعد التشغيل»
            "self_heal",
            // 9.3.0: محرّك «ثبّتها بأي طريقة» — pkg · Node · pip بسلاسل بدائل
            "rescue",
        )
    internalFiles.forEach { setupAssetFile(it) }

    // 8.4.0: أمر «mahdi-python» — معلومات المفسّر والانتقال بين الإصدارات
    setupAssetFile("mahdi-python", "mahdi_python.sh")

    // 8.5.0: أمر «mh» — مركز الأدوات: فحص البيئة mh doctor وإنشاء المشاريع mh new
    setupAssetFile("mh", "mahdi_tools.sh")

    // 3.0.0: شريط التقدّم الأحادي السطر + مثبّت بيئات التشغيل + شعار MAHDI
    // (تُنسَخ عبر internalFiles أعلاه، ويُعاد كتابتها تلقائيًا عند تغيّر محتواها)

    // مثبّت حزم بايثون الذكي (يعرض نسبة تقدّم حقيقية في التطبيق)
    setupPythonFile("mahdi_pip")

    // 5.5.0: محلّل stderr الذي يستخرج اسم المكتبة الناقصة
    setupPythonFile("mahdi_heal")

    // 6.1.0: الإصدار الحقيقي للتطبيق يكتبه التطبيق ليقرأه شعار الطرفية
    // (كان الرقم مكتوبًا يدويًا في banner.sh فبقي 3.1.0 سنوات)
    runCatching {
        val version = application!!.packageManager.getPackageInfo(application!!.packageName, 0).versionName ?: ""
        localBinDir().child(".mahdi_version").apply {
            createFileIfNot()
            writeText(version)
        }
    }

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
        // 8.5.0: الأدوات المنسوخة من الأصول (mh · mahdi-python · mahdi_pip.py · الاختصارات)
        // تُنفَّذ مباشرة من الطرفية، و`chmod -R +x` في sandbox.sh يعمل مرة واحدة عند
        // التثبيت فقط؛ فكانت أي أداة جديدة تُنسخ بعد تحديث التطبيق بلا صلاحية تنفيذ.
        runCatching { setExecutable(true, false) }
    }
}
