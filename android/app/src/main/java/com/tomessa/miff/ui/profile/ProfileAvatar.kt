package com.tomessa.miff.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.StrokeAvatarRing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textTitleBold24

/**
 * The round photo of the person, or their initials when there is none (Profile tab, "Mes informations"). Decorative for the
 * screen reader: the parent says who it is (name, role), a photo has no useful description of its own.
 *
 * [jpeg] is the stored photo (square JPEG of 20 000 bytes at most), decoded once per photo.
 */
@Composable
fun ProfileAvatar(
    jpeg: ByteArray?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap =
        remember(jpeg) {
            jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    Box(
        modifier =
            modifier
                .size(size)
                .miffShadow(ShadowDefault, CircleShape)
                .clip(CircleShape)
                .background(if (bitmap != null) colorBgSurface else colorActionPrimaryTint20)
                .border(StrokeAvatarRing, colorBgSurface, CircleShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials,
                style = textTitleBold24,
                color = colorActionPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
