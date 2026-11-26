package com.example.data

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Single shared Supabase connection for the whole app. */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
            install(Auth)
            install(Postgrest)
        }
    }
}

/**
 * Runs a Supabase call and turns server errors (e.g. "Insufficient resources")
 * into a clean message the UI can show in the snackbar.
 */
suspend fun <T> supabaseResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        Result.failure(IllegalStateException(e.error, e))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "Network error. Check your connection.", e))
    }
