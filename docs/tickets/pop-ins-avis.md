# Ticket : fenêtre d'avis « J'adore ! / Peux faire mieux... » (pop-in d'avis du store)

**Statut** : **décision du PM enregistrée le 2026-11-07** ; **Android construit le 2026-11-07, modifié le 2026-11-08 par de nouvelles décisions du PM** (PR brouillon `android/fenetre-avis`, voir §8) ; **iOS non commencé** ; **adresse du formulaire externe renseignée le 2026-11-13** (PR `android/feedback-form-url`) ; aucun statut Notion modifié. Ticket court créé parce qu'aucun ticket ne couvrait cette fenêtre (recherche faite dans `docs/tickets/`, `docs/specs/` et `design/exports/pop-ins/README.md`, le 2026-11-07).
**Rédigé le** : 2026-11-07 (agent `product-specs`, d'après la demande du coordinateur et `docs/release/decisions-stores-avant-soumission.md` §11).
**Maquette** : `design/exports/pop-ins/03-avis-store.png` (« Que pensez vous de Miff ? », cinq étoiles, boutons « J'adore ! (5 étoiles) » et « Peux faire mieux... »).

## 1. Décision du PM (2026-11-07)

**DÉCIDÉ par le PM le 2026-11-07 : l'écran est construit TEL QUEL, tel que maquetté, et le PM assume le risque.** La recommandation du `release-manager` (ne pas construire l'écran tel que maquetté, utiliser l'API officielle sans question préalable) **n'est pas suivie** ; elle devient le **plan de repli** (§3).

## 2. Risque assumé par le PM

- **Apple, guideline 5.6.1** : les invites d'avis personnalisées ne sont pas autorisées (« Use the provided API to prompt users to review your app… and we will disallow custom review prompts ») ; **5.6.3** : manipuler les avis ou tout élément de l'expérience client de l'App Store n'est pas permis. Un choix « J'adore / Peux faire mieux » qui n'ouvre la boîte d'avis du store que pour les satisfaits est un **filtrage des avis**.
- **Google Play** : pas de filtrage d'avis ; l'API d'avis dans l'app demande de **ne poser aucune question avant ni pendant** l'affichage de la demande, y compris sur l'opinion (« Aimez-vous l'application ? »).
- **Conséquences possibles** : **rejet de l'app, avertissement, retrait d'une version, résiliation du compte développeur**. **Le compte développeur est PARTAGÉ avec Ghostrun** : une sanction touche **aussi Ghostrun** (`decisions-stores-avant-soumission.md` point 20).
- Texte non lu par l'équipe : la politique Google « User Ratings, Reviews, and Installs » (voir `decisions-stores-avant-soumission.md` §11).
- Ce risque est écrit comme **assumé par le PM**, jamais comme « à valider par un juriste » (décision du 2026-10-23).

## 3. Plan de repli

1. **Retirer l'écran par une mise à jour** de l'app (aucune migration de données : l'écran n'écrit rien d'autre qu'une préférence locale « déjà affiché »).
2. **Alternative conforme** : l'API officielle **`SKStoreReviewController`** (Apple) / **Play In-App Review** (Google), **sans question préalable d'opinion**, après un moment réussi (par exemple après plusieurs jours d'usage d'une Miff complète).
3. Pour recueillir les mécontentements en privé : le lien « Nous écrire » des Réglages existe déjà (Android) ; il reste **séparé** de la demande d'avis, jamais en amont.

## 4. Point de contrôle de release (pas un refus)

**Cet écran n'est PAS placé dans une version soumise aux stores sans que le PM le reconfirme à la porte « prêt à soumettre »** (`docs/qa/release-checklist.md`, à créer : y inscrire cette ligne). Tant que la reconfirmation n'est pas donnée :
- l'écran peut être **construit et testé en interne** ;
- il est **absent des builds de diffusion** (comme l'était le bouton central de la barre avant l'Update : drapeau de build) ;
- la reconfirmation est **consignée** (date, version) dans la PR de release.

## 5. Périmètre de la construction « telle quelle »

> **Décisions du PM du 2026-11-08 (elles priment sur la maquette `03-avis-store.png`, qui montre cinq étoiles)** : **plus de système d'étoiles** (la fenêtre n'a que **deux boutons**, « J'adore ! » sans « (5 étoiles) » et « Peux faire mieux... ») ; « J'adore ! » invite à laisser un **avis ET un commentaire** dans le store, de façon **native** (Play In-App Review, repli : fiche du store) ; « Peux faire mieux... » renvoie vers un **formulaire externe** dont le PM donnera l'adresse plus tard (tant qu'elle manque : écran « Merci de nous le dire ! » avec « Nous écrire ») ; **aucune limite** au nombre de fermetures sans réponse ; après « Peux faire mieux... », la question ne revient qu'après 90 jours, sans plafond. Détail et écarts : §8.

- **Visuel** : celui de `03-avis-store.png` ; textes **au tutoiement** (décision du PM du 2026-11-01 : « Que penses-tu de Miff ? » à la place de « Que pensez vous de Miff ? »), français et anglais, sans accord de genre, clés `review_` (à ajouter à `docs/contracts/i18n-keys.md` et au contrôle de parité) ; accessibilité : boutons nommés (**pas d'étoiles depuis le 2026-11-08**), texte maximal et droite-à-gauche ; ton bienveillant (CLAUDE.md).
- **Comportement connu** : « J'adore ! » mène à la demande d'avis du store ; « Peux faire mieux... » **n'ouvre pas** la boîte d'avis du store (note du README du 2026-11-01). **Non précisé par la maquette** (le PM a demandé d'avancer le 2026-11-07 : les points ci-dessous ont reçu un **choix par défaut du développeur, à confirmer**, voir §8) : ce qui s'affiche après « Peux faire mieux... » (remerciement, lien « Nous écrire »), **quand** la fenêtre apparaît et **à quelle fréquence** (le README renvoie au ticket Notion concerné ; aucune page Notion lue ne le dit), et si l'écran peut être refermé sans répondre.
- **Aucun événement Analytics** sur le contenu de la réponse sans décision (`docs/contracts/analytics-events.md` : liste blanche) ; **aucune permission nouvelle**, **aucune donnée personnelle** envoyée.

## 6. Critères d'acceptation

- [ ] **AV1** L'écran correspond à `03-avis-store.png` (écarts listés dans la PR), au tutoiement, FR et EN, avec parité des clés.
- [ ] **AV2** **Aucun build de diffusion** ne contient l'écran tant que le PM n'a pas **reconfirmé à la porte « prêt à soumettre »** ; la reconfirmation est consignée.
- [ ] **AV3** Le **plan de repli** (§3) est décrit dans la PR (ce que retire la mise à jour, ce qui le remplace) ; **aucune promesse** « avis modérés / filtrés / vérifiés » dans l'app ni dans les fiches.
- [ ] **AV4** Aucune donnée personnelle, aucune coordonnée, aucun nom dans un journal ou un événement ; aucune permission nouvelle.

## 7. Dépendances et découpage

- Android d'abord, puis iOS (une branche et une PR par plateforme). Dépend de la **reconfirmation du PM** pour toute soumission ; **ne bloque aucun autre ticket**.
- À signaler à `release-manager` : la fiche de revue Apple et les notes de revue ne mentionnent **pas** cet écran comme conforme ; si un store l'exige, appliquer le plan de repli.

## 8. Mise en œuvre Android (2026-11-07), décisions du PM du 2026-11-08 et choix par défaut à confirmer

PR brouillon `android/fenetre-avis` (#118) ; code dans `android/app/src/main/java/com/tomessa/miff/{domain,data,ui}/review/` ; détail dans `android/CHANGELOG.md` (entrée « fenêtre d'avis ») et `android/README.md` ; recette : `docs/qa/checklists/avis-android-recette-pm.md`. **Rien de ce paragraphe ne contredit la décision du §1** : la fenêtre est construite, le risque du §2 reste assumé par le PM, le plan de repli du §3 et le point de contrôle de release du §4 restent en vigueur.

### Décisions du PM du 2026-11-08 (appliquées)
| N° | Décision |
|---|---|
| D1 | **Plus de système d'étoiles** : la fenêtre n'a que **deux boutons**. Texte « J'adore ! » (la mention « (5 étoiles) » disparaît) et « Peux faire mieux... ». Toute la logique, les textes, les clés, les tests et l'accessibilité des étoiles sont retirés. |
| D2 | **« J'adore ! »** : un « Merci ! » bref (1,6 s), puis l'invitation **native** à laisser **un avis et un commentaire** dans le store : Google Play In-App Review, **repli** sur la fiche du store (`market://details?id=com.tomessa.miff`, puis `https://play.google.com/store/apps/details?id=com.tomessa.miff`). **Plus jamais redemandé** ensuite. |
| D3 | **« Peux faire mieux... »** : la boîte d'avis du store **n'est pas ouverte** ; la fenêtre se ferme et l'**adresse du formulaire externe** s'ouvre dans le navigateur (ACTION_VIEW, **HTTPS seulement**, adresse ouverte **telle quelle**, aucune donnée personnelle ajoutée). **Tant que l'adresse est vide**, repli sur l'écran « Merci de nous le dire ! » avec « Nous écrire » (mailto vers le support provisoire) et « Plus tard ». **L'adresse est à fournir par le PM** : voir « Renseigner le formulaire » ci-dessous. |
| D4 | **C8 confirmé** : après « Peux faire mieux... », la question ne revient qu'après **90 jours**, **sans plafond** de reprises. |
| D5 | **C6 modifié** : **aucune limite** au nombre de fermetures sans réponse (croix ou geste retour) ; après chaque fermeture, la fenêtre revient après **30 jours**, indéfiniment. (Le compteur de fermetures n'existe plus.) |

### Formulaire externe (adresse fournie par le PM le 2026-11-13)
- **Un seul endroit** : la valeur par défaut de la propriété `miff.feedbackFormUrl` dans `android/app/build.gradle.kts` (`providers.gradleProperty("miff.feedbackFormUrl")` puis `.getOrElse("https://forms.gle/5EEH4sEfsnCCLzEx7")`), **adresse fournie par le PM le 2026-11-13** (formulaire Google Forms, adresse publique, pas un secret), qui alimente `BuildConfig.FEEDBACK_FORM_URL`. Pour un essai sans modifier le code : `-Pmiff.feedbackFormUrl=https://...`.
- **Règles** : vide autorisé ; sinon une adresse **`https://`** sans espace, guillemet, antislash ni `$` (refusé à la compilation, et revérifié dans l'app) ; lue **uniquement** depuis `BuildConfig` (jamais depuis un lien reçu) ; ouverte **telle quelle** : **ne mettre dans l'adresse ni nom, ni e-mail, ni identifiant, ni position** (l'app n'en ajoute aucun).
- **Service du formulaire** : **Google Forms, choisi par le PM (adresse donnée le 2026-11-13)**. Rappel : les réponses contiendront des données personnelles que la personne y écrit (hébergées par Google) : à mentionner dans la politique de confidentialité avant d'allumer la fenêtre dans un build de diffusion.
- **Si l'adresse est vide (build fait avec `-Pmiff.feedbackFormUrl=`)** : « Peux faire mieux... » mène à « Merci de nous le dire ! » avec « Nous écrire ». Si aucune application ne peut ouvrir l'adresse, la fenêtre bascule sur le mail au support ; si rien ne s'ouvre, une courte phrase calme le dit.

### Ce qui est fixé par la décision du PM du 2026-11-07 ou par la maquette
- Visuel de `03-avis-store.png` **sauf les étoiles** (D1), au tutoiement, français et anglais, clés `review_` (parité Android / iOS vérifiée).
- Fenêtre **absente des builds de diffusion** (`BuildConfig.REVIEW_PROMPT`, faux en release et releaseLike ; garde Gradle explicite ; preuve sur le bundle par la CI).
- Aucun événement Analytics, aucune permission, aucune donnée personnelle ; Miff n'écoute jamais ce qui est donné dans le store.

### Choix par défaut du développeur, à confirmer par le PM (constantes nommées dans `ReviewPromptDefaults`)
| N° | Point que la maquette ne dit pas | Choix par défaut |
|---|---|---|
| C1 | Quand la fenêtre apparaît | Au retour au premier plan (pas une rotation, pas un retour des Réglages), 1,5 s après, une seule fois par retour. |
| C2 | Fréquence | Au plus une fois tous les **90 jours** après « Peux faire mieux... » (30 jours après une fermeture : D5). |
| C3 | Ancienneté | Une Miff depuis **7 jours au moins**, mesurée depuis la première fois que **ce téléphone** a affiché le Feed avec une Miff (aucune lecture du serveur de plus ; une réinstallation repart de zéro, ce qui retarde seulement la fenêtre). |
| C4 | Statut vu | Le Feed affiche au moins **un statut d'un proche** (lieu, départ ou zone de danger ; pas « Pas encore de nouvelles », pas sa propre carte). |
| C5 | Quand jamais | Jamais pendant l'Update, avec le clavier, l'onboarding, les autorisations, la suppression de compte ; jamais deux fenêtres ; **jamais par-dessus (ni en cachant) un message de l'app** : message d'accès perdu, « En attente de ton accord », feuille « Localiser » ouverte, « Tu as arrêté de partager ». |
| C6 | Fermer sans répondre | Une **croix** (absente de la maquette : écart) ou le geste retour ; retour après 30 jours, **sans limite** (D5, décidé). |
| C10 | Retour au premier plan sur une autre page | Si l'app revient au premier plan pendant les Réglages, la fenêtre attend le retour aux onglets (elle n'interrompt jamais un parcours de Réglages). |

(C7 « J'adore ! » : plus jamais ensuite, et C8, sont décidés : D2 et D4. C9, les étoiles, est supprimé : D1.)

### Écarts avec la maquette `03-avis-store.png` (AV1)
1. **Pas d'étoiles** (la maquette en montre cinq) et **un seul texte « J'adore ! »** au lieu de « J'adore ! (5 étoiles) » : décision du PM du 2026-11-08. La fenêtre n'a que le titre et deux boutons.
2. Tutoiement (« Que penses-tu de Miff ? », décision du 2026-11-01).
3. **Une petite croix en haut à droite** : la maquette n'a aucune sortie visible, or la fermeture sans répondre est prévue (geste retour seul, invisible).
4. **Cadre** de 2 dp, aux couleurs et aux proportions des autres fenêtres de l'app (280 dp de large au plus, rayon 10 dp), au lieu du filet de 1 px de l'export.
5. **Fond** : flou du système derrière la fenêtre sur Android 12 et plus quand le téléphone l'autorise (la maquette montre le Feed flouté), léger assombrissement de l'écran partout ailleurs.
6. **Écrans non maquettés ajoutés** : « Merci ! » (avant la demande du store) et « Merci de nous le dire ! » (tant qu'aucun formulaire externe n'est renseigné).
7. **Plus grand texte du système** : la carte défile au lieu de se couper.

### Ce qui est effacé, et quand
L'état local de la fenêtre (trois valeurs : première Miff vue, dernière apparition, issue ; fichier privé `miff_review_prompt`, hors sauvegarde) est **effacé à la déconnexion et à la suppression du compte** : la personne suivante repart de zéro. Aucune donnée personnelle.

### Plan de repli (AV3), tel qu'il s'applique à la version Android
**Ce que retire la mise à jour** : la fenêtre (`ui|domain|data/review`), sa carte de diagnostic, la dépendance `play-review`, le drapeau `REVIEW_PROMPT`, `FEEDBACK_FORM_URL`, `src/release/AndroidManifest.xml`, les clés `review_` (seule donnée locale : le fichier privé `miff_review_prompt`, aucune migration). **Ce qui la remplace** : la demande officielle (Play In-App Review ; SKStoreReviewController côté iOS) **sans question préalable d'opinion**, après un moment réussi ; le lien « Nous écrire » des Réglages reste **séparé** de la demande d'avis. **Aucune promesse** « avis modérés, filtrés ou vérifiés » n'est faite dans l'app ni dans les fiches.

### Points ouverts et cas limites connus (M5 de la revue, documentés sans correction)
- Le **quota de l'API Play** : le store n'affiche pas toujours sa fenêtre et ne le dit pas ; le repli (fiche du store) n'est pris que si l'API échoue **avant** d'avoir répondu (une fois la carte du store lancée, un échec ne rouvre pas la fiche par-dessus). À regarder à la recette ; sans effet visible, passer au repli seul.
- Un APK de recette n'est **pas** installé depuis Google Play : l'API échoue et c'est la **fiche du store** qui s'ouvre (qui peut dire « introuvable » tant que Miff n'est pas publiée).
- **Cas limites** : si l'app est en arrière-plan quand « Merci ! » se termine, la demande du store attend le retour de la personne (elle ne s'ouvre jamais depuis l'arrière-plan) ; un message de l'app ouvert à l'instant du retour fait attendre la fenêtre jusqu'au prochain retour au premier plan ; le retour au premier plan est compté une seule fois, même si le Feed n'est pas encore chargé 1,5 s après (la fenêtre attend alors le retour suivant) ; une horloge reculée retarde la fenêtre plutôt que de la montrer deux fois.
- **Bibliothèque** : `com.google.android.play:review` ajoute, par sa dépendance `core-common`, une activité transparente non exportée (`PlayCoreDialogWrapperActivity`). Elle est **retirée** du manifeste de release et de releaseLike (`android/app/src/release/AndroidManifest.xml`, vérifié par la CI) ; **le jour où le PM autorise la fenêtre dans un build de diffusion, supprimer ce fichier** et la ligne `manifest.srcFile` de releaseLike dans `build.gradle.kts`.
- **iOS** : écran à construire (une branche et une PR) ; mêmes constantes, mêmes clés, `SKStoreReviewController` à la place de Play In-App Review, mêmes deux boutons sans étoiles.
- **Reconfirmation à la porte « prêt à soumettre »** (§4) : à inscrire dans `docs/qa/release-checklist.md` (**à créer**) ; pour inclure la fenêtre dans un build de diffusion il faut `-Pmiff.reviewPrompt=true -Pmiff.allowReviewPromptInRelease=true` (et supprimer le manifeste de release ci-dessus), jamais autrement. Preuve de la garde : `./gradlew bundleRelease -Pmiff.reviewPrompt=true` **échoue** (résultat consigné dans la PR).

*Ce document ne modifie ni le code, ni Notion, ni Figma. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste.*
