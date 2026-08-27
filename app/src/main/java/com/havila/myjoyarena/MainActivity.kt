package com.havila.myjoyarena

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

// 2. Vous pouvez placer l'interface ICI, en dehors de la classe MainActivity
class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun share(title: String, text: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$text $url")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager via"))
    }
}


class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutOffline: LinearLayout
    private lateinit var layoutSplash: RelativeLayout

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


        // 2. Configuration de la WebView
            webView.settings.javaScriptEnabled = true

            webView.webViewClient = WebViewClient()

            // Récupérer le lien cliqué
            val intent = intent
            val data = intent.data

            if (data != null) {
                // L'appli a été ouverte via un lien, on charge cette URL
                webView.loadUrl(data.toString())
            } else {
                // L'appli a été ouverte normalement, on charge l'accueil
                webView.loadUrl("https://myjoy-arena.onrender.com")
            }


        //  Collez la configuration de la WebView ICI (dans le onCreate)
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidShare")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

            }
        }



        // On récupère le User-Agent par défaut
        val defaultUserAgent = webView.settings.userAgentString
        // On retire la mention "; wv" (WebView) pour tromper la sécurité de Google
        webView.settings.userAgentString = defaultUserAgent.replace("; wv", "")

        // Solution alternative forte si le replace() ne fonctionne pas :
        // webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlLoaded = true // Confirme que la page a bien été chargée au moins une fois
            }
        }

        // 3. Gestion du bouton retour
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // 4. Lancer la surveillance de la connexion Internet
        setupNetworkListener()

        // 5. Gérer la disparition de l'écran de démarrage après 2 secondes (2000 ms)
        Handler(Looper.getMainLooper()).postDelayed({
            hideSplashScreen()
        }, 2000)
    }

    // Fonction pour faire disparaître le logo en douceur avec une animation
    private fun hideSplashScreen() {
        layoutSplash.animate()
            .alpha(0f) // Rend l'écran transparent
            .setDuration(500) // Durée de l'animation en millisecondes
            .withEndAction {
                layoutSplash.visibility = View.GONE
            }
    }

    // Fonction pour surveiller les coupures et retours d'internet en temps réel
    private fun setupNetworkListener() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Vérifier l'état immédiat au lancement
        updateUI(isNetworkAvailable(connectivityManager))

        // Écouter les changements futurs
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Internet est de retour ! (Il faut utiliser runOnUiThread pour modifier l'interface)
                runOnUiThread { updateUI(true) }
            }

            override fun onLost(network: Network) {
                // Internet a coupé !
                runOnUiThread { updateUI(false) }
            }
        })
    }

    // Fonction qui change l'affichage selon s'il y a internet ou non
    private fun updateUI(isConnected: Boolean) {
        if (isConnected) {
            // Cacher le message hors-ligne, montrer la WebView
            layoutOffline.visibility = View.GONE
            webView.visibility = View.VISIBLE

            // Si l'URL n'a pas encore été chargée avec succès, on la charge
            if (!urlLoaded) {
                webView.loadUrl(botUrl)
            }
        } else {
            // Cacher la WebView, montrer le message hors-ligne
            webView.visibility = View.GONE
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
