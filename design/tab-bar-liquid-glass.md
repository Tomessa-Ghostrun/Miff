# Tab bar « liquid glass » (iOS et Android)

**Rédigé le** 2026-11-06 par l'agent `design-system`. **Demande du PM** : la tab bar doit être conçue en « liquid glass » (effet translucide avec jeux de couleurs) sur iOS **et** Android. **Aucun code n'est écrit ici** : ce document prépare un futur mini-ticket de développement « Tab bar liquid glass » (§11).
**État au 2026-11-07 (vérifié dans le code)** : la barre est **livrée sur Android** (`main`, ticket TABBAR-GLASS, PR #91 ; Haze 2.0.1) et **écrite sur iOS dans la PR brouillon #102** (branche `ios/onb-7-coquille`, non fusionnée). Les sections 1 à 10 restent la spécification ; **le §14 compare cette spécification à ce qui est réellement dans les deux apps** (valeurs citées avec leur fichier) et liste les écarts. Le §11 (« ce qu'il faudrait changer ») est **historique**.
**Maquettes** : `design/exports/update/claude/07-tab-bar-ios.png` et `08-tab-bar-android.png` (3 états du bouton central x fond clair / photo sombre / photo claire, plus repli et transparence réduite) ; l'écran Update : `design/screens/update.md`. Les maquettes de la barre sont des **approximations HTML** : le vrai rendu d'iOS 26 est celui du système.
**Convention** : **[Vérifié]** = lu dans la documentation officielle le 2026-11-05 (liens en §13) ; **[Calculé]** = calcul WCAG 2.1 refait par script (`wcag`), reproductible ; **[Proposé]** = ma proposition ; **[À VÉRIFIER]** = non confirmé, à tester sur appareil.

## 1. Résumé

| Sujet | Décision proposée |
|---|---|
| Forme (identique partout) | barre flottante à **3 entrées**, 64 pt de haut, **arrondi 20**, marges latérales 24 pt, 22 pt du bas : celle des maquettes du PM |
| iOS 26 et plus | **Liquid Glass natif** : `glassEffect(_:in:)` avec `Glass.regular` dans un rectangle arrondi 20 **[Vérifié]** |
| iOS 16 à 25 | **repli** : `Material` (`.regularMaterial`) + voile de teinte pour tenir les contrastes **[Vérifié pour les noms]** |
| Android 12 et plus (API 31) | **flou d'arrière-plan** + voile de teinte ; bibliothèque la plus sobre : **Haze, module de flou seul** (§4) |
| Android 8 à 11 (API 26 à 30), appareils modestes, viseur caméra | **sans flou** : fond semi-opaque plus dense |
| Accessibilité | « Réduire la transparence », contraste renforcé, économiseur de batterie, animations supprimées : **repli opaque** (§9) |
| Deux variantes de teinte | **claire** (Feed, Profil) et **sur photo** (viseur, aperçu), automatiques (§5) ; **validées par le PM le 2026-11-06** |
| Bouton central | **jamais du verre** : plaque blanche opaque, anneau bleu, **pastille de couleur seule** (aucun pictogramme, décision du PM) ; **grise** (comme les autres boutons) quand le statut est inconnu ou le partage arrêté (§7) |
| Bulle d'incitation | opaque, **au-dessus** de la barre, sur tous les onglets sauf Update (`update.md` §4) : jamais de verre sur du verre |
| Coût | **0 €** : API système sur iOS, bibliothèque Apache 2.0 sur Android |

Le fichier `design/design-system.md` pointe ici pour la Tabbar (la ligne « Tabbar » mise à jour).

## 2. Anatomie (commune aux deux plateformes)

| Élément | Valeur |
|---|---|
| Barre | 342 pt de large sur 390 (marges 24 pt), **64 pt** de haut, `radius` **20**, posée à 22 pt du bas (+ inset de sécurité) ; **ne change jamais de taille ni de position** (pas de réduction au défilement) |
| Entrées | trois zones de **64 x 48 pt** (minimum 48), espacées régulièrement ; ordre début / centre / fin (inversé en droite-à-gauche, icônes non miroir) |
| Onglet actif | pastille 64 x 48, `radius/md` 10, icône active ; nom parlé « sélectionné » |
| Icônes | **maison classique** (symbole système `house`, décision du PM du 2026-11-08 : plus la maison avec un cœur) et silhouette (`person`) ; **au trait quand l'onglet est inactif, pleine (`house.fill`, `person.fill`) quand il est actif** (en plus de la pastille : jamais la couleur seule) ; nom parlé de l'onglet inchangé (« Feed » / « Profil ») |
| Bouton central | plaque blanche 44 pt + anneau `action/primary` 3 pt + pastille de statut 24 pt, sans pictogramme (§7) |
| Contenu sous la barre | le contenu **passe sous** la barre (c'est ce qui rend le verre visible) ; chaque onglet (Feed, Profil) garde une marge basse égale à la hauteur de la barre + marge (+ celle de la bulle d'incitation quand elle existe : visible sur tous les onglets sauf Update, `update.md` §4) |
| Clavier | la barre disparaît quand le clavier est ouvert (comportement actuel) |

## 3. iOS

### 3.1 iOS 26 : rendu natif
Noms vérifiés dans la documentation Apple (JSON de la documentation, lu le 2026-11-05) :
- `glassEffect(_:in:)` : « Applique l'effet Liquid Glass à une vue » ; déclaration `func glassEffect(_ glass: Glass = .regular, in shape: some Shape = DefaultGlassEffectShape()) -> some View` ; **iOS 26.0 et plus**. Par défaut `Glass.regular` et une capsule : on passe un **rectangle arrondi de 20 pt** pour garder la forme des maquettes.
- `Glass` : `regular`, `clear`, `identity` (propriétés de type) ; `tint(_:)` et `interactive(_:)` (méthodes). **[Proposé]** : `regular` (la variante `clear` est réservée aux composants posés sur des médias riches, avec un voile sombre de 35 % si le fond est clair, d'après les Human Interface Guidelines ; notre barre sur photo est gérée par la variante « sur photo », §5) ; **aucun** `tint` sur la barre (réservé au sens ou à la mise en avant) ; **pas** de `interactive()` (rebond au toucher : inutile, et à éviter si « réduire les animations »).
- `GlassEffectContainer(spacing:)` : à utiliser pour **plusieurs** éléments de verre (rendu en une seule passe). La barre est **un seul** élément de verre : le bouton central est **opaque** et hors du verre, donc **pas de conteneur nécessaire** [Proposé]. Ne **jamais** mettre du verre sur du verre (recommandation Apple).
- Les composants système adoptent le verre en recompilant avec Xcode 26 ; la barre **personnalisée** doit l'appliquer elle-même.
- **Ne pas** retenir `tabBarMinimizeBehavior(.onScrollDown)` (iOS 26) : le bouton central est l'action principale, la barre ne doit pas se réduire.
- `UIDesignRequiresCompatibility` (clé Info.plist qui désactive le nouveau design) : **ignorée par le SDK d'iOS 27 et au-delà** d'après la documentation Apple et la presse spécialisée ; ne pas compter dessus. À noter : compilée avec le SDK 26, l'app verra aussi ses composants **système** (alertes, feuilles, barres natives) adopter le verre sous iOS 26 ; à contrôler à la recette.

### 3.2 iOS 16 à 25 : repli
- `Material` (iOS 15 et plus) **[Vérifié]** : `.ultraThin`, `.thin`, `.regular`, `.thick`, `.ultraThick`, `.bar`. **[Proposé]** `.regularMaterial` **plus** un voile de teinte (§5) : les matériaux système ne sont pas calculables, le voile garantit les contrastes. Valeurs identiques à Android (même dessin sur toutes les versions : un seul design à tester).
- Garde de version : `if #available(iOS 26, *)` pour choisir `glassEffect`, sinon matériau + voile.
- L'app n'a **pas de mode sombre** (design system §6) : les matériaux suivent le réglage du téléphone ; la variante « sur photo » impose un schéma sombre local sur la barre (`.environment(\.colorScheme, .dark)` sur la seule barre **[À VÉRIFIER]**) et la variante claire un schéma clair.

### 3.3 Barre personnalisée ou `TabView` système ? (décision technique, pour `ios-dev`)
**Recommandation** : barre **personnalisée** (`safeAreaInset(edge: .bottom)`). Raisons : le bouton central n'est **pas** un onglet de contenu (il ouvre le viseur) et porte une couleur de statut ; la forme flottante à 3 entrées est celle des maquettes sur **toutes** les versions ; un `TabView` système n'offrirait le verre flottant que sous iOS 26 et une barre plate standard avant. Coûts : reproduire à la main les traits d'accessibilité d'un onglet (sélectionné, nom parlé) ; **[À VÉRIFIER]** si `TabView` avec un onglet central dont l'icône garde ses couleurs est faisable sans hack (non essayé : pas de Mac).

### 3.4 Tests impossibles en local
Aucune compilation iOS locale (poste Windows) : le rendu **natif** ne se vérifie que par TestFlight sur l'**iPhone 11 Pro (iOS 26.5.2)**. Le protocole de test est en §8.

## 4. Android

Il **n'existe pas de Liquid Glass natif** sur Android. Le rendu « verre » est un **flou de l'arrière-plan** + une **teinte** + un **liseré clair**.

### 4.1 Briques de la plateforme
- `RenderEffect` (flou) : **API 31 (Android 12) et plus** **[Vérifié]** ; sous API 31, aucun flou matériel.
- `Modifier.blur(...)` de Compose **floute le contenu de l'élément lui-même**, pas ce qui est derrière lui : il ne suffit pas pour un effet de verre.
- Le flou de l'arrière-plan s'obtient en **enregistrant le contenu défilant dans une couche** (`rememberGraphicsLayer()` et `drawWithContent { record { … } }`, Compose 1.7 et plus **[Vérifié]**) puis en la redessinant, floutée, derrière la barre. Notre projet est en Compose BOM `2026.09.00` : l'API est disponible.

### 4.2 Bibliothèques gratuites et open source examinées
| Option | Licence | Maintenance (au 2026-11-05) | Ce que ça apporte | Limites et risques |
|---|---|---|---|---|
| **A. Haze (`chrisbanes/haze`), module de flou seul** | Apache 2.0 | Dernière version 2.1.0 (2026-11-02), 2.0.1 (2026-09-29), 2.0.0 (2026-09-22) ; environ 2,7 k étoiles | `hazeSource` (marque le contenu à flouter), `hazeBlur`, styles et teinte ; modes de performance (Balanced par défaut) ; Android, iOS, bureau | Version 2 sortie il y a 6 semaines, avec **migrations** entre 1.x et 2.x et entre 2.0 et 2.1 (« recompiler ») : API qui bouge vite. Sous API 31 le flou passe par RenderScript (déprécié) : **on ne l'utilise pas**, on bascule en fond sans flou. **Un `SurfaceView` ne peut pas être capturé** : l'aperçu CameraX doit être en mode compatible (`PreviewView.ImplementationMode.COMPATIBLE`) pour être flouté. Construit avec Compose 1.12.1 : **[À VÉRIFIER]** compatibilité avec la BOM `2026.09.00` (une montée de version de la BOM peut être nécessaire). Module « Glass » (réfraction, fusion de formes) **à ne pas prendre** : stable seulement depuis 2.1.0, jamais plus coûteux en GPU en listes denses (changelog) |
| **B. « Maison » (aucune dépendance)** | n/a | n/a | ≈ 50 lignes : couche graphique + `RenderEffect` (API 31) + voile | Cas limites à écrire et à tester soi-même (décalage de défilement, rotation, découpe arrondie, cycle de vie des couches) ; même contrainte `SurfaceView` ; aucune mise à jour externe |
| **C. Backdrop (`Kyant0/AndroidLiquidGlass`)** | Apache 2.0 | version 2.0.1 (2026-08-26), 2.0.0 (2026-05-28, devenue multiplateforme) ; environ 4,5 k étoiles | Flou **et réfraction/lentille** (« vrai » liquid glass) ; README : « aucun composant de haut niveau » | La réfraction passe par des shaders **API 33 et plus** d'après un dépôt tiers (non confirmé par la documentation du projet) : trois niveaux de repli (33+, 31-32, <31) ; plus lourd en GPU ; à construire entièrement soi-même |

Autres pistes vues et **écartées** : portages multiplateformes encore en alpha (0.0.1-alpha), bibliothèque basée sur les vues et non Compose.

### 4.3 Recommandation (la plus sobre ; validée par le PM le 2026-11-06)
**Option A, Haze limité à son module de flou, version 2.0.1 épinglée** (publiée depuis plus de deux semaines, règle du ticket pour CameraX ; la 2.1.0 a quatre jours), **sans** le module Glass. Pourquoi : c'est la plus petite surface qui évite d'écrire et maintenir les cas limites du flou d'arrière-plan ; l'option C ajoute des shaders et trois niveaux de repli pour un effet (réfraction) que la maquette du PM ne demande pas ; l'option B reste le **plan B** si Haze devait être écartée (changement de licence, abandon, incompatibilité de BOM). Aucun coût.
**Limites à écrire dans le ticket** : (1) API qui évolue vite : épingler et ne pas monter de version sans relecture du journal des changements ; (2) dépendance tierce = **revue de sécurité** (pas de réseau, pas de permission ; vérification des sommes de contrôle Gradle) et **mention de licence** Apache 2.0 à intégrer à l'écran des licences (avec `release-manager`) ; (3) pas de flou sous API 31 : décidé par nous, pas par la bibliothèque ; (4) pas de flou sur le viseur (§10) ; (5) vérifier la compatibilité de version de Compose avant d'ajouter.
**Signal pour changer d'option** : régression de performance sur l'OPPO ou arrêt de la maintenance de Haze → option B.

### 4.4 Appareil de test
OPPO Find X5 Lite, Android 13 (API 33) : flou matériel disponible ; la surcouche ColorOS peut tuer les apps en arrière-plan et modifier l'orientation de la caméra frontale (déjà notés au ticket Update). Niveaux de repli à tester en changeant les réglages (économiseur de batterie) et sur un émulateur API 26 à 30.

## 5. Jeux de couleurs et tokens (tous **[Proposés]**)

Deux variantes, choisies **automatiquement par l'écran** (pas un réglage de l'utilisateur) : **claire** sur le Feed et le Profil ; **sur photo** sur le viseur et l'aperçu. L'app n'ayant pas de mode sombre, la variante claire ne change pas avec le réglage du téléphone ; la variante « sur photo » **est** le jeu sombre, préparé pour un futur mode sombre.

| Token | Claire | Sur photo |
|---|---|---|
| `tabbar/glass-tint` (voile, avec flou) | blanc **80 %** | noir **60 %** |
| `tabbar/glass-tint-noblur` (sans flou) | blanc **92 %** | noir **72 %** |
| `tabbar/surface-opaque` (transparence réduite) | `#ffffff` | `#222222` |
| `tabbar/glass-border` (liseré 1 pt) | blanc 55 % | blanc 18 % |
| `tabbar/icon-active` | `action/primary` `#043cd1` | `#ffffff` |
| `tabbar/icon-inactive` | **`#595959`** (au lieu de `#7d7c7c`) | blanc **78 %** |
| `tabbar/pill-active` | `action/primary-tint-20` (bleu 20 %) | blanc **24 %** |
| `tabbar/blur-radius` | 20 dp / pt | 20 dp / pt |
| Ombre | `shadow/default` (Android), ombre douce 0 / 6 / 20 noir 18 % (iOS) | idem |

**Pourquoi un voile aussi dense (80 % et 60 %)** : sur une photo noire sous une barre claire, ou une photo blanche sous une barre sombre, le verre ne doit pas faire tomber les icônes sous 3:1 ; les valeurs sont les plus translucides qui le garantissent (§6). La barre actuelle de la maquette est déjà presque opaque (≈ `#f1f1f0`) : l'écart visuel avec le Feed est faible.
**Bouton de statut** : voir §7.
**iOS 26** : `Glass.regular` sans teinte ; le système adapte seul le verre au fond. Les icônes de la barre utilisent les mêmes tokens ; si la recette (§8) montre un contraste < 3:1, **ajouter** `tint(blanc 35 %)` sur la variante claire plutôt que durcir les icônes.

## 6. Contrastes WCAG **[Calculés]**

Critères : icônes et éléments graphiques **≥ 3:1** (WCAG 1.4.11) ; texte **≥ 4,5:1**. **Pire fond** = le fond derrière le verre est **noir pur** ou **blanc pur** (toute photo est entre les deux : le contraste varie de façon monotone) ; on **ignore le flou et la saturation** (cas pessimiste).

**Barre claire** (voile blanc, icône sombre ou bleue) :
| Voile | Fond | Icône active (bleu) sur le voile | Icône active sur la pastille | Icône inactive `#595959` | Ancien gris `#7d7c7c` |
|---|---|---|---|---|---|
| 80 % (flou) | noir pur | 5,08 | 3,74 | 4,36 | **2,59 (échec)** |
| 80 % (flou) | gris 50 % | 6,52 | 4,67 | 5,59 | 3,32 |
| 80 % (flou) | blanc pur | 8,16 | 5,73 | 7,00 | 4,16 |
| 92 % (sans flou) | noir pur | 6,82 | 4,87 | 5,85 | 3,48 |
| 100 % (opaque) | tous | 8,16 | 5,73 | 7,00 | 4,16 |

**Barre sur photo** (voile noir, icônes blanches, pastille blanche 24 %, inactif blanc 78 %) :
| Voile | Fond | Icône active sur le voile | Icône active sur la pastille | Icône inactive |
|---|---|---|---|---|
| 60 % (flou) | blanc pur | 5,74 | 3,42 | 4,24 |
| 60 % (flou) | gris 50 % | 12,60 | 5,91 | 8,35 |
| 60 % (flou) | noir pur | 21,00 | 10,83 | 12,41 |
| 72 % (sans flou) | blanc pur | 9,23 | 4,71 | 6,39 |
| 72 % (sans flou) | noir pur | 21,00 | 10,83 | 12,41 |
| `#222222` (opaque) | n/a | 15,91 | n/a | 10,15 |

**Comparaison avec la maquette du PM** : barre grise `#b1bbbd` sur le viseur : icône inactive `#7d7c7c` **2,12:1**, icône active bleue sur pastille `#8b9cbb` **2,94:1** (sous 3:1). **Tous les pires cas ci-dessus sont ≥ 3,4:1.**
**iOS 26 natif** : **non calculable** (verre adaptatif) ; protocole de recette en §8.

## 6 bis. Android : barre nettement plus translucide (décisions du PM, 2026-11-11) **[Android seulement]**

Le PM trouve la barre du bas trop opaque (voile blanc 80 %), puis encore trop opaque à 55 % : **la transparence prime**. **Android** passe à un verre très translucide ; **iOS n'est pas modifié** (valeurs du §5 inchangées : à aligner plus tard si le PM le veut). Ce qui assure la lisibilité n'est plus la densité du voile mais **les icônes** :

| Élément (variante claire, Android) | Avant | Après |
|---|---|---|
| `tabbar/glass-tint` (voile, avec flou) | blanc 80 % | **blanc 32 %** |
| `tabbar/blur-radius` | 20 dp | **28 dp** (le flou fait le travail que le voile faisait) |
| `tabbar/icon-inactive` | `#595959` | **`#1A1A1A`** (quasi noir) |
| `tabbar/pill-active` | bleu 20 % **translucide** | **bleu 20 % sur blanc, opaque : `#CDD8F6`** (l'onglet actif ne dépend plus du fond) |
| `tabbar/glass-tint-noblur` (Android < 12, repli) | blanc 92 % | inchangé (sans flou, le contenu net derrière gênerait la lecture) |
| Variante « sur photo » | noir 60 % | inchangée (viseur sans flou) |

Contrastes recalculés (WCAG, `TabBarGlassContrastTest`) : icône **active** sur sa pastille **5,73:1 quel que soit le fond** ; icône **inactive** **17,4:1** sur blanc, **7,37:1** sur gris moyen, **4,24:1** sur gris foncé (25 % de blanc), et **2,21:1 sur noir pur**.

**RISQUE CONNU, ACCEPTÉ PAR LE PM (la transparence prime)** : sur un fond **noir pur** (une photo presque noire sous la barre), l'icône inactive est **sous 3:1** (2,21:1, WCAG 1.4.11). Seule cette paire est concernée : tout le reste est >= 3:1 et l'état actif reste lisible partout. (Un halo blanc léger derrière l'icône a été essayé puis abandonné : sur la capture il faisait un « fantôme ».) Signal de reprise : si la recette montre une icône du Profil difficile à repérer sur une photo sombre, remonter le voile vers 40 %.

Le bouton central (plaque blanche opaque) ne change pas ; son gris neutre reste `#595959` (décision du 2026-11-06). Les replis opaques existants (économiseur de batterie, animations supprimées, contraste renforcé sous Android 14+) et sans flou (Android < 12, appareil à peu de mémoire, viseur) sont inchangés ; il n'existe pas, sous Android, de réglage système « réduire la transparence » lisible par une app : ces trois signaux le remplacent.

## 7. Le bouton central se détache du verre

Le verre est translucide, la couleur de statut doit rester lisible : le bouton central est donc **opaque et séparé du verre** :
1. **Plaque blanche opaque** 44 pt (ombre légère) : isole le bouton de n'importe quel fond (ne dépend jamais de la transparence).
2. **Anneau `action/primary` 3 pt** : 8,16:1 sur la plaque ; 5,08:1 contre le verre clair du pire fond ; c'est le contour perceptible du bouton.
3. **Pastille de statut** 24 pt, **couleur pleine** (jamais délavée, contrairement à la maquette quand l'onglet est inactif), **3 pt d'air blanc** autour (entre la pastille et l'anneau). **Aucun pictogramme** : « la couleur est le seul signal » [Décidé, PM 2026-11-06]. Le sens passe, pour le lecteur d'écran, par le nom parlé.
4. **Contrastes de la pastille seule [Calculés]** : contre la plaque blanche, vert 1,55:1, orange 1,74:1, rouge 3,13:1, gris neutre 7,00:1 ; contre l'anneau bleu, 5,27 / 4,69 / 2,60:1 ; vert contre orange 1,12:1. **Limites assumées par la décision** : le vert et l'orange sont sous 3:1 contre la plaque (WCAG 1.4.11 non atteint pour la pastille seule) et se confondent pour une personne daltonienne ; le contour du bouton reste lisible grâce à l'anneau bleu (8,16:1). Atténuation facultative sans pictogramme : un liseré fin `#222222` autour de la pastille (bords à 5,1:1 au moins).
5. **État neutre (statut inconnu ou partage arrêté)** [Décidé, PM 2026-11-06] : le bouton est **gris, comme les autres boutons de la barre** : anneau et pastille `#595959` (`tabbar/icon-inactive` claire) sur la plaque blanche ; contraste 7,0:1 contre la plaque. Sur la barre « sur photo », la plaque blanche garde ce gris foncé (les icônes inactives, blanches à 78 %, restent sur le verre sombre).
6. **Pastille d'onglet actif** : quand le bouton central est actif (viseur ouvert), la pastille de 64 x 48 l'entoure (bleue 20 % sur barre claire ; blanche 24 % sur barre sombre) sans changer la plaque.

## 8. Lisibilité sur fonds variés et protocole de recette

Planches `07` et `08` : fond clair (Feed avec une carte à photo sombre passant sous la barre : cas le plus dur pour la barre claire), photo sombre (T-shirt), photo claire (façade).
**Recette sur appareil** (OPPO et iPhone 11 Pro), pour chacun des **3 états** de couleur : (1) Feed défilé avec une photo sombre sous la barre ; (2) Feed fond clair ; (3) viseur face à une fenêtre éclairée ; (4) viseur dans une pièce sombre ; (5) aperçu d'une photo claire. Pour chaque cas, **capture d'écran** de la barre et mesure du contraste de l'icône inactive et de l'icône active (pipette) ; **seuil 3:1** ; consigner les 15 résultats dans la PR. Sous iOS 26, comparer aux options « Clair » / « Teinté » de l'apparence Liquid Glass du téléphone si elles existent **[À VÉRIFIER]**.

## 9. Accessibilité

| Réglage | iOS | Android | Rendu |
|---|---|---|---|
| **Réduire la transparence** | `EnvironmentValues.accessibilityReduceTransparency` (iOS 13 et plus) **[Vérifié]** : « les fonds ne doivent pas être semi-transparents ; ils doivent être opaques » | **Aucune API publique** pour ce réglage (je n'en ai trouvé aucune dans `AccessibilityManager`) | iOS : `tabbar/surface-opaque`. Android : voir ci-dessous |
| **Augmenter le contraste** | `EnvironmentValues.colorSchemeContrast == .increased` (iOS 13 et plus) **[Vérifié]** | `UiModeManager.getContrast()` (API 34 et plus, valeur de −1 à 1) **[Vérifié]** ; texte à contraste élevé : `AccessibilityManager.isHighTextContrastEnabled` (API 36 seulement) **[Vérifié]** ; **rien sous API 34** : l'OPPO sous Android 13 n'a ni l'un ni l'autre | opaque si le contraste est renforcé (iOS, API 34+) ; sinon rien |
| **Réduire les animations / mouvement** | `accessibilityReduceMotion` | animations supprimées : `ValueAnimator.areAnimatorsEnabled()` (API 26) **[à contrôler]** | pas d'animation de la pastille ni de la bulle ; **aucun** effet interactif du verre |
| **Économiseur de batterie** | mode économie d'énergie | `PowerManager.isPowerSaveMode` | **repli opaque** |
| Appareil à peu de mémoire | n/a | `ActivityManager.isLowRamDevice` | repli sans flou |

**Résumé Android** : repli **opaque** si économiseur de batterie, animations supprimées, contraste renforcé (API 34+) ou appareil à peu de mémoire ; **sans flou** sous API 31 et sur le viseur ; flou sinon. **Pas de réglage dans l'app** (charge cognitive) : l'app suit le système.
Autres points : l'état « sélectionné » est annoncé (pas par la couleur seule) ; noms parlés dans `update.md` §8 ; texte maximal sans effet (la barre n'a pas de libellé visible) ; contraste de l'ombre non requis.

## 10. Performance et batterie

- **Surface floutée** : une zone de 342 x 64 pt, rayon de flou 20 : coût faible tant que le contenu dessous est **statique ou défile** (le flou est recalculé quand le contenu change). Haze propose des modes de performance ; garder le mode par défaut (Balanced), mesurer sur une version proche de la production avant d'y toucher (recommandation du projet).
- **Viseur caméra : pas de flou sur Android** [Proposé]. Un flou sur une vidéo à 30 images/s recalcule le flou à chaque image (chaleur, batterie), et exige le mode compatible de `PreviewView` (moins fluide, plus lourd) car un `SurfaceView` ne peut pas être capturé. Sur le viseur, la barre utilise donc le fond **sans flou** (noir 72 %, contrastes ≥ 4,7:1). Sur l'aperçu (image fixe), le flou est possible mais on garde la même barre que sur le viseur par cohérence.
- **iOS** : Apple recommande d'éviter les empilements de verre et de limiter le nombre d'effets à l'écran ; la barre en est un seul. Sous iOS 26 sur le viseur : l'échantillonnage du verre sur une couche de caméra **[À VÉRIFIER sur l'iPhone 11 Pro]** ; repli : variante sans verre (`tabbar/glass-tint-noblur`).
- **Appareils modestes** : sous API 31 et sur appareils à peu de mémoire, pas de flou (rien à calculer, rendu identique à une barre pleine).
- **Mesure à faire dans la PR** : images perdues (Android Studio, `dumpsys gfxinfo`) pendant 10 s de défilement du Feed avec et sans flou sur l'OPPO ; température et batterie sur 5 min de viseur ; rien n'est supposé ici.

## 11. Ce qu'il fallait changer dans le code (mini-ticket « Tab bar liquid glass », Android puis iOS) : historique, voir le §14 pour l'état réel

### Android (`android/app/src/main/java/com/tomessa/miff/`)
| Fichier | Changement |
|---|---|
| `ui/main/MainTabBar.kt` | Remplacer `.background(colorBgSurface)` et l'ombre par un modificateur de verre (flou Haze + voile + liseré) choisi selon un **niveau de capacité** (flou, sans flou, opaque) ; ajouter un paramètre de variante (claire ou sur photo) ; nouveau composable du **bouton central** (plaque, anneau, pastille de couleur, sans pictogramme) à la place du `Box` jaune-vert (`UpdateRingSize` 28 dp, `DISABLED_ALPHA`) ; couleurs d'icônes depuis les tokens `tabbar/*` ; hauteur de ligne 52 dp → entrées de 48 dp dans une barre de 64 dp ; marge latérale 16 dp → 24 dp (24 pt validés pour iOS ; **24 dp proposés** pour Android, même dessin) ; noms parlés par niveau de statut (clés `tabbar_update_*_a11y` à faire évoluer) |
| `ui/main/MainShell.kt` (`MainTabs`) | Le `Scaffold` réserve aujourd'hui la place de la barre (`padding(bottom = padding.calculateBottomPadding())`) : **le contenu ne passe donc pas sous la barre**. Passer à une disposition en `Box` : contenu plein écran (marqué comme **source** du flou), barre superposée en bas ; fournir la **hauteur de la barre** aux écrans (valeur de composition) pour leur marge basse ; garder la disparition au clavier ; ajouter la **destination Update** comme contenu d'onglet (barre visible, `update.md` E9) |
| `ui/feed/FeedPlaceholderScreen.kt`, `ui/profile/ProfileTabScreen.kt` (et le futur Feed) | Marge basse de contenu = hauteur de la barre + marge (+ bulle d'incitation, mesurée, identique sur les deux onglets) |
| Bulle d'incitation (futur ticket UPDATE-2) | Hébergée par `MainTabs` **avec** la barre (visible sur tous les onglets sauf Update, masquée avec le clavier), pas par chaque écran |
| `domain/navigation/MainTab.kt` | `isEnabled(UPDATE)` → vrai ; `showUpdateButton` toujours vrai ; ajouter l'état de **couleur** du bouton (niveau de statut) fourni par `StatusLevel` |
| Détection des capacités | Nouveau petit composant : API ≥ 31, économiseur de batterie, animations, `UiModeManager.getContrast()` (API 34+), appareil à peu de mémoire, écran = viseur → niveau (flou / sans flou / opaque) ; testable en unitaire |
| `ui/theme/Color.kt`, `ColorContrastTest` | Tokens `tabbar/*` et `status/*-bubble` ; **ajouter les paires** des tableaux du §6 aux tests de contraste |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Dépendance Haze (module de flou), version **épinglée 2.0.1**, vérification des sommes de contrôle ; licence Apache 2.0 à l'écran des licences |

### iOS (`ios/Miff/`, la barre n'existe pas encore)
| Fichier | Changement |
|---|---|
| Nouveau `Views/Main/MainTabBar.swift` (+ composants du bouton central) | Barre personnalisée (§3.3), `safeAreaInset(edge: .bottom)` ; verre : `if #available(iOS 26, *)` → `glassEffect(.regular, in: .rect(cornerRadius: 20))`, sinon `.regularMaterial` + voile ; lecture de `accessibilityReduceTransparency` et `colorSchemeContrast` pour le repli opaque ; variante claire ou sur photo |
| `DesignSystem/` | Tokens `tabbar/*` et `status/*-bubble` |
| Écrans onglets | Marge basse = hauteur de la barre (comme Android) |
| `project.yml`, CI | Rien à ajouter (iOS 16 minimum conservé). **Aucun** `UIDesignRequiresCompatibility` |

**Critères d'acceptation proposés** : (1) les trois états de couleur sur fond clair, photo sombre et photo claire sont conformes aux planches 07 et 08 ; (2) contrastes du §6 vérifiés par test unitaire (Android et iOS) ; (3) repli opaque avec « Réduire la transparence » (iOS) et l'économiseur de batterie (Android) ; (4) pas de flou sur le viseur Android ; (5) 15 mesures de recette du §8 consignées ; (6) le contenu défile sous la barre ; (7) aucune permission ni réseau ajoutés ; (8) la barre garde sa taille quand la bulle d'incitation s'affiche.
**Taille indicative** : S à M par plateforme. **Dépend de** : rien d'ouvert (variantes validées le 2026-11-06) ; indépendant du reste du ticket Update, mais à **livrer avant ou avec** UPDATE-1 (le bouton central actif et le viseur sont posés sur cette barre).

## 12. Questions pour le PM
**Aucune question ouverte.** Validés par le PM le 2026-11-06 : les deux variantes de verre, les icônes inactives plus foncées, Haze (module de flou seul, version épinglée, plan B version maison), la marge de **24 pt** (iOS) et l'arrondi de **20**, la pastille de couleur seule. **À confirmer à l'export Figma** (pas une question de produit) : cotes converties depuis des captures de 320 px. *(La marge de la barre sur Android, « 24 dp proposés, 16 dp dans le code », est résolue : le code livré a 24 dp, voir §14.)*

## 13. Sources
Documentation Apple (lue le 2026-11-05) : [glassEffect(_:in:)](https://developer.apple.com/documentation/swiftui/view/glasseffect(_:in:)), [Glass](https://developer.apple.com/documentation/swiftui/glass), [GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer), [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views), [Adopting Liquid Glass](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass), [Materials (HIG)](https://developer.apple.com/design/human-interface-guidelines/materials), [Material](https://developer.apple.com/documentation/swiftui/material), [accessibilityReduceTransparency](https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityreducetransparency), [colorSchemeContrast](https://developer.apple.com/documentation/swiftui/environmentvalues/colorschemecontrast), [tabBarMinimizeBehavior(_:)](https://developer.apple.com/documentation/swiftui/view/tabbarminimizebehavior(_:)). Les pages web d'Apple ne sont pas lisibles directement par l'outil : le contenu vient de la documentation au format JSON de ces mêmes pages ; **iOS 27 n'a pas été vérifié point par point**.
Android : [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) (API 31), [Modifiers graphiques de Compose](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) (`rememberGraphicsLayer`, Compose 1.7 et plus), [UiModeManager](https://developer.android.com/reference/android/app/UiModeManager) (`getContrast`, API 34), [AccessibilityManager](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager) (contraste de texte, API 36).
Bibliothèques : [Haze](https://github.com/chrisbanes/haze) et son [journal des changements](https://raw.githubusercontent.com/chrisbanes/haze/main/CHANGELOG.md) ; [Backdrop / AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) et ses [versions](https://github.com/Kyant0/AndroidLiquidGlass/releases). Les coordonnées Maven et les noms exacts d'artefacts sont **à relire** sur la page de la version avant l'ajout.

## 14. Cohérence avec les barres livrées (vérifiée le 2026-11-07)

Fichiers lus : Android `android/app/src/main/java/com/tomessa/miff/ui/main/GlassTabBar.kt`, `MainTabBar.kt`, `MainShell.kt`, `ui/theme/GlassTokens.kt`, `GlassCapability.kt`, `android/gradle/libs.versions.toml` ; iOS (PR brouillon #102, branche `ios/onb-7-coquille`) `ios/Miff/Views/Main/MainTabBar.swift`, `ios/Miff/DesignSystem/TabBarTokens.swift`, `ios/Miff/Models/Main/MainTab.swift`. **Aucune valeur n'est inventée : « conforme » veut dire identique à la spécification ci-dessus.**

### Ce qui est conforme (Android et iOS)
| Sujet | Spécification | Android (`main`) | iOS (PR #102) |
|---|---|---|---|
| Barre | 64 pt de haut, arrondi 20, marges latérales 24, entrées 64 x 48 | `GlassBarHeight` 64 dp, `BarShape` 20 dp, `BarSideMargin` 24 dp (`Spacing.l`), `ItemWidth`/`ItemHeight` 64 x 48 dp | `TabBarLayout` : 64, 20, 24, 64 x 48 |
| Posée à 22 pt du bas | oui | `BarBottomMargin` 22 dp + barre de navigation (`miffBottomInsets`) | `bottomMargin` 22 |
| Bouton central | plaque blanche 44, anneau 3, pastille 24, sans pictogramme | `PlateSize` 44, `RingSize` 36 (anneau de 3), `DotSize` 24 | `plateSize` 44, `ringSize` 36, `ringWidth` 3, `dotSize` 24 |
| Neutre = gris `#595959` | oui | `centralButtonColors(NEUTRAL)` = `NeutralStatusGrey` (`#595959`, constante propre depuis le 2026-11-11 : les icônes inactives sont `#404040`) | `tabBarIconInactiveLight` `0x595959` |
| Clair : voile blanc 80 %, liseré blanc 55 %, opaque `#ffffff`, icône inactive `#595959`, pastille `action/primary-tint-20` | iOS oui ; **Android : voile 32 %, icône inactive `#1A1A1A`, pastille opaque `#CDD8F6` (§6 bis, 2026-11-11)** | `GlassPaletteLight` | `TabBarVariant.light` |
| Sur photo : voile noir 60 %, liseré blanc 18 %, opaque `#222222`, icônes blanches / blanc 78 %, pastille blanc 24 % | oui | `GlassPaletteOnPhoto` | `TabBarVariant.onPhoto` (prête, aucun écran ne l'utilise encore) |
| Rayon de flou 20 | oui | `GlassBlurRadius` 20 dp (Haze **2.0.1** épinglée, modules `haze` et `haze-blur`, pas le module Glass) | sans objet (matériau système) |
| Plaque du bouton central toujours opaque | oui | `GlassCentralPlate` `#ffffff` | `colorBgSurface` |
| Repli opaque | transparence réduite / contraste renforcé / économiseur / animations supprimées | `GlassPolicy.level` : économiseur de batterie, animations supprimées, contraste renforcé (API 34+) -> `OPAQUE` ; API < 31, appareil à peu de mémoire, viseur -> `NO_BLUR` (voile `tintNoBlur` 92 % / 72 %) ; interrupteur de repli `BLUR_ALLOWED` | `GlassPolicy.level` : « Réduire la transparence » ou « Augmenter le contraste » -> `opaque` ; sinon verre natif si disponible, sinon matériau ; interrupteur de repli `nativeGlassAllowed` |
| Verre | iOS 26 : `glassEffect(.regular)` ; iOS 16 à 25 : `.regularMaterial` + voile + liseré ; Android 12+ : flou + voile + liseré | Haze `hazeBlur` + voile + liseré 1 dp ; contenu enregistré pour le flou **seulement** au niveau `BLUR` | `#if compiler(>=6.2)` + `#available(iOS 26.0, *)` : `glassEffect(.regular, in: rectangle arrondi 20)` ; sinon `.regularMaterial` + voile + liseré 1 pt, schéma clair ou sombre local à la barre |
| Barre personnalisée, pas de `TabView` | oui | `MainTabBar` + `GlassTabHost` (le contenu passe **sous** la barre, `LocalTabBarInset` donne la marge basse) | `safeAreaInset(edge: .bottom)` dans `MainShellView` |
| Nom parlé, état « sélectionné » annoncé | oui | `Role.Tab`, `selected`, nom par niveau (`tabbar_update_green/orange/red_a11y`) | trait `isSelected` ; nom `tabbar_feed_a11y` / `tabbar_profile_a11y` |
| Ombre | Android `shadow/default` ; iOS 0 / 6 / 20 noir 18 % | `ShadowDefault` (0 / 4 / 4, noir 25 %) | `tabBarShadowColor` noir 18 %, rayon 10, décalage vertical 6 |

### Écarts connus (spécification contre implémentation)
| # | Écart | Où | Statut |
|---|---|---|---|
| 1 | **Taille des icônes** : 26 dans la spécification (§2) et sur Android (`IconSize` 26 dp) ; **24 pt sur iOS** (`TabBarLayout.iconSize`) | iOS | À aligner (24 ou 26) par `ios-dev` ; non tranché ici |
| 2 | **Icônes de la maison et du profil** : le PM a choisi le 2026-11-08 une **maison classique** (plus de cœur) ; **iOS** utilise les symboles du système `house` / `house.fill` et `person` / `person.fill` (actif / inactif). **Android : à aligner par l'agent Android** (`ic_tab_feed` est encore la maison avec un cœur) | iOS, Android | Asset à exporter par le PM (voir `update.md` §12) |
| 3 | **Position verticale** : « 22 pt du bas (+ inset de sécurité) » dans la spécification ; Android = 22 dp **au-dessus** de la barre de navigation ; **iOS = 22 pt depuis le bas de l'écran** (la barre empiète sur la zone de l'indicateur d'accueil sur un iPhone à zone sûre, `bottomPadding(safeAreaBottom:)`) | iOS | Écart voulu et documenté dans le code iOS ; la différence visuelle entre les plateformes est de l'ordre de la hauteur de la zone sûre |
| 4 | **Nom parlé du bouton central sur iOS** : `tabbar_update_soon_a11y` (« bientôt ») tant que l'Update iOS n'existe pas ; Android a les quatre noms (vert, orange, rouge, neutre) | iOS | Normal jusqu'à UPDATE-3 |
| 5 | **Variante « sur photo »** : sur Android elle est utilisée par le viseur (`glassVariantFor`), jamais en flou ; sur iOS elle existe mais le viseur n'existe pas | iOS | Normal jusqu'à UPDATE-3 |
| 6 | **Ombre iOS** : la spécification dit « 0 / 6 / 20 » ; le code dit rayon 10 (SwiftUI) | iOS | Équivalence à juger sur l'iPhone 11 Pro (recette TestFlight) |
| 7 | **Mesures de recette du §8** (15 captures et contrastes, images perdues sur l'OPPO) : consignées dans les PR d'origine, pas reprises ici | PR #91, PR #102 | Voir les PR |
| 8 | **iOS 26 natif non vérifiable en local** (pas de Mac) : rendu à contrôler sur l'iPhone 11 Pro (iOS 26.5.2) via TestFlight | iOS | Recette PM |
