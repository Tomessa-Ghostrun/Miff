# Logo et icône de Miff : recréation vectorielle (2026-10-24)

Dossier produit par l'agent `design-system`. Le PM n'a aucun fichier vectoriel : il a fourni **deux visuels raster définitifs** (le 2026-10-24), qui doivent servir partout. Ils ont été **redessinés en vectoriel** (formes géométrisées) et déclinés pour iOS, Android, le splash et le web.

**Les icônes actuelles de `android/`, `ios/` et `web/` n'ont PAS été modifiées.** Des PR dev les remplaceront après validation du PM. Rien n'a été écrit dans Figma.

Aperçu à montrer au PM : `preview/miff-comparatif-source-vs-recreation.png` (source, recréation, écart) et `preview/miff-icon-masks-preview.png` (l'icône sous les masques Android et iOS).

## 1. Les deux visuels définitifs (sources)

| Fichier | Contenu | Taille |
|---|---|---|
| `source/miff-app-icon-pm-provided.png` | **Icône de l'app** : carré arrondi bleu `#043CD1`, grand « M » blanc gras dont la jambe droite est un « i » (M et i soudés), cœur jaune-vert `#D1D828` à la place du point du « i » | 501 x 459 px, flou |
| `source/miff-wordmark-pm-provided.png` | **Logo (mot-symbole)** « Miff » en bleu, cœur jaune-vert sur le « i » | 1035 x 466 px, flou |

Couleurs mesurées sur les sources (moyennes) : bleu `#0038CD` à `#013CD2`, jaune-vert `#D1D528` à `#D2D528`, blanc `#FCFCFE`. Elles correspondent aux tokens du design system (`action/primary` `#043CD1`, `brand/primary` `#D1D828`, `neutral/white` `#FFFFFF`) : **les tokens ont été utilisés dans la recréation**.

Fichier complémentaire : `source/miff-logo-figma-raster-663x376.png` est le raster du logo trouvé dans Figma (fichier original « Miff - App » `Byi9NgQxkBPbmbhU403HSf`, nœud `24:216`, écran d'accueil `23:162`). C'est une **autre version du mot-symbole** (cœur environ 10 % plus petit par rapport aux lettres, proportions légèrement différentes) : elle n'est **pas** utilisée pour la recréation, qui suit les visuels fournis par le PM. La planche de la page « Design system » du Figma original (nœud `78:13`) n'a pas été utilisée non plus (suggestions d'une analyse de logo, pas des décisions du PM).

Origine et droits : œuvre du PM (fichiers fournis par lui le 2026-10-24). **Le PM doit confirmer qu'il détient les droits sur le dessin** (en particulier s'il a été généré avec un outil d'IA : vérifier les conditions d'usage commercial de l'outil avant publication sur les stores).

## 2. Contenu du dossier

**`master/` : sources vectorielles (SVG, chemins uniquement)**

| Fichier | Usage |
|---|---|
| `miff-app-icon.svg` | Icône d'app, carré arrondi (1024 x 1024, coins arrondis à 21,4 %) |
| `miff-app-icon-fullbleed.svg` | Icône d'app plein cadre, sans coins arrondis (pour les stores qui arrondissent eux-mêmes) |
| `miff-wordmark-color.svg` | Mot-symbole couleur (lettres `#043CD1`, cœur `#D1D828`), viewBox 981 x 380, sans marge |
| `miff-wordmark-reverse.svg` | Lettres blanches, cœur jaune-vert (fonds sombres ou bleus) |
| `miff-wordmark-white.svg` | Monochrome blanc |
| `miff-wordmark-mono-blue.svg` | Monochrome bleu |
| `miff-heart.svg` | Cœur seul du mot-symbole (extrait, pas une déclinaison validée) |
| `miff-mark-color.svg`, `miff-mark-reverse.svg`, `miff-mark-white.svg` | « Marque » seule : le M + i + cœur de l'icône, sans le carré (bleu, blanc + cœur jaune-vert, blanc) |

**`ios/`** : `AppIcon-1024.png` (fond bleu plein, **sans alpha et sans coins arrondis** : iOS applique son masque), `AppIcon-1024-dark.png` et `AppIcon-1024-tinted.png` (variantes iOS 18, voir §4). Tous en PNG RGB 1024 x 1024, vérifiés sans canal alpha. Pour le mot-symbole dans les assets iOS : utiliser `master/miff-wordmark-*.svg` (Xcode accepte le SVG avec « Preserve Vector Data » ; pas de PDF produit).

**`android/`**
- `ic_launcher_foreground.xml` et `ic_launcher_monochrome.xml` : VectorDrawable 108 x 108 dp, prêts à copier ; équivalents `.svg` et aperçus `-432.png`. Arrière-plan : couleur unie `#043CD1` (déjà `ic_launcher_background` dans `colors.xml`).
- `play-store-icon-512.png` : icône Google Play 512 x 512, RGB, plein cadre (Google applique son masque).
- `miff_wordmark_color.xml`, `miff_wordmark_reverse.xml`, `miff_wordmark_white.xml` : mot-symbole en VectorDrawable (258 x 100 dp par défaut, ratio 981:380) pour l'écran d'accueil et le splash.
- Icônes héritées mdpi à xxxhdpi : non produites (minSdk 26, l'icône adaptative suffit).

**`web/`** : `favicon.svg`, `favicon-32.png` (coins arrondis transparents), `apple-touch-icon-180.png` (plein cadre, sans alpha), `icon-192.png` et `icon-512.png` (coins arrondis transparents), `icon-maskable-512.png` (plein cadre, glyphe dans la zone sûre de 80 %).

**`preview/`** : les deux aperçus cités plus haut.

## 3. Ce qui a été fait, et ce qui est « reconstruit »

Le travail est un **redessin géométrique**, pas un autotracé brut : lignes droites ajustées par moindres carrés sur la source, formes symétrisées, courbes lissées.
- **M, i, jambes** : polygones aux arêtes droites (diagonales ajustées sur les bords mesurés, M du mot-symbole rendu symétrique).
- **Cœur** : deux cercles et deux tangentes vers une pointe légèrement arrondie, ajustés sur le cœur de la source (légère inclinaison du mot-symbole conservée). Fidélité mesurée environ 95 %.
- **Lettres « f »** : barre et jambe en rectangles exacts ; le crochet vient de la moyenne des deux « f » de la source, contour lissé puis réajusté en courbes de Bézier. Les deux « f » sont désormais **identiques** (ils différaient de quelques pixels dans la source).
- **Carré arrondi de l'icône** : rayon mesuré sur la source (21,4 % du côté).
- Aucun texte ni police dans les SVG : uniquement des chemins et des couleurs hexadécimales.

### Écarts mesurés avec les sources
Le recouvrement est calculé au pixel contre des sources **floues et agrandies** (les bords font 3 à 5 px de transition), donc les écarts sont surtout des liserés de 1 à 3 px.
- Mot-symbole : recouvrement des lettres environ 96 %, du cœur environ 96 % ; 4,5 % des pixels diffèrent.
- Icône : carré bleu environ 98 %, « M + i » blanc environ 91 %, cœur environ 92 % ; 2,8 % des pixels diffèrent (zone centrale).
- Écarts de dessin voulus (nettoyage), à valider :
  1. **Ligne de base du mot-symbole alignée** : dans la source, le « M » s'arrête environ 5 px plus haut que « i » et « f » (sur 380 px de haut). Tout est aligné sur une même ligne de base (le M gagne environ 3 px en hauteur, soit 0,8 %).
  2. **M symétrique** : la source a des diagonales qui diffèrent de 1 à 2 px entre gauche et droite.
  3. **Deux « f » identiques** et crochets lissés (le contour d'origine est ondulé).
  4. **Cœur symétrique** : la source est un peu asymétrique (lobe droit plus large), ce que le redessin ne reproduit pas.
  5. **Icône** : glyphe recentré dans le carré (décalage de moins de 1 px par rapport à la source).
  6. Arêtes vives : les coins du crochet des « f » sont légèrement plus arrondis que dans la source.

## 4. Zones de sécurité et choix par plateforme

**iOS (1024 x 1024)** : le fond bleu est plein cadre, iOS applique son masque ; le glyphe garde la proportion de l'icône du PM (largeur du glyphe = 55 % du carré, centré).
- **Variantes iOS 18** (proposition, à valider) : *sombre* = même glyphe (M et i blancs, cœur jaune-vert) sur noir `#000000` (token `text/primary`, le design system n'a pas de couleur sombre de marque) ; *teintée* = glyphe en niveaux de gris (M et i `#FFFFFF`, cœur `#C2C2C2`, luminance équivalente au jaune-vert) sur noir, que le système teinte. Variantes sans alpha.

**Android adaptatif (108 x 108 dp)** :
- Arrière-plan `#043CD1` uni, avant-plan = M + i blanc et cœur jaune-vert.
- **Zone de sécurité** : cercle de 66 dp de diamètre au centre. Le glyphe est placé de façon que son **cercle englobant minimal ait 32,5 dp de rayon** (limite 33 dp) : le glyphe mesure alors environ 46 x 49 dp (43 % de la largeur). Cela reste volontairement prudent : sur les masques courants le glyphe pourrait être agrandi jusqu'à environ 51 dp de large (zone visible minimale de 72 dp) ; à voir avec le PM.
- Calque monochrome : même géométrie, silhouette opaque (le cœur reste séparé du « i » par un petit espace, donc lisible en monochrome).
- Les aperçus `miff-icon-masks-preview.png` montrent cercle, carré arrondi, squircle, thématique et iOS.

**Web** : l'icône maskable a son glyphe dans le cercle de 80 % de la zone sûre.

**Splash / écran d'accueil** : mot-symbole (versions `color` ou `reverse` selon le fond). Sur Android 12 et plus, le splash système affiche l'icône dans un cercle : utiliser l'avant-plan de l'icône adaptative. À traiter dans les PR dev.

## 5. Incohérences avec l'icône provisoire actuelle (`android/`, `ios/`)

| Point | Visuel du PM | Icône provisoire |
|---|---|---|
| Lettres | « M » gras et « i » soudés à droite | Un seul « M » fin, à angles droits |
| Cœur | Au-dessus du « i » (à droite), dans le creux entre la diagonale et le « i » | Centré au-dessus du « M » |
| Proportions du glyphe | Large et massif | Plus petit et fin |
| Carré arrondi | Rayon 21,4 % | Fond uni, masque du système |
| Couleurs | Mêmes (`#043CD1`, `#D1D828`, blanc) | Mêmes |

## 6. À faire valider par le PM

1. **La recréation est-elle fidèle ?** (aperçu comparatif) : en particulier les 6 écarts voulus du §3 (ligne de base, M symétrique, « f » identiques, cœur symétrique).
2. **Droits** sur le dessin (voir §1).
3. **Variantes sombre et teintée iOS 18** : fond noir proposé, ou autre choix (aucune couleur sombre dans le design system).
4. **Taille du glyphe sur Android** : 46 dp de large (prudent) ou plus grand (jusqu'à environ 51 dp).
5. **Zone de protection** autour du mot-symbole : non définie, SVG livrés sans marge.
6. **Ajout des assets dans le fichier Figma « Miff - Claude »** : non fait (aucune écriture Figma). À faire si le PM le souhaite.
7. **Vérifier la lisibilité** du favicon 32 px et de l'icône 48 px (le cœur y devient un petit point).

## 7. Prochaines étapes (hors de cette PR)
PR dev Android : remplacer `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, ajouter les mots-symboles ; PR dev iOS : `AppIcon.appiconset` (1024 sans alpha, variantes) et assets du mot-symbole ; PR web : favicons. Le release-manager peut utiliser `android/play-store-icon-512.png` et `ios/AppIcon-1024.png` pour les fiches store.
