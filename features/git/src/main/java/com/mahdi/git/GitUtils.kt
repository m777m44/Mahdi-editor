package com.mahdi.git

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mahdi.theme.gitAdded
import com.mahdi.theme.gitConflicted
import com.mahdi.theme.gitDeleted
import com.mahdi.theme.gitModified
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

suspend fun findGitRoot(path: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val startDir = File(path).let { if (it.isDirectory) it else it.parentFile }
            FileRepositoryBuilder()
                .findGitDir(startDir)
                .takeIf { it.gitDir != null }
                ?.build()
                ?.use { repo ->
                    if (!repo.isBare) {
                        repo.workTree?.canonicalPath
                    } else {
                        null
                    }
                }
        }
            .getOrNull()
    }

/** Get the text color for a given [ChangeType] */
@Composable
fun GitChange.getColor(): Color =
    when (type) {
        ChangeType.ADDED,
        ChangeType.UNTRACKED -> MaterialTheme.colorScheme.gitAdded

        ChangeType.DELETED -> MaterialTheme.colorScheme.gitDeleted
        ChangeType.CONFLICTING -> MaterialTheme.colorScheme.gitConflicted
        ChangeType.MODIFIED -> MaterialTheme.colorScheme.gitModified
        ChangeType.RENAMED -> MaterialTheme.colorScheme.gitModified
    }
