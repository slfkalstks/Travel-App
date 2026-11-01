package com.travel.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
actual fun demoBaseUrl(): String = "http://10.0.2.2:8088"
