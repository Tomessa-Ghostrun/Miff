# Ticket : fenêtre d'avis « J'adore ! / Peux faire mieux... » (pop-in d'avis du store)

**Statut** : **décision du PM enregistrée le 2026-11-07** ; **aucun développement lancé**, aucun statut Notion modifié. Ticket court créé parce qu'aucun ticket ne couvrait cette fenêtre (recherche faite dans `docs/tickets/`, `docs/specs/` et `design/exports/pop-ins/README.md`, le 2026-11-07).
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

- **Visuel** : celui de `03-avis-store.png` ; textes **au tutoiement** (décision du PM du 2026-11-01 : « Que penses-tu de Miff ? » à la place de « Que pensez vous de Miff ? »), français et anglais, sans accord de genre, clés `review_` (à ajouter à `docs/contracts/i18n-keys.md` et au contrôle de parité) ; accessibilité : boutons nommés, étoiles utilisables au lecteur d'écran, texte maximal et droite-à-gauche ; ton bienveillant (CLAUDE.md).
- **Comportement connu** : « J'adore ! » mène à la demande d'avis du store ; « Peux faire mieux... » **n'ouvre pas** la boîte d'avis du store (note du README du 2026-11-01). **Non précisé par la maquette, à fixer avant le développement avec le PM** (je ne l'invente pas) : ce qui s'affiche après « Peux faire mieux... » (remerciement, lien « Nous écrire »), **quand** la fenêtre apparaît et **à quelle fréquence** (le README renvoie au ticket Notion concerné ; aucune page Notion lue ne le dit), et si l'écran peut être refermé sans répondre.
- **Aucun événement Analytics** sur le contenu de la réponse sans décision (`docs/contracts/analytics-events.md` : liste blanche) ; **aucune permission nouvelle**, **aucune donnée personnelle** envoyée.

## 6. Critères d'acceptation

- [ ] **AV1** L'écran correspond à `03-avis-store.png` (écarts listés dans la PR), au tutoiement, FR et EN, avec parité des clés.
- [ ] **AV2** **Aucun build de diffusion** ne contient l'écran tant que le PM n'a pas **reconfirmé à la porte « prêt à soumettre »** ; la reconfirmation est consignée.
- [ ] **AV3** Le **plan de repli** (§3) est décrit dans la PR (ce que retire la mise à jour, ce qui le remplace) ; **aucune promesse** « avis modérés / filtrés / vérifiés » dans l'app ni dans les fiches.
- [ ] **AV4** Aucune donnée personnelle, aucune coordonnée, aucun nom dans un journal ou un événement ; aucune permission nouvelle.

## 7. Dépendances et découpage

- Android d'abord, puis iOS (une branche et une PR par plateforme). Dépend de la **reconfirmation du PM** pour toute soumission ; **ne bloque aucun autre ticket**.
- À signaler à `release-manager` : la fiche de revue Apple et les notes de revue ne mentionnent **pas** cet écran comme conforme ; si un store l'exige, appliquer le plan de repli.

*Ce document ne modifie ni le code, ni Notion, ni Figma. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste.*
