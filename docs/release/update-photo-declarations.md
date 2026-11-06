# Update (photo « Donner des nouvelles ») : ce qu'il faudra déclarer et justifier aux stores

Préparé le 2026-11-06 par `release-manager`, à partir du ticket `docs/tickets/update.md` (décisions D16 à D23), de la règle R-PHOTO-LOC (fusionnée, **non déployée** : `backend/data-model.md`, en-tête, §2.8 et §11) et des dossiers de stores déjà préparés dans ce dossier.

**Rien n'est soumis, rien n'est rempli dans une console, aucun texte légal n'est modifié.** L'Update n'est **pas développé** : tout ce qui suit décrit l'app **telle qu'elle sera** si le ticket est livré tel quel, et doit être revérifié contre le code livré. Rien n'est « prêt à soumettre » : la checklist QA de sortie `docs/qa/release-checklist.md` n'existe pas encore. Ces textes n'ont **pas été relus par un juriste** (décision du PM du 2026-10-23, risque assumé) et ne doivent jamais être présentés comme tels.

Décisions D24 à D26 du 2026-11-06 (viseur plein écran, aperçu un peu plus petit, photo plein écran au toucher depuis le Feed, bouton central en pastille de couleur seule) : annoncées, **pas encore écrites dans le ticket**. Elles ne changent aucune déclaration ; elles changent seulement les captures (§10).

---

## 1. En bref pour le PM

### 1.1 Ce que l'Update ajoute aux déclarations

| Nouveauté | Conséquence côté stores |
|---|---|
| **Caméra intégrée** (permission `CAMERA` / `NSCameraUsageDescription`), alors que tous les dossiers actuels disent « aucune caméra » | Manifeste, `PermissionPrivacyTest`, CI, `PrivacyInfo.xcprivacy`, Data safety, étiquettes Apple : à mettre à jour ensemble (§3, §5, §6) |
| **Photo visible des membres de la Miff pendant 24 h** | Contenu généré par les utilisateurs (UGC) : Apple 1.2 et politique UGC de Google Play s'appliquent (§7) |
| **Lieu de la photo** (lat, lng, heure de mesure), figé à la prise de vue | Donnée la plus sensible du modèle après la position : texte de consentement, textes d'usage iOS, politique, étiquettes (§4, §7, §9) |
| **Photos de personnes, dont des mineurs et des tiers**, sans âge minimum | Apple 5.1.4, classification d'âge, public cible Google (§8) |

### 1.2 Points de décision (ma recommandation en gras)

| # | Question | Options | Recommandation |
|---|---|---|---|
| **P1** | **Signaler une photo** : comment ? (Apple 1.2 et Google UGC l'exigent ; aujourd'hui seulement « écris-nous » dans les CGU art. 8) | **A** : bouton « Signaler cette photo » dans l'app qui ouvre un e-mail pré-rempli (identifiants de la Miff et du membre, heure ; **pas** la photo) vers ton adresse de contact, zéro coût, zéro règle Firestore. **B** : écriture dans une collection Firestore `reports` (règle à créer par `backend-data`, lecture dans la console, nouvelle donnée à déclarer). | **A** pour la bêta et la première soumission, **B** préparé en ticket si un relecteur refuse. A est plus faible que B face à « in-app system » de Google (§7). |
| **P2** | **Bloquer un membre** : que veut dire « bloquer » dans un cercle fermé ? | Existe déjà : quitter la Miff ; le **créateur** retire un membre (code renouvelé). Manque : un membre non créateur ne peut rien contre le créateur. **Proposé** : « Ne plus voir les photos de {Prénom} » (réglage **local** au téléphone, sans backend). | **Ajouter le réglage local** (ticket FEED-4), l'écrire dans la note de revue, et dire honnêtement que bloquer « du service » = suspension manuelle par toi (CGU art. 12). |
| **P3** | **Délai de réponse** à un signalement (Apple : « timely responses ») | « sous 48 h » / « sous 72 h » / « dès que possible » | Écrire **un délai que tu peux tenir**. Le README légal demande aujourd'hui de consulter la boîte « au moins une fois par semaine » (J17) : trop lent pour un signalement de photo. **Recommandation : consulter chaque jour pendant les semaines de lancement, annoncer 72 h.** |
| **P4** | **Filtrage automatique des images** (Apple 1.2 : « A method for filtering objectionable material ») | Impossible gratuitement : aucune Cloud Function (Spark), et le ticket Update exclut toute analyse du contenu | **Assumer** : pas de filtre automatique ; argumentaire structurel (prise de vue en direct uniquement, jamais de galerie, cercle fermé par code, 24 h, aucune découverte publique) + signalement + retrait. **Risque de rejet réel, écrit comme tel** (§11, R1). |
| **P5** | **Texte de consentement** : amender v3 ou passer en v4 (D20) | v3 amendé si **aucun** testeur externe ne l'a accepté ; sinon v4 | Le code Android contient déjà `2026-11-v03` (tests `PermissionDomainTest`, `PermissionScreensTest`) : **vérifie dans Play Console (pistes de test) et TestFlight qu'aucune personne extérieure n'a reçu un build avec v3**. Si c'est le cas : v3 amendé (texte proposé au §9.3). |
| **P6** | **Aligner la politique et les CGU** sur l'Update (texte de 140 caractères abandonné, « supprimée par un membre » faux, « dernière position seulement » inexact) | Liste de 17 passages de la politique et 6 des CGU au §9 | **Oui**, avant toute publication des textes. C'est toi qui modifies `docs/legal/` (décision de cette tâche). |
| **P7** | **Retirer le lieu d'une photo, photo par photo ?** (pas prévu : le lieu est joint automatiquement quand les conditions sont réunies, avec une ligne d'information à l'aperçu) | Ajouter un interrupteur à l'aperçu / rester simple | **Rester simple** : les proches voient déjà ta dernière position (réciprocité) ; la ligne d'information suffit. À confirmer, car c'est le point le plus sensible vis-à-vis du consentement. |
| **P8** | **iOS : repli « appareil photo du système »** (ticket V10) | Il faut vérifier qu'il évite vraiment la permission | Voir §3.4 : sur iPhone il est **probable** que le sélecteur système exige aussi l'autorisation caméra ; `ios-dev` le vérifie avant de promettre une alternative. |

### 1.3 Risques de rejet principaux (détail et parades au §11)

1. **Apple 1.2 sans filtrage automatique** ni signalement dans l'app : risque moyen à élevé selon P1 et P4.
2. **Google UGC : signalement ET blocage « dans l'app »** : élevé si on ne livre rien dans l'app.
3. **Incohérence déclarations / app / textes** (caméra, photo « par un membre », texte de 140 caractères, position seule) : motif de rejet le plus évitable.
4. **Photos et lieu de personnes mineures sans contrôle d'âge** (Apple 5.1.4, Google public cible) : risque J2/J31, **assumé**.
5. **Consentement qui ne cite pas la photo localisée** (Apple 5.1.1(ii) et 5.1.5) : évité par D20.

---

## 2. Ce que l'Update fait réellement (faits à déclarer, sources : ticket et modèle de données)

À recopier tel quel dans les formulaires **seulement après** vérification dans le code livré.

