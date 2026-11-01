package com.travel.app.data

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
expect fun demoBaseUrl(): String
