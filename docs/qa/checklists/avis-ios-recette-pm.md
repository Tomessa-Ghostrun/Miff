# Recette iOS de la fenêtre d'avis « Que penses-tu de Miff ? » : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec un compte de test qui a **déjà une Miff** dans laquelle **un proche a déjà un statut** (vous voyez, dans le Feed, une carte d'un autre membre qui dit où il est). Un second compte (Android) aide, mais n'est pas indispensable : il suffit qu'une carte d'un proche indique un lieu.
**Durée** : environ 20 minutes.
**Ticket** : `docs/tickets/pop-ins-avis.md` (décision du PM du 2026-11-07 : fenêtre construite, risque assumé ; décisions du 2026-11-08 : plus d'étoiles, deux boutons, formulaire externe, plus de limite de fermetures ; **décision du 2026-11-13 : iOS a la même fenêtre qu'Android**, §9). Maquette : `design/exports/pop-ins/03-avis-store.png`. Même parcours que la recette Android (`docs/qa/checklists/avis-android-recette-pm.md`).

## À savoir avant de commencer (important)

- **La fenêtre n'existe que dans le build TestFlight « Internal »** (artefact du workflow `ios-testflight`, ou le build Debug du simulateur). Elle est **absente** du build Release : c'est voulu (point de contrôle de release du ticket, §4).
- **Les délais réels (7 jours, 90 jours, 30 jours) sont impraticables en recette.** Pour voir la fenêtre tout de suite : ouvrez l'application, puis l'**écran de diagnostic** (**appui long de 1,5 s en haut à gauche de l'écran**, sur l'heure de la barre d'état), faites défiler jusqu'à la carte **« Fenêtre d'avis (essai) »**, touchez **« Afficher la fenêtre au prochain retour dans l'app »**, **fermez la feuille du diagnostic en la glissant vers le bas**, puis **mettez l'application en arrière-plan** (balayage depuis le bas) et **rouvrez-la** : la fenêtre apparaît environ 2 secondes après le retour dans le Feed. Elle ignore alors les 7 et 90 jours et les fermetures, **mais** ne couvre jamais l'Update ni le clavier ni un message de l'app. Le bouton **« Remettre à zéro l'état de la fenêtre »** efface tout ce que la fenêtre a retenu : à toucher entre deux parcours. **Ne laissez pas la feuille du diagnostic ouverte** en revenant dans l'app : la fenêtre apparaîtrait dessous.
- La carte affiche en clair ce que l'iPhone retient : première Miff vue, dernière apparition, issue, et si un formulaire externe est renseigné. C'est tout ce qui est gardé.
- **La fenêtre d'avis d'Apple (après « J'adore ! ») est limitée par Apple** : elle n'est montrée que quelques fois par an, et, d'après la documentation d'Apple, **pas dans un build TestFlight**. Après le « Merci ! », il est donc **normal qu'aucune fenêtre ne s'ouvre** sur votre build de recette. Ce n'est pas un défaut : Miff demande, Apple décide. À vérifier seulement : le « Merci ! » apparaît, puis disparaît, et rien ne plante.
- **Aucun événement, aucune donnée** n'est envoyée par cette fenêtre.

## A. La fenêtre : deux boutons, pas d'étoiles (4 min)

Provoquez la fenêtre comme ci-dessus.
- [ ] Une **carte blanche à bord bleu** apparaît au centre, l'écran derrière est **flouté et légèrement assombri**.
- [ ] Titre bleu **« Que penses-tu de Miff ? »** (au tutoiement : écart voulu avec la maquette « Que pensez vous de Miff ? »).
- [ ] **Pas d'étoiles** (la maquette en montrait cinq : décision du 2026-11-08) et **deux boutons bleus seulement** : **« J'adore ! »** (sans « (5 étoiles) ») et **« Peux faire mieux... »**. (Libellé identique à Android ; vous aviez écrit « Ça peut mieux faire » : dites-moi si vous préférez ce texte.)
- [ ] Écart à valider : une **petite croix** en haut à droite (la maquette n'en a pas ; elle sert à fermer sans répondre).
- [ ] Rien ne vous presse, rien de culpabilisant : le ton vous semble-t-il calme ? **Notez toute phrase qui vous paraît trop insistante.**

## B. « J'adore ! » (4 min)

Remettez à zéro, provoquez la fenêtre.
- [ ] Touchez **« J'adore ! »** : un écran bref **« Merci ! »** (environ 1,5 seconde, rien à toucher) qui dit qu'on vous invite à laisser **un avis et un commentaire** dans la boutique d'applications, puis la fenêtre se ferme (la demande d'avis d'Apple s'affiche ou non : voir plus haut, Apple décide).
- [ ] Mettez l'application en arrière-plan pendant le « Merci ! » : la demande du store **ne s'ouvre pas tant que vous n'êtes pas revenu** dans Miff.
- [ ] Revenez dans Miff, mettez en arrière-plan, rouvrez : **la fenêtre ne revient pas d'elle-même**. La carte de diagnostic affiche l'issue « LOVED ». En production la fenêtre ne reviendrait **jamais** après « J'adore ! » (testé par `ReviewPromptCoordinatorTests`) ; seul le bouton de recette peut la forcer de nouveau.

## C. « Peux faire mieux... » (6 min)

Remettez à zéro, provoquez la fenêtre. **Aucune demande d'avis d'Apple ne doit s'ouvrir dans toute cette partie.**
- [ ] Touchez **« Peux faire mieux... »** : la fenêtre se ferme et le **formulaire s'ouvre dans Safari**, directement, **sans écran « Merci de nous le dire ! »** (la carte de diagnostic dit « formulaire externe : renseigné (ouvert dans le navigateur) »). Regardez l'adresse affichée : **`https://forms.gle/5EEH4sEfsnCCLzEx7`, exactement, sans rien d'ajouté** (ni votre nom, ni votre e-mail, ni identifiant) ; le formulaire s'affiche. Revenez dans Miff : la fenêtre est fermée.
- [ ] La carte de diagnostic affiche l'issue « CAN_DO_BETTER » ; en production la question ne reviendrait qu'après **90 jours**, autant de fois que nécessaire (décision du 2026-11-08).
- [ ] **Écran de repli** (seulement si l'adresse est vide ou invalide, ce qui n'arrive pas avec ce build : `ReviewFeedbackFormTests` et `ReviewPromptCoordinatorTests` le couvrent ; la capture CI `162-fr-avis-merci-formulaire` le montre) : « Merci de nous le dire ! » avec « Nous écrire » (ouvre le courrier sur l'adresse d'aide, **sans objet ni texte ajouté**) et « Plus tard ».

## D. Fermer sans répondre (3 min)

Remettez à zéro, provoquez la fenêtre.
- [ ] Touchez la **croix** : la fenêtre se ferme ; la carte affiche l'issue « CLOSED ».
- [ ] Un toucher **à côté** de la carte (sur la partie assombrie) **ne ferme pas** la fenêtre.
- [ ] **Il n'y a plus de limite** : en production la fenêtre revient **après 30 jours, à chaque fois, sans fin** (décision du 2026-11-08 ; testé par `ReviewPromptRulesTests`, 50 fermetures de suite). Le bouton de recette peut la forcer autant de fois que vous voulez.

## E. Là où elle ne doit jamais apparaître (4 min)

Touchez « Afficher la fenêtre au prochain retour dans l'app » (puis fermez la feuille du diagnostic) :
- [ ] Ouvrez l'**Update** (bouton rond du milieu, le viseur), mettez l'application en arrière-plan, rouvrez-la : **la fenêtre n'apparaît pas** sur le viseur. Revenez au Feed, mettez en arrière-plan, rouvrez : elle apparaît.
- [ ] Rotation : l'app est en portrait seulement, rien à vérifier.
- [ ] Allez dans les **Réglages** (roue dentée du Profil), mettez en arrière-plan, rouvrez : la fenêtre **n'interrompt pas** les Réglages ; elle n'apparaît qu'après votre retour aux onglets (choix par défaut à confirmer, C10 du ticket).
- [ ] **Jamais par-dessus un message** : avec la feuille **« Localiser »** d'un proche ouverte (Feed), ou quand le Feed dit « **Tu as arrêté de partager** » ou « **En attente de ton accord** », ou quand le message d'**accès perdu** est affiché, ou la pop-in **« Pas de connexion »** : mettez en arrière-plan, rouvrez : la fenêtre **n'apparaît pas** (et ne cache rien).
- [ ] Une fenêtre déjà ouverte : mettez l'application en arrière-plan et rouvrez : **une seule** fenêtre, pas deux.
- [ ] Le **centre de contrôle** ou une alerte du système qui passent devant l'app puis disparaissent ne déclenchent **pas** la fenêtre (ce n'est pas un retour de l'arrière-plan).

## F. VoiceOver et gros texte (3 min)

Activez VoiceOver (Réglages > Accessibilité).
- [ ] La fenêtre est lue dans l'ordre : **titre, « J'adore ! », « Peux faire mieux... », « Fermer »** (la croix en dernier). Aucune étoile n'est annoncée. Le reste de l'écran n'est pas lu tant que la fenêtre est là.
- [ ] Le geste **« échappe »** (deux doigts en Z) ferme la fenêtre (comme la croix), sauf pendant « Merci ! ».
- [ ] Réglages > Accessibilité > Affichage et taille du texte > **Texte plus grand** au maximum : la carte **défile** si besoin, rien n'est coupé, les deux boutons restent utilisables (captures CI `164-fr-avis-question-texte-tres-grand`).
- [ ] **Anglais** (Réglages de Miff > Langue, ou langue de l'iPhone) : « What do you think of Miff? », « I love it! », « Could do better... », « Thanks for telling us! », « Write to us », « Later ».

## G. Ce qui ne doit pas exister, et ce qui est effacé (4 min)

- [ ] **Aucune nouvelle demande de permission** n'est apparue avec cette version.
- [ ] **Déconnexion** (Réglages > Mon compte) puis reconnexion : la carte de diagnostic montre un état **remis à zéro** (« jamais », issue « aucune ») : l'état de la fenêtre ne suit pas d'un compte à l'autre ; même chose après la **suppression d'un compte de test**.
- [ ] Dans un **build Release** (plus tard, publication), **la fenêtre et sa carte de diagnostic n'existent pas**, même après un long usage : la CI le prouve sur le binaire (`check-binaries.sh release` : texte `REVIEWPROMPTPROBE` et noms des types absents).
- [ ] Nulle part dans l'application ni dans les fiches : aucune phrase du genre « avis modérés », « filtrés » ou « vérifiés ».

## Reconfirmation avant toute soumission (rappel, pas un refus)

La fenêtre n'est **pas** placée dans une version soumise aux stores sans que vous la **reconfirmiez à la porte « prêt à soumettre »** (ticket §4). Pour l'inclure dans un build de diffusion iOS il faudrait ajouter `REVIEW_PROMPT` à la configuration Release de `ios/project.yml` **et** lancer le contrôle des binaires avec `MIFF_ALLOW_REVIEW_PROMPT_IN_RELEASE=1` : rien ne le fait par défaut. **Un build Internal ouvert à des testeurs externes contient la fenêtre** : la retirer de la ligne Internal avant de l'ouvrir. Risque assumé par vous le 2026-11-07 : Apple 5.6.1 / 5.6.3 et Google déconseillent le filtrage des avis ; une sanction toucherait aussi Ghostrun (compte développeur partagé). **À ajouter à la politique de confidentialité avant d'allumer la fenêtre en diffusion** : les réponses au formulaire Google Forms peuvent contenir des données personnelles, hébergées par Google. Plan de repli : retirer la fenêtre par une mise à jour, ou passer à la demande officielle sans question préalable.

## Choix par défaut à confirmer (réponse attendue)

Les mêmes que pour Android (`docs/tickets/pop-ins-avis.md`, §8) : **C1 à C5 et C10** (7 jours depuis la première Miff, 90 jours après « Peux faire mieux... », la fenêtre attend les messages de l'app et les écrans empilés…). À dire pour chacun : **ok**, ou **à changer** (et par quoi). Écarts propres à iOS : pas de repli sur la fiche du store, fond flouté par le système, croix de 44 pt.
