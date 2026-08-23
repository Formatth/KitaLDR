package com.formatth.kitaldr

import com.google.firebase.FirebaseApp
import com.formatth.kitaldr.data.KitaLdrRepository

/**
 * Safe runtime Firebase diagnostics for development builds.
 *
 * These helpers expose identifiers only; the API key value itself is never
 * returned to the UI.
 */
val KitaLdrRepository.firebaseProjectId: String?
    get() = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()

val KitaLdrRepository.firebaseApplicationId: String?
    get() = runCatching { FirebaseApp.getInstance().options.applicationId }.getOrNull()

val KitaLdrRepository.firebaseApiKeyConfigured: Boolean
    get() = runCatching { !FirebaseApp.getInstance().options.apiKey.isNullOrBlank() }
        .getOrDefault(false)
