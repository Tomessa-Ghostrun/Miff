# Entrée « Supprimer mon compte » (écran 01)

**Sources** : ticket `docs/tickets/suppression-compte.md` §3.2 (note sous le tableau), §5.1 ; Android `settings_account_*` (écran « Mon compte » existant : adresse e-mail non modifiable, « Changer mon mot de passe », « Connecté avec Google », « Se déconnecter »).
**Maquette** : `mockups/01-entree-mon-compte` (A mot de passe, B Google, C Apple sur iOS).

## Contenu
Écran « Mon compte » existant, en-tête avec retour. De haut en bas : adresse e-mail (lecture seule, « Elle ne peut pas être modifiée ici. ») ; bloc propre à la méthode (compte à mot de passe : « Changer mon mot de passe » et son explication ; compte Google : ligne « Connecté avec Google » ; compte Apple : « Connecté avec Apple » [proposé]) ; **« Se déconnecter »** (bouton principal) ; **« Supprimer mon compte »** en **lien texte**, centré, tout en bas, sous « Se déconnecter ».

| Élément | FR | EN |
|---|---|---|
| Lien | Supprimer mon compte | Delete my account |
| Ligne Apple [proposé] | Connecté avec Apple | Signed in with Apple |

## Règles
- Visible pour **toute personne connectée** (avec ou sans Miff, avec ou sans Miff+). ONB-3b la masque jusqu'à AUTH-5a : l'activer.
- Style discret : jamais un bouton plein, jamais plus visible que « Se déconnecter » ; couleur `text/error` (#d60700, contraste 5,4:1 sur le fond) ; zone de toucher de 44 pt de haut au minimum.
- Un toucher ouvre l'écran A (information) ; rien n'est supprimé ni écrit à cette étape.
- Ton : aucune mise en garde ici, elle vient à l'écran A.

## Accessibilité
Lecteur d'écran : « Supprimer mon compte, bouton » (le rôle est annoncé, pas seulement la couleur). Taille de police maximale : le lien passe à la ligne sans masquer le reste, l'écran défile. Ordre de focus : e-mail, méthode, « Se déconnecter », « Supprimer mon compte ».

## Android / iOS
Identique. Android : retour système = retour aux Réglages. iOS : geste de retour du bord gauche. Le bloc « Connecté avec Apple » n'existe que sur iOS ou pour un compte lié à Apple.
