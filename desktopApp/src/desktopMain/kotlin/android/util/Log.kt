package android.util

/** Minimal JVM replacement for Android's Log API used by the shared scraper. */
object Log {
    fun d(tag: String, message: String): Int = write("DEBUG", tag, message)
    fun e(tag: String, message: String, error: Throwable? = null): Int {
        error?.printStackTrace(System.err)
        return write("ERROR", tag, message)
    }

    private fun write(level: String, tag: String, message: String): Int {
        System.err.println("[$level][$tag] $message")
        return 0
    }
}

/** JVM equivalent for the Base64 methods referenced by the Android scraper. */
object Base64 {
    const val DEFAULT: Int = 0
    fun decode(value: String, flags: Int): ByteArray = java.util.Base64.getDecoder().decode(value)
}
