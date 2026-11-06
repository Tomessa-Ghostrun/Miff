# Recette iOS de LOC-4b (suivi des lieux : arrivées, départs, statut, dernière position) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec **deux comptes de test** : un compte **A** (créateur d'une Miff) et un compte **B** (membre de cette Miff), idéalement sur deux téléphones (l'OPPO fait très bien le second) ; à défaut, un seul téléphone et la console Firebase.
**Durée** : environ 1 h 30 en deux temps : **40 min à la maison, sans bouger** (parties A et I), puis **une sortie à pied d'environ 45 min** (parties B à E ; le redémarrage et le mode avion se font en rentrant).
**Principe** : le Feed iOS n'existe pas encore. Ce qu'on contrôle, c'est **ce qui est écrit dans la console Firebase** (lecture seule) : `miffs/{id}/status/{uid}` et `miffs/{id}/positions/{uid}`, plus le **journal de détection** de l'app (voir ci-dessous). Les notifications ne sont pas envoyées (plan gratuit).
**Build à installer** : le build **Internal** de la PR, par TestFlight (lancé par l'assistant après la revue). Il contient le journal ; le build de publication n'en a pas.

## Ce qui change pour vous

- Une fois que vous avez accepté le texte de consentement (version 3), donné **« Toujours » + la position exacte** et que vous **partagez** avec une Miff, l'app repère toute seule vos **arrivées** et vos **départs** des lieux de la Miff, **même app fermée**, et écrit votre **statut** et votre **dernière position connue** (une seule, sans historique).
- **Rien** n'est détecté ni écrit tant que l'accord (v3) n'est pas donné **et** que « Toujours » + la position exacte ne sont pas accordés.
- Rien ne change à l'écran (pas de Feed sur iPhone) : tout se voit dans la console et dans le journal.

## Lire le journal de détection (sans Mac)

Dans l'app (build Internal) : **appui long de 1,5 s en haut à gauche de l'écran** (sur l'heure) → la feuille de diagnostic s'ouvre → faites défiler jusqu'à la carte **« Journal de détection »** (boutons « Actualiser » et « Effacer le journal »). Chaque ligne : heure, type d'événement, identifiant du lieu (`p1`, `p2`…), batterie. **Jamais** de position, d'adresse ni de nom. Les types utiles : `armed` (zones enregistrées), `regionInside` / `regionOutside` (le système dit si vous êtes dedans), `transitionEnter` / `transitionExit`, `positionRead`, `statusWritten` / `statusWaiting` / `statusRefused` / `statusGivenUp`, `positionWritten` / `positionSkippedGuard`, `breakerStatus` / `breakerPosition`, `presenceUnknown`, `notEligible`, `disarmed`, `wakeSignificantMove`, `wakeNetwork`, `backgroundRefreshOff`, `lowPowerMode`.

## Règles à retenir

- Un défaut : capture d'écran (et de la console) + numéro de l'étape, puis étape suivante.
- **Notez l'heure réelle** de chaque arrivée et de chaque départ (montre) : on la compare à `presenceAt` dans la console. **iOS ne garantit aucun délai** : la surveillance de zones se déclenche en général après 100 à 200 m et quelques dizaines de secondes, parfois plus ; notez ce que vous voyez.
- **Avant de commencer** : vérifiez avec l'assistant que les règles Firestore de la position, de la session (temps 1) et de R-LIEU-1 sont bien déployées (elles le sont depuis le 2026-11-06), sinon les écritures seront refusées.
- Réglages d'iOS à noter (partie I) : **Actualisation en arrière-plan** (Réglages > Général), **mode économie d'énergie**, et le fait de **balayer** l'app dans le sélecteur d'apps.

## Préparation (15 min, à la maison)

1. Installez le build Internal par TestFlight. Si une version précédente est installée, **supprimez-la d'abord** (l'identifiant de session est effacé avec l'app).
2. Compte **A** : créez une Miff (« Créer et partager » : le texte de consentement v3). **Ne créez pas encore de lieu.** Acceptez les notifications (ou non), puis **« Toujours » + position exacte** (écran « Savoir que tu vas bien ! » ; l'iPhone montre d'abord « Autoriser pendant l'utilisation », le passage à « Toujours » se fait ensuite dans les Réglages : notez l'enchaînement exact).
3. Compte **B** : rejoignez la Miff avec le code (« Rejoindre et partager »), mêmes autorisations.
4. Console Firebase > Firestore : ouvrez `miffs/{id}/status/{uid de A}` et `.../positions/{uid de A}`.

## Partie A : à la maison, sans bouger (25 min)

**Avant tout, relevez dans Firebase > Firestore > Utilisation le nombre de lectures du jour** (note-le : on le compare à la fin, partie C).

- [ ] **Rien avant l'accord** : avec un compte dont l'accord date de l'ancien texte (ou après « Pas maintenant » au re-consentement), ouvrez et fermez l'app : **aucun** document `positions/…`, le statut ne change pas, le journal ne montre que `notEligible`.
- [ ] **Après « J'accepte » + « Toujours »** : ouvrez l'app. Dans la console, `positions/{uid}` apparaît : `lat` et `lng` (5 décimales), `measuredAt` = maintenant, `deviceId` présent. Le statut reste `unknown` tant qu'il n'y a pas de lieu.
- [ ] **Créer un lieu marche tout de suite** : à l'enregistrement du lieu, **sans attendre une heure** ni relancer l'app, le journal note l'armement de la zone (`armed`) ; de même pour un lieu déplacé, agrandi ou supprimé (tout est pris en compte à l'enregistrement). **Si rien ne se passe sur un lieu neuf** (vous êtes dedans et aucune arrivée n'est écrite), notez-le tout de suite : c'est le risque connu que l'état « dedans / dehors » d'une région tout juste créée ne soit pas obtenu du système (l'app garde la région qu'elle vient de créer pour le lui demander, mais seule l'essai sur iPhone le prouve).
- [ ] **Arrivée « déjà dedans »** : compte A, créez un **lieu de confiance autour de chez vous** (rayon 100 m, catégorie Maison). Ouvrez puis fermez l'app : `status/{uid}` passe à `presence` = `in_trusted`, `placeId` = `p1`, `presenceAt` et `lastUpdateAt` = maintenant. **Aucune coordonnée** dans le statut. Journal : `armed`, `regionInside`, `transitionEnter`, `statusWritten`.
- [ ] `updateText` et `hasPhoto` sont inchangés. `locationAccess` = `background`, `batteryPct` plausible.
- [ ] **Lieu déplacé pendant que vous y êtes** : modifiez le lieu pour l'envoyer à une adresse lointaine, ouvrez l'app : le statut devient `unknown` (neutre, `placeId` vide), jamais `out`. Journal : `presenceUnknown`. Remettez la bonne adresse : `in_trusted` de nouveau.
- [ ] **Lieu supprimé** pendant que vous y êtes : `unknown` aussi.
- [ ] **Zone de danger** (si Premium) : ajoutez une zone de danger qui entoure chez vous **et** gardez la zone de confiance : `in_trusted` (la confiance l'emporte). Supprimez la zone de confiance : `in_risk`.
- [ ] **Garde-fou** : ouvrez et fermez l'app 5 fois en 5 minutes : au plus **une** écriture de position (journal : `positionSkippedGuard`). La console ne montre qu'**un** document `positions/{uid}` (pas d'historique).
- [ ] **Arrêter de partager** (écran de la Miff) : `status` et `positions` **disparaissent** ; ouvrez/fermez l'app : journal `notEligible`, rien n'est réécrit. **Reprendre** : texte v3, les documents reviennent.
- [ ] **Autorisation retirée** : Réglages d'iOS > Miff > Position : « Pendant l'utilisation ». Revenez dans l'app : l'écran « Toujours autoriser » **réapparaît** avant le Feed. Console : `locationAccess` = `foreground` (une fois), **plus aucune** écriture de position ; journal `disarmed`. Même chose pour **« Position exacte » éteinte** et pour le **Service de localisation** coupé (`denied`). Remettez « Toujours » : la détection reprend (ouvrez l'app une fois).
- [ ] **Feed sans localisation** (build relâché `location_blocking=no`, seulement si vous voulez le voir) : « Plus tard » laisse passer ; aucune position lue, aucune zone, seul `locationAccess` est écrit.

## Partie B : arrivée et départ, app fermée, à pied (20 min)

Le lieu de 100 m est autour de chez vous. Fermez l'app **en la balayant** une première fois (puis laissez le téléphone en poche, écran éteint).

- [ ] **Départ** : sortez et éloignez-vous à environ **400 m**. Notez l'heure de sortie du cercle et l'heure à laquelle la console change. `presence` = `out`, `placeId` = `p1`, `presenceAt` = l'heure **réelle** (à quelques minutes près), **pas** de changement de `lastUpdateAt`, `batteryPct` plausible, `locationAccess` = `background`. Journal : `transitionExit`, `statusWritten`.
- [ ] **Arrivée** : revenez chez vous. `presence` = `in_trusted`, `presenceAt` et `lastUpdateAt` = l'heure de rentrée.
- [ ] **App balayée** : refaites un départ et une arrivée **sans** avoir ouvert l'app depuis le balayage. **Notez si iOS relance l'app** (c'est l'inconnue principale : le système doit la relancer sans mode d'arrière-plan). Si rien n'est écrit, notez-le tel quel : ce n'est pas forcément un défaut du code, c'est une limite à décider (voir les questions). Si le journal montre `transitionExit` puis `statusWaiting` (ou rien d'écrit) alors que le réseau est là, notez-le aussi : c'est peut-être une écriture refusée en arrière-plan, téléphone verrouillé (App Check, protection des fichiers).
- [ ] **Un départ n'écrit jamais `unknown` avant `out`** : à chaque départ de la partie B, regardez l'historique de la console / du journal : la séquence est `in_trusted` puis **directement `out`** ; jamais un `unknown` entre les deux (le « dehors » que le système rend après la sortie n'est pas pris pour une sortie manquée).
- [ ] **Sortie manquée** : balayez l'app, éloignez-vous de 400 m **sans** que rien ne soit écrit, revenez et ouvrez l'app **dehors** : le statut devient `unknown`, **jamais** `out` (l'heure de la sortie est inconnue).
- [ ] **Compte B** (si 2 téléphones, B reste chez lui) : son statut ne change pas quand A bouge.
- [ ] **Rebonds en bordure de zone** (aucune temporisation, décision du PM) : restez longuement en bordure du cercle. Notez combien d'écritures `status` sont faites : les faux départs sont **acceptés et observés** ici.

## Partie C : déplacement significatif, en transport (10 min)

- [ ] **Relance par une simple sortie** : app balayée, sortez de la zone sans ouvrir l'app (le départ est écrit), puis marchez plusieurs centaines de mètres : le journal note aussi `wakeSignificantMove` (le suivi des déplacements est redemandé dès cette première relance, même sans passe de rafraîchissement).
- [ ] **Redémarrage du suivi après une relance** : fermez l'app en la balayant, rouvrez-la **une fois** puis refermez-la (elle redémarre le suivi des déplacements), puis marchez : le journal note `wakeSignificantMove` (le changement significatif reprend bien après une relance).
- [ ] Faites un trajet de plusieurs centaines de mètres (marche ou bus) **sans ouvrir l'app**. `positions/{uid}` est mise à jour (`measuredAt`), **un seul document**. Journal : `wakeSignificantMove`. Notez l'écart entre votre position réelle et `lat` / `lng`, et l'heure de `measuredAt` : iOS ne signale un changement significatif qu'après environ 500 m ou un changement d'antenne.
- [ ] **Relevez les lectures Firestore de la journée** (Firestore > Utilisation) et soustrayez le chiffre noté au début de la partie A : on estime une relance du système à 4 à 6 lectures + 2 à 3 lectures de règles par écriture ; le chiffre réel est reporté dans `docs/dev/surveillance-quotas.md`.
- [ ] **Le journal se remplit plus lentement qu'avant** : c'est voulu (une ouverture ordinaire ne réenregistre plus les zones et ne redemande plus leur état : ni `armed` ni `requestState` en rafale). Si un lieu n'est pas détecté alors que vous y êtes depuis plusieurs minutes, notez-le.
- [ ] **Comptez les écritures** de la journée dans Firestore > Utilisation (statuts et positions) : à comparer au quota gratuit de 20 000 écritures par jour pour tout le projet. Notez le nombre pour la décision sur le garde-fou.

## Partie D : redémarrage de l'iPhone (5 min)

- [ ] Restez **dans** le lieu, redémarrez l'iPhone **sans ouvrir Miff**, déverrouillez-le une fois, attendez 5 minutes, puis sortez à 400 m : le départ est-il écrit ? (les régions sont censées survivre au redémarrage). Notez le résultat.

## Partie E : mode avion et hors connexion (10 min)

- [ ] Passez en **mode avion**, sortez à 400 m, rentrez, **puis** coupez le mode avion chez vous, app fermée. **iOS ne réveille pas l'app au retour du réseau** : le statut attend (journal `statusWaiting`). Ouvrez l'app : il est écrit **avec l'heure réelle** de l'événement, pas celle de l'ouverture. Notez le comportement exact.
- [ ] Mode avion **plus de 24 h** (facultatif) : rien n'est écrit au retour, aucune erreur visible.

## Partie F : un compte, un seul appareil (5 min)

- [ ] Connectez le compte A sur un **second téléphone** : l'iPhone est déconnecté (message calme), et son journal est vidé. Plus aucune écriture de A ne vient de l'iPhone.

## Partie G : nettoyage (10 min)

- [ ] B **arrête de partager** : son `status` et sa `positions` disparaissent ; plus d'écriture. B **quitte** la Miff, A **retire** un membre puis **supprime** la Miff : plus aucun document, plus de zone surveillée.
- [ ] **Déconnexion** : après « Se déconnecter », marchez 400 m : rien n'est écrit. Le journal est **vide** ; reconnectez-vous : rien n'est resté de l'ancienne session.
- [ ] Deux Miffs partageant le **même lieu** (si Premium) : une arrivée écrit un statut dans **chaque** Miff ; arrêter dans l'une n'arrête pas l'autre.

## Partie H : journal et vie privée (5 min)

- [ ] Le journal ne montre que des types, des identifiants de lieu (`p1`…), des heures, la batterie. **Aucune coordonnée, aucune adresse, aucun nom.**
- [ ] Aucune notification n'arrive (normal). Réglages d'iOS > Confidentialité et sécurité > Service de localisation > Miff : « Toujours » ; l'indicateur de localisation (flèche) ne reste pas allumé en continu.

## Partie I : réglages d'iOS qui peuvent retarder la détection (à noter, 10 min)

- [ ] Réglages > Général > **Actualisation en arrière-plan** coupée (pour Miff, ou pour tout) : refaites un départ app fermée. Le journal note `backgroundRefreshOff` à l'ouverture. Qu'est-ce qui change ?
- [ ] **Mode économie d'énergie** allumé : même essai. Le journal note `lowPowerMode`.
- [ ] Notez la **consommation de batterie** de Miff sur 24 h (Réglages > Batterie) : relevé, sans seuil.

## Partie J : textes à relire à voix haute (5 min)

- [ ] Tutoiement et ton rassurant partout (les deux fenêtres du système, l'écran « Toujours autoriser », le re-consentement).
- [ ] Passez l'iPhone en **anglais** : mêmes écrans, sans mot français ; texte agrandi au maximum : lisible.

## Ce qui ne doit PAS arriver

- [ ] Aucune écriture de position avant l'accord (v3) ou sans « Toujours » + position exacte.
- [ ] Aucune coordonnée dans le `status` (seulement dans `positions`).
- [ ] Plus d'un document `positions/{uid}` par Miff et par personne ; aucun historique.
- [ ] Aucun statut `out` écrit à l'armement quand vous étiez déjà dehors, ni pour une sortie manquée.
- [ ] Aucun `unknown` écrit juste avant un `out` lors d'un départ normal.
- [ ] Aucune arrivée écrite deux fois de suite pour la même zone.
- [ ] Aucune boucle d'écritures (le disjoncteur arrête à 60 statuts / 150 positions par jour).
- [ ] Aucune écriture après la déconnexion ou depuis un téléphone remplacé.

## Questions pour vous (points ouverts de la PR)

1. **Relance de l'app terminée sans mode d'arrière-plan** : si la partie B montre qu'iOS ne relance pas Miff, faut-il déclarer le mode « location » (risque de revue Apple 2.5.4, justification dans `docs/release/ios-loc-4b-comportement-reel.md`) ou accepter la limite (détection seulement app ouverte ou récemment utilisée) ?
2. **Retour du réseau** : iOS ne réveille pas l'app ; l'état attend le prochain réveil. Acceptable en bêta ?
3. **Sortie manquée** → `unknown` (neutre) plutôt qu'`out` avec l'heure de la réouverture : à confirmer (même choix qu'Android au redémarrage).
4. **Garde-fou et disjoncteurs** (15 min ou 250 m, plancher 2 min ; 60 / 150 par jour) : valeurs à confirmer après vos mesures de la partie C.
5. **Encarts « En attente de ton accord »** (maquette 23) : non construits sur iPhone (ni Feed ni Réglages) ; à faire avec le Feed iOS ?
6. **Batterie** écrite avec le statut (et identifiant de session) : à classer dans les étiquettes d'App Store Connect, proposition « Other Data Types » (release-manager).
7. **Actualisation en arrière-plan désactivée** : aujourd'hui seulement notée dans le journal ; les proches ne le voient pas. Option recommandée : un mot calme dans le futur Feed / les Réglages iOS (« Les détections peuvent tarder sur ce téléphone »). Autre option : une valeur distincte de `locationAccess` (contrat à changer avec Android). Décision du PM.
