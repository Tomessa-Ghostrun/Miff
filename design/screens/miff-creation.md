# Flux Miff — Création d'une Miff (écrans B et C)

**Source** : `docs/tickets/miff.md` §3.1, §3.2 (B, C), §5.2, §5.3, §5.10, §5.11 ; décisions PM du 2026-10-24 (`miff-questions-pm.md` : QM2 étendu à la création, QM6, QM15).
**Fichier Figma de travail** : « Miff - Claude » (`RbX84qKbxppvAy5iypLYP2`), page prévue « Flux Miff ».
**État du dessin Figma** : **non dessiné** (limite d'appels MCP Figma atteinte, voir `design/figma-design-system-state.json`, bloc `fluxMiff`). Cette fiche est la spécification complète : elle suffit à dessiner (reste à faire, voir le plan dans `figma-design-system-state.json`) et à développer. Les composants locaux nécessaires sont déjà créés sur la page « Composants » (identifiants dans la section « Composants » ci-dessous).
**Plateformes** : valeurs neutres. Cadre de maquette : 320 × 660 (comme les écrans de l'onboarding), fond `gradient/auth-background` (B, C). Marges latérales 16, champs et boutons 288 de large dans le cadre 320.

## Composants utilisés
| Composant | Où | Notes |
|---|---|---|
| `Header` (`51:5`) | B | Flèche retour + titre « Nouvelle Miff ». Propriétés `Title`, `Retour` (affiche la flèche). **Le crochet de validation en haut à droite de l'écran 12 d'origine est retiré** (un seul bouton principal, ticket §4 #3) |
| `TextField` (`51:36`) | B | Variantes `State=Default / Focus / Filled / Error / Disabled`. Texte d'aide en `body/caption`, couleur `text/description` (pas `text/muted` : 4,0:1 sur le fond, sous AA) |
| `CodeField` (`51:57`) | C | `State=Empty / Partial / Valid / Error`. Ici seulement l'état `Valid`, sans le lien « Coller » |
| `Notice` (`51:70`) | B, C | `Tone=Info / Offline / Error` |
| `Toast` (`51:71`) | C | « Code copié » |
| `Button` (`6:8`, existant) | B, C | `Style=Primary` (désactivé : fond `action/primary-tint-30`) et `Style=Secondary` |
| Logo (`AppMockup`, composant existant ; **non modifié**) | C | À cloner depuis l'écran 11 (`37:22`) |

## B. « Nouvelle Miff » (écran de saisie du nom)
**But** : un seul champ, un seul bouton. Pas de code, pas de liste de proches, pas de lieux sur cet écran (le code n'existe pas encore : QM15 ; la section « Lieux » de l'écran 12 d'origine est retirée, ticket §4 #14).

**Structure (de haut en bas)**
1. `Header` : « Nouvelle Miff » / « New Miff ».
2. Titre de section (`display/title`) : « Comment s'appelle ta Miff ? » / « What's your Miff called? » `[À CONFIRMER]` (titre non fourni par la spec ; le texte d'aide est celui du PM).
3. `TextField` libellé « Nom » / « Name », placeholder « Famille Moudugenou » (repris de la maquette d'origine), aide : « Choisis un nom pour retrouver cette Miff facilement. Les personnes qui reçoivent ton code verront ce nom. » / « Pick a name so you can find this Miff easily. People who receive your code will see this name. »
4. Compteur de caractères discret sous le champ, aligné à droite, visible dès 30 caractères : « 34 / 40 ».
5. Bloc de consentement (carte `bg/surface`, `border/default`, radius `radius/md`, padding 16), **visible sans action et sans défilement** juste au-dessus du bouton : « En créant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » / « By creating this Miff, you share your status with its members; you can stop at any time in the Miff's settings. » Police `body/regular-14`, `text/primary`. Rappel système sous le texte (`body/caption`, `text/description`) : « Ton téléphone te demandera séparément l'accès à ta position. Tu peux le refuser. »
6. Bouton principal « Créer et partager » / « Create and share » (`Button/Primary`, 288 × 42).
7. Mention de vie privée facultative `[À CONFIRMER]` : aucune, le bloc 5 suffit (charge cognitive minimale).

**États à dessiner (4 cadres)**
| Cadre | État | Détail |
|---|---|---|
| B1 | Vide | Champ `Default`, bouton désactivé (fond `action/primary-tint-30`, `aria-disabled`) |
| B2 | Prêt | Champ `Filled` focus (`Focus`), 2 à 40 caractères, bouton actif |
| B3 | Enregistrement en cours | Champ `Disabled`, bouton actif remplacé par indicateur de progression (cercle 20 px blanc dans le bouton), libellé « On crée ta Miff… » / « Creating your Miff… » `[À CONFIRMER]` ; la touche retour est inactive. **Un seul appui = une seule opération** |
| B4 | Hors connexion ou erreur | `Notice Tone=Offline` (« Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. ») ou `Notice Tone=Error` (« Ça n'a pas fonctionné. Réessaie dans un instant. ») au-dessus du bouton ; bouton actif ; **rien n'est mis en attente** (ticket §5.11) |

**Règles** : nom de 2 à 40 caractères après suppression des espaces début et fin ; à 40, texte « Limite de 40 caractères atteinte. » en `Helper` ; emojis acceptés ; pas d'erreur rouge tant que l'utilisateur n'a pas tenté de valider (le bouton reste désactivé sous 2 caractères sans message alarmant).
**Accessibilité** : champ avec libellé lié ; le bloc de consentement est lu avant le bouton ; bouton désactivé annoncé comme tel ; contraste texte d'aide `text/description` sur fond dégradé ≥ 7:1 ; cibles ≥ 44 × 44.

## C. « Ta Miff est prête »
**Arrivée** : après l'accusé du serveur du lot de création (jamais avant). Le code est généré à l'enregistrement.

**Structure**
1. Pas de flèche retour (la Miff existe déjà) ; logo centré en haut (cloné de `37:22`), puis un titre `display/headline` : « Ta Miff « Famille Moudugenou » est prête » / « Your Miff "Famille Moudugenou" is ready ».
2. Texte (`body/regular`, `text/description`) : « Partage ce code avec tes proches pour qu'ils puissent la rejoindre. » / « Share this code with your loved ones so they can join. »
3. `CodeField State=Valid` en lecture seule, taille agrandie (hauteur 64), code par groupes de 4 (« XTZR SP36 7988 »), sans « Coller ». Police à chasse fixe `[À CONFIRMER]` : le design system n'a pas de police à chasse fixe ; en attendant, `title/bold-20` avec espacement de lettres +2.
4. Deux boutons : « Partager le code » / « Share code » (`Button/Primary`, ouvre la feuille de partage native) et « Copier » / « Copy » (`Button/Secondary`, copie le code seul).
5. Lien discret « Plus tard » / « Later » `[À CONFIRMER]` ou « Continuer » qui mène au Feed (action `body/bold-16`, `action/primary`). **Proposition : libellé « Continuer » / « Continue »** (le code reste accessible à tout moment depuis l'écran de la Miff).
6. Rappel (`body/caption`, `text/description`) : « Tu retrouveras ce code dans les réglages de ta Miff. » / « You'll find this code in your Miff's settings. »

**États** : C1 nominal ; C2 « Code copié » (`Toast` en bas, 2 s, annoncé par le lecteur d'écran) ; la feuille de partage est native (non dessinée, voir annexes). Le message partagé contient nom de la Miff, code et lien de téléchargement (QM14), jamais d'autres données.
**Accessibilité** : le code est annoncé par groupes de 4 (« X T Z R, S P 3 6, 7 9 8 8 ») ; l'écran ne dépend pas de la couleur.

## Composants (créés dans Figma, page « 🧩 Composants », section « Flux Miff — composants (MIFF-0) »)
`Header` `51:5` ; `TextField` `51:36` ; `CodeField` `51:57` ; `Notice` `51:70` ; `Toast` `51:71`. Créés le 2026-10-27 ; **non contrôlés visuellement** (aucune capture possible après la création, limite d'appels atteinte) : à vérifier au prochain accès.

## Assets manquants (à exporter par le PM)
- Icône flèche retour (importée en SVG noir, `icon/arrow-back`, couleur non liée à un token).
- Illustration éventuelle de célébration pour C : aucune prévue (non demandée).
- Police à chasse fixe pour le code.

## Annexes plateformes
**Android** : bouton « Partager » via le sélecteur système ; « Copier » : `EXTRA_IS_SENSITIVE` sur Android 13+ ; clavier ASCII, suggestions désactivées sur le champ de nom non requis (le nom accepte les emojis). Bouton retour système : ferme l'écran seulement avant la création.
**iOS** : `ShareLink` pour le partage ; champ de nom avec correction automatique possible ; geste de retour désactivé sur C.
