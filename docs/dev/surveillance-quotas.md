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
| **Batterie rafraîchie à l'ouverture et à la sortie de l'app (Android, décision du PM du 2026-11-07, ticket Feed D46)** | **1 écriture + 3 lectures de règles par Miff et par rafraîchissement**, plus **1 lecture de snapshot pour chaque autre membre qui a le Feed ouvert à ce moment** (jusqu'à N−1). Bornes de l'application : au plus une tentative toutes les 15 min, pas d'écriture si le niveau a bougé de moins de 5 points (sauf dernière écriture de plus de 3 h), **compteur du jour dédié plafonné à 40 écritures par téléphone** (en plus des 60 statuts et 150 positions, qu'il ne consomme pas). Estimation par utilisateur actif : **~6 écritures et ~36 lectures par jour** (Miff de 4 membres, borne haute des écouteurs), 10 écritures et 60 lectures les jours chargés ; détail, scénarios 100 / 1 000 / 10 000 et signal de bascule dans la section « Batterie rafraîchie » ci-dessous. | Firestore > Utilisation ; journal de la recette (`docs/qa/checklists/batterie-android-recette-pm.md`) |
| **Rappel** | le **20 000 écritures par jour** de Spark compte **toutes** les écritures du projet (statuts, positions, photos, adhésions, lots de lieux), et se remet à zéro à minuit **heure du Pacifique (≈ 9 h à Paris)**, pas à minuit chez la personne | |
| Suppressions Firestore : 20 000 par jour | rarement en jeu | Firestore > Utilisation |
| Transfert sortant : 10 GiB par mois | 6 GiB par mois | Firestore > Utilisation |
| Stockage : 1 GiB | rarement en jeu avant des milliers d'utilisateurs | Firestore > Utilisation |
| Authentication : 50 000 utilisateurs actifs par mois | très loin | Authentication > Utilisation |
| App Check, Play Integrity : 10 000 appels par jour (palier standard, d'après la documentation Firebase) | à surveiller dès quelques centaines d'utilisateurs : chaque jeton demandé compte | Console Google Cloud > API Play Integrity > Métriques (emplacement exact à vérifier) |

### Batterie rafraîchie à l'ouverture et à la sortie de l'app (Android, 2026-11-07)

**Ce que fait l'application.** À chaque passage au premier plan et à chaque passage à l'arrière-plan, si la personne est éligible (connectée, appareil actif, consentement de localisation v3, partage activé, statut existant), elle réécrit **le niveau de batterie seul** (et l'heure d'écriture `updatedAt`) dans le statut de chaque Miff où elle partage : un lot d'une seule écriture par Miff, aucune position, aucune lecture du serveur par le rafraîchissement lui-même. Les écritures de statut existantes (arrivée, départ, update, accès à la localisation) continuent d'écrire le niveau frais et **comptent comme un rafraîchissement** (pas de doublon).

**Les garde-fous de coût** (constantes nommées dans `TrackingLimits.kt`, un seul endroit à changer) :
- **15 min** au moins entre deux tentatives (`BATTERY_MIN_INTERVAL_MILLIS`), quel que soit le résultat (un refus n'est pas répété à chaque ouverture) ;
- **pas d'écriture si le niveau a bougé de moins de 5 points** depuis la dernière valeur écrite (`BATTERY_MIN_DELTA_PCT`), **sauf si la dernière écriture a plus de 3 h** (`BATTERY_FORCE_AFTER_MILLIS` : le Feed masque la batterie au-delà de 6 h) ;
- **disjoncteur du jour dédié : 40 écritures de batterie par téléphone et par jour** (`MAX_BATTERY_WRITES_PER_DAY`, compté par document écrit, c'est-à-dire par Miff). Choix : un compteur **à part** et non les 60 écritures de statut existantes, pour qu'un téléphone souvent ouvert ne puisse jamais priver ses proches d'une arrivée ou d'un départ (le cœur du produit) ; le plafond absolu d'un téléphone passe de 60 statuts + 150 positions à **60 + 150 + 40** écritures par jour ;
- une écriture attend **4 s** au plus et un passage **8 s** au plus ; un passage qui ne peut pas démarrer tout de suite (le moteur est occupé) est abandonné.

**Coût d'un rafraîchissement, par Miff** (estimations, pas des mesures) :
| Poste | Quantité |
|---|---|
| Écriture du statut (le niveau + `updatedAt` + `deviceId`) | **1 écriture** |
| Lectures de règles (session de l'appareil + fiche de membre, comptage SESS-1 de `backend/data-model.md`) | **3 lectures** (budget : très loin des 20 par lot) |
| Écouteurs des proches : chaque écriture de statut réveille l'écouteur de chaque autre membre qui a le Feed ouvert | **jusqu'à N−1 lectures** (N = membres de la Miff ; un proche dont l'app est fermée ne coûte rien de plus : il relit tout à sa prochaine ouverture, déjà comptée) |
| Lectures du serveur par le rafraîchissement lui-même | 0 |

Soit, pour une Miff de 4 membres : **6 lectures facturées par écriture** (borne haute). Estimation par utilisateur actif et par jour : **~6 rafraîchissements** (5 ouvertures et sorties espacées de plus de 15 min, la plupart avec un changement d'au moins 5 points ou une dernière écriture de plus de 3 h) = **6 écritures + ~36 lectures** ; jour chargé : 10 écritures + 60 lectures ; plafond absolu : 40 écritures par téléphone.

**Scénarios** (uniquement ce chantier, Miff de 4 membres, borne haute des lectures ; part du quota gratuit Spark du jour, 20 000 écritures et 50 000 lectures pour tout le projet) :
| Utilisateurs actifs par jour | Écritures (6 / 10 par jour) | Part de 20 000 | Lectures (36 / 60 par jour) | Part de 50 000 |
|---|---|---|---|---|
| **100** | 600 / 1 000 | 3 % / 5 % | 3 600 / 6 000 | 7 % / 12 % |
| **1 000** | 6 000 / 10 000 | 30 % / 50 % | 36 000 / 60 000 | 72 % / 120 % |
| **10 000** | 60 000 / 100 000 | 300 % / 500 % | 360 000 / 600 000 | 720 % / 1 200 % |

**Ce que ça change à la limite de Spark** (l'application dépasse de toute façon les quotas gratuits bien avant 1 000 utilisateurs actifs, voir plus haut) :
- **Lectures** : de ~176–320 à ~212–356 par utilisateur actif et par jour (hors photos), donc le plafond de 50 000 lectures passe de **~155–285 à ~140–235 utilisateurs actifs par jour** (−10 à −18 %), et à ~130–210 les jours chargés. L'ordre de grandeur ne change pas.
- **Écritures** : l'estimation de départ (6 à 10 statuts + 20 à 60 positions par jour) donne 285 à 770 utilisateurs avant 20 000 écritures ; avec la batterie (+6) : **~265 à 625**. Les **écritures** sont donc, avec les lectures, un plafond du même ordre : à surveiller à part.
- **Sur Blaze** (non activé, décision du PM) : à 10 000 utilisateurs actifs, ce chantier représente ~2 à 3 millions d'écritures et ~11 à 18 millions de lectures par mois, soit de l'ordre de **10 à 20 € par mois** (estimation grossière à partir de tarifs publics non revérifiés pour `europe-west9` : à confirmer dans le calculateur Google). Pas de coût caché hors Firestore.

**Signal pour agir** (les seuils d'alerte des sections 2 et 3 ne sont **pas modifiés** par ce chantier) :
1. Les relevés habituels (écritures du jour > **12 000**, ratio lectures ÷ utilisateurs actifs > **150** deux jours de suite) restent les déclencheurs. Si l'un d'eux se déclenche après la mise en service, **regarder d'abord la part de la batterie** : dans la version de développement, le journal de détection compte les lignes `BATTERY_WRITTEN` (recette, partie C) ; plus de 10 par téléphone et par jour sur une journée normale est anormal.
2. **Leviers gratuits, un seul chiffre à changer chacun** : passer l'intervalle de 15 min à 30 min ou 60 min ; passer les 5 points à 10 ; ne rafraîchir qu'à l'ouverture et plus à la sortie (la moitié des écritures) ; baisser le plafond de 40. Tous réduisent le coût sans toucher à la sécurité.
3. **Bascule en Blaze** : la même que pour le reste (écritures ou lectures du jour au seuil des sections 2 et 3 de façon répétée), décision du PM ; le plafond de 40 écritures par téléphone borne une application **non modifiée** qui boucle ; il ne protège pas d'un client modifié (les règles ne peuvent pas compter les écritures : c'est le rôle d'App Check et de la surveillance de la section 1).

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

## 6. Ce que les agents font de leur côté

- `backend-data` garde ces seuils et la procédure à jour (`backend/data-model.md` §12) et prépare, si le signal se confirme, la condition d'e-mail vérifié (gratuite) ou le plan de fonction « rejoindre » (Blaze).
- `ios-dev` / `android-dev` limitent les essais de saisie de code côté application (pause après 5 échecs) : une aide pour l'utilisateur, **pas** une protection de sécurité.
- Aucun agent n'active Blaze, ne crée d'alerte, ne saisit de moyen de paiement.
- **À qui remonter un signal** : Tomessa MFOUKA (tomessa.mfouka@gmail.com), qui seule décide de la bascule en Blaze.
