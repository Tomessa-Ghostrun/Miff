# Lieux — « Modifier le lieu » et confirmation de suppression (écrans 06 et 07)

**Sources** : maquettes du PM `03` et `04` ; ticket `lieux-de-confiance.md` §6.1, §6.3, §11 (PL7, PL8, MIF2).
**Maquettes visuelles** : `mockups/06-modifier-lieu`, `mockups/07-confirmation-suppression` (planche de 4 états).
**Qui y accède** : le créateur, depuis « Modifier » du détail d'un lieu (écran 16B) ou depuis une ligne de la liste.

## 06 — Modifier le lieu
Même formulaire que `nouveau-lieu.md`, avec :
- Titre « Modifier le lieu » / "Edit place". Tous les champs sont préremplis et **modifiables, type compris** (décision QL16). Le sélecteur de Miffs est **présent** (les Miffs associées sont modifiables : décision Q4).
- **« Enregistrer » inactif tant que rien n'a changé** (comme l'écran de la Miff), puis actif aux mêmes conditions que la création.
- Sous « Enregistrer » : bouton **« Supprimer ce lieu »** / "Delete this place", ton `Sensitive` (fond blanc, contour et texte `text/error`).
- Passer le type à « Zone de danger » sur un compte gratuit : paywall (écran 15), le lieu reste inchangé.
- Si la position ou le rayon change : les téléphones le prennent en compte à leur prochaine relecture (texte de propagation du §6.4 affiché après enregistrement).
- Les lieux supprimés par un autre appareil entre-temps : message « Ce lieu n'existe plus. » [texte proposé] et retour à la liste.

## 07 — Confirmation de suppression
- Dialogue du composant `Dialog` (`miff-dialogues.md`), ton `Sensitive`, posé sur le formulaire voilé et flouté.
- Titre « Supprimer ce lieu ? » / "Delete this place?" (la maquette du PM disait « Etes-vous sûr de supprimer ce lieu ? »).
- Texte « Ce lieu sera définitivement supprimé pour toi et pour tous les membres des Miffs associées. » / "This place will be permanently deleted for you and for all members of the associated Miffs."
- **Lieu dans plusieurs Miffs** : la phrase devient « …pour tous les membres de ces Miffs : » suivie de la liste des Miffs (nom + pictogramme) ; la suppression a lieu dans **toutes** ses Miffs.
- Boutons : « Supprimer ce lieu » (contour rouge) ; lien « Annuler » (premier au focus, touche retour = Annuler).
- États : **en cours** (« Suppression… », indicateur, « Annuler » inactif) ; **hors connexion** (notice « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. », bouton actif, rien en file).
- Après succès : retour à la liste sans message supplémentaire (le lieu a disparu).

## Annexes plateformes
**Android** : `AlertDialog` Material 3. **iOS** : `alert` avec action `role: .destructive` (rendu rouge natif, accepté), « Annuler » placé selon les conventions de la plateforme.
