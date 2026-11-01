package com.mahdi.runner.runners.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.mahdi.settings.Settings
import com.mahdi.theme.MahdiTheme

/**
 * «متصفح المعاينة» داخل Mahdi-Editor (2.8.0).
 *
 * بدل قذف صفحة HTML إلى متصفح خارجي، تُفتح المعاينة داخل التطبيق نفسه في شاشة واحدة:
 * شريط عنوان قابل للتعديل، رجوع/تقدّم، إعادة تحميل، شريط تقدّم، وفتح خارجي عند الحاجة.
 * يعمل مع خادم المعاينة المحلي (`http://localhost:PORT`) ومع أي عنوان آخر.
 */
class PreviewBrowserActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_URL = "preview_url"

        /** يفتح المتصفح الداخلي على عنوان محدّد. */
        fun start(context: Context, url: String) {
            val intent =
                Intent(context, PreviewBrowserActivity::class.java).apply {
                    putExtra(EXTRA_URL, url)
                }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL) ?: "http://localhost:${Settings.http_server_port}"
        setContent { MahdiTheme { BrowserScreen(initialUrl = url, onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(initialUrl: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf(initialUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val context = LocalContext.current

    fun navigateBack() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    BackHandler { navigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            pageTitle.ifBlank {
                                if (isArabic) "متصفح المعاينة" else "Preview browser"
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = if (isArabic) "إغلاق" else "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = if (isArabic) "إعادة تحميل" else "Reload")
                    }
                    IconButton(
                        onClick = {
                            val url = webView?.url ?: address
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setData(url.toUri())
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = if (isArabic) "فتح خارجيًا" else "Open externally",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (isArabic) "تقدّم" else "Forward",
                        modifier = Modifier.size(20.dp),
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text("http://localhost") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions =
                        KeyboardActions(
                            onGo = {
                                val target = normalizeUrl(address)
                                address = target
                                webView?.loadUrl(target)
                            }
                        ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val target = normalizeUrl(address)
                                address = target
                                webView?.loadUrl(target)
                            }
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = if (isArabic) "انتقال" else "Go")
                        }
                    },
                )
            }

            if (progress in 0.01f..0.99f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setupPreviewWebView(this)
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                    pageTitle = view.title.orEmpty()
                                    address = url
                                }
                            }
                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress / 100f
                                }

                                override fun onReceivedTitle(view: WebView, title: String?) {
                                    pageTitle = title.orEmpty()
                                }
                            }
                        loadUrl(initialUrl)
                        webView = this
                    }
                },
                update = {},
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupPreviewWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
}

private fun normalizeUrl(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return "http://localhost"
    return if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("file://")) {
        text
    } else {
        "http://$text"
    }
}
