package com.example.data.network

import kotlin.math.min

object Pbkdf2 {
    fun deriveKey(password: String, salt: ByteArray, iterations: Int, dkLen: Int, algorithm: String): ByteArray {
        val isSha256 = algorithm.lowercase().contains("256")
        val hLen = if (isSha256) 32 else 64
        val result = ByteArray(dkLen)
        val passwordBytes = password.encodeToByteArray()

        var offset = 0
        var blockIndex = 1

        while (offset < dkLen) {
            val indexBytes = byteArrayOf(
                (blockIndex ushr 24).toByte(),
                (blockIndex ushr 16).toByte(),
                (blockIndex ushr 8).toByte(),
                blockIndex.toByte()
            )
            
            // T_1 = F(P, S, c, 1) = HMAC(P, S || INT(1))
            val combined = salt + indexBytes
            var u = if (isSha256) CommonCrypto.hmacSha256(combined, passwordBytes) else CommonCrypto.hmacSha512(combined, passwordBytes)
            val f = u.copyOf()

            for (_j in 2..iterations) {
                u = if (isSha256) CommonCrypto.hmacSha256(u, passwordBytes) else CommonCrypto.hmacSha512(u, passwordBytes)
                for (k in f.indices) {
                    f[k] = (f[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val copyLen = min(dkLen - offset, hLen)
            for (i in 0 until copyLen) {
                result[offset + i] = f[i]
            }
            offset += copyLen
            blockIndex++
        }
        return result
    }
}
