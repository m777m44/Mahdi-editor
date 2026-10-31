package com.mahdi.terminal

import android.content.Context
import com.mahdi.file.child
import com.mahdi.file.sandboxDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.utils.getTempDir
import com.mahdi.utils.isMainThread
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NEXT_STAGE {
    NONE,
    EXTRACTION,
}

suspend fun CoroutineScope.getNextStage(context: Context): NEXT_STAGE =
    withContext(Dispatchers.IO) {
        if (isMainThread()) {
            throw RuntimeException("IO operation on the main thread")
        }

        val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
        val rootfsFiles =
            sandboxDir().listFiles()?.filter {
                it.absolutePath != sandboxHomeDir().absolutePath &&
                    it.absolutePath != sandboxDir().child("tmp").absolutePath
            } ?: emptyList()

        return@withContext if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
            NEXT_STAGE.NONE
        } else {
            NEXT_STAGE.EXTRACTION
        }
    }
