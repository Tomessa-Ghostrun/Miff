package com.tomessa.miff.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomessa.miff.navigation.InternalDestinations

/**
 * Secret gesture to the internal diagnostic screen (PM, 2026-11-12): a finger kept down for 10 s opens it. To put at the ROOT of a window: the whole app
 * (MainActivity) and the content of each dialog or sheet (they are separate windows). It consumes nothing. In the RELEASE build (and releaseLike) this
 * is the identity: no detector, nothing.
 */
@Composable
fun Modifier.holdForDiagnostic(): Modifier = this.then(InternalDestinations.holdModifier())
