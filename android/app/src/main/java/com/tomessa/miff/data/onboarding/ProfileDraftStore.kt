package com.tomessa.miff.data.onboarding

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.data.session.SessionTeardown
import java.io.File
import java.io.IOException

/** The step reached in the profile creation. The step is read from the draft after the app was closed (ticket 6.2). */
enum class DraftStep { PROFILE, PHONE }

/**
 * What the person typed so far in the profile steps, kept ON THE DEVICE until the server has confirmed the profile
 * (Onboarding ticket 6.2). Tied to the account. NEVER contains a password, a token or an e-mail address.
 *
 * It holds personal data (names, photo, phone number): private storage of the app only (no external storage, no backup:
 * `allowBackup` is off and the backup rules exclude everything), erased once the profile is confirmed, at every sign-out
 * and as soon as another account asks for it.
 */
data class ProfileDraft(
    val uid: String,
    val step: DraftStep,
    val firstName: String,
    val lastName: String,
    /** The country chosen in the selector ("FR"); null = not chosen yet, the default of the device applies. */
    val regionCode: String?,
    /** The number as typed (national format, spaces...), NOT the E.164 form. */
    val phoneText: String,
    /** The cropped JPEG (at most 20 000 bytes). */
    val avatar: ByteArray?,
)

interface ProfileDraftStore {
    /** The draft of THIS account. A draft of another account is erased and ignored (ticket 9, case 33). */
    fun read(uid: String): ProfileDraft?

    fun save(draft: ProfileDraft)

    fun clear()
}

/** What the end of a session must do: the draft (names, photo, phone number) goes with it. Registered in AppContainer. */
fun ProfileDraftStore.asTeardown(): SessionTeardown = SessionTeardown { clear() }

class SharedPreferencesProfileDraftStore(
    context: Context,
) : ProfileDraftStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_profile_draft", Context.MODE_PRIVATE)
    private val avatarFile = File(File(context.applicationContext.filesDir, "profile_draft"), "avatar.jpg")

    /** The last avatar written: the file is only rewritten when the picture changes (identity), not at each keystroke. */
    private var lastAvatarWritten: ByteArray? = null

    override fun read(uid: String): ProfileDraft? {
        val savedUid = prefs.getString(KEY_UID, null) ?: return null
        if (savedUid != uid) {
            clear()
            return null
        }
        val step =
            runCatching {
                DraftStep.valueOf(
                    prefs.getString(KEY_STEP, "") ?: "",
                )
            }.getOrDefault(DraftStep.PROFILE)
        val avatar = readAvatar()
        lastAvatarWritten = avatar
        return ProfileDraft(
            uid = savedUid,
            step = step,
            firstName = prefs.getString(KEY_FIRST_NAME, "").orEmpty(),
            lastName = prefs.getString(KEY_LAST_NAME, "").orEmpty(),
            regionCode = prefs.getString(KEY_REGION, null),
            phoneText = prefs.getString(KEY_PHONE, "").orEmpty(),
            avatar = avatar,
        )
    }

    override fun save(draft: ProfileDraft) {
        // apply(): written in the background, in order. The avatar file is written first (20 KB at most, only on change).
        writeAvatarIfChanged(draft.avatar)
        prefs.edit {
            putString(KEY_UID, draft.uid)
            putString(KEY_STEP, draft.step.name)
            putString(KEY_FIRST_NAME, draft.firstName)
            putString(KEY_LAST_NAME, draft.lastName)
            if (draft.regionCode == null) remove(KEY_REGION) else putString(KEY_REGION, draft.regionCode)
            putString(KEY_PHONE, draft.phoneText)
        }
    }

    override fun clear() {
        // commit = true: gone from the disk before returning (the personal data must not wait for a background write).
        prefs.edit(commit = true) { clear() }
        deleteAvatarFile()
        lastAvatarWritten = null
    }

    private fun readAvatar(): ByteArray? =
        try {
            if (avatarFile.isFile && avatarFile.length() in 1..MAX_AVATAR_FILE_BYTES) avatarFile.readBytes() else null
        } catch (e: IOException) {
            null
        }

    private fun writeAvatarIfChanged(avatar: ByteArray?) {
        if (avatar === lastAvatarWritten) return
        try {
            if (avatar == null) {
                deleteAvatarFile()
            } else {
                avatarFile.parentFile?.mkdirs()
                val temp = File(avatarFile.parentFile, "avatar.tmp")
                temp.writeBytes(avatar)
                if (!temp.renameTo(avatarFile)) {
                    avatarFile.delete()
                    temp.renameTo(avatarFile)
                }
            }
            lastAvatarWritten = avatar
        } catch (e: IOException) {
            // The draft is a convenience: a photo that cannot be kept is simply asked again.
        }
    }

    private fun deleteAvatarFile() {
        runCatching {
            avatarFile.delete()
            File(avatarFile.parentFile, "avatar.tmp").delete()
        }
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_STEP = "step"
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_REGION = "region"
        const val KEY_PHONE = "phone_text"

        /** A file bigger than the stored limit is not ours: ignored. */
        const val MAX_AVATAR_FILE_BYTES = 20_000L
    }
}
