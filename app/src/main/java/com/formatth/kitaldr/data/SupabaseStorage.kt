package com.formatth.kitaldr.data

import com.formatth.kitaldr.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.UploadOptionBuilder
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.runBlocking
import java.io.File

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
 * Small synchronous facade for the repository's existing background-thread API.
 * Supabase Storage's upload/delete operations are suspend functions in v3.5.0,
 * so the facade bridges them with runBlocking while the repository remains on
 * its existing worker thread.
 */
class SupabaseStorageClient internal constructor(
    private val supabaseClient: SupabaseClient,
) {
    val storage: SupabaseStorageApi
        get() = SupabaseStorageApi(supabaseClient.storage)
}

class SupabaseStorageApi internal constructor(
    private val storage: Storage,
) {
    fun from(bucketId: String): SupabaseStorageBucket = SupabaseStorageBucket(storage.from(bucketId))
}

class SupabaseStorageBucket internal constructor(
    private val bucket: BucketApi,
) {
    fun upload(
        path: String,
        file: File,
        options: UploadOptionBuilder.() -> Unit = {},
    ) {
        runBlocking {
            bucket.upload(path, file.readBytes(), options)
        }
    }

    fun publicUrl(path: String): String = bucket.publicUrl(path)

    fun delete(path: String) {
        runBlocking {
            bucket.delete(path)
        }
    }
}
