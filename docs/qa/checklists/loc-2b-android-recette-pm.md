# Recette Android de LOC-2b (détection des arrivées et des départs, statut, dernière position) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test** : un compte **A** (créateur d'une Miff) et un compte **B** (membre de cette Miff), idéalement sur deux téléphones ; à défaut, un seul téléphone et la console Firebase.
**Durée** : environ 1 h 30 en deux temps (15 min de préparation à la maison, puis une sortie à pied d'environ 45 min ; le redémarrage et le mode avion se font en rentrant).
**Principe** : l'écran du Feed n'affiche pas encore les statuts des proches (c'est le ticket Feed). Ce qu'on contrôle ici, c'est **ce qui est écrit dans la console Firebase** (lecture seule) : `miffs/{id}/status/{uid}` et `miffs/{id}/positions/{uid}`. Les notifications ne sont pas envoyées (plan gratuit).

## Ce qui change pour vous

- Une fois que vous avez accepté le texte de consentement (version 3), donné **« Toujours » + la précision exacte** et que vous **partagez** avec une Miff, l'app repère toute seule vos **arrivées** et vos **départs** des lieux de la Miff, **même app fermée**, et écrit votre **statut** et votre **dernière position connue** (une seule, sans historique).
- **Rien** n'est détecté ni écrit tant que l'accord (v3) n'est pas donné **et** que « Toujours » n'est pas accordé.
- Un nouvel encart **« En attente de ton accord »** apparaît dans le Feed, sur l'écran de la Miff et dans Réglages > Confidentialité si vous avez répondu « Pas maintenant » au texte de consentement.

## Règles à retenir

- Un défaut : capture d'écran (et de la console) + numéro de l'étape, puis étape suivante.
- **Notez l'heure réelle** de chaque arrivée et de chaque départ (montre) : on la compare à `presenceAt` dans la console. Un retard de **2 à 6 minutes** de la part d'Android est normal (documentation Google) ; au-delà, notez-le.
- Les intitulés des réglages de ColorOS varient : notez les vrais intitulés.
- L'APK à installer est celui de la CI (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main (la connexion Google ne marcherait pas).
- **Avant de commencer** : vérifiez avec l'assistant que les règles Firestore de la position sont bien déployées (sinon les écritures de position sont refusées et la partie C ne montrera rien).

## Préparation (15 min, à la maison)

1. Installez l'APK de debug de la PR. **Désinstallez d'abord** l'ancienne version, ou retirez la localisation et les notifications dans les réglages du téléphone.
2. Compte **A** : créez une Miff (« Créer et partager » : le texte de consentement v3), puis **créez un lieu de confiance autour de chez vous** : **rayon 100 m**, type « Zone de confiance », catégorie Maison. Optionnel mais utile : un second lieu de confiance à environ 1 km (le café, le travail) et, si vous êtes Premium, une **zone de danger** à environ 600 m de chez vous (type « Zone de danger »).
3. Compte **B** : rejoignez la Miff avec le code (« Rejoindre et partager »).
4. Sur chaque téléphone : acceptez les **notifications** (ou non), puis donnez **« Toujours » + « Position précise »** (écran « Savoir que tu vas bien ! »).
5. Réglages ColorOS (voir la partie I) : à noter, mais **ne changez rien au début** : on veut d'abord voir ce que donne l'app telle quelle (décision du PM : pas de spike, on teste puis on ajuste).
6. Ouvrez la console Firebase > Firestore > `miffs/{id}/status/{votre uid}` et `.../positions/{votre uid}`.

## Partie A — Rien avant l'accord (5 min)

- [ ] Avec un compte dont l'accord date de l'**ancien** texte (voir la recette LOC-2a, partie E) ou un compte neuf **avant** « J'accepte » : dans la console, **aucun** document `positions/…` n'apparaît pour lui et son statut ne change pas.
- [ ] Touchez « **Pas maintenant** » au re-consentement : vous arrivez sur le Feed. **Un encart « En attente de ton accord »** est affiché sous le nom de la Miff (« Miff ne met plus à jour ton statut avec « … » tant que tu n'as pas donné ton accord. »), avec le bouton **« Revoir et accepter »** et la phrase « Tu verras de nouveau les statuts de tes proches ici dès que tu auras donné ton accord. ». Aucun mot « refusé » ou « désactivé ».
- [ ] Ouvrez l'écran de la **Miff** : le même encart, et l'interrupteur « Partager mon statut » reste **utilisable** (« En attente de ton accord : ton statut n'est plus mis à jour. Tu peux arrêter de partager. ») : le texte dit que le dernier statut reste visible des membres tant que vous n'avez pas arrêté de partager. Créateur : pas de « Tu peux aussi la quitter ».
- [ ] Réglages > Confidentialité > Localisation : l'état de l'autorisation (« Toujours autorisée » ou « À compléter… »), la phrase qui dit à quoi sert la localisation, et **une ligne « En attente » par Miff** avec « Revoir et accepter ».
- [ ] Marchez dehors 10 minutes dans cet état (ou laissez le téléphone) : **aucune** icône de localisation en barre d'état venant de Miff, aucune écriture dans la console.
- [ ] Dans cet état, **arrêtez de partager** avec l'interrupteur : le statut disparaît de la console (le dialogue habituel).
- [ ] Touchez « **Revoir et accepter** » puis « J'accepte » : l'encart disparaît ; la console montre `consentVersion` = `2026-11-v03`.

## Partie B — Arrivée et départ, app fermée, à pied (20 min)

Le lieu de 100 m est autour de chez vous. Fermez l'app **en la balayant** une fois (puis laissez le téléphone en poche, écran éteint).

- [ ] **Départ** : sortez et éloignez-vous à environ **400 m**. Notez l'heure de sortie du cercle (environ 100 m) et l'heure à laquelle la console change. Dans `status/{uid}` : `presence` = `out`, `placeId` = l'identifiant du lieu, `presenceAt` = l'heure **réelle** de la sortie (à quelques minutes près), `batteryPct` plausible, `locationAccess` = `background`, `updatedAt` récent. **Aucun** champ ne contient de coordonnées.
- [ ] `updateText` et `hasPhoto` sont inchangés (vides / faux si vous n'avez rien publié).
- [ ] **Arrivée** : revenez chez vous. `presence` = `in_trusted`, `placeId` = le lieu, `presenceAt` et `lastUpdateAt` = l'heure de rentrée.
- [ ] **Compte B** (si 2 téléphones, B reste chez lui ou ailleurs) : son statut ne change pas quand A bouge.
- [ ] Refaites **départ puis arrivée** une seconde fois en laissant l'app ouverte au premier plan, puis une troisième fois **après avoir forcé l'arrêt** de l'app (Réglages > Applications > Miff > Forcer l'arrêt). Notez ce qui se passe : un « Forcer l'arrêt » désarme les zones jusqu'à la prochaine ouverture (limite d'Android) ; **ce n'est pas un défaut de l'app**, mais c'est à constater.
- [ ] **Zone de danger** (si créée) : en entrant dans la zone sans être dans une zone de confiance, `presence` = `in_risk`. Si une zone de confiance et une zone de danger **se chevauchent**, la zone de confiance l'emporte (`in_trusted`).
- [ ] **Rebonds en bordure de zone** (pas de temporisation, décision du PM) : restez longuement en bordure du cercle. Notez combien d'écritures `status` ont été faites : les faux départs sont **acceptés et observés** ici.

## Partie C — Déplacement de plus de 250 m, en transport (10 min)

- [ ] Faites un trajet de plus de 250 m (marche ou bus) **sans ouvrir l'app**. Dans `positions/{uid}` : `lat` et `lng` (5 décimales), `measuredAt` = l'heure de la mesure. **Il n'y a qu'un document** (pas d'historique) ; il est remplacé à chaque écriture.
- [ ] La position écrite est la dernière connue : notez l'écart avec votre position réelle et l'heure de `measuredAt`.
- [ ] Ouvrez l'app, puis fermez-la : `measuredAt` se met à jour **au plus toutes les 15 minutes** sauf si vous avez bougé d'au moins 250 m depuis la dernière écriture (garde-fou). Dans le journal (voir H), des lignes `POSITION_SKIPPED_GUARD` apparaissent.
- [ ] **Comptez les écritures** de la journée dans Firestore > Utilisation (part des écritures de `positions`) : à comparer au quota gratuit de 20 000 écritures par jour pour tout le projet. Notez le nombre pour la décision sur le garde-fou.

## Partie D — Redémarrage du téléphone (5 min)

- [ ] Restez **dans** le lieu de 100 m, redémarrez le téléphone **sans ouvrir Miff**. Attendez 5 minutes. Éteignez et rallumez l'écran. Puis sortez à 400 m : la console montre bien le **départ** (les zones ont été réarmées au démarrage, sans ouvrir l'app).
- [ ] Redémarrez le téléphone **hors** du lieu (en rue), rentrez : l'**arrivée** est écrite.
- [ ] Après un redémarrage **hors zone**, le statut peut devenir `unknown` (la personne ne peut pas être reconfirmée : décision de prudence, point ouvert) : notez ce que vous voyez.

## Partie E — Mode avion et hors connexion (10 min)

- [ ] Passez en **mode avion**, sortez à 400 m, rentrez, **puis** coupez le mode avion en étant chez vous. Dans la console : le **dernier état** (l'arrivée) est écrit **dès le retour du réseau**, avec l'heure **réelle** (`presenceAt` = l'heure de rentrée, pas celle du retour du réseau).
- [ ] Sortez en mode avion, **restez dehors**, remettez le réseau : `out` est écrit avec l'heure réelle de la sortie.
- [ ] Mode avion **plus de 24 h** (si vous voulez) : rien n'est écrit au retour, aucune erreur visible.

## Partie F — Refus d'autorisation (5 min)

- [ ] Réglages du téléphone > Applications > Miff > Autorisations > Position : choisissez « Seulement pendant l'utilisation ». Revenez dans l'app : l'écran « Savoir que tu vas bien ! » **réapparaît** avant le Feed. Dans la console, `locationAccess` devient `foreground` (une fois), et **plus aucune** écriture de position ni de zone.
- [ ] Coupez la **localisation du téléphone** (volet rapide) : `locationAccess` = `denied`.
- [ ] Remettez « Toujours » : les arrivées et départs sont de nouveau détectés (ouvrez l'app une fois).

## Partie G — Deux comptes et nettoyage (10 min)

- [ ] Compte B **arrête de partager** (interrupteur) : dans la console, son `status` et sa `positions` **disparaissent**, et plus aucune écriture n'est faite pour cette Miff (marchez 400 m pour le vérifier).
- [ ] B **reprend** le partage : il redonne son accord (texte v3), les arrivées et départs reviennent.
- [ ] B **quitte** la Miff : plus aucun document pour B. A **retire** un membre puis **supprime** la Miff : plus aucune écriture, plus de zone surveillée.
- [ ] **Déconnexion** : après « Se déconnecter », marchez 400 m : rien n'est écrit. Reconnectez-vous : rien n'est resté de l'ancienne session (pas de position).
- [ ] Deux Miffs partageant le **même lieu** (si Premium) : une arrivée écrit un statut dans **chaque** Miff où vous partagez ; arrêter dans l'une n'arrête pas l'autre.

## Partie H — Journal et vie privée (5 min)

- [ ] Le journal de détection (build debug) : `adb shell run-as com.tomessa.miff cat files/detection-journal.txt` (l'assistant peut le lire avec vous). Chaque ligne : heure, type d'événement, identifiant du lieu, batterie. **Aucune coordonnée, aucune adresse, aucun nom.**
- [ ] Le journal est **vidé** à la déconnexion.
- [ ] Aucune notification n'arrive (normal). Aucune capture de l'écran de l'app dans les applications récentes.

## Partie I — ColorOS et autres téléphones (à noter, 10 min)

Si des détections sont **tardives ou manquantes**, vérifiez dans cet ordre et notez ce qui change quelque chose :
- [ ] Réglages > Applications > Miff > **Batterie** : « Aucune restriction » ou « Autoriser l'activité en arrière-plan ».
- [ ] Réglages > Applications > Miff > **Démarrage automatique** : activé.
- [ ] Réglages > Batterie > **Économiseur de batterie** : désactivé pendant les essais.
- [ ] Dans l'app : Réglages > Confidentialité > **« Si les détections tardent »** : l'aide est lisible, facultative, non bloquante ; le bouton ouvre bien les réglages de Miff. Les étapes correspondent-elles à ce que vous voyez sur l'OPPO ? (Notez les vrais intitulés.)

## Partie J — Textes à relire à voix haute (5 min)

- [ ] Tutoiement et ton rassurant partout (« En attente de ton accord », jamais « refusé »).
- [ ] Passez en **anglais** : mêmes écrans lisibles, sans mot français.
- [ ] Texte agrandi au maximum : l'encart et l'aide restent lisibles.

## Ce qui ne doit PAS arriver

- [ ] Aucune écriture de position avant l'accord ou sans « Toujours ».
- [ ] Aucune coordonnée dans le `status` (seulement dans `positions`).
- [ ] Plus d'un document `positions/{uid}` par Miff et par personne ; aucun historique.
- [ ] Aucun statut `out` écrit à l'armement quand vous étiez déjà dehors.
- [ ] Aucune boucle d'écritures (le nombre d'écritures par jour reste raisonnable ; le disjoncteur arrête à 60 statuts / 150 positions).

## Questions pour vous (points ouverts de la PR)

1. Garde-fou de fréquence (15 min ou 250 m, plancher 2 min) et disjoncteurs (60 / 150 par jour) : valeurs à confirmer après vos mesures de la partie C.
2. Heure d'un événement = **heure où il arrive à l'app** (Android ne la donne pas sans lire la position de l'événement, ce qu'on s'interdit) : suffisant ?
3. Après un redémarrage hors d'une zone, le statut devient `unknown` (neutre) plutôt que `out` avec l'heure du redémarrage : à confirmer.
4. Relecture périodique des lieux toutes les 12 h : non construite (les lieux sont relus au plus toutes les heures aux réveils et à l'ouverture) : acceptable en bêta ?
5. Lecture du journal par `adb` : un écran interne est-il souhaité (hors périmètre ici) ?
