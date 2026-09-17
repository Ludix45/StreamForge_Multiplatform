package com.aistudio.streamforge

import android.content.Context
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryOptions

actual fun sentryInit(context: Any?, dsn: String, configuration: (SentryOptions) -> Unit) {
    val androidContext = context as? Context
    if (androidContext != null) {
        Sentry.init(androidContext, configuration)
    } else {
        // Fallback if context is missing, though not recommended for Android
        Sentry.init(configuration)
    }
}
