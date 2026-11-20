# Ticket : Feed, ordre des cartes par date de la dernière photo (FEED-ORDRE-PHOTO)

**Statut** : brouillon, rédigé le 2026-11-20 (agent `product-specs`, avec la partie backend livrée dans la même PR). Le PM a délégué la validation des specs : les décisions par défaut du §3 s'appliquent tant que le PM ne les conteste pas. Aucun statut Notion modifié, **rien de déployé** (les règles sont à déployer par le PM, ordre au §7).
**Demande du PM (2026-11-20)** : « UNIQUEMENT selon la date à laquelle la dernière photo a été publiée. Photo récente = en premier, moins récente = après. »
**Remplace** `docs/tickets/feed-ordre-derniere-update.md` (FEED-ORDRE, 2026-11-17, livré sur Android et iOS) pour ce qui touche **la date de tri** : décisions §3 #1, #2 (dernière phrase), #3, #5, #7 (texte de l'annonce), #8, #9 et critères A1, A3, A5, A6 (déclencheur), A11, A12 (textes) ; correspondance complète au §5. Tout le reste de FEED-ORDRE reste valable (comportement en direct, accessibilité, ordre stable, retour d'onglet, bandeau et alertes, hors-scope). Un bandeau « Remplacé le 2026-11-20 » a été ajouté en tête de l'ancien ticket.
**Sources lues** : `CLAUDE.md` ; `docs/tickets/feed-ordre-derniere-update.md` ; `backend/data-model.md` (§2.7, §2.8, §8, §12) ; `backend/firestore.rules` (`validStatus`, `statusInstant`, `validPhoto`, `photoLocationReadable`) ; **le code, relu pour ce ticket** : Android `domain/feed/FeedBuilder.kt` (`orderMembers`, `newsDate`), `domain/feed/MemberStatus.kt`, `data/feed/StatusObserver.kt`, `domain/tracking/TrackingWrites.kt` (`photoStatus`, `statusUpdate`), `domain/miff/MiffWrites.kt` (statut initial), `domain/feed/CardOrderGate.kt` ; iOS `Models/Feed/FeedOrder.swift`, `FeedBuilder+Order.swift`, `Models/Update/UpdateWrites.swift` (`photoStatus`).

---

## 1. Contexte et valeur

Le PM veut que le Feed raconte « qui a partagé une photo en dernier » : celui qui vient de publier une photo remonte en tête, et plus aucun autre signal ne déplace une carte. Ni une arrivée automatique à la maison, ni une update sans photo, ni une batterie, ni un voyage.

Ce que le tri **n'est toujours pas** : un tri par urgence. Une carte orange ou rouge ne remonte pas pour cette raison ; le bandeau d'alertes (§6.2 de `feed-profil-reglages.md`) est inchangé. Les cartes en **alerte « Je suis en danger »** en cours restent **en première position** (`alerte-danger.md`, décision antérieure et indépendante, déjà dans le code : `FeedBuilder.alertsFirst`) ; le tri par photo s'applique à toutes les autres.

**Conséquence à connaître (voir Question 1)** : une carte dont la dernière photo date de trois jours reste **au-dessus** d'une carte qui n'a jamais publié de photo, même si cette dernière vient d'arriver à la maison. C'est ce que demande la règle « uniquement selon la photo ».

## 2. Faisabilité : ce que le modèle de données permet (constat reconfirmé dans le code et les règles)

| Donnée existante | Ce que c'est | Utilisable pour dater la photo ? |
|---|---|---|
| `status.lastUpdateAt` | Dernier signal « tout va bien ». Écrit par la publication d'une photo (heure serveur) **mais aussi** par l'entrée dans un lieu de confiance (`TrackingWrites.statusUpdate`, `fact.setLastUpdate`) **et** à la création du statut initial (activation du partage). | **Non** : il ne date pas la photo. |
| `status.hasPhoto` | Booléen. Passe à `false` seulement quand **l'auteur** nettoie sa photo expirée, donc peut rester `true` après les 24 h. | **Non** : aucune date. |
| `photos/{uid}.createdAt` | Heure serveur de la photo. Mais lisible seulement par un `get` (4 lectures facturées par photo ouverte, jamais de pré-chargement), **seulement jusqu'à l'expiration** (≤ 25 h), supprimée à la révocation du partage. | **Non** : après 24 h, tout le monde serait « sans date », et il faudrait une lecture par membre à chaque rafraîchissement. |
| `status.updatedAt` | Heure serveur de n'importe quelle écriture du statut (batterie, géofence…). | **Non**. |
| Clés de `status` | **Figées par les règles** (`hasOnly` / `hasAll`) : une clé inconnue est refusée. | Donc **pas de nouveau champ sans changer les règles**. |

