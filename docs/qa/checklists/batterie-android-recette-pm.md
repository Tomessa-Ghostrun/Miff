# Recette Android : batterie rafraîchie à l'ouverture et à la sortie de l'app — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test** dans la même Miff : un compte **A** (le téléphone qu'on teste) et un compte **B** (un autre téléphone, ou le Feed de A vu depuis la console Firebase).
**Durée** : environ 45 min (dont plusieurs attentes de 15 min : prévoyez autre chose à faire entre).
**Principe** : votre décision du 2026-11-07 : l'app réécrit le niveau de batterie de votre statut quand vous **ouvrez** l'app et quand vous la **quittez**, en plus des écritures existantes (arrivée, départ, update). On contrôle ce qui est écrit dans la console Firebase (lecture seule) : `miffs/{id}/status/{uid de A}`, champs **`batteryPct`** et **`updatedAt`**, et ce que voit B dans son Feed. Aucune règle Firestore n'est modifiée.

## Ce qui change pour vous

- À l'ouverture et à la sortie de l'app, si vous partagez avec une Miff (accord v3 à jour, statut existant), `batteryPct` et `updatedAt` de votre statut sont mis à jour. **Rien d'autre ne change** dans le statut (présence, lieu, heures, texte, photo).
- **Limites de coût** : au plus une tentative toutes les **15 min** ; pas d'écriture si la batterie a bougé de **moins de 5 points** depuis la dernière valeur écrite, **sauf** si la dernière écriture date de **plus de 3 h** ; au plus **40 écritures de batterie par jour** et par téléphone.
- Rien n'est affiché de nouveau dans l'app : c'est le niveau que voient vos proches qui est plus récent.

## Règles à retenir

- Un défaut : capture d'écran (et de la console) + numéro de l'étape, puis étape suivante.
- **Notez l'heure réelle** (montre) de chaque ouverture et sortie : on la compare à `updatedAt` dans la console (affichée en UTC : +1 h ou +2 h à Paris).
- L'APK à installer est celui de la CI (artefact `miff-debug-apk` de la PR).
- La batterie du téléphone change lentement : pour le test des 5 points, utilisez un chargeur (ou laissez l'écran allumé longtemps) ; sinon passez l'étape en la notant « non testable ».

## Préparation (10 min)

1. Installez l'APK de debug de la PR. A et B sont dans la même Miff, **partage activé** des deux côtés, accord v3 donné.
2. Ouvrez la console Firebase > Firestore > `miffs/{id}/status/{uid de A}` : notez `batteryPct` et `updatedAt`.

## Partie A — À l'ouverture et à la sortie (15 min)

- [ ] Fermez complètement l'app sur A (retirez-la des applications récentes), attendez 1 minute, puis **ouvrez-la** : dans la console, `updatedAt` passe à l'heure de l'ouverture (à la minute près) et `batteryPct` est le niveau actuel du téléphone. **Heure et niveau notés** : ______
- [ ] Les autres champs du statut n'ont **pas** bougé : `presence`, `placeId`, `presenceAt`, `lastUpdateAt`, `updateText`, `hasPhoto`, `locationAccess`.
- [ ] Tout de suite après (moins de 15 min), **quittez** l'app (bouton Accueil), attendez 30 s : **rien ne change** dans la console (`updatedAt` identique : pas deux écritures en moins de 15 min).
- [ ] **Au bout de 15 min au moins**, avec une batterie qui a bougé d'au moins **5 points** (ou après 3 h), rouvrez puis quittez l'app : `updatedAt` avance et `batteryPct` suit. Sinon (même niveau à moins de 5 points, avant 3 h) : **rien ne change**. Noter le cas observé : ______
- [ ] **Sortie** : après une nouvelle attente de 15 min et un changement de batterie d'au moins 5 points, quittez l'app **sans l'avoir rouverte avant** : l'écriture de sortie apparaît dans la console (la sortie écrit aussi, pas seulement l'ouverture).
- [ ] Sur B, le Feed affiche la batterie de A **sans heure** quand elle date de moins d'1 h, avec l'heure au-delà (PR du Feed), et la **couleur** et « Dernière nouvelle » de A ne bougent pas à cause de ces rafraîchissements (seule la batterie est concernée).

## Partie B — Les écritures existantes écrivent toujours la batterie fraîche (10 min)

- [ ] Faites une **mise à jour manuelle** (Update) ou une arrivée / un départ si vous pouvez : `batteryPct` du statut est le niveau du moment, `updatedAt` aussi.
- [ ] Juste après (moins de 15 min), ouvrez et quittez l'app : **aucune écriture de batterie en plus** (l'écriture précédente compte).

## Partie C — Les cas où rien ne doit être écrit (10 min)

- [ ] **Partage arrêté** : sur A, arrêtez de partager avec la Miff (interrupteur de l'écran de la Miff) : le statut de A disparaît de la console. Ouvrez et quittez l'app : **le statut ne réapparaît pas** (aucun statut n'est créé par ce chantier).
- [ ] **Accord en attente** (ancien texte de consentement, si vous avez encore un compte dans cet état) : ouvrir et quitter l'app **n'écrit rien**.
- [ ] **Déconnecté** : déconnectez A (Réglages > Mon compte) : ouvrir et quitter l'app **n'écrit rien** (aucun statut touché).
- [ ] **Autre téléphone connecté au même compte** (si vous avez un second téléphone) : le premier téléphone, remplacé, est déconnecté et **n'écrit plus** de batterie.
- [ ] **Mode avion** : ouvrez et quittez l'app : **aucune erreur affichée**, l'app ne ralentit pas ; au retour du réseau, l'ouverture suivante écrit normalement.
- [ ] **Aucune position** : `miffs/{id}/positions/{uid}` n'est pas modifiée par ce chantier (son `measuredAt` ne bouge que selon les règles de détection habituelles).

## Partie D — Le coût (à relever, 5 min)

- [ ] Sur la journée de test, relevez dans Firestore > Utilisation le nombre d'écritures du jour **avant** et **après** la recette, et le nombre d'ouvertures / sorties faites : on attend **au plus 1 écriture par Miff et par tentative**, et au plus 1 tentative par quart d'heure. Chiffres : ______
- [ ] Version de développement : l'écran de diagnostic (journal de détection) montre des lignes `BATTERY_WRITTEN` (jamais plus de 40 par jour) et le niveau ; **aucune coordonnée**.
- [ ] Les seuils d'alerte de `docs/dev/surveillance-quotas.md` (écritures du jour > 12 000, ratio de lectures > 150) ne sont pas modifiés ; la section « 5 ter. Android » donne le coût estimé et le signal de bascule.

## Résultat

| Partie | OK / défaut | Remarque |
|---|---|---|
| A. Ouverture et sortie | | |
| B. Écritures existantes | | |
| C. Cas sans écriture | | |
| D. Coût | | |
