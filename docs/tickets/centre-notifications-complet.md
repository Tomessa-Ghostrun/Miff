# Ticket : Centre de notifications complet (toutes les notifications reçues, avec la photo de la personne concernée)

**Statut** : **proposition en attente du GO du PM** (aucun développement lancé). Aucune lecture ni écriture dans Notion, Figma ou Firebase ; aucun statut Notion modifié ; aucun déploiement.
**Rédigé le** : 2026-11-19 (agent `product-specs`). **Vérité du code** : `origin/main` au commit `424e858a` (lu dans un worktree propre ; le dossier `C:\dev\miff` local est périmé et n'a pas servi). **Tickets liés** : `docs/tickets/notifications.md` (§7 « Centre de notifications »), `docs/tickets/notifications-p1-derniere-visite.md` (NOTIF-P1A / P1I), `docs/tickets/notification-update-membres.md` (SRV-4, « {Prénom} a donné des nouvelles »), `docs/tickets/alerte-danger.md` (AD-A2 / AD-I2, §11 et ligne « cloche / centre »).

**Demande du PM** : « J'ai l'impression que certaines push notifications reçues ne s'enregistrent pas dans le centre de notifications. Je veux que toutes les notifications reçues soient dans le centre + que dans le centre il y ait la photo de la personne concernée ; si c'est plusieurs personnes ou une Miff, remplacer la photo par le logo de l'app Miff (le M avec un cœur). »

**Convention** : **[Confirmé]** = lu dans le code de `origin/main` (fichier:ligne). **[Hypothèse]** = plausible, non vérifiable sans mesure sur appareil. **[Décidé]** = décision déjà écrite du PM. **[Défaut]** = ma proposition, validée si le PM ne dit rien (le PM a délégué la spec). **[Question]** = vraie question au PM (section 11).

---

## 1. Résumé pour le PM

1. **Votre impression est exacte, et la cause est identifiée.** Sur **iPhone, aucune notification push reçue n'entre jamais dans le centre** (ni « a donné des nouvelles », ni alerte) : l'iPhone affiche la notification lui-même et l'app n'est pas exécutée, donc rien n'est enregistré, et le code qui devait le faire n'est pas branché. Sur **Android, les alertes « Je suis en danger » ne sont pas inscrites non plus** (la notification s'affiche, la ligne n'est jamais écrite). Les « a donné des nouvelles » arrivent bien dans le centre Android, sauf cas limites (section 4).
2. **Pourquoi on ne peut pas réparer en rustinant l'app** : tant que le centre est rempli *par le téléphone au moment de la réception*, il manquera tout ce que le téléphone n'a pas reçu ou pas traité (iPhone app fermée, téléphone éteint plus de 2 h, app arrêtée de force, autorisation retirée). **La solution fiable est que le serveur note lui-même chaque notification envoyée** dans un petit journal rattaché à chaque personne, que l'app relit à l'ouverture. Même principe sur iPhone et Android, **0 € jusqu'à environ 1 000 utilisateurs actifs par jour, 3 à 4 $ par mois à 10 000** (section 8.7, hypothèses dites).
3. **Photo ou logo** : une personne = sa **photo de profil** (celle que ses proches voient déjà dans leur Miff ; pas la photo d'update, qui expire en 24 h et porte un lieu) ; sans photo = **ses initiales**, comme le Feed ; **plusieurs personnes ou la Miff entière = le logo Miff** (le « M » avec le cœur jaune-vert sur fond bleu). Aujourd'hui seules « Ta Miff est passée en orange / rouge » concernent la Miff entière.
4. **Ce qui change pour la vie privée** : une trace de chaque notification (type, Miff, identifiant de la personne, date ; **ni prénom, ni photo, ni lieu, ni position**) est gardée **30 jours sur nos serveurs** (aujourd'hui le centre est uniquement local). **Il faut publier le texte légal mis à jour avant la mise en service** ; c'est la seule question bloquante (Q1). Aucun texte n'est présenté comme relu par un juriste.
5. **Rien de plus sur l'écran verrouillé** : pas de photo dans la notification du système (v1), pas de pastille sur l'icône, pas de nouveau son. Seul le centre dans l'app change.
6. **Découpage** : 1 lot backend (règles + fonctions existantes), 1 lot Android, 1 lot iOS, 1 petit lot logo, 1 lot légal, 1 lot traductions (section 9). Android et iOS en parallèle une fois le backend en ligne.

---

## 2. Audit : comment le centre est alimenté aujourd'hui

| Sujet | Android | iOS |
|---|---|---|
| **Source** | **Local seulement.** Rempli par le code de l'app au moment où un événement se produit : alarme locale (`NotificationScheduler.dueNow`, `data/notification/NotificationScheduler.kt:639-690`), N6 (`:390`, `:396`), message push de type « données » (`onCircleNews`, `:416-473`), demandes de lieu (N8, `ui/profile/ProfileRequestsViewModel.kt:269`), « Depuis ta dernière visite » (C1, `replaceVisitLines`, `data/notification/NotificationCenter.kt:155`). **Jamais reconstruit depuis Firestore** (sauf N8 et C1, calculés à l'ouverture). | **Local seulement.** Notifications locales : **inscrites à l'ouverture suivante** par comparaison entre ce qui était programmé et ce qui ne l'est plus (`settleFired`, `Services/Notification/NotificationReconciler.swift:146-184` ; rattrapage `:313-354` ; premier plan `NotificationReconciler+Events.swift:81-101`). **Aucun chemin n'inscrit un push du serveur.** |
| **Stockage** | `SharedPreferences` privées `miff_notification_center` (JSON), hors sauvegarde (`allowBackup=false`, `AndroidManifest.xml:99`) (`NotificationCenter.kt:43-94`). | Fichier `Application Support/Notifications/notification-center.json`, **exclu des sauvegardes**, protection « jusqu'à la première authentification », un compte à la fois (`Services/Notification/NotificationCenterFileStore.swift`). |
| **Contenu d'une ligne** | `id` (clé de dédoublonnage), `type`, `miffId`, `createdAt`, `readAt`, `state` (affichée/silencieuse), `who` (prénom de l'auteur, N9 seulement) (`NotificationCenter.kt:32-41`). **Aucune position, aucun lieu, aucune photo** (`domain/notification/NotificationItems.kt:6-11`). | `id`, `type`, `miffId`, `createdAtMillis`, `readAtMillis`, `state` (`Models/Notification/NotificationHistory.swift:13-25`). **Aucun prénom** : les phrases N8 et C1 se composent à l'affichage depuis la mémoire (`RequesterDirectory`). |
| **Durée** | **30 jours ou 100 lignes**, le premier atteint, appliqué à l'ouverture et à chaque ajout (`NotificationItems.kt:36-53`). | Identique (`NotificationHistory.swift:30-31`). |
| **Tri et regroupement** | Plus récent en haut, en **sections Aujourd'hui / Hier / Plus ancien** (`NotificationSections`, `NotificationItems.kt:55-87`) ; le groupe C1 « Depuis ta dernière visite » est **au-dessus** (3 lignes au plus, remplacé à chaque comparaison). Pas de regroupement par type ni par personne. | Identique (`NotificationGrouping`, `NotificationHistory.swift:90-157`). |
| **Lu / non lu** | Point devant la ligne **et** titre en gras ; ligne lue au toucher, « Tout marquer comme lu » ; **tout est lu quand on quitte l'écran** (`NotificationCenterViewModel.kt` `onCleared`). Lire une ligne retire la notification du tiroir. | Même logique (`NotificationCenterService.swift`). |
| **Badge** | **Un point sur la cloche du Feed, jamais un chiffre** (`hasUnread`, `NotificationCenter.kt:116`). **Aucune pastille sur l'icône de l'app** (aucun `badge` dans les messages, `notifRegles.ts:121-129`). | Idem (`hasUnread`). |
| **Purge** | Déconnexion, suppression du compte, perte d'accès (contrat DUR-3, `AppContainer.kt:623`). | Idem (`NotificationCenterService.purge`, `AccountLocalData.swift`). |
| **Avatar dans la ligne** | **Aucun** : un point de non-lu + texte (`ui/notification/NotificationCenterScreen.kt:469-564`). | **Aucun** (`Views/Notification/NotificationCenterView.swift`). |

**Conséquence de conception** : l'existant **ne peut pas** retrouver un push que le téléphone n'a pas traité. C'est le défaut structurel à corriger.

---

## 3. Inventaire complet des notifications

Seules deux fonctions Cloud envoient des push (`backend/functions/src/index.ts:8-11`) ; l'app n'appelle `NotificationManagerCompat.notify` qu'à trois endroits (`NotificationPresenter.kt:147`, `:209`, `:280`) et iOS n'a qu'un ordonnanceur local (`LocalNotificationScheduling.swift:68-81`). Aucun service de premier plan, aucune autre notification. Les e-mails (Firebase Auth) ne sont pas des notifications push.

| Type | Déclencheur → destinataires | Format et affichage | Dans le centre **Android** | Dans le centre **iOS** | Pourquoi |
|---|---|---|---|---|---|
| **N1** « rappel, passé orange » | Local, alarme à 2 h sans nouvelle → moi | Locale ; Android canal `miff_reminders(_quiet)`, iOS locale | **Oui** (affichée, ou ligne silencieuse si app ouverte / en retard / plafond) | **Oui, à l'ouverture suivante** | Android écrit à l'affichage (`dueNow`) ; iOS déduit après coup (`settleFired`) |
| **N2** « passé rouge » | Local, 4 h → moi | idem | Oui | Oui, différé | idem |
| **N3 / N4** « Ta Miff est passée en orange / rouge » | Local, 30 min après, après vérification serveur → moi | Locale ; canal `miff_circle(_quiet)` | Oui (`fireCircle`) | Oui, différé (limite iOS connue : fausse alerte possible) | idem |
| **N6** « zone de danger de ta Miff » | Local, immédiat, quand **j'entre** dans une zone → moi | Locale | Oui (`:390`/`:396`) | Oui, différé | idem |
| **N7** « Ton mode voyage est terminé » | Local, 08:00 le lendemain → moi | Locale | Oui | Oui, différé | idem |
| **N8** demande d'ajout d'un lieu | Écriture Firestore d'un membre → le créateur | **Ligne seule, aucune notification du système** | Oui si l'écran des demandes a été chargé | Idem | Calculée à l'ouverture ; n'est pas un push |
| **C1** « Depuis ta dernière visite » | Comparaison locale à l'ouverture → moi | **Ligne seule** | Oui | Oui | Pas un push |
| **N9** « {Prénom} a donné des nouvelles » (`circle_update`) | **Push FCM** de `notifierUpdate` quand un membre publie une photo → les autres membres partageant, qui ont gardé l'interrupteur (`notifTraitement.ts:213-216`) | Android : message **« données seules »**, haute priorité, TTL 2 h, **l'app compose et affiche** (canal `miff_updates(_quiet)`, tag par auteur) (`notifRegles.ts:104-110`). iOS : notification « alerte » avec clés de texte, **affichée par le système**, `apns-collapse-id` = auteur, TTL 2 h, **ni `content-available`, ni `mutable-content`** (`notifRegles.ts:111-130`) | **Oui**, sauf cas de la section 4 (C4) | **NON, jamais** | iOS : le système affiche sans exécuter l'app ; le crochet de premier plan n'est relié à rien (`App/PushServices.swift:5`, `:14-15`, `:131`) |
| **Alerte, début** (`circle_alert`, `event=start`) | **Push FCM** de `notifierAlerte` (**déployée le 2026-11-19**) → **tous** les autres membres de la Miff d'origine ayant un appareil, même sans partage (`alerteTraitement.ts:181-213`) | Android : « données seules », canal `miff_alerts` (haute importance), tag par auteur (`NotificationPresenter.kt:218-281`). iOS : alerte système, `time-sensitive`, `collapse-id` `alert-{uid}` (`alerteRegles.ts:111-141`) | **NON** : la notification s'affiche, **aucune ligne n'est écrite** (`NotificationScheduler.kt:490-507`, pas de `center.add`) | **NON** : aucun code iOS ne lit ce type (aucune occurrence de `circle_alert` dans `ios/Miff/**/*.swift`) | Promis par le ticket (`alerte-danger.md:181`), jamais fait |
| **Alerte, fin manuelle** (`event=end`) | idem (jamais à l'expiration à 7 jours, décision D19) | idem, remplace le début dans le tiroir | **NON** | **NON** | idem |

**Autres écarts relevés** (sans lien direct avec le centre, à ranger dans AD-I2) : sur iPhone, toucher une notification d'alerte **ouvre seulement l'app** (le délégué ne reconnaît que `circle_update`, `Services/Push/PushNotificationDelegate.swift:48-66`) ; et une alerte reçue **app ouverte est avalée sans bannière ni ligne** (`NotificationHooks.swift:93-106` → `foregroundDelivery`, qui ne connaît que les rappels locaux).

---

## 4. Causes probables de la perte

### 4.1 Confirmées par le code

| # | Cause | Preuve | Effet |
|---|---|---|---|
| **C1** | **iOS n'inscrit jamais un push reçu** | Notification affichée par le système sans code de l'app (`PushNotificationDelegate.swift:4-9`, `notifRegles.ts:111-130`) ; **aucun** `UIBackgroundModes` « remote-notification » (`project.yml:116-121`), **aucune extension de service** (une seule cible `Miff`, `project.yml:70`) ; `PushForegroundHook.onCircleNews` est appelé mais **jamais affecté** (`PushServices.swift:14-15`, `:131`) ; le type `n9` n'existe pas côté iOS (`Models/Notification/NotificationType.swift`) | **100 % des « a donné des nouvelles » reçus sur iPhone sont absents du centre** (le ticket SRV-4 disait « à l'ouverture suivante, au mieux » : jamais implémenté) |
| **C2** | **Android n'inscrit pas les alertes** | `NotificationScheduler.onCircleAlert` (`:490-507`) appelle `presenter.showCircleAlert` et s'arrête : aucune ligne ; il n'existe pas de type de ligne d'alerte (`NotificationModels.kt:40-74`) | 100 % des alertes (début et fin) absentes du centre Android |
| **C3** | **iOS ne sait rien faire d'une alerte** | Aucun code pour `circle_alert` ; app ouverte : `willPresent` → `completionHandler([])` sans trace (`NotificationHooks.swift:93-106`) | Alerte reçue app ouverte : ni bannière, ni ligne ; toucher : n'ouvre aucun Feed |
| **C4** | **Android : messages écartés avant d'écrire la ligne** | Dans `onCircleNews` : autorisation du téléphone absente (`:421`), session invalide, **Miff inconnue de la liste mémorisée** (« inconnu = non », `:423`, `KnownMiffs.kt`), interrupteur général ou « nouvelles » coupé (`NotificationDelivery.decide` → `Drop`) | Aucune ligne, par conception pour l'interrupteur ; **par accident** pour une Miff pas encore connue (premier push après avoir rejoint) ou une autorisation retirée avant la prochaine synchronisation du jeton |
| **C5** | **Un push non livré n'existe nulle part** | Durée de vie du message : **2 h** (Android et iOS) (`notifRegles.ts:13`, `:108`, `:118`) ; la ligne n'est écrite que si le téléphone reçoit | Téléphone éteint, hors réseau, en mode avion ou app arrêtée de force plus de 2 h : **notification perdue et ligne perdue**, alors que le serveur l'a bien envoyée |
| **C6** | **Plafond serveur 1 par heure, par auteur et par Miff** | `PLAFOND_FENETRE_MS`, `verdictEtat` (`notifRegles.ts:15`, `notifTraitement.ts:104-110`) | La 2e publication de la même personne dans l'heure ne produit **ni push ni ligne** (elle reste dans le Feed). Conçu ainsi ; à garder, à dire |
| **C7** | **Rétention 30 jours / 100 lignes** | `NotificationItems.kt:36-53`, `NotificationHistory.swift:30-31` | Les lignes anciennes disparaissent ; normal, mais c'est une perte perçue |

### 4.2 Hypothèses (non vérifiables sans mesure)

- **H1** Android, **app balayée ou « forcer l'arrêt »** (OPPO ColorOS en particulier) : le système peut ne pas réveiller le service de messagerie ; le ticket SRV-4 le note « à mesurer » (`notification-update-membres.md:217`). Le journal serveur couvre ce cas à l'ouverture suivante.
- **H2** iOS : la réception en production n'est validée que dans le sens iPhone → OPPO (`alerte-danger.md:315`) ; le sens OPPO → iPhone est à confirmer en recette (APNs, `time-sensitive` non encore activé sur l'identifiant).
- **H3** Économie d'énergie Android (bucket « restreint ») retardant des messages à priorité haute.
- **H4** Mode Concentration iOS : la notification est rangée sans bannière ; aucune ligne dans tous les cas (C1).

