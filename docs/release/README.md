# Préparation des soumissions aux stores (App Store, Google Play)

Mis à jour le 2026-11-04 par l'agent `release-manager`, après les décisions du PM du 2026-11-01 (position stockée côté serveur, localisation « Toujours » obligatoire, lieux de confiance). **Tout ce dossier est une préparation : aucun agent ne crée un compte, ne paie, ne remplit un formulaire de store ni ne soumet quoi que ce soit.** Ces textes n'ont **pas été relus par un juriste** (décision du PM, risque R15 assumé) et ne doivent jamais être présentés comme tels.

**Complété le 2026-11-06 pour l'Update (photo « Donner des nouvelles »)** : nouveau document `update-photo-declarations.md`, lignes 12 à 18 du tableau « À faire par le PM », points ouverts O12 à O17, contrôles V10 à V19 et sources du 2026-11-06 ci-dessous. **Mis à jour le 2026-11-06 après les réponses du PM (P1 à P8, voir « Décisions du PM (2026-11-06) » plus bas)** : les textes légaux de `docs/legal/` sont alignés sur la photo localisée (rien n'est publié ni déployé), points ouverts O12 à O17 **décidés**, contrôles V17, V20 et V21 ajustés. Le reste du dossier n'a pas été réécrit : les écarts à reporter dans `google-play-data-safety.md` et `app-store-confidentialite-et-revue.md` (photos, texte de 140 caractères, caméra, textes d'usage de la localisation) sont **listés** dans le nouveau document (§3.2, §5, §6) et seront appliqués quand l'Update est développée. L'Update n'est pas développée : rien de ce qu'elle ajoute n'est « prêt ».

## Rien n'est « prêt à soumettre »

