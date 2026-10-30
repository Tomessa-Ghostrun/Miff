# Miff Privacy Policy

**Version 0.1 — last updated: January 1, 2027**

Miff is an app that lets you know your loved ones are okay, without bothering them. To make that work, Miff processes some personal data, including information related to your location. This page explains, simply, what is collected, why, who can see it, how long it is kept, and how you stay in control.

## 1. The essentials in 8 points

1. **Miff does not store your GPS position.** Your phone itself checks whether it is inside a "trusted place". Only the result is sent (for example "at Home" or "outside a trusted place"), together with the time. There is no history of your trips.
2. **Sharing is your choice, Miff by Miff.** It only starts after your explicit consent, and you can stop it at any time.
3. **If you stop sharing, your status and your update photo are deleted** from what your circle can see.
4. **Only the members of your Miffs** see what you share. To see other people's status, you must share yours.
5. **Update photos disappear after 24 hours.**
6. **Usage statistics and crash reports are turned on when you click "Join"** (or "Continue with Google") at sign-up (there is no checkbox), and you can turn them off at any time in Settings, as a single choice. Before that click, nothing is sent.
7. **No advertising, no selling of data.**
8. **You can delete your account from the app** and write to us to exercise your rights.

## 2. Who is responsible for your data?

The data controller is:

- **Tomessa MFOUKA (individual)**
- **76 rue Marx Dormoy, 75018 Paris, France**
- **tomessa.mfouka@gmail.com**

You can use this e-mail address for any question about your data.

## 3. What data, why, and on what legal basis

The GDPR requires us to state a "legal basis" for each use, meaning what allows us to process the data.

| Data | What it is used for | Legal basis |
|---|---|---|
| **Account**: e-mail address, account identifier, sign-in method (e-mail, Google, Apple). Your password, if you have one, is handled by Google's authentication service: we cannot see it in clear text. | Create your account, sign you in, recover access | Contract (the Terms) |
| **Profile**: first name, last name, profile photo | So that your loved ones recognise you in the app | Contract |
| **Membership of a Miff**: which Miffs you created or joined, your role (creator or member), join date, the secret code used to join | Make your circle of trust work | Contract |
| **Trusted places**: name, optional address, coordinates of the place, radius. These places are created by the Miff's creator and visible to its members. **These are the coordinates of the place, not yours.** | Know when you arrive at or leave a place | Contract; consent for sharing your presence |
| **Automatic status** (if you turn sharing on): "in a trusted place / outside a place / risk zone / unknown", the place concerned, the time of the event, the time of the last update | Reassure your circle without a conversation | Explicit and revocable **consent** |
| **Manual update** (optional): a short text (140 characters at most) and a photo | Share news when you feel like it | **Consent** (you are the one sending it) |
| **Phone state** (if you turn sharing on): battery level, and whether location is allowed "in the background", "while using the app", or denied | Help your circle understand why a status may be late (e.g. low battery) | Contract / legitimate interest |
| **Proof of consent**: date and version of the Terms accepted by clicking "Join", date and version of the location consent text. Your choice on statistics and crashes is recorded with your account (on our Firebase servers), with its date and the version of the text you saw; a copy of this choice is also kept on your phone. | Be able to demonstrate your agreement | Obligation to be able to demonstrate consent |
| **Premium subscription**: state (active or not), end date, origin (App Store, Google Play, or access granted by the team). We receive neither your card number nor your payment details. | Enable Premium features | Contract |
| **Usage statistics (Google Analytics for Firebase)** — **turned on when you click "Join", can be turned off at any time**: a few simple events (e.g. sign-up completed, Miff created or joined, Premium screen shown) and technical device data. **Never a position, a place, a Miff name, a code, or free text.** | Understand whether the app is clear and fix what blocks people | **Consent** given by clicking "Join", can be withdrawn at any time (see below) |
| **Crash reports (Firebase Crashlytics)** — **turned on when you click "Join", can be turned off at any time**: technical information about a crash (phone model, system version, app version, app state at the time of the crash) | Fix bugs | **Consent** given by clicking "Join", can be withdrawn at any time (see below) |
| **Technical security data**: checking that a request comes from the genuine Miff app (Firebase App Check, which relies on Play Integrity on Android and App Attest on iOS), IP address and technical logs processed by Google's services | Protect the service against abuse | Legitimate interest (security) |
| **Messages you send us** (by e-mail) | Answer your request | Legitimate interest / legal obligation for rights requests |