**Conclusion : la règle demandée n'est pas réalisable proprement sans une évolution du backend.** Le plus petit changement : **une clé facultative `photoAt` dans `status/{uid}`** (§7). Le statut est déjà écrit à la publication de la photo et déjà écouté par le Feed : **zéro lecture et zéro écriture facturée de plus**, aucune Cloud Function.

**Correction d'un constat de départ** : le statut n'est **pas** réécrit en entier à chaque événement. Android et iOS l'écrivent par **mises à jour partielles** (champs listés ; le document fusionné est jugé par les règles). Seul le statut **initial** (entrée dans la Miff, activation du partage) est un `set` complet. Conséquence : une écriture qui ne cite pas `photoAt` **la garde**, ce qui simplifie la règle (pas de recopie obligatoire) et protège les anciens clients ; les règles acceptent malgré tout la recopie à l'identique (§7).

## 3. Décisions par défaut (à contester)

« Remplace » = remplace la décision du même numéro dans FEED-ORDRE ; « Conservée » = inchangée.

| # | Sujet | Décision par défaut | Pourquoi / limite |
|---|---|---|---|
| 1 | Qu'est-ce qui date une carte ? **(remplace #1)** | **Uniquement** `status.photoAt` : l'heure du serveur de la **dernière publication d'une photo** dans cette Miff. Aucun autre signal ne change l'ordre : update sans photo, arrivée ou départ d'un lieu, batterie, permission de localisation, voyage, `lastUpdateAt`, `presenceAt`, `hasPhoto`. | Demande du PM. `photoAt` est écrit **seulement** à la publication d'une photo (§8). |
| 2 | Ma propre carte **(#2, dernière phrase remplacée)** | Triée comme les autres, selon **ma** dernière photo. Elle garde son contenu propre (pas de Message / Appeler, état de mon partage). Sans photo publiée, ma carte suit la règle #3. | Principe conservé de FEED-ORDRE ; seule la référence à l'ancien #3 change. |
| 3 | Membres sans date **(remplace #3)** | Placés **après tous ceux qui ont une date**, dans l'ordre d'arrivée dans la Miff. « Sans date » = pas de `photoAt` lisible (jamais de photo dans cette Miff, statut créé avant cette clé, statut recréé après un arrêt du partage, ancienne version de l'application) **ou** carte « Partage désactivé », « Informations masquées » ou « en attente de mon accord » (leur statut n'est pas lu, même s'il en reste un). **L'exception de l'ancien #3 disparaît** (carte « Pas encore de nouvelles » avec `lastUpdateAt > presenceAt` : plus aucun rôle). | Une carte « Pas encore de nouvelles » qui a une `photoAt` est classée par elle comme les autres ; sans `photoAt`, elle est sans date. Le libellé des cartes est inchangé. |
| 4 | Égalité de dates **(conservée)** | Ordre d'arrivée dans la Miff, puis identifiant du membre. Les dates sont comparées **à la milliseconde** (même troncature sur Android et iOS) : deux photos dans la même milliseconde sont à égalité. | Déterministe d'un lancement à l'autre. |
| 5 | Photo expirée ou supprimée **(remplace #5, même résultat, autre raison)** | **Sans effet sur l'ordre** : `photoAt` **garde sa date** après l'expiration (24 h) et après le nettoyage par l'auteur (`hasPhoto = false`). Seule la vignette disparaît de la carte. | Le rang dit « la dernière fois qu'il a partagé une photo », pas « la photo est encore visible ». |
| 6 | Comportement en direct **(conservée)** | Le contenu d'une carte se met à jour tout de suite ; le **changement d'ordre** attend la fin du geste (défilement, doigt posé), puis la carte glisse (sans animation si « réduire les animations » est actif ou si la carte n'est pas visible) ; retour sur l'onglet Feed, rafraîchissement manuel, changement de Miff : ordre à jour appliqué directement (fenêtre de 4 secondes) ; un seul réordonnancement final ; chaque carte garde son identité (le membre). | Inchangé : `FeedDisplayOrder` (iOS) / `CardOrderGate` (Android) ne dépendent pas de la date. |
| 7 | Accessibilité **(texte de l'annonce remplacé)** | Le réordonnancement ne déplace pas le focus. Annonce **non interruptive**, une seule par application d'ordre, seulement si l'ordre change réellement : FR « {Prénom} a publié une photo » / EN « {Name} shared a photo », **seulement si ce proche arrive en tête de liste** ; sinon FR « Le Feed a été mis à jour » / EN « The Feed was updated » ; aucune annonce pour ma carte, ni au retour sur l'onglet, ni quand quelqu'un quitte la Miff. | **Nuance** : l'ancien texte « a donné des nouvelles » devenait inexact (une arrivée à la maison ne déplace plus rien). Une carte ne peut désormais monter que par une **nouvelle photo** (ou parce qu'elle obtient sa première date), donc « a publié une photo » est presque toujours vrai. **Deux cas où l'annonce peut être inexacte, acceptés** : (1) quand une alerte « Je suis en danger » se termine à 2 membres, la carte qui n'est plus en tête d'alerte peut « arriver en tête » sans nouvelle photo ; (2) à la reprise du partage d'un proche, sa carte peut changer de rang sans photo nouvelle. L'annonce est alors celle de la règle (un proche arrive en tête), sans conséquence pour la sécurité ; ton factuel, non anxiogène. Ne pas confondre avec la **notification push** « {Prénom} a donné des nouvelles » (serveur, `notification-update-membres.md`), qui reste inchangée. |
| 8 | Backend **(remplace #8 « aucun changement »)** | **Une clé facultative `photoAt`** dans `status/{uid}` + une règle (`validPhotoAt`) ; **aucune lecture, aucune écriture, aucune Cloud Function de plus**. Détail au §7. | Seule voie qui date la photo (§2). **Déploiement des règles par le PM, avant les applications.** |
| 9 | Heure utilisée pour comparer **(remplace #9)** | Seules les valeurs stockées sont comparées. `photoAt` est **toujours l'heure du serveur** (jamais celle d'un téléphone) : **plus aucune dérive d'horloge** à l'écriture, et l'horloge de celui qui regarde n'intervient jamais. | Les règles refusent toute autre valeur (§7). La seule marge : un envoi différé (hors ligne) est daté à la réception par le serveur, comme `createdAt` de la photo. |
| 10 | Date inexploitable à la lecture | Une `photoAt` absente, nulle ou qui n'est pas un horodatage est traitée comme **absente** : jamais de plantage, jamais de refus. Aucune « correction » de la valeur (pas de plafond à l'heure du lecteur). | Les applications doivent tolérer l'absence du champ (statuts existants, anciens clients). |
| 11 | Alertes en cours | Cartes en alerte **d'abord** (ordre propre aux alertes), puis toutes les autres selon `photoAt`. | Décision de `alerte-danger.md`, hors de ce ticket. |

## 4. User stories

- En tant que membre d'une Miff, je veux voir en premier le proche qui a **partagé une photo** le plus récemment, afin de savoir d'un coup d'œil qui vient de me donner des nouvelles en images.
- En tant que membre, je veux que la liste ne bouge pas pendant que je la lis ou que j'appuie sur « Message » ou « Appeler », afin de ne jamais toucher la mauvaise personne.
- En tant que membre qui utilise un lecteur d'écran, je veux être informé sans surprise quand l'ordre change, afin de garder mes repères.

## 5. Critères d'acceptation (identiques Android et iOS)

Correspondance avec FEED-ORDRE : A1 → B1 (remplacé), A2 → B2 (conservé, reformulé), A3 → B3 (remplacé), A4 → B4 (conservé), A5 → B5 (remplacé), A6 → B6 (déclencheur changé), A7 → B7, A8 → B8, A9 → B9 (texte remplacé), A10 → B10, A11 → B11 (remplacé), A12 → B12 (textes remplacés). B13 à B15 sont nouveaux.

- [ ] **B1** Les cartes sont triées de la `photoAt` la plus récente à la plus ancienne.
- [ ] **B2** Ma carte est triée comme les autres, selon ma propre `photoAt` ; elle n'est plus forcée en première position.
- [ ] **B3** Les membres sans date (§3 #3) sont après tous ceux qui en ont une, dans l'ordre d'arrivée dans la Miff.
- [ ] **B4** À dates égales (à la milliseconde), l'ordre est stable et reproductible (§3 #4).
- [ ] **B5** **Seule une nouvelle photo change l'ordre** : ni une update sans photo (`lastUpdateAt` plus récent), ni une arrivée ou un départ d'un lieu, ni la batterie, ni un voyage, ni l'expiration ou la suppression de la photo ne déplacent une carte.
- [ ] **B6** Quand un proche publie une photo, le contenu de sa carte est mis à jour aussitôt ; sa carte rejoint la tête de liste avec une animation douce, sans animation si « réduire les animations » est actif.
- [ ] **B7** Pendant un défilement ou un appui (doigt posé), aucune carte ne change de place ; l'ordre est appliqué à la fin du geste. Un appui en cours sur « Message » / « Appeler » atteint toujours le bon proche. *(Conservé.)*
- [ ] **B8** Au retour sur le Feed, au rafraîchissement manuel et au changement de Miff, l'ordre affiché est l'ordre à jour, sans attendre de geste. *(Conservé.)*
- [ ] **B9** Lecteur d'écran : pas de saut de focus ; annonce non interruptive conforme au §3 #7 ; ordre de lecture = ordre visuel.
- [ ] **B10** Le bandeau de synthèse et les alertes (§6.2), les cartes en alerte en première position, le sélecteur de Miff (Premium) et les états neutres (§6.4) sont inchangés.
- [ ] **B11** Backend : une seule clé facultative `photoAt` dans le statut, écrite **uniquement** à la publication d'une photo avec `FieldValue.serverTimestamp()` ; **aucune lecture et aucune écriture Firestore supplémentaire** par rapport à avant (à vérifier dans la PR : nombre de documents lus par le Feed et d'écritures par publication, inchangés).
- [ ] **B12** Textes FR et EN (repli EN) pour les annonces ; ton non anxiogène. Les autres langues en cours de traduction (`es`, `it`, `pt`, `de`) reçoivent la même clé.
- [ ] **B13** Tolérance : un statut **sans** `photoAt` (statuts existants, ancien client) ne plante pas, n'est pas refusé et est classé « sans date ».
- [ ] **B14** Les clients n'écrivent `photoAt` **que** dans la mise à jour partielle de la publication d'une photo ; jamais dans le nettoyage d'une photo expirée (`hasPhoto = false`), une arrivée en lieu, une batterie, un voyage, le statut initial ; jamais avec l'heure de l'appareil.
- [ ] **B15** Une carte en alerte en cours reste en première position ; les autres suivent l'ordre photo.

## 6. Cas limites

- **Update sans photo après une update avec photo** : `lastUpdateAt` avance (la couleur repart du vert), `photoAt` ne bouge pas : **l'ordre ne change pas**. Test clé : le tri ignore `lastUpdateAt`.
- **Arrivée dans un lieu de confiance** : n'écrit pas `photoAt` : la carte ne remonte plus (contrairement à FEED-ORDRE).
- **Photo expirée (> 24 h) ou nettoyée par son auteur** : `hasPhoto = false`, `photoAt` conservée : le rang reste. La vignette disparaît ; la date de la carte (« il y a X ») reste celle des règles de couleur, inchangées.
- **Photo supprimée à la révocation du partage** : le statut est supprimé avec la photo ; la carte devient « Partage désactivé » (sans date). **À la réactivation, le statut est recréé sans `photoAt` : la date est perdue** ; la carte reste « sans date » jusqu'à la prochaine photo. Voulu (aucune donnée ne survit à la révocation).
- **Quitter puis rejoindre une Miff** : même chose (nouveau statut, pas de date).
- **Horloge** : `photoAt` = heure du serveur ; ni le téléphone de l'auteur ni celui du lecteur ne peuvent fausser le rang. Pendant que ma publication attend le serveur, l'écouteur local estime la date à « maintenant » (comportement déjà en place : Android lit les horodatages d'un statut en attente avec l'estimation, iOS lit avec `data(with: .estimate)` dans `FeedFirestoreMapping.swift`) : ma carte monte tout de suite, puis la valeur du serveur la remplace sans changer l'ordre relatif.
- **Hors ligne** : la photo et le statut sont écrits dans le même lot ; envoyés à la reconnexion, ils portent l'heure de réception par le serveur (comme `createdAt` de la photo). Le Feed hors ligne garde l'ordre des dernières données connues ; aucun réordonnancement pendant un geste à la reconnexion. Une carte qui obtient sa date lors de la première synchronisation après un retour de connexion est appliquée **directement** (fenêtre de 4 secondes), sans annonce.
- **Ancien client** : une application sans la clé écrit toujours son statut par mises à jour partielles : acceptée par les règles, **date jamais effacée ni modifiée**. Si cet ancien client publie une photo, `photoAt` reste celle de la photo précédente (ou absente) : son rang ne monte pas tant qu'il n'a pas mis à jour l'application (limite transitoire ; un compte n'a qu'un appareil, donc cela ne touche que la personne concernée).
- **Migration** : aucun statut existant n'a `photoAt`. **Après le déploiement, tous les membres sont « sans date »** : le Feed est dans l'ordre d'arrivée, comme avant FEED-ORDRE, jusqu'à ce que chacun publie une nouvelle photo. Aucune migration de données, aucune déduction depuis `lastUpdateAt` (ce ne serait pas la date de la photo). Pour la recette, chaque compte de test doit **d'abord publier une photo**.
- **Plusieurs Miffs** : `photoAt` est **par Miff** (une photo publiée dans une Miff porte sa date dans le statut de chaque Miff du lot ; une Miff rejointe ensuite n'en a pas). Le tri s'applique à la Miff affichée.
- **Alerte en cours** : la carte en alerte est en tête quelle que soit sa photo (B15).
- **Un seul membre (moi)**, **tous sans date**, **un membre qui quitte** : comme dans FEED-ORDRE (une seule carte, ordre d'arrivée, pas d'annonce).
- **Photo publiée sans avoir quitté de lieu** (carte « Pas encore de nouvelles ») : la carte remonte par sa `photoAt` ; son libellé est inchangé (le point 4 de FEED-ORDRE, sur le libellé, reste ouvert).
- **Client modifié** : peut écrire `photoAt` = heure du serveur sans publier de photo et ainsi faire monter SA carte dans le Feed de ses proches et déclencher chez eux l'annonce du lecteur d'écran « {Prénom} a publié une photo » (sans notification push) ; ses proches voient « pas de photo ». Aucune donnée d'un autre membre n'est touchée (§7, limite assumée).
- **Effacer sa date** : `photoAt: null` ou `FieldValue.delete()` en `update` partiel efface la date (la règle accepte « absente ou nulle ») ; ne touche que l'auteur (sa carte retombe « sans date »), inoffensif.
- **Statut créé avec `hasPhoto: true` et `photoAt` = heure du serveur, sans photo** : accepté par les règles (rien ne prouve qu'une photo existe dans le lot) ; c'est exactement la limite assumée du §7 (rang de sa propre carte, « pas de photo » pour les proches).

## 7. Backend (livré dans cette PR : règles, tests, data-model ; rien n'est déployé)

**Choix** : la partie backend est livrée **dans la même PR** que ce ticket (règles + tests + `data-model.md`) plutôt que renvoyée à un autre agent : le ticket n'a de sens que si la règle existe et est testée, la règle est courte (une fonction, aucune lecture), et les tests (14 cas, 9 mutations) ont été exécutés dans l'émulateur. Les deux PR applicatives (Android, iOS) viennent ensuite ; elles **dépendent du déploiement des règles**.

- **Règle** (`validPhotoAt`, dans `validStatus`) : `photoAt` facultative, acceptée si **absente ou nulle**, ou **identique à la valeur déjà en base** (recopie à la microseconde, quel que soit l'âge : même contrat que R-LIEU-1), ou **exactement l'heure du serveur ET `hasPhoto` vrai**. Rien d'autre : pas d'heure d'appareil, pas d'antidatage ni de postdatage, jamais la valeur d'un autre champ ou d'un autre membre. C'est **la borne la plus stricte compatible avec une recopie** (une borne à −24 h comme `lastUpdateAt` interdirait de recopier une vieille date).
- **Sécurité** : seul l'auteur écrit son statut (inchangé) ; un membre ne peut pas modifier celui d'un autre (PA8) ; schéma fermé (PA9). **Limite assumée** : les règles ne prouvent pas qu'une photo existe dans le même lot ; un client modifié peut remonter **sa propre** carte à l'heure du serveur (y compris à la **création** d'un statut avec `hasPhoto: true` et `photoAt` serveur, sans photo) et déclencher chez ses proches l'annonce « {Prénom} a publié une photo » (sans push) ; il peut aussi effacer sa date (`photoAt: null` ou `FieldValue.delete()` en mise à jour partielle : inoffensif, propre à son auteur). Aucune donnée d'un autre membre n'est touchée. Alternative écartée : `getAfter(photos/{uid})` dans `validStatus` (+1 lecture de règles par Miff : mise à jour manuelle de 3 à 4 par Miff, **5 Miffs au plus** par lot au lieu de 6, aucune marge, pour empêcher un effet sans gravité sur le seul rang de sa propre carte). À rouvrir si le tri devient un enjeu de confiance.
- **Coût** : **0 lecture de règles de plus** (`resource` n'en est pas une ; `tests/unit/budgets.test.js` passe sans changement), **0 lecture et 0 écriture facturée de plus** (la date est dans le statut déjà écrit et déjà écouté). Aucun index.
- **Compatibilité** : tous les statuts existants et toutes les écritures des clients actuels passent (PA7) ; un ancien client ne peut ni effacer ni modifier la date (mises à jour partielles). Limite documentée : un `set` complet sans la clé la retire (aucun client n'en écrit sur un statut existant ; suppression puis recréation de son statut est de toute façon permise).
- **Tests** : `backend/tests/rules/37-photo-date-feed.test.js` (plan `backend/rules-test-plan.md` §31, `PA1` à `PA13`), mutations `backend/tests/tools/mutations-photo-date.mjs` (9 : 8 détectées + 1 équivalente documentée, le retrait de `resource != null &&`). La CI « Tests des règles Firestore » les exécute.
- **Ordre de déploiement (à faire par le PM, jamais par un agent ; procédure : `docs/dev/deploiement-regles.md`, `--project miff-3d283`)** :
  1. **Fusionner cette PR** (après le « oui » du PM), CI verte.
  2. **Déployer les règles** de `main` (le **fichier entier** : vérifier dans l'en-tête de `backend/firestore.rules` ce qui est encore NON DÉPLOYÉ et que le PM accepte d'envoyer le tout). Ces règles sont **rétro-compatibles** : rien ne casse pour les applications actuelles.
  3. **Seulement ensuite** : livrer les applications Android puis iOS qui écrivent `photoAt` (APK / TestFlight). **Sans repli, une application qui écrit `photoAt` avant le déploiement des règles voit chaque publication de photo refusée en entier** (photo comprise). **Android (PR #300) a un repli** : sur un refus (PERMISSION_DENIED) elle refait UNE tentative identique sans `photoAt`, donc la photo n'est PAS refusée avant le déploiement (seule la carte de l'auteur ne remonte pas) ; la demande de `photoAt` est suspendue 1 h après un repli réussi, puis refaite ; après le déploiement des règles, fermer complètement l'application puis la relancer. **iOS : même repli si sa PR le fait** (à rendre cohérent avec la PR iOS ; sinon, ne jamais installer le build iOS avant l'étape 2).
  3 bis. **Texte légal (avant la mise en service, à rattacher à la PR applicative ; non modifié ici)** : la politique de confidentialité (`docs/legal/politique-confidentialite.fr.md` et `.en.md`) devra mentionner « la date de votre dernière photo, conservée tant que le partage est actif, effacée avec lui » (donnée facultative du statut, supprimée avec le statut à l'arrêt du partage). Ne pas livrer les applications sans cette mention ; aucun texte légal n'est modifié par cette PR.
  4. **Pas de retour arrière des règles** une fois ces applications en circulation (sinon retour au refus des photos) ; en cas de problème, corriger en avant.

## 8. Contrat des clients (Android et iOS ; deux PR, une par plateforme)

Les nouvelles applications **tolèrent l'absence du champ** (B13) : elles fonctionnent avant, pendant et après le déploiement ; seule l'**écriture** de `photoAt` exige que les règles soient déployées (§7).

| Où | Changement |
|---|---|
| **Lecture** | Android : `MemberStatus` gagne `photoAtMillis: Long?` (lu par `StatusObserver` : `doc.getTimestamp("photoAt")`, nul si absent ou d'un autre type) et `fromServer(...)`. iOS : `MemberStatus` gagne `photoAtMillis: Int?` (même lecture tolérante, dans `Services/Firebase/FeedFirestoreMapping.swift`). |
| **Tri** | Android : `FeedBuilder.newsDate(label, status)` retourne `status.photoAtMillis` pour toutes les étiquettes sauf `SharingStopped`, `NewsHidden`, `WaitingAgreement` (nul) ; **la branche spéciale `NoNews` (`lastUpdateAt > presenceAt`) est supprimée** ; `orderMembers` est inchangée (renommer `newsAtMillis` en `photoAtMillis`). iOS : `FeedBuilder.orderDate` identique (retirer les cas `.noNews` et `.travelEnded` spéciaux, retourner `photoAtMillis`) ; `FeedOrderEntry.lastUpdateMillis` devient `photoMillis`. La cohérence avec les alertes (`alertsFirst`) est inchangée. |
| **Écriture** | Android : `TrackingWrites.photoStatus(..., photoPublished = true)` ajoute `data["photoAt"] = FieldValue.serverTimestamp()` à côté de `lastUpdateAt` ; **`photoPublished = false` (nettoyage d'une photo expirée) n'écrit pas `photoAt`**. iOS : `UpdateWrites.photoStatus` ajoute `fields["photoAt"] = .serverTimestamp` dans le même `if photoPublished`. Aucun autre écrivain ne touche la clé (`statusUpdate`, `batteryUpdate`, `accessUpdate`, voyage, statut initial). Les tests existants qui figent les clés écrites par `photoStatus` sont mis à jour. |
| **Annonce** | Android : `feed_order_updated_one_a11y` (EN « %1$s shared a photo », FR « %1$s a publié une photo », et les langues en traduction) ; iOS : `feed_order_updated_one_a11y` dans `Localizable.xcstrings` (EN « %@ shared a photo », FR « %@ a publié une photo »). `feed_order_updated_many_a11y` inchangée. |
| **Inchangé volontairement** | Le cache de la photo (`PhotoReader` / `PhotoLoader`) et la décision d'expiration de la photo restent basés sur `lastUpdateAt` : aucun changement de coût ni de comportement. Voir le hors-scope pour une amélioration possible. |
| **Interdits** | Écrire `photoAt` avec l'heure de l'appareil ; lire `photos/{uid}` pour trier (jamais de lecture de plus) ; déduire une date de `lastUpdateAt` quand `photoAt` manque. |

## 9. Tests attendus

**Tableau partagé Android et iOS** (mêmes cas dans les deux suites, à reprendre tels quels dans les PR). Membres dans l'ordre d'arrivée : `moi, a, b, c, d`. « — » = pas de `photoAt`. Dates en heures du jour, plus grand = plus récent.

| # | Cas | Entrée | Ordre attendu |
|---|---|---|---|
| T1 | Ordre simple, moi sans date | a=10, b=12, c=11, moi=—, d=— | b, c, a, moi, d |
| T2 | Moi daté, en tête | moi=13, a=12, b=10 | moi, a, b, (c, d sans date) |
| T3 | Moi au milieu | moi=11, a=12, b=10 | a, moi, b, (c, d) |
| T4 | Égalité : ordre d'arrivée | a=12, b=12 | a, b ; même résultat si l'entrée est mélangée (100 mélanges) |
| T5 | Égalité, même ordre d'arrivée : identifiant | deux membres de même rang d'arrivée et même date | identifiant croissant |
| T6 | Personne n'a de date | aucun | moi, a, b, c, d (ordre d'arrivée) |
| T7 | Photo expirée : le rang reste | a : `hasPhoto=false`, `photoAt`=10 ; b : `photoAt`=09 ; c=— | a, b, c |
| T8 | **Update sans photo : `lastUpdateAt` ignoré** | a : `photoAt`=10, `lastUpdateAt`=14 ; b : `photoAt`=11, `lastUpdateAt`=11 | b, a |
| T9 | **Arrivée en lieu de confiance sans photo** | a : `presence=in_trusted`, `lastUpdateAt`=15, sans `photoAt` ; b : `photoAt`=09 | b, a |
| T10 | « Pas encore de nouvelles » (`presence=unknown`) avec `photoAt` | a : `unknown`, `photoAt`=12 ; b=10 | a, b |
| T11 | « Pas encore de nouvelles » sans `photoAt`, même avec `lastUpdateAt > presenceAt` | a : `unknown`, `lastUpdateAt` > `presenceAt`, sans `photoAt` ; b=10 | b, a |
| T12 | Étiquettes sans date même avec une `photoAt` restée | a : « Partage désactivé » avec `photoAt`=14 ; b : « Informations masquées » avec `photoAt`=13 ; c : en attente de mon accord ; d=09 | d, puis a, b, c dans l'ordre d'arrivée |
| T13 | Membre sans document `status` | a sans statut ; b=10 | b, a |
| T14 | Valeur illisible | a : `photoAt` nul ou d'un autre type ; b=10 | b, a ; aucun plantage |
| T15 | Un seul membre | moi | moi |
| T16 | Voyage : `photoAt` conservée | a : `presence=travel`, `photoAt`=12 ; b=10 | a, b |
| T17 | Troncature à la milliseconde | deux dates qui ne diffèrent que par les microsecondes | égalité (ordre d'arrivée), identique sur les deux plateformes |
| T18 | Carte en alerte en cours | c en alerte, `photoAt`=— ; a=12 | c, a, … (alerte d'abord) |
| T19 | Photo publiée par moi en attente du serveur (date estimée « maintenant ») | moi=maintenant ; a=12 | moi, a ; puis la valeur du serveur : même ordre |
| T20 | **Écriture** : le message de publication d'une photo contient `photoAt` = `serverTimestamp`, `hasPhoto=true`, `lastUpdateAt` ; le nettoyage (`hasPhoto=false`), la batterie, la géofence, le voyage, le statut initial **n'en contiennent pas** | — | — |

- **Tests unitaires** : T1 à T20 sur la fonction d'ordre et sur les écritures (`TrackingWrites` / `UpdateWrites`). Les anciens tests de FEED-ORDRE qui reposent sur `lastUpdateAt` ou sur l'exception `NoNews` sont **réécrits** (pas supprimés sans remplacement).
- **Tests d'interface** : inchangés par rapport à FEED-ORDRE (réordonnancement différé pendant un défilement et un appui, retour sur l'onglet, « réduire les animations », mise à jour de contenu immédiate, appui sans changement de cible) ; le déclencheur devient « un proche publie une photo ».
- **Accessibilité** : TalkBack / VoiceOver, focus conservé, annonce « a publié une photo » seulement pour la carte qui arrive en tête, jamais pour la mienne.
- **Règles Firestore** : fait dans cette PR (plan §31).
- **Recette sur appareils** (iPhone 11 Pro, OPPO Find X5 Lite) : `docs/qa/checklists/feed-ordre-android-recette-pm.md` et `feed-ordre-ios-recette-pm.md`, réécrites pour la photo. **Prérequis : règles déployées, puis chaque compte de test publie d'abord une photo** (migration, §6).
- **Régression** : bandeau, alertes, sélecteur de Miff, états neutres, boutons Message / Appeler / Localiser, publication d'une photo (lot photo + statut accepté par les règles déployées).

## 10. Découpage et ordre de livraison

| Lot | Contenu | Branche / PR |
|---|---|---|
| **0. Docs + backend** | Ce ticket, le bandeau de l'ancien, les recettes, **les règles, les tests de règles, `data-model.md`** | `docs/feed-ordre-photo` (cette PR) |
| **PM** | **Déploiement des règles** (§7) | par le PM, après la fusion du lot 0 |
| **1. Android** | Lecture de `photoAt`, tri, écriture à la publication, annonce, tests T1–T20 | `android/feed-ordre-photo`, une PR ; **ne pas installer l'APK avant le déploiement des règles** |
| **2. iOS** | Même comportement, mêmes tests | `ios/feed-ordre-photo`, une PR (CI cloud, TestFlight) |

Process habituel : jamais de push direct sur `main`, revue en contexte neuf + revue sécurité, QA, recette PM, fusion après le « oui » du PM. **Maquettes** : aucun écran nouveau ni modifié (seul le mouvement des cartes change) ; aucun export Figma à demander.

## 11. Hors-scope

Tri par urgence ; filtres ou choix de l'ordre par l'utilisateur ; épinglage ; groupes « récents / anciens » avec séparateurs ; changement de l'ordre des alertes ; toute notification ; tout déploiement par un agent ; migration ou rétro-calcul des dates existantes. **Suites possibles, non décidées** : (a) baser le cache et l'expiration de la photo sur `photoAt` plutôt que `lastUpdateAt` (économiserait des lectures de photos quand une arrivée en lieu change `lastUpdateAt` sans nouvelle photo : à étudier dans un ticket à part) ; (b) afficher sur la carte « photo il y a X » (voir Question 2).

## 12. Questions ouvertes pour le PM (aucune ne bloque le démarrage ; les défauts s'appliquent)

1. **Une photo ancienne garde-t-elle vraiment son rang ?** Défaut : oui. Une carte dont la dernière photo date de plusieurs jours reste **au-dessus** d'une carte sans photo (ou avec une photo plus ancienne), même si cette dernière vient d'arriver dans un lieu de confiance. C'est la conséquence de « uniquement selon la photo ». Alternative : classer aussi, après les cartes qui ont une photo **de moins de 24 h**, les autres selon `lastUpdateAt` (deux niveaux) ; elle réintroduirait l'arrivée automatique dans le tri, donc à ne choisir que si tu la préfères.
2. **Faut-il rendre le tri lisible sur les cartes ?** Aujourd'hui la carte affiche son heure de dernière nouvelle (arrivée, départ ou photo) et la vignette affiche l'heure de la photo : l'ordre suit la **photo**, pas l'heure affichée en haut de la carte, il peut donc paraître « pas chronologique ». Défaut : aucun changement de maquette. Si ça te gêne, on ajoute « photo il y a X » ou une légende discrète (maquette à demander).
3. **Au déploiement, tout le monde passe « sans date »** (aucun statut n'a `photoAt`) : le Feed revient à l'ordre d'arrivée jusqu'à ce que chacun publie une photo. Défaut : acceptable (peu de testeurs, pas de migration). Dis-le si tu préfères qu'on amorce les dates autrement (pas recommandé : cela reviendrait à deviner).

*Ambiguïtés non tranchables par défaut : aucune. Les vraies réserves sont la Question 1 (effet d'une photo ancienne) et l'obligation de déployer les règles **avant** les applications (§7).*
