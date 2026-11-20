# Recette iOS de l'ordre du Feed par dernière photo (FEED-ORDRE-PHOTO) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**, avec **deux comptes dans la même Miff** : **A** (sur l'iPhone, qui regarde le Feed) et **B** (un proche, sur l'OPPO ou un second téléphone, qui publie une photo). Idéalement un **troisième compte C** dans la Miff.
**Ce qui change** : les cartes du Feed se rangent de la **dernière photo publiée la plus récente à la plus ancienne** (celui qui vient de publier une photo est en tête). Ma carte est triée comme les autres. Ce n'est **pas** un tri par urgence. Les membres qui n'ont jamais publié de photo vont après les autres, dans l'ordre d'arrivée. Une arrivée à la maison, une update sans photo, la batterie, un voyage ou l'expiration d'une photo **ne déplacent aucune carte**.
**Durée** : environ 15 minutes.
**Spec** : `docs/tickets/feed-ordre-photo.md` (elle remplace `feed-ordre-derniere-update.md` pour la date de tri). Android : même comportement, recette séparée.

## Avant de commencer

- **Les règles Firestore de la PR « Feed : ordre par photo » doivent être déployées par vous AVANT d'installer le build** : sinon le tri ne fonctionne pas (voir le repli ci-dessous).
- **Repli de l'application** : si, par erreur, les règles n'étaient pas encore déployées, la publication d'une photo **réussit quand même** (l'application renvoie une fois la publication sans la date de tri), mais **la carte ne remonte pas**. Ce n'est pas la bonne configuration de recette : déployez les règles, puis publiez une nouvelle photo.
- Installez le **build TestFlight** de la PR iOS `ios/feed-ordre-photo` (lancé par vous). Projet Firebase : `miff-3d283` uniquement.
- **Chaque compte (A, B, C) publie d'abord une photo** : après la mise à jour, aucune date n'existe encore, tout le monde est « sans date » (ordre d'arrivée). Publiez dans l'ordre C, puis B, puis A : A doit alors être en tête, puis B, puis C.
- Un défaut : capture d'écran (ou vidéo courte) + numéro de l'étape, puis étape suivante.

## Partie 1 : l'ordre au repos (6 min)

1. [ ] Sur A, ouvrez le Feed : les cartes sont rangées de la dernière photo la plus récente (en haut) à la plus ancienne. **Ma carte n'est plus forcément la dernière** : elle est à sa place selon ma propre dernière photo.
2. [ ] Une carte **orange ou rouge n'est pas remontée** pour autant. Le bandeau d'alertes en haut est **inchangé** ; une carte « Alerte en cours » reste **en première position**.
3. [ ] Un membre qui **ne partage plus** (carte « Partage désactivé ») ou qui **n'a jamais publié de photo** est **après tous les autres**.
4. [ ] Sur B, publiez une photo (onglet Update) pendant que **A reste sur le Feed sans toucher l'écran** : la carte de B se met à jour **tout de suite** (nouvelle photo) et **glisse en douceur** vers le haut. Pas de clignotement. **La publication ne montre aucune erreur.**
5. [ ] **Ce qui ne change rien** : B arrive dans un lieu de confiance, puis publie une update **sans photo** (si l'écran le permet) : sa carte se met à jour (couleur, heure) mais **reste à sa place**.
6. [ ] **Photo plus ancienne** : un proche dont la dernière photo date de plus de 24 h (la vignette a disparu) **garde son rang**, au-dessus de ceux qui n'ont jamais publié de photo.
7. [ ] Sur A, activez **Réglages > Accessibilité > Mouvement > Réduire les animations**, puis répétez l'étape 4 avec C : la carte **saute à sa nouvelle place sans glisser**.

## Partie 2 : rien ne bouge sous le doigt (5 min)

8. [ ] Sur A, **faites défiler le Feed** (doigt posé, puis la liste qui glisse encore après avoir lâché). Pendant ce temps, sur B, publiez une photo. **Aucune carte ne change de place pendant le défilement.** Quelques instants après l'arrêt de la liste, la carte de B glisse en tête, **une seule fois**.
9. [ ] Sur A, **posez le doigt sur le bouton « Message »** (ou « Appeler ») d'une carte **sans le relâcher**. Pendant ce temps, B publie une photo : **les cartes ne bougent pas**. Relâchez en dehors du bouton (glissez le doigt loin avant) : rien ne se déclenche ; **l'ordre s'applique** à la fin de l'appui.
10. [ ] Même chose avec un appui **normal** sur « Message » : le **bon proche** est contacté (celui de la carte touchée), même si un changement d'ordre était en attente.
11. [ ] Pendant un défilement, **B puis C publient** chacun une photo : à la fin du geste, **un seul** réordonnancement, qui va directement au dernier ordre (pas de cascade de cartes qui sautent).
12. [ ] **Tirer pour actualiser** : la liste se met dans son **ordre à jour tout de suite** à la fin du rafraîchissement (sans attendre un geste), sans animation.

## Partie 3 : retours sur le Feed (3 min)

13. [ ] Sur A, allez sur l'onglet **Update** ou **Profil**. Pendant ce temps B publie une photo. **Revenez sur le Feed** : l'ordre est **déjà à jour**, sans carte qui glisse.
14. [ ] Mettez l'app en arrière-plan 30 secondes (B publie), puis rouvrez-la : même résultat, **ordre à jour d'emblée**.
15. [ ] **Ma propre photo** : sur A, publiez une photo depuis l'onglet Update puis revenez sur le Feed : ma carte est **déjà à sa place** (en tête).
16. [ ] **Plusieurs Miffs** (compte Miff+ seulement) : changez de Miff dans le sélecteur : le Feed affiche **directement** l'ordre à jour de l'autre Miff, sans animation.

## Partie 4 : VoiceOver (2 min)

17. [ ] Activez **VoiceOver**, placez le focus sur la carte d'un proche, puis faites publier une photo à B : **le focus reste sur la même carte** (même si elle bouge). Si l'ordre change, une **annonce** est dite **sans couper** ce qui est en cours : « {Prénom de B} **a publié une photo** » **seulement si la carte de B arrive en tête de liste** ; sinon, ou si plusieurs cartes bougent : « Le Feed a été mis à jour ». **Aucune annonce** quand c'est **ma** carte qui arrive en tête, ni au retour sur l'onglet, ni quand quelqu'un quitte la Miff.
18. [ ] **Anglais** (langue du téléphone) : « {Name} shared a photo » / « The Feed was updated ».
19. [ ] **Retour dans l'app en pleine pression** : posez le doigt sur le Feed, passez à l'écran d'accueil du téléphone, revenez : le Feed **applique l'ordre à jour** et réagit normalement aux changements suivants (rien de « coincé »).

## Cas connus (ne sont pas des défauts)

- **Après la mise à jour, tout le monde est « sans date »** tant qu'aucune nouvelle photo n'a été publiée : ordre d'arrivée (point de départ de la recette).
- **L'heure affichée sur une carte** (arrivée, départ ou photo) peut ne pas aller dans l'ordre de la liste : l'ordre suit **uniquement la photo** (question posée au PM, ticket §12).
- **Arrêter puis réactiver son partage** efface la date de la dernière photo : la carte repasse « sans date » jusqu'à la prochaine photo.
- **Un proche qui n'a pas mis à jour l'application** : sa photo ne le fait pas remonter tant qu'il garde l'ancienne version.
- **Photo publiée sans avoir jamais quitté un lieu** : la carte dit toujours « Pas encore de nouvelles » (libellé inchangé) mais **remonte** grâce à sa photo.
