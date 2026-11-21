# Texte de consentement à la localisation, version 4 (`2026-11-v04`) : le texte livré dans les apps

Rédigé le 2026-11-21 par l'agent `release-manager` à partir de `main` (chaînes Android `values/strings.xml` et `values-fr/strings.xml`, textes iOS `InfoPlist.xcstrings`, constantes `MiffSharingConsent.VERSION` Android et `MiffSharingConsent.version` iOS, qui valent toutes deux `2026-11-v04`) et du ticket `docs/tickets/consentement-localisation-historique.md` (décisions du PM du 2026-11-19, option D). **Ce document décrit ce que les apps affichent ; il ne propose rien de nouveau.** Il remplace `consentement-localisation-v3.md` comme texte en vigueur (la version 3 est archivée dans ce dossier : elle garde ce que les comptes restés en version 3 ont vu). Ce texte n'a **pas été relu par un juriste** (décision du PM du 2026-10-23 : risque assumé) et ne doit jamais être présenté comme tel.

## 1. Ce que la version 4 change par rapport à la version 3

| Sujet | Version 3 (archivée) | Version 4 |
|---|---|---|
| Position conservée sur les serveurs | « Pas d'historique de trajets » | « **Aucun historique de trajets sur nos serveurs** » : la précision est nécessaire parce que l'app tient, sur le téléphone, un journal des arrivées et départs |
| Endroit de la photo | Cité seulement dans le document de proposition (amendement du 2026-11-06), **jamais dans les chaînes livrées** (écart découvert le 2026-11-19) | Ligne « L'endroit de ta photo » sur la carte de consentement |
| Journal du temps (35 jours, sur le téléphone) | Absent | Ligne « Sur ton téléphone seulement » sur la carte, ligne « Pour toi seul(e) » dans la divulgation avant la demande de localisation, précision dans Réglages > Confidentialité |
| Seuils des règles Firestore | position ≥ 03, photo localisée ≥ 03 | **Inchangés** (décision Q1 du PM : la v4 n'est pas exigée pour publier une photo ; aucune modification des règles) |

## 2. Règle de versionnement (inchangée) et qui voit quoi

Format `AAAA-MM-vNN` de largeur fixe, numéro qui ne repart jamais à zéro (règle du §2 de `consentement-localisation-v3.md`). Le mois est celui de la mise en service ; **si la mise en service des apps se fait un autre mois que novembre, la constante des deux apps est à ajuster dans le même commit (`2026-12-v04`), jamais le numéro 04.**

| Cas | Ce que voit la personne | Ce qui est enregistré |
|---|---|---|
| **Nouveau compte** (créer ou rejoindre une Miff) | la carte version 4 (§3) | `consentVersion = 2026-11-v04` dans le lot d'entrée |
| **Compte en version 3**, partage actif | au lancement suivant, **une** carte calme « Une précision sur ce que tu partages » (§4), **sans aucune pause** du partage ; « J'ai compris » ou « Plus tard » ; elle revient **au plus 3 fois**, puis reste accessible dans Réglages > Confidentialité (« Revoir et accepter ») | « J'ai compris » renouvelle l'accord vers `2026-11-v04` ; « Plus tard » ne change rien |
| **Compte en version 3 qui publie une photo** | rien de plus : l'aperçu de la photo dit qu'elle est publiée avec la position | la photo part sous un accord enregistré en version 3 (**risque J66 assumé par le PM**, `docs/legal/README.md` §4) |
| **Compte en version 1 ou 2** (comptes de test antérieurs au 2026-11-04) | re-consentement bloquant (§5), avec le texte v4 | renouvellement vers `2026-11-v04` |

## 3. Écran de consentement (« Créer et partager », « Rejoindre et partager », « Reprendre le partage »)

| Clé | Français | English |
|---|---|---|
| `miff_consent3_title` | Ce que tu partages avec cette Miff | What you share with this Miff |
| `miff_consent3_create` / `_join` / `_resume` | En créant cette Miff, tu partages ton statut, ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise, avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. (variantes « En rejoignant… », « Tu partages à nouveau… ») | By creating this Miff, you share your status, your last known location and, if you publish a photo, the place where it was taken, with its members; you can stop at any time in the Miff's settings. (variants "By joining…", "You are sharing … again") |
| `miff_consent3_kept_*` | **Ta dernière position connue** : Miff garde seulement ta dernière position connue, avec l'heure de la mesure, sur ses serveurs. Aucun historique de trajets sur nos serveurs : chaque position remplace la précédente. Ce n'est pas un suivi en direct. | **Your last known location**: Miff only keeps your last known location, with the time it was measured, on its servers. No history of trips on our servers: each location replaces the previous one. This is not live tracking. |
| `miff_consent3_photo_*` | **L'endroit de ta photo** : quand tu publies une photo, l'endroit où tu l'as prise y est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur position le voient pendant 24 h, puis la photo n'est plus lisible. | **The place of your photo**: when you publish a photo, the place where you took it is attached to it, with the time of the measurement. The members who also share their location can see it for 24 hours, then the photo can no longer be read. |
| `miff_consent3_local_*` | **Sur ton téléphone seulement** : pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Réglages > Confidentialité. | **On your phone only**: to show you how your time is spread, your phone also notes your arrivals at and departures from your places, for 35 days, with no position or address. Nothing is sent and nobody else can see it. You can erase it in Settings > Privacy. |
| `miff_consent3_who_*` | **Qui la voit** : seuls les membres de cette Miff qui partagent aussi la leur la voient. Le créateur n'a pas de droit de plus. | **Who sees it**: only the members of this Miff who also share theirs can see it. The creator has no extra rights. |
| `miff_consent3_closed_*` | **Même quand l'app est fermée** : pour cela, Miff utilise la localisation de ton téléphone, même quand l'app est fermée. | **Even when the app is closed**: to do this, Miff uses your phone's location, even when the app is closed. |
| `miff_consent3_erase_*` | **Effacement de ta position** : si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position et ta photo avec son endroit sont effacées. Après 24 h, ta position est masquée et seule sa date reste visible. | **Your location is erased**: if you stop sharing, leave the Miff or delete your account, your location and your photo with its place are erased. After 24 h your location is hidden and only its date remains visible. |
| `miff_consent3_members_*` | **Ce que les membres voient aussi de toi** : ton prénom, ton nom, ta photo, ton numéro de téléphone et ton niveau de batterie. | **What the members also see of you**: your first name, last name, photo, phone number and battery level. |
| `miff_consent3_places_*` | **Lieux de confiance** : les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres, adresse comprise. | **Trusted places**: the trusted places of this Miff are chosen by its creator and visible to all its members, address included. |
| `miff_consent3_system_note` | Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. | Your phone will ask separately for access to your location. Miff needs the "Always" option to work. |

Les clés gardent le préfixe `miff_consent3_` (nom de clé historique) bien qu'elles portent le texte de la version 4. **La date de la dernière photo (`photoAt`) et le registre serveur des notifications ne sont pas cités dans ce texte** : ce ne sont pas des positions (la politique de confidentialité 1.2 les décrit, article 3), donc ils ne relèvent pas de ce consentement. Même réserve pour les comptes existants que pour la notification du cercle (J42) : le texte de consentement n'est pas modifié pour eux.

## 4. Carte « Une précision sur ce que tu partages » (comptes en version 3, non bloquante)

| | Français | English |
|---|---|---|
| Titre | Une précision sur ce que tu partages | A clarification about what you share |
| Corps (une Miff) | Pour « {nom de la Miff} », nous précisons ce que Miff garde. Sur nos serveurs : ta dernière position connue, et l'endroit de ta photo, lisible pendant 24 h, sans aucun historique de trajets. Sur ton téléphone seulement : un journal de tes arrivées et départs (35 jours, sans position ni adresse) qui dessine ta répartition du temps ; tu peux l'effacer dans Réglages > Confidentialité. | For "{Miff name}", we are clarifying what Miff keeps. On our servers: your last known location, and the place of your photo, readable for 24 hours, with no history of trips. On your phone only: a log of your arrivals and departures (35 days, no position or address) that draws your time breakdown; you can erase it in Settings > Privacy. |
| Phrase de réassurance | Tout continue comme avant. | Everything goes on as before. |
| Boutons | J'ai compris / Plus tard | Got it / Later |

« Tout continue comme avant » est **vrai** parce que les seuils des règles (position et photo) restent à 03 : cette phrase ne doit jamais être livrée si un seuil est relevé. Le nombre d'apparitions de la carte est un compteur local par compte (jamais envoyé, effacé avec le compte).

## 5. Re-consentement des comptes en version 1 ou 2

`miff_renewal_intro_one` / `_unnamed` / `_many` : « Pour « {nom de la Miff} », Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure, et, si tu publies une photo, l'endroit où elle a été prise. Les membres de cette Miff qui partagent aussi la leur peuvent les voir. Aucun historique de trajets sur nos serveurs, jamais en direct. Si tu arrêtes de partager, ta position et ta photo sont effacées. Pour continuer à partager avec cette Miff, nous avons besoin de ton accord. » (EN : "For "{Miff name}", Miff can now keep your last known location, with the time it was measured, and, if you publish a photo, the place where it was taken. The members of this Miff who also share theirs can see them. No history of trips on our servers, never live. If you stop sharing, your location and your photo are erased. To keep sharing with this Miff, we need your agreement.") Ce texte ne parle pas du journal du temps (la carte du §3 le fait à l'entrée).

