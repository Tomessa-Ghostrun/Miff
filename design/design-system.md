# Design system Miff

**Source de vérité visuelle** : les maquettes du fichier Figma original "Miff - App" (`Byi9NgQxkBPbmbhU403HSf`, lecture seule).
**Source des tokens et composants** : fichier de travail "Miff - Claude" (`RbX84qKbxppvAy5iypLYP2`), construit pour coller aux maquettes originales.
**Dernière mise à jour** : 2026-10-16, après recensement des 10 pages du fichier original (couleurs, polices, arrondis, ombres mesurés sur ~85 écrans, barre d'état exclue). **Complété le 2026-11-07** : le §7 recense les jetons et composants **réellement livrés dans les apps** pour le Feed, la barre d'onglets en verre et l'Update (valeurs lues dans `android/app/src/main/java/com/tomessa/miff/ui/` et `ios/Miff/DesignSystem/`, fichier cité à chaque ligne), et le §6 liste les écarts maquette / implémentation.

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
| Danger | `status/danger` `#ff5650` | `status/danger-tint` | Palier rouge, zone de danger |

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
| `display/bubble` | Baloo Thambi 2 (comme `display/headline`) | **30 / 36** | **Phrase principale de la bulle du Feed UNIQUEMENT** (« Tout va bien », « Pas encore de nouvelles ») : même police et même graisse que `display/headline`, seule la taille change (décision du PM du 2026-11-11) |
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

### 2.1 Phrase principale de la bulle du Feed (`display/bubble`, décisions du PM des 2026-11-10 et 2026-11-11)

Le PM voulait d'abord (2026-11-10) la phrase principale de la bulle en gras et dans une écriture proche du logo (Baloo Thambi 2 ExtraBold, 800). Après la recette de l'APK (2026-11-11), il a **renoncé à la police en gras** : la phrase **garde la taille agrandie** mais **la police redevient homogène avec le reste de l'app**.
- **Police : celle de `display/headline`**, c'est-à-dire **Baloo Thambi 2, graisse Regular (400)**, nom PostScript `BalooThambi2-Regular` (instance par défaut de la police variable). Aucune police ni graisse supplémentaire ; licence SIL OFL 1.1 déjà documentée (`android/third_party/fonts/OFL-BalooThambi2.txt`, `ios/third_party/fonts/`).
- **Taille : 30 pt / 36 (iOS) ; 30 sp / 36 (Android)**, contre 24 / 30 pour `display/headline`. Suit la taille de texte du système (sp sur Android, Dynamic Type sur iOS avec `dynamicTypeStyle: .title2`, comme `display/headline`) ; **pas de plafond** : la bulle grandit en hauteur, le texte passe à la ligne, jamais de débordement horizontal.
- **Couleur et alignement** : `text/primary`, centré. Lignes **équilibrées** quand la plateforme le permet (Android `LineBreak.Heading` ; iOS : rien à faire, le centrage suffit).
- **Périmètre** : **uniquement cette phrase de la bulle**. Le reste du Feed (prénoms des cartes, lignes, étiquettes, boutons) ne change pas.
- **iOS** : **le style `textDisplayHeadline` (Baloo Thambi 2, `BalooThambi2-Regular`, poids 400) à la taille 30 / 36**, `dynamicTypeStyle: .title2` ; aucun réglage de variation de police (surtout pas `balooBoldFont`).
- **Android** : `textDisplayBubble = textDisplayHeadline.copy(fontSize = 30.sp, lineHeight = 36.sp)` (`ui/theme/Type.kt`), appliqué dans `Headline` de `ui/feed/FeedBubble.kt`.

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
| **StatusCard** | — | Avatar + nom + badge + % batterie + horodatage + photo. Contient des instances d'Avatar et de Badge. **Version livrée (carte membre du Feed)** : voir §7.3 ; la batterie n'affiche plus d'heure de mesure (D44), l'aperçu de photo est un carré centré (D39), plus de pastille « Toi » (D42). |
| **Tabbar** | — | Barre flottante à 3 entrées (maison classique, décision du PM du 2026-11-08 / bouton central / profil) **en verre translucide** : Liquid Glass natif sous iOS 26 (repli matériau sous iOS 16 à 25), flou d'arrière-plan sous Android 12+ (repli sans flou, puis opaque), deux variantes (claire, sur photo), bouton central à la couleur du statut. Spécification complète, tokens `tabbar/*`, contrastes et accessibilité : `design/tab-bar-liquid-glass.md` ; écran Update : `design/screens/update.md`. Les icônes sont des symboles système sur iOS ; la maison n'a plus de cœur depuis le 2026-11-08. |

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

### Écarts connus entre le design system, les fiches et les apps (relevés le 2026-11-07)
| # | Écart | Où | Statut |
|---|---|---|---|
| 1 | **Espacements** : le §6 dit « non tokenisés » ; Android a un objet `Spacing` (4 / 8 / 16 / 24 / 32, cible tactile 48, largeur du logo 200, avatar de membre 46) qui « évite d'éparpiller les nombres bruts » et déclare lui-même n'être pas un jeton Figma | `ui/theme/Spacing.kt` | Pas de jeton Figma ; iOS : aucun équivalent recensé |
| 2 | **Jetons du Feed absents d'iOS** : `status/*-text`, gris neutre, jetons de l'Update vivent dans des fichiers Android à part (`FeedColor.kt`, `UpdateColor.kt`) pour ne pas casser le test de parité des couleurs ; **aucun n'existe dans `ios/Miff/DesignSystem/ColorTokens.swift` sur `main`** (pas de Feed iOS) | Android / iOS | À ajouter côté iOS avec le Feed iOS, mêmes noms et mêmes valeurs |
| 3 | **`bg/overlay-header` (`#f9f9f7` à 60 %, flou)** : défini ici, **non utilisé par l'en-tête du Feed livré** (fond `bg/canvas` opaque, ombre `shadow/subtle`) | `ui/feed/FeedHeader.kt` | Voir `screens/feed/entete-et-selecteur.md` |
| 4 | **`display/headline` 24 / 30** ici ; les fiches du Feed disent 24 / 32 (bulle) et 17 / 24 (prénom) alors que le code suit le jeton (24 / 30 et `display/label` 16 / 22) ; **depuis le 2026-11-10, la phrase principale de la bulle est `display/bubble` (même police, 30 / 36)** (§2.1), `display/headline` ne sert plus à la bulle | `ui/feed/FeedBubble.kt`, `FeedCards.kt` | Le jeton fait foi |
| 5 | **Seuil de masquage de la batterie** : 6 h (décision du PM, 2026-11-07) contre 24 h dans le ticket et le code de la PR #105 | `docs/tickets/feed.md`, `domain/feed/FeedBuilder.kt` | Incohérence non tranchée (voir `screens/feed/cartes-membres.md`) |
| 6 | **Mouvement de la bulle** : précision du PM (étirement / écrasement déphasés) absente du code de `main` | `ui/feed/BubbleMotion.kt` | Voir `screens/feed/bulles.md` |
| 7 | **Ombre de la barre d'onglets** : Android `shadow/default`, iOS noir 18 %, rayon 10, décalage 6 | `ui/main/GlassTabBar.kt`, `ios/.../TabBarTokens.swift` (PR #102) | Voir `tab-bar-liquid-glass.md` §14 |
| 8 | **Tailles d'icônes de la barre** : 26 (Android, spécification) contre 24 (iOS, PR #102) | idem | Voir `tab-bar-liquid-glass.md` §14 |
| 9 | **`display/bubble` = `display/headline` en 30 / 36** : Android le déclare avec `copy(fontSize, lineHeight)` (pas `style(...)`), donc hors du test de parité `testTextStylesAreIdenticalToAndroid` ; **suivi** : la PR iOS ajoute `textDisplayBubble` (même style que `display/headline`, 30 / 36, `.title2`), puis une mini-PR Android pourra le repasser en `style(FontFamilyBaloo, 30, 36)` pour que le test le couvre | `ui/theme/Type.kt` | À faire avec la PR iOS de la bulle |

---

## 7. Jetons et composants livrés dans les apps (Feed, barre d'onglets, Update)

> Rien ci-dessous n'est une proposition : chaque valeur est copiée du code, avec son fichier. Racine Android : `android/app/src/main/java/com/tomessa/miff/ui/`. iOS : `ios/Miff/DesignSystem/` (la barre d'onglets iOS n'est que dans la PR brouillon #102, non fusionnée ; le Feed et l'Update n'existent pas encore sur iOS).

### 7.1 Jetons de couleur ajoutés par le Feed et l'Update
| Token | Valeur | Nom de code | Fichier | Usage |
|---|---|---|---|---|
| `status/safe-text` | `#4d5200` | `colorStatusSafeText` | `ui/theme/FeedColor.kt` | Texte et pictogramme **sur** la teinte 30 % verte (7,28:1) |
| `status/warning-text` | `#7a4300` | `colorStatusWarningText` | `ui/theme/FeedColor.kt` | Idem, orange (6,76:1) |
| `status/danger-text` | `#a30500` | `colorStatusDangerText` | `ui/theme/FeedColor.kt` | Idem, rouge (5,73:1) |
| `status/neutral` | `#c9c4bd` | `colorStatusNeutral` | `ui/theme/FeedColor.kt` | Anneau, liseré et bulle d'une personne **sans statut connu** : jamais de vert quand on ne sait pas |
| `status/neutral-tint` | `#c9c4bd` à 45 % | `colorStatusNeutralTint` | `ui/theme/FeedColor.kt` | Teinte de la bulle neutre |
| `status/warning-bubble` | `#face90` | `colorStatusWarningBubble` | `ui/theme/UpdateColor.kt` | Bulle d'incitation orange (texte noir 14,3:1) |
| `status/danger-bubble` | `#fa9490` | `colorStatusDangerBubble` | `ui/theme/UpdateColor.kt` | Bulle d'incitation rouge (9,7:1) |
| `update/stage` | `#1d1f20` | `colorUpdateStage` | `ui/theme/UpdateColor.kt` | Fond sombre autour de la photo de l'aperçu |
| `update/scrim` | noir à 45 % | `colorUpdateScrim` | `ui/theme/UpdateColor.kt` | Fond de la croix de l'aperçu |
| `update/veil` | noir à 30 % | `colorUpdateVeil` | `ui/theme/UpdateColor.kt` | Voile sur la photo en cours de partage |
| `update/toast` | `#222222` à 92 % | `colorUpdateToast` | `ui/theme/UpdateColor.kt` | Pastille calme de confirmation |

### 7.2 Jetons de la barre d'onglets en verre (`tabbar/*`)
Source : `ui/theme/GlassTokens.kt` (Android) ; `ios/Miff/DesignSystem/TabBarTokens.swift` (iOS, PR #102). Mêmes valeurs sur les deux plateformes.
| Token | Variante claire | Variante sur photo |
|---|---|---|
| `tabbar/glass-tint` (voile, avec flou ou matériau) | blanc 80 % | noir 60 % |
| `tabbar/glass-tint-noblur` (sans flou, Android) | blanc 92 % | noir 72 % |
| `tabbar/surface-opaque` | `#ffffff` | `#222222` |
| `tabbar/glass-border` (1 dp / 1 pt) | blanc 55 % | blanc 18 % |
| `tabbar/icon-active` | `action/primary` | `#ffffff` |
| `tabbar/icon-inactive` | `#595959` | blanc 78 % |
| `tabbar/pill-active` | `action/primary-tint-20` | blanc 24 % |
| `tabbar/blur-radius` | 20 dp (Android seulement) | idem |
| Plaque du bouton central | `#ffffff` opaque (`GlassCentralPlate`) | idem |
Le choix de la variante est fait **par l'écran** (claire : Feed, Profil ; sur photo : viseur de l'Update), jamais par un réglage.

### 7.3 Composants livrés
| Composant | Description réelle | Fichier |
|---|---|---|
| **Barre d'onglets en verre** | Barre flottante de 64 dp, arrondi 20 dp, marges latérales 24 dp, 22 dp au-dessus du bas ; 3 entrées de 64 x 48 ; verre à trois niveaux (Android : flou Haze 2.0.1 + voile + liseré / sans flou / opaque ; iOS : verre natif `glassEffect` sous iOS 26 / `.regularMaterial` + voile sous iOS 16 à 25 / opaque avec « Réduire la transparence » ou « Augmenter le contraste ») ; icônes de 26 dp (Android) ; le contenu défile **sous** la barre | `ui/main/MainTabBar.kt`, `GlassTabBar.kt` ; `ios/Miff/Views/Main/MainTabBar.swift` (PR #102) |
| **Bouton central** | Plaque blanche opaque 44, anneau 3 de `action/primary` (36 de diamètre), pastille de statut 24 (vert, orange, rouge) ; neutre : anneau et pastille `#595959` ; **aucun pictogramme** ; ombre noir 30 %, décalage 1, flou 4 | `ui/main/GlassTabBar.kt` (`CentralStatusButton`) ; `MainTabBar.swift` |
| **Avatar de statut** | Disque de 46 dp, anneau 3 dp (`StrokeAvatarRing`) de la couleur du niveau, fond `status/neutral`, initiales `body/bold-14` ; **couleur seule, aucun pictogramme** ; décoratif pour le lecteur d'écran | `ui/feed/FeedStyle.kt` (`FeedAvatar`) |
| **Pastille de statut (étiquette d'état)** | Texte `body/regular-14` en `status/*-text` sur la teinte 30 % du niveau, arrondi `radius/sm`, marges 8 / 4 ; **texte seul, sans pictogramme** ; seule l'étiquette « dans un lieu » est précédée de **l'emoji de la catégorie du lieu** (taille du texte, décoratif ; `docs/contracts/place-categories.md` §1 bis) | `ui/feed/FeedCards.kt` (`StateChip`), `FeedStyle.kt` (`tint()`, `onTint()`) |
| **Carte membre** | Fond `bg/surface`, arrondi `radius/sm`, ombre `shadow/subtle`, contour 1 dp `border/default`, liseré de 5 dp de la couleur du niveau ; avatar, prénom `display/label`, pastille de batterie (« 85 % » seul), étiquette d'état, ligne de temps, aperçu carré de la photo, boutons Localiser / Message / Appeler de 48 dp (aucun sur ma carte, qui est la dernière et porte la ligne « Ce que tes proches voient de toi ») | `ui/feed/FeedCards.kt`, `FeedPhoto.kt` |
| **Bulle de la Miff** | Tache organique (10 points), teinte 30 % du niveau, hauteur minimale 176 dp, marges 40 / 56 / 40 / 46 dp ; phrase principale `display/bubble` (`display/headline` en 30 / 36, §2.1)  ; **aucun pictogramme** (PM, 2026-11-11) ; bouton « Contacter » plus petit (36 dp dessinés, 48 dp de toucher, sans icône) ; mouvement lent continu (`BubbleMotion.kt`), immobile sans animations ; carrousel avec pastilles 8 dp (page courante 22 x 8 dp) | `ui/feed/FeedBubble.kt`, `BubbleMotion.kt` |
| **En-tête rétractable du Feed** | Avatar (zone 48 dp, anneau du statut) · logo 100 dp de large · pastille de Miff ; fond `bg/canvas` opaque, ombre `shadow/subtle`, marges 16 dp ; **se masque au défilement vers le bas, réapparaît dès qu'on remonte** ; toujours visible : chargement, erreur, hors connexion, lecteur d'écran actif, sélecteur ouvert | `ui/feed/FeedHeader.kt`, `CollapsingHeader.kt` |
| **Pastille de Miff** | Une seule Miff : fond noir 5 %, 40 dp de haut, non cliquable, sans ombre ni flèche ; Miff+ : fond `bg/surface`, ombre `shadow/default`, 48 dp, flèche bleue, liste de lignes de 48 dp | `ui/feed/FeedHeader.kt` (`Pill`, `MiffPill`) |
| **Bulle d'incitation (Update)** | Rectangle arrondi 20 dp aux couleurs `status/*-bubble`, texte `body/italic`, marges 18 dp (côtés) et 16 dp (haut, bas), queue de 22 x 11 dp à 4 dp au-dessus de la barre, opaque, annoncée comme région polie | `ui/update/UpdateBubble.kt` |
| **Viseur (Update)** | Plein écran ; déclencheur de 80 dp (anneau blanc 4 dp, anneau bleu 3 dp), à 48 dp au-dessus de la barre ; bouton de bascule de 48 dp ; dégradé sombre de 110 dp en haut ; double toucher = changer de caméra | `ui/update/UpdateScreen.kt` |
| **Tirer pour actualiser** | Indicateur Material 3 (`PullToRefreshBox`), fond `bg/surface`, trait `action/primary`, sous l'en-tête ; 1 relecture serveur par 15 s, 8 s au plus | `ui/feed/FeedScreen.kt`, `FeedViewModel.kt` |

### 7.4 Règles transverses confirmées par le code
- **Couleur seule autorisée** pour le niveau sur l'anneau d'avatar, le liseré de carte et la pastille du bouton central (décision du PM, 2026-11-06) ; les **mots** de l'état restent dans l'étiquette et dans le nom parlé.
- **Texte coloré** sur teinte 30 % : toujours les teintes `status/*-text`, jamais `status/*` pleine.
- **Aucune animation d'alarme** ; seul mouvement continu autorisé : la bulle de la Miff (D40), jamais accéléré selon le niveau, immobile avec les animations supprimées.
- **Cibles tactiles** : 48 dp minimum (`Spacing.minTouchTarget`).
