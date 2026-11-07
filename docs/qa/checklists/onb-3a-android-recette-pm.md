# Recette Android d'ONB-3a (onglets, Profil, Réglages) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise.
**Durée** : environ 20 minutes.
**Ce que vous testez** : la barre d'onglets, l'onglet Profil, les Réglages (Mes informations, Mon compte, Langue). Le Feed est **volontairement provisoire** (un message honnête, pas de vrais proches) : ne le comptez pas comme un défaut. « Mon compte » ne contient pour l'instant que votre e-mail et « Se déconnecter » (le reste arrive avec la PR ONB-3b).
**Rappel** : les maquettes ont des écarts voulus (liste dans la description de la PR) ; ne les comptez pas comme des défauts.

## Règles à retenir

- **Le mot de passe du compte de test ne se tape que sur le téléphone**, jamais dans une conversation ni dans un fichier.
- Si quelque chose ne se passe pas comme écrit : **capture d'écran** (Marche + Volume bas), notez le numéro de l'étape, passez à la suivante.
- Ne diffusez pas l'APK en dehors des testeurs.

## Avant de commencer (sur ordinateur)

1. Récupérez l'**APK de test** de la PR « ONB-3a » : page de la PR > onglet Checks > exécution `android` > zone **Artifacts** > **miff-debug-apk** (ZIP à extraire ; il disparaît au bout de 3 jours). Ou `gh run download <numéro de l'exécution> -n miff-debug-apk`.
2. Installez-le sur le téléphone **par-dessus** la version précédente si la signature est la même ; sinon désinstallez l'ancienne d'abord.
3. Un compte de test **avec profil complet** (prénom, nom, numéro) : par exemple celui créé à la recette d'ONB-2.

## Partie A — Accéder aux onglets (3 min)

L'app n'a pas encore d'écran « Créer ou rejoindre une Miff » : sans Miff, vous restez sur l'écran provisoire (c'est voulu, sans barre d'onglets).

- [ ] Ouvrez l'app, **Ouvrir l'application** (écran de diagnostic), connectez-vous avec le compte de test.
- [ ] Vous arrivez sur l'écran « Prochaine étape : créer ou rejoindre une Miff » **sans barre d'onglets**.
- [ ] Touchez **Aperçu interne : ouvrir les onglets et les Réglages** (ce bouton n'existe que dans la version de test).
- **Résultat attendu** : la barre d'onglets apparaît en bas (carte blanche arrondie) avec trois icônes : maison avec un cœur (sélectionnée, fond bleu pâle), un rond jaune-vert un peu atténué (**désactivé**), une silhouette.

## Partie B — Barre d'onglets et Feed provisoire (4 min)

- [ ] L'écran Feed affiche le logo Miff, **« Ton fil n'est pas encore prêt »** et une phrase d'explication. **Pas de cloche, pas de personne inventée, pas de « tout le monde est en sécurité ».**
- [ ] Touchez le rond du milieu : **il ne se passe rien** (normal : « Donner des nouvelles » arrive avec son propre ticket).
- [ ] Touchez la silhouette : l'onglet Profil s'ouvre, l'icône prend le fond bleu pâle. Revenez au Feed : il est resté tel quel.
- [ ] Faites pivoter le téléphone puis revenez : vous êtes toujours sur le même onglet.
- [ ] Bouton ou geste **retour** sur un onglet : l'app se ferme (revient à l'écran d'accueil du téléphone), sans erreur.

## Partie C — Onglet Profil (2 min)

- [ ] En haut : votre **photo** (ou vos initiales si vous n'en avez pas), **Prénom Nom**, « **C'est toi** », et une **roue crantée** à droite.
- [ ] **Rien d'autre** sous cet en-tête (pas de « Famille », pas de lieux : ils viennent avec les tickets Miff et Lieux).
- [ ] Touchez votre photo ou votre nom : **Mes informations** s'ouvre.

## Partie D — Réglages (2 min)

- [ ] Revenez au Profil, touchez la roue crantée : l'écran **Réglages** a **trois** lignes : Mes informations, Mon compte, Langue. **Pas** de ligne « Notifications », **pas** d'encart « essai gratuit » ni de prix, **pas** de « Supprimer mon compte ».
- [ ] La flèche en haut à gauche et le geste retour vous ramènent au Profil.

## Partie E — Mes informations (6 min)

- [ ] Les champs sont : photo (avec un petit crayon), Prénom, Nom, Pays, **Téléphone** (et non « Nom »). Les compteurs affichent le **vrai nombre** de caractères (par exemple « 5/50 » pour « Laura »). Une phrase explique que seuls les membres de vos Miffs voient le numéro.
- [ ] **« Enregistrer » est grisé** tant que vous n'avez rien changé.
- [ ] Changez le prénom : « Enregistrer » devient actif. Remettez l'ancien : il se grise de nouveau.
- [ ] Effacez tout le prénom : un message calme apparaît (« Écris au moins un caractère. »), « Enregistrer » reste grisé. **Il n'y a pas de minimum de 3 caractères** : un prénom d'une lettre est accepté.
- [ ] Tapez 60 lettres dans le nom : le champ s'arrête à 50 et dit « 50 caractères maximum. ».
- [ ] Saisissez un numéro faux (par exemple « 123 ») puis touchez ailleurs : message « Ce numéro ne semble pas correct. », bouton grisé. Corrigez : il se réactive.
- [ ] Changez la photo (galerie **ou** appareil photo) : un écran de recadrage s'affiche, **aucune demande d'autorisation** n'apparaît. Validez : la nouvelle photo est dans le cercle.
- [ ] Touchez **Enregistrer** : « C'est enregistré. » ; le bouton se grise.
- [ ] Revenez au Profil (flèche) : **le nouveau prénom et la nouvelle photo y sont déjà**.
- [ ] Modifiez quelque chose puis touchez la flèche retour **sans enregistrer** : une fenêtre demande « Quitter sans enregistrer ? » avec deux boutons. « Rester ici » vous laisse sur l'écran ; « Quitter » abandonne les changements.
- [ ] Activez le **mode avion**, modifiez un champ, touchez Enregistrer : message calme « Pas de connexion pour le moment… », **vos saisies restent**. Désactivez le mode avion, réessayez : ça passe.
- [ ] (Pour Claude, dans la console Firebase, avec votre accord) vérifier que `users/<uid>` contient le nouveau nom et un numéro au format `+33…` sans espace.

## Partie F — Langue (3 min)

- [ ] Réglages > **Langue** : trois choix : **Français**, **English**, **Langue du téléphone**. Pas d'espagnol, d'allemand, de portugais ni d'italien. Pas de bouton « Enregistrer ».
- [ ] Touchez **English** : **toute l'app passe en anglais tout de suite** (titres, boutons, barre d'onglets lue par TalkBack). Revenez en arrière : les écrans sont en anglais.
- [ ] Touchez **Français**, puis **Langue du téléphone** : l'app suit la langue du téléphone.
- [ ] Android 13 : dans les réglages du téléphone > Applications > Miff > Langue, la langue choisie dans l'app est bien celle indiquée.
- [ ] Fermez complètement l'app et rouvrez-la : le choix est gardé.

## Partie G — Mon compte et déconnexion (2 min)

- [ ] Réglages > **Mon compte** : votre **e-mail** est affiché, on ne peut **pas** le modifier. Un seul bouton : **Se déconnecter** (blanc à bord bleu, pas le gros bouton plein).
- [ ] Touchez-le : « **Te déconnecter ?** Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). » avec **Se déconnecter** et **Annuler** (même poids). Annuler : rien ne change.
- [ ] Confirmez : retour à l'écran de **connexion** (sans le carrousel de bienvenue). Mode avion + relance de l'app : **aucune donnée** de votre profil n'apparaît.
- [ ] Reconnectez-vous avec un **autre** compte : vous ne voyez **pas** le nom ni la photo du compte précédent.

## Partie H — Accessibilité (3 min, facultatif mais utile)

- [ ] **TalkBack** activé : la barre d'onglets annonce « Fil de ta Miff, onglet, sélectionné », « Profil, onglet » ; le rond du milieu est annoncé « bientôt disponible, désactivé ». La roue crantée est annoncée « Réglages ». La photo n'est pas lue comme une image.
- [ ] **Taille de police maximale** (Réglages du téléphone > Affichage) : tous les textes restent lisibles et le bouton « Enregistrer » reste atteignable en faisant défiler.

## À signaler à Claude

Toute différence avec ce qui est écrit, avec capture d'écran et numéro de partie. En particulier : une barre d'onglets qui cache du contenu, un texte qui reste en français après le passage en anglais, un nom ou une photo qui ne se met pas à jour dans le Profil après l'enregistrement.

## Ce qui n'est PAS testable ici (volontairement)

- Le bouton central « Donner des nouvelles », le vrai Feed, les Miffs et les lieux du Profil (autres tickets).
- Le changement de mot de passe, la page Confidentialité et les liens légaux (PR ONB-3b).
- Les copies de votre profil dans les Miffs : sans écran « Créer une Miff », elles ne peuvent être vérifiées qu'avec une Miff créée à la main dans la console (Claude peut préparer le test avec votre accord).
- La version release sans bouton central : la barre n'a alors que deux onglets (vérifiée par un test automatique).
