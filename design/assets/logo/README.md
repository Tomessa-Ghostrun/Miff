# Logo Miff — extraction depuis Figma (2026-10-24)

Dossier produit par l'agent `design-system`, avec l'autorisation du PM (2026-10-24) d'extraire le logo depuis Figma. Il n'existait pas de fichier SVG du logo.

**Les icônes actuellement présentes dans `android/` et `ios/` n'ont PAS été modifiées.** Une PR dev les remplacera après validation du PM.

## 1. Le vrai logo

Le logo du PM est le **mot-symbole « Miff »** : lettres M, i, f, f en bleu épais, avec un **cœur jaune-vert à la place du point du « i »** (cœur légèrement incliné). Deux couleurs, qui correspondent exactement aux tokens du design system :

| Élément | Valeur mesurée sur le raster | Token |
|---|---|---|
| Lettres | environ `#043CD1` | `action/primary` = `#043CD1` |
| Cœur | environ `#D1D828` | `brand/primary` = `#D1D828` |

Il n'existe **pas de « marque seule » séparée** (pas de symbole M, pas de pictogramme) dans le travail du PM : le logo n'existe que sous forme de mot-symbole. Le « M blanc avec cœur sur fond bleu » est une déclinaison **proposée par une analyse de logo** (voir §5), pas dessinée par le PM.

## 2. Origine exacte dans Figma

| Élément | Fichier | Nœud | Détail |
|---|---|---|---|
| **Source principale** | « Miff - App » (original, lecture seule) `Byi9NgQxkBPbmbhU403HSf` | `24:216` « 1d132640-…-removebg-preview 1 » | Calque raster (remplissage image) de 106×60 sur l'écran « Welcoming screen » `23:162` (1er écran d'onboarding), **raster PNG à fond transparent de 663×376 px** (détourage automatique « removebg ») |
| Même image, reprise | « Miff - Claude » `RbX84qKbxppvAy5iypLYP2` | `20:10` « AppMockup » (nom trompeur : c'est le logo), dans « 01 — Bienvenue (valeur) » `21:2` | Copie de l'asset ci-dessus (le nœud 24:216 expose aussi une version réduite 332×188 px), non recréée en vectoriel |
| Planche de réflexion (non retenue comme source) | « Miff - App » | `78:13` (page « Design system » `69:580`) | Image 1663×946 : une planche d'avis sur le logo (points forts, pistes, déclinaisons), qui contient aussi l'icône « M + cœur » provisoire |

- Version Figma : l'API MCP n'expose pas d'identifiant de version ; référence = état du fichier au **2026-10-24**.
- Aucune écriture n'a été faite dans Figma (ni dans l'original, ni dans « Miff - Claude ») : lecture et export uniquement (3 appels `get_metadata`, 2 `download_assets`).
- **Licence** : œuvre du PM (Tomessa Mfouka) ; tous droits réservés au PM. Aucune ressource tierce utilisée. Le fichier raster provient d'un outil de détourage : **le PM doit confirmer qu'il détient bien les droits sur le dessin d'origine** (par exemple s'il a été généré par un outil d'IA, vérifier les conditions d'usage commercial de cet outil avant publication sur les stores).

## 3. Contenu du dossier

