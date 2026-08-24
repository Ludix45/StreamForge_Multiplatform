package com.aistudio.streamforge.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object KtorClient {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        // Redirects are handled by default
        
        // On Web, many requests will fail due to CORS. 
        // We will need a proxy or specific headers for webOS.
    }
    
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}
