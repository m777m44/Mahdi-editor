package com.mahdi.activities.terminal

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mahdi.app.AppFlavour
import com.mahdi.exec.isTerminalInstalled
import com.mahdi.exec.pendingCommand
import com.mahdi.file.child
import com.mahdi.file.localBinDir
import com.mahdi.file.sandboxDir
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.terminal.BUNDLED_ROOTFS_ABI
import com.mahdi.terminal.BUNDLED_ROOTFS_ASSET
import com.mahdi.terminal.NEXT_STAGE
import com.mahdi.terminal.ROOTFS_ARM
import com.mahdi.terminal.ROOTFS_ARM64
import com.mahdi.terminal.ROOTFS_X64
import com.mahdi.terminal.SessionService
import com.mahdi.terminal.TerminalBackEnd
import com.mahdi.terminal.TerminalScreen
import com.mahdi.terminal.changeSession
import com.mahdi.terminal.getNextStage
import com.mahdi.terminal.terminalView
import com.mahdi.theme.MahdiTheme
import com.mahdi.utils.dialogRes
import com.mahdi.utils.errorDialog
import com.mahdi.utils.getTempDir
import com.mahdi.utils.logError
import com.mahdi.utils.toast
import java.io.File
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class Terminal : AppCompatActivity() {
    var sessionBinder by mutableStateOf<WeakReference<SessionService.SessionBinder>?>(null)
    var isBound = false

    val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as SessionService.SessionBinder
                sessionBinder = WeakReference(binder)
                // sessionBinder = WeakReference(binder)
                isBound = true
                handleIntent(intent)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isBound = false
                sessionBinder = null
            }
        }

    override fun onStart() {
        super.onStart()
        startAndBindService()
    }

    /**
     * 8.2.0: إعادة الاتصال اليدوية من شاشة «تعذّر الاتصال بخدمة الطرفية».
     * كان المستخدم يرى سطرًا عاريًا بلا أي إجراء، فيبقى التطبيق مجمّدًا إن ماتت
     * الخدمة أو تأخّر الربط. الآن زرّ واحد يعيد تشغيلها ويُعيد ربطها بأمان.
     */
    fun startAndBindService() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, SessionService::class.java))
            if (!isBound) {
                bindService(Intent(this, SessionService::class.java), serviceConnection, BIND_AUTO_CREATE)
            }
        }
            .onFailure { toast(strings.terminal_service_error) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // 8.1.0 — إصلاح «الزرّ ▶ لا يشغّل شيئًا»:
        //
        // هذه الشاشة تعمل بـ`launchMode=singleTask`، فتشغيل سكربت جديد بينما
        // الطرفية مفتوحة **لا يعيد إنشاءها**: تصل `onNewIntent` فقط، ولا يقرأ
        // أحد `pendingCommand` (يُقرأ مرّة واحدة داخل مصنع AndroidView)، فيبقى
        // الأمر معلّقًا إلى الأبد وتبدو الطرفية كأنها لا تستجيب.
        //
        // الآن: نُنهي الجلسة القديمة بنفس المعرّف ثم نُنشئها من جديد، فيُنفَّذ
        // الأمر الجديد فورًا ويظهر مخرجه في نفس الشاشة.
        val command = pendingCommand
        val binder = sessionBinder?.get()
        if (command != null && binder != null && terminalView.get() != null) {
            binder.terminateSession(command.id)
            changeSession(command.id)
            return
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        instance = this
    }

    fun handleIntent(intent: Intent) {
        this.intent = intent
        val pwd = intent.getStringExtra("cwd") ?: return
        val binder = sessionBinder?.get() ?: return
        terminalView.get() ?: return

        val sessionId = File(pwd).name

        lifecycleScope.launch(Dispatchers.Main) {
            val client = TerminalBackEnd()
            val info = binder.getSessionInfoByPwd(pwd) ?: binder.createSession(sessionId, client, this@Terminal)

            this@Terminal.changeSession(info.id)
            setIntent(intent)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private var activityRef = WeakReference<Terminal?>(null)
        var instance: Terminal?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instance = this

        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MahdiTheme {
                Surface {
                    // 8.2.0: `WeakReference` قد تُنظَّف وبقيت الشاشة تظنّها متصلة
                    // (sessionBinder != null) ثم ينهار `changeSession` على null.
                    // نتحقّق من المرجع نفسه لا من غلافه.
                    if (sessionBinder?.get() != null) {
                        TerminalScreenHost(this)
                    } else {
                        TerminalConnectionState(onReconnect = { startAndBindService() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (!isFinishing) {
            super.onDestroy()
            return
        }

        super.onDestroy()
    }

    var progressText by mutableStateOf(strings.installing.getString())
    var installNextStage by mutableStateOf<NEXT_STAGE?>(null)

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var needsDownload by remember { mutableStateOf(false) }
        var currentFileName by remember { mutableStateOf("") }
        var downloadedBytes by remember { mutableLongStateOf(0L) }
        var totalBytes by remember { mutableLongStateOf(0L) }

        // Helper function to format bytes to MB string
        fun formatBytesToMB(bytes: Long): String {
            return "%.2f".format(bytes / (1024.0 * 1024.0))
        }

        LaunchedEffect(Unit) {
            try {
                val abi = Build.SUPPORTED_ABIS
                val playStore = AppFlavour.current.isPlayStore
                val installed = isTerminalInstalled()

                // The Play Store flavour bundles a single arm64 rootfs. Reject other architectures
                // up front with a clear message instead of failing later inside proot.
                if (!installed && playStore && !abi.contains(BUNDLED_ROOTFS_ABI)) {
                    dialogRes(
                        activity = this@Terminal,
                        title = strings.error.getString(),
                        msg = strings.playstore_arm64_only.getString(),
                        cancelable = false,
                        onOk = { finish() },
                    )
                    return@LaunchedEffect
                }

                val filesToDownload = mutableListOf<DownloadFile>()

                if (!installed && !playStore) {
                    filesToDownload.add(
                        DownloadFile(
                            url =
                                if (abi.contains("x86_64")) {
                                    ROOTFS_X64
                                } else if (abi.contains("arm64-v8a")) {
                                    ROOTFS_ARM64
                                } else if (abi.contains("armeabi-v7a")) {
                                    ROOTFS_ARM
                                } else {
                                    throw RuntimeException("Unsupported CPU")
                                },
                            outputFile = getTempDir().child("sandbox.tar.gz"),
                        )
                    )
                }

                // Play Store builds copy the rootfs bundled in the APK; community builds download it.
                val bundledRootfsAsset = if (!installed && playStore) BUNDLED_ROOTFS_ASSET else null

                needsDownload =
                    bundledRootfsAsset != null || filesToDownload.any { file -> file.outputFile.exists().not() }

                setupEnvironment(
                    context = context,
                    filesToDownload = filesToDownload,
                    bundledRootfsAsset = bundledRootfsAsset,
                    onProgress = { fileName, downloaded, total ->
                        downloadedBytes = downloaded
                        totalBytes = total
                        currentFileName = fileName

                        // Bundled assets are copied from the APK, not downloaded, so keep the
                        // generic "Installing…" label for them.
                        if (total > 0 && bundledRootfsAsset == null) {
                            val downloadedMB = formatBytesToMB(downloaded)
                            val totalMB = formatBytesToMB(total)
                            progressText =
                                "${strings.downloading.getString()} ${fileName.removeSuffix(".so").removePrefix("lib")} ($downloadedMB/$totalMB MB)"
                        }
                    },
                    onComplete = { installNextStage = it },
                    onError = { error, file ->
                        when (error) {
                            is UnknownHostException -> {
                                toast(strings.network_err.getString())
                            }

                            is SocketTimeoutException -> {
                                errorDialog(strings.timeout)
                            }

                            else -> {
                                logError(error)
                                GlobalScope.launch(Dispatchers.IO) {
                                    if (file?.absolutePath?.contains(localBinDir().absolutePath) == true) {
                                        localBinDir().deleteRecursively()
                                    }

                                    if (file?.name == "sandbox.tar.gz") {
                                        sandboxDir().deleteRecursively()
                                        File(getTempDir(), "sandbox.tar.gz").delete()
                                    }
                                }
                                errorDialog(msg = "Setup failed: ${error.message}")
                            }
                        }
                        finish()
                    },
                )
            } catch (e: Exception) {
                if (e is UnknownHostException) {
                    toast(strings.network_err.getString())
                } else if (e is SocketTimeoutException) {
                    errorDialog(strings.timeout)
                } else {
                    logError(e)
                    toast("Setup failed: ${e.message}")
                }
                finish()
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val context = LocalContext.current
            val activity = context as? Activity

            DisposableEffect(Unit) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            if (installNextStage == null) {
                if (needsDownload) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = progressText, style = MaterialTheme.typography.bodyLarge)

                            Spacer(modifier = Modifier.height(16.dp))

                            // The bundled rootfs is a compressed asset, so its size (and therefore a
                            // percentage) is not always known. Fall back to an indeterminate bar.
                            if (totalBytes > 0) {
                                val percent = (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                                progress = downloadedBytes.toFloat() / totalBytes

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                )

                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                            }
                        }

                        Text(
                            text = stringResource(strings.warn_dont_leave_setup),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                TerminalScreen(terminalActivity = this@Terminal)
            }
        }
    }

    data class DownloadFile(val url: String, val outputFile: File)

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun setupEnvironment(
        context: Context,
        filesToDownload: List<DownloadFile>,
        bundledRootfsAsset: String? = null,
        onProgress: (fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (NEXT_STAGE) -> Unit,
        onError: (Exception, File?) -> Unit,
    ) {
        var currentFile: File? = null

        withContext(Dispatchers.IO) {
            try {
                if (bundledRootfsAsset != null) {
                    // Play Store builds ship the rootfs inside the APK, so just stage it for extraction.
                    val outputFile = getTempDir().child("sandbox.tar.gz")
                    currentFile = outputFile
                    outputFile.parentFile?.mkdirs()

                    copyAsset(
                        context = context,
                        assetPath = bundledRootfsAsset,
                        outputFile = outputFile,
                        onProgress = { downloaded, total -> onProgress(outputFile.name, downloaded, total) },
                    )

                    runCatching { outputFile.setExecutable(true) }.onFailure { logError(it) }
                } else {
                    filesToDownload.forEach { file ->
                        val outputFile = file.outputFile
                        currentFile = outputFile

                        outputFile.parentFile?.mkdirs()

                        if (!outputFile.exists()) {
                            downloadFile(
                                url = file.url,
                                outputFile = outputFile,
                                onProgress = { downloaded, total ->
                                    onProgress(file.outputFile.name, downloaded, total)
                                },
                            )
                        } else {
                            // Report existing file as already downloaded
                            onProgress(file.outputFile.name, outputFile.length(), outputFile.length())
                        }

                        runCatching { outputFile.setExecutable(true) }.onFailure { logError(it) }
                    }
                }

                val stage = getNextStage(this@Terminal)
                onComplete(stage)
            } catch (e: Exception) {
                logError(e)
                withContext(Dispatchers.Main) { onError(e, currentFile) }
                // Read into a local so it can be smart-cast: `currentFile` is mutated by the
                // lambdas above, which blocks a smart cast on the captured variable itself.
                val failedFile = currentFile
                if (failedFile?.exists() == true) {
                    failedFile.delete()
                }
            }
        }
    }

    /** Copies an asset bundled in the APK to [outputFile], reporting progress as it goes. */
    private suspend fun copyAsset(
        context: Context,
        assetPath: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            // openFd() only works for uncompressed assets, so fall back to a length-less stream.
            val totalBytes = runCatching { context.assets.openFd(assetPath).use { it.length } }.getOrDefault(0L)

            var copiedBytes = 0L
            context.assets.open(assetPath).use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead
                        withContext(Dispatchers.Main) { onProgress(copiedBytes, totalBytes) }
                    }
                }
            }
        }
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .callTimeout(10, TimeUnit.MINUTES)
                    .build()
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to download file: ${response.code}")
                }

                val body = response.body
                val totalBytes = body.contentLength()

                var downloadedBytes = 0L

                outputFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8.2.0 — شاشة «تعذّر الاتصال بخدمة الطرفية» بصريًا وإجرائيًا.
 *
 * قبلها كان مجرّد `Text` عارٍ في وسط الشاشة بلا زرّ ولا حالة: إن ماتت الخدمة أو
 * تأخّر الربط بقي المستخدم أمام شاشة ساكنة لا يعرف إن كانت تعمل أم لا. الآن:
 * تدرّج هادئ، مؤشّر انتظار، عنوان واضح، ثم — إن طال الانتظار — عنوان فشل وزرّ
 * «إعادة المحاولة» يعيد تشغيل الخدمة بلا فقدان للملفات أو الجلسات.
 */
@Composable
private fun TerminalConnectionState(onReconnect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var timedOut by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    // كل محاولة اتصال تعيد المؤشّر من البداية، فلا تبقى الشاشة على «فشل»
    // بينما الخدمة عادت تعمل خلفها.
    LaunchedEffect(attempt) {
        timedOut = false
        delay(4000)
        timedOut = true
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceContainerHighest))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (timedOut) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                CircularProgressIndicator(color = scheme.primary, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text =
                    stringResource(
                        if (timedOut) strings.terminal_connection_failed else strings.terminal_connecting
                    ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        if (timedOut) {
                            strings.terminal_connection_failed_desc
                        } else {
                            strings.terminal_connecting_desc
                        }
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (timedOut) {
                Spacer(Modifier.height(22.dp))
                FilledTonalButton(
                    onClick = {
                        attempt++
                        onReconnect()
                    }
                ) {
                    Text(stringResource(strings.storage_retry))
                }
            }
        }
    }
}
