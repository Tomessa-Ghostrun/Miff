# Recette Android de la fenêtre d'avis « Que penses-tu de Miff ? » : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec un compte de test qui a **déjà une Miff** dans laquelle **un proche a déjà un statut** (vous voyez, dans le Feed, une carte d'un autre membre qui dit où il est). Un second compte (iPhone) aide, mais n'est pas indispensable : il suffit qu'une carte d'un proche indique un lieu.
**Durée** : environ 20 minutes.
**Ticket** : `docs/tickets/pop-ins-avis.md` (décision du PM du 2026-11-07 : fenêtre construite **telle que maquettée**, risque assumé). Maquette : `design/exports/pop-ins/03-avis-store.png`.

## À savoir avant de commencer (important)

- **La fenêtre n'existe que dans l'APK de debug** (artefact `miff-debug-apk` de la PR). Elle est **absente** de l'APK `releaseLike` et du bundle de release : c'est voulu (point de contrôle de release du ticket, §4). Ne cherchez pas la fenêtre dans l'APK `releaseLike`.
- **Les délais réels (7 jours, 90 jours, 30 jours) sont impraticables en recette.** Pour voir la fenêtre tout de suite : ouvrez l'application (elle démarre sur l'**écran de diagnostic**), faites défiler jusqu'à la carte **« Fenêtre d'avis (essai) »**, touchez **« Afficher la fenêtre au prochain retour dans l'app »**, touchez « Ouvrir l'écran d'accueil » en haut de l'écran de diagnostic, puis **mettez l'application en arrière-plan** (bouton Accueil) et **rouvrez-la** : la fenêtre apparaît environ 2 secondes après le retour dans le Feed. Elle ignore alors les 7 et 90 jours et les fermetures, **mais** ne couvre jamais l'Update ni le clavier. Le bouton **« Remettre à zéro l'état de la fenêtre »** efface tout ce que la fenêtre a retenu (dernière apparition, réponse, fermetures) : à toucher entre deux parcours.
- La carte affiche en clair ce que le téléphone retient : première Miff vue, dernière apparition, issue, fermetures. C'est tout ce qui est gardé (jamais l'étoile touchée).
- **Un APK installé à la main n'est pas « installé depuis Google Play »** : la fenêtre d'avis **du store** (API Google) ne peut pas s'ouvrir, donc, après « J'adore ! », c'est la **fiche du store** qui s'ouvre (le Play Store, ou le navigateur), et elle peut dire « introuvable » tant que Miff n'est pas publiée. C'est le repli prévu, pas un défaut.
- **Aucun événement, aucune donnée** n'est envoyée par cette fenêtre.

## A. La fenêtre telle que maquettée (5 min)

