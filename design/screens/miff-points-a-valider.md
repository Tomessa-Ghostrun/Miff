# Flux Miff — Points à valider par le PM, ambiguïtés et contradictions

**Rédigé le** : 2026-10-27 (agent `design-system`). Complète les fiches `miff-creation.md`, `miff-rejoindre.md`, `miff-ecran-miff.md`, `miff-dialogues.md`. Rien de ci-dessous n'est tranché : chaque point est signalé, pas inventé.

## 1. À valider par le PM (design et textes)
1. **Dessin Figma pas terminé** : seuls 5 composants (`Header`, `TextField`, `CodeField`, `Notice`, `Toast`) sont créés dans « Miff - Claude » (page Composants), sans contrôle visuel. Aucun écran du flux n'est dessiné (limite d'appels Figma atteinte). Voir `design/figma-design-system-state.json` > `fluxMiff` pour le plan restant. À toi : dis-moi si tu veux que je reprenne dès que le quota est à nouveau disponible.
2. **Titre de l'écran de création** (« Comment s'appelle ta Miff ? »), libellé du bouton de l'écran C (« Continuer »), libellés « Compris », « Terminer », « Fermer », « Vérification… », « On crée ta Miff… », « Retrait… », « Nouveau code créé » : aucun n'est dans §5.10 du ticket, tous proposés `[À CONFIRMER]`.
3. **Texte de l'écran E** : la liste « Ce que les membres voient de toi » et la phrase « Ils ne voient pas où tu es : seulement si tu es arrivé(e) ou parti(e) d'un lieu de confiance » sont des propositions. La seconde engage une promesse produit : à valider avec le PM et avec `backend-data` (aucune coordonnée stockée) avant toute diffusion.
4. **Numéro de téléphone** : le texte d'entrée du ticket cite prénom et photo seulement ; la décision du PM (visible des membres des Miffs de l'utilisateur) impose de le citer aussi (ticket Miff §4 #19). Formulation à valider ; un seul texte pour E, C, B (« Créer et partager ») et la reprise du partage (M2), avec un `consentVersion` unique.
5. **Couleur du lien « Supprimer la Miff » / « Quitter la Miff »** : `text/error` (#D60700) retenu pour l'action la plus lourde, sans fond rouge. Alternative plus douce : lien `action/primary`. À arbitrer (ton non anxiogène).
6. **Lien « Plus tard » sur l'écran C** : proposé `Continuer` plutôt que « Plus tard » (le code reste accessible depuis l'écran de la Miff).
7. **Polices à chasse fixe** pour le code : absentes du design system. Proposition : Hanken Grotesk Bold 20 + espacement de lettres 2 px en attendant ; à confirmer (ou ajouter une police mono libre).
8. **Emojis provisoires** des sections (✏️ 🔒 👥 📍) : comme dans l'onboarding, à remplacer par des icônes exportées de Figma par le PM.

## 2. Contradictions et ambiguïtés de la spec (signalées, pas résolues)
1. **« Miff pleine (15) » et le plafond doux** : le ticket dit que le plafond n'est appliqué qu'à l'écran de la Miff (texte « Cette Miff est bien remplie ! » à la place de « Ajouter un proche ») et qu'**aucune règle n'empêche** de rejoindre au-delà de 15 (§5.8, §10 cas 23). Il n'existe donc pas d'état d'erreur « Miff pleine » à l'entrée : l'état demandé est dessiné sur l'écran F (cadre F3). Si le PM veut un refus à l'entrée, c'est un changement de backend (Blaze).
2. **Consentement avant de voir les lieux** (R1 du ticket) : l'écran E ne peut pas montrer les lieux (règles Firestore). Le consentement est donc donné « à l'aveugle » côté lieux. Point d'attention éthique et store (anti-stalkerware), déjà assumé par le PM ; le design compense par le texte et l'arrêt en un geste.
3. **« Localiser » du Feed** : contredit le modèle sans coordonnées (QM11, hors périmètre ; signalé pour le ticket Feed). Aucun écran du flux Miff n'affiche de position.
4. **Écran de choix A** : le champ de code est intégré à la carte (écran 11) alors que la saisie vit sur une page dédiée (D). Je dessine D comme page dédiée ; la carte « Rejoindre » de l'écran 11 devra ouvrir D (ou garder le champ intégré, auquel cas D n'est que la variante d'erreur). Décision à prendre avec le ticket Onboarding.
5. **Section « Lieux » de l'écran 12 d'origine** : retirée de B (le code et les lieux n'existent pas avant la création). Le PM doit confirmer que ce n'est pas une perte voulue.
6. **Retrait d'un membre et visibilité par le membre retiré** : le message L est identique quelle que soit la cause (QM8). C'est volontaire ; l'écran ne peut pas distinguer retrait et suppression, et ne le doit pas.
7. **Réinvitation après un départ** : l'invitation ne s'affiche qu'une fois, à l'ouverture suivante, et seulement si l'app a connaissance du départ (aucun souvenir local sur un nouveau téléphone, §10 cas 22).
8. **« Prévenir mes proches »** : ne prévient personne automatiquement (pas de serveur) ; le libellé pourrait laisser croire que tout le monde est averti. Piste : « Préparer un message pour mes proches » / « Draft a message for my circle ». À arbitrer avec le PM (texte validé par lui : « Prévenir mes proches »).
9. **Écran de la Miff : où est le « code » pour un membre** ? Le ticket dit que tous voient et partagent le code (QM3) ; l'écran G l'affiche donc comme F. Conséquence connue : une fuite de code est une fuite de numéros de téléphone (décision N1 du PM, risque assumé).
10. **Boutons Localiser / Message / Appeler** : hors flux Miff, ticket Feed.

## 3. Écarts avec les principes de l'app (signalés, non implémentés tels quels)
- **Charge mentale** : l'écran de la Miff (F) concentre 6 sections ; risque de surcharge. Piste : replier « Lieux » et le renommage sous « Réglages de la Miff » ; les deux actions les plus fréquentes (partager le code, partager mon statut) restent en haut.
- **Ton** : les maquettes d'origine disaient « Êtes-vous sûr de supprimer cette miff ? … de manière irrévocable » (vouvoiement et mot anxiogène) : remplacés par les textes du ticket §5.10.
- **Confiance sur la localisation** : le statut de partage est visible à chaque écran concerné (ligne « Partager mon statut » sur F/G, texte explicite sur B/E) ; l'arrêt est à un geste.
- **Contrastes (AA)** : `text/muted` (noir à 50 %) et `text/secondary` (#7d7c7c) donnent environ 4,0:1 sur `bg/canvas` : insuffisant pour du texte informatif sous 18 px. Les fiches utilisent `text/description` (noir à 70 %, ≥ 7:1) pour tous les textes d'aide. `text/muted` reste réservé aux états désactivés. Le rouge de statut `#ff5650` n'est jamais utilisé en texte ; `text/error` #D60700 oui (5,4:1 sur blanc). Un texte `text/error` sur `status/danger-tint` n'atteint pas AA (≈ 4,1:1) : **ne jamais poser un texte d'erreur sur une teinte rouge** ; les `Notice Error` utilisent un fond blanc et un contour.

## 4. Ce que le PM doit fournir
Icônes exportées (retour, menu « ⋯ », sections), éventuelle police à chasse fixe, validation des textes `[À CONFIRMER]`, validation du ton du bouton « Prévenir mes proches ». Aucun secret, aucune action console.
