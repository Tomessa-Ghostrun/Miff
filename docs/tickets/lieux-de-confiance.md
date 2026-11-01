# Ticket — Lieux de confiance et Tracking (détection par zones) : le cœur du produit

**Statut** : **version 3 du challenge**, mise à jour le 2026-11-01 après les **réponses du PM aux questions du §16** (voir la section « Décisions du PM » ci-dessous) ; elle suit **à la lettre** les tickets Notion « Lieux de confiance » et « Tracking » (statut Notion : « Spec en attente de review IA ») et le ticket « Statut utilisateur (Vert, orange, rouge) ». À valider par le PM avant tout développement (aucun développement lancé, aucun statut Notion modifié).
**Rédigé le** : 2026-10-31 (version 1), réécrit le 2026-11-01 (version 2), mis à jour le 2026-11-01 (version 3) (agent `product-specs`).
**Règle de hiérarchie** (décision du PM) : **Notion + décisions du PM > ce ticket > reste du dépôt**. Quand une source du dépôt contredit Notion, elle est listée au §5 pour être alignée ; elle n'est pas modifiée ici.
**Sources lues** : les deux tickets Notion (texte fourni par le PM le 2026-11-01, **non relus dans Notion**) ; les 7 maquettes `design/exports/lieux/` (README et PNG lus) ; `design/exports/onboarding-original/` écrans 19 à 22 (permissions) ; `CLAUDE.md` ; `docs/specs/` ; `docs/tickets/` (miff, onboarding, feed-profil-reglages, initialisation-technique) ; `docs/legal/` ; `docs/contracts/` ; `backend/data-model.md` et `backend/firestore.rules` (fonctions `validPlaceShape`, `validPlaceCreate`, `validPlaceUpdate`, `validStatus`, `validSharingChange`).
**Sources web (page et date de lecture)** : guide Apple Region Monitoring (archive) et documentation Android Geofencing / Location permissions (2026-10-31) ; aide Google Play « Background location » (2026-10-31) ; tarifs Google Maps Platform (page du 2026-10-30, lue le 2026-10-31) ; politiques OpenStreetMap tuiles et Nominatim, OpenFreeMap (2026-10-31) ; **Photon** (`photon.komoot.io` et README GitHub `komoot/photon`), **Geoapify** (page des tarifs, conditions version 5 du 2024-02-02, doc de l'autocomplétion), **Apple App Review Guidelines** (2026-11-01). Les pages Apple `developer.apple.com/documentation` (MKLocalSearchCompleter, Core Location récent) et la liste des champs de `request.auth.token` des règles Firestore **n'étaient pas lisibles par l'outil** : ces points sont marqués **[À vérifier]**. Aucune CGU n'est devinée : ce qui n'est pas lu est dit non lu.
**Tickets Notion non relus par moi (2026-11-01)** : « Statut utilisateur (Vert, orange, rouge) » (`3ebfe034…`), « Premium » (`3c3fe034…806c9d…`), « Feed » (`3c3fe034…80e1bb…`), « Update » (`3c3fe034…804ba8…`), « Notifications » (« A cadrer », vide), « Tracking » et « Lieux de confiance » : **mes outils n'y accèdent pas** (page privée : réponse 404 ou titre seul). **Tout ce que j'en écris vient du texte transmis par le PM ou par le coordinateur** (statut : définitions et ordre d'évaluation ; Premium : déclencheurs du paywall, 2 lieux gratuits, retour au gratuit ; Feed : bouton « Localiser », batterie, couleur de la Miff, libellés). **Je n'ai donc pas pu vérifier, ticket par ticket, qu'une question restante n'est pas déjà tranchée** : chacune dit au §16 ce que j'ai (ou n'ai pas) trouvé, et le PM ou le coordinateur doit la confronter à Notion avant de la lui poser.
**Convention** : [Notion] = repris du ticket Notion ; [Décidé] = décision du PM (source citée) ; [Proposé] = ma proposition ; [À vérifier] = non confirmé. Je ne tranche aucune décision produit ou légale.

---

## Décisions du PM (2026-11-01, réponses aux questions du §16 de la version 2)

| Ancien # | Décision | Répercutée dans |
|---|---|---|
| **Q2** | **« Toujours » obligatoire, iOS compris**, comme dans le ticket Notion Tracking. **Interrupteur de compilation** pour relâcher le blocage si Apple refuse. **Risque de rejet Apple 5.1.2(i) assumé par le PM** (écrit comme risque assumé, jamais comme « à valider »). | §6.5, R1, AUT6 |
| **Q3** | **Seul le créateur de la Miff crée des lieux, zones de danger comprises.** Quand une personne **entre dans une zone de danger, son statut devient rouge** (définitions du ticket Notion « Statut utilisateur », §6.9). **Zone de danger = Premium** (précision du même jour, d'après le ticket Notion « Premium » : « créer une zone de danger » est un déclencheur du paywall ; gratuit = 2 lieux de confiance) : le PM avait d'abord dit « pas de réservation Premium », corrigé après relecture du ticket Premium (texte transmis ; voir « Tickets Notion non relus par moi » en tête). | §6.2, §6.6, §6.9, §9.2 (R-LIEU-6), LIM3 |
| **Q4** | **Un document par Miff avec le même identifiant de lieu ; au plus 5 Miffs par création** (budget de 20 lectures de règles). Recommandation acceptée **en entier** : Miffs modifiables après création ; suppression d'un lieu = suppression dans **toutes** ses Miffs ; défaut du sélecteur = la dernière Miff rejointe **si la personne l'a créée**, sinon la première Miff qu'elle a créée. | §6.1, §6.3, §9.3, MIF1, MIF2 |
| **Q9** | **Android : 20 géofences, comme iOS** (même comportement). | §6.5, §8, AND2 |
| **Q6** | **`backend-data` prépare R-LIEU-5** (limite gratuite à 2 lieux), **R-LIEU-2** (catégorie) et **R-LIEU-6** (type modifiable). **La catégorie est obligatoire, comme tous les champs de l'écran** ; « Maison » reste présélectionnée comme sur la maquette `01` (le client n'enregistre jamais sans catégorie). Déploiement sur « oui » explicite du PM le moment venu. | §9.2, PL5 |
| **Q10, Q12, bouton** | **Bouton actif dès 1 caractère de nom ET une suggestion d'adresse choisie** ; message « 2 caractères minimum » au clic. **Un seul bouton : « Enregistrer »** (libellé de la maquette ; l'app dit déjà « Enregistrer » dans les Réglages, jamais « Valider » ; la coche en haut à droite est retirée). **Rayon inférieur à 100 m : note discrète.** | §6.1, §11, PL2 |
| **Q11** | **Notifications non bloquantes** ; **écran sauté si tout est déjà accordé** ; **seule la localisation bloque**. | §6.5, AUT1 |
| **Q13, Q14** | **Phrase du consentement gardée telle quelle** (risque assumé par le PM, jamais présentée comme relue par un juriste) ; **ajout de la mention « même app fermée »** sur l'écran d'autorisation (exigée par Google). Le message rouge de Notion est gardé. | §11, R3 |
| **Position (décision du PM, 2026-11-01, transmise après les réponses ci-dessus)** | **Elle REMPLACE la décision « position uniquement sur le téléphone » : la dernière position connue de la personne est STOCKÉE CÔTÉ SERVEUR (Firestore)**, pour le bouton « Localiser » du Feed et la localisation affichée avec la photo d'update (tickets Notion Feed et Update, transmis). Ce qui est décidé : le stockage. **Ce qui ne l'est pas** (forme, lecteurs, durée, fréquence, textes) : §7 et §16. | §7, §6.6, §6.9, §9, POS5, POS6, VIE1, R9, R18 |
| **Q15, S1 à S3** | **Un compte = un appareil** : mécanisme du document de session côté client + règles ; un appareil hors ligne n'est déconnecté qu'à son retour en ligne ; les règles refusent ses écritures entre-temps ; message de déconnexion : texte proposé. | `session-appareil-unique.md` |

