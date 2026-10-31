# Ticket — Lieux de confiance et Tracking (détection par zones) : le cœur du produit

**Statut** : **version 2 du challenge**, réécrite le 2026-11-01 pour suivre **à la lettre** les deux tickets Notion « Lieux de confiance » et « Tracking » (statut Notion : « Spec en attente de review IA ») et les **décisions du PM du 2026-11-01** sur QL1 à QL20. À valider par le PM avant tout développement (aucun développement lancé, aucun statut Notion modifié).
**Rédigé le** : 2026-10-31 (version 1), réécrit le 2026-11-01 (agent `product-specs`).
**Règle de hiérarchie** (décision du PM) : **Notion + décisions du PM > ce ticket > reste du dépôt**. Quand une source du dépôt contredit Notion, elle est listée au §5 pour être alignée ; elle n'est pas modifiée ici.
**Sources lues** : les deux tickets Notion (texte fourni par le PM le 2026-11-01, **non relus dans Notion**) ; les 7 maquettes `design/exports/lieux/` (README et PNG lus) ; `design/exports/onboarding-original/` écrans 19 à 22 (permissions) ; `CLAUDE.md` ; `docs/specs/` ; `docs/tickets/` (miff, onboarding, feed-profil-reglages, initialisation-technique) ; `docs/legal/` ; `docs/contracts/` ; `backend/data-model.md` et `backend/firestore.rules` (fonctions `validPlaceShape`, `validPlaceCreate`, `validPlaceUpdate`, `validStatus`, `validSharingChange`).
**Sources web (page et date de lecture)** : guide Apple Region Monitoring (archive) et documentation Android Geofencing / Location permissions (2026-10-31) ; aide Google Play « Background location » (2026-10-31) ; tarifs Google Maps Platform (page du 2026-10-30, lue le 2026-10-31) ; politiques OpenStreetMap tuiles et Nominatim, OpenFreeMap (2026-10-31) ; **Photon** (`photon.komoot.io` et README GitHub `komoot/photon`), **Geoapify** (page des tarifs, conditions version 5 du 2024-02-02, doc de l'autocomplétion), **Apple App Review Guidelines** (2026-11-01). Les pages Apple `developer.apple.com/documentation` (MKLocalSearchCompleter, Core Location récent) et la liste des champs de `request.auth.token` des règles Firestore **n'étaient pas lisibles par l'outil** : ces points sont marqués **[À vérifier]**. Aucune CGU n'est devinée : ce qui n'est pas lu est dit non lu.
**Convention** : [Notion] = repris du ticket Notion ; [Décidé] = décision du PM (source citée) ; [Proposé] = ma proposition ; [À vérifier] = non confirmé. Je ne tranche aucune décision produit ou légale.

---

## 0. Ce qui change par rapport au premier challenge

| # | Sujet | Premier challenge (2026-10-31) | Maintenant (Notion et décisions du 2026-11-01) |
|---|---|---|---|
| 1 | **Spike de fiabilité OPPO** (QL1) | Spike obligatoire avant la détection (go / no-go) | **Supprimé** : implémentation directe, **pour tous les appareils** ; le PM teste ensuite et fait des retours. Aucun seuil, aucune porte. Les scénarios de marche restent comme **plan de recette**, non bloquants. |
| 2 | **Plan B ColorOS** (QL2) | Guide fabricant, notification permanente en option | **Supprimé** : on considère que ColorOS ne bloquera pas ; ajustement après test. Écran « Détection de tes lieux » et guide fabricant **sortis du périmètre** (non demandés par Notion). |
| 3 | **Consentement v2** (QL3) | Nouveau texte versionné, re-consentement | **Abandonné** : « j'assume le risque ». Le texte de consentement actuel reste. **Incohérence constatée** avec l'écran d'autorisation du ticket Tracking : §5 #2, risque R3. |
| 4 | **Permission « Toujours »** | Jamais bloquante, « Plus tard » toujours présent, amorce avant la fenêtre | **Obligatoire et bloquante** [Notion Tracking] : Android, l'écran ne se quitte qu'avec position précise + arrière-plan ; iOS, l'onboarding ne se termine pas sans « Toujours ». Écran réaffiché après connexion ou changement de choix. **Contredit** Onboarding Q2 / Q3 et Apple 5.1.2(i) (texte lu) : §5 #1, R1. QL10 devient sans objet. |
| 5 | **Surveillance au-delà des limites** (QL9) | Plafond doux de 10 lieux par Miff, sélection par ordre de création | **Supprimé.** Mécanisme [Notion Tracking] : **les 20 lieux les plus proches** de la position, recalculés à l'ouverture, au passage en arrière-plan, sur déplacement (Android : plus de 250 m ; iOS : changement de position significatif) et à l'entrée dans une zone. **20 aussi sur Android** (Notion) alors que la limite d'Android est 100 : à confirmer (§16). |
| 6 | **Limite gratuite** (QL9, QL15) | 1 lieu | **2 lieux** en gratuit, **illimité** en premium. « 1 lieu » est écrit dans `CLAUDE.md`, les règles, le modèle de données, les CGU, la page web… : liste complète au §9.4. |
| 7 | **Position enregistrée** | « Aucune coordonnée de la personne ne quitte le téléphone » | Notion dit « **on enregistre sa localisation** ». **Tranché par le PM (2026-11-01) : sur le téléphone seulement.** Aucune position envoyée à Firestore ni stockée côté serveur ; seul le statut de présence nommé est écrit. Conséquences (critères, politique, consentement, stores) au §7. |
| 7 bis | **Propagation lente des lieux, faux départs, règles** | Questions ouvertes | **Décidé le 2026-11-01** : propagation lente **acceptée pendant la bêta** avec texte honnête dans l'app ; **aucune temporisation** des faux départs en v1 ; évolutions de règles « statut de plus de 24 h » (R-LIEU-1) et « renouvellement d'un consentement actif » (R-LIEU-3) **acceptées**, déploiement **seulement sur « oui » explicite** du PM le moment venu. |
| 8 | **Formulaire du lieu** | Nom, adresse facultative, position, périmètre 100 à 1 000 m défaut 150 m | [Notion] **Adresse obligatoire** (QL6) ; nom 2 à 50 ; **périmètre 50 à 800 m par pas de 50, défaut 100 m** (QL7) ; **type** confiance / danger ; **catégorie** Maison, Travail, École, Sport, Chez un proche, Loisir, Autre (QL8) ; **Miffs associées (plusieurs)** ; suggestions d'adresse dès 3 caractères. |
| 9 | **Type du lieu** | Type absent (zone à risque non livrée, Q10 du 2026-10-30) | Type **dans le formulaire** [Notion, maquettes] et **modifiable après création, même le type** (QL16). **Contredit** Q10 et les règles (type immuable) : §5 #6, #7. |
| 10 | **Un lieu, plusieurs Miffs** | Un lieu = un document d'une Miff | [Notion] « Miff associé (liste de Miffs) » : **le modèle actuel ne le permet pas tel quel** ; solution proposée au §9.3. |
| 11 | **Autocomplétion d'adresse** (QL14) | Recherche au clic, géocodeur du système | **Suggestions dès 3 caractères, API 100 % gratuite et mondiale** [Notion] : Nominatim exclu (interdit), options vérifiées au §12 ; proposition Android : **Photon** (sans clé) ; iOS : `MKLocalSearchCompleter` **[À vérifier]**. |
| 12 | **Lieux ajoutés après coup** (QL4) | Avis discret aux membres | « Déjà tranché, aucune évolution » : **aucun avis, aucune option** par lieu. Lieux armés dès qu'ils sont relus. |
| 13 | **Un compte, un seul appareil** (QL17) | Limite v1 documentée | **Nouvelle exigence** : connexion sur un appareil = déconnexion des autres. **Sous-ticket séparé** `docs/tickets/session-appareil-unique.md`. |
| 14 | **Ordre des demandes** | Localisation avant notifications (le PM pouvait inverser) | [Notion Tracking] **notifications d'abord, puis localisation**. |
| 15 | **Journal local de détection** (QL18) | Proposé | **Oui**, builds internes et TestFlight seulement, sans coordonnées. |
| 16 | **Textes légaux** (QL20) | Mise à jour par `release-manager` | **Oui, sans relecture juridique** : le risque reste écrit comme assumé, jamais comme « à valider ». |
| 17 | **Découpage** | LOC-0 à LOC-6 avec spike en tête | §14 : lieux, puis Tracking ; plus de spike ; sous-ticket session à part. |
| 18 | **Maquettes** | Formulaire « Nouveau lieu » à exporter | **Reçues** (7 PNG) ; les écrans d'autorisation notifications et localisation **existent** (`onboarding-original` 19 à 22) pour Android ; liste des manques mise à jour au §18. |

---

## 1. Résumé pour le PM

En une phrase : le créateur d'une Miff **pose ses lieux sur une carte** (nom, adresse choisie dans des suggestions, rayon, type, catégorie, Miffs concernées) ; le téléphone de chaque membre **surveille les 20 lieux les plus proches** de lui et **écrit un statut** (« dans le lieu X », « hors lieu ») chez les Miffs où il partage, **sans ouvrir l'app**.

Ce que les tickets Notion rendent nécessaire et que le dépôt ne permet pas encore (détail aux §5 et §9) :
1. **Évolutions de règles Firestore obligatoires** : limite gratuite à 2 lieux, catégorie, type modifiable, session unique ; le client ne peut pas les contourner. Elles se déploient avec ton « oui » explicite.
2. **Un lieu dans plusieurs Miffs** : proposition d'un document par Miff avec le même identifiant (§9.3).
3. **« Toujours » obligatoire** : choix assumé, mais Apple écrit qu'une app **ne peut pas exiger** la localisation ni les notifications pour être utilisée (guideline 5.1.2(i), lue le 2026-11-01) : risque de rejet iOS **élevé** (R1) ; un interrupteur de configuration à la compilation est proposé pour pouvoir relâcher sans réécrire.
4. **« On enregistre sa localisation »** : **décidé par le PM : sur le téléphone seulement** (§7). Aucune position n'est collectée côté serveur ; seul le statut de présence nommé est écrit.
5. **Suggestions d'adresse gratuites et mondiales** : possible, sans garantie de service (Photon) ; aucune option n'est à la fois gratuite, sans clé, avec contrat de service et conforme à l'autocomplétion. L'adresse étant obligatoire, **si les suggestions tombent, plus aucun lieu ne peut être créé** (R6).
6. **Un compte, un appareil** : mécanisme sans Cloud Function proposé dans un sous-ticket séparé ; limite : un ancien appareil **hors ligne** ne peut pas être déconnecté avant son retour en ligne.

**Taille globale** : XL (§14). **Coût** : 0 € avec les choix proposés (§12) ; aucun moyen de paiement requis.

---

## 2. Périmètre

### Dans le périmètre
- **Formulaire « Nouveau lieu / Modifier le lieu »** (maquettes `design/exports/lieux/`) : création, modification **de tous les champs, type compris**, suppression avec confirmation.
- **Liste et détail des lieux** (lecture pour les membres), point d'entrée depuis l'écran de la Miff et l'onglet Profil.
- **Limite gratuite : 2 lieux** ; premium illimité ; blocage du 3e avec message et écran provisoire (QL15).
- **Association à plusieurs Miffs** créées par la personne.
- **Tracking** [Notion] : écrans d'autorisation bloquants (notifications puis localisation), lecture et usage de la position, **sélection des 20 lieux les plus proches**, surveillance par zones, **écriture du statut**, état de la permission (`locationAccess`), nettoyage (partage arrêté, perte d'accès, déconnexion, session remplacée).
- **Journal local de détection** (builds internes et TestFlight).
- Textes FR/EN, évolutions de règles à demander à `backend-data`, fichiers à aligner (§9.4), maquettes manquantes (§18).
- **Sous-ticket séparé** : appareil unique par compte (`docs/tickets/session-appareil-unique.md`).