| Fichier | Nature | Remarque |
|---|---|---|
| `source/miff-logo-figma-raster-663x376.png` | **Raster original** du PM (transparent) | Seule source fidèle. À conserver. |
| `svg/miff-logo-color.svg` | **SVG reconstruit par autotracé** (non dessiné à la main) | Lettres `#043CD1` + cœur `#D1D828`. viewBox `0 0 554 224`, ajustée au plus près du logo (sans marge). |
| `svg/miff-logo-reverse.svg` | idem | Lettres blanches `#FFFFFF` + cœur `#D1D828`, pour fonds sombres/bleus. |
| `svg/miff-logo-white.svg` | idem | Monochrome blanc (cœur compris). |
| `svg/miff-logo-mono-blue.svg` | idem | Monochrome `#043CD1` (cœur compris). |
| `svg/miff-coeur-color.svg` | idem | Cœur seul (74×57). **Extrait à titre indicatif, pas une déclinaison validée.** |
| `png/miff-logo-{color,reverse,white}-2216w.png` | Rendu des SVG ci-dessus, 2216 px de large, fond transparent | Plus grand que la source (663 px), mais calculé à partir du tracé : ne contient pas plus de détail que le raster. |
| `app-icon/ios/miff-icon-{A,B}-…-1024.png` | **Candidats** d'icône iOS 1024×1024, **sans canal alpha** (PNG RGB 3 canaux, vérifié) | A : fond bleu `#043CD1`, lettres blanches, cœur jaune-vert. B : fond blanc `#FFFFFF`, logo couleur. |
| `app-icon/android/miff-adaptive-{A,B}-foreground.svg` / `-432.png` | **Candidats** d'avant-plan adaptatif (108×108 dp ; PNG 432×432 = xxxhdpi), fond transparent | Fonds à définir séparément : A `#043CD1`, B `#FFFFFF`. |
| `app-icon/android/miff-adaptive-{A,B}-monochrome.svg` / `-432.png` | Calque monochrome (icônes thématiques Android 13+) | Silhouette noire opaque ; la teinte est appliquée par le système. |
| `preview/miff-icon-candidates-preview.png` | Aperçu des candidats sous différents masques (cercle, carré arrondi, iOS, thématique, 48 px) | Le cercle rouge en pointillé = zone de sécurité Android (66 dp). |

Les SVG ne contiennent **ni texte, ni police, ni style externe** : uniquement des chemins (`fill-rule="evenodd"`) et des couleurs en hexadécimal. Pas de transparence dégradée, pas de filtres.

### Ce qui est « reconstruit » : à lire absolument
- **Les SVG sont un autotracé du raster (potrace après agrandissement ×4 et lissage), pas un export vectoriel de Figma** : Figma ne contient aucun vecteur du logo. Fidélité mesurée : **99,4 % de recouvrement** avec la silhouette du raster. Les contours sont légèrement irréguliers par endroits (surtout les diagonales du « M », les crochets des « f » et le cœur), car le raster d'origine est lui-même imparfait (détourage automatique) et peu résolu. Un SVG redessiné proprement par un designer (ou le fichier vectoriel d'origine du logo, s'il existe) remplacera avantageusement ces tracés avant publication.
- Le raster source ne fait que 663×376 px (logo : 554×224 px) : il convient aux écrans, pas à de grands formats d'impression.

## 4. Construction des candidats d'icône et zones de sécurité

Couleurs prises dans le design system : `#043CD1` (`action/primary`), `#D1D828` (`brand/primary`), `#FFFFFF` (`neutral/white`).

**iOS (1024×1024 px)** : PNG sans transparence ni alpha. iOS applique lui-même le masque en coin arrondi (ne pas arrondir soi-même). Pas de zone de sécurité officielle ; règle retenue : logo à **68 % de la largeur** (≈ 696 px de large), centré, marges ≥ 16 % de chaque côté. Les variantes sombre et teintée d'iOS 18 ne sont pas produites (optionnel, à décider).

**Android adaptatif (108×108 dp)** :
- Toute la partie visible est incluse dans le cercle de **66 dp de diamètre** au centre (zone de sécurité) ; les 18 dp de chaque bord sont rognés ou animés par le système.
- Le logo mesure **60 dp de large** (≈ 24 dp de haut), centré. Calcul : le rectangle englobant du logo tient dans le cercle de 66 dp jusqu'à 61 dp de large ; 60 dp laisse une petite marge. Le cercle visible minimal (72 dp) est donc largement respecté.
- Calque monochrome : même géométrie, silhouette opaque (le cœur reste séparé du « i » par un espace, donc lisible en monochrome).
- Fond : couleur unie (`#043CD1` pour A, `#FFFFFF` pour B), à déclarer dans `colors.xml` (le fond actuel `ic_launcher_background` est déjà `#043CD1`).
- Les SVG d'avant-plan peuvent être importés dans Android Studio (Vector Asset) ; vérifier que `fill-rule="evenodd"` est converti en `fillType="evenOdd"`.

