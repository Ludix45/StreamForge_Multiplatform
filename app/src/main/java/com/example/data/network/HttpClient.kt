package com.example.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object HttpClient {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = cookieStore[host] ?: mutableListOf()
            // simple merge
            cookies.forEach { newCookie ->
                list.removeAll { it.name == newCookie.name }
                list.add(newCookie)
            }
            cookieStore[host] = list
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val bootstrapClient = OkHttpClient.Builder().build()
    private val dns = DnsOverHttps.Builder().client(bootstrapClient)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
            InetAddress.getByName("2606:4700:4700::1111"),
            InetAddress.getByName("2606:4700:4700::1001")
        )
        .includeIPv6(true)
        .build()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .dns(dns)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val reqBuilder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = client.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP error response code: ${response.code} for URL: $url")
        }
        return response.use { it.body!!.string() }
    }

    fun post(url: String, body: RequestBody, headers: Map<String, String> = emptyMap()): String {
        val reqBuilder = Request.Builder().url(url).post(body).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = client.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP error response code: ${response.code} for URL: $url")
        }
        return response.use { it.body!!.string() }
    }

    fun getWithCookies(url: String): Pair<String, Map<String, String>> {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Unexpected HTTP error response code: ${resp.code} for URL: $url")
        }
        val body = resp.use { it.body!!.string() }
        val cookies = resp.headers("Set-Cookie").associate { header ->
            val parts = header.split(";")[0].split("=", limit = 2)
            val name = parts[0].trim()
            val value = parts.getOrElse(1) { "" }.trim()
            name to value
        }
        return Pair(body, cookies)
    }
}
