package com.tomessa.miff.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.update.PhotoCleaner
import com.tomessa.miff.data.update.PublishLedger
import com.tomessa.miff.data.update.PublishResult
import com.tomessa.miff.data.update.UpdatePublisher
import com.tomessa.miff.domain.feed.MyPhotoFacts
import com.tomessa.miff.domain.update.CameraAccess
import com.tomessa.miff.domain.update.CameraAccessScreen
import com.tomessa.miff.domain.update.UpdatePhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * PRIVACY and SECURITY, for this file: the photo taken lives HERE, in memory, and nowhere else (never on the disk, a log, an event, the state saved by the
 * system, the clipboard). It is forgotten as soon as the person leaves the Update, retakes, publishes, or waits more than 5 minutes. Nothing is written
 * until "Publier" (criterion V2), and "Publier" exists only on the preview of a photo that was taken (D16, criterion U9): no path of this class
 * publishes without a photo.
 */

/** What the Update screen shows. */
sealed interface UpdateStage {
    /** Not opened yet (or left): nothing is drawn, the camera is off. */
    data object Idle : UpdateStage

    /** A: the pop-in that says why, before the box of the system. */
    data object Explain : UpdateStage

    /** B: "On réessaie ?". */
    data object RetryPermission : UpdateStage

    /** C: the camera is off for Miff (refused for good). */
    data object PermissionDisabled : UpdateStage

    /** D: the camera cannot be used now (another application, a policy, no camera). */
    data object CameraUnavailable : UpdateStage

    /** J: sharing is stopped in all my Miffs: no viewfinder, no permission asked. */
    data object SharingStopped : UpdateStage

    /** The viewfinder. [notice] is the calm sentence under it when the last photo could not be prepared. */
    data class Viewfinder(
        val showTip: Boolean,
        val prepareFailed: Boolean = false,
    ) : UpdateStage

    /** The preview of a photo taken: "Recommencer" and "Publier". */
    data class Preview(
        val photo: UpdatePhoto,
        val publishing: Boolean = false,
        val notice: PreviewNotice? = null,
        /** After a partial success: the Miffs the photo did not reach (null = all). */
        val remaining: List<String>? = null,
    ) : UpdateStage {
        override fun toString(): String = "Preview(redacted)"
    }
}

/** The calm sentence shown over the photo when "Publier" did not end in a publication. The photo is always still there. */
sealed interface PreviewNotice {
    data object Offline : PreviewNotice

    data object PositionNotFound : PreviewNotice

    data object TooMany : PreviewNotice

    data object TooSoon : PreviewNotice

    data object Stale : PreviewNotice

    data object Refused : PreviewNotice

    data object Failed : PreviewNotice

    /** The permission of the location was taken back, or the location of the phone is off: "Ouvrir les réglages". */
    data object LocationOff : PreviewNotice

    /** No answer in time: it may have left ("On n'a pas pu confirmer. Regarde dans ton Feed."). */
    data object Unconfirmed : PreviewNotice

    data object NeedsAgreement : PreviewNotice

    data object SharingStopped : PreviewNotice

    data class Partial(
        val sharedIn: Int,
        val total: Int,
    ) : PreviewNotice
}

/** What the screen must do once, outside its own drawing. */
sealed interface UpdateEvent {
    /** The photo is published: back to the Feed, with the calm message. */
    data class Published(
        val sharedIn: Int,
    ) : UpdateEvent
}

