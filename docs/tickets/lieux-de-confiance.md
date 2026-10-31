# Ticket — Lieux de confiance et détection automatique (géofencing) : le cœur du produit

**Statut** : brouillon issu de l'étape « challenge » ; **à valider par le PM avant tout développement** (aucun développement lancé, aucun statut Notion modifié, aucun fichier du dépôt autre que ce ticket modifié).
**Rédigé le** : 2026-10-31 (agent `product-specs`, étape « challenge » du process par ticket).
**Ce ticket fusionne** les specs `docs/specs/lieux-de-confiance.md` (gestion des lieux) et `docs/specs/localisation-lieux-de-confiance.md` (détection), qui restent « Backlog discovery » et deviennent redondantes une fois ce ticket validé (à clore ou à lier par le PM). Il **complète** `docs/tickets/miff.md` (consentement, statut initial), `docs/tickets/onboarding.md` (§6.6, amorce de permission, ONB-4 et ONB-8) et `docs/tickets/feed-profil-reglages.md` (affichage des statuts, décisions du 2026-10-30), sans les remplacer.
**Sources lues** : `CLAUDE.md` ; `docs/specs/` (lieux-de-confiance, localisation-lieux-de-confiance, update-et-escalade-statut, notifications, feed-cercle-de-confiance, miff-groupes, paywall-premium) ; `docs/tickets/` (miff, miff-questions-pm, onboarding, feed-profil-reglages, initialisation-technique §9) ; `docs/legal/` (README, politique de confidentialité FR) ; `docs/contracts/` (analytics-events, i18n-keys) ; `backend/data-model.md` en entier et `backend/firestore.rules` (fonctions `validPlaceShape`, `validPlaceCreate`, `validPlaceUpdate`, `validStatus`, `validSharingChange`, règles `places` et `status`) ; `design/` (design-system, figma-audit, screens Miff, exports `feed`, `profil`, `onboarding-original` écrans 13, 14, 19 à 22, et `design/exports/miff/` du dépôt principal, non suivi par git) ; manifeste Android et configuration iOS (aucune permission ni clé de localisation à ce jour).
**Sources web consultées le 2026-10-31** : documentation Android « Geofencing » et « Location permissions » ; guide Apple « Location and Maps Programming Guide : Region Monitoring » (archive) ; aide Google Play « Background location access » ; tarifs Google Maps Platform (page du 2026-10-30) ; politiques OpenStreetMap (tuiles et Nominatim) ; OpenFreeMap ; dontkillmyapp.com (OPPO). Les pages Apple récentes (`developer.apple.com/documentation`) n'étaient pas lisibles par l'outil : tout point iOS non confirmé est marqué **[À vérifier sur l'iPhone]**.
**Limites de cette lecture** : Notion et Figma n'ont pas été relus (outils non utilisés) ; les 8 écrans de la page Figma « Lieux de confiance » du fichier original n'ont **jamais été exportés** : l'écran « Nouveau lieu » n'est connu que par les descriptions de `docs/specs/lieux-de-confiance.md` (§17). Aucune commande Firebase n'a été exécutée ; aucune règle n'a été modifiée.
**Convention** : [Décidé] = décision du PM déjà écrite ailleurs (source citée) ; [Proposé] = ma proposition, à valider ; [À vérifier] = fait de plateforme non confirmé ; « vérifié » = lu dans le dépôt ou dans une source officielle citée.

---

## 1. Résumé pour le PM

En une phrase : permettre au créateur d'une Miff de **poser ses lieux sur une carte en quelques gestes**, puis **signaler automatiquement à son cercle, sans ouvrir l'app, qu'un proche est arrivé ou parti d'un de ces lieux**, en n'écrivant jamais autre chose qu'un statut (« dans le lieu X », « hors lieu ») et une heure, jamais une position.

Le moteur de détection existe dans chaque téléphone (zones surveillées par iOS, API de géofencing d'Android) : **0 € d'infrastructure, aucune Cloud Function, aucune notification**. Ce qui est difficile n'est pas de l'écrire, c'est de le rendre **fiable, honnête et consenti**. Le challenge relève 14 points qui changent le périmètre ou exigent une décision :

| # | Constat | Pourquoi c'est important | Voir |
|---|---|---|---|
| 1 | **Un spike de fiabilité sur l'OPPO doit passer avant le développement de la détection.** Le fabricant (ColorOS) peut empêcher l'app de recevoir les événements ; aucun développeur ne peut le contourner (« no known solution on the dev end », dontkillmyapp.com). | Si la détection rate un départ sur trois, le produit rassure à tort : c'est pire qu'une absence de détection. | §12, QL1, QL2 |
| 2 | **Le texte de consentement actuel est périmé** : il ne dit ni que les **lieux (adresses, carte) sont visibles de tous**, ni que le **niveau de batterie** est partagé (décision Q14 du 2026-10-30), ni que **la détection a lieu app fermée**, et « Ton téléphone te demandera séparément… » devient faux dès que l'amorce d'arrière-plan s'enchaîne. | Consentement mal éclairé = motif de rejet des stores et risque de confiance. Nouvelle version obligatoire. | §5.12, §10, QL3 |
| 3 | **Les règles Firestore ne permettent pas de renouveler un consentement actif** : le partage ne passe que de « arrêté » à « activé » ou l'inverse (`validSharingChange`). Un membre déjà entré avec l'ancien texte ne peut donc pas « accepter la nouvelle version » sans **arrêter puis reprendre** (ce qui supprime son statut). | La politique de confidentialité promet (art. 12) de redemander le consentement si le texte change. | §8, QL3, QL12 |
| 4 | **Les règles refusent toute réécriture d'un statut dont `presenceAt` ou `lastUpdateAt` a plus de 24 h** (`validStatus`). Une personne chez elle depuis deux jours ne pourra donc ni publier une mise à jour manuelle, ni rafraîchir son état d'autorisation, sans **mentir sur l'heure d'arrivée**. | Bloque aussi le futur ticket Update. À corriger par une petite évolution de règle **avant** le ticket Update. | §8.3, QL12 |
| 5 | **Les lieux se propagent mal sans notification** : quand le créateur ajoute, déplace ou supprime un lieu, le téléphone d'un proche ne l'apprend qu'à l'ouverture de l'app ou lors d'une relecture périodique en arrière-plan (au mieux toutes les quelques heures). | Un lieu ajouté le matin peut ne pas être surveillé chez un proche avant le soir. Honnêteté à écrire dans l'app. | §5.5, QL5 |
| 6 | **Le créateur choisit les lieux surveillés pour tout le monde, même après le consentement.** Un lieu ajouté plus tard est surveillé chez chaque membre **sans nouveau consentement**. | Vecteur d'abus (surveillance détournée) : sensible pour les règles anti-stalkerware des stores. | §5.9, QL4, R6 |
| 7 | **Les adresses et coordonnées des lieux sont lisibles de tout porteur du code** (règles `places`) : un code qui fuite révèle, en plus des numéros de téléphone, l'adresse du domicile du créateur. | Risque non encore écrit dans les tickets Miff et Onboarding. | §5.9, R7, QL6 |
| 8 | **Le repli « mise à jour manuelle + escalade automatique » promis par la spec n'existe pas en l'état** : l'escalade ne démarre qu'après un départ détecté ; sans autorisation « toujours », il n'y en a jamais, donc jamais d'escalade (`unknown` = neutre, `data-model.md` §3). | Contradiction de spec à trancher (QL10). | §4 #1, QL10 |
| 9 | **Cartes et recherche d'adresse gratuites : iOS facile, Android moins.** MapKit et la recherche d'adresse d'Apple sont gratuits, sans clé. Sur Android, **le SDK Google Maps exige un projet avec facturation** (donc un moyen de paiement : décision PM) ; la recherche d'adresse du système est gratuite mais sans garantie ; Nominatim interdit la recherche à la frappe et plafonne l'app entière à 1 requête par seconde. | Proposition : MapLibre + OpenFreeMap sur Android (gratuit, sans clé, sans garantie de service). | §11, QL14 |
| 10 | **Les limites système sont réelles** : 20 zones sur iOS (guide Apple), 100 sur Android (documentation Android). Le « Premium : lieux illimités » ne peut donc pas être tenu tel quel. | Plafond doux et priorisation déterministe proposés. | §5.8, QL9 |
| 11 | **Les garde-fous du dépôt interdisent aujourd'hui toute localisation** (contrôle CI iOS `NSLocation*`, test `AppConfigurationTests`, `PrivacyInfo.xcprivacy`, commentaire du manifeste Android). Ils devront être **modifiés explicitement** par la PR concernée. | À ne pas contourner : c'est le point où le dépôt protège la promesse « aucune position ». | §7, LOC-4 |
| 12 | **Google Play exige une déclaration (« Permissions Declaration Form »), une vidéo et une divulgation dans l'app avant la demande**, et soumet **toutes les pistes de test, fermées et ouvertes**, à cette revue ; Apple revoit l'usage « Toujours ». | À préparer **avant** la bêta externe, pas avant la soumission finale. | §11, §18, R3, R4 |
| 13 | **Aucune notification n'arrive** tant que Blaze n'est pas décidé : « Thomas est bien arrivé à l'école » (carrousel, maquettes) ne peut pas être envoyé ; le proche le voit en ouvrant l'app. | Les textes, la fiche des stores et le carrousel ne doivent rien promettre d'autre. | §4 #12, R18 |
| 14 | **Aucun test iOS possible sans marcher** : pas de Mac, donc pas de simulateur de trajet ; tout passe par TestFlight sur l'iPhone 11 Pro. Android se teste en partie par position simulée. | Plan de test réaliste et journal de détection interne (builds de test uniquement). | §12 |

**Décisions déjà prises qui encadrent ce ticket [Décidé]** : zone à risque **non livrée en v1** (Q10, 2026-10-30) ; liste des lieux = **nom + catégorie**, adresse sur l'écran de détail (Q3) ; **batterie datée** et citée dans le consentement (Q14) ; « Voir le lieu » à la place de « Localiser » (Q13) ; **seul le créateur** crée, modifie, supprime les lieux (`data-model.md` §15, décision 5) ; gratuit = 1 lieu, premium « illimité » ; permissions **jamais bloquantes** et amorce **avant** la fenêtre du système (Onboarding Q2, Q3) ; **aucune coordonnée d'une personne n'est stockée** (`data-model.md` §1) ; Spark, sans Cloud Function, sans notification (décision Blaze reportée).

**Taille globale (relative, sans durée)** : XL (sept sous-tickets, §13). **Coût : 0 €** avec les choix proposés ; seuls le SDK Google Maps (QL14) et Blaze (notifications) seraient payants, et **ne sont pas retenus sans ta décision**.

**Questions pour le PM** : §14 (20 questions, chacune avec ma recommandation). Les six qui bloquent le plus : QL1 (spike d'abord), QL3 (consentement), QL4 (lieux ajoutés après coup), QL5 (propagation sans notification), QL10 (sans « Toujours »), QL12 (deux évolutions de règles).

---

## 2. Périmètre

### Dans le périmètre
- **Gérer les lieux** (créateur) : créer, modifier, supprimer ; nom, position sur une carte, adresse facultative, périmètre ; liste et détail en lecture pour tous les membres ; limite gratuite d'un lieu (blocage du deuxième) ; point d'entrée « Ajouter un lieu » depuis l'écran de la Miff et l'onglet Profil.
- **Détecter** l'arrivée et le départ des lieux sur le téléphone de chaque membre qui partage son statut, **app fermée**, et **écrire le statut** correspondant (§5.6).
- **Rester honnête sur ce qui marche ou non** : état de la détection sur mon téléphone (écran « Détection de tes lieux »), rappels non modaux, guide fabricant (ColorOS en premier), valeur `locationAccess` du statut.
- **Réarmer et réparer** : redémarrage du téléphone, mise à jour de l'app, changement d'autorisation, synchronisation des lieux, limites 20 / 100.
- **Texte de consentement** version 2 (proposition §10), amorce d'arrière-plan alignée, textes d'usage iOS, divulgation Android.
- **Spike de fiabilité** (Android sur l'OPPO, puis iOS sur l'iPhone) avant le développement complet de la détection (§12).
- **Évolutions de règles et de schéma à demander** à `backend-data` (§8.3) : décisions du PM, aucune n'est faite ici.

### Hors périmètre (propriétaire indiqué)
- **Zone à risque** : non livrée en v1 (Q10) ; le type `risk` reste dans le schéma, sans interface.
- **Notifications push** et escalade quand l'app du proche est fermée : exigent Blaze.
- **Mise à jour manuelle** (« Donner des nouvelles », photo, texte) et **Feed** (affichage des statuts, bandeaux, « Voir le lieu » du Feed) : tickets Update et FEED ; ce ticket fournit les données et propose les textes de §10.
- **Demande de la permission « arrière-plan » du système** (amorce, écrans par version d'Android, rappel non modal) : ONB-4 (Android) et ONB-8 (iOS) ; ce ticket **lit l'état** et fournit les textes révisés.
- **Premium** : achat, essai, fin d'abonnement et suppression des lieux en trop (ticket Premium) ; ce ticket expose la **suppression d'un lieu appelable sans écran**.
- Lieux **personnels** d'un membre (« mes lieux »), partage d'un lieu entre plusieurs Miffs, import, dédoublonnage entre Miffs, priorisation **par proximité** quand la limite système est dépassée (v2, §5.8), battement de cœur périodique du statut (QL13), notification permanente d'arrière-plan (plan B, QL2), carte hors connexion.

---

## 3. Parcours et états d'écran

### 3.1 Parcours nominaux

```
Créateur, première fois
  Créer une Miff (ticket Miff) ─► consentement v2 ─► amorce arrière-plan (ONB-4/8) ─► Feed
  Feed vide de lieu ─► « Ajouter un lieu » ─► [Nouveau lieu : nom · recherche d'adresse ou « Utiliser ma position » ·
        carte avec repère et cercle · périmètre · texte « Les membres verront ce lieu »]
        └─► Enregistrer (accusé du serveur) ─► « Le lieu est enregistré. Tes proches le prendront en compte… »
        └─► détection armée sur MON téléphone si l'autorisation le permet

Membre (qui a rejoint avec « Rejoindre et partager »)
  amorce arrière-plan ─► Feed ; à l'ouverture, avis discret « Nouveau lieu : … » (une fois par lieu)
  détection armée dès que l'app a relu les lieux (ouverture, relecture périodique, réveil par un événement)

Au quotidien (sans ouvrir l'app)
  le système réveille l'app à l'entrée ou à la sortie d'un lieu ─► Miff écrit status/{moi} dans chaque Miff partagée concernée
  le proche voit « Thomas est à l'école depuis 8 h 05 » en ouvrant l'app (aucune notification)

Autorisation perdue, GPS coupé, économiseur de batterie, optimisation du fabricant
  bandeau non modal ─► écran « Détection de tes lieux » (état, une action) ─► guide fabricant si besoin
```

### 3.2 Écrans et états à couvrir

| Écran | États à couvrir | Dans les maquettes ? |
|---|---|---|
| **A. Liste des lieux** (écran de la Miff, onglet Profil) | vide (créateur, membre) ; 1 à N lieux ; indicateur « surveillé sur ton téléphone » par lieu ; limite gratuite atteinte ; plafond doux (QL9) ; hors connexion (lecture du cache) | Partiel : export `profil` (« Lieux de la Miff », « Ajouter », « + Ajouter un lieu », emoji dans le nom) ; **doublon « Ajouter »** ; pas d'indicateur de surveillance |
| **B. Nouveau lieu / Modifier le lieu** | vide ; nom valide ; limite de 60 caractères ; recherche en cours ; aucun résultat ; recherche indisponible ; carte indisponible ; position choisie ; périmètre minimum et maximum ; enregistrement en cours ; hors connexion ; erreur ; avertissement de modification (« change pour tous ») | **Non exporté** (page Figma « Lieux de confiance », 8 frames, jamais exportée) ; champ « Type » à retirer |
| **C. Détail d'un lieu** (lecture) | adresse renseignée ou non ; carte ; périmètre ; « Voir sur la carte » ; actions du créateur (modifier, supprimer) | **Non dessiné** |
| **D. Confirmation de suppression** | confirmation ; en cours ; hors connexion | « Écran vu dans les maquettes » selon la spec ; **non exporté** (probablement page « Pop ins ») |
| **E. Limite gratuite** | message ; paywall provisoire ou réel (QL15) | Paywall d'origine : voir `onboarding-original/Paywall.png` ; **écran de limite non dessiné** |
| **F. Avis aux membres** (lieu ajouté, modifié, supprimé) | bandeau non modal, une fois par lieu | **Non dessiné** |
| **G. Détection de tes lieux** | 12 états (§10.4) | **Non dessiné** |
| **H. Guide fabricant** (ColorOS en premier) | étapes ; « Ouvrir les réglages » ; « Je l'ai fait » | **Non dessiné** |
| **I. Consentement v2** (cartes E « Rejoindre et partager », B « Créer et partager », reprise du partage) | texte plus long ; taille de texte maximale | Fiches `design/screens/miff-rejoindre.md` et `miff-creation.md` (texte v1) à **mettre à jour** |
| **J. Amorce arrière-plan** (ONB-4 / ONB-8) | textes révisés (§10.2) ; états déjà prévus par `onboarding.md` §3.2 | Écrans 20 à 22 de l'original : **à remplacer** (bouton unique « Toujours autoriser », erreur rouge « Miff a besoin de l'option… ») |

---

## 4. Ambiguïtés, contradictions et incohérences relevées

| # | Point | Sources en conflit | Traitement |
|---|---|---|---|
| 1 | **Repli « escalade automatique » sans autorisation** : la spec dit « fallback : mise à jour manuelle + escalade vert, orange, rouge » ; or l'escalade part d'un **départ détecté** (`out`) ; sans détection le statut reste `unknown`, **neutre, sans escalade** (hypothèse du modèle). | `localisation-lieux-de-confiance.md` §Solution UX 3 et 4 ; `data-model.md` §3 | **QL10** ; en attendant, le ticket applique le modèle (aucune escalade sans départ détecté) et le dit dans les textes. |
| 2 | **Autorisation « Toujours » obligatoire pour utiliser l'app** (Notion Onboarding, Tracking) ; écrans 20 à 22 : bouton unique, erreur rouge. | Notion / Figma vs spec localisation, Onboarding Q2, Apple 5.1.2 | **Déjà tranché** : jamais bloquant. Les écrans 20 à 22 sont remplacés. |
| 3 | **« On enregistre sa localisation à l'ouverture et à la fermeture de l'app »** (Tracking Notion, cité par `onboarding.md` §4 hors périmètre) et bouton « Localiser » (dernière position connue). | Notion vs `data-model.md` §1 (aucune coordonnée) | **Tranché par ce ticket** : aucune position n'est lue ni enregistrée à l'ouverture ou à la fermeture ; seule la détection par zones existe. « Localiser » est remplacé par « Voir le lieu » (Q13). |
| 4 | **Type « Zone de confiance / Zone à risque »** dans l'écran « Nouveau lieu ». | Spec lieux vs décision Q10 (2026-10-30) | Champ retiré ; tout lieu créé est `trusted`. |
| 5 | **Périmètre** : « curseur, ex. 100 m » (spec) ; règles : 50 à 2 000 m ; Android : 100 à 150 m minimum recommandé ; Apple : environ 200 m de marge de franchissement lors des essais. | Spec / règles / documentation | Bornes proposées : QL7. |
| 6 | **« Lieux de la Miff »** (maquette Profil) alors que le produit parle de lieux « de l'utilisateur » ; exemple « Foot de Thomas » défini par le créateur. | `exports/profil/README.md` ; décision du 2026-10-16 | Les lieux sont **ceux de la Miff**, définis par son créateur : un membre ne peut pas ajouter « son » lieu en v1. Conséquence : un enfant ne peut pas déclarer lui-même « chez ma copine ». Noté en R6 ; à rouvrir si les retours le demandent. |
| 7 | **Catégorie de lieu** : décidée le 2026-10-16 et reprise par Q3 (« nom + catégorie »), mais **absente du schéma** (`places` n'a pas de champ `category`, les règles refusent toute clé inconnue) ; la maquette montre des emojis dans le nom (« 🏠 Maison de Papa »). | `feed-profil-reglages.md` §5.1 ; `data-model.md` §2.9 | **QL8** : liste à fournir par le PM ; sans règle modifiée, l'emoji dans le nom est la seule option. |
| 8 | **Texte de consentement v1** : « Ton téléphone te demandera séparément l'accès à ta position » ; la spec Onboarding Q3 fusionne en un écran le consentement **et** l'amorce de localisation, alors que le ticket Miff écrit le consentement **au clic** de « Rejoindre et partager », **avant** l'amorce. | `miff.md` §5.4 et §5.10 ; `onboarding.md` §3.1 et §6.6 | Deux écrans à la suite : (1) consentement (écriture), (2) amorce d'arrière-plan (information et demande du système, **sans seconde écriture de consentement**). L'amorce tient lieu de **divulgation visible** exigée par Google. À confirmer par le PM (QL3). |
| 9 | **« Visible sans action et sans défilement »** (ticket Miff, carte de consentement) vs texte v2 nettement plus long. | `miff.md` §5.4, critère K1 | Le texte reste **entièrement visible et non replié** ; à la taille de texte maximale, le **défilement de l'écran** est toléré, avec le bouton ancré sous le texte (QL3). |
| 10 | **« Les lieux sont partagés » avant l'entrée** (Q3 du 2026-10-30) alors que les règles interdisent de **lire** les lieux avant d'entrer. | `feed-profil-reglages.md` §13 ; `miff.md` §5.9 | Le texte de consentement **dit** que les lieux (nom, adresse, carte) sont visibles de tous ; on ne peut pas les **montrer** avant. |
| 11 | **Premium « illimité »** (CLAUDE.md, spec) vs 20 zones (iOS) et 100 (Android). | Spec vs limites système | QL9. |
| 12 | **Notifications** : carrousel, maquettes et spec `notifications.md` donnent des exemples (« Thomas est bien arrivé à l'école ») qu'aucun téléphone ne recevra sans serveur. | Spec vs Spark | Hors périmètre ; ce ticket ne promet **aucune** notification ; `aso-marketing` et `release-manager` à prévenir (R18). |
| 13 | **« Gratuit : 1 lieu »** (règles) vs « alors qu'il en a déjà 2 » (Notion Premium). | Déjà relevé par `onboarding.md` | La règle est **1** ; message de limite au deuxième lieu. |
| 14 | **Modifier un lieu** : question ouverte de `lieux-de-confiance.md` ; les règles l'autorisent (nom, adresse, position, périmètre, priorité) ; `type` immuable. | Spec vs règles | **Oui, modifiable** [Proposé, QL16]. |
| 15 | **Guard-rails du dépôt** : `ios/ci/check-binaries.sh`, `AppConfigurationTests` et `PrivacyInfo.xcprivacy` **exigent l'absence** de toute clé `NSLocation*` ; le manifeste Android déclare « no location ». | Dépôt vs ce ticket | Ils doivent être **réécrits** (exiger exactement les clés prévues, interdire le reste) dans la PR iOS et Android concernée (§7). |
| 16 | **`locationAccess`** n'a que quatre valeurs (`background`, `foreground`, `denied`, `unknown`) ; l'état « position approximative » ou « localisation du téléphone coupée » n'existe pas. | `firestore.rules` vs `onboarding.md` §6.6 | Correspondance proposée §5.7 ; aucune règle modifiée. |

---

## 5. Règles métier

### 5.1 Qui voit et qui peut quoi

| Action | Créateur | Membre |
|---|---|---|
| Voir la liste, le détail, la carte d'un lieu (nom, adresse si renseignée, position, périmètre) | Oui | Oui, **une fois entré** (les règles ne montrent rien avant) |
| Créer, modifier, supprimer un lieu | Oui | Non |
| Être détecté dans les lieux de **toutes** les Miffs où il partage | Oui | Oui |
| Voir le statut des autres | Seulement s'il partage et a publié son propre statut (réciprocité) | Idem |

Le créateur n'a **aucun droit de lecture supplémentaire** sur la présence des membres (critère V4 du ticket Miff) : il maîtrise **les lieux**, jamais la surveillance d'une personne.

### 5.2 Le lieu (champs et bornes)

| Champ | Règle v1 [Proposé sauf mention] |
|---|---|
| Nom | 1 à 60 caractères (règles) ; saisie de 2 à 60 [Proposé, comme le nom de Miff] ; emojis acceptés ; deux lieux peuvent porter le même nom |
| Position | Repère sur une carte ; `lat`, `lng` arrondis à **5 décimales** (`data-model.md` §2.9) ; choisie par recherche d'adresse, par glisser du repère, ou par « Utiliser ma position actuelle » (une seule lecture, au premier plan, sur appui) |
| Adresse | **Facultative** (≤ 200 caractères, `null` si vide) ; jamais exigée ; **sensible** (peut être un domicile) |
| Périmètre `radiusM` | Entier ; règles : 50 à 2 000 ; **proposition : 100 à 1 000 m, défaut 150 m** (QL7) |
| Type | Toujours `trusted` en v1 (aucun champ à l'écran) |
| Catégorie | **Non dans le schéma** : QL8 |
| Priorité `priority` | `null` en v1 ; l'ordre de surveillance en cas de dépassement suit `createdAt` croissant (§5.8) |

### 5.3 Limite gratuite et premium
- **Gratuit** : **1 lieu** de confiance. Le créateur gratuit n'a qu'une Miff, donc 1 lieu en tout. Les règles n'acceptent, en gratuit, que l'identifiant `p1` avec le type `trusted` ; **le client vérifie la limite avant d'écrire** (le refus des règles n'est que le filet).
- **Premium** (`entitlements/{uid}`, accordé à la main tant que Blaze n'est pas décidé) : identifiants automatiques, plusieurs lieux.
- Un **membre gratuit** d'une Miff dont le créateur est premium reçoit **tous** les lieux (« les membres héritent ») : voulu par la spec.
- **Fin de Premium** : suppression des derniers lieux ajoutés (décision du 2026-10-24) : **ticket Premium** ; ce ticket expose la procédure « supprimer un lieu » appelable sans interface.
- Au deuxième lieu en gratuit : message calme + paywall provisoire ou réel selon QL15 (jamais d'écriture tentée).

### 5.4 Créer, modifier, supprimer
- **Créer** : une écriture, **accusé du serveur exigé** (comme les actions de Miff, `miff.md` §5.11) ; hors connexion : message neutre, **rien en file d'attente**.
- **Modifier** : nom, adresse, position, périmètre. Avant l'enregistrement, si la position ou le périmètre change, l'écran dit « Ce lieu change pour tous les membres de la Miff. ». Après modification, chaque téléphone **réarme** le lieu et **revalide** l'état (§5.6).
- **Supprimer** : confirmation calme ; une écriture ; le lieu disparaît chez le créateur tout de suite, chez les proches à leur prochaine relecture (§5.5).
- Aucune de ces actions ne demande d'autorisation de localisation (§5.7).

### 5.5 Propagation vers les proches (sans notification)
Le serveur ne peut pas prévenir un téléphone fermé (Spark). Un proche apprend un changement de lieu :
1. **en direct** si l'écran de la Miff est ouvert (écouteur) ;
2. **à l'ouverture de l'app** et au retour au premier plan (relecture) ;
3. **à chaque réveil par un événement de détection** (relecture si la dernière a plus d'une heure) [Proposé] ;
4. **par une relecture périodique en arrière-plan**, au plus toutes les 12 h [Proposé, à mesurer au spike] : fiable sur Android (tâche périodique, minimum système 15 min), **au mieux** sur iOS (le système décide).

**Conséquence assumée à écrire** : « Tes proches prendront ce lieu en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » Un lieu **supprimé** peut rester surveillé quelques heures chez un proche ; un événement détecté sur un lieu absent de la dernière relecture est **ignoré** sans erreur. Le jour où Blaze est décidé, une notification silencieuse supprime ce délai.

### 5.6 De l'événement système au statut (comportement observable)

Les valeurs reprennent `data-model.md` §3 (aucune coordonnée) :

| Événement | Écriture dans `status/{moi}` de chaque Miff partagée concernée |
|---|---|
| **Arrivée** dans un lieu | `presence = "in_trusted"`, `placeId`, `presenceAt` = heure de l'événement, `lastUpdateAt` = idem, `escalationDueAt = null` |
| **Départ** d'un lieu | `presence = "out"`, `placeId` = lieu quitté, `presenceAt` = heure de l'événement, `escalationDueAt` = +2 h (inutile sur Spark) ; `lastUpdateAt` conservé, **ou** relevé à l'heure du départ s'il a plus de 24 h (sans effet sur l'escalade, qui part de `presenceAt`) |
| Autres champs à chaque écriture | `updateText` et `hasPhoto` **conservés** (une détection n'efface pas une mise à jour manuelle) ; `batteryPct` = niveau du moment ; `locationAccess` = §5.7 ; `updatedAt` = heure serveur |

Règles de comportement :
- **Un état local des lieux « dans lesquels je suis »** sert à calculer le statut : si je quitte A en restant dans B, le statut devient `in_trusted` sur B, **pas** `out`. Arrivées et départs dans le désordre ou en double n'écrivent pas deux fois le même état.
- **État initial** : si la personne est **déjà dans** un lieu quand il est armé, c'est traité comme une arrivée. Si elle est dehors, **aucun `out` n'est inventé** : un départ n'existe que s'il y a eu un franchissement réel d'un lieu ; le statut reste `unknown` (aucune escalade sans départ connu).
- **Lieu supprimé ou modifié** : si le dernier statut écrit renvoyait à ce lieu et que la présence ne peut pas être reconfirmée, le statut passe à `unknown` (`placeId = null`) ; la prochaine arrivée le corrige.
- **Zones qui se chevauchent** : seule la zone de confiance existe en v1, pas de priorité à gérer entre types.
- **Une écriture par Miff concernée**, indépendantes (l'échec d'une n'arrête pas l'autre).
- **Pas de temporisation en v1** : l'arrivée et le départ sont écrits dès que le système les signale (QL11) ; les faux départs en bordure de zone sont acceptés et mesurés pendant la bêta.
- **Disjoncteur anti-abus et anti-coût** : au-delà d'un plafond d'écritures automatiques par jour et par appareil (valeur à fixer avec la mesure du spike), l'appareil n'écrit plus que l'état final à intervalles espacés. Pas de limitation dans les règles (la file hors ligne rejouerait des écritures rapprochées, `data-model.md` §8).
- **Aucune lecture de la position qui a déclenché l'événement** : seuls le type d'événement, le lieu et l'heure sont utilisés (§5.10).

### 5.7 Autorisations et états : ce que fait la détection et ce qui est écrit

L'amorce et les demandes du système appartiennent à ONB-4 / ONB-8. Ce ticket **lit** l'état et le traduit ainsi :

| État de l'appareil | Détection automatique | `locationAccess` écrit | Ce que voit la personne |
|---|---|---|---|
| Android : « Autoriser tout le temps » + position précise | Active | `background` | « La détection est active. » |
| Android : « Seulement pendant l'utilisation » | **Inactive** (n'est pas livrée app fermée) | `foreground` | « Il manque une autorisation » + « Ouvrir les réglages » |
| Android 12+ : position **approximative** seulement | **Inactive** (le géofencing exige la position précise, documentation Android) | `foreground` | « La position précise est désactivée » |
| Android : refusée | Inactive | `denied` | « Tu peux continuer à donner des nouvelles toi-même » |
| Android : localisation du téléphone coupée | Inactive | `denied` | « La localisation du téléphone est désactivée » |
| iOS : « Toujours » + position précise | Active | `background` | idem |
| iOS : « Toujours » + position **approximative** | **[À vérifier sur l'iPhone]** ; traité comme limité | `foreground` | « La position précise est désactivée » |
| iOS : « Lorsque l'app est active » ou « Une fois » | **Inactive** | `foreground` | « Il manque une autorisation » |
| iOS : refusée ou **restreinte** (contrôle parental) | Inactive | `denied` | idem refusée |
| iOS : actualisation en arrière-plan désactivée | **Peut ne pas réveiller l'app** (guide Apple) | selon l'autorisation | « L'actualisation en arrière-plan est désactivée » |
| Jamais demandée ou « Plus tard » | Inactive | `unknown` | « La détection automatique est désactivée » + « Activer » |

Règles : état **relu à chaque ouverture, retour au premier plan et réveil** ; un retrait d'autorisation n'est **jamais bloquant**, déclenche un bandeau non modal et une mise à jour de `locationAccess` (sous réserve de QL12) ; la correspondance ci-dessus est une **proposition** (la liste de quatre valeurs ne distingue ni « approximative » ni « services coupés » ; si le PM veut cette finesse côté proches, c'est une évolution de règle, QL12).

### 5.8 Limites du système : 20 (iOS) et 100 (Android)
- Zones surveillées = lieux des Miffs où je partage, **dédoublonnés seulement s'ils sont strictement identiques** (position, périmètre) ; le dédoublonnage plus fin entre Miffs est reporté (v2).
- **Plafond de surveillance** : 20 sur iOS (aucune marge réservée à une autre fonction), 100 sur Android. Au-delà : **sélection déterministe et identique sur tous les téléphones** par `priority` croissante puis `createdAt` croissant ; les lieux non retenus sont signalés **sur mon téléphone seulement** : « {n} lieux ne sont pas surveillés sur ton téléphone » (§10.4). Rien n'est écrit côté serveur.
- **Plafond doux dans l'app** [Proposé, QL9] : au plus 10 lieux par Miff, pour qu'un compte premium dans plusieurs Miffs reste sous la limite iOS dans la grande majorité des cas ; ce plafond n'est pas imposé par les règles (le client le contourne) et le texte « illimité » du Premium doit en tenir compte.
- **Priorisation par proximité** (réenregistrer les zones les plus proches au fil des déplacements) : **v2**, car elle exige de suivre les changements de position de façon continue, avec plus de batterie et une surface de confidentialité plus grande.

### 5.9 Vie privée : ce que voient les autres et ce qui reste sur le téléphone
- **Reste sur le téléphone** : la position de la personne, l'état local des lieux, tout calcul de distance.
- **Envoyé** : un statut (présence dans un lieu nommé ou hors lieu), l'heure, la batterie, l'état d'autorisation, vers les Miffs où la personne partage.
- **Visible de tout membre, y compris un intrus entré avec le code** : nom, adresse si renseignée, **position sur la carte** et périmètre de **chaque** lieu de la Miff. Les règles ne permettent pas de le restreindre. Mitigations proposées : adresse **facultative et jamais préremplie** ; texte de consentement qui le dit (§10.1) ; liste montrant le nom seul, adresse sur l'écran de détail (Q3) ; renouvellement du code à chaque retrait (déjà en place). **Risque assumé à écrire (R7).**
- **Lieu ajouté après le consentement** : surveillé chez chaque membre sans nouveau consentement. Proposition : **avis discret à l'ouverture suivante, une fois par lieu**, détail visible dans la liste, arrêt du partage en un geste ; **option v2** : « ne pas me signaler à cet endroit » par lieu, stockée sur le téléphone seulement (QL4).
- **Lieu qui désigne un tiers** (« Maison du père de Nathan ») : l'adresse d'une personne qui n'est pas utilisatrice est enregistrée ; risque assumé (R7), mention dans le formulaire (« N'ajoute pas l'adresse d'une personne sans son accord »).
- **Mineurs** : aucun âge minimum (décision Q7 de l'Authentification) ; un adulte peut définir des lieux surveillés chez un mineur ; même cadre de risque assumé que `data-model.md` §11 points 2 et 15.

### 5.10 Minimisation : règles absolues
1. Aucune coordonnée **de la personne** n'est écrite, journalisée, envoyée à Analytics ou Crashlytics, ni placée dans un message.
2. Le gestionnaire d'événements **ne lit ni ne conserve** la position fournie avec l'événement (Android la transmet avec l'événement ; iOS ne la fournit pas) : critère d'analyse statique et de test.
3. La position n'est lue **que** (a) à l'appui sur « Utiliser ma position actuelle » (créateur, premier plan, une fois, utilisée pour placer le **lieu**), (b) éventuellement pour une **vérification ponctuelle locale** de l'état d'un lieu (option à mesurer au spike, jamais stockée ni transmise).
4. Pas de suivi continu, pas d'historique, pas de journal de trajets. Un **journal local de détection** (type d'événement, identifiant de lieu, heure, batterie, **sans coordonnées**) n'existe **que** dans les builds internes et TestFlight, purgé à la déconnexion (QL18).
5. Les coordonnées enregistrées sont celles du **lieu** (donnée du créateur), jamais celles d'une personne.

### 5.11 Batterie
Le géofencing est conçu pour ne réveiller l'app qu'au franchissement d'une zone. Ce ticket n'ajoute **aucun service permanent, aucune notification permanente, aucune mise à jour de position répétée** en v1. Coût attendu : une relecture et un réarmement toutes les 12 h au plus. **Mesure au spike** : consommation attribuée à Miff sur 24 h (Android : statistiques de batterie du système ; iPhone : « Utilisation de la batterie ») ; objectif proposé **moins de 2 % par jour** (à valider). Un résultat plus élevé bloque la fusion.

### 5.12 Consentement versionné (texte v2)
- Le consentement de partage reste celui du ticket Miff : **lot atomique** au clic de « Rejoindre et partager » / « Créer et partager », avec `consentVersion` identifiant **le texte affiché**. **Ce ticket remplace le texte** par la version 2 du §10.1.
- **Contenu obligatoire de la v2** : (1) ce qui est partagé : arrivée ou départ d'un lieu de la Miff, heure, **niveau de batterie**, état de la localisation ; (2) **jamais la position précise** ; (3) **les lieux (nom, adresse, carte) sont visibles de tous les membres** et le créateur peut en **ajouter ou modifier** ; (4) la détection se fait **téléphone fermé** avec une autorisation d'arrière-plan **demandée ensuite et refusable** ; (5) arrêt à tout moment.
- **Version** : format `AAAA-MM-vNN` (comme `analyticsConsent`), **constante unique et identique sur Android et iOS**, strictement supérieure à celle du texte de MIFF-1 ; une constante **par langue** (`docs/legal/README.md` §2) ; un test échoue si le texte change sans changer la version.
- **Membres déjà entrés avec une version antérieure** : les règles actuelles ne permettent pas la revalidation sans arrêter puis reprendre (§8.3, R-LIEU-3). Pour la bêta : livrer la v2 **avant** que des testeurs externes ne rejoignent ; si des membres existent déjà (PM, proches de test), ils passent par « Arrêter puis reprendre ». La politique de confidentialité promettant la redemande du consentement (art. 12), je recommande **R-LIEU-3** avant la bêta externe (QL3, QL12).
- La **politique de confidentialité** doit dire la même chose (§11, §18).

---

## 6. Frontières avec les tickets voisins

| Ticket | Frontière |
|---|---|
| **Miff (MIFF-1 à MIFF-4)** | Fournit l'écran de la Miff (liste des lieux : point d'entrée), le consentement (texte révisé ici), le statut initial `unknown`, l'arrêt du partage (qui arrête la surveillance, critère K7), la perte d'accès (qui arrête la surveillance, critère D4). **Ce ticket implémente l'arrêt de surveillance** que ces critères exigent. |
| **Onboarding (ONB-4, ONB-8)** | Amorce, fenêtres du système, écrans par version d'Android, rappel non modal. **Ce ticket lit l'état** et fournit les textes révisés (§10.2) ; il **ne demande jamais** « arrière-plan » lui-même. Il n'existe encore **aucun code de permission** dans le dépôt : ONB-4 doit précéder LOC-3. |
| **Update (à écrire)** | Mise à jour manuelle : écrit le même document `status`. **Bloquée par la règle des 24 h** (§8.3) si la personne reste longtemps au même endroit. Ordre conseillé : QL19. |
| **Feed (FEED-2)** | Affiche les statuts, calcule orange et rouge, « Voir le lieu » (coordonnées **du lieu**). Propositions de textes côté proche en §10.5 ; **le Feed a le dernier mot**. |
| **Premium** | Paywall du deuxième lieu, fin d'abonnement ; réutilise « supprimer un lieu » sans écran. Événement existant `paywall_shown` (`trigger = extra_trusted_place`). |
| **Réglages** | « Confidentialité » héberge l'état de la localisation et l'entrée de l'écran « Détection de tes lieux ». |
| **Authentification (AUTH-1, AUTH-5)** | Déconnexion et suppression de compte **arrêtent la surveillance** (contrat de purge, `data-model.md` §8). |
| **`backend-data`** | Évolutions de règles du §8.3 ; mesures de lectures. |
| **`release-manager`** | Déclarations Google et Apple, étiquettes de confidentialité, politique, vidéo de démonstration (§11, §18). |

---

## 7. Différences Android / iOS

| Sujet | Android (Kotlin, Compose) | iOS (SwiftUI, iOS 16 minimum) |
|---|---|---|
| **Moteur** | API de géofencing de Google Play services | Surveillance de régions de Core Location |
| **Limite de zones** | 100 par app et par utilisateur de l'appareil (documentation Android) | **20** par app (guide Apple) ; erreur dédiée si saturé |
| **Rayon minimum** | 100 à 150 m recommandé ; précision typique 20 à 50 m avec Wi-Fi, bien pire sans | Pas de minimum documenté dans le guide lu ; marge de franchissement d'environ **200 m** pour les essais, franchissement soutenu **20 s** ; Wi-Fi coupé = nettement moins précis |
| **Délai de détection** | **Moins de 2 min** en général ; **2 à 3 min** sous limites d'arrière-plan ; jusqu'à **6 min** si le téléphone est immobile depuis longtemps | Non garanti ; **[À vérifier sur l'iPhone]** |
| **Autorisations** | Position précise + arrière-plan (à partir d'Android 10, ajoutée au manifeste) ; Android 11+ : « tout le temps » seulement dans les réglages du système ; Android 12+ : la personne peut n'accorder que l'approximative | « Lorsque l'app est active » puis évolution vers « Toujours » (invite du système) ; « Autoriser une fois » ; précision réduite possible (iOS 14+) |
| **Après redémarrage** | Les zones sont **effacées** : à réinscrire au démarrage du téléphone (écoute du démarrage) | Régions conservées par le système ; relance de l'app en arrière-plan à l'événement ; **[À vérifier]** : comportement avant le premier déverrouillage |
| **App fermée par la personne** | Selon le fabricant ; si l'app est « forcée à s'arrêter » dans les réglages, plus aucun événement avant la réouverture | **[À vérifier sur l'iPhone]** (le guide Apple ne dit rien de la fermeture forcée) ; l'actualisation en arrière-plan désactivée **empêche** le réveil |
| **Fenêtre pour écrire** | Courte (réception d'un événement) ; l'écriture peut rester en file et partir plus tard | **Environ 10 s** accordées au réveil (guide Apple) ; l'écriture peut rester en file |
| **Particularités du fabricant** | **ColorOS (OPPO)** : optimisation de la batterie, démarrage automatique, verrouillage dans les apps récentes ; aucun contournement côté développeur | Mode économie d'énergie (réveils réduits, **[À vérifier]**) |
| **Réinitialisation automatique** | Android 11+ : permissions retirées après **plusieurs mois** sans usage de l'app (documentation Android) ; la détection s'arrête sans que la personne le sache | Rappels périodiques du système sur l'usage en arrière-plan |
| **Cartes** | MapLibre + OpenFreeMap [Proposé, QL14] | MapKit (gratuit, sans clé) |
| **Recherche d'adresse** | Géocodeur du système (gratuit, sans clé, disponibilité non garantie : à tester, dépend de Google Play services) ; **recherche au clic, pas à la frappe** | Recherche de lieux d'Apple (gratuit, sans clé, limitation de débit possible) ; **recherche au clic** pour le même comportement |
| **Déclaration du store** | Formulaire « Background location » + **vidéo** + divulgation dans l'app **avant** la demande ; revue aussi des pistes de test fermées et ouvertes | Texte d'usage « Toujours » ; étiquettes de confidentialité ; revue de l'usage « Toujours » |
| **Garde-fous du dépôt à modifier** | Commentaire et contrôle du manifeste (« aucune localisation ») | `ios/ci/check-binaries.sh`, `AppConfigurationTests`, `PrivacyInfo.xcprivacy`, `project.yml` (clés `NSLocation*` interdites) |
| **Tests sur appareil** | OPPO Find X5 Lite (Android 13), position simulée possible | iPhone 11 Pro (iOS 26.5) **uniquement par TestFlight** : pas de simulation ; chaque essai consomme des minutes macOS du plan gratuit (`docs/dev/ios-spike.md` §7) |
| **Textes** | `values-fr` / `values` ; clés `place_`, `detection_`, `permission_` ; textes propres `android_` | `Localizable.xcstrings` ; mêmes clés ; textes d'usage dans `InfoPlist.xcstrings` (clés `ios_`) |

---

## 8. Contrat avec les règles Firestore et budget de lectures de règles

### 8.1 Ce que les règles permettent déjà (vérifié dans `backend/firestore.rules`)
- **Lieux** `miffs/{id}/places/{placeId}` : lecture (`get`, `list`) pour tout membre d'une Miff existante ; création par le créateur (forme complète, `createdAt` et `updatedAt` = heure serveur) ; en gratuit, **seulement `p1` en `trusted`** ; en premium, identifiants libres (`^[A-Za-z0-9]{1,20}$`) ; mise à jour par le créateur de `name`, `address`, `lat`, `lng`, `radiusM`, `priority`, `updatedAt` ; suppression par le créateur. Bornes : nom 1 à 60, adresse ≤ 200 ou `null`, `radiusM` entier 50 à 2 000, `priority` 0 à 999 ou `null`.
- **Statut** `miffs/{id}/status/{moi}` : écriture par la personne seule, **toutes les clés**, partage activé **après** le lot, `updatedAt` = heure serveur ; `presenceAt` et `lastUpdateAt` entre **−24 h** et **+5 min**, `escalationDueAt` entre −1 j et +1 j ; lecture des statuts des autres seulement si l'on a activé son partage **et** publié son propre statut.
- **Partage** : transitions « arrêté, activé » (nouvelle date et version) et « activé, arrêté » (avec suppression du statut et de la photo dans le même lot).

### 8.2 Budget de lectures de règles (limite : 20 par lot, 10 par écriture simple)

| Opération de ce ticket | Écritures | Lectures de règles attendues | Marge |
|---|---|---|---|
| **Créer un lieu, gratuit** (`p1`) | 1 | contrôle du créateur : 1 ; aucun contrôle premium pour `p1` | large (sur 10) |
| **Créer un lieu, premium** | 1 | ≤ 4 (mesuré, `data-model.md` §12) | ≥ 6 (sur 10) |
| **Modifier / supprimer un lieu** | 1 | 1 | large |
| **Supprimer les lieux d'une Miff** (suppression de Miff, ticket Miff) | ≤ 5 par lot | ≤ 5 | ≥ 15 (sur 20) |
| **Lire la liste des lieux** (requête) | 0 | 2 (membre + Miff existante), plus les lectures des documents | — |
| **Écrire un statut** (événement), **une écriture par Miff** | 1 | ≤ 1 (contrôle du partage) | large |
| Relecture des lieux en arrière-plan | 0 | 2 par Miff, plus N documents | — |

**Décision de conception** : les statuts sont écrits **séparément, une Miff à la fois** (jamais dans un seul lot), pour qu'un compte premium dans beaucoup de Miffs ne s'approche pas de la limite de 20 et pour isoler les échecs. **À mesurer par `backend-data` avant LOC-2 et LOC-3** : création de lieu gratuit (non listée dans la dernière mesure), écriture de statut avec l'évolution R-LIEU-1.

**Estimation de lectures ajoutées par utilisateur actif et par jour** (estimation, pas une mesure ; base : environ 75 lectures par jour, `data-model.md` §12) : relecture à l'ouverture (déjà comptée « lieux, rarement »), 2 relectures périodiques en arrière-plan × (N + 2) ≈ 6 à 14, réveils par événements ≈ 0 à 6. **Total ajouté : 10 à 25.** Au-delà de **100** lectures par utilisateur actif, le signal de `docs/dev/surveillance-quotas.md` s'applique. Écritures : 6 à 10 événements par jour (`data-model.md` §12 : environ 7 au total), soit 2 000 à 2 800 utilisateurs actifs avant le plafond de 20 000 écritures par jour.

### 8.3 Évolutions de règles ou de schéma à demander à `backend-data` (non faites ici ; chaque déploiement exige tests, revue de sécurité et « oui » explicite du PM, `docs/dev/deploiement-regles.md`)

| Code | Besoin | Proposition | Si refusé |
|---|---|---|---|
| **R-LIEU-1** | Réécrire un statut **sans changer `presenceAt` et `lastUpdateAt`** quand ils ont plus de 24 h (rafraîchir `locationAccess` ou la batterie ; **mise à jour manuelle** après un long séjour au même endroit) | Dans `validStatus`, accepter une mise à jour où ces champs sont **inchangés** par rapport au document existant (lecture de `resource.data`, aucune lecture de règles de plus) | On est forcé de **mentir sur l'heure d'arrivée** (la remettre à « maintenant ») ou de ne jamais rafraîchir un statut de plus de 24 h ; le ticket Update ne peut pas fonctionner après un week-end chez soi |
| **R-LIEU-3** | **Renouveler un consentement actif** (nouvelle version du texte) sans arrêter le partage | Accepter la transition « activé, activé » si la nouvelle version est **supérieure** et `consentedAt` = heure serveur, `revokedAt` = `null` | Les membres existants doivent « arrêter puis reprendre » (suppression de leur statut) ; la promesse de l'art. 12 de la politique est difficile à tenir |
| **R-LIEU-2** | **Catégorie de lieu** (si QL8 = oui) | Clé `category` (liste fermée) dans `places`, facultative | L'emoji dans le nom tient lieu de catégorie |
| R-LIEU-4 (non demandée) | Finesse de `locationAccess` (« approximative », « services coupés ») | Étendre la liste de valeurs | Correspondance du §5.7 |

---

## 9. Cas limites

1. **Entrée ou sortie dans le désordre** (arrivée à B signalée avant le départ de A) : l'état local calcule le statut final (§5.6).
2. **Doublons et rebonds en bordure de zone** : une écriture seulement si l'état change ; disjoncteur du §5.6.
3. **Déjà dans le lieu au moment de l'armement** : arrivée immédiate ; **dehors** : rien d'inventé.
4. **Lieu déplacé ou agrandi pendant que je suis dedans** : réarmement ; si la présence ne peut pas être reconfirmée, `unknown`.
5. **Lieu supprimé pendant que je suis dedans** : statut `unknown` (`placeId = null`).
6. **Événement reçu pour un lieu que la dernière relecture ne connaît plus** : ignoré, sans erreur ni écriture.
7. **Partage arrêté ou Miff quittée, retirée, supprimée** : toute surveillance liée arrêtée ; un événement tardif ne produit **aucune** écriture (de toute façon refusée par les règles).
8. **Déconnexion** : plus aucune surveillance ni écriture ; **le proche voit un état figé** (question ouverte Q20 de `data-model.md`, texte de confirmation de déconnexion déjà prévu).
9. **Téléphone éteint ou batterie vide** : aucun événement ; le dernier statut reste affiché. Si la personne était dans un lieu : **vert figé** (le proche voit l'heure d'arrivée, pas une fraîcheur). Si elle était partie : orange puis rouge selon la règle des 2 h et 4 h, calculée par l'app du proche. Rien n'est « détecté » à distance.
10. **Mode avion ou pas de réseau** : l'événement est écrit au retour du réseau, **avec l'heure réelle** ; au-delà de **24 h** de retard l'écriture est refusée par les règles et abandonnée proprement.
11. **Localisation du téléphone coupée (GPS)** : plus de détection, état « La localisation du téléphone est désactivée » ; au retour, **réarmement et revalidation**.
12. **Autorisation retirée app fermée** : sous Android, le retrait ferme l'app ; **rien ne s'exécute** avant la prochaine ouverture ou la prochaine tâche périodique ; le statut peut afficher `background` à tort jusque-là (limite connue, atténuée par la tâche périodique).
13. **Économiseur de batterie / optimisation du fabricant / démarrage automatique refusé** : détection retardée ou absente ; état affiché pour ce qui est détectable, guide pour le reste.
14. **Redémarrage du téléphone** : Android réinscrit les zones sans ouvrir l'app ; iOS : régions conservées **[À vérifier]**.
15. **Mise à jour de l'app** : réarmement au premier lancement et, sur Android, à la fin de la mise à jour **[À vérifier au spike]**.
16. **Horloge du téléphone décalée de plus de 5 min** en avance : l'heure d'événement est plafonnée à l'heure actuelle pour éviter un refus répété.
17. **Même compte sur deux téléphones** : les deux détectent et écrivent ; un vieux téléphone resté chez soi peut écrire « à la maison » alors que la personne est partie avec l'autre (**limite connue v1**, QL17).
18. **Deux Miffs avec le même lieu** (premium) : deux zones (dédoublonnage v2) ; deux écritures.
19. **Plus de lieux que la limite système** : sélection déterministe, état « n lieux non surveillés » (§5.8).
20. **Désinstallation et réinstallation** : zones perdues ; réarmement après la première synchronisation (même contrat que les Miffs).
21. **Création d'un lieu hors connexion** : impossible, message neutre, rien en file.
22. **Recherche d'adresse indisponible** (géocodeur absent d'un appareil) ou **carte indisponible** : le formulaire reste utilisable par l'autre moyen.
23. **Création sans aucune autorisation** : possible ; la détection de **ce** lieu chez la personne dépend de son autorisation d'arrière-plan.
24. **Lieu partagé par un créateur dont le Premium a expiré** : géré par le ticket Premium (suppression des derniers lieux).
25. **Un membre qui n'ouvre jamais l'app** : les nouveaux lieux ne l'atteignent que par la relecture périodique ; **limite assumée** (QL5).
26. **Permission automatiquement retirée après des mois d'inactivité** (Android 11+) : même effet que le cas 12.
27. **Taille de texte maximale, mode droite-à-gauche** : formulaire, liste et textes de consentement lisibles.
28. **Quitter l'écran de création sans enregistrer** : rien n'est écrit ; l'adresse saisie est oubliée (jamais mise en brouillon sur le serveur).

---

## 10. Textes proposés (FR et EN, tutoiement ; **tous à valider par le PM**)

Principes : phrases courtes, aucun mot de la liste interdite (« danger », « urgence », « alerte », « irrévocable », « expulsé »), chaque erreur propose une issue. Préfixes de clés : `place_` (existant), `permission_` (Onboarding), **`detection_` à ajouter** au tableau de `docs/contracts/i18n-keys.md` et à `KNOWN_PREFIXES`. Les noms de réglages du système (« Toujours », « Autoriser tout le temps ») sont des **libellés propres à chaque plateforme** : à confirmer sur l'OPPO (ColorOS) et l'iPhone (iOS 26).

### 10.1 Consentement version 2 (cartes « Rejoindre et partager », « Créer et partager », reprise du partage)

| | Français | English |
|---|---|---|
| Texte principal | « En rejoignant cette Miff, tu partages avec ses membres ton statut : ton arrivée ou ton départ des lieux de la Miff, l'heure, ton niveau de batterie et l'état de ta localisation. Jamais ta position précise. Les lieux (nom, adresse, carte) sont visibles de tous, et le créateur peut en ajouter. Tu peux arrêter à tout moment dans les réglages de la Miff. » | "By joining this Miff, you share your status with its members: when you arrive at or leave the Miff's places, the time, your battery level and the state of your location access. Never your precise location. The places (name, address, map) are visible to everyone, and the creator can add more. You can stop at any time in the Miff's settings." |
| Variante création | « En créant cette Miff, tu partages avec ses membres ton statut : … » (même suite ; « le créateur peut en ajouter » devient « tu pourras en ajouter ») | "By creating this Miff, you share your status with its members: …" (same; "the creator can add more" becomes "you'll be able to add more") |
| Rappel système | « Ensuite, Miff te proposera d'autoriser la localisation en arrière-plan pour te signaler même quand l'app est fermée. Tu peux refuser : tu rejoins la Miff quand même. » | "Next, Miff will ask you to allow background location so it can signal you even when the app is closed. You can say no and still join." |
| Boutons | « Rejoindre et partager » / « Créer et partager » | "Join and share" / "Create and share" |

### 10.2 Amorce d'arrière-plan (remplace le texte conditionnel de `onboarding.md` §6.11, devenu inexact : la promesse « jamais ta position précise » est désormais tenue par ce ticket)

| | Français | English |
|---|---|---|
| Titre | « Te signaler que tu vas bien, sans y penser » | "Let them know you're fine, without thinking about it" |
| Corps (divulgation Google : « position », « quand l'app est fermée », fonction citée) | « Pour signaler à ta Miff « {nom} » quand tu arrives ou pars des lieux qu'elle a définis, Miff utilise la position de ton téléphone, même quand l'app est fermée ou que tu ne l'utilises pas. Ta position précise n'est ni enregistrée ni partagée : tes proches voient seulement un état (par exemple « à la maison ») et l'heure. Tu peux arrêter à tout moment dans les réglages de Miff. Sans cette autorisation, tu peux toujours donner des nouvelles toi-même. » | "To let your Miff "{name}" know when you arrive at or leave the places it has set, Miff uses your phone's location, even when the app is closed or not in use. Your precise location is never saved or shared: your loved ones only see a status (for example "at home") and the time. You can stop at any time in Miff's settings. Without this permission, you can still send news yourself." |
| Boutons | « Autoriser » / « Plus tard » (poids égal) | "Allow" / "Later" |
| Android 11+, second écran | « Dernière étape : choisis « Autoriser tout le temps » dans les réglages, pour que la détection fonctionne quand l'app est fermée. » + « Ouvrir les réglages » / « Plus tard » | "Last step: choose "Allow all the time" in settings so detection works when the app is closed." + "Open settings" / "Later" |
| **Texte d'usage iOS** (`NSLocationWhenInUseUsageDescription`) | « Miff utilise ta position pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance, et pour placer un lieu sur la carte. Elle n'est jamais enregistrée comme un trajet. » | "Miff uses your location to let your Miff know when you arrive at or leave a trusted place, and to place a spot on the map. It is never saved as a route." |
| **Texte d'usage iOS** (`NSLocationAlwaysAndWhenInUseUsageDescription`) | « Pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Seul un état (par exemple « à la maison ») et l'heure sont partagés, jamais ta position précise. » | "To let your Miff know when you arrive at or leave a trusted place, even when the app is closed, Miff needs your location in the background. Only a status (for example "at home") and the time are shared, never your precise location." |

### 10.3 Gestion des lieux

| Contexte | Français | English |
|---|---|---|
| Titres | « Nouveau lieu » / « Modifier le lieu » | "New place" / "Edit place" |
| Nom (champ, exemple, aide) | « Nom du lieu » ; « Maison, école, travail… » ; « Tes proches verront ce nom. » | "Place name"; "Home, school, work…"; "Your loved ones will see this name." |
| Adresse | « Adresse (facultatif) » ; « Rechercher une adresse » | "Address (optional)"; "Search for an address" |
| Ma position | « Utiliser ma position actuelle » ; « Utilisée une seule fois pour placer le lieu sur la carte. » | "Use my current location"; "Used once to place the spot on the map." |
| Périmètre | « Périmètre » ; « {n} m » ; « Trop petit, il peut manquer un passage ; trop grand, il couvre aussi les environs. » | "Radius"; "{n} m"; "Too small and it may miss a visit; too large and it covers the surroundings too." |
| Partage du lieu | « Les membres de la Miff verront ce lieu : son nom, son adresse si tu la renseignes, et sa position sur la carte. N'ajoute pas l'adresse d'une personne sans son accord. » | "The Miff's members will see this place: its name, its address if you add one, and its position on the map. Don't add someone's address without their consent." |
| Enregistrer | « Enregistrer le lieu » | "Save place" |
| Limite de nom | « Limite de 60 caractères atteinte. » | "60-character limit reached." |
| Succès | « Le lieu « {nom} » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » | "The place "{name}" is saved. Your loved ones will pick it up as soon as they open Miff, or within the next few hours." |
| Modification (avant d'enregistrer) | « Ce lieu change pour tous les membres de la Miff. » | "This place changes for all the Miff's members." |
| Suppression | « Supprimer le lieu « {nom} » ? Tes proches ne seront plus signalés quand ils y arrivent ou en partent. » + « Supprimer » / « Annuler » | "Delete the place "{name}"? Your loved ones won't be signaled when they arrive or leave." + "Delete" / "Cancel" |
| Limite gratuite | « Avec la formule gratuite, tu peux avoir 1 lieu de confiance. Miff+ te permet d'en ajouter d'autres. » + « Découvrir Miff+ » / « Plus tard » | "With the free plan, you can have 1 trusted place. Miff+ lets you add more." + "Discover Miff+" / "Later" |
| Plafond doux (si QL9) | « Cette Miff a déjà beaucoup de lieux. Supprime-en un pour en ajouter un autre. » | "This Miff already has many places. Delete one to add another." |
| Liste vide, créateur / membre | « Pas encore de lieu. Ajoutes-en un (la maison, l'école…) : les membres de la Miff seront signalés quand ils y arrivent ou en partent. » / « Le créateur n'a pas encore ajouté de lieu. » | "No place yet. Add one (home, school…): the Miff's members will be signaled when they arrive or leave." / "The creator hasn't added a place yet." |
| Détail | « Voir sur la carte » | "View on map" |
| Avis aux membres (une fois par lieu) | « Nouveau lieu dans la Miff « {nom} » : « {lieu} ». Tu seras signalé quand tu y arrives ou en pars. Tu peux arrêter à tout moment dans les réglages de la Miff. » ; « Le lieu « {lieu} » a été modifié. » ; « Le lieu « {lieu} » a été supprimé. » + « Compris » | "New place in the Miff "{name}": "{place}". You'll be signaled when you arrive or leave. You can stop at any time in the Miff's settings."; "The place "{place}" was changed."; "The place "{place}" was deleted." + "Got it" |
| Recherche sans résultat | « On ne trouve pas cette adresse. Essaie avec moins de détails, ou place le repère sur la carte. » | "We can't find this address. Try fewer details, or place the pin on the map." |
| Recherche indisponible | « La recherche d'adresse n'est pas disponible pour le moment. Tu peux placer le repère directement sur la carte. » | "Address search isn't available right now. You can place the pin directly on the map." |
| Carte indisponible | « La carte ne s'affiche pas pour le moment. Tu peux saisir une adresse. » | "The map isn't loading right now. You can enter an address." |
| Hors connexion, erreur | (textes du ticket Miff, §5.10) | (same) |

### 10.4 Écran « Détection de tes lieux » (sur mon téléphone) — état, texte, action

| État | Titre et texte (FR) | Action |
|---|---|---|
| Active | « La détection est active. Elle peut avoir quelques minutes de retard. » (EN : "Detection is on. It can sometimes be a few minutes late.") | — |
| Désactivée (jamais autorisée, « Plus tard ») | « La détection automatique est désactivée. Autorise la localisation en arrière-plan pour que Miff signale tes arrivées et tes départs sans que tu ouvres l'app. Sans cela, tu peux toujours donner des nouvelles toi-même. » | « Activer » |
| Seulement pendant l'utilisation | « Il manque une autorisation. Choisis « Toujours » (« Autoriser tout le temps » sur Android) dans les réglages du téléphone. » | « Ouvrir les réglages » |
| Position approximative | « La position précise est désactivée. Miff en a besoin pour repérer un lieu. » | « Ouvrir les réglages » |
| Refusée | « La localisation est refusée. Tu peux continuer à donner des nouvelles toi-même, et changer d'avis quand tu veux. » | « Ouvrir les réglages » |
| Localisation du téléphone coupée | « La localisation de ton téléphone est désactivée. » | « Ouvrir les réglages » |
| Économiseur de batterie (Android) | « L'économiseur de batterie peut retarder la détection. » | « Ouvrir les réglages » |
| Optimisation du fabricant (Android) | « Ton téléphone peut mettre Miff en veille et retarder la détection. » | « Voir comment faire » (guide) |
| Actualisation en arrière-plan désactivée (iOS) | « L'actualisation en arrière-plan est désactivée pour Miff. » | « Ouvrir les réglages » |
| Lieux non surveillés (limite) | « {n} lieux ne sont pas surveillés sur ton téléphone : il ne peut en surveiller qu'un nombre limité. » | — |
| Partage arrêté | « Tu ne partages pas ton statut avec cette Miff : rien n'est détecté. » | « Reprendre le partage » |
| Aucun lieu | « Aucun lieu pour le moment. » | — |

**Guide fabricant** (titre : « Garder Miff active en arrière-plan ») : « Certains téléphones (OPPO, Xiaomi, Samsung…) mettent les applis en veille pour économiser la batterie, ce qui peut retarder la détection. 1. Ouvre les réglages de Miff. 2. Dans Batterie, choisis « Autoriser l'activité en arrière-plan » (ou « Sans restriction »). 3. Active « Démarrage automatique » si tu le vois. 4. Dans les applis récentes, verrouille Miff plutôt que de la fermer en la balayant. » + « Ouvrir les réglages de Miff » / « Je l'ai fait ». **Les libellés de ColorOS sont à confirmer sur l'OPPO** ; aucune action n'est faite à la place de la personne.

### 10.5 Côté proche (propositions pour le ticket Feed, qui a le dernier mot ; heures absolues, ton neutre)

| Cas | Français | English |
|---|---|---|
| Dans un lieu | « {Prénom} est à {lieu} depuis {heure}. » | "{Name} has been at {place} since {time}." |
| Parti | « {Prénom} a quitté {lieu} à {heure}. » | "{Name} left {place} at {time}." |
| Pas de nouvelles | « Pas de nouvelles de {Prénom} depuis {heure}. » + « Envoyer un message » | "No news from {Name} since {time}." + "Send a message" |
| Détection limitée | « La détection automatique de {Prénom} est limitée. Une mise à jour de sa part te rassurera. » | "{Name}'s automatic detection is limited. An update from them would reassure you." |
| Partage arrêté | « {Prénom} ne partage pas son statut pour le moment. » | "{Name} isn't sharing their status right now." |

### 10.6 Brouillons pour les stores (`release-manager`, **jamais relus par un juriste**)

- **Google Play, divulgation visible** : texte de §10.2 (corps) affiché **avant** la demande, sans ouvrir de menu, avec « position », « quand l'app est fermée » et la fonction concernée ; **fonction déclarée** (une seule) : signaler à son cercle l'arrivée ou le départ de lieux choisis (exemple de la documentation Google : alerte d'un compagnon familial) ; vidéo de 30 s ou moins montrant l'activation en arrière-plan, la divulgation et la fenêtre du système.
- **Apple, note de revue** : « Miff signale automatiquement à un cercle choisi par l'utilisateur son arrivée ou son départ de lieux nommés, sans ouvrir l'app. La position précise n'est jamais enregistrée ni transmise. L'app reste pleinement utilisable sans l'autorisation « Toujours » (mises à jour manuelles). L'utilisateur peut arrêter le partage à tout moment, ce qui supprime son statut. »

---

## 11. Coût et choix économiques (objectif : 0 €)

| Poste | Choix gratuit | Limite connue | Signal et option payante |
|---|---|---|---|
| **Détection** | API du système, aucun serveur | Fiabilité dépendante du fabricant ; **aucun contournement légitime** | Spike (§12) ; plan B payant en batterie, pas en argent : notification permanente (QL2) |
| **Écritures de statut et de lieux** | Firestore Spark | Environ 7 écritures par utilisateur actif et par jour ; **20 000 par jour** au total | Signal de `data-model.md` §12 (écritures > 12 000) |
| **Lectures** (relectures de lieux) | Firestore Spark | +10 à 25 lectures par utilisateur actif et par jour ; **50 000 par jour** | Ratio lectures / utilisateurs actifs > 100 : à examiner ; > 150 deux jours de suite : bascule |
| **Carte iOS** | MapKit, sans clé | Limitation de débit par Apple (erreur à gérer, **[À vérifier]**) | Aucune option payante nécessaire |
| **Carte Android (proposition)** | **MapLibre + tuiles OpenFreeMap** : gratuit, sans clé, commercial autorisé, attribution obligatoire (consulté 2026-10-31) | **Aucun contrat de niveau de service** ; financé par des dons ; hébergement possible par soi-même | **Signal** : tuiles indisponibles ou lentes pendant la bêta, ou changement des conditions ; **option payante** : fournisseur de tuiles avec clé (décision PM) ; le fournisseur doit être **remplaçable par un seul réglage** |
| **Carte Android (écartée par défaut)** | SDK Google Maps : « Maps SDK » listé à usage illimité, **mais exige un projet avec facturation** (documentation Google, consultée 2026-10-31) | Moyen de paiement obligatoire ; clé liée au nom du paquet et au SHA-1 | **Décision PM obligatoire** (règle d'or) ; jamais sans elle |
| **Tuiles OpenStreetMap directes** (`tile.openstreetmap.org`) | Écarté | Accès « au mieux », blocage possible sans préavis, pas de préchargement ni de hors-ligne (politique OSM) | Ne pas utiliser pour une app publique |
| **Recherche d'adresse iOS** | Recherche de lieux d'Apple, sans clé | Débit limité par Apple | — |
| **Recherche d'adresse Android** | Géocodeur du système, sans clé | Pas de recherche à la frappe garantie ; peut être absent ; **à tester sur l'OPPO** | Si absent ou peu fiable : repère sur la carte seulement (déjà prévu) |
| **Nominatim public** | **Écarté** | **Interdit** : recherche à la frappe ; **1 requête par seconde pour toute l'app** ; user-agent identifiant ; les services peuvent être retirés | Ne pas utiliser (violerait la politique) |
| **Google Geocoding / Places** | **Écartés** | Facturés au-delà de 10 000 requêtes par mois et par service (tarifs consultés 2026-10-31) ; exigent facturation | Décision PM |
| **Stores** | Gratuit | Comptes déjà payés | — |

**Confidentialité des fournisseurs** : taper une adresse envoie du texte à Apple ou Google (système) ; afficher la carte envoie la zone consultée (adresse IP et tuiles) à Apple ou à OpenFreeMap. **Seul le créateur** consulte la carte pour placer un lieu. La politique de confidentialité (art. 3 et 6) **ne le dit pas encore** : à compléter par `release-manager` avant toute publication (§18).

---

## 12. Tests réalistes sur appareils et spike de fiabilité

### 12.1 Pyramide de tests

| Niveau | Quoi | Où |
|---|---|---|
| **1. Logique pure** (CI Linux pour Android, CI macOS pour iOS) | Calcul du statut à partir d'événements (désordre, doublons, chevauchement, état initial), table des autorisations, sélection au-delà de la limite, plafond d'écritures, mapping `locationAccess`, bornes d'horloge, validation des champs d'un lieu, texte et version de consentement | Tests unitaires **identiques** sur les deux plateformes |
| **2. Android instrumenté** | Enregistrement et suppression de zones, réception d'un événement, **réarmement après redémarrage** (redémarrage de l'appareil, pas d'ouverture de l'app) | OPPO réel ; position **simulée** (application de position fictive des options pour développeurs) : sert à tester le **chemin de code**, **pas** la fiabilité du fabricant |
| **3. Marche réelle** (référence) | Franchissements réels avec l'app dans l'état voulu | **OPPO Find X5 Lite** (Android 13, ColorOS) et **iPhone 11 Pro** (iOS 26.5, TestFlight) |
| **4. Deux téléphones ou la console** | Le statut écrit est lu par un second compte ou dans Firestore (console, lecture seule, `--project miff-3d283`) | Jusqu'à FEED-2, la recette se fait par la console ou l'écran de diagnostic interne |

L'iPhone n'a **aucun simulateur de trajet** (pas de Mac) : chaque essai de détection est une marche réelle et un build TestFlight. D'où l'importance du niveau 1 et du **journal local de détection** (builds de test seulement, §5.10).

### 12.2 Spike LOC-1 (Android, OPPO) puis LOC-4 (iOS) : objectifs
Répondre, par la mesure, à : **la détection est-elle assez fiable sur ces appareils pour être le cœur du produit ?** Build **de débogage uniquement** (permissions de localisation déclarées dans une variante debug, **jamais** dans le build de publication), **aucune écriture Firestore**, journal local, un ou deux lieux de test autour du domicile du PM.

**Matrice de scénarios** (chacun répété au moins 3 fois ; lieu de rayon 150 m ; sortie jusqu'à 400 m ou plus, 10 min dehors, retour) :

| # | Scénario | Ce qu'on mesure |
|---|---|---|
| S1 | App ouverte puis écran éteint, **réglages par défaut de ColorOS** | Détection, délai |
| S2 | App **balayée** depuis les applis récentes | Idem |
| S3 | Téléphone **immobile 1 h** (écran éteint) puis départ | Détection après immobilité (jusqu'à 6 min attendues sur Android) |
| S4 | **Redémarrage** du téléphone, **sans** ouvrir l'app, puis départ | Réarmement automatique |
| S5 | **Économiseur de batterie** activé | Effet |
| S6 | **Optimisation de la batterie désactivée + démarrage automatique + verrouillage dans les récentes** (réglages recommandés) | Écart avec S1 |
| S7 | **Nuit entière** (mise en veille profonde) puis départ au matin | Détection au réveil |
| S8 | **Localisation du téléphone coupée** puis rétablie en route | Réarmement |
| S9 | **Wi-Fi coupé** | Précision et délai |
| S10 | **Deux lieux** de 120 m espacés de 150 m (chevauchement) | Statut final cohérent |
| S11 | **Transport** (bus) traversant la zone | Faux positifs et rebonds |
| S12 | Mise à jour de l'app (installation par-dessus) | Zones conservées ou à réinscrire |
| S13 | Lieu **dans lequel on est déjà** lors de l'armement | Arrivée immédiate |
| S14 | Autorisation **retirée** dans les réglages puis rendue | Détection de l'état |

**Relevé à remplir** (une ligne par franchissement) : scénario ; heure réelle du franchissement (montre) ; heure d'enregistrement par le journal ; détecté (O/N) ; écart ; batterie ; état de l'app ; réglages du fabricant. **Mesure de la batterie** : consommation attribuée à Miff sur 24 h, avant et après.

**Critères de décision proposés** (à valider par le PM, QL1) :
- **Go** : au moins **9 franchissements sur 10** détectés **en moins de 10 minutes** dans S1, S3, S4 et S7 avec les réglages recommandés (S6), et batterie attribuée à Miff **inférieure à 2 % par jour** ;
- **À corriger avant de continuer** : entre 7 et 9 sur 10 : mitigations du §5.9 et guide fabricant, nouveau spike ;
- **No-go** : moins de 7 sur 10 avec les réglages recommandés : décision du PM sur le plan B (QL2) ou sur la requalification du produit.

**Options à évaluer pendant le spike (non engagées)** : vérification ponctuelle **locale** de l'état des lieux (une lecture, jamais stockée) lors du réarmement et de la tâche périodique ; sur iOS, réveil par changements de position significatifs en complément des régions ; écriture iOS par transfert en arrière-plan si la fenêtre de 10 s est insuffisante. Chacune augmente la surface de confidentialité ou la batterie : **décision du PM** si elle s'avère nécessaire.

### 12.3 Plan de test du développement complet
Les scénarios S1 à S14 sont rejoués avec le moteur complet (statuts réellement écrits, lus par un second compte). Le **scénario de marche standard de recette PM** : (1) installer, créer une Miff, un lieu de 150 m autour de chez soi, autoriser « toujours » ; (2) rester 10 min dedans ; (3) s'éloigner à 400 m, y rester 10 min ; (4) revenir ; (5) noter pour chaque franchissement l'heure réelle et l'heure vue sur le second téléphone ou dans la console. À répéter avec l'app balayée, après redémarrage, avec l'économiseur de batterie, avec le GPS coupé.

---

## 13. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket (`CLAUDE.md`). Tailles relatives, sans durée. Le **spike passe en premier** ; la gestion des lieux (sans détection) peut avancer en parallèle.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **LOC-0** | **Décisions, design, textes, règles, déclarations** (PM, `design-system`, `backend-data`, `release-manager`) | Réponses à §14 ; maquettes de §17 ; textes §10 validés ; **version 2 du consentement** (mise à jour de `miff.md` §5.10 et des fiches `miff-rejoindre.md`, `miff-creation.md`) ; R-LIEU-1 et R-LIEU-3 (écriture, tests, **mesure des lectures**, déploiement avec « oui » du PM) ; mise à jour de la politique de confidentialité ; brouillons des déclarations stores | S à M | Validation de ce ticket |
| **LOC-1** | **Android : spike de fiabilité sur l'OPPO** | §12.2 ; build de débogage, aucune écriture Firestore ; rapport chiffré et recommandation go / no-go | S à M | INIT-2 ; scénarios validés |
| **LOC-2** | **Android : lieux** (créer, modifier, supprimer, carte, recherche, limite gratuite, liste, détail, avis aux membres) | §5.2 à §5.5, §10.3 ; MapLibre ; textes `place_` ; suppression appelable sans écran ; **aucune détection** | L | MIFF-1 (écran de la Miff), ONB-3 (Profil), LOC-0 (design, QL14) |
| **LOC-3a** | **Android : détection** (armement, synchronisation des lieux, événements vers statut, `locationAccess`, nettoyage à l'arrêt du partage, à la perte d'accès et à la déconnexion, réarmement, tâche périodique) | §5.6 à §5.8, §5.11 ; manifeste (autorisations) ; tests niveau 1 et 2 | L | **LOC-1 (go)**, LOC-2, **ONB-4**, MIFF-1, R-LIEU-1 déployée si QL12 oui |
| **LOC-3b** | **Android : santé de la détection** (écran « Détection de tes lieux », bandeaux, guide fabricant) | §5.7, §10.4 | M | LOC-3a, Réglages > Confidentialité (ONB-3b) |
| **LOC-4** | **iOS : spike de fiabilité sur l'iPhone (TestFlight)** | Mêmes mesures ; **modifie les garde-fous du dépôt** (`NSLocation*`, `PrivacyInfo.xcprivacy`, contrôle CI) dans cette PR ; journal local | S à M | INIT-4, LOC-1 (protocole) |
| **LOC-5** | **iOS : lieux** | Mêmes livrables que LOC-2 ; MapKit | L | LOC-2 (référence), MIFF-3, ONB-7 |
| **LOC-6a / 6b** | **iOS : détection, puis santé** | Mêmes livrables que LOC-3a / 3b ; textes d'usage | L + M | LOC-4 (go), LOC-5, ONB-8, MIFF-3 |

Chaque PR : revue de code en contexte neuf, **revue de sécurité** (position, journaux, consentement, autorisations, état local, clés), QA, recette du PM, fusion par Claude après « oui » explicite du PM pour la PR (`CLAUDE.md`). **Exception de LOC-1 et LOC-4** : aucune écriture réseau, build de test seulement.

---

## 14. Questions pour le PM (20, avec ma recommandation)

Je ne tranche aucune décision produit ou légale. « Reco » = recommandation de l'agent. Les six premières **bloquent** le démarrage de LOC-1 à LOC-3.

| # | Question | Reco | Impact si autre choix |
|---|---|---|---|
| **QL1** | **Fait-on le spike de fiabilité sur l'OPPO (puis l'iPhone) avant de développer la détection ?** Seuils go / no-go du §12.2 ? | **Oui.** Seuils proposés : 9 sur 10 en moins de 10 min dans S1, S3, S4, S7 avec les réglages recommandés ; batterie < 2 % par jour. | Sans spike, on peut construire une détection que le fabricant désactive : risque de fausse réassurance non mesuré. |
| **QL2** | **Si ColorOS reste insuffisant même avec les réglages recommandés : plan B ?** (a) guide utilisateur seulement ; (b) **notification permanente** (service au premier plan) qui augmente la fiabilité, coûte de la batterie et exige une déclaration supplémentaire à Google ; (c) réviser la promesse du produit. | **(a) d'abord** ; (b) seulement si le spike échoue **et** que tu l'acceptes (notification visible en permanence : arbitrage « discrétion » contre « fiabilité »). | (b) ajoute une permission, une notification et une revue Play. |
| **QL3** | **Consentement v2** : valides-tu le texte du §10.1 (batterie, lieux visibles, détection app fermée, arrêt à tout moment) et l'enchaînement **consentement (écriture) puis amorce d'arrière-plan** ? Tolères-tu le **défilement** à taille de texte maximale (§4 #9) ? **Membres déjà entrés** : revalidation par « arrêter puis reprendre » ou par R-LIEU-3 ? | Texte et enchaînement **oui** ; livrer la v2 **avant** la bêta externe ; **R-LIEU-3** avant la bêta (la politique promet la redemande, art. 12). | Sans R-LIEU-3, tout changement futur du texte impose d'arrêter puis reprendre le partage. |
| **QL4** | **Lieux ajoutés ou modifiés après le consentement** : surveillés immédiatement chez chaque membre, avec **avis discret à l'ouverture suivante** (une fois par lieu) ? Ou n'armer un lieu qu'après que le membre l'a vu ? Option v2 « ne pas me signaler à cet endroit » ? | **Armement immédiat + avis + texte de consentement explicite** ; option par lieu en **v2**. | « Armer après avoir vu » respecte mieux le consentement mais un enfant qui n'ouvre jamais l'app ne serait pas détecté sur les nouveaux lieux. |
| **QL5** | **Propagation des lieux sans notification** : acceptes-tu qu'un lieu ajouté, déplacé ou supprimé n'atteigne un proche qu'à l'ouverture de son app ou dans les **prochaines heures** (relecture au plus toutes les 12 h) ? | **Oui pour la bêta**, avec le texte du §10.3 ; levée par Blaze. | Réduire à 1 h coûterait des lectures et de la batterie et resterait « au mieux » sur iPhone. |
| **QL6** | **Qui voit les adresses ?** Tout membre (y compris un intrus avec le code) voit nom, adresse, position, périmètre. Adresse **facultative** ? Liste = nom seul, adresse sur le détail ? | **Adresse facultative, jamais préremplie**, mention « n'ajoute pas l'adresse d'une personne sans son accord », risque assumé écrit (R7). | Une adresse obligatoire augmenterait l'exposition du domicile. |
| **QL7** | **Périmètre** : bornes et valeur par défaut ? | **100 à 1 000 m, défaut 150 m, pas de 50 m** ; texte d'aide du §10.3. Les règles tolèrent 50 à 2 000. | Moins de 100 m : détections manquées (Android recommande 100 à 150 m) ; plus de 1 000 m : « à la maison » devient trompeur. |
| **QL8** | **Catégories de lieu** (maison, école…) : liste ? (décidée en 2026-10-16, absente du schéma) | **Reporter** : emoji dans le nom pour la v1 ; si tu veux la liste, la fournir (je n'en invente pas) → R-LIEU-2. | Ajouter la catégorie = règle modifiée, tests, déploiement. |
| **QL9** | **Plafond de lieux** : plafond doux de **10 par Miff** et sélection déterministe au-delà de 20 (iOS) / 100 (Android) ? Que devient « illimités » dans les textes ? | **Oui**, plafond doux 10 ; reformuler « jusqu'à 10 lieux par Miff » dans les textes Premium et les stores (**à valider par toi**). | Illimité = lieux non surveillés sur iOS sans que la personne comprenne pourquoi. |
| **QL10** | **Sans autorisation « Toujours »** : pas d'escalade automatique (neutre, « localisation limitée »), la mise à jour manuelle étant le seul repli ? Ou escalade depuis la dernière mise à jour manuelle ? | **Pas d'escalade sans départ détecté** ; ajuster le texte de la spec. | Une escalade sans détection alerterait le cercle de personnes qui n'utilisent simplement pas les mises à jour : anxiogène. |
| **QL11** | **Faux départs et arrivées en bordure de zone** : aucune temporisation en v1 (même comportement iOS / Android) ? | **Oui**, mesurer pendant le spike et la bêta. | Une temporisation de quelques minutes n'existe pas sur iOS et créerait deux comportements. |
| **QL12** | **Règles Firestore** : acceptes-tu qu'on demande à `backend-data` (a) **R-LIEU-1** (réécrire un statut sans changer l'heure) **avant** le ticket Update, (b) **R-LIEU-3** (renouveler un consentement), (c) R-LIEU-2 si QL8 = oui ? Chaque déploiement avec ton « oui » explicite. | **(a) et (b) oui.** | Sans (a), pas de mise à jour manuelle après 24 h au même endroit sans mentir sur l'heure d'arrivée. |
| **QL13** | **Fraîcheur du statut** : pas de « battement de cœur » périodique en v1 ; le proche voit l'**heure d'arrivée ou de départ** (pas « mis à jour il y a ») ? | **Oui**, c'est honnête et non anxiogène ; pas d'écriture périodique. | Un battement de cœur coûterait 2 écritures par jour et par personne et exigerait R-LIEU-1. |
| **QL14** | **Cartes et recherche d'adresse** : iOS = MapKit ; Android = **MapLibre + OpenFreeMap** (gratuit, sans clé, sans garantie de service), géocodeur du système ; Google Maps (facturation) **écarté** sauf ton accord ? | **Oui à cette proposition** ; fournisseur de tuiles remplaçable par un réglage ; signal de bascule documenté (§11). | Google Maps = moyen de paiement + clé ; Nominatim / OSM direct = **interdits** par leurs conditions. |
| **QL15** | **Deuxième lieu en gratuit** : que montrer avant le ticket Premium ? | Message de limite (§10.3) puis **l'écran provisoire « Miff+ arrive bientôt »** d'`onboarding.md` §6.12 ; événement `paywall_shown` (`extra_trusted_place`) seulement si un écran s'affiche et si le consentement Analytics est donné. | Un vrai paywall exige la validation d'achat (Blaze). |
| **QL16** | **Modifier et supprimer un lieu** : modification autorisée (sauf le type), avertissement « change pour tous », suppression avec confirmation calme ? | **Oui.** | Interdire la modification forcerait supprimer et recréer. |
| **QL17** | **Un compte sur deux téléphones** : accepter la limite v1 (un vieux téléphone peut écrire « à la maison ») ? | **Oui, documentée** ; à revoir si les testeurs la rencontrent. | Un appareil de détection unique exigerait un champ ou une règle de plus. |
| **QL18** | **Journal local de détection** (builds internes et TestFlight seulement, sans coordonnées, purgé à la déconnexion) ? Une ligne « dernière détection » dans les builds publics ? | **Journal : oui en test ; rien en publication** (un journal serait un historique de trajets). | Le spike et la recette en dépendent. |
| **QL19** | **Ordre des chantiers** : spike (LOC-1) et lieux (LOC-2) d'abord, puis **ticket Update avant la détection**, puis LOC-3 ? | **Oui** (la mise à jour manuelle remplit le Feed sans autorisation d'arrière-plan et donne un rôle au bouton central). | Faire la détection avant l'Update retarde la première recette utile sur le Feed. |
| **QL20** | **Textes légaux et déclarations** : OK pour que `release-manager` mette à jour la politique (fournisseurs de cartes et de recherche, position des lieux visible, `consentVersion`), le formulaire Google et les étiquettes Apple **avant la bêta externe** ? | **Oui** (voir §18). | Sans cela, écart entre l'app et ses textes (motif de rejet). |

---

## 15. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Détection peu fiable sur ColorOS ou ailleurs** (optimisation de batterie, démarrage automatique) | **Fausse réassurance** : « à la maison » alors que la personne est partie | Spike avant développement ; état affiché ; guide ; auto-réparation ; honnêteté des textes ; QL1, QL2 |
| R2 | **Échec silencieux** : permission retirée app fermée, zones perdues après redémarrage, app forcée à s'arrêter | Statut figé et faux | Réarmement, tâche périodique, écran « Détection », heure d'arrivée affichée plutôt qu'une fraîcheur |
| R3 | **Revue Google Play** : formulaire « Background location », vidéo, divulgation ; revue des pistes de test fermées et ouvertes ; mise à jour exigeant une nouvelle déclaration | Bêta externe bloquée ou app retirée | Préparer tôt (§10.6, §18) ; fonction unique déclarée ; divulgation avant la fenêtre |
| R4 | **Revue Apple** (5.1.1, 5.1.2, 5.1.5, usage « Toujours ») | Rejet | Jamais bloquant ; amorce complète ; note de revue ; texte d'usage précis |
| R5 | **Propagation lente des lieux** (pas de notification) | Lieu non surveillé pendant des heures | Texte honnête ; relecture à l'ouverture, au réveil, périodique ; Blaze plus tard |
| R6 | **Usage détourné** : le créateur choisit des lieux surveillés chez tous, ajoutables à tout moment sans nouveau consentement ; aucun lieu propre au membre | Surveillance d'un proche ; contradiction avec les règles anti-stalkerware des stores | Avis à l'ouverture, texte de consentement explicite, arrêt en un geste, réciprocité ; option par lieu en v2 ; **risque assumé à ré-écrire par le PM** |
| R7 | **Adresse et position des lieux lisibles par tout porteur du code** (y compris l'adresse du domicile du créateur ou d'un tiers) | Fuite de domicile en cas de fuite du code | Adresse facultative ; texte ; renouvellement du code ; **risque assumé** (même famille que R2 du ticket Miff et R7 de l'Onboarding) |
| R8 | **Dépendance à un fournisseur de tuiles sans garantie** (OpenFreeMap) et au débit limité d'Apple | Carte indisponible | Formulaire utilisable sans carte ; fournisseur remplaçable ; signal de bascule |
| R9 | **Géocodeur Android absent ou capricieux** | Recherche indisponible | Repère sur la carte ; test sur l'OPPO |
| R10 | **Règle des 24 h** sur les réécritures de statut | Heure d'arrivée falsifiée ou mise à jour manuelle impossible | R-LIEU-1 avant Update |
| R11 | **Impossible de renouveler un consentement actif** | Texte modifié sans consentement renouvelé | R-LIEU-3 ; v2 livrée avant la bêta |
| R12 | **Deux téléphones, un compte** | Statut contradictoire | Limite documentée (QL17) |
| R13 | **Promesse « lieux illimités » intenable** (20 sur iOS, 100 sur Android) | Lieux non surveillés sans explication | Plafond doux ; état « n lieux non surveillés » ; reformulation Premium |
| R14 | **Batterie perçue** (« Miff vide ma batterie ») | Désinstallation | Aucun suivi continu ; mesure < 2 % par jour ; pas de service permanent en v1 |
| R15 | **iOS testable seulement à pied et par TestFlight** ; minutes macOS limitées (environ 200 par mois selon l'hypothèse du dépôt) | Cycle lent | Logique pure testée en CI ; journal local ; builds regroupés |
| R16 | **Rebonds et faux départs** | Statuts qui oscillent, écritures en excès | Une écriture par changement d'état ; disjoncteur ; mesure en bêta |
| R17 | **Horloge du téléphone décalée** | Écritures refusées | Heure plafonnée à l'heure actuelle ; abandon après 24 h |
| R18 | **Promesse de notifications dans le carrousel, les maquettes et la fiche** | Perte de confiance, rejet possible | Textes honnêtes (déjà exigés par `onboarding.md`) ; `aso-marketing` informé |
| R19 | **Mineurs sans âge minimum** et lieux choisis par un adulte | RGPD art. 8 ; règles des stores | **Risque assumé** (`data-model.md` §11, décision du 2026-10-23, pas de relecture juridique) |
| R20 | **Permissions retirées automatiquement après des mois d'inactivité** (Android 11+) | Détection arrêtée sans que la personne le sache | Relecture de l'état à chaque exécution ; bandeau non modal |
| R21 | **Garde-fous du dépôt modifiés** (iOS : contrôle CI, test de configuration ; Android : manifeste) | Régression qui laisserait passer une clé de localisation non voulue | Réécrire les garde-fous pour **exiger exactement** les clés prévues et interdire le reste ; revue de sécurité |
| R22 | **Aucun texte relu par un juriste** (décision du 2026-10-23) | Texte de consentement ou politique contestables | Risque assumé ; les textes ne prétendent jamais avoir été relus |

---

## 16. Critères d'acceptation

Codes : **PL** lieu, **LIM** limite gratuite, **CAR** carte et adresse, **SYN** propagation, **DET** détection, **STA** écriture du statut, **AUT** autorisations, **SAN** santé et fiabilité, **HOR** hors connexion et robustesse, **CON** consentement, **VIE** vie privée, **TRA** transverses, **AND** Android, **IOS** iOS, **SPK** spike, **PM** recette du PM, **QA** vérifications du QA. Les critères PL à TRA valent pour les deux plateformes.

### Lieux
- [ ] **PL1** Seul le créateur voit « Ajouter un lieu », « Modifier » et « Supprimer » ; un membre voit la liste et le détail en lecture seule.
- [ ] **PL2** Le formulaire contient nom, position (carte), adresse facultative et périmètre, **aucun champ « Type »** ; tout lieu créé porte `type = "trusted"`.
- [ ] **PL3** « Enregistrer le lieu » est grisé tant que le nom (espaces de début et de fin retirés) a moins de 2 caractères ou que la position n'est pas choisie ; au-delà de 60 caractères, saisie refusée avec « Limite de 60 caractères atteinte. ».
- [ ] **PL4** La création est **une seule écriture** avec toutes les clés (`name`, `type`, `address`, `lat`, `lng`, `radiusM`, `priority`, `createdBy`, `createdAt`, `updatedAt`) ; `priority = null` ; `lat` et `lng` à 5 décimales ; `address = null` si vide ; le succès n'apparaît **qu'après l'accusé du serveur**.
- [ ] **PL5** Modifier change nom, adresse, position, périmètre ; jamais `type`, `createdBy`, `createdAt` ; un changement de position ou de périmètre affiche « Ce lieu change pour tous les membres de la Miff. » **avant** l'enregistrement.
- [ ] **PL6** Supprimer demande une confirmation au ton calme puis envoie une écriture ; le lieu disparaît de la liste du créateur sans attendre les proches.
- [ ] **PL7** **Créer, modifier ou supprimer un lieu ne demande aucune autorisation de localisation** ; la permission « pendant l'utilisation » n'est demandée qu'à l'appui sur « Utiliser ma position actuelle ».
- [ ] **PL8** La liste montre le nom (et la catégorie si QL8) ; le détail montre l'adresse **si elle est renseignée**, la carte, le périmètre et « Voir sur la carte », qui ouvre l'application de cartes du système (aucune clé).
- [ ] **PL9** États vides distincts pour le créateur et pour le membre (§10.3) ; hors connexion, la liste se lit depuis le cache avec une mention calme.
- [ ] **PL10** La suppression d'un lieu est exposée sous forme de **composant appelable sans interface** (ticket Premium, suppression de Miff), avec ses propres tests.

### Limite gratuite
- [ ] **LIM1** Un créateur gratuit qui a déjà 1 lieu et appuie sur « Ajouter » voit le message de limite ; **aucune écriture n'est envoyée** (test : zéro appel réseau).
- [ ] **LIM2** En gratuit, le lieu est écrit avec l'identifiant `p1` et le type `trusted` ; après suppression de `p1`, un nouveau lieu peut être créé.
- [ ] **LIM3** Avec `entitlements` premium actif (compte de test), identifiants automatiques et plusieurs lieux sont possibles, dans le plafond doux (QL9).
- [ ] **LIM4** Un membre gratuit d'une Miff dont le créateur est premium reçoit et surveille **tous** les lieux.
- [ ] **LIM5** `paywall_shown` / `paywall_dismissed` avec `trigger = extra_trusted_place` ne partent que si un écran de paywall s'affiche **et** que le consentement Analytics est donné, sans nom, type ni emplacement.

### Carte et adresse
- [ ] **CAR1** La recherche d'adresse part **à l'appui sur « Rechercher » ou à la validation du clavier**, jamais à chaque frappe, avec le même comportement sur Android et iOS.
- [ ] **CAR2** La carte montre un repère et le cercle du périmètre ; déplacer le repère et le curseur met à jour le cercle ; **chaque valeur est aussi saisissable sans la carte** (accessibilité).
- [ ] **CAR3** L'attribution cartographique est visible sur la carte.
- [ ] **CAR4** Aucun résultat, recherche indisponible, carte indisponible, hors connexion : messages du §10.3, le formulaire reste utilisable par l'autre moyen.
- [ ] **CAR5** Aucune clé secrète dans le dépôt ni dans l'application ; aucun téléchargement anticipé de tuiles ; le fournisseur de tuiles se change par **un seul réglage**.
- [ ] **CAR6** **Liste blanche de domaines réseau** : l'app n'appelle que les services de carte et de recherche **déclarés dans ce ticket et dans la politique de confidentialité** (test de configuration réseau et revue).
- [ ] **CAR7** L'adresse saisie ne part que vers le service de recherche du système ; elle n'apparaît dans aucun journal, événement Analytics ni rapport de plantage.

### Propagation des lieux
- [ ] **SYN1** Sur l'écran de la Miff ouvert, un lieu ajouté, modifié ou supprimé par le créateur apparaît **en direct** chez un membre (test à deux appareils).
- [ ] **SYN2** À l'ouverture de l'app et au retour au premier plan, les lieux de chaque Miff partagée sont relus et la surveillance est mise à jour s'ils diffèrent.
- [ ] **SYN3** Une relecture en arrière-plan a lieu **au moins toutes les 12 h** sur Android ; sur iOS, le délai réel est mesuré au spike et documenté.
- [ ] **SYN4** Un lieu ajouté, modifié ou supprimé produit **un avis discret** non bloquant à l'ouverture suivante, **une fois** par lieu, sans mot de la liste interdite.
- [ ] **SYN5** Un événement reçu pour un lieu absent de la dernière relecture est **ignoré** : aucune erreur, aucune écriture.
- [ ] **SYN6** La relecture coûte **au plus N + 2** lectures par Miff (mesure).

### Détection
- [ ] **DET1** Un lieu armé avec une autorisation suffisante est détecté à l'arrivée et au départ **sans ouvrir l'app** (scénarios SPK et PM).
- [ ] **DET2** Arrivée et départ produisent les valeurs de `data-model.md` §3 (voir §5.6) : `in_trusted` avec `escalationDueAt = null` ; `out` avec le lieu quitté.
- [ ] **DET3** Déjà dans le lieu à l'armement : traité comme une arrivée ; dehors : **aucun `out` n'est écrit**, le statut reste `unknown`.
- [ ] **DET4** Deux lieux enchaînés ou qui se chevauchent : le statut final est cohérent (quitter A en restant dans B donne `in_trusted` sur B).
- [ ] **DET5** Des événements en double ou dans le désordre n'écrivent pas deux fois le même état (test unitaire, mêmes cas sur les deux plateformes).
- [ ] **DET6** Seules les Miffs où le partage est **actif** reçoivent une écriture ; partage arrêté : surveillance de ses lieux arrêtée ; reprise : réarmée.
- [ ] **DET7** Quitter la Miff, être retiré, Miff supprimée, **déconnexion**, suppression de compte : surveillance liée arrêtée ; sur appareil, **plus aucun événement ni écriture** ensuite.
- [ ] **DET8** Plusieurs Miffs : **une écriture indépendante par Miff** ; l'échec de l'une n'empêche pas l'autre.
- [ ] **DET9** Aucun suivi continu : aucun service permanent, aucune notification permanente, aucune mise à jour de position répétée (vérification sur appareil).
- [ ] **DET10** L'app **ouverte** détecte aussi (même mécanisme, mêmes écritures).
- [ ] **DET11** Au-delà de la limite système, la sélection est **déterministe et identique sur tous les téléphones** (`priority` puis `createdAt`) ; les lieux non retenus sont signalés (§10.4) ; aucune écriture serveur.
- [ ] **DET12** Lieu modifié ou supprimé pendant que la personne y était : statut `unknown` (`placeId = null`) si la présence n'est pas reconfirmée.

### Écriture du statut
- [ ] **STA1** Chaque écriture porte **toutes** les clés et respecte `validStatus` ; `updatedAt` est l'heure serveur.
- [ ] **STA2** Aucune écriture ne contient coordonnées, adresse ni distance ; `placeId` est un identifiant seulement.
- [ ] **STA3** `updateText` et `hasPhoto` du statut précédent sont **conservés** par une écriture de détection.
- [ ] **STA4** `presenceAt` est l'heure de l'événement système, **plafonnée à l'heure actuelle** ; une écriture de plus de 24 h de retard est abandonnée proprement, sans boucle.
- [ ] **STA5** `locationAccess` suit la correspondance du §5.7.
- [ ] **STA6** `batteryPct` est le niveau du moment, `null` si indisponible.
- [ ] **STA7** Un changement d'autorisation constaté met à jour `locationAccess` (sous réserve de R-LIEU-1 si le statut a plus de 24 h).
- [ ] **STA8** Le disjoncteur d'écritures automatiques par jour et par appareil est testé unitairement.

### Autorisations
- [ ] **AUT1** Ce ticket ne demande **jamais** l'autorisation d'arrière-plan ; il lit l'état fourni par ONB-4 / ONB-8.
- [ ] **AUT2** Chaque ligne du tableau du §5.7 est vérifiée sur appareil : détection active ou non comme annoncé, bon texte dans l'écran « Détection de tes lieux », bonne valeur de `locationAccess`.
- [ ] **AUT3** Autorisation retirée dans les réglages : à la prochaine exécution, état relu, bandeau non modal, **aucun plantage, aucune fenêtre automatique** ; l'app reste utilisable.
- [ ] **AUT4** « Plus tard » ou refus : l'app reste pleinement utilisable (mise à jour manuelle) ; aucune fenêtre répétée.

### Santé et fiabilité
- [ ] **SAN1** L'écran « Détection de tes lieux » est accessible depuis Réglages > Confidentialité et depuis le bandeau non modal ; il affiche l'un des états du §10.4 avec **une** action.
- [ ] **SAN2** La surveillance est **réarmée** : au lancement, au retour au premier plan, au redémarrage du téléphone (Android : **sans ouvrir l'app**), après une mise à jour de l'app, au changement d'autorisation, au retour de la localisation du téléphone, à chaque synchronisation de lieux.
- [ ] **SAN3** Une auto-vérification périodique (au plus 12 h sur Android) réarme les zones et relit les autorisations.
- [ ] **SAN4** Les causes détectables (économiseur de batterie, optimisation de la batterie, actualisation en arrière-plan iOS, localisation du téléphone coupée, précision réduite) sont affichées ; le **guide fabricant** couvre le reste.
- [ ] **SAN5** Le guide fabricant n'apparaît jamais au premier lancement, est fermable, non répétitif, et n'agit pas à la place de la personne.
- [ ] **SAN6** Aucun texte de cet écran n'est anxiogène (relecture §10).

### Hors connexion et robustesse
- [ ] **HOR1** Créer, modifier, supprimer un lieu exige l'accusé du serveur ; en mode avion, message neutre et **rien en file d'attente**.
- [ ] **HOR2** Un événement détecté hors connexion est écrit au retour du réseau avec son **heure réelle**.
- [ ] **HOR3** Un double appui ne produit qu'une opération ; rotation et arrière-plan ne produisent pas de doublon.
- [ ] **HOR4** Horloge du téléphone décalée de +10 min : aucune boucle d'écritures refusées.
- [ ] **HOR5** Quota ou App Check refusés : message « Pas de connexion pour le moment. », jamais le mot « quota » ; **une écriture déclenchée en arrière-plan** passe App Check (essai en mode strict).

### Consentement
- [ ] **CON1** Le texte de consentement v2 (§10.1) est affiché **avant** le bouton, **entièrement lisible et non replié** (le défilement de l'écran est toléré à taille de texte maximale, le bouton restant sous le texte) ; « Annuler » n'écrit rien.
- [ ] **CON2** `consentVersion` est une **constante unique** (par langue), identique sur Android et iOS, **strictement supérieure** à celle de MIFF-1 ; un test échoue si le texte change sans changer la version.
- [ ] **CON3** « Reprendre le partage » affiche le **même texte v2** et écrit une nouvelle date et version.
- [ ] **CON4** Aucun statut n'est écrit et aucune surveillance n'est armée avant le consentement **et** une autorisation suffisante.
- [ ] **CON5** Membre entré avec une version antérieure : comportement conforme à la réponse à QL3 (testé avec deux comptes).
- [ ] **CON6** Les textes de l'app, la politique de confidentialité et les déclarations des stores disent la même chose (revue `release-manager`, avant la bêta externe).

### Vie privée
- [ ] **VIE1** Aucune coordonnée de la personne n'est écrite, journalisée, envoyée en Analytics ou Crashlytics, ni copiée dans un message : revue de code et **analyse statique** (le gestionnaire d'événements ne lit pas la position fournie avec l'événement).
- [ ] **VIE2** La position n'est lue que sur appui sur « Utiliser ma position actuelle » (une fois, au premier plan) et, si retenue au spike, pour la vérification locale ponctuelle ; elle n'est jamais stockée.
- [ ] **VIE3** Le journal local de détection n'existe **que** dans les builds internes et TestFlight, sans coordonnées, purgé à la déconnexion (test de présence dans le build de publication : absent).
- [ ] **VIE4** Les lieux sont purgés du cache à la perte d'accès, à la déconnexion et à la suppression de compte (contrat de purge, `data-model.md` §8).
- [ ] **VIE5** Aucun nouvel événement Analytics (liste blanche inchangée hors `paywall_*` existants) ; aucun nom de paramètre interdit.
- [ ] **VIE6** Aucun écran de ce ticket ne donne au créateur d'information sur un membre que ce membre ne voit pas aussi sur le créateur.

### Transverses
- [ ] **TRA1** Tous les textes en français et en anglais ; clés `place_`, `detection_`, `permission_` identiques sur les deux plateformes (contrôle de parité, préfixe `detection_` ajouté au contrat) ; pluriels par le mécanisme de la plateforme.
- [ ] **TRA2** Accessibilité : alternative sans carte (CAR2), lecteur d'écran, zones tactiles d'au moins 48 dp / 44 pt, texte agrandi sans chevauchement, droite-à-gauche, focus après chaque dialogue.
- [ ] **TRA3** App Check initialisé avant toute requête ; aucun secret dans le dépôt.
- [ ] **TRA4** **Aucune règle modifiée par les PR de ce ticket** ; R-LIEU-1, R-LIEU-2, R-LIEU-3 sont des PR `backend-data` séparées, avec tests et mesure des lectures.
- [ ] **TRA5** Mesure des lectures : relecture des lieux ≤ N + 2 par Miff ; ouverture de l'écran de la Miff : au plus N + 4 (inchangé).
- [ ] **TRA6** Tous les textes passent la relecture « est-ce que je dirais cela à un proche inquiet ? » (PM8).

### Android
- [ ] **AND1** Manifeste de publication : autorisations de **position précise, position approximative (exigée avec la précision à partir d'Android 12 : [À vérifier]), arrière-plan** et démarrage du téléphone ; **aucune autre** ; pas d'autorisation de service au premier plan en v1 ; contrôle CI du manifeste de publication.
- [ ] **AND2** Les zones ont un rayon d'au moins 100 m ; le nombre de zones ne dépasse jamais 100.
- [ ] **AND3** Test sur l'OPPO Find X5 Lite : scénarios S1 à S14 du spike rejoués avec le moteur complet ; relevé joint au rapport.
- [ ] **AND4** Redémarrage de l'OPPO sans ouverture de l'app : les zones sont réinscrites (journal de test).
- [ ] **AND5** Divulgation visible avant la demande (§10.2), formulaire et vidéo Play préparés par `release-manager` **avant** toute piste de test fermée ou ouverte.

### iOS
- [ ] **IOS1** `Info.plist` : exactement `NSLocationWhenInUseUsageDescription` et `NSLocationAlwaysAndWhenInUseUsageDescription` (français et anglais, §10.2) ; **aucune autre clé de localisation** ; mode d'arrière-plan « location » **non déclaré** sauf nécessité démontrée au spike ; les garde-fous du dépôt sont réécrits **dans la même PR** pour exiger ces clés et interdire le reste.
- [ ] **IOS2** `PrivacyInfo.xcprivacy` et étiquettes de confidentialité d'App Store Connect décrivent la donnée réellement collectée (état de présence dérivé de la position, coordonnées des lieux).
- [ ] **IOS3** Jamais plus de 20 régions ; l'échec d'enregistrement (saturation) est géré sans plantage.
- [ ] **IOS4** TestFlight sur l'iPhone 11 Pro : scénarios applicables du spike ; comportement après **fermeture forcée** et après **redémarrage** constaté et documenté.
- [ ] **IOS5** Actualisation en arrière-plan désactivée : détectée et affichée.

### Spike (LOC-1, LOC-4)
- [ ] **SPK1** Build de débogage seulement ; **aucune écriture Firestore** ; permissions de localisation absentes du build de publication (contrôle CI).
- [ ] **SPK2** Les scénarios S1 à S14 sont exécutés au moins 3 fois chacun ; relevé complet joint.
- [ ] **SPK3** Le rapport donne : taux de détection et délais par scénario, batterie sur 24 h, comportement avec et sans réglages du fabricant, recommandation go / no-go selon §12.2 ; **le PM décide**.
- [ ] **SPK4** Les options du §12.2 (vérification ponctuelle locale, réveil complémentaire, écriture en arrière-plan) sont mesurées **si** le taux est insuffisant, avec leur coût en batterie et en confidentialité.

### Ce que le PM peut vérifier lui-même sur téléphone
- [ ] **PM1** Créer un lieu de 150 m autour de chez soi sans aucune autorisation de localisation ; voir le message « Tes proches le prendront en compte… ».
- [ ] **PM2** Avec un second compte : voir le lieu apparaître (écran de la Miff ouvert) et l'avis discret à l'ouverture suivante.
- [ ] **PM3** Accorder « toujours », marcher à 400 m et revenir ; lire le statut sur le second téléphone ou dans la console ; noter les écarts (scénario §12.3).
- [ ] **PM4** Balayer l'app des récentes, redémarrer le téléphone : refaire une sortie.
- [ ] **PM5** Retirer l'autorisation dans les réglages : bandeau calme, aucune fenêtre ; rouvrir l'écran « Détection de tes lieux ».
- [ ] **PM6** Créer un deuxième lieu en gratuit : message de limite, rien d'écrit.
- [ ] **PM7** Se déconnecter puis sortir du lieu : plus aucun statut écrit.
- [ ] **PM8** Relire chaque texte à voix haute : est-ce que je le dirais à un proche inquiet ?

### Pour le QA (`qa-tester`)
- [ ] **QA1** Tableau de couverture critère par critère, avec les cas à deux comptes et les scénarios du §12.
- [ ] **QA2** Mêmes scénarios, mêmes textes et mêmes états sur Android et iOS.
- [ ] **QA3** Revue des journaux de la build de débogage : aucune coordonnée, aucune adresse, aucun nom de lieu dans les événements Analytics.
- [ ] **QA4** Les tests de règles du dépôt (`backend/tests`) passent toujours ; les lectures de règles de chaque opération restent dans la marge.

### Définition de « terminé »
Tous les critères de la plateforme concernée cochés ; revue de code en contexte neuf et revue de sécurité faites ; QA faite ; recette du PM faite ; « prêt à merger » validé par un « oui » explicite du PM ; fusion par Claude.

---

## 17. Maquettes à exporter ou à dessiner

Les exports doivent être rangés dans `design/exports/<flux>/` avec un README de repérage (`CLAUDE.md`, décision du 2026-10-28). **Rien n'est à exporter avant d'avoir répondu aux questions bloquantes de §14** (le texte de consentement et les états de l'écran de détection en dépendent).

### 17.1 Déjà dessinées dans Figma (fichier original « Miff - App »), **jamais exportées** : à exporter en priorité
| Page Figma | Frames | Dossier suggéré | Pourquoi |
|---|---|---|---|
| **« 📍 Lieux de confiance » (`13:1947`)** | **Les 8 frames** | `design/exports/lieux/` | Seule source de « Nouveau lieu » (nom, adresse, curseur, type) et de la suppression d'un lieu ; je n'ai pu relire que sa description |
| **« 🟩 Pop ins » (`69:12`)** | Les 3 frames (confirmations, dont probablement la suppression d'un lieu) | `design/exports/pop-ins/` | Écran « vu dans les maquettes » pour la suppression |
| « 📸 Update » (`13:1950`) | Les 10 frames | `design/exports/update/` | Utile au contrat avec le ticket Update (le repli manuel) ; pas bloquant ici |

Déjà disponibles et lus : `design/exports/profil/` (liste « Lieux de la Miff »), `design/exports/onboarding-original/` (écrans 20 à 22, amorce de localisation : **à remplacer**), `design/exports/feed/` (statuts), `design/exports/miff/` (dépôt principal : créer et rejoindre).

### 17.2 N'existent nulle part : à dessiner par `design-system` dans « Miff - Claude », puis à exporter par toi
1. **Formulaire « Nouveau lieu / Modifier le lieu »** : recherche d'adresse, « Utiliser ma position actuelle », carte avec repère et cercle, curseur de périmètre, mention de partage, états (vide, recherche en cours, aucun résultat, recherche indisponible, carte indisponible, erreur, enregistrement, hors connexion, avertissement de modification).
2. **Carte plein écran** pour ajuster le repère et le cercle ; style du repère et du cercle (tokens), attribution.
3. **Détail d'un lieu** (lecture) avec « Voir sur la carte » et actions du créateur.
4. **Liste des lieux** (écran de la Miff et onglet Profil), avec indicateur « surveillé sur ton téléphone » ; **sans le doublon « Ajouter »** de la maquette Profil.
5. **Message de limite gratuite** (deuxième lieu) et état « plafond doux ».
6. **Confirmation de suppression d'un lieu** (ton calme).
7. **Avis aux membres** (lieu ajouté, modifié, supprimé), bandeau non modal.
8. **Écran « Détection de tes lieux »** : les 12 états du §10.4.
9. **Bandeaux non modaux** (autorisation retirée, position approximative, lieux non surveillés, optimisation du fabricant).
10. **Guide fabricant** (étapes, boutons) pour ColorOS, et variante générique.
11. **Cartes de consentement v2** (rejoindre, créer, reprendre le partage), avec le texte long et la taille de texte maximale.
12. **Amorce d'arrière-plan v2** (Android et iOS), **second écran Android 11+**, états après refus, position approximative (déjà prévus par `onboarding.md` §3.2, **non dessinés**) ; remplace les écrans 20 à 22 (un seul bouton, erreur rouge).
13. **Icônes de catégories** si QL8 = oui.
14. Côté proche (ticket Feed, mentionné pour cohérence) : états « à {lieu} depuis {heure} », « a quitté {lieu} à {heure} », « détection limitée », « partage arrêté ».
15. **Écran de diagnostic interne** (builds de test, liste d'événements sans coordonnées) : dessin libre, pas de recette design.

---

## 18. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.

**Avant LOC-1 (spike Android)**
1. Répondre à **QL1, QL2, QL3** au minimum, et à QL14 (cartes) avant LOC-2.
2. Fixer **un lieu d'essai** (par exemple autour de chez toi) et prévoir des **créneaux de marche** (S1 à S14, §12.2) avec l'OPPO ; noter les heures de franchissement.
3. Régler l'OPPO selon S1 (réglages par défaut) puis S6 (réglages recommandés) ; relever les **libellés réels de ColorOS** (batterie, démarrage automatique, verrouillage).
4. Avoir **deux comptes de test** et, pour la recette de bout en bout, un **second téléphone** (l'iPhone après LOC-6).

**Avant LOC-2 et LOC-3**
5. **Exporter** les pages Figma du §17.1 et valider les écrans dessinés du §17.2.
6. Autoriser la demande à `backend-data` de **R-LIEU-1, R-LIEU-3** (et R-LIEU-2 si QL8) : tests, mesure de lectures ; **déploiement seulement avec ton « oui » explicite** (`docs/dev/deploiement-regles.md`).
7. Valider les textes du §10.

**Avant la bêta externe**
8. **Google Play Console** : formulaire de localisation en arrière-plan (fonction unique), **vidéo de 30 s**, divulgation dans l'app, politique de confidentialité à jour ; **toute piste de test fermée ou ouverte est concernée**. Préparé par `release-manager`, **soumis par toi**.
9. **App Store Connect** : étiquettes de confidentialité (position), note de revue (§10.6).
10. **Politique de confidentialité et CGU** (FR et EN, ensemble) : fournisseurs de cartes et de recherche, position **du lieu** visible par les membres, « Utiliser ma position » ne stocke que les coordonnées du lieu, `consentVersion`. À faire par `release-manager`, **sans prétendre à une relecture juridique** (décision du 2026-10-23).
11. Prévenir `aso-marketing` : aucune promesse de notification ni de « suivi en temps réel ».
12. Décider de **Blaze** (notifications, propagation instantanée des lieux) : hors de ce ticket.

Aucun moyen de paiement n'est requis par les choix proposés. Contact et éditeur provisoires inchangés (Tomessa MFOUKA, `tomessa.mfouka@gmail.com`).

---

## 19. Dépendances

| Avec | Nature |
|---|---|
| **INIT-2, INIT-4** | Socle Android et iOS, App Check, textes, TestFlight (fusionnés) |
| **AUTH-1, AUTH-3** | Session, routage, déconnexion avec purge (arrêt de la surveillance) |
| **ONB-3, ONB-7** | Onglet Profil, Réglages > Confidentialité (entrée de l'écran « Détection ») |
| **ONB-4, ONB-8** | Amorce et demandes du système : **absentes du dépôt** ; LOC-3 et LOC-6 en dépendent |
| **MIFF-1, MIFF-3** | Écran de la Miff, consentement (texte révisé ici), statut initial, perte d'accès, arrêt du partage |
| **`backend-data`** | Mesures de lectures ; R-LIEU-1, R-LIEU-2, R-LIEU-3 ; mise à jour de `data-model.md` après décision |
| **Update (à écrire)** | Écrit le même statut ; dépend de R-LIEU-1 ; ordre en QL19 |
| **FEED-2** | Affichage des statuts ; textes de §10.5 ; sans lui, recette par la console |
| **Premium** | Paywall du deuxième lieu, fin d'abonnement ; réutilise la suppression sans écran |
| **`design-system`** | §17.2 |
| **`release-manager`** | Politique, déclarations, vidéo, étiquettes (§10.6, §18) |
| **`aso-marketing`** | Aucune promesse de notification |
| **`qa-tester`** | Scénarios du §12, relevé du spike |
| **Actions du PM** | §18 |

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`, ni les textes légaux. Aucun texte légal ou de store n'est présenté comme relu par un juriste. Aucune commande Firebase n'a été exécutée.*
