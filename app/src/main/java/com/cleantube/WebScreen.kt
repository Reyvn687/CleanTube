package com.cleantube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

private const val HOME_URL = "https://m.youtube.com/"

private val AllowedHosts = listOf(
    "youtube.com", "youtu.be", "google.com", "gstatic.com", "ytimg.com",
    "googleusercontent.com", "googlevideo.com", "ggpht.com"
)

private fun isAllowedHost(host: String): Boolean =
    AllowedHosts.any { host == it || host.endsWith(".$it") }

/** Video bağlantılarına basanda CleanTube-un təmiz pleyerini açan körpü. */
private class Bridge(private val vm: MainViewModel) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openVideo(url: String) {
        handler.post { vm.openLink(url) }
    }
}

/** Yalnız video linklərinə klik hadisəsini tutur, səhifənin görünüşünü dəyişmir. */
private const val HOOK_JS = """
(function () {
  if (window.__ctHook) return;
  window.__ctHook = true;
  function videoUrl(e) {
    var t = e.target;
    var a = t && t.closest ? t.closest('a[href]') : null;
    if (!a) return null;
    var h = a.getAttribute('href') || '';
    if (h.indexOf('/watch') > -1 || h.indexOf('/shorts/') > -1 || h.indexOf('youtu.be/') > -1) return a.href;
    return null;
  }
  document.addEventListener('click', function (e) {
    var u = videoUrl(e);
    if (u) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      CleanTube.openVideo(u);
    }
  }, true);
})();
"""

/**
 * YouTube-un rəsmi mobil saytı (API açarı tələb etmir): geri/irəli/yenilə/ana səhifə düymələri,
 * aşağı çəkib yeniləmə, yüklənmə zolağı. Videoya basanda video təmiz pleyerdə açılır.
 */
@Composable
fun WebScreen(vm: MainViewModel, holder: Array<WebView?>) {
    val context = LocalContext.current
    val web = holder[0] ?: createWeb(context, vm).also { holder[0] = it }

    DisposableEffect(web) {
        web.onResume()
        onDispose { web.onPause() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { web.goBack() }, enabled = vm.webCanGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
            }
            IconButton(onClick = { web.goForward() }, enabled = vm.webCanGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "İrəli")
            }
            IconButton(onClick = { web.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Yenilə")
            }
            IconButton(onClick = { web.loadUrl(HOME_URL) }) {
                Icon(Icons.Default.Home, contentDescription = "YouTube ana səhifə")
            }
        }
        if (vm.webProgress < 100) {
            LinearProgressIndicator(
                progress = { vm.webProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (web.parent as? ViewGroup)?.removeView(web)
                SwipeRefreshLayout(ctx).apply {
                    addView(
                        web,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    setOnRefreshListener { web.reload() }
                    setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
                }
            },
            update = { layout ->
                if (vm.webProgress >= 100) layout.isRefreshing = false
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWeb(context: Context, vm: MainViewModel): WebView {
    val web = WebView(context)
    val s = web.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.mediaPlaybackRequiresUserGesture = true
    s.setSupportZoom(false)
    s.setSupportMultipleWindows(false)
    s.javaScriptCanOpenWindowsAutomatically = false
    s.allowFileAccess = false
    // WebView işarəsi olmadan: YouTube adi mobil brauzer kimi göstərilir
    s.userAgentString = s.userAgentString.replace("; wv", "")

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(web, true)

    web.addJavascriptInterface(Bridge(vm), "CleanTube")

    web.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            vm.webProgress = newProgress
        }
    }

    web.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            vm.webProgress = 5
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            vm.webProgress = 100
            view?.evaluateJavascript(HOOK_JS, null)
            if (view != null) {
                vm.webCanGoBack = view.canGoBack()
                vm.webCanGoForward = view.canGoForward()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request == null || !request.isForMainFrame) return false
            val uri: Uri = request.url
            if (extractVideoId(uri.toString()) != null) {
                vm.openLink(uri.toString())
                return true
            }
            if (!isAllowedHost(uri.host.orEmpty())) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
                return true
            }
            return false
        }

        // Sayt səhifəni yenidən yükləmədən keçid edəndə də videonu tutmaq üçün ehtiyat yol
        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (view == null) return
            if (url != null && !isReload && extractVideoId(url) != null) {
                view.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(function(v){v.pause();});",
                    null
                )
                vm.openLink(url)
                view.post { if (view.canGoBack()) view.goBack() }
            }
            vm.webCanGoBack = view.canGoBack()
            vm.webCanGoForward = view.canGoForward()
        }
    }

    web.loadUrl(HOME_URL)
    return web
}
