package com.tomessa.miff.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.data.onboarding.DraftStep
import kotlinx.serialization.Serializable

@Serializable
data object ProfileStepRoute

@Serializable
data object PhoneStepRoute

/**
 * The profile creation as the person sees it (Onboarding ticket 6.3 and 6.4): the profile step, then the phone step with
 * its arrow back, and, over them, the square crop of the photo. For an older account without a number, the phone step alone.
 *
 * The two steps are the screens of a small navigation: that is what gives the native back gesture its preview animation
 * (predictive back) from the phone step to the profile step. The step reached is also kept by the view model in the
 * local draft, so that closing the app puts the person back where they were.
 */
@Composable
fun ProfileFlow(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cropping = state.crop != null
    Box {
        // While the crop screen is open it is the ONLY thing that exists for the screen reader: what is behind it is
        // hidden from the accessibility tree (TalkBack would otherwise reach the fields under the overlay).
        Box(modifier = if (cropping) Modifier.clearAndSetSemantics {} else Modifier) {
            ProfileContent(viewModel, state.mode)
        }
        state.crop?.let { crop ->
            PhotoCropScreen(
                crop = crop,
                onChanged = viewModel::onCropChanged,
                onConfirm = viewModel::onCropConfirmed,
                onCancel = viewModel::onCropCancelled,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    viewModel: ProfileViewModel,
    mode: OnboardingMode,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box {
        if (mode == OnboardingMode.PHONE_ONLY) {
            PhoneStepScreen(
                state = state,
                onBack = null,
                onCountrySelected = viewModel::onCountrySelected,
                onPhoneChanged = viewModel::onPhoneChanged,
                onSubmit = viewModel::onSubmitPhone,
            )
        } else {
            ProfileSteps(viewModel)
        }
    }
}

@Composable
private fun ProfileSteps(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    // Where the person was when the app was closed (read once; afterwards the navigation leads).
    val firstStep = remember { state.step }
    val goBack: () -> Unit = {
        // Nothing moves while the write is going on (the view model refuses the step back, the screen must agree).
        val idle = !viewModel.uiState.value.isWriting
        if (idle && !navController.popBackStack()) {
            navController.navigate(ProfileStepRoute) { popUpTo<PhoneStepRoute> { inclusive = true } }
        }
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.hasRoute<ProfileStepRoute>()) viewModel.onBackToProfile()
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (firstStep == DraftStep.PHONE) PhoneStepRoute else ProfileStepRoute,
    ) {
        composable<ProfileStepRoute> {
            ProfileStepScreen(
                state = state,
                onFirstNameChanged = viewModel::onFirstNameChanged,
                onLastNameChanged = viewModel::onLastNameChanged,
                onContinue = {
                    viewModel.onContinueFromProfile()
                    if (viewModel.uiState.value.step == DraftStep.PHONE) {
                        navController.navigate(PhoneStepRoute) { launchSingleTop = true }
                    }
                },
                onPhotoChosen = viewModel::onPhotoChosen,
                onRemovePhoto = viewModel::onRemovePhoto,
                onCameraUnavailable = viewModel::onCameraUnavailable,
            )
        }
        composable<PhoneStepRoute> {
            // Opened directly (app reopened on this step): there is nothing to pop, "back" goes to the profile step.
            BackHandler(enabled = navController.previousBackStackEntry == null || state.isWriting, onBack = goBack)
            PhoneStepScreen(
                state = state,
                onBack = goBack,
                onCountrySelected = viewModel::onCountrySelected,
                onPhoneChanged = viewModel::onPhoneChanged,
                onSubmit = viewModel::onSubmitPhone,
            )
        }
    }
}
