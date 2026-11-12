# Recette iOS de l'écran d'offre Miff+ (PAY-UI-I, paywall v2) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**, avec **un compte gratuit** (sans Miff+) qui a **au moins une Miff** et des lieux de confiance.
**Ce que vous testez** : l'écran d'offre Miff+ **fidèle à vos deux maquettes** (`design/exports/paywall/Paywall.png` et `Paywall-1.png`), présenté en **plein écran**, et **votre règle** : à **chaque affichage**, quel que soit le point d'entrée, la **croix de fermeture n'apparaît que 3 secondes après l'apparition de l'écran**.
**Spec** : `docs/tickets/paywall-v2.md` (fait foi), `docs/specs/paywall-premium.md`, `docs/tickets/premium-achat-reel.md`. **Durée** : environ 20 minutes. La recette Android correspond à PAY-UI-A.
**À savoir** : **aucun achat n'existe encore** (SRV-7 et SRV-8 ne sont pas livrés, Miff+ reste accordé à la main). Sur le téléphone vous ne verrez donc que la variante **« Miff+ arrive bientôt »** : **aucun prix, aucun bouton d'essai**. Les états avec prix (essai, deux formules, chargement, erreurs, abonné) sont dans les **captures de la CI** de la PR (pas sur le téléphone avant SRV-8).

## Avant de commencer

