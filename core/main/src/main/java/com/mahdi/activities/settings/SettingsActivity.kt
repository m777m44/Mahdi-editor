package com.mahdi.activities.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.appbars.LocalHomeAction
import com.mahdi.components.compose.appbars.LocalTopBarLogo
import com.mahdi.file.FileManager
import com.mahdi.resources.strings
import com.mahdi.theme.MahdiTheme
import com.mahdi.ui.components.AppLogo
import com.mahdi.utils.toast
import java.lang.ref.WeakReference

var settingsNavController = WeakReference<NavController?>(null)
var snackbarHostStateRef: WeakReference<SnackbarHostState?> = WeakReference(null)

class SettingsActivity : AppCompatActivity() {
    val fileManager = FileManager(this)

    // 8.2.0: زرّ «إدارة» في تنبيه «تعذّر الاتصال» كان يفتح الشاشة عبر LaunchedEffect(intent)
    // و`intent` ليس حالة Compose، فأي نية جديدة (onNewIntent) لا تُعاد معالجتها، وقد
    // يصل التنبيه إلى شاشة فارغة. صار المسار حالةً مراقَبة تُحدَّث عند وصول نية جديدة.
    private var activeIntent by mutableStateOf<Intent?>(null)

    companion object {
        private var activityRef = WeakReference<SettingsActivity?>(null)
        var instance: SettingsActivity?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()
        activeIntent = intent
        setContent {
            MahdiTheme {
                // 5.0.0: زر «الرئيسية» + الشعار الديناميكي في شريط **كل** شاشات الإعدادات.
                // في الإعدادات «الرئيسية» = المحرّر، لذلك الإجراء هو إنهاء هذا النشاط.
                CompositionLocalProvider(
                    LocalHomeAction provides { finish() },
                    LocalTopBarLogo provides { AppLogo(size = 26.dp, cornerRadius = 8.dp) },
                ) {
                Surface {
                    val navController = rememberNavController()
                    settingsNavController = WeakReference(navController)
                    SettingsNavHost(activity = this@SettingsActivity, navController = navController)

                    val snackbarHostState = remember { SnackbarHostState() }
                    LaunchedEffect(snackbarHostState) { snackbarHostStateRef = WeakReference(snackbarHostState) }

                    LaunchedEffect(activeIntent) {
                        val target = activeIntent ?: return@LaunchedEffect
                        val route = target.getStringExtra("route") ?: return@LaunchedEffect
                        // يُستهلك المسار مرة واحدة: الرجوع للخلف لا يعيد فتح نفس الشاشة.
                        target.removeExtra("route")
                        runCatching {
                            navController.navigate(route) { launchSingleTop = true }
                        }
                            .onFailure { toast(strings.unknown_err) }
                    }
                }
                }
            }
        }
    }
}