class UpdateViewModel(
    private val publisher: UpdatePublisher,
    private val cleaner: PhotoCleaner,
    private val ledger: PublishLedger,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableStage = MutableStateFlow<UpdateStage>(UpdateStage.Idle)
    val stage: StateFlow<UpdateStage> = mutableStage.asStateFlow()

    private val mutableEvents = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UpdateEvent> = mutableEvents.asSharedFlow()

    private var publishJob: Job? = null
    private var cleanJob: Job? = null

    /**
     * The Update was opened (the tab came on screen). [sharingStopped]: sharing is stopped in all my Miffs. A photo still waiting (the screen was only hidden
     * by the app going to the background) is kept for 5 minutes and then forgotten.
     */
    fun onOpened(
        granted: Boolean,
        shouldShowRationale: Boolean,
        sharingStopped: Boolean,
    ) {
        val current = mutableStage.value
        if (current is UpdateStage.Preview) {
            if (!current.photo.isStale(nowMillis())) return
            mutableStage.value = UpdateStage.Preview(current.photo, notice = PreviewNotice.Stale)
            return
        }
        // Opened again while the viewfinder is already there (the app came back to the foreground): nothing changes, the opening is not counted twice.
        if (current is UpdateStage.Viewfinder && granted && !sharingStopped) return
        if (sharingStopped) {
            mutableStage.value = UpdateStage.SharingStopped
            return
        }
        mutableStage.value =
            when (CameraAccess.forOpening(granted, ledger.cameraExplained(), shouldShowRationale)) {
                CameraAccessScreen.VIEWFINDER -> viewfinder()
                CameraAccessScreen.EXPLAIN -> UpdateStage.Explain
                CameraAccessScreen.RETRY -> UpdateStage.RetryPermission
                CameraAccessScreen.DISABLED -> UpdateStage.PermissionDisabled
            }
    }

    /** "Continuer": the explanation was accepted, so the box of the system is about to be shown (the screen asks for it right after this). */
    fun onExplanationAccepted() = ledger.markCameraExplained()

    fun onPermissionAnswered(
        granted: Boolean,
        shouldShowRationale: Boolean,
    ) {
        mutableStage.value =
            when (CameraAccess.afterRequest(granted, shouldShowRationale)) {
                CameraAccessScreen.VIEWFINDER -> viewfinder()
                CameraAccessScreen.EXPLAIN, CameraAccessScreen.RETRY -> UpdateStage.RetryPermission
                CameraAccessScreen.DISABLED -> UpdateStage.PermissionDisabled
            }
    }

    private fun viewfinder(): UpdateStage.Viewfinder {
        // The tip of the double tap: the first three openings only (V3).
        val openings = ledger.viewfinderOpenings()
        ledger.countViewfinderOpening()
        return UpdateStage.Viewfinder(showTip = openings < TIP_OPENINGS)
    }

    /** The viewfinder is back (after a refusal on the way, or a "Réessayer"). */
    fun onRetryCamera(
        granted: Boolean,
        shouldShowRationale: Boolean,
    ) {
        mutableStage.value =
            when (CameraAccess.forOpening(granted, ledger.cameraExplained(), shouldShowRationale)) {
                CameraAccessScreen.VIEWFINDER -> viewfinder()
                CameraAccessScreen.EXPLAIN -> UpdateStage.Explain
                CameraAccessScreen.RETRY -> UpdateStage.RetryPermission
                CameraAccessScreen.DISABLED -> UpdateStage.PermissionDisabled
            }
    }

    /** The camera could not start (another application holds it, a policy, no camera): state D, never a technical message. */
    fun onCameraUnavailable() {
        if (mutableStage.value is UpdateStage.Viewfinder) mutableStage.value = UpdateStage.CameraUnavailable
    }

    /** A photo was taken and prepared (the viewfinder, or the camera application of the phone). Only from the viewfinder or the unavailable-camera screen. */
    fun onPhotoReady(photo: UpdatePhoto) {
        val current = mutableStage.value
        if (current is UpdateStage.Viewfinder || current is UpdateStage.CameraUnavailable ||
            current is UpdateStage.PermissionDisabled
        ) {
            mutableStage.value = UpdateStage.Preview(photo)
        }
    }

    /** The photo taken could not be prepared (not decodable, or it never fits in 150 000 bytes): a calm sentence, nothing is written. */
    fun onPhotoFailed() {
        val current = mutableStage.value
        mutableStage.value =
            when (current) {
                is UpdateStage.Viewfinder -> {
                    current.copy(prepareFailed = true)
                }

                is UpdateStage.CameraUnavailable, is UpdateStage.PermissionDisabled -> {
                    UpdateStage.Viewfinder(
                        showTip = false,
                        prepareFailed = true,
                    )
                }

                else -> {
                    current
                }
            }
    }

    /**
     * "Recommencer" AND the cross of the preview (D22: the same gesture): back to the viewfinder, the photo is forgotten, nothing is published, no question
     * asked. Not while a publication is under way (it cannot be cancelled once sent).
     */
    fun onRetake() {
        val current = mutableStage.value as? UpdateStage.Preview ?: return
        if (current.publishing) return
        mutableStage.value = UpdateStage.Viewfinder(showTip = false)
    }

    /** The person left the Update (another tab, back): the photo is forgotten, whatever happened. A publication already sent keeps going. */
    fun onClosed() {
        if (publishJob?.isActive == true) return
        mutableStage.value = UpdateStage.Idle
    }

    /** "Publier". Only on the preview of a photo. */
    fun onPublish() {
        val current = mutableStage.value as? UpdateStage.Preview ?: return
        if (current.publishing) return
        mutableStage.value = current.copy(publishing = true, notice = null)
        publishJob =
            viewModelScope.launch {
                val result = publisher.publish(current.photo, only = current.remaining)
                val now = mutableStage.value as? UpdateStage.Preview
                when (result) {
                    is PublishResult.Published -> {
                        mutableStage.value = UpdateStage.Idle
                        mutableEvents.tryEmit(UpdateEvent.Published(result.sharedIn))
                    }

                    is PublishResult.Partial -> {
                        if (now != null) {
                            mutableStage.value =
                                now.copy(
                                    publishing = false,
                                    notice = PreviewNotice.Partial(result.sharedIn, result.total),
                                    remaining = result.remaining,
                                )
                        }
                    }

                    else -> {
                        if (now != null) mutableStage.value = now.copy(publishing = false, notice = noticeOf(result))
                    }
                }
            }
    }

    private fun noticeOf(result: PublishResult): PreviewNotice =
        when (result) {
            PublishResult.Offline -> PreviewNotice.Offline
            PublishResult.PositionNotFound -> PreviewNotice.PositionNotFound
            PublishResult.TooMany -> PreviewNotice.TooMany
            PublishResult.TooSoon -> PreviewNotice.TooSoon
            PublishResult.Stale -> PreviewNotice.Stale
            PublishResult.Refused -> PreviewNotice.Refused
            PublishResult.LocationOff -> PreviewNotice.LocationOff
            PublishResult.Unconfirmed -> PreviewNotice.Unconfirmed
            PublishResult.NeedsAgreement -> PreviewNotice.NeedsAgreement
            PublishResult.SharingStopped -> PreviewNotice.SharingStopped
            else -> PreviewNotice.Failed
        }

    /** At the launch and at the return to the foreground: removes MY photo from the database once it has expired (no reading of the server). */
    fun cleanUpExpiredPhoto(mine: MyPhotoFacts? = null) {
        if (cleanJob?.isActive == true) return
        cleanJob = viewModelScope.launch { cleaner.cleanUp(mine) }
    }

    override fun onCleared() {
        // The view model of the session goes with the account: no photo stays in memory.
        mutableStage.value = UpdateStage.Idle
    }

    private companion object {
        const val TIP_OPENINGS = 3
    }
}