La checklist QA de sortie `docs/qa/release-checklist.md` **n'existe pas encore** (vérifié le 2026-11-04 : `docs/qa/checklists/` ne contient que des recettes par ticket). Tant qu'elle n'existe pas et qu'elle n'est pas passée, aucune des fonctions décrites ici n'est « prête » : **la localisation « Toujours », la position stockée, les lieux et le consentement version 3 ne sont pas encore développés** (aujourd'hui : aucune permission de localisation dans le manifeste Android, aucune clé `NSLocation*` côté iOS).

## Les documents

| Fichier | Contenu |
|---|---|
| `google-play-localisation-arriere-plan.md` | Déclaration Google Play « localisation en arrière-plan » : fonctionnalité unique, divulgation visible dans l'app (FR/EN), vidéo de démonstration à prévoir, justification, politique « stalkerware » |
| `google-play-data-safety.md` | Formulaire « Sécurité des données » (Data safety) : réponses proposées, points à confirmer |
| `app-store-confidentialite-et-revue.md` | Étiquettes de confidentialité Apple (App Privacy, Precise Location...), manifeste de confidentialité, textes d'usage iOS (FR/EN), note de revue Apple pour la guideline 5.1.2(i), plan B |
| `changements-testeurs-externes.md` | Ce qui change pour les testeurs externes (court) |
| `alignement-decisions-2026-11-11.md` | **Alignement du 2026-11-11** : 3 lieux gratuits, « Enregistrer cette photo », photo Google, mode voyage, notifications locales : ce qui a été corrigé, **ce qui reste à faire à la publication** (avec les fichiers) et les points ambigus |
| `update-photo-declarations.md` | **Update (photo « Donner des nouvelles »)** : permission caméra (Android, iOS FR/EN), photo et lieu de la photo (toujours attaché), écarts Data safety et App Privacy, contenu généré par les utilisateurs (Apple 1.2, Google UGC), mineurs, passages légaux adaptés, pages web à mettre à jour, captures, risques de rejet et **plan de repli** (2026-11-06, décisions P1 à P8) |
| `checklist-android.md` | **Soumission Google Play, pas à pas** (2026-11-11) : clé d'envoi et secrets GitHub, workflow `android-release`, Play App Signing, piste interne, 12 testeurs / 14 jours, SHA-1 / SHA-256 dans Firebase et App Check, Data safety, localisation en arrière-plan avec vidéo ; ce qui reste bloqué côté PM |
| `decisions-stores-avant-soumission.md` | **20 décisions et actions du PM avant toute soumission** (2026-11-07) : classification d'âge, catégorie, contenu généré par les utilisateurs, identité publique de l'éditeur, règle des 12 testeurs de Google, déploiement du site, nom « Miff », partage du code, avis, analytics, vidéos, Miff+, ordre iOS / Android. Recoupe les brouillons de `marketing/` avec ce dossier ; règles des stores relues le même jour |
| `../legal/consentement-localisation-v3.md` | Texte de consentement version 3 et règle `AAAA-MM-vNN` |
| `../legal/*.md`, `../../web/` | Politique de confidentialité, CGU, page web (FR et EN), mis à jour dans la même PR |

**`checklist-android.md`** (2026-11-11) : étapes de soumission Google Play pas à pas pour le PM (création de l'app, clé d'envoi, Play App Signing, piste de test interne, règle des 12 testeurs, empreintes dans Firebase, déclarations) et ce qui reste bloqué côté PM ; annexe technique (version, signature, pages de 16 Ko). La checklist `checklist-ios.md` **n'est pas encore écrite**.

## À faire par le PM, à la main (aucun agent ne le fait)

| # | Action | Où | Quand |
|---|---|---|---|
| 1 | **Lire et trancher les points ouverts** (tableau ci-dessous et `docs/legal/consentement-localisation-v3.md` §6) | ici | avant la bêta externe |
| 2 | Vérifier que les adresses fournies dans les textes (contact provisoire) sont toujours celles que tu veux rendre publiques | `web/site.config.json` + `docs/legal/*.md` | avant toute publication |
| 3 | **Google Play Console** : remplir « Contenu de l'application » > « Autorisations sensibles » > « Autorisations de localisation » (une seule fonctionnalité), avec la vidéo | Play Console | à la soumission Android |
| 4 | **Google Play Console** : remplir « Sécurité des données » à partir de `google-play-data-safety.md`, après avoir lu l'aide affichée à côté de chaque question | Play Console | idem |
| 5 | **App Store Connect** : remplir « Confidentialité de l'app » (étiquettes), la note de revue, joindre la vidéo et les identifiants du compte de démonstration | App Store Connect | à la soumission iOS |
| 6 | **Enregistrer une vidéo de démonstration** de 30 secondes au plus sur l'OPPO (Android), et une pour Apple si tu le souhaites (voir les deux documents) | appareil | quand LOC-2 est livré |
| 7 | **Préparer un compte de test** (e-mail, mot de passe, Miff déjà créée) pour les relecteurs Google et Apple, **sans authentification à deux facteurs** | Firebase Auth / app | à la soumission |
| 8 | **Captures d'écran** des stores (écran d'autorisation « Toujours », écran Miff, carte d'un lieu) | appareils | à la soumission |
| 9 | Répondre au questionnaire de **classification d'âge** et de **public cible** (Google : « Public cible et contenu » ; Apple : « Classification d'âge ») **de façon honnête**, en lisant les questions du jour (voir O7 ci-dessous) | stores | à la soumission |
| 10 | Si le déploiement du site est décidé : **actions de console Analytics** de `docs/legal/README.md` §3 (durée 2 mois, désactiver la collecte précise de localisation et d'appareil dans l'UE) | analytics.google.com | **avant** de publier les textes |
| 11 | Dire « oui, déploie » pour publier le site (Firebase Hosting) ; **rien n'est déployé** par cette PR | terminal local | quand les textes sont validés |
| 12 | **Update (photo)** : **fait** (2026-11-06) : P1 à P8 décidés, voir `update-photo-declarations.md` §1.2 ; reste à valider l'écart « photo toujours localisée » (P7) avec le ticket (§1.2 bis) | ici | avant UPDATE-1b |
| 13 | **Update** : vérifier dans Play Console (pistes de test) et TestFlight **qu'aucune personne extérieure n'a reçu un build avec le consentement `2026-11-v03`** (décision D20 : amender v3 ou passer en v4) | Play Console, TestFlight | avant UPDATE-1b |
| 14 | **Update** : **lire les textes légaux modifiés** (diff de la PR `docs/legal-photo-localisee` : politique, CGU, consentement) et confirmer que tu assumes les risques J34 à J37 ; rien n'est publié avant ton « oui, déploie » | `docs/legal/`, `web/` | avant la mise en ligne du site |
| 15 | **Update** : consulter la boîte e-mail de contact **au moins une fois par semaine** (délai annoncé dans les CGU, décision P3 ; plus souvent est libre mais ne s'écrit nulle part) et être capable de retirer un contenu signalé (console Firebase, ou le créateur retire le membre) | boîte e-mail, console | dès la bêta externe |
| 16 | **Update** : compte de démonstration à **deux comptes** dans la même Miff, positions publiées (position simulée), pour que le relecteur voie une photo localisée ; vidéo Apple (≤ 60 s) qui montre l'Update (**sans** signalement ni blocage : ils n'existent pas) | appareils | à la soumission |
| 17 | **Update** : captures de la fiche et de la revue (viseur, aperçu, carte du Feed avec photo, photo en plein écran, carte du lieu, message « Partage ta position pour voir cette photo », texte de consentement amendé) : liste et règles au §10 de `update-photo-declarations.md` ; comptes de test, photos de personnes consentantes, aucun enfant, aucun lieu réel | appareils | quand UPDATE-1, UPDATE-1b et FEED-4 sont recettés |
| 18 | **Update** : reprendre les formulaires **Data safety**, **App Privacy**, **classification** et **public cible** avec les écarts des §5, §6 et §8 de `update-photo-declarations.md` (caméra, photos, lieu de la photo, suppression des lignes « texte de 140 caractères ») | consoles des stores | à la soumission |
| 19 | **Update** : **vérifier dans Play Console et TestFlight** si une personne extérieure a accepté le consentement `2026-11-v03` d'origine (si oui : plan v4, `docs/legal/consentement-localisation-v3.md` §1 bis) | Play Console, TestFlight | avant UPDATE-1b |

