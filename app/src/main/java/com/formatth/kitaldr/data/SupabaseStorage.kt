package com.formatth.kitaldr.data

import com.formatth.kitaldr.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

/** Minimal Supabase Storage client used only for profile images. */
object SupabaseStorage {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client by lazy {
        check(isConfigured) {
            "Supabase Storage is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties."
        }
        SupabaseStorageClient(
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ) {
                install(Storage)
            }
        )
    }
}

/**
 * Small facade that keeps the repository API stable while exposing the
 * Storage plugin through the Supabase Kotlin extension property.
 */
class SupabaseStorageClient internal constructor(
    private val supabaseClient: SupabaseClient,
) {
    val storage: Storage
        get() = supabaseClient.storage
}
