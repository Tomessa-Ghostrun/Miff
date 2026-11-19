# Procédure du lot 4 : traduction en masse (es, it, pt, de) par fichier d'échange

**Statut** : lot 4A (outillage) du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L). Ce document dit **comment** mener le lot 4B (la traduction elle-même), avec les commandes du script `scripts/i18n-exchange.mjs`. Il complète `docs/i18n/protocole-relecture-croisee.md` (qui dit **quoi** vérifier), `docs/i18n/glossaire.md`, `docs/i18n/guide-de-style.md` et `docs/contracts/i18n-languages.md` (registre).
**Rédigé le** : 2026-11-19, sur `origin/main` ; mis à jour le 2026-11-19 après le pilote es (lots 001 et 002) et les décisions du PM (écran de diagnostic non traduit, alerte dégelée, découpage en agents).

> **Règles du projet qui s'appliquent** : aucune dépense, **aucun service de traduction externe** (D10 du ticket) : les agents du projet traduisent et relisent. Les textes sont publiés sous la responsabilité du PM **sans jamais prétendre à une relecture native ni juridique** (R1 du ticket, décision du 2026-10-23). Jamais de push sur `main` : une PR par plateforme. Le script ne modifie **jamais** les textes français ni anglais.

---

## 1. Principe et fichiers

Les textes **communs** à Android et iOS (même clé des deux côtés) sont traduits **une seule fois** dans un fichier d'échange, puis **injectés** dans les deux apps ; les textes propres à une plateforme (`android_*`, `ios_*`, `InfoPlist`) sont traduits dans leur propre fichier. Tout vit dans `docs/i18n/echange/`.

| Fichier | Rôle | Versionné |
|---|---|---|
| `source-fr-en.json` | Un texte par clé commune : `cle`, `plateformes`, `type` (`string`, `plural`, `substituted`), `fr`, `en`, `placeholders`, `contexte` (légende d'écran d'Android, fichiers de code, commentaire du catalogue), `longueurMax`, `courte`, `gele`, `sensible`, `interne`, `motif`, `invariableProbable`, `divergent` | oui |
| `propres-android.json`, `propres-ios.json` | Même structure pour les clés propres à une plateforme ; `propres-ios.json` contient aussi les 4 textes d'`InfoPlist.xcstrings` (`table: "InfoPlist.xcstrings"`) | oui |
| `config.json` | Réglages : préfixes **gelés**, familles **sensibles** (protocole §5), textes **internes** (diagnostic), **motifs** de format imposés, exceptions d'identité (`invariables`), taille des lots | oui |
| `gel-prs.json` | Instantané des clés touchées par les PR ouvertes (§3) | oui |
| `traduction-<langue>.json` | **Le livrable du lot 4B** : `{"langue": "es", "traductions": {"<clé>": …}}` | oui (lot 4B) |
| `traduction-<langue>.notes.json` | Questions et textes source à corriger signalés par les agents (`questions`, `a_corriger_source`), écrit par `fusionner` | oui (lot 4B) |
| `lots/<langue>/…` | Lots donnés aux agents (traduction, relecture aveugle, relecture, rétro-traduction), `plan-traducteurs.json` | **non** (`.gitignore`), régénérables par `lots` |
| `sorties/…` | Sorties brutes des agents (avant `fusionner`) | **non** (`.gitignore`) |

**Pourquoi les lots ne sont pas versionnés.** 4 langues × 14 lots ≈ 6 Mo (chaque lot recopie le glossaire de la langue pour être lisible seul) ; ils dépendent du gel : ils périmeraient à chaque PR de texte fusionnée ; ils se régénèrent en quelques secondes depuis le fichier d'échange et les documents. Le fichier d'échange, lui, est versionné (critère 7 du ticket) mais **n'est régénéré qu'au gel** du lot 4B : une PR de fonctionnalité n'a pas à le mettre à jour.

**Conventions du fichier de traductions** (vérifiées par `controler`) :

| Cas | Valeur |
|---|---|
| Texte simple | `"cle": "texte"` |
| Pluriel | `"cle": {"one": "…", "many": "…", "other": "…"}` : formes de la langue (§6) ; `many` identique à `other` quand le français l'écrit (es, it, pt) ; allemand : `one` et `other` seulement |
| Texte différent entre Android et iOS (`divergent`) | `"cle": {"android": "…", "ios": "…"}` (ou un seul texte, appliqué aux deux) |
| Substitution iOS (`type: "substituted"`) | `"cle": {"modele": "%#@n@ …", "formes": {"n": {"one": "…", "other": "…"}}}` (aucune au 2026-11-19) |
| Valeurs insérées d'un texte **commun** | notation Android : `%1$s`, `%2$d`, `%2$02d`, `%s`, `%d` ; le script écrit `%@` / `%lld` côté iOS en copiant le style du français iOS |
| Valeurs insérées d'un texte **propre** | notation de la plateforme (`%@`, `%lld` pour iOS) |
| Caractères | tels qu'ils s'affichent : jamais `&amp;`, jamais `\'`, jamais `&#160;` (le script encode pour Android) ; espaces insécables en vrais caractères U+00A0 ; `%%` pour un pourcentage dans un texte qui a des valeurs insérées |

