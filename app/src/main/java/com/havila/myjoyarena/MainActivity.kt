package com.havila.myjoyarena

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

// Interface Javascript pour la fonction "Partager"
class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun share(title: String, text: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, " ")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager via"))
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutOffline: LinearLayout
    private lateinit var layoutSplash: RelativeLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    private var urlLoaded = false
    private val botUrl = "https://myjoy-arena.onrender.com" // MON URL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Lier les éléments visuels
        webView = findViewById(R.id.ma_webview)
        layoutOffline = findViewById(R.id.layout_offline)
        layoutSplash = findViewById(R.id.layout_splash)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        progressBar = findViewById(R.id.progress_bar)

        // 2. Configuration avancée de la WebView
        setupWebView()

        // 3. Configuration du Swipe to Refresh
        swipeRefresh.setOnRefreshListener {
            if (isNetworkAvailable(getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)) {
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
            }
        }
        
        // Couleur de l'animation de chargement
        swipeRefresh.setColorSchemeResources(android.R.color.holo_blue_bright, android.R.color.holo_green_light, android.R.color.holo_orange_light, android.R.color.holo_red_light)

        // 4. Gestion du bouton retour
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // 5. Lancer la surveillance de la connexion Internet
        setupNetworkListener()

        // 6. Gérer la disparition de l'écran de démarrage après 2 secondes (2000 ms)
        Handler(Looper.getMainLooper()).postDelayed({
            hideSplashScreen()
        }, 2000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        
        // Activations essentielles pour les apps web modernes
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // Très important pour le cache, localStorage, etc.
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        
        // Optimisations d'affichage
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false // Cache les boutons de zoom moches

        // Interface JS
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidShare")

        // User Agent
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = defaultUserAgent.replace("; wv", "")

        // WebChromeClient : Gère la ProgressBar
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // WebViewClient : Gère la navigation et le SwipeRefresh
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlLoaded = true
                swipeRefresh.isRefreshing = false // Arrête l'animation de rafraîchissement
                progressBar.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                // Si c'est un lien vers une autre appli (tel, mailto, whatsapp, etc.)
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // Par défaut, charger dans la WebView
                return false
            }
        }
    }

    // Fonction pour faire disparaître le logo en douceur avec une animation
    private fun hideSplashScreen() {
        layoutSplash.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction {
                layoutSplash.visibility = View.GONE
            }
    }

    // Fonction pour surveiller les coupures et retours d'internet en temps réel
    private fun setupNetworkListener() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        updateUI(isNetworkAvailable(connectivityManager))

        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { updateUI(true) }
            }

            override fun onLost(network: Network) {
                runOnUiThread { updateUI(false) }
            }
        })
    }

    // Fonction qui change l'affichage selon s'il y a internet ou non
    private fun updateUI(isConnected: Boolean) {
        if (isConnected) {
            layoutOffline.visibility = View.GONE
            // On ne montre la webview que si le splash est fini, 
            // mais on simplifie en gérant via la frame racine.
            swipeRefresh.visibility = View.VISIBLE

            if (!urlLoaded) {
                val linkToLoad = intent.data?.toString() ?: botUrl
                webView.loadUrl(linkToLoad)
            }
        } else {
            swipeRefresh.visibility = View.GONE
            layoutOffline.visibility = View.VISIBLE
        }
    }

    // Fonction utilitaire pour vérifier si internet est dispo à un instant T
    private fun isNetworkAvailable(connectivityManager: ConnectivityManager): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}