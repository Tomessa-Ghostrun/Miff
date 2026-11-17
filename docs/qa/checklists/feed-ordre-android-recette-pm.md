# Recette Android : ordre du Feed par dernières nouvelles (FEED-ORDRE)

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13), avec **deux comptes dans la même Miff** (votre OPPO + un second téléphone ou l'iPhone 11 Pro). Aucune connaissance technique requise.
**Durée** : 10 minutes. **Ce qu'on vérifie** : les cartes du Feed sont classées du proche qui a donné des nouvelles le plus récemment au plus ancien, **ma carte comprise**, et la liste ne bouge jamais sous votre doigt.
**Avant** : installer l'APK de la PR « Android : ordre du Feed » (artefact de la CI, jamais un APK construit à la main). Ticket : `docs/tickets/feed-ordre-derniere-update.md`.
**À savoir** : « donner des nouvelles » veut dire ici la **dernière nouvelle enregistrée** : une update publiée depuis l'écran Update **ou** l'arrivée détectée dans un lieu de confiance (le modèle de données ne sait pas les distinguer, voir le ticket, question 1). Un proche dont la carte dit **« Pas encore de nouvelles »** reste **après** tous les autres, même s'il a publié une update avant son premier départ d'un lieu (voir « Écarts connus »).

## 1. L'ordre

| # | Vérifier | OK |
|---|---|---|
| 1 | Ouvrir le Feed avec au moins 3 cartes qui ont des nouvelles : elles sont de la **plus récente** à la plus ancienne (l'heure/le jour affiché sur chaque carte va dans ce sens) | [ ] |
| 2 | **Ma carte** n'est plus forcément en dernier ni en premier : elle se place selon ma dernière nouvelle, comme les autres | [ ] |
| 3 | Un proche dont la carte dit **« Pas encore de nouvelles »** ou **« Partage désactivé »** est **après** tous ceux qui ont des nouvelles | [ ] |
| 4 | Une carte **rouge ou orange** ne remonte **pas** parce qu'elle est rouge ou orange (le tri n'est pas un tri par urgence) ; le bandeau d'alertes en haut est inchangé | [ ] |
| 5 | Fermer complètement l'app et la rouvrir : **le même ordre** (rien ne « clignote » entre deux ouvertures) | [ ] |

## 2. Quand un proche donne des nouvelles

Le second compte publie une update (écran Update) pendant que vous êtes sur le Feed, **sans toucher l'écran** :

| # | Vérifier | OK |
|---|---|---|
| 6 | Sa carte se met à jour tout de suite et **glisse doucement** en tête de liste | [ ] |
| 7 | Le second compte publie une update pendant que vous **faites défiler** le Feed : **aucune carte ne bouge** pendant le défilement ; quand vous lâchez et que la liste s'arrête, la carte glisse à sa place (une seule fois) | [ ] |
| 8 | Même chose pendant que vous **gardez le doigt posé** sur « Message » ou « Appeler » (sans relâcher) : rien ne bouge ; **le bouton touché reste celui du bon proche** | [ ] |
| 9 | Revenir sur le Feed depuis l'onglet Update ou Profil, ou depuis une autre app : l'ordre est **déjà à jour**, sans glissement | [ ] |
| 10 | Tirer pour actualiser : l'ordre à jour apparaît directement, sans attendre | [ ] |
| 11 | Ma propre update (écran Update) : au retour sur le Feed, ma carte est **déjà à sa place** | [ ] |

## 3. Animations réduites et lecteur d'écran (TalkBack, si vous l'utilisez)

| # | Vérifier | OK |
|---|---|---|
| 12 | Réglages > Accessibilité > **Supprimer les animations** : une carte qui change de place **saute** sans glisser | [ ] |
| 13 | TalkBack : quand l'ordre change, une annonce **discrète** (sans couper la lecture en cours) dit « *Prénom* a donné des nouvelles » (ou « Le Feed a été mis à jour » si plusieurs cartes bougent) ; **le focus ne saute pas** | [ ] |
| 14 | Pas d'annonce pour **ma** carte, ni en revenant sur l'onglet Feed | [ ] |

## Écarts connus, à ne pas signaler comme des défauts
- **Une arrivée automatique fait aussi remonter la carte** (par exemple « arrivé à la maison ») : voir le ticket, questions 1 et 2.
- **Update manuelle avant tout départ d'un lieu** : tant que le proche n'a jamais quitté un lieu de confiance, sa carte dit « Pas encore de nouvelles » et reste en bas, même après une update. C'est cohérent avec ce que dit la carte ; le changer demande de revoir ce texte (hors périmètre de ce ticket).
- **Horloge d'un téléphone décalée** : l'ordre peut être faussé de quelques minutes, au pire quelques heures (acceptable en MVP, voir le ticket).
- **Fenêtre de 4 secondes** : juste après un retour sur le Feed ou un rafraîchissement, un changement d'ordre est appliqué directement (sans glissement ni annonce).

**Résultat** : ____ points sur 14 validés. Remarques : ______________________
