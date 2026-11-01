package com.travel.app.platform

import androidx.compose.runtime.Composable

// iOS uses the explicit navigation controls in the shared mobile screen.
@Composable
actual fun HandleBack(enabled: Boolean, onBack: () -> Unit) = Unit
