# Contrat — clés de traduction (Android et iOS)

**Statut** : contrat partagé entre `android-dev` et `ios-dev`. Toute modification passe par une PR qui touche ce fichier.
**Vérifié en CI** : `scripts/check-i18n-parity.mjs` (job `hygiene`, sur toute PR).

## Principe

Les deux apps utilisent **exactement les mêmes clés** pour les mêmes textes. Le français est la langue source ; l'anglais est la langue de repli (décision PM du 2026-10-16). Aucune chaîne visible en dur dans le code.

| Plateforme | Fichier source (français) | Autres langues |
|---|---|---|
| Android | `android/app/src/main/res/values/strings.xml` | `values-en/strings.xml`, etc. |
| iOS | `ios/**/Localizable.xcstrings` (String Catalog, langue source `fr`) | dans le même catalogue |

## Format d'une clé

- `snake_case`, minuscules, chiffres autorisés : `^[a-z][a-z0-9]*(_[a-z0-9]+)+$`
- Structure : `<préfixe>_<écran ou contexte>_<élément>` — par exemple `auth_login_title`, `miff_join_code_hint`, `common_cancel`.
- Le nom décrit le **rôle** du texte, jamais son contenu (`common_cancel`, pas `common_annuler`), pour que la clé reste vraie dans toutes les langues.
- Une clé = une phrase complète. Pas d'assemblage de morceaux (genre, prépositions et ordre des mots changent selon la langue). Un cas différent = une clé différente.
- Texte d'accessibilité (lecteur d'écran) : suffixe `_a11y` (`miff_invite_button_a11y`).

## Préfixes

| Préfixe | Usage | Présente sur |
|---|---|---|
| `auth_` | Inscription, connexion, mot de passe | Android et iOS |
| `miff_` | Créer, rejoindre, gérer une Miff, membres, code secret | Android et iOS |
| `place_` | Lieux de confiance | Android et iOS |
| `common_` | Boutons et libellés réutilisés (annuler, valider, erreur réseau...) | Android et iOS |
| `ios_` | Texte propre à iOS (ex. textes de permission système) | **iOS seulement** |
| `android_` | Texte propre à Android | **Android seulement** |

Les clés `ios_` et `android_` sont ignorées par le contrôle de parité. Un préfixe de fonctionnalité nouveau (ex. `settings_`, `paywall_`, `feed_`, `onboarding_`) s'ajoute **dans ce tableau et dans `KNOWN_PREFIXES` du script** par la PR de la fonctionnalité ; en attendant, le script affiche seulement un avertissement.

## Règles de contenu

- Ton bienveillant, non anxiogène (voir `CLAUDE.md`) : valable pour les messages d'erreur et les notifications.
- Pluriels : mécanisme de la plateforme (`<plurals>` Android, variations de pluriel du String Catalog iOS) **avec la même clé**. Jamais de `(s)` ni de `if count == 1` dans le code.
- Variables dans une phrase : paramètres positionnels (`%1$s` côté Android, `%1$@` côté iOS) pour permettre de changer l'ordre selon la langue. Le script de parité compare les **clés**, pas les paramètres : la revue de code vérifie que les deux plateformes passent les mêmes valeurs dans le même ordre.
- Dates, heures relatives (« il y a 12 min »), nombres : formateurs du système, jamais de texte construit à la main.
- Une ressource qui ne se traduit pas (nom de marque) : `translatable="false"` côté Android, `"shouldTranslate": false` côté iOS (ignorée par le contrôle).
- Mise en page : début/fin, jamais gauche/droite (préparation de l'écriture droite-à-gauche).

## Ajouter ou modifier un texte

1. Ajouter la clé **dans les deux fichiers sources français** dans la même PR (ou la même série de PR, si Android précède iOS : le contrôle de parité n'est alors actif que lorsque les deux fichiers existent ; dès qu'ils existent tous les deux, une clé ajoutée d'un seul côté fait échouer la CI).
2. Ajouter la traduction anglaise (ou laisser la clé marquée « à traduire » dans l'outil de la plateforme, puis la compléter avant la recette).
3. Ne jamais renommer une clé sans la renommer partout.
