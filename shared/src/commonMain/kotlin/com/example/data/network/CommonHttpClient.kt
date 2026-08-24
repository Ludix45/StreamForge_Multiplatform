package com.example.data.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object CommonHttpClient {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 30000
        }
        defaultRequest {
            header("User-Agent", USER_AGENT)
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return client.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsText()
    }

    suspend fun getWithResponse(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return client.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }
    }

    suspend fun post(url: String, body: Any? = null, headers: Map<String, String> = emptyMap()): String {
        return client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            if (body != null) {
                setBody(body)
            }
        }.bodyAsText()
    }

    suspend fun postForm(url: String, formParameters: Parameters, headers: Map<String, String> = emptyMap()): String {
        return client.submitForm(url, formParameters) {
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsText()
    }
}