Les comptes développeur (Apple 99 €/an, Google Play 25 € une fois) **existent déjà** (créés pour Ghostrun) : rien à payer ni à créer. Un agent ne se connecte jamais à ces comptes.

## Points ouverts pour le PM (non tranchés, jamais devinés)

| # | Point | Pourquoi c'est ouvert |
|---|---|---|
| **O1** à **O6** | Voir `docs/legal/consentement-localisation-v3.md` §6 : refus du re-consentement, phrase « Tu peux le refuser » (R3), re-consentement multi-Miffs, localisation de la photo d'update (L7), fréquence d'écriture (L4), ré-acceptation des CGU | Décisions produit non fournies |
| **O7** | **Public cible et âge** : aucun âge minimum n'est fixé (risque J2 assumé), mais les stores demandent une classification et un public cible. Google Play impose des règles supplémentaires aux apps destinées aux enfants. **Quelle tranche d'âge déclarer ?** Recommandation : ne **pas** déclarer l'app comme destinée aux enfants ; choisir la tranche la plus basse que tu acceptes d'assumer et la déclarer partout de la même façon. | Aucune décision PM ; le risque est assumé, la réponse aux formulaires ne l'est pas encore |
| **O8** | **Google Play, politique « stalkerware » (suivi d'autres personnes)** : le texte de la politique ne reconnaît le suivi d'autres personnes que pour les apps « conçues et commercialisées exclusivement pour les parents » (lu le 2026-11-04), et dit qu'elles « ne peuvent pas servir à suivre quelqu'un d'autre (un conjoint, par exemple) ». Miff vise aussi les amis et les couples, avec un partage **réciproque, consenti et visible** (donc l'inverse d'un suivi caché). **Risque assumé par le PM (décision du 2026-11-04, voir « Décisions du PM » plus bas)**, en plus de R1, R3, R4, R15, R16 et R18 : refus ou retrait possible, écrit comme risque assumé, jamais « à valider » (voir `google-play-localisation-arriere-plan.md` §6). Reste à décider seulement : notification persistante ou non (§6). | **Tranché le 2026-11-04** (assumé) |
| **O9** | **Data safety : les autres membres d'une Miff comptent-ils comme un « partage » ?** La définition lue (« transférer des données à un tiers ») ne dit rien du cas « visible des autres utilisateurs de l'app ». | À lire dans l'aide de la console au moment de remplir ; voir `google-play-data-safety.md` |
| **O10** | **Fournisseurs de cartes et d'adresses** : Android = Photon (Komoot) et OpenFreeMap, iOS = services d'Apple (`MKLocalSearchCompleter`, ticket Lieux §12, **marqué « à vérifier »**). Les politiques de Photon/Komoot ne sont pas lisibles dans les pages consultées. | Les textes disent ce qu'on sait, et rien de plus |
| **O11** | **Nom de l'offre** : l'offre s'appelle « Miff+ » (décision PM du 2026-10-31). **Aligné le 2026-11-04** : CGU, politique de confidentialité et page d'accueil web disent « Miff+ » (la page ne dit plus que l'offre n'est pas définie et ne cite aucun prix) | Fait pour les textes du dépôt ; fiches des stores à écrire avec `aso-marketing` |
| **O12** | **Update : signaler une photo** | **Décidé le 2026-11-06 (P1) : pas de signalement dans l'app** ; e-mail seulement ; risque Apple 1.2 / Google UGC assumé (J34) ; repli : bouton qui ouvre un e-mail pré-rempli (`update-photo-declarations.md` §11 bis) |
| **O13** | **Update : bloquer** | **Décidé le 2026-11-06 (P2) : pas de blocage** ; quitter la Miff et retrait par le créateur existent ; assumé (J34) ; repli : masquage local |
| **O14** | **Update : délai de réponse promis** à un signalement | **Décidé le 2026-11-06 (P3) : « au moins une fois par semaine »**, aucune promesse de 72 h ni plus courte |
| **O15** | **Update : filtrage automatique des images** | **Décidé le 2026-11-06 (P4) : aucun**, assumé (J35) ; ne jamais écrire « modéré » |
| **O16** | **Update : consentement v3 amendé ou v4** | **Décidé le 2026-11-06 (P5) : v3 amendé** (texte prêt) **et plan v4 noté** ; **reste : le PM vérifie les consoles** (action 19) |
| **O17** | **Update : alignement des textes légaux** | **Décidé le 2026-11-06 (P6) et fait** dans la PR `docs/legal-photo-localisee` (rien de publié). **Nouveau, P7** : « photo toujours localisée » ; le ticket Update et la règle R-PHOTO-LOC décrivent encore une photo possible sans lieu : à aligner (`update-photo-declarations.md` §1.2 bis) |

## Contrôles du code avant de publier (affirmations des textes à confirmer par les devs)

Les textes publics affirment les points suivants. **Chacun doit être vérifié dans le code** par `android-dev` / `ios-dev` / `backend-data` avant publication ; sinon le texte devient faux.

| # | Affirmation | Qui vérifie |
|---|---|---|
| V1 | La position n'est jamais envoyée à Analytics, Crashlytics, aux journaux ni à un message de partage | devs (test existant : liste blanche d'événements) |
| V2 | Les requêtes d'adresse (Photon) ne contiennent que le texte tapé : **pas de position de la personne** (pas de paramètre de biais `lat`/`lon`), et la carte n'affiche pas la position de la personne | `android-dev` |
| V3 | Sur iPhone, la carte et les suggestions viennent bien d'Apple (MapKit, `MKLocalSearchCompleter`) ; sinon corriger l'article 6 de la politique | `ios-dev` |
| V4 | La dernière position est effacée dans le **même lot** que le statut (arrêt du partage, sortie, retrait, suppression de la Miff ou du compte) | `backend-data` (règles) + devs |
| V5 | L'app masque une position de plus de 24 h en gardant sa date visible | devs |
| V6 | La copie locale de la dernière position est stockée en privé, exclue des sauvegardes et effacée à la déconnexion | devs |
| V7 | **Mis à jour le 2026-11-12 (SRV-3)** : un jeton de notification n'est enregistré **qu'après** l'explication affichée dans l'app **et** l'autorisation du téléphone ; il est supprimé (document `devices` **et** `deleteToken` chez FCM) à la déconnexion, à la suppression du compte, à la coupure des notifications, au retrait de la permission et au remplacement par un autre téléphone ; aucune position, aucun nom ni identifiant publicitaire dans le document ; jamais journalisé ni envoyé à Analytics (`docs/dev/jetons-appareil-clients.md` §8) | devs |
| V8 | Le document de session ne contient qu'un identifiant aléatoire d'installation, la plateforme et l'heure | `backend-data` |
| V9 | Le lieu enregistré est visible des membres avec son adresse ; le créateur seul crée, modifie, supprime | règles + devs |
| V10 | **Update** : le manifeste Android déclare `CAMERA` et aucune permission de stockage, de photothèque ni de micro ; `uses-feature` caméra à `required="false"` ; `PermissionPrivacyTest` et la CI mis à jour | `android-dev` |
| V11 | **Update** : iOS, `NSCameraUsageDescription` en FR et EN, **EXACTEMENT** la clé `NSPhotoLibraryAddUsageDescription` (ajout seul, FEED-8, décision du 2026-11-11), jamais `NSPhotoLibraryUsageDescription` ni `NSMicrophone*` ; `check-binaries.sh` et `PrivacyInfo.xcprivacy` mis à jour | `ios-dev` |
| V12 | **Update** : la photo publiée ne contient aucune métadonnée (test d'octets, dix photos) et pèse au plus 150 000 octets | devs |
| V13 | **Update** : la caméra ne démarre que si l'écran Update est visible, s'arrête en arrière-plan, ne peut pas être déclenchée à distance | devs |
| V14 | **Update** : aucune photo dans la galerie ni sur disque avec le viseur intégré ; le repli système utilise un fichier du cache privé supprimé aussitôt | devs |
| V15 | **Update** : le lieu de la photo n'est écrit que si partage actif, consentement suffisant, position publiée et mesure récente ; aucune coordonnée dans un journal, Analytics, Crashlytics ni l'état sauvegardé | devs + `backend-data` |
| V16 | **Update** : `updateText` vaut toujours `null` (sinon rétablir les lignes « texte » des déclarations) | devs |
| V17 | **Update** : **aucun** bouton ni écran de signalement ou de blocage n'est livré (P1, P2) ; aucun texte d'écran, capture ni fiche n'en suggère un | devs |
| V18 | **Update** : la suppression de compte efface `photos` (lieu compris) et `positions` dans toutes les Miffs, y compris celles créées | devs + `backend-data` |
| V19 | **Update** : iOS, comportement réel du repli « appareil photo du système » quand la caméra est refusée (P8 : aucun repli promis dans les textes) | `ios-dev` |
| V20 | **Update** : tous les textes d'écran (explication de la caméra, aperçu, consentement amendé, `NSLocation*UsageDescription`) citent l'endroit de la photo dans les mêmes termes que la politique et les CGU | devs + `release-manager` |
| V21 | **Update** : un membre qui ne partage pas sa position voit « Partage ta position pour voir cette photo » pour toute photo ; un compte sans consentement suffisant ne peut pas publier | devs |
| V22 | **« Enregistrer cette photo »** (décision du 2026-11-11) : l'icône n'existe qu'au plein écran de la photo d'un proche ; l'écriture part d'un toucher, jamais d'un effet automatique ; Android : aucune permission de stockage ni de photothèque ; iOS : seule `NSPhotoLibraryAddUsageDescription` (ajout) est déclarée, jamais la clé de lecture ; le fichier enregistré n'a ni métadonnée ni lieu (`update-photo-declarations.md` §16). **V11 est à lire avec cette exception** | devs |
| V23 | **Mode voyage** : la dernière position et l'endroit des photos continuent d'être écrits et lus pendant un voyage ; aucun texte (app, fiche, politique) ne dit que le voyage arrête ou suspend la position | devs + `release-manager` |
| V24 | **Photo Google** : téléchargée une seule fois, en HTTPS, depuis `googleusercontent.com`, redimensionnée et enregistrée sans métadonnée ; aucune copie conservée après l'enregistrement du profil (le brouillon local est effacé) | devs |

## Sources officielles consultées le 2026-11-04

Ces règles changent : `release-manager` les relit **avant chaque soumission**.

- Google Play, localisation en arrière-plan (critères, divulgation, formulaire, vidéo) : https://support.google.com/googleplay/android-developer/answer/9799150
- Google Play, Sécurité des données (définitions « collecté », « partagé », localisation précise 3 km², obligatoire/facultatif) : https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play, politique « Device and Network Abuse » (section stalkerware, en vigueur depuis le 1er octobre 2020) : https://support.google.com/googleplay/android-developer/answer/10065487
- Apple, App Review Guidelines (5.1.1, 5.1.2, 5.1.4, 5.1.5, 2.5.4) : https://developer.apple.com/app-store/review/guidelines/
- Apple, détails de confidentialité de l'app (Precise Location = latitude/longitude à 3 décimales ou plus) : https://developer.apple.com/app-store/app-privacy-details/
- Firebase, déclarations Apple et Google (sans tableau complet pour Analytics, à compléter au moment du remplissage) : https://firebase.google.com/docs/ios/app-store-data-collection et https://firebase.google.com/docs/android/play-data-disclosure
- OpenFreeMap, politique de confidentialité : https://openfreemap.org/privacy/ ; Photon : https://photon.komoot.io (aucune politique de confidentialité lisible sur la page)

### Sources ajoutées le 2026-11-06 (Update)

Liste détaillée, avec ce qui a été lu dans le texte et ce qui ne l'a pas été, au §14 de `update-photo-declarations.md`. Principales : Apple Guidelines 1.2 et 5.1.4 (https://developer.apple.com/app-store/review/guidelines/), politique « User Generated Content » de Google Play (https://support.google.com/googleplay/android-developer/answer/9876937 et https://support.google.com/googleplay/android-developer/answer/12923286), documentation Android `<uses-feature>` (https://developer.android.com/guide/topics/manifest/uses-feature-element).

## Décisions du PM (2026-11-04)
- **Risque Google « stalkerware » assumé** par le PM (politique lue le 2026-11-04 : le suivi d'autres personnes n'est admis que pour les apps « exclusivement pour les parents » et ne peut pas servir à suivre « un conjoint », même avec son accord ; Miff vise aussi amis et couples). Le partage reste réciproque, consenti et visible ; aucune notification persistante n'est prévue. Risque de refus ou de retrait de l'app assumé, écrit comme tel, jamais « à valider » ; aucun texte ne prétend à une relecture juridique.
- **Consentement v3 : option A** (phrase honnête, sans « Tu peux le refuser »).
- **Refus du re-consentement v3** : rien n'est écrit tant que le nouveau consentement n'est pas accepté ; l'utilisateur peut quitter la Miff ou supprimer son compte.

## Décisions du PM (2026-11-06) : l'Update (photo « Donner des nouvelles »)
- **P1** pas de signalement dans l'app ; **P2** pas de blocage ; **P3** « au moins une fois par semaine », aucune promesse de 72 h ; **P4** aucun filtrage automatique, jamais « modéré » ; **P5** consentement v3 amendé pour citer la photo localisée, plan v4 noté, vérification des consoles par le PM ; **P6** textes légaux alignés maintenant ; **P7** une photo est **toujours** attachée à une position, avec l'heure de la mesure ; **P8** repli iOS à vérifier par `ios-dev`, rien d'autre promis.
- Conséquences écrites : `update-photo-declarations.md` (§1.2, §1.2 bis, §9, §10, §11, §11 bis, §13), `docs/legal/` (politique, CGU, consentement, README, risques J34 à J37). Risques de rejet Apple 1.2 / Google UGC **assumés**, jamais écrits « à valider », jamais présentés comme relus par un juriste.
