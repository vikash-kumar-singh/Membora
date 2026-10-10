package com.membora.app.data.remote

import com.membora.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

object ApiClient {
    val api: MemboraApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(MemboraApi::class.java)
    }
}
