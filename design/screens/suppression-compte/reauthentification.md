# C. Ré-authentification (écrans 05 et 06)

**Sources** : ticket §3.2 (C), §5.4 (méthodes, révocation Apple, échecs), §5.5 étape 1 et 8, §8, décision Q6 du 2026-11-05 (une ré-authentification au début, une seconde seulement si Firebase la réclame, aucun mot de passe gardé en mémoire) ; textes existants `auth_*`.
**Maquettes** : `05-reauthentification` (C1 vide, C2 saisi, C3 incorrect, C4 Google, C5 Apple iOS, C6 en cours) ; `06-reauthentification-etats` (C7 plusieurs méthodes, C8 mauvais compte Google, C9 trop d'essais, C10 hors connexion, C11 « Dernière vérification »).
**Quand** : après « Supprimer mon compte » du dialogue B ; et, rarement, à la fin de la procédure si Firebase réclame une connexion plus récente (C11).

## Contenu
Écran plein cadre (pas une fenêtre : tout reste visible avec le clavier), en-tête « Une dernière vérification » avec retour, fond `bg/canvas`.
- Pastille de cadenas + phrase : « Pour te protéger, confirme que c'est bien toi. » / "To keep you safe, please confirm it's you." (`settings_delete_reauth_body`).
- « Compte : laura@gmail.com » [proposé] : l'adresse déjà connue de la personne (aucun risque d'énumération).
- **Mot de passe** : champ encadré, étiquette « Mot de passe », œil d'affichage, lien « Mot de passe oublié ? » (44 pt). Saisie automatique autorisée.
- **Google** : bouton « Continuer avec Google » (composant officiel) ; phrase « Confirme avec le compte Google que tu utilises dans Miff. » [proposée].
- **Apple (iOS)** : bouton « Continuer avec Apple » (composant officiel) ; phrase « Apple va te demander de confirmer. Ensuite, Miff n'aura plus l'autorisation « Se connecter avec Apple ». » [proposée].
- Pied : « Confirmer » (principal, grisé tant que le champ est vide) + « Dès que tu confirmes, la suppression commence. » [proposé] + « Annuler ».

## Méthodes proposées
Seulement celles **réellement liées au compte** (lecture des fournisseurs de l'utilisateur) : mot de passe seul, Google seul, Apple seul (iOS), ou plusieurs (C7 : champ puis « Ou : » puis bouton). **Aucun bouton Apple sur Android** (décision Q1 de l'authentification). Un compte lié à Apple sur iOS passe **aussi** par la feuille Apple, même s'il se ré-authentifie par mot de passe (la révocation est obligatoire).

## États et textes
| État | Maquette | Texte FR | Texte EN | Comportement |
|---|---|---|---|---|
| Vide | C1 | — | — | « Confirmer » grisé |
| Saisi | C2 | — | — | actif ; œil pour afficher |
| Mauvais mot de passe | C3 | Ce mot de passe ne correspond pas. Tu peux réessayer. | That password doesn't match. You can try again. | contour 2 px + texte ; reste sur l'écran ; aucun compteur d'essais |
| Mauvais compte Google | C8 | Ce n'est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. | That isn't the account you're signed in with. Choose the right account to continue. | notice ; rien n'est supprimé |
| Trop d'essais | C9 | Un peu trop d'essais pour le moment. Patiente quelques minutes ou réinitialise ton mot de passe. | (clé `auth_error_too_many`, existante) | notice |
| Hors connexion | C10 | Pas de connexion pour le moment. Réessaie dès que tu as du réseau. | (clé `auth_error_network`, existante) | notice ; le bouton reste actif |
| Vérification en cours | C6 | Vérification… | Checking… | bouton occupé, « Annuler » inactif (un appui = une opération) |
| Fenêtre Google / Apple fermée | — | aucun message | — | écran intact |
| **Dernière vérification** | C11 | Dernière vérification avant de terminer. + Ta suppression est presque finie. | One last check before we finish. + Your deletion is almost done. | seconde demande (Firebase la réclame à la fin) ; **pas d'« Annuler »** (point de non-retour franchi) mais « Plus tard » : la suppression reprend au prochain lancement ; le mot de passe n'a pas été gardé, on le redemande |

Titre de l'écran : « Une dernière vérification » / "One last check" (`settings_delete_reauth_title`). Jamais de code technique, jamais de reproche.

## Règles
- Avant la réussite : **aucune écriture, aucun marqueur, aucun arrêt d'écoute** (SC9). « Annuler » et retour ramènent à A sans rien avoir modifié.
- Après la réussite : point de non-retour, écran de progression (D) ; sur iOS avec Apple, révocation tout de suite.
- « Mot de passe oublié ? » quitte la suppression (rien n'a commencé).
- Le mot de passe n'est jamais conservé, journalisé ni envoyé en événement (SC13, SC47).

## Accessibilité
Champs étiquetés (« Mot de passe », bouton « Afficher le mot de passe » / « Masquer ») ; erreurs annoncées au lecteur d'écran dès leur apparition ; l'erreur ne repose pas sur la couleur (contour + texte) ; focus initial sur le champ (ou sur le titre pour Google / Apple) ; cibles ≥ 44 pt ; contraste du texte d'erreur 5,4:1.

## Android / iOS
Android : champ avec saisie automatique du gestionnaire de mots de passe ; Google via le sélecteur de compte système (Credential Manager) ; aucun bouton Apple. iOS : feuille Apple officielle ; le code d'autorisation Apple de la réponse sert à la révocation (prérequis console, ticket §10).

## Assets manquants
Logos et boutons Google / Apple : composants officiels (la maquette ne dessine que l'emplacement). Icônes de cadenas et d'œil : provisoires de maquette, à exporter depuis Figma.
