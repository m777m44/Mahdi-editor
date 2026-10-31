package com.mahdi.search.file

import android.content.Context
import com.mahdi.file.FileObject
import com.mahdi.search.index.FileMeta
import com.mahdi.search.index.IndexDatabase
import com.mahdi.utils.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File search using indexed database. Fast, but requires index to be built. */
class FileSearchIndexed(private val context: Context) : FileSearchStrategy {
    override suspend fun search(query: String, projectRoot: FileObject): List<FileMeta> =
        withContext(Dispatchers.IO) {
            try {
                IndexDatabase.getDatabase(context, projectRoot).fileMetaDao().search(query)
            } catch (e: Exception) {
                logError(e, "Error searching file index")
                emptyList()
            }
        }
}
