package com.travel.app.platform

import androidx.compose.runtime.Composable

// This JVM target exists only to run shared unit tests.
@Composable
actual fun HandleBack(enabled: Boolean, onBack: () -> Unit) = Unit
