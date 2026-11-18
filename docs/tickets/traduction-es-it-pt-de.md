# Ticket : Miff en espagnol, italien, portugais et allemand (I18N-4L)

**Statut** : **proposition de l'agent `product-specs`, avec défauts recommandés** ; le PM a délégué la rédaction, seules les questions du §14 attendent une réponse. Aucun développement lancé, aucun statut Notion modifié, aucun coût.
**Rédigé le** : 2026-11-18, sur `origin/main` au commit `dc4fa264` (copie de travail propre ; le dossier `C:\dev\miff` local est périmé et n'a pas servi).
**Demande** : ajouter **espagnol (`es`), italien (`it`), portugais (`pt`, Portugal) et allemand (`de`)** au français et à l'anglais. L'anglais reste la langue de repli. La liste est déjà celle du PM dans Notion (ticket « Inscription » : « langues supportées : anglais, français, espagnol, portugais, allemand, italien ») ; elle prolonge la « vague 2 » de `docs/tickets/initialisation-technique.md` §4.F (le néerlandais n'en fait pas partie ici).

**Convention** : **[Décidé]** = décision déjà écrite par le PM. **[Proposé]** = défaut recommandé par cette spec. **[Mesuré]** = chiffre relevé dans le code de `origin/main`. **[À vérifier]** = fait de connaissance générale ou comportement de plateforme à confirmer par un test ou sur appareil avant de s'y fier.

---

## 1. Résumé pour le PM

1. **Le gros du travail est de la traduction, pas du code** : **1 238 textes par langue** (environ **9 900 mots** en français), soit **≈ 4 950 textes et ≈ 39 600 mots** pour les quatre langues. Les 1 107 textes communs à Android et iOS sont **traduits une seule fois** puis copiés dans les deux apps (cohérence garantie).
2. **Les notifications push n'ont rien à traduire côté serveur** [Mesuré] : la Cloud Function n'écrit aucun texte et ne connaît pas la langue (« pas de langue stockée », `backend/data-model.md` §8). Android compose la notification sur le téléphone, iOS la fait résoudre par le système depuis le catalogue. Donc **aucune modification de Cloud Function**.
3. **Il y a quand même du code à toucher**, parce que « français et anglais » est écrit en dur à une dizaine d'endroits (liste des langues des Réglages, filtre de langues de la release Android, recherche d'adresse, page web de réinitialisation, CI iOS, tests). Liste exacte au §3.4.
4. **Trois pièges de langue** que la traduction pure ne règle pas : les **phrases assemblées** (« a quitté » + nom de lieu : accord et prépositions), les **heures précédées d'un article** en espagnol, italien et portugais (« a las 16:02 » mais « a la 1:02 »), et l'**allongement de l'allemand** (boutons, onglets, bulles).
5. **Qualité sans natif et sans service payant** : glossaire figé avant de traduire, traduction par un agent, **relecture par un second agent indépendant**, contrôles automatiques en CI, recette PM limitée à la mise en page. Le **risque « pas de relecture par un locuteur natif » est assumé**, écrit comme tel (§11).
6. **Coût : 0 €** (aucune API de traduction, aucun abonnement). Rien ne coûte, voir §12.
7. **Ordre** : outillage et glossaire d'abord (sans conflit avec les PR en cours), traduction en masse **après** la fusion des PR qui ajoutent des textes, puis la règle « toute nouvelle clé arrive dans les six langues » imposée par la CI.

---

## 2. Périmètre

### 2.1 Traduit dans ce ticket [Proposé]

| Élément | Traduit ? | Remarque |
|---|---|---|
| Textes de l'app Android (`values-<langue>/strings.xml`) | **Oui** | 1 150 textes (dont 43 propres à Android) |
| Textes de l'app iOS (`Localizable.xcstrings`) | **Oui** | 1 191 textes (dont 84 propres à iOS) |
| Textes système iOS (`InfoPlist.xcstrings` : caméra, galerie, localisation) | **Oui** | 4 textes, ≈ 220 mots ; **sensibles** : ce sont les justifications que lit Apple et que voit la personne |
| Notifications (textes `notif_*`, `notif_circle_update_*`) | **Oui, dans les catalogues** | pas de travail serveur (§4.1) |
| Page web « Choisir un nouveau mot de passe » (lien de l'e-mail Firebase) | **Oui** | ≈ 250 mots par langue, tout petit, dans le parcours produit |
| Fiches des stores (titre, sous-titre, texte promotionnel, mots-clés, description) | **Oui, au lancement** | lot dédié (§10, lot 7) |
| Légendes des captures d'écran des stores | **Oui, au lancement** | + captures dans chaque langue (travail du PM, §13) |
| Modèles d'e-mails Firebase Authentication | **Réglage PM en console** | voir §4.3 et §13 |

### 2.2 Reste en français et anglais seulement [Proposé]

| Élément | Pourquoi | Ce que voit une personne en es/it/pt/de |
|---|---|---|
| **CGU, politique de confidentialité, texte de consentement long** (`docs/legal/`, ≈ 19 000 mots au total en FR+EN) | Quadrupler une surface juridique **sans aucune relecture** (CLAUDE.md : risque assumé, jamais « relu par un juriste ») est le pire rapport risque / bénéfice ; une traduction approximative d'un texte légal peut contredire la version de référence | Le lien « Conditions » ou « Confidentialité » ouvre la **version anglaise** |
| Page d'accueil du site, page « Supprimer mon compte » du site | Aucun enjeu produit ; la page de suppression est une exigence de Google Play, l'anglais suffit | Version anglaise |
| Écran de diagnostic (builds debug / TestFlight), journaux | Interne | inchangé |

Les **écrans de l'app qui résument le consentement** (localisation, statistiques, notifications) sont **traduits** : ils font partie de l'interface et la personne doit les comprendre. Seuls les documents longs hébergés sur le site restent FR+EN. **Risque assumé écrit au §11 (R3).**

### 2.3 Hors périmètre
Néerlandais et autres langues ; **portugais du Brésil** (§5) ; arabe et droite-à-gauche (non concernés ici : les quatre langues s'écrivent de gauche à droite) ; traduction de contenus saisis par les personnes (prénoms, noms de Miff, noms de lieux, adresses) ; fiches des stores de pays non retenus pour la distribution.

---

## 3. Audit chiffré de l'existant (`origin/main`, 2026-11-18)

### 3.1 Volumes [Mesuré]

| | Android | iOS | Communs (même clé des deux côtés) |
|---|---|---|---|
| Textes traduisibles (une clé = un texte ou un pluriel) | **1 150** (1 140 chaînes + 10 pluriels) | **1 191** + 4 `InfoPlist` | **1 107** |
| Propres à une plateforme (`android_*`, `ios_*`) | 43 (≈ 363 mots) | 84 (≈ 854 mots) | |
| Mots du français source | ≈ 8 833 | ≈ 9 298 (+ ≈ 220 `InfoPlist`) | ≈ 8 470 |
| Mots de l'anglais (comparaison) | ≈ 8 055 | | |
| Caractères, hors accessibilité et diagnostic (949 chaînes simples) | français 41 948, anglais 37 044 (**le français est 13 % plus long que l'anglais**) | | |

**À traduire par langue** : 1 107 communs + 43 Android + 84 iOS + 4 `InfoPlist` = **1 238 textes ≈ 9 900 mots**. Ressources non traduisibles (marque, noms de langue) : 16 côté Android, ignorées par les contrôles.
**Textes en cours d'arrivée** [Mesuré sur les branches ouvertes] : environ **+35 clés** dans les PR ouvertes (surtout « À propos et aide », #222 et #224 ; Répartition du temps iOS #259 ; langue iOS #218), sans compter le ticket Alerte danger (écrans, numéros d'urgence) qui en ajoutera plusieurs dizaines. D'où l'ordre du §10.

Autres volumes : **187** chaînes françaises avec valeurs insérées (`%1$s`, `%2$d`, `%2$02d`…) ; **10 pluriels** ; **5** chaînes avec espace insécable ; **3** textes avec accord au féminin « (e) » en français (`auth_session_replaced_notice`, `settings_account_sign_out_body`, `settings_delete_reauth_wrong_account`) ; **486** chaînes de 24 caractères ou moins en anglais (étiquettes, boutons, onglets : la population à risque pour l'allongement).

### 3.2 Infrastructure actuelle

**Android** [Mesuré]
- Français dans `res/values-fr/`, **anglais dans `res/values/`** (`resources.properties` : `unqualifiedResLocale=en-US`, donc l'anglais est la langue de repli). Aucun `locales_config.xml` écrit à la main : `androidResources.generateLocaleConfig = true` le **génère à partir des dossiers `values-*`** (choix « Langues des applications » d'Android 13+).
- Langue dans l'app : `AppLanguage` est une **énumération écrite en dur** (`FRENCH("fr")`, `ENGLISH("en")`, `SYSTEM`), l'écran Réglages > Langue la parcourt ; Android 13+ passe par `LocaleManager`, Android 8 à 12 par un petit fichier privé (`miff_language`) lu par `MainActivity.attachBaseContext`. Les noms « Français » / « English » sont non traduisibles (`translatable="false"`).
- **Piège** : `build.gradle.kts` ligne 250 : en `release` (et `releaseLike`), `localeFilters.addAll("en", "fr")` **retire de l'APK toute autre langue**. Sans modification, les nouvelles langues seraient absentes de la version publiée alors qu'elles marcheraient en debug **[À vérifier par un build release]**.
- Lint : `MissingTranslation` et `ExtraTranslation` sont des **erreurs** (donc dès qu'un dossier `values-es/` existe, toute clé manquante fait échouer le build, pour chaque langue, sans rien écrire).
- Pluriels : 10 `<plurals>` ; en français, 9 ont `one`/`many`/`other` et 1 seulement `one`/`other` (`settings_delete_owned_row`), l'anglais `one`/`other`.

**iOS** [Mesuré]
- Un seul catalogue `Localizable.xcstrings` (langue source `fr`, **un seul fichier JSON de 1 191 entrées**) et `InfoPlist.xcstrings` (4 entrées). `project.yml` : `developmentLanguage: fr`, `CFBundleDevelopmentRegion: en`, `CFBundleLocalizations: [fr, en]` (c'est cette liste qui dit à iOS quelles langues l'app parle).
- Pluriels : 10 variations de pluriel dans le catalogue ; aucune « substitution » ; les textes sont lus par `L10n` dans le paquet de la langue choisie.
- **Réglages > Langue (PR #218, encore ouverte)** : `AppLanguageChoice` est une énumération **en dur** (`french`, `english`, `system`) ; le choix applique la langue **à chaud** en lisant le paquet `fr.lproj` ou `en.lproj` ; « Langue du téléphone » garde le comportement d'avant (paquet principal, repli anglais). Sur `main`, `AppLanguageName.code(preferred:available:)` compare **la première partie** de la langue de l'appareil (`pt` pour `pt-PT`) aux langues déclarées dans `Bundle.main.localizations`. **Conséquence directe (§5)** : si l'on déclarait `pt-PT`, un appareil en `pt-PT` ne serait **pas reconnu** et tomberait sur l'anglais.
- Limite connue de #218 : la **langue choisie dans l'app n'est pas envoyée au serveur**, et les notifications **push iOS** (clés résolues par le système) suivent la **langue du téléphone**, pas le choix fait dans l'app.

**Contrôle de parité** [Mesuré]
- `scripts/check-i18n-parity.mjs`, tests dans `scripts/tests/check-i18n-parity.test.mjs`, job CI **« Parité des clés de traduction »** (`hygiene.yml`). Il compare **uniquement les clés françaises entre Android (`values-fr`) et iOS** (`ios_*` et `android_*` ignorées, format `snake_case`, doublons, préfixes connus). Il **ne regarde ni l'anglais, ni les valeurs, ni les pluriels, ni les langues autres que le français** : il est donc **à étendre** (§7).
- Les garde-fous de langue vivent aussi dans les tests de chaque plateforme : iOS `LocalizationTests` (français et anglais présents, mêmes valeurs insérées, nommage, pluriels) ; Android lint. Plusieurs tests Android lisent à la main `values-fr/strings.xml` et `values/strings.xml` (`PermissionPrivacyTest`, `NotificationPrivacyTest`, `FeedPrivacyRuleTest`…) pour interdire certains mots ou formes : **à généraliser à toutes les langues**, sinon une traduction pourrait contourner une règle de ton ou de confidentialité.
- Contrat `docs/contracts/i18n-keys.md` : mêmes clés des deux côtés, une clé = une phrase, valeurs positionnelles, dates et nombres par les formateurs du système.

### 3.3 Textes produits ailleurs que dans les deux apps

| Source | État actuel [Mesuré] | À faire |
|---|---|---|
| **Cloud Function `notifierUpdate`** (`backend/functions/src/notif*.ts`) | N'écrit **aucun texte**. Android : message « données seulement », l'app compose la notification dans **sa** langue. iOS : `titleLocKey = notif_circle_update_title`, `locKey = notif_circle_update_body`, résolus par iOS depuis le catalogue | **Rien côté serveur.** Traduire les deux clés dans les catalogues |
| **Langue d'une personne** | **Non stockée** : le document d'appareil a 7 clés exactement, « ni langue, ni état de l'autorisation » (`backend/data-model.md`) ; le choix de langue vit sur l'appareil (UserDefaults / `LocaleManager`). Choix de confidentialité à conserver | Rien. **Ne pas** ajouter de champ « langue » (aucun besoin : le texte est composé ou résolu sur le téléphone du destinataire) |
| **E-mails Firebase Authentication** (réinitialisation du mot de passe) | Android appelle `useAppLanguage()` : l'e-mail suit la langue de l'app et le lien porte `lang=fr` ou `lang=en`. Pour une autre langue, c'est le modèle par défaut de la console (texte de Google) | Réglage PM en console, §4.3 |
| **Page web `reset-password`** (`web/src/assets/reset-strings.js`) | `SUPPORTED_LANGUAGES = ['fr','en']`, repli anglais ; un test vérifie parité et tutoiement | Ajouter les 4 langues (≈ 250 mots chacune) et étendre le test |
| **Pages légales et accueil du site** | `web/src/pages.json` : « toute page existe en fr ET en en (un test le vérifie) » ; `lang-redirect.js` ne connaît que fr et en ; liens depuis l'app par `android_legal_*_path` / `ios_legal_*_path` (testés par `LegalLinksTest` contre `pages.json`) | Ne pas traduire (§2.2). Pour les 4 langues : `android_legal_*_path` = chemins **anglais** |
| **Fiches des stores** | `marketing/store-listing-ios.md`, `-android.md` : français + anglais | Lot 7 |
| **Recherche d'adresse** (Android : Photon ou Geoapify ; iOS : MapKit) | Android envoie `lang=fr` si la langue est le français, **sinon `lang=en`** (`AddressSearchClients.kt`, `PlaceNavigation.kt`). iOS : MapKit suit la langue du téléphone | Remplacer par une table « langue de l'app -> langue acceptée par le fournisseur » avec repli. Photon n'accepte qu'un petit nombre de langues [À vérifier : ≈ en, de, fr, it] : pour `es` et `pt`, repli sur le défaut du service ; Geoapify en accepte davantage. Test réel avant livraison |
| **Noms de catégories de lieux** | Libellés dans le catalogue (`docs/contracts/place-categories.md`), emoji identiques partout | Traduire les 7 libellés ; emoji inchangés |
| **Numéros d'urgence** (ticket `alerte-danger.md` §8.4, **pas encore codé**) | Catalogue de données par **région de la locale** (France 17/15/18/112, reste de l'UE 112, autre 112) ; libellés « du catalogue de textes (FR/EN) » | Libellés traduits dans les 6 langues ; **la langue ne choisit pas le pays** (un téléphone en allemand en France garde les numéros français). Ajouter Allemagne, Espagne, Italie, Portugal au catalogue est une **extension de données** possible, à vérifier sur sources officielles par le dev du ticket alerte |
| **Noms de lieux, de Miff, prénoms** | Saisis par les personnes | Jamais traduits |

### 3.4 Endroits où « fr / en » est écrit en dur [Mesuré] (tout est à rendre piloté par une seule liste)

1. Android : `AppLanguage` (enum), `AppLanguage.fromTag`, écran `LanguageScreen` (`when` sur trois valeurs), clés `settings_language_french` / `_english`.
2. Android : `localeFilters.addAll("en", "fr")` dans `app/build.gradle.kts`.
3. Android : `AddressSearchClients.kt` et `PlaceNavigation.kt` (« fr » sinon « en »).
4. Android : tests de contenu qui ne lisent que `values-fr` et `values`.
5. iOS : `AppLanguageChoice` (enum, `from(stored:)` ne reconnaît que fr et en), `LanguageView`, clés `ios_settings_language_french` / `_english`, `AppLanguageName`.
6. iOS : `CFBundleLocalizations` dans `project.yml`.
7. iOS : `LocalizationTests` (fr et en seulement) ; `ios/ci/screenshots.sh` ligne 51 : la capture **« 03-de-repli-anglais » utilise l'allemand comme langue non prise en charge** : elle **deviendrait fausse** dès que l'allemand est pris en charge (à remplacer par une langue non prise en charge, par exemple le néerlandais `nl`).
8. Web : `reset-strings.js` (`SUPPORTED_LANGUAGES`), `lang-redirect.js`, `pages.json`.
9. `scripts/check-i18n-parity.mjs` (français seulement) et `docs/contracts/i18n-keys.md` (tableau des fichiers).
10. Fiches des stores, brief de captures.

---

## 4. Ce qui ne demande pas de développement (et ce que le PM doit savoir)

### 4.1 Notifications
Rien à changer côté serveur. Côté téléphone : Android compose dans la langue **de l'app** (choix dans l'app ou langue du téléphone) ; iOS laisse le système résoudre les clés dans la langue **du téléphone**. Écart connu (avant ce ticket) : sur iOS, un utilisateur qui force l'allemand dans l'app avec un téléphone en français reçoit les **push** en français. Écrit comme **limite connue**, pas corrigée ici (la corriger exigerait d'envoyer la langue au serveur, donc de la stocker : refusé par le choix de confidentialité du §3.3).

### 4.2 Pluriels (règles CLDR) [À vérifier avec lint Android et Xcode au lot 1]

| Langue | Catégories CLDR | Sens de `one` | En pratique dans Miff |
|---|---|---|---|
| fr | `one`, `many`, `other` | 0 et 1 | déjà en place (9 sur 10 écrits avec `many`) |
| **es, it** | `one`, `many`, `other` | **1 seulement** (0 -> `other` : « 0 días », « 0 giorni ») | 2 formes utiles |
| **pt** (Portugal) | `one`, `many`, `other` | **1 seulement** (au Brésil, 0 et 1) | 2 formes utiles |
| **de** | `one`, `other` | 1 seulement | 2 formes |

Précision sur « deux formes » : c'est vrai pour **tous les nombres que l'app affiche** (membres, lieux, jours, heures, minutes). Les versions récentes des règles CLDR ajoutent `many` en es/it/pt/fr, mais seulement pour les millions entiers (« 1 000 000 de ... »), **inatteignable** ici. **Règle [Proposée]** : `one` et `other` **obligatoires** dans toutes les langues ; `many` écrit **identique à `other`** quand le français l'écrit (même pratique qu'aujourd'hui), jamais exigé s'il manque. Attention à la différence sur zéro : un texte français pensé pour « 0 » ou « 1 » (`one`) doit être relu pour 0 dans les trois autres langues latines et en allemand.

### 4.3 E-mails d'authentification Firebase (réglage du PM, 0 €)
Firebase propose des modèles par langue dans la console (Authentication > Modèles > choisir la langue du modèle). Le code appelle déjà `useAppLanguage()` côté Android ; il faut vérifier côté iOS la même chose. **[À vérifier]** : que la console liste bien es, it, de et le portugais du Portugal (je n'ai pas pu la consulter). Si une langue n'y figure pas, l'e-mail part dans le modèle par défaut (anglais) : acceptable, à noter. L'agent fournit les textes d'objet et de corps (tutoiement, ton rassurant) à coller ; le PM les colle (§13).

### 4.4 Formats [À vérifier au lot 1 par des tests sur formateur réel]
Tout ce qui est **date, heure, nombre, pourcentage, devise** passe par le formateur du système, donc se met en forme tout seul. À ne **pas** faire à la main :
- **Pourcentage** : le formateur donne « 31 % » en français (espace fine insécable U+202F), **« 31 % » aussi en allemand et en espagnol (espace insécable)**, mais **« 31% » en italien et en portugais**, comme en anglais. L'idée « pas d'espace en de/es » est donc **à corriger** : seuls it et pt (et en) n'en ont pas. Aucun travail si le formateur est utilisé. **Textes avec pourcentage écrit en dur** [Mesuré] : `paywall_annual_savings` (« presque 30 % d'économie », décision PM du 2026-11-07 à conserver), `profile_timeshare_percent_tiny` (« < 1 % »), `android_onboarding_battery_1` et `_2` (« 85 % », « 22 % »). À écrire à la main **avec la bonne espace de chaque langue**, et à tester.
- **Motifs de date et d'heure du Feed** (`feed_time_pattern_24`, `_12`, `feed_date_pattern`) : **ce sont des motifs de format** (`HH 'h' mm`, `d MMM`), pas des phrases. Un agent traducteur ne doit pas les « traduire » : ils sont écrits par langue d'après le formateur de la langue (par exemple 24 h partout sauf choix 12 h du téléphone ; `d. MMM` en allemand) et **validés par un test qui formate une date réelle** dans chaque langue.
- **Durées du compte à rebours** (`countdown_duration_*`) : textes du catalogue (« 1 h 12 min », espace insécable), à décliner par langue (allemand « 1 Std. 12 Min. » ou « 1 h 12 min » : choix du glossaire).
- **Valeurs du store** (prix, devises) : jamais dans un texte traduit (déjà la règle).

### 4.5 Polices, caractères, tri, recherche [Mesuré]
- **Polices** : Hanken Grotesk (Android et iOS) et Baloo Thambi 2 contiennent **tous les caractères nécessaires** (ñ Ñ ¿ ¡ ç ã õ â ê ô à è ì ò ù ä ö ü ß ẞ, guillemets « » „ " " ' ', tirets, €, º ª, ·) : vérification faite dans les fichiers de police du dépôt. Aucune police à ajouter, aucun repli système attendu.
- **Droite-à-gauche** : non concerné (les quatre langues s'écrivent de gauche à droite).
- **Majuscules** : `ß` s'écrit « SS » en majuscules ; l'app ne met pas de texte entier en capitales (une seule initiale, `CategoryIcon`) : sans effet.
- **Tri** : seule la liste des pays de l'indicatif utilise un collateur de la locale (`PhoneNumbers.kt`, Android) ; les autres tris sont par date ou identifiant. Test : « Österreich », « España », « Ñ » classés dans la langue active.
- **Recherche** : pas de recherche plein texte locale ; la recherche d'adresse est externe (§3.3).
- **Noms de pays** : fournis par le système dans la langue de l'app (pas de texte à écrire).

### 4.6 Sélection de la langue
- **Premier lancement** : l'app suit la langue du téléphone si elle est prise en charge, sinon l'**anglais** [Décidé : Notion « Paramètre », et comportement actuel]. Une langue du téléphone non prise en charge (néerlandais, japonais…) donne l'anglais.
- **Changement dans l'app** : à chaud, sans redémarrage, sur Android comme sur iOS (déjà le cas pour fr/en ; **iOS : à valider une fois #218 fusionnée**).
- **Liste des langues dans Réglages > Langue** [Proposé] : **Deutsch, English, Español, Français, Italiano, Português** (ordre alphabétique du **nom écrit dans sa langue**, jamais traduit), puis **« Langue du téléphone »** en dernière ligne comme aujourd'hui. Les noms ne sont pas traduisibles (`translatable="false"` / `shouldTranslate: false`).
- **Source unique de la liste** [Proposé] : un fichier de registre des langues (§6) dont **dérivent** l'énumération Android, l'énumération iOS, `CFBundleLocalizations`, `localeFilters`, la liste du script de parité et un test qui échoue si l'une des sources diverge.

---

## 5. Variantes régionales : défauts recommandés

Contexte : app lancée depuis la France, soumise au RGPD, premier public francophone, pas d'équipe locale.

| Langue | Défaut [Proposé] | Arguments |
|---|---|---|
| **Espagnol** | **Un seul jeu `es`**, espagnol **neutre standard** : tutoiement (« tú »), jamais « vosotros » (qui est un régionalisme d'Espagne), vocabulaire sans régionalismes : « teléfono » (ni « móvil » ni « celular »), « ubicación » pour la position. | Un jeu, une relecture, un jeu de captures. Marche pour l'Espagne et l'Amérique latine ; le neutre est compris partout sans être « local » nulle part |
| **Portugais** | **`pt` = portugais du Portugal (pt-PT), orthographe de 1990** ; **pt-BR ajouté plus tard** comme jeu séparé `pt-BR` | Contexte UE et RGPD, proximité de la France (diaspora portugaise nombreuse) ; le portugais du Brésil est une langue de **marché** différente (vocabulaire : « ecrã » / « tela », « telemóvel » / « celular », « partilhar » / « compartilhar », et surtout « tu » courant au Portugal contre « você » au Brésil), à traiter avec ses captures et sa fiche. Ajouter `pt-BR` ensuite = **un dossier de plus**, sans toucher au reste |
| **Italien** | `it`, tutoiement (« tu ») | Pas de variante |
| **Allemand** | `de`, tutoiement (« du ») | Une seule variante pour l'Allemagne, l'Autriche et la Suisse (langue-seule). Pas de « ß » en Suisse : sans conséquence ici, l'orthographe suit l'Allemagne |

**Pourquoi le code `pt` et non `pt-PT`** [Proposé] : sur iOS, le code de langue `pt` (`pt.lproj`) est reconnu pour tout appareil portugais, alors que `pt-PT` ne le serait pas par le code actuel (§3.2). Sur Android, un dossier sans région (`values-pt`) correspond à **toute** région du portugais. Le jour où `pt-BR` existe : on ajoute `values-pt-rBR` / la langue `pt-BR`, et `pt` reste le portugais du Portugal pour tous les autres appareils portugais.

**Comportement décrit appareil par appareil** (ce qu'il faut observer en recette ; **[À vérifier] sur appareil ou en CI**, je n'ai pas pu tester ici) :

| Appareil | Android | iOS |
|---|---|---|
| Portugais du **Brésil** (`pt-BR`), « Langue du téléphone » | Le dossier `values-pt` (sans région) correspond au portugais de toute région : **l'app s'affiche en portugais (texte du Portugal)**, pas en anglais | Une langue `pt` déclarée correspond aussi à `pt-BR` : **portugais (texte du Portugal)** ; le code d'app compare la partie « langue » seule, donc reconnaît `pt` |
| Portugais du **Portugal** | `values-pt` : portugais | `pt` : portugais |
| Espagnol du **Mexique** (`es-MX`) ou d'Amérique latine (`es-419`) | `values-es` : **espagnol neutre** | `es` : **espagnol neutre** |
| **Néerlandais**, japonais, toute langue non prise en charge | **Anglais** (ressources par défaut) | **Anglais** (`CFBundleDevelopmentRegion = en`) |
| Allemand d'Autriche ou de Suisse (`de-AT`, `de-CH`) | `values-de` | `de` |

Conséquence assumée : un Brésilien lit du portugais **européen** (« tu », « ecrã »), ce qui reste compréhensible et vaut mieux que l'anglais. Il ne s'agit pas d'un lancement au Brésil.

---

## 6. Décisions par défaut (récapitulatif) [Proposé]

| # | Décision |
|---|---|
| D1 | Langues : `es`, `it`, `pt` (Portugal), `de`, en plus de `fr` et `en`. Repli : anglais |
| D2 | **Tutoiement partout** : es « tú », it « tu », pt « tu », de « du ». Jamais « usted », « Lei », « você », « Sie » |
| D3 | Une seule source d'ordre des langues : le registre (fichier unique) ; la liste des Réglages en dérive |
| D4 | **Les 1 107 textes communs sont traduits une seule fois**, dans un fichier d'échange par langue ; un script les **fusionne** dans `values-<langue>/strings.xml` et dans le catalogue iOS (sans réécrire tout le fichier). Les 43 + 84 + 4 textes propres à une plateforme sont traduits directement |
| D5 | Les **textes légaux du site restent FR+EN** ; l'app envoie vers la version anglaise pour les 4 nouvelles langues |
| D6 | **Phrases assemblées** : une phrase complète par cas quand c'est possible ; sinon formulation neutre sans préposition ni article (§8) |
| D7 | **Heures** : pas d'article variable devant l'heure en es/it/pt (§8) |
| D8 | **Genre** : jamais de marque de genre (`(e)`, `o/a`, `@`, `x`). Tournures neutres (verbe à la forme impersonnelle, nom, « se ha cerrado tu sesión »). Les 3 textes français « (e) » sont des cas d'école du glossaire |
| D9 | **Statut par langue** dans le registre : `planned` (contrôles en avertissement), `required` (contrôles bloquants). Une langue ne passe en `required` que lorsque sa traduction est complète ; la sortie publique peut donc se faire **langue par langue** si l'une échoue à la recette |
| D10 | Aucun service de traduction externe, aucune API payante, aucune donnée de l'app envoyée à un tiers pour traduire (les textes source ne contiennent aucune donnée personnelle, mais on s'en tient aux agents) |

---

## 7. Critères d'acceptation (numérotés)

**Outillage et parité (lot 1)**
1. Un **registre des langues** unique (par exemple `docs/contracts/i18n-languages.json` : code, nom dans sa langue, catégories de pluriel attendues, dossier Android, code iOS, statut `planned` / `required`) existe et est lu par le script de parité.
2. `scripts/check-i18n-parity.mjs` contrôle, **pour chaque langue du registre**, sur **Android et iOS** : (a) le même ensemble de clés que le français ; (b) **aucun texte vide**, aucun marqueur « TODO » ; (c) les **mêmes valeurs insérées** que le français (position et type, `%1$s` = `%1$@`, `%1$d` = `%1$lld`, `%2$02d` = `%2$02lld`, ordre libre mais ensemble identique) ; (d) pour chaque pluriel : `one` et `other` présents, aucune forme inconnue de la langue ; (e) les caractères échappés Android (apostrophes `\'`, `&amp;`, `&#160;`) corrects ; (f) les espaces insécables du français sont **conservées ou remplacées par la règle de la langue**, jamais perdues par erreur dans les durées.
3. Le script **signale (avertissement)** : un texte **identique au français** pour une langue qui n'est pas le français (hors marque et ressources non traduisibles) ; un texte **plus long que 1,5 fois le français** et, pour une liste de clés « étiquette courte » (boutons, onglets, titres, puces), **plus long qu'un budget** fixé par clé dans le registre (par défaut : longueur du français + 40 %).
4. Le script **échoue** si : une marque de genre `(e)`, `o/a`, `@` apparaît ; une forme de vouvoiement connue apparaît (liste courte par langue : « usted », « Lei » en italien comme pronom, « você », « Sie / Ihr / Ihre » en allemand) ; un mot de la liste des **termes proscrits par langue** (ton alarmiste ou culpabilisant, fournie par le glossaire) apparaît. Ces listes sont des **heuristiques** : un faux positif se règle en l'ajoutant à une liste d'exceptions documentée.
5. Les tests du script (`scripts/tests/`) couvrent chaque contrôle avec un cas qui échoue et un cas qui réussit ; le job CI **« Parité des clés de traduction »** garde son nom, exécute les tests puis le script.
6. Un **test de cohérence des sources** échoue si l'énumération Android, l'énumération iOS, `CFBundleLocalizations`, `localeFilters`, le dossier des ressources et le registre ne listent pas **exactement les mêmes langues**.
7. Le contrat `docs/contracts/i18n-keys.md` est mis à jour : **toute nouvelle clé arrive dans les six langues** dès qu'une langue est `required`, y compris dans les PR de fonctionnalité ; ajout du **fichier d'échange** et de la marche à suivre pour une clé commune.

**Glossaire et style (lot 2)**
8. Un glossaire figé existe **avant** toute traduction en masse, par langue, pour au moins les termes du §15 et ceux que le lot 2 ajoute après lecture complète du français (cible : **toute expression qui revient plus de 3 fois**). Il contient aussi, par langue : le tutoiement, la forme d'adresse, les termes proscrits, les règles de ponctuation (guillemets, apostrophes, espaces avant « ? » et « ! » : aucune en es/it/pt/de, contrairement au français), l'écriture des heures et des durées.
9. Les **phrases assemblées** de l'app (§8.1) sont inventoriées dans le glossaire avec, pour chacune, la tournure retenue dans les quatre langues.

**Code (lots 3 et 4, prérequis)**
10. La liste des langues des Réglages (Android et iOS) est **construite depuis le registre** ; elle affiche 6 langues + « Langue du téléphone » ; chaque nom est écrit dans sa langue ; le choix s'applique **à chaud** ; un test vérifie les 6 lignes, la sélection unique et la lecture du TalkBack / VoiceOver (« Español, sélectionné »).
11. Une valeur inconnue ou écrite par une version future (par exemple `pt-BR`) donne « Langue du téléphone » et **ne casse pas l'écran** (comportement actuel, étendu au test).
12. **Appareil en `pt-BR`** : l'app s'affiche en **portugais** sur Android et iOS ; **`es-MX`** : espagnol ; **langue non prise en charge** : anglais (cas vérifiés en test automatique ou capture, comportement consigné dans le README de chaque plateforme).
13. `localeFilters` (release) contient les 6 langues ; un contrôle de build release vérifie que `values-es`, `values-it`, `values-pt`, `values-de` sont **dans l'APK** (liste des locales du bundle).
14. La recherche d'adresse utilise une **table langue -> langue acceptée par le fournisseur** avec repli ; un test par langue et par fournisseur.
15. Aucune chaîne en dur qui assemble des morceaux de phrase pour une des nouvelles langues ; la capture CI « 03-de-repli-anglais » est remplacée par une langue réellement non prise en charge.

**Traduction (lots 5 et 6)**
16. **100 % des clés** des deux plateformes sont présentes dans les 4 langues (le script de parité est **vert en `required`** pour chacune).
17. **Relecture croisée faite** : pour chaque langue, un second agent indépendant, sans accès à la traduction du premier avant d'avoir produit sa propre lecture du français, a relu 100 % des textes et ses remarques ont été traitées ; le compte rendu (nombre de remarques, par catégorie : sens, ton, grammaire, longueur, terme du glossaire) est joint à la PR.
18. **Aucun texte alarmiste ni culpabilisant** : liste de contrôle du ton (CLAUDE.md) appliquée aux notifications, messages d'erreur, écrans de localisation, alerte danger, compte à rebours ; échantillon relu intégralement (au moins les clés `notif_*`, `permission_*`, `*_error*`, `miff_message_*`, `countdown_*`, `feed_bubble_*`).
19. **Pluriels** : les 10 pluriels ont `one` et `other` dans chaque langue ; test avec les nombres 0, 1, 2, 5, 21, 100 ; le zéro est correct (« 0 días », « 0 giorni », « 0 dias », « 0 Tage »).
20. **Formats** : motifs de date et d'heure validés par un test qui formate une vraie date dans chaque langue ; pourcentages écrits à la main conformes à la langue (§4.4) ; aucune heure précédée d'un article variable (§8.2).
21. **Longueurs** : aucun avertissement de longueur non traité ; sur Android, **captures de tests d'écran (Robolectric) en allemand à l'échelle de police 1,3 et 2,0** pour les écrans listés au §8.3 ; sur iOS, **captures CI en allemand** avec les plus grandes tailles d'accessibilité (`accessibility3`) pour les mêmes écrans ; aucun texte coupé sans possibilité de le lire, aucun bouton sans libellé complet pour le lecteur d'écran.
22. **Textes système iOS** (`InfoPlist.xcstrings`) traduits dans les 4 langues, avec **la même exactitude que le français** sur ce que voit le cercle (position, 24 h, photo) ; test statique `InfoPlistStaticTests` étendu.
23. Page web de réinitialisation du mot de passe : 6 langues, test de parité et de tutoiement étendus ; repli anglais pour toute autre langue.
24. Les **tests de contenu** (mots interdits, confidentialité des notifications, absence de position dans les textes) s'appliquent **à toutes les langues** (boucle sur le registre, plus de fichier français en dur).

**Notifications, e-mails, stores (lots 7 et 8)**
25. Les deux textes de la notification de cercle (`notif_circle_update_title` / `_body`) existent dans le catalogue iOS dans les 4 langues et la notification affichée en test (capture sur iPhone) est correcte en allemand et en espagnol ; Android : même vérification avec les deux langues. **Aucune modification de `backend/functions/`** (vérifié dans la PR).
26. Textes d'e-mail Firebase fournis pour les 4 langues (objet + corps, tutoiement), prêts à coller ; contrôle côté iOS que `useAppLanguage` ou équivalent est appelé.
27. Fiches des stores : titre, sous-titre, texte promotionnel, mots-clés et description dans les 4 langues **dans les limites de caractères** de chaque store (contrôlées par un script), sans affirmation absente de l'app, sans contredire les déclarations déjà arrêtées dans `docs/release/` ; légendes de captures dans les 4 langues.

**Recette et publication**
28. Recette PM sur iPhone 11 Pro et OPPO Find X5 Lite **dans chacune des 4 langues** (checklist `docs/qa/checklists/traduction-recette-pm.md` à écrire par `qa-tester`), plus un appareil Android réglé en `pt-BR` et un en `es-MX` si le PM en dispose (sinon la CI et les tests couvrent ces cas).
29. Aucun plantage ni texte de clé brut (« feed_label_xxx ») visible dans aucune des 6 langues sur les écrans de la checklist ; aucune clé manquante signalée par lint ou Xcode.
30. Aucune soumission store n'est faite par un agent : les fiches sont **préparées** (CLAUDE.md).

---

## 8. Cas qui peuvent casser, et solutions par défaut

### 8.1 Phrases assemblées (grammaire) [Mesuré]
Le français insère un nom de lieu (`feed_place_noun_*`) dans des phrases : `feed_label_left` (« A quitté la maison »), `feed_bubble_orange`, `feed_bubble_me_orange`, `notif_c1_arrival`, `_departure`, `_update`, `_silence` (+ `notif_c1_silence_vowel` pour « de / d' », sans objet en es/it/pt/de), `notif_c1_moment_*`. Le nom contient parfois son article (« la maison », « le travail », « l'école »). Selon la langue, la préposition et l'article **se contractent ou changent de forme** : espagnol « a la / al / del », italien « alla / al / dal », portugais « na / no / da / do », allemand « zu Hause / bei der Arbeit / in der Schule » (**cas grammaticaux** : le nom change selon qu'il vient après « est arrivé à » ou « a quitté »).
**Défaut [Proposé]** : le lot 2 écrit, pour chacune des phrases ci-dessus, **la tournure dans chaque langue qui fonctionne avec un nom de lieu unique, sans préposition ni article propre au verbe** (par exemple des tournures en deux temps : « Salida : casa » ; « Verlassen: Zuhause »), ou, si une langue ne peut pas, **un second jeu de noms de lieux** (`feed_place_noun_*` utilisé après « de », `feed_place_noun_*_at` utilisé après « à »), ajouté **aux six langues ensemble** et en une PR (changement de contrat mineur). La décision est prise **au lot 2** sur pièces, pas ici.

### 8.2 Heures précédées d'un article (espagnol, italien, portugais) [Mesuré]
Les formules `feed_moment_at_*` (« à 16 h 02 »), `_since_*` (« depuis 16 h 02 »), `_from_*` (« de 16 h 02 ») reçoivent l'heure écrite par le système. En es/it/pt, la préposition **change avec l'heure** : « a las 16:02 » / « a la 1:02 » ; « alle 16:02 » / « all'1:02 » ; « às 16:02 » / « à 1:02 ». Une clé unique est donc fausse une heure sur vingt-quatre.
**Défaut [Proposé]** : **écrire ces formules sans article** (« hoy, 16:02 » ; « desde 16:02 » ; « oggi, 16:02 » ; « dalle »… remplacé par « da 16:02 » ; « hoje, 16:02 » ; « desde 16:02 »), formulations un peu télégraphiques mais **toujours exactes** et compactes dans les cartes ; l'allemand n'a pas le problème (« um 16:02 Uhr », « seit 16:02 Uhr »). Le test du critère 20 formate les 24 heures. Alternative écartée : choisir la clé selon l'heure dans le code (complexité et risque d'écart entre iOS et Android).

### 8.3 Allongement (surtout l'allemand) et tailles de police
Estimation [non mesurée sur de vrais textes] : l'allemand écrit environ **+20 à +35 % de caractères par rapport à l'anglais** (donc de l'ordre de +10 % par rapport au français) et casse les mots composés longs (« Benachrichtigungseinstellungen ») ; espagnol, italien, portugais : proches du français (±10 %). **Écrans à contrôler en allemand, à grande police** : barre d'onglets (noms parlés et visibles), boutons de bas d'écran, **bulles du Feed** (`feed_bubble_*`), étiquettes de statut des cartes, **paywall** (CTA d'essai, trois offres), carte « Répartition du temps » (légende), carrousel des Miffs, compte à rebours, fenêtre d'avis, écran des couleurs, saisie du code secret, Réglages (sous-titres de lignes), écran d'avertissement de la localisation. **Règle [Proposée]** : les mots longs peuvent passer à la ligne ou se couper proprement (césure automatique activée sur Android et iOS pour l'allemand), jamais être tronqués avec « … » quand le sens se perd (un nom de bouton ne se tronque pas). Si un libellé ne tient pas, on **raccourcit la traduction** avant de toucher à la mise en page.

### 8.4 Autres
- **Ton** : voir D2, D8 et critère 18. Le tutoiement allemand (« du ») et portugais européen (« tu ») est naturel entre proches ; il reste **chaleureux, non familier à l'excès**.
- **Guillemets** : français « … » ; allemand „…“ ; espagnol et italien « … » ou “…” selon le glossaire ; portugais « … » ou “…” : fixé au glossaire, **jamais mélangé**.
- **Espaces avant la ponctuation haute** : le français met une espace insécable avant `? ! : ;` ; **les quatre autres langues n'en mettent aucune**. Contrôle : le script signale toute espace insécable devant `? ! : ;` dans es/it/pt/de.
- **Accessibilité** : les textes `*_a11y` (lecteur d'écran) sont traduits comme les autres, en phrases naturelles à lire.
- **Photos de démonstration et captures** : prénoms inventés déjà neutres (Thomas, Amandine, « Maman ») ; pour les captures par langue, adapter « Maman » (« Mamá », « Mamma », « Mãe », « Mama ») dans le jeu de démonstration.

---

## 9. Qualité sans traducteur natif ni service payant

Processus [Proposé] (par langue, les quatre en parallèle une fois le glossaire figé) :

1. **Glossaire et guide de ton figés** (lot 2), relus par le PM sur le **français et les équivalents en anglais** (ce que le PM peut juger : le sens des termes, pas la langue cible).
2. **Traduction** par un agent, **à partir du français ET de l'anglais** (les deux sources se contrôlent l'une l'autre), en suivant le glossaire ; pluriels écrits complets ; fichier d'échange.
3. **Contrôles automatiques** (critères 2 à 4) : la CI refuse les erreurs mécaniques.
4. **Relecture croisée par un second agent indépendant** (contexte neuf) : il relit chaque texte avec le français, le glossaire et la traduction, signale sens, ton, grammaire, termes hors glossaire, longueurs ; ses remarques sont **appliquées ou refusées par écrit**. Pour les textes sensibles (consentement, localisation, `InfoPlist`, notifications, alerte danger, erreurs), une **rétro-traduction** en français par un troisième regard permet de repérer les glissements de sens.
5. **Revue sécurité et confidentialité** du ton sur les textes qui touchent à la position (CLAUDE.md : consentement explicite).
6. **Recette PM sur appareil** : le PM ne lit pas ces quatre langues. Il vérifie la **mise en page** (rien de coupé, rien de brut), le **fonctionnement du changement de langue**, la cohérence des écrans, pas la justesse linguistique.
7. **Après la sortie** : la porte de retour est le formulaire d'avis et l'adresse de contact ; un message d'un utilisateur natif qui signale une faute se corrige comme un ticket ordinaire (un texte = une clé = une ligne).

**Limite écrite** : ce processus **réduit** les fautes, il ne les supprime pas. Voir R1 au §11.

---

## 10. Plan de découpage en lots, ordre et dépendances

Principe : **ne jamais traduire en masse tant que des PR ajoutent des clés**, sinon chaque nouvelle clé crée un trou à rattraper et le catalogue iOS (un seul fichier JSON de plus de 1 000 entrées) génère des conflits à chaque PR. D'où : tout ce qui ne touche pas aux ressources d'abord ; la masse ensuite, **sur un instantané où les PR en cours sont fusionnées** ; puis la règle permanente.

| Lot | Contenu | Dépend de | Conflits avec PR ouvertes | Taille |
|---|---|---|---|---|
| **0** | Ce ticket + questions PM | (rien) | aucun (un fichier docs) | fait |
| **1. Outillage et parité N langues** | Registre des langues, extension de `check-i18n-parity.mjs` et de ses tests, mise à jour de `i18n-keys.md`, statut `planned` (**avertissements seulement**), test de cohérence des sources (critère 6). Aucune ressource de langue ajoutée | lot 0 validé | quasi nul (scripts + un contrat) | S |
| **2. Glossaire et guide de style** | `docs/i18n/glossaire.md` (§15 complété), tutoiement, ponctuation, tournures des phrases assemblées (§8.1 et §8.2), termes proscrits par langue ; **décision sur le second jeu de noms de lieux si nécessaire** ; relu par le PM sur le français | lot 0 ; parallèle au lot 1 | aucun (docs) | M |
| **3. Prérequis de code (Android puis iOS)** | Liste des langues pilotée par le registre (énumérations, `LanguageScreen` / `LanguageView`), `localeFilters`, table de langues de la recherche d'adresse, tests de contenu en boucle sur toutes les langues, remplacement de la capture « allemand = repli », éventuel second jeu de noms de lieux (6 langues, §8.1), césure automatique allemande, page web de réinitialisation (structure). **Une PR par plateforme**, jamais de push direct sur `main` | lots 1 et 2 ; **#218 (iOS langue), #222 / #224 (À propos), #244, #259 fusionnées d'abord** | **Élevé** sur `SettingsScreens.kt`, `LanguageView.swift`, `FeedTexts` : on attend leur fusion | M |
| **4. Traduction en masse (instantané)** | Une fois les PR du lot 3 fusionnées : **gel court des nouvelles clés** (quelques jours), traduction du fichier d'échange des 1 107 clés communes pour les 4 langues, puis des 43 + 84 + 4 propres ; relecture croisée ; fusion par script dans `values-*` (Android) et les catalogues (iOS). **Android d'abord (une PR), iOS ensuite (une PR)**, langues passées en `required` à la fusion de chaque plateforme | lot 3 fusionné | **Maîtrisé** par le gel : les fichiers `values-es` etc. sont nouveaux ; le catalogue iOS est modifié par un script en une seule PR courte, rebasée au dernier moment | L |
| **5. Page web de réinitialisation, e-mails Firebase** | 6 langues de `reset-strings.js` (+ test), textes d'e-mails à coller par le PM | lot 4 (textes sources stables) | aucun | S |
| **6. Notifications et textes système** | Vérification en test du rendu des deux clés `notif_circle_update_*` (4 langues), `InfoPlist` (déjà dans le lot 4 pour les clés ; ici les tests et captures), **aucun changement serveur** ; consigne de la limite « push iOS = langue du téléphone » | lot 4 | aucun | S |
| **7. Fiches des stores / ASO** | Titre, sous-titre, texte promotionnel, mots-clés, description, légendes de captures dans les 4 langues pour les deux stores ; script de limites de caractères ; mots-clés **par langue** à partir de la recherche documentaire, **sans** donnée de volume de recherche (aucun outil payant : limite écrite) ; **préparation uniquement**, aucune soumission | lot 4 ; décision du PM sur les pays de distribution | aucun (docs `marketing/`) | M |
| **8. Recette PM** | Checklist `docs/qa/checklists/traduction-recette-pm.md` ; test sur les deux téléphones dans les 4 langues, changement à chaud, `pt-BR` / `es-MX` si possible, grande police en allemand ; corrections par petites PR | lots 4 à 6 | aucun | M |
| **9. Règle permanente** | À la fusion du lot 4, les 4 langues passent en `required` : toute PR qui ajoute une clé doit l'ajouter dans les **6 langues** (le job échoue sinon). Chaque agent de fonctionnalité (`android-dev`, `ios-dev`) traduit selon le glossaire ; relecture croisée allégée (contrôles automatiques + revue de code) | lot 4 | s'applique à toutes les PR suivantes | continu |

**Pendant l'attente du lot 4**, les PR de fonctionnalité continuent comme aujourd'hui (français + anglais) : le script ne les bloque pas (statut `planned`). Au gel, l'agent du lot 4 rattrape les clés arrivées entre-temps.

**Interactions à surveiller** : ticket **Alerte danger** (nombreux textes et numéros d'urgence : mieux vaut qu'il précède le gel, sinon ses clés iront en lot 9) ; **ticket Premium / paywall** (déjà fusionné sauf PR) ; **#121 (signature TestFlight)** sans lien.

---

## 11. Risques et risques assumés

### 11.1 Les cinq risques principaux

| # | Risque | Gravité | Parade |
|---|---|---|---|
| **R1** | **Qualité de langue sans relecture par un natif** : fautes, maladresses, ton qui devient froid ou alarmiste, faux amis, tutoiement mal dosé. Particulièrement grave sur les textes de **localisation et de consentement** (confiance, motif de refus des stores) | Élevée | Glossaire figé, deux agents indépendants, rétro-traduction des textes sensibles, contrôles automatiques de ton. **Risque ASSUMÉ par le PM** (voir 11.2) |
| **R2** | **Phrases assemblées et heures avec article** (§8.1, §8.2) : grammaire fausse dans l'une des langues (« a la 16:02 », cas de l'allemand) sans que la CI le voie | Élevée | Tournures neutres au glossaire (lot 2), test des 24 heures, second jeu de noms de lieux si nécessaire |
| **R3** | **Textes légaux et de consentement** : les CGU, la politique de confidentialité et la page de suppression restent en FR+EN ; une personne germanophone, hispanophone, italophone ou lusophone accepte des conditions qu'elle lit en anglais. Le RGPD demande une information compréhensible ; l'Allemagne attend aussi une identification de l'éditeur (mentions d'éditeur) pour une offre visant son marché **[connaissance générale, non vérifiée juridiquement ; pas de relecture juridique, risque assumé]** | Moyenne à élevée | Les écrans de consentement **dans l'app** sont, eux, traduits ; éditeur et contact déjà publics (décision du 2026-10-24) ; choisir les pays de distribution au lancement (§14, Q2) |
| **R4** | **Dérive entre les deux plateformes et trous de traduction** (une clé ajoutée dans un seul catalogue ou dans une seule langue ; le catalogue iOS est un fichier monolithique qui entre en conflit avec chaque PR) | Moyenne | Traduction des communs **une seule fois** puis fusion par script ; gel court ; règle permanente et job de parité N langues ; statut `planned` / `required` |
| **R5** | **Débordement visuel en allemand** (boutons, onglets, bulles, paywall) et grandes polices ; invisible tant qu'on ne regarde pas | Moyenne | Budgets de longueur par clé, captures allemandes à grande police sur Android (Robolectric) et iOS (CI), césure automatique, raccourcir la traduction avant la mise en page |

### 11.2 Risques assumés (écrits comme tels, pas « à valider »)
- **R1 assumé** : **aucune relecture par un locuteur natif** de es, it, pt, de ; le PM l'a accepté en déléguant ce chantier. Les textes seront publiés sous sa responsabilité, sans prétendre à une relecture native.
- **Pas de relecture juridique** (décision du 2026-10-23) : les traductions des écrans de consentement, comme les textes légaux, ne sont **jamais présentées comme relues par un juriste**.
- **R3 assumé** : textes légaux et page de suppression en FR+EN seulement.
- **Brésil** : un appareil en `pt-BR` affiche le portugais du Portugal.
- **iOS** : les notifications push suivent la langue du téléphone, pas le choix dans l'app.
- **Prédiction de longueur** : l'allongement allemand est estimé, pas mesuré, jusqu'aux captures du lot 4.

### 11.3 Autres points notés
- Les **mots-clés ASO** par langue reposent sur du jugement, sans outil de volume de recherche (aucun outil payant) : peu fiables, à réviser avec les statistiques réelles des stores.
- **Photon** (recherche d'adresse) : langues limitées [À vérifier] ; l'adresse proposée peut être dans une autre langue que celle de l'app (sans gravité, ce sont des noms de rues).
- **Firebase** : liste des langues de modèles d'e-mail [À vérifier en console].
- **Pas de nouvelle donnée personnelle** : aucune langue stockée côté serveur, aucun paramètre de langue dans Analytics.

---

## 12. Coûts

**0 €.** Traduction par les agents (déjà dans l'outil du projet), aucune API de traduction, aucun logiciel de traduction payant, aucun relecteur payant, aucune fonction serveur de plus (donc rien sur la facture Blaze), aucun appel Firestore de plus (la langue n'est pas stockée), aucune CI de plus que celle déjà utilisée (le job de parité tourne quelques secondes ; les captures CI iOS ajoutées allongent légèrement les minutes macOS déjà budgétées : **à surveiller**, voir `docs/dev/surveillance-quotas.md`). Aucun achat, aucun compte : conforme à la règle d'or du projet. **Seul point à signaler** : les minutes macOS de la CI iOS si l'on multiplie les captures par langue (garder un petit nombre d'écrans par langue, l'allemand en priorité).

---

## 13. Ce que le PM devra faire (une fois les lots livrés)

1. **Console Firebase** (projet `miff-3d283`) : Authentication > Modèles > « Réinitialisation du mot de passe » > pour chaque langue disponible, coller l'objet et le corps fournis par l'agent (lot 5) ; vérifier que le lien d'action reste `https://miff-3d283.web.app/reset-password` (déjà fait pour fr et en).
2. **Captures d'écran** pour les stores : une série par langue (4) et par plateforme, sur les téléphones de test réglés dans la langue (l'agent fournit la liste des écrans et les légendes ; aucune capture n'est faite par un agent sur ses propres données).
3. **Tests sur appareil** (recette du lot 8) : sur l'iPhone 11 Pro et l'OPPO Find X5 Lite, passer la langue du téléphone en espagnol, italien, portugais (Portugal), allemand, **puis changer la langue depuis Réglages > Langue de l'app** ; regarder : rien de coupé, pas de texte brut, notification de cercle reçue, retour à « Langue du téléphone ». Si possible une fois en portugais du Brésil et en espagnol du Mexique (réglages du téléphone), et un test avec la plus grande taille de police en allemand.
4. **Fiches des stores** : relire les fiches préparées (légendes et nombre de caractères), puis les soumettre lui-même (aucune soumission par un agent).
5. **Répondre aux questions ci-dessous.**

---

## 14. Questions pour le PM (4 au plus, chacune avec un défaut)

| # | Question | Défaut si pas de réponse |
|---|---|---|
| **Q1** | **Textes légaux** : acceptez-vous que les CGU, la politique de confidentialité et la page de suppression de compte restent en **français et anglais seulement** (les personnes es/it/pt/de les lisent en anglais), les écrans de consentement dans l'app étant, eux, traduits ? Les traduire tous représenterait ≈ 19 000 mots de plus par langue, sans relecture juridique. | **Oui, FR + EN seulement** (risque R3 assumé) |
| **Q2** | **Pays de distribution** : au lancement, ouvrez-vous les stores aux pays de ces langues (Espagne, Italie, Portugal, Allemagne, Autriche, Suisse…) ou reste-t-on sur les pays déjà prévus avec les langues disponibles « en plus » pour les téléphones concernés ? (Cela fixe la priorité de la fiche store traduite, et l'importance des mentions d'éditeur pour l'Allemagne.) | **Langues disponibles dans l'app dès qu'elles sont prêtes ; fiches traduites préparées ; ouverture des pays décidée au lancement** |
| **Q3** | **Livraison** : les quatre langues **ensemble** dans une même version (Android et iOS), ou langue par langue (par exemple l'allemand d'abord) ? | **Ensemble** ; le statut par langue permet de retirer une langue qui échoue à la recette sans bloquer les autres |
| **Q4** | **Relecture volontaire** : avez-vous dans votre entourage des personnes (gratuitement, sans engagement) capables de lire quelques écrans en es, it, pt ou de ? Cela **ne remplace pas** le processus, ça le complète. | **Non, risque R1 assumé** ; si oui, le glossaire sert de grille et leurs remarques passent comme des tickets ordinaires |

Rappel : le **portugais du Portugal** (`pt`) et **l'espagnol neutre** (`es`) sont les défauts du §5 ; vous n'avez pas à choisir, sauf si vous voulez le portugais du Brésil d'abord (alors le défaut s'inverse et la liste des priorités change : à me dire).

---

## 15. Glossaire de départ (à compléter et figer au lot 2)

**Aucune de ces propositions n'a été relue par un natif.** Elles servent de point de départ à l'agent du lot 2, qui les confirme après lecture complète des 1 238 textes. Marque, noms d'écran et formes d'adresse sont **les mêmes que dans l'app française** ; en cas de doute : suivre l'anglais de l'app.

| Français (source) | Anglais (app) | Espagnol `es` | Italien `it` | Portugais `pt` (Portugal) | Allemand `de` |
|---|---|---|---|---|---|
| **Miff** (nom de l'app et du groupe) | Miff | Miff | Miff | Miff | Miff |
| une Miff (un groupe) | a Miff | una Miff (« tu Miff ») | una Miff (« la tua Miff ») | uma Miff (« a tua Miff ») | eine Miff (« deine Miff ») |
| **Miff+** (offre payante) | Miff+ | Miff+ | Miff+ | Miff+ | Miff+ (nom de marque, jamais traduit) |
| cercle (de confiance) | circle | círculo | cerchia | círculo | Kreis |
| tes proches | your loved ones | tus seres queridos | i tuoi cari | os teus entes queridos | deine Liebsten |
| lieu de confiance | trusted place | lugar de confianza | luogo di fiducia | local de confiança | vertrauter Ort |
| zone de danger | danger zone | zona de peligro | zona di pericolo | zona de perigo | Gefahrenzone |
| Hors-zone | Out of zone (à confirmer avec l'app) | Fuera de zona | Fuori zona | Fora de zona | Außerhalb |
| donner des nouvelles / update | share news (à confirmer avec l'app) | dar noticias | dare notizie | dar notícias | Neuigkeiten teilen |
| code secret | secret code | código secreto | codice segreto | código secreto | Geheimcode |
| partager ma position | share my location | compartir mi ubicación | condividere la mia posizione | partilhar a minha localização | meinen Standort teilen |
| arrêter le partage | stop sharing | dejar de compartir | smettere di condividere | deixar de partilhar | Teilen beenden |
| membre | member | miembro | membro | membro | Mitglied |
| Feed (onglet) | Feed | Feed (à confirmer) | Feed (à confirmer) | Feed (à confirmer) | Feed (à confirmer) |
| orange / rouge (couleurs d'état) | orange / red | naranja / rojo | arancione / rosso | laranja / vermelho | Orange / Rot |
| mode voyage | travel mode | modo viaje | modalità viaggio | modo viagem | Reisemodus |
| essai gratuit | free trial | prueba gratuita | prova gratuita | teste gratuito | kostenlos testen |
| notifications | notifications | notificaciones | notifiche | notificações | Benachrichtigungen |
| se connecter / se déconnecter | sign in / sign out | iniciar sesión / cerrar sesión | accedi / esci | iniciar sessão / terminar sessão | Anmelden / Abmelden |
| supprimer mon compte | delete my account | eliminar mi cuenta | elimina il mio account | eliminar a minha conta | Konto löschen |
| téléphone (appareil) | phone | teléfono | telefono | telemóvel | Telefon |
| écran | screen | pantalla | schermata | ecrã | Bildschirm |
| plus de nouvelles depuis 16 h (ton) | no news since 4 pm | sin novedades desde las 16:02 → **formule sans article** (§8.2) | nessuna novità da 16:02 | sem novidades desde 16:02 | Seit 16:02 Uhr nichts Neues |

**Règles de ton à figer par langue** : chaleureux, rassurant, jamais moralisateur ni alarmiste, y compris erreurs et notifications ; **pas de point d'exclamation d'alerte** ; pas de formule de reproche (« tu n'as pas… ») ; verbes d'action simples. Exemples de **tournures à proscrire** à lister au lot 2 par langue : formules de peur (« danger imminent », « urgence », « attention ! » hors écran d'alerte danger), reproche (« tu as oublié »), culpabilisation du proche.

---

## 16. Hors-scope et suites possibles
`pt-BR`, néerlandais, autres langues ; traduction des textes légaux (avec une relecture, si le PM change d'avis) ; envoi de la langue au serveur pour un push iOS dans la langue de l'app (à ne faire qu'avec une décision de confidentialité) ; outil de volume de recherche pour l'ASO ; vérification par un natif (service ou volontaire) ; catalogue d'urgence étendu aux pays des nouvelles langues (ticket Alerte danger).

*Sources lues* : `CLAUDE.md`, `docs/contracts/i18n-keys.md`, `docs/tickets/initialisation-technique.md` (§4.F), `docs/tickets/alerte-danger.md` (§8.4), `docs/tickets/feed-profil-reglages.md`, `docs/contracts/place-categories.md`, `backend/data-model.md`, `backend/functions/src/notif*.ts`, `scripts/check-i18n-parity.mjs`, `.github/workflows/hygiene.yml`, `android/app/build.gradle.kts`, `AppLanguage.kt`, `LanguageStore.kt`, `ios/project.yml`, `Localizable.xcstrings`, `InfoPlist.xcstrings`, `ios/ci/screenshots.sh`, `web/src/*`, `marketing/*`, Notion (« Inscription », « Paramètre »), et la branche `ios/reglages-langue` (PR #218).
