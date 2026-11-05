# App Store : étiquettes de confidentialité, textes d'usage de la localisation, note de revue (préparation)

Préparé le 2026-11-04 par `release-manager`. **Rien n'est soumis, aucun formulaire App Store Connect n'est rempli.** Le PM remplit lui-même (§7). Voir `README.md` de ce dossier pour les points ouverts.

## 1. Ce qu'Apple dit (lu le 2026-11-04, App Review Guidelines et « App privacy details »)

- **5.1.2(i)** : « Your app may not require users to enable system functionalities (such as push notifications, location services, tracking) in order to access functionality, content, use the app... ». C'est exactement ce que fait le blocage « Toujours » décidé par le PM : **risque de rejet assumé par le PM le 2026-11-01 (R1)**, avec un **interrupteur de compilation** qui relâche le blocage si Apple refuse (§5 et §6).
- **5.1.1(iv)** : ne pas manipuler ni forcer à consentir à un accès inutile ; « si l'utilisateur refuse de partager la localisation, proposer par exemple de saisir une adresse à la main » (exemple d'alternative cité par Apple).
- **5.1.1(i)** : politique de confidentialité dans la fiche **et** dans l'app, qui dit quelles données, comment, à quoi, durées de conservation, comment retirer son consentement et demander la suppression. (Faite : `docs/legal/politique-confidentialite.*.md`.)
- **5.1.1(v)** : suppression de compte **dans l'app** (l'export par e-mail reste compatible).
- **5.1.5** : services de localisation seulement s'ils sont directement liés au service ; **notifier et obtenir le consentement avant de collecter, transmettre ou utiliser** la position ; expliquer la finalité dans l'app.
- **5.1.1(ii)** et textes d'usage : « Ensure your purpose strings clearly and completely describe your use of the data » : la phrase d'usage doit décrire **complètement** l'usage, donc **dire que la dernière position est visible des membres de la Miff**.
- **2.5.4** : les services d'arrière-plan ne servent que leur usage prévu (la localisation en fait partie). Ne déclarer **aucun** mode d'arrière-plan inutile (`UIBackgroundModes`) : à confirmer par `ios-dev` (la surveillance de régions et le changement de position significatif ne semblent pas l'exiger).
- **5.1.4** (enfants) : applicable aux apps de la catégorie Enfants ou qui collectent des données personnelles d'un mineur (politique de confidentialité obligatoire). Voir O7 du README.
- Les labels : « Precise Location » = « lat/long avec **trois décimales ou plus** » ; « linked to the user » : toute donnée de compte l'est sauf si elle est dé-identifiée avant collecte ; « tracking » = lier les données à des données tierces pour de la publicité ou les partager avec un courtier : **non** pour Miff.

## 2. Étiquettes de confidentialité (App Store Connect > Confidentialité de l'app), proposition

Tracking : **Non** (`NSPrivacyTracking = false`, aucun domaine de suivi, ni AdSupport ni App Tracking Transparency).

| Type Apple | Collecté | Lié à l'utilisateur | Suivi | Finalités | Notes |
|---|---|---|---|---|---|
| **Location > Precise Location** | **Oui** (dernière position connue : latitude, longitude, 5 décimales, donc « précise » au sens d'Apple) | **Oui** (rattachée à son compte) | Non | **App Functionality** | **Nouveau depuis le 2026-11-01** (avant : aucune position collectée). Jamais de finalité d'analyse ni de publicité |
| Location > Coarse Location | **À confirmer** : zone approximative déduite de l'adresse IP par Google Analytics | Non (selon la documentation Google Analytics) | Non | Analytics | À recopier de la documentation Firebase/Google Analytics du jour (la page Firebase consultée ne donne pas la classification) |
| Contact Info > **Name** | Oui | Oui | Non | App Functionality | Prénom, nom |
| Contact Info > **Email Address** | Oui | Oui | Non | App Functionality | |
| Contact Info > **Phone Number** | Oui | Oui | Non | App Functionality | Obligatoire à la création du profil, visible des membres de la Miff |
| User Content > **Photos or Videos** | Oui (facultatives) | Oui | Non | App Functionality | Photo de profil ; photo de mise à jour (24 h) |
| User Content > **Other User Content** | Oui (facultatif) | Oui | Non | App Functionality | Le petit texte de mise à jour (140 caractères) |
| Identifiers > **User ID** | Oui | Oui | Non | App Functionality | Identifiant de compte Firebase |
| Identifiers > **Device ID** | **À confirmer** (identifiant d'installation Firebase, identifiant aléatoire de session) | À confirmer | Non | App Functionality, Analytics | À trancher avec la documentation Firebase du jour ; **aucun IDFA** |
| Usage Data > **Product Interaction** | Oui, si Analytics activé (activé au clic « Rejoindre ») | **À confirmer** (non lié si l'app n'appelle pas `setUserId` ; à vérifier par `ios-dev`) | Non | Analytics | Facultatif pour la personne (interrupteur dans les Réglages) |
| Diagnostics > **Crash Data** | Oui, si activé | À confirmer | Non | App Functionality | Crashlytics |
| Purchases > Purchase History | **À confirmer** (état d'abonnement ; aucun paiement connu de nous) | Oui | Non | App Functionality | À revoir avec le ticket Premium |
| Contacts, Health, Financial Info, Browsing/Search History, Sensitive Info | **Non** | | | | **Search History (à confirmer)** : le texte tapé pour chercher une adresse est envoyé à un service de cartes ; il n'est pas gardé par Miff. Le PM ou `ios-dev` tranche selon la réponse d'Apple (`MKLocalSearchCompleter`) |

Les SDK Firebase embarquent leur propre manifeste de confidentialité ; l'app doit aussi déclarer les siens (§3).

## 3. Manifeste de confidentialité de l'app (`PrivacyInfo.xcprivacy`) : à mettre à jour par `ios-dev` avec la fonction

Aujourd'hui le fichier dit « l'app ne collecte AUCUNE position à ce stade » (`ios/Miff/Resources/PrivacyInfo.xcprivacy`) et la CI vérifie l'**absence** de `NSLocation*` (`ios/ci/check-binaries.sh`, `AppConfigurationTests`, `ios/project.yml`). Avec LOC-4, ces garde-fous doivent être **réécrits dans la même PR** (ticket Lieux §9.4). Entrées à ajouter dans `NSPrivacyCollectedDataTypes` : `NSPrivacyCollectedDataTypePreciseLocation` (Linked : vrai, Tracking : faux, Purposes : `NSPrivacyCollectedDataTypePurposeAppFunctionality`), puis les photos, le contenu utilisateur et, si confirmé, l'identifiant d'appareil. Le manifeste, les étiquettes d'App Store Connect et la politique de confidentialité doivent dire **la même chose**.

## 4. Textes d'usage de la localisation (Info.plist, `InfoPlist.xcstrings` du dépôt)

Ils remplacent les textes du ticket Lieux §11, **devenus faux** depuis la décision du 2026-11-01 (ils disaient « jamais ta position précise »). Ils décrivent complètement l'usage, comme l'exige Apple.

**`NSLocationAlwaysAndWhenInUseUsageDescription`** (la fenêtre « Toujours »)

| Langue | Texte |
|---|---|
| Français | « Pour signaler à ta Miff que tu arrives dans un lieu de confiance ou que tu le quittes, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue, avec l'heure, sans historique. Tu peux arrêter à tout moment. » |
| English | "To let your Miff know when you arrive at or leave a trusted place, even when the app is closed, Miff needs your location in the background. The members of your Miff who also share can see your status and your last known location, with the time, with no history. You can stop at any time." |

**`NSLocationWhenInUseUsageDescription`** (la première fenêtre, « Pendant l'utilisation »)

| Langue | Texte |
|---|---|
| Français | « Miff utilise ta position pour savoir quand tu arrives dans un lieu de confiance ou que tu le quittes, et pour que les membres de ta Miff qui partagent aussi voient ta dernière position connue, avec l'heure. » |
| English | "Miff uses your location to know when you arrive at or leave a trusted place, and so that the members of your Miff who also share can see your last known location, with the time." |

À savoir : (1) `NSLocationAlwaysUsageDescription` (ancien) n'est pas nécessaire avec iOS 16 comme version minimale ; (2) ces textes décrivent les **décisions du 2026-11-01** : si le PM modifie la visibilité de la position, ils changent aussi ; (3) cohérence obligatoire avec `docs/legal/consentement-localisation-v3.md`, la politique de confidentialité et l'écran d'autorisation (`design/screens/lieux/permission-localisation-ios.md`).

## 5. Note de revue Apple (champ « Notes » d'App Store Connect > Informations de revue de l'app), en anglais

À copier-coller quand l'app est prête **et seulement alors**. Elle est honnête sur le blocage : Apple le constaterait de toute façon en testant l'app.

> **About background location ("Always") and guideline 5.1.2(i)**
>
> Miff is a reassurance app for a person's circle of trust (family, friends, partner). Its only purpose is to let people know their loved ones are okay without disturbing them: with the user's explicit consent, the app automatically tells the user's circle (a "Miff") when the user arrives at or leaves a place the circle has set up (home, school, work), without the user opening the app.
>
> **Why "Always" is required.** Arrivals and departures happen while the app is closed and the phone is in a pocket. Miff uses Core Location region monitoring and significant-location-change monitoring, which wake the app on those events. With "While Using" access the single feature of the app cannot work: there would be nothing for the app to do. We are aware of guideline 5.1.2(i). We believe this is a case where the system functionality is the core of the service itself (as with family location apps), and we explain it to the user before the system prompt (screen "Know that you're okay!") in plain language. If Apple considers that the app must remain usable without "Always", we have prepared a build setting that relaxes the requirement (a "Later" link on the permission screen) and will ship it on request.
>
> **What is collected.** Only the **last known location** (latitude, longitude, time of measurement), stored on our servers (Firebase, Paris region), replacing the previous one. **No history of trips.** It is visible only to the members of the same Miff whose sharing is active and who also share theirs; the Miff creator has no extra rights. It is erased when the user stops sharing, leaves the Miff, is removed, or deletes the Miff or the account. After 24 hours the app shows it hidden, with its date. It is never sent to analytics or crash-reporting, never used for advertising, never sold. App Privacy answers reflect this (Precise Location, linked to the user, App Functionality; no tracking).
>
> **Consent.** The user agrees separately for each Miff on a screen that states the data shared and that it works even when the app is closed (consent text version 2026-11-v03). Sharing can be stopped at any time in the app. Account deletion is available in the app (Settings).
>
> **How to test.** Sign in with the demo account provided below (it already belongs to a Miff). Because arrivals and departures cannot be reproduced at a desk, we attached a short video of the feature on a real device, and the app also shows the status of the Miff members after you change the simulated location in Xcode (Debug > Simulate Location).
>
> Privacy policy: [URL à renseigner après déploiement du site]. Contact: tomessa.mfouka@gmail.com.

**À remplacer par le PM avant d'envoyer** : l'URL de la politique (le site n'est pas encore déployé), les identifiants du compte de démonstration, le fait qu'une vidéo est jointe (Apple accepte une pièce jointe). **La dernière phrase du deuxième paragraphe (la variante prête « sur demande ») est facultative : la garder révèle à Apple que le blocage peut être relâché ; c'est au PM de décider.** La phrase sur « Simulate Location in Xcode » suppose que le relecteur dispose de Xcode ; si ce n'est pas le cas, retirer cette phrase et ne garder que la vidéo (à confirmer par `ios-dev`).

## 6. Plan B si Apple refuse (5.1.2(i) ou 5.1.1(iv))

1. **Répondre dans le Resolution Center** : reprendre l'argumentaire du §5 (la localisation en arrière-plan est le cœur du service, comme pour les apps de localisation familiale) et demander une revue du refus (appel possible dans App Store Connect).
2. Si Apple maintient : **compiler la variante avec l'interrupteur** qui relâche le blocage (lien « Plus tard » sur l'écran d'autorisation, maquette « variante B » de `permission-localisation-ios.md`). Dans ce cas, **les textes légaux doivent redevenir vrais pour cette variante** (la politique dit aujourd'hui que Miff ne fonctionne pas sans « Toujours ») : il faudra une version iOS de l'article 4 et des CGU art. 6, et le texte de consentement `2026-11-v03` doit utiliser l'**option A** (ou B) du point O2. **Cela se décide avec le PM au moment du refus** ; rien n'est préparé ici de plus.
3. Ne jamais contourner (par exemple en simulant l'autorisation) : un compte développeur risque la suspension, ce qui toucherait aussi Ghostrun, qui partage le compte.

## 7. À faire par le PM, à la main

| # | Action |
|---|---|
| 1 | App Store Connect > l'app > « Confidentialité de l'app » : remplir selon le §2 (en lisant les questions d'Apple du jour) ; renseigner l'URL de la politique |
| 2 | Informations de revue : coller la note du §5 (avec l'URL et le compte de démonstration), joindre la vidéo |
| 3 | **Créer le compte de démonstration** (e-mail + mot de passe, sans double authentification, déjà membre d'une Miff) |
| 4 | **Enregistrer une vidéo** (30 à 60 s) de l'iPhone 11 Pro : écran d'autorisation, fenêtre « Toujours », arrivée dans un lieu (marcher, ou position simulée), statut qui change, arrêt du partage |
| 5 | Répondre au questionnaire de **classification d'âge** honnêtement (O7) |
| 6 | Décider du plan B (§6) **si** Apple refuse : rien n'est décidé avant |
| 7 | Captures d'écran obligatoires de la fiche (écran « Toujours », Miff, carte d'un lieu) |

Rien de tout cela n'est fait par un agent. Le build iOS passe par la CI cloud et TestFlight (aucune compilation locale possible, poste Windows).