**Écran de démarrage (splash)** : ce dossier ne livre pas de visuel dédié. Le plus simple et le plus cohérent est de reprendre le logo (version `reverse` ou `color` selon le fond) ; sur Android 12+, le splash système utilise l'icône dans un cercle (le logo doit tenir dans le tiers central) : à traiter avec la PR dev, après choix du fond.

Remarque : le mot-symbole est très large (rapport 2,5:1), donc **petit dans une icône carrée** (voir l'aperçu 48 px : lisible, mais le cœur devient un point). C'est le principal arbitrage, voir §6.

## 5. Incohérences entre le logo Figma et l'icône provisoire actuelle

Icône provisoire (agent, `ic_launcher_foreground.xml` et `AppIcon-1024.png`) : « M » blanc générique + petit cœur jaune-vert centré au-dessus, sur fond bleu `#043CD1`.

| Point | Logo du PM | Icône provisoire |
|---|---|---|
| Contenu | Mot « Miff » en entier | Un seul « M » |
| Lettre M | Pleins épais, diagonales larges, très gras | Dessin géométrique fin, à angles droits, **non issu du logo** |
| Position du cœur | À droite, sur le « i » (point du « i ») | Centré au-dessus du « M » : le sens du cœur-point-du-i est perdu |
| Forme du cœur | Arrondi, légèrement incliné, plus grand | Symétrique, droit, plus petit |
| Couleurs | `#043CD1` + `#D1D828` | Mêmes valeurs, mais lettre blanche sur fond bleu (le logo du PM est bleu sur fond clair) |
| Forme générale | Large (2,5:1) | Carré compact |

La planche de la page « Design system » (`78:13`) propose elle-même une « icône d'app » M + cœur et un « badge » rond avec « Miff » : ce sont des **suggestions d'une analyse de logo, pas des décisions du PM**. Le M de cette planche est lui aussi un M générique.

## 6. À faire valider par le PM

1. **Le raster est bien le logo définitif ?** Et en avez-vous une version vectorielle d'origine (Illustrator, Canva, etc.) ou haute résolution ? Sinon, faire redessiner le tracé proprement.
2. **Droits** : provenance du dessin (voir §2, licence).
3. **Icône : mot « Miff » entier (candidats A/B) ou « M + cœur » ?** L'icône « M seul » n'est pas dans votre logo ; elle demanderait de dessiner un symbole à partir du vrai M et du cœur. Je n'ai pas fait ce choix à votre place.
4. **Fond** : bleu `#043CD1` avec lettres blanches (A, plus visible sur l'écran d'accueil, et cohérent avec l'icône provisoire) ou blanc avec lettres bleues (B, fidèle à la version « principale » du logo). Le blanc se confond avec les fonds clairs de certains lanceurs.
5. **Cœur en blanc ou jaune-vert** sur fond bleu : le jaune-vert garde l'identité de marque (contraste correct sur le bleu `#043CD1`, mais le cœur est petit à 48 px).
6. **Marges** : taille du logo dans l'icône (68 % iOS, 60 dp Android) : plus grand = plus lisible mais plus proche du bord ou de la zone rognée par certains lanceurs.
7. **Zone de protection** autour du logo (pas définie dans les travaux du PM) : tracé fourni sans marge, à ajouter à l'usage.
8. **Accessibilité** : alternative texte « Miff » à utiliser partout où le logo est affiché seul.

## 7. Mise à jour du design system
`design/design-system.md` mentionne « l'icône d'application (M blanc et cœur sur fond bleu) est dans la page Design system du fichier original » : cette phrase est à corriger après validation du PM (le vrai logo est le mot-symbole décrit ici). Je n'ai pas modifié ce fichier dans cette PR (périmètre limité à `design/assets/logo/**`).
