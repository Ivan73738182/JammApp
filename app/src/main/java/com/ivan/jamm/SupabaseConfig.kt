package com.ivan.jamm

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    const val URL = "https://awtgvknefkfqjhpm sytm.supabase.co"
    const val KEY = "sb_publishable_YLl37cw2qFIxEqd9DJ1-QQ_GAQxTpzq"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = URL,
            supabaseKey = KEY
        ) {
            install(Storage)
            install(Auth)
            // Явно добавляем заголовок Authorization для Storage
            defaultRequest {
                header("Authorization", "Bearer $KEY")
            }
        }
    }
}
