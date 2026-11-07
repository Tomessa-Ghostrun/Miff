# Brief de captures d'écran et de visuels de fiche

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), destiné au PM pour la production visuelle finale. Rien n'est produit ni publié.**

Ce brief dit **quoi montrer, dans quel ordre, avec quel message**. Il ne remplace pas le design : les maquettes (`design/exports/*`) et le design system restent la référence visuelle.

**Recoupé le 2026-11-07 par `release-manager`** avec `docs/release/update-photo-declarations.md` §10 (liste S1 à S9 et V1), `docs/release/app-store-confidentialite-et-revue.md` et le code. Trois constats : (1) l'**iOS n'a ni Feed, ni Update, ni Réglages** dans `main` : les captures iOS des écrans 1, 2, 6, 7 et 8 sont **impossibles** tant que ces écrans ne sont pas livrés (l'Android les a) ; (2) la photo de mise à jour **n'a pas de texte** (« petit mot » retiré des légendes) et porte **toujours son endroit** : l'écran 6 ne cache plus l'endroit, il le montre, avec la mention « 24 h » ; (3) aucun écran ne doit suggérer un signalement ou un blocage (ils n'existent pas, décisions P1 et P2).

---

## 1. Principes

1. **Captures de l'app réelle**, pas de maquettes Figma présentées comme l'app. Google Play exige que captures et vidéo reflètent l'expérience réelle (page « Metadata policy », vérifiée le 2026-11-07) ; Apple a la même attente. On peut **encadrer** les captures (fond de couleur, légende), pas montrer des écrans qui n'existent pas.
2. **Ne montrer que ce qui est livré.** Pas de notifications, pas d'abonnement ni de prix, pas de « Économisez 30 % », pas de sélecteur à plusieurs Miffs (l'écran à plusieurs Miffs relève de l'offre payante non active). Un écran qui n'est pas dans la version soumise ne figure pas dans les captures.
3. **Données de démonstration fictives** : prénoms inventés (ceux déjà utilisés dans la page d'accueil du dépôt : Thomas, Amandine, « Maman »), nom de Miff fictif (« Famille Alberta », déjà utilisé), adresses fictives, aucun numéro de téléphone réel, aucune vraie position. **Jamais** de données d'un proche ou d'un testeur réel.
4. **Photos** : uniquement des photos dont la licence permet l'usage commercial (les photos de la page d'accueil viennent d'une banque libre ; voir `design/exports/onboarding-photos/CREDITS.md`). Des personnes identifiables apparaissent : **le PM doit vérifier que la licence couvre l'usage sur une fiche de store** (je ne l'ai pas vérifié). Ne pas utiliser d'enfants.
5. **Légendes courtes, calmes, en tutoiement**, sans promesse de sécurité. Chaque légende est écrite en FR et en EN. Google Play : une légende ne doit pas occuper plus de 20 % de l'image.
6. **Thème clair** (l'app n'a pas de thème sombre à ce stade, décision de `feed-profil-reglages.md`).
7. **Barre d'état propre** : heure et batterie neutres, pas d'opérateur, pas de notifications visibles.
8. **Texte lisible en miniature** : les trois premières captures sont celles qu'on voit dans les résultats de recherche (hypothèse de pratique courante, non vérifiée par une source officielle consultée) ; elles portent l'essentiel.

---

## 2. Ordre narratif et écrans à capturer

Ordre pensé pour les **trois premières captures** (promesse, bénéfice, confiance), puis le détail du fonctionnement. Les deux stores utilisent la même histoire.

| # | Écran à capturer (parcours réel) | Légende FR | Légende EN | Pourquoi ici | Dépendances / à confirmer |
|---|---|---|---|---|---|
| **1** | **Feed** : en-tête avec le nom de la Miff, un bandeau calme, deux cartes de proches (statut « À la maison », « À l'école », heure, éventuelle photo) | Des nouvelles de tes proches | News from your loved ones | La promesse en un coup d'oeil | Feed **Android** livré ; **iOS absent**. Libellé réel du bandeau sur Android : « Tout va bien » (`feed_bubble_all_good`), donc sans promesse de sécurité (note 3) |
| **2** | **Carte d'un proche avec statut automatique** (gros plan : « À la maison depuis 12 min » ou équivalent réel) | Les nouvelles arrivent toutes seules | News arrives by itself | Le bénéfice clé : rien à écrire | Intitulés réels des statuts à reprendre tels quels |
| **3** | **Écran d'explication de la localisation** avant la demande du système (texte validé du ticket Lieux : « même quand l'app est fermée », qui voit quoi, possibilité d'arrêter) | Ce que voient tes proches, expliqué avant la demande | What your loved ones see, explained before the request | La confiance : consentement visible, dès la 3e image | Version **tutoiement** réellement livrée (les maquettes originales sont au vouvoiement : `design/exports/pop-ins/README.md`). **Légende changée** (« Tu choisis ce que tu partages » pouvait se lire comme « la localisation est facultative », alors que « Toujours » est obligatoire). L'écran réel (« Savoir que tu vas bien ! ») est dans `docs/release/google-play-localisation-arriere-plan.md` §5 et `app-store-confidentialite-et-revue.md` §4 : ne montrer que le texte réellement livré |
| **4** | **Rejoindre une Miff** (saisie du code secret) ou **Créer une Miff** (code à partager) | Un code secret, et tu rejoins ton cercle | A secret code to join your circle | Comment commence l'usage | Maquettes de repérage : `design/exports/miff/` (« Rejoindre une Miff », « Nouvelle Miff ») |
| **5** | **Nouveau lieu de confiance** (nom, adresse, périmètre sur une carte) | Choisis tes lieux de confiance | Choose your trusted places | La configuration | Écran réel du ticket Lieux ; **ne pas** montrer une vraie adresse |
| **6** | **Donner des nouvelles** : aperçu de la photo avec la ligne de visibilité (« Tes proches verront cette photo pendant 24 h. Ceux qui partagent aussi leur position verront où tu l'as prise. »), bouton « Publier » | Une photo du moment | A photo of the moment | Donner des nouvelles quand on veut, en disant d'emblée « 24 h » et « où tu l'as prise » | Maquettes : `design/exports/update/`. Livré sur Android seulement (PR #93) ; **pas de champ de texte** en v1. Texte d'écran à aligner sur `update-photo-declarations.md` §9.3 (S1 à S3). Comptes de test, lieu fictif, personne consentante, aucun enfant |
| **7** | **Arrêter le partage** (écran de la Miff ou Réglages) | Tu peux arrêter à tout moment | Stop whenever you like | Révocabilité, ton rassurant | Écran réel (non maquetté à l'origine : `design/exports/miff/README.md`). Android : « Arrêter de partager ton statut avec cette Miff ? » ; iOS : écran de la Miff livré, à constater |
| (8) | *Optionnel* : **Réglages > Confidentialité** (statistiques, liens, contact) | Ta vie privée, expliquée simplement | Your privacy, explained simply | Seulement si une 8e image est utile | Écran livré par ONB-3b (Android) ; **iOS : aucun écran de Réglages dans `main`** |

### Notes

1. **Écrans plutôt que blocs de texte** : sept images suffisent. Apple en autorise jusqu'à 10, Google Play jusqu'à 8 par type d'appareil.
2. **Gradient et cartes** : reprendre les couleurs du design system, jamais de couleur de statut seule pour porter l'information (règle d'accessibilité du ticket Feed).
3. **Le ton des bandeaux** : le ticket Feed (`docs/tickets/feed-profil-reglages.md`, §4D et Q12) a demandé de ne jamais afficher « Tout le monde est en sécurité » sans statuts connus et a proposé « Tout va bien ». Vérifié sur Android le 2026-11-07 : le libellé livré est « Tout va bien ». La capture doit montrer ce que l'app affiche **réellement** ; si le libellé réel change pour promettre une sécurité, le signaler au PM avant publication (voir `positioning.md`, section 4).
4. **Escalade et rouges** : ne pas montrer d'écrans orange ou rouge dans les captures (ton anxiogène et hors fiche). Seul l'état normal est mis en avant.
5. **Localisation de la photo** : `docs/release/update-photo-declarations.md` a été recoupé le 2026-11-07. Une photo est **toujours** publiée avec son endroit (décision du PM P7) : on ne cache donc pas cet endroit, on le dit (ligne de visibilité de l'aperçu, mention « 24 h »). Les captures S5 (photo en plein écran avec « Voir où c'était »), S6 (carte du système sur le lieu de la photo) et S7 (message « Partage ta position pour voir cette photo ») de ce document relèvent de la **revue des stores**, pas de la fiche ; elles dépendent de FEED-4, non livré. Lieux et positions **fictifs** (position simulée), jamais le domicile du PM.
6. **Une seule histoire par store** : mêmes sept écrans pour iOS et Android, captures prises sur chaque plateforme (pas de capture iOS sur la fiche Android).

---

## 3. Formats exigés par les stores

Sources : pages officielles consultées le 2026-11-07. **Les exigences changent** : revérifier dans App Store Connect et la Play Console au moment du dépôt.

### App Store (iPhone)

| Point | Exigence |
|---|---|
| Nombre | 1 au minimum, 10 au maximum par taille d'appareil |
| Formats de fichier | `.jpeg`, `.jpg`, `.png` ; **pas de canal alpha ni de transparence** |
| Tailles à produire (portrait) | iPhone à Dynamic Island (grand écran) : **1320 x 2868**, 1290 x 2796 ou 1260 x 2736 ; iPhone à Dynamic Island (écran moyen) : **1206 x 2622** ou 1179 x 2556 ; iPhone à Face ID (grand écran) : 1284 x 2778 ou 1242 x 2688 ; iPhone à Face ID (écran moyen) : 1170 x 2532, 1125 x 2436 ou 1080 x 2340 |
| Taille obligatoire | La page officielle indique qu'**au moins une capture pour l'iPhone à Dynamic Island (écran moyen)** est requise pour soumettre, et que le jeu « Face ID grand écran » est requis si l'app tourne sur iPhone **et** qu'aucun jeu « Dynamic Island grand écran » n'est fourni. Si une taille manque, App Store Connect met à l'échelle depuis une taille plus grande |
| iPad | Un jeu **13 pouces** (2064 x 2752 ou 2048 x 2732) est requis **seulement si l'app tourne sur iPad** (« iPad (if your app supports iPadOS) », page Apple relue le 2026-11-07). **Constaté dans `ios/project.yml` : `TARGETED_DEVICE_FAMILY: "1"` (iPhone seulement)** : aucune capture iPad à produire tant que cela ne change pas |
| Recommandation | Produire **deux jeux** : 1320 x 2868 et 1206 x 2622, qui couvrent le grand et le moyen écran Dynamic Island et permettent de répondre aux deux cas décrits ci-dessus |

### Google Play (téléphone)

| Point | Exigence |
|---|---|
| Nombre | 2 au minimum (tous types d'appareils confondus) pour publier ; **8 au maximum par type d'appareil** |
| Format | JPEG ou PNG 24 bits (sans alpha) |
| Dimensions | Chaque côté entre 320 et 3840 px ; **le grand côté ne dépasse pas deux fois le petit côté** |
| Éligibilité aux emplacements étendus (pour une app) | Au moins **4 captures** d'au moins 1080 px, en 9:16 (portrait, 1080 x 1920 minimum) ou 16:9 (paysage) |
| Image de présentation | **1024 x 500 px**, JPEG ou PNG 24 bits sans alpha |
| Icône | 512 x 512 px, PNG 32 bits avec alpha, 1024 Ko au plus |
| Vidéo | Facultative : lien YouTube public ou non répertorié, non limité par l'âge, publicités désactivées ; seules les 30 premières secondes se lancent automatiquement |
| Textes alternatifs | 140 caractères au plus (image de présentation et captures) |
| Tablettes / Chromebooks | Au moins 4 captures seulement **si l'app déclare les grands écrans** ; à confirmer avec `android-dev` (sinon, rien à produire) |

### Pièges pratiques propres au matériel du PM (hypothèses à vérifier)

- **OPPO Find X5 Lite** : résolution probablement 1080 x 2400, soit un rapport de 2,22 : **au-delà du double autorisé par Google Play**. Une capture brute serait refusée. Solutions gratuites : encadrer chaque capture dans une image 1080 x 1920 (fond de couleur et légende) ou prendre les captures sur l'émulateur d'Android Studio avec un profil en 16:9 ou 1080 x 2160.
- **iPhone 11 Pro** : captures natives en 1125 x 2436 (taille « Face ID écran moyen ») ; elles peuvent servir de matière, mais la fiche exigera au moins les tailles Dynamic Island décrites plus haut. Pas de Mac : composer les visuels dans Figma (fichier de travail « Miff - Claude », jamais l'original) aux dimensions exactes puis exporter en PNG sans transparence.

---

## 4. Textes alternatifs (accessibilité, Google Play : 140 caractères au plus)

Recomptés à la main, à revérifier dans la console.

| # | FR | EN |
|---|---|---|
| 1 | Le fil de Miff : deux proches avec leur statut, « À la maison » et « À l'école ». | The Miff feed: two loved ones with their status, "At home" and "At school". |
| 2 | Gros plan sur la carte d'un proche : son statut se met à jour tout seul. | Close-up of a loved one's card: their status updates by itself. |
| 3 | L'écran qui explique la localisation avant que le téléphone ne la demande, et ce que voient les proches. | The screen explaining location before the phone asks for it, and what your loved ones see. |
| 4 | Saisie du code secret pour rejoindre une Miff. | Entering the secret code to join a Miff. |
| 5 | Création d'un lieu de confiance : nom, adresse et périmètre. | Creating a trusted place: name, address and radius. |
| 6 | Aperçu d'une photo avant de la publier : visible 24 h, avec l'endroit où elle a été prise. | Preview of a photo before publishing: visible for 24 hours, with the place where it was taken. |
| 7 | Réglage pour arrêter le partage à tout moment. | Setting to stop sharing at any time. |

---

## 5. Image de présentation Google Play (1024 x 500)

- Fond uni aux couleurs du design system, logo Miff, phrase « Des nouvelles, sans déranger. » / « News, without bothering anyone. », **aucun** prix, aucune mention de classement, aucun visage d'enfant.
- Une seule capture simplifiée (le Feed) sur la droite ; texte lisible à petite taille ; marges de sécurité (certaines surfaces recadrent l'image).
- Version FR et version EN.

---

## 6. Vidéo de présentation (facultative, à décider)

Si le PM veut une vidéo (gratuite à produire avec une capture d'écran de l'app et un montage simple) :

- Durée : 20 à 30 secondes (Google ne lance automatiquement que les 30 premières secondes).
- Trois plans : (1) le Feed et ses deux cartes ; (2) un statut qui change tout seul ; (3) l'écran de consentement et l'arrêt du partage. Phrase de fin : « Des nouvelles, sans déranger. »
- **Pas de musique sous licence, pas de voix off synthétique non vérifiée, pas de marque tierce.** Sous-titres FR et EN.
- Les caractéristiques techniques d'une vidéo « App Preview » sur l'App Store (durée, formats) n'ont **pas** été vérifiées : à lire dans App Store Connect avant de produire. **Vérifié le 2026-11-07** (Apple 2.3.4) : « previews may only use video screen captures of the app itself » ; (2.3.3) les captures « should show the app in use, and not merely the title art, login page, or splash screen » ; (2.3.10) ne citer, dans les métadonnées, ni nom ni icône d'une autre plateforme mobile.
- **Vidéos de revue (distinctes de la vidéo marketing)** : la vidéo de localisation en arrière-plan de Google (30 s au plus, Android réel) et la vidéo de revue d'Apple (60 s au plus, iPhone réel) sont décrites dans `docs/release/` ; elles ne montrent ni signalement ni blocage, qui n'existent pas.
- Pour la **demande de localisation en arrière-plan de Google Play**, une vidéo de démonstration distincte est exigée par la console : elle relève de `release-manager` (`docs/release/google-play-localisation-arriere-plan.md`) et ne doit pas être confondue avec cette vidéo marketing.

---

## 7. Recette du PM avant dépôt

- [ ] Sept captures par plateforme et par langue (FR et EN) : l'app doit s'afficher dans la langue de la légende (la langue de l'app se choisit dans Réglages > Langue). **Sur iOS, seulement quand le Feed, l'Update et les Réglages existent** ; jamais de capture Android présentée comme iOS.
- [ ] Aucune légende ni image qui suggère un signalement, un blocage, une modération, un suivi en direct ou une localisation facultative.
- [ ] Aucune donnée réelle visible (nom, numéro, adresse, position, e-mail).
- [ ] Aucune capture montrant un état non livré ou un libellé qui promet une sécurité.
- [ ] Tailles exactes vérifiées dans la console, PNG sans transparence.
- [ ] Licences des photos vérifiées.
