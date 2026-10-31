// DO NOT UPDATE PACKAGE NAME OTHERWISE EXTENSIONS WILL BREAK
package com.mahdi.extension

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.annotation.Keep
import com.mahdi.extension.api.ExtensionActivity
import com.mahdi.extension.api.ExtensionScreen
import com.mahdi.extension.api.logDebug
import com.mahdi.extension.api.logError
import com.mahdi.extension.api.logInfo
import com.mahdi.extension.api.logWarn
import com.mahdi.file.createDirIfNot
import java.io.File
import kotlinx.coroutines.CoroutineScope

@Keep
class ExtensionContext(val extension: LocalExtension, val appContext: Context, val scope: CoroutineScope) {

    val settings = SharedPrefExtensionSettings(extension.id)

    val currentActivity
        get() = ActivityProvider.currentActivity

    val appResources by lazy { AppResources(appContext, appContext.resources, appContext.packageName) }

    val extensionFiles
        get() = File(extension.installPath).resolve("files").createDirIfNot()

    val assets: AssetManager by lazy {
        AssetManager::class.java.getDeclaredConstructor().newInstance().apply {
            val method = javaClass.getMethod("addAssetPath", String::class.java)
            method.invoke(this, extension.apkFile.absolutePath)
        }
    }

    val resources by lazy { Resources(assets, appContext.resources.displayMetrics, appContext.resources.configuration) }

    fun logDebug(msg: String) = extension.id.logDebug(msg)

    fun logInfo(msg: String) = extension.id.logInfo(msg)

    fun logWarn(msg: String) = extension.id.logWarn(msg)

    fun logError(msg: String) = extension.id.logError(msg)

    fun startScreen(screen: ExtensionScreen) {
        ExtensionActivity.start(ActivityProvider.currentActivity ?: appContext, screen)
    }
}
