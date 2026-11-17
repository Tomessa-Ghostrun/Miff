# Ticket : Profil, répartition du temps passé dans les lieux de la Miff (graphique en anneau)

**Statut** : brouillon de cadrage, **à valider par le PM avant tout développement** (aucun développement lancé, aucun statut Notion modifié). Les valeurs marquées « Défaut » sont des **décisions prises par défaut, à contester** (§4). Les seules vraies questions de produit sont en §18.
**Rédigé le** : 2026-11-17 (agent `product-specs`), à la demande du PM (« je te laisse faire », sans spec fournie), d'après la capture `19.webp` (onglet Profil Android).
**Révisé le** : 2026-11-17, **après lecture du code et des tickets à jour d'`origin/main`** (copie en lecture seule `C:\dev\miff-main-ro`). La première version avait été écrite sur un `main` local périmé : toutes ses hypothèses « à vérifier » sont remplacées par les faits constatés (§3), et deux de ses constats étaient faux (libellé « Zone à risque », dépendance au Tracking).
**Décisions finales du PM intégrées** : (1) le wording de l'état « pas assez de données » est **figé** (§7) ; (2) la **zone de danger** est le **type de lieu** choisi à la création du lieu ; ses lieux sont représentés dans le graphique en couleur d'alerte douce (§10).
**Complète** : `docs/tickets/lieux-de-confiance.md`, `docs/tickets/feed-profil-reglages.md` (Profil), `docs/tickets/mode-voyage.md`, `docs/contracts/profile-places-members.md`, `docs/contracts/place-categories.md`. **Ne remplace rien.**
**Sources lues** : `CLAUDE.md` ; la capture ; le code Android (`data/tracking/*`, `domain/tracking/*`, `domain/place/*`, `ui/profile/ProfileTabScreen.kt`, `ui/components/RecentsPrivacy.kt`, `data/session/AccountLocalData.kt`, ressources `values-fr`) et iOS (`Services/Tracking/*`, `Models/Tracking/*`) ; les tickets `lieux-de-confiance`, `mode-voyage`, `feed-profil-reglages` ; `backend/data-model.md` et `firestore.rules` ; `docs/contracts/*` ; `docs/legal/politique-confidentialite.fr.md` ; la mémoire du projet.
**Limites de cette lecture** : pas de shell (aucun test lancé, aucune mesure) ; Notion non relu (un ticket Notion « Profil » ou « Statistiques » prime s'il existe) ; le comportement réel sur appareil (retard des événements, OPPO) n'est pas mesuré.
**Convention** : [Défaut] = décision par défaut. [Décidé] = décision du PM déjà écrite (source citée). [À vérifier] = fait de plateforme non confirmé.

---

## 1. Résumé pour le PM

**En une phrase** : sous le carrousel de Miffs du Profil, une carte « Répartition de ton temps » montre, en anneau, comment **tu** as réparti ton temps entre les lieux de la Miff sélectionnée, ses éventuelles zones de danger et « Ailleurs », sur 7, 14 ou 30 jours ; **tout est calculé sur le téléphone, rien n'est envoyé, aucune écriture Firestore**.

**Ce que le code fait déjà (bonne nouvelle)** : le suivi des lieux existe sur Android et iOS. Il détecte les entrées et sorties, en déduit une **présence par Miff** (dans un lieu de confiance, dans une zone de danger, dehors, inconnue), et l'envoie à l'état courant du serveur. **Mais il n'en garde aucun historique** : c'est exactement ce qu'il faut ajouter, **sur le téléphone seulement**. On enregistre chaque **changement de présence** que le suivi calcule déjà (même instant que celui envoyé aux proches), si bien que **le graphique et le Feed ne peuvent jamais se contredire**.

**Données (décision centrale)** : **option A, journal local** (gratuit, privé, 0 écriture serveur, pas de nouveau consentement). Limite connue : historique propre à l'appareil, **commence à la livraison**, perdu au changement de téléphone ; **jamais de donnée inventée** (« pas encore assez de données » tant qu'il en manque). Signal de bascule vers un journal Firestore : §4 D1.

**Autres décisions par défaut** (§4) : seulement **mon** temps, jamais celui des proches ; catégories = lieux de confiance (5 nommés max, puis « {n} autres lieux »), **une ligne agrégée « Zone de danger »**, « Ailleurs » ; la zone de confiance **l'emporte** sur la zone de danger en cas de chevauchement (**comme le Feed**) ; phrase de synthèse neutre, sans coche verte ni emoji de fête ; gratuit pour tous ; aucun événement Analytics ; palette dérivée du design system.

**Écarts de libellé constatés (à connaître)** : le type de lieu s'appelle **« Zone de danger »** dans l'app (formulaire de lieu, Feed, notifications ; valeur technique `risk`), pas « Zone à risque » comme sur la maquette (Figma et capture). **Le graphique reprend « Zone de danger »**. Le sous-texte du type, « Lieu à éviter », est un jugement : hors périmètre, signalé (§10.1).

**Coût : 0 €.** **Taille** : M par plateforme et par étape (§14). **À faire avant de publier sur les stores** : mettre à jour la politique de confidentialité (qui promet « pas d'historique », §8).

---

## 2. Périmètre et frontières

### Dans le périmètre
- La **carte** de l'onglet Profil (en-tête, sélecteur 7 / 14 / 30 jours, anneau, légende, phrase de synthèse, confidentialité, tous les états : §5, §7).
- Le **journal local** des changements de présence, son **calcul** (§6) et son **branchement** sur le suivi existant (§13).
- La **représentation des zones de danger** (§10), sans en créer ni en gérer.
- L'**effacement** du journal (déconnexion, suppression de compte, perte d'accès, bouton dans Réglages > Confidentialité).
- Textes FR + EN, accessibilité, maquettes manquantes (§15).

### Hors périmètre
| Sujet | Propriétaire | Frontière |
|---|---|---|
| Détection des entrées / sorties, permissions, 20 zones | Suivi existant (LOC-2b, `TrackingEngine`) | On **écoute** ses changements de présence ; on ne demande **jamais** la position, on n'ajoute aucun réveil, aucun suivi. |
| Création, modification, catégorie, type des lieux | Ticket Lieux (livré) | La carte **lit** les lieux déjà lus par la section « Lieux de confiance de … ». |
| Carrousel de Miffs | Profil (livré) | La carte suit la Miff sélectionnée du carrousel. |
| Temps passé **des proches**, comparaison, partage du graphique | Aucun | Nouveau partage de localisation à consentir : **interdit sans décision explicite** (§18 Q1). |
| Historique serveur, multi-appareils | Aucun (option B) | Signal de bascule §4 D1. |
| Mise à jour des textes légaux et fiches des stores | `release-manager`, PM | **Signalée en §8, non modifiée ici.** |

---

## 3. Constat : ce que le code et le modèle font aujourd'hui