### 4.3 Écartées (vérifié, ce ne sont **pas** des causes)

Dédoublonnage (clé = auteur + identifiant de message FCM, 48 h : un message livré deux fois fait une ligne, pas zéro) ; **tri / dernière visite** (C1 est un groupe à part, remplacé, qui n'efface rien des autres lignes) ; **heures calmes** (elles ne changent que le canal : « silencieux » n'est pas « supprimé ») ; **plafond de 6 par jour sur Android** (au-delà : ligne **silencieuse**, donc écrite) ; **expiration de la photo 24 h** (la ligne ne contient aucune photo : elle reste) ; **notifications remplacées dans le tiroir** (une 2e notification du même auteur remplace la 1re à l'écran, mais **Android garde les deux lignes**, la clé contenant l'identifiant du message, `CircleNews.kt:198-215`).

**Verdict** : les causes **C1, C2 et C3** expliquent à elles seules « certaines push ne s'enregistrent pas » (votre iPhone 11 Pro est le cas le plus touché). C5 est la cause qu'**aucun correctif côté téléphone** ne peut traiter.

---

## 5. Identité visuelle : photos, initiales, logo

### 5.1 Où sont les photos

- **Photo de profil** : `users/{uid}.avatar` (octets, JPEG carré ~160 px, **20 000 octets au plus**), **copiée** dans `miffs/{miffId}/members/{uid}.avatar`, **lisible par tous les membres de la Miff** (`backend/data-model.md:471`, `:550`, `:567`). Aucun Storage (Spark/Blaze sans Storage, `data-model.md:439`). **Les membres d'une même Miff voient donc déjà cette photo** dans le Feed et le carrousel du Profil.
- **Affichage existant** : composant d'avatar du Feed (photo, sinon **initiales** `Initials.of(prénom, nom)`, `domain/profile/MyProfile.kt:44-60` ; `MemberFace`, `domain/profile/MiffFaces.kt:16-40` ; iOS `MemberThumb`, `Services/Miff/MiffMemberPreviews.swift`). Une photo de plus de 20 000 octets n'est **jamais décodée** (initiales à la place).
- **Photo d'update** (`miffs/{miffId}/photos/{uid}`) : **non utilisée** ici. Elle expire en 24 h, porte un lieu et une description, et n'est lisible qu'avec réciprocité de position : l'afficher dans un historique de 30 jours serait contraire au modèle de vie privée.

