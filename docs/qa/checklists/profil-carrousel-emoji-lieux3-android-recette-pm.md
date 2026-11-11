# Recette Android : carrousel de Miffs au Profil, emoji des catégories, 3 lieux gratuits — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Il vous faut** : un compte **gratuit** avec une seule Miff, un compte **Miff+** (accordé à la main) avec au moins **3 Miffs** dont une de plus de 5 membres si possible, et un second compte membre pour tester les photos.
**Durée** : environ 20 minutes. **Sources** : retours du PM du 2026-11-10, maquettes `design/exports/profil/`, contrat `docs/contracts/place-categories.md`.

## À savoir avant de commencer

- La partie **C (3 lieux gratuits)** ne fonctionne **qu'après le déploiement des règles Firestore** (PR `backend/lieux-gratuits-3`, il faut votre « oui » explicite : `docs/dev/deploiement-regles.md`). Avant, un compte gratuit qui enregistre son 3e lieu reçoit le message calme « Ça n'a pas fonctionné. Réessaie dans un instant. » : c'est attendu, rien n'est perdu. Les parties A et B ne dépendent d'aucun déploiement.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A — Le carrousel « Mes Miffs » (Profil)

1. [ ] Compte **gratuit, 1 Miff** : l'onglet Profil montre sous votre photo le titre « Mes Miffs » et une carte avec **le nom de la Miff, « n membres » et, dessous, les photos (ou initiales) des membres** en petits ronds. Deux pastilles sous la carte (la carte de la Miff, puis « Créer une Miff »).
2. [ ] Une carte d'une Miff de **1 membre** dit « 1 membre » (singulier). Une Miff de **6 membres ou plus** montre **4 ronds puis « +N »**.
3. [ ] Glissez vers la gauche : la **dernière carte est « Créer une Miff »**. Touchez-la avec ce compte gratuit : l'écran **« Plusieurs Miffs avec Miff+ »** s'ouvre (le même que depuis le sélecteur du Feed), avec « Plus tard ». Fermez : rien n'a changé.
4. [ ] Touchez la carte de la Miff : l'écran de la Miff s'ouvre comme avant.
5. [ ] Compte **Miff+, 3 Miffs** : une carte par Miff puis « Créer une Miff » en dernier. Le carrousel s'ouvre sur la **Miff affichée par le Feed**. Touchez « Créer une Miff » : le **parcours de création** s'ouvre (pas l'offre).
6. [ ] Les nombres de membres et les photos des autres Miffs s'affichent **quand vous arrivez sur leur carte** (un très court instant avec des ronds gris, sans message). Hors connexion (mode avion) sur une Miff pas encore vue : la carte garde son **nom**, sans compteur et sans message d'erreur.
7. [ ] **Grand texte** (Réglages du téléphone > Taille de police au maximum) : le nom, « n membres » et « Créer une Miff » restent lisibles, la carte grandit, rien n'est coupé ; les petits ronds gardent leur taille.
8. [ ] **TalkBack** : chaque carte est annoncée en un seul bloc (« Famille Martin, 4 membres, Carte 1 sur 3, bouton ») ; les actions « Carte suivante » / « Carte précédente » sont proposées sans glisser ; les photos et pastilles ne sont pas lues.
9. [ ] Mode sombre et mode clair : les cartes restent lisibles.

## Partie B — Les catégories de lieux en emoji

1. [ ] Formulaire d'un lieu (Profil > « Ajouter un lieu ») : les 7 catégories montrent **🏠 Maison, 💼 Travail, 🏫 École, ⚽ Sport, 👨‍👩‍👧 Chez un proche, 🎉 Loisir, 📍 Autre**, chacune dans sa pastille bleue avec son nom écrit à côté.
2. [ ] La liste des lieux (Profil) et le détail d'un lieu montrent le même emoji que la catégorie choisie. Un très ancien lieu **sans catégorie** montre l'épingle (pas un emoji).
3. [ ] **Le rendu est celui de la police de votre téléphone** (pas celui d'Apple : ils ne sont pas utilisables dans une app Android) : dites-nous si l'un des 7 vous semble peu lisible ou si un carré vide apparaît.
4. [ ] **Grand texte** : l'emoji reste dans sa pastille. **TalkBack** : c'est le **nom** de la catégorie qui est lu (« Maison », « Sport »…), jamais le nom de l'emoji.

## Partie C — 3 lieux gratuits (après le déploiement des règles)

1. [ ] Compte **gratuit** : créez 3 lieux de confiance. Le compteur affiche **« 3 lieux sur 3 avec la formule gratuite »**.
2. [ ] « Ajouter un lieu » reste visible avec la pastille Miff+ et ouvre « Tu as atteint 3 lieux » (jamais un bouton grisé).
3. [ ] Compte **Miff+** : plus de limite, pas de compteur.
