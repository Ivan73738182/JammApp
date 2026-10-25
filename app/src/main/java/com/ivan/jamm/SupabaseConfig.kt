package com.ivan.jamm

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    const val URL = "https://awtgvknefkfqjhpmsytm.supabase.co"
    const val KEY = "sb_publishable_YLl37cw2qFIxEqd9DJ1-QQ_GAQxTpzq"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = URL,
            supabaseKey = KEY
        ) {
            install(Storage)
            install(GoTrue)
        }
    }
}
