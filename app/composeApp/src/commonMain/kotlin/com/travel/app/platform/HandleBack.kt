package com.travel.app.platform

import androidx.compose.runtime.Composable

@Composable
expect fun HandleBack(enabled: Boolean, onBack: () -> Unit)
