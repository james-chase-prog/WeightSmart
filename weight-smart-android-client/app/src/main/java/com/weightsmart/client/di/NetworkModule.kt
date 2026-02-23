package com.weightsmart.client.di

import com.weightsmart.client.data.remote.WeightSmartApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule
 * Defines how network dependencies are created and scoped.
 *
 * Architecture Role:
 * Constructs the Retrofit instance connecting converters (GSON), HTTP clients (OkHttp), and
 * interceptors (Logging). All network objects are Singleton-scoped so only one OkHttpClient
 * and Retrofit instance exist, enabling connection pooling and resource efficiency.
 *
 * Key Concepts & Documentation:
 * Singleton Scope: @InstallIn(SingletonComponent::class) ensures these objects survive for the entire app lifecycle.
 * Dependency Injection (Hilt): A static injection framework to reduce boilerplate.
 * <a href="https://dagger.dev/hilt/">Reference: Hilt Documentation</a>
 * OkHttp Interceptors: Middleware that can monitor, rewrite, and retry HTTP calls.
 * <a href="https://square.github.io/okhttp/features/interceptors/">Reference: OkHttp Interceptors</a>
 * Retrofit Converters: Plugins that serialize Java/Kotlin objects to JSON and back.
 * <a href="https://square.github.io/retrofit/#converters">Reference: Retrofit Converters</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-20
 */
@Module
@InstallIn(SingletonComponent::class) // This module lives as long as the app lives
object NetworkModule {

    // Define the Server URL using "10.0.2.2" for the android Emulator
    // FIXME: If testing on a real phone, use computer's real IP (e.g., 192.168.1.x)
    // TODO(PRODUCTION): Change to HTTPS production URL (e.g., "https://api.weightsmart.com/")
    //   and remove res/xml/network_security_config.xml + its reference in AndroidManifest.xml
    private const val BASE_URL = "http://10.0.2.2:8080/api/"

    /**
     * Provides a logging interceptor to view network traffic in Logcat.
     * Satisfies: OkHttpClient's interceptor chain.
     * Level.BODY prints headers + JSON content (useful for debugging API errors).
     * TODO(PRODUCTION): Switch to Level.NONE or Level.BASIC for release builds.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Logs the Body of HTTP requests (Great for debugging)
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /**
     * Provides the OkHttpClient with timeouts and interceptors.
     * Satisfies: Retrofit's HTTP client dependency.
     * Configures 30s timeouts to handle slow server responses or network latency
     * without crashing the app. Interceptor chain includes logging for debug builds.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Wait 30s for server to answer
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides the Retrofit instance.
     * Satisfies: WeightSmartApi creation.
     * Wires the BASE_URL, OkHttpClient, and GsonConverterFactory together.
     * Retrofit generates implementations of the WeightSmartApi interface at runtime.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // Converts JSON <-> Data Classes
            .build()
    }

    /**
     * Provides the WeightSmartApi implementation.
     * Satisfies: All repository and worker classes that call the server.
     * Retrofit generates the HTTP method bodies at runtime from the interface annotations.
     */
    @Provides
    @Singleton
    fun provideWeightSmartApi(retrofit: Retrofit): WeightSmartApi {
        return retrofit.create(WeightSmartApi::class.java)
    }
}