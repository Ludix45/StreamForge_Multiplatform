package com.example.data.network

import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual object CommonCrypto {
    actual fun aesDecryptCbc(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val outBuffer = ByteArray(encryptedData.size + kCCBlockSizeAES128.toInt())
        val numBytesDecrypted = memScoped {
            val dataOutMoved = alloc<size_tVar>()
            val status = CCCrypt(
                kCCDecrypt,
                kCCAlgorithmAES,
                kCCOptionPKCS7Padding,
                key.refTo(0), key.size.convert(),
                iv.refTo(0),
                encryptedData.refTo(0), encryptedData.size.convert(),
                outBuffer.refTo(0), outBuffer.size.convert(),
                dataOutMoved.ptr
            )
            if (status != kCCSuccess) throw Exception("AES Decryption failed with status $status")
            dataOutMoved.value.toInt()
        }
        return outBuffer.copyOf(numBytesDecrypted)
    }

    actual fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
        CCHmac(
            kCCHmacAlgSHA256,
            key.refTo(0), key.size.convert(),
            data.refTo(0), data.size.convert(),
            result.refTo(0)
        )
        return result
    }

    actual fun hmacSha512(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(CC_SHA512_DIGEST_LENGTH)
        CCHmac(
            kCCHmacAlgSHA512,
            key.refTo(0), key.size.convert(),
            data.refTo(0), data.size.convert(),
            result.refTo(0)
        )
        return result
    }
}
