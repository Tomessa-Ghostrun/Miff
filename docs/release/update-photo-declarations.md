# Update (photo « Donner des nouvelles ») : ce qu'il faudra déclarer et justifier aux stores

Préparé le 2026-11-06 par `release-manager`, à partir du ticket `docs/tickets/update.md` (décisions D16 à D26), de la règle R-PHOTO-LOC (fusionnée, **déployée** depuis le 2026-11-06, fichier de `main` redéployé par le PM le 2026-11-10 : information du PM, non vérifiable par l'agent ; `backend/data-model.md`, en-tête, §2.8 et §11) et des dossiers de stores déjà préparés dans ce dossier. **Mis à jour le 2026-11-06 après les réponses du PM aux points P1 à P8** (§1.2, tous « décidés ») : les textes légaux de `docs/legal/` sont alignés (§9, PR `docs/legal-photo-localisee`).

**Rien n'est soumis, rien n'est rempli dans une console, rien n'est publié ni déployé** (aucun déploiement Firebase Hosting : les textes légaux sont modifiés dans le dépôt, pas en ligne). L'Update n'est **pas développée** : tout ce qui suit décrit l'app **telle qu'elle sera** si le ticket est livré tel quel, et doit être revérifié contre le code livré. Rien n'est « prêt à soumettre » : la checklist QA de sortie `docs/qa/release-checklist.md` n'existe pas encore. Ces textes n'ont **pas été relus par un juriste** (décision du PM du 2026-10-23, risque assumé) et ne doivent jamais être présentés comme tels.

Décisions D24 à D26 du 2026-11-06 (viseur plein écran, aperçu un peu plus petit, photo plein écran au toucher depuis le Feed, bouton central en pastille de couleur seule) : elles ne changent aucune déclaration ; elles changent seulement les captures (§10).

---

## 1. En bref pour le PM

### 1.1 Ce que l'Update ajoute aux déclarations

| Nouveauté | Conséquence côté stores |
|---|---|
| **Caméra intégrée** (permission `CAMERA` / `NSCameraUsageDescription`), alors que tous les dossiers actuels disent « aucune caméra » | Manifeste, `PermissionPrivacyTest`, CI, `PrivacyInfo.xcprivacy`, Data safety, étiquettes Apple : à mettre à jour ensemble (§3, §5, §6) |
| **Photo visible des membres de la Miff pendant 24 h** | Contenu généré par les utilisateurs (UGC) : Apple 1.2 et politique UGC de Google Play s'appliquent (§7) |
| **Lieu de la photo** (lat, lng, heure de mesure), figé à la prise de vue | Donnée la plus sensible du modèle après la position : texte de consentement, textes d'usage iOS, politique, étiquettes (§4, §7, §9) |
| **Photos de personnes, dont des mineurs et des tiers**, sans âge minimum | Apple 5.1.4, classification d'âge, public cible Google (§8) |

### 1.2 Points de décision : **tous décidés par le PM le 2026-11-06**

| # | Question | Décision du PM (2026-11-06) | Ce que cela change |
|---|---|---|---|
| **P1** | Signaler une photo dans l'app | **NON, pas besoin.** | Signalement **par e-mail seulement** (CGU art. 8, politique art. 8). Risque de rejet Apple 1.2 / Google UGC **assumé** (J34, R2) ; plan de repli au §11 bis (bouton qui ouvre un e-mail pré-rempli) |
| **P2** | Bloquer un membre | **NON.** | Seuls moyens écrits : quitter la Miff, ou retrait d'un membre par le créateur (CGU art. 5). **Assumé** (J34, R3) ; repli possible : masquage local (§11 bis). Ne jamais écrire qu'un blocage existe |
| **P3** | Délai de réponse | **On garde « une fois par semaine »** : aucune promesse de 72 h nulle part. | « Au moins une fois par semaine » dans les CGU art. 8 et la politique art. 8. Consulter la boîte plus souvent reste libre, **mais ne s'écrit nulle part**. Risque Apple « timely responses » (R4) assumé |
| **P4** | Filtrage automatique des images | **Aucun** ; assumer avec l'argumentaire (prise de vue en direct, cercle fermé, 24 h). **Ne jamais écrire « modéré ».** | Textes : « nous n'analysons pas les photos automatiquement ». Argumentaire au §7.4 et note de revue au §7.5. Assumé (J35, R1) |
| **P5** | Consentement : amender v3 ou passer en v4 | **Amender v3** (texte prêt) **et noter le plan v4** ; **le PM vérifie** dans Play Console et TestFlight si un testeur externe a déjà accepté la v3 d'origine. | Texte amendé et plan v4 dans `docs/legal/consentement-localisation-v3.md` (§1 bis, §3, §4). **Numéro `2026-11-v03` inchangé** : le dépôt ne l'impose pas (aucune valeur publiée ; seul le PM peut constater si un build de test l'a distribué à quelqu'un d'extérieur). Si quelqu'un l'a accepté : v4 (`2026-11-v04`), seuil de `isPhotoLocationConsentVersion` relevé à 04 |
| **P6** | Aligner les textes légaux (17 passages de la politique, 6 des CGU) | **OUI, maintenant.** | **Fait** dans cette PR (§9.1) ; rien n'est publié |
| **P7** | Lieu de la photo : option « sans lieu » ? | **NON** : une photo est **TOUJOURS** attachée à une position, avec l'heure de la mesure. | Les textes légaux, le consentement amendé et les textes d'écran l'écrivent. **Écart avec le ticket et la règle : voir §1.2 bis** |
| **P8** | iOS : repli « appareil photo du système » | **À vérifier par `ios-dev`** (V19), **rien d'autre promis dans les textes**. | Aucun texte légal ne cite ce repli ; la politique dit seulement que refuser la caméra n'empêche pas d'utiliser le reste de Miff |

### 1.2 bis Un écart à connaître (P7) : le ticket et la règle décrivent encore une photo qui peut être publiée sans lieu

- **Ce qui existe** : le ticket `docs/tickets/update.md` (D23, §6.5, §7 variante « si localisée », cas limites) dit que « le client n'écrit la localisation que si la mesure GPS est récente, sinon la photo n'est pas localisée, sans message d'erreur » ; la règle R-PHOTO-LOC (`lat`, `lng`, `measuredAt` **facultatifs**, `backend/data-model.md`) l'autorise. **Ce que le PM a décidé (P7)** : la photo est **toujours** localisée. Les textes légaux suivent P7 (« une photo est toujours publiée avec son endroit »).
- **Conséquence** : tant que le ticket n'est pas aligné, **les textes décrivent une app plus stricte que celle du ticket**. Il ne faut donc pas publier les textes avant l'alignement (déjà exigé par R7).
- **Questions que l'alignement doit trancher** (je ne les tranche pas ; ma recommandation en gras) :
  1. Si la mesure est impossible ou trop ancienne à l'obturateur (autorisation retirée entre-temps, intérieur sans signal) : **re-mesurer brièvement (3 s au plus), puis, à défaut, ne pas publier et afficher un message calme** (par exemple « On n'arrive pas à te situer pour l'instant. Réessaie dans un moment. ») plutôt que publier sans lieu, qui rendrait les textes faux.
  2. Règle Firestore : **rendre l'endroit obligatoire côté client d'abord** (aucun changement de règle déployée), puis côté règle plus tard (un client ancien serait sinon refusé) ; décision `backend-data`.
  3. Un membre qui ne partage pas sa position **ne voit aucune photo** (conséquence de la réciprocité sur une photo toujours localisée) et un compte sans consentement suffisant ne peut **pas publier**. Cohérent avec « Toujours est obligatoire », mais à écrire dans la recette.
- **Qui** : `product-specs` (ticket D23, §6.5, §7, §8), `backend-data` (`data-model.md` §2.8, R-PHOTO-LOC), puis `android-dev` / `ios-dev`. Ces fichiers ne sont pas modifiés par la présente PR.

### 1.3 Risques de rejet principaux (détail et parades aux §11 et §11 bis)

1. **Apple 1.2 sans filtrage automatique** ni signalement ni blocage dans l'app : risque **moyen à élevé**, **assumé** (P1, P2, P4).
2. **Google UGC : signalement ET blocage « dans l'app »** : risque **élevé**, **assumé**. Plan de repli prêt (§11 bis).
3. **Incohérence déclarations / app / textes** (caméra, photo « par un membre », texte de 140 caractères, position seule, photo « parfois sans lieu » contre P7) : motif de rejet le plus évitable.
4. **Photos et lieu de personnes mineures sans contrôle d'âge** (Apple 5.1.4, classification d'âge, public cible Google) : risque J2 / J31 / J36, **assumé**.
5. **Consentement qui ne cite pas la photo localisée** (Apple 5.1.1(ii) et 5.1.5) : évité par le texte amendé (P5), **si** la vérification des consoles est faite.

---

## 2. Ce que l'Update fait réellement (faits à déclarer, sources : ticket et modèle de données)

À recopier tel quel dans les formulaires **seulement après** vérification dans le code livré.

| Fait | Valeur | Source |
|---|---|---|
| Prise de vue | Caméra intégrée (CameraX / AVFoundation), **en direct uniquement** ; aucune photo de galerie ; aucune permission de photothèque ni de stockage ; la photo vit en mémoire jusqu'à la publication (jamais dans un fichier ni dans la photothèque de l'app **par l'auteur** (le destinataire peut enregistrer une photo reçue par un geste explicite : §16) ; **iOS** : le cache local du SDK Firestore peut ensuite en garder une copie, chiffrée par la protection des données d'iOS, purgée à la déconnexion et à la suppression du compte) | ticket §5.3, §5.6, P4 |
| Métadonnées | JPEG **ré-encodé depuis les pixels** : ni EXIF, ni GPS, ni date, ni modèle (**à prouver par un test d'octets**, critère V3 du ticket) | ticket §5.6 |
| Taille | ≤ 150 000 octets, portrait 3:4, côté long ≤ 1 024 px | `firestore.rules` `validPhoto`, ticket V7 |
| Hébergement | Octets dans Firestore (`miffs/{id}/photos/{uid}`), région Paris (europe-west9), **sans Cloud Storage** ; une copie par Miff où la personne partage ; **chaque photo écrase la précédente** | `data-model.md` §2.8 |
| Visibilité | Membres de la Miff dont le partage est actif ; **le créateur n'a aucun droit de plus** | `data-model.md` §2.8, §9 |
| Durée | Illisible après 24 h (23 h côté client) ; suppression physique « au mieux » (par l'app de son auteur, au lancement) ; supprimée avec l'arrêt du partage, le départ, le retrait, la suppression de la Miff ou du compte | `data-model.md` §2.8, ticket §5.8, C12 |
| Lieu de la photo | `lat`, `lng` (5 décimales), `measuredAt` (heure de la **mesure**), **présents sur toute photo publiée** (décision P7, 2026-11-06) ; une seule mesure au moment de l'obturateur (≤ 3 s), écrite avec consentement amendé (ou v4) et partage actif. **Écart avec le ticket (D23) et la règle R-PHOTO-LOC, qui les laissent facultatifs : §1.2 bis** | `data-model.md` en-tête (R-PHOTO-LOC), ticket §6.5, D23, P7 |
| Lecture du lieu | Toute photo étant localisée : lisible seulement de l'auteur ou d'un membre qui a **publié sa propre position** (réciprocité). Conséquence produit : un membre qui ne partage pas sa position **ne peut ouvrir aucune photo** (« Partage ta position pour voir cette photo. ») | `data-model.md` « Lecture » |
| Ce que le serveur ne vérifie pas | Que la position ou l'heure soient vraies (client modifié), que la photo corresponde au lieu | `data-model.md` « Limites connues » |
| Analytics, plantages, journaux | Aucun événement, aucune coordonnée, aucun contenu de photo (D12) | ticket D12 |
| Analyse du contenu | **Aucune** (pas de détection de visage, pas d'IA) | ticket §5.6 |
| Texte libre avec la photo | **Aucun en v1** (`updateText = null`) | ticket C4 |
| Notification | **Aucune** | ticket D10 |
| Limite assumée | Un proche peut faire une capture d'écran **ou enregistrer la photo avec « Enregistrer cette photo » (seulement s'il le choisit, §16)** : « illisible après 24 h » vaut pour l'app, pas pour une capture ni pour une photo enregistrée | ticket R5, CGU art. 8 |

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
| Français | « Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. La photo est publiée avec ta position et visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta photothèque. » |
| English | "Miff uses the camera only when you open the “Share news” screen, to take a photo of you or of what's around you. The photo is published with your position and can be seen by the members of your Miffs for 24 hours, then it can no longer be read. Nothing is saved to your photo library." |

**Texte final livré par UPDATE-3 (iOS, PR #111)** : le texte ci-dessus (plus complet que la proposition initiale, **volontairement** : il cite la position jointe à la photo, la durée et la fin de lisibilité, ce qu'exige « clearly and completely »). Il est dans `ios/Miff/Resources/InfoPlist.xcstrings` (FR et EN) et en valeur par défaut anglaise dans `ios/project.yml`. **La phrase sur le lieu de la photo est livrée aussi** dans les deux textes de la localisation (`NSLocationWhenInUseUsageDescription` et `NSLocationAlwaysAndWhenInUseUsageDescription`), mot pour mot comme ci-dessous ; **le texte d'usage de la localisation d'Android n'est pas concerné** (autre mécanisme). **Limite à dire honnêtement** : Après la publication, le cache local du SDK Firestore peut en garder une copie (chiffrée par la protection des données d'iOS, purgée à la déconnexion et à la suppression du compte) ; l'app ne crée elle-même aucun fichier de la photo.

Le nom du bouton est celui du nom parlé du ticket (« Donner des nouvelles » / « Share news ») : à aligner sur le libellé final des maquettes.

**À ajouter aux textes d'usage de la localisation** (`app-store-confidentialite-et-revue.md` §4), parce que le lieu de la photo est un usage de la position qu'ils ne citent pas aujourd'hui (5.1.1(ii) « completely ») :

| Langue | Phrase à ajouter à `NSLocationWhenInUseUsageDescription` et à `NSLocationAlwaysAndWhenInUseUsageDescription` |
|---|---|
| Français | « Quand tu publies une photo, l'endroit où tu l'as prise y est joint, visible pendant 24 h des membres qui partagent aussi leur position. » |
| English | "When you publish a photo, the place where you took it is attached to it, visible for 24 hours to the members who also share their location." |

**Autres clés à ne PAS ajouter** : `NSPhotoLibraryUsageDescription` (lecture de la galerie : Miff ne la lit jamais), `NSMicrophoneUsageDescription` (aucun son). **Mise à jour du 2026-11-11 : `NSPhotoLibraryAddUsageDescription` (ajout seulement) est désormais EXIGÉE sur iPhone** pour « Enregistrer cette photo » (§16) ; ce n'est pas la caméra qui l'utilise. `ios/ci/check-binaries.sh` doit **vérifier leur absence** et la **présence** de la clé caméra (aujourd'hui il vérifie l'absence de `NSLocation*`, et le ticket note qu'aucun contrôle `NSCamera*` n'existe).

**Manifeste de confidentialité** (`PrivacyInfo.xcprivacy`) : ajouter les types collectés « photos ou vidéos » (clé `NSPrivacyCollectedDataTypePhotosorVideos` d'après des documentations de SDK tiers ; **l'orthographe exacte est à vérifier dans Xcode** car la page d'Apple n'était pas accessible) avec Linked = vrai, Tracking = faux, finalité `NSPrivacyCollectedDataTypePurposeAppFunctionality`. La position précise y est déjà prévue (`app-store-confidentialite-et-revue.md` §3).

### 3.3 L'explication dans l'app, avant la boîte du système

Les deux stores attendent que l'usage soit expliqué dans l'app (Apple 5.1.5 pour la position ; 5.1.1(ii) pour le consentement). Le texte de l'écran d'explication est déjà au ticket §7 (« Une photo pour tes proches » / « A photo for your loved ones ») : il dit à quoi sert la caméra, qui voit la photo, 24 h, rien dans la galerie. **À aligner** : ajouter la phrase sur le lieu de la photo **quand UPDATE-1b est livré** (texte à écrire avec le consentement amendé, §9.3). Cohérence obligatoire entre : écran d'explication, `NSCameraUsageDescription`, politique art. 4, consentement v3.

### 3.4 Que se passe-t-il si la personne refuse la caméra ? (Apple 5.1.1(iv))

Apple écrit : « Apps must respect the user's permission settings and not attempt to manipulate, trick, or force people to consent to unnecessary data access ... Where possible, provide alternative solutions for users who don't grant consent. » (lu le 2026-11-06).
- **Ce qui est conforme** : la caméra n'est demandée que pour l'Update ; tout le reste de l'app marche sans ; le refus n'est jamais bloquant ni culpabilisant (états B, C, D du ticket).
- **Point d'attention (P8, décidé le 2026-11-06 : à vérifier par `ios-dev`, rien d'autre n'est promis)** : sur Android, l'alternative « appareil photo du système » **n'exige aucune permission** (mécanisme de la photo de profil). Sur iPhone, ma connaissance est que le sélecteur système en mode caméra **demande aussi l'autorisation caméra** : si c'est exact, il n'existe **pas** d'alternative sans permission sur iOS, et l'état C doit le dire honnêtement (« Ouvrir les réglages ») plutôt que de promettre un repli. **`ios-dev` vérifie sur l'iPhone 11 Pro avant la PR** (contrôle V19). **Aucun texte légal ni aucune note de revue ne cite ce repli**, et le ticket (états C et D, bouton « Utiliser l'appareil photo du téléphone ») doit être ajusté sur iOS si la vérification est négative.

---

## 4. Justification de la localisation de la photo (aux relecteurs et dans les textes)

**À ne pas faire** : l'ajouter à la « fonctionnalité unique » de la déclaration Google de localisation en arrière-plan (`google-play-localisation-arriere-plan.md` §3). Google exige **une seule** fonctionnalité ; le lieu de la photo est une mesure **au premier plan**, au moment de l'obturateur, avec la permission déjà accordée : **aucune permission nouvelle, aucun usage en arrière-plan en plus.** Elle se déclare dans la politique, le consentement, les étiquettes et la note de revue.

**Paragraphe à ajouter à la note de revue Apple** (anglais) et à reprendre dans « App access » côté Google si nécessaire :

> **Photo and its location ("Share news").** The "Share news" button opens an in-app camera (live capture only: no photo library reading, and nothing is ever saved to the device gallery automatically: a received photo is saved only if the person taps "Save this photo" in its full-screen view, with add-only access on iPhone and no permission on Android). The user previews the photo and publishes it; it is then visible for 24 hours only to the members of the user's Miffs, and replaces the previous one. Every photo is published with the place where it was taken: Miff takes a single foreground location measurement at the moment of the shot and attaches the coordinates and the measurement time to the photo (the user has accepted the location consent text, which mentions this). The location of a photo can be opened only by members who also share their own location, and is deleted with the photo (24 hours, or when sharing stops, the user leaves, or the account is deleted). The camera is never started in the background or remotely, and photos are not analysed. A member who does not share their own location cannot open a photo: the app then shows "Share your position to see this photo", which is expected behaviour, not a defect.

**Pourquoi la dernière phrase** : un relecteur qui teste avec un compte qui n'a pas publié sa position verra un message à la place de la photo et pourrait croire que l'app est cassée. Le **compte de démonstration** (README du dossier, action 7) doit donc avoir **deux comptes** dans la même Miff, **chacun avec sa position publiée** (position simulée).

**Réciprocité et textes** : la formule déjà employée partout (« seuls les membres de la Miff qui partagent aussi la leur ») reste vraie pour le lieu de la photo ; ce qu'il faut **ajouter** est que le lieu est une **seconde copie de position**, attachée à la photo, qui **peut différer de la dernière position connue** pendant 24 h (pas d'historique de trajets, mais deux positions au plus par Miff). C'est écrit dans la politique (art. 4) et dans le consentement amendé (P5).

---

## 5. Google Play : Data safety (modifications à reporter dans `google-play-data-safety.md`)

Définitions lues le 2026-11-06 (aide Google, 10787469) : « collecter » = transmettre hors de l'appareil ; « partager » = transférer à un **tiers**, hors prestataire, hors action précise de l'utilisateur ; « localisation précise » = zone de moins de 3 km² ; types « Photos » (« A user's photos ») ; finalités dont « App functionality » ; question de suppression : « Does your app provide a way for users to request deletion of their data? » (« There is no prescribed mechanism »). La page **ne traite pas** le cas de données visibles d'autres utilisateurs de l'app : **O9 reste ouvert** (voir README).

| Catégorie | Type | Réponse proposée pour l'Update | Écart avec le tableau actuel |
|---|---|---|---|
| Photos et vidéos | **Photos** | **Collecté : oui** (octets envoyés à Firestore). **Partagé : voir O9** (recommandation prudente inchangée : déclarer le partage avec d'autres utilisateurs plutôt que rien). **Obligatoire ? Facultatif** (la personne choisit de publier ; elle peut ne jamais utiliser l'Update). **Finalité : App functionality.** **Traitée de façon éphémère : non** (stockée jusqu'à 24 h sur le serveur). Chiffrée en transit : oui. Suppression : oui | Ligne existante « photo de profil facultative ; photo de mise à jour facultative » : **conservée**, mais préciser « prise avec la caméra de l'app ». La photo de mise à jour devient **obligatoire pour publier** (D16) sans devenir obligatoire pour l'app : « facultatif » reste exact |
| Localisation | **Localisation précise** | Déjà **collectée, obligatoire** (« Toujours »). Le lieu de la photo est **la même catégorie** ; aucune ligne de plus. Mentionner dans les notes : « jointe aussi à la photo (24 h) » | Notes de la ligne existante à compléter |
| Messages | « Autres messages dans l'app » (petit texte de 140 caractères) | **À retirer** si l'Update est livrée sans texte (ticket C4 : `updateText = null`). Garder la ligne **uniquement** si un texte est effectivement livré | **La ligne actuelle est inexacte avec le ticket Update** : une sous- ou sur-déclaration est un écart avec l'app |
| Audio | — | **Non collecté** (aucun micro, pas de `RECORD_AUDIO`) | à confirmer par `android-dev` |
| Fichiers et documents | — | **Non** : aucune lecture de fichiers ; l'écriture d'une photo reçue dans la galerie n'a lieu que sur le geste « Enregistrer cette photo » (local, rien n'est envoyé : pas une collecte, §16) ; le repli système utilise un fichier du cache privé, supprimé aussitôt | à confirmer |
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

| Exigence | État de Miff (2026-11-06) | Écart avec le store | Décision |
|---|---|---|---|
| Conditions acceptées **avant** de publier | Acceptées au clic sur « Rejoindre » (CGU art. 3), avant toute photo | Aucun | — |
| Contenus répréhensibles **définis** | CGU art. 7 et 8 complétés le 2026-11-06 : photos intimes sans accord, contenu qui met un mineur en danger, retrait et suspension | Pas d'alignement mot à mot sur la politique « Inappropriate Content » de Google (**non lue**) | À relire au remplissage |
| **Filtrage** (Apple) | **Aucun** (« nous n'analysons pas les photos automatiquement ») | Apple 1.2, 1er point : non satisfait | **P4 : assumé**, argumentaire §7.4. Le filtre sur l'appareil `SensitiveContentAnalysis` ne convient pas (il ne s'active que si l'utilisateur l'a activé dans iOS, et l'app vise iOS 16 ; synthèse tierce, page non lue) |
| **Signaler** | **E-mail uniquement** (CGU art. 8, politique art. 8) | Apple 1.2, 2e point ; Google « in-app system » : **non satisfaits** | **P1 : assumé** (J34) ; repli §11 bis |
| **Bloquer** | Quitter la Miff ; le créateur retire un membre (code renouvelé) ; suspension à la main (CGU art. 12) | Apple 1.2, 3e point ; Google : **non satisfaits** | **P2 : assumé** (J34) ; repli §11 bis |
| **Réponse dans un délai** | « Au moins une fois par semaine » (CGU art. 8) | Apple « timely responses » : discutable | **P3 : hebdomadaire**, aucune promesse plus courte |
| **Contact publié** | E-mail du support dans les CGU et la politique (provisoire, décision du 2026-10-24) | Contact **dans l'app** (Réglages) et URL de support dans les fiches : à vérifier | Ticket Réglages ; l'URL de support est un champ obligatoire d'App Store Connect |
| Action sur les contenus en infraction | Possible à la main (console Firebase) ; le créateur peut retirer un membre | Procédure écrite absente | Procédure de 5 lignes à écrire par `backend-data` (qui supprime quoi, comment) |

### 7.4 Ce qui joue en faveur de Miff (à dire aux relecteurs, sans le présenter comme une exemption)

- Prise de vue **en direct uniquement**, jamais de galerie : on ne peut pas publier un contenu téléchargé ni une image trouvée sur Internet.
- **Cercle fermé** : on ne voit que les photos des membres de sa propre Miff ; aucune recherche, aucun profil public, aucun commentaire, aucun partage, aucune messagerie.
- **Une seule photo par personne, 24 h**, remplacée à chaque publication.
- Créateur qui **retire** un membre ; chacun peut **quitter** ; suppression de compte dans l'app.

### 7.5 Paragraphe de note de revue Apple pour l'UGC (anglais)

> **User-generated content (guideline 1.2).** The only user-generated content is a single live-camera photo per user (no gallery upload, no text, no comments, no messaging), published with the place where it was taken and visible for 24 hours only to the members of the user's private circle ("Miff"), which can be joined only with a secret code. There is no public feed or discovery. The terms (accepted at sign-up) forbid illegal, violent, hateful or sexually explicit content, intimate photos without consent, and photos of people without their consent. Reports are handled by e-mail at tomessa.mfouka@gmail.com, which we check at least once a week; we can remove content and suspend accounts. Any member can leave a circle, the circle's creator can remove a member, and users can delete their account in the app. We do not run automatic image filtering (photos are not analysed), and the app has no in-app report or block button at this time. Contact: tomessa.mfouka@gmail.com [et URL de support].

La dernière proposition de ce paragraphe est **volontairement honnête** : ne jamais la remplacer par une affirmation contraire. Le PM peut décider de la retirer, jamais de la contredire.

À ne **jamais** écrire : « modéré », « filtré », « vérifié » ou « sans risque », ni qu'un signalement ou un blocage existe dans l'app, ni un délai de réponse autre que « au moins une fois par semaine ».

---

## 8. Mineurs et âge minimum

- **Cadre déjà noté** : aucun âge minimum, aucun contrôle d'âge ; risques J2, J31 (`docs/legal/README.md`), points 2, 15, 20, 24 de `data-model.md` §11 ; **assumés par le PM** (2026-10-23, 2026-11-01). Le ticket Update reprend R3 : « risque assumé par le PM (décisions du 2026-10-23 et du 2026-11-01), jamais présenté comme relu par un juriste ». Rien ne change dans ce statut ; **l'Update ajoute deux cas** : la **photo** d'une personne (visage, domicile) qui peut être mineure, et le **lieu de la photo**. Pour la photo, le ticket (R3) l'écrit déjà comme assumé ; l'extension au lieu de la photo est écrite comme J36 et J37 dans `docs/legal/README.md` (§9.4), comme risque assumé (décisions des 2026-10-23, 2026-11-01 et P7 du 2026-11-06) ; le PM peut me dire s'il ne la considère pas assumée. Aucun de ces points n'est écrit comme « à valider » ni comme relu.
- **Apple 5.1.4(b), lu le 2026-11-06** : les apps « that collect, transmit, or have the capability to share personal information (such as name, address, email, location, photos, videos, drawings, the ability to chat, other personal data ...) from a minor must include a privacy policy and must comply with all applicable children's privacy statutes. » Miff **a la capacité** de partager lieu et photo d'un mineur : la politique de confidentialité est donc obligatoire (elle existe) et la conformité aux lois sur la vie privée des enfants est l'affaire du PM (risque assumé). Même article : une app **hors** catégorie Enfants ne peut employer, dans son nom, sous-titre, icône, captures ou description, aucun terme laissant entendre que le public principal est enfantin (« For Kids » : réservé à la catégorie Enfants, guideline 2.3.8 citée).
- **Google Play** (résultats de recherche vers les pages 9893335 « Families » et 9285070 « target audience », **non lues dans le texte**) : le public cible est déclaré dans la console ; l'imagerie et la terminologie peuvent amener Google à réévaluer le public déclaré ; une app non destinée aux enfants ne doit pas leur plaire par inadvertance. À lire par le PM au moment du remplissage.
- **Recommandations (O7 du README, confirmées)** : ne pas déclarer l'app comme destinée aux enfants ; répondre **de la même façon partout** ; **captures et fiche sans enfants ni univers enfantin** (§10) ; ne pas écrire « pour toute la famille » dans le sous-titre sans réfléchir (le mot « famille » reste admis, « enfants » non).
- **DÉCIDÉ par le PM le 2026-11-07 (âge)** : « prenons le minimum, donc 3 ans » = **classification de CONTENU au minimum possible (PEGI 3 / IARC « tous publics » côté Google Play, 4+ côté Apple, résultats des questionnaires)** ; **ce n'est PAS un choix de public cible** : si une tranche d'enfants est cochée au public cible, la politique Familles de Google s'applique, et une app qui demande la localisation en arrière-plan ne peut pas cibler les enfants ; **le PM n'a pas décidé de cibler les enfants** ; **la recommandation reste le public cible 13+** tant qu'il ne confirme pas explicitement le contraire (`docs/release/decisions-stores-avant-soumission.md` §2). Les CGU disent déjà « pas d'âge minimum » ; le risque mineurs / RGPD art. 8 reste **assumé**, jamais « à valider par un juriste ».
- **Photo de personnes tierces** : CGU art. 8 l'interdit sans accord, mais aucun filtre ne l'empêche (R3 du ticket). C'est un argument à garder en tête pour la rubrique UGC, pas une parade.

---

## 9. Politique de confidentialité et CGU : passages adaptés le 2026-11-06 (P6)

Les quatre textes (`politique-confidentialite.fr.md` / `.en.md`, `cgu.fr.md` / `.en.md`) ont été modifiés **dans les deux langues à la fois**. Le numéro « 0.1 » et la date « 4 novembre 2026 » sont **conservés** : aucun texte n'est publié, la date réelle est à mettre le jour de la mise en ligne (README légal §3). Éditeur et contact provisoires inchangés (décision du 2026-10-24). Rédigés par l'équipe d'agents, publiés sous la seule responsabilité du PM, **sans relecture juridique** : les textes ne le prétendent nulle part.

### 9.1 Politique de confidentialité : les 17 passages

| # | Passage | Fait | À noter |
|---|---|---|---|
| L1 | Art. 1, point 1 | « dernière position connue et, si tu publies une photo, l'endroit où elle a été prise : rien d'autre » | — |
| L2 | Art. 1, point 7 (et 4, 5) | Photo prise avec la caméra de l'app, jamais depuis la galerie, publiée avec son endroit, visible 24 h ; réciprocité et effacement étendus à la photo | — |
| L3 | Art. 3, tableau | Ligne « Mise à jour manuelle (140 caractères) » **remplacée** par « Photo "Donner des nouvelles" » (sans texte) et nouvelle ligne « Endroit de ta photo » | « enregistrée sans ses métadonnées » **dépend de V12** (test d'octets) : retirer cette précision si le test échoue |
| L4 | Art. 3, « ce que Miff ne collecte pas » | Endroit de la photo (24 h) ajouté ; « ta galerie de photos » ajoutée | — |
| L5 | Art. 3, nouveau paragraphe « Caméra et photo » | Caméra active seulement écran ouvert, jamais en arrière-plan ; photo en mémoire ; rien dans la galerie ; pas d'analyse ; refuser la caméra n'empêche pas le reste de l'app | Dépend de V12, V13, V14. **P8 : aucun repli iOS promis** |
| L6 | Art. 4 | Puce « Quand tu publies une photo… » et sous-section **« L'endroit de ta photo, en détail »** (ce qui est enregistré, **toujours** avec la photo, qui le voit, quand il est effacé, non vérifiable par le serveur, capture d'écran, jamais ailleurs) | — |
| L7 | Art. 4, « Ton consentement » | « version 3 ou plus récente … et l'endroit de tes photos » ; nouvel accord demandé avant d'enregistrer la position **ou de publier une photo** | **Sans numéro précis** : reste vrai avec un plan v4 |
| L8 | Art. 5, « Qui voit quoi » | Photo + endroit + heure de la mesure, **visibles seulement des membres qui partagent aussi leur position** (un membre qui ne partage pas ne peut pas ouvrir une photo) | — |
| L9 | Art. 5 | Phrase sur les captures d'écran (« illisible après 24 heures » vaut pour l'app, pas pour une capture) | — |
| L10 | Art. 7, ligne photo | « supprimées … **par l'app de leur auteur** à une ouverture suivante » (plus « d'un membre ») | `data-model.md` §2.8 à aligner par `backend-data` (C12 du ticket) |
| L11 | Art. 7, même ligne | L'endroit suit la durée de la photo ; suppression physique non garantie à la 24e heure ; photo d'un compte qui n'ouvre plus l'app = stockée, illisible | J8 complété |
| L12 | Art. 4 et art. 8 | L'arrêt du partage supprime « ta photo avec son endroit » | — |
| L13 | Art. 9 | La lecture de l'endroit de la photo est sous la même règle que la position | — |
| L14 | Art. 10, mineurs | Photos et endroit d'un mineur visibles de sa Miff ; Miff n'analyse pas les photos et ne contrôle pas l'âge | Risque J36 assumé |
| L15 | Art. 7 (ligne téléphone) et art. 11 | Copie des photos reçues tant qu'elles sont lisibles ; photo préparée en mémoire seulement | **À confirmer par les devs de FEED-4** (copie locale) ; retirer la mention si FEED-4 ne garde rien |
| L16 | Art. 12 | « texte de consentement à la localisation … de ta dernière position connue et de l'endroit de tes photos » | Sans numéro |
| L17 | En-tête | **Non modifié** (date et version) | À faire le jour de la mise en ligne |

Ajoutés en plus : art. 1 point 4 (réciprocité étendue), art. 6 (Firestore : « photos et leur endroit »), art. 8 (**puce « Signaler une photo ou un contenu »**, par e-mail, boîte consultée au moins une fois par semaine, rappel que Miff n'est pas un service d'urgence).

### 9.2 CGU

| # | Passage | Fait |
|---|---|---|
| G1 | Art. 2 | « Tu peux aussi donner de tes nouvelles en publiant une photo, prise avec la caméra de l'app (sans texte) ; elle est publiée avec l'endroit où elle a été prise. » |
| G2 | Art. 6 | Puce « Ta photo et son endroit » ; « qui la voit » et « effacement » étendus à la photo ; arrêt du partage supprime la photo avec son endroit |
| G3 | Art. 7 | Photo intime ou dénudée sans accord, contenu qui met un mineur en danger ; « photos Donner des nouvelles » nommées |
| G4 | Art. 8 (réécrit) | Fonctionnement de la photo ; **signalement par e-mail, boîte consultée au moins une fois par semaine** ; « nous n'analysons pas les photos automatiquement » ; retrait et suspension (art. 12) ; **quitter la Miff / retrait d'un membre par le créateur** ; aucune mention d'un signalement ou d'un blocage dans l'app |
| G5 | Art. 8, capture d'écran | Inchangé (déjà exact) |
| G6 | Art. 3, âge | Inchangé : risque assumé, jamais « à valider » |

Ajoutés en plus : encadré d'ouverture (« l'endroit d'une photo est celui du moment où elle a été prise, pas la position actuelle »), art. 12 (« en particulier les articles 6, 7 et 8 »).

### 9.3 Consentement et textes d'écran à aligner (hors `docs/legal/`, non modifiés ici)

- **Consentement** : `docs/legal/consentement-localisation-v3.md` (§1 bis : amendement et **plan v4**, §3 et §4 : textes FR/EN amendés, §6 : O4 tranché, §7 : cases à cocher). Procédure (D20) : le PM vérifie les consoles ; si **personne** d'extérieur n'a accepté la v3 d'origine, on amende (aucun changement de constante ni de règle) ; sinon v4 (`2026-11-v04`, seuil de `isPhotoLocationConsentVersion` relevé à 04, constantes Android et iOS changées dans le même commit, règles redéployées sur « oui » du PM).
- **Écran d'explication de la caméra** (ticket Update §7) : ajouter l'endroit. Texte proposé, FR : « Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. La photo est visible des membres de tes Miffs pendant 24 h, avec l'endroit où tu l'as prise ; ensuite elle n'est plus lisible. Rien n'est enregistré dans ta galerie. » EN : "To take a photo of you, or of what's around you, Miff needs access to your camera. The photo can be seen by the members of your Miffs for 24 hours, along with the place where you took it; then it can no longer be read. Nothing is saved to your gallery."
- **Ligne de visibilité de l'aperçu** : la variante « si localisée » devient **la seule** : « Tes proches verront cette photo pendant 24 h. Ceux qui partagent aussi leur position verront où tu l'as prise. »
- **`NSLocation*UsageDescription` (iOS)** : phrase du §3.2 mise à jour (« est joint » au lieu de « peut y être joint »).

### 9.4 Risques ajoutés (écrits comme assumés)

`docs/legal/README.md` §4 : **J34** (aucun signalement ni blocage dans l'app, rejet Apple 1.2 / Google UGC), **J35** (aucun filtrage automatique), **J36** (mineurs : photo et endroit), **J37** (localisation sensible, copie datée d'une position) ; J8, J16 et J31 complétés. Écrits « assumés par le PM (2026-11-06) », jamais « à valider ». Pour les mineurs, l'extension à la photo s'appuie sur les décisions des 2026-10-23 et 2026-11-01 et sur P7 : **le PM peut me dire s'il ne la considère pas assumée**.

### 9.5 Pages web : fichiers à mettre à jour à la publication (rien n'est publié ni déployé)

| Fichier | État | Détail |
|---|---|---|
| `docs/legal/cgu.fr.md`, `cgu.en.md`, `politique-confidentialite.fr.md`, `.en.md` | **Modifiés (cette PR)** | Sources du texte légal : `scripts/build-web.mjs` (`LEGAL_SOURCES`) en tire les pages `/fr/cgu`, `/en/terms`, `/fr/confidentialite`, `/en/privacy` **au build**. Aucune autre modification du dossier `web/` n'est nécessaire pour elles |
| `web/src/fr/supprimer-mon-compte.html` (l. 24), `web/src/en/delete-account.html` (l. 24) | **Modifiés (cette PR)** | Phrase « arrêter le partage » : « ta photo (avec son endroit) ». Modification d'une seule expression par langue, hors texte légal généré ; aucun test ne la fige ; `node --test "web/tests/*.test.mjs"` : 70 tests passés |
| `web/src/landing.json`, clés FR (l. 52, 61, 63, 65, 72, 99) et EN (l. 174, 183, 185, 187, 194, 221) : `b3Text`, `t2Text`, `t3Text`, `t4Text`, `who2What`, `a1` | **À modifier avant la mise en ligne, non fait** | Page d'accueil (marketing, **pas** la source du texte légal : décision de ton à relire par `aso-marketing`). Elles disent « Miff ne garde que ta dernière position connue » (`t4Text`) ou ne citent que la position. Proposition de fond : ajouter « et, si tu publies une photo, l'endroit où elle a été prise (24 h) » à `t4Text`, `a1` et `who2What`, et « ta photo » à `t2Text` (arrêt du partage). Les tests de `landing.test.mjs` ne figent pas ces textes (ils vérifient la parité FR/EN des clés) |
| `web/site.config.json` | Inchangé | Éditeur, adresse postale, e-mail (provisoires) |
| Date et version « 0.1 — 4 novembre 2026 » (en-tête des quatre textes) | À mettre à jour **le jour réel** de la mise en ligne | README légal §3 |
| Déploiement | **Jamais fait par un agent** | « Oui, déploie » explicite du PM ; garde-fou `scripts/web-predeploy-check.mjs` ; actions de console Analytics du README légal §3 d'abord ; **pas avant** l'alignement du ticket Update sur P7 (§1.2 bis) ni la confirmation de L5 et L15 par les devs |

---

## 10. Captures d'écran et vidéo à prévoir

**Règles communes** (Apple 5.1.4 et Google « target audience », fiche accurate) : **comptes de test**, jamais de vrais proches ; **photos libres de droit ou prises par des adultes de l'équipe consentants** (aucun enfant, aucun univers enfantin) ; **lieux et adresses fictifs** (jamais le domicile du PM ; position simulée) ; numéros de téléphone factices ; heures cohérentes (« Photo de Léa · 15 h 40 ») ; **aucun vocabulaire de surveillance** (« suivre », « espionner », « temps réel »), un lieu de photo n'étant **pas** une position en direct ; la bulle orange/rouge peut être montrée mais `aso-marketing` en décide (ton). **Ne jamais capturer ni filmer une fonction qui n'existe pas** : après les décisions P1 et P2, il n'y a **ni bouton de signalement ni blocage** dans l'app, donc aucune capture ne les montre et aucune fiche ne les mentionne. Les tailles exigées sont celles affichées par App Store Connect et la Play Console au moment du téléversement (non listées ici).

| # | Écran | Source | Pour | Remarques |
|---|---|---|---|---|
| S1 | **Écran d'explication de la caméra** (« Une photo pour tes proches ») | maquettes de design (PR #83, `04-permission-camera`) | Revue Apple et Google (preuve de l'explication avant la boîte) ; fiche optionnelle | Doit montrer « 24 h », « rien dans ta galerie » **et l'endroit de la photo** (texte du §9.3) : **à refaire** avec le texte aligné |
| S2 | **Viseur** (selfie, rond bleu, point de couleur du bouton central) | `design/exports/update/` (7 PNG du PM) et PR #83 | Fiche (iOS et Android) | Décision D24 : plein écran ; refaire la capture avec la version finale |
| S3 | **Aperçu** (croix, Recommencer, Publier, ligne « Tes proches verront cette photo pendant 24 h. Ceux qui partagent aussi leur position verront où tu l'as prise. ») | idem | Fiche + revue | Montrer la ligne de visibilité **avec la mention de l'endroit** : preuve que le consentement est contextuel |
| S4 | **Carte du Feed avec photo** (ma carte et celle d'un proche, « Photo de {Prénom} · heure ») | PR #83 (`06-cartes-et-miffs`) | Fiche | Fil avec bulle verte et photo, ton rassurant |
| S5 | **Photo en plein écran au toucher depuis le Feed** (D26), avec « Voir où c'était » et « Position d'il y a 4 min » | à dessiner (FEED-4) | Fiche + revue | **Capture à refaire après FEED-4** ; aucune adresse réelle |
| S6 | **Carte du système sur le lieu de la photo** (épingle, sans nom de lieu) | FEED-4 | Revue (justification du lieu) | Lieu fictif (position simulée). **Chaque photo ayant un lieu (P7), cette capture est toujours possible** |
| S7 | **Message « Partage ta position pour voir cette photo »** | FEED-4 | **Revue seulement** (§4) | Vaut maintenant pour **toute** photo : évite le faux diagnostic « app cassée » |
| S8 | **Texte de consentement amendé** (« Ce que tu partages avec cette Miff », ligne « L'endroit de ta photo ») | LOC-2a / LOC-4a puis UPDATE-1b | Revue Apple et Google (consentement explicite avant la collecte, 5.1.1(ii)) | **Remplace** l'ancienne capture « signalement / masquage », qui n'existe plus (P1, P2) |
| S9 | **Réglages : arrêter le partage** (efface photo, statut, position) | existant | Revue | Déjà prévu pour la localisation |
| V1 | **Vidéo Apple** (≤ 60 s) : ouvrir l'Update, boîte caméra, photo, aperçu, publier, voir la photo chez le second compte (plein écran, « Voir où c'était »), arrêter le partage | iPhone 11 Pro | Informations de revue Apple | **Ne montre ni signalement ni blocage** (inexistants). La **vidéo Google** de localisation en arrière-plan (30 s) **ne montre pas l'Update** : une seule fonctionnalité (`google-play-localisation-arriere-plan.md` §3) |

**Déjà bloquant pour les captures** : les maquettes des écrans de l'Update ne sont pas toutes dessinées, FEED-4 et UPDATE-1b n'existent pas, et le ticket n'est pas aligné sur « photo toujours localisée » (§1.2 bis).

---

## 11. Risques de rejet et parades

Probabilité : mon estimation qualitative (faible, moyenne, élevée), **pas une mesure** ; je ne peux pas prédire l'avis d'un relecteur.

| # | Risque de rejet | Règle | Prob. | Parade |
|---|---|---|---|---|
| **R1** | Pas de **filtrage automatique** des images publiées | Apple 1.2 (1er point) | Moyenne | **Assumé (P4, J35).** Argumentaire structurel (§7.4), note de revue §7.5, retrait à la main sur signalement ; plan de repli §11 bis ; **ne jamais écrire « modéré »** |
| **R2** | **Signalement** absent de l'app (e-mail seulement) | Apple 1.2 (2e point) ; Google UGC « in-app system » | Moyenne à élevée | **Assumé (P1, J34).** Parade : plan de repli §11 bis (bouton de signalement qui ouvre un e-mail pré-rempli) à ne livrer qu'en cas de rejet |
| **R3** | **Blocage** absent | Apple 1.2 (3e point) ; Google UGC | Moyenne à élevée | **Assumé (P2, J34).** Quitter la Miff et retrait par le créateur existent ; masquage local en repli (§11 bis) ; dire honnêtement la limite (pas de blocage « du service » sans suspension manuelle, CGU art. 12) |
| **R4** | **Délai de réponse** (« timely responses ») : « au moins une fois par semaine » | Apple 1.2 ; engagement dans les CGU | Moyenne | **Assumé (P3).** Aucun délai plus court n'est promis ; ne rien écrire qui ne soit tenu |
| **R5** | **Permission caméra incohérente** (manifeste, Info.plist, CI, tests, Data safety, étiquettes qui disent « pas de caméra ») | Apple 5.1.1 ; Google Data safety | Élevée si on oublie, faible avec la liste V10 à V19 | Mettre à jour tous les contrôles **dans la même PR** que le code ; relire les déclarations avant chaque soumission |
| **R6** | **Texte d'usage incomplet** (la caméra, ou le lieu de la photo dans les textes de localisation) | Apple 5.1.1(ii) « completely describe » | Moyenne | Textes du §3.2 |
| **R7** | **Politique ou CGU inexactes** (texte de 140 caractères, suppression par « un membre », « dernière position seulement », **photo « parfois sans lieu »**) | Apple 5.1.1(i) ; Google Data safety, politique de confidentialité | Moyenne | Textes alignés le 2026-11-06 (§9) ; **reste** : aligner le ticket Update et R-PHOTO-LOC sur P7 (§1.2 bis), confirmer L5 et L15 (§9.1) ; **ne pas déployer le site avant** |
| **R8** | **Consentement qui ne cite pas la photo localisée** | Apple 5.1.1(ii), 5.1.5 ; RGPD | Faible à moyenne | Texte amendé prêt (P5, §9.3) ; **reste** : vérification des consoles par le PM, et chaînes Android / iOS à modifier dans UPDATE-1b **avant** qu'une photo localisée ne soit envoyée |
| **R9** | **Mineurs sans contrôle d'âge**, photos et lieu de mineurs visibles d'un adulte entré avec le code | Apple 5.1.4(b) ; Google public cible ; RGPD art. 8 | Moyenne | **Risque assumé par le PM** (J2, J31, R3 du ticket) ; réponses identiques et honnêtes aux questionnaires ; fiche et captures sans univers enfantin ; **non** destinée aux enfants ; jamais « relu par un juriste » |
| **R10** | **Politique « stalkerware » de Google** renforcée par la photo + le lieu (« où tu étais à la photo ») | Google « Device and Network Abuse » (lu le 2026-11-04) | Moyenne | **Risque assumé par le PM le 2026-11-04** (O8) ; rien d'autre n'est caché : caméra visible, réciprocité, aucun mode discret ; fiche sans vocabulaire de surveillance |
| **R11** | **Permission demandée « sans raison »** | Apple 5.1.1(iii) | Faible | Demandée à l'ouverture de l'Update seulement, après l'explication ; jamais au démarrage ; caméra active seulement écran visible ; jamais à distance |
| **R12** | **Pas d'alternative si la caméra est refusée** (iOS) | Apple 5.1.1(iv) | Faible à moyenne | P8 : `ios-dev` vérifie le sélecteur système (V19) ; aucun texte ne promet un repli ; le reste de l'app marche sans caméra |
| **R13** | **Métadonnées (EXIF, GPS) envoyées par erreur** | Promesse de la politique ; Apple 5.1.1 | Faible si testé | Test d'octets sur dix photos (V3) ; **n'écrire L5 dans la politique qu'après** |
| **R14** | **Le relecteur ne voit pas la photo** (compte sans position publiée, un seul compte, pas de Miff) | Revue impossible → rejet « 2.1 » ou équivalent | Moyenne | Compte de démonstration à **deux comptes** dans une Miff, positions simulées, vidéo, note de revue §4 |
| **R15** | **Fiche inexacte** (« la photo disparaît », « sécurité garantie », « temps réel ») | Exactitude des métadonnées | Faible | `aso-marketing` : « visible 24 h par ton cercle » ; jamais « effacée partout » (capture d'écran possible) ; cohérence avec les CGU |
| **R16** | **Suppression de compte** qui laisserait la photo (toutes les Miffs) | Apple 5.1.1(v) ; Google | Faible | Vérifier que le lot de suppression couvre `photos` et `positions` de **chaque** Miff (tickets suppression de compte, déjà prévus) ; à ajouter à la recette |
| **R17** | **Classification d'âge ou public cible** mal remplis | Google « Misrepresentation ... may result in removal » ; Apple | Moyenne | §6 et §8 : réponses honnêtes, mêmes partout, refaites si l'app change |
| **R18** | **Aucun testeur externe n'a-t-il accepté v3 ?** non vérifié | D20 | — | **À vérifier par le PM** dans Play Console et TestFlight (P5). Si quelqu'un l'a accepté : plan v4 (`consentement-localisation-v3.md` §1 bis) |

### 11 bis. Plan de repli en cas de rejet (rien de tout cela n'est développé ni promis)

Principe : on ne livre **rien** de plus tant qu'un store n'a pas refusé (décisions P1, P2, P4 du PM). Si un rejet cite la guideline 1.2 d'Apple ou la politique UGC de Google, on remonte cet escalier, **du moins coûteux au plus coûteux** :

| Étape | Ce qu'on fait | Coût / effet | Limite |
|---|---|---|---|
| 1 | **Répondre au relecteur** (Resolution Center d'Apple, appel Google) avec l'argumentaire (§7.4 : prise de vue en direct, cercle fermé par code, 24 h, aucune découverte publique, retrait et suspension possibles, e-mail publié) et la note de revue §7.5 | Aucun code | Peut suffire pour Apple ; moins probable pour Google (« in-app system ») |
| 2 | **Bouton « Signaler cette photo »** sur la photo plein écran et sur la carte du membre, qui **ouvre un e-mail pré-rempli** (l'application d'e-mail de la personne) vers l'adresse de contact, avec l'identifiant de la Miff, celui du membre et l'heure ; **jamais la photo** | Petit développement par plateforme (S) ; **zéro règle Firestore, zéro donnée stockée par Miff, zéro coût** ; la politique et les étiquettes ne changent pas (l'e-mail est envoyé par la personne, pas collecté par Miff) | Google peut juger l'e-mail insuffisant comme « système dans l'app » |
| 3 | **Masquage local** « Ne plus voir les photos de {Prénom} » : réglage stocké **sur le téléphone**, sans backend | Développement (S à M) ; aucune donnée collectée | Ne bloque pas « du service » : le seul blocage réel reste la suspension à la main (CGU art. 12) |
| 4 | **Signalement enregistré dans Firestore** (collection `reports`, règle à créer par `backend-data`, lecture dans la console) | Plus lourd ; **nouvelle donnée** à déclarer (Data safety, App Privacy, politique art. 3 et 7) ; décision du PM | Reste un système manuel de traitement |
| 5 | **Dernier recours** : soumettre **sans l'Update** (le bouton central est masqué dans les builds de diffusion tant qu'elle n'est pas livrée, D14) et la ré-ajouter plus tard | Aucun développement ; retarde la fonction | Perd la photo localisée en v1 |

**Textes à ajuster si on franchit les étapes 2 à 4** : CGU art. 8 et politique art. 8 (ajouter le bouton ; l'e-mail reste le canal), note de revue §7.5 (retirer la phrase « no in-app report or block button »), captures (§10). **Ce qu'on ne fera jamais** : prétendre à une modération, promettre un délai de 72 h, ou décrire une fonction non livrée.

---

## 12. Contrôles dans le code avant publication (à ajouter au tableau V1 à V9 du `README.md`)

| # | Affirmation à vérifier | Qui |
|---|---|---|
| V10 | Le manifeste Android déclare `CAMERA` et **aucune** permission de stockage, de photothèque ni de micro ; `uses-feature` caméra (et frontale) à `required="false"` ; `PermissionPrivacyTest` et la CI mis à jour | `android-dev` |
| V11 | iOS : `NSCameraUsageDescription` présent en FR et EN avec le texte du §3.2 ; **aucune** clé `NSPhotoLibrary*` ni `NSMicrophone*` ; `check-binaries.sh` et `PrivacyInfo.xcprivacy` mis à jour | `ios-dev` |
| V12 | La photo publiée ne contient **aucune** métadonnée (test d'octets sur dix photos types) et pèse ≤ 150 000 octets | devs |
| V13 | La caméra ne démarre que si l'écran Update est visible et s'arrête en arrière-plan ; aucun déclenchement à distance | devs |
| V14 | Aucune photo n'est écrite dans la galerie ni sur disque avec le viseur intégré ; le repli système utilise un fichier du cache privé supprimé aussitôt | devs |
| V15 | Le lieu de la photo (`lat`, `lng`, `measuredAt`, **toujours ensemble**) est écrit sur **toute** photo publiée (P7) ; si la mesure est impossible ou trop ancienne, comportement décidé à l'alignement du ticket (§1.2 bis) ; aucune coordonnée dans un journal, Analytics, Crashlytics ou l'état sauvegardé | devs + `backend-data` |
| V16 | `updateText` vaut toujours `null` ; aucun champ de texte n'est livré avec la photo (sinon rétablir les lignes « Autres messages » / « Other User Content » des déclarations) | devs |
| V17 | **Aucun** bouton ni écran de signalement ou de blocage n'est livré (P1, P2) ; aucun texte d'écran, capture ni fiche n'en suggère un ; si le plan de repli du §11 bis est déclenché, vérifier alors le fonctionnement hors ligne sans plantage | devs |
| V18 | La suppression de compte efface `photos` (avec lieu) **et** `positions` dans toutes les Miffs, y compris celles créées | devs + `backend-data` |
| V19 | Sur iPhone, comportement exact du repli « appareil photo du système » quand la caméra est refusée (§3.4, P8) ; ajuster les états C et D du ticket si le repli n'existe pas | `ios-dev` |
| V20 | Les textes d'écran (explication de la caméra, ligne de visibilité de l'aperçu, consentement amendé, `NSLocation*UsageDescription`) citent tous l'endroit de la photo, dans les mêmes termes que la politique et les CGU (§9.3) | devs + `release-manager` |
| V21 | Un membre qui ne partage pas sa position voit « Partage ta position pour voir cette photo » pour toute photo ; un compte sans consentement suffisant ne peut pas publier de photo (recette) | devs |

---

## 13. À faire par le PM, à la main (aucun agent ne le fait)

| # | Action | Quand |
|---|---|---|
| 1 | **Vérifier dans Play Console (pistes de test) et TestFlight** si une personne extérieure a reçu un build avec le consentement `2026-11-v03` d'origine, et me le dire : si oui, plan v4 ; si non, le texte amendé suffit (P5) | avant UPDATE-1b |
| 2 | **Lire les textes légaux modifiés** (diff de la PR : politique, CGU, consentement) : ils sont rédigés par des agents et publiés **sous ta seule responsabilité**, sans relecture juridique ; confirmer en particulier que tu assumes J34 à J37 | avant la mise en ligne du site |
| 3 | **Valider l'alignement du ticket Update sur P7** (§1.2 bis) : ma recommandation est de **ne pas publier sans lieu** et d'afficher un message calme | avant UPDATE-1b |
| 4 | **Consulter la boîte e-mail de contact au moins une fois par semaine** (délai annoncé, P3) et pouvoir retirer un contenu signalé (console Firebase, ou le créateur de la Miff retire le membre). Plus souvent est possible, jamais écrit | dès la bêta externe |
| 5 | Préparer **deux comptes de démonstration** dans la même Miff (e-mail + mot de passe, sans double authentification), positions publiées (position simulée), pour la revue | à la soumission |
| 6 | Enregistrer la **vidéo Apple** (≤ 60 s) et prendre les **captures** du §10 (sans signalement ni blocage) | quand l'Update (UPDATE-1, UPDATE-1b, FEED-4) est livrée et recettée |
| 7 | Refaire / vérifier les **formulaires** à partir des §5 et §6 : Data safety, App Privacy, classification, public cible (réponses identiques partout) | à la soumission |
| 8 | Après le déploiement de R-PHOTO-LOC (fait le 2026-11-06 puis fichier de `main` redéployé le 2026-11-10 : information du PM, non vérifiable par l'agent ; `docs/dev/deploiement-regles.md`) : ordre **règles d'abord, builds ensuite** | avant UPDATE-1b |
| 9 | Si un store refuse en citant la guideline 1.2 / la politique UGC : **me prévenir**, on suit le plan de repli du §11 bis (étape 1 d'abord) | au rejet |

---

## 13 bis. Changement de posture : « Enregistrer cette photo » d'un proche (FEED-8, décision du PM du 2026-11-08, REMPLACÉE le 2026-11-11)

**Ce qui change (version du 2026-11-11, qui REMPLACE « sans permission / feuille de partage » : la feuille ne proposait pas « Enregistrer l'image »).** Dans le **plein écran** de la photo d'un proche (Feed, **à cet endroit seulement** : ni l'aperçu de la carte, ni l'Update), une icône **« Enregistrer cette photo »** **enregistre la photo DIRECTEMENT dans la galerie**, au toucher. **iPhone** : `PHPhotoLibrary` en autorisation **`.addOnly`** (ajout seul : Miff ne lit **jamais** la galerie), demandée au premier toucher, `PHAssetCreationRequest.forAsset()` avec un JPEG **ré-encodé depuis les pixels** (aucun EXIF, aucun GPS, **aucune donnée de lieu** dans le fichier), **aucune écriture automatique**. **Android** : aucune permission nécessaire (mêmes clés de texte `feed_photo_download*`, non touché). **Nouvelle posture de permission (iOS)** : une demande **« ajout à la galerie »** existe désormais : clé **`NSPhotoLibraryAddUsageDescription`** (FR : « Miff enregistre la photo dans ta galerie uniquement quand tu touches « Enregistrer cette photo ». Miff ne lit jamais tes photos ni ta galerie. » ; EN équivalent), **jamais** `NSPhotoLibraryUsageDescription` (lecture) ; V11 et `ios/ci/check-binaries.sh` exigent désormais EXACTEMENT la clé d'ajout. **App Store Connect / App Privacy : toujours aucune collecte** (l'enregistrement est local à la personne, rien n'est envoyé) ; la fiche n'a rien à déclarer de plus, mais le relecteur Apple verra la demande d'autorisation : la note de revue peut dire « ajout seul, au toucher d'une icône, jamais de lecture ».

**Posture avant / après.**
| Avant | Après |
|---|---|
| La photo d'un proche « n'est jamais enregistrée sur ton téléphone » (en mémoire seulement) | **Elle ne l'est que si tu choisis de l'enregistrer.** Une copie enregistrée est dans la galerie de la personne, **hors de Miff** : « illisible après 24 h » ne vaut plus pour elle ; Miff ne peut ni la retrouver ni l'effacer |
| Une capture d'écran était possible (limite assumée R5, CGU art. 8) | Toujours possible ; la copie est maintenant **un geste proposé par l'app**, ce qui change le sens de « on ne peut pas l'empêcher » en « on l'a rendu facile » |
| Le lieu de la photo ne sortait pas de Miff que par « Voir où c'était » (Plans, coordonnées seules) | **Inchangé** : l'image enregistrée ne contient **pas** le lieu (ni EXIF, ni GPS) ; seul le visuel part |

**Textes à aligner avant toute publication (rien n'est modifié ici : les textes légaux restent tels quels, décision du PM)** :
1. **Politique de confidentialité (FR et EN)** : point 7 du résumé (« visible 24 heures, puis elle n'est plus lisible ») ; ligne « Photo « Donner des nouvelles » » de l'art. 3 (tableau des données) ; **art. 3 « Caméra et photo »** ; art. 4 : puce « **Une capture d'écran est possible** » et art. 5 « Qui voit quoi ? » : « Un membre peut faire une capture d'écran de ce qu'il voit » (ajouter : **ou enregistrer la photo avec le bouton prévu à cet effet**, copie que nous ne pouvons pas effacer) ; **tableau de conservation de l'art. 7** (ligne de la photo : « Illisibles après 24 h » ne vaut pas pour une copie enregistrée par un membre) ; **art. 11 « stockage sur ton téléphone »** (la photo n'est en mémoire que le temps de l'écran, sauf si tu l'enregistres) ; « Ce que Miff ne collecte pas : ta galerie de photos » (reste vrai : Miff ne **lit** pas la galerie ; préciser qu'il **n'y écrit jamais lui-même**).
2. **CGU (FR et EN)** : art. 8 (« Un membre d'une Miff peut enregistrer ce qu'il voit (capture d'écran, par exemple) ») : ajouter l'enregistrement par le bouton ; rappeler l'**usage interdit** d'une photo enregistrée (la diffuser hors du cercle).
3. **Écran d'explication de la caméra et ligne de visibilité de l'aperçu** (ticket Update §7) : « Ta photo est visible 24 h par ton cercle » devient « … et ton cercle peut l'enregistrer ».
4. **Consentement à la localisation v3 / v4** (`consentement-localisation-v3.md`) : vérifier la mention « une copie est possible » (capture d'écran) ; l'image enregistrée ne contient pas le lieu.
5. **§2 (faits) de ce document** : lignes « Limite assumée » et « Prise de vue » (« jamais dans un fichier ni dans la photothèque » vaut pour **la prise de vue de l'auteur**, pas pour le destinataire).
6. **Fiches des stores et pages web** : ne jamais écrire « la photo disparaît », « personne ne peut la garder » (R15) ; `aso-marketing` à prévenir. **App Store Connect / Data safety** : **une permission nouvelle sur iPhone (ajout à la galerie, ajout seul)**, aucune sur Android ; les déclarations « Photos » ne changent pas (collectée : oui ; **pas de nouvelle collecte** : l'enregistrement est local à la personne).
7. **Textes d'usage** (`InfoPlist.xcstrings`, `project.yml`) : **un ajout** : `NSPhotoLibraryAddUsageDescription` (ajout seul, FR + EN, texte ci-dessus), **jamais** `NSPhotoLibraryUsageDescription` ; le commentaire de `project.yml`, `ios/ci/check-binaries.sh` et les tests de configuration l'exigent. Le texte d'usage de la **caméra** (« Rien n'est enregistré dans ta photothèque ») reste exact pour la photo que TU prends ; la note de revue Apple peut préciser la différence.

**Risque ajouté, écrit comme assumé (décision PM, pas de relecture juridique)** : une photo localisée de proche (visage, lieu) peut être copiée **plus facilement** et diffusée hors de Miff ; le mineur est le cas le plus sensible (§8). Parade existante : durée de 24 h côté app, cercle restreint, retrait d'un membre, quitter la Miff. **Nouveau contrôle V22** : le bouton n'existe qu'au plein écran de la photo d'un proche, l'ajout à la galerie est en autorisation `.addOnly` au toucher seulement, l'image enregistrée est ré-encodée sans métadonnée ni lieu, seule la clé `NSPhotoLibraryAddUsageDescription` est déclarée (jamais de lecture) (test `PhotoSaveTests`, `check-binaries.sh`).

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

---

## 15. « Enregistrer la photo » depuis le Feed : changement de posture vie privée (décision du PM du 2026-11-08)

Ajouté le 2026-11-08 par `android-dev` (PR « Android : sélecteur de Miff avec paywall, enregistrer la photo du Feed »). **Aucun texte légal ni déclaration de store n'est modifié ici** : ce paragraphe liste ce qui devient inexact ou à nuancer, pour que `release-manager` et le PM l'alignent avant toute publication. Rien n'est soumis ni publié ; ces textes n'ont pas été relus par un juriste (décision du PM du 2026-10-23, risque assumé).

### 15.1 Ce qui change dans l'app

- Dans le **plein écran de la photo d'update du Feed, et là seulement** (pas l'aperçu de la carte, pas l'Update), un bouton **« Enregistrer la photo »** (décision D52, `docs/tickets/feed.md`). Android : Android 10 et plus, dossier `Pictures/Miff` de la galerie via MediaStore ; Android 9 et moins, sélecteur de documents du système (la personne choisit où). **Aucune permission nouvelle** (manifeste inchangé ; la CI refuse toute permission de stockage ou de photothèque). iOS : même bouton, mêmes noms de clés (`feed_photo_download*`) ; **attention** : sur iPhone, écrire dans la photothèque exige `NSPhotoLibraryAddUsageDescription` (« ajout seulement », aucune lecture) sauf si la feuille de partage ou l'export de fichier du système est utilisé : le choix revient à `ios-dev`, et la ligne 108 de ce document (« à ne PAS ajouter ») est à réviser en conséquence.
- **Jamais à l'insu de la personne** : l'enregistrement n'a lieu qu'au toucher du bouton. Octets de la photo **nettoyés à l'enregistrement** (segments APPn et commentaires retirés, fin coupée, sans ré-encodage ; l'app de l'auteur ne met déjà aucun EXIF, mais une app modifiée le pourrait) ; nom de fichier neutre `Miff-AAAA-MM-JJ-HHMMSS.jpg` (date de l'enregistrement, ni prénom, ni lieu, ni position) ; l'endroit de la photo (latitude, longitude) **n'est pas copié** avec le fichier (le fichier n'a aucune métadonnée).
- **Le changement de posture** : jusqu'ici, une photo reçue ne sortait jamais de l'app (hors capture d'écran) et « illisible après 24 heures » valait pour toute copie faite dans l'app. Désormais **un membre peut garder une copie de la photo d'un proche sur son téléphone, hors de Miff, sans limite de durée** ; Miff ne peut ni la retrouver ni l'effacer. C'est proche de la capture d'écran, déjà possible et déjà écrite dans les textes, mais plus facile, plus nette et plus durable. Risque à ajouter au registre : **assumé par décision du PM du 2026-11-08**, jamais écrit comme « à valider ».

### 15.2 Textes à aligner (NON modifiés par cette PR)

| Où | Passage actuel (FR) | Ce qu'il faut dire à la place ou en plus |
|---|---|---|
| `docs/legal/politique-confidentialite.fr.md` / `.en.md`, art. 3, « Caméra et photo » (ligne 64) | « La photo que tu prends reste dans la mémoire de l'app … elle n'est enregistrée ni dans ta galerie ni ailleurs sur ton téléphone, et Miff n'a pas accès à ta galerie » | Reste vrai pour **ta propre photo avant sa publication** et pour l'accès à la galerie (Miff ne la lit pas). Ajouter : **« Tu peux, si tu le choisis, enregistrer sur ton téléphone une photo reçue d'un proche, avec le bouton « Enregistrer la photo » : seulement si tu le décides. »** |
| même fichier, « Une capture d'écran est possible » (ligne 94) et partage entre membres (ligne 115) | seule la capture d'écran est citée | Ajouter **le bouton « Enregistrer la photo »** : un membre peut garder la photo hors de Miff ; « illisible après 24 heures » vaut pour l'app, **ni pour une capture ni pour une photo enregistrée** |
| même fichier, tableau des durées (ligne 157) et des données sur le téléphone (ligne 169) | photos reçues « tant qu'elles sont lisibles », effacées à la déconnexion | Une **photo enregistrée volontairement** n'est **pas** effacée par Miff (déconnexion, suppression du compte, fin des 24 heures) : elle devient un fichier de la personne |
| même fichier, « stockage local » (ligne 216) | « La photo que tu prépares n'est gardée que dans la mémoire de l'app, jamais dans ta galerie » | Garder pour la photo que **tu** prends ; ajouter la phrase sur l'enregistrement choisi d'une photo reçue |
| `docs/legal/cgu.fr.md` / `.en.md`, art. 8 (ligne 94 : « Un membre … peut enregistrer ce qu'il voit (capture d'écran, par exemple) ») | capture d'écran en exemple | Ajouter **l'enregistrement de la photo depuis l'app**, et rappeler la règle d'usage : ne pas republier, transférer ni montrer hors du cercle une photo reçue ; photo d'un enfant : l'autorité parentale déjà écrite |
| `docs/legal/politique-confidentialite.fr.md`, art. 10 (mineurs) | photo d'un mineur visible de sa Miff, 24 h | Ajouter qu'une copie enregistrée par un membre échappe aux 24 heures |
| Ce document : §2 (ligne 61 : « jamais dans un fichier ni dans la photothèque de l'app » : vrai pour le **viseur**, à nuancer pour le Feed), ligne 108 (clés iOS à ne pas ajouter), ligne 130 (note de revue : « nothing is saved to the device gallery »), ligne 148 (Apple « Fichiers et documents » : aucune écriture dans la galerie), contrôles V10 et V14 (§12) | « nothing is saved to the device gallery » | Remplacer par **« A photo is saved on the device only if the person taps “Save photo” in the full-screen photo of the Feed; nothing is ever saved automatically; no storage permission is requested. »** ; Data safety et App Privacy : la donnée « Photos » reste « collectée » comme avant, aucune nouvelle collecte ; revérifier la ligne « Fichiers et documents » à la soumission |
| `docs/tickets/update.md` (lignes 30, 147, 157, 204, 293, 481 : « jamais écrite dans la galerie », « rien n'est enregistré ») et l'écran d'explication de la caméra | « Rien n'est enregistré sur ton téléphone » (à propos de **ta** photo) | Reste vrai pour la photo qu'on **prend** ; ne pas l'étendre à la photo qu'on **reçoit** |
| `docs/qa/checklists/update-android-recette-pm.md` et l'équivalent iOS | contrôle « aucune écriture dans la galerie » | Limiter ce contrôle à l'écran de prise de vue et à la publication ; la recette du bouton du Feed est dans `docs/qa/checklists/feed-android-recette-pm.md` |
| Phrase courte, pour l'app et les textes | « jamais stockée sur ton téléphone » | **« seulement si tu choisis de l'enregistrer »** |

### 15.3 Contrôles dans le code (ajoutés par cette PR)

`PhotoDownloadRuleTest` (Android) lit les sources : seul `data/media/PhotoSaver.kt` écrit dans les images ou un document ; seul le plein écran du Feed y accède ; l'écriture part d'un toucher (jamais d'un `LaunchedEffect`) ; aucune permission de stockage dans le manifeste ; rien n'est envoyé à un journal, à Analytics ou au presse-papiers ; le nom du fichier ne reçoit qu'un instant et un fuseau. `UpdatePrivacyRuleTest` continue de garantir que **le viseur et la publication** n'écrivent aucun fichier.

---

## 16. « Enregistrer cette photo » et photo Google : modalités décidées par le PM (2026-11-11), alignées par la PR `docs/aligner-decisions-publication`

**Ce § remplace, pour les modalités, la « feuille de partage du système, sans permission » des §13 bis et 15.1** (elle ne proposait pas « Enregistrer l'image » sur iPhone). Il se lit avec `docs/release/alignement-decisions-2026-11-11.md`. Rien n'est soumis, rempli ni publié ; ces textes ne sont **pas** relus par un juriste (risque assumé par le PM).

### 16.1 Ce que fait l'app

Dans le **plein écran de la photo d'un proche** (Feed, **ce seul endroit** : ni l'aperçu de la carte, ni l'Update), l'icône **« Enregistrer cette photo »** (en haut à droite du plein écran sur Android) enregistre la photo **par un geste explicite, jamais automatiquement**.

| Plateforme | Comportement | Permission |
|---|---|---|
| **Android** (PR #156, fusionnée) | Android 10 et plus : la photo est ajoutée au dossier `Pictures/Miff` de la galerie (MediaStore) au toucher. Android 9 et moins : le sélecteur de documents du système (la personne choisit où). Fichier sans métadonnée, nom neutre | **Aucune** (la CI refuse toute permission de stockage ou de photothèque) |
| **iPhone** (PR #158, **brouillon, non fusionnée à la date de rédaction : à relire à la fusion de #158**) | Enregistrement **direct dans la galerie** au toucher, par une autorisation **« ajout seulement »** (`PHPhotoLibrary` `.addOnly`, demandée au premier toucher), JPEG ré-encodé depuis les pixels (ni EXIF ni lieu) | **`NSPhotoLibraryAddUsageDescription`** (nouvelle) ; **jamais** `NSPhotoLibraryUsageDescription` (lecture) |

Jamais « jamais enregistrée » : la formule exacte est **« seulement si tu choisis de l'enregistrer »**. Une copie enregistrée est un fichier du téléphone du membre, **hors de Miff** (les 24 heures et la suppression du compte ne s'y appliquent pas) ; l'image enregistrée ne contient ni EXIF ni lieu.

### 16.2 Liste des permissions à déclarer (à lire avec V10 et V11 du `README.md`)

| Plateforme | Permission ou clé | Statut |
|---|---|---|
| Android | `CAMERA` | Déclarée (Update) |
| Android | Stockage, photothèque, micro | **Aucune** (V10) ; l'enregistrement d'une photo reçue n'en demande pas |
| Android | Localisation (premier plan, arrière-plan) | Voir `google-play-localisation-arriere-plan.md` |
| iOS | `NSCameraUsageDescription` (FR, EN) | Déclarée (Update) |
| iOS | **`NSPhotoLibraryAddUsageDescription` (FR, EN)** | **Nouvelle (2026-11-11), ajout seulement** |
| iOS | `NSPhotoLibraryUsageDescription`, `NSMicrophone*` | **Interdites** (V11) |
| iOS | `NSLocation*UsageDescription` | Voir `app-store-confidentialite-et-revue.md` §4 |

**Texte d'usage iOS** (tel que dans la PR #158, `InfoPlist.xcstrings`) : FR « Miff enregistre la photo dans ta galerie uniquement quand tu touches « Enregistrer cette photo ». Miff ne lit jamais tes photos ni ta galerie. » ; EN "Miff saves the photo to your library only when you tap “Save this photo”. Miff never reads your photos or your library."

### 16.3 Déclarations des stores

- **Google Play, Data safety** : aucune ligne de plus. « Collecté » = transmis hors de l'appareil : un enregistrement local n'en est pas un. « Fichiers et documents » reste **Non**. La ligne « Photos » (photo de profil, photo de mise à jour) est déjà déclarée.
- **App Store, App Privacy** : aucune collecte de plus (l'enregistrement est local, rien n'est envoyé). Le relecteur Apple verra la demande d'ajout à la galerie : la note de revue l'explique (`app-store-confidentialite-et-revue.md` §5).
- **Photo de profil Google** (décision du 2026-11-08) : la ligne « Photos » est déjà déclarée ; **aucune nouvelle déclaration**. Elle est téléchargée **une fois**, en HTTPS, depuis `googleusercontent.com` : **l'adresse IP de l'appareil est exposée à Google** (écrit dans la politique, art. 3, 5 et 6).

**Remplace la phrase de la note de revue du §4 (ligne « Photo and its location »)** : « nothing is saved to the device gallery » devient **« nothing is ever saved to the gallery automatically; a received photo is saved only if the person taps “Save this photo” in its full-screen view (iPhone: add-only photo library permission; Android: no permission) »**.

### 16.4 Textes déjà alignés (PR du 2026-11-11)

Politique de confidentialité FR/EN (résumé point 7 ; art. 3 « Caméra et photo », « Ce que Miff ne collecte pas », ligne « Profil » ; art. 4 ; art. 5 ; art. 6 ; art. 7 ; art. 10 ; art. 11) et CGU FR/EN (art. 4 et 8), détail dans `docs/legal/README.md` §8. **Restent à faire à la main** : écran d'explication de la caméra et ligne de visibilité de l'aperçu (« … et ton cercle peut l'enregistrer »), qui sont du code des applications ; `docs/tickets/update.md`.

### 16.5 Contrôle ajouté

**V22** (voir `README.md`) : le bouton n'existe qu'au plein écran de la photo d'un proche ; l'écriture part d'un toucher (jamais d'un effet automatique) ; Android : aucune permission de stockage ; iOS : seule la clé d'ajout est déclarée, jamais la clé de lecture ; le fichier enregistré n'a ni métadonnée ni lieu.