Provoquez la fenêtre comme ci-dessus.
- [ ] Une **carte blanche à bord bleu** apparaît au centre, l'écran derrière est **flouté ou voilé** (le flou dépend du téléphone : un voile clair est normal s'il n'y a pas de flou).
- [ ] Titre bleu **« Que penses-tu de Miff ? »** (au tutoiement : c'est l'écart voulu avec la maquette « Que pensez vous de Miff ? »).
- [ ] **Cinq étoiles** vert-jaune : petite, moyenne, **grande au milieu**, moyenne, petite (comme la maquette).
- [ ] Deux boutons bleus : **« J'adore ! (5 étoiles) »** et **« Peux faire mieux... »**.
- [ ] Écart à valider : une **petite croix** en haut à droite (la maquette n'en a pas ; elle sert à fermer sans répondre).
- [ ] Rien ne vous presse, rien de culpabilisant : le ton vous semble-t-il calme ? **Notez toute phrase qui vous paraît trop insistante.**

## B. « J'adore ! » (4 min)

Remettez à zéro, provoquez la fenêtre.
- [ ] Touchez **« J'adore ! (5 étoiles) »** : un écran bref **« Merci ! »** (environ 1,5 seconde, rien à toucher), puis la **demande d'avis du store** (ou, sur un APK installé à la main, la **fiche du store**).
- [ ] Revenez dans Miff, mettez en arrière-plan, rouvrez : **la fenêtre ne revient pas d'elle-même**. La carte de diagnostic affiche l'issue « LOVED ». En production la fenêtre ne reviendrait **jamais** après « J'adore ! » (testé par `ReviewPromptViewModelTest`) ; seul le bouton de recette peut la forcer de nouveau.
- [ ] Remettez à zéro, provoquez la fenêtre, touchez **la cinquième étoile** (la plus à droite, petite) : même résultat que « J'adore ! ».

## C. « Peux faire mieux... » (4 min)

Remettez à zéro, provoquez la fenêtre.
- [ ] Touchez **« Peux faire mieux... »** : **aucune boîte d'avis du store** ne s'ouvre. Un écran calme **« Merci de nous le dire ! »** avec « Nous écrire » et « Plus tard ».
- [ ] **« Nous écrire »** : votre application e-mail s'ouvre sur l'adresse de support, **sans objet ni texte ajouté** (rien de personnel dans le message). Revenez : la fenêtre est fermée.
- [ ] Remettez à zéro, recommencez, touchez **« Plus tard »** : la fenêtre se ferme, rien d'autre ne se passe.
- [ ] Remettez à zéro, recommencez, touchez **une étoile entre la première et la quatrième** : même écran « Merci de nous le dire ! » (choix par défaut à confirmer, C9 du ticket).
- [ ] La carte de diagnostic affiche l'issue « CAN_DO_BETTER » ; en production la question ne reviendrait qu'après **90 jours**.

## D. Fermer sans répondre (3 min)

Remettez à zéro, provoquez la fenêtre.
- [ ] Touchez la **croix** : la fenêtre se ferme ; la carte affiche **fermetures : 1** (issue « CLOSED »).
- [ ] Provoquez la fenêtre, fermez avec le **geste retour** : **fermetures : 2**.
- [ ] Provoquez-la une troisième fois **avec** « Afficher la fenêtre au prochain retour » : elle apparaît (c'est le bouton de recette qui force). En production, après deux fermetures, elle **ne reviendrait plus jamais** (testé par `ReviewPromptRulesTest`).
- [ ] Un toucher **à côté** de la carte (sur le voile) **ne ferme pas** la fenêtre.

## E. Là où elle ne doit jamais apparaître (3 min)

Touchez « Afficher la fenêtre au prochain retour dans l'app » puis :
- [ ] Ouvrez l'**Update** (bouton rond du milieu, le viseur), mettez l'application en arrière-plan, rouvrez-la : **la fenêtre n'apparaît pas** sur le viseur. Revenez au Feed, mettez en arrière-plan, rouvrez : elle apparaît.
- [ ] Rotation du téléphone sur le Feed : **la fenêtre n'apparaît pas** à cause de la rotation seule.
- [ ] Allez dans les **Réglages** (roue dentée du Profil), mettez en arrière-plan, rouvrez : la fenêtre **n'interrompt pas** les Réglages ; elle n'apparaît qu'après votre retour aux onglets (choix par défaut à confirmer, C10 du ticket).
- [ ] Une fenêtre déjà ouverte : mettez l'application en arrière-plan et rouvrez : **une seule** fenêtre, pas deux.

## F. Lecteur d'écran (TalkBack) et gros texte (3 min)

Activez TalkBack (réglages d'accessibilité).
- [ ] La fenêtre est lue dans l'ordre : **titre, étoiles (« Étoile 1 sur 5 » … « Étoile 5 sur 5 »), « J'adore ! (5 étoiles) », « Peux faire mieux... », « Fermer »** (la croix en dernier).
- [ ] Les étoiles et les boutons se touchent facilement (zones d'au moins 48 dp).
- [ ] Réglages > Affichage > **taille de police au maximum** : la carte **défile** si besoin, rien n'est coupé, les deux boutons restent utilisables.
- [ ] **Anglais** (Réglages de Miff > Langue) : « What do you think of Miff? », « I love it! (5 stars) », « Could do better... », « Thanks for telling us! », « Write to us », « Later ».

## G. Ce qui ne doit pas exister (3 min)

- [ ] **Aucune nouvelle demande de permission** n'est apparue avec cette version.
- [ ] Dans l'**APK `releaseLike`** (artefact de la PR), **le bouton de diagnostic n'existe pas et la fenêtre n'apparaît jamais**, même après un long usage : c'est la preuve visible que la fenêtre est absente des builds de diffusion (la CI le prouve aussi sur le bundle : texte `REVIEWPROMPTPROBE` absent).
- [ ] Nulle part dans l'application ni dans les fiches : aucune phrase du genre « avis modérés », « filtrés » ou « vérifiés ».

## Reconfirmation avant toute soumission (rappel, pas un refus)

La fenêtre n'est **pas** placée dans une version soumise aux stores sans que vous la **reconfirmiez à la porte « prêt à soumettre »** (ticket §4). Pour l'inclure dans un build de diffusion il faudrait `-Pmiff.reviewPrompt=true -Pmiff.allowReviewPromptInRelease=true` : rien ne le fait par défaut. Risque assumé par vous le 2026-11-07 : Apple 5.6.1 / 5.6.3 et Google déconseillent le filtrage des avis ; une sanction toucherait aussi Ghostrun (compte développeur partagé). Plan de repli : retirer la fenêtre par une mise à jour, ou passer à la demande officielle sans question préalable.

## Choix par défaut à confirmer (réponse attendue)

Le ticket (`docs/tickets/pop-ins-avis.md`, §8) liste C1 à C10. À dire pour chacun : **ok**, ou **à changer** (et par quoi). Le plus important : 7 jours depuis la première Miff, 90 jours après une réponse, 30 jours après une fermeture, 2 fermetures, étoiles 1 à 4 = « Peux faire mieux... », pas de plafond de reprises après « Peux faire mieux... ».
