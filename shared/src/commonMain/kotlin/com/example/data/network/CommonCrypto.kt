package com.example.data.network

expect object CommonCrypto {
    fun aesDecryptCbc(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray
    fun hmacSha512(data: ByteArray, key: ByteArray): ByteArray
}
