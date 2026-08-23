package com.formatth.kitaldr

import com.google.firebase.FirebaseApp
import com.formatth.kitaldr.data.KitaLdrRepository

/** Safe runtime Firebase diagnostics for development builds. */
val KitaLdrRepository.firebaseProjectId: String?
    get() = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()

val KitaLdrRepository.firebaseApplicationId: String?
    get() = runCatching { FirebaseApp.getInstance().options.applicationId }.getOrNull()

val KitaLdrRepository.firebaseApiKeyConfigured: Boolean
    get() = runCatching { !FirebaseApp.getInstance().options.apiKey.isNullOrBlank() }
        .getOrDefault(false)

/** Only the last 6 characters are shown; the full key is never exposed. */
val KitaLdrRepository.firebaseApiKeySuffix: String
    get() = runCatching {
        FirebaseApp.getInstance().options.apiKey?.let { key ->
            if (key.length <= 6) key else "…${key.takeLast(6)}"
        } ?: "missing"
    }.getOrDefault("unavailable")
