# Texte de consentement à la localisation, version 3 (proposition)

Proposé le 2026-11-04 par l'agent `release-manager`, **amendé le 2026-11-06** pour citer la photo localisée (décision du PM : une photo est toujours publiée avec l'endroit où elle a été prise ; voir §1 bis). **Statut : proposition, à valider par le PM. Rien n'est publié** (l'app Android du dépôt contient déjà la version 3 d'origine, sans la photo localisée ; iOS : PR #74 en brouillon ; aucune app n'est publiée sur un store ; des builds de test ont pu être distribués : voir §1 bis). Ce texte n'a **pas été relu par un juriste** (décision du PM du 2026-10-23 : risque assumé, R15) et ne doit jamais être présenté comme tel.

## 1. Pourquoi une version 3

La décision du PM du 2026-11-01 remplace « la position reste sur le téléphone » par « la dernière position connue est **stockée côté serveur** et lisible des membres de la Miff ». Le texte `2026-10-v2` ne le dit pas : il ne couvre donc **pas** l'écriture de la position. Les règles Firestore préparées (branche `backend/regles-position`, **non déployées**, voir `backend/data-model.md` de cette branche) refusent toute écriture de position tant que le consentement du membre n'est pas de **version 3 ou plus récente**. Les comptes déjà consentis (`2026-10-v1`, `2026-10-v2`) doivent donc **re-consentir** avant la première écriture de position.

## 1 bis. Amendement du 2026-11-06 : la photo localisée, et le plan « version 4 »

**Pourquoi.** Décision du PM du 2026-11-06 : une photo « Donner des nouvelles » est **toujours** publiée avec l'endroit où elle a été prise (latitude, longitude, et l'heure de **sa propre mesure**, D23 du ticket `docs/tickets/update.md`). C'est une seconde copie de position, attachée à l'image, qui survit au remplacement de la dernière position pendant 24 h. Le texte v3 d'origine ne la cite pas (`backend/data-model.md` §11, point 9 ter) : il est donc **amendé** (§3 et §4 ci-dessous), conformément à la décision D20 du ticket.

**Numéro de version : inchangé (`2026-11-v03`).** Rien dans le dépôt n'impose de le changer : aucun texte légal n'est publié, et le texte v3 n'a été livré à personne d'extérieur à notre connaissance. Cette connaissance est **incomplète** (je n'ai accès ni à Play Console ni à TestFlight) : c'est le PM qui vérifie qu'aucun testeur externe n'a accepté la version 3 d'origine. Les règles Firestore exigent déjà le même seuil (`isPhotoLocationConsentVersion`, NN >= 03).

**Ce que l'amendement change dans les apps (rien n'est modifié par cette PR de documents).** Android : les chaînes `miff_consent3_*` (`values/strings.xml` et `values-fr/strings.xml`) et la phrase du re-consentement ; iOS : les mêmes textes dans la PR #74 (brouillon). La constante `MiffSharingConsent.VERSION` ne change pas, ni les tests de format. À faire dans la PR **UPDATE-1b**, avant que l'app n'envoie une photo localisée.

**Plan « version 4 » (à appliquer seulement si le PM constate qu'une personne extérieure a déjà accepté la version 3 d'origine).**

| Étape | Détail |
|---|---|
| 1 | Le PM l'écrit dans la PR de UPDATE-1b (« une personne extérieure a accepté la v3 le … »). |
| 2 | Nouvelle version `2026-11-v04` (ou `AAAA-MM-v04` avec le mois de mise en service : le numéro **ne repart jamais à zéro**), **constante changée dans le même commit sur Android et iOS**, mêmes textes FR/EN que ci-dessous. |
| 3 | **Seuil de `isPhotoLocationConsentVersion` relevé à 04** (une ligne de règle, un test, une mutation : `backend/data-model.md`, en-tête). Le seuil de la **position** reste à 03 : la position n'a pas changé. Règles à redéployer sur « oui » explicite du PM, **règles d'abord, builds ensuite**. |
| 4 | Écran de re-consentement (§4) pour les comptes dont le partage est actif en v3 d'origine : il ne couvre que « l'endroit de tes photos ». **Tant qu'il n'est pas accepté, l'app ne publie pas de photo** (une photo est toujours localisée) ; la position et le reste continuent. |
| 5 | Aucun changement de la politique de confidentialité ni des CGU : leurs passages disent « version 3 ou plus récente qui décrit … l'endroit de la photo », sans numéro précis. |

