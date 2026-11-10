# Contrat — clés de traduction (Android et iOS)

**Statut** : contrat partagé entre `android-dev` et `ios-dev`. Toute modification passe par une PR qui touche ce fichier.
**Vérifié en CI** : `scripts/check-i18n-parity.mjs` (job `hygiene`, sur toute PR).

## Principe

Les deux apps utilisent **exactement les mêmes clés** pour les mêmes textes. Côté Android, le français vit dans `values-fr/` et l'anglais de repli dans `values/` : le contrôle de parité lit le **français** (`values-fr`). Le français est la langue source ; l'anglais est la langue de repli (décision PM du 2026-10-16). Aucune chaîne visible en dur dans le code.

| Plateforme | Fichier source (français) | Autres langues |
|---|---|---|
| Android | `android/app/src/main/res/values-fr/strings.xml` | `values/strings.xml` = **anglais de repli** (ressources par défaut d'Android), `values-<langue>/` pour les autres |
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
| `onboarding_` | Carrousel de bienvenue (et, plus tard, les autres écrans du ticket Onboarding) | Android et iOS |
| `profile_` | Étapes de création du profil (photo, prénom, nom, recadrage, pays et numéro de téléphone) ; onglet Profil (`profile_tab_`) ; les champs de Réglages > Mes informations en réutilisent les clés (ticket Onboarding, ONB-2 et ONB-3a) | Android et iOS |
| `tabbar_` | Barre d'onglets : noms parlés (lecteur d'écran) des trois onglets (ONB-3a) | Android et iOS |
| `feed_` | Onglet Feed : en-tête, bulle, cartes, Localiser, états (tickets FEED). Les heures s'écrivent par **trois familles** de formules (`feed_moment_at_*` « à 16 h 02 », `feed_moment_since_*` « depuis 16 h 02 », `feed_moment_from_*` « de 16 h 02 ») × trois cas (aujourd'hui, hier, un autre jour), et par les motifs `feed_time_pattern_24`, `feed_time_pattern_12`, `feed_date_pattern` (format Java / Foundation propre à la langue). Les lieux dans une phrase sont les clés `feed_place_noun_*` (« la maison »). Les valeurs et les cas de test de la logique sont le contrat entre iOS et Android (`FeedBuilderTest`, `FeedTextsTest`) | Android et iOS |
| `settings_` | Réglages : liste, Mes informations, Mon compte, Langue, Confidentialité (ONB-3a, ONB-3b) ; écran Abonnement en lecture seule (`settings_subscription_*`, ABO-1 : la clé `settings_subscription_plan_name` « Miff+ » est un nom de marque non traduit, `shouldTranslate: false` côté iOS, `translatable="false"` côté Android ; `settings_subscription_active_until` reçoit la date au format long de la langue, formatée par le système) | Android et iOS |
| `miff_` | Créer, rejoindre, gérer une Miff, membres, code secret | Android et iOS |
| `place_` | Lieux de confiance | Android et iOS |
| `permission_` | Écrans d'autorisation du système (notifications, localisation « Toujours ») et leur divulgation (LOC-2a) | Android et iOS |
| `update_` | L'Update (UPDATE-1, UPDATE-2, FEED-4) : explication et refus de la caméra, viseur, aperçu, publication et ses messages calmes, bulle d'incitation (`update_bubble_*`, textes des maquettes du PM), photo dans le Feed (`update_card_photo_*`, `update_photo_*`). Les noms parlés du bouton central sont sous `tabbar_update_*_a11y` | Android et iOS |
| `notif_` | Notifications (NOTIF-P0, `docs/tickets/notifications.md` §11 et §20.10) : textes des notifications N1 à N4 et N6 (`notif_n1_title`, `notif_n1_body`…, N6 = variante A `notif_n6a_*`), amorce de l'autorisation (`notif_prime_*`), carte de deuxième chance (`notif_second_chance_*`), états de l'autorisation (`notif_state_*`), Réglages > Notifications (`notif_settings_*`), écran d'aide « notifications coupées » (`notif_help_*`), cloche et centre de notifications (`notif_bell_*`, `notif_center_*`), texte de Confidentialité (`notif_privacy_body`). Les noms des canaux du système Android sont sous `android_notif_*`. **Anciennes clés** `permission_notifications_*` (amorce d'origine) et `settings_privacy_notifications_body` : toujours utilisées par iOS tant qu'il n'a pas repris les textes P0, **inutilisées côté Android depuis NOTIF-1A** ; à supprimer **des deux plateformes ensemble** quand iOS aura migré | Android et iOS |
| `common_` | Boutons et libellés réutilisés (annuler, valider, erreur réseau...) | Android et iOS |
| `home_` | Écran d'accueil | Android et iOS |
| `diag_` | Écran de diagnostic interne (builds debug / TestFlight, jamais en release) | Android et iOS |
| `ios_` | Texte propre à iOS (ex. textes de permission système, étapes des Réglages d'iOS : `ios_permission_location_*`, LOC-4a) | **iOS seulement** |
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
