package com.mahdi

import android.app.Application
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.mahdi.App.Companion.iconPackManager
import com.mahdi.App.Companion.themeManager
import com.mahdi.activities.main.MainActivity
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.common.MahdiPackage
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.global.StoreCommand
import com.mahdi.components.DialogProvider
import com.mahdi.components.DialogRegistry
import com.mahdi.extension.ActivityProvider
import com.mahdi.extension.api.DynamicRoute
import com.mahdi.extension.api.IntentHandleRegistry
import com.mahdi.extension.api.IntentHandler
import com.mahdi.extension.extensionManager
import com.mahdi.extension.loader.loadAllExtensions
import com.mahdi.extension.loader.unloadAllExtensions
import com.mahdi.extension.manager.ExtensionAPIManager
import com.mahdi.extension.manager.ExtensionManager
import com.mahdi.extension.ui.MahdiInstallDialog
import com.mahdi.feature.Feature
import com.mahdi.feature.FeatureToggle
import com.mahdi.file.FileWrapper
import com.mahdi.file.copyToTempDir
import com.mahdi.filetree.isMahdiPackage
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.SettingsCategory
import com.mahdi.settings.SettingsRegistry
import com.mahdi.settings.extension.ExtensionSettings
import com.mahdi.settings.extension.PackageDetail
import com.mahdi.settings.extension.StoreScreen
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExtensionFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.ext.getString(),
            key = "enable_extension",
            default = true,
            icon = Icon.ResourceIcon(drawables.extension),
        )

    private var intentHandler: IntentHandler? = null
    private var dialogProvider: DialogProvider? = null
    private var settingsCategory: SettingsCategory? = null
    private val routes = mutableListOf<DynamicRoute>()

    @OptIn(DelicateCoroutinesApi::class)
    override fun init(application: Application) {
        extensionManager = ExtensionManager(application)

        intentHandler =
            IntentHandler { file ->
                if (!file.isMahdiPackage()) return@IntentHandler false

                withContext(Dispatchers.IO) {
                    val context = application.applicationContext
                    val cacheDir = context.cacheDir
                    val tempFile = file.copyToTempDir()
                    val tempDir = File(cacheDir, "ext_temp_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    try {
                        MahdiPackage.extract(tempFile, tempDir)

                        val manifest = MahdiPackage.loadManifest(tempDir)
                        if (manifest != null) {
                            val packageIcon = tempDir.resolve("icon.png")
                            val iconFile = FileWrapper(packageIcon).takeIf { it.exists() }?.copyToTempDir()

                            withContext(Dispatchers.Main) {
                                MainActivity.instance?.viewModel?.openPackageInstallDialog(manifest, tempFile, iconFile)
                            }
                        }
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
                return@IntentHandler true
            }
                .also { IntentHandleRegistry.register(it) }

        dialogProvider =
            DialogProvider {
                MainActivity.instance?.let {
                    val viewModel = it.viewModel
                    val pendingInstall =
                        viewModel.pendingPackageInstall.collectAsStateWithLifecycle().value ?: return@let
                    MahdiInstallDialog(
                        pendingInstall.manifest,
                        pendingInstall.icon,
                        pendingInstall.packageFile,
                        viewModel::closePackageInstallDialog,
                    )
                }
            }
                .also { DialogRegistry.register(it) }

        // Initialize and load extensions
        GlobalScope.launch(Dispatchers.IO) {
            application.registerActivityLifecycleCallbacks(ExtensionAPIManager)

            extensionManager.indexLocalExtensions()
            extensionManager.loadAllExtensions()
            extensionManager.indexStoreExtensions()
        }

        // 2.6.0: عاد «تصفح الإضافات» بصيغة Mahdi-Editor — إضافات وثيمات وحزم أيقونات
        // وخوادم لغات ومشغّلات، كلها في شاشة واحدة.
        settingsCategory =
            SettingsCategory(
                    label = strings.browse_extensions.getString(),
                    description = strings.browse_extensions_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.store),
                    route = SettingsRoutes.Extensions.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        // Register settings routes
        routes.add(
            DynamicRoute(
                "${SettingsRoutes.Extensions.route}?query={query}&category={category}",
                arguments =
                    listOf(
                        navArgument("query") {
                            nullable = true
                            type = NavType.StringType
                        },
                        navArgument("category") {
                            nullable = true
                            type = NavType.StringType
                        },
                    ),
            ) { navController, backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                val category = backStackEntry.arguments?.getString("category")
                StoreScreen(navController = navController, query = query, category = category)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ExtensionDetail.route}/{extensionId}") { navController, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                PackageDetail(extension, navController)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ExtensionSettings.route}/{extensionId}") { _, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.installedExtensions.value[it] }
                ExtensionSettings(extension)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ThemeDetail.route}/{themeId}") { navController, backStackEntry ->
                val themeId = backStackEntry.arguments?.getString("themeId")
                val theme = themeId?.let { themeManager.getTheme(it) }
                PackageDetail(theme, navController)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.IconPackDetail.route}/{iconPackId}") { navController, backStackEntry ->
                val iconPackId = backStackEntry.arguments?.getString("iconPackId")
                val iconPack = iconPackId?.let { iconPackManager.getIconPackPackage(it) }
                PackageDetail(iconPack, navController)
            }
        )

        routes.forEach { SettingsRegistry.registerRoute(it) }

        // أمر عام: «تصفح الإضافات» من لوحة الأوامر ومن الشاشة الرئيسية
        runCatching { CommandProvider.registerCommand(StoreCommand()) }
    }

    override fun dispose(application: Application) {
        extensionManager.unloadAllExtensions()
        application.unregisterActivityLifecycleCallbacks(ExtensionAPIManager)
        application.unregisterActivityLifecycleCallbacks(ActivityProvider)

        intentHandler?.let { IntentHandleRegistry.unregister(it) }
        dialogProvider?.let { DialogRegistry.unregister(it) }
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        runCatching { CommandProvider.commandList.value.filterIsInstance<StoreCommand>().forEach { CommandProvider.unregisterCommand(it) } }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()
    }
}
