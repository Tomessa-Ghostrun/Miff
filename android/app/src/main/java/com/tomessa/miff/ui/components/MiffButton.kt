package com.tomessa.miff.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyLarge

/**
 * Primary button of the design system: action/primary background, shadow/default, radius/md,
 * disabled state = background at 30 %. The text is allowed to wrap (large fonts, long translations).
 */
@Composable
fun MiffButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colorActionPrimary,
                contentColor = colorTextOnAction,
                disabledContainerColor = colorActionPrimaryTint30,
                disabledContentColor = colorTextOnAction,
            ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
        modifier =
            modifier
                .padding(vertical = Spacing.xs)
                .then(if (enabled) Modifier.miffShadow(ShadowDefault, ShapeMd) else Modifier)
                .heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(text = text, style = textBodyLarge)
    }
}

/**
 * Secondary button: white, blue text and thin blue border (the "Annuler" of the mockups). For the actions that must not
 * look like the main thing to do (signing out, for instance). Same height, radius and wrapping as [MiffButton].
 */
@Composable
fun MiffSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colorBgSurface,
                contentColor = colorActionPrimary,
                disabledContainerColor = colorBgSurface,
                disabledContentColor = colorActionPrimaryTint30,
            ),
        border = BorderStroke(StrokeAccent, if (enabled) colorActionPrimary else colorActionPrimaryTint30),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
        modifier = modifier.padding(vertical = Spacing.xs).heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(text = text, style = textBodyLarge)
    }
}

/**
 * Destructive button (deletion of the account, decision of the PM): red text and thin red border on a WHITE background, never a
 * red fill (contrast, and the tone stays calm). The meaning is carried by the words, not by the colour alone. Same height, radius
 * and wrapping as [MiffButton]. Always the same weight as the safe choice next to it.
 */
@Composable
fun MiffDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colorBgSurface,
                contentColor = colorTextError,
                disabledContainerColor = colorBgSurface,
                disabledContentColor = colorTextError.copy(alpha = DISABLED_ALPHA),
            ),
        border =
            BorderStroke(
                StrokeAccent,
                if (enabled) colorTextError else colorTextError.copy(alpha = DISABLED_ALPHA),
            ),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
        modifier = modifier.padding(vertical = Spacing.xs).heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(text = text, style = textBodyLarge)
    }
}

private const val DISABLED_ALPHA = 0.4f