- Installez le **build TestFlight** de la PR iOS `ios/paywall-v2` (CI `ios-testflight`, lancée par vous). **Projet Firebase : `miff-3d283` uniquement.**
- Le compte est **gratuit** (Réglages > Abonnement n'existe pas encore : un compte accordé Miff+ à la main n'atteint pas l'offre).
- **Une seconde** : pour mesurer les 3 secondes, comptez « un, deux, trois » à voix basse ou filmez l'écran. L'écart toléré est d'une demi-seconde environ.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie 1 : fidélité aux maquettes (5 min)

1. [ ] Ouvrez l'offre par **un lieu au-delà de la limite gratuite** (Miff > « Ajouter un lieu » ; aujourd'hui **2 lieux sur iPhone**, voir « Écarts »). L'écran s'ouvre **en plein écran** (il monte depuis le bas, sans barre de navigation).
2. [ ] Le **fond en dégradé clair**, le **logo Miff** en haut, la **phrase de contexte** (« Tu as atteint 2 lieux »), le **titre sur deux lignes en dégradé bleu-vert** (« Débloque l'ensemble des fonctionnalités de Miff ! »), puis **trois cartes** blanches à pastille bleue : **Lieux de confiance illimités**, **Nombre de Miffs illimité**, **Zones de danger**. **Il n'y a PAS de carte « Notifications intelligentes »** (décision du 2026-11-12 : elles ne sont pas livrées).
3. [ ] Sous les cartes : **« Miff+ arrive bientôt »** et « Cette option n'est pas encore ouverte. Tes lieux actuels restent en place. » **Aucun prix, aucun bouton d'essai, aucun bouton « Plus tard »**, aucun lien « Restaurer ». Le tutoiement partout.
4. [ ] Changez la langue du téléphone en **anglais** : les mêmes textes en anglais (« Miff+ is coming soon »).

## Partie 2 : la croix de 3 secondes, aux quatre points d'entrée (8 min)

Pour **chaque** entrée : à l'ouverture, **la croix n'est pas là** (un espace vide en haut à gauche, rien ne bouge quand elle arrive) ; **après environ 3 secondes** elle **apparaît en fondu** (disque gris foncé, croix blanche), et **reste**.

5. [ ] **Lieu au-delà de la limite** (Miff > « Ajouter un lieu »).
6. [ ] **Zone de danger** : formulaire d'un lieu, choisissez le type « zone de danger » (réservée à Miff+) : même comportement. La phrase de contexte dit « Les zones de danger font partie de Miff+ ».
7. [ ] **2e Miff par le sélecteur du Feed** : touchez le nom de la Miff en haut du Feed, puis « Créer une Miff » ou « Rejoindre une Miff » : même comportement. La phrase de contexte dit « Une seule Miff avec la formule gratuite ».
8. [ ] **Nouveau départ à chaque affichage** : fermez l'offre (croix) et **rouvrez-la tout de suite** (même entrée, puis une autre) : **la croix est de nouveau absente 3 secondes**.
9. [ ] **Glissement** : pendant les 3 premières secondes, **tirez l'écran vers le bas** et glissez depuis le bord gauche : **rien ne ferme** l'écran. **Après** les 3 secondes, la croix ferme l'écran (touchez-la).
10. [ ] **Aucune autre sortie** : pendant les 3 secondes, **aucun** bouton, lien ni geste ne ferme l'écran.
11. [ ] **Arrière-plan** : ouvrez l'offre, **quittez l'app immédiatement** (bouton accueil) et revenez **après 5 secondes** : **la croix est là** (le temps compte, même app en arrière-plan). Ouvrez-la de nouveau, attendez la croix, **mettez l'app en arrière-plan puis revenez** : la croix **reste**, le délai n'est **pas rejoué**.
12. [ ] **Mode sombre, rotation, texte plus grand** (après l'apparition de la croix) : la croix **reste**.
13. [ ] **Hors connexion** (mode avion) : l'offre s'ouvre quand même et **la croix apparaît** au bout de 3 secondes.

## Partie 3 : texte très grand et accessibilité (5 min)

14. [ ] Réglages iPhone > Accessibilité > Taille du texte : **la plus grande** (curseur « Textes plus grands » au maximum). Ouvrez l'offre : **rien n'est tronqué**, **l'écran défile**, la croix (après 3 s) a toujours une **zone tactile généreuse** et ne chevauche aucun texte.
15. [ ] **VoiceOver** (Réglages > Accessibilité > VoiceOver) : à l'ouverture, **la croix n'est pas lue** pendant les 3 secondes (explorez l'écran : aucun élément « Fermer »). À l'apparition de la croix, VoiceOver dit **« Tu peux maintenant fermer cet écran »** (**une seule fois**, **sans déplacer** votre position de lecture). Ensuite, « **Fermer** » est un bouton en tête d'écran. **Vous n'êtes jamais bloqué** : on peut explorer tout l'écran, avant comme après.
16. [ ] **Réduire les animations** (Réglages > Accessibilité > Mouvement > Réduire les animations) : la croix apparaît **instantanément** (sans fondu) au bout de 3 secondes.

## Partie 4 : ce que cette recette ne couvre pas

- Les **états avec prix** (essai gratuit, annuel / mensuel, chargement, indisponible, achat en cours, erreur, restauration, abonné) : voir les **captures de la CI** (`95c` à `95m`, `99`, `99b`) ; ils ne s'affichent pas sur le téléphone avant **SRV-8**.
- Les **textes et prix des stores**, l'**achat**, la **restauration** : SRV-8A / SRV-8I.
- La **relecture des règles d'Apple** sur le délai de la croix (risque assumé, `paywall-v2.md` §7) : par `release-manager` avant la soumission.

## Écarts connus à valider (voir la description de la PR pour la liste complète)

- **Limite gratuite d'un lieu : 2 sur iPhone** (la décision du 2026-11-10 est de 3, déjà appliquée sur Android). L'offre dit donc « Tu as atteint 2 lieux » sur iPhone. À aligner par un autre ticket ; l'écran d'offre, lui, suit automatiquement la limite appliquée par l'app.
- **Saisie d'un code pour une 2e Miff** et **carte « Créer une Miff » du Profil** : sur iPhone, ces deux gestes n'ouvrent pas l'offre (le sélecteur du Feed l'ouvre ; la saisie d'un code reçoit un message calme si le serveur refuse). Dites-moi si vous voulez les brancher aussi.