### Hors périmètre (propriétaire indiqué)
- Notifications push et tout effet d'alerte d'une zone à risque : exigent Blaze.
- Mise à jour manuelle (ticket Update) et affichage des statuts (FEED-2).
- Achat, essai, fin d'abonnement et suppression des lieux en trop (ticket Premium) ; ce ticket expose la suppression d'un lieu **appelable sans écran**.
- Consentement version 2 (abandonné), écran « Détection de tes lieux », guide fabricant, avis aux membres, option par lieu (supprimés ou non demandés).
- Lieux personnels d'un membre : **seul le créateur crée des lieux, il n'y a pas de système d'invitation** (QL5).
- Carte hors connexion, historique de trajets, suivi continu à l'écran.

---

## 3. Parcours et écrans

```
Onboarding (ou connexion à un compte existant, ou choix de localisation détecté comme changé)
  ─► écran d'autorisation des NOTIFICATIONS ─► écran d'autorisation de la LOCALISATION
        Android : ne se quitte pas sans position précise + arrière-plan (pop-in native, puis réglages sur Android 11+)
        iOS     : l'onboarding ne se termine pas sans « Toujours »
  ─► Feed

Créateur
  « Ajouter un lieu » ─► Nouveau lieu : Nom · Miff(s) · Adresse (suggestions dès 3 caractères) · carte + cercle ·
        Périmètre (50 à 800 m, pas de 50, défaut 100 m) · Type · Catégorie ─► Valider / Enregistrer
  Modifier : mêmes champs, type compris, + « Supprimer ce lieu » ─► confirmation ─► suppression

Tous les membres (sans ouvrir l'app)
  position lue à l'ouverture, au passage en arrière-plan, sur déplacement significatif, à l'entrée dans une zone
  ─► les 20 lieux les plus proches sont surveillés ─► arrivée / départ ─► status/{moi} écrit dans chaque Miff partagée concernée
```