**How your agreement is collected at sign-up (Terms and statistics).**
- At sign-up, the button is called **"Join"**. Just above it, a sentence says: "By tapping “Join” or “Continue with Google”, you accept our Terms of Use and our Privacy Policy, as well as the sharing of your anonymous usage statistics and crash reports (you can change this at any time in the Settings)."
- **If you sign in with Google from the "Sign in" screen and do not have a Miff account yet**, your account is created before you could read that sentence. A screen then shows it to you right away (“By tapping “Continue”, you accept…”), with two buttons: **"Continue"** and **"Cancel"**. "Cancel" (or the phone's back button) **deletes the account that was just created**, keeping nothing and sending nothing to Google Analytics or Crashlytics.
- **There is no checkbox.** Your click counts both as acceptance of the Terms of Use and the Privacy Policy, and as agreement to the sharing of usage statistics and crash reports. These choices are therefore **given together, in a single gesture, and sending statistics and reports is turned on from the start**.
- **Before this click, nothing is sent** to Google Analytics or Crashlytics.
- **What is collected by default after the click**: the simple events and technical data described in the table above, and crash reports. They never contain your position, a place, a Miff name, a secret code or free text. The app calls them "anonymous": they contain neither your name nor your e-mail address, but they can be linked to a technical installation identifier, and we cannot promise they are anonymous in the strict sense.
- **How to withdraw**: in the app **Settings**, you can turn off usage statistics and crash reports at any time. It is **a single choice for both** (not two separate switches). This changes nothing about your account or your use of Miff.
- **Effect of withdrawal**: sending stops from that moment. What has already been sent is not erased immediately: it is kept until the end of Google's retention period (see article 7).
- **Sharing your location is not covered by this click.** It requires a separate agreement, given Miff by Miff (see article 4).
[CONDITIONAL SECTION — KEEP ONLY IF PUSH NOTIFICATIONS ARE ACTIVE AT LAUNCH: **Notifications**: a technical identifier of your phone (notification token) is recorded to send you notifications, through Apple's and Google's notification services. It is deleted when you sign out. You can turn notifications off in your phone's settings.]

**What Miff does not collect**: your raw GPS position, a history of your movements, your contact list, your advertising identifier for advertising. Miff does not ask for your age or date of birth.

## 4. Background location, explained

This is the most sensitive part, so here is everything.

**Why Miff asks for location "all the time" (in the background).** To automatically tell your circle that you have arrived at or left a trusted place, without you having to open the app. Without this permission, Miff remains usable: you can send an update by hand.

**How it works.** Miff uses the "region monitoring" features of iOS and Android. Your phone itself detects when you enter or leave a zone. Miff does not record continuous tracking of your position and does not build a history of your trips. Only the result (entry or exit, place, time) is sent, and only to the Miffs for which you turned sharing on.

**Your consent.**
- Before any system permission request, Miff explains in the app what location is used for.
- **This agreement is separate from your click on "Join"**: accepting the terms and statistics never shares your location.
- Sharing with a Miff only turns on if you decide so, and it is recorded with the date and the version of the text you saw. Nobody can turn it on for you.
- It is independent from one Miff to another: you can share with one and not with another.
- You can **withdraw your consent at any time**, in the app settings (stop sharing), or by removing the location permission in your phone settings. Withdrawing consent has no consequence on your account. As soon as you stop sharing with a Miff, your status and your update photo for that Miff are deleted.
- If you refuse background location, you can keep using Miff with manual updates.

**No hidden tracking.** Miff is not made to follow someone without their knowledge. The person concerned must know and agree.

## 5. Who sees what?

The members of a Miff see:
- your first name, last name and profile photo;
- the Miff's trusted places (name, optional address, radius);
- **if you share**: your status (in a place, outside a place…), the time, your optional update (text, photo), your battery level and the state of your location permission;
- if you do not share, that your sharing is turned off.

To see other people's status, you must share yours. People who are not in the Miff see nothing. Anyone who knows a Miff's secret code can join it: only give it to people you trust.

The Miff team's access to the database is limited to authorised people and to what is necessary (maintenance, support, security).

We do not sell your data and we do not use it for advertising.

## 6. Service providers and transfers outside the European Union

Miff relies on services from **Google (Google Firebase)** and from **Apple / Google** for sign-in and purchases.

| Provider and service | Role | Where data is processed |
|---|---|---|
| **Google Firebase — Cloud Firestore** | The app's database (profile, Miffs, places, statuses, photos) | **Paris region (europe-west9)** |
| **Google Firebase — Authentication** | Account creation and sign-in | According to Google's documentation, this service runs **from data centres located in the United States** |
| **Google Firebase — App Check** | Check that a request comes from the genuine app | Google data centres, location not specified per service |
| **Google Analytics for Firebase** (turned on by clicking "Join", can be turned off) | Usage statistics | Google data centres, possibly including outside the EU |
| **Firebase Crashlytics** (turned on by clicking "Join", can be turned off) | Crash reports | Google data centres, possibly including outside the EU |
| **Google Sign-In** | Sign in with your Google account (if you choose it) | According to Google's policy |
| **Sign in with Apple** | Sign in with your Apple ID (if you choose it). With Apple, you can choose to hide your e-mail address. | According to Apple's policy |
| **App Store (Apple) / Google Play** | App download, payment and subscription management | According to Apple's and Google's policies |
| [IF PUSH IS ACTIVE: **Firebase Cloud Messaging, Apple Push Notification service**] | [Sending notifications] | [Google / Apple data centres] |

**About transfers outside the EU.** Some of these services may process data outside the European Union, notably in the United States. In that case, protection relies on the mechanisms provided by the GDPR, for example the "EU-U.S. Data Privacy Framework" to which Google states it adheres, and/or standard contractual clauses. You can ask us for more information.

These providers act on our behalf (processors). Google and Apple also act on their own behalf for sign-in and payments: their own privacy policies then apply.

## 7. How long do we keep your data?

Miff keeps no history: the status is a "current state", overwritten at each event.

| Data | Retention period |
|---|---|
| Account and profile | Until you delete your account. For now, Miff does not automatically delete inactive accounts. |
| Status (presence, times, battery, location state) | One single state, replaced at each event. Deleted when you stop sharing, leave the Miff, are removed from the Miff, or delete your account. |
| **Update photo** | **Unreadable to members after 24 hours.** It is then deleted from our servers as soon as possible, by the app of its author or of a member of the Miff when the app is opened. It is also deleted if you stop sharing, leave the Miff or delete your account. Physical deletion at exactly the 24th hour is not guaranteed: in the meantime, the photo is readable by nobody. |
| Profile photo and name copied into a Miff | Until you leave the Miff or delete your account. |
| Trusted places | Until the creator deletes them or the Miff is deleted. |
| A Miff's secret code | Until it is renewed or the Miff is deleted. When the creator removes a member, the code is automatically renewed. |
| Proof of consent | While you are a member of the relevant Miff (for location consent); with your account (for the Terms). We do not keep this proof after you leave the Miff. |
| Premium subscription state | For the duration of the subscription, then deleted 30 days after your account is deleted. |
| Usage statistics (while turned on) | Period set to the minimum offered by Google in its console [TO BE VERIFIED before publication]. |
| Crash reports (while turned on) | Period defined by Google Crashlytics [TO BE VERIFIED before publication]. |
| Messages sent to our contact address | The time needed to handle your request, then 1 year, after which they are deleted. |
| Backups | We do not enable database backups. |
| Data stored on your phone (offline working copy) | Erased when you sign out, delete your account, or lose access to a Miff. |

**Special case: a deleted Miff.** When a creator deletes their Miff (or their account, which also deletes the Miffs they created), the other members are removed. If a former member's app has not been opened yet, the data about them in that Miff (member record, possible status or photo) becomes unreadable to everyone immediately, but is only physically erased the next time their app is opened.

## 8. Your rights and how to exercise them

You have the right to:
- **access** your data;
- have inaccurate data **rectified** (you can edit your profile in the app);
- have your data **erased**;
- **receive** your data in a reusable format (portability);
- **object** to processing based on legitimate interest;
- ask for **restriction** of processing;
- **withdraw your consent** at any time (location, statistics, crash reports), without affecting what was done before;
- set **directives** on what happens to your data after your death (French law).

**How to do it:**
- **Delete your account**: directly in the app, in the settings. This deletes your account and the associated data described above. If you are the **creator** of a Miff, deleting your account also deletes that Miff, with its places, statuses and photos. The other members then lose access to it: the app simply tells them, without any alarm, that the Miff no longer exists, and the data about them in that Miff, which has become unreadable, is erased the next time they open their app. Their own account is not affected and they can create or join another Miff. If you wish, let your loved ones know before deleting your account..
- **Stop sharing your location**: in the app settings, Miff by Miff.
- **Statistics and crash reports**: a single switch in the app Settings (the two are linked). Turning it off stops sending from that moment; what has already been sent is kept according to the periods in article 7.
- **Access or export your data**: during the test phase (beta), write to us at **tomessa.mfouka@gmail.com**. We reply with your data in a readable format.
- **Any other request**: write to us at **tomessa.mfouka@gmail.com**. We may ask you to confirm your identity, to prevent someone from impersonating you.

We reply within **one month** at most (extendable in the cases provided by law).

**Complaint.** If you think your rights are not respected, you can lodge a complaint with the **CNIL** (the French data protection authority), at www.cnil.fr, or with the authority of your country of residence. We would prefer that you write to us first, but you are not required to.

## 9. Security

We protect your data with appropriate measures: strict access rules (only members of a Miff can access that Miff's information), encrypted connections, encryption at rest provided by Google, verification that requests come from the genuine app (App Check), and access to administration tools limited to authorised people. No system is infallible. In the event of a data breach likely to harm you, we will notify the CNIL and, if necessary, you, as the law requires.

Also protect your phone (lock code) and only share a Miff's secret code with people you trust.

## 10. Minors

Miff does not impose a minimum age and does not ask for your age. Miff is designed for circles of loved ones (family, friends, partners), which may include young people. In France, the law provides that **below the age of 15**, the agreement of a parent or holder of parental authority is required for certain online services relying on consent. Miff cannot verify its users' age.

If you are a minor, we advise you to use Miff with your parents' agreement. If you are a parent and think that a child's data was collected without your agreement, write to **tomessa.mfouka@gmail.com**: we will delete their account and data.

Each member gives their own consent to share their location, and can stop at any time.

**Please note:** Miff does not verify its users' age. We therefore cannot check whether a parent's agreement was given where the law requires it.

## 11. Trackers, identifiers and storage on your phone

Miff does not use cookies. The app uses:
- **your phone's local storage**: sign-in session, your preferences (including your choices on statistics and crashes), and a working copy of your Miffs' data to work offline. These items are necessary for the app to work;
- **technical Firebase identifiers** (installation identifier) to make the services work. **For statistics and crash reports, they are only used from your click on "Join", until you turn these options off in Settings.**

Miff does not display advertising and does not "follow" you from one app to another. [TO BE VERIFIED by the development teams before publication: no advertising identifier is used on iOS or Android.]

**The Miff website** (the pages for terms, privacy, account deletion and choosing a new password) uses no cookies and no trackers. Like any hosting service, Firebase Hosting (Google) records your IP address and the pages requested in its technical logs when you visit the site. If you choose a new password from the link in the e-mail, the page sends that new password directly to Google (Firebase Authentication), which records it to update your account; Miff does not see it and does not keep it.

You can change your mind at any time in the app settings.

## 12. Changes to this policy

We may change this policy, for example if the app evolves. For an important change, we tell you in the app before it applies. If the change concerns location or a use that requires your consent, we ask for your consent again. The date and version at the top of the page show the version in force.

## 13. Applicable law

This policy is governed by **French law** and by the GDPR. If you are a consumer, you keep the protection given by the mandatory rules of your country of residence.

---
*Contact: tomessa.mfouka@gmail.com — Tomessa MFOUKA (individual), 76 rue Marx Dormoy, 75018 Paris, France*
