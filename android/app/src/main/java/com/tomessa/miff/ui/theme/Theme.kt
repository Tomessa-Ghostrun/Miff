package com.tomessa.miff.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Light-only color scheme (the mockups have no dark mode): the system dark mode is deliberately ignored. */
private val MiffColorScheme =
    lightColorScheme(
        primary = colorActionPrimary,
        onPrimary = colorTextOnAction,
        primaryContainer = colorActionPrimaryTint20,
        onPrimaryContainer = colorTextPrimary,
        secondary = colorBrandPrimary,
        onSecondary = colorTextPrimary,
        background = colorBgCanvas,
        onBackground = colorTextPrimary,
        surface = colorBgSurface,
        onSurface = colorTextPrimary,
        surfaceVariant = colorBgCanvas,
        onSurfaceVariant = colorTextDescription,
        outline = colorBorderStrong,
        outlineVariant = colorBorderDefault,
        error = colorTextError,
        onError = colorTextOnAction,
    )

private val MiffTypography =
    Typography(
        displayLarge = textDisplayHero,
        headlineLarge = textDisplayHeadline,
        headlineMedium = textDisplayTitle,
        headlineSmall = textDisplayLabel,
        titleLarge = textTitleBold24,
        titleMedium = textTitleBold20,
        titleSmall = textBodyBold16,
        bodyLarge = textBodyLarge,
        bodyMedium = textBodyRegular,
        bodySmall = textBodyCaption,
        labelLarge = textBodyBold16,
        labelMedium = textBodyBold14,
        labelSmall = textCaptionBold12,
    )

@Composable
fun MiffTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MiffColorScheme,
        typography = MiffTypography,
        content = content,
    )
}
