package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import coil.Coil
import coil.ImageLoader
import com.example.data.network.HttpClient
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.AppNavigator
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory

/**
 * MainActivity: Il punto di ingresso principale dell'applicazione.
 * Qui configuriamo il tema, l'orientamento dello schermo e inizializziamo il ViewModel e Coil per le immagini.
 */
class MainActivity : ComponentActivity() {
    
    // Inizializzazione del ViewModel condiviso che gestisce lo stato dell'intera app
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Aggiungi la funzione di poter girare lo schermo nell'app ovunque (in base al sensore dell'utente), 
        // tranne nel player (che viene bloccato e forzato al landscape all'interno di Screens.kt)
        // FULL_USER permette di ruotare lo schermo liberamente.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        
        // Configurazione di Coil per usare l'HttpClient personalizzato (necessario per bypassare eventuali blocchi o referer)
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient { HttpClient.client }
            .build()
        Coil.setImageLoader(imageLoader)

        // Abilita la modalità edge-to-edge per un design più moderno a tutto schermo
        enableEdgeToEdge()
        
        // Imposta il contenuto Compose
        setContent {
            MyApplicationTheme {
                // AppNavigator gestisce la navigazione tra le diverse schermate (Home, Dettagli, Player)
                AppNavigator(viewModel = viewModel)
            }
        }
    }
}
