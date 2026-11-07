# Surveiller l'usage de la base (quotas) et reconnaître le moment de passer en Blaze

**Pour qui** : le PM, non-développeur. Rien à programmer ici : ce sont des relevés à faire dans la console Firebase, des seuils et des décisions à prendre.

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Pourquoi c'est nécessaire** : tant que Miff est sur le plan gratuit (Spark), Firestore offre **50 000 lectures par jour** pour tout le projet. Si ce plafond est atteint, **l'application s'arrête pour tout le monde jusqu'à la remise à zéro quotidienne** : ce n'est pas une facture, c'est une panne. Ça peut arriver parce que le produit marche (beaucoup d'utilisateurs) ou parce que quelqu'un abuse (un script qui interroge la base en boucle). Cette page explique comment le voir venir.

**Ce que ça coûte de surveiller** : 0 € et environ 1 minute par jour pendant la bêta externe.

> Les règles de sécurité ne peuvent pas limiter le nombre d'essais d'un utilisateur (elles n'ont pas de compteur). La protection repose sur trois choses : App Check appliqué (voir `app-check-strict.md`), cette surveillance, et, si besoin, la bascule en Blaze décrite plus bas.

---

## 1. Les 4 chiffres à relever chaque jour de bêta externe

| # | Chiffre | Où le lire dans la console Firebase | Valeur normale (estimation, pas une mesure) |
|---|---|---|---|
| 1 | **Lectures du jour** | Projet **Miff** > Build > **Firestore Database** > onglet **Utilisation** > graphique « Lectures » | Quelques centaines à quelques milliers pendant la bêta |
| 2 | **Utilisateurs actifs du jour** | Projet **Miff** > **Analytics** > tableau de bord > « Utilisateurs actifs » sur 1 jour. Si Analytics n'affiche rien (le suivi n'est actif qu'avec le consentement des utilisateurs), compter à la main les testeurs qui ont ouvert l'app (tu les connais pendant une bêta fermée) | Le nombre de testeurs qui ont ouvert l'app |
| 3 | **Nouveaux comptes du jour** | Projet **Miff** > Build > **Authentication** > onglet **Utilisateurs** : trier par date de création et compter les comptes du jour | Proche du nombre de testeurs invités ce jour-là |
| 4 | **Part des requêtes « vérifiées »** | Projet **Miff** > Sécurité > **App Check** > onglet **API** > Cloud Firestore : le détail des requêtes (vérifiées, client obsolète, origine inconnue, invalides) | Proche de 100 % « vérifiées » une fois App Check en place |

Les graphiques de la console sont des **estimations** (délai possible d'environ 4 minutes, d'après la documentation Google). La « journée » des quotas se termine **vers minuit, heure du Pacifique** (soit vers 9 h du matin à Paris : calcul à partir de la documentation Google, à confirmer en observant le graphique). Le plus fiable est de relever le chiffre de la **veille complète**, dans la matinée.

### Le calcul : lectures par utilisateur actif

> **Ratio = lectures du jour ÷ utilisateurs actifs du jour**

Exemple : 6 000 lectures un jour où 50 testeurs ont ouvert l'app donnent un ratio de 120.

Pourquoi ce ratio : l'estimation de `backend/data-model.md` §12 est d'environ **75 lectures par utilisateur actif et par jour**. Un ratio qui s'écarte beaucoup de 75 signale soit un bug de l'application (une boucle de lecture), soit un abus. Il est indépendant du nombre de testeurs, contrairement au total brut.

Si les deux chiffres sont petits (moins de 10 utilisateurs actifs), le ratio varie beaucoup d'un jour à l'autre : regarde alors surtout le total des lectures et la tendance sur la semaine.

---

## 2. Les seuils et ce qu'il faut faire

| Niveau | Quand | Ce que ça veut dire | Que faire |
|---|---|---|---|
| **Normal** | Ratio ≤ 100, total < 30 000 lectures, nouveaux comptes habituels | Tout va bien | Rien, noter le relevé |
| **À examiner** | Ratio **> 100** un jour | Consommation plus forte que prévu : peut être un bug d'app, un testeur qui laisse l'app tourner, ou le début d'un abus | Voir « À examiner » ci-dessous |
| **SIGNAL de bascule** | Ratio **> 150 deux jours de suite**, ou total **> 30 000 lectures deux jours de suite** sans explication, ou comptes créés **de façon anormale**, ou quota de **50 000 lectures atteint** (même un seul jour) | Un abus ou une charge qui va finir par couper l'app pour tous | Voir « Signal de bascule » ci-dessous |

**Création de comptes anormale** : par exemple plus de **5 fois la moyenne des 7 jours précédents** sans campagne d'invitation de ta part (seuil de départ proposé dans le ticket de durcissement, à recalibrer avec le vrai volume de la bêta). Pour éviter les fausses alertes quand les chiffres sont petits, une règle simple : au moins **20 nouveaux comptes dans la journée** *et* plus de 5 fois la moyenne (cette condition supplémentaire est une proposition de l'agent `backend-data`, à ajuster). Une rafale de créations de comptes est le signe typique d'un script.

### À examiner (ratio > 100 un jour)

1. Regarde si un événement explique le chiffre : test intensif de ta part ou d'un testeur, nouvelle version de l'app, nouveau testeur qui a beaucoup navigué.
2. Regarde les **nouveaux comptes** (chiffre 3) : un pic est suspect.
3. Regarde App Check (chiffre 4) : une forte part de requêtes « origine inconnue » ou « invalides » est suspecte (quelqu'un interroge la base sans passer par l'application).
4. Note le relevé (voir le journal plus bas) et **surveille le lendemain**.
5. Si la cause est probablement un bug de l'application, signale-le à `ios-dev` / `android-dev` avec la date et les chiffres.

### Signal de bascule (ratio > 150 deux jours de suite, comptes anormaux, ou quota atteint)

Le plan gratuit risque de ne plus suffire, ou quelqu'un abuse. Dans cet ordre (du moins cher au plus cher) :

1. **Vérifier qu'App Check est bien « appliqué »** sur Cloud Firestore (et sur Authentication si la console le propose) : voir `app-check-strict.md`. Si ce n'est pas le cas, c'est la première chose à faire.
2. **Réexaminer l'exigence d'e-mail vérifié** (gratuit) : aujourd'hui les règles ne l'exigent pas (décision du PM du 2026-10-22). Si les comptes jetables sont le problème, demander à `backend-data` d'ajouter la condition `email_verified` (environ une ligne dans les règles, testée avant déploiement), puis déployer avec la procédure `deploiement-regles.md`.
3. **Décider du plan Blaze** (payant à l'usage, carte bancaire à saisir par toi). Blaze permet de remplacer la recherche directe d'un code par une fonction serveur « rejoindre » qui compte les essais par compte, et ouvre les notifications push. À savoir avant de décider :
   - Les quotas gratuits restent gratuits ; on ne paie que **au-delà**.
   - Une **alerte de budget prévient mais ne plafonne pas** la dépense : à créer (Google Cloud > Facturation > Budgets et alertes) dès l'activation.
   - L'ordre de grandeur à plusieurs milliers d'utilisateurs actifs est de quelques euros par mois (estimation grossière de `data-model.md` §12, à confirmer avec le calculateur de prix Google).
   - **La décision est la tienne** : elle est inscrite comme question ouverte à trancher **avant la première bêta externe** (ticket de durcissement, Q7). Aucun agent n'active Blaze ni n'ajoute de moyen de paiement.

### Quota atteint (l'app est refusée pour tout le monde)

1. Les requêtes reviennent à la normale après la remise à zéro (vers 9 h à Paris, à confirmer). Prévenir les testeurs par le canal habituel, sans entrer dans les détails techniques.
2. **Ne rien contourner** : pas de second projet, pas de comptes multiples, pas de règle assouplie « pour que ça passe » (un contournement des conditions d'utilisation de Firebase peut faire bannir le projet, ce qui est pire qu'un passage en Blaze).
3. Traiter ensuite comme un **signal de bascule** (liste ci-dessus).

---

## 3. Les autres quotas à garder en tête

| Quota (Spark) | Seuil d'alerte conseillé | Où |
|---|---|---|
| Écritures Firestore : 20 000 par jour | 12 000 par jour | Firestore > Utilisation |
| **Écritures de la détection (LOC-2b)** : statuts et dernières positions, par téléphone et par jour | normal : 6 à 10 statuts + 20 à 60 positions par Miff ; **borné** à 60 statuts + 150 positions par téléphone (disjoncteurs de l'app) ; relever **à part** la part des écritures de `positions` à la recette | Firestore > Utilisation, puis le journal de la recette (`docs/qa/checklists/loc-2b-android-recette-pm.md`, partie C) |
| **Écritures de la détection iOS (LOC-4b)** : mêmes bornes et mêmes valeurs qu'Android (`TrackingLimits`) | normal : 6 à 10 statuts + 20 à 60 positions par Miff ; **borné** à 60 statuts + 150 positions par téléphone ; **en plus**, sur iPhone, une relecture **forcée** des Miffs ET des lieux (sans attendre l'heure) à chaque lieu écrit par le créateur et à chaque changement de partage (`refresh(.sharingChanged)`, `ChangeNotifyingPlaceRepository`) : **10 à 30 lectures par enregistrement** selon le nombre de Miffs et de lieux (rare, mais à compter si le créateur modifie plusieurs lieux de suite) ; **relance par le système** : estimée à **4 à 6 lectures + 2 à 3 lectures de règles par écriture**, **à mesurer** à la recette partie C (relever Firestore > Utilisation avant et après la sortie, puis noter ici le chiffre réel) ; signal de bascule : lectures du jour > 40 000 de façon répétée avec peu de testeurs | Firestore > Utilisation, puis le journal de la recette (`docs/qa/checklists/loc-4b-ios-recette-pm.md`, partie C) |
| **Lectures par réveil de la détection (LOC-2b), estimation à mesurer à la recette** | une relecture (au plus 1 par heure de réveil, plus à chaque ouverture après 1 h, retour du réseau, changement de partage, redémarrage) = **M + N × (5 + P)** lectures, M = Miffs de la personne, N = Miffs où elle partage sous la v3, P = lieux d'une Miff (index : M documents ; par Miff : 1 fiche + 2 lectures de règles, puis P lieux + 2 lectures de règles) ; exemple 1 Miff, 3 lieux : ≈ 9. À cela s'ajoutent les lectures de règles des écritures : **1** par statut écrit, **2** par position écrite. Jusqu'à 12 relectures par jour : ≈ 110 lectures, plus 40 à 120 lectures de règles pour les positions : de l'ordre de **150 à 250 lectures par utilisateur actif et par jour**, soit bien plus que les ~75 estimés avant la détection. **À mesurer à la recette** ; signal : ratio lectures / utilisateurs actifs > 150 deux jours de suite (§2). | Firestore > Utilisation, journal de la recette |
| **Photos d'update (R-PHOTO-LOC, localisation obligatoire, décision du PM du 2026-11-06)** | chaque photo ouverte par un autre membre = **1 lecture du document + 3 lectures de règles = 4 lectures facturées** (3 avant la localisation de la photo), car toute photo est localisée et sa lecture vérifie la Miff, mon partage et **ma** position publiée. **Estimation, pas une mesure** : Miff de 4 membres, une photo ouverte par autre membre à chaque ouverture à froid (~3 par jour) = 36 lectures par jour et par utilisateur actif au lieu de 27 (+9) ; Miff de 8 membres : 84 au lieu de 63. Avec les ~176 à 320 lectures de la détection et de la session, on passe à environ **210 à 355** dans ce cas haut, soit **~140 à 240 utilisateurs actifs par jour** avant les 50 000 lectures de Spark (contre ~155 à 285 avant) ; l'ordre de grandeur est inchangé, la limite basse baisse jusqu'à ~10 à 15 % par rapport à un Feed sans photo (hypothèse pessimiste : chaque autre membre a toujours une photo et chaque ouverture à froid relit tout). **Limite connue** : le coût est proportionnel au nombre de photos OUVERTES, donc au comportement de l'application. **Signal** : celui du §2 (ratio > 150 deux jours de suite) ; si le ratio monte nettement dès que les photos sont en usage, **vérifier d'abord que l'application ne pré-charge pas les photos du Feed** (une lecture par ouverture, cache tant que la date de mise à jour ne change pas : levier gratuit), avant de parler de Blaze. | Firestore > Utilisation, journal de la recette |
| **Rappel** | le **20 000 écritures par jour** de Spark compte **toutes** les écritures du projet (statuts, positions, photos, adhésions, lots de lieux), et se remet à zéro à minuit **heure du Pacifique (≈ 9 h à Paris)**, pas à minuit chez la personne | |
| Suppressions Firestore : 20 000 par jour | rarement en jeu | Firestore > Utilisation |
| Transfert sortant : 10 GiB par mois | 6 GiB par mois | Firestore > Utilisation |
| Stockage : 1 GiB | rarement en jeu avant des milliers d'utilisateurs | Firestore > Utilisation |
| Authentication : 50 000 utilisateurs actifs par mois | très loin | Authentication > Utilisation |
| App Check, Play Integrity : 10 000 appels par jour (palier standard, d'après la documentation Firebase) | à surveiller dès quelques centaines d'utilisateurs : chaque jeton demandé compte | Console Google Cloud > API Play Integrity > Métriques (emplacement exact à vérifier) |

---

## 4. Alertes automatiques (facultatif, à vérifier par le PM)

Une alerte qui t'écrit quand les lectures dépassent un seuil serait idéale, mais **sa disponibilité sur le plan gratuit n'est pas confirmée** (la documentation Google dit que les alertes d'usage se configurent dans Cloud Monitoring, sans préciser si la facturation est requise). À tester par toi :

1. Console Google Cloud (projet `miff-3d283`) > **Monitoring** > **Alertes** > **Créer une règle d'alerte**.
2. Métrique : Cloud Firestore > « Document Reads » (lectures de documents). Seuil : par exemple 30 000 sur 24 heures.
3. Canal de notification : ton e-mail.
4. **Si Google demande d'activer la facturation ou de saisir un moyen de paiement : arrête-toi.** Il n'y a alors pas d'alerte gratuite ; on reste sur le relevé manuel de la section 1. Ne saisis aucun moyen de paiement sans décision explicite.

Dans tous les cas, ton relevé quotidien reste la référence.

---

## 5. Journal de relevé (modèle à recopier)

Une ligne par jour de bêta externe, dans un fichier à toi (ou dans une conversation avec Claude) :

| Date | Lectures | Utilisateurs actifs | Ratio | Nouveaux comptes | Requêtes vérifiées (App Check) | Niveau | Remarque |
|---|---|---|---|---|---|---|---|
| (jj/mm) | | | | | | Normal / À examiner / Signal | |

**Deux jours de suite au niveau « Signal » = décision à prendre**, pas à reporter.

---

## 5 bis. iOS : coût de la batterie rafraîchie à l'ouverture et à la sortie (décision du PM, 2026-11-07)

> Section propre à iOS (`ios/CHANGELOG.md`). Android a la sienne. **Estimations, pas des mesures** : à relever à la recette (`docs/qa/checklists/batterie-ios-recette-pm.md`).

**Ce que fait l'app** : quand la personne ouvre ou quitte l'app, elle réécrit son niveau de batterie dans `miffs/{id}/status/{uid}` de chaque Miff où elle partage (un `update` de `batteryPct` + `updatedAt`, aucune règle Firestore modifiée). Pour que ça reste raisonnable :

| Limite (constante `TrackingLimits`) | Valeur | Effet |
|---|---|---|
| `batteryMinIntervalMillis` | 15 min | jamais deux rafraîchissements (ou tentatives) d'une même Miff à moins de 15 min |
| `batteryMinDeltaPoints` | 5 points | rien si la batterie a bougé de moins de 5 points depuis la dernière valeur écrite... |
| `batteryMaxAgeMillis` | 3 h | ... sauf si cette dernière écriture a plus de 3 h (le Feed masque la batterie après 6 h) |
| `maxBatteryWritesPerDay` | 40 par jour et par téléphone | compteur **dédié** (pas les 60 statuts : un rafraîchissement ne prend jamais la place d'une arrivée ou d'un départ) ; pire cas 60 + 40 = 100 statuts par téléphone et par jour |

**Coût d'UN rafraîchissement dans UNE Miff** :

| Poste | Quantité | Remarque |
|---|---|---|
| Écriture Firestore | 1 | compte dans les 20 000 écritures par jour de Spark |
| Lectures de règles | 2 | `validStatus` : la session de l'appareil (1) + mon partage actif (1) |
| Lecture de session côté app | 1 par passage qui écrit (pas par Miff) | `DeviceSession.clearance`, déjà faite avant toute première écriture d'un passage |
| Lectures de snapshot chez les proches | jusqu'à N - 1 | une par proche dont le Feed écoute en ce moment (N = membres de la Miff) ; 0 si personne n'a l'app ouverte |

Soit **≈ 3 lectures + 1 écriture pour une Miff, plus N - 1 lectures de snapshot** au plus. Un passage sans rafraîchissement (moins de 15 min, moins de 5 points, niveau inconnu, hors ligne) **ne lit ni n'écrit rien**.

**Hypothèses (recalibrées après la revue indépendante)** : par utilisateur actif et par jour, **6 à 12 écritures de batterie par Miff** en usage normal (valeur centrale 9 : ouvertures et sorties espacées de 15 min, avec un niveau qui bouge de 5 points ou plus, plus le rafraîchissement de 3 h), 1 Miff de 4 membres (N - 1 = 3 proches à l'écoute : pire cas raisonnable, soit 3 + 3 = 6 lectures par écriture, 54 par utilisateur et par jour). Référence du reste de l'app, version prudente : 10 statuts + 50 à 60 positions = 60 à 70 écritures par utilisateur actif et par jour (haut des fourchettes du §3) et 210 à 355 lectures (§3).

| Utilisateurs actifs par jour | Écritures de batterie / jour (centrale ; plage 6 à 12) | Lectures de batterie / jour (centrale ; plage) | Part de Spark pour la batterie seule (écritures 20 000 / lectures 50 000) | Pire cas : 40 documents par jour et par téléphone |
|---|---|---|---|---|
| 100 | ≈ 900 (600 à 1 200) | ≈ 5 400 (3 600 à 7 200) | 4,5 % / 11 % | 4 000 écritures (20 %), 24 000 lectures (48 %) |
| 1 000 | ≈ 9 000 (6 000 à 12 000) | ≈ 54 000 (36 000 à 72 000) | 45 % / 108 % : les lectures dépassent déjà Spark | 40 000 écritures, 240 000 lectures |
| 10 000 | ≈ 90 000 (60 000 à 120 000) | ≈ 540 000 (360 000 à 720 000) | 4,5 fois le quota d'écritures, 11 fois celui de lectures | 400 000 écritures, 2,4 millions de lectures |

**Ce que ça change pour la limite de Spark** (estimation, à mesurer) : avec 60 à 70 écritures par utilisateur actif pour le reste de l'app, le plafond de 20 000 écritures par jour tient jusqu'à ~285 à 335 utilisateurs actifs par jour ; avec la batterie (+ 9 à 12), il est atteint vers **250 à 300 utilisateurs actifs par jour** (≈ 290 avec 10 statuts, 50 positions et 9 batteries ; ≈ 245 avec 60 positions et 12 batteries). Avec les fourchettes basses du §3 (6 statuts, 20 positions), on irait jusqu'à ~600, mais **le calcul prudent est celui à retenir**. Côté lectures, + 54 par utilisateur (+ 15 à 26 % des 210 à 355 du §3) : le plafond de 50 000 lectures passe de **~140 à 240** à **~120 à 190** utilisateurs actifs par jour. Autrement dit : **la batterie rapproche la bascule de 13 à 20 % environ ; elle ne la crée pas** (les positions pèsent plus), mais elle n'est pas négligeable.

**Cas Miff+ avec N Miffs** (M6) : le compteur de 40 compte les **documents écrits** (un par Miff) par téléphone, pas les ouvertures. Une personne Miff+ dans N Miffs écrit **N documents par rafraîchissement** : le coût d'usage normal est environ **9 × N** écritures par jour, et le plafond de 40 est atteint en 40 / N passages complets (4 passages avec 10 Miffs). Passé le plafond, les Miffs sont servies à tour de rôle le lendemain (curseur de rotation) et certaines gardent un niveau plus ancien : c'est le comportement voulu, pas un bogue. Les lectures de règles suivent (2 par document) ; la lecture de session reste 1 par passage.

**Horloge du téléphone** (M2) : le jour du disjoncteur et les durées de 15 min et de 3 h viennent de l'horloge du téléphone. Une horloge **reculée** rend la comparaison impossible : la règle laisse alors écrire (comme pour la position), et seuls le plafond de 40 et les règles de sécurité limitent. Une horloge en **avance** de plus de 5 minutes fait refuser l'écriture par les règles (`updatedAt` est l'heure du serveur, mais d'autres champs peuvent être jugés) ; le refus n'est pas rejoué avant 15 min. **Limite connue** : le disjoncteur est un garde-fou de l'app, pas une barrière de sécurité (un client modifié peut l'ignorer) ; la protection contre l'abus reste App Check et la surveillance de ce document.

**Ordre de grandeur sous Blaze** (tarifs publics Google à vérifier avec leur calculateur, non confirmés ici ; les quotas gratuits quotidiens restent offerts) : à 10 000 utilisateurs actifs (valeur centrale), la batterie ajoute ~2,7 millions d'écritures et ~16 millions de lectures par mois, soit **de l'ordre de 7 à 9 EUR par mois** (plage haute : jusqu'à ~12 EUR). Supportable, mais plus du double de la première estimation.

**Signal de bascule** (aucun agent n'active Blaze ; la décision est celle du PM) :
- le signal général du §2 (ratio lectures / utilisateurs actifs > 150 deux jours de suite) ou plus de **12 000 écritures par jour** dans la console ;
- spécifique à la batterie : un testeur dont le journal montre **plus de 15 `batteryRefreshed` par jour et par Miff** un jour normal (les limites ne jouent pas comme prévu), ou le disjoncteur de 40 atteint (`breakerStatus` dans le journal juste après un `batteryRefreshSkipped`).

**Leviers gratuits avant Blaze** (dans l'ordre, par un petit changement de constante) : passer le seuil de 5 à 10 points ; passer l'intervalle de 15 à 30 min ; ne rafraîchir qu'à la sortie ; baisser le plafond de 40 à 20. Aucun ne demande de réécriture.

---

## 6. Ce que les agents font de leur côté

- `backend-data` garde ces seuils et la procédure à jour (`backend/data-model.md` §12) et prépare, si le signal se confirme, la condition d'e-mail vérifié (gratuite) ou le plan de fonction « rejoindre » (Blaze).
- `ios-dev` / `android-dev` limitent les essais de saisie de code côté application (pause après 5 échecs) : une aide pour l'utilisateur, **pas** une protection de sécurité.
- Aucun agent n'active Blaze, ne crée d'alerte, ne saisit de moyen de paiement.
- **À qui remonter un signal** : Tomessa MFOUKA (tomessa.mfouka@gmail.com), qui seule décide de la bascule en Blaze.
