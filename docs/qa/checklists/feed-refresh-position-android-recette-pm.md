# Recette Android : « Tirer pour actualiser » actualise aussi ma position (FEED-REFRESH-POSITION)

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13), avec **deux comptes dans la même Miff** (votre OPPO + un second téléphone ou l'iPhone 11 Pro) et **au moins un lieu de confiance** dont vous connaissez l'adresse. Aucune connaissance technique requise.
**Durée** : 15 minutes (plus un petit trajet pour les points 6 et 7). **Ce qu'on vérifie** : le geste « tirer le Feed vers le bas » relit les nouvelles des proches **comme avant** et, **en plus**, regarde une fois où vous êtes pour corriger votre statut si l'app avait raté une arrivée ou un départ.
**Avant** : installer l'APK de la PR « Android : tirer pour actualiser actualise ma position » (artefact de la CI, jamais un APK construit à la main). Ticket : `docs/tickets/feed-refresh-position.md`. Autorisation de localisation **« Toujours »** + **précise** accordée à Miff.
**À savoir** : le geste **n'écrit un statut que si votre présence a changé** (arrivée ou départ non vu) ; il **ne dit jamais « tout va bien »** à votre place. Votre position visible par « Localiser » est mise à jour **au plus toutes les 15 minutes** (ou après 250 m) : c'est voulu.

## 1. Le geste reste fluide

| # | Vérifier | OK |
|---|---|---|
| 1 | Tirer le Feed vers le bas : l'indicateur tourne puis disparaît **en moins de 8 secondes**, comme avant, même si le GPS est lent (par exemple à l'intérieur d'un bâtiment) | [ ] |
| 2 | Tirer **plusieurs fois de suite** : rien ne se bloque, aucun message d'erreur, aucun écran d'erreur | [ ] |
| 3 | TalkBack (si utilisé) : l'action « Actualiser » et les annonces « Actualisation en cours » / « Informations actualisées » sont **inchangées** | [ ] |

## 2. Une arrivée ou un départ que l'app n'avait pas vu

| # | Vérifier | OK |
|---|---|---|
| 4 | **Arrivée** : être **dans** un lieu de confiance alors que votre carte dit encore que vous êtes dehors (par exemple après avoir **fermé l'app de force** puis être arrivé, ou en coupant puis rétablissant la localisation du téléphone). Ouvrir le Feed, **tirer** : votre carte passe à « dans ce lieu » (vert) ; sur le second téléphone, votre carte fait de même (quelques secondes) | [ ] |
| 5 | **Départ** : quitter le lieu de plus de **200 m** sans que votre carte ait bougé (même méthode), ouvrir le Feed, **tirer** : sur le second téléphone, votre carte ne dit plus « dans ce lieu » et devient **neutre** (« Pas encore de nouvelles », jamais un « a quitté » : l'heure réelle du départ est inconnue, l'app ne l'invente pas) | [ ] |
| 6 | **Au bord du lieu** (à 100-150 m de l'adresse, rayon de 100 m) : tirer **ne** vous déclare **pas** parti | [ ] |
| 7 | **Rien n'a changé** (vous êtes toujours au même endroit) : tirer ne change rien sur le second téléphone (pas de nouvelle heure sur votre carte) | [ ] |

## 3. Limites et cas calmes

| # | Vérifier | OK |
|---|---|---|
| 8 | Tirer **deux fois en moins de 30 secondes** : la position n'est lue qu'**une fois** (pas de différence visible : c'est la batterie qui en profite) ; les nouvelles des proches, elles, se relisent toutes les 15 s | [ ] |
| 9 | **Mode avion / sans réseau** : tirer ne bloque rien, la bannière « Dernières informations de… » est là, **aucune erreur** ; au retour du réseau, tout revient normalement | [ ] |
| 10 | **Mode voyage activé** : tirer relit les proches, **rien** ne change sur votre statut de voyage ; le second téléphone voit toujours « en voyage » (seule votre dernière position peut se rafraîchir, comme à l'ouverture de l'app, car elle reste partagée pendant un voyage) | [ ] |
| 11 | **Partage arrêté** pour la Miff (Réglages de la Miff) : tirer relit ce qui est affichable, **rien** n'est envoyé ; la bulle « Reprendre le partage » est toujours là | [ ] |
| 12 | **Localisation du téléphone coupée** (volet rapide du téléphone, pendant que le Feed est ouvert) : tirer affiche **un court message calme** « Ta position n'a pas pu être actualisée… » ; pas d'écran d'erreur ; les proches sont relus quand même. (Une autorisation retirée dans les réglages du téléphone, elle, ramène à l'écran d'autorisation habituel dès le retour dans l'app : le Feed n'est alors plus affiché.) | [ ] |
| 13 | Rétablir la localisation (volet rapide), tirer à nouveau : le message ne revient pas, tout fonctionne | [ ] |

## 4. Confidentialité (ce que vous ne devez **pas** voir)

| # | Vérifier | OK |
|---|---|---|
| 14 | Aucune notification, aucun message, aucun texte à l'écran ne montre une **adresse ou des coordonnées** après un geste | [ ] |
| 15 | Réglages > Confidentialité : rien de nouveau à accepter ; aucune demande d'autorisation supplémentaire n'apparaît | [ ] |

## Écarts connus, à ne pas signaler comme des défauts
- **L'heure d'une arrivée ou d'un départ rattrapé est celle du geste**, pas celle du vrai franchissement (inconnue de l'app).
- **Un départ n'est repéré qu'à plus de 200 m** du lieu de rayon 100 m (marge voulue contre les fausses alertes) : plus près, c'est le suivi automatique habituel qui s'en charge.
- **Un départ non vu devient « neutre »** et non « a quitté… » (décision alignée sur iOS, ticket §6 A3) ; si le suivi automatique finit par le voir, il écrit alors le vrai départ.
- **Le message du point 12 n'a pas de bouton « Réglages »** : un lien n'existe pas dans ce type de message ; quand c'est l'autorisation qui manque, l'écran d'autorisation habituel prend le relais et en a un.

**Résultat** : ____ points sur 15 validés. Remarques : ______________________
