# Recette iOS : sélecteur de Miff avec offre pour les comptes gratuits, icône maison — parcours pour le PM

**Pour qui** : vous, sur l'iPhone 11 Pro (iOS 26.5.2), avec deux comptes de test : un compte **gratuit** qui a déjà une Miff (**G**) et un compte avec **Miff+** accordé à la main (**P**, document `entitlements/{uid}` actif, posé depuis la console).
**Durée** : environ 15 minutes.
**Principe** : vos retours du 2026-11-08 (décision D51 de `docs/tickets/feed.md`). **Aucun achat n'existe** : l'écran de l'offre dit « Miff+ arrive bientôt » et rien d'autre ne se passe.
**Build à installer** : le build de la PR « iOS : sélecteur de Miff avec offre pour les comptes gratuits, icône maison », par TestFlight (lancé par l'assistant après la revue).

## Ce qui change pour vous

- Dans le Feed, la pastille du nom de la Miff est **cliquable pour tout le monde**, même avec une seule Miff. La liste montre vos Miffs (celle qui est affichée est cochée), puis « + Rejoindre une Miff » et « + Créer une Miff ».
- Compte **gratuit** qui a déjà une Miff : toucher « + Rejoindre » ou « + Créer » ouvre **l'offre** (« Débloque l'ensemble des fonctionnalités de Miff ! », avec une phrase du haut propre à la 2e Miff). Compte **Miff+** : le parcours normal.
- L'icône du Feed dans la barre du bas est une **maison classique**, sans cœur.

## Partie A : compte gratuit (G), une seule Miff

- [ ] Dans le Feed, la pastille du nom de la Miff a **une flèche bleue et une ombre** (plus de pastille plate) et **réagit au toucher**.
- [ ] Elle ouvre une feuille en bas avec **votre Miff cochée** (« ✓ Nom »), puis « + Rejoindre une Miff » et « + Créer une Miff ».
- [ ] Pendant que la feuille est ouverte, l'en-tête reste visible.
- [ ] Touchez « + Créer une Miff » : l'**offre** s'ouvre. Phrase du haut : « Une seule Miff avec la formule gratuite » / « Avec Miff+, tu peux créer ou rejoindre autant de Miffs que tu veux. » La ligne « Nombre de Miffs illimité » a le **contour bleu**. En bas : « Miff+ arrive bientôt » / « Cette option n'est pas encore ouverte. Ta Miff actuelle reste en place. » **Aucun prix, aucun bouton d'achat, le mot « Premium » n'apparaît nulle part.**
- [ ] Fermez avec la croix : retour au Feed, rien n'a changé. Recommencez avec « Plus tard », puis avec le geste retour (bord gauche) : même résultat.
- [ ] Touchez « + Rejoindre une Miff » : la **même offre** s'ouvre (pas l'écran de saisie du code).
- [ ] Touchez le nom de votre Miff dans la liste : la liste se ferme, rien d'autre ne change (une seule Miff).
- [ ] VoiceOver : la pastille est annoncée « Miff affichée : {nom}. Appuie pour changer de Miff ou en ajouter une » comme un bouton.

## Partie B : compte Miff+ (P)

- [ ] Même pastille, même liste, **toutes les Miffs** listées et la Miff affichée cochée.
- [ ] « + Créer une Miff » ouvre l'écran **Créer une Miff** (pas l'offre). « + Rejoindre une Miff » ouvre l'écran de saisie du code.
- [ ] Choisir une autre Miff dans la liste change le Feed ; la coche suit.

## Partie C : cas particuliers

- [ ] **Hors connexion** (mode avion) avec le compte G : la pastille s'ouvre ; au toucher de « + Créer », l'app ne sait pas si le compte est gratuit : elle ouvre l'écran **Créer une Miff**, qui dit calmement qu'il n'y a pas de connexion (jamais une erreur technique). Aucune Miff n'est créée.
- [ ] Même chose avec « + Rejoindre ».
- [ ] Compte sans aucune Miff (état de secours « Pas encore de Miff ») : la pastille reste **plate, sans flèche, non cliquable** ; les deux boutons de l'écran créent ou rejoignent normalement (jamais l'offre).
- [ ] **Contournement** (pour l'assistant, pas pour vous) : un compte gratuit qui forcerait la création d'une 2e Miff est refusé par les règles de sécurité (emplacement `free` unique) : couvert par les tests des règles (`backend/tests/rules/03-join.test.js` J10 et J11, `07-privileges.test.js` A6).

## Partie D : icône de la barre

- [ ] L'onglet Feed montre une **maison classique** (toit et porte, **sans cœur**), au trait quand il n'est pas actif, **pleine** quand il est actif (avec la pastille bleue).
- [ ] L'onglet Profil de même (silhouette au trait, pleine quand actif). *(Ajouté par cohérence ; à retirer si vous préférez le trait seul.)*
- [ ] VoiceOver : les noms des onglets sont inchangés (« Feed », « Profil »).
- [ ] La barre en verre est inchangée (taille, position, bouton central coloré).

## Points ouverts pour vous (à noter à la fin)

- [ ] La coche « ✓ » écrite dans la feuille du système vous convient-elle ? (Une vraie coche bleue demanderait de remplacer la feuille par un menu ou un panneau maison : plus long, à votre choix.)
- [ ] Hors connexion, le compte gratuit voit l'écran Créer plutôt que l'offre : acceptable ? (Choix prudent : on ne dit jamais « gratuit » sur un doute.)
- [ ] Les écrans Créer / Rejoindre affichent encore leur message calme « une seule Miff » si un compte gratuit y arrive par un autre chemin : faut-il y mettre l'offre aussi ?
