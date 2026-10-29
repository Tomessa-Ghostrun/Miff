package com.tomessa.miff.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
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