**Ce que ces réponses révèlent** (nouvelles questions, §16, **ne sont pas tranchées ici**) : (1) la contradiction entre « la position reste uniquement sur le téléphone » et le ticket Notion Feed (bouton « Localiser », localisation affichée avec la photo d'update) est **réglée par une décision du PM du même jour : la dernière position connue est stockée côté serveur** (ligne suivante, §7) ; ses **impacts** (modèle, règles, consentement, textes légaux, stores, coût) sont listés au §7 et au §16 ; (2) **le mécanisme du statut vert / orange / rouge** sans serveur est écrit au **§6.9**, avec ses ambiguïtés ; (3) les seuils du ticket Notion Statut (**1 h / 2 h**) **contredisent** ceux du dépôt (**2 h / 4 h**) : §5.1 #20.

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
| 7 | **Position enregistrée** | « Aucune coordonnée de la personne ne quitte le téléphone » | Notion dit « **on enregistre sa localisation** ». **Tranché par le PM (2026-11-01, décision finale) : la dernière position connue est stockée côté serveur** (Firestore), pour « Localiser » et la localisation de la photo d'update (Feed, Update). Une décision intermédiaire du même jour (« téléphone seulement ») est **remplacée**. Conséquences (modèle, règles, consentement, textes légaux, stores, coût) au §7. |
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

En une phrase : le créateur d'une Miff **pose ses lieux sur une carte** (nom, adresse choisie dans des suggestions, rayon, type, catégorie, Miffs concernées) ; le téléphone de chaque membre **surveille les 20 lieux les plus proches** de lui et **écrit un statut** (« dans le lieu X », « hors lieu », « dans une zone de danger ») chez les Miffs où il partage, **sans ouvrir l'app** ; la **couleur** (vert, orange, rouge) est **calculée à la lecture par le téléphone de chaque proche** (§6.9).

Ce que les tickets Notion rendent nécessaire et que le dépôt ne permet pas encore (détail aux §5 et §9) :
1. **Évolutions de règles Firestore obligatoires** : limite gratuite à 2 lieux, catégorie obligatoire, type modifiable (danger = Premium), session unique ; le client ne peut pas les contourner. Elles se déploient avec ton « oui » explicite.
2. **Un lieu dans plusieurs Miffs** : un document par Miff avec le même identifiant, 5 Miffs au plus par création (**décidé**, §9.3).
3. **« Toujours » obligatoire** (**décidé**, iOS compris) : Apple écrit qu'une app **ne peut pas exiger** la localisation ni les notifications pour être utilisée (guideline 5.1.2(i), lue le 2026-11-01) : risque de rejet iOS **élevé et assumé par le PM** (R1) ; un interrupteur de compilation permet de relâcher sans réécrire.
4. **« On enregistre sa localisation »** : **décidé par le PM (2026-11-01) : la dernière position connue est stockée côté serveur**, pour « Localiser » (Feed) et la localisation de la photo d'update (Update). C'est **un changement majeur de vie privée** : modèle de données, règles, consentement, politique de confidentialité, CGU, page web, déclarations des stores et coût Spark sont à traiter (§7, §9.4, questions L1 à L9 du §16).
5. **Les seuils du statut changent** : le ticket Notion Statut dit **vert avant 1 h, orange de 1 h à 2 h, rouge au-delà**, alors que le dépôt (data-model, Feed, spec Update) dit 2 h / 4 h (§5.1 #20, §6.9).
6. **Suggestions d'adresse gratuites et mondiales** : possible, sans garantie de service (Photon) ; aucune option n'est à la fois gratuite, sans clé, avec contrat de service et conforme à l'autocomplétion. L'adresse étant obligatoire, **si les suggestions tombent, plus aucun lieu ne peut être créé** (R6).
7. **Un compte, un appareil** (**mécanisme validé**) : sans Cloud Function, dans un sous-ticket séparé ; limite : un ancien appareil **hors ligne** ne peut pas être déconnecté avant son retour en ligne.

**Taille globale** : XL (§14). **Coût** : 0 € avec les choix proposés (§12) ; aucun moyen de paiement requis.

---

## 2. Périmètre

### Dans le périmètre
- **Formulaire « Nouveau lieu / Modifier le lieu »** (maquettes `design/exports/lieux/`) : création, modification **de tous les champs, type compris**, suppression avec confirmation.
- **Liste et détail des lieux** (lecture pour les membres), point d'entrée depuis l'écran de la Miff et l'onglet Profil.
- **Limite gratuite : 2 lieux** ; premium illimité ; blocage du 3e avec message et écran provisoire (QL15).
- **Association à plusieurs Miffs** créées par la personne.
- **Tracking** [Notion] : écrans d'autorisation bloquants (notifications puis localisation), lecture et usage de la position, **sélection des 20 lieux les plus proches**, surveillance par zones, **écriture du statut**, état de la permission (`locationAccess`), nettoyage (partage arrêté, perte d'accès, déconnexion, session remplacée).
- **Mécanisme du statut vert / orange / rouge** (§6.9) : ce que le téléphone écrit (faits, jamais la couleur) et la **règle de calcul de la couleur** à la lecture (fonction pure partagée avec le Feed, FEED-2, qui l'affiche).
- **Journal local de détection** (builds internes et TestFlight).
- Textes FR/EN, évolutions de règles à demander à `backend-data`, fichiers à aligner (§9.4), maquettes manquantes (§18).
- **Sous-ticket séparé** : appareil unique par compte (`docs/tickets/session-appareil-unique.md`).

### Hors périmètre (propriétaire indiqué)
- Notifications push et tout effet d'alerte d'une zone à risque : exigent Blaze.
- Mise à jour manuelle (ticket Update) et affichage des statuts (FEED-2).
- Achat, essai, fin d'abonnement et suppression des lieux en trop (ticket Premium) ; ce ticket expose la suppression d'un lieu **appelable sans écran**.
- Consentement version 2 (abandonné), écran « Détection de tes lieux », guide fabricant, avis aux membres, option par lieu (supprimés ou non demandés).
- Lieux personnels d'un membre : **seul le créateur crée des lieux, zones de danger comprises, il n'y a pas de système d'invitation** (QL5, décision Q3 du 2026-11-01).
- **Affichage** des couleurs, bandeau et couleur globale de la Miff : Feed (FEED-2) ; ce ticket n'en fournit que la règle de calcul (§6.9).
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
        Périmètre (50 à 800 m, pas de 50, défaut 100 m) · Type · Catégorie ─► Enregistrer
  Modifier : mêmes champs, type compris, + « Supprimer ce lieu » ─► confirmation ─► suppression

Tous les membres (sans ouvrir l'app)
  position lue à l'ouverture, au passage en arrière-plan, sur déplacement significatif, à l'entrée dans une zone
  ─► les 20 lieux les plus proches sont surveillés ─► arrivée / départ ─► status/{moi} écrit dans chaque Miff partagée concernée
  ─► (chez le proche) la couleur est calculée à la lecture, avec l'heure du moment (§6.9)
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
| 1 | **« Toujours » + précision exacte obligatoires**, écran bloquant, réaffiché | `onboarding.md` Q2 (jamais bloquant) et §6.6 / critères PE1 à PE12 ; `miff.md` §5.4 point 8 et critère K4 (« n'empêche pas de rejoindre ») ; `localisation-lieux-de-confiance.md` (critères 4 et 5 : repli manuel) ; politique de confidentialité art. 4 (« Si tu refuses… tu peux continuer à utiliser Miff ») ; **Apple 5.1.2(i)** : « Your app may not require users to enable system functionalities (such as push notifications, location services, tracking) in order to access functionality, content, use the app » et 5.1.1(iv) « not … force people to consent to unnecessary data access » (lus le 2026-11-01) | Décision du PM appliquée. Ces textes sont à **réécrire** (liste §9.4). Risque de rejet iOS élevé, **assumé par le PM (Q2, 2026-11-01)** : R1. |
| 2 | **Texte de consentement actuel** : « Ton téléphone te demandera séparément l'accès à ta position. Tu peux le refuser : tu rejoins la Miff quand même. » | Tracking (refus impossible pour continuer) | **Incohérence réelle** : on rejoint la Miff (écriture faite) mais on ne peut plus utiliser l'app tant que « Toujours » n'est pas accordé. Décision QL3 : risque assumé, texte inchangé, pas de nouvelle version. Documenté en R3. Le consentement ne dit toujours pas non plus que **les lieux (adresses, carte) sont visibles de tous** ni que **la batterie est partagée** (Q14 du 2026-10-30) : même décision. |
| 3 | **Lieu associé à plusieurs Miffs** | `data-model.md` §2.9 : `places` est une sous-collection **d'une** Miff ; règles `places` par Miff | §9.3 : un document par Miff, même identifiant. |
| 4 | **Nom de 2 à 50** | Règles : 1 à 60 (`validPlaceShape`) | Compatible : le **client** impose 2 à 50 ; les règles tolèrent plus large (aucune règle à changer ; le serveur n'impose pas 50). |
| 5 | **Rayon 50 à 800 par pas de 50, défaut 100 m** | Règles 50 à 2 000 (compatible) ; spec lieux « ex. 100 m » ; `data-model.md` §2.9 « l'UI doit proposer ≥ 100 m » ; documentation Android : 100 à 150 m minimum recommandé | Notion suivi. Un rayon de 50 m est **peu fiable** (R5) : mention proposée, à confirmer (§16). |
| 6 | **Type « danger »** dans le formulaire | Décision Q10 du 2026-10-30 (zone à risque non livrée en v1) ; `data-model.md` : danger = **premium** (décision du 2026-10-16) | Le type est livré. **Effet défini par le PM (Q3, 2026-11-01)** : entrer dans une zone de danger écrit `in_risk` et le statut de la personne devient **rouge** (§6.9) ; la décision Q10 du Feed (« affichage neutre ») est **à lever** (§5.1 #23). Danger = **Premium** (Notion Premium, relayé) : cohérent avec `data-model.md`. |
| 7 | **Type modifiable** (QL16) | Règles : `type` immuable (`validPlaceUpdate`) ; limite : danger = premium | **Règle à modifier** (R-LIEU-6, **autorisée par le PM**) : autoriser le changement et exiger le premium si le nouveau type est « danger ». |
| 8 | **Catégorie** (7 valeurs) | Schéma sans `category` ; règles refusent toute clé inconnue | **Règle à modifier** (R-LIEU-2, autorisée) ; **catégorie obligatoire** [Décidé Q6]. **Travail** absent des maquettes `03` à `06` (présent sur `01`, `02`, Notion) : à corriger ; « Ecole » (Notion) / « École » (maquette). |
| 9 | **Sélecteur de Miff** : créateur seulement, plusieurs possibles, défaut « la dernière Miff rejointe » | Maquette : valeur unique, texte au singulier ; absent en modification (`03`) ; « dernière rejointe » peut ne pas être créée par la personne | **Tranché (Q4)** : défaut = dernière rejointe si créée par la personne, sinon première créée ; sélection multiple ; Miffs modifiables après création ; maquettes à corriger (§18.2). |
| 10 | **Gratuit : 2 lieux** | Règles : `p1` seul ; **1 lieu** ailleurs | Liste exhaustive §9.4 ; règle **R-LIEU-5**. |
| 11 | **Adresse obligatoire** | Règles : `address` facultative (`null` accepté) ; politique art. 3 et 5 : « adresse éventuelle » | Imposé par le **client** seulement ; textes légaux à aligner. |
| 12 | **« On enregistre sa localisation »** | `data-model.md` §1 (aucune coordonnée d'une personne) ; politique art. 1 pt 1, art. 3 (« Ce que Miff ne collecte pas : ta position GPS brute »), art. 4 (« Miff n'enregistre pas de suivi continu ») ; étiquettes des stores | **Écart tranché par le PM (2026-11-01) : dernière position connue stockée côté serveur** ; ces textes deviennent **faux** et sont à réécrire (§7, §9.4). |
| 13 | **Ordre : notifications puis localisation** | `onboarding.md` §3.1 (localisation puis notifications) | Notion suivi ; `onboarding.md` à aligner. |
| 14 | **Écran réaffiché** après connexion ou changement de choix | `onboarding.md` §6.6 principe 4 (« aucune relance par fenêtre ») | Décision du PM appliquée. |
| 15 | **20 lieux sur Android** | Limite Android : 100 (documentation lue) ; `localisation-lieux-de-confiance.md` : 100 | **Tranché (Q9)** : 20 sur Android aussi. |
| 16 | **Message rouge** « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » | `CLAUDE.md` (ton non anxiogène, y compris les erreurs) ; `onboarding.md` §4 #4 | Repris **mot pour mot** [Décidé Q13] ; conflit de ton connu et accepté. |
| 17 | **Un compte, un appareil** | `data-model.md` Q20 (déconnexion), contrat de purge, `users/{uid}/devices` (jetons FCM) | Sous-ticket séparé. |
| 18 | **Suppression « pour vous et l'ensemble des membres de la miff associé »** | Règles : suppression par le créateur seul (cohérent) | Cohérent ; ambigu si le lieu est dans plusieurs Miffs (§16). |
| 19 | **Textes de la maquette en vouvoiement** (« Choisissez », « Vous ne pourrez… ») | Décision tutoiement (Authentification Q2) | Adaptés au tutoiement ; écarts listés dans la PR (`CLAUDE.md`, règle du 2026-10-28). |
| 20 | **Seuils du statut** : [Notion Statut, transmis] vert avant **1 h**, orange de **1 h à 2 h**, rouge au-delà de **2 h** | `backend/data-model.md` §3 (`ORANGE_APRES = 2 h`, `ROUGE_APRES = 4 h`) ; `docs/specs/update-et-escalade-statut.md` (orange après 2 h, rouge après 2 h de plus, update toutes les heures en rouge) ; `feed-profil-reglages.md` (D1 : limites 1 h 59 / 2 h 00 / 3 h 59 / 4 h 00 ; B1 ; Q11 ; « rouge seulement après 4 h » comme principe de ton) ; ticket `miff.md` ; `escalationDueAt` « +2 h » (§6.6 de ce ticket, `data-model.md` §3) | **Notion l'emporte** : 1 h / 2 h. Fichiers à aligner (§9.4). **Conséquence de ton** : le rouge arrive deux fois plus vite que ce que le Feed jugeait acceptable (R17). |
| 21 | **Chevauchement confiance / danger** : [Notion Statut] la zone de **confiance l'emporte** (évaluée d'abord) | `data-model.md` §3 et version 2 de ce ticket : « la zone à risque prime » (hypothèse) | **Notion l'emporte** ; ce ticket et `data-model.md` §3 à corriger. |
| 22 | **Dernière position de la personne** : [Notion Feed, transmis] bouton « Localiser » (application de cartes avec les coordonnées de la dernière position connue), localisation affichée avec la photo d'update | `data-model.md` §1, §2.7, §3 (aucune coordonnée d'une personne) ; `feed-profil-reglages.md` Q13 (« Voir le lieu »), C9, B4 ; `miff.md` QM11 / R16 (« Localiser » : ticket Feed à définir) | **Tranché par le PM : position stockée** (§7). Notion Feed l'emporte : « Localiser » est livré avec les coordonnées de la dernière position connue ; Q13 du Feed (« Voir le lieu ») est **levée**. Documents à aligner : §9.4. |
| 23 | **Zone de danger = rouge** [Q3, Notion Statut] | `feed-profil-reglages.md` Q10 (« zone à risque non livrée, `in_risk` affiché en neutre ») ; C9 ; `miff.md` F7 (bulle rouge « à adoucir ») | Q10 est **levée** par la décision du PM ; le **libellé** reste à écrire (ton `CLAUDE.md`, §16 N5). |
| 24 | **Règle « rouge : une update toutes les heures »** et « une update manuelle ramène à vert » | `update-et-escalade-statut.md` règles 2 et 3, critères | Non reprise par le ticket Notion Statut tel que transmis ; en zone de danger la couleur ne dépend pas d'une update (§16 N3). |
| 25 | **Zone de danger = Premium, créateur seulement** [Notion Premium, transmis] | `data-model.md` §6, règles `validPlaceCreate` | Cohérent : aucune règle à changer pour la création (déjà premium). Le passage Premium vers gratuit supprime les derniers lieux : ticket Premium (§16 N7). |
### 5.2 Ambiguïtés dans Notion (non devinées)
1. **[Tranché Q10]** « Cliquables quand au moins un caractère a été saisi ET une adresse renseignée » : bouton actif dès 1 caractère de nom + suggestion choisie ; « 2 caractères minimum » au clic.
2. **[Tranché Q10]** « Adresse renseignée » = **une suggestion choisie** (latitude et longitude viennent d'elle).
3. **[Tranché]** Un seul bouton : « Enregistrer » (la coche est retirée).
4. **[Tranché Q4]** Les Miffs associées sont modifiables après création.
5. **« 20 lieux les plus proches »** : mesurés au **centre** du lieu ou à son bord ? Un utilisateur **à l'intérieur** d'un grand lieu pourrait voir ce lieu sortir de la liste (centre plus loin que 20 autres) et **rater son départ**. Proposition [Proposé] : **toujours conserver** les zones dans lesquelles la personne est actuellement. **Non couvert par les réponses du PM** : §16 N8.
6. **« Fermeture de l'app »** sur Android : il n'existe pas d'événement de fermeture fiable ; proposition : passage en arrière-plan [Proposé, détail technique sans effet visible].
7. **[Tranché Q11]** Notifications : demandées, jamais bloquantes ; seule la localisation bloque.
8. **[Tranché Q11]** Écran sauté si tout est déjà accordé.
9. **« Lieux attachés à l'utilisateur »** : lieux des Miffs dont il est membre ; **seulement** celles où il partage son statut [Proposé ; conséquence du consentement par Miff, `data-model.md` §4].
10. **[Tranché Q4]** Défaut du sélecteur : dernière Miff rejointe si créée par la personne, sinon première Miff créée.
11. **« Zone de danger »** (Notion) vs **« Zone à risque / Lieu à éviter »** (maquette, spec) : libellé et ton (R7). **Non tranché** : §16 N5.
12. **Définition d'une « update »**, point de départ du chronomètre, utilisateur jamais passé en zone de confiance, état figé d'un téléphone éteint, couleur globale de la Miff : §6.9 et §16 (N1 à N6).

---

## 6. Règles métier

### 6.1 Le lieu (formulaire) [Notion]

| Champ | Règle |
|---|---|
| Nom | **2 caractères minimum** (« 2 caractères minimum » sinon) ; **50 maximum** (« Limite de 50 caractères atteinte » sinon) ; compteur « 12/50 » (maquette) |
| Miff(s) associée(s) | Liste déroulante des Miffs **dont la personne est créatrice** (les autres n'apparaissent pas) ; **plusieurs possibles, au plus 5 par enregistrement** [Décidé Q4] ; défaut = la dernière Miff rejointe **si la personne l'a créée**, sinon la première Miff qu'elle a créée [Décidé Q4] ; modifiables après création ; texte (maquette, au tutoiement) |
| Adresse | **Obligatoire** [Décidé QL6] ; **suggestions dès 3 caractères** ; la personne **choisit une adresse** ; la latitude et la longitude sont récupérées de la suggestion choisie |
| Carte | Le point est placé sur une carte (solution gratuite) et le **rayon** est visible |
| Périmètre | Curseur de **50 à 800 m, pas de 50, défaut 100 m** ; **sous 100 m**, note discrète sous le curseur (texte §11) [Décidé Q12] |
| Type | **Zone de confiance** par défaut, ou **Zone à risque / de danger** (**Premium** : un compte gratuit qui la choisit voit le message du §11) |
| Catégorie | **Obligatoire** [Décidé Q6] : Maison (présélectionnée), Travail, École, Sport, Chez un proche, Loisir, Autre |
| Caractéristiques enregistrées | identifiant unique, Nom, Latitude, Longitude, Adresse, Rayon, Miff(s), Créateur, Type, Catégorie |
| Bouton | **Un seul : « Enregistrer »** [Décidé] ; **non cliquable par défaut** ; cliquable dès **1 caractère** de nom **et** une **suggestion d'adresse choisie** ; si le nom a moins de 2 caractères, « 2 caractères minimum » s'affiche au clic [Décidé Q10] |
| Modification | **Tous les champs, type compris** [Décidé QL16] ; suppression par « Supprimer ce lieu » avec confirmation |

### 6.2 Limites gratuite et premium [Décidé QL9]
- **Gratuit : 2 lieux**, **illimité en premium**. Le contournement des limites système n'est plus un plafond de lieux mais le mécanisme des **20 lieux les plus proches** (§6.5). Le texte « illimités » du Premium redevient exact.
- Un compte gratuit n'a qu'une Miff : 2 lieux au total. Les règles imposent la limite par **emplacements fixes** (§9.2) : **le client vérifie avant d'écrire**, le refus des règles n'est que le filet.
- **Zone à risque / de danger : Premium, créateur de la Miff seulement** [Décidé le 2026-10-16 ; confirmé par le ticket Notion Premium, transmis : « créer une zone de danger » déclenche le paywall ; gratuit = 2 lieux **de confiance**]. Un compte gratuit n'a donc **jamais** de zone de danger : la question « les deux lieux gratuits peuvent-ils être de danger ? » est **réglée (non)**.
- Au 3e lieu en gratuit : message de limite puis écran provisoire « Miff+ arrive bientôt » [Décidé QL15] ; événement `paywall_shown` (`extra_trusted_place`) déjà au contrat, avec consentement Analytics. Au choix d'une zone de danger en gratuit : même écran provisoire (événement et déclencheur à confirmer avec le ticket Premium).
- **Passage Premium vers gratuit** [Notion Premium, transmis : « suppression des derniers lieux de confiance, dernières Miffs rejointes, etc. » ; décision du 2026-10-24] : **ticket Premium**. **Points non précisés par ce que j'ai pu lire** : sort des zones de danger (toutes supprimées, puisque non gratuites ?), choix des 2 lieux de confiance conservés (les premiers créés ?), délai et préavis, qui exécute la suppression sans Cloud Function (le téléphone du créateur, au prochain lancement : §16 N7).

### 6.3 Un lieu dans plusieurs Miffs
Un lieu associé à N Miffs est enregistré comme **N documents**, un par Miff, **avec le même identifiant**. Conséquences : création, modification et suppression écrivent N documents (§9.3) ; sur le téléphone, les N documents sont **un seul lieu physique** (même identifiant, mêmes coordonnées) : une seule zone surveillée, N statuts écrits (un par Miff où la personne partage). [Décidé Q4] **Au plus 5 Miffs par création** ; **modifier les Miffs associées** crée les documents des Miffs ajoutées et supprime ceux des Miffs retirées ; **supprimer un lieu le supprime dans toutes ses Miffs** (le texte de confirmation le dit : §11).

### 6.4 Propagation vers les proches (sans notification)
Le serveur ne peut pas prévenir un téléphone fermé. Un proche apprend un changement de lieu : (1) en direct si l'écran de la Miff est ouvert ; (2) à l'ouverture de l'app ; (3) au réveil par un événement de position ou de zone, **depuis le cache local** (relecture serveur si la dernière a plus d'une heure) ; (4) par une relecture périodique en arrière-plan au plus toutes les 12 h [Proposé]. **Aucun avis n'est affiché aux membres** (QL4). Le créateur voit après l'enregistrement : « Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » La **sélection des 20 plus proches se fait sur la liste locale** (lecture du cache, pas de requête serveur à chaque déplacement). **Délai accepté par le PM pendant la bêta (2026-11-01)**, avec ce texte honnête dans l'app ; levé le jour où Blaze (notifications) est décidé.

### 6.5 Tracking [Notion]

**Autorisations.** [Décidé Q11] Les **notifications** sont demandées en premier mais **ne bloquent jamais** ; l'écran est **sauté si tout est déjà accordé** ; **seule la localisation bloque**. [Décidé Q2] Le blocage s'applique **aux deux plateformes** ; un **interrupteur de compilation** permet de ne pas bloquer (R1, AUT6).
- **Android** : sur l'écran d'autorisation, la personne ne peut pas sortir tant qu'elle n'a pas accordé `ACCESS_FINE_LOCATION` **et** `ACCESS_BACKGROUND_LOCATION`. « Toujours autoriser » ouvre la pop-in native. Si les deux sont accordés : écran suivant ; sinon la pop-in reste avec, **sous le bouton, en rouge** : « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner ». **Android 11+ (API 30+)** : « Toujours » ne s'accorde pas depuis la pop-in ; après la première pop-in, le bouton **redirige vers l'écran système des paramètres** ; la personne reste sur l'écran tant que « Toujours autorisé » n'est pas donné.
- **iOS** : sans `authorizedAlways`, l'onboarding ne peut pas se terminer.
- **Quand** : pendant l'onboarding, après la connexion à un compte existant, ou quand l'app détecte que la personne a **désactivé ou changé** ses choix de localisation : notifications **puis** localisation (§5.2 #7, #8).

**Position et zones.**
- À l'**ouverture** et à la **fermeture** (passage en arrière-plan) de l'app, la position est **enregistrée** (où : §7).
- **Android** : détection d'un déplacement de **plus de 250 m** (fournisseur de position fusionné, distance minimale de mise à jour) → mise à jour de la liste des lieux surveillés par le géofencing d'Android avec **les 20 lieux les plus proches** parmi les lieux attachés à la personne.
- **iOS** : sur « changement de position significatif » (`startMonitoringSignificantLocationChanges`) → la position est enregistrée et la liste est mise à jour ; **surveillance de régions pour les 20 zones les plus proches** de la dernière position connue.
- **Aussi** : à chaque **entrée détectée dans une zone** attachée à la personne, la liste est recalculée.
- **Cas d'usage Notion (iOS)** : 2 Miffs, 11 lieux chacune → 22 lieux attachés ; à l'ouverture, en quittant l'app, sur déplacement significatif et à l'entrée dans une zone, les 20 plus proches sont surveillés. **Même fonctionnement sur Android** avec les méthodes appropriées.

### 6.6 De l'événement au statut (valeurs de `data-model.md` §3 ; **les champs de présence ne contiennent aucune coordonnée** : la dernière position connue est un bloc **séparé**, §7)

| Événement | Écriture dans `status/{moi}` de chaque Miff partagée concernée |
|---|---|
| Arrivée dans une **zone de confiance** | `presence = "in_trusted"`, `placeId`, `presenceAt` = heure de l'événement, `lastUpdateAt` = idem, `escalationDueAt = null` |
| Départ d'une zone de confiance | `presence = "out"`, `placeId` = lieu quitté, `presenceAt` = heure de l'événement, `escalationDueAt` : **inutile sur Spark** (valeur à fixer par `backend-data` : `null`, ou « prochaine échéance » = départ + 1 h avec les nouveaux seuils ; aucun effet visible) ; `lastUpdateAt` conservé, relevé à l'heure du départ s'il a plus de 24 h |
| Arrivée dans une **zone de danger** (et dans **aucune** zone de confiance) | `presence = "in_risk"`, `placeId`, `presenceAt` ; la personne apparaît **rouge** à ses proches (§6.9) |
| Départ d'une zone de danger (sans être dans une zone de confiance) | `presence = "out"`, `presenceAt`, `placeId` = zone quittée ; chronomètre : §6.9 et §16 N2 |
| À chaque écriture | `updateText` et `hasPhoto` **conservés** ; `batteryPct` = niveau du moment ; `locationAccess` = §6.7 ; `updatedAt` = heure serveur |

Règles : un **état local « lieux où je suis »** calcule le statut (quitter A en restant dans B = `in_trusted` sur B) ; **si une zone de confiance et une zone de danger se chevauchent, la zone de confiance l'emporte** [Notion Statut : ordre d'évaluation « en zone de confiance → vert ; sinon en zone de danger → rouge » ; remplace l'hypothèse « le danger prime » du modèle §3] ; état initial : si déjà dedans à l'armement = arrivée, si dehors = **aucun `out` inventé** (statut `unknown`) ; lieu supprimé ou modifié : statut `unknown` si la présence ne peut pas être reconfirmée ; **une écriture indépendante par Miff** ; **aucune temporisation** [Décidé le 2026-11-01 : pas de temporisation en v1 ; les faux départs en bordure de zone sont acceptés et observés à la recette] ; **disjoncteur** d'écritures automatiques par jour et par appareil (valeur à fixer à la recette) ; aucune lecture de la position fournie avec l'événement.

### 6.7 `locationAccess` (quatre valeurs seulement, aucune règle modifiée)
`background` = « Toujours » + précise ; `foreground` = « pendant l'utilisation », « une fois » ou position approximative ; `denied` = refusée, restreinte ou localisation du téléphone coupée ; `unknown` = jamais demandée. Avec « Toujours » obligatoire, ces valeurs ne servent plus qu'aux états **transitoires** (retrait de l'autorisation constaté, avant que l'écran bloquant ne réapparaisse). La mise à jour de `locationAccess` après plus de 24 h dans le même état dépend de **R-LIEU-1** (§9.2).

### 6.8 Vie privée : ce que voient les autres
Tout membre voit, pour **chaque** lieu de sa Miff : nom, **adresse** (obligatoire désormais), **position sur la carte**, rayon, type, catégorie (règles `places`, inchangées). Un **intrus entré avec le code** voit donc l'adresse du domicile du créateur : risque assumé (R4, même famille que R2 du ticket Miff). Le créateur n'a aucun droit de lecture supplémentaire sur les statuts. Un lieu peut désigner un tiers (« Maison du père de Nathan ») : adresse d'une personne non utilisatrice enregistrée (R4). **Depuis la décision du 2026-11-01, un membre voit aussi la dernière position connue des autres membres de la Miff** (bouton « Localiser », localisation de la photo d'update) : qui peut la lire, voir §7 et §16 L2.

### 6.9 Statut vert / orange / rouge : le mécanisme précis, sans serveur (plan Spark, aucune Cloud Function)

**Sources** : définitions et ordre d'évaluation du ticket Notion « Statut utilisateur (Vert, orange, rouge) » (texte transmis par le PM, **non relu dans Notion**) ; ticket Notion Feed (couleur de la Miff, libellés ; transmis) ; mécanisme existant de `data-model.md` §3 ; `docs/specs/update-et-escalade-statut.md`.

**Définitions [Notion Statut, transmises]**
- **Vert** : la personne est dans une zone de confiance, **ou** a quitté une zone de confiance depuis **moins d'1 h**.
- **Orange** : a quitté une zone de confiance depuis **plus d'1 h** sans publier d'update (entre **1 h et 2 h**).
- **Rouge** : la personne est dans une **zone de danger**, **ou** était orange et est sans update depuis **plus de 2 h**.
- **Ordre d'évaluation** : (1) en zone de confiance → vert ; (2) sinon en zone de danger → rouge ; (3) sinon selon l'ancienneté de la dernière update : moins de 1 h vert, 1 h à 2 h orange, plus de 2 h rouge.

**Principe** : **le téléphone de la personne écrit des FAITS ; le téléphone de chaque proche calcule la COULEUR à la lecture.** La couleur n'est **jamais** écrite dans Firestore.

| Qui | Fait quoi |
|---|---|
| **Téléphone de la personne** | À chaque événement de zone (arrivée, départ) et à chaque update manuelle, écrit les **faits** dans `miffs/{miffId}/status/{moi}` de chaque Miff où elle partage (§6.6) : `presence` (`in_trusted` / `out` / `in_risk` / `unknown`), `placeId`, `presenceAt` (heure réelle de l'événement), `lastUpdateAt` (dernière update : voir N1), plus `batteryPct`, `locationAccess`, `updateText`, `hasPhoto`. **Ces champs ne contiennent aucune position.** Il n'écrit rien d'autre et **ne programme aucune bascule** (il peut être éteint). |
| **Téléphone d'un proche** (et la carte « moi » de la personne) | Reçoit ou relit le document de statut, **et calcule la couleur avec l'heure du moment**, selon l'algorithme ci-dessous. Orange et rouge « sans update depuis X » sont donc **obtenus par le simple écoulement du temps**, sans serveur et sans nouvelle écriture. |
| **Serveur (Firestore)** | Stocke les faits et fait respecter les règles (heure d'événement bornée −24 h / +5 min, partage actif, schéma fermé). Ne calcule rien. |

**Algorithme (identique iOS et Android, fonction pure testée ; constantes 1 h et 2 h partagées)**
```
niveau(membre, statut, maintenant):
  partage désactivé               -> NEUTRE   (« partage arrêté »)
  statut absent                   -> NEUTRE   (« pas encore de nouvelles »)
  presence = in_trusted           -> VERT                         (1) zone de confiance d'abord
  presence = in_risk              -> ROUGE                        (2) sinon zone de danger
  presence = unknown              -> NEUTRE   [hypothèse du modèle actuel ; N4]
  presence = out                  ->                              (3) sinon ancienneté
      repère  = max(presenceAt, lastUpdateAt)                     [N2 : à confirmer]
      écoulé  = maintenant - repère
      écoulé < 1 h                -> VERT
      1 h <= écoulé <= 2 h        -> ORANGE
      écoulé > 2 h                -> ROUGE
```
Bornes prises **à la lettre de Notion** (« moins d'1 h » vert, « entre 1 h et 2 h » orange, « plus de 2 h » rouge).
**Le chevauchement** zone de confiance / zone de danger est résolu **sur le téléphone de la personne** : la valeur écrite `presence` est `in_trusted` si elle est dans au moins une zone de confiance, sinon `in_risk` si elle est dans une zone de danger, sinon `out` (§6.6) ; le proche n'a donc rien à départager.

**Quand la couleur est recalculée chez le proche** [Proposé, détail visible = « la couleur change toute seule »] : à l'ouverture et au retour au premier plan ; **toutes les minutes** tant que l'écran qui l'affiche est visible ; à chaque changement du document de statut. **Sur un téléphone de proche fermé ou verrouillé, rien ne se calcule et rien n'est notifié** (Spark ; `feed-profil-reglages.md` §5.3). L'heure affichée est **absolue** (« depuis 16 h 02 ») pour orange et rouge (décision du Feed).

**Exemple (nouveaux seuils)** : la personne arrive à la maison à 08 h 00 (`in_trusted`, `presenceAt = lastUpdateAt = 08:00`), part à 15 h 00 (`out`, `presenceAt = 15:00`, `lastUpdateAt` conservé ou relevé). Chez le proche : vert jusqu'à 16 h 00 (exclu), **orange de 16 h 00 à 17 h 00 (inclus)**, **rouge après 17 h 00** ; une update manuelle à 16 h 30 repousse le repère (vert jusqu'à 17 h 30). Si la personne entre dans une zone de confiance à 16 h 40, le document passe `in_trusted` : vert.

**Ce que voit-on si le téléphone de la personne est éteint ou sans réseau**
| Situation | Ce que le proche voit | Pourquoi |
|---|---|---|
| Éteint **après** avoir écrit son départ | Orange à +1 h, rouge à +2 h (c'est le filet de sécurité voulu) | Le départ est déjà en base ; le temps fait le reste, chez le proche |
| **Sans réseau** pendant le départ | Au retour du réseau, l'événement est écrit **avec son heure réelle** (si moins de 24 h) : le proche voit d'un coup la couleur correspondant à l'heure réelle | `presenceAt` = heure de l'événement, pas de l'écriture |
| Éteint ou sans réseau **avant** que le départ ne soit écrit | **Reste « vert, dans la zone de confiance »** aussi longtemps que rien n'est écrit : **le système ne peut pas distinguer un téléphone éteint à la maison d'une personne tranquillement chez elle** | Sans écriture périodique ni serveur, aucune « absence de signal » n'est mesurable. **Limite connue, à accepter ou à traiter : N9.** |
| Éteint plus de 24 h puis rallumé | L'événement détecté tardivement est refusé par les règles au-delà de 24 h (STA3) ; la personne se réaligne à la prochaine détection ou à la prochaine update (R-LIEU-1) | Bornes d'horloge des règles |

**Couleur globale de la Miff et libellés [Notion Feed, transmis]** : **rouge** si au moins un membre est rouge, **vert** si tous sont verts, **orange** sinon. Libellés : « A quitté {catégorie ou nom du lieu} » (départ), « Dans une zone à risque » (rouge en zone de danger). Le statut ne porte que `placeId` : **le nom et la catégorie du lieu se lisent dans la liste locale des lieux** (lisible par tous les membres) ; si le lieu a été supprimé, libellé générique. La **catégorie** (R-LIEU-2) est donc utile au Feed. **Points non couverts par ce que j'ai lu** : membres « neutres » dans la couleur globale (N6), libellé rouge non anxiogène (N5), `CLAUDE.md` impose un ton rassurant (« Dans une zone à risque » en rouge est le contraire).

**Contradictions et ambiguïtés avec Notion Statut et avec le dépôt** (détail et recommandations au §16) : (a) **seuils 1 h / 2 h** contre 2 h / 4 h du dépôt (§5.1 #20) ; (b) **point de départ du chronomètre** : « ancienneté de la dernière update » (ordre d'évaluation) contredit « a quitté depuis moins d'1 h = vert » si la dernière update date d'avant le départ (N2) ; (c) **définition d'une « update »** (N1) ; (d) **utilisateur jamais passé en zone de confiance** (N4) ; (e) **update manuelle en zone de danger** : l'ordre de Notion donne rouge quel que soit l'update, la spec Update dit qu'elle ramène à vert (N3) ; (f) chevauchement : confiance l'emporte (Notion) contre « danger prime » (modèle) : **Notion suivi**.

---

## 7. « On enregistre sa localisation » : **décision du PM du 2026-11-01 = la dernière position connue est STOCKÉE CÔTÉ SERVEUR (Firestore)**

**Historique** : la version 2 du ticket retenait « sur le téléphone seulement » (décision intermédiaire du PM). Le ticket Notion **Feed** (bouton « Localiser » : application de cartes ouverte sur les coordonnées de la dernière position connue ; pourcentage de batterie) et le ticket **Update** (la photo d'update s'affiche avec la localisation), tels que transmis, supposent l'inverse. **Le PM a tranché le même jour : la position est stockée.** Cette décision **remplace** la précédente ; tout ce qui disait « aucune coordonnée de la personne » (modèle de données §1 et §3, politique de confidentialité, CGU, page web, critères POS5 / POS6 / VIE1 / IOS2, risque R9, textes d'usage iOS) est **à réécrire**.

**Ce qui est décidé** : la **dernière position connue** de la personne est écrite dans Firestore, pour « Localiser » (Feed) et la localisation affichée avec la photo d'update (Update). **Ce qui n'est PAS décidé** et ne l'est pas ici : tout le reste de la liste ci-dessous, repris en questions L1 à L9 au §16, **chacune avec ma recommandation**.

**Ce que cela déclenche : sous-travaux et questions (rien n'est rédigé ici, tout est listé)**

| # | Impact | Ce qu'il faut traiter | Propriétaire | Question |
|---|---|---|---|---|
| 1 | **Modèle de données** | Un ou plusieurs champs « dernière position » (latitude, longitude, heure de la mesure ; précision éventuelle) **sans historique** (le document est écrasé). Où : dans `status/{moi}` de chaque Miff (N écritures par mise à jour de position, effacement avec le statut) **ou** un document par personne lu par les membres (règles plus lourdes : appartenance commune à vérifier). Mise à jour de `data-model.md` §1 (principe « jamais de coordonnées »), §2.7, §3, §8, §11 | `backend-data` | L1, L2 |
| 2 | **Qui peut la lire** | Les membres d'une **même Miff** avec **consentement actif et partage non arrêté**, et **réciprocité** (comme les statuts) ? Le créateur : **rien de plus** que les autres (`miff.md` §5.9) ? Une photo d'update et sa localisation : même règle | PM, `backend-data` | L2 |
| 3 | **Effacement** | Arrêt du partage, sortie de la Miff, retrait, suppression de la Miff, suppression du compte, déconnexion forcée (session remplacée) : la position est **effacée dans le même lot** que le statut (contrat de purge de `data-model.md` §8) ; son effacement ne doit jamais dépendre d'une Cloud Function | `backend-data`, MIFF-1/3, AUTH-5 | L3 |
| 4 | **Durée de conservation** | Une position **sans limite de durée** ou **périmée au-delà d'une durée** (affichage masqué, effacement par l'app, comme les photos : illisible après X h) ? Aujourd'hui une photo d'update est illisible après 24 h (`data-model.md` §2.8) | PM | L3 |
| 5 | **Quand elle est écrite et coût Spark** | Notion Tracking : à l'ouverture, à la fermeture (arrière-plan), sur déplacement (Android > 250 m ; iOS : changement significatif), à l'entrée dans une zone. **Chaque occasion = 1 écriture par Miff où la personne partage.** Estimation **non mesurée** : plusieurs dizaines par jour et par personne × nombre de Miffs, contre **20 000 écritures par jour** pour tout le projet (Spark) : plafond d'utilisateurs actifs divisé par 3 à 10. Garde-fous à décider : écart minimal de temps ou de distance entre deux écritures, disjoncteur, une seule écriture pour plusieurs Miffs | PM, `backend-data` | L4 |
| 6 | **Règles Firestore** | Schéma `status` fermé : **nouvelle évolution de règles** (champ ou document, validation de plage de latitude / longitude, heure bornée, lecture réciproque, effacement) ; tests ; mesure de lectures ; revue de sécurité ; déploiement **sur « oui » explicite du PM** | `backend-data` | L2 |
| 7 | **Consentement et explication** | `CLAUDE.md` : consentement **explicite** de la localisation. Le texte actuel du consentement (décision Q14 : gardé tel quel) **ne dit pas** que la dernière position exacte sera visible des proches. Un texte d'explication clair (et probablement un **nouveau consentement** pour les comptes existants) est nécessaire ; la décision « phrase gardée telle quelle » (Q14) portait sur « Tu peux le refuser » et **ne couvre pas** ce nouvel usage | PM, `product-specs`, MIFF-1 | L5 |
| 8 | **Politique de confidentialité et CGU** (`docs/legal/`), **page web** (`web/`) | Réécriture de l'art. 1 pt 1, art. 3 (« Ce que Miff ne collecte pas : ta position GPS brute »), art. 4 (« n'enregistre pas de suivi continu »), art. 5 à 7 (finalité, destinataires, durée), CGU (usage de la position, anti-surveillance) ; page web et FAQ à aligner. **Sans prétendre à une relecture juridique** ; risque assumé (R15) | `release-manager` | L6 |
| 9 | **Google Play** | Formulaire « localisation en arrière-plan » (fonction unique, vidéo), **Data safety : « position précise » collectée et partagée avec d'autres utilisateurs**, divulgation dans l'app avant la fenêtre du système ; **politique anti-stalkerware** : à relire | `release-manager` | L6 |
| 10 | **App Store** | **Privacy labels** : « Précise Location » liée à l'identité de la personne ; `PrivacyInfo.xcprivacy` ; textes d'usage `NSLocation…` **à réécrire** (ceux du §11 disent « jamais ta position précise » : devenus faux) ; revue 5.1.1 / 5.1.2 | `release-manager` | L6 |
| 11 | **Analytics** | **Jamais de position, de coordonnée, de lieu ni d'adresse** dans les événements (règle du projet) ; aucun nouvel événement de position ; Crashlytics et journaux idem | ios-dev, android-dev | (règle) |
| 12 | **Affichage** | « Localiser » ouvre l'application de cartes du système sur la dernière position ; une position **ancienne** doit afficher **sa date et son heure** (jamais « en direct ») ; ton non anxiogène ; bouton absent si aucune position ; la photo d'update affiche la localisation **au moment de l'update** ou la dernière position connue (à préciser) | Feed / Update | L7 |
| 13 | **Risques** | Suivi à sens unique, anti-stalkerware, adresse de proximité, lieu et position visibles par un intrus entré avec le code (R2 de `miff.md`, R4, **R18**) | PM | L2, L8 |

**Ce qui reste vrai** : (1) la **position brute n'est jamais envoyée à chaque instant** : il n'y a **pas d'historique de trajets**, seulement la dernière position (à confirmer comme décision : L1) ; (2) la sélection des 20 lieux se fait **toujours sur le téléphone**, sur la liste locale ; (3) le gestionnaire d'événements de zone ne lit pas la position fournie avec l'événement ; (4) le journal local de détection ne contient pas de coordonnées ; (5) aucune position dans Analytics, Crashlytics, les journaux, les messages de partage.

### Options exposées avant la décision (pour mémoire : l'option B est retenue)

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

**Retenu par le PM (2026-11-01) : option B** (dernière position écrite dans Firestore), contre ma recommandation d'origine (option A) : décision du PM, appliquée ; les colonnes ci-dessus restent la liste des impacts, détaillés dans le tableau précédent. Le tableau « options » dit « si un membre pouvait la lire : risque anti-stalkerware maximal » : **c'est exactement le cas retenu** (les membres la liront pour « Localiser »), d'où R18 et la question L2.

**Dans tous les cas** : (1) la position n'est jamais dans Analytics, Crashlytics, les journaux ni un message de partage ; (2) le gestionnaire d'événements de zone ne lit pas la position fournie avec l'événement ; (3) le journal local de détection (builds de test) ne contient pas de coordonnées.

---

## 8. Différences Android / iOS

| Sujet | Android | iOS (iOS 16 minimum) |
|---|---|---|
| Moteur de zones | API de géofencing de Google Play services ; 100 par app (doc) ; **20 retenus** [Notion, confirmé par le PM : Q9] | Surveillance de régions de Core Location ; **20 par app** (guide Apple) |
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
| **R-LIEU-5** | **2 lieux gratuits** | Emplacements `p1` et `p2` (type `trusted` en gratuit) au lieu de `p1` seul ; mise à jour des tests (`06-places`, mutations, fixtures) | **Oui** (Notion) ; **préparation autorisée par le PM (Q6)** |
| **R-LIEU-2** | **Catégorie** | Clé `category` ajoutée à `validPlaceShape` (liste fermée : `home`, `work`, `school`, `sport`, `relative`, `leisure`, `other` [Proposé : valeurs techniques]) ; **clé obligatoire** [Décidé Q6] | **Oui** (Notion) ; **préparation autorisée (Q6)** |
| **R-LIEU-6** | **Type modifiable ; danger = Premium** | Ajouter `type` aux clés modifiables ; exiger le premium si le nouveau type est `risk` (lectures de règles : `isPremium` jusqu'à 3 de plus) ; un compte gratuit ne peut pas passer un lieu en danger | **Oui** (QL16) ; **préparation autorisée (Q6)** |
| **R-LIEU-POS** | **Dernière position connue stockée** (décision du 2026-11-01) | Champ(s) de position dans `status` ou document dédié ; validation (latitude −90 à 90, longitude −180 à 180, heure bornée) ; lecture réciproque ; effacement avec le statut ; mesure de lectures et d'écritures ; mise à jour de `data-model.md` §1, §2.7, §3, §8, §11 | **Oui, nouvelle** ; **forme, lecteurs et durée non décidés** (§7, L1 à L4) ; à livrer **avant** LOC-2 et le ticket Update ; **pas encore autorisée par le PM** (préparation à autoriser : L9) |
| **R-LIEU-1** | Réécrire un statut **sans changer `presenceAt` / `lastUpdateAt`** quand ils ont plus de 24 h (mise à jour manuelle après un long séjour, rafraîchissement de `locationAccess`) | Accepter les champs **inchangés** (lecture de `resource.data`, aucune lecture de règles de plus) | **Acceptée par le PM (2026-11-01)** ; à livrer **avant le ticket Update** |
| **R-LIEU-3** | **Renouveler un consentement actif** (nouvelle version du texte) sans arrêter le partage | Accepter la transition « activé, activé » si la nouvelle version est supérieure et `consentedAt` = heure serveur | **Acceptée par le PM (2026-11-01)** comme évolution de règle ; **sans usage immédiat** (pas de consentement v2, QL3) ; la politique promet la redemande du consentement en cas de changement (art. 12) |
| **R-SESS** | Appareil unique | Voir `session-appareil-unique.md` | Oui, sous-ticket séparé |

Chaque déploiement : tests, mesure des lectures, revue de sécurité, **« oui » explicite du PM le moment venu** (`docs/dev/deploiement-regles.md`). Aucun n'est fait ici.

### 9.3 Un lieu dans plusieurs Miffs : options pour `backend-data`
- **Option A (retenue par le PM, Q4)** : **un document par Miff**, même identifiant (créé côté client une seule fois). Aucune règle de lecture à changer ; création, modification, suppression = lot de N documents (**N ≤ 5** à la création [Décidé] ; la limite à appliquer quand on ajoute des Miffs en modification est **à mesurer et fixer par `backend-data`**). Inconvénient : cohérence entre copies (un lot unique est atomique).
- **Option B** : collection de premier niveau `places/{id}` avec une liste `miffIds`. Les règles ne savent pas parcourir une liste pour contrôler l'appartenance de lecture : **réécriture lourde**, non recommandée.

**Budget de lectures de règles (limite 20 par lot)** : création premium ≤ 4 **mesuré pour un document** (`data-model.md` §12) → **au pire sans cache 4 × N** : **au plus 5 Miffs par création** [Décidé Q4, pas de reprise multi-lots à prévoir]. Création gratuite d'un emplacement `p1`/`p2` : 1 lecture par document. Modification : 1 par document (+3 si le type passe à danger). Suppression : 1 par document. **À mesurer par `backend-data`** avant le développement.

| Opération | Écritures | Lectures de règles attendues |
|---|---|---|
| Créer un lieu, gratuit (1 Miff) | 1 | ≈ 1 |
| Créer un lieu, premium, N Miffs | N | ≤ 4N (pire cas sans cache) |
| Modifier, N Miffs | N | ≈ N (+ 3N si type vers danger) |
| Supprimer, N Miffs | N | ≈ N |
| Lire la liste (par Miff) | requête | 2 + un par document |
| Écrire un statut (par Miff) | 1 | ≤ 1 |

**Lectures ajoutées par utilisateur actif et par jour** (estimation, pas une mesure) : relectures serveur des lieux (au plus 2 par jour × (N + 2) par Miff) ≈ 6 à 20 ; le tri des 20 plus proches se fait sur le **cache local** (0 lecture). Écritures : 6 à 10 événements de statut par jour. **Position stockée (§7, option B retenue) : en plus, une écriture de position par occasion (ouverture, fermeture, déplacement, entrée dans une zone) et par Miff où la personne partage : plusieurs dizaines par jour possibles, estimation non mesurée, à encadrer (L4).**

### 9.4 Fichiers à aligner (limite gratuite 2 ; catégorie ; type ; Miffs) : liste trouvée par recherche dans le dépôt
Aucun n'est modifié par ce ticket.
- **`CLAUDE.md`** (« gratuit = 1 lieu de confiance »).
- **Règles et modèle** : `backend/firestore.rules` (en-tête ligne 24 et `validPlaceCreate`, ligne 417 : `p1`, type, `category`) ; `backend/data-model.md` (§6 tableau « 1 lieu de confiance (gratuit) » ; §8 ligne « Créer / modifier / supprimer un lieu » ; §2.9 schéma (catégorie, modifiabilité du type) ; §15 décision 5 et question 4 ; §14) ; `backend/rules-test-plan.md` ; `backend/rules-test-report.md` ; tests : `tests/rules/06-places.test.js`, `10-complements.test.js`, `12-durcissement-dur2.test.js`, `13-complements-dur3.test.js`, `09-rule-reads.test.js`, `16-lot-entree-miff0.test.js`, `tests/helpers/fixtures.js`, `tests/tools/mutations-dur3.mjs`, `tests/unit/mutations.test.js`.
- **Specs** : `docs/specs/lieux-de-confiance.md`, `docs/specs/paywall-premium.md`, `docs/specs/miff-groupes.md`, `docs/specs/localisation-lieux-de-confiance.md` (repli manuel, « Plus tard », 100 zones).
- **Tickets** : `docs/tickets/onboarding.md` (§3.1 ordre, §6.6, critères PE1 à PE12, §6.8, risque R10, §4 hors périmètre) ; `docs/tickets/miff.md` (§5.4 point 8, K4, texte de consentement, §4 #17 et 18) ; `docs/tickets/feed-profil-reglages.md` (§4B B13, §10 Q3 / Q10, §13 décisions : Q10 « zone à risque non livrée » à lever) ; `docs/tickets/initialisation-technique.md` (lignes 314 à 316 : limites gratuites).
- **Contrats et textes** : `docs/contracts/analytics-events.md` (note `extra_trusted_place` : « un seul lieu ») ; `docs/legal/cgu.fr.md` et `cgu.en.md` (lignes 57, 90, 95 : « un seul lieu » / « one trusted place »), `docs/legal/README.md` ; **politique de confidentialité** FR et EN (art. 1, 3, 4, 5, 6) : y écrire que la position est lue **sur l'appareil seulement** pour choisir les lieux (§7), les fournisseurs de suggestions d'adresse et de cartes (§12), l'adresse obligatoire des lieux, la limite gratuite à 2 lieux ; **déclarations des stores** : aucune position collectée côté serveur.
- **Site web** : `web/src/landing.json` (lignes 88, 90, 107, 210, 212, 229), `web/tests/landing.test.mjs` (lignes 58, 61), `web/README.md` (ligne 46).
- **Marketing** : `marketing/` (fiches des stores, si elles citent la limite gratuite ou « illimités ») : **non lu**, à vérifier.
- **Position stockée (décision du 2026-11-01) : textes et documents devenus faux** : `backend/data-model.md` §1 (« On ne stocke jamais de coordonnées de l'utilisateur »), §2.7, §3, §8, §11, §15 ; `docs/legal/politique-confidentialite.fr.md` et `.en.md` (art. 1 pt 1 « Miff ne stocke pas ta position GPS », art. 3 « Ce que Miff ne collecte pas : ta position GPS brute », art. 4 « n'enregistre pas de suivi continu », art. 5 à 7) ; `docs/legal/cgu.fr.md` ligne 65 et `cgu.en.md` (équivalent) ; **page web** `web/src/landing.json` lignes 56 (« Ta position reste entre tes mains »), 65 et 187 (« Miff ne stocke pas ta position GPS »), 99 et 221 (« Miff ne stocke ni ta position GPS ni… ») + `web/tests/landing.test.mjs` si les textes sont testés ; `docs/tickets/miff.md` (§5.4 point 8, QM11, R16, texte de consentement), `docs/tickets/feed-profil-reglages.md` (Q13, C9, B4, R3, §6.3), `docs/tickets/onboarding.md` ; `marketing/` (promesse « ta position reste chez toi » : **non lu**, à vérifier) ; fiches et déclarations des stores.
- **Statut (seuils 1 h / 2 h, danger = rouge, confiance l'emporte)** : `backend/data-model.md` §3 (constantes `ORANGE_APRES` / `ROUGE_APRES`, `in_risk`, chevauchement, `escalationDueAt`) ; `docs/specs/update-et-escalade-statut.md` (règles 1 à 3) ; `docs/specs/feed-cercle-de-confiance.md` ; `docs/tickets/feed-profil-reglages.md` (B1, D1 et ses bornes 1 h 59 / 2 h 00 / 3 h 59 / 4 h 00, feed-5, feed-7, Q10, Q11, principe « rouge seulement après 4 h ») ; `docs/tickets/miff.md` F7 ; tests de règles de `status` si une valeur change.
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
9. **Téléphone éteint** : aucun événement ; dernier statut affiché ; départ connu = couleur calculée par l'app du proche (orange à 1 h, rouge à 2 h après le départ connu) ; **sans départ écrit, le dernier état (« vert, à la maison ») reste affiché** (§6.9, N9).
10. **Autorisation retirée app fermée** : sur Android l'app est fermée par le système ; rien ne s'exécute avant la prochaine exécution ; l'écran bloquant s'affiche alors.
11. **Horloge décalée de plus de 5 min** : heure d'événement plafonnée à l'heure actuelle.
12. **Rayon de 50 m** : détection peu fiable (R5).
13. **Création hors connexion** : impossible, rien en file ; suggestions indisponibles : message, nouvelle tentative possible.
14. **Lieu créé pour 3 Miffs dont l'une a été supprimée entre-temps** : lot refusé en entier ; message neutre.
15. **Compte gratuit qui ouvre le 3e lieu** : message de limite, aucune écriture.
16. **Changement de type vers « danger » par un compte gratuit** : refusé par le client (et par R-LIEU-6) ; écran provisoire.
17. **Compte connecté sur deux téléphones** : traité par le sous-ticket session.
18. **Taille de texte maximale, droite-à-gauche** : formulaire lisible.
19. **Zone de confiance et zone de danger qui se chevauchent** : la personne est dans les deux → `in_trusted` (vert) ; elle quitte la zone de confiance en restant dans la zone de danger → `in_risk` (rouge).
20. **Personne qui n'est jamais passée en zone de confiance** (aucun départ connu) : `unknown`, couleur neutre, aucune escalade (N4).
21. **Position stockée très ancienne** (téléphone éteint, partage arrêté puis repris) : jamais présentée comme actuelle ; date et heure affichées ; durée de validité : L3.
22. **Lieu partagé par plusieurs Miffs** : la position est écrite pour chaque Miff où la personne partage (selon la forme retenue, L1) ; arrêter le partage dans l'une efface la position **de cette Miff seulement**.

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
| Rayon sous 100 m [Décidé Q12, texte de la reco] | « En dessous de 100 m, la détection peut manquer un passage. » (note discrète sous le curseur) | "Below 100 m, detection may miss a visit." |
| Type | « Quel type de zone définis-tu ? » ; « Zone de confiance — Lieu sûr pour tes proches » ; « Zone à risque — Lieu à éviter » (libellés de la maquette conservés) | "What type of zone are you setting?"; "Trusted zone — A safe place for your loved ones"; "Risk zone — A place to avoid" |
| Catégorie | « Sélectionne la catégorie qui convient » ; Maison, Travail, École, Sport, Chez un proche, Loisir, Autre | "Select the matching category"; Home, Work, School, Sport, At a relative's, Leisure, Other |
| Boutons [Décidé : un seul bouton, comme dans Réglages] | « Enregistrer » ; « Supprimer ce lieu » (jamais « Valider ») | "Save"; "Delete this place" |
| Suppression (maquette adaptée ; suppression dans **toutes** les Miffs [Décidé Q4]) | « Supprimer ce lieu ? Ce lieu sera définitivement supprimé pour toi et pour tous les membres des Miffs associées. » + « Supprimer ce lieu » / « Annuler » | "Delete this place? It will be permanently deleted for you and for all the members of the associated Miffs." |
| Succès | « Le lieu « {nom} » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » | "The place "{name}" is saved. Your loved ones will pick it up as soon as they open Miff, or within the next few hours." |
| Limite gratuite (2) | « Avec la formule gratuite, tu peux avoir 2 lieux. Miff+ te permet d'en ajouter d'autres. » + « Découvrir Miff+ » / « Plus tard » | "With the free plan, you can have 2 places. Miff+ lets you add more." |
| Zone à risque en gratuit | « Les zones à risque font partie de Miff+. » | "Risk zones are part of Miff+." |
| Suggestions : aucune / indisponibles | « On ne trouve pas cette adresse. Essaie avec moins de détails. » / « La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant. » | "We can't find this address. Try fewer details." / "Address search isn't available right now. Please try again in a moment." |
| Hors connexion, erreur | (textes du ticket Miff §5.10) | (same) |
| **Message rouge Android** [Notion, mot pour mot] | « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » | Pas de traduction dans Notion ; proposition : "Miff needs the "Allow all the time" option and precise location to work" |
| Écran d'autorisation de la localisation (maquettes 20 à 22 adaptées au tutoiement) | Titre « Savoir que vous allez bien ! » devient « Savoir que tu vas bien ! » ; corps « Miff a besoin d'accéder à ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. » ; bouton « Toujours autoriser » ; Android 11+ : « Autoriser la localisation en arrière plan ». **Réserve** : ce texte (celui de la maquette) ne dit ni « en arrière-plan / app fermée » ni ce qui est partagé : la divulgation exigée par Google Play avant la fenêtre du système (« position », « quand l'app est fermée », fonction concernée) est à ajouter (Q13). Texte proposé : « Pour signaler à ta Miff quand tu arrives ou pars des lieux qu'elle a définis, Miff utilise la position de ton téléphone, même quand l'app est fermée. Tes proches voient seulement un état (par exemple « à la maison ») et l'heure. » **[Décidé Q13 : « même app fermée » gardé. Phrase finale « seulement un état… » devenue FAUSSE depuis la décision du 2026-11-01 (la dernière position connue est visible des proches) : texte à réécrire par le PM après L2 et L5, jamais à publier tel quel.]** | "Know that you're fine!" ; "Miff needs access to your location so your loved ones can know whether you're safe." ; "Always allow" ; proposed disclosure: "To let your Miff know when you arrive at or leave the places it has set, Miff uses your phone's location, even when the app is closed. Your loved ones only see a status (for example "at home") and the time." |
| Textes d'usage iOS (`NSLocationWhenInUseUsageDescription`) | « Miff utilise ta position pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance. Elle n'est jamais enregistrée comme un trajet. » **[À réécrire : seule la dernière position connue est conservée, visible de ta Miff (décision du 2026-11-01) ; « jamais enregistrée » est à vérifier mot par mot avec L1 et L3]** | "Miff uses your location to let your Miff know when you arrive at or leave a trusted place. It is never saved as a route." |
| Textes d'usage iOS (`NSLocationAlwaysAndWhenInUseUsageDescription`) | « Pour te signaler à ta Miff quand tu arrives ou pars d'un lieu de confiance, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Seul un état (par exemple « à la maison ») et l'heure sont partagés, jamais ta position précise. » **[FAUX depuis la décision du 2026-11-01 : la position précise (dernière connue) est conservée et visible de ta Miff ; texte à réécrire avant toute build iOS ; ne pas publier tel quel]** | "To let your Miff know when you arrive at or leave a trusted place, even when the app is closed, Miff needs your location in the background. Only a status (for example "at home") and the time are shared, never your precise location." |

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
| **LOC-0** | Décisions, règles, textes, design | Réponses restantes du §16 ; **R-LIEU-5, R-LIEU-2, R-LIEU-6** (autorisées, Q6) (+ R-LIEU-1 avant Update, R-LIEU-3 acceptée) (écriture, tests, mesures, déploiement avec « oui ») ; **R-LIEU-POS (position stockée) : conception après L1 à L4** ; mise à jour des fichiers du §9.4 (dont les textes devenus faux) ; maquettes manquantes (§18) | M à L (L avec la position) | validation |
| **LOC-1** | **Android : lieux** | Formulaire (§6.1), suggestions (Photon), carte (MapLibre), multi-Miffs, limite 2, liste et détail, suppression sans écran | L | MIFF-1, ONB-3, LOC-0 |
| **LOC-2** | **Android : Tracking** | Écrans d'autorisation (notifications non bloquantes puis localisation bloquante), position, 20 plus proches, zones, écriture du statut (§6.9) **et de la dernière position (§7)**, `locationAccess`, nettoyage, réarmement, journal local | L à XL | LOC-1, MIFF-1, **ONB-4 (à réécrire)** ; ticket Update recommandé avant (QL19) ; **R-LIEU-POS déployée et L1 à L6 tranchées** |
| **SESS-1/2/3** | **Appareil unique par compte** (règles, Android, iOS) | `docs/tickets/session-appareil-unique.md` | M chacun | AUTH-1/3 |
| **LOC-3** | **iOS : lieux** | Mêmes livrables ; MapKit ; `MKLocalSearchCompleter` | L | LOC-1, MIFF-3, ONB-7 |
| **LOC-4** | **iOS : Tracking** | Mêmes livrables ; garde-fous du dépôt réécrits dans cette PR ; `authorizedAlways` obligatoire | L à XL | LOC-2, LOC-3, **ONB-8 (à réécrire)** |

Plus de spike. Chaque PR : revue de code en contexte neuf, revue de sécurité (position, journaux, clés, autorisations), QA, recette du PM, fusion par Claude après « oui » explicite du PM.

---

## 15. Risques

| # | Risque | Mitigation |
|---|---|---|
| **R1** | **Rejet Apple** : « Toujours » et notifications **exigées** pour utiliser l'app (5.1.2(i), lu le 2026-11-01), 5.1.1(iv) ; rejet ou questions Google sur la divulgation | Décision du PM appliquée (Q2, 2026-11-01) ; **interrupteur de compilation** pour relâcher le blocage sans réécrire si Apple refuse ; note de revue et divulgation préparées ; **risque de rejet 5.1.2(i) assumé par le PM**, jamais « à valider » |
| **R2** | **Détection non fiable** selon le fabricant (ColorOS) ou liste recalculée avec retard (limites d'arrière-plan Android) | Pas de spike ni de plan B (décisions QL1, QL2) ; recette PM ; ajustement après retours |
| **R3** | **Consentement inchangé** : phrase « Tu peux le refuser » devenue fausse ; lieux visibles et batterie non cités | **Risque assumé par le PM** (QL3 et Q14, 2026-11-01) pour la phrase « Tu peux le refuser » ; jamais présenté comme « à valider ». **Ne couvre pas** la position stockée (R18, L5) |
| **R4** | **Adresse obligatoire et visible de tout porteur du code**, adresse d'un tiers possible | Risque assumé ; adresse jamais dans les journaux ; renouvellement du code à chaque retrait |
| **R5** | Rayon de 50 m peu fiable (100 à 150 m recommandés par Android) | Notion suivi ; mention proposée (Q12) |
| **R6** | **Fournisseurs gratuits sans garantie** (Photon, OpenFreeMap) ; MapKit non lu ; l'adresse étant **obligatoire**, une panne des suggestions **empêche toute création de lieu** | Fournisseur remplaçable par un réglage ; repli Geoapify (décision PM) ; signal de bascule §12 |
| **R7** | Libellé « zone de danger / à risque » anxiogène ; effet non défini | Libellés de la maquette conservés (décision antérieure), à revoir avant les stores |
| **R8** | Évolutions de règles nombreuses (R-LIEU-5, -2, -6, SESS) | Tests, mesures, déploiement avec « oui » ; client qui vérifie avant d'écrire |
| **R9** | Position gardée sur le téléphone (tri des 20 lieux) : donnée sensible locale (verrouillage de l'appareil, sauvegardes) | Stockage privé de l'app, exclu des sauvegardes, effacé à la déconnexion et à la suppression de compte ; jamais dans les journaux |
| **R10** | Lot multi-Miffs limité par les 20 lectures de règles | **Au plus 5 Miffs par création** [Décidé Q4] ; limite en modification à fixer par mesure |
| **R11** | Un ancien appareil hors ligne reste connecté jusqu'à son retour réseau | Sous-ticket session |
| **R12** | Propagation lente des lieux (pas de notification) | Texte honnête ; relectures ; Blaze plus tard |
| **R13** | Promesse de notifications (écran 19 : « Thomas est bien arrivé à l'école ») alors que rien n'est envoyé (Spark) | Texte honnête (`onboarding.md` §6.6 déjà) ; fiche de store |
| **R14** | iOS testable seulement à pied et par TestFlight ; minutes macOS limitées | Logique pure en CI ; journal local |
| **R15** | Aucun texte relu par un juriste | **Risque assumé par le PM** (2026-10-23 et QL20) |
| **R16** | **Zone de danger utilisable comme outil de contrôle** : le créateur définit une zone ; toute personne qui y entre apparaît **en rouge** à tout son cercle, sans l'avoir choisi. Peut servir à surveiller un proche (couple, famille) ; sensible pour les stores (anti-stalkerware) et contraire au ton `CLAUDE.md` si mal formulé. **Non reconnu comme risque assumé par le PM à ce jour** | Le membre voit la liste des lieux et leur type (lecture) ; libellé calme (N5) ; aucune notification (Spark) ; le PM décide s'il l'assume ou s'il veut une garde (ex. information du membre quand une zone de danger est ajoutée : contredirait QL4, donc à lui de trancher) |
| **R17** | **Seuils plus courts que prévu** (orange à 1 h, rouge à 2 h au lieu de 2 h / 4 h) : un trajet ou un après-midi sans update donne « rouge » à tout le cercle ; contraire à l'intention de ton du Feed (« rouge seulement après 4 h ») | Notion l'emporte ; libellés calmes, heures absolues, aucune notification ; à observer à la recette |
| **R18** | **Dernière position connue stockée et lisible des proches** (décision du 2026-11-01) : suivi à sens unique, donnée très sensible côté serveur, **motif de rejet ou de retrait** possible (Google : localisation en arrière-plan et anti-stalkerware ; Apple : labels et 5.1.1), consentement actuel insuffisant, textes légaux et page web devenus faux | Impacts listés au §7 ; consentement explicite et **nouveau** (L5) ; lecture limitée aux membres avec partage actif (L2) ; effacement avec le statut (L3) ; jamais d'historique (L1) ; **risque à reconnaître par le PM**, aucun texte ne doit prétendre à une relecture juridique |

---

## 16. Questions restantes pour le PM (courtes, avec ma recommandation)

**Réglées et retirées de cette liste** (réponses du PM du 2026-11-01, voir « Décisions du PM » en tête) : Q2, Q3 (effet de la zone de danger et créateur seul), Q4, Q6, Q9, Q10, Q11, Q12, Q13, Q14, Q15 ; **position stockée côté serveur** (§7) ; propagation lente des lieux **acceptée en bêta** (§6.4) ; **pas de temporisation** des faux départs (§6.6) ; R-LIEU-1 et R-LIEU-3 **acceptées** (§9.2) ; zone de danger = **Premium** [Notion Premium, transmis] (§6.2).

**Règle appliquée** : une réponse déjà présente dans un ticket Notion est une décision, pas une question. **Je n'ai pas pu lire les tickets Notion** (§ « Tickets Notion non relus par moi ») : la colonne « Couvert par un ticket ? » dit ce que j'ai trouvé **dans le texte transmis et dans le dépôt**, pas dans Notion lui-même ; **à confronter à Notion avant de poser la question au PM**.

### A. Bloquantes pour démarrer LOC-0 / LOC-1

LOC-1 (formulaire Android des lieux) ne dépend que de **Q5**. LOC-0 (règles, textes) peut préparer R-LIEU-5, -2, -6 dès maintenant (autorisées) ; la **partie « position stockée » de LOC-0** (R-LIEU-POS, `data-model.md`, textes légaux, consentement) attend **L1, L2, L3, L5, L9**.

| # | Question | Reco | Couvert par un ticket ? |
|---|---|---|---|
| **Q5** | **Suggestions d'adresse Android** : Photon d'abord (sans clé, sans garantie de service), Geoapify en repli (clé, décision PM) ? iOS : `MKLocalSearchCompleter` à vérifier sur l'iPhone ? (Non répondue le 2026-11-01.) | Photon puis Geoapify | **Non** : Notion Lieux, tel que transmis, exige « API 100 % gratuite et mondiale » sans nommer de fournisseur ; aucun ticket n'en choisit un. |
| **L1** | **Forme de la position stockée** : **seulement la dernière position connue, écrasée à chaque mise à jour, aucun historique de trajets** ? Champs : latitude, longitude, heure de la mesure (précision facultative) ; **même précision que les lieux (5 décimales) ou plus grossière** ? | Dernière position seule, jamais d'historique ; heure de mesure obligatoire ; précision normale (une position grossière rendrait « Localiser » inutile), à reconsidérer si le PM préfère protéger | **Partiellement** : « dernière position connue » (Notion Feed, transmis) implique une seule position ; l'absence d'historique, les champs et la précision ne sont écrits nulle part. |
| **L2** | **Qui peut lire la position** : les **membres d'une même Miff avec consentement actif, partage non arrêté et réciprocité** (comme les statuts) ? **Le créateur n'a rien de plus** ? Un membre qui n'a pas encore publié son propre statut ne voit rien ? | Oui, exactement la règle de lecture des statuts ; jamais de lecture par le créateur ou un tiers | **Non** : le ticket Feed (transmis) donne le bouton sur la carte d'un membre, pas la règle d'accès ; la règle de réciprocité existe pour les statuts (`data-model.md` §2.7). |
| **L3** | **Effacement et durée** : la position est **effacée dans le même lot** que le statut (arrêt du partage, sortie, retrait, suppression de la Miff ou du compte). **Durée** : masquée au-delà de X h (la photo d'update est illisible après 24 h) ou conservée tant qu'elle n'est pas remplacée ? Jamais présentée comme « en direct » ? | Effacement avec le statut ; masquée après **24 h** comme la photo ; date et heure toujours affichées | **Non** : aucun ticket lu ne parle de durée ou d'effacement de la position. |
| **L5** | **Consentement et explication** : un **nouveau texte** dit clairement que **la dernière position exacte est visible des membres de ta Miff**, et les comptes qui ont déjà consenti doivent **re-consentir** (R-LIEU-3, déjà acceptée) avant l'écriture de la position ? Q14 (phrase « Tu peux le refuser » gardée) ne couvre pas ce nouvel usage. | Oui : texte explicite, re-consentement, pas d'écriture de position avant | **Non** : Q14 ne portait que sur la phrase de refus ; `CLAUDE.md` exige un consentement explicite. |
| **L9** | **Autorises-tu `backend-data` à préparer R-LIEU-POS** (champs, règles, tests, mesures ; déploiement toujours sur ton « oui » explicite) ? | Oui, une fois L1 à L3 répondues | **Non** : aucune autorisation de préparation n'existe pour cette règle (Q6 portait sur R-LIEU-5, -2, -6). |

### B. Non bloquantes pour démarrer LOC-0 / LOC-1

« À répondre avant » dit ce que chaque question bloque ensuite.

| # | Question | Reco | À répondre avant | Couvert par un ticket ? |
|---|---|---|---|---|
| **L4** | **Quand et à quelle fréquence la position est écrite** et **coût Spark** : Notion Tracking dit « à l'ouverture, à la fermeture, sur déplacement, à l'entrée dans une zone » : **chaque occasion** écrit-elle, pour **chaque Miff** ? Garde-fou : pas plus d'une écriture toutes les X minutes ou tous les Y mètres ? | Garde-fou (ex. 15 min **ou** 250 m, à fixer par mesure) ; une seule position écrite par Miff et par occasion ; disjoncteur par jour | LOC-2 | **Partiellement** : les occasions sont dans Notion Tracking (transmis) ; ni fréquence maximale ni coût. Quota Spark : `CLAUDE.md`, `docs/dev/surveillance-quotas.md`. |
| **L6** | **Textes légaux, web, stores** : accord pour que `release-manager` réécrive politique, CGU, page web et préparations stores (Google « localisation en arrière-plan », Data safety, App Store privacy labels) selon la liste du §7 et §9.4, **sans prétendre à une relecture juridique** ? | Oui, avant la bêta externe ; aucune publication des textes actuels devenus faux | Bêta externe | **Non** (aucun ticket n'existe pour cela ; `docs/legal/README.md` renvoie à `release-manager`). |
| **L7** | **Photo d'update et localisation** : la localisation affichée avec la photo est-elle **la position au moment de l'update** (écrite avec l'update) ou **la dernière position connue** ? « Localiser » sur une position ancienne : texte (« Dernière position connue à 08 h 12 ») ? | Position **au moment de l'update**, date et heure toujours visibles ; ni « en direct » ni alarmiste | Ticket Update / FEED-2 | **À confirmer dans Notion Update** (que je n'ai pas pu lire) : le texte transmis dit « la localisation » sans préciser laquelle. |
| **L8** | **Risques R16 et R18** : les **reconnais-tu comme risques assumés** (zone de danger utilisable comme contrôle ; position stockée lisible des proches), comme tu l'as fait pour R1 et R15 ? | À toi ; je ne les écris pas « assumés » sans ta réponse | Bêta externe | **Non** : seuls R1, R3, R4, R15 sont assumés par écrit. |
| **N1** | **Définition d'une « update »** (ticket Statut : « sans publier d'update ») : mise à jour manuelle (texte, photo facultatifs) **ou** entrée en zone de confiance ? **Ouvrir l'app** compte-t-il ? | Manuelle **ou** entrée en zone de confiance (déjà `lastUpdateAt` dans `data-model.md`) ; ouvrir l'app ne compte pas | LOC-2 / FEED-2 | **À confirmer dans Notion Statut et Update** : le texte transmis ne définit pas « update » ; le dépôt la définit ainsi. |
| **N2** | **Point de départ du chronomètre** : l'ordre d'évaluation dit « ancienneté de la dernière update », mais « vert = a quitté depuis moins d'1 h ». Si la dernière update date d'avant le départ, on serait rouge dès le départ. Le repère est-il **le plus récent entre le départ et la dernière update** ? Et **après un départ d'une zone de danger** (sans update) : chronomètre qui repart de ce départ ? | Repère = **max(heure du départ, dernière update)**, y compris après une zone de danger (même règle, simple) | LOC-2 / FEED-2 | **Ambigu dans le texte transmis** de Notion Statut (contradiction interne) ; le dépôt dit `max(presenceAt, lastUpdateAt)`. |
| **N3** | **Update manuelle pendant qu'on est en zone de danger** : l'ordre de Notion donne rouge quoi qu'on publie ; la spec Update dit « une update ramène à vert » et « en rouge, une update toutes les heures ». | Rouge tant qu'on est dans la zone, **mais l'update (texte, photo) s'affiche à côté** ; la règle « une update par heure en rouge » est **abandonnée** (rien ne peut la faire respecter sans notification) | FEED-2 / Update | **Non** : contradiction entre Notion Statut (transmis) et `update-et-escalade-statut.md`, que le ticket Statut ne tranche pas. |
| **N4** | **Personne jamais passée en zone de confiance** (aucun départ connu, `presence = unknown`) : quelle couleur ? | **Neutre**, « Pas encore de nouvelles de {Prénom} », aucune escalade (pas de départ à mesurer) | LOC-2 / FEED-2 | **Non** couvert par ce que j'ai lu ; le modèle actuel est neutre (hypothèse Q10 de `data-model.md`). |
| **N5** | **Libellés « rouge » et « zone à risque »** non anxiogènes (`CLAUDE.md`) : le Feed (transmis) dit « Dans une zone à risque ». | Rouge pour la couleur, **texte factuel** : « {Prénom} est à {nom du lieu} » ; jamais « zone à risque » devant les proches ; « Plus de nouvelles depuis 17 h 02 » pour le rouge d'ancienneté ; le **type** garde son nom « Zone de danger / à risque » dans le formulaire du créateur (Notion) | FEED-2 | **Conflit** entre le libellé Notion Feed (transmis) et `CLAUDE.md` ; non tranché par un ticket. |
| **N6** | **Couleur globale de la Miff** [Notion Feed, transmis : rouge si au moins un rouge, vert si tous verts, sinon orange] : les membres **neutres** (partage arrêté, pas de statut, inconnu) comptent-ils ? | Ils sont **ignorés** du calcul ; « Tout va bien » seulement si au moins un statut connu et tous les connus verts (déjà décidé QM12) ; pas d'orange à cause d'un neutre | FEED-2 | **Partiellement** (QM12 pour la phrase « Tout va bien », pas pour la couleur). |
| **N7** | **Passage Premium vers gratuit** [Notion Premium, transmis : « suppression des derniers lieux de confiance, dernières Miffs rejointes, etc. »] : quels lieux (**toutes** les zones de danger ? les **2 premiers** lieux de confiance gardés ?), dans quel ordre, **quand** (à l'échéance ou après un préavis), et **qui exécute** sans Cloud Function (le téléphone du créateur à son prochain lancement, donc avec retard) ? | Zones de danger **toutes** supprimées ; on garde les **2 premiers lieux de confiance créés** ; préavis ; exécution au prochain lancement du créateur, dit honnêtement | Ticket Premium | **Partiellement** : Notion Premium (transmis) et décision du 2026-10-24 disent « les derniers » ; aucun détail d'ordre, de délai ni d'exécutant. |
| **N8** | **« 20 lieux les plus proches »** : distance au **centre** ou au **bord** ? Garder **toujours** les zones où l'on est ? | Au bord (centre moins rayon) ; garder les zones actuelles | LOC-2 | **Non** : Notion Tracking (transmis) dit « les plus proches » sans plus. |
| **N9** | **Téléphone éteint ou sans réseau avant que le départ ne soit écrit** : on reste « vert, à la maison » sans limite. Acceptes-tu cette limite en v1 ? (Corrigeable seulement par une écriture périodique ou un serveur.) | Accepter ; afficher « Dernière nouvelle à 08 h 12 » et la batterie ; ne rien inventer | LOC-2 / FEED-2 | **Non** : aucun ticket lu ne traite l'absence de signal sans départ écrit. |

---

## 17. Critères d'acceptation

Codes : **PL** lieu, **LIM** limites, **MIF** multi-Miffs, **ADR** adresse et carte, **AUT** autorisations (Tracking), **POS** position et sélection, **DET** détection, **STA** statut, **VIE** vie privée, **HOR** hors connexion, **TRA** transverses, **AND**, **IOS**, **PM**, **QA**.

### Formulaire et lieux
- [ ] **PL1** Seul le créateur de la Miff voit « Ajouter un lieu », « Modifier », « Supprimer ce lieu » ; un membre lit la liste et le détail.
- [ ] **PL2** Le **seul** bouton « Enregistrer » est **non cliquable par défaut** (pas de bouton « Valider », pas de coche) ; il devient cliquable dès 1 caractère de nom **et** une **suggestion d'adresse choisie** ; nom de moins de 2 caractères au clic : « 2 caractères minimum », rien n'est écrit.
- [ ] **PL2b** Sous 100 m de rayon, la note discrète du §11 s'affiche ; elle disparaît à 100 m et plus.
- [ ] **PL3** Nom de moins de 2 caractères : « 2 caractères minimum » ; au-delà de 50 : « Limite de 50 caractères atteinte » ; jamais plus de 50 enregistrés.
- [ ] **PL4** Périmètre : curseur de **50 à 800 m, pas de 50 m, défaut 100 m**.
- [ ] **PL5** Type : « Zone de confiance » sélectionné par défaut ; catégorie **obligatoire** parmi **Maison (présélectionnée), Travail, École, Sport, Chez un proche, Loisir, Autre** ; aucun lieu n'est enregistré sans catégorie.
- [ ] **PL6** Un lieu enregistré porte : identifiant unique, nom, latitude, longitude, adresse, rayon, Miff(s), créateur, type, catégorie ; **toutes les clés** sont écrites ; `lat` et `lng` à 5 décimales.
- [ ] **PL7** Modifier permet de changer **tous les champs, type compris** ; un changement de position ou de rayon est pris en compte par les téléphones à leur prochaine relecture.
- [ ] **PL8** « Supprimer ce lieu » ouvre la confirmation (maquette `04`, tutoiement) ; la suppression est appelable **sans interface** (Premium, suppression de Miff).
- [ ] **PL9** Aucune écriture n'est affichée comme réussie avant l'accusé du serveur ; hors connexion : message, rien en file.
- [ ] **PL10** La liste montre **nom + catégorie** ; le détail montre l'adresse, la carte et le rayon, avec « Voir sur la carte » (application de cartes du système).

### Limites
- [ ] **LIM1** Compte gratuit : **2 lieux** ; au 3e : message de limite, **aucune écriture envoyée**, écran provisoire « Miff+ arrive bientôt ».
- [ ] **LIM2** Premium (compte de test avec `entitlements`) : plus de 2 lieux, tous types.
- [ ] **LIM3** Compte gratuit : choisir ou passer à « zone de danger / à risque » est refusé côté client avec le message §11 et l'écran provisoire (zone de danger = Premium, décision du 2026-10-16 et ticket Notion Premium) ; seul le créateur de la Miff peut en créer.
- [ ] **LIM4** Un membre gratuit d'une Miff dont le créateur est premium reçoit et surveille les lieux (héritage).
- [ ] **LIM5** `paywall_shown` / `paywall_dismissed` (`extra_trusted_place`) : seulement si un écran s'affiche et si le consentement Analytics est donné ; aucun nom ni emplacement.

### Plusieurs Miffs
- [ ] **MIF1** Le sélecteur ne liste **que** les Miffs créées par la personne ; défaut = la dernière Miff rejointe **si** elle est dans la liste, sinon la première Miff créée [Décidé Q4] ; sélection multiple de **5 Miffs au plus** ; les Miffs sont modifiables après création.
- [ ] **MIF2** Un lieu associé à N Miffs est écrit comme N documents de **même identifiant** ; création, modification et suppression sont atomiques par lot ; **au plus 5 Miffs par création** (le sélecteur n'en accepte pas davantage) ; modifier les Miffs associées ajoute ou retire des documents ; supprimer le lieu le supprime de toutes ses Miffs.
- [ ] **MIF3** Sur le téléphone, les N documents forment **une seule zone surveillée** et N statuts (un par Miff partagée).

### Adresse et carte
- [ ] **ADR1** Les suggestions apparaissent **à partir de 3 caractères**, avec temporisation de 300 ms, annulation de la requête précédente et cache de session ; la personne **choisit** une suggestion ; latitude et longitude viennent de ce choix.
- [ ] **ADR2** Le point est placé sur une carte gratuite avec le cercle du rayon, mis à jour par le curseur ; l'attribution est visible.
- [ ] **ADR3** Aucun appel réseau hors de la **liste blanche de domaines** déclarée dans ce ticket et dans la politique ; aucune clé secrète ; fournisseur de tuiles et de suggestions remplaçable par un réglage.
- [ ] **ADR4** Suggestions ou carte indisponibles : message (§11), aucun plantage, nouvelle tentative possible ; le texte saisi n'apparaît dans aucun journal ni événement.

### Autorisations (Tracking)
- [ ] **AUT1** Ordre : écran des **notifications** puis écran de la **localisation**, pendant l'onboarding, après la connexion à un compte existant et quand un changement de choix de localisation est détecté ; **écran sauté si tout est déjà accordé** ; **les notifications ne bloquent jamais**, seule la localisation bloque [Décidé Q11].
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
- [ ] **POS5** [Décidé le 2026-11-01, remplace « téléphone seulement »] La **dernière position connue** (et elle seule, **sans historique**, sous réserve de L1) est écrite dans Firestore **uniquement** pour les Miffs où le partage est actif et le consentement donné (incluant le nouveau consentement : L5) ; **jamais** envoyée ailleurs (test : aucune requête réseau hors Firestore et la liste blanche ne contient de coordonnées de la personne). La copie locale (tri des 20 lieux) est dans le stockage privé de l'app, exclue des sauvegardes, **effacée à la déconnexion, à la suppression de compte et à la perte d'accès**.
- [ ] **POS6** Les champs de présence (`presence`, `placeId`, `presenceAt`…) **ne contiennent aucune coordonnée** ; les coordonnées de la personne n'existent que dans le bloc de position défini par R-LIEU-POS (L1, L2) ; **aucune coordonnée ni adresse** dans le texte d'une update ou un événement Analytics.
- [ ] **POS7** La position est **effacée dans le même lot** que le statut (arrêt du partage, sortie de la Miff, retrait, suppression de la Miff ou du compte, session remplacée) ; durée de validité et affichage daté : L3.
- [ ] **POS8** Une écriture de position respecte le garde-fou de fréquence décidé (L4) ; compte d'écritures mesuré à la recette, comparé au quota Spark.

### Détection et statut
- [ ] **DET1** Une zone surveillée est détectée à l'arrivée et au départ **app fermée** (recette PM).
- [ ] **DET2** Arrivée et départ produisent les valeurs du §6.6 ; arrivée dans une zone de danger **sans** être dans une zone de confiance écrit `in_risk` ; zone de confiance et zone de danger qui se chevauchent : `in_trusted` (la confiance l'emporte).
- [ ] **DET3** Déjà dans le lieu à l'armement : arrivée ; dehors : aucun `out` inventé.
- [ ] **DET4** Seules les Miffs où le partage est actif reçoivent une écriture ; partage arrêté, Miff quittée / retirée / supprimée, déconnexion, suppression de compte, session remplacée : surveillance arrêtée, plus aucune écriture.
- [ ] **STA1** Chaque écriture est complète et conforme à `validStatus` ; une écriture par Miff ; `updateText` et `hasPhoto` conservés ; `batteryPct` du moment.
- [ ] **STA2** Aucune coordonnée, adresse ni distance dans les **champs de présence** d'un statut (la position est un bloc distinct : POS6).
- [ ] **STA6** **Couleur calculée à la lecture, jamais écrite** : fonction pure partagée iOS / Android, tests sur toutes les branches du §6.9 : zone de confiance → vert ; sinon zone de danger → rouge ; sinon moins de 1 h vert, de 1 h à 2 h (bornes incluses) orange, plus de 2 h rouge ; chevauchement ; partage désactivé, statut absent et `unknown` → neutre (N4) ; bornes 59 min 59 s / 1 h 00 / 2 h 00 / 2 h 00 min 01 s.
- [ ] **STA7** Le temps qui passe fait basculer la couleur **sans écriture** : une carte affichée passe de vert à orange puis rouge sans action (recalcul à la minute, au retour au premier plan, à l'ouverture) ; **aucune écriture de statut** n'est faite par le téléphone du proche.
- [ ] **STA8** Téléphone éteint après un départ écrit : orange à +1 h, rouge à +2 h chez le proche ; départ détecté hors connexion : écrit avec son heure réelle (HOR1) ; absence de départ écrit : dernier état conservé, daté (N9).
- [ ] **STA9** Les seuils 1 h / 2 h sont des **constantes uniques** partagées (jamais dupliquées dans le Feed).
- [ ] **STA3** `presenceAt` plafonné à l'heure actuelle ; une écriture de plus de 24 h de retard est abandonnée sans boucle.
- [ ] **STA4** Disjoncteur d'écritures testé unitairement.

### Vie privée
- [ ] **VIE1** Aucune coordonnée de la personne dans les journaux, Analytics, Crashlytics, messages de partage (**seul Firestore, bloc de position défini par R-LIEU-POS, en reçoit**) ; le gestionnaire d'événements de zone ne lit pas la position fournie avec l'événement (analyse statique et test).
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
- [ ] **AND2** Au plus **20** zones enregistrées, comme iOS [Décidé Q9] ; réinscription après redémarrage du téléphone sans ouvrir l'app.
- [ ] **AND3** Test sur l'OPPO Find X5 Lite (recette PM, scénario §13).
- [ ] **AND4** Divulgation visible avant la demande, formulaire et vidéo Play préparés avant toute piste de test fermée ou ouverte.

### iOS
- [ ] **IOS1** `Info.plist` : exactement les deux clés `NSLocation…` (FR / EN) ; mode d'arrière-plan « location » non déclaré sauf nécessité démontrée ; garde-fous du dépôt réécrits **dans la même PR**.
- [ ] **IOS2** `PrivacyInfo.xcprivacy` et étiquettes App Store décrivent la donnée réellement collectée : **position précise (dernière position connue) collectée, liée à l'identité de la personne et visible des membres de sa Miff**, plus l'état de présence (lieu nommé, heure) ; Google Play (Data safety, formulaire « localisation en arrière-plan », vidéo) dit la même chose ; textes d'usage `NSLocation…` réécrits (le « jamais ta position précise » du §11 est faux) ; **jamais prétendus relus par un juriste**.
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
- [ ] **QA3** Journaux de la build de débogage : aucune coordonnée ; événements Analytics : aucune position (test de contrat).
- [ ] **QA4** Les tests de règles du dépôt passent après chaque évolution.

**Définition de « terminé »** : tous les critères de la plateforme cochés ; revues faites ; QA ; recette du PM ; « prêt à merger » par un « oui » explicite ; fusion par Claude.

---

## 18. Maquettes

### 18.1 Fournies et lues
`design/exports/lieux/` (7 images) : formulaire complet, modification, confirmation de suppression, aperçu de carte. `design/exports/onboarding-original/` : **écran 19** (autorisation des notifications), **écrans 20, 21, 22** (autorisation de la localisation : « Toujours autoriser », « Autoriser la localisation en arrière plan », état d'erreur rouge). **Ces quatre écrans correspondent au ticket Tracking** (Android) et deviennent la référence, au tutoiement. `design/exports/profil/` : liste « Lieux de la Miff » (nom, adresse, « Ajouter »).

### 18.2 Manquantes (à dessiner par `design-system`, puis à exporter par le PM)

> **Mise à jour du 2026-11-01** : les points 1 à 9, 11, 12 et la correction du point 8 ont été dessinés dans le dépôt (fiches `design/screens/lieux/`, maquettes HTML et PNG `design/exports/lieux/claude/`), **sans Figma** (le PM le juge inutile). Restent non dessinés : 10 (écran de diagnostic interne) et 13 (cartes du Feed). Décisions de design à valider : `design/screens/lieux/README.md`.
1. **Suggestions d'adresse** sous le champ (liste, chargement, aucune, indisponible) ; saisie de l'adresse choisie.
2. **Sélection de plusieurs Miffs** (la maquette montre une liste déroulante à valeur unique) ; état « une seule Miff créée ».
3. **Écrans iOS d'autorisation** (« Toujours » obligatoire, évolution depuis « Lorsque l'app est active ») et état « autorisation retirée / réaffichage ».
4. **Écran Android 11+** : confirmer que 21 est l'écran intermédiaire vers les réglages et 22 l'état d'erreur de la pop-in.
5. **Limite gratuite atteinte** (3e lieu) et message « zone à risque : Miff+ ».
6. **Détail d'un lieu** en lecture (adresse, carte, rayon, type, catégorie, « Voir sur la carte ») et liste avec la catégorie.
7. **États du formulaire** : erreurs (2 caractères minimum, limite de 50), enregistrement, hors connexion, carte indisponible.
8. **Correction de la maquette** : catégorie **Travail** absente de `03` à `06` ; **coche « Valider » à retirer, seul « Enregistrer » reste** [Décidé] ; catégorie présélectionnée « Maison » ; note sous 100 m ; sélecteur de Miffs multiple (5 au plus) ; vouvoiement.
9. **Appareil remplacé** : message de déconnexion (sous-ticket session).
10. **Écran de diagnostic interne** (builds de test) : libre.
11. **Icônes des catégories** (si ce ne sont pas des emojis).
12. **Consentement et écran d'autorisation de la localisation** à redessiner après L5 (la dernière position exacte est visible de la Miff) ; mention « même app fermée » [Décidé Q13].
13. **Zone de danger** : badge et carte de proche rouge « en zone de danger » au libellé calme (N5) ; carte et bandeau d'ancienneté (orange, rouge) aux seuils 1 h / 2 h (les maquettes du Feed montrent 18 min en orange) : tickets Feed.

**Plus nécessaires** (version 1) : écran « Détection de tes lieux », guide fabricant, avis aux membres, cartes de consentement v2.

---

## 19. Ce que le PM doit fournir ou faire

1. Répondre aux **questions restantes** du §16 : **partie A (Q5, L1, L2, L3, L5, L9) pour démarrer LOC-0 / LOC-1** ; **partie B** avant la plateforme ou le ticket qu'elle bloque. Confronter chacune aux tickets Notion que je n'ai pas pu lire.
2. `backend-data` est **autorisé** à préparer R-LIEU-5, -2, -6 (Q6 ; R-LIEU-1 et R-LIEU-3 déjà acceptées) ; **R-LIEU-POS (position stockée) attend ton accord (L9)** ; **chaque règle se déploie avec ton « oui » explicite le moment venu** (`docs/dev/deploiement-regles.md`).
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
| `backend-data` | R-LIEU-5, -2, -6 (-1), **R-LIEU-POS**, R-SESS ; mesures de lectures et d'écritures ; `data-model.md` (§1, §2.7, §3 à réécrire : position stockée, seuils 1 h / 2 h, confiance prioritaire) |
| Update (à écrire) | Même document `status` ; dépend de R-LIEU-1 ; ordre : lieux, Update, Tracking (QL19) |
| FEED-2 | Affichage des couleurs et libellés (§6.9) : « Localiser », zone de danger rouge (Q10 du Feed **levée**), seuils 1 h / 2 h ; questions N1 à N6 |
| Premium | Paywall du 3e lieu et de la zone de danger, fin d'abonnement (N7), suppression sans écran |
| `design-system` | §18.2 |
| `release-manager` | Politique, CGU, page web, stores, étiquettes : **tout ce qui dit « Miff ne stocke pas ta position »** (§7, §9.4, L6) |
| `aso-marketing` | Limite gratuite à 2, aucune promesse de notification |
| Sous-ticket session | `docs/tickets/session-appareil-unique.md` |

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`, ni les textes légaux. Aucun texte légal ou de store n'est présenté comme relu par un juriste.*
