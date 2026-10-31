package com.mahdi.runner

import android.content.Context
import com.mahdi.file.child
import com.mahdi.file.createDirIfNot
import com.mahdi.file.localDir
import com.mahdi.utils.application
import java.io.File

fun runnerDir(context: Context = application!!): File {
    return localDir(context).child("runners").createDirIfNot()
}
