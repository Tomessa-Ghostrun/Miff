# Feed : feuille « Localiser » et photo en plein écran (maquettes 08, 09)

**Pour** : FEED-3 (Localiser), FEED-4 (photo), puis iOS. **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.6, §5.7, §6.3, §7 (feuille et photo), C10, C11, D1, D10, L1 à L4, P1 à P3, Q1 ; décision du PM du 2026-11-01 (position stockée côté serveur).

**Confiance sur la localisation** (principe transverse) : ces deux écrans montrent **la date**, redisent que **ce n'est pas du direct**, et rendent visible **qui voit quoi** (réciprocité). Aucune coordonnée n'est affichée en clair, aucun nom ni étiquette n'est transmis à l'application de cartes.

## 08 : feuille « Localiser »
**Déclenchement** : toucher Localiser sur la carte d'un autre membre dont le partage est actif. Le bouton devient un indicateur pendant la lecture (une seule lecture serveur, jamais le cache, pas d'écouteur) ; la feuille s'ouvre au résultat. **Écart justifié avec Notion** (C10) : un toucher de plus qu'une ouverture directe de la carte, pour montrer la date.

**Forme** : feuille du bas, coins hauts à 16 pt, fond `bg/surface`, voile noir 30 %, poignée de 36 pt. **Pas de carte intégrée.** Rien n'est conservé après la fermeture.

| Cas | Contenu | Boutons |
|---|---|---|
| A. Récente (< 24 h) | Titre « Dernière position connue de {Prénom} » ; « Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct. » ; ligne de confiance **[proposé]** | **Ouvrir dans les cartes** (principal), Fermer (lien) |
| B. Ancienne (> 24 h) | Titre ; pictogramme horloge + « Elle date d'hier à 08 h 12 : elle n'est plus affichée. » **Aucune coordonnée, aucun bouton de carte** | Fermer (contour) |
| C. Introuvable | Pictogramme position barrée + « {Prénom} n'a pas encore de position connue. » | Fermer |
| D. Refusé (réciprocité, partage arrêté, retrait) | Cadenas + « On ne peut pas afficher la position de {Prénom} pour le moment. Elle apparaît quand vous partagez tous les deux. » **Un seul message** pour tous les refus (on ne dit pas qui a arrêté) | Fermer |
| E. Hors connexion | Nuage barré + « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » | Fermer (retoucher Localiser relance) |
| F. Aucune application | Cas A + ligne « Cette action n'est pas disponible sur cet appareil. » | Fermer |

| Élément | FR | EN |
|---|---|---|
| Titre | Dernière position connue de {Prénom} | {Name}'s last known position |
| Récente | Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct. | Measured at 8:12 AM (20 min ago). This isn't a live position. |
| Confiance **[proposé]** | Vous partagez tous les deux : {Prénom} peut aussi voir ta dernière position. | You both share: {Name} can also see your last known position. |
| Boutons | Ouvrir dans les cartes · Fermer | Open in Maps · Close |
| Ancienne | Elle date d'hier à 08 h 12 : elle n'est plus affichée. | It dates from yesterday at 8:12 AM: it's no longer shown. |
| Introuvable | {Prénom} n'a pas encore de position connue. | {Name} has no known position yet. |
| Refus | On ne peut pas afficher la position de {Prénom} pour le moment. Elle apparaît quand vous partagez tous les deux. | We can't show {Name}'s position right now. It appears when you both share. |
| Hors connexion | Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. | No connection right now. Try again once you're back online. |
| Sans application | Cette action n'est pas disponible sur cet appareil. | This action isn't available on this device. |

**Accessibilité** : le titre est lu en premier, le focus va au bouton principal (ou « Fermer » dans les cas B à E) ; fermeture par retour système, glissement vers le bas, toucher sur le voile ; boutons de 46 pt ; jamais le mot « erreur » ni « quota » ; l'heure suit le format du téléphone. Texte au maximum : la feuille grandit, devient défilante au-delà de 80 % de la hauteur.
**Vie privée** : aucune coordonnée dans l'état sauvegardé de l'écran, un journal ou un rapport de plantage (L4).

## 09 : photo en plein écran
**Déclenchement** : toucher la photo d'une carte. Fond noir ; photo centrée, zoom au pincement (1x à 4x), double toucher pour zoomer ; barre d'état claire.

| Cas | Contenu |
|---|---|
| A. Avec localisation (Q1 déployée) | « Photo de {Prénom} · 15 h 40 » ; « Position figée au moment de la photo. » **[proposé]** ; bouton **« Voir où c'était »** (contour blanc, 46 pt) → carte du système, coordonnées seules, **aucun nom** |
| B. Sans localisation | « Photo de {Prénom} · 15 h 40 » seulement. **Rien** à la place du bouton (pas de « localisation indisponible ») |
| C. Dans le fil : photo localisée sans ma position publiée | Tuile bleu pâle à bord pointillé + cadenas + « Partage ta position pour voir cette photo. » (réciprocité : même règle que Localiser). Les photos sans localisation restent visibles |

| Élément | FR | EN |
|---|---|---|
| Légende | Photo de {Prénom} · 15 h 40 | {Name}'s photo · 3:40 PM |
| Position **[proposé]** | Position figée au moment de la photo. | Position when the photo was taken. |
| Bouton | Voir où c'était | See where it was |
| Tuile | Partage ta position pour voir cette photo. | Share your position to see this photo. |
| Fermer (a11y) | Fermer la photo | Close the photo |

- **Fermeture** : bouton rond de 44 pt en haut à gauche (fond blanc 18 %), retour système, glissement vers le bas.
- Une photo **expirée** (plus de 24 h) ou refusée n'est jamais affichée, même en cache, et ne produit **aucun message**.
- Le Feed n'écrit ni ne supprime jamais une photo.
- **Accessibilité** : la photo a le nom « Photo de {Prénom} de 15 h 40 » ; le bouton « Voir où c'était » est annoncé « Ouvre la carte du système » ; le zoom a une alternative (taille d'écran) ; contraste du texte blanc sur dégradé noir > 15:1.

## Plateformes
- **Android** : `ModalBottomSheet` ; `geo:` avec repli sur la page web de Google Maps ; photo en plein écran = écran ou dialogue plein cadre avec retour système.
- **iOS** : `.sheet` avec détente moyenne ; Plans (`maps.apple.com`) ; photo en `fullScreenCover`, geste de fermeture vers le bas ; Dynamic Type sur la feuille.
