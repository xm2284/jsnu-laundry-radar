package com.jsnu.laundry.watch

import android.content.Context
import com.jsnu.laundry.data.api.HaierApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Retrofit / OkHttp 工厂：直连海乐公开接口，无鉴权 */
object ApiFactory {

    const val BASE_URL = "https://yshz-user.haier-ioc.com/"

    fun create(context: Context): HaierApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    // 微信小程序 UA（网页版实测有效，非必需但更稳）
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(req)
            }
            .build()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HaierApi::class.java)
    }
}
