package com.travel.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController(
    configure = {
        // Shared screens handle the keyboard with imePadding and scrollable form content.
        // Do not also pan the entire UIKit view beneath the status bar.
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) { TravelApp() }