| Fait | Valeur | Source |
|---|---|---|
| Prise de vue | Caméra intégrée (CameraX / AVFoundation), **en direct uniquement** ; aucune photo de galerie ; aucune permission de photothèque ni de stockage ; la photo vit en mémoire jusqu'à la publication | ticket §5.3, §5.6, P4 |
| Métadonnées | JPEG **ré-encodé depuis les pixels** : ni EXIF, ni GPS, ni date, ni modèle (**à prouver par un test d'octets**, critère V3 du ticket) | ticket §5.6 |
| Taille | ≤ 150 000 octets, portrait 3:4, côté long ≤ 1 024 px | `firestore.rules` `validPhoto`, ticket V7 |
| Hébergement | Octets dans Firestore (`miffs/{id}/photos/{uid}`), région Paris (europe-west9), **sans Cloud Storage** ; une copie par Miff où la personne partage ; **chaque photo écrase la précédente** | `data-model.md` §2.8 |
| Visibilité | Membres de la Miff dont le partage est actif ; **le créateur n'a aucun droit de plus** | `data-model.md` §2.8, §9 |
| Durée | Illisible après 24 h (23 h côté client) ; suppression physique « au mieux » (par l'app de son auteur, au lancement) ; supprimée avec l'arrêt du partage, le départ, le retrait, la suppression de la Miff ou du compte | `data-model.md` §2.8, ticket §5.8, C12 |
| Lieu de la photo | `lat`, `lng` (5 décimales), `measuredAt` (heure de la **mesure**), **facultatifs, ensemble ou pas du tout** ; une seule mesure au moment de l'obturateur (≤ 3 s), écrite seulement si récente et si consentement v3+ et partage actif ; sinon photo **sans** lieu et sans message d'erreur | `data-model.md` en-tête (R-PHOTO-LOC), ticket §6.5, D23 |
| Lecture du lieu | Photo localisée lisible seulement de l'auteur ou d'un membre qui a **publié sa propre position** (réciprocité). Conséquence produit : un membre qui ne partage pas sa position **ne peut pas ouvrir une photo localisée du tout** (« Partage ta position pour voir cette photo. ») | `data-model.md` « Lecture » |
| Ce que le serveur ne vérifie pas | Que la position ou l'heure soient vraies (client modifié), que la photo corresponde au lieu | `data-model.md` « Limites connues » |
| Analytics, plantages, journaux | Aucun événement, aucune coordonnée, aucun contenu de photo (D12) | ticket D12 |
| Analyse du contenu | **Aucune** (pas de détection de visage, pas d'IA) | ticket §5.6 |
| Texte libre avec la photo | **Aucun en v1** (`updateText = null`) | ticket C4 |
| Notification | **Aucune** | ticket D10 |
| Limite assumée | Un proche peut faire une capture d'écran : « illisible après 24 h » vaut pour l'app, pas pour la capture | ticket R5, CGU art. 8 |

---

## 3. Permission caméra

### 3.1 Android : `CAMERA`

- **Déclarer** `android.permission.CAMERA` dans le manifeste, et **rien d'autre de nouveau** : ni `READ_MEDIA_IMAGES`, ni `READ_MEDIA_VIDEO`, ni `WRITE_EXTERNAL_STORAGE`, ni `RECORD_AUDIO` (critère P4 du ticket).
- **Ajouter `<uses-feature android:name="android.hardware.camera" android:required="false" />`** (et la même chose pour `android.hardware.camera.front` si le code la cite). Raison, lue dans la documentation Android : la permission `CAMERA` **implique** les fonctions `android.hardware.camera` et `android.hardware.camera.autofocus`, donc Google Play **filtrerait** l'app sur les appareils sans caméra ; déclarer `required="false"` « désactive effectivement tout filtrage Google Play pour cette fonction ». L'app doit alors fonctionner sans caméra (état D du ticket : message calme, repli).
- **Pas de formulaire Play Console dédié pour `CAMERA`** : la page « Permissions and APIs that Access Sensitive Information » (lue le 2026-11-06) liste les déclarations obligatoires (localisation en arrière-plan, `READ_MEDIA_*`, accès à tous les fichiers, SMS, journal d'appels, etc.) et **ne cite pas `CAMERA`**. À revérifier au moment de la soumission (la même page annonce des déclarations à venir pour la localisation précise sous Android 17+ : **à lire**, ça concerne `ACCESS_FINE_LOCATION`, pas la caméra).
- **Contrôles du dépôt à mettre à jour dans la PR de développement** : `PermissionPrivacyTest` (dit « pas de caméra »), `android.yml` (CI), commentaire du manifeste, `docs/release/google-play-data-safety.md`.
- **Moment de la demande** : à l'ouverture de l'écran Update seulement, après l'écran d'explication, **jamais au démarrage** (critère P1). C'est ce qui évite le grief « permission inutile à l'ouverture ».

### 3.2 iOS : `NSCameraUsageDescription`

Sans cette clé, iOS interrompt l'app au premier accès caméra (connaissance générale ; la page de documentation d'Apple n'a pas pu être lue par mon outil le 2026-11-06). Apple demande que la phrase décrive « clearly and completely » l'usage (guideline 5.1.1(ii), lue). Textes **courts et honnêtes** proposés (fichier `InfoPlist.xcstrings`, FR et EN) :

| Langue | `NSCameraUsageDescription` |
|---|---|
| Français | « Miff utilise ta caméra uniquement quand tu ouvres « Donner des nouvelles », pour prendre la photo que tu choisis de partager. Les membres de tes Miffs la voient pendant 24 h. Rien n'est enregistré dans ta galerie. » |
| English | "Miff uses your camera only when you open "Share news", to take the photo you choose to share. The members of your Miffs can see it for 24 hours. Nothing is saved to your photo library." |

Le nom du bouton est celui du nom parlé du ticket (« Donner des nouvelles » / « Share news ») : à aligner sur le libellé final des maquettes.

**À ajouter aux textes d'usage de la localisation** (`app-store-confidentialite-et-revue.md` §4), parce que le lieu de la photo est un usage de la position qu'ils ne citent pas aujourd'hui (5.1.1(ii) « completely ») :

| Langue | Phrase à ajouter à `NSLocationWhenInUseUsageDescription` et à `NSLocationAlwaysAndWhenInUseUsageDescription` |
|---|---|
| Français | « Si tu publies une photo, l'endroit où tu l'as prise peut y être joint, visible pendant 24 h des membres qui partagent aussi leur position. » |
| English | "If you publish a photo, the place where you took it can be attached to it, visible for 24 hours to the members who also share their location." |

**Autres clés à ne PAS ajouter** : `NSPhotoLibraryUsageDescription`, `NSPhotoLibraryAddUsageDescription` (l'app n'écrit jamais dans la galerie), `NSMicrophoneUsageDescription` (aucun son). `ios/ci/check-binaries.sh` doit **vérifier leur absence** et la **présence** de la clé caméra (aujourd'hui il vérifie l'absence de `NSLocation*`, et le ticket note qu'aucun contrôle `NSCamera*` n'existe).

**Manifeste de confidentialité** (`PrivacyInfo.xcprivacy`) : ajouter les types collectés « photos ou vidéos » (clé `NSPrivacyCollectedDataTypePhotosorVideos` d'après des documentations de SDK tiers ; **l'orthographe exacte est à vérifier dans Xcode** car la page d'Apple n'était pas accessible) avec Linked = vrai, Tracking = faux, finalité `NSPrivacyCollectedDataTypePurposeAppFunctionality`. La position précise y est déjà prévue (`app-store-confidentialite-et-revue.md` §3).

### 3.3 L'explication dans l'app, avant la boîte du système

Les deux stores attendent que l'usage soit expliqué dans l'app (Apple 5.1.5 pour la position ; 5.1.1(ii) pour le consentement). Le texte de l'écran d'explication est déjà au ticket §7 (« Une photo pour tes proches » / « A photo for your loved ones ») : il dit à quoi sert la caméra, qui voit la photo, 24 h, rien dans la galerie. **À aligner** : ajouter la phrase sur le lieu de la photo **quand UPDATE-1b est livré** (texte à écrire avec le consentement amendé, §9.3). Cohérence obligatoire entre : écran d'explication, `NSCameraUsageDescription`, politique art. 4, consentement v3.

### 3.4 Que se passe-t-il si la personne refuse la caméra ? (Apple 5.1.1(iv))

Apple écrit : « Apps must respect the user's permission settings and not attempt to manipulate, trick, or force people to consent to unnecessary data access ... Where possible, provide alternative solutions for users who don't grant consent. » (lu le 2026-11-06).
- **Ce qui est conforme** : la caméra n'est demandée que pour l'Update ; tout le reste de l'app marche sans ; le refus n'est jamais bloquant ni culpabilisant (états B, C, D du ticket).
- **Point d'attention** : sur Android, l'alternative « appareil photo du système » **n'exige aucune permission** (mécanisme de la photo de profil). Sur iPhone, ma connaissance est que le sélecteur système en mode caméra **demande aussi l'autorisation caméra** : si c'est exact, il n'existe **pas** d'alternative sans permission sur iOS, et l'état C doit le dire honnêtement (« Ouvrir les réglages ») plutôt que de promettre un repli. **`ios-dev` vérifie sur l'iPhone 11 Pro avant la PR** ; en attendant, ne rien promettre dans la note de revue.

---

## 4. Justification de la localisation de la photo (aux relecteurs et dans les textes)

**À ne pas faire** : l'ajouter à la « fonctionnalité unique » de la déclaration Google de localisation en arrière-plan (`google-play-localisation-arriere-plan.md` §3). Google exige **une seule** fonctionnalité ; le lieu de la photo est une mesure **au premier plan**, au moment de l'obturateur, avec la permission déjà accordée : **aucune permission nouvelle, aucun usage en arrière-plan en plus.** Elle se déclare dans la politique, le consentement, les étiquettes et la note de revue.

**Paragraphe à ajouter à la note de revue Apple** (anglais) et à reprendre dans « App access » côté Google si nécessaire :

> **Photo and its location ("Share news").** The "Share news" button opens an in-app camera (live capture only: no photo library access, nothing is saved to the device gallery). The user previews the photo and publishes it; it is then visible for 24 hours only to the members of the user's Miffs, and replaces the previous one. If, and only if, the user shares location with that Miff and accepted the consent text, Miff takes a single foreground location measurement at the moment of the shot and attaches the coordinates and the measurement time to the photo (otherwise the photo is published without location). The location of a photo can be opened only by members who also share their own location, and is deleted with the photo (24 hours, or when sharing stops, the user leaves, or the account is deleted). The camera is never started in the background or remotely, and photos are not analysed. A photo with a location cannot be opened by a member who does not share their own location: the app then shows "Share your position to see this photo", which is expected behaviour, not a defect.

**Pourquoi la dernière phrase** : un relecteur qui teste avec un compte qui n'a pas publié sa position verra un message à la place de la photo et pourrait croire que l'app est cassée. Le **compte de démonstration** (README du dossier, action 7) doit donc avoir **deux comptes** dans la même Miff, **chacun avec sa position publiée** (position simulée).

**Réciprocité et textes** : la formule déjà employée partout (« seuls les membres de la Miff qui partagent aussi la leur ») reste vraie pour le lieu de la photo ; ce qu'il faut **ajouter** est que le lieu est une **seconde copie de position**, attachée à la photo, qui **peut différer de la dernière position connue** pendant 24 h (pas d'historique de trajets, mais deux positions au plus par Miff).

---

## 5. Google Play : Data safety (modifications à reporter dans `google-play-data-safety.md`)

Définitions lues le 2026-11-06 (aide Google, 10787469) : « collecter » = transmettre hors de l'appareil ; « partager » = transférer à un **tiers**, hors prestataire, hors action précise de l'utilisateur ; « localisation précise » = zone de moins de 3 km² ; types « Photos » (« A user's photos ») ; finalités dont « App functionality » ; question de suppression : « Does your app provide a way for users to request deletion of their data? » (« There is no prescribed mechanism »). La page **ne traite pas** le cas de données visibles d'autres utilisateurs de l'app : **O9 reste ouvert** (voir README).

| Catégorie | Type | Réponse proposée pour l'Update | Écart avec le tableau actuel |
|---|---|---|---|
| Photos et vidéos | **Photos** | **Collecté : oui** (octets envoyés à Firestore). **Partagé : voir O9** (recommandation prudente inchangée : déclarer le partage avec d'autres utilisateurs plutôt que rien). **Obligatoire ? Facultatif** (la personne choisit de publier ; elle peut ne jamais utiliser l'Update). **Finalité : App functionality.** **Traitée de façon éphémère : non** (stockée jusqu'à 24 h sur le serveur). Chiffrée en transit : oui. Suppression : oui | Ligne existante « photo de profil facultative ; photo de mise à jour facultative » : **conservée**, mais préciser « prise avec la caméra de l'app ». La photo de mise à jour devient **obligatoire pour publier** (D16) sans devenir obligatoire pour l'app : « facultatif » reste exact |
| Localisation | **Localisation précise** | Déjà **collectée, obligatoire** (« Toujours »). Le lieu de la photo est **la même catégorie** ; aucune ligne de plus. Mentionner dans les notes : « jointe aussi à la photo (24 h) » | Notes de la ligne existante à compléter |
| Messages | « Autres messages dans l'app » (petit texte de 140 caractères) | **À retirer** si l'Update est livrée sans texte (ticket C4 : `updateText = null`). Garder la ligne **uniquement** si un texte est effectivement livré | **La ligne actuelle est inexacte avec le ticket Update** : une sous- ou sur-déclaration est un écart avec l'app |
| Audio | — | **Non collecté** (aucun micro, pas de `RECORD_AUDIO`) | à confirmer par `android-dev` |
| Fichiers et documents | — | **Non** : aucune écriture dans la galerie ni lecture de fichiers (le repli système utilise un fichier du cache privé, supprimé aussitôt) | à confirmer |
| Activité dans l'app, plantages | Analytics, Crashlytics | Inchangé. **Vérifier** que ni la photo ni les coordonnées ne figurent dans un événement, un journal ou un rapport (D12, critère U6) | contrôle V1 élargi |

**Formulaire « Contenu de l'application »** : « Classification du contenu » (questionnaire IARC), « Public cible » : voir §8. Google précise (page de la classification, lue) : « Misrepresentation of your app's content may result in its removal or suspension » : répondre à ce qui sera livré, pas à ce qui est prévu. La classification doit être refaite si l'app change (aide Google, relevée par recherche).

---

## 6. Apple : App Privacy (modifications à reporter dans `app-store-confidentialite-et-revue.md`)

Définitions lues le 2026-11-06 (« App privacy details ») : « Photos or Videos » = « The user's photos or videos » ; « Precise Location » = latitude/longitude à **trois décimales ou plus** ; « collect » = transmettre hors de l'appareil et rendre accessible plus longtemps que le temps de servir la requête en direct ; données « linked » à l'identité sauf dé-identification avant collecte ; finalité « App Functionality » = authentifier, activer des fonctions, etc. Apple précise aussi que si l'on a « a feature that enables users to upload a particular media type, such as photos or videos, then you'll need to disclose the specific type of data » : la photo est donc à déclarer. Apple prévoit une exemption de divulgation « facultative » mais elle exige que **tous** ses critères soient remplis (collecte rare et hors fonctionnalité principale, choix de l'utilisateur à chaque fois, etc.) : je ne m'appuie pas sur elle.

| Type Apple | Réponse pour l'Update | Écart avec le tableau actuel |
|---|---|---|
| **Photos or Videos** | Collecté : oui ; lié à l'utilisateur : oui ; suivi : non ; finalité : **App Functionality** | Ligne existante (« photo de profil ; photo de mise à jour (24 h) ») : **inchangée**, à confirmer au moment du remplissage |
| **Precise Location** | Déjà déclaré (5 décimales > seuil de 3). Le lieu de la photo est **la même donnée** | Aucune ligne de plus ; compléter la note de revue (§4) |
| **Other User Content** | **À retirer** si aucun texte n'est livré (ticket C4) ; Apple n'oblige pas à déclarer ce que l'utilisateur pourrait saisir, mais ici le champ n'existe pas | **Ligne actuelle inexacte** si l'Update est livrée sans texte |
| Autres lignes (Name, Email, Phone, User ID, Device ID, Product Interaction, Crash Data, Purchases) | Inchangées par l'Update | — |
| Suivi (tracking) | **Non** | inchangé. Apple 5.1.2(vi) interdit d'utiliser les données de caméra pour du marketing ou de l'exploration de données : l'app n'en fait rien (aucune analyse) |

**Classification d'âge Apple** (page lue le 2026-11-06 sur ses 100 000 premiers caractères) : les questions couvrent notamment « User-Generated Content (UGC) », « Messaging and Chat », « Social Media ». D'après cette page, UGC et messagerie font partie des capacités qui n'élèvent pas la note au-delà de 4+, tandis que « Social Media » (flux qui amplifient les contenus par vues, mentions « j'aime », commentaires, partages) correspond à 13+. **Réponses proposées, à relire devant les questions du jour** : UGC **oui** (photos visibles d'un cercle) ; messagerie et chat **non** (aucune conversation dans l'app ; le numéro de téléphone ouvre l'application du téléphone, à confirmer par les devs) ; réseau social **non** (aucun flux public, aucun « j'aime », commentaire ni partage). La note affichée sera celle que calcule App Store Connect : ne pas la choisir à la main.

---

## 7. Contenu généré par les utilisateurs (UGC) : exigences et état de Miff

Le contenu concerné : la **photo d'update** (nouvelle), la photo de profil, le prénom et le nom, le nom et l'adresse d'un lieu. Aucun texte libre échangé, aucune messagerie, aucun commentaire.

### 7.1 Apple, guideline 1.2 (texte lu le 2026-11-06)

> « Apps that enable users to create, generate, upload, share, or otherwise contribute content present particular challenges ... To prevent abuse, such apps must include: A method for filtering objectionable material from being posted to the app; A mechanism to report offensive content and timely responses to concerns; The ability to block abusive users from the service; Published contact information so users can easily reach you. »

Le texte ne prévoit **aucune exception** pour les groupes fermés ou privés (je n'en ai pas trouvé ; je n'en invente pas). Il ajoute : « Developers who fail to implement appropriate safeguards or fail to remediate identified violations after notice may be considered in violation of the Developer Code of Conduct. »

### 7.2 Google Play, politique « User Generated Content » (pages lues le 2026-11-06)

- Définition : contenu que les utilisateurs apportent à l'app, « visible to or accessible by at least a subset of the app's users » : un cercle fermé en fait partie ; la page d'aide ne mentionne **aucune exemption** pour les petits groupes ni les groupes privés.
- Exigences : l'utilisateur doit **accepter les conditions d'utilisation avant de créer ou téléverser du contenu** (« cannot be skipped ») ; l'app doit **définir les contenus et comportements répréhensibles** dans ses conditions, en cohérence avec les politiques de Google Play ; une **modération** « reasonable » et continue, dont **« an in-app system for reporting and blocking objectionable UGC and users »**, et des **actions** sur les contenus ou utilisateurs en infraction.
- Détail par type d'app : pour les apps où les utilisateurs sont vérifiés ou inscrits hors ligne (école, entreprise), le **signalement** dans l'app est exigé ; pour les apps à interaction individuelle (messagerie, mentions), le **blocage** ; pour les apps à contenu public, **les deux**. La page d'aide résume : « An app that features UGC must provide abilities to report and block both users and content », « readily accessible from within the app », et recommande deux fonctions **séparées et clairement nommées**. « Developers are free to implement the report and block features in a format that is best for their apps. »
- Miff n'est **ni public, ni une messagerie** : la classe exacte n'est pas dite. **Lecture prudente retenue** : prévoir le signalement **et** une forme de blocage.

### 7.3 État de Miff face à ces exigences

| Exigence | Aujourd'hui (CGU, app) | Manque | Proposition |
|---|---|---|---|
| Conditions acceptées **avant** de publier | Acceptées au clic sur « Rejoindre » (CGU art. 3) ; avant toute photo | Rien (à garder visible : la publication n'est possible que compte créé) | — |
| Contenus répréhensibles **définis** | CGU art. 7 et 8 : illégal, violent, haineux, sexuellement explicite, atteinte aux droits d'autrui, photo d'une personne sans accord, d'un enfant sans autorité parentale | Pas d'alignement explicite sur les politiques de Google (**je n'ai pas lu** la politique « Inappropriate Content ») ; pas de mention des images intimes non consenties, ni de la conséquence (retrait, suspension) | Compléter l'art. 7 (§9.2, G3) |
| **Filtrage** (Apple) | Aucun automatique | Impossible sur Spark, exclu par le ticket | P4 : assumer et argumenter. Le filtre sur l'appareil `SensitiveContentAnalysis` d'Apple (iOS 17+, entitlement dédié) **ne convient pas** : il ne s'active que si l'utilisateur l'a activé dans les réglages d'iOS, et l'app vise iOS 16 (lu via recherche, sources tierces) |
| **Signaler** | Seulement « écris-nous » (CGU art. 8) | Aucun bouton dans l'app | P1 : option A (e-mail pré-rempli) puis B si refus |
| **Bloquer** | Quitter la Miff ; le créateur retire un membre (code renouvelé) | Un membre ne peut rien contre le créateur ; aucun masquage individuel | P2 : « Ne plus voir les photos de {Prénom} » (local) ; suspension manuelle (CGU art. 12) |
| **Réponse dans un délai** | « Nous pouvons supprimer un contenu » (CGU art. 8) | Aucun délai, boîte consultée une fois par semaine (J17) | P3 |
| **Contact publié** | E-mail du support dans les CGU et la politique (provisoire, décision du 2026-10-24) | Contact **dans l'app** (Réglages) et URL de support dans les fiches | À vérifier dans le ticket Réglages ; l'URL de support est un champ obligatoire d'App Store Connect |
| Action sur les contenus en infraction | Possible à la main (console Firebase) | Procédure écrite : qui supprime quoi (la photo d'un autre : possible à la main seulement, ou par le créateur ; les règles autorisent le créateur à supprimer une photo) | Procédure de 5 lignes à écrire par `backend-data` (hors de cette tâche) |

### 7.4 Ce qui joue en faveur de Miff (à dire aux relecteurs, sans le présenter comme une exemption)

- Prise de vue **en direct uniquement**, jamais de galerie : on ne peut pas publier un contenu téléchargé ni une image trouvée sur Internet.
- **Cercle fermé** : on ne voit que les photos des membres de sa propre Miff ; aucune recherche, aucun profil public, aucun commentaire, aucun partage, aucune messagerie.
- **Une seule photo par personne, 24 h**, remplacée à chaque publication.
- Créateur qui **retire** un membre ; chacun peut **quitter** ; suppression de compte dans l'app.

### 7.5 Paragraphe de note de revue Apple pour l'UGC (anglais)

> **User-generated content (guideline 1.2).** The only user-generated content is a single live-camera photo per user (no gallery upload, no text, no comments, no messaging), visible for 24 hours only to the members of the user's private circle ("Miff"), which can be joined only with a secret code. There is no public feed or discovery. Users can report a photo from the full-screen photo screen ([P1: décrire le mécanisme retenu]) and hide a member's photos ([P2]); the circle's creator can remove a member; any member can leave; users can delete their account in the app. The terms (accepted at sign-up) forbid illegal, violent, hateful or sexually explicit content and photos of people without their consent. We do not run automatic image filtering: photos are not analysed. Contact: tomessa.mfouka@gmail.com [et URL de support].

À ne **jamais** écrire : « modéré », « filtré », « vérifié » ou « sans risque ».

---

## 8. Mineurs et âge minimum

- **Cadre déjà noté** : aucun âge minimum, aucun contrôle d'âge ; risques J2, J31 (`docs/legal/README.md`), points 2, 15, 20, 24 de `data-model.md` §11 ; **assumés par le PM** (2026-10-23, 2026-11-01). Le ticket Update reprend R3 : « risque assumé par le PM (décisions du 2026-10-23 et du 2026-11-01), jamais présenté comme relu par un juriste ». Rien ne change dans ce statut ; **l'Update ajoute deux cas** : la **photo** d'une personne (visage, domicile) qui peut être mineure, et le **lieu de la photo**. Pour la photo, le ticket (R3) l'écrit déjà comme assumé ; l'extension au lieu de la photo est proposée comme J35 (§9.4) et n'est **pas encore assumée par écrit**. Aucun de ces points n'est écrit comme « à valider » ni comme relu.
- **Apple 5.1.4(b), lu le 2026-11-06** : les apps « that collect, transmit, or have the capability to share personal information (such as name, address, email, location, photos, videos, drawings, the ability to chat, other personal data ...) from a minor must include a privacy policy and must comply with all applicable children's privacy statutes. » Miff **a la capacité** de partager lieu et photo d'un mineur : la politique de confidentialité est donc obligatoire (elle existe) et la conformité aux lois sur la vie privée des enfants est l'affaire du PM (risque assumé). Même article : une app **hors** catégorie Enfants ne peut employer, dans son nom, sous-titre, icône, captures ou description, aucun terme laissant entendre que le public principal est enfantin (« For Kids » : réservé à la catégorie Enfants, guideline 2.3.8 citée).
- **Google Play** (résultats de recherche vers les pages 9893335 « Families » et 9285070 « target audience », **non lues dans le texte**) : le public cible est déclaré dans la console ; l'imagerie et la terminologie peuvent amener Google à réévaluer le public déclaré ; une app non destinée aux enfants ne doit pas leur plaire par inadvertance. À lire par le PM au moment du remplissage.
- **Recommandations (O7 du README, confirmées)** : ne pas déclarer l'app comme destinée aux enfants ; répondre **de la même façon partout** ; **captures et fiche sans enfants ni univers enfantin** (§10) ; ne pas écrire « pour toute la famille » dans le sous-titre sans réfléchir (le mot « famille » reste admis, « enfants » non).
- **Photo de personnes tierces** : CGU art. 8 l'interdit sans accord, mais aucun filtre ne l'empêche (R3 du ticket). C'est un argument à garder en tête pour la rubrique UGC, pas une parade.

---

## 9. Politique de confidentialité et CGU : passages à adapter (propositions, `docs/legal/` n'est pas modifié)

Version actuelle : **0.1, 4 novembre 2026** (jamais publiée : on peut modifier sans changer de numéro, README légal §3 « Version du texte »). **Toute modification se fait dans les deux langues à la fois**, et dans la page `web/` (construite à partir des mêmes textes). Les lignes citées sont celles de la version FR du 2026-11-06.

### 9.1 Politique de confidentialité (`politique-confidentialite.fr.md` et `.en.md`)

| # | Passage actuel (ligne) | Problème | Proposition |
|---|---|---|---|
| **L1** | Art. 1 pt 1 (l. 9) : « Miff garde ta dernière position connue, et seulement elle. » | **Inexact** avec le lieu de la photo : une seconde position est gardée jusqu'à 24 h | « Miff garde ta dernière position connue, plus, si tu publies une photo, l'endroit où elle a été prise (24 h). Pas d'historique de trajets. » |
| **L2** | Art. 1 pt 7 (l. 15) : « Les photos de mise à jour disparaissent après 24 heures. » | Incomplet (lieu, visibilité) et trop fort (capture d'écran, suppression « au mieux ») | « Les photos que tu publies sont visibles des membres de tes Miffs pendant 24 heures, puis plus lisibles. » |
| **L3** | Art. 3, tableau, « **Mise à jour manuelle** (facultative) : petit texte (140 caractères au plus) et photo » (l. 43) | **Le texte est abandonné en v1** (ticket C4 ; l'`updateText` est toujours `null`) ; ni caméra ni lieu cités | Ligne photo : « prise avec la caméra de l'app (jamais depuis ta galerie), sans texte », et nouvelle ligne **Lieu de la photo** (lat, lng, heure de mesure, consentement v3 amendé ou v4) |
| **L4** | Art. 3, « Ce que Miff ne collecte pas » (l. 65) : « seule ta dernière position connue est gardée » | Inexact comme L1 | Ajouter la photo localisée ; ajouter « ni ta galerie de photos » |
| **L5** | Art. 3 : aucune mention de la **caméra** | Apple 5.1.1(i) : « all uses of that data » | Phrase : « La caméra n'est active que lorsque l'écran « Donner des nouvelles » est ouvert ; la photo n'est enregistrée nulle part sur ton téléphone ; ses métadonnées (date, appareil, position) ne sont pas envoyées. » **À n'écrire que lorsque le test d'octets (critère V3) est passé.** |
| **L6** | Art. 4, « Ta dernière position connue, en détail », « Ce qui est enregistré » (l. 79) : « la latitude, la longitude et l'heure de la mesure, **rien d'autre** » | Inexact pour la photo | Décrire le lieu de la photo à part : mêmes champs, **attaché à la photo**, effacé avec elle, jamais attaché si la mesure n'est pas récente |
| **L7** | Art. 4, « Ton consentement » (l. 89) : « ... un texte de consentement de version 3 ou plus récent, qui dit clairement que les membres de ta Miff voient ta dernière position connue » | Le texte v3 actuel ne cite pas la photo localisée | Citer la photo localisée (D20) |
| **L8** | Art. 5, « Qui voit quoi » (l. 100) : « ton éventuelle mise à jour (texte, photo) » | Texte abandonné ; le **lieu de la photo** est omis ; pas de mention de la règle de lecture | « ta photo ; si tu partages ta position, l'endroit où elle a été prise, **visible seulement des membres qui partagent aussi leur position** (un membre qui ne partage pas sa position ne peut pas ouvrir une photo localisée) » |
| **L9** | Art. 5 : rien sur les **captures d'écran** | Promesse « 24 h » mal comprise (R5 du ticket) | « Un membre peut faire une capture d'écran de ce qu'il voit : nous ne pouvons pas l'empêcher » (déjà dans les CGU art. 8) |
| **L10** | Art. 7, ligne **Photo de mise à jour** (l. 145) : « supprimée ... **par l'app de son auteur ou d'un membre de la Miff** lors d'une ouverture de l'app » | **Faux avec le Feed** : « le Feed ne supprime jamais une photo expirée » (ticket Update C12) ; seule la suppression par l'auteur est implémentée | « ... supprimée par l'app de son auteur à l'ouverture suivante de l'app » (et laisser `data-model.md` §2.8 à `backend-data`) |
| **L11** | Art. 7 : aucune durée pour le **lieu de la photo** | Manquant | « Même durée que la photo : illisible après 24 h, supprimé avec elle » (suppression physique non garantie à la 24e heure, J8/J27) |
| **L12** | Art. 8, Droits (l. 175) : l'arrêt du partage supprime « ta photo de mise à jour et ta dernière position connue » | Exact, mais le lieu de la photo est dedans | Préciser « et l'endroit de ta photo » |
| **L13** | Art. 9, Sécurité (l. 186) : « la dernière position d'un membre n'est lisible que par ... » | Incomplet | Ajouter la photo localisée et la réciprocité |
| **L14** | Art. 10, Mineurs (l. 196) : parle de la position d'un mineur | La **photo** d'un mineur n'est pas citée | Ajouter « sa photo et l'endroit où elle a été prise » ; conserver la mention « Miff ne contrôle pas l'âge » |
| **L15** | Art. 11, stockage local (l. 203) | La photo en préparation (mémoire seulement) et la copie locale d'une photo reçue ne sont pas décrites | Une phrase : « la photo en cours de préparation n'est gardée qu'en mémoire ; les photos reçues peuvent être gardées en copie privée, effacée à la déconnexion » (**à confirmer** par les devs de FEED-4) |
| **L16** | Art. 12 (l. 214) : « ... texte de consentement de version 3, qui décrit l'enregistrement de ta dernière position connue » | À compléter | « ... et de l'endroit de ta photo » |
| **L17** | En-tête et art. 1 | Date du 4 novembre | Mettre à jour la date au jour de la mise en ligne (README légal §3) |

### 9.2 CGU (`cgu.fr.md` et `.en.md`)

| # | Passage actuel (ligne) | Problème | Proposition |
|---|---|---|---|
| **G1** | Art. 2 (l. 31) : « Tu peux aussi envoyer une **mise à jour** à la main (un petit texte, avec une photo si tu veux). » | Texte abandonné ; la photo est obligatoire pour publier | « Tu peux aussi donner de tes nouvelles en publiant une photo. » |
| **G2** | Art. 6 (l. 67 à 70) : arrêt du partage, « ce que tes proches voient », « Miff garde seulement cette dernière position » | Incomplet comme L1 | Ajouter le lieu de la photo, « jusqu'à 24 h » |
| **G3** | Art. 7, Règles d'usage (l. 78 à 85) | Définit déjà plusieurs interdits (Google : « define objectionable content ») ; pas de conséquence ni d'images intimes non consenties | Ajouter « photos intimes d'une personne sans son accord » et « tout contenu qui met un mineur en danger » ; rappeler que le contenu peut être retiré et le compte suspendu (art. 12) |
| **G4** | Art. 8 (l. 90 à 93) | « Tu peux nous signaler ... à tomessa.mfouka@gmail.com » : pas de signalement dans l'app, pas de délai | Écrire le mécanisme retenu (P1), le blocage local (P2) et le **délai réel** (P3) ; dire que le contenu signalé peut être masqué en attendant l'examen **seulement si** c'est techniquement vrai |
| **G5** | Art. 8 : « Un membre d'une Miff peut enregistrer ce qu'il voit » | Exact | Aucun changement ; à citer dans la politique (L9) |
| **G6** | Art. 3 (l. 44) : âge | Aucun changement | Inchangé : risque assumé, jamais « à valider » |

### 9.3 Texte de consentement (`consentement-localisation-v3.md`)

Le point **O4** (« la localisation affichée avec la photo est-elle la position au moment de l'update ou la dernière position connue ? ») est **tranché** par D23 (position mesurée au moment de la photo, avec sa propre heure). Les passages du texte v3 qui deviennent inexacts : le §3 (ligne « ce qui est enregistré » : « seulement ta dernière position connue ... chaque position remplace la précédente »), la ligne « qui la voit », la ligne « effacement », et l'écran de re-consentement du §4.

**Procédure (D20)** :
1. **Vérifier** qu'aucune personne extérieure n'a accepté `2026-11-v03` : le code contient déjà la constante (tests Android), donc un build de test avec v3 existe peut-être déjà. À constater dans Play Console (pistes de test ouvertes ou fermées) et dans TestFlight (testeurs externes). Je ne peux pas accéder à ces consoles.
2. **Si personne** : amender v3 (texte ci-dessous). La constante reste `2026-11-v03` et le seuil des règles `isPhotoLocationConsentVersion` reste 03. Les builds internes qui l'affichent changent de texte (strings Android, PR iOS #74 en brouillon).
3. **Sinon** : v4 (`2026-11-v04`, ou le mois de mise en service), re-consentement des comptes qui ont accepté v3, constante changée sur les deux plateformes dans le même commit, **seuil de `isPhotoLocationConsentVersion` relevé à 04** (une ligne de règle, un test, une mutation : `data-model.md` en-tête), règles à redéployer sur ton « oui ».

**Texte amendé proposé** (FR / EN, à ajouter ou à remplacer dans les lignes du §3 de `consentement-localisation-v3.md`) :

| Ligne | Français | English |
|---|---|---|
| ce qui est enregistré (remplace) | « Miff garde seulement ta dernière position connue, avec l'heure de la mesure, et, si tu publies une photo, l'endroit où elle a été prise (avec l'heure de cette mesure). Pas d'historique de trajets. Ce n'est pas un suivi en direct. » | "Miff only keeps your last known location, with the time it was measured, and, if you publish a photo, the place where it was taken (with the time of that measurement). No history of trips. This is not live tracking." |
| photo (nouveau) | « L'endroit d'une photo reste attaché à la photo pendant 24 h, même si ta dernière position change entre-temps. Il n'est joint que si ton téléphone vient de le mesurer. » | "The place of a photo stays attached to the photo for 24 hours, even if your last known location changes in the meantime. It is only attached if your phone has just measured it." |
| qui la voit (complète) | « Seuls les membres de cette Miff qui partagent aussi la leur voient ta position et l'endroit de ta photo. » | "Only the members of this Miff who also share theirs can see your location and the place of your photo." |
| effacement (remplace) | « Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position et l'endroit de tes photos sont effacés. Après 24 h, une photo n'est plus lisible et ta position est masquée (seule sa date reste visible). » | "If you stop sharing, leave the Miff or delete your account, your location and the place of your photos are erased. After 24 hours a photo can no longer be read and your location is hidden (only its date remains visible)." |

Même complément pour l'écran de re-consentement du §4 (une phrase « et, si tu publies une photo, l'endroit où elle a été prise »).

**Textes à tenir identiques** (« si l'un change, les trois changent », §5 du consentement) : l'écran de consentement, les textes d'usage iOS (§3.2) et la divulgation Google (`google-play-localisation-arriere-plan.md` §5), qui ne parle pas du lieu de la photo : **elle n'a pas à le citer** (le lieu de la photo n'est pas la fonctionnalité en arrière-plan), mais la politique et le consentement oui.

### 9.4 Risques à ajouter au tableau de `docs/legal/README.md` §4 (si tu valides ; écrits comme **non assumés** tant que tu n'as pas dit oui)

| # | Risque | Conséquence | Mitigation |
|---|---|---|---|
| J34 | **Photos d'utilisateurs sans filtrage automatique** (UGC : Apple 1.2, Google UGC) ; signalement par e-mail ou à la main | Refus ou retrait par un store ; contenu illicite non vu à temps (LCEN / DSA, J16) | Prise de vue en direct seulement, cercle fermé, 24 h, signalement, retrait, boîte consultée chaque jour au lancement |
| J35 | **Lieu de la photo** : seconde copie de position précise, attachée à une image, non vérifiable côté serveur, qui survit au remplacement de la dernière position (24 h) | Plainte, rejet des stores, mauvaise utilisation entre proches | Consentement qui la cite (D20), réciprocité, suppression avec la photo, mesure récente seulement ; extension de J25 |

---

## 10. Captures d'écran et vidéo à prévoir

**Règles communes** (Apple 5.1.4 et Google « target audience », fiche accurate) : **comptes de test**, jamais de vrais proches ; **photos libres de droit ou prises par des adultes de l'équipe consentants** (aucun enfant, aucun univers enfantin) ; **lieux et adresses fictifs** (jamais le domicile du PM) ; numéros de téléphone factices ; heures cohérentes (« Photo de Léa · 15 h 40 ») ; **aucun vocabulaire de surveillance** (« suivre », « espionner », « temps réel »), un lieu de photo n'étant **pas** une position en direct ; la bulle orange/rouge peut être montrée mais `aso-marketing` en décide (ton). Les tailles exigées sont celles affichées par App Store Connect et la Play Console au moment du téléversement (non listées ici).

| # | Écran | Source | Pour | Remarques |
|---|---|---|---|---|
| S1 | **Écran d'explication de la caméra** (« Une photo pour tes proches ») | maquettes de design (PR #83, `04-permission-camera`) | Revue Apple et Google (preuve de l'explication avant la boîte) ; fiche optionnelle | Doit montrer « 24 h » et « rien dans ta galerie » |
| S2 | **Viseur** (selfie, rond bleu, point de couleur du bouton central) | `design/exports/update/` (7 PNG du PM) et PR #83 | Fiche (iOS et Android) | Décision D24 : plein écran ; refaire la capture avec la version finale |
| S3 | **Aperçu** (croix, Recommencer, Publier, ligne « Tes proches verront cette photo pendant 24 h. ... ») | idem | Fiche + revue | Montrer la ligne de visibilité (preuve que le consentement est contextuel) |
| S4 | **Carte du Feed avec photo** (ma carte et celle d'un proche, « Photo de {Prénom} · heure ») | PR #83 (`06-cartes-et-miffs`) | Fiche | Fil avec bulle verte et photo, ton rassurant |
| S5 | **Photo en plein écran au toucher depuis le Feed** (D26), avec « Voir où c'était » et « Position d'il y a 4 min » | à dessiner (FEED-4) | Fiche + revue | **Capture à refaire après FEED-4** ; ne pas montrer d'adresse réelle |
| S6 | **Carte du système sur le lieu de la photo** (épingle, sans nom de lieu) | FEED-4 | Revue (justification du lieu) | Lieu fictif en test (position simulée) |
| S7 | **Message « Partage ta position pour voir cette photo »** | FEED-4 | **Revue seulement** (§4) | Évite le faux diagnostic « app cassée » |
| S8 | **Signalement d'une photo** et **« Ne plus voir les photos de {Prénom} »** | à dessiner (P1, P2) | **Revue seulement** (Apple 1.2, Google UGC) | Joindre à la note de revue |
| S9 | **Réglages : arrêter le partage** (efface photo, statut, position) | existant | Revue | Déjà prévu pour la localisation |
| V1 | **Vidéo Apple** (≤ 60 s) : ouvrir l'Update, boîte caméra, photo, aperçu, publier, voir la photo chez le second compte, signaler / masquer | iPhone 11 Pro | Informations de revue Apple | La **vidéo Google** de localisation en arrière-plan (30 s) **ne montre pas l'Update** : une seule fonctionnalité (`google-play-localisation-arriere-plan.md` §3) |

**Déjà bloquant pour les captures** : les maquettes des écrans de l'Update ne sont pas toutes dessinées (PR #83 en cours), et FEED-4 n'existe pas.

---

## 11. Risques de rejet et parades

Probabilité : mon estimation qualitative (faible, moyenne, élevée), **pas une mesure** ; je ne peux pas prédire l'avis d'un relecteur.

| # | Risque de rejet | Règle | Prob. | Parade |
|---|---|---|---|---|
| **R1** | Pas de **filtrage automatique** des images publiées | Apple 1.2 (1er point) | Moyenne | P4 : argumentaire structurel (§7.4), note de revue §7.5, signalement + retrait ; plan B si refus : ajouter une revue a posteriori (boîte quotidienne), ou une analyse sur l'appareil si Apple en propose une adaptée (non disponible aujourd'hui) ; **ne jamais écrire « modéré »** |
| **R2** | **Signalement** absent de l'app ou limité à un e-mail hors app | Apple 1.2 (2e point) ; Google UGC « in-app system » | Moyenne (A) / faible (B) | P1 : A pour la bêta, B préparé ; bouton visible dans la photo en plein écran et sur la carte du membre |
| **R3** | **Blocage** absent | Apple 1.2 (3e point) ; Google UGC | Moyenne à élevée si rien | P2 : masquage local + retrait par le créateur + quitter ; dire honnêtement la limite (pas de blocage « du service » sans suspension manuelle) |
| **R4** | **Délai de réponse** non tenu (« timely responses ») | Apple 1.2 ; engagement dans les CGU | Faible au départ, croît avec l'usage | P3 : annoncer 72 h et consulter chaque jour pendant le lancement ; ne jamais promettre un délai impossible |
| **R5** | **Permission caméra incohérente** (manifeste, Info.plist, CI, tests, Data safety, étiquettes qui disent « pas de caméra ») | Apple 5.1.1 ; Google Data safety | Élevée si on oublie, faible avec la liste V10 à V19 | Mettre à jour tous les contrôles **dans la même PR** que le code ; relire les déclarations avant chaque soumission |
| **R6** | **Texte d'usage incomplet** (la caméra, ou le lieu de la photo dans les textes de localisation) | Apple 5.1.1(ii) « completely describe » | Moyenne | Textes du §3.2 |
| **R7** | **Politique ou CGU inexactes** (texte de 140 caractères, suppression par « un membre », « dernière position seulement ») | Apple 5.1.1(i) ; Google Data safety, politique de confidentialité | Élevée si les passages L1 à L16 ne sont pas alignés | P6 : alignement avant la mise en ligne ; **ne pas déployer le site avant** que les textes décrivent l'app livrée |
| **R8** | **Consentement qui ne cite pas la photo localisée** | Apple 5.1.1(ii), 5.1.5 ; RGPD | Moyenne | D20 / P5 / §9.3 : amender v3 ou passer en v4 **avant** UPDATE-1b ; la règle Firestore refuse déjà toute photo localisée sans seuil de version |
| **R9** | **Mineurs sans contrôle d'âge**, photos et lieu de mineurs visibles d'un adulte entré avec le code | Apple 5.1.4(b) ; Google public cible ; RGPD art. 8 | Moyenne | **Risque assumé par le PM** (J2, J31, R3 du ticket) ; réponses identiques et honnêtes aux questionnaires ; fiche et captures sans univers enfantin ; **non** destinée aux enfants ; jamais « relu par un juriste » |
| **R10** | **Politique « stalkerware » de Google** renforcée par la photo + le lieu (« où tu étais à la photo ») | Google « Device and Network Abuse » (lu le 2026-11-04) | Moyenne | **Risque assumé par le PM le 2026-11-04** (O8) ; rien d'autre n'est caché : caméra visible, réciprocité, aucun mode discret ; fiche sans vocabulaire de surveillance |
| **R11** | **Permission demandée « sans raison »** | Apple 5.1.1(iii) | Faible | Demandée à l'ouverture de l'Update seulement, après l'explication ; jamais au démarrage ; caméra active seulement écran visible ; jamais à distance |
| **R12** | **Pas d'alternative si la caméra est refusée** (iOS) | Apple 5.1.1(iv) | Faible à moyenne | §3.4 : vérifier le comportement du sélecteur système ; ne pas promettre un repli qui n'existe pas ; le reste de l'app marche sans caméra |
| **R13** | **Métadonnées (EXIF, GPS) envoyées par erreur** | Promesse de la politique ; Apple 5.1.1 | Faible si testé | Test d'octets sur dix photos (V3) ; **n'écrire L5 dans la politique qu'après** |
| **R14** | **Le relecteur ne voit pas la photo** (compte sans position publiée, un seul compte, pas de Miff) | Revue impossible → rejet « 2.1 » ou équivalent | Moyenne | Compte de démonstration à **deux comptes** dans une Miff, positions simulées, vidéo, note de revue §4 |
| **R15** | **Fiche inexacte** (« la photo disparaît », « sécurité garantie », « temps réel ») | Exactitude des métadonnées | Faible | `aso-marketing` : « visible 24 h par ton cercle » ; jamais « effacée partout » (capture d'écran possible) ; cohérence avec les CGU |
| **R16** | **Suppression de compte** qui laisserait la photo (toutes les Miffs) | Apple 5.1.1(v) ; Google | Faible | Vérifier que le lot de suppression couvre `photos` et `positions` de **chaque** Miff (tickets suppression de compte, déjà prévus) ; à ajouter à la recette |
| **R17** | **Classification d'âge ou public cible** mal remplis | Google « Misrepresentation ... may result in removal » ; Apple | Moyenne | §6 et §8 : réponses honnêtes, mêmes partout, refaites si l'app change |
| **R18** | **Aucun testeur externe n'a-t-il accepté v3 ?** non vérifié | D20 | — | P5 : vérification dans les consoles par le PM avant UPDATE-1b |

---

## 12. Contrôles dans le code avant publication (à ajouter au tableau V1 à V9 du `README.md`)

| # | Affirmation à vérifier | Qui |
|---|---|---|
| V10 | Le manifeste Android déclare `CAMERA` et **aucune** permission de stockage, de photothèque ni de micro ; `uses-feature` caméra (et frontale) à `required="false"` ; `PermissionPrivacyTest` et la CI mis à jour | `android-dev` |
| V11 | iOS : `NSCameraUsageDescription` présent en FR et EN avec le texte du §3.2 ; **aucune** clé `NSPhotoLibrary*` ni `NSMicrophone*` ; `check-binaries.sh` et `PrivacyInfo.xcprivacy` mis à jour | `ios-dev` |
| V12 | La photo publiée ne contient **aucune** métadonnée (test d'octets sur dix photos types) et pèse ≤ 150 000 octets | devs |
| V13 | La caméra ne démarre que si l'écran Update est visible et s'arrête en arrière-plan ; aucun déclenchement à distance | devs |
| V14 | Aucune photo n'est écrite dans la galerie ni sur disque avec le viseur intégré ; le repli système utilise un fichier du cache privé supprimé aussitôt | devs |
| V15 | Le lieu de la photo n'est écrit que si : partage actif, consentement ≥ seuil, position publiée, mesure récente (sinon photo non localisée sans message) ; aucune coordonnée dans un journal, Analytics, Crashlytics ou l'état sauvegardé | devs + `backend-data` |
| V16 | `updateText` vaut toujours `null` ; aucun champ de texte n'est livré avec la photo (sinon rétablir les lignes « Autres messages » / « Other User Content » des déclarations) | devs |
| V17 | Le signalement et le masquage local existent, fonctionnent hors ligne sans plantage, et leur libellé est clair (« Signaler cette photo » / « Ne plus voir les photos de {Prénom} ») | devs |
| V18 | La suppression de compte efface `photos` (avec lieu) **et** `positions` dans toutes les Miffs, y compris celles créées | devs + `backend-data` |
| V19 | Sur iPhone, comportement exact du repli « appareil photo du système » quand la caméra est refusée (§3.4) | `ios-dev` |

---

## 13. À faire par le PM, à la main (aucun agent ne le fait)

| # | Action | Quand |
|---|---|---|
| 1 | Lire et **trancher P1 à P8** (§1.2) | avant le début du développement de l'écran de signalement, et avant UPDATE-1b pour P5 |
| 2 | **Vérifier dans Play Console et TestFlight** qu'aucune personne extérieure n'a reçu un build avec le consentement `2026-11-v03` (P5, D20) | avant UPDATE-1b |
| 3 | Décider si tu veux **modifier toi-même `docs/legal/`** avec les propositions du §9 (ou demander à `release-manager` une PR dédiée) | avant la mise en ligne du site |
| 4 | **Être prêt à consulter la boîte e-mail de contact chaque jour** pendant les semaines de lancement, et à retirer un contenu (console Firebase ou créateur de la Miff) | dès la bêta externe |
| 5 | Préparer **deux comptes de démonstration** dans la même Miff (e-mail + mot de passe, sans double authentification), positions publiées, pour la revue | à la soumission |
| 6 | Enregistrer la **vidéo Apple** (≤ 60 s) et prendre les **captures** du §10 | quand l'Update (UPDATE-1, FEED-4) et le signalement sont livrés et recettés |
| 7 | Refaire / vérifier les **formulaires** à partir des §5 et §6 : Data safety, App Privacy, classification, public cible (réponses identiques partout) | à la soumission |
| 8 | Après le déploiement de R-PHOTO-LOC (ton « oui », `docs/dev/deploiement-regles.md`) : ordre **règles d'abord, builds ensuite** | avant UPDATE-1b |

---

## 14. Sources consultées le 2026-11-06 (et limites)

Ces règles changent : `release-manager` les relit avant chaque soumission. Les pages d'Apple n'affichent pas de date de mise à jour.

**Lues dans le texte**
- Apple, App Review Guidelines (1.1.4, 1.2, 1.2.1, 5.1.1, 5.1.2, 5.1.4, 5.1.5 ; seuls les 100 000 premiers caractères sur 114 428 ont été lus) : https://developer.apple.com/app-store/review/guidelines/
- Apple, App privacy details (types Photos or Videos, Precise Location, Other User Content ; « collect » ; données liées ; finalités) : https://developer.apple.com/app-store/app-privacy-details/
- Apple, valeurs et définitions de la classification d'âge (100 000 premiers caractères sur 116 853 : la partie sur la localisation et les dates d'entrée en vigueur n'a **pas** été lue) : https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions
- Google Play, politique « User Generated Content » : https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play, aide « Understand User Generated Content policy » : https://support.google.com/googleplay/android-developer/answer/12923286
- Google Play, Sécurité des données (collecter, partager, types, suppression) : https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play, « Permissions and APIs that Access Sensitive Information » (la caméra n'y figure pas) : https://support.google.com/googleplay/android-developer/answer/16558241
- Google Play, classification du contenu (conséquences d'une réponse inexacte) : https://support.google.com/googleplay/android-developer/answer/9859655
- Android, `<uses-feature>` (la permission `CAMERA` implique les fonctions caméra et autofocus ; `required="false"`) : https://developer.android.com/guide/topics/manifest/uses-feature-element

**Non lues dans le texte (résultats de recherche seulement : à relire au moment du remplissage)**
- Google Play, Families et public cible : https://support.google.com/googleplay/android-developer/answer/9893335 et https://support.google.com/googleplay/android-developer/answer/9285070
- Google Play, questionnaire de classification (thèmes « Displays User's Location to Others », « Online Interaction or Content Exchange », « UGC Sharing ») : https://support.google.com/googleplay/answer/6209544
- Apple, `SensitiveContentAnalysis` (disponibilité iOS 17+, entitlement, activation par l'utilisateur) : https://developer.apple.com/documentation/sensitivecontentanalysis (page non lue ; synthèse d'après des documentations de paquets tiers)

**Illisibles par mon outil**
- Apple, `NSCameraUsageDescription` (page de documentation) : ce que j'en dis (arrêt de l'app si la clé manque, localisation par `InfoPlist.strings`) vient de ma connaissance générale, pas de la page.
- Apple, « Describing data use in privacy manifests » (erreur 404) : les noms de clés du §3.2 viennent de documentations de SDK tiers ; à vérifier dans Xcode.

**Non vérifié** : la politique Google « Inappropriate Content » (alignement des interdits des CGU), les règles de l'App Store sur les métadonnées (exactitude de la fiche), la procédure exacte de TestFlight / Play Console pour lister les testeurs externes ayant accepté un texte (c'est le PM qui regarde).
