# Alerte « Je suis en danger » : déclarations des stores, notes de revue, risques de rejet (préparation)

Préparé le 2026-11-18 par `release-manager`, d'après le ticket `docs/tickets/alerte-danger.md` (§9, §11, §14.4, §16, §20) et les documents de ce dossier. **Rien n'est soumis, aucun formulaire App Store Connect ni Play Console n'est rempli, aucun compte n'est créé.** Le PM fait lui-même ce qui est listé au §9.
**Rien n'est « prêt à soumettre »** : la checklist QA de publication (`docs/qa/release-checklist.md`) **n'existe pas** dans le dépôt à la date de rédaction, et la fonction n'est pas développée.
Compagnon : `docs/legal/propositions/alerte-danger.md` (textes légaux, mention de l'écran, risques assumés, liste des fichiers). **Aucun texte de ce document n'a été relu par un juriste** (décision du PM du 2026-10-23, risque assumé).

## 1. Sources lues le 2026-11-18, et leurs limites

Les règles des stores changent : `release-manager` les relit **avant chaque soumission**. Les pages ont été lues avec un outil qui renvoie un résumé de la page ; les citations ci-dessous sont celles que l'outil a rapportées, **à recontrôler sur la page** avant de s'en servir dans un échange avec un store.

| Source | Lu | Limite |
|---|---|---|
| Apple, App Review Guidelines (https://developer.apple.com/app-store/review/guidelines/) : 1.1, 1.2 (intro), 1.4 à 1.4.5, 2.3 (intro), 2.3.1(a), 2.5.19, 4.5.4, 5.1.1 (premières phrases), 5.1.5 ; en pied de page : « Last Updated: October 16, 2026 » | Oui, texte cité | Page lue en deux passages (114 000 caractères), **sections 5.2.5 à la fin lues seulement pour « emergency / SOS / alert » : aucune mention** |
| Apple, App privacy details (https://developer.apple.com/app-store/app-privacy-details/) : définitions de « Collect », « Sensitive Info », « Other User Content », « Device ID », « User ID », « Name », « Precise Location » ; critères de divulgation facultative | Oui, texte cité | |
| Apple, documentation Time Sensitive Notifications et Critical Alerts (pages `developer.apple.com/documentation/...`) | **Non lisible** (les pages ne renvoient que leur titre) | **Les faits Time Sensitive et Critical Alerts ci-dessous ne sont PAS confirmés par une page Apple** : ils viennent de pages de tiers (éditeurs de plateformes de notification, forums développeurs Apple) et de la connaissance générale ; à confirmer par `ios-dev` dans Xcode et le portail développeur |
| Google Play, localisation en arrière-plan (https://support.google.com/googleplay/android-developer/answer/9799150) | Oui | Ne traite pas des fonctions d'urgence |
| Google Play, « Deceptive Behavior » incluant « Misleading Claims » et « Behavior Transparency » (https://support.google.com/googleplay/android-developer/answer/9888077) | Oui | Pas de date de mise à jour affichée (« ©2026 Google ») |
| Google Play, `USE_FULL_SCREEN_INTENT` (https://support.google.com/googleplay/android-developer/answer/13392821) | Oui | Ne traite pas des alertes d'urgence |
| Google Play, Data safety (https://support.google.com/googleplay/android-developer/answer/10787469) | Oui | **Ne dit rien** du cas « donnée visible d'autres utilisateurs de l'app » : point ouvert O9 inchangé |
| Google Play, « Health Content and Services » (https://support.google.com/googleplay/android-developer/answer/16679511) | Oui | Ne mentionne ni urgence, ni SOS, ni alerte de sécurité |

**Ce que je n'ai pas trouvé** : ni Apple ni Google (dans les pages lues) ne publient de règle **dédiée** aux apps « SOS » ou « alerte de sécurité personnelle ». Je n'en déduis pas qu'il n'y en a pas : seulement que les règles pertinentes sont les règles générales du §2. Une pétition de développeur affirmant que Google refuse les apps SOS pour les notifications plein écran existe ; elle n'est **pas** une source officielle et n'est pas vérifiée (elle est cohérente avec le choix du ticket de ne pas utiliser `USE_FULL_SCREEN_INTENT`).

## 2. Ce que disent les règles, et **deux corrections au ticket**

**Apple**
- **1.4 (« Physical Harm »), introduction** : « If your app behaves in a way that risks physical harm, we may reject it. » **Correction au ticket (§14.4)** : le ticket cite « 1.4.1 (physical harm) ». Dans le texte lu le 2026-11-18, **1.4.1 est « Medical apps »** (précisions sur la médecine, la mesure de santé). Le texte pertinent pour une fonction de sécurité personnelle est l'**introduction de 1.4**, pas 1.4.1. **Ne pas citer « 1.4.1 » dans une note de revue ni dans une réponse à Apple.** 1.4.5 (« Apps should not urge customers to participate in activities … that risk physical harm ») ne concerne pas l'alerte.
- **5.1.5 (Location Services)** : « Location-based APIs shouldn't be used to provide emergency services or autonomous control over vehicles, aircraft, and other devices… ». **C'est le seul passage lu qui parle de services d'urgence.** Pertinent car l'alerte s'accompagne d'une mesure de position (une seule, à l'envoi) : la formulation de l'app et de la fiche ne doit **jamais** présenter la position comme un moyen d'envoyer des secours.
- **4.5.4 (Push Notifications)** : « Push Notifications must not be required for the app to function, and should not be used to send sensitive personal or confidential information. » Les notifications d'alerte contiennent un prénom et le mot « alerte » : sujet de vigilance (risque JA9 de la proposition légale). Les notifications restent facultatives (l'app fonctionne sans).
- **2.3.1(a)** : « All new features, functionality, and product changes must be described with specificity in the Notes for Review section of App Store Connect (generic descriptions will be rejected) and accessible for review. » **Donc la note de revue du §6 est obligatoire**, pas facultative.
- **2.5.19** : « Don't include any hidden, dormant, or undocumented features in your app ». Le **geste de maintien de 5 secondes** n'est visible qu'à qui le fait : il doit être décrit dans la note de revue et annoncé dans l'app (bulle bleue, fiche « En savoir plus »).
- **2.3 (métadonnées exactes)** : les informations de confidentialité, la description et les captures doivent refléter l'expérience réelle de l'app et rester à jour avec chaque version.
- **Time Sensitive (non confirmé par une page Apple, voir §1)** : niveau d'interruption `timeSensitive`, capacité « Time Sensitive Notifications » (entitlement `com.apple.developer.usernotifications.time-sensitive`), **sans demande d'accord à Apple** (sources tierces concordantes), activable dans Xcode ou sur l'identifiant de l'app. L'utilisateur peut la retirer à l'app dans ses réglages. **Critical Alerts** (entitlement `com.apple.developer.usernotifications.critical-alerts`) : **accord préalable d'Apple sur demande**, non demandé en v1 (D18).

**Google Play**
- **Localisation en arrière-plan** : acceptée si elle sert la **fonctionnalité principale** et apporte un avantage significatif ; la page cite parmi les avantages « physical safety, perceived safety, and health/fitness » et, comme exemple, une alerte quand un enfant quitte une zone. **Une seule fonctionnalité** par déclaration. **L'alerte « Je suis en danger » n'utilise pas la localisation en arrière-plan** (une mesure au premier plan, app ouverte) : la fonctionnalité déclarée reste **seule et inchangée** (voir §5).
- **Misleading Claims** : « We don't allow apps that contain false or misleading information or claims », y compris « in the description, title, icon, and screenshots » ; exemple : « Apps that claim functionalities that are not possible to implement ». **Conséquence** : aucune fiche, capture ni texte ne doit laisser croire que l'alerte contacte les secours, ni promettre une livraison ou un délai.
- **Behavior Transparency** : pas de fonction cachée ou non documentée. Même conséquence que 2.5.19 d'Apple.
- **`USE_FULL_SCREEN_INTENT`** : par défaut accordée seulement aux apps dont la fonction centrale est l'alarme ou l'appel ; les autres doivent demander l'accord de l'utilisateur ; déclaration obligatoire dans la Play Console. **Miff ne l'utilise pas et ne doit pas l'ajouter** (ticket §11.3, AD34 ; contrôle V32 de la proposition légale).
- **Santé** (« Health Content and Services ») : aucune mention d'urgence ; l'alerte n'est pas une fonction de santé. **Ne pas cocher la déclaration « Health apps »** (à confirmer en lisant l'aide de la console, voir §4).

## 3. Apple : étiquettes de confidentialité (App Privacy) : ce que l'alerte change

**Résultat** : **aucun nouveau type de donnée** par rapport aux lignes déjà proposées dans `app-store-confidentialite-et-revue.md` §2. L'alerte réutilise : le **prénom** (Name), l'**identifiant de compte** (User ID), le **jeton de notification** (Device ID), la **dernière position** (Precise Location, une mesure de plus du même type) et du **contenu généré par l'utilisateur** (Other User Content). Tracking : **Non**, inchangé.

| Type Apple | Changement | Lié à l'utilisateur | Finalité | Note à ajouter à la ligne existante |
|---|---|---|---|---|
| **User Content > Other User Content** | **Ligne existante étendue** | Oui | App Functionality | « et l'état de l'alerte « Je suis en danger » (en cours ou terminée, heures de début et de fin), visible des membres de la Miff d'origine, 7 jours au plus » (Apple : « Any other user-generated content »). Aucune catégorie plus spécifique ne convient mieux ; **ne pas** utiliser Sensitive Info (voir ci-dessous) |
| Location > **Precise Location** | Aucun | Oui | App Functionality | « une mesure supplémentaire à l'envoi d'une alerte, de même nature que la dernière position connue ; jamais dans l'alerte ni dans la notification » |
| Contact Info > **Name** | Aucun | Oui | App Functionality | Le prénom est aussi dans la notification d'alerte |
| Identifiers > **Device ID** (jeton) | Aucun | Oui | App Functionality | Le jeton sert aussi aux notifications d'alerte |
| Identifiers > **User ID** | Aucun | Oui | App Functionality | L'identifiant de l'auteur est dans la clé de l'alerte et dans l'état technique des notifications |
| Contact Info > **Phone Number** | Aucun | Oui | App Functionality | Les numéros des membres sont affichés sur l'écran « appeler » (déjà visibles des membres) |
| **Sensitive Info** | **Non à déclarer** | | | La définition d'Apple liste : origine raciale ou ethnique, orientation sexuelle, grossesse ou naissance, handicap, convictions religieuses ou philosophiques, appartenance syndicale, opinions politiques, données génétiques, données biométriques. **Une alerte « Je suis en danger » n'y figure pas** (texte lu le 2026-11-18). Si Apple en décide autrement, le PM la déclarera ; en cas de doute en remplissant, ne pas sous-déclarer (même règle prudente que pour O9) |
| Health & Fitness, Financial Info, Contacts, Browsing/Search History | Non | | | Aucune donnée d'alerte n'est de cette nature |
| Usage Data > Product Interaction / Diagnostics | Inchangé | | | **Aucun événement Analytics, aucun journal** ne contient une donnée d'alerte (D13) |

- **Définition de « collecte »** (Apple, citée) : transmettre hors de l'appareil et conserver plus longtemps que le service en temps réel de la requête. L'alerte est **conservée** (jusqu'à 7 jours + 2 jours) : elle est donc collectée. L'**indicateur local** « explication vue » et l'état « en attente d'envoi » restent sur l'appareil : **non collectés**.
- **Manifeste `ios/Miff/Resources/PrivacyInfo.xcprivacy`** : `OtherUserContent`, `PreciseLocation` et `DeviceID` y figurent déjà ; **aucune nouvelle catégorie attendue**. `ios-dev` le confirme dans la PR iOS de l'alerte ; **le manifeste, les étiquettes et la politique doivent dire la même chose**.
- **`Info.plist`** : **aucune nouvelle clé d'usage** (aucun accès aux contacts, aucun `NSContactsUsageDescription`, aucun appel direct). La capacité Time Sensitive n'est pas une clé d'usage et n'est pas une déclaration App Store Connect : c'est un entitlement (`ios/Config/Miff.entitlements`) et un réglage de l'identifiant d'app (action du PM, §9).
- **Questionnaire de classification d'âge** : le relire à la soumission ; l'alerte n'ajoute pas de contenu, mais Apple modifie régulièrement les questions (voir O7).

## 4. Google Play : formulaire « Sécurité des données » (Data safety) : ce que l'alerte change

**Résultat** : une ligne **étendue**, aucune permission nouvelle, aucune nouvelle catégorie de collecte sensible. Rappel de la définition lue : « collecté » = transmis hors de l'appareil ; « partagé » = transféré à un tiers (hors prestataires, hors action précise de l'utilisateur dont il attend le partage) ; **la page ne dit rien** du cas « visible d'autres utilisateurs de l'app » (point **O9** inchangé : **recommandation prudente = déclarer ces données comme partagées avec d'autres utilisateurs**).

| Catégorie Play | Type | Changement | Collecté | Partagé | Obligatoire ? | Finalités | Note |
|---|---|---|---|---|---|---|---|
| **Activité dans l'app** | **Autre contenu généré par les utilisateurs** (libellé exact à lire dans la console) | **Nouvelle ligne** (ou ligne existante étendue si une ligne équivalente existe) | Oui | **À traiter avec O9** (visible des membres de la Miff d'origine) ; recommandation prudente : oui, avec d'autres utilisateurs | **Facultatif** (la personne choisit de déclarer une alerte ; les stores acceptent « facultatif » quand tous peuvent refuser) | Fonctionnalités | L'alerte : état, heures de début et de fin, 7 jours au plus. **Ce rattachement est une proposition** : la page lue ne définit pas ce libellé ; le PM lit l'aide affichée dans la console. Candidat de repli : « Autres actions » |
| Localisation | Localisation précise | Aucun | Oui | O9 | Obligatoire | Fonctionnalités | Une mesure de plus à l'envoi, même donnée |
| Informations personnelles | Nom ; Numéro de téléphone ; ID utilisateur | Aucun | | | | | Prénom dans la notification ; numéros affichés sur l'écran « appeler » |
| Identifiants d'appareil ou autres | Jeton de notification | Aucun | Oui | Non (prestataire) | Facultatif | Fonctionnalités | Sert aussi aux alertes |
| Santé et forme physique | — | **Non** | | | | | L'alerte n'est pas une donnée de santé ; ne pas cocher « Health apps » |
| Pratiques de sécurité | Chiffrement en transit : oui ; demande de suppression : oui (dans l'app et sur le web) | Aucun | | | | | Politique : l'alerte est supprimée avec le compte |

- **Politique de confidentialité en ligne** : la page publiée doit contenir le texte de la proposition légale **avant** la mise en service (un écart entre le formulaire, la politique et l'app est un motif de rejet : `google-play-data-safety.md` en-tête).
- **Aucune permission nouvelle** : pas de `USE_FULL_SCREEN_INTENT`, pas de `CALL_PHONE`, pas de « Contacts ». L'appel passe par l'intention `ACTION_DIAL` du système. La permission `POST_NOTIFICATIONS` (Android 13) est déjà demandée par la fonctionnalité de notifications existante.

## 5. Justification de la localisation en arrière-plan : **inchangée**

- **Google Play** : `docs/release/google-play-localisation-arriere-plan.md` §3 et §4 restent **mot pour mot** : une seule fonctionnalité déclarée (« Detecting arrivals and departures at trusted places to update the user's circle »). **L'alerte n'utilise pas la localisation en arrière-plan** : la mesure unique à l'envoi est faite **app ouverte** (le geste exige l'écran Update au premier plan). Ne **pas** ajouter l'alerte comme seconde fonctionnalité : Google exige **une seule** fonctionnalité par déclaration, et une seconde fonctionnalité est un motif de refus.
- **Vidéo de démonstration** (30 s) : inchangée ; **ne pas** y filmer l'alerte (elle n'a rien à faire dans la démonstration de la localisation en arrière-plan).
- **Divulgation visible dans l'app** (écran d'autorisation) : inchangée. La description de la fiche Play garde la phrase de divulgation exigée.
- **Apple** : `app-store-confidentialite-et-revue.md` §4 (textes d'usage `NSLocation*`) et §5 (argumentaire « Always ») restent inchangés ; **le texte de consentement `2026-11-v03` ne change pas** (D15). À amender seulement si la **position renforcée** (v1.1) est un jour développée : nouveau consentement versionné, textes d'usage, déclarations, note de revue.
- **Cohérence** : la phrase « Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue » reste vraie pour l'alerte (réciprocité inchangée).

## 6. Notes de revue

### 6.1 Apple : paragraphe à **ajouter** à la note du `app-store-confidentialite-et-revue.md` §5 (en anglais, champ « Notes »)

À coller **seulement** quand la fonction est livrée, **après** la checklist QA, avec le compte de démonstration du §6.3. Obligatoire au titre de la guideline 2.3.1(a) (description précise des nouvelles fonctions).

> **New feature in this version: the "I'm in danger" alert (a voluntary message to the user's circle; Miff is not an emergency service).**
>
> **What it is.** On the "Give news" screen, the user can touch and hold the round button for 5 seconds (the screen fills with red as a progress bar; releasing earlier cancels silently and nothing is sent). The members of the one Miff currently selected in the Feed are notified and see the user's card at the top of their Feed, with buttons to call or write. The user can end the alert at any time by holding the alert banner for 5 seconds; otherwise it ends by itself after 7 days. It is free. With VoiceOver or Switch Control, the button offers an accessibility action "Declare an alert" with a two-step confirmation, which is the easiest way to test it.
>
> **What it is not.** It does **not** call or notify any emergency service, and the app never places a call by itself: "Call" opens the system Phone app with a number (a member of the Miff, or emergency numbers chosen from the device's **region setting**, never from its location), and iOS asks for confirmation before dialling. The app says so on screen ("Miff lets your loved ones know, but it isn't an emergency service. If you're in immediate danger, call 112.") and in its Terms. Location services are **not** used to provide emergency services (guideline 5.1.5): the alert contains no location.
>
> **Notifications.** Recipients are the other members of that Miff who accepted notifications. The notification reads "{First name} declared an alert" (no location, no place name, no Miff name, no last name). It uses the **Time Sensitive** interruption level (the "Time Sensitive Notifications" capability); we do **not** use Critical Alerts. Notifications are optional and the app works without them.
>
> **Location and consent.** No new data and no new permission. One fresh measurement of the user's last known location is taken at sending, exactly like the existing last known location (same consent version 2026-11-v03, same recipients, same reciprocity rule, no history). It is never in the alert or in the notification.
>
> **Data.** The alert record (state, start and end times, 7-day expiry, a random session id) lives in the Miff's data for at most 7 days plus 2 days before automatic deletion, and is deleted immediately when the user leaves the Miff, stops sharing or deletes the account. It is declared in App Privacy under "Other User Content" (App Functionality, linked to the user, no tracking). No alert data goes to analytics or crash reporting.
>
> **How to test without notifying anyone.** Sign in with demo account A (below). It belongs to the Miff "Demo Miff", whose other two members are demo accounts that are not signed in on any device, so **no notification is sent to anyone** when you trigger the alert. To see the recipient's view, sign out and sign in with demo account B on this device (the app allows one device per account). The phone numbers in the demo Miff are fictitious; if you tap "Call" on the alert screen, iOS asks for confirmation before dialling: please cancel.

**Reste de la note** : l'URL de la politique, le compte de démonstration et la vidéo restent à remplacer par le PM (`app-store-confidentialite-et-revue.md` §5 et §7). **Ne pas citer** « guideline 1.4.1 » (voir §2).

### 6.2 Google Play : « Accès à l'application » et notes

- **Accès à l'application** (Contenu de l'application) : fournir les identifiants du **même** compte de démonstration A et, dans la zone de texte, reprendre en **français ou en anglais** les trois dernières phrases de la note ci-dessus (comment déclencher l'alerte, comment la terminer, pourquoi personne n'est notifié, numéros fictifs).
- **Aucune déclaration spéciale** n'est attendue pour l'alerte (pas de permission restreinte nouvelle). **Ne pas** décrire l'alerte dans le formulaire de localisation en arrière-plan (voir §5).
- **Vérifier** dans la console, à chaque soumission, que la déclaration de **catégorie** et le **public cible** (O7) restent cohérents.

### 6.3 Comment tester l'alerte **sans prévenir de vrais contacts** (pour le PM, les relecteurs des stores et `qa-tester`)

| Règle | Pourquoi |
|---|---|
| **Une Miff de démonstration** dont les membres sont **uniquement des comptes de test du PM** : A (le relecteur), B et C. **Aucun vrai proche, aucun compte de production.** | L'alerte part à tous les membres de la Miff d'origine (D9, D16) |
| **B et C ne sont connectés sur aucun téléphone** avec les notifications acceptées (aucun jeton enregistré), ou leurs jetons sont supprimés | Le serveur n'envoie qu'aux membres **avec un jeton** (ticket §11.1) : **aucun envoi**, donc aucune notification chez personne |
| **L'auteur (A) ne reçoit jamais sa propre notification** | Ticket §11.1 |
| **Numéros de téléphone fictifs** pour B et C, **ou des numéros appartenant au PM** ; ne jamais mettre le numéro d'une vraie personne ni un numéro attribué à un tiers. Le bouton « Appeler » ouvre le composeur **pré-rempli** : rien n'est composé tant que la personne ne l'a pas décidé (Android : `ACTION_DIAL` ; iOS : confirmation système) | Un relecteur curieux peut toucher « Appeler » : un numéro réel serait appelé. **Je n'ai pas pu vérifier de plage de numéros officiellement réservée à la fiction** (recherche sans résultat) : le PM les choisit lui-même et les vérifie dans le plan de numérotation de l'ARCEP, ou utilise ses propres numéros |
| **Les numéros d'urgence du catalogue (17, 15, 18, 112) sont réels** : ne **jamais** les composer pendant un test ; les écrans montrent des numéros, le composeur du système les pré-remplit, **on n'appuie pas sur « Appeler »** | Appel réel aux secours |
| **Pour tester la notification elle-même** : un **second téléphone du PM** connecté avec B, notifications acceptées. C'est le seul cas où un jeton existe, et il appartient au PM | Recette PM (OPPO + iPhone, ticket §21 étape 7) |
| **Après chaque test** : terminer l'alerte (maintien de 5 s sur la bannière) ; la supprimer si besoin dans la Miff de démonstration | Une alerte active dure 7 jours |
| **Le plafond technique silencieux** (10 débuts par auteur, Miff et 24 h ; 1 par minute) peut empêcher un push d'un test répété ; l'alerte reste visible dans le Feed | Ne pas conclure à un défaut (ticket §10.3) |
| **Capture et vidéo** (facultatives, 30 à 60 s, hors vidéo de localisation) : maintien, remplissage rouge, écran « avertis », bannière, fin | Aide à la revue (Apple accepte une pièce jointe) |

## 7. Ajouts à la checklist de soumission (à reporter dans `checklist-android.md` et dans la checklist iOS, qui n'existe pas encore)

- [ ] **QA passée** (`docs/qa/release-checklist.md`, à créer par `qa-tester` : absente aujourd'hui) **et** recette PM sur OPPO + iPhone, 2 comptes, puis 1 compte dans 2 Miffs.
- [ ] Textes légaux (proposition `docs/legal/propositions/alerte-danger.md`) **publiés avant** la mise en service ; règle TTL posée ; **fonction `notifierAlerte` déployée en dernier**.
- [ ] Capacité **Time Sensitive** activée sur `com.tomessa.miff` et profils de CI régénérés ; **aucune** entitlement Critical Alerts.
- [ ] Manifeste Android : **aucune permission nouvelle** (en particulier pas `USE_FULL_SCREEN_INTENT`, `CALL_PHONE`, contacts) ; `Info.plist` : aucune nouvelle clé d'usage.
- [ ] App Privacy et Data safety mis à jour (§3, §4) ; manifeste de confidentialité iOS cohérent.
- [ ] Note de revue Apple (§6.1) et accès Play (§6.2) mis à jour ; comptes de démonstration créés **par le PM** (§6.3).
- [ ] Fiches des stores relues (§8) ; captures sans promesse d'urgence.
- [ ] Règles des stores **relues le jour de la soumission** (Apple : « Last Updated » relevé le 2026-11-18 : October 16, 2026).

## 8. Fiches des stores : ce qu'elles peuvent et ne peuvent pas dire

- **Aujourd'hui** (`marketing/store-listing-ios.md`, `marketing/store-listing-android.md`) : les deux fiches disent « notifications non actives » et ne mentionnent pas l'alerte. Elles sont à revoir **le jour de la livraison, pas avant**, par `aso-marketing`.
- **Formule autorisée** (ticket §14.4) : « Prévenir ses proches en un geste ». **Précaution** : `web/tests/landing.test.mjs` interdit sur la page d'accueil web « prévient / alerte tes proches » ; cette formule vaut pour les fiches des stores, pas telle quelle pour la page web (voir la liste des fichiers de la proposition légale, ligne 6).
- **Jamais** : « sécurité garantie », « sauve des vies », « alerte les secours », « SOS », « bouton d'urgence », « appelle les secours pour toi », toute promesse de livraison, de lecture ou de délai, « en direct » (Google *Misleading Claims* ; Apple 2.3 et 5.1.5).
- **À écrire honnêtement** : si la fiche cite l'alerte, elle dit aussi « Miff n'est pas un service d'urgence » (cohérence avec l'app et les CGU).
- **Notes de version / « Nouveautés »** : décrire l'alerte factuellement (« un geste volontaire pour prévenir les proches de ta Miff »), sans superlatif.

## 9. À faire par le PM, à la main (aucun agent ne le fait)

| # | Action |
|---|---|
| 1 | **Décider** si la fiche des stores mentionne l'alerte, et comment les comptes existants sont informés de la nouvelle version des textes (voir la PR : décisions proposées avec un défaut) |
| 2 | **Créer** les comptes de démonstration A, B, C et la Miff de démonstration (§6.3), **sans** jeton sur B et C ; choisir des numéros fictifs ou à toi ; me communiquer **uniquement** les adresses e-mail de test à mettre dans les notes (pas les mots de passe dans le dépôt) |
| 3 | **Activer la capacité Time Sensitive** sur `com.tomessa.miff` dans le portail développeur Apple et régénérer les profils (`docs/dev/apple-certificats-ci.md`) ; **vérifier sur l'iPhone** le libellé exact du réglage en français et le comportement (mode silencieux, Concentration) |
| 4 | **Poser la règle TTL** sur `alerts.purgeAt` dans la console Firebase (`--project miff-3d283`) |
| 5 | **Remplir** App Privacy (App Store Connect) et Data safety (Play Console) en lisant l'aide de chaque question (§3, §4) ; trancher O9 |
| 6 | **Coller** la note de revue (§6.1) et l'accès Play (§6.2) ; joindre une vidéo si tu le souhaites |
| 7 | **Dire « oui, déploie »** pour le site (textes légaux) puis pour les règles et la fonction : tu tapes les commandes (`docs/dev/deploiement-functions.md`), dans l'ordre du ticket §21 |
| 8 | Relire les règles des stores **le jour de la soumission** |

Rien de tout cela n'est fait par un agent. Aucun paiement, aucune création de compte développeur et aucune soumission ne sont effectués par `release-manager`.

## 10. Risques de rejet ou d'avertissement des stores, **assumés par le PM** (écrits comme tels, jamais « à valider »)

Sources : §1 et §2. Probabilités : **estimations de `release-manager`, non vérifiables**, jamais des certitudes.

| # | Risque | Texte en cause | Estimation | Mitigation | Statut |
|---|---|---|---|---|---|
| **RS1** | **Apple voit une app « SOS » sans en avoir le statut** : le bouton « Je suis en danger » peut être lu comme un moyen d'appeler les secours ; risque de refus au titre de 1.4 (physical harm) si l'app laisse croire à une protection qu'elle n'offre pas. | 1.4 (intro) ; 2.3 | Moyenne | Mention visible sans défilement sur l'écran qui suit l'envoi ; « prévient tes proches, pas les secours » ; liste de numéros d'urgence réels à un toucher ; aucun appel automatique ; fiche et captures sobres. | **Assumé** |
| **RS2** | **Apple 5.1.5** : la mesure de position à l'envoi peut être lue comme « location API to provide emergency services ». | 5.1.5 | Faible à moyenne | Position **non** dans l'alerte ni la notification ; même donnée que le partage existant ; note de revue §6.1. | **Assumé** |
| **RS3** | **Apple 4.5.4** : la notification porte un prénom et le mot « alerte » (information sur la sécurité d'une personne). | 4.5.4 | Faible (même régime que « a donné des nouvelles ») | Aucun détail ; notifications facultatives ; aperçu masquable. | **Assumé** |
| **RS4** | **Apple 2.3.1(a) / 2.5.19** : geste caché (maintien de 5 s) et nouvelle fonction non décrite → refus. | 2.3.1(a), 2.5.19 | Faible si la note est écrite | Note de revue §6.1 ; bulle bleue et fiche « En savoir plus ». | **Assumé** (la note est une obligation, §7) |
| **RS5** | **Time Sensitive mal utilisé** : Apple peut juger que l'alerte ne mérite pas l'urgence temporelle, ou retirer la capacité. **Non confirmé par une page Apple lisible.** | non lu | Faible | Une seule catégorie de notification utilise ce niveau ; pas de Critical Alerts. | **Assumé** |
| **RS6** | **Google *Misleading Claims*** : une fiche ou une capture qui laisse croire que l'alerte contacte les secours ou garantit une réponse. | Misleading Claims | Faible si les règles du §8 sont tenues | Règles du §8 ; relecture de `aso-marketing`. | **Assumé** |
| **RS7** | **Google, notification plein écran** : l'alerte est la fonction qui tentera le plus d'utiliser `USE_FULL_SCREEN_INTENT` ; Google la réserve par défaut aux alarmes et aux appels. | `USE_FULL_SCREEN_INTENT` | Nulle tant qu'elle n'est pas ajoutée | **Ne pas l'ajouter** (D18, AD34, V32). Si un jour souhaitée : déclaration console et accord utilisateur, décision du PM. | **Assumé** |
| **RS8** | **Google « stalkerware » (O8)** : l'alerte est un message **volontaire et visible** de la personne concernée, donc plutôt l'inverse d'un suivi caché ; mais elle renforce l'image « sécurité des proches » d'une app qui s'adresse aussi aux amis et aux couples. | Device and Network Abuse | Inchangée | Inchangée (O8 assumé le 2026-11-04). | **Assumé** |
| **RS9** | **Écart entre déclarations et app** (App Privacy, Data safety, politique, manifeste) : motif de rejet ou de retrait. | App privacy ; Data safety | Moyenne si oubli | Contrôles V25 à V34 ; relecture le jour de la soumission. | **Assumé** |
| **RS10** | **Apple « Always » obligatoire (5.1.2(i))** et **Google « stalkerware »** : risques **déjà assumés** (R1, O8), **non modifiés** par l'alerte. | 5.1.2(i) | — | `app-store-confidentialite-et-revue.md` §5 et §6. | **Assumé (2026-11-01, 2026-11-04)** |
