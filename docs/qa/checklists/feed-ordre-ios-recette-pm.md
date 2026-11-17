# Recette iOS de l'ordre du Feed (FEED-ORDRE) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**, avec **deux comptes dans la même Miff** : **A** (sur l'iPhone, qui regarde le Feed) et **B** (un proche, sur l'OPPO ou un second téléphone, qui publie une update). Idéalement un **troisième compte C** dans la Miff.
**Ce qui change** : les cartes du Feed se rangent de la **dernière update la plus récente à la plus ancienne** (celui qui vient de donner des nouvelles est en tête). Ma carte est triée comme les autres. Ce n'est **pas** un tri par urgence. Les membres sans nouvelles à classer vont après les autres, dans l'ordre d'arrivée.
**Durée** : environ 15 minutes.
**Spec** : `docs/tickets/feed-ordre-derniere-update.md` (décisions par défaut du §3). Android : même comportement, recette séparée.
**Important** : la « dernière update » est `lastUpdateAt` : une **update manuelle** (photo) **ou** une **arrivée dans un lieu de confiance** la font remonter (le modèle ne les distingue pas ; question 1 du ticket).

## Avant de commencer

- Installez le **build TestFlight** de la PR iOS `ios/feed-ordre-derniere-update` (lancé par vous). Projet Firebase : `miff-3d283` uniquement.
- Les comptes A, B (et C) partagent dans la même Miff et ont chacun **déjà donné de leurs nouvelles** au moins une fois (une photo, ou une arrivée dans un lieu), pour que leur carte ait une couleur et pas « Pas encore de nouvelles ».
- Un défaut : capture d'écran (ou vidéo courte) + numéro de l'étape, puis étape suivante.

## Partie 1 : l'ordre au repos (5 min)

1. [ ] Sur A, ouvrez le Feed : les cartes sont rangées de la dernière update la plus récente (en haut) à la plus ancienne. **Ma carte n'est plus forcément la dernière** : elle est à sa place selon ma propre dernière update.
2. [ ] Une carte **orange ou rouge n'est pas remontée** pour autant : l'ordre suit la date de la dernière nouvelle, pas la couleur. Le bandeau d'alertes en haut (rouges d'abord, silence le plus ancien d'abord) est **inchangé**.
3. [ ] Un membre qui **ne partage plus** (carte « Partage désactivé ») ou dont la carte dit « **Pas encore de nouvelles** » est **après tous les autres**.
3 bis. [ ] **Update avant tout départ** : avec un compte B tout neuf (partage activé, jamais sorti d'un lieu), faites publier une photo : sa carte dit toujours « Pas encore de nouvelles » mais **remonte** en tête. Avant sa photo, elle était après tous les autres.
4. [ ] Sur B, publiez une photo (onglet Update) pendant que **A reste sur le Feed sans toucher l'écran** : la carte de B se met à jour **tout de suite** (nouvelle photo, heure) et **glisse en douceur** vers le haut de la liste. Pas de clignotement.
5. [ ] Sur A, activez **Réglages > Accessibilité > Mouvement > Réduire les animations**, puis répétez l'étape 4 : la carte **saute à sa nouvelle place sans glisser**.

## Partie 2 : rien ne bouge sous le doigt (5 min)

6. [ ] Sur A, **faites défiler le Feed** (doigt posé, puis la liste qui glisse encore après avoir lâché). Pendant ce temps, sur B, publiez une photo. **Aucune carte ne change de place pendant le défilement.** Quelques instants après l'arrêt de la liste, la carte de B glisse en tête, **une seule fois**.
7. [ ] Sur A, **posez le doigt sur le bouton « Message »** (ou « Appeler ») d'une carte **sans le relâcher**. Pendant ce temps, B publie une photo : **les cartes ne bougent pas**. Relâchez en dehors du bouton (glissez le doigt loin avant) : rien ne se déclenche ; **l'ordre s'applique** à la fin de l'appui.
8. [ ] Même chose avec un appui **normal** sur « Message » : le **bon proche** est contacté (celui de la carte touchée), même si un changement d'ordre était en attente.
9. [ ] Pendant un défilement, **B puis C publient** chacun une photo : à la fin du geste, **un seul** réordonnancement, qui va directement au dernier ordre (pas de cascade de cartes qui sautent).
10. [ ] **Tirer pour actualiser** : la liste se met dans son **ordre à jour tout de suite** à la fin du rafraîchissement (sans attendre un geste), sans animation.

## Partie 3 : retours sur le Feed (3 min)

11. [ ] Sur A, allez sur l'onglet **Update** ou **Profil**. Pendant ce temps B publie. **Revenez sur le Feed** : l'ordre est **déjà à jour**, sans carte qui glisse.
12. [ ] Mettez l'app en arrière-plan 30 secondes (B publie), puis rouvrez-la : même résultat, **ordre à jour d'emblée**.
13. [ ] **Ma propre update** : sur A, publiez une photo depuis l'onglet Update puis revenez sur le Feed : ma carte est **déjà à sa place**.
14. [ ] **Plusieurs Miffs** (compte Miff+ seulement) : changez de Miff dans le sélecteur : le Feed affiche **directement** l'ordre à jour de l'autre Miff, sans animation.

## Partie 4 : VoiceOver (2 min)

15. [ ] Activez **VoiceOver**, placez le focus sur la carte d'un proche, puis faites publier B : **le focus reste sur la même carte** (même si elle bouge). Si l'ordre change, une **annonce** est dite **sans couper** ce qui est en cours : « {Prénom de B} a donné des nouvelles » **seulement si la carte de B arrive en tête de liste** ; sinon, ou si plusieurs cartes bougent : « Le Feed a été mis à jour ». **Aucune annonce** quand c'est **ma** carte qui arrive en tête, ni au retour sur l'onglet, ni quand quelqu'un quitte la Miff.
16. [ ] **Anglais** (langue du téléphone) : « {Name} shared an update » / « The Feed was updated ».
17. [ ] **Retour dans l'app en pleine pression** : posez le doigt sur le Feed, passez à l'écran d'accueil du téléphone, revenez : le Feed **applique l'ordre à jour** et réagit normalement aux changements suivants (rien de « coincé »).

## Cas connus (ne sont pas des défauts)

- **Update manuelle avant tout départ d'un lieu** : si un proche publie une photo **avant** d'avoir jamais quitté un lieu de confiance, sa carte dit toujours « Pas encore de nouvelles » (l'étiquette ne change pas) **mais elle remonte** à la date de son update (étape 3 bis). Un membre qui vient d'activer son partage, ou dont un voyage vient de se terminer sans nouvelle update, n'a pas de date : il reste après les autres.
- **Une arrivée automatique dans un lieu fait aussi remonter la carte** (même `lastUpdateAt`).
- **Heure de l'appareil de celui qui publie** : une update envoyée par un téléphone dont l'heure est décalée ou après être restée hors connexion peut se classer à côté de sa place réelle. Les updates manuelles d'iOS utilisent l'heure du serveur.