| Question | Fait constaté (source) |
|---|---|
| **Que stocke-t-on ?** | **Un état courant par membre et par Miff**, écrasé : `status/{uid}` (`presence` ∈ `in_trusted`, `in_risk`, `out`, `unknown` ; `placeId`, `presenceAt`) + dernière position (`positions/{uid}`, décision du PM du 2026-11-01). **Aucun historique** (`backend/data-model.md` §1, §2.7 ; politique de confidentialité). |
| **Où le suivi reçoit-il un événement de lieu ?** | Android : `TrackingReceiver.geofenceWork` → `TrackingEngine.onTransition(kind, zoneKey, atMillis)` → `doTransition`. iOS : `SystemLocationManager` (`didEnterRegion` / `didExitRegion` / `didDetermineState`) → `TrackingRuntime` → `TrackingEngine.onTransition`. **Ni l'un ni l'autre ne lit la position fournie avec l'événement** (règle de vie privée existante : on garde cela). |
| **Quel instant ?** | **L'instant d'arrivée de l'événement dans l'app** (`System.currentTimeMillis()` / `Date()`), « le système ne donne pas d'autre heure sans lire la position, ce qui n'est pas fait » (commentaire du receiver). Donc **retard possible** (minutes, parfois plus). Le moteur borne l'heure à « maintenant » (`EventTime.forWrite`) et refuse plus de 24 h. |
| **Présence calculée par Miff** | `PresenceMachine.derive` : **zone de confiance prioritaire sur la zone de danger** (« la zone de confiance l'emporte », décision du PM) ; entre zones du même type, la dernière entrée ; aucune zone = `out`. Un événement ne produit un fait que si la présence **change** (`derived == last` → rien). Le moteur écrit déjà des faits `unknown` quand il ne peut pas confirmer (lieu supprimé, déplacé ou **type modifié**, redémarrage sans position récente, fin de voyage, reprise du partage). |
| **État initial** | Android : `INITIAL_TRIGGER_ENTER` à l'armement (une entrée est signalée si l'on est déjà dedans) ; redémarrage (`REARM`) : présence reconstruite depuis une position récente (< 10 min) ou `unknown`, **jamais un `out` deviné**. iOS : `recheckStates` à l'ouverture (`requestState`). **Rien ne signale « dehors » avant une première sortie** : avant le premier fait, la Miff est « inconnue ». |
| **Quand le suivi tourne-t-il ?** | Seulement si : connecté, **« Toujours » + position précise**, partage actif avec le **consentement de version 3** (`MiffSharingConsent.coversPosition`), compte tenu de la session unique (SESS-2). Sinon : `disarm` / arrêt, **présence oubliée**. |
| **Mode voyage** | Pendant un voyage (`LocalState.travel`), **aucune zone armée, aucune présence écrite** ; l'état serveur est `travel` ; à la fin, le moteur écrit `unknown`. Le graphique doit traiter la durée du voyage comme **non mesurée**. |
| **Limite de zones** | `ZoneSelection.MAX_WATCHED = 20` (toutes Miffs confondues, Android **et** iOS) : au-delà, les 20 plus proches seulement. |
| **Journal existant** | `DetectionJournal` est un **journal de recette réservé aux builds debug / TestFlight** (type d'événement + identifiant de lieu + heure + batterie ; la build de publication reçoit `NoDetectionJournal`, et un test vérifie que le fichier d'écriture n'est pas dans les sources de release). **Il ne peut pas servir** au graphique (absent en release, pas de durée, pas de rétention) : il faut un **journal distinct** (§13). |
| **Purge à la déconnexion / suppression de compte** | `SessionTeardown.stop()` (le moteur y efface `local` et `journal`) + `accountLocalDataErasers` (« toute nouvelle donnée liée à un compte doit être listée dans l'un des deux », `AccountLocalData.kt`). La copie locale du suivi est un fichier privé **exclu des sauvegardes** (`backup_rules.xml`, `data_extraction_rules.xml` : toutes les préférences). |
| **Lieux** | `places/{placeId}` : `name`, `type` (`trusted` / `risk`, **modifiable** depuis R-LIEU-6 ; danger = Miff+ **créateur seulement**), **`category`** (7 valeurs fermées : `home` 🏠, `work` 💼, `school` 🏫, `sport` ⚽, `relative` 👨‍👩‍👧, `leisure` 🎉, `other` 📍 ; table commune `docs/contracts/place-categories.md`, **affichage seulement**, un lieu ancien sans catégorie = épingle neutre), `radiusM`. Un lieu peut être **rattaché à plusieurs Miffs** (même identifiant, une copie par Miff). **Quota gratuit : 3 lieux de confiance** (depuis le 2026-11-10 ; `CLAUDE.md` dit encore « 1 lieu », périmé), illimité en Miff+. Un compte gratuit n'a jamais de zone de danger **mais en hérite** s'il est membre d'une Miff dont le créateur est Miff+. |
| **Libellés des types (écran de création de lieu)** | FR : **« Zone de confiance »** (sous-texte « Lieu sûr pour tes proches ») et **« Zone de danger »** (sous-texte « Lieu à éviter ») ; EN : « Trusted zone » / « Danger zone ». Clés `place_type_trusted`, `place_type_danger`. L'étiquette du Feed dit « Dans une zone de danger » ; la notification N6 dit déjà à la personne « Tu es dans une zone de danger de ta Miff ». La maquette (Figma, capture) dit « Zone à risque » : **écart de libellé**. |
| **Onglet Profil** | Ordre décidé (2026-11-11) : en-tête → carrousel « Mes Miffs » → **lieux de la Miff du carrousel** → carte « Mode voyage » en dernier. Les lieux de la Miff du carrousel sont lus **une fois** (cache 5 min, 0 lecture pour la Miff du Feed) : la carte les **réutilise**. |
| **Aperçu des applications récentes** | Android 13+ : `RecentsPrivacy()` est **déjà** posé sur `ProfileTabScreen` (décision du PM du 2026-10-31) ; Android 12 et moins : limite connue. **iOS : aucun mécanisme équivalent** dans le code. |
| **Analytics** | Liste blanche stricte, **aucun événement** sur les lieux (`docs/contracts/analytics-events.md`). |
| **Politique de confidentialité** | Dit : « Il n'y a pas d'historique de trajets » (§1 point 1), « ne construit pas d'historique de trajets » (art. localisation), « Miff ne garde pas d'historique » (conservation). **À compléter** avec le journal local (§8). |

---

## 4. Décisions par défaut (toutes tranchées, à contester)

| # | Sujet | Défaut | Pourquoi | Alternative / signal pour changer |
|---|---|---|---|---|
| **D1** | **Où vit l'historique** | **A : journal local.** Un enregistrement par **changement de présence** d'une Miff : (Miff, état, lieu, instant). **Aucune coordonnée, aucun nom, aucune adresse.** Rétention **35 jours** glissants, fichier privé de l'app **exclu des sauvegardes**. | 0 lecture, 0 écriture Firestore ; **rien ne quitte le téléphone** : pas de nouveau partage de localisation, pas de nouveau consentement, ni règles ni modèle serveur modifiés. Au plus 60 faits par jour (disjoncteur existant `MAX_STATUS_WRITES_PER_DAY`), soit quelques centaines de Ko au pire sur 35 jours. | **B : journal Firestore**, écarté : une écriture par fait (~10 par jour et par personne : 20 000 écritures gratuites par jour atteintes à ~2 000 personnes actives) et des lectures pour dessiner 30 jours ; TTL payant sur Spark (nettoyage à la main) ; règles à écrire et déployer ; **un historique serveur d'habitudes contredit la promesse « pas d'historique »** et appelle un nouveau consentement, une durée, l'effacement à chaque sortie, un risque « stalkerware ». **Signal pour basculer** : le PM veut un historique qui survit au changement de téléphone, la vue des proches (§18 Q1), plus de 30 jours, ou des retours testeurs en nombre. B se ferait en **compteurs journaliers par lieu**, pas en journal d'événements. |
| **D2** | **Qui** | **Mon temps seulement.** | Le détail du temps passé des proches serait une nouvelle information de localisation partagée. | Extension future : §18 Q1, jamais par défaut. Le titre dit « ton temps ». |
| **D3** | **Catégories** | Un **lieu de confiance = une part** ; **une ligne agrégée « Zone de danger »** (§10) ; **« Ailleurs »**. Pas de « Hors-zone » de la capture. | Décision du PM pour la zone de danger. « Ailleurs » : le « ? » de « Hors-zone » évoque l'inconnu. | — |
| **D4** | **Lisibilité** | **5 lieux de confiance** nommés au plus (les 5 plus longs), les suivants regroupés en « {n} autres lieux », puis « Zone de danger » (si du temps y a été passé), puis « Ailleurs » en dernier. Un lieu à 0 n'est pas listé. | Premium = lieux illimités. « {n} autres lieux » plutôt que « Autres lieux » pour ne pas être confondu avec « Ailleurs ». | — |
| **D5** | **Compte gratuit (≤ 3 lieux)** | Anneau de **2 à 4 parts**, traité comme le cas normal. | Cas le plus fréquent. | — |
| **D6** | **Fenêtre** | **Glissante**, [maintenant − N × 24 h ; maintenant], N = 7, 14, 30. Défaut **7 jours**, **choix mémorisé sur l'appareil, effacé à la déconnexion** (comme la Miff active du Feed). | Pas de remise à zéro le lundi. | — |
| **D7** | **Dénominateur** | Le **temps mesuré** seulement (hors présence « inconnue », voyage, suivi arrêté, lieu supprimé, plafond dépassé). **Jamais compté en « Ailleurs ».** | Règle d'honnêteté : ne jamais inventer. | — |
| **D8** | **Seuil « assez de données »** | **24 h de temps mesuré** dans la fenêtre choisie. | Un anneau sur 3 h serait trompeur. Constante partagée iOS / Android. | À ajuster après recette. |
| **D9** | **Présence ouverte très longue** | Un état n'est compté que **48 h au plus sans nouveau fait** ; au-delà, **non mesuré**. | Si l'app est tuée, le système retarde l'événement ou le téléphone est éteint, une sortie manquée transformerait une journée en « 14 jours au bureau ». 48 h couvre un week-end sans sortie. | Plafond plus haut = plus de précision pour les longs séjours, plus d'erreur après une sortie manquée. |
| **D10** | **Chevauchement** | **Même règle que le Feed** : la **zone de confiance l'emporte** sur la zone de danger ; entre deux zones du même type, la **dernière entrée**. Le graphique **reproduit la présence que le moteur calcule** (`PresenceMachine.derive`), il ne la recalcule pas. | Décision existante du PM (« la zone de confiance l'emporte ») ; cohérence totale Feed / graphique. | — |
| **D11** | **Premium** | **Gratuit pour tous**, 7 / 14 / 30 jours. | Coût nul ; bloquer une carte de confiance derrière un paywall serait mal perçu. | Si levier Miff+ voulu : fenêtre 30 jours (signalé, non retenu). |
| **D12** | **Analytics** | **Aucun nouvel événement** (même pas « fenêtre changée »). Aucune durée, pourcentage ou nom de lieu dans un événement, un journal debug ou un rapport de plantage. | Le contrat interdit les événements sur les lieux. | — |
| **D13** | **Phrase de synthèse** | **Descriptive**, même registre qu'on soit à 90 % ou 10 % dans ses lieux ; **ne nomme jamais la zone de danger** ; pas de coche verte, pas d'emoji de fête, pas d'objectif, pas de comparaison (§5.4). | CLAUDE.md : jamais moralisateur ni culpabilisant. | — |
| **D14** | **Mode sombre** | Aucun (`design-system.md` §6) ; tokens uniquement. | — | — |
| **D15** | **Effacer l'historique** | Ligne « **Effacer mon historique de temps** » dans Réglages > Confidentialité, avec confirmation. | Contrôle de l'utilisateur sur ses données. | Peut être retirée sans conséquence. |
| **D16** | **À l'arrêt du partage d'une Miff** | Le suivi s'arrête (déjà le cas) ; **le journal de cette Miff est conservé jusqu'à 35 jours** (ou jusqu'au départ / retrait / suppression de la Miff, ou effacement manuel). | Données de la personne, sur son téléphone ; le départ de la Miff les efface. | — |
| **D17** | **Aperçu des applications récentes** | **Android : déjà masqué** (`RecentsPrivacy()` sur l'onglet Profil, Android 13+), rien à faire. **iOS : non traité** (aucun mécanisme dans l'app, hors proportion pour ce ticket) : **limite connue** notée en §16. | Faisable simplement seulement là où le mécanisme existe. | Un rideau de confidentialité iOS pour tout le Profil = ticket à part. |
| **D18** | **Lieux au-delà des 20 surveillés** | Note sous l'anneau « Tous tes lieux ne sont pas suivis en même temps : la répartition peut être incomplète. » quand le nombre de zones dépasse 20. | Toutes Miffs confondues, les 20 plus proches seulement. | — |

---

## 5. Comportement observable

### 5.1 Placement
Onglet Profil, **sous le carrousel « Mes Miffs » (et ses pastilles)**, **au-dessus** de « Lieux de confiance de <Miff> » ; la carte « Mode voyage » reste en dernier. La carte **suit la Miff sélectionnée du carrousel** (comme la liste des lieux) ; la fenêtre choisie est conservée.
**Masquée** (pas d'espace vide) seulement quand la Miff sélectionnée n'a **aucun lieu** (la section « Lieux » porte déjà le message). Quand la carte « Créer une nouvelle Miff » du carrousel est affichée, la carte **reste celle de la dernière Miff affichée**, comme la liste des lieux (`profile-places-members.md` §2) : pas de clignotement.

### 5.2 Contenu (état nominal)
1. **En-tête** : pastille « graphique en barres », titre **« Répartition de ton temps »**, sous-titre **« Sur les 7 derniers jours »** (14 / 30), **sélecteur** « 7 jours ⌄ » (menu à 3 choix, le choix actuel coché).
2. **Anneau** : une tranche par catégorie, séparées par un mince espace de la couleur de fond. **Au centre** : « **72 %** » et « dans tes lieux de confiance » = somme des **lieux de confiance seulement** (ni zone de danger ni « Ailleurs », comme la capture).
3. **Légende** (même ordre que l'anneau, sens horaire depuis midi) : pastille de couleur, **emoji de la catégorie du lieu** (table `place-categories.md`, repli épingle si pas de catégorie ; **nom du lieu actuel**), pourcentage à droite. Puis « {n} autres lieux », **« Zone de danger »** (§10), **« Ailleurs »**.
4. **Phrase de synthèse** (§5.4) dans un bandeau doux.
5. **Confidentialité**, toujours visible : cadenas + « **Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.** »

### 5.3 Pourcentages
Entiers, somme **exactement 100 %** (méthode du plus grand reste ; égalité : le plus grand temps réel, puis l'ordre de la légende). Part réelle > 0 et < 1 % : « **< 1 %** » (compte 0 dans la répartition). Format du système (« 72 % » FR avec espace insécable, « 72% » EN). **Pas d'animation** en v1.

### 5.4 Phrase de synthèse (Défaut, tutoiement)
| Situation | FR | EN |
|---|---|---|
| Plus grande part = un lieu de confiance nommé | « Sur les 7 derniers jours, c'est à {lieu} que tu as passé le plus de temps. » | “Over the last 7 days, you spent the most time at {place}.” |
| Plus grande part = « Ailleurs » | « Sur les 7 derniers jours, tu as surtout été ailleurs que dans tes lieux de confiance. » | “Over the last 7 days, you were mostly somewhere other than your trusted places.” |
| Plus grande part = « {n} autres lieux » **ou « Zone de danger »**, ou égalité avec elle | **phrase générique** : « Voici comment ton temps s'est réparti sur les 7 derniers jours. » | “Here is how your time was spread over the last 7 days.” |
Égalité : la première de la légende ; si c'est la zone de danger, phrase générique. **Règle de ton** : la phrase **ne nomme jamais** la zone de danger, ne donne **aucun pourcentage**, ne compare à aucune période ni objectif. « 7 » devient « 14 » / « 30 » (clés distinctes). **Écart avec la capture** : plus de coche verte, de « cette semaine ! » ni d'emoji de fête ; fond neutre bleuté.

---

## 6. Règles de calcul (contrat identique iOS et Android)

Constantes partagées (mêmes valeurs, une définition par plateforme) : fenêtres {7, 14, 30} jours, seuil 24 h, plafond 48 h, rétention 35 jours, 5 lieux nommés.

### 6.1 Ce que le journal enregistre
Un **fait de présence** par changement de la présence **que le moteur calcule déjà pour une Miff** : (Miff, état, lieu, instant), état ∈ {`dans un lieu de confiance`, `dans une zone de danger`, `dehors`, `inconnue`}.
- **Instant = exactement celui que le moteur écrit dans le statut** (`presenceAt`), pour que le Feed et le graphique ne se contredisent jamais.
- **« Inconnue » est écrit aussi** quand le suivi s'arrête pour une cause connue : partage arrêté, permission « Toujours + précise » ou localisation éteinte, déconnexion, début d'un **voyage** (`pauseStatuses`), Miff perdue. Le moteur écrit déjà `unknown` pour : lieu supprimé, déplacé ou **de type modifié**, redémarrage sans position récente, fin de voyage, reprise du partage.
- Aucune coordonnée, aucun nom, aucune adresse, aucun identifiant de personne. Le nom, la catégorie et le type affichés viennent **toujours de la liste actuelle des lieux**.
- **L'écriture doit réussir téléphone verrouillé et app fermée** (le système réveille l'app pour un événement de lieu) : un événement ne doit pas être perdu parce que le stockage est inaccessible.
- **Ce ticket ne demande jamais la position** et n'ajoute aucun réveil.

### 6.2 Calcul pour une Miff M et une fenêtre N jours
1. Fenêtre = [maintenant − N × 24 h ; maintenant] en **durée écoulée** (168 h pour 7 jours même si l'heure change).
2. Chaque fait vaut **de son instant jusqu'au fait suivant** ; le dernier jusqu'à maintenant ; **aucun état n'est compté plus de 48 h sans nouveau fait** (surplus = non mesuré). Les durées sont **rognées à la fenêtre**.
3. Temps « inconnue » = **non mesuré**. **Temps mesuré** = tout le reste.
4. **Lieu de confiance P** = temps des faits `dans un lieu de confiance` de P ; **zone de danger** = somme des faits `dans une zone de danger` ; **Ailleurs** = faits `dehors`.
5. Un fait dont le **lieu n'existe plus** dans la liste actuelle, ou dont le **type actuel du lieu ne correspond plus** à l'état enregistré (type modifié), est **non mesuré** (le moteur ne l'a pas su non plus : jamais une réattribution devinée).
6. Pourcentage = temps / temps mesuré, arrondi selon §5.3 ; le détail de la zone de danger est arrondi **à l'intérieur** de sa ligne (plus grand reste), pour que le détail totalise toujours la ligne. Le pourcentage central ne compte que les lieux de confiance.
7. Temps mesuré < 24 h : « pas assez de données », **aucun pourcentage**.
8. Un fait daté **avant** le précédent (horloge changée) est ramené à l'instant du précédent (durée 0, jamais négative) ; un fait dans le futur est ramené à « maintenant ».

### 6.3 Cas de test (identiques iOS et Android ; fenêtre 7 jours = 168 h ; tout mesuré sauf mention)
| # | Situation | Résultat attendu |
|---|---|---|
| T1 | Maison 84 h, Sport 21 h, dehors 63 h | Maison 50 %, Sport 12 %, Ailleurs 38 % (égalité à 0,5 : le plus grand temps réel, Ailleurs, reçoit le point) ; somme 100 ; centre 62 % |
| T2 | État « Maison » depuis 10 h **avant** la fenêtre, fait suivant 20 h après son début | 20 h comptées |
| T3 | État « Maison » depuis 5 h, aucun fait suivant | 5 h |
| T4 | État « Maison » depuis 60 h, aucun fait suivant | 48 h comptées ; 12 h non mesurées, exclues |
| T5 | Deux faits identiques de suite (même état, même lieu) | traités comme un seul |
| T6 | Fait daté avant le précédent | durée 0, pas de temps négatif |
| T7 | A (confiance) entré à 0 h, B (confiance) entré à 2 h, sortie de B à 3 h, sortie de A à 6 h | A 5 h (0–2 h et 3–6 h), B 1 h ; rien n'est compté deux fois |
| T8 | « Inconnue » de la 100e à la 130e heure | 30 h non mesurées ; dénominateur 138 h |
| T9 | 20 h mesurées seulement | « pas assez de données » |
| T10 | 7 lieux avec du temps | 5 nommés + « 2 autres lieux » + Ailleurs ; somme 100 |
| T11 | Lieu supprimé qui comptait 10 h | ces 10 h sont non mesurées (pas « Ailleurs ») ; son enregistrement est purgé |
| T12 | Fenêtre traversant un changement d'heure | exactement 168 h écoulées |
| T13 | Un lieu à 0,3 % | « < 1 % » ; les autres totalisent 100 |
| T14 | Jamais dans un lieu, 100 h de « dehors » | centre « 0 % » ; légende « Ailleurs 100 % » ; phrase « tu as surtout été ailleurs » |
| T15 | Un seul lieu (compte gratuit), 30 % du temps | 2 parts : lieu 30 %, Ailleurs 70 % |
| T16 | Fait dans le futur | ramené à « maintenant » |
| T17 | Lieu de confiance A entré à 0 h ; zone de danger R entrée à 1 h, sortie à 3 h ; sortie de A à 6 h | **A 6 h, aucune zone de danger** (la confiance l'emporte, comme le Feed) |
| T18 | Zone de danger seule : R entrée à 1 h, sortie à 3 h | zone de danger 2 h |
| T19 | Deux lieux de danger : 5 % et 7 % (Maison 50 %, Ailleurs 38 %) | ligne « Zone de danger » 12 % ; détail 7 % et 5 % ; centre 50 % ; somme 100 |
| T20 | Aucune zone de danger, ou aucun temps dedans | **aucune ligne** (jamais « 0 % ») |
| T21 | Zone de danger = plus grande part | phrase de synthèse **générique** |
| T22 | Lieu dont le type est passé de confiance à danger depuis | faits antérieurs non mesurés (§6.2 point 5) |
| T23 | Voyage de 60 h dans la fenêtre | 60 h non mesurées |

### 6.4 Précision à annoncer (limite connue)
L'événement est daté **à son arrivée dans l'app** : retard possible (économie d'énergie, surcouches constructeur Android, iOS relancé en arrière-plan). Android ne corrige une sortie manquée qu'au prochain événement ou redémarrage ; iOS redemande l'état des zones à l'ouverture. Le graphique est **indicatif** : jamais de durée précise affichée, seulement des pourcentages entiers. Un téléphone éteint ou à plat ne se voit pas : après une sortie, le temps est compté « Ailleurs » jusqu'au plafond de 48 h.

---

## 7. États de la carte

Priorité : **masquée** > **erreur** > **suivi à l'arrêt sans données** > **pas assez de données** > **nominal** (avec, le cas échéant, une ligne de pause).

| État | Quand | Affichage (FR ; EN en parallèle) | Action |
|---|---|---|---|
| **Masquée** | §5.1 (a) | Rien | — |
| **Chargement** | lecture locale | Aucun indicateur avant 300 ms ; au-delà, squelette sans animation | — |
| **Nominal** | ≥ 24 h mesurées | §5.2 | Changer la fenêtre |
| **Nominal + pause** | assez de données mais suivi arrêté **maintenant** | Carte normale + ligne calme sous l'anneau : « Le suivi de tes lieux est en pause. » | — |
| **Pas assez de données (suivi actif)** | < 24 h mesurées, suivi actif | Anneau **vide et neutre** (décor, caché du lecteur d'écran) et le texte **« Ton graphique arrive dans quelques jours, nous analysons ta situation »** / “Your chart will be ready in a few days, we are looking into your situation” (**wording figé par le PM** ; traduction EN fidèle au sens) + confidentialité | — |
| **Mode voyage actif** | un voyage est en cours, sans données | « Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. » | Aucune (le mode voyage se gère dans sa carte) |
| **Partage arrêté** pour cette Miff, sans données | partage désactivé | « Tu as arrêté de partager ton statut avec cette Miff : ta répartition n'est plus mise à jour pour elle. » | Lien vers l'écran de la Miff (qui permet de reprendre) |
| **Localisation insuffisante** | pas « Toujours » + précise, ou localisation éteinte, sans données | « Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. » | Bouton de l'étape de permission **existante** de l'app (même texte d'action que l'Update : « Toujours autoriser » / réglages) ; jamais d'insistance |
| **Lieux non tous suivis** | plus de 20 zones surveillables | Ligne sous l'anneau, D18 | — |
| **Erreur de lecture locale** | stockage illisible | « La répartition n'est pas disponible pour le moment. » + **« Réessayer »** ; ne bloque jamais le reste du Profil. Journal définitivement corrompu : **réinitialisé**, la carte passe à « pas assez de données » | Réessayer |
| **Hors connexion** | — | **Aucun effet** (aucune dépendance réseau) | — |

Textes sans point d'exclamation ; **la couleur d'alerte n'apparaît jamais** dans un état vide, une erreur ou le bandeau (elle est réservée à la tranche et à la ligne « Zone de danger », §10).

---

## 8. Vie privée, consentement, conséquences à signaler

- **L'écran n'envoie rien** : aucune lecture ni écriture Firestore en plus, aucun événement Analytics, aucun contenu (lieu, durée, pourcentage) dans un journal ou un rapport de plantage.
- **« Ces données restent sur ton téléphone. Personne d'autre ne peut les voir. » doit être vrai** : exclusion des sauvegardes et des transferts d'appareil (Android : étendre `backup_rules.xml` et `data_extraction_rules.xml` au nouveau stockage s'il n'est pas une préférence ; iOS : exclusion de la sauvegarde à poser et vérifier) ; aucune synchronisation entre appareils.
- **Consentement** : le journal est un **sous-produit local** du suivi déjà soumis au consentement de partage (version 3) et à la permission de localisation ; **aucun nouveau consentement** *parce que rien ne quitte le téléphone*. **Si le PM choisit un jour B ou la vue des proches, ce raisonnement tombe.**
- **Effacement** : déconnexion, suppression de compte, perte d'accès à une Miff (pour celle-ci), péremption à 35 jours, bouton de Réglages (D15) ; passage par `SessionTeardown` / `accountLocalDataErasers` (§13).
- **À signaler (non modifié ici)** :
  1. **Politique de confidentialité** (`docs/legal/politique-confidentialite.*`) : « Il n'y a pas d'historique de trajets » (§1), « ne construit pas d'historique de trajets » (art. localisation) et « Miff ne garde pas d'historique » (conservation) décrivent **le serveur**. Avec ce ticket, **l'appareil garde 35 jours de changements de présence** (lieu, heure, sans coordonnée). Il faut une phrase sur le **journal local** (contenu, durée, aucune sortie du téléphone, effacement). **TIME-1 ne doit pas partir sur les stores avant cette mise à jour** ; les builds de test (TestFlight, APK interne) peuvent le recevoir. Les textes sont rédigés par l'équipe sous la responsabilité du PM, sans relecture juridique (risque assumé, décision du 2026-10-23).
  2. **Fiches des stores** : étiquettes Apple (une donnée qui ne quitte pas l'appareil n'est en général pas « collectée » [À vérifier]) et section « Sécurité des données » Google ; justification de la localisation en arrière-plan : un **usage de plus** des événements de lieu.
  3. **`backend/data-model.md`** : aucune modification ; une ligne « journal local, hors Firestore » au §10 par `backend-data` pour la cohérence.
  4. **`CLAUDE.md`** dit encore « gratuit = 1 lieu » (périmé : 3 lieux depuis le 2026-11-10, liste d'alignement dans `lieux-de-confiance.md` §9.4).
- **Risque côté appareil** : quelqu'un qui tient le téléphone déverrouillé voit la carte (comme il verrait le Feed). Stockage privé, sans coordonnées ni noms, 35 jours, effaçable.

---

## 9. UI, accessibilité, langues

### 9.1 Fidélité à la capture `19.webp` et écarts
Reprise fidèle : carte blanche arrondie, pastille d'icône à gauche du titre, sélecteur en pastille bordée avec chevron, anneau avec pourcentage central, légende à droite (pastille, emoji, nom, % aligné), bandeau de synthèse en bas. **Écarts proposés (à lister dans chaque PR)** :
| # | Maquette | Écart | Raison |
|---|---|---|---|
| E1 | « Répartition du temps » | « Répartition de **ton** temps » | Personnel (D2) |
| E2 | « 72 % en zone sécurisée » | « dans tes lieux de confiance » | Aucune promesse de sécurité |
| E3 | « Zone à risque » (rouge vif, ⚠️) | **« Zone de danger »** (libellé de l'app), une ligne agrégée, `status/danger` du design system, tranche **hachurée** et icône d'avertissement, détail au toucher | Libellé de l'app ; ton ; jamais la couleur seule |
| E4 | « Hors-zone » + « ? » | « Ailleurs » + icône neutre | Le « ? » évoque l'inconnu |
| E5 | Vert / jaune / rouge / gris génériques | Palette « graphique » dérivée du bleu de la marque ; seule la zone de danger porte la couleur d'alerte | Cohérence design system |
| E6 | Bandeau vert, coche, « ! », emoji de fête | Bandeau neutre bleuté, icône d'information, phrase descriptive | Pas de jugement (D13) |
| E7 | Légende à droite | Légende **sous** l'anneau en très grande police | Accessibilité |
| E8 | — | Ajout de la ligne de confidentialité, des états (§7) | Non designés |
| E9 | Emoji de la légende ⚽🏋️ | Emoji de la **table de catégories** de l'app (⚽ pour le sport) | Même glyphe que la liste des lieux |

### 9.2 Accessibilité
- **Couleur jamais seule** : chaque tranche reliée à une légende (emoji + nom + %) dans le même ordre ; espace entre tranches ; **contraste ≥ 3:1** avec le fond de la carte (le jaune-vert de marque n'y arrive pas ; `status/danger` `#ff5650` est à ~3,0:1 : contour ou léger assombrissement, `design-system` vérifie) ; la tranche de danger a en plus un **motif** et son icône.
- **Lecteur d'écran** : carte = groupe titré ; **anneau caché** (décoratif) ; **une description textuelle unique** (« Répartition de ton temps sur les 7 derniers jours : Maison 48 %, Sport 24 %, Zone de danger 8 %, Ailleurs 20 %. ») ; lignes de légende en éléments de liste, sans doublon ; sélecteur « Période, 7 jours, bouton » ; **le nom de la catégorie est lu, jamais l'emoji** (règle `place-categories.md`).
- Cibles ≥ 48 dp / 44 pt ; texte jusqu'à **200 %** sans troncature ; droite-à-gauche miroité ; pas d'animation ; thème clair seulement.

### 9.3 Langues
FR + EN (repli EN), tutoiement, pluriels corrects (« 1 autre lieu » / « 2 autres lieux » ; « Zone de danger » / « Zones de danger » selon le nombre de lieux de ce type), format de pourcentage du système. Clés `profile_timeshare_*` (préfixe `profile_` existant), parité vérifiée par la CI, aucune chaîne ni valeur de design en dur.

---

## 10. Zone de danger dans le graphique (DÉCIDÉ par le PM)

**Décision.** La zone de danger se définit **à la création d'un lieu par le champ « type »** (« Zone de confiance » / « Zone de danger »). Le donut a **une part par lieu** ; les lieux de type danger sont représentés **en couleur d'alerte douce, jamais la couleur seule**. La part « Zone à risque 12 % » de la maquette correspond à cette catégorie. **Libellé de l'app repris tel quel.**

### 10.1 Écart de libellé et vérification
- Libellé réel : **« Zone de danger »** (`place_type_danger`) ; sous-texte « Lieu à éviter » (`place_type_danger_desc`) ; EN « Danger zone ». La maquette dit « Zone à risque » : **le graphique suit l'app**. Aucun changement de modèle : `type = risk` existe, modifiable depuis R-LIEU-6, **Miff+ créateur seulement** (décision du 2026-10-16).
- Le sous-texte « Lieu à éviter », vu du créateur, est un **jugement** qui s'accorde mal avec le ton voulu ; hors périmètre (ticket Lieux), **signalé**.
- Un **membre gratuit hérite** des zones de danger d'une Miff dont le créateur est Miff+ : sa carte affiche la ligne si du temps y a été passé.

### 10.2 Comportement [Défaut, recommandation du PM]
- **Une seule ligne « Zone de danger »** (« Zones de danger » si plusieurs lieux de ce type) avec le **total** ; **détail au toucher** : feuille du bas (Android) / feuille (iOS) listant chaque lieu (emoji de catégorie, nom, %), dont la somme égale la ligne ; bouton de fermeture et geste « retour » du système ; lue au lecteur d'écran (« Zone de danger, 12 pour cent, afficher le détail »). Aucune phrase d'origine (retirée par prudence de ton).
- **Couleur d'alerte douce** : tranche en `status/danger`, **hachurée**, icône d'avertissement (le triangle existant de l'app) ; **nom et pourcentage en texte courant** (`status/danger` ne sert jamais au texte). Ni fond rouge, ni clignotement.
- **Place** : après les lieux de confiance et « {n} autres lieux », avant « Ailleurs ». **Centre** : lieux de confiance seulement.
- **Chevauchement** : la zone de confiance l'emporte (D10, T17).
- **Ton** : la phrase de synthèse **ne nomme jamais** cette catégorie et n'en donne aucun pourcentage ; plus grande part = phrase générique (T21). **Aucune ligne** sans temps passé (T20) ni place réservée. États vides et erreurs : calmes, **sans couleur d'alerte**.
- **Ce qui change pour la personne** : elle voit, sur sa propre carte, le mot « danger » avec son pourcentage. **Cohérent avec l'app** (le Feed et N6 lui disent déjà « Tu es dans une zone de danger de ta Miff »). Risque de lecture accusatrice **assumé par le PM** (§16 R7).

### 10.3 Impacts
- **Modèle / règles / consentement** : **aucun changement** ; le graphique reste local. Le consentement d'entrée dans la Miff (ticket Miff) devrait déjà dire que le créateur définit les lieux et zones.
- **Suivi** : les faits `dans une zone de danger` existent déjà (`in_risk`) ; il suffit de les **enregistrer comme les autres** (§13). Une zone de danger surveillée consomme une des 20 zones.
- **Stores** : les règles anti-stalkerware (Apple, Google) visent précisément une zone désignée par un tiers : fiches à compléter par `release-manager`. Cas d'usage parent-enfant et couple.

### 10.4 Structure du donut (extensible sans refonte)
Liste ordonnée de **catégories typées**, jamais de « lieux » en dur : ajouter une catégorie plus tard = une ligne et un token, sans toucher au calcul, à la mise en page ni aux textes d'accessibilité.
| Type | Libellé / icône | Couleur | Regroupement |
|---|---|---|---|
| `lieu` | nom + emoji de catégorie | palette graphique en séquence | 5 au plus, puis `autres lieux` |
| `autres lieux` | « {n} autres lieux » + icône neutre | token dédié | — |
| `danger` | « Zone de danger » + icône d'avertissement | `status/danger` + hachures | **agrégée**, détail au toucher |
| `ailleurs` | « Ailleurs » + icône neutre | gris neutre | — |
Ordre : `lieu` (du plus grand au plus petit), `autres lieux`, `danger`, `ailleurs`. La palette garde **une couleur de réserve** (§15).

---

## 11. Cas limites

1. **Premier jour après la livraison** : journal vide → « pas assez de données » ; aucun temps antérieur reconstitué (jamais de lecture du statut serveur pour « deviner »).
2. **Nouveau téléphone, réinstallation** : le journal repart de zéro (limite D1). **Deux téléphones** : un journal chacun ; **un seul appareil tient la session** (SESS-2) et seul celui-ci écrit des présences : l'autre n'a pas de suivi actif.
3. **Même lieu rattaché à plusieurs Miffs** : un fait par Miff (le moteur dérive par Miff) ; « Ailleurs » d'une Miff inclut le temps passé dans un lieu d'une autre Miff.
4. **Changement d'heure, fuseau, voyage à l'étranger** : instants absolus, fenêtre en durée écoulée.
5. **Horloge modifiée à la main** : §6.2 point 8.
6. **App tuée / événement manqué / redémarrage** : plafond 48 h ; au redémarrage le moteur écrit une présence reconstruite ou « inconnue » (jamais un « dehors » deviné).
7. **Plusieurs Miffs** : balayer le carrousel recalcule sans clignotement ni état « vide » intermédiaire.
8. **Lieu supprimé, déplacé ou de type modifié pendant l'affichage** : recalcul sans erreur (§6.2 point 5).
9. **Plus de 5 lieux** : T10 (le regroupement ne concerne que les lieux de confiance).
10. **Lieu sans catégorie** : épingle neutre (règle de `place-categories.md`).
11. **Compte gratuit sans zone de danger** : jamais de place réservée à cette ligne.
12. **Quota de stockage plein** : l'écriture peut échouer ; **aucun message** (le suivi prime), fait manquant traité comme les autres.
13. **Données de démonstration** : un outil de remplissage du journal pour la recette, **uniquement** dans les builds debug / TestFlight (comme l'écran de diagnostic), **jamais** en release, libellé « données de test », test automatisé qui le prouve (TS-23).

---

## 12. Critères d'acceptation

Android et iOS, sauf mention. **Les mêmes cas de calcul (§6.3) passent à l'identique.**

**Données et vie privée**
- [ ] **TS-1** Aucune lecture ni écriture Firestore de plus pour afficher la carte (mesuré dans la PR ; les lieux déjà lus sont réutilisés).
- [ ] **TS-2** Le journal ne contient ni coordonnée, ni nom ou adresse de lieu, ni identifiant de personne (test sur le contenu stocké).
- [ ] **TS-3** Le journal est exclu des sauvegardes et des transferts d'appareil (Android : règles d'extraction vérifiées ; iOS : exclusion posée et vérifiée).
- [ ] **TS-4** Aucun événement Analytics, journal debug ou clé de plantage ne contient lieu, durée ou pourcentage (liste blanche inchangée).
- [ ] **TS-5** Effacement : déconnexion, suppression de compte (via `SessionTeardown` / `accountLocalDataErasers`), départ / retrait / suppression d'une Miff (pour celle-ci) effacent le journal et la préférence de fenêtre. **Preuve** : se déconnecter, relancer, se reconnecter : « pas assez de données ».
- [ ] **TS-6** « Effacer mon historique de temps » : confirmation calme (« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »), puis « pas assez de données » ; aucune écriture serveur.
- [ ] **TS-7** Les faits de plus de 35 jours sont effacés.
- [ ] **TS-8** Les faits sont enregistrés **téléphone verrouillé et app fermée** (recette sur l'iPhone 11 Pro et l'OPPO Find X5 Lite avec de vrais déplacements ou une localisation simulée).
- [ ] **TS-8b** **Le journal reproduit la présence du moteur** : à chaque changement de présence d'une Miff écrit dans le statut, un fait de même état, même lieu, même instant est journalisé (test unitaire sur le moteur avec les mêmes scénarios que le Feed).

**Calcul**
- [ ] **TS-9** T1 à T23 passent sur les deux plateformes.
- [ ] **TS-10** Somme = 100 % ; jamais de temps négatif ; jamais un lieu compté deux fois.
- [ ] **TS-11** Le temps non mesuré n'est jamais compté en « Ailleurs ».

**Affichage et états**
- [ ] **TS-12** La carte est entre le carrousel et « Lieux de confiance de … », suit la Miff sélectionnée, conserve la fenêtre, ne clignote pas au balayage ; la carte « Mode voyage » reste en dernier.
- [ ] **TS-13** Défaut 7 jours ; 14 et 30 disponibles ; choix mémorisé au redémarrage, oublié à la déconnexion.
- [ ] **TS-14** Compte gratuit (≤ 3 lieux) : 2 à 4 parts lisibles ; plus de 5 lieux : « {n} autres lieux » ; « Ailleurs » toujours en dernier.
- [ ] **TS-15** Chaque état de §7 dans la bonne condition, **texte exact** FR et EN ; l'état « pas assez de données » reprend le wording figé du PM.
- [ ] **TS-16** Carte masquée sans lieu ; jamais de carte tant que la lecture des lieux n'est pas faite (comme la liste des lieux : rien dessiné pendant le chargement).
- [ ] **TS-17** Aucune formule de félicitation, de reproche ou de jugement ; **la phrase de synthèse ne nomme jamais la zone de danger** ; la couleur d'alerte n'apparaît **que** sur la tranche et la ligne « Zone de danger ».
- [ ] **TS-17b** Zone de danger : **une seule ligne agrégée** (icône d'avertissement, %, tranche hachurée) s'il y a du temps passé ; toucher la ligne ouvre un détail (un lieu par ligne) dont la somme égale la ligne, fermable (bouton et geste du système) et accessible ; aucune ligne sans temps.
- [ ] **TS-17c** Libellé et icône de la ligne = ceux de l'app (« Zone de danger ») ; le type vient du champ `type` du lieu ; **aucun changement de modèle ni de règles**.
- [ ] **TS-18** Hors connexion : comportement identique.
- [ ] **TS-19** L'emoji de la légende est **le même** que celui de la liste des lieux en dessous (table `place-categories.md`).
- [ ] **TS-19b** Android 13+ : l'aperçu des applications récentes reste masqué sur l'onglet Profil avec la carte (`RecentsPrivacy`).

**Accessibilité et langues**
- [ ] **TS-20** Lecteur d'écran : description unique, anneau caché, légende sans doublon, sélecteur annoncé ; cibles ≥ 48 dp / 44 pt ; **nom de la catégorie lu, pas l'emoji**.
- [ ] **TS-21** Police à 200 % : aucune troncature ni chevauchement.
- [ ] **TS-22** FR + EN, parité de clés CI, tokens uniquement.
- [ ] **TS-23** L'outil de remplissage de données de test **n'existe pas** en release (test automatisé, dans l'esprit du test existant sur `DetectionJournal`).

**Sécurité et coût**
- [ ] **TS-24** Aucune bibliothèque de graphique tierce (dessin natif) ; aucune dépendance payante. (iOS : certains composants de graphique système exigent iOS 17 alors que le minimum est iOS 16 : `ios-dev` confirme.)
- [ ] **TS-25** Aucun secret ni règle Firestore touchés ; revue de sécurité ciblée sur le contenu du journal, son stockage, son effacement ; le **nouveau journal ne partage pas de code avec `DetectionJournal`** (qui doit rester absent des builds de release).

---

## 13. Branchement sur le suivi existant (ce qu'il faut ajouter, précisément)

Rien à changer dans la détection. À ajouter :
1. **Un journal de présence distinct de `DetectionJournal`** (qui reste de recette, debug / TestFlight) : un composant **actif en release**, derrière une interface testable (double en test), avec **un seul point d'entrée** « la présence dérivée de la Miff X est devenue (état, lieu) à l'instant t ».
2. **Brancher ce point d'entrée** aux endroits où le moteur change la présence dérivée d'une Miff (il y a **un seul calculateur** : `PresenceMachine.derive` ; ne **pas** recalculer la présence ailleurs) :
   - Android `TrackingEngine` : `doTransition` (`PresenceMachine.onTransition`), `reconcile` (redémarrage, lieu supprimé ou modifié), `presenceAfterResume`, `readPlacesOf` (lieu déplacé ou de type modifié → `unknown`), `disarm` / `doStop` / `syncMiffs` (Miff non éligible : `unknown`), début et fin de voyage (`pauseStatuses`, `endTravel`, `adoptTravel`).
   - iOS : les mêmes passages dans `TrackingEngine+Events`, `+Refresh`, `+TravelEnd`, `+Writes`, `LocationTracking` (miroir fonctionnel du moteur Android).
   - **L'instant enregistré = celui que le moteur utilise pour le statut** (`StatusFact.presenceAtMillis` / `at`), pas un `now` pris ailleurs.
3. **Effacement** : brancher le journal sur `SessionTeardown.stop()` (déconnexion, suppression de compte, session perdue) **et** sur la perte d'accès / départ d'une Miff (purge de cette Miff seulement), et le lister dans `accountLocalDataErasers` si besoin (règle du fichier : toute donnée liée à un compte y est listée). Ajouter le bouton de Réglages > Confidentialité (Android `ui/settings/PrivacyScreen.kt` ; iOS `Views/Settings/AccountAndPrivacyViews.swift`).
4. **Stockage** : fichier privé de l'app, exclu des sauvegardes, écriture possible téléphone verrouillé ; au plus 60 faits par jour et par appareil (disjoncteur déjà en place) donc petit.
5. **Lecture** : la carte reçoit les lieux **déjà lus** par la section « Lieux de confiance de … » (`ProfilePlacesViewModel` / `MiffPlacesBook` ; iOS `ProfilePlacesViewModel`) : **0 lecture Firestore de plus**. La Miff sélectionnée est celle du carrousel (`MiffCarouselHost`).
6. **Pas de changement** de `Analytics`, de règles Firestore, de `data-model.md`.

**Ce qui n'est pas nécessaire** : l'état initial (déjà fourni par `INITIAL_TRIGGER_ENTER` / `requestState` : une présence « dans un lieu » est signalée si l'on y est ; sinon « inconnue » jusqu'au premier fait, **non mesurée**), ni une confirmation périodique (non ajoutée : le plafond de 48 h couvre le cas).

---

## 14. Découpage (Android d'abord, puis iOS ; une branche et une PR par plateforme)

Le suivi existe déjà : **plus de dépendance bloquante**. La carte n'est plus masquée « tant que le Tracking n'est pas livré ».

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **TIME-0** | **Décisions et maquettes** (PM, `design-system`) | Palette « graphique » (tokens), états, panneau de détail, grande police, ligne Réglages, variantes iOS ; exports PNG dans `design/exports/profil/` avec README | S à M | Validation de ce ticket ; **ne bloque pas TIME-1** |
| **TIME-1** | **Android : journal de présence local, calcul, branchement, effacement** | §13 ; calcul §6 ; **tests T1 à T23 et TS-8b** ; bouton « Effacer mon historique de temps » ; outil de données de test (debug) ; aucune carte | M | Suivi existant ; **ne pas publier sur le Play Store avant la mise à jour de la politique** |
| **TIME-2** | **Android : carte du Profil et états** | §5, §7, §9, §10 ; sélecteur et mémorisation ; textes FR + EN ; accessibilité ; mesure de lectures (zéro de plus) | M | TIME-1, TIME-0 |
| **TIME-3** | **iOS : journal de présence local, calcul, branchement, effacement** | Mêmes livrables que TIME-1 ; écriture verrouillé ; exclusion iCloud | M | TIME-1 (référence), moteur iOS |
| **TIME-4** | **iOS : carte du Profil et états** | Mêmes livrables que TIME-2 ; dessin natif (iOS 16) | M | TIME-3, TIME-2 |

**Ordre conseillé** : TIME-1 le plus tôt possible (**l'historique ne commence qu'à sa livraison**, donc le déployer avant la carte fait gagner des jours de données), TIME-0 en parallèle, TIME-2, puis iOS. Chaque PR : revue de code en contexte neuf, revue sécurité, QA, recette du PM sur ses deux téléphones, fusion par Claude après « oui » explicite. **La recette de la vraie donnée demande des jours de port du téléphone** : l'outil de données de test valide l'interface, une recette réelle d'au moins 24 h valide la collecte.

**Différences Android / iOS** : Android : événements reçus dans un récepteur qui s'exécute app fermée (écriture sans interface), surcouches constructeur (OPPO) qui retardent ou suppriment des événements, `RecentsPrivacy` déjà en place ; iOS : événements de région livrés avec retard après relance, `recheckStates` à l'ouverture, protection de fichier compatible avec l'écriture verrouillé, **pas de masquage de l'aperçu des apps récentes**, test sans Mac : CI cloud + TestFlight. **Aucune nouvelle permission.**

---

## 15. Maquettes nécessaires (exports PNG avant développement, règle du 2026-10-28)

Fournie : `19.webp` (carte nominale Android, 7 jours). **À dessiner dans « Miff - Claude »** :
1. **Palette « graphique »** : 5 couleurs de lieux + « autres lieux » + « ailleurs » + **1 couleur de réserve**, dérivées du **bleu de marque** (`action/primary` `#043cd1` et teintes) et du jaune-vert **assombri** pour tenir 3:1 ; **aucune teinte rouge, orange ou jaune-vert de statut** ; la **zone de danger** utilise `status/danger` (seule exception, hachures, contraste à vérifier). Tokens `chart/1`…, `chart/danger`, `chart/other`, `chart/elsewhere`.
2. **Carte nominale** : 2 parts (gratuit), 4 parts, 5 lieux + « autres lieux » + « Ailleurs », **avec** la ligne « Zone de danger ».
3. **Tous les états** de §7.
4. **Menu du sélecteur** (7 / 14 / 30), iOS et Android.
5. **Feuille de détail de la zone de danger**.
6. **Grande police (200 %)** : légende sous l'anneau, texte central.
7. **Icônes neutres** « Ailleurs » et « {n} autres lieux » ; pastille d'en-tête ; cadenas.
8. **Ligne Réglages > Confidentialité** « Effacer mon historique de temps » et sa confirmation.
9. **Bandeau de synthèse** neutre ; **variante iOS** complète.

---

## 16. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Promesse « pas d'historique »** (politique, stores) | Perte de confiance, rejet | Journal **local**, jamais envoyé ; phrase de confidentialité sur la carte ; **mise à jour de la politique avant publication** (§8) |
| R2 | **Précision** : événements datés à l'arrivée, app tuée (OPPO), téléphone éteint compté « Ailleurs » | Graphique inexact | Pourcentages entiers, jamais de durée ; plafond 48 h ; temps non mesuré exclu ; mesure sur les deux téléphones du PM |
| R3 | **Historique propre à l'appareil, qui démarre à la livraison** | « Ça ne marche pas » le premier jour ; perte au changement de téléphone | Wording figé du PM ; limite documentée ; signal de bascule B (D1) ; déployer TIME-1 tôt |
| R4 | **Journal d'habitudes sur un téléphone** (téléphone déverrouillé par un tiers) | Routines révélées | Stockage privé, sans coordonnées ni noms, 35 jours, effaçable |
| R5 | **Palette** : peu de couleurs distinctes et contrastées dans la marque | Tranches confondues | Légende emoji + nom + %, espaces, 8 catégories au plus ; `design-system` valide |
| R6 | **Wording « nous analysons ta situation »** (**figé par le PM**) | Peut laisser croire à une analyse côté serveur alors que les données restent sur le téléphone, comme le dit la phrase de confidentialité juste en dessous | **Risque assumé par le PM**, aucune variante. Seule cohérence prévue : le texte n'est affiché **que si le suivi est actif** ; sinon états « partage arrêté », « localisation insuffisante », « mode voyage » |
| R7 | **« Zone de danger » affichée à la personne** (choix du PM, libellé de l'app) | Lecture accusatrice, surtout pour un parent / un conjoint créateur de zones | **Risque assumé sur le mot** ; mesures : ligne agrégée, couleur douce, motif, synthèse muette sur la catégorie, états calmes |
| R8 | **Écart avec la maquette** (E1 à E9) | Le PM voulait la maquette « telle quelle » | Écarts listés et justifiés ; chaque PR les reprend |
| R9 | **iOS non testable en local** | Cycle lent | Android d'abord ; tests de calcul identiques ; TestFlight |
| R10 | **Aperçu des apps récentes sur iOS et Android ≤ 12** | La carte peut apparaître dans le sélecteur d'applications | Limite connue, déjà acceptée pour le reste de l'app (README Android) |
| R11 | **Politique de confidentialité inexacte sur l'appareil** tant qu'elle n'est pas mise à jour | Risque juridique assumé par le PM | Rappel §8 ; TIME-1 non publié sur les stores avant ; aucune relecture juridique prétendue |
| R12 | **Plus de 20 zones** (Miff+ multi-lieux) | « Ailleurs » gonflé pour les zones non surveillées | Note sous l'anneau (D18) |

---

## 17. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Stockage et calcul | Sur l'appareil, quelques centaines de Ko au pire | Historique propre à l'appareil, démarre à la livraison, 35 jours | Besoin de multi-appareils / plus de 30 jours / vue des proches : **option B** (compteurs journaliers Firestore), décision du PM, nouveau consentement |
| Lectures / écritures Firestore | **Zéro** de plus | — | Une PR qui en ajouterait doit le justifier (`data-model.md` §12) |
| Bibliothèque de graphique | **Aucune** (dessin natif) | iOS 16 : pas de graphique en anneau système | Une bibliothèque tierce = décision du PM |
| Analytics, Blaze | **Aucun nouvel événement**, aucune fonction | — | — |

Aucun moyen de paiement ajouté ; aucun choix économique ne réduit la sécurité ni la vie privée.

---

## 18. Questions pour le PM (les seules qui ne se tranchent pas par défaut)

1. **Extension future aux proches** : veux-tu un jour voir, ou montrer à ton cercle, le temps passé par lieu ? Cela suppose l'option B, un **nouveau consentement distinct**, la mise à jour des textes légaux et un examen anti-stalkerware. **Par défaut : non, jamais sans ta décision explicite.**
2. **Publication de la politique de confidentialité** : la mention du journal local doit partir **avant** toute publication stores de TIME-1 ; c'est toi qui publies les textes (risque assumé, sans relecture juridique). Veux-tu qu'on prépare la phrase maintenant (par `release-manager`) ?

*Tout le reste est tranché par défaut en §4 et contestable : valeurs 24 h / 48 h, journal conservé 35 jours à l'arrêt du partage, la confiance l'emporte sur la danger, « Ailleurs », « {n} autres lieux », phrase d'origine retirée, coche verte et emoji de fête retirés, masquage dans les apps récentes (déjà fait sur Android, non traité sur iOS), ligne « Effacer mon historique ».*

---

## 19. Dépendances et ordre

Aucune dépendance bloquante : le suivi (`TrackingEngine`), les lieux, le carrousel et le mode voyage existent. Pour TIME-1 : suivre le contrat de `docs/tickets/mode-voyage.md` (voyage = non mesuré) et `docs/contracts/place-categories.md` (emoji). Pour TIME-2 : maquettes de TIME-0. Rien dans ce ticket n'exige Blaze, un moyen de paiement, un déploiement de règles ou une modification de Notion.

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
