# D. Progression et E. Pause, reprise, échec (écrans 07 et 08)

**Sources** : ticket §3.2 (D, E), §5.5, §5.6, §8, critères SC15, SC22 à SC31 ; code existant `MiffDeletionCoordinator` (états `Running`, `WaitingForNetwork`, `Stuck`, `Done`, tentatives, pauses) ; décisions Q6, Q7 du 2026-11-05.
**Maquettes** : `07-progression` (D1 sans Miff, D2 « Miff 1 sur 2 », D3 longue attente, D4 retrait Apple iOS) ; `08-pause-reprise-echec` (E1 hors connexion, E2 reprise au lancement, E3 échec répété avec contact, E4 nouvel essai).

## D. Progression
Écran **sans en-tête, sans retour, sans « Annuler »** (point de non-retour franchi), fond dégradé `gradient/auth-background`, logo Miff, indicateur d'activité, titre, ligne d'étape, phrase d'attente.
| Élément | FR | EN |
|---|---|---|
| Titre | On supprime ton compte… | We're deleting your account… |
| Phrase | Garde l'app ouverte, ça ne prend qu'un instant. | Keep the app open, it only takes a moment. |
| Étape, Miffs (§8) | Miff {index} sur {total} | Miff {index} of {total} |
| Étape, profil [proposé] | On supprime ton profil… | We're deleting your profile… |
| Étape, iOS compte Apple [proposé] | On retire l'autorisation Apple… | We're removing the Apple authorization… |
| Attente longue [proposé] | Ça peut prendre quelques minutes si ta Miff compte beaucoup de proches. | This can take a few minutes if your Miff has many members. |

Règles : **jamais de pourcentage** ni de détail des lots (« 14 membres retirés ») ; l'indicateur est indéterminé ; « Miff {index} sur {total} » compte d'abord les Miffs rejointes puis les créées (ordre décidé) ; la phrase d'attente longue n'apparaît que si la procédure compte plus de trois retraits ; l'écran reste allumé **pendant la procédure seulement**.

## E. Pause, reprise, échec
| Écran | Maquette | FR | EN | Comportement |
|---|---|---|---|---|
| Hors connexion | E1 | **On reprend dès que possible** [titre proposé] / Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. / Tu peux fermer l'app : on reprendra au prochain lancement. [proposé] | We'll pick up as soon as we can / No connection right now. The deletion will resume as soon as you have a network. / You can close the app: we'll pick up the next time you open it. | reprise automatique au retour du réseau ; aucune étape n'est comptée faite sans accusé du serveur |
| Reprise au lancement | E2 | **On termine la suppression de ton compte** [titre proposé] / Ta suppression de compte n'est pas terminée. On la termine maintenant. | We're finishing the deletion of your account / Your account deletion isn't finished. We're finishing it now. | le marqueur local passe **avant** le routage (jamais « crée ton profil ») ; pas de nouvelle ré-authentification tant que Firebase n'en réclame pas |
| Échec répété | E3 | **La suppression n'est pas terminée** [titre proposé] / On n'a pas pu terminer la suppression. Réessaie dans un moment. Si ça continue, écris-nous : {contact}. / Tu peux aussi fermer l'app : on reprendra au prochain lancement. [proposé] + bouton « Réessayer » | The deletion isn't finished / We couldn't finish the deletion. Please try again in a moment. If it keeps happening, write to us: {contact}. + “Try again” | après 3 essais espacés d'une même étape ; **le compte d'authentification n'est jamais supprimé tant qu'une Miff créée subsiste** (SC23) |
| Nouvel essai | E4 | On réessaie… [proposé] | Trying again… | bouton occupé ; tout est relu sur le serveur, rien n'est dupliqué |

Ton : informer calmement, **ni « erreur » ni « quota » ni « échec »**, aucun compte de ce qui reste, aucune urgence.

## Reprise après relance (rappel du ticket §5.6)
Marqueur local (identifiant du compte seulement) → écran E2 → reprise à l'inventaire serveur. Si, au lancement, le compte est déjà supprimé (session invalide, utilisateur introuvable, jeton révoqué) : c'est **un succès** : purge locale puis carrousel avec le bandeau G2 (voir `compte-supprime.md`).

## Accessibilité
L'écran entier est une région `status` en politesse « polite » : **chaque changement de ligne est annoncé** (« Miff 1 sur 2 », « On supprime ton profil… », pause, reprise) ; l'indicateur d'activité est décoratif ; les boutons (« Réessayer ») ≥ 44 pt ; taille de police maximale : le texte défile, le bouton reste atteignable ; mouvement réduit : l'indicateur devient statique avec le texte.

## Android / iOS
Android : retour système **ignoré** pendant la progression (ou met l'app en arrière-plan sans interrompre : la reprise existe) ; surcouches constructeur (ColorOS de l'OPPO) : reprise au lancement, pas de service d'arrière-plan. iOS : aucun geste de retour ; tâche d'arrière-plan courte autorisée puis reprise au lancement ; l'écran reste allumé pendant la procédure.
