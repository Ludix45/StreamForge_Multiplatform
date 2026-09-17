package com.aistudio.streamforge

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryOptions

/**
 * Funzione expect per l'inizializzazione specifica della piattaforma.
 */
expect fun sentryInit(context: Any?, dsn: String, configuration: (SentryOptions) -> Unit)

object SentryManager {
    /**
     * Inizializza Sentry con il DSN del progetto.
     * @param context Oggetto specifico della piattaforma (necessario per Android)
     * @param dsn Il DSN del progetto Sentry
     */
    fun init(context: Any? = null, dsn: String) {
        if (dsn.isBlank()) return
        
        sentryInit(context, dsn) { options: SentryOptions ->
            options.dsn = dsn
            options.attachStackTrace = true
            options.attachThreads = true
            // Abilita il debug per vedere eventuali errori di invio nei log del dispositivo
            options.debug = true
            // Raccogli statistiche sulle sessioni (chi usa l'app e quando)
            options.sessionTrackingIntervalMillis = 30000
        }
        // Invia un messaggio di test per confermare che la connessione funzioni
        Sentry.captureMessage("StreamForge: Sentry Manager Initialized")
    }

    /**
     * Traccia un evento personalizzato per le statistiche.
     */
    fun trackEvent(name: String, params: Map<String, String> = emptyMap()) {
        Sentry.captureMessage("$name: ${params.toString()}")
    }

    /**
     * Traccia l'avvio della riproduzione di un contenuto.
     */
    fun logContentPlay(title: String, provider: String, type: String) {
        trackEvent("content_play", mapOf(
            "title" to title,
            "provider" to provider,
            "type" to type
        ))
    }

    /**
     * Traccia una ricerca effettuata dall'utente.
     */
    fun logSearch(query: String, provider: String) {
        trackEvent("user_search", mapOf(
            "query" to query,
            "provider" to provider
        ))
    }
}