---

## 2. Volumes au 2026-11-19 (`origin/main` `6545b10f`)

| | Textes | Mots français |
|---|---|---|
| **Communs** (même clé Android et iOS) | **1 215** | ≈ 8 940 |
| Propres à Android (`android_*`) | **56** | ≈ 500 |
| Propres à iOS (`ios_*`) | **87** | ≈ 835 |
| `InfoPlist.xcstrings` (iOS) | **4** | ≈ 216 |
| **Total par langue** | **1 362** | **≈ 10 490** (anglais ≈ 9 930) |
| dont gelés (§3) | **33** (30 communs) | ≈ 650 |
| dont **internes** (écran de diagnostic, **non traduits**, §3 bis) | 100 | ≈ 950 |
| **À traduire dans les lots** (hors gelés et internes) | **1 229** | **≈ 8 890** |
| dont sensibles, en tête (protocole §5 ; dont 82 d'alerte, renforcés) | 549 | ≈ 4 610 |
| dont courants | 680 | ≈ 4 290 |
| dont textes qui diffèrent entre Android et iOS (`divergent`) | 13 (dont 5 hors diagnostic : traduits **deux fois**, décision du PM) | |
| dont pluriels | 10 | |

L'estimation du ticket (1 238 par langue, ≈ 9 900 mots) a été faite avant la fusion de l'Alerte danger et de plusieurs tickets : **+ 124 textes** (dont 78 `alert_*`). Le total à traduire pour les quatre langues est ≈ **4 916 textes, ≈ 35 600 mots** (hors gelés et diagnostic).

**Textes qui ne se traduisent pas à la main** : 3 motifs de format du Feed (`feed_time_pattern_24`, `feed_time_pattern_12`, `feed_date_pattern`) : `config.json` fixe leur valeur par langue d'après le guide §3, le contrôle l'exige ; 41 textes dont le français et l'anglais sont identiques (`invariableProbable`) : 22 sont **déclarés invariables** dans `config.json` → `invariables.cles` (marque, prénoms d'exemple, adresse d'exemple, « Zoom », unités « m », « h », « min », noms des magasins...) et 8 le sont pour une ou plusieurs langues seulement (`invariables.parLangue` : « Flash » en es, it, pt ; « Sport » en it et de ; « Orange » et « Version » en de) : **aucun constat** pour ceux-là, y compris les contrôles d'apostrophe et d'espace d'unité ; les autres (« Notifications », « Type », « Photo »...) restent à traduire et leur oubli est une erreur ou, quand le français et l'anglais sont identiques, un avertissement.

---

## 3. Le gel : clés en mouvement

Une clé est **gelée** (`gele: true`, raison dans `geleRaisons`) si :

1. son nom commence par un préfixe de `config.json` → `gel.prefixes` : `consent3_`, `miff_consent`, `notif_centre`, `feed_place_` ; ou figure dans `gel.cles` ;
2. **ou** une PR ouverte l'ajoute, la supprime ou la modifie (français ou anglais, Android ou iOS).

**Méthode du point 2** (`node scripts/i18n-exchange.mjs gel-prs [--branche <ref>]…`, nécessite `gh` et le réseau) :

1. `gh pr list --state open` donne les PR et leurs fichiers ; seules les PR qui touchent `values-fr/strings.xml`, `values/strings.xml`, `Localizable.xcstrings` ou `InfoPlist.xcstrings` comptent ;
2. pour chacune : `git fetch origin refs/pull/N/head`, puis `git merge-base origin/main <tête>` (les changements déjà sur `main` ne sont pas attribués à la PR) ;
3. les clés sont comparées entre le point de départ et la tête de la PR, fichier par fichier (valeur brute Android ; localisations `fr` et `en` iOS) : clé **ajoutée**, **supprimée**, **modifiée** ;
4. le résultat est écrit dans `gel-prs.json` ; `extraire` le lit. `--branche` ajoute une branche sans PR.

**Alerte dégelée (décision du PM, 2026-11-19)** : l'alerte est validée et en service ; les préfixes `alert_` et `notif_alert_` ont été retirés de `gel.prefixes`. Leurs 82 textes sont traduits dans les lots, **marqués `sensible` et `sensibleRenforce`** (`config.json` → `sensiblesRenforces`) : ton calme et factuel (G104 à G107), mention « pas un service d'urgence » et « 112 » gardées exactement ; ils passent par la rétro-traduction et par la phase 5 du protocole.

État au 2026-11-19 : **#282** (iOS lot 3 : +18, −6, ~3), **#286** (Android lot 3 : +14, −6), **#287** (consentement v4 : +17, ~11), **#288** (centre de notifications : +4). Les 14 nouvelles clés `feed_place_*` et les 6 anciennes (`feed_place_noun_*`) sont gelées par le préfixe et par #282 / #286. **33 textes sont gelés** (≈ 650 mots) : 6 `feed_place_noun_*`, 17 `miff_consent*`, 3 `miff_renewal_*`, `settings_privacy_location_body` et `settings_privacy_time_body`, `permission_location_seen_body`, `notif_c1_arrival`, 2 noms de langue iOS, 1 texte `InfoPlist` (`NSLocationAlwaysAndWhenInUseUsageDescription`). Cette liste **bouge** avec les PR ouvertes : relancer `gel-prs` puis `lots` juste avant de lancer les agents. Les clés **ajoutées** par une PR n'existent pas encore sur `main` : elles ne sont pas dans le fichier d'échange (`gel-prs.json` les liste ; elles entreront par `extraire` après la fusion).

**Dégeler** (étape 9 du §4) : fusionner les PR, `git fetch`, rebaser, `gel-prs` (les PR fusionnées disparaissent de l'instantané), retirer de `config.json` les préfixes dont la famille est stable (les familles `consent3_` / `miff_consent` après #287, `feed_place_` après #282 et #286), `extraire`, puis `lots --restant` (§4, étape 9).

---

## 3 bis. L'écran de diagnostic n'est pas traduit (décision du PM, 2026-11-19)

Les ≈ 100 textes `diag_*`, `ios_diag_*` et `android_diag_*` (écran interne des versions de test) **restent en français et en anglais** ; ils sont marqués `interne` (`config.json` → `internes.motifs`) et **exclus des lots**, du contrôle des clés manquantes et du contrôle d'identité avec le français. Solution retenue, qui ne change **aucune ressource fr ou en et aucun code** :

1. **Registre** (`docs/contracts/i18n-languages.json` → `exceptions`) : une exception par langue (es, it, pt, de), `{"lang": "es", "keyPattern": "^(?:ios_|android_)?diag_", "check": "missing-key"}`, sur les deux plateformes. Le script de parité ne signale donc **jamais** « clé manquante » pour ces clés, y compris quand la langue passe en `required` ; il continue de les exiger en français et en anglais (test : `scripts/tests/i18n-exchange.test.mjs`, « registre : les clés de l'écran de diagnostic… »). Les clés absentes ne comptent pas comme défaut dans le tableau du résumé de la CI.
2. **Android, à faire dans la PR Android du lot 4B** (le lint est une erreur : `MissingTranslation`, `android/app/build.gradle.kts`) : ignorer ces clés dans `android/app/lint.xml`, un fichier de configuration, pas du code ni une ressource de texte :
   `<issue id="MissingTranslation"><ignore regexp="&quot;(android_)?diag_" /></issue>`
   (le message du lint cite le nom de la clé ; [À vérifier] à la première exécution de `./gradlew lint` dans la PR : si le motif ne correspond pas, essayer `regexp="(android_)?diag_"`). Cette PR-ci ne touche pas à Android.
3. **iOS** : une clé sans localisation « es » retombe sur l'anglais quand la langue du téléphone est l'espagnol (région de développement `en`). Limite connue [À vérifier sur TestFlight] : quand la personne **choisit** l'espagnol dans Réglages > Langue, `L10n` lit le paquet `es.lproj`, et une clé absente de ce paquet s'affiche brute (« diag_title ») : **uniquement** sur l'écran de diagnostic, absent des versions publiées. Pas de changement de code ici ; à noter pour la recette.
4. Plan B si le point 2 ne passait pas : traduire les 100 textes (`internes.motifs` vidé dans `config.json`, retrait des exceptions du registre). Le coût est ≈ 950 mots par langue.

---

## 4. Les étapes

Toutes les commandes se lancent depuis la racine du dépôt (ou avec `--racine <dossier>`). Node 24, aucune dépendance. `--langue` prend `es`, `it`, `pt`, `de`.

### Étape 0. Préparer

- Travailler dans une copie propre de `origin/main` **hors de `C:\dev\miff`** (le dossier local est périmé), sur une branche `i18n/lot4b-<langue ou plateforme>`. Jamais de push sur `main`.
- Vérifier que les PR du lot 3 (Android `android/i18n-lot3` #286, iOS `ios/i18n-lot3` #282) sont fusionnées ; sinon leurs clés restent gelées (§3).
- `node --test "scripts/tests/*.test.mjs"` : tout est vert.

### Étape 1. Geler, extraire, mesurer

```
node scripts/i18n-exchange.mjs gel-prs
node scripts/i18n-exchange.mjs extraire
```

`extraire` écrit les trois fichiers d'échange et affiche les volumes (communs, propres, gelés, divergents, sensibles, mots). `extraire --verifier` sort en code 1 si les fichiers sont périmés par rapport aux ressources (à utiliser **juste avant** les lots et avant la PR). Les avertissements (clé sans équivalent sur l'autre plateforme, variation iOS non gérée) sont à lire : une clé « exclue de l'échange » reste à traiter à la main.

### Étape 2. Calibrage (protocole, étape 0)

Un relecteur B relit les 35 textes de `docs/i18n/chaines-reference.md` ; plus de 3 écarts de gravité A ou B ⇒ corriger d'abord le glossaire et le guide (petite PR), puis relancer l'étape 1 pour que les lots recopient la version corrigée.

### Étape 3. Fabriquer les lots de traduction

```
node scripts/i18n-exchange.mjs lots --langue toutes            # 4 langues
node scripts/i18n-exchange.mjs lots --langue de --taille 80    # une langue, lots plus petits
```

Sortie : `docs/i18n/echange/lots/<langue>/batch-001.json` …, `plan-traducteurs.json` (attribution des lots) et `gele-traduction.json` (clés gelées exclues). Le défaut est **≈ 100 textes** (`config.json` → `tailleLot`) en lots **équilibrés** (jamais un dernier lot minuscule). **Les lots sensibles sont en tête** et ne sont **jamais mélangés** aux lots courants : localisation (`permission_*`, `InfoPlist`, `settings_privacy_location_*`, `feed_locate_*`), consentement, paywall, connexion (`auth_*`), alerte, notifications, suppression, puis le reste du Feed (ordre : `config.json` → `ordreSensibles`) ; dans chaque famille, l'ordre d'Android est conservé (les textes d'un même écran restent ensemble). Les textes **internes** (diagnostic) et **gelés** n'y figurent pas. Le champ `groupe` d'un lot vaut `sensible` ou `courant`.

Chaque lot est **lisible seul** : `consignes`, `formatSortie`, `glossaire` (voir ci-dessous) et `textes` ; chaque texte porte `cle`, `type`, `fr`, `en`, `placeholders`, `contexte` (`ecran`, `usages`, `prefixe`), `longueurMax`, `longueurCible` (étiquettes courtes : budget du guide §4.1), `formesAttendues` (pluriels de la langue), `sensible`, `interne`, `motif` + `valeurImposee`, `invariableProbable`, `variantesIos`, `sensibleRenforce`.

**Découpage retenu : 4 agents traducteurs par langue** (`--agents 4`, défaut), **3 à 4 lots chacun**, **blocs contigus dans l'ordre des lots**, découpage choisi pour équilibrer les mots du traducteur le plus chargé (`plan-traducteurs.json`) ; **1 agent relecteur B par langue** (phase 1 aveugle puis phase 2, sur les mêmes lots) ; **1 agent C de rétro-traduction par langue**, qui ne reçoit que les textes sensibles. Soit **6 agents par langue, 24 au total** (16 traducteurs, 4 relecteurs, 4 rétro-traducteurs). Le plan, au 2026-11-19, est donné en §7 bis.

`glossaire` est **extrait de `docs/i18n/glossaire.md` et `guide-de-style.md`** pour la langue : les termes figés (G01…, avec le français, l'anglais, le terme de la langue et les notes), la forme d'adresse, les termes **proscrits**, les phrases assemblées et formules d'heure, les noms des réglages du système, les pièges de genre, et, du guide, les principes, la fiche de la langue, les pluriels, les budgets et le ton. Les `consignes` de chaque lot rappellent, outre le ton et les valeurs insérées : la règle **{"android", "ios"}** pour un texte `variantesIos` (même si seule une unité, un mot ou un type de valeur diffère ; un texte sans `variantesIos` reçoit une seule traduction en notation Android) ; **pas de calque** du français ; **tutoiement** même si un texte (interne) vouvoie, avec le champ de sortie **`a_corriger_source`** pour signaler la phrase fautive ; les **termes proscrits** (« Attention : » ne devient pas « ¡Atención! », « Attenzione! », « Atenção! » ni « Achtung » ; « obligatoire » ne devient pas « obligatorio », « obbligatorio », « obrigatório » ni « Pflicht »). Un champ `glossaire.avertissements` non vide signifie qu'une section du document n'a pas été trouvée (document modifié) : corriger avant de lancer les agents.

### Étape 4. Traduire (agent traducteur A, un lot à la fois)

Modèle de consigne à donner à l'agent (contexte neuf pour chaque lot) :

> Traduis le lot `docs/i18n/echange/lots/<langue>/batch-NNN.json` selon les `consignes` du fichier. Lis d'abord `CLAUDE.md`, `docs/i18n/guide-de-style.md` et `docs/i18n/chaines-reference.md`. Écris ta sortie dans `docs/i18n/echange/lots/<langue>/sortie/batch-NNN.json` au format `formatSortie` : une clé `traductions` qui contient **toutes** les clés du lot, une seule fois. N'invente aucune décision produit : pose une `questions` si un texte est ambigu.

Chaque agent traducteur (T1 à T4) enchaîne **ses** lots dans l'ordre du `plan-traducteurs.json` ; **les quatre langues se traitent en parallèle**. Le glossaire §2.8 (G108 à G119) contient les termes relevés par le pilote es. Aucun lot n'est traduit par un service externe.

### Étape 5. Fusionner et contrôler la forme

```
node scripts/i18n-exchange.mjs fusionner --langue es --dossier docs/i18n/echange/lots/es/sortie
node scripts/i18n-exchange.mjs controler --langue es --complet
```

`fusionner` conserve aussi les champs `questions` et `a_corriger_source` des agents dans `traduction-<langue>.notes.json` (à lire avant la relecture). Il assemble les sorties dans `docs/i18n/echange/traduction-es.json` (clés triées ; **refuse** une clé présente deux fois avec des valeurs différentes ; plusieurs passes successives complètent le fichier). `controler` (option `--tout` pour tous les constats, `--json` pour une machine) applique, **par langue**, les contrôles du script de parité **et** des contrôles propres à l'échange :

| Contrôle | Gravité | Détail |
|---|---|---|
| `placeholders` | **erreur** | mêmes valeurs insérées que le français (type et position ; ordre libre), dans chaque forme de pluriel |
| `plural-forms`, `plural-recommended` | **erreur** | formes obligatoires du registre (`one`, `other`) ; `many` **exigée** en es, it, pt quand le français l'écrit ; formes inconnues refusées (de : jamais `many`) ; mauvaise structure (texte pour un pluriel) refusée (`forme`) |
| `empty`, `todo` | erreur | texte vide ou marqueur à traduire |
| `identical-to-source` | **erreur** ; avertissement si le français et l'anglais sont identiques ; **aucun constat** pour un texte déclaré invariable (`config.json` → `invariables`), un motif de format, un texte interne ou une exception du registre | texte identique au français hors marque et valeurs insérées |
| `gender-mark`, `formal-address`, `forbidden-term` | erreur | marque de genre, vouvoiement, terme proscrit du registre |
| `high-punctuation` | **erreur** | espace insécable avant `? ! : ;` (aucune en es, it, pt, de) |
| `nbsp-lost` | erreur pour les durées, avertissement ailleurs | espace insécable du français perdue |
| `pourcentage` | erreur | es et de : « 30 % » avec espace insécable ; it et pt : « 30% » collé |
| `entite-xml`, `barre-oblique`, `espaces`, `pourcent-isole` | erreur | entité XML, barre oblique inverse, espace de bord ou double, `%` isolé dans un texte à valeurs insérées (écrire `%%`) |
| `tiret-long`, `marque` | erreur | « — » interdit ; `Miff` et `Miff+` écrits exactement (jamais « miff », « Miff Plus ») |
| `motif` | erreur | valeur imposée des motifs de format (`config.json`) |
| `unite-espace` | avertissement | espace ordinaire entre un nombre et son unité (`h`, `min`, `m`...) |
| `apostrophe`, `guillemet-droit`, `es-ponctuation` | avertissement | it : apostrophe ’ ; es, pt, de : pas d'apostrophe ; guillemets de la langue ; `¿ ?` et `¡ !` appariés |
| `length`, `short-label-budget` | avertissement | plus de 1,5 fois le français ; étiquette courte au-dessus du budget (`longueurMax`) |
| rapport vs anglais | information | rapport de longueur global (`×1.13`) et les 5 textes les plus longs par rapport à l'anglais |

Les faux positifs se règlent par une **exception documentée** au registre (`exceptions`, voir `docs/contracts/i18n-languages.md` §6) ou `config.json` → `invariables`, jamais en désactivant un contrôle.

### Étape 6. Relecture croisée (protocole)

Le protocole `protocole-relecture-croisee.md` s'applique tel quel ; les lots sont générés par le même script, avec une langue et un mode :

| Phase | Commande | Entrée de l'agent | Sortie attendue |
|---|---|---|---|
| 1. Lecture aveugle (B) | `lots --langue es --mode aveugle` | français, anglais, glossaire, **pas de traduction** | `lectures` : une lecture en une ligne par clé ; sa propre traduction pour les textes `sensible` |
| 2. Relecture (B) | `lots --langue es --mode relecture` | idem + `traduction` de A | `remarques` : clé, code (S T G P L N X C A), gravité (A B C), avant, proposition |
| 3. Rétro-traduction (C) | `lots --langue es --mode retro` | **uniquement** la traduction des textes sensibles (ni français, ni anglais, ni glossaire) | `retro` : le texte en français, `doutes` |
| 4. Réponse de A | (à la main) | remarques | « appliquée », « refusée, motif », « escaladée » ; mise à jour du fichier de traductions, nouvelle passe de `controler` |
| 5. Revue du ton (localisation, consentement) | (agent de revue) | familles `permission_*`, `miff_consent3_*`, `miff_renewal_*`, `settings_privacy_*`, `ios_permission_*`, `InfoPlist`, `alert_*` | liste de contrôle (a à g) de `chaines-reference.md` |

Le relecteur B reçoit les lots `aveugle-NNN` puis `relecture-NNN` : **mêmes regroupements** que la traduction (sensibles en tête). L'agent C reçoit `retro-NNN` (seuls les textes sensibles, ni français ni anglais).

Comptes rendus : modèle du protocole §6, un fichier `docs/i18n/relectures/<lot>-<langue>.md` par langue **dans la PR**. Une remarque de gravité A ou B ouverte **bloque** le passage en `required`. Zéro remarque sur un lot entier déclenche une seconde lecture par un autre agent (protocole §7).

### Étape 7. Réinjecter dans les apps (une PR par plateforme)

```
node scripts/i18n-exchange.mjs injecter --langue es --plateforme android --dry-run   # simulation
node scripts/i18n-exchange.mjs injecter --langue es --plateforme android
```

Ce que fait la commande (après avoir relancé le contrôle de forme : **une seule erreur de forme empêche toute écriture**) :

- **Android** : écrit `values-es/strings.xml` (dossier du registre) dans l'ordre et avec les légendes de section de `values-fr/strings.xml`, `<plurals>` avec leurs formes, attributs `tools:ignore` recopiés ; échappe `\'`, `\"`, `&amp;`, `&lt;`, `\n`, `@` / `?` initiaux ; écrit `&#160;` pour l'espace insécable. Un fichier déjà présent garde ses clés que les traductions ne mentionnent pas.
- **iOS** : ajoute la langue (`es`) à chaque entrée de `Localizable.xcstrings` et d'`InfoPlist.xcstrings` : `stringUnit` avec `state: "translated"`, `variations.plural` pour les pluriels (ordre `one`, `many`, `other`), substitutions copiées du français (`argNum`, `formatSpecifier`), langues triées comme Xcode ; types `%@` / `%lld` calqués sur le français iOS. Le fichier n'est réécrit que s'il a le format canonique de Xcode (sinon, arrêt).
- **Jamais** de modification d'un texte `fr` ou `en`, d'une clé ou de l'ordre des clés. Une clé absente du catalogue, ou `shouldTranslate: false`, est **ignorée** et listée.
- **Idempotent et déterministe** : relancer sans changer les traductions ne modifie aucun fichier (`0 fichier(s) modifié(s)`). Le rapport donne, par plateforme, les clés écrites, déjà identiques, sans traduction, ignorées (`--json` pour une machine).

**Ordre** : PR Android (`--plateforme android`) d'abord, puis PR iOS (`--plateforme ios`). Les fichiers `values-es` etc. sont nouveaux : aucun conflit. Le catalogue iOS est un seul grand fichier : **rebaser au dernier moment** ; en cas de conflit sur `Localizable.xcstrings` ou `InfoPlist.xcstrings`, ne pas résoudre à la main : `git checkout origin/main -- ios/Miff/Resources/Localizable.xcstrings ios/Miff/Resources/InfoPlist.xcstrings`, puis relancer l'injection (idempotente).

Vérifications avant de pousser : `node scripts/check-i18n-parity.mjs --lang es` (aucune clé manquante pour la plateforme injectée, aucune erreur), `node --test "scripts/tests/*.test.mjs"`, `node scripts/check-forbidden-files.mjs`, et, côté Android, `./gradlew lint` (lint `MissingTranslation` et formes de pluriel) et, côté iOS, la CI du dépôt (Xcode n'est pas disponible sur le poste du PM).

### Étape 8. Passer une langue de `planned` à `required`, par plateforme

Le statut est **par plateforme** (`docs/contracts/i18n-languages.json` → `languages[].platforms.android.status` et `.ios.status`), donc une langue peut passer `required` sur Android à la fusion de la PR Android, et sur iOS à la fusion de la PR iOS (D9 du ticket ; la sortie publique se fait langue par langue si l'une échoue à la recette). Conditions, dans la **même PR** que l'injection de la plateforme :

1. `controler --langue es --complet` : **0 erreur** ; clés expectées toutes présentes (hors gelées, traitées à l'étape 9) ;
2. `check-i18n-parity.mjs --lang es` : aucune clé manquante ni erreur pour la plateforme ;
3. relecture croisée terminée pour la langue : **0 remarque de gravité A ou B ouverte** (comptes rendus joints) ;
4. les déclarations de langue du lot 3 sont en place pour la plateforme (énumération `AppLanguage` / `AppLanguageChoice`, `localeFilters` Android, `CFBundleLocalizations` iOS, noms de langue de Réglages) : le contrôle de cohérence des sources (critère 6) **échoue** si une langue `required` manque d'une source (voir `docs/contracts/i18n-languages.md` §4) ; vérifier l'état des PR #282 et #286 ;
5. changer `"status": "planned"` en `"required"` pour cette langue et cette plateforme **uniquement**, relancer `check-i18n-parity.mjs` (code 0) et les tests de la plateforme.

Dès qu'une langue est `required` sur une plateforme, **toute PR qui ajoute une clé doit l'ajouter dans cette langue** (règle permanente, `i18n-keys.md` point 3). Les textes des familles gelées doivent être traduits **avant** le passage en `required` (étape 9), sinon la CI échoue sur leurs clés manquantes.

### Étape 9. Rattraper les clés dégelées

Après la fusion des PR du gel (§3) : rebaser, `gel-prs`, retirer de `config.json` les préfixes devenus stables, `extraire`, puis :

```
node scripts/i18n-exchange.mjs lots --langue es --restant          # seulement les clés absentes de traduction-es.json
```

Traduire, relire (aveugle, relecture, rétro-traduction pour les sensibles), `fusionner`, `controler --complet`, `injecter`. Une clé dont le **texte français a changé** après sa traduction n'est pas détectée par le script : `extraire` ne garde pas l'historique ; la parade est le gel (§3) et la relecture de la PR qui modifie un texte (règle permanente, protocole §8).

---

## 5. Critères de sortie, par langue

Une langue est « terminée » pour une plateforme quand **toutes** ces cases sont cochées (elles reprennent les critères 16 à 22 du ticket) :

- [ ] `traduction-<langue>.json` contient 100 % des clés attendues (`controler --complet` : 0 erreur) ;
- [ ] les 3 motifs de format ont la valeur du guide ; les 4 pourcentages écrits à la main sont conformes à la langue (guide §3) ;
- [ ] les 10 pluriels ont les formes de la langue (`one`, `other`, et `many` en es, it, pt) ; zéro vérifié (« 0 días », « 0 giorni », « 0 dias », « 0 Tage ») ;
- [ ] aucun avertissement de longueur non traité ; allemand : captures à la police 1,3 et 2,0 (Android) et `accessibility3` (iOS) pour les écrans du ticket §8.3 ;
- [ ] relecture croisée faite à 100 %, rétro-traduction des textes sensibles faite, revue du ton sur localisation et consentement faite, **0 remarque A ou B ouverte**, comptes rendus joints à la PR ;
- [ ] les 4 textes d'`InfoPlist` disent exactement ce que dit le français sur la position, les 24 h et la photo (critère 22) ;
- [ ] `check-i18n-parity.mjs` : 0 clé manquante, 0 erreur pour la plateforme ; consistance des sources verte ;
- [ ] le registre passe la langue en `required` pour la plateforme, dans la PR ;
- [ ] la PR mentionne : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement », **jamais** « relu par un natif » ni « relu par un juriste ».

---

## 6. Pluriels par langue (rappel)

| Langue | Formes écrites dans le fichier de traductions | Remarque |
|---|---|---|
| es, it, pt | `one`, `many`, `other` | `many` écrite **identique à `other`** ; 0 relève de `other` |
| de | `one`, `other` | `many` interdite ; 0 relève de `other` |

Le contrôle exige `many` en es, it, pt quand le français l'écrit (9 des 10 pluriels), parce qu'Android signale sinon la forme manquante au lint ; l'exigence vient du registre (`plurals.recommended`), pas d'une règle propre à ce script.

---

## 7. Erreurs fréquentes du contrôle

| Message | Cause probable | Remède |
|---|---|---|
| `placeholders : valeurs insérées attendues « 1s 2s », trouvées « 1s »` | une valeur insérée oubliée (ou dans une seule forme de pluriel) | recopier toutes les valeurs du champ `placeholders` ; l'ordre dans la phrase est libre |
| `identical-to-source` | texte laissé en français | traduire ; si c'est un nom invariable, l'ajouter à `config.json` → `invariables` par une PR |
| `forme : objet de formes de pluriel attendu` | texte simple donné pour un pluriel (ou l'inverse) | respecter `type` |
| `forme : texte divergent entre Android et iOS` | clé `divergent` traduite avec un objet incomplet | `{"android": …, "ios": …}` ou un texte unique |
| `high-punctuation` | espace avant `?`, `!`, `:`, `;` | supprimer l'espace (le français seul en met) |
| `pourcentage` | « 30% » en es ou de, « 30 % » en it ou pt | guide §2.1 |
| `pourcent-isole` | `%` dans un texte qui a des valeurs insérées | écrire `%%` |
| `entite-xml`, `barre-oblique` | l'agent a écrit `&amp;` ou `\'` | écrire `&` et `'` : le script encode |
| `gender-mark`, `formal-address` | « o/a », « (e) », « usted »… | tournure neutre du glossaire §7, tutoiement |
| iOS : `le catalogue iOS n'est pas au format Xcode attendu` | le catalogue a été reformaté à la main ou par un autre outil | revenir à la version de `origin/main`, relancer |

---

## 7 bis. Plan des lots et des agents (état du 2026-11-19, hors gelés et diagnostic)

Même découpage pour les quatre langues : **13 lots** (6 sensibles, 7 courants), 1 229 textes, ≈ 8 890 mots français, attribués dans l'ordre :

| Agent | Lots | Contenu | Textes | Mots fr |
|---|---|---|---|---|
| **T1** | `batch-001`, `batch-002`, `batch-003` | sensibles : localisation et InfoPlist, consentement, paywall, connexion, début de l'alerte | 276 | ≈ 2 560 |
| **T2** | `batch-004`, `batch-005`, `batch-006` | sensibles : fin de l'alerte, notifications, suppression et départ d'une Miff, Feed, compte à rebours | 273 | ≈ 2 050 |
| **T3** | `batch-007`, `batch-008`, `batch-009`, `batch-010` | courants : accueil, connexion, onboarding, profil, Miff, lieux | 389 | ≈ 2 030 |
| **T4** | `batch-011`, `batch-012`, `batch-013` | courants : lieux, nouvelles (update), voyage, profil, textes propres à Android et à iOS | 291 | ≈ 2 250 |

Le plan exact est régénéré par `lots` (il change quand le gel ou les ressources changent) : **toujours donner aux agents le `plan-traducteurs.json` du jour**. Pour l'espagnol, les 192 textes du pilote (lots 001 et 002 de l'ancien découpage) sont déjà traduits : copier `traduction-pilote-es.json` en `docs/i18n/echange/traduction-es.json` puis `lots --langue es --restant` donne **12 lots** (6 sensibles, 6 courants, 1 117 textes, ≈ 8 100 mots) au lieu de 13.

---

## 8. Estimation du travail

| Passe | Lots par langue | Lots pour 4 langues | Agents |
|---|---|---|---|
| Traduction (A) | 13 (≈ 95 textes, ≈ 690 mots) | 52 | **16** (4 par langue, 3 à 4 lots chacun) |
| Lecture aveugle (B, phase 1) | 13 | 52 | 4 (1 par langue) |
| Relecture (B, phase 2) | 13 | 52 | les mêmes 4 |
| Rétro-traduction (C, phase 3, textes sensibles seulement) | 6 (≈ 92 textes) | 24 | 4 (1 par langue) |
| **Total** | **45** | **180 passes** | **24 agents** |

Taille d'un lot de traduction : ≈ 120 Ko (dont ≈ 55 Ko de glossaire recopié) ; c'est le seul « coût » : du temps de contexte, jamais d'argent. Les 549 textes sensibles (≈ 4 610 mots) dépassent l'estimation du protocole (≈ 250) parce que `config.json` → `sensibles.motifs` reprend **toutes** les familles du protocole §5 (dont l'alerte, 82 textes) ; resserrer la liste réduit les passes de rétro-traduction.

---

## 9. Ce que l'outillage ne fait pas (limites)

- **Il ne traduit pas** et n'appelle aucun service externe.
- **Il ne détecte pas** une faute de langue, un faux sens ou un ton alarmiste non listé : ce sont les relectures du §4, étape 6 ; les contrôles sont des **heuristiques** (genre, vouvoiement, termes proscrits) et ne remplacent pas un relecteur natif (risque R1 assumé).
- **Il ne vérifie pas** les formules d'heure (test des 24 heures, critère 20) ni les pluriels sur un formateur réel (critère 19) : ce sont des tests de plateforme du lot 3.
- **Il ne gère pas** les tableaux de chaînes Android (`<string-array>`, aucun aujourd'hui) ni les variations iOS autres que pluriels et substitutions (aucune aujourd'hui) : il les signale et les exclut de l'échange.
- **Il ne change pas le statut** `planned` / `required` : c'est une édition du registre dans la PR (étape 8).
- Un texte français **modifié après** sa traduction n'est pas signalé (voir étape 9).
- Les 13 textes qui diffèrent entre Android et iOS (dont 8 de l'écran de diagnostic) se traduisent deux fois ; si la différence n'est pas voulue, l'aligner côté code au lot 4B.

---

## 10. Décisions du PM et questions restantes (2026-11-19)

**Décidé** : (1) l'écran de diagnostic **n'est pas traduit** (§3 bis) ; (2) l'alerte (`alert_*`, `notif_alert_*`) est **dégelée**, marquée sensible renforcée ; (3) les 13 textes qui diffèrent entre Android et iOS **restent traduits deux fois** ; (4) découpage : 4 traducteurs, 1 relecteur B et 1 rétro-traducteur C par langue ; (5) glossaire complété (§2.8, G108 à G119).

**Restantes** :
1. **Lint Android** (§3 bis, point 2) : la valeur exacte du motif de `lint.xml` se confirme à la première exécution de `./gradlew lint` dans la PR Android du lot 4B.
2. **Clés gelées** (33 textes, ≈ 650 mots) : à traduire après la fusion de #282, #286, #287 et #288 (étape 9).
3. **`forbiddenTerms` du registre** est encore vide : le contrôle automatique des termes proscrits (glossaire §4) n'est pas alimenté ; les consignes des lots les citent et la relecture les contrôle. Les ajouter au registre est une PR séparée (risque de faux positifs).
