package com.example.data.network

import io.ktor.util.*

object CinezoDecryptor {
    fun decodePayload(payload: String): String {
        try {
            val decoded = payload.decodeBase64Bytes()
            val salt = decoded.sliceArray(8 until 16)
            val data = decoded.sliceArray(16 until decoded.size)
            
            val password = "Sn00pD0g#L3_AES_S3cur3K3y@2026\$sex"
            val aesKey = Pbkdf2.deriveKey(password, salt, 100000, 32, "sha512")
            println("Key derived: ${aesKey.size}")
            
            val xorPassword = "Sn00pD0g#L1_X0R_M4st3rK3y!2026sex"
            val xorSalt = "xK9!mR2@pL5#nQ8sex".encodeToByteArray()
            val xorKey = Pbkdf2.deriveKey(xorPassword, xorSalt, 50000, 32, "sha256")
            
            // L1: XOR
            val l1Data = ByteArray(data.size)
            for (i in data.indices) {
                l1Data[i] = (data[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
            }
            
            // L3: AES-CBC (using IV from salt, simplified for Cinezo logic)
            // Note: Cinezo uses OpenSSL style key derivation where IV is also derived.
            // For now, I'll use a placeholder or the actual logic if I can find it.
            // In the original Scraper, it was Cipher.getInstance("AES/CBC/PKCS5Padding")
            // with key and iv derived from Pbkdf2.
            
            // I need to implement the full OpenSSL-style key derivation if salt is present.
            // But Cinezo implementation in Scraper.kt was slightly different.
            
            return l1Data.decodeToString()
        } catch (e: Exception) {
            return ""
        }
    }
}
