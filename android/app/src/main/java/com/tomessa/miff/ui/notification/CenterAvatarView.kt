package com.tomessa.miff.ui.notification

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.notification.CenterAvatar
import com.tomessa.miff.ui.theme.colorStatusNeutral
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold14

/** The size of the round picture of a line of the centre (CNC-L: the heart of the logo is checked at this size). */
val CenterAvatarSize: Dp = 40.dp

/** The tag of the round picture, for the tests (never spoken). */
const val CENTER_AVATAR_TAG = "center-avatar"

/**
 * CNC-A / CNC-L: the round picture of a line of the centre of notifications: the photo of the person, her initials, the logo of Miff, or (while a reading is under way) a neutral round with
 * nothing on it, so that nothing flickers. DECORATIVE for the screen reader: the line says who it is about in its words ("Léa a donné des nouvelles"), the picture is never the only way to
 * know. A photo is decoded once per photo (it is 20 000 bytes at most: the rules are applied before, see [com.tomessa.miff.domain.notification.CenterAvatars]); one that cannot be decoded
 * falls back to the initials.
 */
@Composable
fun CenterAvatarView(
    avatar: CenterAvatar,
    modifier: Modifier = Modifier,
    size: Dp = CenterAvatarSize,
) {
    Box(
        // No description on purpose: the picture is decorative. The tag is for the tests of the size and of the side only (it is not read aloud).
        modifier = modifier.size(size).clip(CircleShape).clearAndSetSemantics { testTag = CENTER_AVATAR_TAG },
        contentAlignment = Alignment.Center,
    ) {
        when (avatar) {
            CenterAvatar.Logo -> {
                Image(
                    painter = painterResource(R.drawable.ic_miff_logo_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            CenterAvatar.Placeholder -> {
                Box(Modifier.fillMaxSize().background(colorStatusNeutral))
            }

            is CenterAvatar.Initials -> {
                Initials(avatar.text)
            }

            is CenterAvatar.Photo -> {
                // Keyed by the CONTENT of the photo: each reading brings a new array of the same photo.
                val bitmap =
                    remember(avatar.jpeg.size, avatar.jpeg.contentHashCode()) {
                        BitmapFactory.decodeByteArray(avatar.jpeg, 0, avatar.jpeg.size)?.asImageBitmap()
                    }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Initials(avatar.initials)
                }
            }
        }
    }
}

@Composable
private fun Initials(text: String) {
    Box(Modifier.fillMaxSize().background(colorStatusNeutral), contentAlignment = Alignment.Center) {
        Text(text = text, style = textBodyBold14, color = colorTextForm, maxLines = 1)
    }
}
