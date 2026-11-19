# Ticket : Feed, ordre des cartes par date de dernière update (FEED-ORDRE)

**Statut** : brouillon, rédigé le 2026-11-17 (agent `product-specs`). Le PM a délégué la validation des specs (« pas besoin de mon go pour valider les specs, commence le dev dès que les specs sont prêtes ») : les décisions par défaut du §3 s'appliquent tant que le PM ne les conteste pas. Aucun statut Notion modifié, aucune règle Firestore modifiée, rien de déployé.
**Demande du PM (2026-11-17)** : « je veux désormais que l'ordre du feed soit par ordre antéchronologique de date de dernière update publiée (du plus récent au plus ancien), sur Android et iOS ».
**Remplace** : dans `docs/tickets/feed-profil-reglages.md`, §6.3, les deux mentions [Proposé] « Ordre : stable (moi, puis ordre d'arrivée) » et « Ma propre carte … en premier de liste ». Le reste de §6.3 (contenu des cartes, boutons) est inchangé.
**Sources lues** : `CLAUDE.md` ; `docs/tickets/feed-profil-reglages.md` (§6.2, §6.3, §6.4, C6, R8) ; `backend/data-model.md` (§2.7, §2.8, §3, §8, §11) ; `backend/firestore.rules` (`validStatus`, `validPhoto`) ; `docs/specs/update-et-escalade-statut.md`.
**Limite de cette lecture** : la copie locale de `main` est en retard sur `origin/main` et je n'avais pas de shell pour la mettre à jour. **Je n'ai donc pas pu lire le code du Feed** (Android : seuls des fichiers compilés d'un ancien build, qui montrent une fonction `FeedBuilder.orderMembers` ; iOS : rien de lisible). Les devs confirment en début de PR où l'ordre est calculé aujourd'hui et comment les cartes se mettent à jour (écouteur temps réel de `status`, cf. C6 : « aucun polling », R8).

---

## 1. Contexte et valeur

Aujourd'hui l'ordre est figé (moi, puis ordre d'arrivée dans la Miff), choisi pour ne pas déplacer les boutons sous le doigt. Le PM veut que le Feed raconte « qui a donné des nouvelles en dernier » : le proche qui vient de rassurer remonte en tête. Le principe de §6.3 reste vrai (pas de saut de liste pendant que la personne lit ou touche) : on le garde, c'est la règle de comportement en direct du §3 #6.

Ce que le tri **n'est pas** : un tri par urgence. Une carte orange ou rouge ne remonte pas parce qu'elle est orange ou rouge ; le bandeau d'alertes (§6.2, « plus ancien silence d'abord ») est inchangé.

## 2. Ce que le modèle de données permet (constat, à lire avant le §3)

| Donnée existante | Ce que c'est | Utilisable pour le tri ? |
|---|---|---|
| `status/{uid}.lastUpdateAt` | « Dernier signal tout va bien » (`data-model.md` §2.7). Écrit par une **update manuelle**, par une **entrée en lieu de confiance** (§8) **et** à la création du premier statut, à l'activation du partage (valeur = heure serveur). Survit à l'expiration de la photo. Bornée par les règles à −24 h / +5 min par rapport au serveur (heure d'appareil possible). | **Oui, retenue par défaut.** Aucune lecture en plus, aucun champ nouveau, aucune règle à changer. |
| `photos/{uid}.createdAt` | Heure serveur de la photo. Mais : la photo est **facultative** (décidé), elle est **illisible après 24 h**, supprimée à la révocation, et se lit par un `get` par membre (lectures en plus sur Spark). | **Non** : après 24 h tout le monde serait « sans update », et une update sans photo serait ignorée. |
| `status/{uid}.updatedAt` | Heure serveur de **n'importe quelle** écriture de statut (batterie, permission, géofence). | **Non** : ce n'est pas une update publiée. |

**Conséquence à dire clairement au PM** : le modèle **ne permet pas** de distinguer « update publiée via l'écran Update » de « arrivée dans un lieu de confiance détectée automatiquement » : les deux écrivent `lastUpdateAt`. La décision #1 ci-dessous est donc plus large que la demande du PM (voir Question 1 pour l'alternative stricte, qui exige un nouveau champ).

## 3. Décisions par défaut (à contester)

| # | Sujet | Décision par défaut | Pourquoi / limite |
|---|---|---|---|
| 1 | Qu'est-ce qu'une « update publiée » ? | La valeur `status.lastUpdateAt` : update manuelle (avec ou sans photo, avec ou sans texte) **et** entrée en lieu de confiance. **Pas** la seule photo. | Seule donnée existante qui survit à l'expiration de la photo et ne coûte aucune lecture. Écart avec la demande : une arrivée automatique à la maison fait aussi remonter la carte. Alternative stricte = Question 1. |
| 2 | Ma propre carte | Triée comme les autres (plus de « moi en premier »). Elle garde son contenu propre (pas de Message / Appeler, état de mon partage). | Demande du PM. Si je n'ai pas encore d'update, ma carte suit la règle #3. |
| 3 | Membres sans date exploitable | Placés **après tous les autres**, dans l'ordre stable d'arrivée dans la Miff. « Sans date exploitable » = aucun document `status` lisible (partage désactivé, statut pas encore publié, informations masquées) **ou** carte affichée « Pas encore de nouvelles » (§6.4, `presence = "unknown"`) **sauf** si `lastUpdateAt` est **strictement postérieur** à `presenceAt` : le premier statut écrit les deux à la même heure serveur, donc un `lastUpdateAt` plus tardif ne peut être qu'une update manuelle faite avant tout départ d'un lieu ; la carte compte alors comme datée et remonte avec sa date. À égalité (partage tout juste activé), elle reste en fin. **Le libellé « Pas encore de nouvelles » de cette carte reste inchangé** (question 4, au PM). | À l'activation du partage, `lastUpdateAt` est rempli avec l'heure d'activation : le champ seul ne dit pas « jamais d'update ». La comparaison `lastUpdateAt > presenceAt` lève l'ambiguïté sans nouveau champ ni lecture. Décision par défaut ajoutée après la revue indépendante de la PR Android #258 (cas signalé par ce ticket). |
| 4 | Égalité de dates | Ordre stable : ordre d'arrivée dans la Miff, puis, à défaut, identifiant du membre (ordre toujours déterministe, identique d'un lancement à l'autre). | Évite qu'une liste « clignote » entre deux rafraîchissements. |
| 5 | Photo expirée | **Sans effet sur l'ordre.** La date vient de `lastUpdateAt`, qui reste après l'expiration (24 h) ; seule la vignette de la photo disparaît de la carte. | Voir §2 : c'est la raison du choix de `lastUpdateAt` plutôt que de `photos.createdAt`. |
| 6 | Comportement en direct | Le **contenu** d'une carte se met à jour tout de suite. Le **changement d'ordre** est mis en attente tant que la personne fait défiler la liste ou a le doigt posé (y compris sur un bouton) ; il est appliqué dès que le geste est terminé, avec une animation douce (la carte glisse vers sa nouvelle place ; **sans animation** si « réduire les animations » est actif, ou si la carte n'est pas visible). Au retour sur l'onglet Feed (depuis Update, un autre onglet, l'arrière-plan) ou au rafraîchissement manuel, l'ordre à jour est appliqué directement à l'affichage, sans attendre. S'il y a plusieurs changements en attente, un seul réordonnancement final (pas de cascade). | Solution la plus simple qui respecte « ne pas déplacer les boutons sous le doigt ». Chaque carte garde son identité (le membre) pendant le déplacement, pour qu'un appui en cours n'atteigne jamais la carte d'un autre. |
| 7 | Accessibilité | Le réordonnancement **ne déplace pas le focus** du lecteur d'écran : si le focus est sur la carte d'un proche, il y reste, même si la carte bouge. Annonce **non interruptive** (TalkBack / VoiceOver), une seule par application d'ordre, seulement quand l'ordre change réellement : FR « {Prénom} a donné des nouvelles » (EN « {Name} shared an update »), **seulement si ce proche arrive en tête de liste**, sinon « Le Feed a été mis à jour » (plusieurs cartes bougent, ou une carte monte sans arriver en tête) ; **aucune annonce** pour ma propre carte ni au retour sur l'onglet. L'ordre de lecture suit l'ordre visuel. | Ton rassurant, pas d'effet de surprise pour une personne qui navigue au lecteur d'écran. Textes à passer dans les chaînes FR/EN existantes (repli EN). |
| 8 | Règles Firestore, backend | **Aucun changement.** Le tri est fait sur l'appareil, à partir des documents `status` déjà lus. Aucune lecture supplémentaire, aucun déploiement. | Règle d'or coûts et sécurité. Si un dev démontre le contraire, il s'arrête et le signale avant de coder. |
| 9 | Heure utilisée pour comparer | Seules les valeurs stockées sont comparées entre elles ; l'horloge du téléphone de **celui qui regarde** n'intervient jamais dans le tri. | Les décalages de l'horloge du lecteur n'ont donc aucun effet. Reste la dérive possible de l'horloge de **celui qui écrit** (bornée à −24 h / +5 min par les règles) : voir cas limites. |

## 4. User stories

- En tant que membre d'une Miff, je veux voir en premier le proche qui a donné des nouvelles le plus récemment, afin de savoir d'un coup d'œil qui vient de me rassurer.
- En tant que membre, je veux que la liste ne bouge pas pendant que je la lis ou que j'appuie sur « Message » ou « Appeler », afin de ne jamais toucher la mauvaise personne.
- En tant que membre qui utilise un lecteur d'écran, je veux être informé sans surprise quand l'ordre change, afin de garder mes repères.

## 5. Critères d'acceptation (identiques Android et iOS)

- [ ] **A1** Les cartes sont triées de la date `lastUpdateAt` la plus récente à la plus ancienne.
- [ ] **A2** Ma carte est triée comme les autres ; elle n'est plus forcée en première position.
- [ ] **A3** Les membres sans date exploitable (§3 #3) sont après tous les autres, dans l'ordre d'arrivée dans la Miff.
- [ ] **A4** À dates égales, l'ordre est stable et reproductible (§3 #4).
- [ ] **A5** Quand une photo expire, l'ordre ne change pas (§3 #5).
- [ ] **A6** Quand un proche publie une update, le contenu de sa carte est mis à jour aussitôt ; sa carte rejoint la tête de liste avec une animation douce, sans animation si « réduire les animations » est actif.
- [ ] **A7** Pendant un défilement ou un appui (doigt posé), aucune carte ne change de place ; l'ordre est appliqué à la fin du geste. Un appui en cours sur « Message » / « Appeler » atteint toujours le bon proche.
- [ ] **A8** Au retour sur le Feed et au rafraîchissement manuel, l'ordre affiché est l'ordre à jour, sans attendre de geste.
- [ ] **A9** Lecteur d'écran : pas de saut de focus ; annonce non interruptive conforme au §3 #7 ; ordre de lecture = ordre visuel.
- [ ] **A10** Le bandeau de synthèse et les alertes (§6.2) sont inchangés ; le sélecteur de Miff (Premium) et les états neutres (§6.4) sont inchangés.
- [ ] **A11** Aucun changement de règles Firestore, aucune lecture Firestore supplémentaire par rapport à avant (à vérifier dans la PR).
- [ ] **A12** Textes FR et EN (repli EN) pour les annonces ; ton non anxiogène.

## 6. Cas limites

- **Un seul membre (moi)** : une seule carte, aucun tri visible, aucune annonce, état « Tu es seul(e) dans cette Miff pour l'instant » inchangé.
- **Tous sans update** : aucun ne remonte ; ordre d'arrivée dans la Miff, comme avant (moi n'est plus forcé en tête).
- **Horloges décalées** : le tri compare les dates stockées entre elles ; l'horloge de celui qui lit n'a aucun effet. Une update écrite par un téléphone dont l'heure est décalée (ou une écriture faite hors ligne puis envoyée plus tard, bornée à 24 h dans le passé par les règles) peut se classer de quelques minutes, au pire quelques heures, à côté de sa place réelle. Acceptable en MVP. **À examiner par les devs** : si l'écriture manuelle de `lastUpdateAt` utilise l'heure de l'appareil, utiliser l'heure serveur (déjà acceptée par les règles) ; sinon laisser tel quel et le noter dans la PR.
- **Partage désactivé ou révoqué par un proche** : son `status` est supprimé, sa carte passe dans le groupe « sans date » (§3 #3) avec son badge « Partage désactivé ».
- **Un membre quitte ou est retiré** : sa carte disparaît ; pas d'annonce de réordonnancement pour cela.
- **Membre qui active son partage** : son premier statut porte la date d'activation (`lastUpdateAt` = `presenceAt`) et sa carte affiche « Pas encore de nouvelles » : elle reste **après** les autres (§3 #3) jusqu'à un vrai signal. **Update manuelle avant tout départ de géofence** (`presence` encore « unknown », constaté dans le code Android) : `lastUpdateAt` devient postérieur à `presenceAt`, la carte **remonte** avec sa date ; son libellé reste « Pas encore de nouvelles » tant qu'aucun départ n'a été détecté (question 4). Une fin de voyage, qui conserve `lastUpdateAt`, ne compte pas comme une update.
- **Hors connexion** : l'ordre est celui des dernières données connues (états conservés, datés, §6.4) ; aucun réordonnancement à la reconnexion pendant un geste.
- **Beaucoup d'updates d'affilée** : un seul réordonnancement à la fin du geste, pas de cascade.
- **Plusieurs Miffs (Premium)** : le tri s'applique à la Miff affichée ; changer de Miff réaffiche l'ordre à jour, sans animation.
- **Ma propre update depuis l'écran Update** : au retour sur le Feed, ma carte est déjà à sa place (§3 #6, retour sur l'onglet), sans annonce.

## 7. Tests attendus

- **Tests unitaires de la fonction d'ordre (mêmes cas sur Android et iOS, tableau partagé dans la PR)** : A1 à A5 ; égalité ; dates nulles ; membre sans `status` ; `presence = "unknown"` avec date ; un seul membre ; ma carte au milieu ; ordre déterministe sur 100 exécutions mélangées.
- **Tests d'interface** : réordonnancement différé pendant un défilement et pendant un appui, appliqué à la fin ; retour sur l'onglet applique l'ordre direct ; « réduire les animations » sans animation ; mise à jour de contenu immédiate sans changement d'ordre ; un appui en cours ne change pas de cible.
- **Accessibilité** : TalkBack (Android) et VoiceOver (iOS), focus conservé, annonce unique et non interruptive, aucune annonce pour ma carte.
- **Recette sur appareils** (iPhone 11 Pro, OPPO Find X5 Lite) avec deux comptes dans la même Miff : l'un publie une update pendant que l'autre fait défiler le Feed, puis lâche.
- **Régression** : bandeau, alertes, sélecteur de Miff, états neutres, boutons Message / Appeler / Localiser.

## 8. Découpage et ordre de livraison

| Lot | Contenu | Branche / PR |
|---|---|---|
| **0. Docs** | Ce ticket | `docs/feed-ordre-derniere-update` (PR docs en brouillon) |
| **1. Android** | Fonction d'ordre + règle de comportement en direct + accessibilité + tests | `android/feed-ordre-derniere-update`, une PR |
| **2. iOS** | Même comportement, mêmes cas de test | `ios/feed-ordre-derniere-update`, une PR (compilation par la CI cloud, test sur iPhone via TestFlight) |

Process habituel : Android puis iOS, jamais de push direct sur `main`, revue en contexte neuf + revue sécurité, QA, recette PM, fusion après un « oui » du PM. **Maquettes** : aucun écran nouveau ni modifié (seul le mouvement des cartes change) ; pas d'export Figma à demander sauf si le PM souhaite un repère visuel (Question 3). Les devs vérifient en début de PR que le Feed (FEED-1, FEED-2) est bien sur `main` pour leur plateforme.

## 9. Hors-scope

Tri par urgence ; filtres ou choix de l'ordre par l'utilisateur ; épinglage d'un proche ; groupes « récents / anciens » avec séparateurs ; changement de l'ordre des alertes du bandeau ; nouveau champ ou nouvelle règle Firestore ; toute notification ; tout déploiement.

## 10. Questions ouvertes pour le PM (aucune ne bloque le démarrage)

1. **« Update publiée » = photo (ta demande) ou tout signal « tout va bien » (défaut) ?** Le modèle ne sait pas distinguer l'update manuelle de l'arrivée automatique dans un lieu de confiance, ni « jamais d'update » de « partage tout juste activé ». Pour coller exactement à ta demande, il faudrait un **nouveau champ** (par exemple une date « dernière update manuelle » dans le statut) : changement des règles Firestore, **déploiement fait par toi**, écriture dans les deux apps, et les anciennes versions de l'app ne le renseigneraient pas. *Recommandation : défaut maintenant (rien à déployer) ; on ajoute le champ seulement si, à l'usage, une arrivée automatique qui fait remonter une carte te gêne.*
2. **Une arrivée automatique à la maison fait-elle remonter la carte du proche ?** C'est la conséquence directe de la Question 1 : avec le défaut, oui. Si c'est gênant, c'est le déclencheur pour ajouter le champ.
3. **Veux-tu une indication visible que l'ordre suit « les dernières nouvelles »** (par exemple une légende discrète sous le sélecteur) ? Défaut : non, le repère de temps de chaque carte suffit ; aucune maquette demandée.
4. **Une carte qui a publié une update avant tout départ d'un lieu doit-elle dire « Pas encore de nouvelles » ?** Aujourd'hui elle le dit (règle de couleur N4 : tant qu'aucun lieu de confiance n'a été quitté, la présence est inconnue), tout en remontant dans l'ordre grâce à son update. Changer le libellé (par exemple afficher l'heure de la dernière update) touche la règle de couleur et le texte des cartes sur Android et iOS : hors périmètre de ce ticket, à décider par le PM.

*Ambiguïtés non tranchables par défaut : aucune. Les vraies réserves sont la Question 1 (écart entre ta demande et ce que le modèle permet aujourd'hui) et la Question 4 (libellé de la carte d'un proche qui a publié avant tout départ).*
