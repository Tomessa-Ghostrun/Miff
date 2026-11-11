# Fiche Google Play (Android) : brouillon

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), à valider par le PM puis à intégrer par `release-manager`. Aucune soumission, aucun compte, aucun achat : rien n'est publié.**
Langue principale : français. Langue de repli : anglais.

**À lire avant d'utiliser ce fichier**
- Les déclarations déjà arrêtées dans `docs/release/` (notamment `google-play-data-safety.md`, `google-play-localisation-arriere-plan.md` et `update-photo-declarations.md`) **ne doivent jamais être contredites**. **Recoupé le 2026-11-07 par `release-manager`** avec ces fichiers, `docs/legal/*`, `web/src/landing.json`, `backend/data-model.md`, `docs/tickets/*.md` et le code Android de `main` (`8e87ebd`). Corrections faites dans ce fichier : la localisation en arrière-plan n'est **plus décrite comme facultative** (« Toujours » est obligatoire, décision du PM du 2026-11-01) ; la description reprend la phrase exigée par `google-play-localisation-arriere-plan.md` §5 (arrière-plan, app fermée, statut **et dernière position connue** vus du cercle) ; « petit mot » est retiré (la photo n'a **pas de texte** en v1 et porte **toujours son endroit**) ; la formule gratuite est de **3 lieux** (décision du PM du 2026-11-10 ; 2 depuis le 2026-11-01) ; l'exigence de test fermé de Google est vérifiée (section 5).
- La section 0 de `store-listing-ios.md` (C1 à C15) vaut aussi pour Android, avec cette différence : **l'Android a le Feed, l'Update et les Réglages (dont la suppression de compte)** ; c'est l'iOS qui ne les a pas encore.
- Notifications push et abonnement (Miff+) **non actifs** : aucune mention. Aucune marque tierce.
- **Rien n'est « prêt à soumettre »** (`docs/qa/release-checklist.md` n'existe pas encore). Décisions et actions du PM : `docs/release/decisions-stores-avant-soumission.md`.
- Règles de métadonnées Google Play vérifiées le 2026-11-07 (page officielle « Metadata policy ») : titre de 30 caractères au plus ; pas d'émojis ni de symboles répétés dans le titre ; pas de majuscules abusives ; pas de mots-clés répétés ou sans rapport ; pas de prix ni d'offre promotionnelle dans le titre ; pas de mention de classement (« n° 1 », « populaire ») ; pas de témoignages anonymes dans la description ; captures et vidéo fidèles à l'expérience réelle. Description courte : 80 caractères (page officielle des spécifications). Description longue : 4000 caractères (connaissance de l'agent, à revérifier dans la console).

---

## 1. Champs de la fiche : français

| Champ Play Console | Texte | Caractères |
|---|---|---|
| **Nom de l'app** | Miff - Cercle de confiance | 26 / 30 |
| **Description courte** | Des nouvelles de tes proches, sans les déranger. Partage consenti et révocable. | 79 / 80 |
| **Catégorie** | Style de vie (Lifestyle) | voir section 4 |
| **Notes de version** (par langue, 500 caractères au plus : limite à revérifier) | voir ci-dessous | environ 350 |

### Description longue (FR)

