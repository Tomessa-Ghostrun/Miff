# Design system Miff

**Source de vérité visuelle** : les maquettes du fichier Figma original "Miff - App" (`Byi9NgQxkBPbmbhU403HSf`, lecture seule).
**Source des tokens et composants** : fichier de travail "Miff - Claude" (`RbX84qKbxppvAy5iypLYP2`), construit pour coller aux maquettes originales.
**Dernière mise à jour** : 2026-10-16, après recensement des 10 pages du fichier original (couleurs, polices, arrondis, ombres mesurés sur ~85 écrans, barre d'état exclue).

Les noms de code ci-dessous sont ceux enregistrés dans Figma (`codeSyntax`) : le nom Web est `var(--color-…)`, les noms Android et iOS sont identiques (camelCase).

> Règle pour les devs : ne jamais écrire de valeur en dur (hex, taille, arrondi) quand un token existe. Si une maquette utilise une valeur absente de ce document, c'est un écart à signaler, pas à improviser.

---

## 1. Couleurs

### Marque et actions
| Token | Valeur | Nom de code | Usage |
|---|---|---|---|
| `brand/primary` | `#d1d828` (jaune-vert) | `colorBrandPrimary` | Identité, anneau d'avatar "en sécurité", cœur du logo et de l'icône d'application |
| `action/primary` | `#043cd1` (bleu) | `colorActionPrimary` | Boutons principaux, liens, icônes actives, bordures d'accent |
| `action/primary-tint-5/10/20/30` | `#043cd1` à 5 / 10 / 20 / 30 % | `colorActionPrimaryTint5/10/20/30` | Fonds de pastilles d'icônes (20 %), cartes de choix (30 %), bouton désactivé (30 %) |
| `text/on-action` | `#ffffff` | `colorTextOnAction` | Texte sur fond bleu |

### Statuts (cœur du produit)
| Statut | Token plein | Token teinte (30 %) | Quand |
|---|---|---|---|
| En sécurité | `status/safe` `#d1d828` | `status/safe-tint` | Dans un lieu de confiance / update récente |
| Attention | `status/warning` `#ffb650` (orange) | `status/warning-tint` | Palier orange (voir `docs/specs/update-et-escalade-statut.md`) |
| Danger | `status/danger` `#ff5650` | `status/danger-tint` | Palier rouge, zone à risque |

Noms de code : `colorStatusSafe`, `colorStatusWarning`, `colorStatusDanger` et leurs `…Tint`. Le texte d'erreur de formulaire utilise `text/error` (`colorTextError`), qui pointe vers le rouge plus foncé `status/red-700` `#d60700` (même teinte que le statut danger, contraste 5,4:1 sur blanc, au-dessus du minimum WCAG AA de 4,5:1). `#ff5650` ne sert qu'aux anneaux, fonds et pastilles, jamais au texte (3,0:1, insuffisant).

> À surveiller : le texte des badges en couleur pleine sur teinte 30 % (jaune-vert `#d1d828` et rouge `#ff5650`) a lui aussi un contraste faible. Non traité ici : à arbitrer avec le PM au design des écrans qui affichent des badges.

### Fonds, textes, bordures
| Token | Valeur | Usage |
|---|---|---|
| `bg/canvas` | `#f9f9f7` | Fond d'écran standard |
| `bg/surface` | `#ffffff` | Cartes, champs, modales |
| `bg/placeholder` | `#d9d9d9` | Zones de photo en attente |
| `bg/overlay-header` | `#f9f9f7` à 60 % | En-tête translucide (flou) |
| `bg/gradient-end` | `#edefb9` | Bas du fond dégradé (voir §5) |
| `text/primary` | `#000000` | Texte courant |
| `text/form` | `#222222` | Texte saisi et titres des écrans d'authentification |
| `text/placeholder` | `#222222` à 50 % | Placeholders de champs |
| `text/description` | `#000000` à 70 % | Descriptions sous un titre |
| `text/muted` | `#000000` à 50 % | Texte atténué |
| `text/secondary` | `#7d7c7c` | Métadonnées (batterie, horodatage) |
| `border/default` | `#efefef` | Contour de carte |
| `border/strong` | `#000000` à 50 % | Contour de champ de saisie |
| `border/subtle` | `#000000` à 30 % | Contours discrets, séparateurs |
| `border/action` | `#043cd1` | Contour d'accent (modale, cartes) |

### Décisions de cohérence prises avec le PM (2026-10-16)
- **Un seul rouge** : les erreurs de formulaire (`#ea4335` dans les maquettes) passent au rouge `#d60700` (`text/error`), plus foncé que le rouge de statut `#ff5650` pour rester lisible (contraste AA, décision PM du 2026-10-22).
- **Un seul jaune-vert** : `#d2d929` et `#d1d52c` (quasi identiques dans les maquettes) sont ramenés à `#d1d828`. L'écart visuel est imperceptible. La fin du dégradé de titre reste `#d1d52c` dans le style Figma tant que les maquettes ne sont pas retouchées.

---

## 2. Typographie
Deux familles : **Baloo Thambi 2** (titres et noms, ton chaleureux) et **Hanken Grotesk** (tout le reste).

| Style | Police | Taille / interligne | Usage |
|---|---|---|---|
| `display/hero` | Baloo Thambi 2 | 64 / 72 | Grand texte décoratif (états vides), usage rare |
| `display/headline` | Baloo Thambi 2 | 24 / 30 | Message principal du Feed ("Tout le monde est en sécurité") |
| `display/title` | Baloo Thambi 2 | 20 / 26 | Titres d'écran (Réglages, notifications) |
| `display/label` | Baloo Thambi 2 | 16 / 22 | Prénoms dans les cartes |
| `title/bold-24` | Hanken Grotesk Bold | 24 / 30 | Accroche en dégradé (Bienvenue, Paywall) |
| `title/bold-20` | Hanken Grotesk Bold | 20 / 26 | Titres d'écrans de formulaire, titres de section |
| `body/large` | Hanken Grotesk Regular | 20 / 26 | Texte saisi, libellés de bouton larges |
| `body/regular` | Hanken Grotesk Regular | 16 / 22 | Texte courant (le plus utilisé) |
| `body/italic` | Hanken Grotesk Italic | 16 / 22 | Nom de la Miff dans le sélecteur |
| `body/bold-16` | Hanken Grotesk Bold | 16 / 22 | Libellés importants, liens |
| `body/regular-14` | Hanken Grotesk Regular | 14 / 20 | Descriptions courantes |
| `body/bold-14` | Hanken Grotesk Bold | 14 / 20 | Titres de ligne et de carte |
| `body/caption` | Hanken Grotesk Regular | 12 / 16 | Légendes, descriptions courtes (très utilisé) |
| `caption/bold-12` | Hanken Grotesk Bold | 12 / 16 | Liens secondaires |
| `caption/micro-10` | Hanken Grotesk Regular | 10 / 14 | Mentions très petites |
| `caption/tag-8` | Hanken Grotesk Regular | 8 / 10 | Pastille "Annulable à tout moment" |

**Exceptions** : la maquette utilise la police **Ubuntu Bold** sur les boutons "Continuer avec Apple/Google". Elle n'est **pas** reprise : ces deux boutons doivent utiliser les composants officiels de chaque plateforme (règle Apple et Google). Les polices Abel (barre d'état) et celles des captures d'écran sont du décor de maquette, à ignorer.

