# Flux Miff — Écran de la Miff (créateur F, membre G), ligne de proche, « Partager mon statut »

**Source** : `docs/tickets/miff.md` §3.2 (F, G, M), §5.1, §5.3, §5.8, §5.9, §5.10, §5.13 ; décisions QM1, QM3, QM5, QM13.
**Points d'entrée** : pastille de Miff du Feed (ticket Feed) et « Mes Miffs » dans Réglages.
**État du dessin Figma** : **non dessiné** (limite d'appels MCP atteinte ; voir `design/figma-design-system-state.json`, bloc `fluxMiff`). Fiche complète.
**Cadre** : 320 de large, fond `bg/canvas`, **hauteur libre** (contenu défilant : montrer la page entière sur un cadre de 320 × ~900, avec une ligne repère « ligne de flottaison 660 »). Marges 16.

## Composants utilisés
`Header` (`51:5`), `MemberRow` (ligne de proche), `RolePill`, `ToggleRow` (« Partager mon statut »), `SectionHeader`, `Dialog` (voir `miff-dialogues.md`), `Notice` (`51:70`), `Button` (`6:8`), `TextField` (`51:36`, renommage), `CodeField` (`51:57`). `MemberRow`, `RolePill`, `ToggleRow`, `SectionHeader` et `Dialog` sont **prévus** (conception ci-dessous) : leur création dans Figma n'a pas pu être faite (limite d'appels).

