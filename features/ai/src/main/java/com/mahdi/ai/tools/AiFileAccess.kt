package com.mahdi.ai.tools

import com.mahdi.file.FileObject
import com.mahdi.file.FileWrapper
import com.mahdi.file.NetWrapper
import com.mahdi.file.UriWrapper
import com.mahdi.file.ZipFileObject

enum class AiFileKind {
    Local,
    Document,
    Remote,
    Archive,
    Unknown,
}

fun FileObject.aiKind(): AiFileKind =
    when (this) {
        is FileWrapper -> AiFileKind.Local
        is UriWrapper -> AiFileKind.Document
        is NetWrapper -> AiFileKind.Remote
        is ZipFileObject -> AiFileKind.Archive
        else -> AiFileKind.Unknown
    }

fun FileObject.nativePathOrNull(): String? = if (this is FileWrapper) getAbsolutePath() else null

internal val AI_IGNORED_DIRS = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".cxx")