**Comptes internes.** Les comptes de l'équipe qui ont accepté la v3 d'origine n'ont pas vu la mention de la photo localisée : sans conséquence pour des comptes de test, mais leur acceptation ne doit pas être présentée comme une preuve de consentement à la photo localisée.

## 2. Règle de versionnement : `AAAA-MM-vNN` (largeur fixe)

| Élément | Règle |
|---|---|
| Format | `AAAA-MM-vNN` : année sur 4 chiffres, mois sur 2 chiffres (`01` à `12`), la lettre `v` minuscule, puis **deux chiffres avec zéro de tête** (`03`, `04`, ... `99`). Rien avant, rien après (ni espace, ni retour à la ligne). |
| Pourquoi la largeur fixe | L'ordre alphabétique devient l'ordre chronologique : les règles vérifient qu'une nouvelle version est **strictement supérieure** à l'ancienne. Sans largeur fixe, `2026-11-v10` passerait avant `2026-11-v2`. |
| Numéro NN | Il **ne repart jamais à zéro** d'un mois à l'autre. Un texte modifié = un numéro de plus, jamais deux textes sous la même version. |
| Mois (`MM`) | Mois de **mise en service** du texte dans les apps. Si la version 3 n'est livrée qu'en décembre, elle s'écrit `2026-12-v03` ; le numéro, lui, reste `03`. |
| **Version 3** | **`2026-11-v03`** (valeur proposée, si elle est livrée en novembre 2026). Les règles exigent `NN >= 03` pour écrire une position. |
| Anciennes versions | `2026-10-v1` et `2026-10-v2` restent lisibles comme **anciennes** versions (elles ne couvrent pas la position). Elles ne sont plus de largeur fixe : on les renouvelle **directement vers une version de largeur fixe plus récente** (`2026-10-v2` vers `2026-11-v03` : accepté ; `2026-10-v1` vers `2026-10-v2` : n'est plus accepté comme renouvellement). |
| Une seule version pour les deux langues | Le texte français et le texte anglais sont deux présentations **du même** texte : ils partagent la même version, et on ne modifie jamais l'un sans l'autre. (Le README de `docs/legal/` disait « par langue » : à lire comme « dans chaque langue, à l'identique ».) |
| Constante partagée | Une seule constante par plateforme, changée **dans le même commit** que les textes : Android `MiffSharingConsent.VERSION` (`android/.../domain/miff/MiffModels.kt`), iOS `MiffSharingConsent.version` (`ios/Miff/Models/Miff/MiffModels.swift`). La version du texte des statistiques (`2026-10-v01`) est une **autre** constante, qui ne change pas. |
| Limite connue | **La version est déclarée par l'app.** Aucune règle ne prouve quel texte la personne a réellement vu : la protection est le comportement des apps (constante partagée, revue de code, QA). |

## 3. Le texte de l'écran « Créer / Rejoindre une Miff » (remplace la version `2026-10-v2`)

Tutoiement, ton rassurant. Les clés existantes des apps sont rappelées pour que `android-dev` et `ios-dev` sachent quoi remplacer.

| Clé actuelle | Français (version 3) | English (version 3) |
|---|---|---|
| (nouveau) titre | « Ce que tu partages avec cette Miff » | "What you share with this Miff" |
| `miff_consent_create` / `miff_consent_join` (phrase qui vaut accord) | « En créant cette Miff, tu partages ton statut, ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise, avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » / « En rejoignant cette Miff, tu partages … » | "By creating this Miff, you share your status, your last known location and, if you publish a photo, the place where it was taken, with its members; you can stop at any time in the Miff's settings." / "By joining this Miff, you share …" |
| (nouveau) ce qui est enregistré **(amendé le 2026-11-06)** | « Miff garde ta dernière position connue, avec l'heure de la mesure, et, pour une photo, l'endroit où elle a été prise. Pas d'historique de trajets : chaque position remplace la précédente. Ce n'est pas un suivi en direct. » | "Miff keeps your last known location, with the time it was measured, and, for a photo, the place where it was taken. No history of trips: each location replaces the previous one. This is not live tracking." |
| (nouveau) **la photo (ajouté le 2026-11-06)** | « Chaque photo que tu publies est accompagnée de l'endroit où tu l'as prise, mesuré à ce moment-là, avec l'heure de la mesure. Cet endroit reste attaché à la photo pendant 24 h, même si ta dernière position connue change entre-temps. » | "Every photo you publish comes with the place where you took it, measured at that moment, with the time of the measurement. That place stays attached to the photo for 24 hours, even if your last known location changes in the meantime." |
| (nouveau) qui la voit **(amendé)** | « Seuls les membres de cette Miff qui partagent aussi la leur voient ta position et l'endroit de ta photo. Le créateur n'a pas de droit de plus. » | "Only the members of this Miff who also share theirs can see your location and the place of your photo. The creator has no extra rights." |
| (nouveau) même app fermée | « Pour cela, Miff utilise la localisation de ton téléphone, même quand l'app est fermée. » | "To do this, Miff uses your phone's location, even when the app is closed." |
| (nouveau) effacement **(amendé)** | « Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position et ta photo avec son endroit sont effacées. Après 24 h, ta position est masquée (seule sa date reste visible) et ta photo n'est plus lisible. » | "If you stop sharing, leave the Miff or delete your account, your location and your photo with its place are erased. After 24 h your location is hidden (only its date remains visible) and your photo can no longer be read." |
| `miff_consent_members_see_title` / `_body` | « Ce que les membres voient aussi de toi » / « Ton prénom, ton nom, ta photo, ton numéro de téléphone et ton niveau de batterie. » | "What the members also see of you" / "Your first name, last name, photo, phone number and battery level." |
| `miff_consent_places` | « Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres, adresse comprise. » | "The trusted places of this Miff are chosen by its creator and visible to all its members, address included." |
| `miff_consent_system_note_create` / `_join` | **Deux options, décision du PM requise (point ouvert O2) :** voir le §6. | see §6 |
| Boutons (inchangés) | « Créer et partager » / « Rejoindre et partager » | "Create and share" / "Join and share" |

**Ce qui est enregistré avec l'accord** (rien ne change côté données) : date (heure serveur), version `2026-11-v03` (ou `04` si le plan du §1 bis s'applique), par Miff, avec le partage activé.

**Cohérence à tenir** : l'écran d'explication de la caméra (ticket Update §7) dit aujourd'hui « La photo est visible des membres de tes Miffs pendant 24 h » sans citer l'endroit ; la phrase de visibilité de l'aperçu a une variante « si localisée ». Avec la décision « toujours localisée », les deux doivent dire la même chose que ce texte : voir `docs/release/update-photo-declarations.md` §9.3.

**Pourquoi la batterie est ajoutée** : le statut contient déjà le niveau de batterie et l'état de l'autorisation de localisation, visibles des membres (`backend/data-model.md`, politique de confidentialité art. 5). Le texte v2 ne les citait pas (Q14 du 2026-10-30). C'est une proposition : le PM peut la retirer, la politique de confidentialité les décrit de toute façon.

## 4. L'écran de re-consentement (comptes qui ont accepté `2026-10-v1` ou `2026-10-v2`)

Affiché **avant toute écriture de position**, une fois par Miff concernée (ou une fois pour toutes les Miffs de la personne : voir O3).

| | Français | English |
|---|---|---|
| Titre | « Une précision sur ce que tu partages » | "A clarification about what you share" |
| Corps | « Pour « {nom de la Miff} », Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure, et, si tu publies une photo, l'endroit où elle a été prise. Les membres de cette Miff qui partagent aussi la leur peuvent les voir. Pas d'historique, jamais en direct, et tout est effacé si tu arrêtes de partager. Pour continuer à partager avec cette Miff, nous avons besoin de ton accord. » | "For "{Miff name}", Miff can now keep your last known location, with the time it was measured, and, if you publish a photo, the place where it was taken. The members of this Miff who also share theirs can see them. No history, never live, and everything is erased if you stop sharing. To keep sharing with this Miff, we need your agreement." |
| Bouton principal | « J'accepte » | "I agree" |
| Lien (si O1 le permet) | « Pas maintenant » | "Not now" |

Écriture : renouvellement du consentement d'un partage actif (règle R-LIEU-3 de `backend/regles-position`, **non déployée**) : `consentedAt` = heure serveur, `consentVersion` = `2026-11-v03`, sans arrêter le partage.

## 5. Les textes d'autorisation du système

La divulgation visible avant la demande « Toujours » (exigée par Google Play) et les textes d'usage iOS sont rédigés dans `docs/release/google-play-localisation-arriere-plan.md` et `docs/release/app-store-confidentialite-et-revue.md`. Ils disent la même chose que ce texte (dernière position connue, visible des membres qui partagent aussi, même app fermée, arrêt possible) : **si l'un change, les trois changent**.

## 6. Points ouverts pour le PM (non tranchés, jamais devinés)

| # | Point | Options et recommandation |
|---|---|---|
| **O1** | **Que se passe-t-il si une personne refuse le re-consentement** (ou ne l'accepte pas) ? | Le PM a décidé que « Toujours » est obligatoire pour utiliser l'app, mais n'a rien dit du refus du **partage de la position**. Recommandation : tant que `2026-11-v03` n'est pas accepté, rien n'est écrit (ni position, ni statut de la Miff concernée), l'écran reste proposé à chaque ouverture, et la personne peut quitter la Miff ou supprimer son compte. À confirmer. |
| **O2** | **Phrase « Tu peux le refuser »** (note système de l'écran, risque **R3** assumé le 2026-11-01 pour le texte v2) | **Option A (recommandée)** : « Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. » (EN : "Your phone will ask separately for access to your location. Miff needs the "Always" option to work."). **Option B** : garder la phrase d'origine (« Tu peux le refuser : tu rejoins la Miff quand même. »), donc **fausse**, avec le risque R3 déjà assumé. La décision R3 portait sur le texte v2 inchangé ; la version 3 est un **nouveau** texte : c'est donc au PM de dire s'il veut y recopier une phrase qu'il sait fausse. |
| **O3** | Un seul écran de re-consentement pour toutes les Miffs (un renouvellement par Miff, dans une même écriture) ou un écran par Miff ? | Un seul écran, qui liste les Miffs concernées (compte gratuit : une seule Miff). |
| **O4** | **La localisation affichée avec la photo d'une mise à jour** | **Tranché** : la position **mesurée au moment de la photo**, avec sa propre heure (D23 du ticket Update, 2026-11-06), et **toujours jointe à la photo** (décision du PM du 2026-11-06, P7). Les textes du §3 et du §4 l'écrivent. Dépendance : le ticket Update (« photo sans localisation » si la mesure est trop ancienne) et la règle R-PHOTO-LOC (champs facultatifs) décrivent encore une photo qui peut être publiée sans lieu : à aligner (`product-specs`, `backend-data`) avant UPDATE-1b ; voir `docs/release/update-photo-declarations.md` §1.2 (P7). |
| **O5** | **Fréquence maximale** d'écriture de la position (question L4, non tranchée) | Les textes disent « à certains moments » sans chiffre ; à préciser si le PM veut annoncer un plafond. |
| **O6** | Un nouveau consentement est-il aussi à demander pour les **CGU et la politique** (qui changent) ? | Les règles interdisent aujourd'hui de modifier `termsVersion` après la création du profil : il n'existe **aucun mécanisme de ré-acceptation des CGU**. Sans effet tant qu'aucun testeur externe n'a accepté de texte publié ; à traiter avant la bêta externe (ticket « nouvelle version des CGU » à créer, `backend-data` + devs). |

## 7. Vérifications avant de livrer ce texte

- [ ] **Photo localisée (2026-11-06)** : le PM a vérifié dans Play Console et TestFlight si une personne extérieure a accepté la v3 d'origine (sinon amendement, sinon plan v4 du §1 bis) ; les chaînes Android `miff_consent3_*` et celles d'iOS (PR #74) portent le texte amendé ; la mention « l'endroit de ta photo » figure dans l'écran d'explication de la caméra.
- [ ] Le PM a répondu à O1 et O2 ; les textes FR et EN sont identiques sur Android et iOS (critère de parité i18n).
- [ ] La constante `2026-11-v03` (ou le mois de livraison) est la même sur Android et iOS ; un test vérifie le format `AAAA-MM-vNN` de largeur fixe.
- [ ] Les règles de `backend/regles-position` sont déployées (sur « oui » explicite du PM) **avant** que les apps n'écrivent une position.
- [ ] La politique de confidentialité et les CGU (même PR que ce document) sont à jour dans leur dernière version.