## Composants à créer (spécification)
| Composant | Variantes / propriétés | Contenu et tokens |
|---|---|---|
| `RolePill` | `Role=Creator / You` ; texte « Créateur » / « Owner » ; « Toi » / « You » | Hauteur 20, radius `radius/full`, texte `caption/bold-12`. Creator : fond `action/primary-tint-10`, texte `action/primary`. You : fond `border/default`, texte `text/primary`. **Jamais en vert/jaune-vert** (couleur réservée au statut « en sécurité ») |
| `MemberRow` | Propriétés booléennes `Créateur`, `Toi`, `Menu` ; texte `Name` | Carte 288 de large, padding 12, radius `radius/sm`, fond `bg/surface`, contour `border/default`. À gauche `Avatar Status=Neutral` 46 px (photo de profil ; **aucun anneau de statut** : cet écran n'affiche aucun statut, ticket §5.9) ; au centre le prénom + nom (`display/label`) et, dessous, les `RolePill` ; à droite le bouton « ⋯ » (cible 44 × 44, `body/bold-16`, `text/primary`) **visible seulement pour le créateur et seulement sur les autres proches** (pas sur sa propre ligne) |
| `ToggleRow` | `State=On / Off / Disabled` | Carte 288, padding 14 × 16, radius `radius/md`, fond `bg/surface`, contour `border/default`. Titre « Partager mon statut » / « Share my status » (`body/bold-16`), description (`body/regular-14`, `text/description`), interrupteur 51 × 31 (On : fond `action/primary`, Off : fond `neutral/black-a30`, bouton rond blanc ; Disabled : 50 % d'opacité, hors connexion). Remplace par l'interrupteur natif de chaque plateforme dans l'app |
| `SectionHeader` | Propriétés `Icon`, `Title`, `Description` | Pastille 33 × 33 (`action/primary-tint-20`, radius `radius/md`) + titre `display/title` + description `body/caption` `text/description`. Même modèle que l'écran 12 d'origine ; emojis provisoires comme dans l'onboarding (à remplacer par les icônes exportées) |

## F. Écran de la Miff — créateur
**Structure (de haut en bas)**
1. `Header` : titre = **nom de la Miff** (« Famille Moudugenou ») ; plus de titre « Nouvelle Miff » (ticket §4 #5).
2. **Nom** (`SectionHeader` « Nom ») : `TextField Filled` modifiable ; bouton « Enregistrer » (`Button/Primary`, actif seulement si le nom est modifié et valide, 2 à 40 caractères) ; aide « Les personnes qui reçoivent ton code verront ce nom. ».
3. **Code** (`SectionHeader` « Ajouter des proches » / « Add loved ones », description « Partage ce code avec tes proches pour qu'ils puissent rejoindre cette Miff. ») : `CodeField Valid` lecture seule, boutons « Partager » / « Copier », puis lien « Créer un nouveau code » (ouvre le dialogue K, voir `miff-dialogues.md`). Texte du bouton historique « Ajouter un proche » (libellé du PM) : le bouton « Partager le code » en tient lieu (ticket §4 #6).
4. **Proches** (`SectionHeader` « Proches dans cette Miff ») : liste de `MemberRow` ; le créateur en premier avec `RolePill Creator` + `Toi` ; autres proches avec « ⋯ » (menu : « Retirer de la Miff » seul choix en v1 ; dessiné comme petit menu déroulant 160 × 44, radius `radius/md`, `shadow/default`, texte `body/regular` `text/primary`). **Pas de corbeille rouge.** État vide (0 autre proche) : « Pas encore de proche dans cette Miff. » (`body/regular-14`, `text/description`, centré), sans formule alarmante.
5. **Partager mon statut** : `ToggleRow` (voir M dans `miff-dialogues.md`). Description : « Tes proches voient ton statut. » (On) / « Ton statut n'est plus partagé avec cette Miff. » (Off) `[À CONFIRMER]`.
6. **Lieux de confiance** (`SectionHeader`, description « Voici les lieux liés à cette Miff. ») : liste de lieux (nom seul ; création et édition = ticket Lieux) ; vide : « Pas encore de lieu pour cette Miff. » ; bouton « Ajouter un lieu » **hors périmètre** (réservé ticket Lieux, emplacement marqué en pointillé dans la maquette).
7. **Pied** : texte (`body/regular-14`, `text/description`) « Tu as créé cette Miff : si tu veux la fermer, tu peux la supprimer. Les autres proches n'y auront alors plus accès. » + lien « Supprimer la Miff » (`body/bold-16`, couleur `text/error`, cible 44 pt) qui ouvre le dialogue J. **Pas de bouton « Quitter »** pour le créateur (QM1).

**États à dessiner**
| Cadre | État | Particularités |
|---|---|---|
| F1 | Nominal, 4 proches, partage actif | — |
| F2 | Invitation après un départ | `Notice Info` en haut de la liste : « Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif. » + bouton « Créer un nouveau code » + « Plus tard » (une seule fois par départ, sans insister) |
| F3 | Miff pleine (15 membres) | Le bouton « Partager le code » est remplacé par le texte calme « Cette Miff est bien remplie ! » (`body/regular`, `text/description`, aucun chiffre) ; le code reste visible et copiable (voir points à valider : le plafond est doux, non imposé par le serveur) |
| F4 | Partage arrêté (créateur) | `ToggleRow Off` ; ligne de la personne : mention « Partage arrêté » `body/caption` ; réciprocité : les autres proches n'affichent aucun statut (pas de statut sur cet écran de toute façon) |
| F5 | Hors connexion | `Notice Offline` en haut ; lecture seule depuis le cache ; boutons « Enregistrer », « Créer un nouveau code », « ⋯ », interrupteur, « Supprimer » désactivés (50 %) avec la mention calme de la `Notice` |
| F6 | Chargement | Squelettes gris (`bg/placeholder` à 40 %) : titre, 3 lignes de proche, pas d'animation si « réduire les animations » est activé |
| F7 | Renouvellement en cours | Bouton « Créer un nouveau code » avec indicateur ; code affiché inchangé jusqu'à l'accusé du serveur |

## G. Écran de la Miff — membre (nouveau)
Mêmes sections **en lecture seule** : titre non modifiable (pas de champ, pas d'Enregistrer), code visible et partageable (QM3), liste de proches **sans « ⋯ »**, `ToggleRow` « Partager mon statut », lieux en lecture, pied : texte « Tu peux quitter cette Miff à tout moment. Ta position ne sera plus partagée avec ce cercle. » `[À CONFIRMER]` + lien « Quitter la Miff » (`body/bold-16`, `text/error`, ouvre I). Pas de « Créer un nouveau code ».
États : G1 nominal ; G2 partage arrêté ; G3 hors connexion (mêmes règles que F5).

## Ligne de proche avec menu « ⋯ » (précisions)
- Menu : accessible par TalkBack (« Actions pour Camille ») et VoiceOver (actions personnalisées) ; la cible est de 44 × 44 pt même si l'icône fait 24.
- Libellé du menu : « Retirer de la Miff » / « Remove from Miff » (pas de verbe moralisateur : jamais « expulser », « exclure »).
- La ligne du créateur n'a jamais de menu ; sa pastille « Créateur » est visible de tous.
- Photo : avatar neutre, 46 px, aucune bordure de couleur (le design system réserve l'anneau au statut).

## Accessibilité (écran entier)
Ordre de lecture : titre, nom, code, proches, partage, lieux, action destructive en dernier. Les liens destructifs ne reposent pas sur la couleur seule (texte explicite). `text/error` #D60700 sur `bg/canvas` ≈ 5,2:1 (AA). Les interrupteurs exposent leur état.

## Assets manquants
Icônes des sections (emojis provisoires : ✏️ 🔒 👥 📍), icône « ⋯ » (texte provisoire), icône de menu. À exporter depuis Figma par le PM.

## Annexes plateformes
**Android** : menu « ⋯ » = `DropdownMenu` Material ; interrupteur Material 3 ; bouton retour système = retour au Feed ; copier : `EXTRA_IS_SENSITIVE` (Android 13+).
**iOS** : « ⋯ » = `Menu` (SwiftUI) ; `Toggle` natif ; actions VoiceOver personnalisées ; geste de retour.
