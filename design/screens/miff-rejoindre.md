# Flux Miff — Rejoindre une Miff (écrans D et E)

**Source** : `docs/tickets/miff.md` §3.2 (D, E), §5.3, §5.4, §5.10, §5.11, §10 ; décisions PM du 2026-10-24 (QM2 : rejoindre vaut partager, bouton « Rejoindre et partager »).
**État du dessin Figma** : **non dessiné** (limite d'appels MCP atteinte, voir le bloc `fluxMiff` de `design/figma-design-system-state.json`). Fiche complète pour dessiner et développer.
**Cadre** : 320 × 660, fond `gradient/auth-background`, comme l'onboarding.

## Composants utilisés
`Header` (`51:5`), `CodeField` (`51:57`, 4 états), `Notice` (`51:70`), `Button` (`6:8`), `Toast` si besoin. Lien « Coller » inclus dans `CodeField` (bouton, jamais de lecture automatique du presse-papiers).

## D. Saisie du code
**Point d'entrée** : bouton « Rejoindre une Miff existante » de l'écran de choix. Le PM avait dessiné deux versions (champ intégré à la carte de choix ; page dédiée) : **cet écran est la page dédiée** (un seul composant de saisie, ticket §4 #9). L'écran de choix garde sa carte « Rejoindre », qui ouvre cet écran (le champ n'est plus intégré à la carte).

**Structure** : `Header` « Rejoindre une Miff » / « Join a Miff » ; texte d'aide « Saisis le code que ton proche t'a donné. » / « Enter the code your loved one gave you. » ; `CodeField` ; bouton « Continuer » / « Continue » (`Button/Primary`, désactivé tant que les 12 caractères valides ne sont pas saisis).

**Saisie** : majuscules automatiques, alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, espaces et tirets ignorés, groupes de 4 affichés automatiquement.

**États à dessiner (7 cadres)**
| Cadre | État | Contenu |
|---|---|---|
| D1 | Vide | `CodeField Empty` (« XXXX XXXX XXXX » en placeholder), bouton désactivé |
| D2 | 12 caractères valides | `CodeField Valid`, bouton actif |
| D3 | Vérification en cours | Champ désactivé, bouton avec indicateur et libellé « Vérification… » / « Checking… » `[À CONFIRMER]` |
| D4 | Code introuvable | `CodeField Error`, texte : « On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. » / « We can't find a Miff with this code. Check it with the person who gave it to you. » (un seul message, quelle que soit la cause : code renouvelé, Miff supprimée, retrait) |
| D5 | Caractère impossible (message local, sans réseau) | `CodeField Error`, texte : « Ce code ne ressemble pas à un code Miff. Vérifie les lettres et les chiffres. » / « This doesn't look like a Miff code. Check the letters and numbers. » ; peut être montré dans le même cadre que D4 en variante |
| D6 | Pause après 5 essais | `Notice Info` : « On fait une petite pause d'une minute avant de réessayer. » / « Let's take a one-minute break before trying again. » ; bouton désactivé ; pas de compte à rebours alarmant (pas de rouge) |
| D7 | Déjà membre | Carte `bg/surface` : « Tu fais déjà partie de la Miff « Famille Moudugenou ». » / « You're already part of the Miff "Famille Moudugenou". » + bouton « Ouvrir la Miff » / « Open Miff » ; aucune écriture |
| D8 (catalogue) | Hors connexion / erreur réseau ou quota | `Notice Offline` ou `Notice Error` (mêmes textes que B4) ; quota ou App Check refusés : même message, jamais le mot « quota » |

**Accessibilité** : l'erreur est annoncée (`liveRegion` / `accessibilityLiveRegion`) ; le texte d'erreur utilise `text/error` (#D60700, 5,4:1) ; l'erreur ne repose pas sur la seule couleur (texte + contour 2 px).

## E. Confirmation avant d'entrer (nouveau, décision QM2)
**Quand** : après vérifications (profil présent, format, code connu, pas déjà membre, index obsolète nettoyé). C'est **l'écran du consentement explicite** : tout est visible **sans action et sans défilement**, sur un téléphone de 320 pt de large avec la taille de texte maximale.

**Structure (de haut en bas)**
1. `Header` avec flèche retour (« Annuler » équivaut au retour et ne fait rien).
2. Titre `display/headline` : « Rejoindre la Miff « Famille Moudugenou » ? » / « Join the Miff "Famille Moudugenou"? »
3. Carte de consentement (`bg/surface`, contour `border/action` 2 px, radius `radius/md`, padding 16) contenant :
   - Texte principal (`body/regular`, `text/primary`) : « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » / « By joining this Miff, you share your status with its members; you can stop at any time in the Miff's settings. »
   - Ligne « Ce que les membres voient de toi » (`body/bold-14`) avec liste courte `body/regular-14` : ton prénom, ton nom, ta photo `[À CONFIRMER : et ton numéro de téléphone, voir la liste des points à valider]` ; « Ils ne voient pas où tu es : seulement si tu es arrivé(e) ou parti(e) d'un lieu de confiance. » `[À CONFIRMER]` (ce dernier texte décrit le modèle : aucune coordonnée stockée, `data-model.md` §1 ; à valider par le PM car il engage une promesse).
   - Rappel système (`body/caption`, `text/description`) : « Ton téléphone te demandera séparément l'accès à ta position. Tu peux le refuser : tu rejoins la Miff quand même. » / « Your phone will ask separately for access to your location. You can say no and still join. »
4. Bouton principal « Rejoindre et partager » / « Join and share » (`Button/Primary`) ; lien secondaire « Annuler » / « Cancel » (`body/bold-16`, `action/primary`, cible 44 pt).

**États** : E1 nominal ; E2 en cours (bouton avec indicateur, tout inactif) ; E3 refus (code renouvelé ou Miff supprimée entre-temps) : retour à D avec le message de D4 ; E4 hors connexion : `Notice Offline` au-dessus du bouton, rien en attente. Succès : navigation vers le Feed, puis demande de localisation par le ticket Localisation (hors périmètre ; non bloquante).
**Pas de statut ni de position** affichés ici (ticket §5.9). Aucun lieu n'est visible avant l'entrée (les règles l'interdisent) : l'écran ne montre pas les lieux. **Point d'attention** (risque R1 du ticket) : l'utilisateur consent avant de voir les lieux surveillés ; l'écran compense par le texte et l'arrêt possible en un geste dès l'arrivée.

## Assets manquants
Aucun nouvel asset. Icône « coller » : texte seul « Coller » (pas d'icône).

## Annexes plateformes
**Android** : clavier ASCII, majuscules, suggestions et correction désactivées ; « Coller » lit le presse-papiers **uniquement sur appui** ; bouton retour système = « Annuler ».
**iOS** : `PasteButton` (iOS 16, pas d'alerte de collage) remplace le lien « Coller » ; `.autocorrectionDisabled()` ; geste de retour = « Annuler ». Dynamic Type testé sur E.