## 6. Textes d'autorisation du système et Réglages

| Où | Texte (français ; l'anglais est dans les apps) |
|---|---|
| Android, divulgation avant la demande « Toujours », `permission_location_seen_body` | Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure. Aucun historique de trajets sur nos serveurs. |
| Android et iOS, `permission_location_local_*` | **Pour toi seul(e)** : sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. Rien n'est envoyé. |
| iOS, `NSLocationAlwaysAndWhenInUseUsageDescription` | Pour signaler à ta Miff que tu arrives dans un lieu de confiance ou que tu le quittes, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue, avec l'heure, sans historique de trajets sur nos serveurs. Quand tu publies une photo, l'endroit où tu l'as prise y est joint, visible pendant 24 h des membres qui partagent aussi leur position. Sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. Tu peux arrêter à tout moment. |
| iOS, `NSCameraUsageDescription` | Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. Si tu la publies, elle est partagée avec l'endroit où tu l'as prise. Les membres de tes Miffs qui partagent aussi leur position peuvent la voir pendant 24 h, puis elle n'est plus visible ; un membre peut toutefois en avoir gardé une copie. Rien n'est enregistré dans ta photothèque. |
| Réglages > Confidentialité, `settings_privacy_location_body` | Miff utilise la localisation de ton téléphone, même quand l'app est fermée, pour repérer tes arrivées et tes départs des lieux de ta Miff. Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure, et l'endroit de ta photo si tu en publies une. Aucun historique de trajets n'est gardé sur nos serveurs. Tu peux arrêter de partager à tout moment : ta position est alors effacée. |
| Réglages > Confidentialité, `settings_privacy_time_body` (titre « Historique de temps ») | Pour calculer la répartition de ton temps, ton téléphone garde pendant 35 jours les moments où tu arrives dans un de tes lieux et où tu en pars, sans position ni adresse. Rien n'est envoyé : personne d'autre ne peut les voir. Arrêter de partager ne l'efface pas : c'est ici que tu peux le faire. |

La règle « si l'un change, les trois changent » (divulgation Android, texte d'usage iOS « Toujours », carte de consentement) s'applique. `NSLocationWhenInUseUsageDescription` n'est pas modifié (aucun mot « historique »).