```
Miff, c'est savoir que tes proches vont bien, sans les déranger.

Plus besoin de demander « tu es bien arrivé(e) ? » ni d'y répondre. Chacun vit sa journée, et ton cercle de confiance a des nouvelles quand il en a besoin.

COMMENT ÇA MARCHE

1. Crée ou rejoins une Miff
Une Miff, c'est ton cercle de confiance : famille, amis, couple... On la crée et on partage son code secret, ou on rejoint celle d'un proche avec le code qu'il ou elle te donne.

2. Choisis des lieux de confiance
La maison, l'école, le travail... Le créateur de la Miff définit les lieux, et chaque membre les retrouve dans l'app.

3. Les nouvelles arrivent toutes seules
Avec ton accord, ton téléphone repère quand tu arrives dans un lieu de confiance ou que tu en pars, même quand l'app est fermée. Ton cercle voit un statut tout simple, comme « À la maison », et ta dernière position connue avec l'heure, sans que tu aies rien à écrire.

4. Donne des nouvelles quand tu veux
Une photo prise sur le moment avec la caméra de Miff, sans texte. Elle est publiée avec l'endroit où tu l'as prise et visible 24 heures.

5. Un coup d'oeil, et c'est fait
Tu ouvres Miff, tu vois où en sont tes proches, tu refermes. Si l'envie t'en prend, un bouton t'envoie vers ton application habituelle pour écrire un message ou appeler.

TU GARDES LA MAIN

- Miff utilise la localisation de ton téléphone, y compris en arrière-plan et même quand l'app est fermée, pour repérer tes arrivées et tes départs des lieux de confiance et partager ton statut et ta dernière position connue avec les membres de ta Miff qui partagent aussi. Tu peux arrêter à tout moment.
- Cette autorisation (« Toujours », avec la position précise) est nécessaire au fonctionnement de Miff : sans elle, l'app ne peut pas être utilisée normalement. Miff t'explique à quoi elle sert avant que ton téléphone te la demande.
- Le partage avec chaque Miff est ton choix : il ne démarre qu'avec ton accord, et tu peux l'arrêter à tout moment, sans te justifier. Ton statut, ta photo et ta dernière position sont alors effacés.
- Ce que voit ton cercle : ton statut, ta dernière position connue avec l'heure de sa mesure, et ta photo avec l'endroit où elle a été prise. Ce n'est pas un suivi en direct, et Miff ne garde aucun historique de tes trajets.
- Seuls les membres de ta Miff qui partagent aussi leur position voient la tienne et ta photo. On ne rejoint une Miff qu'avec son code secret.
- Les photos ne sont plus visibles après 24 heures dans Miff. Un proche peut toutefois en garder une copie : par capture d'écran, ou en touchant « Enregistrer cette photo », seulement s'il choisit de l'enregistrer.
- Aucune publicité, aucune revente de données.
- Tu peux supprimer ton compte depuis l'app.

POUR QUI ?

En famille, entre amis, en couple : le cercle que tu choisis, avec l'accord de chacun. Miff n'est pas fait pour suivre quelqu'un à son insu.

BON À SAVOIR

- Dans cette version, tu peux être dans une Miff à la fois, avec 3 lieux de confiance.
- La détection des arrivées et des départs dépend de ton téléphone et de ses réglages d'économie d'énergie : elle peut être en retard ou ne pas se produire. Un statut en retard ne veut pas dire qu'il y a un problème.
- Miff n'est pas un service d'urgence. En cas de danger ou de doute sérieux, appelle le 112.
- Un compte ne fonctionne que sur un téléphone à la fois.
- Disponible en français et en anglais.

Une question, une idée ? Écris-nous depuis les Réglages de l'app.
```

