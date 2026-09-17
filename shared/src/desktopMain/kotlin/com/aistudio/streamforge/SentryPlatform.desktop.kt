package com.aistudio.streamforge

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryOptions

actual fun sentryInit(context: Any?, dsn: String, configuration: (SentryOptions) -> Unit) {
    Sentry.init(configuration)
}
