# Recette Android : arrêter puis reprendre le partage, la carte ne reste plus grise : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **un second téléphone** (l'iPhone 11 Pro ou un autre) sur le **même cercle**, pour regarder le Feed de l'autre côté. **Durée** : 15 minutes.
**Ce que vous testez** : quand vous **arrêtez** le partage d'une Miff puis le **reprenez**, votre carte, chez vos proches, doit **redevenir verte, orange ou rouge** selon l'endroit où vous êtes, comme si elle n'avait jamais été grise. Avant ce correctif, elle restait **grise** jusqu'à votre prochaine entrée ou sortie d'un lieu.
**Ce que vous ne faites PAS** : rien dans la console Firebase.

## Avant de commencer

- [ ] Installez l'APK de la PR (artefact de la CI) sur l'OPPO. Réglages de la localisation : **« Toujours »** + **position précise** (sinon rien n'est écrit : c'est voulu).
- [ ] Une Miff où vous partagez, avec **au moins un lieu de confiance** (par exemple « Maison »). Sur l'autre téléphone, ouvrez le Feed de cette Miff et repérez **votre carte**.
- [ ] Si quelque chose ne se passe pas comme écrit : capture d'écran, notez le numéro de l'étape, passez à la suivante.

## Partie A : dans un lieu de confiance (5 min)

- [ ] Vous êtes **dans** le lieu de confiance (ou arrivez-y). Sur l'autre téléphone, votre carte est **verte**.
- [ ] Sur l'OPPO : Miff > **Partager mon statut** > **Arrêter le partage** (confirmez). Sur l'autre téléphone, votre carte disparaît ou indique « Partage arrêté » (normal : le statut est supprimé).
- [ ] Toujours sur l'OPPO, **sans bouger** : **Reprendre le partage** (confirmez).
- [ ] Sur l'autre téléphone, **dans la minute qui suit** (tirez pour actualiser si besoin) : votre carte est **verte**, **pas grise**. L'heure affichée peut être celle de la reprise (l'app ne se souvient plus de votre heure d'arrivée après un arrêt) : c'est normal.
- [ ] Votre **position** (bouton « Localiser » côté proche) est de nouveau disponible.

## Partie B : hors de tout lieu (5 min)

- [ ] Éloignez-vous du lieu (hors de tous les lieux de la Miff), l'app ayant pu prendre votre position il y a **moins de 10 minutes** (ouvrez l'app une fois dehors). Votre carte est **orange ou rouge** selon l'ancienneté, comme d'habitude.
- [ ] **Arrêter le partage**, puis **Reprendre le partage**.
- [ ] Chez le proche, votre carte indique que vous êtes **sorti** (« est sorti(e) ») avec la couleur habituelle, **pas grise**.

## Partie C : sans signal (3 min) : le cas où elle reste grise, volontairement

- [ ] Mettez le téléphone en **mode avion** juste après avoir **ouvert l'app dehors**, attendez **plus de 10 minutes**, remettez le réseau (la position n'est plus récente, la localisation du téléphone peut rester coupée), puis **Arrêter** et **Reprendre** le partage.
- [ ] Chez le proche, la carte peut rester **grise** (« pas de nouvelles » : on n'invente rien). Elle redevient normale à la **prochaine entrée ou sortie** d'un lieu, ou quand l'app relit votre position (ouvrez l'app). C'est le comportement attendu, pas un défaut.

## Partie D : deux Miffs qui partagent le même lieu (2 min, si vous avez une deuxième Miff)

- [ ] Arrêtez puis reprenez le partage **d'une seule** des deux Miffs : l'autre Miff **ne change pas** (sa carte reste verte, rien ne clignote ni ne se remet à zéro).

## Partie E : mode voyage (si vous voulez, 2 min)

- [ ] Activez le **mode voyage**. Arrêtez puis reprenez le partage d'une Miff : chez le proche, la carte affiche **« En voyage »** (jamais « chez vous » ni « sorti »).

## Ce qui est vérifié ailleurs

- La CI est **verte** (compilation, formatage, analyse, tests unitaires : arrêt puis reprise, sans signal, voyage, autre Miff sur la même zone).
- Pas de lecture supplémentaire notable : au plus **une écriture de statut et une écriture de position** de plus par reprise (geste manuel).
