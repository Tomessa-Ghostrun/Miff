# Contrat : registre des langues et contrôles de traduction

**Statut** : contrat partagé entre `android-dev`, `ios-dev` et tous les agents qui ajoutent du texte. Toute modification passe par une PR qui touche ce fichier ou le registre.
**Source de vérité** : `docs/contracts/i18n-languages.json` (lisible par les scripts et la CI).
**Vérifié en CI** : `scripts/check-i18n-parity.mjs`, job « Parité des clés de traduction » (workflow `hygiene`, sur toute PR). Tests du script : `scripts/tests/check-i18n-parity.test.mjs` et `scripts/tests/check-i18n-languages.test.mjs`.
**Origine** : ticket `docs/tickets/traduction-es-it-pt-de.md` (lot 1, outillage et parité sur N langues). Le lot 1 ne modifie **aucune ressource de traduction** et aucun code applicatif.

## 1. Le registre

Un seul fichier décrit les langues de Miff. Aujourd'hui : `fr` (langue source), `en` (langue de repli), puis `es`, `it`, `pt` (portugais du Portugal), `de`.

| Champ | Rôle |
|---|---|
| `sourceLanguage` / `fallbackLanguage` | `fr` : référence de toutes les comparaisons. `en` : langue de repli de l'app (et référence pour le rapport de longueur) |
| `languages[].code` | Code de la langue (`es`, `pt`...). Jamais de région (`pt` et non `pt-PT`, voir le ticket §5) |
| `languages[].nativeName` | Nom de la langue **écrit dans sa langue** (Español, Deutsch...), jamais traduit : c'est celui qu'affichera Réglages > Langue (lot 3) |
| `languages[].platforms.android` | `status` (`planned` ou `required`) et `resDir` (dossier de ressources : `values-fr`, `values` pour l'anglais de repli, `values-es`...) |
| `languages[].platforms.ios` | `status` et `code` (code de langue du String Catalog et du dossier `.lproj`) |
| `languages[].plurals` | `required` (formes obligatoires : `one`, `other`), `allowed` (formes que la langue connaît), `recommended` (signalées en avertissement si absentes) |
| `languages[].style` | `spaceBeforeHighPunctuation` (`nbsp` en français, `none` ailleurs), `forbidGenderMarks`, `formalForms` (vouvoiement), `forbiddenTerms` (termes proscrits, listes du glossaire `docs/i18n/glossaire.md` §4) |
| `brandTerms`, `nbspRequiredKeyPatterns`, `shortLabelKeyPatterns`, `lengthRatio`, `lengthBudgets` | Réglages des contrôles (voir §3) |
| `exceptions` | Faux positifs documentés (voir §6) |
| `consistency.sources` | Les endroits du dépôt qui déclarent les langues (voir §4) |

Le **statut est par plateforme** : Android et iOS peuvent passer en `required` à des moments différents (ticket §10, lot 4 : « Android d'abord, iOS ensuite »).

### Pluriels (règle pratique du ticket, §4.2)

| Langue | Formes autorisées | Obligatoires | Recommandée |
|---|---|---|---|
| fr, es, it, pt | `one`, `many`, `other` | `one`, `other` | `many` (écrit identique à `other` quand le français l'écrit) |
| en, de | `one`, `other` | `one`, `other` | |

`many` n'est jamais exigée (pour es, it, pt et fr, CLDR ne l'utilise que pour les millions entiers) : son absence est un avertissement, jamais une erreur. Une forme inconnue de la langue (`few`, `two`, `zero`, ou `many` en allemand et en anglais) est une erreur. Toute forme `other` ou `many` porte le nombre comme le français ; seule la forme `one` peut s'en passer (« Un miembro »).

## 2. Statuts : `planned` et `required`

- **`required`** : la langue est livrée. Tout écart est une **erreur** et fait échouer le job CI (code 1). C'est le cas de `fr` et `en`, dont le comportement est inchangé.
- **`planned`** : la langue est en préparation. Les **mêmes contrôles** tournent, mais tout écart est un **avertissement non bloquant**, avec le décompte (par exemple « clé manquante : 1150 texte(s) »). Une langue `planned` peut n'avoir encore aucun fichier : elle apparaît alors avec `0/1150` clés.

La langue source (`fr`) doit rester `required` sur les deux plateformes : le script refuse un registre qui ne le respecte pas (code 2).

## 3. Ce que le script contrôle, pour chaque langue et chaque plateforme

Sur Android (`values-xx/strings.xml`, pluriels `<plurals>`) **et** iOS (`Localizable.xcstrings` et `InfoPlist.xcstrings` : chaînes, variations de pluriel, substitutions `%#@arg@`). Les ressources non traduisibles (`translatable="false"`, `shouldTranslate: false`) sont ignorées.

| Contrôle (identifiant dans `--json`) | Gravité naturelle | Détail |
|---|---|---|
| `missing-key`, `extra-key` | erreur | Mêmes clés que le français sur la plateforme |
| `empty`, `todo` | erreur | Aucun texte vide ; aucun marqueur `TODO`, `TBD`, `FIXME`, `XXX`, `???` |
| `state` | erreur | iOS : état de traduction autre que `translated` |
| `control-char` | erreur | Aucun caractère de contrôle, de largeur nulle ou de direction (U+200B à U+200F, U+202A à U+202E, U+2066 à U+2069...) |
| `placeholders` | erreur | Mêmes valeurs insérées que le français, même position et même type : `%1$s` = `%1$@`, `%2$d` = `%2$lld`, `%2$02d` = `%2$02lld` ; ordre libre dans la phrase |
| `plural-forms` | erreur | Formes inconnues, en double, ou `one` / `other` absentes |
| `plural-recommended` | avertissement | Forme recommandée absente (`many`) |
| `structure` | erreur | Texte simple, pluriel ou texte à substitutions différent du français |
| `android-escape` | erreur | Apostrophe non échappée (`\'`), `&` qui n'est pas une entité, guillemet droit non échappé, valeur commençant par `@` ou `?`, `%` isolé dans un texte à valeurs insérées (écrire `%%`). Un `%` seul est toléré dans un texte sans valeur insérée (« 30 % ») |
| `nbsp-lost` | erreur pour les durées (`nbspRequiredKeyPatterns`, par défaut `^countdown_duration_`), avertissement ailleurs | Le français a une espace insécable entre une valeur et son unité ou un mot, la traduction n'en a aucune. L'espace avant `? ! : ; »` ou après `«` n'est pas comptée. U+00A0 et U+202F valent toutes deux |
| `high-punctuation` | avertissement | Espace insécable avant `? ! : ;` dans une langue qui n'en met pas (toutes sauf `fr`) |
| `gender-mark` | erreur | `(e)`, `(a)`, `o/a`, `@` inclusif, `*in`, `:in`, `/in`, `...Innen`. Actif si `style.forbidGenderMarks` (es, it, pt, de) ; les adresses e-mail ne comptent pas |
| `formal-address` | erreur | Vouvoiement (`style.formalForms`) : es « usted(es) », « vosotros » ; it « Lei » ; pt « você(s) » ; de « Sie / Ihr / Ihre / Ihnen... » (heuristique, voir §6) |
| `forbidden-term` | erreur | Terme de `style.forbiddenTerms` (ton alarmiste ou culpabilisant) ; **vide dans le registre pour l'instant : à alimenter depuis `docs/i18n/glossaire.md` §4 au lot 4** |
| `identical-to-source` | avertissement | Texte identique au français (marque `brandTerms`, valeurs insérées et chiffres exemptés) |
| `length` | avertissement | Plus de 1,5 fois la longueur du français (`lengthRatio.maxVsSource`), le français étant compté pour au moins 10 caractères |
| `short-label-budget` | avertissement | Clé d'« étiquette courte » (`shortLabelKeyPatterns`) au-dessus de son budget : `lengthBudgets[clé]` ou, par défaut, longueur du français + 40 % |

**Longueur relative** : en plus des avertissements par texte (référence : le français, comme dans le ticket), le rapport donne par langue et plateforme le **rapport de longueur à l'anglais** (`lengthRatioVsFallback`, somme des caractères des textes simples), affiché sur la ligne de chaque langue (« longueur ≈ ×1.12 vs en »).

**Parité Android / iOS** (existante, inchangée) : mêmes clés communes des deux côtés sur le français, snake_case, préfixes connus, clés en double. Elle reste bloquante.

## 4. Cohérence des sources (critère 6 du ticket)

Le script vérifie que le registre et les endroits qui déclarent les langues disent la même chose :

| Source (`consistency.sources[].id`) | Type | Règle |
|---|---|---|
| `android-enum` : `AppLanguage.kt` | déclaration | Exactement les langues `required` d'Android |
| `android-locale-filters` : `localeFilters.addAll(...)` de `app/build.gradle.kts` | déclaration | Exactement les langues `required` d'Android |
| `ios-enum` : `enum AppLanguageChoice` de `ios/Miff/Models/Main/AppLanguage.swift` (les `case x = "code"` ; `system` n'a pas de valeur brute) | déclaration | Exactement les langues `required` d'iOS |
| `ios-bundle-localizations` : `CFBundleLocalizations` de `ios/project.yml` | déclaration | Exactement les langues `required` d'iOS |
| `android-res-dirs` : dossiers `values-xx` contenant un `strings.xml` | ressources | Au moins les `required`, rien d'inconnu du registre |
| `ios-catalog-languages` : langues du catalogue iOS | ressources | Au moins les `required`, rien d'inconnu du registre |

- Langue `required` absente d'une source : **erreur**.
- Langue absente du registre trouvée dans une source : **erreur**.
- Langue `planned` **déclarée** (énumération, `localeFilters`, `CFBundleLocalizations`) : **avertissement** (la déclarer, c'est la proposer aux personnes ; la passer d'abord en `required`).
- Langue `planned` dont les **ressources** existent : normal (la traduction est en cours), aucun constat.
- Fichier de source déclaré dans le registre mais introuvable : **erreur** (le fichier a été déplacé : mettre à jour le registre dans la même PR).

**Régions** : un tag avec région n'est jamais réduit à sa langue. `pt-BR`, `pt-rBR` et `pt_BR` donnent le code `pt-BR`, distinct de `pt` (portugais du Portugal, défaut du PM : un seul code `pt`). Un `pt-BR` trouvé dans une source est donc « inconnu du registre » (erreur) tant que le registre ne le liste pas ; un dossier `values-pt-rBR` aussi.

**Non couvert** : les libellés des langues dans Réglages > Langue (`ios_settings_language_<nom>` côté iOS, noms non traduisibles côté Android) n'ont pas de nom de clé déductible du code de langue ; leur présence pour chaque langue `required` reste vérifiée par les tests de chaque plateforme (`LanguageScreen` / `LanguageView`, lot 3).

## 5. Utiliser le script

```
node scripts/check-i18n-parity.mjs                  # contrôle complet, rapport lisible
node scripts/check-i18n-parity.mjs --verbose        # tous les constats, un par ligne
node scripts/check-i18n-parity.mjs --lang de        # une seule langue
node scripts/check-i18n-parity.mjs --json           # rapport pour machine (langues, décomptes, constats, cohérence)
node scripts/check-i18n-parity.mjs --summary r.md   # tableau Markdown (la CI l'ajoute au résumé du job)
node scripts/check-i18n-parity.mjs --registry autre.json --root <dossier>
node --test "scripts/tests/check-i18n-*.test.mjs"   # tests du script
```

Codes de sortie : `0` = aucune erreur (les avertissements ne comptent pas), `1` = au moins une erreur, `2` = fichier ou registre illisible, langue inconnue, argument incorrect. **Sans registre** dans le dossier cible, seul le contrôle historique (français, Android et iOS) tourne.

## 6. Faux positifs : exceptions documentées

Les listes de vouvoiement, de marques de genre et de termes proscrits sont des **heuristiques** (par exemple « Sie » au début d'une phrase allemande peut vouloir dire « elle »). On ne désactive jamais un contrôle : on ajoute l'exception précise au registre, avec la PR qui l'explique.

```json
"exceptions": [
  { "lang": "de", "key": "auth_example_key", "check": "formal-address" },
  { "lang": "de", "keyPattern": "^legal_", "check": "formal-address", "platform": "ios" }
]
```

`match` (facultatif) limite l'exception au texte exact trouvé par le contrôle (par exemple `"Sie"`) : le reste de la clé reste contrôlé. `check` prend un identifiant du §3 ; `platform` (`android` ou `ios`) est facultatif ; `key` est exacte, `keyPattern` est une expression régulière.

**Faux positifs connus à traiter au lot 4** (relecture des constats de `planned` avant le passage en `required`) :

| Langue | Cas | Traitement |
|---|---|---|
| de | « Sie » au début d'une phrase = « elle / elles » ; « Ihr » = « vous (pluriel) » ou « leur » | Exception par clé avec `match` ; si la phrase est vraiment une adresse de politesse, c'est une erreur de traduction |
| it | « Lei » = « elle » (pronom de la 3e personne, hors adresse de politesse) | Exception par clé avec `match` |
| es, it | « y/o », « e/o » = « et/ou » | Déjà exemptés (une lettre seule avant la barre ne vaut pas marque de genre) |
| es, pt | Mot qui finit par « @ » ou adresse e-mail | Les adresses e-mail sont exemptées ; sinon exception par clé |

## 7. Passer une langue de `planned` à `required`

À faire dans **la PR qui livre la traduction complète de la plateforme** (lot 4 du ticket : Android d'abord, iOS ensuite) :

1. Vérifier en local : `node scripts/check-i18n-parity.mjs --lang <code> --verbose` ne signale **aucune** clé manquante ni aucune erreur pour la plateforme, et que les avertissements restants (longueur, identique au français, `many` recommandée) sont traités ou acceptés par écrit dans la PR.
2. Dans `docs/contracts/i18n-languages.json`, changer `platforms.<plateforme>.status` de `planned` à `required`.
3. Dans la même PR, déclarer la langue là où la cohérence l'exige (§4) : énumération des langues et `localeFilters` (Android), `CFBundleLocalizations` (iOS). Sinon le script échoue : c'est voulu.
4. Alimenter `forbiddenTerms` de la langue avec la liste du glossaire (`docs/i18n/glossaire.md` §4) **avant** le passage en `required`.
5. Lancer les tests (`node --test "scripts/tests/check-i18n-*.test.mjs"`) : ils lisent le vrai dépôt.

Revenir de `required` à `planned` (une langue qui échoue à la recette) est possible par la même voie, en retirant ses déclarations (§4) dans la même PR : la sortie publique peut se faire langue par langue.

## 8. Règle permanente

**Toute nouvelle clé de texte arrive dans toutes les langues `required`, dans la PR qui l'introduit.** Concrètement :

- Aujourd'hui (`fr` et `en` seules `required`) : français et anglais, comme avant ; la CI n'impose rien pour les langues `planned`, mais affiche le décompte des clés manquantes.
- **Dès que les quatre nouvelles langues sont `required`** : toute PR qui ajoute ou renomme une clé l'ajoute dans **les six langues** sur la ou les plateformes concernées (Android `values-xx/strings.xml`, iOS `Localizable.xcstrings`), en suivant le glossaire (`docs/i18n/glossaire.md` et `docs/i18n/guide-de-style.md`). Le job « Parité des clés de traduction » échoue sinon. Aucune clé n'est « traduite plus tard ».
- Les textes communs aux deux plateformes sont traduits **une seule fois** puis copiés des deux côtés (même valeur, mêmes valeurs insérées) ; les clés `android_*` et `ios_*` sont traduites directement.

Le contrat des clés (`docs/contracts/i18n-keys.md`) renvoie ici pour la marche à suivre.

**Exception de l'écran de diagnostic (2026-11-19).** Les clés `diag_*`, `ios_diag_*` et `android_diag_*` (écran interne des versions de test) restent en français et en anglais : le registre porte, pour chacune des langues es, it, pt et de, l'exception `{"lang": "es", "keyPattern": "^(?:ios_|android_)?diag_", "check": "missing-key"}` (aucune clé manquante signalée, sur les deux plateformes). Le français et l'anglais ne sont pas concernés. Côté Android, le lint `MissingTranslation` est une erreur : la PR de réinjection ignore ces clés dans `android/app/lint.xml` (voir `docs/i18n/procedure-lot4.md` §3 bis).
