package com.mahdi.search.code

import com.mahdi.search.CodeItem
import kotlinx.coroutines.flow.Flow

/** Strategy for searching code content. */
interface CodeSearchStrategy {
    fun search(query: String): Flow<CodeItem>
}