## 7. Cohérence avec la politique de confidentialité 1.2 (vérifiée le 2026-11-21)

| Point | Texte de l'app | Politique 1.2 | État |
|---|---|---|---|
| Historique | « sur nos serveurs » | Résumé point 1, art. 4, art. 7 | Cohérent |
| Journal du temps : 35 jours, sans position, jamais envoyé, effaçable, **pas effacé par l'arrêt du partage** | `settings_privacy_time_body` | Art. 4, art. 7 | Cohérent |
| Endroit de la photo : 24 h, visible des membres qui partagent aussi | `miff_consent3_photo_*` | Art. 3, 4, 5 | Cohérent |
| Comptes en version 3 | carte « Une précision » (3 fois au plus) | Art. 3 (preuves de consentement), art. 4 (« Ton consentement ») : **la politique ne dit plus que la version 3 « décrit » l'endroit de la photo** | Cohérent ; limite écrite (J66) |
| Date de la dernière photo, registre serveur des notifications | non cités | Art. 3, 5, 7, 8 | **Choix assumé : hors de ce texte** (voir §3) |

## 8. Points ouverts

- **Le numéro de mois de la constante** : `2026-11-v04` suppose une mise en service en novembre ; sinon, changer les deux constantes ensemble (le PM le décide avec la date de distribution).
- **Un compte de test resté en version 3** continue d'écrire l'endroit de ses photos sous un accord qui ne le citait pas : **risque J66, assumé par le PM le 2026-11-19**, jamais présenté comme un consentement à l'endroit de la photo.
- **Le mot « historique »** reste dans le titre « Historique de temps » (risque R3 du ticket, assumé).
