# Recette Android : ordre du Feed par dernière photo (FEED-ORDRE-PHOTO)

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13), avec **deux comptes dans la même Miff** (votre OPPO + un second téléphone ou l'iPhone 11 Pro). Aucune connaissance technique requise.
**Durée** : 10 à 15 minutes. **Ce qu'on vérifie** : les cartes du Feed sont classées du proche qui a **publié une photo le plus récemment** au plus ancien, **ma carte comprise**, et la liste ne bouge jamais sous votre doigt.
**Avant** : (1) **les règles Firestore de la PR « Feed : ordre par photo » doivent être déployées** (par vous, avant d'installer l'APK : sinon la publication d'une photo est refusée) ; (2) installer l'APK de la PR « Android : ordre du Feed par photo » (artefact de la CI, jamais un APK construit à la main) ; (3) **chaque compte de test publie d'abord une photo** (l'ordre ne connaît aucune date avant : tous les membres sont « sans date » après la mise à jour). Ticket : `docs/tickets/feed-ordre-photo.md` (il remplace `feed-ordre-derniere-update.md` pour la date de tri).
**À savoir** : seule une **nouvelle photo** change l'ordre. Une arrivée à la maison, une update sans photo, la batterie, un voyage ou l'expiration d'une photo (24 h) **ne déplacent aucune carte**. Les membres qui n'ont jamais publié de photo (ou « Partage désactivé ») sont **après** les autres, dans l'ordre d'arrivée dans la Miff.

## 1. L'ordre

| # | Vérifier | OK |
|---|---|---|
| 1 | Avec au moins 3 cartes ayant chacune publié une photo à des moments différents : elles vont de la photo la **plus récente** (en haut) à la plus ancienne | [ ] |
| 2 | **Ma carte** se place selon **ma** dernière photo, comme les autres (ni forcément première, ni forcément dernière) | [ ] |
| 3 | Un proche qui **n'a jamais publié de photo** (ou dont la carte dit « Partage désactivé ») est **après** tous ceux qui en ont publié une | [ ] |
| 4 | Une carte **rouge ou orange** ne remonte **pas** parce qu'elle est rouge ou orange ; le bandeau d'alertes en haut est inchangé ; une carte « Alerte en cours » reste **en première position** | [ ] |
| 5 | Fermer complètement l'app et la rouvrir : **le même ordre** (rien ne « clignote » entre deux ouvertures) | [ ] |
| 6 | **Ce qui ne change rien** : le second compte arrive dans un lieu de confiance, ou publie une update **sans photo** : sa carte se met à jour (couleur, heure) mais **reste à sa place** | [ ] |
| 7 | **Photo plus ancienne** : un proche dont la dernière photo date de plus de 24 h (la vignette a disparu) garde son rang, au-dessus de ceux qui n'ont jamais publié de photo | [ ] |

## 2. Quand un proche publie une photo

Le second compte publie une photo (écran Update) pendant que vous êtes sur le Feed, **sans toucher l'écran** :

| # | Vérifier | OK |
|---|---|---|
| 8 | Sa carte se met à jour tout de suite (nouvelle photo) et **glisse doucement** en tête de liste | [ ] |
| 9 | Le second compte publie une photo pendant que vous **faites défiler** le Feed : **aucune carte ne bouge** pendant le défilement ; quand vous lâchez et que la liste s'arrête, la carte glisse à sa place (une seule fois) | [ ] |
| 10 | Même chose pendant que vous **gardez le doigt posé** sur « Message » ou « Appeler » (sans relâcher) : rien ne bouge ; **le bouton touché reste celui du bon proche** | [ ] |
| 11 | Revenir sur le Feed depuis l'onglet Update ou Profil, ou depuis une autre app : l'ordre est **déjà à jour**, sans glissement | [ ] |
| 12 | Tirer pour actualiser : l'ordre à jour apparaît directement, sans attendre | [ ] |
| 13 | Ma propre photo (écran Update) : au retour sur le Feed, ma carte est **déjà à sa place** (en tête) | [ ] |
| 14 | **Publication acceptée** : la photo se publie sans message d'erreur (si une erreur apparaît, les règles ne sont probablement pas déployées : arrêtez et prévenez-moi) | [ ] |

## 3. Animations réduites et lecteur d'écran (TalkBack, si vous l'utilisez)

| # | Vérifier | OK |
|---|---|---|
| 15 | Réglages > Accessibilité > **Supprimer les animations** : une carte qui change de place **saute** sans glisser | [ ] |
| 16 | TalkBack : quand l'ordre change, une annonce **discrète** (sans couper la lecture en cours) dit « *Prénom* **a publié une photo** » si ce proche arrive **en tête**, sinon « Le Feed a été mis à jour » ; **le focus ne saute pas** | [ ] |
| 17 | Pas d'annonce pour **ma** carte, ni en revenant sur l'onglet Feed | [ ] |

## Écarts connus, à ne pas signaler comme des défauts
- **Après la mise à jour, tout le monde est « sans date »** tant qu'aucune nouvelle photo n'a été publiée : le Feed est alors dans l'ordre d'arrivée (c'est le point de départ de la recette).
- **L'heure affichée sur une carte** (arrivée, départ ou photo) peut ne pas aller dans l'ordre de la liste : l'ordre suit **uniquement la photo** (question posée au PM, ticket §12).
- **Arrêter puis réactiver son partage** efface la date de sa dernière photo : sa carte repasse « sans date » jusqu'à la prochaine photo.
- **Un proche qui n'a pas encore mis à jour l'application** : sa publication de photo ne le fait pas remonter tant qu'il garde l'ancienne version.
- **Fenêtre de 4 secondes** : juste après un retour sur le Feed ou un rafraîchissement, un changement d'ordre est appliqué directement (sans glissement ni annonce).

**Résultat** : ____ points sur 17 validés. Remarques : ______________________
