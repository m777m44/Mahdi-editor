package com.mahdi.session

import android.content.Context
import com.mahdi.activities.main.session.DocumentState
import com.mahdi.activities.main.session.DocumentStateDatabase
import com.mahdi.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ملف سبق فتحه، مع موضع المؤشّر محفوظًا لاستئناف الكتابة من حيث توقّفت. */
data class RecentFile(
    val path: String,
    val lastOpened: Long,
    val line: Int,
    val column: Int,
) {
    val name: String
        get() = path.substringAfterLast('/')

    val directory: String
        get() = path.substringBeforeLast('/', "")

    val exists: Boolean
        get() = File(path).isFile
}

/**
 * «آخر ملف مفتوح» — يقرأ سجلّ حالة المستندات الذي يحفظه المحرر أصلًا
 * (المسار + المؤشّر + موضع التمرير + وقت آخر فتح) ويعرضه في الشاشة الرئيسية
 * ليستأنف المستخدم عمله بضغطة واحدة بعد إعادة فتح التطبيق.
 */
object RecentFiles {

    private fun database(): DocumentStateDatabase? {
        val context: Context = application ?: return null
        return runCatching { DocumentStateDatabase.getDatabase(context) }.getOrNull()
    }

    private fun DocumentState.toRecent() =
        RecentFile(
            path = path,
            lastOpened = lastOpened,
            line = cursorLineLeft,
            column = cursorColumnLeft,
        )

    /** أحدث [limit] ملفًا (الموجودة فعليًا فقط، الأحدث أولًا). */
    suspend fun recent(limit: Int = 8): List<RecentFile> =
        withContext(Dispatchers.IO) {
            val db = database() ?: return@withContext emptyList()
            runCatching { db.documentStateDao().getRecent(limit * 3) }
                .getOrDefault(emptyList())
                .map { it.toRecent() }
                .filter { it.exists }
                .distinctBy { it.path }
                .take(limit)
        }

    /** آخر ملف فُتح فعلًا (null إن لم يوجد سجلّ). */
    suspend fun last(): RecentFile? = recent(1).firstOrNull()

    /** يحذف ملفًا من السجلّ (يُستخدم بعد «بدء جديد» أو حذف الملف). */
    suspend fun forget(path: String) =
        withContext(Dispatchers.IO) {
            runCatching { database()?.documentStateDao()?.deleteByPath(path) }
            Unit
        }

    /** مسح كل السجلّ. */
    suspend fun clear() =
        withContext(Dispatchers.IO) {
            runCatching { database()?.documentStateDao()?.clear() }
            Unit
        }
}
