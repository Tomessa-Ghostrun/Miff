# Recette Android : les lieux de la Miff visibles par tous les membres (Profil) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Il vous faut** : deux comptes dans la **même Miff** (le **créateur** et un **membre**, deux téléphones ou un seul qui change de compte), la Miff ayant au moins **un lieu de confiance** et idéalement **une zone de danger** (zone de danger = Miff+ : à créer avec un compte Miff+ accordé à la main) ; pour la partie C, un compte **Miff+ avec 2 Miffs** dont une dont vous n'êtes pas le créateur.
**Durée** : environ 15 minutes. **Source** : décision du PM du 2026-11-11 (pas de maquette), contrat `docs/contracts/profile-places-members.md`, ticket `docs/tickets/lieux-de-confiance.md` (critère PL1b).

## À savoir avant de commencer

- Aucune règle Firestore n'a changé : rien à déployer pour cette recette.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : avec le compte MEMBRE (pas le créateur)

1. [ ] Onglet **Profil**, sous le carrousel « Mes Miffs » : un titre **« Lieux de confiance de <nom de la Miff> »**, une phrase « Les lieux de ta Miff, définis par son créateur. », puis la liste des lieux (emoji de catégorie, nom, adresse).
2. [ ] **Aucun** bouton « Ajouter un lieu », **aucun** compteur « n lieux sur 3 avec la formule gratuite », **aucune** pastille ou offre « Miff+ » dans ce bloc (même si ce compte est gratuit).
3. [ ] Une **zone de danger** (si la Miff en a une) porte l'étiquette **« Zone de danger »**, sans autre mot alarmant.
4. [ ] Touchez un lieu : le détail s'ouvre (carte, adresse, « Voir sur la carte », rayon, type, catégorie, « Visible par… », « Créé par… ») **sans bouton « Modifier »** en haut à droite, sans suppression, sans autre action d'édition. Retour : la liste est inchangée.
5. [ ] **Carte « Mode voyage » toujours tout en bas** de l'écran Profil, **après** le carrousel et la liste des lieux.
6. [ ] Une Miff **sans aucun lieu** (avec un compte membre) : la phrase calme **« Aucun lieu de confiance pour l'instant. »**, sans bouton.
7. [ ] **Mode avion** sur une Miff pas encore vue (compte avec 2 Miffs, partie C) : « Les lieux ne sont pas disponibles pour le moment. » et un bouton « Réessayer » ; rien d'inquiétant ; en reprenant le réseau et en touchant « Réessayer », la liste apparaît.

## Partie B : avec le compte CRÉATEUR (rien ne doit avoir changé pour lui)

1. [ ] Même titre « Lieux de confiance de <nom de la Miff> », la liste, puis **« Ajouter un lieu »** ; compte gratuit : le compteur « n lieux sur 3 avec la formule gratuite » ; à 3 lieux, « Ajouter un lieu » porte la pastille **Miff+** et ouvre l'offre.
2. [ ] Touchez un lieu : le détail a le bouton **« Modifier »** ; modifiez le nom, enregistrez : la liste du Profil montre le **nouveau nom** en revenant (en un court instant), avec la phrase « Le lieu « … » est enregistré. »
3. [ ] « Ajouter un lieu » puis enregistrer : le nouveau lieu apparaît dans la liste ; **supprimer** un lieu depuis « Modifier » : il disparaît de la liste.
4. [ ] Passez sur le compte membre (ou son téléphone) puis, **dans les 5 minutes**, revenez sur le Profil : il peut encore voir l'ancienne liste ; **au-delà de 5 minutes** (ou après avoir quitté et rouvert l'onglet Feed), la nouvelle liste. C'est voulu (économie de lectures) : dites-nous si c'est gênant.

## Partie C : plusieurs Miffs (compte Miff+)

1. [ ] Glissez le carrousel d'une Miff à l'autre : **le titre et la liste suivent la carte affichée** (« Lieux de confiance de <autre Miff> »).
2. [ ] Sur une Miff **dont vous n'êtes pas le créateur**, aucun « Ajouter un lieu » ni compteur ni « Miff+ » ; sur une Miff **dont vous êtes le créateur**, ils sont là.
3. [ ] Sur la carte « Créer une Miff » (la dernière), la liste reste celle de la dernière Miff regardée (son nom est dans le titre).
4. [ ] Repasser d'une carte à l'autre plusieurs fois de suite ne fait **pas** clignoter la liste (réutilisée pendant 5 minutes).

## Partie D : lecteur d'écran, texte, thèmes

1. [ ] **TalkBack** : le titre est annoncé comme un titre ; chaque ligne est lue d'un bloc (« Maison de Papa, catégorie Maison, 26 rue du Volnay… ») ; une zone de danger finit par **« Zone de danger »** ; le bouton « Réessayer » est annoncé comme un bouton ; le détail d'un lieu (membre) n'a aucun bouton « Modifier » à explorer.
2. [ ] **Grand texte** (taille de police au maximum) : le titre passe à la ligne, rien n'est coupé, les lignes restent touchables.
3. [ ] **Anglais** (langue du téléphone en anglais) : « Trusted places of … », « No trusted place yet. », « The places aren't available right now. ».
4. [ ] Mode sombre et mode clair : lisible.
