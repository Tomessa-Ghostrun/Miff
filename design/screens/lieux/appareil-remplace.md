# Appareil remplacé (écran 18)

**Sources** : `docs/tickets/session-appareil-unique.md` §2 (comportement), §8 (S3 : texte validé par le PM le 2026-11-01), critères SES1, SES5, SES8 ; modèle d'écran « accès perdu » de `design/screens/miff-dialogues.md` (L).
**Maquette visuelle** : `mockups/18-appareil-remplace`.
**Quand** : à l'ouverture de l'app (ou en direct si elle est ouverte) sur un téléphone dont la session a été remplacée par une connexion sur un autre téléphone. L'app a déjà arrêté la surveillance des zones et purgé son cache. Affiché **une seule fois**, jamais en boucle.

## Contenu
Écran plein cadre, fond dégradé `gradient/auth-background`, **pas de dialogue d'alarme, pas de rouge**.
1. Logo Miff discret (mot-symbole officiel).
2. Pictogramme : deux téléphones et deux flèches (celui de gauche atténué).
3. Titre (`display/headline`) : « Ton compte est maintenant utilisé sur un autre téléphone » / "Your account is now being used on another phone".
4. Texte : « Tu as été déconnecté(e) de celui-ci. » / "You've been signed out of this one." — **texte validé par le PM** (S3), coupé en titre et texte.
5. Encart d'information : « Un compte ne peut être utilisé que sur un seul téléphone à la fois. Tes Miffs et tes lieux n'ont pas bougé. » / "An account can only be used on one phone at a time. Your Miffs and places haven't changed." [ajout proposé]
6. Bouton principal **« Me reconnecter »** / "Sign back in" ; sous le bouton : « En te reconnectant ici, l'autre téléphone sera déconnecté. » / "Signing back in here will sign the other phone out." [ajout proposé, déduit du §2.4 du sous-ticket].

## Règles
- Jamais présenté comme une perte d'accès à une Miff (SES5) : l'encart dit explicitement que Miffs et lieux sont intacts.
- Si l'appareil était hors ligne, l'écran apparaît à son retour en ligne, **avant** toute écriture (SES2).
- « Me reconnecter » mène à l'écran de connexion habituel.
- Ton : informer calmement, sans culpabiliser (pas de « tentative suspecte », pas d'« intrus »).

## Accessibilité et plateformes
Focus initial sur le titre ; contraste texte/fond ≥ 7:1 ; bouton ≥ 44 pt. Identique sur Android (bouton retour système = quitte l'app) et iOS (aucun geste de retour).
