package es.alejandro.grandarbiter.ui.tools.releasenotes

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNoteSet
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

const val ARG_SET_CODE = "setCode"

class ReleaseNoteViewerViewModel(savedStateHandle: SavedStateHandle, repository: ReleaseNotesRepository) : ViewModel() {
    private val code: String = savedStateHandle.get<String>(ARG_SET_CODE).orEmpty()
    val set: StateFlow<ReleaseNoteSet?> = repository.sets.map { sets -> sets.firstOrNull { it.code == code } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { viewModelScope.launch { repository.load() } }
}

/**
 * A set's official release notes inside the app (WebView), with our own top bar. Only
 * magic.wizards.com pages load here; any other link goes to the browser. Offline, it shows the
 * cached copy of a page opened before, if the WebView still has it (best effort: the real offline
 * version is the next step).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNoteViewerScreen(onBack: () -> Unit, viewModel: ReleaseNoteViewerViewModel = koinViewModel()) {
    val set by viewModel.set.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Column {
                        Text(set?.name.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.tools_release_notes), style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant)
                    }
                },
                actions = {
                    set?.let { s ->
                        IconButton(onClick = { openExternally(context, webView?.url ?: s.url) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.release_notes_open_browser), tint = colors.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        val current = set ?: return@Scaffold
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).also { view ->
                        configure(view, onProgress = { progress = it }, onFailed = { failed = it }, onHistory = { canGoBack = view.canGoBack() })
                        view.loadUrl(current.url)
                        webView = view
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (failed) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.release_notes_offline), textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    TextButton(onClick = { failed = false; webView?.reload() }) { Text(stringResource(R.string.release_notes_retry)) }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")   // Wizards' site needs JS; only magic.wizards.com loads, no JS bridge.
private fun configure(view: WebView, onProgress: (Int) -> Unit, onFailed: (Boolean) -> Unit, onHistory: () -> Unit) {
    view.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        // Offline: whatever this WebView cached the last time the page was opened.
        cacheMode = if (isOnline(view.context)) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
    }
    view.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "https" && uri.host == ALLOWED_HOST) return false
            openExternally(view.context, uri.toString())
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onFailed(false)
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = onHistory()

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onFailed(true)
        }
    }
}

private const val ALLOWED_HOST = "magic.wizards.com"

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun openExternally(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing sensible to do.
    }
}
