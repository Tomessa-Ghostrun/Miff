package com.tomessa.miff.domain.review

import com.tomessa.miff.domain.feed.Bubble
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.MemberCard
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.feed.StateLabel
import com.tomessa.miff.domain.feed.TimeLine
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.StatusLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Has seen the status of a loved one": a card of SOMEONE ELSE that says where they are, not "pas encore de nouvelles", not my own card. */
class ReviewSituationTest {
    private fun card(
        isMe: Boolean,
        label: StateLabel,
    ) = MemberCard(
        member = MiffMember("u", "Camille", "", null, MiffRole.MEMBER, sharingEnabled = true),
        isMe = isMe,
        level = StatusLevel.GREEN,
        label = label,
        time = TimeLine.None,
        battery = null,
        limitedLocation = false,
        phone = null,
        canLocate = false,
    )

    private fun content(vararg cards: MemberCard) =
        FeedContent(
            miffId = "m",
            miffName = "Famille",
            joinCode = "CODE",
            isFull = false,
            myLevel = StatusLevel.GREEN,
            myLabel = StateLabel.NoNews,
            miffLevel = StatusLevel.GREEN,
            bubble = Bubble.NoNews,
            banners = emptyList(),
            cards = cards.toList(),
            announceKey = "k",
        )

    private val home = StateLabel.At(PlaceRef.Category(PlaceKind.HOME))

    @Test
    fun aLovedOnesPlace_orDeparture_orDangerZone_isAStatusSeen() {
        assertTrue(ReviewSituation.friendStatusSeen(content(card(false, home))))
        assertTrue(ReviewSituation.friendStatusSeen(content(card(false, StateLabel.Left(PlaceRef.Unknown)))))
        assertTrue(ReviewSituation.friendStatusSeen(content(card(false, StateLabel.Danger))))
    }

    @Test
    fun noNews_stoppedSharing_myOwnCard_orNoFeed_isNot() {
        assertFalse(ReviewSituation.friendStatusSeen(content(card(false, StateLabel.NoNews))))
        assertFalse(ReviewSituation.friendStatusSeen(content(card(false, StateLabel.SharingStopped))))
        assertFalse(ReviewSituation.friendStatusSeen(content(card(true, home))))
        assertFalse(ReviewSituation.friendStatusSeen(content()))
        assertFalse(ReviewSituation.friendStatusSeen(null))
    }

    @Test
    fun oneCardIsEnough_amongOthers() {
        assertTrue(
            ReviewSituation.friendStatusSeen(
                content(card(false, StateLabel.NoNews), card(false, home), card(true, home)),
            ),
        )
    }
}