Polices à intégrer dans les apps : Baloo Thambi 2 et Hanken Grotesk (Google Fonts, licence libre).

---

## 3. Arrondis, ombres, traits
| Token | Valeur | Usage |
|---|---|---|
| `radius/md` (`RadiusMd`) | 10 | Boutons, champs, cartes de choix, pastilles d'icône |
| `radius/sm` (`RadiusSm`) | 5 | Cartes de membre, badges, notifications |
| `radius/full` (`RadiusFull`) | 9999 | Avatars, boutons ronds |
| `shadow/default` | 0 / 4 / 4 / noir 25 % | Boutons, cartes, pastilles |
| `shadow/subtle` | 0 / 0 / 3 / noir 8 % | Boutons de connexion sociale |

Traits : anneau d'avatar de statut 3 px ; contours de champ et de carte 1 px ; accent bleu 2 à 2,5 px.

Arrondis rares vus dans les maquettes (3, 15, 20) : à traiter comme des écarts, pas des tokens.

---

## 4. Composants (fichier "Miff - Claude", page "🧩 Composants")

| Composant | Variantes | Notes |
|---|---|---|
| **Avatar** | `Status=Safe / Warning / Danger / Neutral` | Cercle 46 px, anneau 3 px selon statut. La photo s'applique au niveau de l'instance. |
| **Button** | `Style=Primary / Secondary` | Primaire : fond `action/primary`, ombre `shadow/default`, arrondi `radius/md`. Propriété texte `Label`. État désactivé = fond à 30 %. |
| **Badge** | `Status=Safe / Warning / Danger` | Fond teinte 30 %, texte en couleur pleine, arrondi `radius/sm`. Propriété texte `Label`. |
| **StatusCard** | — | Avatar + nom + badge + % batterie + horodatage + photo. Contient des instances d'Avatar et de Badge. |
| **Tabbar** | — | Importée telle quelle comme image vectorielle (maison / bouton central / profil). Icônes non recolorables pour l'instant. |

