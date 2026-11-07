# Fiche Google Play (Android) : brouillon

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), à valider par le PM puis à intégrer par `release-manager`. Aucune soumission, aucun compte, aucun achat : rien n'est publié.**
Langue principale : français. Langue de repli : anglais.

**À lire avant d'utiliser ce fichier**
- Les déclarations déjà arrêtées dans `docs/release/` (notamment `google-play-data-safety.md`, `google-play-localisation-arriere-plan.md` et `update-photo-declarations.md`) **ne doivent jamais être contredites**. **Je n'ai pas pu les relire** (absentes de la copie du dépôt dont je disposais). La description reste donc volontairement **minimale** sur la localisation et les données ; la section 0 de `store-listing-ios.md` (C1 à C14) vaut aussi pour Android et doit être recoupée avec ces fichiers.
- Notifications push et abonnement (Miff+) **non actifs** : aucune mention. Aucune marque tierce.
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
Si tu l'acceptes, ton téléphone repère quand tu arrives dans un lieu de confiance ou que tu en pars. Ton cercle voit un statut tout simple, comme « À la maison », sans que tu aies rien à écrire.

4. Donne des nouvelles quand tu veux
Une photo, un petit mot : une mise à jour en un geste.

5. Un coup d'oeil, et c'est fait
Tu ouvres Miff, tu vois où en sont tes proches, tu refermes. Si l'envie t'en prend, un bouton t'envoie vers ton application habituelle pour écrire un message ou appeler.

TU GARDES LA MAIN

- Le partage de ta localisation est ton choix, Miff par Miff. Miff t'explique à quoi il sert avant que ton téléphone te demande l'autorisation.
- Pour repérer tes arrivées et tes départs quand l'app est fermée, Miff te demande l'autorisation d'utiliser ta localisation en arrière-plan. C'est facultatif : sans elle, tu envoies tes mises à jour à la main.
- Tu peux arrêter le partage à tout moment, sans te justifier.
- Seuls les membres de ta Miff voient ce que tu partages. On ne rejoint une Miff qu'avec son code secret.
- Les photos de mise à jour ne sont plus visibles après 24 heures.
- Aucune publicité, aucune revente de données.
- Tu peux supprimer ton compte depuis l'app.

POUR QUI ?

En famille, entre amis, en couple : le cercle que tu choisis, avec l'accord de chacun. Miff n'est pas fait pour suivre quelqu'un à son insu.

BON À SAVOIR

- Dans cette version, tu peux être dans une Miff à la fois, avec un lieu de confiance.
- La détection des arrivées et des départs dépend de ton téléphone et de ses réglages d'économie d'énergie : elle peut être en retard ou ne pas se produire. Un statut en retard ne veut pas dire qu'il y a un problème.
- Miff n'est pas un service d'urgence. En cas de danger ou de doute sérieux, appelle le 112.
- Disponible en français et en anglais.

Une question, une idée ? Écris-nous depuis les Réglages de l'app.
```

Différences avec la fiche iOS : « iPhone » devient « téléphone » ; ajout d'une phrase explicite sur la localisation en arrière-plan (Google exige une information visible, dans l'app avant la demande ; la rappeler dans la fiche n'est pas obligatoire mais évite une surprise à la revue) ; mention des réglages d'économie d'énergie des fabricants, qui sont le risque de fiabilité le plus probable sur Android (appareil de test du PM : un téléphone avec surcouche constructeur).

### Notes de version (FR)

```
Première version de Miff.

- Crée ta Miff ou rejoins celle d'un proche avec son code secret.
- Choisis des lieux de confiance.
- Si tu l'acceptes, tes arrivées et tes départs sont partagés avec ton cercle, sans rien faire.
- Envoie une mise à jour (photo, petit mot) quand tu veux.
- Disponible en français et en anglais.

Une idée, un souci ? Écris-nous depuis les Réglages.
```

---

## 2. Champs de la fiche : anglais (langue de repli)

| Champ | Texte | Caractères |
|---|---|---|
| **App name** | Miff - Trusted Circle | 21 / 30 |
| **Short description** | News from your loved ones, without bothering them. Sharing is your choice. | 74 / 80 |

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
If you agree to it, your phone notices when you arrive at or leave a trusted place. Your circle sees a very simple status, like "At home", with nothing for you to write.

4. Share news whenever you like
A photo, a short note: an update in one tap.

5. One glance, and done
You open Miff, you see how your loved ones are doing, you close it. If you feel like it, a button takes you to your usual app to write a message or call.

YOU STAY IN CONTROL

- Sharing your location is your choice, Miff by Miff. Miff explains what it is for before your phone asks for permission.
- To notice your arrivals and departures when the app is closed, Miff asks for permission to use your location in the background. It is optional: without it, you send your updates by hand.
- You can stop sharing at any time, without having to explain yourself.
- Only the members of your Miff see what you share. A Miff can only be joined with its secret code.
- Update photos are no longer visible after 24 hours.
- No ads, no data resale.
- You can delete your account from the app.

WHO IS IT FOR?

Families, friends, couples: the circle you choose, with everyone's agreement. Miff is not made to follow someone without their knowledge.

GOOD TO KNOW

- In this version, you can be in one Miff at a time, with one trusted place.
- Detection of arrivals and departures depends on your phone and its battery-saving settings: it can be late or may not happen. A late status does not mean something is wrong.
- Miff is not an emergency service. In case of danger or serious doubt, call your local emergency number.
- Available in French and English.

A question, an idea? Write to us from the app's Settings.
```

