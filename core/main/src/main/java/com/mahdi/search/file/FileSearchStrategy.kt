package com.mahdi.search.file

import com.mahdi.file.FileObject
import com.mahdi.search.index.FileMeta

/** Strategy for searching file names. */
interface FileSearchStrategy {
    suspend fun search(query: String, projectRoot: FileObject): List<FileMeta>
}