### Composants ajoutés pour le flux Miff (2026-10-27, page « Composants », section « Flux Miff — composants (MIFF-0) »)
Créés dans Figma, **non encore contrôlés visuellement** (quota d'appels atteint juste après la création). Détail dans `design/screens/miff-*.md`.
| Composant | Identifiant | Variantes / propriétés |
|---|---|---|
| `Header` | `51:5` | Propriétés `Title`, `Retour`. 320 × 56, flèche retour + titre centré |
| `TextField` | `51:36` | `State=Default / Focus / Filled / Error / Disabled`. Contour 1 px `border/strong`, focus 2 px `border/action`, erreur 2 px `text/error` |
| `CodeField` | `51:57` | `State=Empty / Partial / Valid / Error`. Code par groupes de 4, lien « Coller » |
| `Notice` | `51:70` | `Tone=Info / Offline / Error`. Erreur : fond blanc et contour (jamais de texte rouge sur teinte rouge : contraste < AA) |
| `Toast` | `51:71` | Propriété `Text` |

À créer ensuite (spécifiés dans `miff-ecran-miff.md` et `miff-dialogues.md`) : `RolePill`, `MemberRow`, `ToggleRow`, `SectionHeader`, `Dialog`.

### Pas encore des composants (présents sur les écrans, à extraire avant la phase suivante)
Champ à soulignement, champ encadré, carte de choix (créer/rejoindre une Miff), modale d'explication de permission, en-tête avec retour et validation, ligne de fonctionnalité du Paywall, pastille de pagination, bouton rond "suivant", boutons Apple/Google (remplacés par les officiels en développement).

---

## 5. Dégradés (styles de remplissage Figma)
- `gradient/brand-title` : texte d'accroche, horizontal, `#043cd1` vers `#d1d52c`.
- `gradient/auth-background` : fond des écrans d'authentification et de configuration, vertical, `#f9f9f7` (à 17 %) vers `#edefb9`.

---

## 6. Écarts connus et limites
- **Pas de mode sombre** : les maquettes n'en prévoient pas.
- **Espacements non tokenisés** : seuls les arrondis le sont. Les marges sont à lire sur les maquettes ou à définir lors de l'extraction des composants.
- **Icônes** : réutilisées en vecteurs depuis le Figma original (flèche retour, appareil photo, pin de localisation, groupe, logos). À exporter proprement pour les apps.
- **Logo officiel et icône d'application** : fournis par le PM le 2026-10-24 (deux visuels, seuls fichiers existants), recréés en vectoriel dans `design/assets/logo/` (voir son `README.md`). Le **logo** est le mot-symbole « Miff » bleu `#043CD1` avec un cœur jaune-vert `#D1D828` à la place du point du « i ». L'**icône d'application** est un carré arrondi bleu `#043CD1` avec un grand « M » blanc gras dont la jambe droite est un « i » (le « M » et le « i » sont soudés), surmonté du cœur jaune-vert. L'ancienne icône provisoire (M géométrique avec cœur centré, présente dans `android/` et `ios/`) est à remplacer par ces visuels ; la recréation vectorielle reste à faire valider par le PM.
- **Fonds décoratifs** (formes organiques du carrousel de bienvenue) : importés comme vecteurs, non éditables.
- Certaines icônes de petites sections (Nouvelle Miff, Paywall) sont des emojis provisoires dans "Miff - Claude" : à remplacer par les vraies icônes avant la phase de développement des écrans concernés.
- **Couleurs secondaires suggérées** (lavande, rose, turquoise, image de la page "Design system" du Figma original) : non utilisées dans les écrans, donc **non incluses**. À ajouter si le PM les valide.