### Release notes (EN)

```
First version of Miff.

- Create your Miff or join a loved one's with its secret code.
- Choose trusted places.
- If you agree, your arrivals and departures are shared with your circle, with nothing to do.
- Send an update (photo, short note) whenever you like.
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
| Accès à l'app pour la revue (identifiants de démonstration) | **À FOURNIR** : un compte de démonstration et une Miff d'exemple | À FOURNIR |
| Statut « professionnel » (trader) et coordonnées publiques | Selon les règles européennes, un développeur individuel qui vend (le jour où Miff+ existera) peut devoir afficher publiquement nom, adresse, e-mail. À vérifier dans la console au moment de la création de la fiche ; point de décision du rapport | À décider |

Textes alternatifs des images (140 caractères au plus) et légendes (moins de 20 % de l'image) : voir `screenshots-brief.md`.

---

## 4. Catégorie, étiquettes, public cible, classification

### Catégorie (proposition)

**Style de vie (Lifestyle)**. Alternative : Social. Même raisonnement que pour iOS (voir `store-listing-ios.md`, section 5) : on évite de se positionner en réseau social, qui attire des attentes que le produit ne remplit pas. C'est une hypothèse, non prouvée (`aso-keywords.md`, H5). Les « étiquettes » (tags) de la fiche sont à choisir dans la liste proposée par la Play Console ; je n'ai pas la liste exacte et ne l'invente pas.

### Public cible et contenu (déclaration « Public cible »)

- **Proposition : ne pas cibler les enfants de moins de 13 ans** : choisir les tranches 13-15, 16-17 et 18 ans et plus (ou 16 ans et plus, au choix du PM). Raisons : (1) Miff ne vérifie pas l'âge et n'impose aucun âge minimum (risque J2 assumé par le PM, RGPD art. 8 : 15 ans en France) ; (2) cibler les moins de 13 ans soumet l'app à la politique « Familles » de Google, très stricte, qu'un produit de partage de localisation en arrière-plan aurait du mal à satisfaire (hypothèse : à confirmer sur la page officielle de la politique « Familles » avant de trancher).
- **Risque** : les parents d'un enfant de moins de 13 ans ne seront pas visés par la fiche. Le texte reste honnête (« avec l'accord de chacun »).
- Ne pas utiliser d'images ou de personnages qui attirent les enfants.

### Classification du contenu (questionnaire IARC)

Réponses proposées (hypothèses à confirmer en remplissant le questionnaire réel) :

| Rubrique | Réponse |
|---|---|
| Violence, sexualité, langage, substances, jeux d'argent | Aucun contenu |
| Les utilisateurs peuvent interagir / échanger du contenu | **Oui** (mise à jour photo + texte entre membres d'une Miff, groupe privé) |
| Partage de la position avec d'autres utilisateurs | **Oui** (c'est le coeur du produit : le dire honnêtement) |
| Achats numériques | Non au moment de la soumission (changer le jour où Miff+ est activé) |
| Publicité | Non |

**Résultat probable** : « Tous publics » avec les mentions « Les utilisateurs interagissent » et « Partage de la position ». À confirmer. Ces mentions sont **normales et honnêtes** ; ne pas les contourner (une réponse inexacte au questionnaire est un motif de retrait).

### Déclarations de la console (renvois, sans les recopier)

Ces déclarations sont du ressort de `release-manager` et doivent reprendre **exactement** les fichiers déjà arrêtés : section « Sécurité des données » (`docs/release/google-play-data-safety.md`), formulaire de localisation en arrière-plan avec vidéo (`docs/release/google-play-localisation-arriere-plan.md`), déclarations liées à la photo de mise à jour (`docs/release/update-photo-declarations.md`), ID de publicité = Non (`docs/legal/README.md` §3, action 4), publicités = Non, application financière / santé / gouvernementale = Non (à confirmer).

---

## 5. Contraintes de calendrier propres à Google Play (voir `launch-plan.md`)

D'après la page d'aide officielle de Google (référencée par plusieurs sources consultées le 2026-11-07, contenu à revérifier sur `support.google.com/googleplay/android-developer/answer/14151465`) : pour un **compte développeur personnel créé après le 13 novembre 2023**, il faut mener un **test fermé** avec **au moins 12 testeurs inscrits en continu pendant 14 jours** avant de pouvoir demander l'accès à la production. **Je ne sais pas si le compte existant du PM (créé pour un autre projet) relève de cette règle** : sa date de création et son type (personnel ou organisation) ne sont pas dans les documents que j'ai pu lire. À vérifier dans la console avant de fixer la date de lancement.

---

## 6. Checklist avant soumission (côté marketing)

- [ ] Section 0 de `store-listing-ios.md` recoupée pour Android (C1 à C14).
- [ ] Description courte et titre recomptés dans la console.
- [ ] Icône 512, image de présentation 1024 x 500, 4 captures au moins en 1080 px (voir `screenshots-brief.md`).
- [ ] URL de politique et de suppression de compte en ligne.
- [ ] Public cible et IARC décidés.
- [ ] Test fermé de 14 jours planifié si la règle s'applique.
- [ ] Aucune mention de Miff+, d'essai, de prix, de « Économisez 30 % », de notifications.
- [ ] Nom « Miff » : vérification d'antériorité (risque J18).
