# Recette Android de PAY-UI-A (écran d'offre Miff+ et croix de fermeture après 3 secondes) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Durée** : environ 25 minutes.
**Ticket** : `docs/tickets/paywall-v2.md` (critères CR1 à CR14, PW1 à PW15, P1, P3 à P6). **Maquettes** : `design/exports/paywall/Paywall.png` et `Paywall-1.png`.
**Ce qui n'est PAS dans cette recette** : l'achat réel, l'essai gratuit, les prix (rien de payant n'est branché : SRV-7 et SRV-8 ne sont pas livrés ; le premium reste accordé à la main), l'iPhone (PAY-UI-I). **Les états « essai », « annuel / mensuel », « chargement », « erreur »… ne sont pas visibles sur le téléphone** : ils sont vérifiés par les tests automatiques et par les captures de la PR ; le téléphone ne montre que la variante **« Miff+ arrive bientôt »**.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (nom avec le SHA du commit, dans `android\build\apk-a-tester\`).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- Il vous faut **un compte gratuit** (sans document `entitlements`) **qui a déjà une Miff** (pour la 2e Miff), et **3 lieux de confiance** dans cette Miff (pour le 4e lieu).
- Un chronomètre (celui du téléphone) pour compter les 3 secondes.

## Partie A : les quatre entrées, la croix après 3 secondes (10 min)

Pour **chacune** des quatre entrées (étapes 1 à 4), contrôlez la même chose, dans l'ordre :
**(a)** à l'ouverture, **aucune croix** en haut à gauche (l'emplacement est vide) ; **(b)** le bouton **Retour** du téléphone (ou le geste) **ne fait rien** pendant les 3 secondes, sans message ; **(c)** après environ **3 secondes**, la croix **apparaît en fondu** (disque gris foncé avec une croix blanche), sans que le reste de l'écran bouge ; **(d)** à partir de là, la croix **ferme** l'écran, et le Retour du téléphone **aussi**.

1. [ ] **4e lieu** : Profil, « Ajouter un lieu » avec 3 lieux déjà créés (ou le formulaire d'un lieu) : l'écran d'offre s'ouvre avec la phrase « Tu as atteint 3 lieux ». Contrôles (a) à (d).
2. [ ] **2e Miff, sélecteur du Feed** : Feed, touchez le sélecteur de Miff puis « Créer une Miff » ou « Rejoindre une Miff » : phrase « Une seule Miff avec la formule gratuite ». Contrôles (a) à (d).
3. [ ] **2e Miff, carte du carrousel du Profil** : Profil, dernière carte du carrousel « Mes Miffs » : même écran. Contrôles (a) à (d).
4. [ ] **Zone de danger** : formulaire d'un lieu, type « Zone de danger » puis enregistrer : phrase « Les zones de danger font partie de Miff+ ». Contrôles (a) à (d).
5. [ ] **Nouvel affichage = nouveau départ** : fermez l'offre (après la croix), rouvrez-la **tout de suite** (même 1 seconde plus tard, même depuis une autre entrée) : la croix est **de nouveau absente pendant 3 secondes**.
6. [ ] **Pas d'autre sortie** : pendant les 3 secondes, il n'y a **aucun** bouton « Plus tard » ni « Continuer en gratuit » ; en touchant l'emplacement vide de la croix, rien ne se passe.

## Partie B : la croix reste, rien ne rejoue le délai (5 min)

7. [ ] **Rotation** : ouvrez l'offre, attendez que la croix soit là, faites pivoter le téléphone (portrait, paysage, portrait) : la croix **reste**, **sans** attendre à nouveau 3 secondes.
8. [ ] **Arrière-plan après la croix** : croix visible, passez à une autre app, revenez : la croix est toujours là.
9. [ ] **Arrière-plan avant la fin** : ouvrez l'offre, passez à une autre app **avant** 3 secondes, restez 10 secondes dans l'autre app, revenez : la croix apparaît **tout de suite ou presque** (le temps a continué de couler).
10. [ ] **Taille de texte et langue** : une fois la croix visible, mettez la police au maximum (Réglages du téléphone > Affichage), puis revenez : la croix est là, ne bouge pas. Même chose en passant l'app en anglais.
11. [ ] **Animations coupées** (Options pour les développeurs > échelle de durée des animations : désactivée) : la croix apparaît **d'un coup**, sans fondu.

## Partie C : lecteur d'écran et accessibilité (5 min)

12. [ ] **TalkBack activé** : à l'ouverture, en explorant l'écran, **aucun** « Fermer » n'est annoncé ; vers 3 secondes TalkBack dit **« Tu peux maintenant fermer cet écran »** (une seule fois, sans que le focus saute) ; ensuite en remontant en haut de l'écran, le bouton **« Fermer »** existe.
13. [ ] **Pas piégé** : avec TalkBack, on peut lire tout l'écran (titre, trois cartes, « Miff+ arrive bientôt ») pendant les 3 secondes.
14. [ ] **Plus grande police** : tout reste lisible, rien n'est coupé, l'écran défile, la croix reste facile à toucher.

## Partie D : contenu de l'écran « Miff+ arrive bientôt » (5 min)

15. [ ] **Fidélité aux maquettes** : fond en dégradé clair, logo Miff en haut, titre en deux lignes bleu-vert, **trois** cartes blanches avec pastille bleue (lieux, Miffs, zones de danger). Comparez avec `Paywall.png` (les écarts sont listés dans la PR).
16. [ ] **« Notifications intelligentes » n'apparaît nulle part.**
17. [ ] **Aucun prix, aucun « € », aucun bouton « Commencer l'essai », aucun « Économisez » / « presque 30 % », aucun bouton « Plus tard », aucun lien « Restaurer »** : seulement « Miff+ arrive bientôt » et la phrase calme (« Tes lieux actuels restent en place. » ou « Ta Miff actuelle reste en place. »).
18. [ ] **Ton** : tutoiement partout, rien d'alarmant ; essayez l'anglais.

## Partie E : analytique (facultatif, développeur)

19. [ ] Avec les statistiques **acceptées** (Réglages > Confidentialité), la console Firebase (DebugView) montre `paywall_shown` **une fois** par ouverture (pas à la rotation), puis `paywall_dismissed` à la fermeture, avec le seul paramètre `trigger` (`extra_trusted_place`, `extra_miff` ou `danger_zone`). **Statistiques refusées : aucun événement.** Aucun paramètre de durée, de position ni de nom.

## Résultat

Étapes en échec : ______ . Points à corriger : ______ . Date et appareil : ______ .

*Risque assumé par le PM (décision du 2026-11-12) : une croix différée peut être jugée trompeuse par une revue de store ou des utilisateurs ; la durée est une seule valeur (`PaywallCloseDelay.MILLIS`), à ramener à 0 par une petite PR si besoin. Aucun texte n'est relu par un juriste (décision du 2026-10-23).*
