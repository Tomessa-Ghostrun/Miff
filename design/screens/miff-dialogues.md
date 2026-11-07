# Flux Miff — Dialogues de confirmation et messages (H, I, J, K, L, M)

**Source** : `docs/tickets/miff.md` §3.2 (H à M), §5.6, §5.7, §5.10, §5.11, §5.13 ; décisions QM1, QM8, QM9.
**État du dessin Figma** : **non dessiné** (limite d'appels MCP atteinte ; voir `design/figma-design-system-state.json`, bloc `fluxMiff`). Fiche complète.
**Modèle unique** (ticket §3.3) : un seul composant `Dialog`, ton calme, jamais d'urgence artificielle ni de rouge d'alerte plein écran.

## Composant `Dialog` (à créer ; non créé faute d'appels)
- Largeur 280 (comme `PrimerModal` de l'onboarding), padding 24 × 20, espacement 16, fond `bg/surface`, contour `border/action` 2 px, radius `radius/md`, ombre `shadow/default`. Posé sur un fond voilé (`neutral/black-a50` en plein cadre) au-dessus de l'écran précédent.
- Contenu : titre (`title/bold-20`, `text/primary`), texte (`body/regular`, `text/description`), bouton principal (42 de haut), bouton optionnel `Extra` (secondaire), lien « Annuler » (`body/bold-16`, `action/primary`, 44 de haut).
- Variantes : `Tone=Default` (principal = `Button/Primary`, fond `action/primary`) ; `Tone=Sensitive` (principal blanc, contour et texte `text/error` #D60700 : **pas de fond rouge**, pour rester calme). Propriétés : `Title`, `Body`, `PrimaryLabel`, `CancelLabel`, `ShowExtra`, `ExtraLabel`.
- Les actions « Retirer », « Quitter », « Supprimer » utilisent `Tone=Sensitive` ; « Créer un nouveau code », « Arrêter », « Reprendre » `Tone=Default`.
- **Le bouton le moins risqué est toujours « Annuler »** (premier dans l'ordre de focus, sélectionné par la touche retour).
- Un appui = une opération ; pendant l'opération, le bouton principal affiche un indicateur et tous les boutons sont inactifs.

## H. Retirer un proche (créateur)
| Cadre | Contenu |
|---|---|
| H1 Confirmation (`Sensitive`) | Titre « Retirer Camille de la Miff ? » / « Remove Camille from the Miff? » ; texte « Camille n'aura plus accès à ce cercle. Un nouveau code sera créé : l'ancien ne fonctionnera plus. » / « Camille will no longer have access to this circle. A new code will be created: the old one will stop working. » ; boutons « Retirer » / « Remove », « Annuler » / « Cancel » |
| H2 En cours | Bouton principal avec indicateur, libellé « Retrait… » / « Removing… » `[À CONFIRMER]` |
| H3 Succès avec nouveau code | Dialogue `Default` : titre « Camille ne fait plus partie de la Miff » ; texte « Voici le nouveau code, à partager avec qui tu veux. » ; `CodeField Valid` ; boutons « Partager le code » et « Terminer » / « Done » `[À CONFIRMER]` |
| H4 Cible déjà partie | Pas de dialogue : `Notice Info` sur l'écran F, « Camille ne fait plus partie de la Miff. » ; le code est tout de même renouvelé (ticket §10, cas 8) |
| H5 Erreur / hors connexion | `Notice Error` / `Notice Offline` dans le dialogue, bouton principal actif |

## I. Quitter la Miff (membre)
Titre « Quitter la Miff « Famille Moudugenou » ? » / « Leave the Miff "Famille Moudugenou"? » ; texte « Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code. » / « Your location will no longer be shared with this circle. You can come back if someone gives you the code again. » ; boutons « Quitter la Miff » (`Sensitive`) / « Annuler ». États : I1 confirmation, I2 en cours, I3 hors connexion (`Notice Offline`). Succès : retour au Feed sans message supplémentaire (le départ est visible par la disparition de la Miff). **Le créateur ne voit jamais ce dialogue** : voir le texte du pied de l'écran F (QM1).

## J. Supprimer la Miff (créateur)
| Cadre | Contenu |
|---|---|
| J1 Confirmation (`Sensitive`, `ShowExtra`) | Titre « Supprimer la Miff « Famille Moudugenou » ? » ; texte « Les 3 autres proches n'y auront plus accès et les lieux seront effacés. Cette action est définitive. » / « The 3 other members will lose access and the places will be deleted. This can't be undone. » (sans autre proche : « Les lieux seront effacés. Cette action est définitive. », **jamais « 0 proche »**) ; bouton secondaire « Prévenir mes proches » (QM9) ; principal « Supprimer la Miff » ; lien « Annuler » |
| J2 Message prérempli (feuille de partage native, **non dessinée**) | « Je ferme notre Miff « Famille Moudugenou » dans Miff : elle ne sera plus accessible. » / « I'm closing our Miff "Famille Moudugenou" in Miff: it will no longer be accessible. » Pas de code, pas d'identifiant. L'app ne sait pas qui l'a reçu. À dessiner comme encart explicatif (carte `bg/surface` + bulle de message) à titre indicatif seulement |
| J3 Progression | Dialogue sans bouton principal : « On finalise la suppression… » / « Finishing the deletion… » ; indicateur de progression ; bouton « Annuler » **absent** (la procédure est reprenable) ; le texte ne dit pas combien de proches restent |
| J4 Terminée | « La Miff a été supprimée. » / « The Miff has been deleted. » ; bouton « Fermer » / « Close » `[À CONFIRMER]` ; retour au Feed ou à l'état vide |
| J5 Interrompue puis reprise | `Notice Info` à la réouverture : texte `[À CONFIRMER]` (non fourni par la spec : proposition « On reprend la suppression de ta Miff… ») |

## K. Renouveler le code (créateur)
K1 Confirmation (`Default`) : titre « Créer un nouveau code ? » ; texte « L'ancien ne fonctionnera plus. Les membres actuels restent dans la Miff. » / « The old one will stop working. Current members stay in the Miff. » ; boutons « Créer un nouveau code », « Annuler ». K2 Succès : `CodeField Valid` mis à jour + `Toast` « Nouveau code créé » `[À CONFIRMER]` ; K3 hors connexion (`Notice Offline`). L'invitation discrète après un départ est décrite dans `miff-ecran-miff.md` (F2).

## L. Accès perdu / message au membre retiré (QM8, message unique)
Écran plein cadre sur fond `bg/canvas`, **pas de dialogue d'alarme**, sans mot « expulsé », « banni » ni « supprimé par » : logo discret, titre `display/headline` « Tu n'as plus accès à la Miff « Famille Moudugenou » », texte `body/regular` « Ta position n'est plus partagée avec ce cercle. » / « You no longer have access to the Miff "Famille Moudugenou". Your location is no longer shared with this circle. » ; bouton principal « Compris » / « Got it » `[À CONFIRMER : libellé]`. Le message est identique que la personne ait été retirée ou que la Miff ait été supprimée ; il n'indique jamais la cause (la personne n'est pas informée d'un retrait). Affiché **une seule fois**, sans boucle ; ensuite bascule vers une autre Miff ou l'état vide du Feed. Si la Miff n'a pas de nom disponible (cache purgé) : « Tu n'as plus accès à une de tes Miffs. » `[À CONFIRMER]`.

## M. Arrêter / reprendre le partage (promesse du texte d'entrée)
| Cadre | Contenu |
|---|---|
| M1 Arrêter, confirmation (`Default`) | Titre « Arrêter de partager ton statut avec cette Miff ? » ; texte « Ton statut et ta photo d'update seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. » / « Your status and update photo will be deleted for this circle. You can resume whenever you like. » ; boutons « Arrêter le partage » / « Annuler » |
| M2 Reprendre, confirmation | Titre « Reprendre le partage ? » ; texte « Tu partages à nouveau ton statut avec les membres de cette Miff ; tu peux arrêter à tout moment. » ; boutons « Reprendre le partage » / « Annuler » ; **même niveau de visibilité que l'écran d'entrée E** |
| M3 En cours / M4 hors connexion | Indicateur ; `Notice Offline` avec interrupteur désactivé |
| M5 Confirmation d'arrêt réussi | L'interrupteur bascule seulement après l'accusé du serveur ; pas de toast alarmant |

## Accessibilité
Focus initial sur le titre puis sur « Annuler » ; la touche retour/Échap ferme sans agir ; les boutons destructifs ne reposent pas sur la couleur seule ; texte `text/error` ≥ 5,4:1 sur blanc ; taille de texte système maximale : les boutons s'empilent et ne se chevauchent pas.

## Annexes plateformes
**Android** : boîtes de dialogue Material 3 (`AlertDialog`) ; « Retour » = Annuler ; feuille de partage via `ACTION_SEND` pour « Prévenir mes proches ».
**iOS** : `alert` / `confirmationDialog` ; action destructrice avec `role: .destructive` (rendu rouge natif, accepté) ; `ShareLink` pour « Prévenir mes proches » ; Annuler en dernier selon les conventions de la plateforme.

## Assets manquants
Aucun nouvel asset ; logo = composant existant, non modifié.