### 5.2 Le logo « M avec un cœur »

- **Sources vectorielles** (recréation validée du visuel du PM du 2026-10-24) : `design/assets/logo/master/miff-app-icon-fullbleed.svg` (carré bleu `#043CD1`, « M » + « i » blancs, cœur `#D1D828`, plein cadre), `miff-mark-color.svg` (la marque seule, sans carré), `design/assets/logo/README.md`.
- **Déjà dans les apps** : Android `res/drawable/ic_launcher_foreground.xml` (M + i + cœur, 108 dp) et `splash_logo_mark.xml` ; iOS `DesignSystem/Logo/LogoPaths.swift` (`markLetters`, `markHeart`) et `LogoShapes.swift` ; `Assets.xcassets/AppIcon` (PNG, **non** utilisable dans l'app sans arrondi du système).
- **Utilisable tel quel ?** **L'icône d'app, non** (carré arrondi + masque du système). **Le glyphe oui**, posé sur un **disque bleu `#043CD1`** découpé comme un avatar. Pas de variante clair / sombre nécessaire : bleu, blanc et jaune-vert tiennent sur les deux thèmes ; le bleu reste identique pour rester reconnaissable.
- **Point d'attention** : le README du logo prévient que **le cœur devient un petit point sous ~48 px** (`README.md` §6.7). Avatar de 40 dp / 40 pt : **à vérifier en recette** ; repli = glyphe un peu plus grand dans le disque (variante « avatar »).
- **Droits** : le README demande au PM de **confirmer qu'il détient les droits** sur le dessin (§1 et §6.2) ; point déjà ouvert, non résolu par ce ticket.

### 5.3 La personne concernée : qui est l'acteur de chaque ligne

| Ligne | Acteur | Image affichée **[Défaut]** |
|---|---|---|
| N9 « {Prénom} a donné des nouvelles » | l'auteur de la publication | **sa photo** ; sans photo ses **initiales** |
| Alerte « {Prénom} a déclaré une alerte » / « a mis fin à son alerte » | l'auteur de l'alerte | **sa photo** ; sans photo ses initiales |
| C1 « Depuis ta dernière visite » | le proche cité | sa photo / initiales |
| N8 « {Prénom} propose un lieu » | le demandeur | sa photo / initiales |
| N3 / N4 « Ta Miff est passée en orange / rouge » | **la Miff entière** | **logo Miff** |
| N1 / N2 / N6 / N7 (rappels et information **à toi**, voix de Miff) | toi | **logo Miff** **[Question Q2]** |
| Acteur introuvable (a quitté la Miff, compte supprimé, fiche pas encore lue) | inconnu | **logo Miff** + texte « Un proche » (jamais un rond vide ni un « ? ») |
| **Plusieurs personnes** (ligne regroupée) | plusieurs | **logo Miff** : **non utilisée en v1** [Question Q4] ; aujourd'hui aucune ligne ne concerne plusieurs personnes |

**Règle générale** : **la photo dit « qui »** ; **le logo dit « Miff »** (la Miff, plusieurs personnes, ou personne d'identifiable). Les initiales sont le **repli d'une personne sans photo**, pas un cas de logo : le logo ne doit jamais laisser croire que plusieurs personnes sont concernées quand il s'agit d'une seule.

---

## 6. Vie privée et sécurité (non négociables)

1. **Photos** : elles sont déjà visibles des membres de la Miff (§5.1). Le centre **ne stocke jamais** une photo : il la **lit à l'affichage** (mémoire du Feed ou de la fiche de membre, cache Firestore de l'appareil), comme il le fait déjà pour les prénoms de N8 et C1. Elle disparaît avec le cache à la déconnexion, la suppression du compte et la perte d'accès (contrat DUR-3).
2. **Journal serveur minimal** (§8) : `type`, `miffId`, `actorUid`, date, date d'expiration. **Aucun prénom, aucune photo, aucune position, aucun lieu, aucune description, aucun nom de Miff.** Sans prénom côté serveur, une personne qui change de prénom ou supprime son compte n'a **pas de copie de son prénom** dans les journaux des autres.
3. **Ce que les lignes affichent** : exactement les mêmes mots que les notifications actuelles (prénom seul, jamais le nom de famille). **Aucune photo ni prénom sur l'écran verrouillé** : la notification du système ne change pas.
4. **Qui lit le journal** : **son propriétaire seul** (règle Firestore `isSelf(uid)`), jamais un autre membre ni le créateur ; **écriture réservée au serveur** ; l'utilisateur peut **effacer** ses lignes (véritable suppression côté serveur, §8.3).
5. **Durée** : **30 jours** sur le serveur (règle TTL) et sur l'appareil (existant). **Effacement** : déconnexion et suppression du compte (appareil + `users/{uid}/notifications/*` dans le lot de suppression), perte d'accès à une Miff (lignes de cette Miff retirées de l'appareil et masquées ; supprimées du serveur par le TTL au plus tard).
6. **Une ligne survit-elle au départ d'un membre ou à la suppression d'une Miff ?** **[Défaut]** : **le départ d'un membre** : la ligne **reste** pendant ses 30 jours, mais **l'image devient le logo et le texte « Un proche »** (aucune identité résolue pour quelqu'un qui n'est plus dans la Miff). **La suppression d'une Miff, ou la perte d'accès** : les lignes de cette Miff **disparaissent de l'écran** aussitôt (filtre sur la liste des Miffs du compte, `KnownMiffs`) et sont purgées par le TTL.
7. **Sauvegardes** : le stockage local reste **hors sauvegarde** (Android `allowBackup=false` ; iOS `isExcludedFromBackup`). Le journal serveur n'est pas exporté.
8. **Mêmes règles que les notifications existantes** : mêmes destinataires, mêmes interrupteurs (une personne qui coupe « Quand un proche donne des nouvelles » **n'a pas de ligne** pour ces nouvelles ; l'alerte n'a pas d'interrupteur de type), même plafond horaire, pas de position, App Check et plafonds de coûts des fonctions inchangés.
9. **Sécurité des données reçues** : l'identifiant d'une ligne ou d'un acteur qui vient du réseau est **validé de forme** (`^[A-Za-z0-9_-]{1,128}$`) avant tout usage ; une ligne d'un type inconnu est ignorée sans erreur (compatibilité des versions).

---

## 7. Options d'architecture comparées

| | (a) **Journal côté serveur** (recommandé) | (b) **Enregistrement local à la réception** | (c) **Reconstruction depuis les données existantes** |
|---|---|---|---|
| Principe | Les fonctions existantes écrivent une ligne par destinataire dans `users/{uid}/notifications/{id}` ; l'app la relit | Android : écrire à la réception (déjà fait pour N9). iOS : extension de service (NSE) + App Group, ou push silencieux `content-available` | Recomposer à partir de `status/{uid}`, `photos/{uid}`, `alerts/{uid}` |
| App tuée / arrêtée de force | **Couvert** à l'ouverture suivante | Android : **non** si arrêtée de force ; iOS NSE : oui (si le push arrive) | Oui (si la donnée existe encore) |
| Téléphone éteint > 2 h (TTL FCM) | **Couvert** (la ligne existe côté serveur) | **Non** | Partiel |
| Autorisation de notification retirée | **Couvert** | Non | Oui |
| iPhone | **Oui, sans cible ni signature en plus** | NSE = **nouvelle cible Xcode, App Group, profils de signature et CI à reprendre** (déjà jugé trop lourd pour la v1, `notification-update-membres.md:227 ; piste NSE du ticket parent : notifications.md:368`) ; `content-available` = mode d'arrière-plan à déclarer aux stores et **non garanti** | Oui |
| Historique | **Oui (30 j)** | Oui mais incomplet | **Non** : `status` est écrasé, `photos` expire en 24 h, `alerts` est un seul document par auteur (`data-model.md` §2.7, §2.8, §2.18) |
| Photo de la personne | Résolue à l'affichage (identifiant d'acteur dans la ligne) | Idem | Idem |
| Coût | **≈ 0 $ à 1 000 actifs/jour, ≈ 3-4 $/mois à 10 000** | 0 $ | 0 $ mais **inutilisable** |
| Vie privée | **Nouvelle donnée serveur** (identifiants + date, 30 j) : texte légal à publier | Rien ne quitte l'appareil | — |

**Recommandation : (a), combinée à l'existant.** Le serveur est la source de vérité des notifications **venues d'un autre téléphone** (N9, alertes) ; les notifications **locales** (N1 à N7, N8, C1) restent produites sur l'appareil, comme aujourd'hui (elles parlent de soi et ne dépendent pas d'un serveur). Au moment du push, **Android continue d'écrire sa ligne localement** (immédiat), **avec le même identifiant que le serveur** : la ligne ne peut pas être doublée, et le journal ne fait que **combler les trous**. **(b) iOS n'est pas retenu** (cible + signature + non garanti) ; **(c) n'est pas viable** (pas d'historique).

---

## 8. Conception cible

### 8.1 Modèle de données

Collection **`users/{uid}/notifications/{entryId}`** (nouvelle ; `backend/data-model.md` §2.21 à écrire) :

| Champ | Type | Note |
|---|---|---|
| `v` | nombre | Version de forme (`1`). Un client ignore une version ou un `kind` inconnus. |
| `kind` | `"update"` \| `"alert_start"` \| `"alert_end"` | Les trois sortes de **push** envoyés aujourd'hui. |
| `miffId` | string | Identifiant de la Miff concernée (forme validée). |
| `actorUid` | string | Auteur de la publication ou de l'alerte (jamais le destinataire). |
| `createdAt` | timestamp | Heure du serveur (horloge injectée dans la fonction, testable). Sert à l'affichage **et** de curseur de synchronisation. |
| `expireAt` | timestamp | `createdAt` + 30 jours : **règle TTL** à poser par le PM (comme `notifState.expireAt`). |

- **`entryId` déterministe** : `u-{miffId}-{actorUid}-{createdAtMillis}` (update), `as-…` (début d'alerte, millisecondes du `startedAt`), `ae-…` (fin). Il est **aussi mis dans le message FCM** (clé `entryId`, ajoutée à `construireMessage` et `construireMessageAlerte`) : le téléphone qui reçoit le push écrit sa ligne locale **avec ce même identifiant** (Android) ou l'inscrit au toucher / au premier plan (iOS) ; la synchronisation ne crée **jamais** de doublon.
- Pas de `readAt` serveur : **lu / non lu reste local** (un compte = un appareil, SESS-1). Pas d'écriture client sauf la **suppression**.

### 8.2 Écriture côté serveur (aucune fonction nouvelle, aucun Blaze de plus)

Les deux fonctions **existantes** (`notifierUpdate`, `notifierAlerte`) écrivent les lignes **dans la transaction de réservation déjà en place** (`notifTraitement.ts:225-234`, `alerteTraitement.ts:216-229`) : « réservé » ⇒ « journal écrit », atomiquement, avant l'envoi FCM. Seuls les destinataires dont **un push est envoyé** ont une ligne (même cible que l'envoi : mêmes interrupteurs, mêmes plafonds, même session active). Budget : **+1 écriture par destinataire** (≤ 20 pour une update, ≤ 39 pour une alerte), **0 lecture de plus**, une seule transaction (≤ 500 écritures, limite sans objet). Journaux d'exécution : un compteur `journalises` (nombre seul, jamais un identifiant). Garde-coûts (`garde-couts.test.mjs`) : inchangés (aucune fonction ajoutée, mêmes limites).

### 8.3 Règles Firestore (lecture par le propriétaire, écriture serveur, suppression par le propriétaire)

```
match /users/{uid}/notifications/{entryId} {
  allow get: if isSelf(uid);
  allow list: if isSelf(uid) && request.query.limit <= 100;
  allow delete: if isSelf(uid);          // « Effacer » efface vraiment, sur le serveur aussi
  allow create, update: if false;        // écrit par les fonctions (Admin SDK) seulement
}
```

**0 lecture de règles** (aucun `get()`). Index : champs en index simple sur `createdAt` seulement (les autres peuvent être exclus de l'indexation pour réduire le stockage : à vérifier à l'émulateur). La suppression de compte ajoute `users/{uid}/notifications/*` à son lot (comme `devices`).

### 8.4 Synchronisation côté app (Android et iOS)

- **Jamais d'écouteur permanent** (principe du projet : coût maîtrisé). **Une requête incrémentale** `createdAt > curseur − 2 min`, triée, 100 au plus (2 pages au plus), déclenchée : au premier plan, à l'ouverture du centre, et à la réception d'un push (quand l'app tourne). Au plus **1 par minute** hors push. Hors connexion : silencieux, on réessaie plus tard.
- **Fusion** : chaque document devient une ligne locale (`type` N9, ou nouveaux types d'alerte), **ajoutée une seule fois par `entryId`** (l'existant `add` est idempotent par clé). Lignes d'une Miff inconnue ou de soi-même : ignorées.
- **Première synchronisation d'un appareil** (nouvel appareil, réinstallation, première mise à jour) : les lignes **de plus de 24 h arrivent déjà lues** (aucun point de cloche pour du passé) ; celles des dernières 24 h sont non lues.
- **Lecture / suppression** : lire = local ; **supprimer une ligne ou « Tout effacer » = suppression locale + suppression serveur** (en lot, au mieux hors connexion).
- **Types ajoutés** (noms provisoires) : `N9` (existe côté Android, à créer côté iOS), `N10` = alerte déclarée, `N11` = alerte terminée. **Hors périmètre des réglages** (comme N8 et C1 : `PERIMETER` / `allCases` à exclure ; tout `when` exhaustif le signalera à la compilation). **État `silent`** pour les lignes venues du journal (elles ne comptent pas dans les plafonds du tiroir).
- **Écritures locales ajoutées** : **Android** : `onCircleAlert` écrit sa ligne (C2) ; `onCircleNews` utilise `entryId` quand il est présent (sinon la clé actuelle : compatibilité d'un serveur plus ancien). **iOS** : le crochet de premier plan est relié (C1/C3), le toucher d'un push inscrit et lit sa ligne, une synchronisation part à chaque retour au premier plan.
- **Lignes déjà existantes (migration, §8.9)** : une ancienne ligne N9 d'Android porte l'identifiant de l'auteur **dans sa clé** (`n9|u|{auteur}|…`, `CircleNews.kt:198-215`) : sa photo est retrouvée sans rien migrer.

### 8.5 Affichage d'une ligne

- **Mise en page [Défaut]** : `[point non lu] [avatar 40 dp / 40 pt, rond] [texte]` ; le texte, l'heure relative (« 14 h 05 », « hier », jour), le nom de la Miff **seulement si le compte en a plusieurs** : **inchangés**. Titre en gras si non lu (jamais la couleur seule).
- **Avatar** : photo décodée (≤ 20 000 octets, jamais plus), sinon initiales sur disque de la couleur neutre du Feed, sinon (acteur inconnu, Miff, toi) **logo** ; pendant le chargement : disque neutre, **sans clignotement**.
- **Résolution de l'acteur** (`CenterFaces` Android, extension de `RequesterDirectory` iOS) : d'abord la mémoire du Feed (Miff active : **0 lecture**), sinon **un `get members/{uid}` par acteur distinct et par session** (cache Firestore de l'appareil, 5 min en mémoire, pas de boucle en cas d'échec) : au pire une dizaine de lectures à l'ouverture du centre. Nom et photo suivent la même source : jamais l'un sans l'autre.
- **Alertes** : mêmes mots que le push (« {Prénom} a déclaré une alerte » / « a mis fin à son alerte », corps de `notif_alert_*`), **sans rouge ni icône d'alarme** dans le centre (le Feed porte déjà la couleur) ; ton calme.
- **Toucher** : ferme le centre et ouvre le Feed de la Miff, sur la carte de la personne quand elle est connue (comportement actuel de N9 et de C1).
- **État vide** inchangé (« Rien à signaler »). **Bandeau « notifications coupées »** inchangé ; **les lignes du journal restent visibles quand les notifications du téléphone sont coupées** (comme C1, Q4 de NOTIF-P1A) : c'est précisément le cas où elles manquaient.
- **Accessibilité** : la ligne reste **un seul élément** pour le lecteur d'écran (« {texte}, {heure}, non lu ») ; l'avatar est **décoratif** (le prénom est déjà dans le texte) ; cible 48 dp / 44 pt ; texte agrandi : la ligne grandit, l'avatar garde sa taille ; droite-à-gauche : avatar côté début ; aucune animation.
- **Traductions** : clés à ajouter dans les 6 langues du registre (`docs/contracts/i18n-languages.json` : fr, en, es, it, pt, de), **tutoiement** : « Un proche » existe déjà (`notif_c1_someone`) ; à créer : libellé d'accessibilité éventuel du logo, rien d'autre (les phrases N9 et d'alerte existent).

### 8.6 Ton

Rassurer et informer, jamais alarmer : aucune ligne ne dit « inquiétant » ni ne compte des heures ; le journal **ne dit pas** « manqué », « non lu depuis X » ni « tu n'as pas répondu ». Les lignes de plus de 24 h arrivent **lues** à la première synchronisation (aucune « dette » de notifications à l'ouverture d'un nouvel appareil).

### 8.7 Coûts estimés (Blaze, Paris ; **hypothèses, aucune n'est une mesure**)

Hypothèses : Miff de 4 membres (3 destinataires), **2 publications par utilisateur actif et par jour** (hypothèse haute reprise de `notification-update-membres.md` §14), 5 ouvertures de l'app par jour, **1 lecture minimum par requête même vide**. Tarifs repris de l'étude : lectures 0,036 $ et écritures 0,107 $ pour 100 000 ; suppressions ≈ 0,012 $ pour 100 000 (**à vérifier**, y compris le tarif des suppressions faites par le TTL) ; quotas gratuits quotidiens conservés (50 000 lectures, 20 000 écritures, 20 000 suppressions).

| Utilisateurs actifs par jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Lignes écrites par jour (journal) | 600 | 6 000 | 60 000 |
| Lectures par jour (requêtes + lignes + fiches de membres) | ≈ 1 600 | ≈ 16 000 | ≈ 160 000 |
| Stockage (30 jours, ≈ 300 octets par ligne) | < 1 Mo | ≈ 55 Mo | ≈ 0,55 Go (gratuit jusqu'à 1 Go) |
| **Coût mensuel marginal** | **0 $** | **≈ 0 $** (sous les quotas) | **≈ 3 à 4 $** (écritures ≈ 1,3 à 1,9 $, lectures ≈ 1,2 à 1,7 $, suppressions < 0,3 $) |

**Signal pour surveiller** (à ajouter à `docs/dev/surveillance-quotas.md`) : écritures dans `notifications` par jour supérieures à **3 × le nombre de destinataires × publications** attendus, ou lectures du centre par ouverture > 15 : un emballement (boucle de synchronisation, écouteur oublié) ou un abus. **Bascule** : si l'écriture dépasse ~40 000 par jour, passer à une ligne **par Miff et par jour** regroupée (une écriture au lieu de N) ; le client lit déjà par curseur, la forme s'adapte (`v`).

### 8.8 Ce qui n'est pas fait en v1 (assumé)

- **Pas de photo dans la notification du système** (écran verrouillé, tiroir). Android : possible plus tard avec la photo déjà en cache (grande icône) ; iOS : exigerait l'extension de service rejetée en §7. **[Question Q5]**
- **Pas de regroupement** de plusieurs personnes (« 3 proches ont donné des nouvelles ») : une ligne par personne, avec sa photo. **[Question Q4]**
- **Pas d'historique des mises à jour au-delà de la règle « 1 par heure et par auteur »** (C6) : le Feed reste la source de vérité.

### 8.9 Migration des utilisateurs existants

Aucune migration de données : **pas de rétro-remplissage** (le journal commence au déploiement de la fonction). Ordre : **règles puis TTL, puis texte légal publié, puis fonctions, puis apps**. Une app ancienne ignore la clé `entryId` du message et le journal : **aucune régression**. Android : les anciennes lignes N9 gardent leur identifiant, la photo se retrouve via la clé. iOS : rien à migrer (aucune ligne n'existait). **Retour arrière** : redéployer la révision précédente des fonctions (les journaux déjà écrits expirent seuls en 30 jours ; les apps lisent un journal vide sans erreur).

---

## 9. Découpage en tickets

Une branche et une PR par plateforme, jamais de push direct sur `main`. **Déploiements tapés par le PM** (règles, TTL, fonctions, site).

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **CNC-L** | **Logo en avatar** (`design-system`, puis dev) | Avatar « logo Miff » rond (disque `#043CD1` + glyphe) en vectoriel Android (drawable) et iOS (forme existante), test de lisibilité à 40 dp / pt (le cœur), **confirmation des droits par le PM** | S | Aucune |
| **CNC-B** | **Backend** (`backend-data`) | `data-model.md` §2.21 ; règle `notifications` + tests d'émulateur ; `entryId` dans les 2 messages ; écriture du journal **dans la transaction de réservation** de `notifierUpdate` et `notifierAlerte` ; tests (forme exacte du document, idempotence, atomicité avec la réservation, aucun journal pour un destinataire sans envoi) ; suppression de compte ; guide de déploiement (règles → TTL `notifications.expireAt` → fonctions) ; `surveillance-quotas.md` | M | Aucune ; **déployé après CNC-J publié** |
| **CNC-J** | **Légal** (`release-manager`) | Politique de confidentialité FR/EN : art. 3 (journal serveur), 5, 7 (30 jours), 8 (effacement), 11 (le centre n'est plus « sans photo » : la photo est lue, pas stockée) ; registre des risques assumés (`docs/legal/README.md` §4) ; fiches des stores à contrôler (Data safety, App Privacy : probablement aucune nouvelle catégorie, **à vérifier**) ; publication du site par le PM | S | Aucune (en parallèle) ; **publié avant la mise en service de CNC-B** |
| **CNC-A** | **Android** (`android-dev`) | Types `N10`/`N11` ; ligne d'alerte (C2) ; `entryId` ; synchronisation incrémentale ; avatar dans la ligne ; `CenterFaces` ; première synchronisation « lue si > 24 h » ; suppression serveur ; masquage Miff perdue ; purge | M | CNC-B (en ligne), CNC-L |
| **CNC-I** | **iOS** (`ios-dev`) | Types `n9`/`n10`/`n11` ; **branchement du crochet de premier plan** (C1) ; **lecture de `circle_alert`** (premier plan, toucher → Feed, C3) ; `entryId` inscrit au toucher ; synchronisation incrémentale ; avatar ; `CenterFaces` ; suppression serveur ; purge. TestFlight sur l'iPhone 11 Pro | M | CNC-B, CNC-L ; coordination avec AD-I2 |
| **CNC-T** | **Traductions** (6 langues) | Libellé d'accessibilité du logo et toute phrase nouvelle ; parité Android / iOS (`i18n-keys.md`) ; tutoiement | XS | CNC-A / CNC-I |

**Maquettes** : aucune existante pour un avatar dans le centre. **[Défaut]** : les développeurs reprennent la ligne actuelle et y ajoutent l'avatar (§8.5), et **listent leurs écarts dans la PR** (règle du PM du 2026-10-28) ; si le PM a une maquette, il dépose l'export PNG dans `design/exports/centre-notifications/` avant le début du développement.

---

## 10. Critères d'acceptation (numérotés, testables)

**Complétude**
- [ ] **CNC-1** Un « a donné des nouvelles » reçu **iPhone app fermée** apparaît dans le centre **à l'ouverture suivante** (non lu, avec point de cloche).
- [ ] **CNC-2** Idem pour **le début et la fin d'une alerte**, sur **Android et iPhone**, **app ouverte, en arrière-plan ou fermée**.
- [ ] **CNC-3** Un push dont le téléphone était **éteint plus de 2 h** (message FCM expiré) apparaît quand même dans le centre à l'ouverture suivante.
- [ ] **CNC-4** Une **app arrêtée de force** (Android) retrouve ses lignes à l'ouverture.
- [ ] **CNC-5** **Aucun doublon** : un push reçu **et** relu depuis le journal fait **une seule** ligne (même `entryId`), sur Android et iOS ; un message livré deux fois fait une ligne.
- [ ] **CNC-6** Un destinataire qui a coupé « Quand un proche donne des nouvelles » **n'a pas de ligne** ; l'alerte **a** sa ligne (pas d'interrupteur de type) ; un destinataire sans appareil n'a ni push ni ligne ; l'auteur n'a **jamais** de ligne de son propre événement.
- [ ] **CNC-7** Les lignes restent visibles quand les notifications du téléphone sont coupées.

**Photo et logo**
- [ ] **CNC-8** Une ligne d'une personne affiche **sa photo de profil** ; sans photo, **ses initiales** ; photo > 20 000 octets ou illisible : initiales, **jamais décodée**.
- [ ] **CNC-9** « Ta Miff est passée en orange / rouge » affiche le **logo Miff** ; acteur introuvable (parti, supprimé) : **logo + « Un proche »**, jamais un rond vide ; les rappels à soi (N1, N2, N6, N7) selon la décision Q2.
- [ ] **CNC-10** Le nom et la photo d'une même ligne **viennent de la même source** et changent ensemble (un profil modifié se reflète à la prochaine résolution, sans rien réécrire dans le centre).
- [ ] **CNC-11** L'avatar est **décoratif** pour le lecteur d'écran ; la ligne se lit en un élément ; texte agrandi et droite-à-gauche corrects ; cible ≥ 48 dp / 44 pt.
- [ ] **CNC-12** Le **cœur du logo reste identifiable à 40 dp / pt** (sinon variante « avatar » plus grande).

**Vie privée et sécurité**
- [ ] **CNC-13** Une ligne du journal contient **exactement** : `v`, `kind`, `miffId`, `actorUid`, `createdAt`, `expireAt` ; **aucun prénom, photo, lieu, position, nom de Miff** (test de forme exacte).
- [ ] **CNC-14** Règles : le propriétaire **lit et supprime** ses lignes (liste ≤ 100) ; **un autre utilisateur, un membre de la Miff, le créateur, un anonyme : refusés** ; **aucun client ne peut créer ni modifier** une ligne (tests d'émulateur, avec mutations).
- [ ] **CNC-15** « Supprimer » une ligne et « Tout effacer » **suppriment aussi côté serveur** ; la ligne ne revient pas après réinstallation.
- [ ] **CNC-16** Déconnexion, suppression du compte, perte d'accès : centre local vidé / masqué, `users/{uid}/notifications/*` supprimé avec le compte ; **aucune lecture** de ce chemin par un compte qui n'est pas connecté.
- [ ] **CNC-17** **Rien ne change** sur l'écran verrouillé (texte de la notification du système, absence de photo, aucune pastille sur l'icône, aucun nouveau son).
- [ ] **CNC-18** Aucune donnée du centre ni du journal dans les événements Analytics, journaux, rapports de plantage (comme `N-P0-10`).

**Synchronisation et coûts**
- [ ] **CNC-19** **Pas d'écouteur permanent** ; au plus 1 synchronisation par minute hors push ; requête vide = 1 lecture ; ouverture du centre : au plus une dizaine de lectures de fiches de membres, sans boucle en cas d'échec.
- [ ] **CNC-20** Première synchronisation : lignes de plus de 24 h **déjà lues** ; plus de 100 lignes sur le serveur : 2 pages au plus.
- [ ] **CNC-21** Hors connexion : aucune erreur affichée, les lignes locales restent ; au retour du réseau, rattrapage sans doublon.
- [ ] **CNC-22** Écritures du journal **dans la transaction de réservation** : une réservation qui échoue n'écrit ni push ni ligne ; une ligne écrite a toujours son envoi tenté ; un événement rejoué n'écrit rien de plus.

**Compatibilité et textes**
- [ ] **CNC-23** Une app **ancienne** avec le serveur nouveau, et une app **nouvelle** avec un serveur ancien (sans `entryId`), fonctionnent comme aujourd'hui, sans erreur.
- [ ] **CNC-24** Anciennes lignes N9 d'Android : photo retrouvée par l'identifiant contenu dans leur clé ; ligne sans auteur : logo.
- [ ] **CNC-25** Textes FR, EN, ES, IT, PT, DE présents, **tutoiement**, aucune clé manquante (parité Android / iOS) ; ton calme, aucune ligne alarmiste.
- [ ] **CNC-26** Politique de confidentialité mise à jour **et publiée avant** le déploiement de la fonction (CNC-J), sans prétendre à une relecture juridique.

---

## 11. Questions pour le PM (avec valeur par défaut)

La **seule bloquante** est **Q1** (elle touche vos engagements envers les utilisateurs). Les autres ont un défaut sûr : le développement peut avancer avec elles.

| # | Question | Défaut proposé |
|---|---|---|
| **Q1** *(bloquante)* | **Journal sur nos serveurs, 30 jours** : acceptez-vous qu'une trace de chaque notification envoyée (type, Miff, identifiant de la personne, date ; ni prénom, ni photo, ni lieu) soit gardée 30 jours sur Firebase, et que la politique de confidentialité le dise avant la mise en service ? Sans cela, **l'iPhone ne pourra jamais avoir tout dans le centre** (l'alternative iPhone serait une extension de service, lourde et non garantie). | **Oui** |
| **Q2** | **Vos propres rappels** (« donne de tes nouvelles », « tu es passé orange / rouge », zone de danger, fin du mode voyage) : **logo Miff** ou **votre propre photo** ? | **Logo Miff** (c'est Miff qui vous parle ; votre visage n'ajoute rien) |
| **Q3** | **Membre sans photo** : ses **initiales** (comme le Feed) ou le logo ? Et une personne **partie de la Miff** : logo + « Un proche » ? | **Initiales** ; parti = **logo + « Un proche »** |
| **Q4** | **Plusieurs personnes** (ex. 3 proches ont donné des nouvelles dans l'heure) : une ligne **regroupée** avec le logo, ou **une ligne par personne** avec sa photo ? | **Une ligne par personne** en v1 ; le logo reste pour la Miff entière et l'inconnu |
| **Q5** | **Photo dans la notification du système** (tiroir, écran verrouillé) ? | **Non en v1** (confidentialité à l'écran verrouillé ; iPhone exigerait l'extension) ; à rouvrir après la recette |

---

## 12. Risques, risques assumés et limites dites

**Risques assumés par le PM** (décision du 2026-10-23 : pas de relecture juridique ; textes rédigés par l'équipe, publiés sous sa responsabilité, jamais présentés comme relus) :
- **A1** Une **nouvelle donnée personnelle côté serveur** (identifiants + date, 30 jours) alors que la politique actuelle décrit un centre « local » (`docs/legal/politique-confidentialite.fr.md:240`, `:289`) ; la mise à jour du texte précède la mise en service.
- **A2** Après la suppression du compte d'un auteur, son **identifiant** reste jusqu'à 30 jours dans les journaux de ses anciens proches (pseudonyme, sans prénom ni photo) ; aucune purge croisée n'est possible sans fonction supplémentaire.
- **A3** Les photos s'affichent dans **une liste de plus** : un tiers qui regarde l'écran déverrouillé les voit, comme dans le Feed.
- **A4** **Droits du logo** non confirmés (`design/assets/logo/README.md` §1, §6.2) : ne bloque pas le code, bloque la publication si le PM ne les détient pas.
- **A5** Le consentement de localisation v3 dans l'app dit encore « pas d'historique » (J65 du registre) : ce ticket **ne le corrige pas** (ticket séparé déjà prévu).

**Limites connues, dites** :
- Le plafond **1 push par heure et par auteur** fait que certaines publications n'ont ni push ni ligne (le Feed les montre) ; une publication ignorée par le plafond n'est **pas** une perte du centre.
- Une ligne du journal peut exister **sans que la notification ait été vue** (téléphone éteint, autorisation retirée) : c'est voulu, c'est le but.
- Les causes **H1 à H4** restent à mesurer en recette (OPPO app balayée, iPhone dans le sens OPPO → iPhone) ; le journal les rend **indolores** sans les expliquer.
- La résolution de la photo dépend de la fiche de membre déjà lue : au démarrage à froid, ligne brièvement avec le logo avant la photo ; mesuré en recette (CNC-8, CNC-10).
- Un client modifié peut **supprimer ses propres lignes** : sans conséquence (elles sont privées).

---

## 13. Plan de test

- **Backend (émulateur Firestore + faux client FCM)** : forme exacte du document ; id déterministe ; idempotence (événement rejoué) ; atomicité avec `notifState` ; aucun journal sans envoi ; alerte début/fin (mêmes destinataires que l'envoi) ; plafond horaire ; suite de règles : propriétaire (get, list ≤ 100, delete), autre utilisateur, créateur, membre, anonyme, create / update refusés, `limit` > 100 refusé ; garde-coûts inchangé ; `npm test` de `backend/` et `functions/`.
- **Suite commune Android / iOS** (mêmes vecteurs, mêmes résultats) : `(type, acteur) → image + texte`, 12 vecteurs : N9 photo / initiales / inconnu ; alerte début, fin ; C1 ; N8 ; N3, N4 → logo ; N1, N2, N6, N7 (selon Q2) ; photo > 20 000 octets → initiales ; fusion sans doublon (push puis journal, journal puis push) ; curseur et recouvrement de 2 min ; première synchronisation (> 24 h lues) ; migration d'une ancienne clé N9.
- **Recette PM (2 comptes, OPPO + iPhone 11 Pro)** : publier de chaque téléphone vers l'autre, app ouverte / en arrière-plan / fermée / mode avion 3 h ; déclarer et terminer une alerte ; effacer une ligne puis réinstaller ; vérifier la lisibilité du logo à 40 dp.
- **CI iOS** : captures (`ci/…`) et tests Swift à relancer en CI cloud (aucune compilation locale sur ce poste).

---

## 14. Hors périmètre

Photo dans la notification du système (Q5) ; regroupement de plusieurs personnes (Q4) ; réglage de rétention ; export de l'historique ; accusés de réception ; notification au créateur d'une demande de lieu par push (N8 reste une ligne seule) ; correction du consentement v3 (A5) ; nouvelle fonction planifiée de purge ; changement des seuils orange / rouge ; modification des textes des notifications existantes. **Rien dans Notion, Figma ni Firebase n'a été modifié par ce document.**

*Ce document ne modifie ni le code, ni les règles, ni les fonctions. Aucun texte légal n'y est rédigé comme définitif ni présenté comme relu par un juriste.*
