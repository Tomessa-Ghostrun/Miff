# B. Dialogue de confirmation (écran 04)

**Sources** : ticket §3.2 (B), §5.3 point 4, §8 ; composant `Dialog` `Tone=Sensitive` de `design/screens/miff-dialogues.md` ; planche 07 des maquettes Lieux (suppression d'un lieu).
**Maquette** : `mockups/04-confirmation` (B1 standard, B2 créateur, B3 réseau coupé).
**Quand** : après « Continuer » sur l'écran A.

## Contenu
Dialogue centré sur fond voilé (l'écran A flouté derrière), contour bleu 2 px, arrondi `radius/md`.
- Titre : « Supprimer ton compte ? » / "Delete your account?" (`settings_delete_confirm_title`).
- Texte : « Ton compte et tes données Miff seront supprimés. Ce n'est pas réversible. » / "Your account and your Miff data will be deleted. This can't be undone." (`settings_delete_confirm_body`).
- **Variante créateur** [proposée] (`settings_delete_confirm_body_owner`) : « Ton compte, tes données Miff et tes Miffs seront supprimés. Ce n'est pas réversible. » / "Your account, your Miff data and your Miffs will be deleted. This can't be undone."
- Deux boutons **de même taille (44 pt) et de même poids** : **« Supprimer mon compte »** (`Sensitive` : contour et texte `text/error` #d60700 **sur fond blanc, jamais de fond rouge**) ; **« Garder mon compte »** (contour bleu). Le sens est porté par le texte, pas par la couleur seule.

## États
| État | Maquette | Comportement |
|---|---|---|
| Standard | B1 | « Supprimer mon compte » ouvre la ré-authentification (C) |
| Créateur | B2 | texte avec « tes Miffs » ; mêmes boutons |
| Réseau coupé entre A et B | B3 | notice « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » ; le bouton reste actif ; rien n'est mis en file |

## Règles
- Une ré-authentification suit : **rien n'est écrit tant qu'elle n'a pas réussi** (SC9).
- Fermer le dialogue (toucher à côté, retour système, « Garder mon compte ») : retour à A, rien n'a été touché (SC4). Aucun reproche, aucun « Êtes-vous sûr ? ».
- Aucun délai de réflexion ni mot à retaper (décision du ticket §5.1).

## Accessibilité
Dialogue d'alerte : le titre est lu d'abord, puis le texte ; **premier focus sur « Garder mon compte »** (le choix sûr) ; boutons ≥ 44 pt ; l'ordre de lecture reste « Supprimer » puis « Garder » ; le piège de focus garde la navigation dans le dialogue.

## Android / iOS
Android : `AlertDialog` Material 3, retour système = « Garder mon compte ». iOS : `alert` avec action `role: .destructive` (rendu rouge natif accepté) ; « Garder mon compte » en `cancel` (dernier selon la convention de la plateforme, même texte).
