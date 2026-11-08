# Recette iOS : photo Google à l'inscription et retour par geste — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro, avec une version envoyée par TestFlight (branche `ios/photo-google-geste-retour`, mode **signed**).
**Durée** : environ 20 minutes.
**Ce que vous testez** : (1) l'inscription avec Google reprend la photo du compte Google comme photo de profil préremplie ; (2) le geste standard d'iPhone (glisser depuis le **bord gauche** de l'écran vers la droite) ramène à l'écran précédent partout où il y a une flèche de retour.
**Pas de glissement de droite à gauche** : le geste d'iOS pour revenir en arrière part du bord gauche et va vers la droite ; c'est celui qui est implémenté (écart avec la formulation du retour du PM, signalé dans la PR).
**Matériel** : un compte Google **avec** photo, un compte Google **sans** photo (silhouette), un compte e-mail, si possible un compte Google qui a déjà un profil.
**Rappel de sécurité** : mots de passe de test uniquement sur le téléphone.

## A. Photo Google

| # | Action | Résultat attendu |
|---|---|---|
| A1 | Se déconnecter. « Continuer avec Google » avec le compte Google AVEC photo, jamais inscrit sur Miff. Accepter les conditions. | À l'étape profil, le prénom et le nom sont préremplis **et la photo ronde montre votre photo Google** ; dessous, la phrase « On a repris ta photo Google, tu peux la changer. » |
| A2 | Toucher la photo. | « Choisir une autre photo » et « Supprimer la photo ». |
| A3 | Supprimer la photo. | La photo disparaît, l'appareil photo revient, la phrase aussi. |
| A4 | Fermer l'app (balayage) et la rouvrir. | Vous revenez à l'étape profil **sans photo** (votre choix est respecté, elle n'est pas remise). |
| A5 | Refaire A1 avec un autre compte Google, choisir une autre photo dans la photothèque, recadrer, valider. | Votre photo remplace celle de Google ; plus de phrase. |
| A6 | Terminer l'inscription (prénom, nom, téléphone). Ouvrir l'onglet Profil, puis Réglages > Mes informations. | La photo gardée est celle de votre profil (Google ou choisie), nette, ronde. Les autres membres d'une Miff la voient aussi. |
| A7 | Compte Google SANS photo (silhouette). | Aucune photo préremplie, aucune phrase, aucun message d'erreur. |
| A8 | Si possible : couper le réseau juste après avoir choisi le compte Google (la photo ne peut alors pas être téléchargée). | L'inscription continue sans photo, sans message alarmiste. |
| A9 | Compte **e-mail** : s'inscrire. | Comme avant : pas de photo préremplie. |
| A10 | « Continuer avec Apple » (si disponible). | Comme avant : pas de photo préremplie. |
| A11 | Se connecter avec un compte Google qui a **déjà un profil** et une photo. | Aucun écran de profil, la photo existante n'est **pas** remplacée (Réglages > Mes informations). |

## B. Retour par geste

Pour chaque ligne : ouvrir l'écran, glisser depuis le bord gauche vers la droite.

| # | Écran | Résultat attendu |
|---|---|---|
| B1 | Connexion ou inscription ouverte depuis le carrousel de bienvenue | Retour au carrousel. |
| B2 | « Mot de passe oublié » | Retour à la connexion. |
| B3 | Profil > Réglages (roue crantée), puis Mon compte, Confidentialité, Aide détection, Mes informations | Retour d'un écran à chaque glissement. |
| B4 | Écran d'une Miff (depuis « Mes Miffs » ou le Feed) | Retour à l'onglet d'où vous venez. |
| B5 | Nouveau lieu / Modifier le lieu ; détail d'un lieu | Retour à l'écran précédent. Pendant « Enregistrement… » ou « Suppression… » : **pas de retour**. |
| B6 | Créer une Miff : saisie du nom | Retour au Feed/Profil. Pendant « Création… » : **pas de retour**. « Ta Miff est prête » : **pas de retour** (« Continuer » est la sortie). |
| B7 | Rejoindre : saisie du code | Retour. Confirmation « Rejoindre et partager » : **pas de retour** (« Annuler » est la sortie). |
| B8 | Réglages > Mon compte > Supprimer mon compte (information), puis la vérification | Retour à l'écran précédent ; pendant « Vérification… » : **pas de retour**. |
| B9 | Profil à l'inscription, étape téléphone | Retour à l'étape profil, vos saisies sont gardées. Pendant l'enregistrement : **pas de retour**. |
| B10 | Compte SANS Miff : roue crantée > Réglages restreints > Mon compte | Retour à « Pas encore de Miff ». |
| B11 | Sur un écran racine (Feed, Profil, carrousel, première étape du profil) glisser depuis le bord | **Rien ne se passe** (pas de blocage de l'écran). |
| B12 | Glisser à moitié puis relâcher | L'écran revient à sa place, rien de cassé. |
| B13 | Tout de suite après avoir ouvert un écran (pendant l'animation), glisser | Pas de blocage ni d'écran vide. |

## Si quelque chose cloche

Capture d'écran + numéro de ligne. Les lignes B5 à B9 « pas de retour » sont voulues, pas des défauts.
