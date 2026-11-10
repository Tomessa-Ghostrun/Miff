# Recette iOS : Feed sans connexion, pop-in « Pas de connexion » avec réessai automatique — parcours pour le PM

**Pour qui** : vous, sur l'iPhone 11 Pro (iOS 26.5.2), avec un compte de test déjà dans une Miff.
**Durée** : environ 15 minutes.
**Principe** : on coupe le réseau avec le **mode avion** (Wi-Fi et données coupés), on ouvre l'app, et on vérifie qu'elle **dit calmement qu'il n'y a pas de connexion** au lieu de tourner sans fin. Aucune console n'est nécessaire.
**Build à installer** : le build **Internal** de la PR, par TestFlight (lancé par l'assistant après la revue).

## Ce qui change pour vous

- Sans réseau et **sans rien à montrer** (ni contenu, ni copie gardée sur le téléphone), le Feed (et l'onglet Profil) affichent une **petite fenêtre** : « **Pas de connexion** — Vérifie ta connexion, on réessaie dans un instant. » avec un bouton **Réessayer**.
- L'app **réessaie toute seule toutes les 10 secondes**, sans rien lire tant que le réseau n'est pas revenu. Dès que la connexion revient, la fenêtre **disparaît d'elle-même** et le Feed se charge.
- Quand le téléphone **a déjà des informations en mémoire**, rien ne change : le Feed les montre avec « Dernières informations de… » et la bande « Pas de connexion pour le moment ».

## Règles à retenir

- Un défaut : capture d'écran (ou enregistrement d'écran) et numéro de l'étape, puis étape suivante.
- Dans la partie A, le Feed doit s'afficher **vite** (une à deux secondes) et **sans jamais montrer la fenêtre** : c'est le cas du PM (informations en mémoire, pas de réseau).

## Partie A : pas de réseau, avec des informations en mémoire (comportement inchangé)

- [ ] Réseau actif : ouvrez l'app, laissez le Feed se charger (la Miff, les cartes). Quittez complètement l'app (balayage vers le haut dans le sélecteur d'applications).
- [ ] Activez le **mode avion**, rouvrez l'app. Le Feed affiche la Miff avec **« Dernières informations de … »** et la bande grise « Pas de connexion pour le moment… ». **Aucune** fenêtre « Pas de connexion » ne masque l'écran.
- [ ] Aucune couleur verte sans date. Le défilement et l'en-tête fonctionnent comme d'habitude.

## Partie B : la fenêtre, sans informations en mémoire

Cas difficile à provoquer à la main : le téléphone garde en mémoire tout ce qu'il a déjà affiché. Voici la façon la plus simple d'y arriver.

- [ ] **Compte neuf sur le téléphone** : déconnectez-vous, connectez-vous avec un **autre compte** de test qui est dans une Miff (avec réseau), et **activez le mode avion dès que le Feed affiche son squelette**, avant que les cartes apparaissent. Si le Feed s'est déjà chargé, recommencez avec un autre compte ou considérez que ce test est couvert par les tests automatiques.
- [ ] Ouvrez ou rouvrez le Feed en mode avion : après **environ 2 secondes**, la fenêtre « **Pas de connexion** » apparaît, avec « Vérifie ta connexion, on réessaie dans un instant. » et le bouton **Réessayer**. Le ton est calme : pas de rouge, pas de point d'exclamation.
- [ ] Touchez **Réessayer** (toujours en mode avion) : un petit indicateur tourne un instant dans le bouton, **la fenêtre reste**. Vous pouvez toucher plusieurs fois sans rien casser.
- [ ] Attendez **30 secondes** sans toucher : la fenêtre reste, sans clignoter ni se multiplier (**une seule** fenêtre à la fois).

## Partie C : le retour du réseau

- [ ] Fenêtre affichée, **désactivez le mode avion** : la fenêtre **disparaît d'elle-même** (au plus quelques secondes) et le Feed se charge. Vous n'avez rien eu à toucher.
- [ ] Recommencez : mode avion, fenêtre affichée, désactivez le mode avion et **touchez Réessayer** avant qu'elle ne parte : le Feed se charge, la fenêtre part.
- [ ] Recommencez : mode avion, fenêtre affichée, **attendez 10 à 15 secondes** puis désactivez le mode avion : le Feed se charge au plus 10 secondes après le retour du réseau (essai automatique), sans action de votre part.

## Partie D : quitter l'écran arrête tout

- [ ] Mode avion, fenêtre affichée sur le **Feed**. Touchez l'onglet **Profil** (barre du bas, restée utilisable) : la fenêtre du Feed n'est plus là. Revenez au Feed : elle revient (si toujours rien à montrer).
- [ ] Mode avion, fenêtre affichée : mettez l'app en **arrière-plan** (bouton d'accueil), désactivez le mode avion, **attendez 30 secondes**, puis rouvrez l'app : le Feed se charge normalement (pas de fenêtre figée).

## Partie E : l'onglet Profil

- [ ] Sans informations en mémoire pour le profil, mode avion, ouvrez l'onglet **Profil** : la même fenêtre « Pas de connexion » apparaît par-dessus l'onglet. Désactivez le mode avion : elle disparaît et la photo et le nom s'affichent.

## Partie F : accessibilité (si vous voulez la vérifier)

- [ ] **VoiceOver** (Réglages > Accessibilité) : à l'apparition de la fenêtre, une voix dit « Pas de connexion. Vérifie ta connexion, on réessaie dans un instant. » ; le focus reste dans la fenêtre (titre, phrase, bouton Réessayer) ; pendant un essai, le bouton dit « Nouvel essai en cours ».
- [ ] **Taille de texte** maximale (Réglages > Accessibilité > Affichage et luminosité > Taille du texte, curseur au maximum) : la fenêtre reste lisible, le texte défile si besoin et le bouton reste accessible.
- [ ] **Réduire les animations** activé : la fenêtre apparaît et disparaît en fondu, sans mouvement.

## Partie G : ce qui ne doit JAMAIS arriver

- [ ] Un chargement qui tourne **sans fin** sans fenêtre quand le mode avion est actif et que rien n'est en mémoire.
- [ ] Deux fenêtres l'une sur l'autre, ou une fenêtre qui clignote.
- [ ] Une fenêtre « Pas de connexion » alors que le Feed montre déjà des informations en mémoire.
- [ ] Un texte alarmiste (« erreur », « échec », point d'exclamation, rouge).
- [ ] Le téléphone qui chauffe ou la batterie qui chute à cause d'essais en boucle (les essais sont espacés de 10 s et ne lisent rien sans réseau).