| Écran | États à couvrir | Maquette |
|---|---|---|
| **Nouveau lieu** (création) | vide ; nom valide ; 2 caractères minimum ; limite de 50 ; suggestions d'adresse (chargement, aucune, indisponibles) ; adresse choisie ; carte avec cercle ; curseur ; type ; catégorie ; Miffs (une ou plusieurs) ; enregistrement ; hors connexion ; erreur ; limite gratuite atteinte | `01` à `07` : **oui** ; sans suggestions, sans multi-sélection, sans limite gratuite |
| **Modifier le lieu** | idem + « Supprimer ce lieu » ; sélecteur de Miff absent de la maquette | `03` : oui (incohérences §5 #8, #9) |
| **Confirmation de suppression** | confirmation ; en cours ; hors connexion | `04` : oui |
| **Liste / détail d'un lieu** | liste (nom + catégorie) ; détail (adresse, carte, rayon, type) ; « Voir sur la carte » (système) | Liste : `design/exports/profil/` ; détail : **non dessiné** |
| **Autorisation notifications** | affichage ; accordée ; refusée ; réaffichage | `onboarding-original/Welcoming screen-19.png` : oui |
| **Autorisation localisation** | « Toujours autoriser » ; pop-in refusée avec message rouge ; Android 11+ : bouton « Autoriser la localisation en arrière plan » vers les réglages | `…-20`, `…-21`, `…-22` : **oui pour Android** ; **iOS non dessiné** (§18) |
| **Appareil remplacé** | message de déconnexion | **Non dessiné** (sous-ticket session) |

---

## 4. Maquettes lues : ce qu'elles montrent

- `01` Formulaire vide : Nom, Adresse, Périmètre (curseur, **100 m**), Type (**Zone de confiance** « Lieu sûr pour vos proches » / **Zone à risque** « Lieu à éviter »), Catégorie (7 choix), « Enregistrer » grisé ; coche de validation grisée en haut à droite.
- `02` Idem avec le sélecteur **Miff** (liste déroulante à valeur unique, « Famille Mfouka ») et l'aperçu de carte.
- `03` Modification : « Supprimer ce lieu » en rouge ; la catégorie **Travail est absente** de `03` à `06`.
- `04` « Etes-vous sûr de supprimer ce lieu ? / Ce lieu sera définitivement supprimé pour vous et l'ensemble des membres de la miff associé. » + « Supprimer ce lieu » / « Annuler ».
- `05`, `06` Formulaire rempli : « Maison du père de Nathan » (12/50), « 17 Avenue d'Italie, 75013, Paris », « Enregistrer » et coche actifs ; `06` avec la zone à risque sélectionnée. `07` Aperçu de carte avec cercle.
- Écrans de permission (`onboarding-original`) : `19` notifications (« Vous avertir, seulement quand c'est utile ! », exemples « Thomas est bien arrivé à l'école… »), `20` « Toujours autoriser », `21` « Autoriser la localisation en arrière plan », `22` message rouge « Miff a besoin de l'option toujours autoriser pour fonctionner » (le texte de Notion ajoute « et de la précision exacte »).

---

## 5. Contradictions et ambiguïtés : ce que Notion crée avec le reste du dépôt

### 5.1 Contradictions avec le dépôt (Notion et décisions du PM l'emportent ; à aligner)

| # | Point Notion / décision | Source du dépôt en conflit | Traitement |
|---|---|---|---|
| 1 | **« Toujours » + précision exacte obligatoires**, écran bloquant, réaffiché | `onboarding.md` Q2 (jamais bloquant) et §6.6 / critères PE1 à PE12 ; `miff.md` §5.4 point 8 et critère K4 (« n'empêche pas de rejoindre ») ; `localisation-lieux-de-confiance.md` (critères 4 et 5 : repli manuel) ; politique de confidentialité art. 4 (« Si tu refuses… tu peux continuer à utiliser Miff ») ; **Apple 5.1.2(i)** : « Your app may not require users to enable system functionalities (such as push notifications, location services, tracking) in order to access functionality, content, use the app » et 5.1.1(iv) « not … force people to consent to unnecessary data access » (lus le 2026-11-01) | Décision du PM appliquée. Ces textes sont à **réécrire** (liste §9.4). Risque de rejet iOS élevé : R1. |
| 2 | **Texte de consentement actuel** : « Ton téléphone te demandera séparément l'accès à ta position. Tu peux le refuser : tu rejoins la Miff quand même. » | Tracking (refus impossible pour continuer) | **Incohérence réelle** : on rejoint la Miff (écriture faite) mais on ne peut plus utiliser l'app tant que « Toujours » n'est pas accordé. Décision QL3 : risque assumé, texte inchangé, pas de nouvelle version. Documenté en R3. Le consentement ne dit toujours pas non plus que **les lieux (adresses, carte) sont visibles de tous** ni que **la batterie est partagée** (Q14 du 2026-10-30) : même décision. |
| 3 | **Lieu associé à plusieurs Miffs** | `data-model.md` §2.9 : `places` est une sous-collection **d'une** Miff ; règles `places` par Miff | §9.3 : un document par Miff, même identifiant. |
| 4 | **Nom de 2 à 50** | Règles : 1 à 60 (`validPlaceShape`) | Compatible : le **client** impose 2 à 50 ; les règles tolèrent plus large (aucune règle à changer ; le serveur n'impose pas 50). |
| 5 | **Rayon 50 à 800 par pas de 50, défaut 100 m** | Règles 50 à 2 000 (compatible) ; spec lieux « ex. 100 m » ; `data-model.md` §2.9 « l'UI doit proposer ≥ 100 m » ; documentation Android : 100 à 150 m minimum recommandé | Notion suivi. Un rayon de 50 m est **peu fiable** (R5) : mention proposée, à confirmer (§16). |
| 6 | **Type « danger »** dans le formulaire | Décision Q10 du 2026-10-30 (zone à risque non livrée en v1) ; `data-model.md` : danger = **premium** (décision du 2026-10-16) | Le type est livré. **Effet d'une zone de danger non défini** (§16) : v1 écrit `in_risk` (modèle §3) ; l'affichage reste celui que décidera le Feed (Q10 : neutre). |
| 7 | **Type modifiable** (QL16) | Règles : `type` immuable (`validPlaceUpdate`) ; limite : danger = premium | **Règle à modifier** (R-LIEU-6) : autoriser le changement et exiger le premium si le nouveau type est « danger ». |
| 8 | **Catégorie** (7 valeurs) | Schéma sans `category` ; règles refusent toute clé inconnue | **Règle à modifier** (R-LIEU-2). **Travail** absent des maquettes `03` à `06` (présent sur `01`, `02`, Notion) : à corriger ; « Ecole » (Notion) / « École » (maquette). |
| 9 | **Sélecteur de Miff** : créateur seulement, plusieurs possibles, défaut « la dernière Miff rejointe » | Maquette : valeur unique, texte au singulier ; absent en modification (`03`) ; « dernière rejointe » peut ne pas être créée par la personne | Ambigu : §16. |
| 10 | **Gratuit : 2 lieux** | Règles : `p1` seul ; **1 lieu** ailleurs | Liste exhaustive §9.4 ; règle **R-LIEU-5**. |
| 11 | **Adresse obligatoire** | Règles : `address` facultative (`null` accepté) ; politique art. 3 et 5 : « adresse éventuelle » | Imposé par le **client** seulement ; textes légaux à aligner. |
| 12 | **« On enregistre sa localisation »** | `data-model.md` §1 (aucune coordonnée d'une personne) ; politique art. 1 pt 1, art. 3 (« Ce que Miff ne collecte pas : ta position GPS brute »), art. 4 (« Miff n'enregistre pas de suivi continu ») ; étiquettes des stores | **Écart tranché par le PM (2026-11-01) : position sur le téléphone seulement** ; politique et étiquettes à préciser (§7). |
| 13 | **Ordre : notifications puis localisation** | `onboarding.md` §3.1 (localisation puis notifications) | Notion suivi ; `onboarding.md` à aligner. |
| 14 | **Écran réaffiché** après connexion ou changement de choix | `onboarding.md` §6.6 principe 4 (« aucune relance par fenêtre ») | Décision du PM appliquée. |
| 15 | **20 lieux sur Android** | Limite Android : 100 (documentation lue) ; `localisation-lieux-de-confiance.md` : 100 | Notion suivi ; question §16. |
| 16 | **Message rouge** « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » | `CLAUDE.md` (ton non anxiogène, y compris les erreurs) ; `onboarding.md` §4 #4 | Repris **mot pour mot** ; conflit de ton signalé (§16). |
| 17 | **Un compte, un appareil** | `data-model.md` Q20 (déconnexion), contrat de purge, `users/{uid}/devices` (jetons FCM) | Sous-ticket séparé. |
| 18 | **Suppression « pour vous et l'ensemble des membres de la miff associé »** | Règles : suppression par le créateur seul (cohérent) | Cohérent ; ambigu si le lieu est dans plusieurs Miffs (§16). |
| 19 | **Textes de la maquette en vouvoiement** (« Choisissez », « Vous ne pourrez… ») | Décision tutoiement (Authentification Q2) | Adaptés au tutoiement ; écarts listés dans la PR (`CLAUDE.md`, règle du 2026-10-28). |

### 5.2 Ambiguïtés dans Notion (non devinées)
1. **« Cliquables quand au moins un caractère a été saisi ET une adresse renseignée »** et **« nom ≥ 2 caractères sinon "2 caractères minimum" »** : avec 1 caractère, le bouton est actif mais le message apparaît-il au clic ? Proposition : bouton actif dès 1 caractère + adresse **choisie**, message au clic. À confirmer.
2. **« Adresse renseignée »** : une adresse tapée sans choisir de suggestion n'a pas de latitude ni de longitude. Proposition : « renseignée » = **une suggestion choisie**.
3. **Boutons « Valider » (coche) et « Enregistrer »** : deux boutons qui font la même chose (maquette).
4. **Plusieurs Miffs en modification** : peut-on ajouter ou retirer une Miff associée à un lieu existant ? La maquette de modification n'a pas de sélecteur.
5. **« 20 lieux les plus proches »** : mesurés au **centre** du lieu ou à son bord ? Un utilisateur **à l'intérieur** d'un grand lieu pourrait voir ce lieu sortir de la liste (centre plus loin que 20 autres) et **rater son départ**. Proposition [Proposé] : **toujours conserver** les zones dans lesquelles la personne est actuellement.
6. **« Fermeture de l'app »** sur Android : il n'existe pas d'événement de fermeture fiable ; proposition : passage en arrière-plan.
7. **Notifications dans le même écran bloquant ?** Notion décrit l'ordre (notifications puis localisation) mais ne dit pas que les notifications sont bloquantes ; je ne rends que la localisation bloquante.
8. **Écran réaffiché « après connexion »** : si les autorisations sont déjà accordées, l'écran est-il sauté ? Proposition : oui.
9. **« Lieux attachés à l'utilisateur »** : lieux des Miffs dont il est membre ; **seulement** celles où il partage son statut [Proposé].
10. **Défaut « dernière Miff rejointe »** pour un sélecteur limité aux Miffs créées.
11. **« Zone de danger »** (Notion) vs **« Zone à risque / Lieu à éviter »** (maquette, spec) : libellé et ton (R7).

---

## 6. Règles métier

### 6.1 Le lieu (formulaire) [Notion]

| Champ | Règle |
|---|---|
| Nom | **2 caractères minimum** (« 2 caractères minimum » sinon) ; **50 maximum** (« Limite de 50 caractères atteinte » sinon) ; compteur « 12/50 » (maquette) |
| Miff(s) associée(s) | Liste déroulante des Miffs **dont la personne est créatrice** (les autres n'apparaissent pas) ; **plusieurs possibles** ; défaut = la dernière Miff rejointe ; texte (maquette, au tutoiement) |
| Adresse | **Obligatoire** [Décidé QL6] ; **suggestions dès 3 caractères** ; la personne **choisit une adresse** ; la latitude et la longitude sont récupérées de la suggestion choisie |
| Carte | Le point est placé sur une carte (solution gratuite) et le **rayon** est visible |
| Périmètre | Curseur de **50 à 800 m, pas de 50, défaut 100 m** |
| Type | **Zone de confiance** par défaut, ou **Zone à risque / de danger** |
| Catégorie | Maison, Travail, École, Sport, Chez un proche, Loisir, Autre |
| Caractéristiques enregistrées | identifiant unique, Nom, Latitude, Longitude, Adresse, Rayon, Miff(s), Créateur, Type, Catégorie |
| Boutons | « Valider » et « Enregistrer » **non cliquables par défaut** ; cliquables quand au moins un caractère est saisi **et** une adresse est renseignée (§5.2 #1 et #2) |
| Modification | **Tous les champs, type compris** [Décidé QL16] ; suppression par « Supprimer ce lieu » avec confirmation |

### 6.2 Limites gratuite et premium [Décidé QL9]
- **Gratuit : 2 lieux**, **illimité en premium**. Le contournement des limites système n'est plus un plafond de lieux mais le mécanisme des **20 lieux les plus proches** (§6.5). Le texte « illimités » du Premium redevient exact.
- Un compte gratuit n'a qu'une Miff : 2 lieux au total. Les règles imposent la limite par **emplacements fixes** (§9.2) : **le client vérifie avant d'écrire**, le refus des règles n'est que le filet.
- **Zone à risque : premium** [Décidé le 2026-10-16, `data-model.md` §6 et `paywall-premium.md`] ; la question de savoir si les **deux** lieux gratuits peuvent être de type danger est ouverte (§16).
- Au 3e lieu en gratuit : message de limite puis écran provisoire « Miff+ arrive bientôt » [Décidé QL15] ; événement `paywall_shown` (`extra_trusted_place`) déjà au contrat, avec consentement Analytics.
- Fin de Premium : suppression des derniers lieux (décision du 2026-10-24) : **ticket Premium**.

### 6.3 Un lieu dans plusieurs Miffs
Un lieu associé à N Miffs est enregistré comme **N documents**, un par Miff, **avec le même identifiant**. Conséquences : création, modification et suppression écrivent N documents (§9.3) ; sur le téléphone, les N documents sont **un seul lieu physique** (même identifiant, mêmes coordonnées) : une seule zone surveillée, N statuts écrits (un par Miff où la personne partage).

### 6.4 Propagation vers les proches (sans notification)
Le serveur ne peut pas prévenir un téléphone fermé. Un proche apprend un changement de lieu : (1) en direct si l'écran de la Miff est ouvert ; (2) à l'ouverture de l'app ; (3) au réveil par un événement de position ou de zone, **depuis le cache local** (relecture serveur si la dernière a plus d'une heure) ; (4) par une relecture périodique en arrière-plan au plus toutes les 12 h [Proposé]. **Aucun avis n'est affiché aux membres** (QL4). Le créateur voit après l'enregistrement : « Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » La **sélection des 20 plus proches se fait sur la liste locale** (lecture du cache, pas de requête serveur à chaque déplacement). **Délai accepté par le PM pendant la bêta (2026-11-01)**, avec ce texte honnête dans l'app ; levé le jour où Blaze (notifications) est décidé.

### 6.5 Tracking [Notion]

**Autorisations (bloquantes).**
- **Android** : sur l'écran d'autorisation, la personne ne peut pas sortir tant qu'elle n'a pas accordé `ACCESS_FINE_LOCATION` **et** `ACCESS_BACKGROUND_LOCATION`. « Toujours autoriser » ouvre la pop-in native. Si les deux sont accordés : écran suivant ; sinon la pop-in reste avec, **sous le bouton, en rouge** : « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner ». **Android 11+ (API 30+)** : « Toujours » ne s'accorde pas depuis la pop-in ; après la première pop-in, le bouton **redirige vers l'écran système des paramètres** ; la personne reste sur l'écran tant que « Toujours autorisé » n'est pas donné.
- **iOS** : sans `authorizedAlways`, l'onboarding ne peut pas se terminer.
- **Quand** : pendant l'onboarding, après la connexion à un compte existant, ou quand l'app détecte que la personne a **désactivé ou changé** ses choix de localisation : notifications **puis** localisation (§5.2 #7, #8).

**Position et zones.**
- À l'**ouverture** et à la **fermeture** (passage en arrière-plan) de l'app, la position est **enregistrée** (où : §7).
- **Android** : détection d'un déplacement de **plus de 250 m** (fournisseur de position fusionné, distance minimale de mise à jour) → mise à jour de la liste des lieux surveillés par le géofencing d'Android avec **les 20 lieux les plus proches** parmi les lieux attachés à la personne.
- **iOS** : sur « changement de position significatif » (`startMonitoringSignificantLocationChanges`) → la position est enregistrée et la liste est mise à jour ; **surveillance de régions pour les 20 zones les plus proches** de la dernière position connue.
- **Aussi** : à chaque **entrée détectée dans une zone** attachée à la personne, la liste est recalculée.
- **Cas d'usage Notion (iOS)** : 2 Miffs, 11 lieux chacune → 22 lieux attachés ; à l'ouverture, en quittant l'app, sur déplacement significatif et à l'entrée dans une zone, les 20 plus proches sont surveillés. **Même fonctionnement sur Android** avec les méthodes appropriées.

### 6.6 De l'événement au statut (valeurs de `data-model.md` §3 ; aucune coordonnée)

| Événement | Écriture dans `status/{moi}` de chaque Miff partagée concernée |
|---|---|
| Arrivée dans une **zone de confiance** | `presence = "in_trusted"`, `placeId`, `presenceAt` = heure de l'événement, `lastUpdateAt` = idem, `escalationDueAt = null` |
| Départ d'une zone de confiance | `presence = "out"`, `placeId` = lieu quitté, `presenceAt` = heure de l'événement, `escalationDueAt` = +2 h (inutile sur Spark) ; `lastUpdateAt` conservé, relevé à l'heure du départ s'il a plus de 24 h |
| Arrivée dans une **zone à risque** | `presence = "in_risk"`, `placeId`, `presenceAt` (modèle §3 ; **effet visible non défini**, §16) |
| Départ d'une zone à risque | `presence = "out"`, `presenceAt`, `escalationDueAt` = +2 h |
| À chaque écriture | `updateText` et `hasPhoto` **conservés** ; `batteryPct` = niveau du moment ; `locationAccess` = §6.7 ; `updatedAt` = heure serveur |

Règles : un **état local « lieux où je suis »** calcule le statut (quitter A en restant dans B = `in_trusted` sur B) ; si un lieu de confiance et une zone à risque se chevauchent, **la zone à risque prime** (hypothèse du modèle §3, à confirmer avec Q3) ; état initial : si déjà dedans à l'armement = arrivée, si dehors = **aucun `out` inventé** (statut `unknown`) ; lieu supprimé ou modifié : statut `unknown` si la présence ne peut pas être reconfirmée ; **une écriture indépendante par Miff** ; **aucune temporisation** [Décidé le 2026-11-01 : pas de temporisation en v1 ; les faux départs en bordure de zone sont acceptés et observés à la recette] ; **disjoncteur** d'écritures automatiques par jour et par appareil (valeur à fixer à la recette) ; aucune lecture de la position fournie avec l'événement.

### 6.7 `locationAccess` (quatre valeurs seulement, aucune règle modifiée)
`background` = « Toujours » + précise ; `foreground` = « pendant l'utilisation », « une fois » ou position approximative ; `denied` = refusée, restreinte ou localisation du téléphone coupée ; `unknown` = jamais demandée. Avec « Toujours » obligatoire, ces valeurs ne servent plus qu'aux états **transitoires** (retrait de l'autorisation constaté, avant que l'écran bloquant ne réapparaisse). La mise à jour de `locationAccess` après plus de 24 h dans le même état dépend de **R-LIEU-1** (§9.2).

### 6.8 Vie privée : ce que voient les autres
Tout membre voit, pour **chaque** lieu de sa Miff : nom, **adresse** (obligatoire désormais), **position sur la carte**, rayon, type, catégorie (règles `places`, inchangées). Un **intrus entré avec le code** voit donc l'adresse du domicile du créateur : risque assumé (R4, même famille que R2 du ticket Miff). Le créateur n'a aucun droit de lecture supplémentaire sur les statuts. Un lieu peut désigner un tiers (« Maison du père de Nathan ») : adresse d'une personne non utilisatrice enregistrée (R4).

---

## 7. Écart « on enregistre sa localisation » (Tracking) : **décision du PM du 2026-11-01 = sur le téléphone seulement (option A)**

**Décision** : la position enregistrée à l'ouverture, à la fermeture et sur déplacement significatif reste **sur le téléphone** (elle sert à choisir les 20 lieux les plus proches). **Aucune position n'est envoyée à Firestore ni stockée côté serveur** ; **seul le statut de présence dans un lieu nommé est écrit**. L'option B ci-dessous est **écartée** ; elle est conservée pour mémoire des impacts.

**Ce que cela impose (à refléter dans les autres documents, §9.4)** :
- **Critères** : POS5 et VIE1 (aucune position hors du téléphone ; position gardée en mémoire ou stockage privé de l'app, exclu des sauvegardes, **effacée à la déconnexion, à la suppression de compte et à la perte d'accès**).
- **Politique de confidentialité** (FR et EN, par `release-manager`, sans prétendre à une relecture juridique) : dire que la position est **lue sur l'appareil pour choisir les lieux surveillés et n'est pas transmise** ; préciser « Miff ne stocke pas ta position GPS **sur ses serveurs** » (l'art. 1 pt 1 et l'art. 3 « Ce que Miff ne collecte pas » restent vrais côté serveur, mais « ne stocke pas » est inexact pour l'appareil sans cette précision) ; art. 4 « pas de suivi continu » : à vérifier au regard des mises à jour de position sur déplacement.
- **Consentement** : aucun changement de texte (décision QL3).
- **Déclarations des stores** : **aucune donnée de position collectée côté serveur** ; seul est collecté un **état de présence dérivé de la position** (lieu nommé, heure) ; coordonnées des **lieux** (donnée du créateur) ; textes d'usage iOS conformes ; formulaire Google (fonction unique) et étiquettes Apple à remplir en conséquence.
- **Règles Firestore** : aucune évolution ; le schéma `status` reste fermé.
- **Coût** : 0 écriture de position.

### Options exposées avant la décision (pour mémoire)

| | **Option A : sur le téléphone seulement** | **Option B : envoyée à Firestore** |
|---|---|---|
| Ce qui est fait | Dernière position gardée **localement** (mémoire ou stockage privé de l'app, exclu des sauvegardes, effacé à la déconnexion et à la suppression de compte) ; sert uniquement à trier les lieux | Dernière position écrite dans un document Firestore à chaque ouverture, fermeture et déplacement significatif |
| Besoin fonctionnel couvert | **Oui** : le tri des 20 lieux se fait sur l'appareil | Pas nécessaire au tri (l'appareil l'a déjà) ; utile **seulement** si une fonction lit cette position (aucune n'est prévue : « Localiser » a été remplacé par « Voir le lieu », QM11 / Q13) |
| Vie privée | Aucune position ne quitte le téléphone ; cohérent avec `data-model.md` §1 | **Donnée de localisation précise de la personne côté serveur** ; si un membre pouvait la lire : suivi à sens unique, risque **anti-stalkerware** maximal ; si seule la personne la lit : stockage sans usage |
| Règles et schéma Firestore | **Aucune évolution** | Nouveau champ ou document (le schéma `status` est fermé et refuse toute clé inconnue) ; règles, tests, déploiement ; mise à jour de `data-model.md` §1 et §11 |
| Politique de confidentialité | Précision à ajouter : la position est **lue sur l'appareil pour choisir les lieux**, non transmise ; la phrase « Miff ne stocke pas ta position GPS » est à préciser (« sur nos serveurs ») | Réécriture de l'art. 1 pt 1, art. 3 (« Ce que Miff ne collecte pas »), art. 4, art. 5, art. 7 (durée) ; nouveau consentement probable |
| Stores | Étiquette « position » : usage sur l'appareil ; justification Google/Apple inchangée | Déclaration de **collecte de position précise** liée à l'utilisateur ; revue plus sévère |
| Coût Firestore | 0 | Écritures supplémentaires : ouverture + fermeture + déplacements (Android : toutes les mises à jour de plus de 250 m) ; **plusieurs dizaines par jour et par personne** possibles, à comparer aux 20 000 écritures par jour de Spark (plafond divisé par 3 à 10) [estimation, non mesurée] |
| Batterie | Identique | Identique + envois réseau |

**Retenu : option A** (ma recommandation).

**Dans tous les cas** : (1) la position n'est jamais dans Analytics, Crashlytics, les journaux ni un message de partage ; (2) le gestionnaire d'événements de zone ne lit pas la position fournie avec l'événement ; (3) le journal local de détection (builds de test) ne contient pas de coordonnées.

---

## 8. Différences Android / iOS

| Sujet | Android | iOS (iOS 16 minimum) |
|---|---|---|
| Moteur de zones | API de géofencing de Google Play services ; 100 par app (doc) ; **20 retenus** [Notion] | Surveillance de régions de Core Location ; **20 par app** (guide Apple) |
| Mise à jour de la liste | Déplacement de plus de 250 m (mises à jour de position avec distance minimale) ; **en arrière-plan, Android limite la fréquence des mises à jour de position** (limites d'arrière-plan depuis Android 8) : la liste peut être recalculée avec retard **[À vérifier à la recette]** | Changements de position significatifs (faible consommation) ; relance de l'app en arrière-plan |
| Autorisations | Position précise **et** arrière-plan ; Android 11+ : réglages du système obligatoires ; Android 12+ : la position approximative seule ne suffit pas au géofencing (doc) | « Toujours » (`authorizedAlways`) obligatoire ; évolution depuis « Lorsque l'app est active » par invite du système |
| Après redémarrage | Les zones sont effacées : réinscription au démarrage du téléphone | Régions conservées par le système **[À vérifier]** |
| Délai de détection | Moins de 2 min en général, 2 à 3 min sous limites d'arrière-plan, jusqu'à 6 min si immobile (doc) | Non garanti ; marge d'environ 200 m et 20 s (guide Apple) |
| Fenêtre pour écrire | Courte ; écriture possiblement en file | Environ **10 s** au réveil (guide Apple) |
| Fabricant | ColorOS : à constater **après implémentation** (décision QL2) | Mode économie d'énergie, actualisation en arrière-plan **[À vérifier]** |
| Cartes | MapLibre + OpenFreeMap (§12) | MapKit |
| Suggestions d'adresse | Photon (§12) | `MKLocalSearchCompleter` **[À vérifier : conditions et limites non lues]** |
| Déclarations store | Formulaire « Background location », vidéo, divulgation dans l'app ; revue aussi des pistes de test | Texte d'usage « Toujours » ; étiquettes ; revue de l'usage « Toujours » ; **5.1.2(i)** (R1) |
| Garde-fous du dépôt à réécrire | Commentaire du manifeste (« aucune localisation ») ; contrôle CI | `ios/ci/check-binaries.sh`, `AppConfigurationTests`, `PrivacyInfo.xcprivacy`, `project.yml` : exigent aujourd'hui **l'absence** de `NSLocation*` |
| Tests sur appareil | OPPO Find X5 Lite (Android 13) ; position simulée possible | iPhone 11 Pro (iOS 26.5.2) **uniquement par TestFlight**, à pied |

---

## 9. Contrat avec le modèle de données, les règles Firestore et budget de lectures

### 9.1 Ce que les règles permettent aujourd'hui (vérifié dans `backend/firestore.rules`)
- `miffs/{id}/places/{placeId}` : lecture par tout membre d'une Miff existante ; création par le créateur de la Miff, forme complète (`name` 1–60, `type` `trusted` ou `risk`, `address` ≤ 200 ou `null`, `lat`, `lng`, `radiusM` entier 50–2000, `priority`, `createdBy`, `createdAt`, `updatedAt`) ; **gratuit : seulement l'identifiant `p1` et le type `trusted`** ; premium : identifiants libres et zones à risque ; mise à jour de `name`, `address`, `lat`, `lng`, `radiusM`, `priority`, `updatedAt` **seulement** (`type` immuable) ; suppression par le créateur.
- `status/{moi}` : toutes les clés, partage actif **après** le lot, `presenceAt` et `lastUpdateAt` entre **−24 h et +5 min**, `updatedAt` = heure serveur.

### 9.2 Ce que Notion exige que les règles ne permettent pas

| Code | Besoin | Proposition pour `backend-data` | Obligatoire ? |
|---|---|---|---|
| **R-LIEU-5** | **2 lieux gratuits** | Emplacements `p1` et `p2` (type `trusted` en gratuit) au lieu de `p1` seul ; mise à jour des tests (`06-places`, mutations, fixtures) | **Oui** (Notion) |
| **R-LIEU-2** | **Catégorie** | Clé `category` ajoutée à `validPlaceShape` (liste fermée : `home`, `work`, `school`, `sport`, `relative`, `leisure`, `other` [Proposé : valeurs techniques]) ; clé obligatoire ou facultative : Q6 | **Oui** (Notion) |
| **R-LIEU-6** | **Type modifiable, même danger** | Ajouter `type` aux clés modifiables ; exiger le premium si le nouveau type est `risk` (lectures de règles : `isPremium` jusqu'à 3 de plus) | **Oui** (QL16) |
| **R-LIEU-1** | Réécrire un statut **sans changer `presenceAt` / `lastUpdateAt`** quand ils ont plus de 24 h (mise à jour manuelle après un long séjour, rafraîchissement de `locationAccess`) | Accepter les champs **inchangés** (lecture de `resource.data`, aucune lecture de règles de plus) | **Acceptée par le PM (2026-11-01)** ; à livrer **avant le ticket Update** |
| **R-LIEU-3** | **Renouveler un consentement actif** (nouvelle version du texte) sans arrêter le partage | Accepter la transition « activé, activé » si la nouvelle version est supérieure et `consentedAt` = heure serveur | **Acceptée par le PM (2026-11-01)** comme évolution de règle ; **sans usage immédiat** (pas de consentement v2, QL3) ; la politique promet la redemande du consentement en cas de changement (art. 12) |
| **R-SESS** | Appareil unique | Voir `session-appareil-unique.md` | Oui, sous-ticket séparé |

Chaque déploiement : tests, mesure des lectures, revue de sécurité, **« oui » explicite du PM le moment venu** (`docs/dev/deploiement-regles.md`). Aucun n'est fait ici.

### 9.3 Un lieu dans plusieurs Miffs : options pour `backend-data`
- **Option A (recommandée)** : **un document par Miff**, même identifiant (créé côté client une seule fois). Aucune règle de lecture à changer ; création, modification, suppression = lot de N documents. Inconvénient : cohérence entre copies (un lot unique est atomique ; au-delà de la limite de lectures de règles, plusieurs lots, donc reprise à prévoir).
- **Option B** : collection de premier niveau `places/{id}` avec une liste `miffIds`. Les règles ne savent pas parcourir une liste pour contrôler l'appartenance de lecture : **réécriture lourde**, non recommandée.

**Budget de lectures de règles (limite 20 par lot)** : création premium ≤ 4 **mesuré pour un document** (`data-model.md` §12) → **au pire sans cache 4 × N** : au plus **5 Miffs par lot garanties** ; au-delà, plusieurs lots. Création gratuite d'un emplacement `p1`/`p2` : 1 lecture par document. Modification : 1 par document (+3 si le type passe à danger). Suppression : 1 par document. **À mesurer par `backend-data`** avant le développement.

| Opération | Écritures | Lectures de règles attendues |
|---|---|---|
| Créer un lieu, gratuit (1 Miff) | 1 | ≈ 1 |
| Créer un lieu, premium, N Miffs | N | ≤ 4N (pire cas sans cache) |
| Modifier, N Miffs | N | ≈ N (+ 3N si type vers danger) |
| Supprimer, N Miffs | N | ≈ N |
| Lire la liste (par Miff) | requête | 2 + un par document |
| Écrire un statut (par Miff) | 1 | ≤ 1 |

**Lectures ajoutées par utilisateur actif et par jour** (estimation, pas une mesure) : relectures serveur des lieux (au plus 2 par jour × (N + 2) par Miff) ≈ 6 à 20 ; le tri des 20 plus proches se fait sur le **cache local** (0 lecture). Écritures : 6 à 10 événements de statut par jour. **Option A du §7 : 0 écriture de position.**

### 9.4 Fichiers à aligner (limite gratuite 2 ; catégorie ; type ; Miffs) : liste trouvée par recherche dans le dépôt
Aucun n'est modifié par ce ticket.
- **`CLAUDE.md`** (« gratuit = 1 lieu de confiance »).
- **Règles et modèle** : `backend/firestore.rules` (en-tête ligne 24 et `validPlaceCreate`, ligne 417 : `p1`, type, `category`) ; `backend/data-model.md` (§6 tableau « 1 lieu de confiance (gratuit) » ; §8 ligne « Créer / modifier / supprimer un lieu » ; §2.9 schéma (catégorie, modifiabilité du type) ; §15 décision 5 et question 4 ; §14) ; `backend/rules-test-plan.md` ; `backend/rules-test-report.md` ; tests : `tests/rules/06-places.test.js`, `10-complements.test.js`, `12-durcissement-dur2.test.js`, `13-complements-dur3.test.js`, `09-rule-reads.test.js`, `16-lot-entree-miff0.test.js`, `tests/helpers/fixtures.js`, `tests/tools/mutations-dur3.mjs`, `tests/unit/mutations.test.js`.
- **Specs** : `docs/specs/lieux-de-confiance.md`, `docs/specs/paywall-premium.md`, `docs/specs/miff-groupes.md`, `docs/specs/localisation-lieux-de-confiance.md` (repli manuel, « Plus tard », 100 zones).
- **Tickets** : `docs/tickets/onboarding.md` (§3.1 ordre, §6.6, critères PE1 à PE12, §6.8, risque R10, §4 hors périmètre) ; `docs/tickets/miff.md` (§5.4 point 8, K4, texte de consentement, §4 #17 et 18) ; `docs/tickets/feed-profil-reglages.md` (§4B B13, §10 Q3 / Q10, §13 décisions : Q10 « zone à risque non livrée » à lever) ; `docs/tickets/initialisation-technique.md` (lignes 314 à 316 : limites gratuites).
- **Contrats et textes** : `docs/contracts/analytics-events.md` (note `extra_trusted_place` : « un seul lieu ») ; `docs/legal/cgu.fr.md` et `cgu.en.md` (lignes 57, 90, 95 : « un seul lieu » / « one trusted place »), `docs/legal/README.md` ; **politique de confidentialité** FR et EN (art. 1, 3, 4, 5, 6) : y écrire que la position est lue **sur l'appareil seulement** pour choisir les lieux (§7), les fournisseurs de suggestions d'adresse et de cartes (§12), l'adresse obligatoire des lieux, la limite gratuite à 2 lieux ; **déclarations des stores** : aucune position collectée côté serveur.
- **Site web** : `web/src/landing.json` (lignes 88, 90, 107, 210, 212, 229), `web/tests/landing.test.mjs` (lignes 58, 61), `web/README.md` (ligne 46).
- **Marketing** : `marketing/` (fiches des stores, si elles citent la limite gratuite ou « illimités ») : **non lu**, à vérifier.
- **Code** : manifeste Android (commentaire « No location »), `ios/project.yml`, `ios/ci/check-binaries.sh`, `ios/MiffTests/AppConfigurationTests.swift`, `ios/Miff/Resources/PrivacyInfo.xcprivacy`, `ios/README.md` (ligne 119).

---

## 10. Cas limites

1. **Lieu à l'intérieur duquel la personne se trouve et qui sort des 20 plus proches** : zones actuelles conservées [Proposé, §5.2 #5].
2. **Plus de 20 lieux** : sélection sur la position ; les autres ne sont pas surveillés tant que la position ne change pas ; aucune écriture serveur.
3. **Position inconnue** (premier lancement, localisation coupée) : écran bloquant ; aucune sélection.
4. **Déplacement rapide** (transport) : recalculs ; rebonds en bordure de zone : une écriture par changement d'état.
5. **Lieu partagé par plusieurs Miffs** : une zone, N statuts ; l'échec d'une écriture n'arrête pas les autres.
6. **Lieu modifié ou supprimé pendant que la personne y était** : statut `unknown` si la présence n'est pas reconfirmée.
7. **Partage arrêté, Miff quittée, retirée, supprimée, déconnexion, suppression de compte, session remplacée** : surveillance de ces lieux arrêtée ; plus aucune écriture.
8. **Hors connexion** : l'événement est écrit au retour, avec son heure réelle ; au-delà de 24 h de retard l'écriture est refusée par les règles et abandonnée.
9. **Téléphone éteint** : aucun événement ; dernier statut affiché ; départ connu = escalade calculée par l'app du proche (2 h, 4 h).
10. **Autorisation retirée app fermée** : sur Android l'app est fermée par le système ; rien ne s'exécute avant la prochaine exécution ; l'écran bloquant s'affiche alors.
11. **Horloge décalée de plus de 5 min** : heure d'événement plafonnée à l'heure actuelle.
12. **Rayon de 50 m** : détection peu fiable (R5).
13. **Création hors connexion** : impossible, rien en file ; suggestions indisponibles : message, nouvelle tentative possible.
14. **Lieu créé pour 3 Miffs dont l'une a été supprimée entre-temps** : lot refusé en entier ; message neutre.
15. **Compte gratuit qui ouvre le 3e lieu** : message de limite, aucune écriture.
16. **Changement de type vers « danger » par un compte gratuit** : refusé par le client (et par R-LIEU-6) ; écran provisoire.
17. **Compte connecté sur deux téléphones** : traité par le sous-ticket session.
18. **Taille de texte maximale, droite-à-gauche** : formulaire lisible.

---

## 11. Textes proposés (FR / EN, **tutoiement**, à valider ; les textes Notion sont repris mot pour mot et marqués [Notion])

| Contexte | Français | English |
|---|---|---|
| Nom, trop court [Notion] | « 2 caractères minimum » | "2 characters minimum" |
| Nom, trop long [Notion] | « Limite de 50 caractères atteinte » | "50-character limit reached" |
| Nom (aide, maquette adaptée) | « Choisis un nom pour retrouver l'endroit facilement » ; exemple « ex: Maison du père de Nathan » | "Choose a name to find the place easily"; "e.g. Nathan's dad's house" |
| Miff (aide, maquette adaptée) | « Choisis la Miff attachée à ce lieu. Tu ne pourras choisir que les Miffs que tu as toi-même créées. » | "Choose the Miff this place belongs to. You can only choose Miffs you created yourself." |
| Adresse (aide, maquette adaptée) | « Entre l'adresse exacte de ce lieu » ; exemple « ex: 17 Avenue d'Italie, 75013, Paris » | "Enter the exact address of this place" |
| Périmètre | « Définis le rayon autour du lieu » ; « {n} m » | "Set the radius around the place"; "{n} m" |
| Type | « Quel type de zone définis-tu ? » ; « Zone de confiance — Lieu sûr pour tes proches » ; « Zone à risque — Lieu à éviter » (libellés de la maquette conservés) | "What type of zone are you setting?"; "Trusted zone — A safe place for your loved ones"; "Risk zone — A place to avoid" |
| Catégorie | « Sélectionne la catégorie qui convient » ; Maison, Travail, École, Sport, Chez un proche, Loisir, Autre | "Select the matching category"; Home, Work, School, Sport, At a relative's, Leisure, Other |
| Boutons | « Enregistrer » ; « Supprimer ce lieu » | "Save"; "Delete this place" |
| Suppression (maquette adaptée) | « Supprimer ce lieu ? Ce lieu sera définitivement supprimé pour toi et pour l'ensemble des membres de la Miff associée. » + « Supprimer ce lieu » / « Annuler » | "Delete this place? It will be permanently deleted for you and for all the members of the associated Miff." |
| Succès | « Le lieu « {nom} » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » | "The place "{name}" is saved. Your loved ones will pick it up as soon as they open Miff, or within the next few hours." |
| Limite gratuite (2) | « Avec la formule gratuite, tu peux avoir 2 lieux. Miff+ te permet d'en ajouter d'autres. » + « Découvrir Miff+ » / « Plus tard » | "With the free plan, you can have 2 places. Miff+ lets you add more." |
| Zone à risque en gratuit | « Les zones à risque font partie de Miff+. » | "Risk zones are part of Miff+." |
| Suggestions : aucune / indisponibles | « On ne trouve pas cette adresse. Essaie avec moins de détails. » / « La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant. » | "We can't find this address. Try fewer details." / "Address search isn't available right now. Please try again in a moment." |
| Hors connexion, erreur | (textes du ticket Miff §5.10) | (same) |
| **Message rouge Android** [Notion, mot pour mot] | « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » | Pas de traduction dans Notion ; proposition : "Miff needs the "Allow all the time" option and precise location to work" |
| Écran d'autorisation de la localisation (maquettes 20 à 22 adaptées au tutoiement) | Titre « Savoir que vous allez bien ! » devient « Savoir que tu vas bien ! » ; corps « Miff a besoin d'accéder à ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. » ; bouton « Toujours autoriser » ; Android 11+ : « Autoriser la localisation en arrière plan ». **Réserve** : ce texte (celui de la maquette) ne dit ni « en arrière-plan / app fermée » ni ce qui est partagé : la divulgation exigée par Google Play avant la fenêtre du système (« position », « quand l'app est fermée », fonction concernée) est à ajouter (Q13). Texte proposé : « Pour signaler à ta Miff quand tu arrives ou pars des lieux qu'elle a définis, Miff utilise la position de ton téléphone, même quand l'app est fermée. Tes proches voient seulement un état (par exemple « à la maison ») et l'heure. » | "Know that you're fine!" ; "Miff needs access to your location so your loved ones can know whether you're safe." ; "Always allow" ; proposed disclosure: "To let your Miff know when you arrive at or leave the places it has set, Miff uses your phone's location, even when the app is closed. Your loved ones only see a status (for example "at home") and the time." |
| Textes d'usage iOS (`NSLocationWhenInUseUsageDescription`) | « Miff utilise ta position pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance. Elle n'est jamais enregistrée comme un trajet. » (cohérent avec la décision « position sur le téléphone seulement ») | "Miff uses your location to let your Miff know when you arrive at or leave a trusted place. It is never saved as a route." |
| Textes d'usage iOS (`NSLocationAlwaysAndWhenInUseUsageDescription`) | « Pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Seul un état (par exemple « à la maison ») et l'heure sont partagés, jamais ta position précise. » (idem) | "To let your Miff know when you arrive at or leave a trusted place, even when the app is closed, Miff needs your location in the background. Only a status (for example "at home") and the time are shared, never your precise location." |

Aucun texte légal ni de store n'est présenté comme relu par un juriste.

---

## 12. Cartes et suggestions d'adresse gratuites : options vérifiées

Exigence [Notion] : **suggestions dès 3 caractères**, API **100 % gratuite et mondiale** ; carte **gratuite**. Règle d'or du projet : gratuit, conforme aux CGU, aucun moyen de paiement sans décision du PM.

| Option | Gratuit, sans moyen de paiement | Autocomplétion autorisée | Mondiale | Clé | Garantie | Verdict |
|---|---|---|---|---|---|---|
| **Photon public** (`photon.komoot.io`, README GitHub, lus 2026-11-01) | Oui | **Oui** (« search-as-you-type » listé comme fonction) | Oui (données OpenStreetMap ; couverture planétaire du serveur **non lue**) | **Aucune** | **Aucune** : « no uptime guarantees » ; « Extensive usage will be throttled or completely banned » ; plafonds **non chiffrés** ; instance privée recommandée pour de gros volumes (≈ 95 Go de disque, 64 Go de RAM : hors budget) | **Retenu pour la bêta** : seul à satisfaire Notion sans clé ; **signal de bascule** : réponses 403/429/5xx répétées ou volume jugé « extensif » |
| **Geoapify, formule gratuite** (pages tarifs et conditions v5 du 2024-02-02, lues 2026-11-01) | Oui : **3 000 crédits par jour**, 5 requêtes par seconde ; usage commercial en production autorisé (page des tarifs) | Oui (1 crédit par requête, confirmé par la doc) | « Couverture mondiale » **non confirmée** par la page lue | **Oui** : clé restreignable par IP, référents ou origines (**pas de restriction mobile documentée**) ; usage de la clé dans une app mobile **non couvert** par les conditions lues | Dépassement « soft » ; suspension possible ; SLA payant seulement ; **attribution obligatoire** | **Solution de repli** si Photon échoue ; la clé extractible de l'app est un risque d'abus (quota, blocage) ; **décision du PM** |
| **MapKit `MKLocalSearchCompleter`** (iOS) | Pas de clé ; **conditions, limites et débit non lus** (page illisible) | Conçu pour la suggestion à la frappe **[À vérifier]** | **[À vérifier]** | Aucune | **[À vérifier]** | Retenu pour iOS **sous réserve de vérification** sur l'iPhone |
| Nominatim public | Oui | **Interdit** (« must not implement such a service on the client side ») ; 1 requête par seconde pour toute l'app | Oui | Aucune | Accès retirable | **Exclu** |
| Google Places / Geocoding | **Non** : facturation, 10 000 requêtes gratuites par mois puis payant (tarifs lus 2026-10-31) | Oui | Oui | Oui | Oui | **Exclu** sans décision |
| Mapbox | Non (facturation) | — | — | — | — | **Exclu** (consigne du PM) |
| Géocodeur du système Android | Oui | Pas conçu pour la suggestion à la frappe ; disponibilité non garantie | Variable | Aucune | Aucune | Possible en **dernier recours** |

**Garde-fous proposés pour l'autocomplétion** (réduisent le risque d'« usage extensif » et la fuite de saisie) : seuil de 3 caractères [Notion], **temporisation de 300 ms** après la dernière frappe, annulation de la requête précédente, **mise en cache** du résultat par texte pendant la session, aucune requête sur le texte déjà choisi, domaine déclaré dans la liste blanche réseau, attribution OpenStreetMap affichée. **Ce que reçoit le fournisseur** : le texte saisi et l'adresse IP ; la politique de confidentialité doit le dire.

**Cartes** : **iOS : MapKit** (sans clé). **Android : MapLibre + tuiles OpenFreeMap** (page lue le 2026-10-31 : gratuit, sans clé ni limite de requêtes, usage commercial autorisé, attribution requise, **aucun SLA**, financé par des dons) ; le fournisseur de tuiles doit être **remplaçable par un réglage**. **Écartés** : tuiles `tile.openstreetmap.org` (accès « au mieux », blocage sans préavis), SDK Google Maps (exige un projet avec facturation, lu le 2026-10-31). **Signal de bascule** : tuiles lentes ou indisponibles pendant la recette, ou changement de conditions. Coût de la bascule vers un fournisseur à clé : décision du PM.

---

## 13. Tests réalistes

- **Niveau 1, logique pure (CI)** : sélection des 20 plus proches, conservation des zones actuelles, calcul du statut (désordre, doublons, chevauchement, état initial), mapping `locationAccess`, bornes d'horloge, validations du formulaire, équivalence Android / iOS (mêmes cas).
- **Niveau 2, Android** : position simulée (application de position fictive) sur l'OPPO pour exercer le chemin de code ; **ne prouve rien sur le fabricant**.
- **Niveau 3, marche réelle** (référence, **recette du PM, non bloquante pour l'implémentation**) : lieu de 100 m autour de chez soi, sortie à 400 m, retour ; répéter avec l'app balayée, après redémarrage, avec l'économiseur de batterie, avec la localisation coupée ; noter l'heure réelle et l'heure vue sur le second téléphone ou dans la console Firebase (lecture seule). Sur l'iPhone : **uniquement TestFlight, à pied** (pas de simulateur, pas de Mac).
- **Journal local de détection** [Décidé QL18] : builds internes et TestFlight seulement ; type d'événement, identifiant de lieu, heure, batterie ; **aucune coordonnée** ; purgé à la déconnexion ; **absent du build de publication** (test).
- Mesure de la batterie attribuée à Miff sur 24 h par le PM (statistiques du système) : relevé, sans seuil bloquant.

---

## 14. Découpage proposé (Android d'abord, puis iOS)

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **LOC-0** | Décisions, règles, textes, design | Réponses §16 ; **R-LIEU-5, R-LIEU-2, R-LIEU-6** (+ R-LIEU-1 avant Update, R-LIEU-3 acceptée) (écriture, tests, mesures, déploiement avec « oui ») ; mise à jour des fichiers du §9.4 ; maquettes manquantes (§18) | M | validation |
| **LOC-1** | **Android : lieux** | Formulaire (§6.1), suggestions (Photon), carte (MapLibre), multi-Miffs, limite 2, liste et détail, suppression sans écran | L | MIFF-1, ONB-3, LOC-0 |
| **LOC-2** | **Android : Tracking** | Écrans d'autorisation bloquants (notifications puis localisation), position, 20 plus proches, zones, écriture du statut, `locationAccess`, nettoyage, réarmement, journal local | L à XL | LOC-1, MIFF-1, **ONB-4 (à réécrire)** ; ticket Update recommandé avant (QL19) |
| **SESS-1/2/3** | **Appareil unique par compte** (règles, Android, iOS) | `docs/tickets/session-appareil-unique.md` | M chacun | AUTH-1/3 |
| **LOC-3** | **iOS : lieux** | Mêmes livrables ; MapKit ; `MKLocalSearchCompleter` | L | LOC-1, MIFF-3, ONB-7 |
| **LOC-4** | **iOS : Tracking** | Mêmes livrables ; garde-fous du dépôt réécrits dans cette PR ; `authorizedAlways` obligatoire | L à XL | LOC-2, LOC-3, **ONB-8 (à réécrire)** |

Plus de spike. Chaque PR : revue de code en contexte neuf, revue de sécurité (position, journaux, clés, autorisations), QA, recette du PM, fusion par Claude après « oui » explicite du PM.

---

## 15. Risques

| # | Risque | Mitigation |
|---|---|---|
| **R1** | **Rejet Apple** : « Toujours » et notifications **exigées** pour utiliser l'app (5.1.2(i), lu le 2026-11-01), 5.1.1(iv) ; rejet ou questions Google sur la divulgation | Décision du PM appliquée ; **interrupteur de configuration à la compilation** pour relâcher le blocage sans réécrire ; note de revue et divulgation préparées ; risque **assumé** par le PM |
| **R2** | **Détection non fiable** selon le fabricant (ColorOS) ou liste recalculée avec retard (limites d'arrière-plan Android) | Pas de spike ni de plan B (décisions QL1, QL2) ; recette PM ; ajustement après retours |
| **R3** | **Consentement inchangé** : phrase « Tu peux le refuser » devenue fausse ; lieux visibles et batterie non cités | **Risque assumé par le PM** (QL3, 2026-11-01) ; jamais présenté comme « à valider » |
| **R4** | **Adresse obligatoire et visible de tout porteur du code**, adresse d'un tiers possible | Risque assumé ; adresse jamais dans les journaux ; renouvellement du code à chaque retrait |
| **R5** | Rayon de 50 m peu fiable (100 à 150 m recommandés par Android) | Notion suivi ; mention proposée (Q12) |
| **R6** | **Fournisseurs gratuits sans garantie** (Photon, OpenFreeMap) ; MapKit non lu ; l'adresse étant **obligatoire**, une panne des suggestions **empêche toute création de lieu** | Fournisseur remplaçable par un réglage ; repli Geoapify (décision PM) ; signal de bascule §12 |
| **R7** | Libellé « zone de danger / à risque » anxiogène ; effet non défini | Libellés de la maquette conservés (décision antérieure), à revoir avant les stores |
| **R8** | Évolutions de règles nombreuses (R-LIEU-5, -2, -6, SESS) | Tests, mesures, déploiement avec « oui » ; client qui vérifie avant d'écrire |
| **R9** | Position gardée sur le téléphone : donnée sensible locale (verrouillage de l'appareil, sauvegardes) | Décision du PM : aucune position côté serveur ; stockage privé de l'app, exclu des sauvegardes, effacé à la déconnexion et à la suppression de compte ; jamais dans les journaux |
| **R10** | Lot multi-Miffs limité par les 20 lectures de règles | ≤ 5 Miffs par lot garanties ; reprise |
| **R11** | Un ancien appareil hors ligne reste connecté jusqu'à son retour réseau | Sous-ticket session |
| **R12** | Propagation lente des lieux (pas de notification) | Texte honnête ; relectures ; Blaze plus tard |
| **R13** | Promesse de notifications (écran 19 : « Thomas est bien arrivé à l'école ») alors que rien n'est envoyé (Spark) | Texte honnête (`onboarding.md` §6.6 déjà) ; fiche de store |
| **R14** | iOS testable seulement à pied et par TestFlight ; minutes macOS limitées | Logique pure en CI ; journal local |
| **R15** | Aucun texte relu par un juriste | **Risque assumé par le PM** (2026-10-23 et QL20) |

---

## 16. Questions restantes pour le PM (courtes, avec ma recommandation)

**Réglées le 2026-11-01 et retirées de cette liste** : position enregistrée = **sur le téléphone seulement** (§7) ; propagation lente des lieux **acceptée en bêta** avec texte honnête (§6.4) ; **pas de temporisation** des faux départs (§6.6) ; règles « statut de plus de 24 h » (R-LIEU-1) et « renouvellement d'un consentement actif » (R-LIEU-3) **acceptées**, déploiement sur « oui » explicite le moment venu (§9.2).

| # | Question | Reco |
|---|---|---|
| **Q2** | **« Toujours » obligatoire, y compris iOS** : tu maintiens malgré Apple 5.1.2(i) ? Tu acceptes l'interrupteur de configuration pour relâcher si Apple refuse ? | Maintenir **et** prévoir l'interrupteur |
| **Q3** | **Zone à risque** : réservée au premium (décision du 2026-10-16) ? Les 2 lieux gratuits peuvent-ils être « danger » ? Que fait l'entrée (v1 : `in_risk` écrit, affichage neutre du Feed, aucune alerte) ? | Premium seulement ; effet : statut seulement |
| **Q4** | **Plusieurs Miffs** : un document par Miff, même identifiant (§9.3) ? Peut-on changer les Miffs associées en modification ? Supprimer un lieu le supprime-t-il de **toutes** ses Miffs ? Défaut si la dernière Miff rejointe n'est pas créée par la personne ? | Option A ; modification oui ; suppression partout ; première Miff créée |
| **Q5** | **Suggestions d'adresse Android** : Photon d'abord (sans clé, sans garantie), Geoapify en repli (clé) ? iOS : `MKLocalSearchCompleter` à vérifier sur l'iPhone ? | Photon puis Geoapify |
| **Q6** | **Règles Firestore nécessaires à Notion** : peux-tu autoriser `backend-data` à préparer **R-LIEU-5** (2 lieux gratuits), **R-LIEU-2** (catégorie) et **R-LIEU-6** (type modifiable) ? (R-LIEU-1 et R-LIEU-3 sont déjà acceptées.) Catégorie obligatoire ? | Oui ; catégorie obligatoire, défaut « Maison » comme la maquette |
| **Q9** | **Android : 20 ou 100 lieux** surveillés (Notion dit 20) ? | 20 (Notion) |
| **Q10** | **Bouton actif et « adresse renseignée »** (§5.2 #1, #2) ; **boutons Valider et Enregistrer** : un seul ? | Suggestion choisie ; bouton actif dès 1 caractère ; un seul |
| **Q11** | **Notifications** : seulement demandées, jamais bloquantes ? **Écran sauté** si tout est déjà accordé ? | Oui ; oui |
| **Q12** | **Rayon de 50 m** : afficher une mention (« En dessous de 100 m, la détection peut manquer un passage ») ? | Oui, discrète |
| **Q13** | **Message rouge Android** et textes des écrans d'autorisation : garder le ton de Notion (« a besoin… pour fonctionner ») malgré `CLAUDE.md` ? Ajouter la divulgation « app fermée » exigée par Google ? | Garder le message ; ajouter la divulgation sur l'écran |
| **Q14** | **Phrase du consentement** « Tu peux le refuser… » : la laisser telle quelle (risque assumé) ? | Oui (ta décision du 2026-11-01) |
| **Q15** | **Sous-ticket session** : mécanisme du document `session` validé ? | Oui, avec R-SESS |

---

## 17. Critères d'acceptation

Codes : **PL** lieu, **LIM** limites, **MIF** multi-Miffs, **ADR** adresse et carte, **AUT** autorisations (Tracking), **POS** position et sélection, **DET** détection, **STA** statut, **VIE** vie privée, **HOR** hors connexion, **TRA** transverses, **AND**, **IOS**, **PM**, **QA**.

### Formulaire et lieux
- [ ] **PL1** Seul le créateur de la Miff voit « Ajouter un lieu », « Modifier », « Supprimer ce lieu » ; un membre lit la liste et le détail.
- [ ] **PL2** « Valider » et « Enregistrer » sont **non cliquables par défaut** ; ils deviennent cliquables quand au moins un caractère est saisi dans le nom **et** une adresse est choisie.
- [ ] **PL3** Nom de moins de 2 caractères : « 2 caractères minimum » ; au-delà de 50 : « Limite de 50 caractères atteinte » ; jamais plus de 50 enregistrés.
- [ ] **PL4** Périmètre : curseur de **50 à 800 m, pas de 50 m, défaut 100 m**.
- [ ] **PL5** Type : « Zone de confiance » sélectionné par défaut ; catégorie parmi **Maison, Travail, École, Sport, Chez un proche, Loisir, Autre**.
- [ ] **PL6** Un lieu enregistré porte : identifiant unique, nom, latitude, longitude, adresse, rayon, Miff(s), créateur, type, catégorie ; **toutes les clés** sont écrites ; `lat` et `lng` à 5 décimales.
- [ ] **PL7** Modifier permet de changer **tous les champs, type compris** ; un changement de position ou de rayon est pris en compte par les téléphones à leur prochaine relecture.
- [ ] **PL8** « Supprimer ce lieu » ouvre la confirmation (maquette `04`, tutoiement) ; la suppression est appelable **sans interface** (Premium, suppression de Miff).
- [ ] **PL9** Aucune écriture n'est affichée comme réussie avant l'accusé du serveur ; hors connexion : message, rien en file.
- [ ] **PL10** La liste montre **nom + catégorie** ; le détail montre l'adresse, la carte et le rayon, avec « Voir sur la carte » (application de cartes du système).

### Limites
- [ ] **LIM1** Compte gratuit : **2 lieux** ; au 3e : message de limite, **aucune écriture envoyée**, écran provisoire « Miff+ arrive bientôt ».
- [ ] **LIM2** Premium (compte de test avec `entitlements`) : plus de 2 lieux, tous types.
- [ ] **LIM3** Compte gratuit : choisir ou passer à « zone à risque » est refusé côté client avec le message §11 (décision du 2026-10-16).
- [ ] **LIM4** Un membre gratuit d'une Miff dont le créateur est premium reçoit et surveille les lieux (héritage).
- [ ] **LIM5** `paywall_shown` / `paywall_dismissed` (`extra_trusted_place`) : seulement si un écran s'affiche et si le consentement Analytics est donné ; aucun nom ni emplacement.

### Plusieurs Miffs
- [ ] **MIF1** Le sélecteur ne liste **que** les Miffs créées par la personne ; défaut = la dernière Miff rejointe **si** elle est dans la liste (sinon : comportement confirmé à Q4) ; sélection multiple possible.
- [ ] **MIF2** Un lieu associé à N Miffs est écrit comme N documents de **même identifiant** ; création, modification et suppression sont atomiques par lot (≤ 5 Miffs par lot) ; au-delà, reprise après interruption.
- [ ] **MIF3** Sur le téléphone, les N documents forment **une seule zone surveillée** et N statuts (un par Miff partagée).

### Adresse et carte
- [ ] **ADR1** Les suggestions apparaissent **à partir de 3 caractères**, avec temporisation de 300 ms, annulation de la requête précédente et cache de session ; la personne **choisit** une suggestion ; latitude et longitude viennent de ce choix.
- [ ] **ADR2** Le point est placé sur une carte gratuite avec le cercle du rayon, mis à jour par le curseur ; l'attribution est visible.
- [ ] **ADR3** Aucun appel réseau hors de la **liste blanche de domaines** déclarée dans ce ticket et dans la politique ; aucune clé secrète ; fournisseur de tuiles et de suggestions remplaçable par un réglage.
- [ ] **ADR4** Suggestions ou carte indisponibles : message (§11), aucun plantage, nouvelle tentative possible ; le texte saisi n'apparaît dans aucun journal ni événement.

### Autorisations (Tracking)
- [ ] **AUT1** Ordre : écran des **notifications** puis écran de la **localisation**, pendant l'onboarding, après la connexion à un compte existant et quand un changement de choix de localisation est détecté (écran sauté si tout est accordé, sous réserve de Q11).
- [ ] **AUT2** Android : la personne **ne quitte pas** l'écran sans `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` ; « Toujours autoriser » ouvre la pop-in native ; accordés : écran suivant ; sinon la pop-in reste et le message rouge [Notion] s'affiche sous le bouton.
- [ ] **AUT3** Android 11+ : après la première pop-in, le bouton renvoie vers l'écran système des paramètres ; au retour, l'état est relu ; l'écran se ferme seulement quand « Toujours autorisé » est donné.
- [ ] **AUT4** iOS : sans `authorizedAlways`, l'onboarding **ne se termine pas**.
- [ ] **AUT5** Un retrait d'autorisation constaté (ouverture, retour au premier plan) fait réapparaître l'écran bloquant.
- [ ] **AUT6** Un interrupteur de configuration à la compilation permet de ne **pas** bloquer (R1) ; testé dans les deux positions ; par défaut : **bloquant** (décision du PM).
- [ ] **AUT7** Aucune position n'est lue avant l'accord de la personne.

### Position et sélection (voir §7 pour le stockage)
- [ ] **POS1** La position est lue à l'ouverture, au passage en arrière-plan, sur déplacement significatif et à l'entrée dans une zone ; Android : déplacement de plus de 250 m ; iOS : changement de position significatif.
- [ ] **POS2** À chacune de ces occasions, la liste surveillée devient **les 20 lieux les plus proches** parmi les lieux des Miffs où la personne partage (cas d'usage Notion à 22 lieux testé).
- [ ] **POS3** Les zones dans lesquelles la personne se trouve restent surveillées (§5.2 #5, sous réserve de confirmation).
- [ ] **POS4** La sélection utilise la **liste locale** : aucune requête serveur à chaque recalcul.
- [ ] **POS5** [Décidé le 2026-11-01] La position enregistrée reste **sur le téléphone seulement** : jamais écrite dans Firestore, jamais envoyée ailleurs (test : aucune requête réseau ne contient de coordonnées de la personne) ; stockage privé de l'app, exclu des sauvegardes, **effacée à la déconnexion, à la suppression de compte et à la perte d'accès**.
- [ ] **POS6** Aucun champ de `status` ni document Firestore ne porte de coordonnées de la personne ; seul le statut de présence nommé est écrit.

### Détection et statut
- [ ] **DET1** Une zone surveillée est détectée à l'arrivée et au départ **app fermée** (recette PM).
- [ ] **DET2** Arrivée et départ produisent les valeurs du §6.6 ; arrivée dans une zone à risque écrit `in_risk`.
- [ ] **DET3** Déjà dans le lieu à l'armement : arrivée ; dehors : aucun `out` inventé.
- [ ] **DET4** Seules les Miffs où le partage est actif reçoivent une écriture ; partage arrêté, Miff quittée / retirée / supprimée, déconnexion, suppression de compte, session remplacée : surveillance arrêtée, plus aucune écriture.
- [ ] **STA1** Chaque écriture est complète et conforme à `validStatus` ; une écriture par Miff ; `updateText` et `hasPhoto` conservés ; `batteryPct` du moment.
- [ ] **STA2** Aucune coordonnée, adresse ni distance dans un statut.
- [ ] **STA3** `presenceAt` plafonné à l'heure actuelle ; une écriture de plus de 24 h de retard est abandonnée sans boucle.
- [ ] **STA4** Disjoncteur d'écritures testé unitairement.

### Vie privée
- [ ] **VIE1** Aucune coordonnée de la personne dans les journaux, Analytics, Crashlytics, messages de partage ; le gestionnaire d'événements de zone ne lit pas la position fournie avec l'événement (analyse statique et test).
- [ ] **VIE2** Journal local de détection : builds internes et TestFlight seulement, sans coordonnées, purgé à la déconnexion, **absent du build de publication** (test).
- [ ] **VIE3** Lieux purgés du cache à la perte d'accès, à la déconnexion, à la suppression de compte.
- [ ] **VIE4** Aucun nouvel événement Analytics hors `paywall_*`.

### Hors connexion et transverses
- [ ] **HOR1** Un événement détecté hors connexion est écrit au retour du réseau avec son heure réelle.
- [ ] **HOR2** Double appui = une opération ; rotation et arrière-plan sans doublon.
- [ ] **TRA1** FR et EN, clés `place_`, `permission_` identiques sur les deux plateformes (contrôle de parité) ; tutoiement ; écarts avec les maquettes listés dans la PR.
- [ ] **TRA2** Accessibilité : alternative sans carte (saisie d'adresse + curseur), lecteur d'écran, zones tactiles, texte agrandi, droite-à-gauche.
- [ ] **TRA3** App Check initialisé avant toute requête, y compris les écritures déclenchées en arrière-plan.
- [ ] **TRA4** Les PR applicatives **ne modifient pas** `firestore.rules` ; les règles viennent de PR `backend-data` séparées, testées, mesurées, déployées avec « oui » explicite.
- [ ] **TRA5** Mesure des lectures : relecture des lieux ≤ N + 2 par Miff ; lot multi-Miffs dans la marge de 20.

### Android
- [ ] **AND1** Manifeste de publication : autorisations de localisation (précise, approximative si exigée par Android 12+ **[À vérifier]**, arrière-plan) et démarrage du téléphone ; aucune autre ; contrôle CI.
- [ ] **AND2** Au plus 20 zones enregistrées ; réinscription après redémarrage du téléphone sans ouvrir l'app.
- [ ] **AND3** Test sur l'OPPO Find X5 Lite (recette PM, scénario §13).
- [ ] **AND4** Divulgation visible avant la demande, formulaire et vidéo Play préparés avant toute piste de test fermée ou ouverte.

### iOS
- [ ] **IOS1** `Info.plist` : exactement les deux clés `NSLocation…` (FR / EN) ; mode d'arrière-plan « location » non déclaré sauf nécessité démontrée ; garde-fous du dépôt réécrits **dans la même PR**.
- [ ] **IOS2** `PrivacyInfo.xcprivacy` et étiquettes App Store décrivent la donnée réellement collectée : **aucune position collectée côté serveur**, état de présence dérivé de la position (lieu nommé, heure) ; Google Play (Data safety et formulaire) dit la même chose.
- [ ] **STA5** Propagation : après l'enregistrement d'un lieu, le créateur lit le texte honnête du §11 (« …dans les prochaines heures ») ; aucun avis n'est montré aux membres (QL4).
- [ ] **IOS3** Jamais plus de 20 régions ; saturation gérée sans plantage.
- [ ] **IOS4** TestFlight sur l'iPhone 11 Pro : scénario §13 ; comportement après fermeture forcée et redémarrage constaté et documenté.

### Ce que le PM peut vérifier lui-même
- [ ] **PM1** Créer un lieu : nom, suggestions dès 3 caractères, rayon, type, catégorie, Miff ; voir la carte et le cercle.
- [ ] **PM2** Créer 2 lieux, tenter un 3e : message de limite, rien d'écrit.
- [ ] **PM3** Associer un lieu à 2 Miffs créées ; le modifier, changer son type ; le supprimer.
- [ ] **PM4** Premier lancement : écran notifications puis localisation ; refuser : message rouge sous le bouton ; Android 11+ : passage par les réglages.
- [ ] **PM5** Marcher à 400 m et revenir ; lire le statut (second téléphone ou console) ; refaire avec l'app balayée, après redémarrage.
- [ ] **PM6** Retirer l'autorisation dans les réglages : l'écran bloquant réapparaît.
- [ ] **PM7** Relire les textes à voix haute (ton).

### Pour le QA
- [ ] **QA1** Tableau de couverture critère par critère, cas à deux comptes.
- [ ] **QA2** Mêmes comportements Android et iOS.
- [ ] **QA3** Journaux de la build de débogage : aucune coordonnée.
- [ ] **QA4** Les tests de règles du dépôt passent après chaque évolution.

**Définition de « terminé »** : tous les critères de la plateforme cochés ; revues faites ; QA ; recette du PM ; « prêt à merger » par un « oui » explicite ; fusion par Claude.

---

## 18. Maquettes

### 18.1 Fournies et lues
`design/exports/lieux/` (7 images) : formulaire complet, modification, confirmation de suppression, aperçu de carte. `design/exports/onboarding-original/` : **écran 19** (autorisation des notifications), **écrans 20, 21, 22** (autorisation de la localisation : « Toujours autoriser », « Autoriser la localisation en arrière plan », état d'erreur rouge). **Ces quatre écrans correspondent au ticket Tracking** (Android) et deviennent la référence, au tutoiement. `design/exports/profil/` : liste « Lieux de la Miff » (nom, adresse, « Ajouter »).

### 18.2 Manquantes (à dessiner par `design-system`, puis à exporter par le PM)
1. **Suggestions d'adresse** sous le champ (liste, chargement, aucune, indisponible) ; saisie de l'adresse choisie.
2. **Sélection de plusieurs Miffs** (la maquette montre une liste déroulante à valeur unique) ; état « une seule Miff créée ».
3. **Écrans iOS d'autorisation** (« Toujours » obligatoire, évolution depuis « Lorsque l'app est active ») et état « autorisation retirée / réaffichage ».
4. **Écran Android 11+** : confirmer que 21 est l'écran intermédiaire vers les réglages et 22 l'état d'erreur de la pop-in.
5. **Limite gratuite atteinte** (3e lieu) et message « zone à risque : Miff+ ».
6. **Détail d'un lieu** en lecture (adresse, carte, rayon, type, catégorie, « Voir sur la carte ») et liste avec la catégorie.
7. **États du formulaire** : erreurs (2 caractères minimum, limite de 50), enregistrement, hors connexion, carte indisponible.
8. **Correction de la maquette** : catégorie **Travail** absente de `03` à `06` ; un seul bouton entre « Valider » et « Enregistrer » ; vouvoiement.
9. **Appareil remplacé** : message de déconnexion (sous-ticket session).
10. **Écran de diagnostic interne** (builds de test) : libre.
11. **Icônes des catégories** (si ce ne sont pas des emojis).

**Plus nécessaires** (version 1) : écran « Détection de tes lieux », guide fabricant, avis aux membres, cartes de consentement v2.

---

## 19. Ce que le PM doit fournir ou faire

1. Répondre aux **12 questions restantes** du §16 (Q2 à Q4 bloquent LOC-0).
2. Autoriser `backend-data` à préparer les règles du §9.2 (R-LIEU-1 et R-LIEU-3 déjà acceptées ; R-LIEU-5, -2, -6 : Q6) ; **déployer chacune avec ton « oui » explicite le moment venu** (`docs/dev/deploiement-regles.md`).
3. Exporter ou faire dessiner les maquettes du §18.2.
4. Avant la bêta externe : formulaire **Google Play** (fonction unique, vidéo, divulgation), étiquettes et note **App Store**, mise à jour de la **politique de confidentialité** et des CGU (§9.4) par `release-manager` **sans prétendre à une relecture juridique**.
5. Prévoir deux comptes de test et des créneaux de marche (§13) ; noter les libellés réels de ColorOS si un réglage est nécessaire.
6. Décider de **Blaze** (notifications, propagation instantanée) : hors de ce ticket.

Aucun moyen de paiement n'est requis. Contact et éditeur provisoires inchangés.

---

## 20. Dépendances

| Avec | Nature |
|---|---|
| INIT-2, INIT-4, AUTH-1/3 | Socle, session, purge |
| **ONB-4, ONB-8** | Les écrans d'autorisation **sont ceux du ticket Tracking** : `onboarding.md` §6.6 et critères PE1 à PE12 à **réécrire** (décision du PM) |
| MIFF-1, MIFF-3 | Écran de la Miff, consentement, statut initial, arrêt du partage, perte d'accès |
| `backend-data` | R-LIEU-5, -2, -6 (-1), R-SESS ; mesures de lectures ; `data-model.md` |
| Update (à écrire) | Même document `status` ; dépend de R-LIEU-1 ; ordre : lieux, Update, Tracking (QL19) |
| FEED-2 | Affichage des statuts (zone à risque : neutre, Q10) |
| Premium | Paywall du 3e lieu, fin d'abonnement, suppression sans écran |
| `design-system` | §18.2 |
| `release-manager` | Politique, CGU, stores, étiquettes |
| `aso-marketing` | Limite gratuite à 2, aucune promesse de notification |
| Sous-ticket session | `docs/tickets/session-appareil-unique.md` |

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`, ni les textes légaux. Aucun texte légal ou de store n'est présenté comme relu par un juriste.*
