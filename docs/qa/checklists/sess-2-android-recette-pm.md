# Recette Android de SESS-2 (un compte, un seul appareil) — parcours pour le PM

**Pour qui** : vous, avec votre **OPPO Find X5 Lite** (Android 13) et **un second appareil Android** (un autre téléphone, ou l'émulateur d'Android Studio) sur lesquels vous installez **le même APK**.
**Durée** : environ 20 minutes.
**Un seul compte de test suffit** (c'est justement le but : le même compte sur deux appareils). Il doit déjà avoir une Miff avec le partage activé, « Toujours » + précision accordés sur l'OPPO (recette LOC-2b), pour que le point D soit visible.

## Ce qui change pour vous

- Quand vous vous connectez avec **un compte déjà connecté ailleurs**, l'**ancien appareil est déconnecté** et le nouveau reste connecté.
- L'ancien appareil affiche, à l'ouverture, un message calme : **« Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. »** (en anglais : « Your account is now being used on another phone. You've been signed out of this one. »). Ce n'est ni une erreur, ni une perte d'accès à une Miff.
- L'ancien appareil **n'écrit plus rien** (ni statut, ni position) : c'est le but, qu'un vieux téléphone resté à la maison ne dise plus « tout va bien » à votre place.
- Limite connue et **validée par vous** : un appareil **sans réseau** n'est déconnecté qu'à son **retour en ligne**.

## Règles à retenir

- Un défaut : capture d'écran (et de la console Firebase) + numéro de l'étape, puis étape suivante.
- L'APK à installer est celui de la CI (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main. **Désinstallez d'abord** l'ancienne version sur les deux appareils (sinon l'installation d'avant la fonction se comporte comme une « ancienne installation », voir le point G).
- La console Firebase (Firestore) sert en **lecture seule** : `users/{uid}/session/active` (deux champs : `deviceId` et `startedAt`) et `miffs/{id}/status/{uid}`, `miffs/{id}/positions/{uid}` (le champ `deviceId` y apparaît aussi).
- Notez l'**appareil A** = l'OPPO et l'**appareil B** = le second appareil.

## Préparation (5 min)

1. Installez l'APK sur A (OPPO) et sur B. Ne connectez rien encore.
2. Ouvrez la console Firebase > Firestore > `users/{votre uid}/session/active` (le document n'existe pas encore, ou il date d'un essai précédent).

## Partie A — Connexion : une session est écrite (3 min)

- [ ] Sur **A**, connectez-vous avec le compte de test (e-mail ou Google). Le Feed s'ouvre comme d'habitude, **sans message** et sans attente notable de plus.
- [ ] Console : `users/{uid}/session/active` existe avec un `deviceId` de **32 caractères** (lettres, chiffres, `-` et `_`) et un `startedAt` récent (heure du serveur). **Notez les 6 premiers caractères du `deviceId`** : c'est l'identifiant de A.
- [ ] Le `deviceId` du statut `miffs/{id}/status/{uid}` et de la position `positions/{uid}` est le **même** dès la prochaine écriture (par exemple après une sortie de zone, ou en ouvrant puis en fermant l'app).

## Partie B — Le second appareil prend la place (5 min)

- [ ] Sur **B**, connectez-vous avec **le même compte**. Le Feed de B s'ouvre normalement.
- [ ] Console : `session/active` a changé : un **autre** `deviceId` (celui de B), un `startedAt` plus récent. Un seul document.
- [ ] Sur **A**, **app ouverte** : en quelques secondes, l'app revient à l'**écran de connexion** avec la note **« Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. »** (note grise, pas rouge, pas de message sur une Miff). B reste connecté.
- [ ] Sur A, la note ne revient pas si vous quittez puis rouvrez l'écran de connexion, et elle disparaît dès que vous commencez à vous connecter.
- [ ] Mettez le téléphone en anglais (Réglages de l'app > Langue) : la note est « Your account is now being used on another phone. You've been signed out of this one. ».

## Partie C — Plus d'écriture depuis l'ancien appareil (4 min)

- [ ] Console : notez l'`updatedAt` de `status/{uid}` et le `measuredAt` de `positions/{uid}` (écrits par B ou par A avant le remplacement).
- [ ] Avec A (déconnecté) : sortez / rentrez du lieu, ou ouvrez et fermez l'app. **Aucune** écriture ne vient de A : `status` et `positions` gardent le `deviceId` de B, `session/active` garde celui de B.
- [ ] Sur B, faites une sortie ou une arrivée de zone (ou ouvrez puis fermez l'app) : le statut est mis à jour, avec le `deviceId` de B.

## Partie D — App fermée sur l'ancien appareil (3 min)

- [ ] Reconnectez-vous sur **A** (il reprend la place : B est à son tour déconnecté, vérifiez-le sur B). **Fermez l'app de A** en la balayant.
- [ ] Sur **B**, reconnectez-vous (B reprend la place).
- [ ] Sur A (app **fermée**), provoquez un événement de zone (sortie de zone, ou activez / désactivez la localisation pour forcer un réveil). Dans la console, **aucun** statut ni position écrit par A après le moment de la reprise par B.
- [ ] Rouvrez A : écran de connexion avec la note (la note a été gardée pendant que l'app était fermée).

## Partie E — Reconnexion et déconnexion volontaire (3 min)

- [ ] Sur A, **reconnectez-vous** : A reprend la place, B est déconnecté à son tour.
- [ ] Sur A, **Réglages > Se déconnecter** : l'écran de connexion s'affiche **sans note**. Console : `users/{uid}/session/active` a **disparu**.
- [ ] Reconnectez-vous sur A : un nouveau `session/active` apparaît, avec un `deviceId` **différent** du précédent.

## Partie F — Hors connexion (3 min)

- [ ] Sur B (connecté, session à lui), passez en **mode avion**. Sur A, connectez-vous (A prend la place).
- [ ] B est toujours ouvert et **n'a rien remarqué** (limite validée). Pendant ce temps, laissez B faire une sortie / arrivée de zone si vous le pouvez : l'état attend.
- [ ] Sur B, désactivez le mode avion. **Au retour du réseau**, B revient à l'écran de connexion avec la note, **sans avoir écrit** son état en attente (la console garde le statut de A).
- [ ] **Déconnexion volontaire hors connexion** : sur A, mode avion, « Se déconnecter » : A se déconnecte quand même ; la console peut garder `session/active` (rien n'a pu être supprimé hors connexion), c'est attendu : la prochaine connexion l'écrase.

## Partie G — Ancienne installation (facultatif, 2 min)

- [ ] Si vous avez encore un téléphone avec **l'app d'avant** (même compte connecté, sans session dans la console), mettez-la à jour **sans la désinstaller** : à l'ouverture, elle reste connectée et la console montre un nouveau `session/active` (elle en a écrit une). Rien à faire d'autre.

## Ce que vous notez dans le compte rendu

- Le délai entre la connexion sur B et la note sur A (app ouverte) : en secondes.
- Si un message d'erreur inattendu est apparu (« Ça n'a pas fonctionné » en créant / rejoignant une Miff juste après un remplacement, par exemple).
- Tout écran où la note est mal affichée (coupée, trop proche de la barre d'état).
