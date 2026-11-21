# Analyse AARRR de Miff : acquisition, activation, rétention, revenu, recommandation (pré-lancement)

**Statut** : rapport d'analyse **pré-lancement**, à lire et discuter par le PM. Aucune décision n'est prise par ce document, aucun code n'est modifié, rien n'est écrit dans Notion (le PM le publie lui-même).
**Date** : 2026-11-19. **Vérité du code et des tickets** : `origin/main` au commit `a47bfcbc` (lu dans un worktree propre ; le dossier `C:\dev\miff` local est périmé et n'a pas servi).
**Rédigé par** : agent de rédaction, à la demande du PM (« optimiser l'app quasiment finalisée »).
**À retenir en une phrase** : l'app n'est pas publiée et ne compte que quelques testeurs, donc **il n'existe aucune donnée d'usage** ; ce rapport dit ce que l'app fait aujourd'hui, où l'on peut raisonnablement s'attendre à des fuites, **comment le mesurer** avant le lancement, et quoi tester en premier.

---

## 1. Résumé exécutif

**Cinq constats clés**

1. **Presque rien ne se mesure après la première Miff.** Le code envoie 7 événements utiles (inscription, connexion, Miff créée ou rejointe, fin d'onboarding, paywall affiché et fermé) ; un huitième existe dans le contrat mais n'est jamais envoyé (`onboarding_cta_tapped`). Rien ne dit si la personne a franchi les permissions, créé un lieu, vu un proche, ou est revenue. Le réseau (Miffs à 2 membres et plus) n'est pas mesurable avec Analytics tel que le contrat l'impose.
2. **La fuite d'entonnoir la plus probable est la « porte des permissions »** entre « Miff rejointe » et « premier Feed » : localisation « Toujours » **et** précise, **obligatoire et bloquante** (Android release : le relâchement est interdit à la compilation ; iOS : interrupteur de compilation), précédée d'une amorce de notifications. C'est aussi un risque de rejet Apple 5.1.2(i), assumé par le PM.
3. **La boucle d'invitation est le canal de croissance principal, et elle est fragile.** L'invité traverse environ 11 à 13 écrans avant de voir quoi que ce soit ; le message de partage mène à la page d'accueil du site (qui dit « Bientôt », sans boutons de store) **sans le code**, qu'il faut retaper en 12 caractères.
4. **Le moment « aha » dépend d'un geste physique ou manuel.** Le cercle ne reçoit un push que pour une update photo (« X a donné des nouvelles ») et pour l'alerte danger ; les arrivées et départs ne sont pas poussés aux proches (ticket Notifications, phase P2, non livrée). La valeur se constate donc surtout en ouvrant l'app (rétention « tirée », pas « poussée »).
5. **Le revenu n'est pas encore activable.** En production, le paywall affiche « Miff+ arrive bientôt » : aucun achat n'est branché, le premium est accordé à la main. Les risques de confiance déjà assumés (croix après 3 s, consentement groupé et préactivé, fenêtre d'avis filtrante) sont à surveiller dans les chiffres et dans les avis.

**Cinq priorités**

1. **Instrumenter l'activation et le « aha »** : contrat d'événements v2 (6 à 8 événements, noms sans mots interdits, jamais de lieu ni de position) + comptages serveur agrégés pour le réseau (Miffs à 2 membres et plus).
2. **Mesurer puis alléger la porte des permissions** : proxy sans position (`feed_first_view` contre `miff_joined`), aperçu de « ce que voient tes proches » avant la demande, plan B Apple prêt.
3. **Réparer la boucle d'invitation** : message de partage qui explique Miff, lien à code en fragment (jamais envoyé au serveur), page d'arrivée avec boutons de store le jour J.
4. **Raccourcir le chemin jusqu'au premier signal reçu** : premier lieu guidé après « Ta Miff est prête », première « nouvelle » guidée ; puis push d'arrivée en résumé discret (phase P2).
5. **Préparer la monétisation avant de l'ouvrir** : achat réel + fin d'abonnement (PREM-FIN), plan de mesure du paywall, garde-fous (croix, avis) ; vérifier avec Firebase si les tests A/B sont possibles avec la collecte « éteinte au démarrage » (Remote Config n'est pas intégré aujourd'hui).

---

## 2. Contexte et méthode

### 2.1 État réel respecté dans ce rapport

- L'app **n'est pas publiée** : seuls des testeurs (APK, TestFlight) l'utilisent. **Aucun taux** (conversion, rétention, activation) n'est connu, et aucun n'est inventé ici.
- Offre décidée : gratuit = **3 lieux de confiance** et **1 Miff à la fois** (la mention « 1 lieu » de `CLAUDE.md` est périmée) ; Miff+ = lieux et Miffs illimités + zones de danger ; essai 7 jours sur l'annuel seulement ; annuel 59,88 € (4,99 €/mois) reconduit au même tarif ; mensuel 6,99 € (`docs/specs/paywall-premium.md`).
- Android (version 0.50.0 sur `main`, 0.51.0 en PR) et iOS (TestFlight) ont rattrapé l'essentiel du même périmètre ; certains documents de `marketing/` (7 novembre) sont **antérieurs** à plusieurs livraisons (push entre proches, fenêtre d'avis, alerte danger) et sont corrigés ici par les tickets plus récents.
- Backend Firebase en plan **Blaze** (décision du 2026-11-11) : fonctions `notifierUpdate` (en production) et `notifierAlerte` (déployée le 2026-11-19, essai de bout en bout à faire).

### 2.2 Niveaux de preuve

| Étiquette | Sens |
|---|---|
| **[Confirmé code]** | Lu dans le code (fichier cité) ou dans un ticket/spec de `main` (fichier cité). |
| **[Décision PM]** | Décision enregistrée dans un ticket ou `CLAUDE.md`. |
| **[Hypothèse]** | Raisonnement de l'auteur, **non vérifié** ; à tester. |
| **[Repère externe]** | Chiffre ou fait venant d'une source tierce. Toujours accompagné de « non vérifié » quand je n'ai pas ouvert la source primaire ou que les définitions varient. |

### 2.3 Ce qui a été fait et pas fait

- Fait : lecture de `CLAUDE.md`, des specs, des tickets listés dans la demande, de `marketing/`, de `docs/release/decisions-stores-avant-soumission.md`, de `docs/legal/README.md`, de `docs/contracts/analytics-events.md`, du code d'analytique et des barrières de permissions Android et iOS, de quelques recherches web (annexe A).
- **Non fait** : aucun test sur téléphone, aucune lecture des consoles Firebase / Google Analytics / stores (aucun accès), aucune lecture de Notion (hors périmètre : les tickets de `docs/` sont le miroir). Les parcours ci-dessous sont reconstitués d'après les specs et le code ; **le nombre d'écrans est une estimation**.
- Les chiffres de marché (section 4 et annexe A) sont des **repères externes non vérifiés** : ils servent à situer un ordre de grandeur, **jamais de seuil ou d'objectif**.

---

## 3. Modèle de croissance de Miff

### 3.1 La boucle

```
Une personne (le « rassurant ») crée une Miff ──► partage le code à 1 à 14 proches
        ▲                                                │
        │                                                ▼
 un proche invité installe, s'inscrit, rejoint, accorde la localisation « Toujours »
        │                                                │
        │                                                ▼
        │            les statuts se mettent à jour tout seuls (arrivées, départs, update photo)
        │                                                │
        └─── il/elle invite à son tour d'autres cercles ◄─┘   (famille, amis, couple : 1 compte = 1 seule Miff en gratuit)
```

Particularités qui structurent toute l'analyse :

| Particularité | Conséquence pour la mesure et l'optimisation | Preuve |
|---|---|---|
| **Effet de réseau fort** : un utilisateur seul n'a aucune valeur | L'unité d'analyse utile est **le cercle (la Miff)**, pas l'utilisateur. Or le contrat Analytics interdit tout identifiant de Miff : il faut des comptages **côté serveur**. | [Confirmé code] `docs/contracts/analytics-events.md` règle 3 |
| **Deux rôles très différents** : le créateur (motivé, paye éventuellement) et l'invité (à convaincre, peur d'être « pisté ») | Mesurer les deux parcours séparément ; l'invité est le point faible de l'entonnoir. | [Confirmé] `marketing/positioning.md`, `launch-plan.md` §2 |
| **La valeur se reçoit, elle ne se « consomme » pas** : rassurer sans déranger | Un bon usage peut être **peu d'ouvertures** ; la rétention en « sessions » est un mauvais seul indicateur (voir 3.2). | [Hypothèse] |
| **Permissions qui conditionnent tout** : localisation « Toujours » précise, notifications | Les taux d'octroi sont la première variable d'activation, et sont **sensibles** (pas d'événement sur la position). | [Confirmé code] `PermissionGate.kt` |
| **Confiance et consentement** : application qui touche à la position d'un proche | Mesurer **sans jamais** dériver des lieux ou des habitudes ; pas de dark patterns. | [Décision PM] `CLAUDE.md` |

### 3.2 Métrique phare (North Star) proposée

**« Cercles rassurés par semaine »** = nombre de Miffs qui, sur 7 jours glissants, ont **au moins 2 membres qui partagent** et **au moins un signal reçu et vu par un autre membre** (arrivée/départ affiché dans le Feed, update photo vue, ou push « a donné des nouvelles » ouvert).

Pourquoi celle-ci plutôt que « utilisateurs actifs » ou « Miffs créées » :

1. Elle porte **l'effet de réseau** (≥ 2 membres) : une Miff créée mais jamais rejointe ne compte pas.
2. Elle porte **la promesse** (« savoir que ses proches vont bien ») : un signal **reçu et vu**, pas seulement émis.
3. Elle n'encourage pas à ouvrir l'app plus souvent, ce qui serait contraire à « sans déranger » : un cercle serein où chacun jette un œil une fois par semaine **compte**.
4. Elle se calcule **sans position ni lieu** : seulement des tailles de Miff et des dates de signal.

Contre-indicateurs à suivre en même temps (pour ne pas « gagner » en dégradant la confiance) : arrêts de partage, suppressions de compte, désinstallations (consoles), part des comptes qui coupent les statistiques, notes et thèmes des avis.

**Limite** : le contrat actuel ne permet pas de calculer cette métrique dans Analytics (pas d'identifiant de Miff). Elle se calcule **côté serveur** (comptage agrégé, section 5.4), avec un **substitut utilisateur** dans Analytics (événement `circle_signal_seen`, section 5.3) en attendant. [Hypothèse] à valider avec `backend-data`.

---

## 4. AARRR étape par étape

Pour chaque étape : état actuel, frictions, opportunités, métriques, hypothèses. Les hypothèses sont notées **H1 à H15** et priorisées en section 6.

### 4.1 Acquisition : « comment une personne arrive-t-elle ? »

**État actuel**

| Élément | Constat | Preuve |
|---|---|---|
| Stores | **Non publiés.** Fiches préparées FR et EN (`store-listing-ios.md`, `store-listing-android.md`), mots-clés **sans aucun volume mesuré** (« hypothèse », « non mesuré »). Sous-titre iOS FR « Des nouvelles, sans déranger ». | [Confirmé] `marketing/aso-keywords.md` |
| Site | Page d'accueil « Bientôt sur iPhone et Android », lien « Être prévenu(e) du lancement » = **simple `mailto`**. Aucune mesure de visites sur le site (aucune balise d'analyse dans `web/src`). | [Confirmé code] `web/src/landing-cta.html`, `web/src/landing.json` |
| Liste d'attente | Les adresses ne servent **qu'à la sortie** (politique de confidentialité) ; non utilisables pour la bêta (décision à confirmer). | [Confirmé] `decisions-stores-avant-soumission.md` §8 |
| Canal n° 1 | **Le partage du code par un utilisateur** (feuille de partage native). Texte : « Rejoins ma Miff « {nom} » sur Miff. Voici le code à saisir : {code} / Pour télécharger l'application : {lien} ». Le lien est **la racine du site**, donc la page d'accueil. | [Confirmé code] `android/.../ui/miff/MiffSharing.kt` l.24 et l.32 ; `ios/Miff/Views/Miff/MiffSharing.swift` |
| Canaux secondaires | Cercle personnel du PM, communautés, vidéos, presse : tous gratuits, publiés par le PM. Aucun budget. | [Décision PM] `launch-plan.md` §5 |
| Langues | FR et EN dans les apps et les fiches ; **ES, IT, PT, DE à venir** (ticket de traduction : ~1 238 textes par langue, relecture par un second agent, **pas de relecture native**, risque assumé). | [Confirmé] `docs/tickets/traduction-es-it-pt-de.md` §1 |
| Règle Google | Pour un compte développeur personnel récent : au moins **12 testeurs** en test fermé, **14 jours consécutifs**, avant la production. | [Confirmé] `launch-plan.md` §4 (page Google lue le 2026-11-07) |

**Frictions et fuites**

| # | Friction | Niveau de preuve |
|---|---|---|
| A1 | **Le lien de partage ne porte pas le code et ne mène à aucun store** : l'invité reçoit un code, un lien vers une page « Bientôt », puis doit retaper 12 caractères après installation. Le message ne dit pas ce qu'est Miff ni que le partage est consenti et révocable (point n° 10 de `decisions-stores-avant-soumission.md`, **non encore tranché**). | [Confirmé code] |
| A2 | **Aucun lien profond** (ouvrir l'app sur « Rejoindre » avec le code prérempli) : hors périmètre du ticket Miff (« lien d'invitation qui ouvre l'app »). | [Confirmé] `docs/tickets/miff.md` §2 (hors périmètre) |
| A3 | **Aucune mesure de l'acquisition** hors consoles des stores (à la publication) : ni visites du site, ni origine des installations. | [Confirmé code] |
| A4 | **Peur d'être pisté** de l'invité : le mot « localisation » fait peur à une partie de la cible ; le texte de partage ne rassure pas. | [Confirmé] `launch-plan.md` §9 point 5 |
| A5 | **Notes de store à risque** si la détection est mauvaise (OPPO) ou si le consentement choque (voir 7). | [Hypothèse] |
| A6 | **Visibilité de recherche inconnue** : aucun volume de mots-clés mesuré ; hypothèse du plan de lancement : la recherche pèsera peu au début (H8 du plan). | [Confirmé] `aso-keywords.md` |

**Opportunités**

- Corriger le message de partage (phrase d'explication proposée par `release-manager`, à valider) : H1.
- Page d'arrivée d'invitation avec le code lu **dans la partie `#` de l'adresse** (jamais envoyée au serveur ni aux journaux), bouton « Copier le code », boutons de store à la publication : H1. Côté app, un bouton « Coller » existe déjà sur l'écran de saisie du code (`miff.md` §5.3).
- Après publication, **expériences de fiche** gratuites des stores (Product Page Optimization chez Apple, expériences de fiche Google Play) : [Repère externe non vérifié dans cette session, à confirmer dans les consoles] : H12.
- Mesurer les invitations comme geste (événement `invite_shared`, sans code) : section 5.3.

**Métriques d'acquisition**

| Métrique | Définition | Source | Disponible aujourd'hui ? |
|---|---|---|---|
| Impressions → vues de fiche → installations | Entonnoir de fiche | Consoles des stores | Après publication seulement |
| Part de recherche et de « référent » | Origine des installations | Consoles des stores | Après publication |
| Invitations partagées par Miff créée | `invite_shared` / `miff_created` | Analytics (événement à ajouter) | **Non** |
| Rejoints par Miff créée | `miff_joined` (existe) / `miff_created` (existe) | Analytics | **Oui** (utilisateurs ayant consenti uniquement) |
| Visites de la page d'accueil et d'invitation | Visites, clics sur « Être prévenu(e) » | Aucune | **Non** (décision à prendre : une mesure sans cookie et sans consentement est un choix du PM) |

---

### 4.2 Activation : « quand l'utilisateur a-t-il reçu la valeur ? »

**Parcours actuel (reconstitué)** : voir l'annexe B pour le tableau écran par écran. Résumé du chemin d'un **invité** :

`Installation` → `Splash` → `Carrousel (3 slides, première ouverture)` → `Inscription` (e-mail ou Google ; Apple sur iOS) avec **ligne de consentement** (CGU + statistiques + rapports de plantage, aucune case) → `Profil` (photo facultative, prénom, nom) → `Téléphone` (**obligatoire**, pays + numéro, aucune vérification) → `Choix « Créer / Rejoindre »` → `Saisie du code` (12 caractères) → `Confirmation « Rejoindre et partager »` → `Notifications` (amorce, jamais bloquante) → `[Renouvellement du consentement si dû]` → `Localisation` (amorce **bloquante** ; Android 11+ : fenêtre « pendant l'utilisation » puis réglages système « Toujours » + précise) → `Feed`.

Soit **environ 11 à 13 écrans** avant le premier Feed. [Confirmé specs] `docs/tickets/onboarding.md` §3.1 et §6, `docs/tickets/miff.md` §3.1, `android/.../domain/permission/PermissionGate.kt`. **Le nombre exact est une estimation.**

**Faits importants confirmés**

| Fait | Preuve |
|---|---|
| Le consentement statistiques est **réputé donné au clic** sur « Rejoindre » (préactivé, groupé avec les CGU) ; modifiable dans Réglages. Risque J21 assumé. Conséquence mesure : **l'entonnoir avant l'inscription (installation, carrousel, écran d'inscription) est invisible dans Analytics**. | [Décision PM] `docs/tickets/authentification.md` §8 ; `docs/legal/README.md` J21 |
| La collecte démarre **éteinte** (manifeste Android : `firebase_analytics_collection_enabled`, `screen_view` automatique coupé ; iOS : même principe, `pauseCollection()` au lancement). Elle s'allume au clic ou à la lecture du choix du compte. | [Confirmé code] `AndroidManifest.xml` l.113-134 ; `ios/Miff/MiffApp.swift` l.19-22 |
| La **localisation « Toujours » + précise est obligatoire** : écran bloquant sur Android et iOS ; Android release : `miff.locationBlocking=false` **interdit** sans option explicite ; iOS : `MIFF_LOCATION_BLOCKING` par défaut `YES`. Sur l'écran bloquant, un lien discret « Se déconnecter » est la seule sortie. | [Décision PM] `docs/tickets/lieux-de-confiance.md` Q2 ; [Confirmé code] `android/app/build.gradle.kts` l.353-365, `GatePolicy.kt`, `ios/.../LocationBlockingSwitch.swift` |
| Un **consentement au partage par Miff** précède la permission système (« Rejoindre et partager » / « Créer et partager »), versionné et daté. | [Confirmé] `docs/tickets/miff.md` §5.4 |
| Le téléphone est **obligatoire** (Q1, contre la recommandation de l'agent), non vérifié, visible des membres de la Miff. | [Décision PM] `docs/tickets/onboarding.md` §6.4 |
| **Aucune échappatoire** « Ce n'est pas toi ? » pendant l'onboarding (Q14). | [Décision PM] |
| Un compte = **un appareil** à la fois. | [Décision PM] `docs/tickets/session-appareil-unique.md` |
| Après « Ta Miff est prête », le créateur arrive au Feed. **Aucune étape guidée « crée ton premier lieu »** n'est spécifiée : sans lieu, aucun statut automatique n'existe. | [Confirmé] `docs/tickets/miff.md` §3.1 ; chaîne `miff_places_empty` |
| Dans une Miff à un seul membre, le Feed affiche « Personne d'autre dans cette Miff pour l'instant » et propose le partage du code. | [Confirmé code] `FeedBubble.kt` l.195, `FeedScreen.kt` l.178 |

**Frictions et fuites d'entonnoir probables** (toutes **[Hypothèse]** sur leur ampleur, aucun chiffre)

| # | Où | Pourquoi ça peut fuir | Qui est touché |
|---|---|---|---|
| B1 | **Porte des permissions** (localisation « Toujours » + précise, bloquante) | Demande **avant** d'avoir vu la valeur ; sur Android 11+, détour par les réglages système ; l'invité qui n'est pas convaincu abandonne. Risque de **refus Apple** (5.1.2(i)) qui ferait basculer tout le parcours iOS. | Tous, surtout l'invité |
| B2 | **Téléphone obligatoire** et **non vérifié** | Donnée de contact exigée avant toute valeur ; « obligation = friction » reconnue par le ticket (R7, R18). | Tous |
| B3 | **Longueur du chemin** (11 à 13 écrans) | Chaque écran est une fuite potentielle ; aucun écran n'est mesuré. | Tous |
| B4 | **Saisie d'un code de 12 caractères** | Retaper un code secret depuis une messagerie ; un bouton « Coller » existe, mais le copier-coller dépend de l'invité. | Invité |
| B5 | **Valeur différée** : sans lieu créé et sans événement de déplacement, le Feed est « Pas encore de nouvelles » | Le premier « il est arrivé » peut arriver des heures ou des jours après l'inscription. | Tous |
| B6 | **Notifications demandées en premier** (avant la localisation), avant que l'utilisateur ait vu un signal | Le taux d'acceptation d'une demande « à froid » est généralement plus faible ; sur iOS la fenêtre système n'apparaît qu'une fois. | Tous |
| B7 | **Création d'un compte par erreur** sans sortie visible avant la fin de l'onboarding | Abandon définitif plutôt que reprise. | Quelques-uns |
| B8 | **Fiabilité de détection sur OPPO/ColorOS** : une arrivée en retard, c'est un « aha » raté | Mauvaise première impression, puis désinstallation. | Utilisateurs Android à surcouche agressive |
| B9 | **Un compte = un appareil** | Changement de téléphone, tablette de famille : déconnexion de l'autre appareil. | Cas d'usage de proches partageant un appareil |

**Définition proposée de l'activation** (à valider par le PM)

| Niveau | Définition | Pourquoi |
|---|---|---|
| **A0 : compte créé** | `sign_up_completed` (existe) | Début mesurable (consentement donné) |
| **A1 : onboarding terminé** | `onboarding_completed` (existe) : profil + téléphone + première Miff créée ou rejointe | Premier jalon contractuel du PM |
| **A2 : Miff « vivante »** | Miff avec **≥ 2 membres qui partagent** dans les 7 jours suivant sa création | Prérequis de toute valeur ; **mesure serveur** |
| **A3 : « aha » individuel** | L'utilisateur a **vu au moins un signal d'un proche** (statut connu d'un autre membre dans le Feed, update vue, ou push « a donné des nouvelles » ouvert) dans les 7 jours suivant son installation | Le premier « tout va bien » reçu ; **événement à ajouter** (`circle_signal_seen`) |
| **A4 : « tu es aussi rassurant »** | L'utilisateur a **émis** un signal (arrivée/départ automatique ou première update) | Boucle bidirectionnelle |

**Activation = A3 pour un invité ; A2 puis A3 pour un créateur.** Le moment « aha » est « le premier signal d'un proche vu ».

**Opportunités** (H3, H4, H6, H7, H11)

- **Étape guidée « ajoute ton premier lieu »** juste après « Ta Miff est prête » pour le créateur (sans lieu, la Miff est muette).
- **Première nouvelle guidée** : une update photo génère un push immédiat chez les proches (« X a donné des nouvelles ») : c'est le seul « aha » **instantané** disponible aujourd'hui (les arrivées dépendent d'un déplacement).
- **Amorce de localisation plus convaincante** : montrer **ce que voient les proches** (une carte de statut type, sans position) avant la fenêtre système ; le texte fusionné avec le consentement existe déjà, il peut s'enrichir d'un aperçu.
- **Amorce de notifications déplacée** après la première valeur (premier signal reçu) plutôt qu'avant la localisation. [Hypothèse]
- **Aide OPPO** : la page « Si les rappels n'arrivent pas » est prévue dans le ticket Notifications (§8.4, M9) ; vérifier qu'elle est atteignable dès la première heure.

**Métriques d'activation**

| Métrique | Calcul | Disponible ? |
|---|---|---|
| Taux `sign_up_completed` → `onboarding_completed` | existants | Oui |
| Taux `onboarding_completed` → `feed_first_view` | proxy de **passage de la porte des permissions** sans événement sur la position | **À ajouter** |
| Délai médian `onboarding_completed` → `circle_signal_seen` (premier) | A3 | **À ajouter** |
| Part des Miffs créées devenant « vivantes » à 7 jours | A2 | **Serveur, à ajouter** |
| Part des utilisateurs avec première update dans les 7 jours | `first_news_shared` | **À ajouter** (recommandé par `release-manager`, `decisions-stores-avant-soumission.md` §14) |

---

### 4.3 Rétention : « reviennent-ils ? »

**Inventaire des mécanismes de rétention existants** (tous gratuits, sans publicité)

| Mécanisme | Ce qu'il fait | Preuve |
|---|---|---|
| **Feed** (bulle de couleur, cartes de proches, « Dernière nouvelle il y a… ») | Lieu de la valeur : voir d'un coup d'œil | [Confirmé] `docs/tickets/feed.md` |
| **Couleurs vert / orange / rouge** (seuils 2 h / 4 h ; zone de danger : rouge à l'entrée) | Escalade automatique, filet de sécurité | [Confirmé] `docs/specs/update-et-escalade-statut.md` |
| **Compte à rebours avant l'orange** (Feed et Profil) | Pousse doucement vers l'update | [Confirmé] `docs/tickets/compte-a-rebours-orange.md`, Android 0.42.0 |
| **Rappels locaux « à toi »** (N1, N2, N6, N7) et « ta Miff a changé de couleur » (N3, N4) | Notifications **locales** sur l'appareil de la personne | [Confirmé] CHANGELOG Android 0.26.0 à 0.31.0 |
| **Push entre proches** « X a donné des nouvelles » (fonction `notifierUpdate`, en production ; validé de bout en bout le 2026-11-13) | **Seul** push vers les proches avec l'alerte danger | [Confirmé] mémoire d'état des déploiements ; `docs/tickets/notification-update-membres.md` |
| **Centre de notifications** + « Depuis ta dernière visite » (P1, local) | Retrouver ce qu'on a manqué, sans notification | [Confirmé] `docs/tickets/notifications-p1-derniere-visite.md` |
| **Centre de notifications complet (journal serveur 30 jours)** | Décidé le 2026-11-19, **pas encore développé** | [Décision PM] `docs/tickets/centre-notifications-complet.md` |
| **Alerte « Je suis en danger »** (maintien 5 s ; push au cercle) | Valeur de dernier recours ; fonction déployée, drapeau `ALERT_DANGER` à activer en build de diffusion | [Confirmé] `docs/tickets/alerte-danger.md`, `android/app/build.gradle.kts` l.144 |
| **Mode voyage**, **Répartition du temps** (graphique local, apparaît après 7 jours de journal), **demande d'ajout de lieu** | Valeur personnelle et de coordination | [Confirmé] tickets correspondants |

**Constat structurant** : les arrivées et départs (le cœur promis par la landing et le carrousel) ne déclenchent **aucun push vers les proches** aujourd'hui : « B3 : arrivée dans un lieu de confiance » est classée phase P2 « à construire » (`docs/tickets/notifications.md` §5, `notification-update-membres.md` §2). Un proche **découvre** une arrivée en ouvrant l'app. Le site a d'ailleurs retiré ses maquettes de notifications (branches `web/accueil-sans-notifs-arrivee`, `web/og-sans-notifs`). **Conséquence [Hypothèse]** : sans push, la rétention repose sur l'habitude d'ouvrir l'app et sur les rappels « à toi ».

**Frictions et risques de rétention**

| # | Risque | Niveau de preuve |
|---|---|---|
| C1 | **Fiabilité du suivi en arrière-plan** : ColorOS (OPPO du PM), économie d'énergie, iOS « changement de position significatif ». | [Confirmé] `docs/tickets/notifications.md` R1 ; [Hypothèse] sur l'ampleur |
| C2 | **Pas de push d'arrivée** : valeur « tirée » ; les proches qui n'ouvrent pas l'app ne voient rien. | [Confirmé] ci-dessus |
| C3 | **Cercle qui s'éteint** : un cercle de 2 personnes dont l'une désinstalle ; **aucune relance** de la personne restante n'est prévue. | [Hypothèse] |
| C4 | **Fatigue de notifications ou, au contraire, silence** : le ton « sans déranger » impose peu de notifications ; trop peu = oubli. | [Hypothèse] |
| C5 | **Valeur en trop dans l'inquiétude** : un « rouge » mal compris provoque un appel anxieux, puis une désinstallation. Le ton non anxiogène est un garde-fou de rétention. | [Décision PM] `CLAUDE.md` |
| C6 | **Arrêt du partage** : un membre qui arrête de partager rend le Feed muet pour les autres (et il ne voit plus les autres, réciprocité). | [Confirmé] `docs/tickets/miff.md` §5.13 |
| C7 | **Fin de Miff+** : la fin d'abonnement fait perdre des Miffs et lieux à d'autres membres ; PREM-FIN (30 jours de mise en veille) n'est pas encore développé. | [Confirmé] `docs/tickets/prem-fin.md` |

**Définitions proposées (rétention)**

La rétention de Miff doit refléter la **valeur reçue**, pas le simple retour. Deux familles :

| Famille | Définition | Source |
|---|---|---|
| **Rétention d'ouverture** (classique) | Part des nouveaux utilisateurs actifs (au moins une ouverture) à J1, J7, J30 après l'inscription | GA4 (consentants), consoles des stores |
| **Rétention de valeur** (proposée) | Part des nouveaux utilisateurs qui ont **vu au moins un signal d'un proche** (`circle_signal_seen`) pendant la semaine S, pour S = 1, 2, 4 après l'inscription | GA4 |
| **Rétention de cercle** (proposée) | Part des Miffs « vivantes » en semaine 1 qui le sont encore en semaines 2, 4, 8 | Serveur (agrégat) |

Remarque : l'objectif **n'est pas** d'augmenter le nombre d'ouvertures. Une rétention de valeur stable avec peu d'ouvertures est un bon résultat pour un produit « sans déranger ».

**Hypothèses de rétention** : H5 (push d'arrivée en résumé discret), H10 (relance douce d'une Miff à un seul membre), H11 (aide OPPO), H6 (primer de notifications). Voir section 6.

---

### 4.4 Revenu : « paient-ils ? »

**État actuel**

| Élément | Constat | Preuve |
|---|---|---|
| Offre | Miff+ : lieux et Miffs illimités, zones de danger. Essai 7 j sur l'annuel seulement. Annuel 4,99 €/mois (59,88 €), mensuel 6,99 €. « Notifications intelligentes » **retirée** de l'offre affichée. | [Décision PM] `paywall-premium.md` |
| Limites gratuites | **3 lieux de confiance**, **1 Miff**. Zone de danger **réservée** au premium (créer et accepter), mais **tout membre peut la demander** (même gratuit) ; le **créateur** doit être Miff+ pour l'accepter. | [Décision PM] `lieu-danger-demande-gratuite.md` |
| Déclencheurs du paywall | 4e lieu, zone de danger, 2e Miff (sélecteur du Feed visible de tous). **Aucune relance automatique.** | [Confirmé code] `PlaceNavigation.kt` l.98-100 ; `paywall-premium.md` |
| Écran en production | **Variante « sans achat »** (`PaywallOffer.ComingSoon`) : « Miff+ arrive bientôt », aucun prix, aucun essai ; **aucune bibliothèque de facturation** dans le build Android. Le premium est accordé **à la main** dans la console. | [Confirmé code] `domain/paywall/PaywallOffer.kt` l.39-43 et l.92 ; `docs/dev/premium-manuel.md` |
| Croix du paywall | N'apparaît que **3 secondes** après chaque affichage (retour et gestes ignorés pendant ce délai). Risque assumé par le PM (rejet, avis). | [Décision PM] `paywall-v2.md` §6 ; `domain/paywall/PaywallCloseGate.kt` l.14 |
| Validation | Validation directe côté serveur (pas de RevenueCat). Achat réel : SRV-7, SRV-8A, SRV-8I **non développés**. PREM-FIN à livrer **avant** l'ouverture publique des achats. | [Décision PM] `premium-achat-reel.md` ; `prem-fin.md` |
| Prix | Lus dans les stores, **jamais en dur** ; « presque 30 % d'économie » (28,6 % réel) ; règle Apple 2.3.1(a) à surveiller. | [Décision PM] |
| Coût du service | Blaze avec alertes de budget ; coût dominant = lectures Firestore (≈ 130 par utilisateur actif et par jour estimées pour le Feed). | [Confirmé] `docs/tickets/feed.md` §1 ; `docs/dev/surveillance-quotas.md` |

**Frictions, risques et opportunités**

| # | Constat | Niveau de preuve |
|---|---|---|
| D1 | **Aucun revenu possible aujourd'hui** : le paywall est informatif. Les événements `paywall_shown` / `paywall_dismissed` ne mesurent que l'**exposition**, pas l'intention d'achat. | [Confirmé code] |
| D2 | **Le payeur n'est pas le bénéficiaire principal** : lieux et zones de danger sont gérés par le **créateur** ; c'est son droit Miff+ qui est lu. Un membre gratuit qui voudrait une zone de danger ne peut que la **demander**. | [Confirmé] `lieu-danger-demande-gratuite.md` §2 |
| D3 | **Les 3 lieux gratuits suffisent probablement** à beaucoup de cercles (maison, école, travail) : exposition au paywall potentiellement **faible**. | [Hypothèse] |
| D4 | **Zone de danger = libellé fort** : « zone de danger » comme argument de vente dans un produit « non anxiogène » ; le PM a retenu ce libellé (le ticket note le risque de ton R1). | [Confirmé] `feed.md` R1 |
| D5 | **La croix différée de 3 s** peut dégrader la confiance, les avis et le rejet de revue. Le paywall n'apparaît qu'à une limite atteinte (pas à l'ouverture), ce qui limite l'exposition. | [Décision PM, risque assumé] |
| D6 | **Fin d'abonnement** : suppression des excédents après 30 jours = un cercle peut disparaître ; sensible pour la confiance. PREM-FIN pas encore livré. | [Confirmé] |
| D7 | **Mesure d'achat** : sans événement d'achat dans le contrat, le revenu se lira dans les consoles des stores et dans `entitlements` (serveur), pas dans Analytics. | [Confirmé] contrat : « Aucun événement d'achat n'est défini ». |
| D8 | **Opportunité** : quand un membre gratuit **demande une zone de danger**, c'est le **créateur** qui rencontre le paywall en l'acceptant : message contextuel (« Marie a demandé une zone de danger ») plus parlant qu'un paywall générique. | [Hypothèse] H9 |
| D9 | **Pari produit** : « Miff+ pour le cercle » (un abonnement qui profite à tous les membres) correspond mieux à la valeur partagée. À décider, pas à tester sans cadrage (stores, famille). | [Hypothèse] |

**Métriques de revenu** (à partir de l'ouverture des achats)

| Métrique | Définition | Source |
|---|---|---|
| Exposition au paywall | `paywall_shown` par déclencheur / utilisateurs actifs | Analytics (existe) |
| Taux de fermeture | `paywall_dismissed` / `paywall_shown` par déclencheur | Analytics (existe) |
| Intention | `paywall_cta_tapped` (annuel, mensuel) | Analytics (**à ajouter**) |
| Essai démarré, converti, annulé | États de l'abonnement | Consoles des stores + `entitlements` côté serveur |
| Taux d'accès payant | Abonnés / utilisateurs actifs | Serveur (comptage) |
| Revenu moyen par abonné, remboursements | Consoles des stores | Stores |
| Coût par utilisateur actif | Coûts Blaze / utilisateurs actifs | Console Google Cloud / `surveillance-quotas.md` |

---

### 4.5 Recommandation : « parlent-ils de Miff, invitent-ils ? »

**État actuel**

| Levier | État | Preuve |
|---|---|---|
| **Invitation par code** | Partage natif ; code de 12 caractères ; **15 membres maximum** (plafond doux dans l'app) ; renouvelable ; **aucune validation d'arrivée** ; changement automatique du code à chaque retrait. | [Confirmé] `miff.md` §5.3, §5.8 |
| **Message de partage** | Texte court sans explication ; lien vers la racine du site. | [Confirmé code] |
| **Lien profond / lien d'invitation** | **Absent** (hors périmètre explicite). | [Confirmé] `miff.md` §2 |
| **Fenêtre d'avis** (« J'adore ! / Peux faire mieux... ») | Construite Android et iOS. **Drapeau de build** `REVIEW_PROMPT` : `false` par défaut en build de diffusion, `true` en debug. Conditions : Miff depuis ≥ 7 jours **et** au moins un statut de proche vu ; retour après 30 jours (fermeture) ou 90 jours (« Peux faire mieux ») ; « J'adore ! » → avis natif du store. | [Confirmé code] `android/app/build.gradle.kts` l.139, l.188 ; `docs/tickets/pop-ins-avis.md` §8 |
| **Formulaire de retours** | « Peux faire mieux... » → formulaire externe ; adresse renseignée le 2026-11-13. | [Confirmé] CHANGELOG 0.39.0 |
| **ASO et fiches** | Prêtes FR/EN ; aucune mesure ; textes sans marque tierce ni promesse de sécurité. | [Confirmé] `marketing/*` |
| **Contenu** | Idées calmes (FAQ « suis-je pisté(e) ? », consentement en 15 secondes), publiées par le PM. | [Confirmé] `launch-plan.md` §7 |

**Frictions et risques**

| # | Constat | Niveau de preuve |
|---|---|---|
| E1 | **La fenêtre d'avis est un filtrage** : seuls les satisfaits sont envoyés vers le store. Apple 5.6.1 (invites personnalisées interdites) ; Google (pas de question d'opinion avant l'API d'avis). Risque assumé par le PM, **compte développeur partagé avec Ghostrun**. Le build de diffusion ne l'inclut pas sans reconfirmation du PM. | [Décision PM, risque assumé] `pop-ins-avis.md` §2, §4 |
| E2 | **Un invité sur deux peut ne pas comprendre pourquoi installer** (peur du pistage) ; l'argument « tu choisis ce que tu partages » est dans les fiches mais pas dans le message d'invitation. | [Hypothèse] |
| E3 | **Plafond de 15 membres et absence de validation** : un cercle étendu s'agrandit par fuite de code ; sans effet sur la recommandation mais sur la confiance. | [Confirmé] |
| E4 | **Pas de mesure de la recommandation** : ni invitations, ni jointures par invitation, ni NPS. | [Confirmé code] |
| E5 | **Langue** : un cercle familial multilingue (ES, IT, PT, DE à venir) ; la page d'invitation et le message de partage doivent suivre la langue de **l'invité**, pas de l'émetteur. | [Hypothèse] |

**Opportunités** : H1 (message + lien à fragment), H12 (expériences de fiche), H13 (langues), H14 (demande d'avis conforme à l'API officielle).

**Métriques de recommandation**

| Métrique | Définition | Disponible ? |
|---|---|---|
| **Invitations par Miff créée** | `invite_shared` / `miff_created` | À ajouter |
| **Rejoints par Miff créée** | `miff_joined` / `miff_created` | Oui (utilisateurs consentants) |
| **Taux d'acceptation d'invitation** | `miff_joined` (hors premier de la Miff) / `invite_shared` | À ajouter (ordre de grandeur seulement : une invitation partagée peut viser plusieurs personnes) |
| **Taille moyenne et répartition des Miffs** | Comptage serveur | À ajouter |
| **Note de store, thèmes des avis** | Lecture manuelle | Après publication |
| **Coefficient viral** | Rejoints / utilisateur actif | **À ne pas calculer avant** d'avoir des données ; aucun repère n'est donné ici |

---

## 5. Plan de mesure

### 5.1 Règles absolues (rappel du contrat et de `CLAUDE.md`)

1. **Aucune position**, aucune adresse, aucun nom, **aucune catégorie ni nom de lieu** dans un événement ; aucune distance à un lieu.
2. **Liste blanche stricte** : le code **refuse** tout événement ou paramètre inconnu, tout nom contenant l'un des mots interdits (`place`, `location`, `lieu`, `position`, `zone`, `geo`, `region`, `address`, `gps`, etc.) et tout type non permis. Un événement par PR sur le contrat **et** sur les deux plateformes, validé par le PM.
3. **Aucun identifiant utilisateur** (`setUserId` interdit), aucune propriété utilisateur, aucun identifiant de Miff, aucun code.
4. **Consentement d'abord** : rien n'est envoyé avant le clic « Rejoindre » ou « Continuer » ; modifiable dans Réglages.
5. **Rétention GA4 : 14 mois**, option « Réinitialiser les données utilisateur lors d'une nouvelle activité » **désactivée** (action de console du PM, `docs/legal/README.md` §3 action 1). Cette valeur ne touche pas les rapports agrégés standard ; elle s'applique aux **explorations et entonnoirs** (page Google lue le 2026-11-19, [Repère externe] ; le défaut d'une nouvelle propriété n'y est pas indiqué : **vérifier à l'écran**).
6. **Chaque événement ajouté élargit** les déclarations des stores (Data safety, App Privacy) et la politique de confidentialité (J23).

### 5.2 Inventaire : ce qui existe déjà dans le code

| Événement (contrat v1) | Paramètres | Envoyé par le code ? | Où |
|---|---|---|---|
| `sign_up_completed` | `method` (email, google, apple) | **Oui** (3 chemins) | Android `data/session/SessionManager.kt` l.337, l.820, l.881 ; iOS `Services/Session/SessionManager+Operations.swift` l.93, l.320, l.395 |
| `sign_in_completed` | `method` | **Oui** | Android `SessionManager.kt` l.875 ; iOS `SessionManager+Operations.swift` l.389 |
| `miff_created` | `is_first_miff` | **Oui** | Android `ui/miff/CreateMiffViewModel.kt` l.140 ; iOS `ViewModels/CreateMiffViewModel.swift` l.106 |
| `miff_joined` | `is_first_miff` | **Oui** | Android `ui/miff/JoinMiffViewModel.kt` l.225 ; iOS `ViewModels/JoinMiffViewModel.swift` l.193 |
| `onboarding_completed` | `via` (created, joined) | **Oui**, une seule fois, seulement si l'utilisateur n'avait aucune Miff avant | Android `SessionManager.kt` l.441 ; iOS `SessionManager+Operations.swift` l.233 |
| `paywall_shown` | `trigger` (extra_miff, extra_trusted_place, danger_zone) | **Oui** | Android `ui/place/PlaceNavigation.kt` l.323 ; iOS `Views/Place/PlacePaywallView.swift` l.31 |
| `paywall_dismissed` | `trigger` | **Oui** (une seule fois par affichage) | Android `PlaceNavigation.kt` l.318 ; iOS `PlacePaywallView.swift` l.37 |
| `onboarding_cta_tapped` | `slide_index` | **Non** : défini mais **jamais appelé** (le carrousel n'a pas accès au traceur : décision Q8) | `AnalyticsEvent.kt`, `AnalyticsEvent.swift` |
| `analytics_diagnostic_test` | aucun | Écran de diagnostic seulement | Debug / TestFlight |

Autres faits :
- **Un seul point d'entrée** (`AnalyticsTracker`) ; contrôle de liste blanche **puis** du consentement ; tests identiques sur les deux plateformes. [Confirmé code] `android/.../domain/analytics/AnalyticsTracker.kt`.
- **Événements automatiques du SDK** (non filtrables) : `first_open`, `session_start`, `user_engagement`, `app_update`, `os_update` ; `screen_view` **coupé** sur les deux plateformes. [Confirmé] contrat, section « Exception ».
- **Crashlytics** : même consentement ; durée 90 jours (valeur publiée par Google, `docs/legal/README.md` J23).
- **Aucun événement sur** : profil, permissions, lieux, Feed, update, notifications, réglages, abonnement, partage du code, suppression de compte. [Confirmé] contrat.
- **`first_open` et collecte « éteinte au démarrage »** : [Hypothèse à vérifier avec DebugView] la collecte étant éteinte jusqu'au consentement, `first_open` pourrait ne jamais être enregistré pour un nouvel utilisateur, ce qui fausserait les rapports de **rétention GA4 natifs** (qui reposent sur la première ouverture). À contrôler avant de s'y fier ; sinon calculer la rétention à partir de `sign_up_completed` comme date de départ.

### 5.3 Ce qui manque et proposition de contrat v2

Tous les noms ci-dessous **respectent** les mots interdits ; chacun est **une proposition à valider par le PM** avant toute PR sur le contrat et le code.

| # | Événement proposé | Quand | Paramètres (valeurs fermées) | Étape AARRR | Pourquoi | Sensibilité |
|---|---|---|---|---|---|---|
| E-01 | `profile_completed` | Le profil + téléphone est écrit et confirmé par le serveur | aucun | Activation | Mesurer la fuite « profil + téléphone » (B2) | Faible |
| E-02 | `invite_shared` | La feuille de partage du code est ouverte, ou « Copier » touché | `kind` : `share`, `copy` | Recommandation | Mesurer l'intention d'inviter (jamais le code, jamais le destinataire) | Faible |
| E-03 | `feed_first_view` | Première fois que le Feed s'affiche sur ce compte | aucun | Activation | **Proxy de passage de la porte des permissions** sans parler de position (B1) | Faible |
| E-04 | `notif_primer_result` | Fin de l'écran d'amorce de notifications | `result` : `allowed`, `later`, `already_on` | Activation | Taux d'octroi des notifications (H6) | Moyenne : le contrat dit « aucun événement sur les notifications » ; **décision explicite du PM** |
| E-05 | `first_news_shared` | Première update photo publiée | aucun | Activation / Rétention | Recommandé par `release-manager` ; geste émetteur | Faible |
| E-06 | `circle_signal_seen` | Premier signal d'un proche vu dans la session (Feed avec ≥ 1 statut connu d'un autre membre, ou push ouvert) | `source` : `feed`, `notification` ; `members_bucket` : `2`, `3_5`, `6_plus` | **Aha / Rétention / North Star** | Mesure A3 ; ne dit ni qui ni où | Moyenne : pas de lieu, pas d'identifiant de Miff |
| E-07 | `news_shared` | Chaque update photo publiée | `has_description` : bool | Rétention | Fréquence d'émission ; usage de la description | Faible |
| E-08 | `paywall_cta_tapped` | Appui sur un bouton d'offre | `plan` : `annual`, `monthly` ; `trigger` | Revenu | Intention d'achat | Faible |
| E-09 | `subscription_started` | Achat confirmé par le store (client) | `plan` ; `trial` : bool | Revenu | Vérité complémentaire au serveur | Faible |
| E-10 | `purchase_failed` | Achat interrompu ou refusé | `reason` : `cancelled`, `pending`, `error` | Revenu | Frictions d'achat | Faible |
| E-11 | `sharing_stopped` | L'utilisateur arrête de partager avec une Miff | aucun | Confiance | Indicateur de santé du consentement | Moyenne : à décider (sensible) |
| E-12 | `account_deletion_started` | Début d'une suppression de compte | aucun | Confiance | Indicateur de perte de confiance | Moyenne : à décider |

**À NE PAS instrumenter** (recommandation) :

- tout événement sur **les lieux** (création, nombre, type) : un nom contenant `place` est refusé par le code et un synonyme **contournerait l'esprit** du contrat ; utiliser des comptages **serveur** agrégés (5.4) ;
- l'**octroi de la localisation** : la décision de `decisions-stores-avant-soumission.md` §14 est de ne **pas** le mesurer par événement ; le proxy E-03 suffit ;
- l'**alerte danger** (déclenchement) : information sensible, à ne lire que dans des compteurs serveur agrégés pour la surveillance d'abus ;
- tout **identifiant de Miff** ou **de personne**.

**Paramètres de sécurité des événements** : valeurs fermées, aucun texte libre, aucun nombre décimal, aucun tableau (contrat règle 6).

### 5.4 Comptages côté serveur (sans passer par Analytics)

Analytics ne peut ni compter des Miffs (aucun identifiant) ni couvrir les utilisateurs qui ont coupé les statistiques. Pour les métriques de **réseau, de revenu et de coût**, il faut des **comptages agrégés issus de données déjà présentes** dans Firestore et Authentication, sans nouveau contenu personnel. **[Hypothèse]** : à cadrer avec `backend-data` (fonction planifiée, ou script lecture seule lancé à la demande par le PM) ; **le PM décide** (nouvelle finalité à décrire dans la politique de confidentialité si c'est automatisé).

| Indicateur serveur | Calcul | Usage |
|---|---|---|
| Comptes créés par jour | Console Authentication (déjà utilisée dans `surveillance-quotas.md`) | Vérité de référence ; rapport avec `sign_up_completed` = **couverture Analytics** |
| Part des comptes qui consentent aux statistiques | `users/{uid}.analyticsConsent.enabled` | Mesurer le biais de l'échantillon Analytics |
| **Distribution de la taille des Miffs** | Nombre de membres par Miff (1, 2, 3 à 5, 6 et plus) | Réseau ; A2 |
| **Miffs vivantes à J7** | ≥ 2 membres qui partagent 7 jours après la création | A2 |
| **Cercles rassurés (North Star)** | Miffs ≥ 2 membres partageant avec ≥ 1 signal dans les 7 derniers jours | North Star (le « vu » reste mesuré côté client par E-06) |
| Délai de détection (agrégé) | Écart entre l'instant de présence et l'instant d'écriture, en tranches | Qualité du suivi (B8, C1) |
| Abonnements par état (essai, actif, grâce, expiré) | `entitlements/{uid}` | Revenu |
| Envois de push par type, échecs | Journaux des fonctions (`notifierUpdate`, `notifierAlerte`) | Qualité et coût |
| Lectures Firestore par utilisateur actif | Console (`surveillance-quotas.md`) | Coût |

### 5.5 Tableaux de bord proposés (outils gratuits)

Looker Studio avec connecteur GA4 est gratuit [Repère externe non vérifié dans cette session ; à confirmer au moment de l'installation]. Sinon, les rapports GA4 natifs et les consoles suffisent pendant la bêta, et un **carnet de bêta manuel** (tableur) pour les cercles de test (voir 6.1).

| Tableau de bord | Contenu | Fréquence | Données |
|---|---|---|---|
| **1. Entonnoir d'activation** | `sign_up_completed` → `profile_completed` → `onboarding_completed` → `feed_first_view` → `circle_signal_seen` ; ventilation par `method`, `via`, plateforme | Hebdomadaire | Analytics |
| **2. Santé du réseau** | Taille des Miffs, Miffs vivantes à J7, cercles rassurés par semaine, invitations par Miff | Hebdomadaire | Serveur + Analytics |
| **3. Rétention** | Rétention d'ouverture J1/J7/J30, rétention de valeur S1/S2/S4, rétention de cercle | Mensuelle | Analytics + serveur |
| **4. Monétisation** | Exposition et fermeture du paywall par déclencheur, intention, essais, conversions, remboursements | Hebdomadaire après ouverture des achats | Analytics + stores + serveur |
| **5. Confiance et qualité** | Part qui coupe les statistiques, arrêts de partage, suppressions de compte, plantages, notes de store, courriels de support | Hebdomadaire | Serveur + consoles + support |
| **6. Coûts** | Lectures par utilisateur actif, coûts Blaze, envois de push | Quotidien pendant la bêta | Console Firebase (existant) |

### 5.6 Seuils d'alerte

**Je n'ai aucune donnée de référence** : les seuils chiffrés sont donc **à fixer après la phase 2 de bêta** (`launch-plan.md` §8 le dit déjà). En attendant, deux types de règles **sans chiffre inventé** :

| Règle | Formulation | Statut |
|---|---|---|
| Seuils de coût déjà décidés | Ratio lectures par utilisateur actif **> 100** : à examiner ; **> 150 deux jours de suite** ou total **> 30 000 lectures deux jours de suite** : signal de bascule | [Confirmé] `docs/dev/surveillance-quotas.md` |
| Écart par rapport à la tendance | Alerte si une étape de l'entonnoir **baisse de plus de moitié** par rapport à la médiane des 4 semaines précédentes, ou si une version publiée dégrade une étape de plus de la moitié de l'écart de la version précédente | [Proposition] ; à recalibrer |
| Alerte de confiance | Toute hausse **inhabituelle** des arrêts de partage, des suppressions de compte ou des retours « malaise sur la localisation » : examen **prioritaire** (le plan de lancement le prévoit déjà) | [Confirmé] `launch-plan.md` §4 phase 2 |
| Alerte de panne | Part de plantages (Crashlytics) en hausse sur une nouvelle version | [Proposition] |

**Taille d'échantillon** : avec quelques dizaines de testeurs, aucune différence de taux n'est statistiquement concluante ; lire les chiffres comme des **indices** et les croiser avec les retours qualitatifs (appels courts, messages).

---

## 6. Hypothèses d'optimisation priorisées et feuille de route

### 6.1 Possibilités d'expérimentation (A/B) : ce qui est vrai, ce qui reste à vérifier

| Question | Réponse | Niveau de preuve |
|---|---|---|
| Firebase A/B Testing est-il gratuit ? | Il figure dans la liste des produits « sans coût » de la page tarifaire Firebase. | [Repère externe] `firebase.google.com/pricing` (lu le 2026-11-19) |
| Faut-il Remote Config ? | Les expériences se basent sur **Remote Config** ou sur des messages de notification ; l'objectif se définit par un **événement Analytics** ou un entonnoir. | [Repère externe] `firebase.google.com/docs/ab-testing/abtest-config` |
| Remote Config est-il gratuit ? | Gratuit jusqu'à **100 000 récupérations par jour** par projet ; nouvelle grille tarifaire à partir du **1er septembre 2026**, avec délais de grâce pour les projets existants (dates différentes selon les pages ; **à vérifier dans la console**). Les fonctions A/B restent incluses. | [Repère externe non vérifié en détail] `firebase.google.com/docs/remote-config/pricing` |
| Remote Config est-il dans l'app ? | **Non** : aucune intégration dans le code Android ni iOS ; un test iOS interdit même son usage dans les sources du paywall. Il faudrait **l'ajouter** (dépendance, mention dans les déclarations, tests). | [Confirmé code] `ios/MiffTests/Paywall/PaywallSourcesTests.swift` l.116 |
| A/B Testing avec la collecte « éteinte au démarrage » ? | La doc indique qu'Analytics doit être actif côté client **au moment du premier démarrage** pour être inclus. Miff coupe la collecte jusqu'au consentement : les **nouveaux** utilisateurs risquent de ne pas être inclus pour les écrans d'avant l'inscription ; une expérience sur des écrans **après** le consentement serait plus plausible. **À vérifier par un essai réel** avant de planifier. | [Hypothèse] à partir de la doc officielle (recherche du 2026-11-19) |
| Les décisions de **compilation** (localisation bloquante, fenêtre d'avis, alerte) sont-elles testables en A/B ? | **Non** : ce sont des interrupteurs de **build**. On compare par **cohortes de builds** (deux groupes de testeurs, deux versions) : pas aléatoire, à interpréter prudemment. | [Confirmé code] `build.gradle.kts` |
| Pendant la bêta, le meilleur outil ? | Cercles de test **suivis à la main** (carnet de bêta : date d'installation, d'entrée dans la Miff, du premier signal vu, retour à J7, remarques), complétés par les événements. | [Proposition] |

### 6.2 Hypothèses priorisées (score ICE)

**Méthode** : ICE = Impact (1-10) × Confiance (1-10) × Facilité (1-10). **Tous les scores sont des jugements de l'auteur, sans donnée** ; à discuter. L'effort est relatif (S, M, L), le risque qualifie les effets de bord (confiance, store, coût).

| Rang | ID | Hypothèse (« si … alors … ») | I | C | F | **ICE** | Effort | Risque |
|---|---|---|---|---|---|---|---|---|
| 1 | **H2** | **Instrumenter** l'activation et le « aha » (E-01 à E-07, 5.3) + comptages serveur : alors toutes les autres hypothèses deviennent mesurables | 9 | 9 | 7 | **567** | S à M | Faible ; élargit les déclarations des stores |
| 2 | **H1** | **Message de partage expliqué + lien avec code en fragment + page d'arrivée** : alors davantage d'invités terminent l'installation et rejoignent | 8 | 6 | 8 | **384** | S (message) à M (page + lien) | Faible ; le code reste un secret (fragment `#`, jamais envoyé au serveur) |
| 3 | **H3** | **Premier lieu guidé** après « Ta Miff est prête » et **première nouvelle guidée** : alors plus de Miffs reçoivent un premier signal rapidement | 8 | 6 | 6 | **288** | M | Faible ; ne pas inciter à partager un lieu à la place du créateur (seul le créateur crée) |
| 4 | **H11** | **Aide « mes arrivées sont en retard » / OPPO** visible dès la première semaine : alors moins de désinstallations après un « aha » manqué | 6 | 6 | 7 | **252** | S à M | Faible ; renvoi vers les réglages de batterie génériques (pas d'exemption directe) |
| 5 | **H14** | **Demande d'avis conforme** (API officielle du store, sans question préalable, après un moment réussi) : alors moins de risque de rejet et des avis plus fiables | 5 | 7 | 7 | **245** | M | Réduit un risque de rejet ; **contredit une décision du PM** (fenêtre « J'adore ») : à arbitrer |
| 6 | **H8** | **Achat réel + essai 7 j + PREM-FIN** : alors un revenu devient possible et mesurable | 10 | 8 | 3 | **240** | L | Moyen : revue des stores, textes d'abonnement, médiateur de la consommation (risque J13) |
| 7 | **H4** | **Amorce de localisation avec aperçu « ce que voient tes proches »** (et plan B si Apple refuse) : alors plus de personnes franchissent la porte | 9 | 5 | 5 | **225** | M | Moyen ; ne résout pas un refus de principe ; le plan B iOS rend les textes légaux inexacts pour iOS |
| 8 | **H6** | **Amorce de notifications après la première valeur** (premier signal vu) : alors plus d'octrois | 6 | 5 | 7 | **210** | S | Faible ; Android 13+ et iOS : une fenêtre système unique |
| 9 | **H10** | **Relance douce d'une Miff restée à 1 membre** (notification locale de l'émetteur, une seule fois, facultative) : alors plus de cercles complets | 6 | 4 | 7 | **168** | S | Moyen : ton (jamais culpabilisant), consentement aux notifications |
| 10 | **H12** | **Expériences de fiche de store** après publication : alors meilleure conversion fiche → installation | 5 | 4 | 8 | **160** | S | Faible ; dépend du volume de vues de fiche |
| 11 | **H13** | **Traductions ES, IT, PT, DE** de la fiche et de la page d'invitation d'abord : alors plus d'installations dans ces marchés | 6 | 5 | 5 | **150** | M | Risque assumé : pas de relecture native |
| 12 | **H9** | **Paywall contextuel pour le créateur** quand un membre demande une zone de danger : alors meilleure conversion que le paywall générique | 5 | 4 | 6 | **120** | S | Faible |
| 13 | **H7** | **Téléphone différé** (demandé au moment du premier « Appeler / Message ») : alors moins d'abandons à l'onboarding | 6 | 4 | 4 | **96** | M à L | **Contredit une décision du PM** (Q1) ; règles Firestore actuelles exigent le téléphone (route `PHONE_ONLY`) |
| 14 | **H5** | **Push d'arrivée/départ au cercle, en résumé discret** : alors la valeur « tirée » devient « poussée » et la rétention de valeur progresse | 9 | 5 | 2 | **90** | L | Moyen : tension « sans déranger », coût de fonctions, consentement par proche, mise à jour légale et fiches des stores |
| 15 | **H15** | **Croix du paywall à 0 s au lieu de 3 s** : alors moins d'avis négatifs et de risque de rejet, avec un taux de conversion moindre | 4 | 3 | 6 | **72** | S | **Contredit une décision du PM** ; ne tester qu'avec des données d'achat réelles |

**Lecture** : H5 (push d'arrivée) a un ICE faible à cause de l'effort, mais c'est **le pari stratégique** n° 1 pour la rétention ; il est présenté séparément ci-dessous. H14 et H15 sont des **arbitrages de risque**, pas des optimisations de croissance : le PM a déjà tranché.

### 6.3 Expériences à mener (protocole)

| Expérience | Hypothèse | Méthode | Mesure principale | Contre-mesure de confiance | Critère de décision |
|---|---|---|---|---|---|
| **EXP-1 Instrumentation** | H2 | Lancer le contrat v2 en bêta | Couverture : part de testeurs dont l'entonnoir est lisible de bout en bout | Aucune hausse des coupures de statistiques | Entonnoir lisible sur ≥ 90 % des testeurs consentants (**proposition de critère**, à ajuster) |
| **EXP-2 Message d'invitation** | H1 | Alterner **deux textes** par cohorte de cercles (A : actuel ; B : explicatif), puis lien à fragment | Rejoints par invitation partagée ; délai installation → rejoint | Avis qualitatifs sur la compréhension | Choisir B si les testeurs le comprennent mieux **et** s'il ne génère pas de plainte de « spam » |
| **EXP-3 Porte des permissions** | H4 | Comparer deux **builds** (actuel / amorce enrichie) sur deux cohortes de testeurs | `miff_joined` → `feed_first_view` | Retours « malaise » ; arrêts de partage | Décision du PM ; plan B Apple prêt en parallèle |
| **EXP-4 Premier signal** | H3 | Ajouter l'étape guidée au parcours du créateur ; comparer avant / après | Délai Miff créée → premier `circle_signal_seen` ; Miffs vivantes à J7 | Aucun lieu créé sans le choix explicite du créateur | Maintenir si le délai diminue sans hausse des suppressions |
| **EXP-5 Primer de notifications** | H6 | A/B via Remote Config **si** l'essai de 6.1 est concluant ; sinon cohortes de builds | `notif_primer_result` = `allowed` | Pas de fenêtre répétée (Q2 décidée) | Choisir la meilleure variante, pas de relance |
| **EXP-6 Paywall** (après achat réel) | H8, H9 | Variantes de **texte** et de **formule mise en avant** ; jamais de prix inventé ni de délai de croix | `paywall_cta_tapped` / `paywall_shown` ; essais démarrés | Notes de store, avis mentionnant le paywall | Seuil à fixer avec les premières données ; stop si avis négatifs |
| **EXP-7 Push d'arrivée (pari)** | H5 | Pilote **fermé** sur quelques cercles volontaires, en résumé quotidien ou regroupé | Rétention de valeur S1/S2/S4 des cercles pilotes ; retours de ton | Aucune sensation de surveillance : interview | Décision du PM ; mise à jour légale **avant** activation |

### 6.4 Feuille de route priorisée

**Rapide (moins d'une semaine)** : surtout des décisions et de la mise en place, très peu de code.

| Action | Effort | Impact | Qui |
|---|---|---|---|
| Valider la liste d'événements v2 (5.3) : au minimum E-01, E-03, E-05, E-06 (+ E-02) | S | Très élevé (débloque la mesure) | PM, puis `product-specs` |
| Vérifier à l'écran les réglages GA4 : rétention 14 mois, « réinitialiser » désactivé, **événements clés** marqués (`sign_up_completed`, `onboarding_completed`, `miff_created`, `miff_joined`) | S | Moyen | PM |
| Tester en **DebugView** que `first_open` et la rétention GA4 se comportent comme prévu avec la collecte « éteinte au départ » (5.2) | S | Moyen | PM + dev |
| **Trancher le point n° 10** : phrase d'explication dans le message de partage (FR et EN) | S | Moyen à élevé | PM |
| **Carnet de bêta** : tableur à remplir à la main pour chaque cercle de test (installation, entrée dans la Miff, premier signal vu, retour J7, remarque) + 5 questions d'entretien (confiance, clarté de la localisation, ressenti du « Toujours ») | S | Élevé | PM |
| Préparer le script lecture seule des comptages serveur (5.4), lancé par le PM sur demande | S | Élevé | `backend-data` |
| Décider de mesurer ou non les visites de la page d'accueil et d'invitation (mesure sans cookie ou aucune) | S | Moyen | PM |

**Un mois**

| Action | Effort | Impact |
|---|---|---|
| Implémenter les événements validés sur Android et iOS (contrat, code, tests identiques, déclarations stores à jour) | M | Très élevé |
| Lien d'invitation avec code en **fragment** + page d'arrivée (explication, « Copier le code », boutons de store le jour J) | M | Élevé |
| Étape guidée « premier lieu » après la création de la Miff ; invite à la **première nouvelle** | M | Élevé |
| Déplacer l'amorce de notifications après la première valeur (variante, à mesurer) | S | Moyen |
| Essai d'**A/B Testing + Remote Config** sur un écran **après consentement** (valider la faisabilité, 6.1) | M | Moyen |
| Page d'aide « arrivées en retard » / OPPO accessible dès la première semaine | S à M | Moyen |
| Premiers **dashboards 1, 2, 5 et 6** | S | Élevé |

**Trois mois**

| Action | Effort | Impact |
|---|---|---|
| **Achat réel** (SRV-7, SRV-8A, SRV-8I) **et PREM-FIN** (30 jours de mise en veille) avant toute ouverture publique des achats | L | Très élevé (revenu) |
| **Push d'arrivée/départ** en résumé discret, pilote fermé puis décision (H5 / EXP-7) | L | Très élevé (rétention) |
| Expériences de fiche de store ; mesure d'acquisition réelle après publication | S | Moyen |
| Traductions ES, IT, PT, DE : fiches et page d'invitation d'abord | M | Moyen |
| Décision de fond sur la demande d'avis (API officielle) avant la soumission aux stores | M | Réduit un risque de rejet |
| Premiers **seuils chiffrés** d'alerte, fixés d'après les données de bêta | S | Élevé |

---

## 7. Risques et garde-fous

| # | Risque | Effet | Garde-fou proposé |
|---|---|---|---|
| R1 | **Localisation « Toujours » obligatoire** (Apple 5.1.2(i), Google « stalkerware ») | Rejet de l'app ; perte de l'entonnoir iOS | Plan B documenté (`app-store-confidentialite-et-revue.md` §6) ; interrupteur de compilation ; **jamais** d'événement sur l'octroi ; proxy `feed_first_view` |
| R2 | **Consentement groupé et préactivé** (CGU + statistiques) | Non-conformité RGPD probable (J21) ; refus de store ; biais de mesure | Risque assumé par le PM ; mesurer la part des comptes qui coupent les statistiques ; plan : revenir à deux cases sans changement de schéma |
| R3 | **Mesure qui frôle la surveillance** | Perte de confiance et de réputation | Pas d'événement sur les lieux ; pas de position ; pas d'identifiant ; agrégats serveur seulement ; revue du PM à chaque événement |
| R4 | **Dark patterns** : croix du paywall à 3 s ; amorce de notifications ; relances | Avis négatifs, rejet de revue | Aucune relance automatique ; textes calmes ; suivre les avis ; plan de retour à 0 s documenté (une seule constante) |
| R5 | **Fenêtre d'avis filtrante** (Apple 5.6.1 / 5.6.3) | Rejet, sanction du compte développeur **partagé avec Ghostrun** | Build de diffusion sans la fenêtre sans reconfirmation du PM ; plan de repli : API officielle |
| R6 | **Mineurs** : aucun âge minimum ; téléphone d'un mineur lisible de ses co-membres | Risque RGPD art. 8, store | Public cible 13+ (recommandation) ; jamais de communication vers les enfants |
| R7 | **Petit échantillon** : quelques dizaines de testeurs | Faux enseignements | Lire en indices ; croiser avec les entretiens ; ne pas décider un choix irréversible sur un seul chiffre |
| R8 | **Biais d'échantillon** : seuls les consentants sont mesurés | Surestimer la rétention | Suivre la part de consentants ; comparer avec les comptes de la console Authentication |
| R9 | **Fonctions et coûts** : push et centre de notifications serveur augmentent les coûts | Facture inattendue | Alertes de budget Blaze, `maxInstances`, App Check, `surveillance-quotas.md` |
| R10 | **Textes légaux** : chaque événement ou comptage ajouté doit être décrit | Texte inexact | Mise à jour de la politique, de Data safety et d'App Privacy **avant** la mise en production |
| R11 | **Fuite de code d'invitation** | Intrus qui lit les numéros | Lien à fragment ; code renouvelé à chaque retrait ; envisager l'approbation du créateur (déjà notée R7 du ticket Onboarding) |
| R12 | **Tests qui contournent le ton** : expérience de wording alarmiste « pour convertir » | Contraire au positionnement | Toute variante de texte passe par le PM ; interdits listés dans `positioning.md` |
| R13 | **Annonces avant livraison** : annoncer le push d'arrivée ou Miff+ avant qu'ils existent | Mensonge involontaire, rejet | Fiches et landing alignées sur ce qui est livré (`launch-plan.md` §1) |

---

## 8. Questions ouvertes pour le PM

1. **Événements** : validez-vous la liste E-01 à E-12 (ou un sous-ensemble) ? En particulier E-04 (notifications), E-06 (signal vu), E-11 et E-12 (arrêt de partage, suppression de compte) : acceptez-vous leur sensibilité ?
2. **Comptages serveur** : acceptez-vous des comptages agrégés (taille des Miffs, Miffs vivantes, abonnements) via un script lancé par vous à la demande, ou une fonction planifiée ? Si automatisé : mise à jour de la politique à prévoir.
3. **Mesure du site** : voulez-vous mesurer les visites de la page d'accueil et d'invitation (mesure sans cookie ou aucune) ?
4. **Message de partage** (point n° 10) : validez-vous la phrase d'explication proposée par `release-manager` ? Souhaitez-vous le lien à code en fragment (page d'arrivée) ?
5. **Porte des permissions** : voulez-vous préparer dès maintenant l'amorce enrichie (aperçu de ce que voient les proches) et un plan B iOS prêt à déployer si Apple refuse ?
6. **Téléphone obligatoire** (Q1) : acceptez-vous de **mesurer d'abord** la fuite à cette étape (E-01) avant de reconsidérer ? Ou de le différer dès maintenant ?
7. **Étape guidée « premier lieu »** après « Ta Miff est prête » : l'ajoutez-vous au parcours du créateur ?
8. **Push d'arrivée** (phase P2, B3) : le lancez-vous en **pilote fermé** après la bêta ? Quelle forme (résumé discret, regroupé, par proche) respecte le mieux « sans déranger » ?
9. **Fenêtre d'avis** : maintenez-vous la décision du 2026-11-07, ou la remplacez-vous par l'API officielle avant la soumission ?
10. **A/B** : acceptez-vous d'ajouter **Remote Config** (une dépendance, une mention dans les déclarations) pour pouvoir tester, après vérification de la faisabilité ?
11. **Offre** : souhaitez-vous étudier un « Miff+ pour le cercle » (un abonnement profitant à tous) comme pari produit ultérieur ?
12. **Objectifs chiffrés** : à quel moment fixez-vous les seuils (fin de la bêta externe ?) et qui valide (vous seul) ?

---

## Annexe A : sources et repères externes

**Documents du dépôt (tous sur `origin/main` au commit `a47bfcbc`)**

- `CLAUDE.md` ; `docs/specs/*.md` (notamment `paywall-premium.md`, `localisation-lieux-de-confiance.md`, `update-et-escalade-statut.md`, `notifications.md`).
- `docs/tickets/` : `onboarding.md`, `onboarding-decisions-pm.md`, `authentification.md`, `miff.md`, `lieux-de-confiance.md`, `feed.md`, `update.md`, `notifications.md`, `notification-update-membres.md`, `notifications-p1-derniere-visite.md`, `centre-notifications-complet.md`, `alerte-danger.md`, `mode-voyage.md`, `profil-repartition-temps.md`, `paywall-v2.md`, `premium-achat-reel.md`, `prem-fin.md`, `pop-ins-avis.md`, `demande-ajout-lieu.md`, `lieu-danger-demande-gratuite.md`, `compte-a-rebours-orange.md`, `session-appareil-unique.md`, `traduction-es-it-pt-de.md`.
- `docs/contracts/analytics-events.md` ; `docs/legal/README.md` ; `docs/release/decisions-stores-avant-soumission.md` ; `docs/dev/surveillance-quotas.md`, `docs/dev/premium-manuel.md`.
- `marketing/` : `positioning.md`, `launch-plan.md`, `aso-keywords.md`, `store-listing-ios.md`, `store-listing-android.md`.
- Code : `android/app/src/main/java/com/tomessa/miff/domain/analytics/`, `domain/permission/PermissionGate.kt`, `domain/paywall/`, `ui/miff/MiffSharing.kt`, `android/app/build.gradle.kts`, `AndroidManifest.xml` ; `ios/Miff/Models/Analytics/AnalyticsEvent.swift`, `ios/Miff/MiffApp.swift`, `ios/Miff/Models/Permission/LocationBlockingSwitch.swift`.

**Sources web consultées le 2026-11-19 (repères externes)**

| Sujet | Source | Réserve |
|---|---|---|
| Firebase A/B Testing : fondée sur Remote Config ou messages, objectifs par événement Analytics ou entonnoir | https://firebase.google.com/docs/ab-testing/abtest-config | La page ne dit pas comment les utilisateurs sans consentement sont traités |
| A/B Testing dans la liste « sans coût » | https://firebase.google.com/pricing | Lu via un résumé automatique ; à confirmer en console |
| Remote Config : gratuit jusqu'à 100 000 récupérations/jour, nouvelle tarification à partir du 1er septembre 2026 | https://firebase.google.com/docs/remote-config/pricing | Dates de grâce des projets existants **divergentes** selon les pages : à vérifier en console |
| GA4 : conservation des données d'événements 2 ou 14 mois (propriété standard) ; n'affecte pas les rapports agrégés standard, seulement explorations et entonnoirs | https://support.google.com/analytics/answer/7667196 | Le défaut d'une nouvelle propriété n'y est pas indiqué |
| RevenueCat, State of Subscription Apps 2026 : médiane download-to-paid à J35 de **2,1 %** pour les apps freemium (contre 10,7 % pour les « hard paywalls ») | https://www.revenuecat.com/state-of-subscription-apps | **Repère externe non vérifié** : lu via résumé de recherche, source primaire non ouverte ; apps d'abonnement en général, pas des apps de famille ; définitions différentes de notre entonnoir ; **ne pas utiliser comme objectif** |
| Rétention d'applications mobiles : valeurs souvent citées autour de 26 % à J1, 13 % à J7, 7 % à J30 (Adjust 2024, cité par des tiers) ; autres sources plus basses ou plus hautes selon plateforme et méthode | https://demo-1.adjust.com/blog/what-makes-a-good-retention-rate/ ; synthèse de recherche | **Repère externe non vérifié** ; définitions et échantillons différents ; **ne pas utiliser comme seuil** |
| Life360 : 95,8 millions d'utilisateurs actifs mensuels et 2,8 millions de « cercles payants » au T4 2025 | https://investors.life360.com/news-releases/news-release-details/life360-reports-record-q4-2025-results | Publication de l'éditeur ; le rapport « cercles payants / utilisateurs » (≈ 2,9 %) est **un calcul de l'auteur**, **non comparable** (une unité est un cercle, l'autre un utilisateur) ; ne pas l'utiliser comme taux de conversion |
| Firebase Dynamic Links (liens différés) | Non consulté dans cette session | **Repère externe non vérifié** : à ma connaissance le service a été arrêté par Google en 2025 ; à confirmer avant toute piste de lien différé |
| Product Page Optimization (Apple) et expériences de fiche (Google Play) | Non consultées dans cette session | **Repère externe non vérifié** : à confirmer dans les consoles |

**Aucun chiffre de ce rapport n'est une donnée d'usage de Miff** : il n'en existe pas encore.

---

## Annexe B : tableau des écrans du parcours (reconstitué)

Légende « Événement » : événement Analytics envoyé aujourd'hui (oui, non, proposé). Les frictions sont des **[Hypothèse]** sauf mention contraire.

| # | Écran / étape | Source (spec ou code) | Événement | Friction ou point d'attention |
|---|---|---|---|---|
| 1 | Fiche du store | `marketing/store-listing-*.md` | Consoles seulement | Non publié ; aucun volume de mots-clés |
| 2 | Installation puis splash (~1,8 s) | `onboarding.md` §5.1 | Aucun | Animation à chaque démarrage à froid |
| 3 | Carrousel de 3 slides, « Commencer », « J'ai déjà un compte » | `onboarding.md` §6.1 ; `WelcomeCarousel.kt` | **Aucun** (`onboarding_cta_tapped` jamais envoyé) | Collecte éteinte : invisible |
| 4 | Inscription / connexion (e-mail, Google ; Apple sur iOS) + ligne de consentement | `authentification.md` §3, §8 | `sign_up_completed` / `sign_in_completed` | Consentement groupé préactivé (R2) |
| 5 | Panneau de consentement (nouvel utilisateur Google/Apple) | `authentification.md` §7.8 | idem | « Continuer » / « Annuler » |
| 6 | Profil : photo facultative, prénom, nom | `onboarding.md` §6.3 | Proposé : `profile_completed` (E-01) à la fin du téléphone | Pas de flèche retour ; aucune échappatoire (Q14) |
| 7 | Téléphone : pays + numéro **obligatoire**, non vérifié | `onboarding.md` §6.4 | idem | Donnée de contact exigée avant toute valeur (B2) |
| 8 | Choix « Créer / Rejoindre » | `onboarding.md` §6.5 | Aucun | Réglages restreints via la roue crantée |
| 9a | Créer : nom, « Créer et partager » | `miff.md` §3.1 | `miff_created` | Texte de partage de statut avant le geste |
| 9b | Rejoindre : saisie du code de 12 caractères | `miff.md` §5.3 | Aucun | Retaper un code (B4) ; pause après 5 échecs |
| 10 | Confirmation « Rejoindre et partager » | `miff.md` §5.4 | `miff_joined` ; `onboarding_completed` (première Miff) | Consentement au partage versionné |
| 11 | « Ta Miff est prête » : code + Partager / Copier | `miff.md` §3.1 | Proposé : `invite_shared` (E-02) | Aucune étape guidée « premier lieu » |
| 12 | Amorce de **notifications** (Android 13+, iOS) | `PermissionGate.kt` ; `onboarding.md` §6.6 | Proposé : `notif_primer_result` (E-04) | Demandée avant la localisation, avant la première valeur (B6) |
| 13 | Renouvellement du consentement (si texte plus ancien) | `PermissionGate.kt` | Aucun | Revient à chaque lancement si repoussé |
| 14 | Amorce de **localisation** bloquante + fenêtre système ; Android 11+ : réglages « Toujours » + précise | `PermissionGate.kt` ; `lieux-de-confiance.md` §6.5 | Aucun (voulu) | **Porte principale** (B1) ; sortie = « Se déconnecter » |
| 15 | Feed : bulle de couleur, cartes, « Personne d'autre dans cette Miff pour l'instant » si seul | `feed.md` ; `FeedBubble.kt` | Proposé : `feed_first_view` (E-03), `circle_signal_seen` (E-06) | « Pas encore de nouvelles » tant qu'aucun lieu ni déplacement |
| 16 | Création de lieux (créateur seul) ; au-delà de 3 lieux, zone de danger, 2e Miff : paywall | `lieux-de-confiance.md` ; `PlaceNavigation.kt` | `paywall_shown` / `paywall_dismissed` | Paywall « arrive bientôt » (pas d'achat) ; croix après 3 s |
| 17 | Update photo (« Donner des nouvelles ») ; maintien 5 s : alerte | `update.md` ; `alerte-danger.md` | Proposé : `first_news_shared`, `news_shared` | Seul « aha » instantané ; photo toujours localisée |
| 18 | Notifications : push « a donné des nouvelles » ; rappels locaux ; centre ; « Depuis ta dernière visite » | `notification-update-membres.md` ; `notifications-p1-derniere-visite.md` | Aucun | Pas de push d'arrivée (B3 non livré) |
| 19 | Profil : Miffs, répartition du temps (après 7 jours), mode voyage, compte à rebours | `profil-repartition-temps.md` ; `mode-voyage.md` | Aucun (voulu : rien de ce type n'est envoyé) | Valeur de rétention locale |
| 20 | Fenêtre d'avis (build de test ; absente du build de diffusion par défaut) | `pop-ins-avis.md` | Aucun (voulu) | Risque store (E1) |
| 21 | Réglages : arrêt du partage, statistiques (interrupteur), suppression de compte | `onboarding.md` §6.9 ; `suppression-compte.md` | Proposé : `sharing_stopped`, `account_deletion_started` | Indicateurs de confiance |

---

## Annexe C : règles de nommage des événements proposés

- Verbes au participe passé, anglais, minuscules séparées par `_`, **sans** aucun des mots interdits du contrat (`lat`, `lng`, `place`, `lieu`, `location`, `loc`, `position`, `geo`, `geofence`, `gps`, `region`, `zone`, `city`, `distance`, `nearby`, etc.).
- Paramètres : listes fermées de valeurs (`string` ≤ 100 caractères, `int`, `bool`) ; jamais de texte libre.
- Une PR sur `docs/contracts/analytics-events.md` **et** sur le code des deux plateformes, avec raison produit en une phrase et confirmation qu'aucune position n'est transmise ; validation du PM ; mise à jour des déclarations des stores et de la politique de confidentialité.
