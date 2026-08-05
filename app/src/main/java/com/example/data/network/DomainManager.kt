package com.example.data.network

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestisce i domini dei vari provider di streaming.
 * Permette l'aggiornamento dinamico degli indirizzi, ad esempio recuperandoli dai canali Telegram ufficiali
 * qualora i siti principali vengano oscurati o cambino estensione.
 */
object DomainManager {
    // Mappa interna contenente i domini in uso correntemente
    private var domains: Map<String, String> = mapOf(
        "streamingcommunity" to "https://streamingcommunityz.pl/",
        "animeunity" to "https://www.animeunity.so/",
        "animeworld" to "https://www.animeworld.ac/",
        "eurostreaming" to "https://eurostream.ing/",
        "cinezo" to "https://www.cinezo.net/",
        "mostraguarda" to "https://v.vidxgo.co/"
    )

    /**
     * Tenta di aggiornare i domini. Prima prova a recuperare i domini aggiornati dai canali di comunicazione (es. Telegram).
     * Se fallisce, carica l'ultima copia cache salvata localmente.
     */
    suspend fun load(prefs: SharedPreferences) = withContext(Dispatchers.IO) {
        try {
            // Cerchiamo di ottenere dinamicamente i domini (script di aggiornamento)
            updateDomainsDynamically(prefs)
        } catch (e: Exception) {
            Log.e("DomainManager", "Impossibile aggiornare i domini dinamici, uso la cache. Errore: ${e.message}")
            // Se c'è un errore, proviamo a caricare dalla cache
            loadFromCache(prefs)
        }
    }

    /**
     * Script interno per aggiornare gli indirizzi di dominio dei provider.
     * Effettua lo scraping dalle fonti ufficiali (come Telegram) per trovare il dominio attualmente attivo.
     */
    private suspend fun updateDomainsDynamically(prefs: SharedPreferences) {
        val updatedDomains = domains.toMutableMap()
        
        // 1. Aggiorna StreamingCommunity dal loro canale Telegram ufficiale
        try {
            // Scarica il contenuto HTML del canale Telegram
            val response = HttpClient.get("https://t.me/s/Streaming_community_sito")
            
            // Espressione regolare per trovare i domini di streamingcommunity (es. streamingcommunityz.tech, .pl, ecc.)
            val regex = Regex("streamingcommunity[a-z0-9]*\\.[a-z]+")
            val matches = regex.findAll(response).map { it.value }.toList()
            
            if (matches.isNotEmpty()) {
                // Prendiamo l'ultimo match, che in un canale Telegram rappresenta il messaggio più recente (il dominio più nuovo)
                val latestDomain = matches.last()
                updatedDomains["streamingcommunity"] = "https://$latestDomain/"
                Log.d("DomainManager", "StreamingCommunity aggiornato dinamicamente a: $latestDomain")
            } else {
                Log.d("DomainManager", "Nessun dominio trovato nel canale Telegram per StreamingCommunity")
            }
        } catch (e: Exception) {
            Log.e("DomainManager", "Errore durante l'estrazione del dominio di StreamingCommunity", e)
        }

        // --- SPAZIO PER AGGIUNGERE GLI SCRAPER DI ALTRI PROVIDER IN FUTURO ---
        // Esempio per AnimeUnity, Animeworld, ecc. se i loro canali diventano disponibili.

        // Aggiorna la mappa in memoria
        domains = updatedDomains
        
        // Salva i domini aggiornati nelle preferenze così al prossimo avvio partiamo avvantaggiati
        saveToCache(prefs, updatedDomains)
    }

    /**
     * Salva i domini attuali nelle SharedPreferences sotto forma di JSON
     */
    private fun saveToCache(prefs: SharedPreferences, mapToSave: Map<String, String>) {
        try {
            val obj = JSONObject()
            mapToSave.forEach { (key, value) ->
                obj.put(key, value)
            }
            prefs.edit().putString("domains_cache_v2", obj.toString()).apply()
            Log.d("DomainManager", "Domini salvati in cache con successo")
        } catch (e: Exception) {
            Log.e("DomainManager", "Errore nel salvare i domini in cache", e)
        }
    }

    /**
     * Carica i domini salvati localmente dalla cache
     */
    private fun loadFromCache(prefs: SharedPreferences) {
        val cached = prefs.getString("domains_cache_v2", null)
        if (cached != null) {
            try {
                val obj = JSONObject(cached)
                val map = mutableMapOf<String, String>()
                obj.keys().forEach { key ->
                    map[key] = obj.getString(key)
                }
                if (map.isNotEmpty()) {
                    domains = map
                    Log.d("DomainManager", "Domini caricati dalla cache")
                }
            } catch (e: Exception) {
                Log.e("DomainManager", "Impossibile analizzare i domini dalla cache", e)
            }
        }
    }

    /**
     * Ritorna l'URL corrente per il sito richiesto.
     * Se non trovato nella mappa, restituisce un valore di fallback codificato a mano.
     */
    fun getUrl(site: String): String {
        val siteKey = site.lowercase().replace(" ", "").trim()
        val found = domains[siteKey]
        if (!found.isNullOrBlank()) {
            return found
        }
        
        // Fallback di sicurezza in caso non sia presente nella mappa (es. canali tv, servizi streaming ufficiali)
        return when (siteKey) {
            "streamingcommunity" -> "https://streamingcommunityz.pl/"
            "animeunity" -> "https://www.animeunity.so/"
            "animeworld" -> "https://www.animeworld.ac/"
            "eurostreaming" -> "https://eurostream.ing/"
            "cinezo" -> "https://www.cinezo.net/"
            "mostraguarda" -> "https://v.vidxgo.co/"
            "discoveryplus" -> "https://www.discoveryplus.com/it/"
            "discovery" -> "https://discoveryplus.it/"
            "dmax" -> "https://dmax.it/"
            "nove" -> "https://nove.tv/"
            "realtime" -> "https://realtime.it/"
            "homegardentv" -> "https://homegardentv.it/"
            "foodnetwork" -> "https://foodnetwork.it/"
            "mediasetinfinity" -> "https://mediasetinfinity.mediaset.it/"
            "raiplay" -> "https://www.raiplay.it/"
            "crunchyroll" -> "https://www.crunchyroll.com/it/"
            "primevideo" -> "https://www.primevideo.com/"
            "tubitv" -> "https://tubitv.com/"
            else -> error("Dominio per $siteKey non trovato")
        }
    }
}