Différences avec la fiche iOS : « iPhone » devient « téléphone » ; la **première puce de « Tu gardes la main » reprend mot pour mot la phrase que Google exige dans la description de la fiche** (`google-play-localisation-arriere-plan.md` §5, vérifiée le 2026-11-07 sur la page Google « Use of location permissions in the background » : la divulgation doit se trouver dans l'app **et** dans la description de la fiche **et** sur le site, avec les mots « location » et « background » ou « when the app is closed », et la liste des fonctions concernées) ; mention des réglages d'économie d'énergie des fabricants, qui sont le risque de fiabilité le plus probable sur Android (appareil de test du PM : un téléphone avec surcouche constructeur). « Écris-nous depuis les Réglages » est exact sur Android (Réglages > Confidentialité > Nous contacter).

### Notes de version (FR)

```
Première version de Miff.

- Crée ta Miff ou rejoins celle d'un proche avec son code secret.
- Choisis des lieux de confiance.
- Avec ton accord, tes arrivées et tes départs sont partagés avec ton cercle, sans rien faire.
- Publie une photo du moment quand tu veux (visible 24 heures).
- Disponible en français et en anglais.

Une idée, un souci ? Écris-nous depuis les Réglages.
```

---

## 2. Champs de la fiche : anglais (langue de repli)

| Champ | Texte | Caractères |
|---|---|---|
| **App name** | Miff - Trusted Circle | 21 / 30 |
| **Short description** | News from loved ones, without bothering them. Consented, revocable sharing. | 75 / 80 |

### Full description (EN)

```
Miff is how you know your loved ones are okay, without bothering them.

No more asking "did you get there okay?" or having to answer. Everyone lives their day, and your circle of trust has news when it needs it.

HOW IT WORKS

1. Create or join a Miff
A Miff is your circle of trust: family, friends, partner... You create one and share its secret code, or you join a loved one's Miff with the code they give you.

2. Choose trusted places
Home, school, work... The creator of the Miff sets the places, and every member finds them in the app.

3. News arrives by itself
With your agreement, your phone notices when you arrive at or leave a trusted place, even when the app is closed. Your circle sees a very simple status, like "At home", and your last known location with the time, with nothing for you to write.

4. Share news whenever you like
A photo taken on the spot with Miff's camera, with no text. It is published with the place where you took it and visible for 24 hours.

5. One glance, and done
You open Miff, you see how your loved ones are doing, you close it. If you feel like it, a button takes you to your usual app to write a message or call.

YOU STAY IN CONTROL

- Miff uses your phone's location, including in the background and even when the app is closed, to detect your arrivals and departures at trusted places and to share your status and last known location with the members of your Miff who also share. You can stop at any time.
- This permission ("Always", with precise location) is needed for Miff to work: without it, the app cannot be used normally. Miff explains what it is for before your phone asks for it.
- Sharing with each Miff is your choice: it only starts with your agreement, and you can stop at any time, without having to explain yourself. Your status, your photo and your last known location are then erased.
- What your circle sees: your status, your last known location with the time it was measured, and your photo with the place where it was taken. This is not live tracking, and Miff keeps no history of your trips.
- Only the members of your Miff who also share their location see yours and your photo. A Miff can only be joined with its secret code.
- Photos are no longer visible in Miff after 24 hours. A loved one can still keep a copy: by taking a screenshot, or by tapping "Save this photo", only if they choose to save it.
- No ads, no data resale.
- You can delete your account from the app.

WHO IS IT FOR?

Families, friends, couples: the circle you choose, with everyone's agreement. Miff is not made to follow someone without their knowledge.

GOOD TO KNOW

- In this version, you can be in one Miff at a time, with 3 trusted places.
- Detection of arrivals and departures depends on your phone and its battery-saving settings: it can be late or may not happen. A late status does not mean something is wrong.
- Miff is not an emergency service. In case of danger or serious doubt, call your local emergency number.
- An account only works on one phone at a time.
- Available in French and English.

A question, an idea? Write to us from the app's Settings.
```

### Release notes (EN)

```
First version of Miff.

- Create your Miff or join a loved one's with its secret code.
- Choose trusted places.
- With your agreement, your arrivals and departures are shared with your circle, with nothing to do.
- Publish a photo of the moment whenever you like (visible for 24 hours).
- Available in French and English.

An idea, a problem? Write to us from Settings.
```

---

## 3. Éléments graphiques et informations de gestion (À FOURNIR par le PM)

| Élément | Exigence (page officielle Google Play du 2026-11-07) | État |
|---|---|---|
| Icône | 512 x 512 px, PNG 32 bits avec alpha, 1024 Ko au plus | À FOURNIR (l'icône Miff existe dans l'app ; export 512 à produire par le PM) |
| Image de présentation (« feature graphic ») | 1024 x 500 px, JPEG ou PNG 24 bits sans alpha | À FOURNIR (brief dans `screenshots-brief.md`) |
| Captures d'écran téléphone | Voir `screenshots-brief.md` | À FOURNIR |
| Vidéo de présentation | Facultative ; lien YouTube public ou non répertorié, sans restriction d'âge, publicités désactivées | Facultative, à décider |
| Adresse e-mail de contact | Obligatoire. Contact public provisoire acté : `tomessa.mfouka@gmail.com` (CLAUDE.md) | À confirmer |
| Site web | **À FOURNIR.** Proposition : `https://miff-3d283.web.app/fr/` (adresse prévue par `web/site.config.json`), **non déployée** à ce jour, déploiement soumis à l'accord explicite du PM | À FOURNIR |
| Téléphone | Ne pas inventer ; aucun numéro n'est acté | À FOURNIR ou laisser vide si la console l'accepte |
| URL de la politique de confidentialité | **À FOURNIR.** Pages prévues : `/fr/confidentialite` et `/en/privacy` | À FOURNIR |
| URL de suppression de compte (exigée par Google en plus du chemin dans l'app) | **À FOURNIR.** Pages prévues : `/fr/supprimer-mon-compte` et `/en/delete-account` | À FOURNIR |
| Accès à l'app pour la revue (identifiants de démonstration) | **À FOURNIR** : **deux comptes** dans la même Miff, chacun avec sa position publiée, sans double authentification (`update-photo-declarations.md` §4 et §13) | À FOURNIR |
| Statut « professionnel » (trader) et coordonnées publiques | **Non vérifié côté Google** : la page d'aide Google sur le statut de « trader » n'a pas pu être lue le 2026-11-07 (erreur 404 sur l'adresse tentée). Côté Apple, la règle est lue (voir `store-listing-ios.md` §4). À lire dans la Play Console au moment de la création de la fiche ; point de décision `decisions-stores-avant-soumission.md`, n° 5 | À décider |

Textes alternatifs des images (140 caractères au plus) et légendes (moins de 20 % de l'image) : voir `screenshots-brief.md`.

---

## 4. Catégorie, étiquettes, public cible, classification

### Catégorie (proposition)

**Style de vie (Lifestyle)**. Alternative : Social. Même raisonnement que pour iOS (voir `store-listing-ios.md`, section 5) : on évite de se positionner en réseau social, qui attire des attentes que le produit ne remplit pas. C'est une hypothèse, non prouvée (`aso-keywords.md`, H5). Les « étiquettes » (tags) de la fiche sont à choisir dans la liste proposée par la Play Console ; je n'ai pas la liste exacte et ne l'invente pas.

### Public cible et contenu (déclaration « Public cible »)

- **Proposition : ne pas cibler les enfants de moins de 13 ans** : choisir les tranches 13-15, 16-17 et 18 ans et plus (ou 16 ans et plus, au choix du PM). Les six tranches proposées par la console sont (page Google « Target audience », lue le 2026-11-07) : 5 ans et moins, 6-8, 9-12, 13-15, 16-17, 18 et plus. Raisons : (1) Miff ne vérifie pas l'âge et n'impose aucun âge minimum (risque J2 assumé par le PM, RGPD art. 8 : 15 ans en France) ; (2) **vérifié le 2026-11-07** : si une tranche d'enfants est cochée, la politique « Familles » s'applique (« if one of the target audiences for your app is children »), avec une obligation de déclarer toute collecte d'informations personnelles ou sensibles d'enfants, y compris par les SDK, et l'interdiction de demander la localisation pour les apps qui visent **uniquement** les enfants ; les apps mixtes (enfants et plus âgés) ont d'autres obligations (écran d'âge neutre pour les publicités, identifiants). Un produit de partage de position en arrière-plan n'a pas de raison de s'y exposer.
- **Si la fiche ou l'imagerie plaît aux enfants** alors que la tranche déclarée les exclut, Google peut refuser l'app (« Google Play may reject it ») : retirer ces éléments, ou changer de tranche. La page précise aussi que, selon la loi locale, des utilisateurs jusqu'à 21 ans peuvent être considérés comme des enfants : la tranche choisie ne règle pas tout.
- **Risque** : les parents d'un enfant de moins de 13 ans ne seront pas visés par la fiche. Le texte reste honnête (« avec l'accord de chacun »).
- Ne pas utiliser d'images ou de personnages qui attirent les enfants.
- **Cohérence** : mêmes réponses qu'Apple (`store-listing-ios.md` §6). Voir l'incohérence à connaître avec « Miff n'impose pas d'âge minimum » dans les CGU et la politique.

### Classification du contenu (questionnaire IARC)

Réponses proposées (hypothèses à confirmer en remplissant le questionnaire réel) :

| Rubrique | Réponse |
|---|---|
| Violence, sexualité, langage, substances, jeux d'argent | Aucun contenu |
| Les utilisateurs peuvent interagir / échanger du contenu | **Oui** (une photo, sans texte, visible 24 h des membres d'une Miff, groupe privé fermé par un code ; ni commentaires, ni messagerie) |
| Partage de la position avec d'autres utilisateurs | **Oui** (c'est le coeur du produit : la dernière position connue, et l'endroit de la photo, vus des membres qui partagent aussi ; le dire honnêtement) |
| Achats numériques | Non au moment de la soumission (changer le jour où Miff+ est activé) |
| Publicité | Non |

**Résultat probable** : « Tous publics » avec les mentions « Les utilisateurs interagissent » et « Partage de la position ». À confirmer. Ces mentions sont **normales et honnêtes** ; ne pas les contourner (une réponse inexacte au questionnaire est un motif de retrait).

### Déclarations de la console (renvois, sans les recopier)

Ces déclarations sont du ressort de `release-manager` et doivent reprendre **exactement** les fichiers déjà arrêtés : section « Sécurité des données » (`docs/release/google-play-data-safety.md`), formulaire de localisation en arrière-plan avec vidéo (`docs/release/google-play-localisation-arriere-plan.md`), déclarations liées à la photo de mise à jour (`docs/release/update-photo-declarations.md`), ID de publicité = Non (`docs/legal/README.md` §3, action 4), publicités = Non, application financière / santé / gouvernementale = Non (à confirmer).

---

## 5. Contraintes de calendrier propres à Google Play (voir `launch-plan.md`)

**Vérifié le 2026-11-07 sur la page d'aide officielle de Google** (`support.google.com/googleplay/android-developer/answer/14151465`, lue directement) : « Google Play requires personal developer accounts created after November 13, 2023, to test their apps. » ; « At least 12 testers must be opted in to your closed test when you apply for production access » ; ils doivent être inscrits « continuously for the preceding 14 days » ; un testeur qui s'inscrit, teste moins de 14 jours puis se désinscrit **ne compte pas**.

**Ce que la page ne dit pas** (donc non vérifié) : (a) si la règle vaut **pour chaque nouvelle app** d'un compte ou seulement pour la première (la page parle d'« son app » au singulier) ; (b) le sort des comptes « organisation » (la page n'en parle pas ; des sources tierces disent qu'ils en sont exemptés, non vérifié chez Google). **Ce que je ne sais pas non plus** : la date de création et le type (personnel ou organisation) du compte Google Play du PM, créé pour Ghostrun. Le PM le lit dans la Play Console (détails du compte ; l'intitulé exact du menu n'est pas vérifié) ; **tant qu'il ne l'a pas fait, il faut planifier comme si la règle s'appliquait à Miff** (au moins 14 jours entre le premier testeur inscrit et la demande de production, avec 12 vraies personnes). Les services de « testeurs à louer » ne sont pas une option (voir `launch-plan.md`).

---

## 6. Checklist avant soumission (côté marketing)

- [ ] Section 0 de `store-listing-ios.md` recoupée pour Android (C1 à C15) le jour de la soumission.
- [ ] La description contient toujours la phrase de divulgation de la localisation en arrière-plan (première puce de « Tu gardes la main »), identique à celle de l'app et du site.
- [ ] Description courte et titre recomptés dans la console.
- [ ] Icône 512, image de présentation 1024 x 500, 4 captures au moins en 1080 px (voir `screenshots-brief.md`).
- [ ] URL de politique et de suppression de compte en ligne.
- [ ] Public cible et IARC décidés.
- [ ] Type et date de création du compte Google Play lus dans la console ; test fermé de 14 jours (12 testeurs) planifié si la règle s'applique.
- [ ] Aucune mention de Miff+, d'essai, de prix, de « Économisez 30 % » ni de « presque 30 % d'économie » (décidé le 2026-11-07), de notifications.
- [ ] Nom « Miff » : vérification d'antériorité (risque J18) ; une app « MIFF » existe dans l'App Store (festival du film de Moscou), aucune vérification faite côté Google Play (`decisions-stores-avant-soumission.md`, n° 9).
