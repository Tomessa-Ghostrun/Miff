# Recette Android : barre d'onglets « verre » (TABBAR-GLASS)

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise.
**Durée** : 10 minutes. **Ce qu'on vérifie** : la barre du bas est un verre dépoli (on devine ce qui passe dessous, en flou), reste lisible, ne cache jamais le contenu, et redevient pleine (opaque) quand le téléphone le demande.
**Avant** : installer l'APK de la PR « Tab bar verre Android » (artefact `miff-debug-apk` de la CI, jamais un APK construit à la main). Avoir une Miff avec quelques cartes et au moins un lieu (pour que l'onglet Profil défile).
**À savoir** : dans l'APK de test (debug), le bouton central est visible mais **grisé et inactif** : c'est voulu, il s'allumera avec le ticket « Update ». Il est **gris** (statut inconnu). Les couleurs vert / orange / rouge du bouton se vérifieront à ce moment-là (elles sont déjà testées sur ordinateur). Le **viseur photo** n'existe pas encore : sa barre sombre se vérifiera avec le ticket « Update ».

## 1. Le dessin (fidélité aux maquettes `design/exports/update/claude/08-tab-bar-android.png`)

| # | Vérifier | OK |
|---|---|---|
| 1 | La barre **flotte** au-dessus du bas de l'écran, avec une marge à gauche et à droite et des coins bien arrondis, et une ombre légère | [ ] |
| 2 | Trois entrées : maison avec cœur, bouton rond au centre, silhouette. Le bouton central est **exactement au milieu** de l'écran | [ ] |
| 3 | L'onglet en cours a une **pastille bleu pâle** derrière une icône **bleue** ; l'autre icône est **gris foncé** (plus foncé qu'avant) | [ ] |
| 4 | Le bouton central est une **plaque blanche** avec un **anneau gris** et un **rond gris plein au milieu, sans aucun dessin dedans** (état neutre) | [ ] |
| 5 | Toucher l'onglet Profil puis l'onglet Feed : la pastille suit, la barre ne change ni de taille ni de place | [ ] |

## 2. Le verre (Feed et Profil)

| # | Vérifier | OK |
|---|---|---|
| 6 | **Feed** : faire défiler une carte (de préférence avec une photo ou une zone colorée) **sous** la barre : on la devine **floue** à travers la barre, les icônes restent nettes | [ ] |
| 7 | **Profil** : même chose en faisant défiler la liste « Lieux de la Miff » sous la barre | [ ] |
| 8 | Sur le Feed puis sur le Profil, aller jusqu'en **bas** : le dernier élément (carte, bouton « Ajouter un lieu ») peut remonter **entièrement au-dessus** de la barre, rien n'est caché derrière | [ ] |
| 9 | **BLOQUANT pour « prêt à merger »** : **Fluidité** : faire défiler le Feed et le Profil vite pendant 10 secondes : pas de saccade visible. Mesure chiffrée : `adb shell dumpsys gfxinfo com.tomessa.miff reset`, défiler 10 s, puis `adb shell dumpsys gfxinfo com.tomessa.miff` (images lentes), **avec le flou puis sans** (`GlassPolicy.BLUR_ALLOWED = false`). Si ça saccade : ne pas fusionner tel quel, couper le flou (l'interrupteur existe) ou passer au plan B | [ ] |
| 10 | **Lisibilité** : les deux icônes (maison, silhouette) se lisent **sans effort** même quand une carte sombre passe dessous | [ ] |

## 3. Les replis (la barre devient pleine, sans flou)

Dans les réglages du téléphone, **pendant que l'app est ouverte** si possible (la barre doit changer toute seule en revenant) :

| # | Réglage | Attendu | OK |
|---|---|---|---|
| 11 | **Économiseur de batterie** activé | Barre **blanche pleine** (plus rien ne se devine à travers), même forme, mêmes icônes | [ ] |
| 12 | Économiseur désactivé | Le verre revient | [ ] |
| 13 | **Supprimer les animations** (Options pour développeurs : échelle des animations sur « désactivée », ou Accessibilité > Supprimer les animations) | Barre **blanche pleine** | [ ] |
| 14 | Remettre les animations | Le verre revient | [ ] |
| 15 | Sur Android 14 ou plus seulement (pas votre OPPO sous Android 13) : contraste renforcé | Barre pleine | [ ] non applicable sur Android 13 |

## 4. Grande police et langues

| # | Vérifier | OK |
|---|---|---|
| 16 | **Très grande police** (Réglages > Affichage > Taille de police, au maximum) : la barre garde **la même taille**, les trois entrées restent bien touchables | [ ] |
| 17 | TalkBack (si vous l'utilisez) : les trois entrées sont annoncées comme avant (« Fil de ta Miff », « Donner des nouvelles, bientôt disponible », « Profil ») et « sélectionné » pour l'onglet en cours | [ ] |
| 18 | Le clavier (par exemple dans Réglages > Mes informations) : la barre n'apparaît pas dans ces écrans ; en revenant au Feed elle est de nouveau là | [ ] |

## Écarts connus, à ne pas signaler comme des défauts
- **Icônes** : les planches 07 et 08 montrent des icônes **au trait** (maison avec cœur, silhouette) ; les icônes actuelles de l'app (`ic_tab_feed`, `ic_person`, **pleines**) sont **conservées en attendant l'export vectoriel recolorable du PM** (fiche `design/screens/update.md`, « Assets manquants »). Seules leurs couleurs suivent le nouveau jeu.
- **Fin du Feed de la PR #81** : le Feed de la PR #81 n'est pas dans cette branche. À la fusion des deux, la **dernière carte du Feed doit pouvoir remonter entièrement au-dessus de la barre** (même contrôle que le point 8). Correction attendue côté Feed : `contentPadding = PaddingValues(bottom = Spacing.m + LocalTabBarInset.current, ...)` dans sa liste, et le Feed hébergé par `GlassTabHost` (comme `MainTabs` le fait ici) ; le test `TabBarContentMarginTest` contient déjà le contrôle (`assertLastRowAboveBar`) à rejouer sur le Feed.

## Ce que vous n'avez pas à vérifier ici
Le **viseur** (barre sombre à icônes blanches, sans flou) et les **couleurs du bouton central** (vert, orange, rouge) : ils arrivent avec le ticket « Update ». Les contrastes des couleurs sont déjà contrôlés automatiquement (test `TabBarGlassContrastTest`).

## Mesures optionnelles (pour documenter, pas pour valider)
Dans la PR, sur l'OPPO : images perdues pendant 10 s de défilement du Feed, avec et sans flou (la commande est dans la description de la PR). Si le PM n'a pas le temps : à faire plus tard, rien n'est bloquant.

**Résultat** : ____ points sur 18 validés. Remarques : ______________________
