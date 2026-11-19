# Ticket : texte de consentement à la localisation et journal local du temps (risque J65), version 4 du consentement (CONS-4)

**Statut** : **proposition de l'agent `product-specs`, avec défauts recommandés** ; le PM a délégué la rédaction, seules les questions du §11 attendent une réponse. Aucun développement lancé, aucun statut Notion modifié, aucun coût, aucun déploiement.
**Rédigé le** : 2026-11-19, sur `origin/main` au commit `424e858a` (PR #278, textes légaux 1.1). Copie de travail propre `C:\dev\miff-docs-consentement` ; le dossier `C:\dev\miff` local est périmé et la copie `C:\dev\miff-main-ro` est un cran en arrière (`c492e250`, PR #246) : elles n'ont pas servi de référence.
**Demande** : corriger l'écart **J65** (`docs/legal/README.md` §4) : le texte de consentement affiché dans l'app (« version 3 ») dit « pas d'historique de trajets » / « pas d'historique », alors que l'app tient, depuis le graphique « Répartition de ton temps », un **journal local de présence** (35 jours, sur le téléphone, jamais envoyé, sans coordonnée ni nom de lieu, exclu des sauvegardes), et que les textes légaux 1.1 précisent « sur ses serveurs ».

**Convention** : **[Mesuré]** = relevé dans le code ou les documents de `origin/main`, avec fichier et ligne. **[Proposé]** = défaut recommandé par cette spec. **[À vérifier]** = connaissance générale du droit ou des règles des stores, à confirmer sur la source avant de s'y fier. **Aucun texte de ce document n'a été relu par un juriste** (décision du PM du 2026-10-23 : risque assumé, jamais présenté autrement).

---

## 1. Résumé pour le PM

1. **Le problème est réel mais petit, et il est double.** (a) Six textes de l'app disent « pas d'historique » de façon absolue, alors qu'un journal local existe (§3). (b) En vérifiant, on a trouvé un **second écart, plus ancien** : le texte de consentement v3 **tel qu'il est dans les apps ne cite pas l'endroit de la photo**, alors que la politique publiée (art. 3 et 4) dit que la version 3 « les décrit ». L'amendement du 2026-11-06 n'a jamais été appliqué aux chaînes `miff_consent3_*` (§3.2).
2. **Recommandation : option D, une nouvelle version `2026-11-v04` du consentement**, qui corrige les deux écarts d'un coup, **sans bloquer personne** pour la position et le statut (les comptes en v3 continuent comme avant, un écran calme leur demande de « confirmer »), et qui **exige la v4 seulement avant de publier une photo** (§5, §6).
3. **Pourquoi pas seulement corriger le texte (A et C)** : notre propre règle dit « un texte modifié = un numéro de plus, jamais deux textes sous la même version » (`consentement-localisation-v3.md` §2). Garder « v03 » pour un texte différent détruit la preuve de ce que la personne a vu (RGPD art. 7 §1 : savoir démontrer le consentement). L'exception du 2026-11-06 reposait sur « personne d'extérieur n'a accepté la v3 » : **non vérifiable par l'agent** et fausse dès qu'un testeur a accepté (TestFlight en est au build 38).
4. **Pourquoi pas une re-demande bloquante pour tous (B)** : le traitement côté serveur ne change pas (une dernière position, effacée à l'arrêt) ; on **précise** l'information, on n'ajoute pas de finalité. Bloquer la position de chaque testeur pour une précision serait disproportionné et contraire à « parcours courts, peu de friction ».
5. **Coût** : petit à moyen. 2 PR de code (Android, iOS) de taille voisine, 1 PR règles (une ligne, un test, une mutation), 1 PR documents (textes légaux 1.2, stores, i18n). **0 € de service.** Traduction es/it/pt/de : à faire **après** ce ticket, pas avant (§9).
6. **Questions pour le PM : 3, chacune avec son défaut** (§11). Rien ne bloque la rédaction ni la revue.

---

## 2. Ce que dit le texte aujourd'hui : audit des endroits (où figure « historique » / « trajets »)

### 2.1 Textes affichés DANS les apps (Android et iOS, FR et EN) [Mesuré]

| # | Clé (même clé sur Android et iOS) | Écran | Texte actuel (FR) | Android `values-fr` / `values` | iOS `Localizable.xcstrings` | Écart |
|---|---|---|---|---|---|---|
| 1 | `miff_consent3_kept_body` | **Écran de consentement** avant « Créer et partager » / « Rejoindre et partager » / « Reprendre le partage » (carte `SharingConsentCard`, `ConsentCards.kt:115`, `ConsentCards.swift:109`) | « Miff garde seulement ta dernière position connue, avec l'heure de la mesure. **Pas d'historique de trajets** : chaque position remplace la précédente. Ce n'est pas un suivi en direct. » | `strings.xml:798` / `:819` | `:16202` | **Écart J65 principal** : énoncé général, faux dès qu'on tient compte du journal local |
| 2 | `miff_renewal_intro_one` / `_unnamed` / `_many` | **Écran de re-consentement** (comptes en v1 ou v2) | « … Pas d'historique, jamais en direct, et elle est effacée si tu arrêtes de partager … » | `:811` à `:813` / `:832` à `:834` | `:16423`, `:16440`, `:16457` | idem |
| 3 | `permission_location_seen_body` | **Écran de divulgation de la localisation** (Android : « prominent disclosure » avant l'invite système, `PermissionScreens.kt:358` ; iOS : `PermissionScreens.swift:378`) | « … ta dernière position connue, avec l'heure. **Pas d'historique.** » | `:777` / `:798` | `:15896` | idem. **C'est aussi le texte que Google Play lit comme divulgation** |
| 4 | `settings_privacy_location_body` | Réglages > Confidentialité > Localisation (`LocationPrivacy.kt:106`, `AccountAndPrivacyViews.swift:151`) | « … **Aucun historique de trajets n'est gardé.** … » | `:534` / `:557` | `:15318` | idem ; **pas un texte de consentement** : se corrige sans re-consentement |
| 5 | `settings_privacy_time_title` / `_body` / `_erase` | Réglages > Confidentialité > « **Historique de temps** » (`PrivacyScreen.kt:154`, `AccountAndPrivacyViews.swift:183`) | « … ton téléphone garde pendant 35 jours les moments où tu arrives dans un de tes lieux et où tu en pars, sans position ni adresse. Rien n'est envoyé : personne d'autre ne peut les voir. » | `:1267` à `:1270` / `:1286` à `:1289` | `:15488`, `:15590` | **Exact**, mais le mot « historique » y désigne le journal local alors que ailleurs « pas d'historique » : c'est la contradiction visible de l'utilisateur |
| 6 | `NSLocationAlwaysAndWhenInUseUsageDescription` (texte exact affiché par iOS à la demande « Toujours ») | Fenêtre du système iOS | « … Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue, avec l'heure, **sans historique**. … » | (sans objet) | `InfoPlist.xcstrings:38`, valeur de repli anglaise dans `ios/project.yml:131` | Exact pour ce que **voient les membres**, trop absolu en lecture seule ; le journal local n'est pas cité |
| 7 | `NSLocationWhenInUseUsageDescription` | Fenêtre du système iOS (« Pendant l'utilisation ») | pas de mot « historique » | (sans objet) | `InfoPlist.xcstrings:55`, `project.yml:130` | **Aucun écart** : inchangé |

Textes **sans écart** : `permission_location_disclosure` (phrase obligatoire de la divulgation Google, `strings.xml:773` / `:794`, `PermissionScreens.kt:322`, `Localizable.xcstrings:15828`) ; `permission_location_control_body`, `permission_location_closed_body` ; `miff_consent3_who_body`, `_closed_body`, `_members_body`, `_places_body`.

Remarque : la clé de version `consent3` désigne le **préfixe historique** des chaînes, pas la version du texte (MiffSharingConsent.VERSION).

### 2.2 Autres endroits où « historique » / « trajets » figure [Mesuré]

| Endroit | Texte | Statut |
|---|---|---|
| Politique de confidentialité 1.1 (`docs/legal/politique-confidentialite.fr.md` et `.en.md`) : résumé point 1 (l. 9), art. 3 (l. 43, 102), art. 4 (l. 112, 117, 131 à 139, 152 à 153), art. 7 (l. 213, 239) | « sur ses serveurs » / « sur ton téléphone seulement » | **Alignés** (J48, §12 du README) |
| CGU 1.1 art. 6 et 10 | idem | Alignés |
| Page d'accueil `web/src/landing.json` : `step2Text`, `b3Text`, `t4Title`, `a1` (FR l. 36, 52, 64, 99 ; EN l. 158, 174, 186, 221) | « sur ses serveurs » | **Alignés** le 2026-11-19 |
| Fiches des stores `marketing/store-listing-android.md` (l. 53, 128) et `store-listing-ios.md` (l. 81, 161, 235) | « Miff ne garde aucun historique de tes trajets sur ses serveurs » | **Alignés** ; ligne 211 / 235 d'`ios` : « sans historique » dit de la dernière position (exact pour les serveurs) |
| `marketing/launch-plan.md` (l. 16, 159, 183), `marketing/positioning.md` (l. 23, 55, 108, 128, 151) | « sans historique », « jamais l'historique de ses trajets » | **Pas alignés** (« à reformuler à leur prochaine révision », `journal-local-temps-declarations.md` §5) ; exacts pour les serveurs |
| `docs/release/google-play-localisation-arriere-plan.md` : justification du formulaire Play (§4, « no history »), divulgation (§5 ligne 2 : « Pas d'historique. ») | idem | À réaligner avec le texte v4 |
| `docs/release/app-store-confidentialite-et-revue.md` : textes d'usage (§4, l. 66 à 67), note de revue (§5, l. 97 : « No history of trips. ») | idem | À réaligner (`journal-local-temps-declarations.md` §1 propose déjà le remplacement) |
| `docs/legal/consentement-localisation-v3.md` (§3, ligne « ce qui est enregistré ») | « Pas d'historique de trajets : chaque position remplace la précédente. » | Texte source de la v3 ; à **archiver** et remplacer par `consentement-localisation-v4.md` (§7) |
| `docs/i18n/chaines-reference.md` (S07, S10, …), `docs/i18n/glossaire.md` (G58, G70) | le texte actuel + traductions proposées | À mettre à jour **avant** la traduction (§9) |
| `ios/MiffTests/Permission/PermissionPrivacyTests.swift:266` | le test **exige** « Pas d'historique » dans `permission_location_seen_body` | À adapter dans la PR iOS |

**Le mot « historique » est une obligation de la charte i18n** (`docs/i18n/guide-de-style.md`, règle 10 et contrôle l. 275 : les textes `permission_location_*` et `miff_consent3_*` doivent garder les « quatre précisions » : dernière, heure, **historique**, arrière-plan ; glossaire G58 : la formulation doit « dire » l'absence d'historique, pas la contredire). Donc **on ne supprime pas** « pas d'historique » : on le **précise** (« sur nos serveurs »).

### 2.3 Le journal local, ce qu'il est vraiment (pour ne rien affirmer de faux) [Mesuré]

- Un fait par **changement** de présence : Miff, état (lieu de confiance, zone de danger, ailleurs, non mesuré), **identifiant technique** du lieu, instant. **Aucune coordonnée, aucun nom, aucune adresse, aucun identifiant de personne** (`PresenceLog.kt:9-13` et `:24-30` ; iOS `PresenceHistory.swift`).
- **35 jours glissants** (`StoredPresenceLog.kt:20`, `:118` ; iOS `PresenceHistoryFileStore.swift:11`, `:84`), au plus 60 faits par jour.
- **Jamais envoyé** : aucune lecture ni écriture Firestore, aucun événement Analytics, aucune ligne de journal technique (`PresenceLog.kt:9-13`).
- **Exclu des sauvegardes** : Android `backup_rules.xml:4-8` et `data_extraction_rules.xml:8-12` (tous les domaines exclus) ; iOS `PresenceHistoryFileStore.swift:228` (`isExcludedFromBackup = true`).
- **Effacé** : déconnexion, suppression de compte, départ de la Miff (pour cette Miff), bouton « Effacer mon historique de temps » (`PresenceLog.kt:52-80`). **Pas effacé à l'arrêt du partage** (décision D16 ; politique art. 4 l. 137 et art. 7 l. 239).
- **Il existe sur les deux plateformes dans `main`** : Android (PR #260) et iOS (PR #259, `Services/TimeShare/`, `Views/Profile/TimeShareCard.swift`). *Note* : `docs/legal/README.md` §12 et J48 écrivent encore « sur iPhone, il n'est pas encore dans `main` » : **périmé**, à corriger dans la PR légale (§8).

---

## 3. Le « consentement v3 » : ce que c'est techniquement [Mesuré]

### 3.1 Où il est enregistré

- **Un champ par membre et par Miff** : `miffs/{miffId}/members/{uid}.sharing = { enabled, consentedAt, consentVersion, revokedAt }` (`backend/data-model.md:554-564`). Écrit dans **le lot d'entrée** de « Créer et partager » / « Rejoindre et partager » (`data-model.md:920`), avec `consentedAt` = **heure du serveur** (règle `validSharingCreate`, `backend/firestore.rules:364-372`) et `consentVersion` = la constante de l'app (Android `MiffSharingConsent.VERSION = "2026-11-v03"`, `MiffModels.kt:110` ; iOS `MiffSharingConsent.version`, `MiffModels.swift:116`).
- **Un consentement par Miff**, pas par compte. Il se **retire** en arrêtant le partage : `enabled=false`, `revokedAt`, et suppression du statut, de la photo, de la position et de l'alerte dans le même lot (`firestore.rules:376-411`, `data-model.md:915`).
- **Preuve conservée** : `consentedAt` + `consentVersion` **tant que le membre est dans la Miff** ; supprimée avec sa fiche (`data-model.md:1073`). Après un départ ou une suppression de compte, il ne reste **aucune preuve** (risque assumé, Q13 de `data-model.md`).
- **Limite connue** : la version est **déclarée par l'app** ; aucune règle ne prouve quel texte a été affiché (`firestore.rules:346-348`). La protection est la constante partagée, la revue de code et la QA.

### 3.2 Ce que les règles font de la version

| Règle | Seuil | Ligne |
|---|---|---|
| Écrire la **dernière position** | `isPositionConsentVersion` : `AAAA-MM-vNN`, **NN de 03 à 99** | `firestore.rules:349-351`, `:795` |
| Écrire une **photo** (donc son endroit) | `isPhotoLocationConsentVersion` : fonction **dédiée**, aujourd'hui le même seuil (03 à 99), prévue pour « pouvoir évoluer seule » | `:357-359`, `:820` |
| **Renouveler** un consentement actif (R-LIEU-3) | nouvelle version de largeur fixe, **strictement supérieure**, jamais sortie de la couverture position, `consentedAt` = heure serveur, aucune écriture annexe, 0 lecture de règles | `:376-411` ; `data-model.md:267`, `:914` |
| Créer / rejoindre | seulement « 1 à 20 caractères » : la règle ne vérifie **pas** que la version soit la courante | `:364-372`, `data-model.md:920` |

### 3.3 Ce qui se passe quand la version change (comportement actuel des apps)

- Au lancement, **une seule fois**, l'app lit les fiches de la personne et classe chaque Miff « à renouveler » si `consentVersion < VERSION` (`isRenewalDue`, `MiffModels.kt:134` ; `PermissionGateViewModel.kt:113-130`, `ConsentRenewals.kt:70-95`). Si le serveur ne répond pas, **rien n'est bloqué** (`Unknown`), la vérification se refait au lancement suivant.
- Un compte à renouveler voit l'**écran de re-consentement** (« Une précision sur ce que tu partages ») : « J'accepte » écrit la nouvelle version, « Pas maintenant » n'écrit rien et **l'écran revient au lancement suivant** ; « Quitter la Miff » est proposé aux membres.
- **Ce que l'app bloque réellement** : le suivi et l'écriture de la position ne sont permis que si `coversPosition(version)` (**NN ≥ 03**, pas « = version courante ») : `TrackingEngine.kt:619` et `:1612`, `PhotoStore.kt:79`. Conséquence : **un compte en v1 ou v2 est mis en pause ; un compte en v3 ne le serait pas** si la constante passait à v4, **mais l'interface le dirait** (pastille « En attente : Miff ne met plus à jour ton statut », `strings.xml:931-932`). **Ce décalage est un piège de l'option B** : il faut le traiter explicitement (§6.3).
- Comptes concernés aujourd'hui : **uniquement des comptes de test** (aucune app publiée sur un store ; des builds de test circulent : TestFlight 38 d'après le dépôt). Les comptes en v1 ou v2 sont des comptes d'avant le 2026-11-04.

### 3.4 Comment `termsVersion` (CGU « 1.0 ») diffère du consentement de localisation

| | CGU et politique | Consentement à la localisation |
|---|---|---|
| Champ | `users/{uid}.termsAcceptedAt` + `termsVersion` (Android `ConsentVersions.TERMS = "1.0"`, `Consent.kt:15` ; iOS `Consent.swift:9`) | `miffs/{miffId}/members/{uid}.sharing` |
| Quand | une fois, au clic sur « Rejoindre » ou « Continuer avec Google » | à chaque entrée dans une Miff (créer / rejoindre / reprendre) |
| Portée | le compte | **la Miff** |
| Modifiable ensuite | **non** : les règles gèlent `termsVersion` après la création (`firestore.rules:1107`, `:1111-1118`) : **aucun mécanisme de ré-acceptation** (risque J32) | **oui** : renouvellement R-LIEU-3 |
| Retrait | pas de retrait (supprimer le compte) | arrêt du partage, par Miff |
| Statistiques | `analyticsConsent` (version `2026-10-v01`, bloc séparé) | sans rapport |

**Conséquence pour ce ticket** : changer un texte légal 1.1 → 1.2 **ne se traduit par aucune ré-acceptation des CGU** (J32, assumé) ; seul le consentement de localisation sait se renouveler. C'est une raison de plus de faire passer la précision par la v4.

### 3.5 Le second écart découvert (photo localisée) [Mesuré]

- `docs/legal/consentement-localisation-v3.md` §1 bis, §3 et §7 : le texte v3 devait être **amendé** pour citer l'endroit de la photo (amendement du 2026-11-06), « à faire dans la PR UPDATE-1b ». **Les chaînes des apps ne l'ont pas reçu** : `miff_consent3_create` (« tu partages ton statut et ta dernière position connue avec ses membres », `strings.xml:794` / `:815`, `Localizable.xcstrings:16134`) et `miff_consent3_erase_body` ne parlent **que** de la position. L'endroit de la photo est dit **au moment de la photo** (aperçu de l'Update : « Publiée avec ta position. Ceux qui partagent aussi la leur peuvent voir où tu l'as prise », `values-fr/strings.xml:971` ; iOS : texte d'usage de la caméra, `InfoPlist.xcstrings:4`), ce qui **atténue** l'écart sans le fermer.
- La **politique 1.1** écrit pourtant : « Ta position n'est enregistrée qu'après ton accord à un texte de consentement de version 3 ou plus récente, qui dit clairement que les membres voient ta dernière position connue **et l'endroit de tes photos** » (art. 4 l. 151 ; art. 3 l. 47 : « un texte de version 3 ou plus récente **qui les décrit** »). **Pour la v3 telle que livrée, c'est inexact.** Le risque J37 écrit lui aussi « consentement qui la cite ».
- **À ajouter au registre des risques** : J66 (§8).

---

## 4. Principes de la solution (ce que le PM a demandé, traduit)

1. **Dire la vérité complète, en peu de mots** : ce qui est mesuré, ce qui est partagé, ce qui reste sur le téléphone, qui voit, comment arrêter ou effacer.
2. **Rassurer sans cacher** : le journal local est un **plus de transparence**, pas un aveu. Il se présente comme « pour toi seul(e) ».
3. **Ne pas surcharger l'écran de consentement** (faible charge cognitive) : **une ligne de plus**, courte, et une ligne retouchée ; rien d'autre ne bouge.
4. **Jamais** « suivi », « surveillance », « habitudes » dans les textes d'écran ni dans les fiches des stores (`journal-local-temps-declarations.md` §4, point 4).
5. **Jamais de claim de relecture juridique**, jamais « conforme » : les risques restent écrits comme assumés.

---

## 5. Les options

Légende des colonnes : **RGPD** = art. 7 (consentement démontrable, éclairé, spécifique) et art. 12 à 13 (information transparente) [À vérifier : lecture du texte du règlement, non confirmée par un juriste] ; **Apple** = guidelines 5.1.1 (collecte et stockage, textes d'usage « complets ») et 5.1.2 (utilisation) ; **Google** = règle « User Data » : divulgation en vue (« prominent disclosure ») exacte avant la demande de localisation en arrière-plan, et section « Data safety » exacte [À vérifier sur les pages Apple et Google avant soumission, lues pour la dernière fois le 2026-11-17 dans `journal-local-temps-declarations.md` §1 et §2].

| | **A. Corriger le texte seulement, version v03 inchangée** | **B. Version v4 + re-demande bloquante à tous** | **C. Corriger le texte + information non bloquante, sans nouvelle acceptation** | **D. v4 + confirmation non bloquante (position) + v4 exigée pour la photo (recommandée)** |
|---|---|---|---|---|
| Principe | on réécrit `miff_consent3_*` et on garde « 2026-11-v03 » | constante `2026-11-v04` ; seuil de la position relevé à 04 ; comptes en v3 mis en pause jusqu'à acceptation | texte corrigé, constante inchangée ; une bulle « Ce qui change » (lecture seule) | constante `2026-11-v04` ; écran calme « Une précision » pour les comptes en v3 ; **seul l'endroit de la photo** exige la v4 |
| Preuve de consentement (RGPD 7 §1) | **dégradée** : « v03 » désigne deux textes (celui du build 38 et le nouveau) ; contraire à la règle du dépôt | **la meilleure** : chaque compte a la v4 ou est en pause | dégradée comme A (la bulle n'enregistre rien) | **bonne** : la v4 enregistrée = la personne a vu le texte exact ; v3 = ancien texte, connu et archivé |
| Information transparente (art. 12-13) | texte juste, mais les comptes existants ne savent pas qu'il a changé | juste, avec acceptation | juste, avec information | juste, avec information **et** acceptation facultative |
| Apple (5.1.1 / 5.1.2, textes d'usage) | texte d'usage à compléter ; risque faible | idem | idem | idem ; **la photo localisée est couverte par un accord au bon texte** |
| Google (divulgation, Data safety) | divulgation exacte ; Data safety inchangée (le journal n'est pas « collecté ») | idem | idem | idem |
| Risque de rejet par un store | faible (les stores lisent le texte courant, pas la version) | faible | faible | faible |
| Impact sur les comptes existants | aucun | **pause du partage** des comptes en v3 jusqu'au clic : friction, risque de refus, **inquiétude** (« Miff ne met plus à jour ton statut ») | aucun | un écran, **sans pause** de la position ; une demande d'accord avant la **prochaine photo** |
| Coût | le plus bas (chaînes seules) | **le plus haut** : règles (seuil position), déploiement dans le bon ordre, traitement du décalage « pause affichée / pas de pause réelle » (§3.3), tests des deux plateformes | bas (chaînes + une bulle) | moyen : chaînes, constante, un écran réutilisé, un seuil de règle pour la photo |
| Cohérence avec la politique 1.1 (« version 3 ou plus récente qui les décrit ») | **fausse** pour l'endroit de la photo (v3 ne le dit pas) | vraie | **fausse** pour l'endroit de la photo | **vraie** (la photo exige la v4) |
| Cohérence avec la règle « un texte = un numéro » | **violée** | respectée | **violée** | respectée |

**Option D, défaut recommandé, et pourquoi** :
1. C'est la seule qui rend **vraie** la phrase publiée de la politique sur la photo, sans friction pour la position.
2. Elle respecte la règle de versionnement du dépôt et **garde la preuve exploitable** (une v3 archivée dit exactement ce que ces personnes ont vu).
3. Elle n'inquiète personne : « Tout continue comme avant » est **vrai** pour la position et le statut. La seule chose qui demande un accord est la publication d'une photo, un geste volontaire, où la phrase « Pour partager une photo avec ta position, ton accord au nouveau texte est nécessaire » **existe déjà** (`values-fr/strings.xml:980`).
4. **Si le PM préfère B** : possible, mais il faut alors relever le seuil de la position à 04 **après** que les builds v4 soient chez les testeurs (sinon des builds v3 se font refuser leurs écritures sans comprendre), et corriger la pastille « En attente ». Plus risqué pour un gain de preuve minime ici.
5. **Si le PM choisit A ou C** : il doit écrire qu'**aucune personne extérieure** n'a accepté la v3 telle que livrée et que les comptes internes ne comptent pas comme preuve (même formule que `consentement-localisation-v3.md` §1 bis). Ni l'agent ni ce ticket ne peuvent le vérifier (Play Console et TestFlight ne sont pas accessibles).

---

## 6. Spécification de l'option D

### 6.1 Règles de version

1. **Nouvelle constante `2026-11-v04`** (Android `MiffSharingConsent.VERSION`, iOS `MiffSharingConsent.version`, **dans le même commit** que les textes ; largeur fixe `AAAA-MM-vNN` ; le mois est celui de **mise en service** : si la livraison se fait en décembre, `2026-12-v04`, le numéro reste 04 ; le numéro ne repart jamais à zéro).
2. **Seuil de la position : inchangé (03)** (`isPositionConsentVersion`) : la précision ne change pas ce qui est enregistré sur les serveurs.
3. **Seuil de la photo : relevé à 04** (`isPhotoLocationConsentVersion`, `firestore.rules:357-359`). Une ligne de règle, un test (`25-photo-localisee.test.js`), une mutation. Côté apps : une fonction `coversPhotoPlace(version)` (NN ≥ 04) à côté de `coversPosition`, utilisée par `PhotoStore.kt:79` et son équivalent iOS ; **le client bloque avant d'écrire**, il n'attend pas le refus des règles.
4. **Archivage** : le texte v3 tel que livré (chaînes de `origin/main` au commit `424e858a`) est **conservé** dans `docs/legal/consentement-localisation-v3.md` (en-tête « remplacé par la v4 le AAAA-MM-JJ ; ce document garde ce que les comptes en v3 ont vu ») ; le texte v4 va dans `consentement-localisation-v4.md`.

### 6.2 Qui voit quoi

| Cas | Ce que voit la personne | Ce qui est écrit |
|---|---|---|
| **Nouveau** (créer / rejoindre) | la carte v4 (§7) | `consentVersion = 2026-11-v04` dans le lot d'entrée |
| **Compte en v3** (partage actif) | au lancement suivant, **une** carte calme « Une précision sur ce que tu partages » (§7.4) avec « J'ai compris » ; **rien n'est mis en pause**, pas de pastille « En attente » | « J'ai compris » = renouvellement R-LIEU-3 vers `2026-11-v04` ; « Plus tard » = rien |
| **Compte en v3 qui veut publier une photo** | si pas encore en v4 : la carte ci-dessus, avec « Pour publier une photo avec son endroit, nous avons besoin de ton accord à ce texte » (réutilise `update_notice_agreement`) | le renouvellement, puis la photo |
| **Compte en v1 ou v2** (comptes de test d'avant le 2026-11-04) | **comme aujourd'hui** (re-consentement bloquant), avec le texte v4 | R-LIEU-3 vers v04 (la règle « jamais sortir de la couverture position » est respectée) |
| **Compte qui refuse ou ignore** | « Plus tard » : la carte revient **au plus 3 fois**, puis reste accessible dans Réglages > Confidentialité (« Revoir et accepter ») ; **aucune pression, aucun compte à rebours, aucun blocage de la position** | rien |

### 6.3 Pièges à traiter (à la charge des devs, avec tests)

1. **`isRenewalDue` ne doit plus signifier « en pause »** : distinguer « à renouveler » (v < courante) de « en attente bloquante » (v ne couvre pas la position). Sinon la pastille « En attente » (`strings.xml:931-932`) s'afficherait à tort aux comptes en v3 alors que rien n'est en pause (`AwaitingAgreement.replace`, `PermissionGateViewModel.kt:115-120`). **Le texte ne doit jamais dire qu'un partage est en pause quand il ne l'est pas.**
2. **Un test de cohérence** : pour toute version V, « l'interface dit en pause » ⇔ « le suivi est réellement arrêté » (`coversPosition(V)` faux).
3. **Les tests qui utilisent « 2026-11-v03 » comme version courante** (≈ 20 fichiers : `ConsentRenewalsTest.kt`, `TrackingEngine*Test.kt`, `MiffWritesTest.kt`, `PermissionDomainTest.kt`, `PermissionScreensTest.kt`, iOS `ConsentRenewalsTests.swift`, `PermissionPrivacyTests.swift:266`, `SharedTextsParityTests.swift`) : distinguer « une version qui couvre la position » de « la version courante » ; **ne pas** remplacer en masse.
4. **Ordre de livraison** : (1) PR documents ; (2) builds Android et iOS v4 chez les testeurs ; (3) **ensuite seulement**, PR règles (seuil photo 04) et déploiement **par le PM, sur « oui » explicite** (`docs/dev/deploiement-regles.md`). Si les règles passaient avant, un build v3 verrait ses photos refusées sans message adapté. Retour arrière : redéployer le fichier de `main` précédent.
5. **iOS : aucun contrôle local possible** (poste Windows) ; textes d'usage et écran vérifiés par la CI et le TestFlight du PM.

---

## 7. Les nouveaux textes (FR + EN, tutoiement, ton rassurant)

Chaque phrase a été **vérifiée** contre le code et `backend/data-model.md` (colonne « Vérifié contre »). Rien n'y affirme ce que l'app ne fait pas : en particulier **l'arrêt du partage n'efface pas le journal local** (D16) : on ne le dit **jamais** effacé à l'arrêt, on dit où l'effacer.

### 7.1 Écran de consentement (carte `SharingConsentCard` : « Créer et partager », « Rejoindre et partager », « Reprendre le partage »)

| Clé | Français (v4) | English (v4) | Vérifié contre |
|---|---|---|---|
| `miff_consent3_create` / `_join` / `_resume` (phrase qui vaut accord) | « En créant cette Miff, tu partages ton statut, ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise, avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » (variantes « En rejoignant… », « Tu partages à nouveau… ») | "By creating this Miff, you share your status, your last known location and, if you publish a photo, the place where it was taken, with its members; you can stop at any time in the Miff's settings." | `data-model.md:554-564`, règles photo (§3.2), arrêt du partage `firestore.rules:376-411` |
| `miff_consent3_kept_title` / `_body` | « Ta dernière position connue » / « Miff garde seulement ta dernière position connue, avec l'heure de la mesure, sur ses serveurs. **Aucun historique de trajets sur nos serveurs** : chaque position remplace la précédente. Ce n'est pas un suivi en direct. » | "Your last known location" / "Miff only keeps your last known location, with the time it was measured, on its servers. **No history of trips on our servers**: each location replaces the previous one. This is not live tracking." | `data-model.md:695`, `:990` (pas d'historique, pas de journal de coordonnées) ; politique art. 4 l. 112 |
| **nouveau** `miff_consent3_photo_title` / `_body` | « L'endroit de ta photo » / « Quand tu publies une photo, l'endroit où tu l'as prise y est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur position le voient pendant 24 h, puis la photo n'est plus lisible. » | "The place of your photo" / "When you publish a photo, the place where you took it is attached to it, with the time of the measurement. The members who also share their location can see it for 24 hours, then the photo can no longer be read." | politique art. 4 l. 124 à 126 ; règles `validPhotoLocation` |
| **nouveau** `miff_consent3_local_title` / `_body` | « Sur ton téléphone seulement » / « Pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Réglages > Confidentialité. » | "On your phone only" / "To show you how your time is spread, your phone also notes your arrivals at and departures from your places, for 35 days, with no position or address. Nothing is sent and nobody else can see it. You can erase it in Settings > Privacy." | §2.3 : 35 jours, aucune coordonnée, jamais envoyé, effaçable ; `settings_privacy_time_body` |
| `miff_consent3_erase_title` / `_body` | « Effacement de ta position » / « Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position et ta photo avec son endroit sont effacées. Après 24 h, ta position est masquée et seule sa date reste visible. » | "Your location is erased" / "If you stop sharing, leave the Miff or delete your account, your location and your photo with its place are erased. After 24 h your location is hidden and only its date remains visible." | `data-model.md:915`, `:1073` ; politique art. 4 l. 150 à 152 |
| Inchangés | `miff_consent3_title`, `_who_*`, `_closed_*`, `_members_*`, `_places_*`, `_system_note` | idem | — |

*Charge cognitive* : **deux lignes de plus** sur la carte (photo, téléphone), les autres sont retouchées. Défaut : garder les deux ; si le design juge la carte trop longue, **la ligne « Sur ton téléphone seulement » ne se retire pas** (c'est elle qui ferme J65) ; la ligne « photo » ne se retire pas non plus (elle ferme J66).

### 7.2 Textes d'autorisation du système

**iOS, `NSLocationAlwaysAndWhenInUseUsageDescription`** (`InfoPlist.xcstrings:38` ; valeur de repli anglaise `ios/project.yml:131`, **à changer ensemble**) :

| | Texte |
|---|---|
| FR | « Pour signaler à ta Miff que tu arrives dans un lieu de confiance ou que tu le quittes, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue, avec l'heure, sans historique de trajets sur nos serveurs. Quand tu publies une photo, l'endroit où tu l'as prise y est joint, visible pendant 24 h des membres qui partagent aussi leur position. Sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. Tu peux arrêter à tout moment. » |
| EN | "To let your Miff know when you arrive at or leave a trusted place, even when the app is closed, Miff needs your location in the background. The members of your Miff who also share can see your status and your last known location, with the time, with no history of trips on our servers. When you publish a photo, the place where you took it is attached to it, visible for 24 hours to the members who also share their location. On your phone only, Miff also notes your arrivals and departures to show you how your time is spread. You can stop at any time." |

`NSLocationWhenInUseUsageDescription` (`InfoPlist.xcstrings:55`) : **inchangé** (aucun mot « historique », ne promet rien de faux). Cohérence : la règle « si l'un change, les trois changent » (`consentement-localisation-v3.md` §5) s'applique à **la divulgation Android, au texte « Toujours » iOS et au consentement** ; le texte « Pendant l'utilisation » n'est pas touché. *Limite* : iOS ne montre ce texte qu'à la **première** demande ; les personnes qui ont déjà répondu ne le reverront pas : c'est la carte v4 qui les informe.

**Android, divulgation en vue avant l'invite système** (écran `PermissionScreens.kt`) :
- `permission_location_disclosure` : **inchangée** (phrase obligatoire, mots exigés par Google : « localisation », « arrière-plan », « même quand l'app est fermée » ; `google-play-localisation-arriere-plan.md` §5).
- `permission_location_seen_body` : FR « Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure. Aucun historique de trajets sur nos serveurs. » / EN "The members of your Miff who also share see your status (for example "at home") and your last known location, with the time. No history of trips on our servers."
- **nouvelle ligne** `permission_location_local_title` / `_body` : FR « Pour toi seul(e) » / « Sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. Rien n'est envoyé. » EN "For you only" / "On your phone only, Miff also notes your arrivals and departures to show you how your time is spread. Nothing is sent." (reprend la ligne déjà proposée dans `journal-local-temps-declarations.md` §3 ; décision PM + design sur l'ajout d'une 4e ligne : Q3).
- iOS : même `permission_location_seen_body` et même nouvelle ligne dans `PermissionScreens.swift` (parité).

**Formulaire Google Play « Autorisations de localisation en arrière-plan »** (`google-play-localisation-arriere-plan.md` §4, anglais) : dans « Justification », remplacer « (latitude, longitude, time of the measurement; no history) » par « (latitude, longitude, time of the measurement; no history of trips on our servers) » et **ajouter la phrase déjà préparée** (`journal-local-temps-declarations.md` §3 : usage local, 35 jours, sans coordonnée, exclu des sauvegardes, jamais transmis, effaçable, aucune permission de plus). Le libellé de la fonctionnalité unique ne change pas.

**Note de revue Apple** (`app-store-confidentialite-et-revue.md` §5, l. 97) : « No history of trips. » devient « No history of trips on our servers. » et le paragraphe « On-device time summary » de `journal-local-temps-declarations.md` §1 est ajouté. **Étiquettes App Privacy et Data safety : inchangées** (donnée traitée seulement sur l'appareil, non « collectée » selon les définitions lues le 2026-11-17 ; analyse de l'équipe, pas une confirmation des stores ; bascule dès qu'un envoi du journal ou d'un dérivé est décidé).

### 7.3 Réglages > Confidentialité

| Clé | Français | English | Vérifié contre |
|---|---|---|---|
| `settings_privacy_location_body` | « Miff utilise la localisation de ton téléphone, même quand l'app est fermée, pour repérer tes arrivées et tes départs des lieux de ta Miff. Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure, et l'endroit de ta photo si tu en publies une. Aucun historique de trajets n'est gardé sur nos serveurs. Tu peux arrêter de partager à tout moment : ta position est alors effacée. » | "Miff uses your phone's location, even when the app is closed, to notice your arrivals and departures at the places of your Miff. Only the members of a Miff who also share theirs see your last known location, with the time, and the place of your photo if you publish one. No history of trips is kept on our servers. You can stop sharing at any time: your location is then erased." | idem §7.1 |
| `settings_privacy_time_body` (titre « Historique de temps » **inchangé** : il porte déjà le bouton et la politique le cite) | « Pour calculer la répartition de ton temps, ton téléphone garde pendant 35 jours les moments où tu arrives dans un de tes lieux et où tu en pars, sans position ni adresse. Rien n'est envoyé : personne d'autre ne peut les voir. **Arrêter de partager ne l'efface pas : c'est ici que tu peux le faire.** » | "To work out how your time is spread, your phone keeps for 35 days the moments you arrive at and leave your places, with no position or address. Nothing is sent: nobody else can see them. **Stopping sharing does not erase it: you can do it here.**" | D16, politique art. 4 l. 137 |
| nouvelle ligne sous le bouton « Effacer mon historique de temps » (déjà proposée, `journal-local-temps-declarations.md` §5) | « Efface les arrivées et départs que Miff garde sur ton téléphone pour la répartition de ton temps. Cela ne change rien pour tes proches. » | "Erases the arrivals and departures Miff keeps on your phone for your time breakdown. This changes nothing for your loved ones." | `settings_privacy_time_confirm_body` |

### 7.4 Écran de précision pour les comptes en v3 (non bloquant) et re-consentement des comptes en v1 ou v2

- **Comptes en v3** (nouvelles clés `miff_clarify_*`, **à ne pas confondre** avec `miff_renewal_*`, qui reste le re-consentement bloquant) :
  - FR titre « Une précision sur ce que tu partages » ; corps « Pour « %1$s », nous précisons ce que Miff garde. Sur nos serveurs : ta dernière position connue, et l'endroit de ta photo pendant 24 h, sans aucun historique de trajets. Sur ton téléphone seulement : un journal de tes arrivées et départs (35 jours, sans position ni adresse) qui dessine ton graphique « Répartition de ton temps » ; tu peux l'effacer dans Réglages > Confidentialité. **Tout continue comme avant.** Seule la publication d'une photo avec son endroit demandera ton accord à ce texte. » ; boutons « J'ai compris » / « Plus tard ».
  - EN title "A clarification about what you share"; body "For "%1$s", we are clarifying what Miff keeps. On our servers: your last known location, and the place of your photo for 24 hours, with no history of trips. On your phone only: a log of your arrivals and departures (35 days, no position or address) that draws your "Time breakdown" chart; you can erase it in Settings > Privacy. **Everything goes on as before.** Only publishing a photo with its place will ask for your agreement to this text."; buttons "Got it" / "Later".
  - « Tout continue comme avant » est **vrai** uniquement si le seuil de la position reste 03 (§6.1) : **cette phrase ne doit jamais être livrée avec l'option B**.
- **Comptes en v1 ou v2** : `miff_renewal_intro_one` / `_unnamed` / `_many` reprennent la phrase de `consentement-localisation-v3.md` §4 amendée, avec « sans historique de trajets sur nos serveurs » à la place de « Pas d'historique », et la mention de l'endroit de la photo ; ils ne parlent pas du journal (la carte du §7.1 le fait à l'entrée).

---

## 8. Cohérence avec les textes légaux 1.1 et mises à jour documentaires

| Texte | Article / ligne | Aujourd'hui | Avec la v4 |
|---|---|---|---|
| Politique, résumé point 1 | l. 9 | « sur nos serveurs… Sur ton téléphone seulement… 35 jours » | **Inchangé** (c'est la référence) |
| Politique art. 3 (tableau) | l. 42 (journal), l. 43 (position), l. 47 (preuves de consentement) | « version 3 ou plus récente qui les décrit » | **Retouché** : « version 4 ou plus récente » pour l'endroit de la photo ; « version 3 ou plus récente » reste vrai pour la position seule |
| Politique art. 4 | l. 112 à 139, l. 151 à 153 | idem ; « **Cet accord est distinct…** », « Le journal… n'a pas d'accord à part » | **Retouché** pour la version (l. 151) ; le reste est exact et confirme la ligne « Sur ton téléphone seulement » de la carte. **Ne pas écrire** « le journal a un accord à part » : le journal n'en a pas (D16) |
| Politique art. 7 | l. 213, l. 239 | « sur ses serveurs ne garde pas d'historique » | Inchangé |
| CGU art. 6 et 10 | — | « sur ses serveurs » | Inchangé |
| `docs/legal/README.md` | J65, J37, J48 (e), §12 | « v4 à venir » ; « sur iPhone, il n'est pas encore dans `main` » (**périmé**, §2.3) | **J65 : clos** (renvoi vers ce ticket) ; **J66 nouveau** : « le texte de consentement v3 tel que livré ne citait pas l'endroit de la photo alors que la politique dit qu'il le décrit ; corrigé par la v4 ; comptes en v3 : endroit de photo écrit sous un texte qui ne le citait pas, mitigé par l'aperçu de la photo (risque assumé) » ; J48 (e) mis à jour ; §12 corrigé pour iOS |
| `docs/legal/consentement-localisation-v3.md` | §1 bis | plan « version 4 » prévu si un testeur extérieur a accepté la v3 | **Appliqué** : voir §6.1 ; le document est archivé |

**Publication** : ces retouches de la politique forment une **version 1.2** (date = jour de la mise en service des apps v4), **publiée en une seule fois** avec les apps, comme la 1.1. Pas de ré-acceptation des CGU (J32 assumé). **Rien n'est déployé par l'agent** : le PM déploie la page web (`firebase deploy --only hosting --project miff-3d283`) sur son « oui » (d'après la décision du 2026-11-13, le PM tape les commandes de déploiement). **État de la 1.1** : fusionnée dans `main` par la PR #278 ; son déploiement sur l'hébergement n'est **pas vérifiable par l'agent** (la ligne de commit dit « non déployée »).

**Ce qu'on n'écrit pas** : aucune promesse de relecture juridique ; aucun « conforme au RGPD » ; pas de mention de suspension ou de sanction ; pas de l'engagement « si le journal quittait ton téléphone, nous te redemanderions ton accord » (non décidé par le PM : `journal-local-temps-declarations.md`, point 3) ; pas de « suivi », « habitudes », « surveillance ».

---

## 9. Découpage

| # | Lot | Contenu | Dépend de | Taille |
|---|---|---|---|---|
| **CONS-4-L** | **Légal et stores** (agents `release-manager` + `product-specs`, PR documents) | `consentement-localisation-v4.md` (texte, versionnement) ; archivage de la v3 ; politique 1.2 FR/EN (art. 3, 4) ; README légal (J65 clos, J66, J48, §12) ; `google-play-localisation-arriere-plan.md` §4-5 ; `app-store-confidentialite-et-revue.md` §4-5 ; `marketing/launch-plan.md` et `positioning.md` (« sur ses serveurs ») ; `web/tests/legal-1-1.test.mjs` (version) | réponses du PM (§11) | petit |
| **CONS-4-A** | **Android** (`android-dev`, une branche et une PR) | constante `2026-11-v04` ; `coversPhotoPlace` ; chaînes §7 (FR + EN) ; nouvelles lignes `miff_consent3_photo_*` et `_local_*` ; carte « Une précision » non bloquante ; correction de la pastille « En attente » (§6.3) ; ligne sous « Effacer » ; tests §10 | CONS-4-L (textes validés) ; **maquette de la carte et de la ligne de divulgation** (§12) | moyen |
| **CONS-4-I** | **iOS** (`ios-dev`, une branche et une PR, **après** Android, parité exacte des textes) | idem ; `InfoPlist.xcstrings:38`, `project.yml:131`, `Localizable.xcstrings` ; adaptation de `PermissionPrivacyTests.swift:266` ; `InfoPlistStaticTests` | CONS-4-A (textes figés) | moyen |
| **CONS-4-R** | **Règles** (`backend-data`, PR) | `isPhotoLocationConsentVersion` ≥ 04, test + mutation dans `25-photo-localisee.test.js`, `data-model.md` (§11 point 9 ter, tableau des seuils) ; **déploiement par le PM seulement après que les builds v4 soient chez les testeurs** | CONS-4-A et -I livrés ; « oui » explicite du PM | très petit |
| **CONS-4-T** | **i18n** (`docs/i18n/`, avec le ticket `traduction-es-it-pt-de.md`) | `chaines-reference.md` (S07, S10, nouvelles S), glossaire (nouveaux termes : « journal », « sur nos serveurs », « précision »), guide de style (contrôle « quatre précisions » inclus) ; **les textes de ce ticket sont dans le périmètre de la traduction es / it / pt / de** | **CONS-4-A et -I fusionnées avant la traduction en masse** (sinon les chaînes de consentement seraient traduites deux fois) | petit |

**Ordre** : CONS-4-L (texte validé) → CONS-4-A → CONS-4-I → builds chez les testeurs → CONS-4-R → déploiement des règles par le PM → publication de la politique 1.2 → traduction. Le process de `CLAUDE.md` s'applique à chaque lot (une PR par plateforme, revue en contexte neuf, **revue sécurité**, QA, recette PM, fusion sur « oui »).

---

## 10. Critères d'acceptation

**Textes et parité**
1. **CA-1** : les chaînes de §7 sont **identiques mot pour mot** sur Android et iOS, en FR et en EN (test de parité `SharedTextsParityTests`, équivalent Android) ; les nouvelles clés existent dans les deux fichiers.
2. **CA-2** : **aucun** texte d'écran ne dit « pas d'historique » **sans** « de trajets sur nos serveurs » ou l'équivalent (test de recherche sur les deux catalogues : toute occurrence de « historique » / « history » est accompagnée de « nos serveurs » / « our servers », hors libellé « Historique de temps »).
3. **CA-3** : les quatre précisions (« dernière », « heure », « historique », « arrière-plan ») restent présentes dans `permission_location_*` et `miff_consent3_*` (contrôle i18n).
4. **CA-4** : aucun texte ne contient « suivi » / « surveillance » / « habitudes » hors négation (« ce n'est pas un suivi en direct »), ni promesse de relecture juridique, ni « conforme ».
5. **CA-5** : aucune affirmation fausse : le journal n'est **jamais** dit effacé à l'arrêt du partage ; « Rien n'est envoyé » n'est vrai que si aucun envoi n'existe (contrôles V36 à V38 de `docs/release/README.md` repassés).

**Comportement**
6. **CA-6** : un **nouveau** compte voit la carte v4 et l'enregistre (`consentVersion = 2026-11-v04`, `consentedAt` = heure serveur) ; testé sur Android et iOS.
7. **CA-7** : un compte en **v3 partage toujours sa position et son statut** après la mise à jour ; **aucune pastille « En attente »** ; la carte « Une précision » apparaît **sans bloquer** le Feed ; « J'ai compris » écrit v04 par R-LIEU-3 ; « Plus tard » n'écrit rien et la carte revient **3 fois au plus** puis reste dans Réglages > Confidentialité.
8. **CA-8** : un compte en v3 **ne peut pas publier de photo** (le client bloque **avant** d'écrire) tant qu'il n'a pas accepté v04, avec la phrase calme existante ; après l'acceptation, la photo part avec son endroit.
9. **CA-9** : un compte en **v1 ou v2** voit le re-consentement bloquant comme avant, avec le texte v4, et la position reste interdite jusqu'à l'acceptation.
10. **CA-10** : pour toute version V, « l'interface annonce une pause » ⇔ « le suivi est réellement arrêté » (test de cohérence).
11. **CA-11** : l'arrêt du partage retire toujours statut, photo, position et alerte (inchangé) ; le journal local reste ; « Effacer mon historique de temps » l'efface.

**Règles et sécurité**
12. **CA-12** : (après CONS-4-R) une photo écrite avec un consentement `2026-11-v03` est **refusée** par les règles, avec `2026-11-v04` elle passe ; la position reste acceptée en v03 ; tests + mutation ; **aucune règle ouverte, aucune validation sautée**.
13. **CA-13** : aucune coordonnée ni nom de lieu dans le texte, les journaux, Analytics ou Crashlytics ajoutés par ce ticket ; revue sécurité faite.

**Stores et légal**
14. **CA-14** : la justification Play (§4) et la note de revue Apple (§5) sont alignées sur le texte v4 ; les étiquettes App Privacy et la Data safety **restent inchangées** et le document dit pourquoi et où est la limite.
15. **CA-15** : la politique 1.2 (FR et EN, deux langues à la fois) est cohérente avec les textes de l'app ; J65 clos, J66 écrit, J48 (e) et §12 du README corrigés.
16. **CA-16** : la v3 est **archivée** (texte exact de `424e858a`) avec la mention « remplacée par la v4 ».

**Tests à écrire** (en plus de la mise à jour des tests existants, §6.3 point 3) : format `AAAA-MM-vNN` de la nouvelle constante ; `isRenewalDue` pour v1, v2, v3, v4 ; `coversPosition` et `coversPhotoPlace` pour 02, 03, 04, 99 ; pastille « En attente » seulement si `!coversPosition` ; photo bloquée en v3 ; carte « Une précision » : un seul affichage par lancement, trois au plus ; parité FR/EN ; règles (CA-12) et mutation.

---

## 11. Questions pour le PM (3 au plus, chacune avec son défaut)

1. **Photo : exiger l'accord v4 avant de publier une photo avec son endroit ?** **Défaut : oui.** C'est ce qui rend vraie la phrase publiée de la politique et ferme J66 ; le coût est un écran de plus avant la **première photo** d'un compte en v3. Si **non** : on laisse le seuil de la photo à 03 et on ajoute à J66 « l'endroit de la photo de comptes en v3 est écrit sous un texte qui ne le citait pas » comme risque assumé.
2. **Les comptes déjà en v3 : confirmation calme sans pause (option D) ou re-demande qui met la position en pause (option B) ?** **Défaut : D, sans pause.** Argument : on précise l'information, on n'ajoute pas de finalité côté serveur ; et « Tout continue comme avant » rassure.
3. **Écran de consentement et d'autorisation Android/iOS : ajouter les deux lignes courtes (« L'endroit de ta photo », « Sur ton téléphone seulement » / « Pour toi seul(e) ») ou garder l'écran plus court ?** **Défaut : ajouter les deux** (la transparence prime sur la brièveté pour une donnée de localisation ; Apple demande des textes d'usage complets et Google une divulgation exacte). Si l'écran est jugé trop dense, le design arbitre, mais **sans retirer** la ligne du journal local.

Hors questions (défauts retenus, **non décidés** par le PM, à contredire si besoin) : titre « Historique de temps » conservé ; « Plus tard » 3 fois au plus ; pas de nouveau nom de clé (`miff_consent3_*` conservé) ; politique 1.2 publiée en une seule fois avec les apps.

---

## 12. Maquettes (règle du 2026-10-28)

Cette évolution a du design : **demander au PM l'export PNG** (dans `design/exports/<flux>/`) de la carte de consentement avec deux lignes de plus (écrans 21 et 24 de `design/screens/lieux/consentement-miff-v3.md`), de l'écran de re-consentement (`reconsentement-position-v3.md`) et de l'écran de divulgation Android. L'écran « Une précision » (non bloquant) **n'est pas designé** : les devs ont la liberté de le construire à partir de l'écran de re-consentement, **chaque écart étant listé et justifié dans la PR**.

---

## 13. Risques et risques assumés

| # | Risque | Traitement |
|---|---|---|
| R1 | **Comptes en v3 qui ignorent la carte** : leur preuve reste « v3 » (un texte sans mention de la photo ni du journal) | **Assumé.** Mitigé : la photo exige v04 (si Q1 = oui) ; le journal est local et décrit à plusieurs endroits ; la v3 est archivée. Jamais présenté comme un consentement au journal |
| R2 | **Personne extérieure ayant déjà accepté la v3** (inconnu de l'agent) | La v4 couvre ce cas ; le PM n'a rien à vérifier pour D (il le devrait pour A et C) |
| R3 | **Le mot « historique » reste dans un libellé** (« Historique de temps ») | Assumé : libellé du bouton cité par la politique ; renommer coûte une traduction de plus et rompt le lien avec les textes publiés |
| R4 | **Le journal local est tenu sans interrupteur** (J48) | Inchangé et **dit** par la carte v4 ; effaçable en un geste ; reprendre si une option serveur est un jour décidée (nouveau consentement) |
| R5 | **Décalage de déploiement** (règles avant builds) | §6.3 point 4 : builds d'abord, règles ensuite, déploiement par le PM sur « oui » |
| R6 | **Pas de ré-acceptation des CGU** (J32) | Assumé, inchangé |
| R7 | **Preuve perdue après un départ ou la suppression de compte** (Q13) | Assumé, inchangé |
| R8 | **Cette spec n'a pas été relue par un juriste** | Décision du PM du 2026-10-23 ; l'analyse RGPD/stores ci-dessus est celle de l'équipe, sans garantie de conformité |
| R9 | **Texte iOS « Toujours » plus long** (≈ 600 caractères) | Apple demande des textes complets ; si la fenêtre devient trop dense, retirer la phrase photo avant celle du journal (la photo est aussi dite à la prise de vue) |

**Déclarations qui restent exactes sans changement** : Apple App Privacy (le journal n'est pas collecté), Google Data safety (idem), Google « stalkerware » (J28, assumé), Apple 5.1.2 (J29, assumé).
