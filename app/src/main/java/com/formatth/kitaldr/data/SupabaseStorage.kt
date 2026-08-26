package com.formatth.kitaldr.data

import com.formatth.kitaldr.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage

/** Minimal Supabase Storage client used only for profile images. */
object SupabaseStorage {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client by lazy {
        check(isConfigured) {
            "Supabase Storage is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Storage)
        }
    }
}
