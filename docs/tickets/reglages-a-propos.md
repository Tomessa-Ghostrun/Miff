# Mini-ticket : Réglages > « À propos et aide » (version, aide, contact, licences, avis)

**Statut** : **décidé par le PM le 2026-11-13** (toutes les valeurs par défaut de §10 validées, voir « Décisions du PM, 2026-11-13 » ci-dessous). **ABOUT-1A (Android) en développement** : branche `android/reglages-a-propos`, PR brouillon, recette `docs/qa/checklists/reglages-a-propos-android-recette-pm.md`. ABOUT-1I (iOS) : à lancer après Android. Aucun statut Notion modifié par cette PR.
**Rédigé le** : 2026-11-11 (agent `product-specs`). **Ticket Notion** : à créer par le PM ou sur son accord (non créé).
**Demande** : le PM veut que « Nous contacter » ne soit plus caché dans Confidentialité, qu'une version soit visible, qu'un écran de licences et d'attributions existe, et qu'une entrée « Donner mon avis » apparaisse dès que l'adresse du formulaire externe existe.

**Sources lues** : `CLAUDE.md` ; `docs/dev/lieux-fournisseurs-gratuits.md` (tableau des licences et attributions, §« Licences et attributions ») ; `docs/tickets/pop-ins-avis.md` (§8, formulaire externe, `FEEDBACK_FORM_URL`) ; `docs/tickets/feed-profil-reglages.md` (§4C, RG6 / B5) ; `docs/tickets/onboarding.md` (RG6) ; `android/README.md` (inventaire des dépendances) ; `ios/README.md` (dépendances) ; le code des Réglages Android (`SettingsScreens.kt`, `PrivacyScreen.kt`, `ExternalLinks.kt`) et iOS (`SettingsScreens.swift`, `BuildInfo.swift`).
**Limites de cette lecture (à connaître)** :
- Je n'ai pas d'outil shell dans cette session : **je n'ai pas pu faire `git fetch`**. J'ai lu l'exemplaire du dépôt d'un autre worktree (`C:\dev\miff-ios-notifs-c`), **peut-être en retard sur `origin/main`**. Tout ce que j'écris sur « l'existant » est **à revérifier sur `origin/main`** avant le développement.
- Les conditions des services et des bibliothèques ne sont pas relues ici : je reprends ce qui est déjà consigné dans `lieux-fournisseurs-gratuits.md` et les README. **Pas de relecture juridique** (décision du PM du 2026-10-23, risque assumé) : l'écran des licences n'affirme jamais « conforme » ni « relu ».

## Décisions du PM, 2026-11-13

Le PM a validé **toutes les valeurs par défaut de §10** et complété la question du formulaire. Ces décisions **remplacent** les « [Proposé] » ci-dessous quand elles diffèrent.

| # | Décision |
|---|---|
| **Q1** | Une seule ligne **« À propos et aide »**, **en dernière position** de Réglages, **aussi présente** dans les Réglages restreints (compte sans Miff). |
| **Q2** | « Nous contacter » est **DÉPLACÉ** hors de Confidentialité (plus de doublon). RG6 (`onboarding.md`), B5 (`feed-profil-reglages.md`) et les checklists `onb-3b-android-recette-pm.md` et `onb-7-ios-recette-pm.md` sont **mis à jour** (fait dans la PR Android). Les liens Conditions et Politique **restent** dans Confidentialité. |
| **Q3** | **Formulaire** : l'adresse est **fournie** (`https://forms.gle/5EEH4sEfsnCCLzEx7`, Google Forms, publique, ce n'est pas un secret ; configurée dans `BuildConfig.FEEDBACK_FORM_URL`, propriété `miff.feedbackFormUrl`, PR `android/feedback-form-url`). La ligne **« Un retour sur l'app ? »** (libellé exact voulu par le PM) est donc **AFFICHÉE**, **y compris dans un build de diffusion** (elle n'est plus « masquée tant que vide » : elle ne l'est que si l'adresse est vide ou invalide). Elle ouvre le formulaire **dans le navigateur**, l'adresse **telle quelle** : **aucune donnée personnelle ni identifiant** n'y est ajouté. |
| **Q4** | **Version** seulement dans cet écran (iOS perd son pied de liste), avec **copie** au toucher. |
| **Q5** | **Licences** : **texte complet** au toucher, **hors connexion**. |
| **Q6** | Les aides existantes sont **liées depuis cet écran**, en plus de leur place actuelle (mêmes écrans, aucun contenu copié). |
| **Q7** | **Aucune** info technique de plus (modèle de téléphone, identifiants) dans la version copiée. |
| **Q8** | **Pas de maquette** : gabarit des écrans de Réglages, écarts listés dans la PR. |

**Écarts constatés à l'implémentation Android (2026-11-13)** :
- **Libellé de la ligne du formulaire** : « Un retour sur l'app ? » (décision du PM) remplace « Donner mon avis » partout dans ce document. Son sous-titre reste « Dis-nous ce qui te plaît et ce qui pourrait être mieux. » (aucune promesse de réponse, ni store, ni note, ni étoiles : AP10).
- **« Si les rappels n'arrivent pas »** ouvre **le même écran** que le lien du même nom de Réglages > Notifications, c'est-à-dire l'aide « Si les détections tardent » (`DetectionHelpRoute`) : la lecture de §3.3 (« Notifications coupées ») était fausse, le critère **AP5** (« mêmes écrans ») fait foi. Les deux lignes mènent donc à la même aide aujourd'hui ; la ligne « rappels » n'est **pas** dans les Réglages restreints (comme Réglages > Notifications : rien à notifier sans Miff).
- **Licences** : la liste est regroupée **par bibliothèque principale** (une ligne par famille, ses composants listés dans le détail) pour rester lisible ; la garde de CI (AP15) vérifie que **chaque composant** livré dans le build de diffusion est couvert par une ligne.

**Convention** : **[Décidé]** = décision du PM déjà écrite. **[Proposé]** = ma proposition. **[À vérifier]** = fait de l'existant à reconfirmer sur `origin/main`.

---

## 1. Résumé pour le PM

1. **Une seule nouvelle ligne dans Réglages : « À propos et aide »**, qui ouvre **un seul écran**, court, avec quatre blocs : **version**, **besoin d'un coup de main** (aide + contact + avis), **nos textes**, **licences**. Le nombre de touches pour « Nous contacter » ne change pas (3 depuis l'onglet Profil : engrenage, ligne, contact) ; ce qui change, c'est qu'on **trouve** le contact là où on le cherche (une ligne « aide »), au lieu de le chercher dans « Confidentialité ».
2. **L'existant, tel que je l'ai lu [À vérifier]** : « Nous contacter » est dans Confidentialité, section « Nos textes », sur Android et iOS. **iOS affiche déjà** « Version X (build Y) » en bas de la liste des Réglages ; **Android n'affiche aucune version**. Aucun écran de licences. `FEEDBACK_FORM_URL` (Android) **est renseignée depuis le 2026-11-13** (`https://forms.gle/5EEH4sEfsnCCLzEx7`, Google Forms, adresse fournie par le PM ; PR `android/feedback-form-url`), et **iOS n'a pas encore d'équivalent**.
3. **« Un retour sur l'app ? » (ex- « Donner mon avis »)** : décision du PM du 2026-11-13, la ligne est **affichée** (l'adresse existe). Elle reste **masquée si l'adresse devenait vide ou invalide** : aucune ligne morte, aucun écran « bientôt ». Un seul endroit par plateforme pour l’adresse.
4. **Licences** : une liste lisible (nom, licence), le texte complet au toucher, **hors connexion**, et les **attributions de cartes et d'adresses** (OpenStreetMap, OpenFreeMap, Photon) que l'on avait promis d'écrire « quand l'écran existera ». Les attributions **sous la carte et sous la liste d'adresses restent en place** : l'écran les complète, il ne les remplace pas.
5. **Coût : 0 €.** Aucune lecture ni écriture Firestore, aucune permission, aucun événement Analytics, aucun appel réseau à l'ouverture de l'écran (seuls les liens externes touchés sortent de l'app).
6. **Android d'abord, puis iOS** (une branche, une PR par plateforme). Taille : **M** par plateforme (la liste de licences est le gros morceau).

---

## 2. Contexte et principes

- **Ton** : tutoiement, bienveillant, aucune promesse que l'équipe ne peut pas tenir (pas de « on te répond sous 24 h »). L'écran est **calme** : peu de lignes, pas de jargon.
- **Confiance** : l'écran dit clairement quelles données partent où quand c'est le cas (formulaire externe : un site tiers ; cartes et adresses : services tiers) et renvoie vers la Politique de confidentialité.
- **Contact [Décidé 2026-10-24]** : le contact est le **contact provisoire** de `CLAUDE.md` (adresse e-mail du support, via la configuration de build existante), **jamais écrit en dur dans un texte traduit**. Son adresse devient publique avec la publication (risque assumé).
- **Parité** : mêmes lignes, mêmes textes, mêmes clés `about_` sur Android et iOS ; les différences de plateforme sont listées en §6.

---

## 3. Parcours et contenu de l'écran

### 3.1 Où l'on arrive

- **Réglages** reçoit une ligne **« À propos et aide »** (sous-titre « Version, aide, licences et contact »), **en dernière position** de la liste. **[Proposé]**
- Elle est **aussi présente dans les Réglages restreints** (compte sans Miff : décision du PM du 2026-11-07, Mon compte et Confidentialité) : c'est le moment où l'on a le plus besoin d'aide. **[Proposé]**
- Parcours le plus court vers l'aide : **onglet Profil, engrenage, « À propos et aide »**.

### 3.2 Wireframe

```
┌────────────────────────────────────┐
│ ←   À propos et aide               │
├────────────────────────────────────┤
│ Miff                               │
│ Version 0.1.0 (build 12)    [copier]│   toucher = copie « Miff 0.1.0 (12) »
│                                    │
│ Besoin d'un coup de main ?         │
│ ▸ Si les détections tardent        │   (écran d'aide existant)
│ ▸ Si les rappels n'arrivent pas    │   (Android ; écran d'aide existant)
│ ▸ Nous contacter                   │   (e-mail)
│ ▸ Donner mon avis                  │   (masquée tant que l'adresse est vide)
│                                    │
│ Nos textes                         │
│ ▸ Conditions d'utilisation         │   (page web, langue de l'app)
│ ▸ Politique de confidentialité     │
│                                    │
│ ▸ Licences et attributions         │
└────────────────────────────────────┘
```

### 3.3 Détail des lignes

| Ligne | Comportement | Remarques |
|---|---|---|
| **Version** | Affiche « Version {nom} (build {numéro}) » ; **toucher = copie** « Miff {nom} ({numéro}) » dans le presse-papiers, confirmation brève « Copié. » (sous la ligne ou toast du système) | Utile pour un testeur qui signale un souci. **Aucune autre donnée** n'est copiée (ni modèle de téléphone, ni identifiant, ni compte). |
| **Si les détections tardent** | Ouvre l'écran d'aide **déjà existant** (`settings_detection_help_title`) | Même destination que le lien de Confidentialité (le lien d'origine **reste**). Sur iOS : l'aide propre à iOS. |
| **Si les rappels n'arrivent pas** | Ouvre l'aide « Notifications coupées » existante | **Android seulement** [À vérifier : ligne `notif_settings_help_link`] ; **absente sur iOS** tant qu'iOS n'a pas d'équivalent. |
| **Nous contacter** | Ouvre l'application e-mail avec l'adresse du contact provisoire, **sans texte pré-rempli** | **Remplace** le lien de Confidentialité (§9 Q2). Si aucune application e-mail ne peut s'ouvrir : un court message calme **affiche l'adresse** avec un bouton « Copier » (et **ne laisse pas la ligne sans réaction**). |
| **Donner mon avis** | Ouvre le **formulaire externe** dans le navigateur, adresse ouverte **telle quelle** | **Masquée** quand l'adresse est vide. Voir §4. |
| **Conditions d'utilisation / Politique de confidentialité** | Ouvre la page web de la **langue de l'app** (comme dans Confidentialité) | Présentes aussi dans Confidentialité (les liens y **restent**). |
| **Licences et attributions** | Ouvre l'écran des licences (§5) | — |

**Ce qui n'est PAS dans l'écran (volontairement)** : pas de FAQ, pas de « Comment ça marche », pas de notes de version, pas de « Évaluer Miff sur le store » (c'est la fenêtre d'avis, ticket `pop-ins-avis.md`, qui reste **séparée** de ce formulaire), pas de partage de l'app. Voir §8.

---

## 4. « Donner mon avis » (formulaire externe)

- **Visible seulement si l'adresse est renseignée et valide** (`https://`, sans espace, guillemet, antislash ni `$` : règle déjà en vigueur pour `miff.feedbackFormUrl`, `pop-ins-avis.md` §8). Sinon : **aucune ligne**, aucun message.
- **Même adresse** que celle de « Peux faire mieux... » (fenêtre d'avis) : **un seul endroit par plateforme**. Android : `BuildConfig.FEEDBACK_FORM_URL` existe ; iOS : équivalent à créer, **même règle** (HTTPS seulement, vide autorisé, jamais lue depuis un lien reçu). **Mise à jour 2026-11-13 : l'adresse existe (Google Forms, fournie par le PM, valeur par défaut de `miff.feedbackFormUrl`) : la ligne « Donner mon avis » sera donc visible dès la livraison de ABOUT-1A ; aucune configuration supplémentaire à attendre. Non codé dans la PR `android/feedback-form-url`.**
- **Aucune donnée personnelle n'est ajoutée à l'adresse** (ni nom, ni e-mail, ni identifiant, ni position). Le navigateur et le service du formulaire verront l'adresse IP : c'est un site tiers, comme n'importe quel lien.
- **Texte** : « Donner mon avis » / « Dis-nous ce qui te plaît et ce qui pourrait être mieux. » (sous-titre ; aucune promesse de réponse). **Séparation nette avec les stores** : ce lien n'ouvre **jamais** une fiche de store et ne parle pas de « note » ni d'« étoiles » (pour rester loin du filtrage d'avis décrit en `pop-ins-avis.md` §2).
- **Cette ligne est indépendante du drapeau de build de la fenêtre d'avis** (`REVIEW_PROMPT`, éteint en release) : elle peut figurer dans un build de diffusion, puisqu'elle n'ouvre qu'un formulaire privé. À confirmer à la porte « prêt à soumettre » avec le reste (Q3).
- **À fournir par le PM** : l'adresse et le **choix du service** (règle d'or : gratuit d'abord, conforme aux conditions du service ; vérifier où les réponses sont hébergées, car elles contiendront des données personnelles que la personne y écrit). Pistes à étudier, **aucune condition lue** : un service de formulaires gratuit hébergé en Europe, ou Google Forms. **Je ne choisis pas.**

---

## 5. Écran « Licences et attributions »

**Principe** : une liste lisible, complète, **hors connexion**, toujours à jour avec ce que l'app contient vraiment.

### 5.1 Contenu

- **Phrase d'introduction** : « Miff s'appuie sur des logiciels et des données libres. Merci à celles et ceux qui les créent. Voici ce que nous utilisons, et sous quelle licence. »
- **Trois groupes** :
  1. **Logiciels** : une ligne par bibliothèque **livrée dans l'app** : nom, version, licence abrégée. Toucher = **texte complet de la licence** (et de la notice de copyright quand il en existe une), lisible hors connexion. Les dépendances de test, de debug, les plugins de compilation et la fenêtre d'avis (absente des builds de diffusion) **n'y figurent pas** : on ne liste que ce qui est dans l'app de diffusion.
  2. **Cartes et adresses** (voir 5.2).
  3. **Polices** : Baloo Thambi 2 et Hanken Grotesk, SIL OFL 1.1.
- Un lien « Politique de confidentialité » en bas du groupe « Cartes et adresses », pour dire ce qui part de l'appareil.

### 5.2 Cartes et adresses (reprend `lieux-fournisseurs-gratuits.md`)

| Plateforme | Ligne affichée (FR) | Licence / base |
|---|---|---|
| **Android** | « Carte : OpenFreeMap, © OpenMapTiles, données © contributeurs OpenStreetMap » | Conditions d'OpenFreeMap ; ODbL |
| **Android** | « Adresses : service Photon de komoot, données © contributeurs OpenStreetMap » | Photon : Apache 2.0 (logiciel) ; ODbL (données) |
| **Android** | « Moteur de carte : MapLibre Native » | BSD-2-Clause : texte de la licence et **mention de copyright** de MapLibre |
| **iOS** | « Cartes et adresses : Plans d'Apple (MapKit) » | Mention propre à Apple, déjà affichée dans la carte ; conditions de MapKit **non lues** (`lieux-fournisseurs-gratuits.md`, §iPhone) |

**Une phrase de transparence** (cf. le même document) : « Quand tu tapes une adresse pour créer un lieu, le texte part vers le service d'adresses, sans ton nom ni ta position. » À **faire relire par `release-manager` avec la Politique de confidentialité** pour qu'elles ne se contredisent pas. **[À vérifier]** : je ne réécris pas la politique ici.

### 5.3 Exactitude et maintenance

- L'inventaire de départ est celui de `android/README.md` (« Inventaire des dépendances ») et de `ios/README.md` (17 paquets dont les dépendances indirectes) : les licences y sont notées « d'après les dépôts amont, non relues fichier par fichier ».
- **Critère de maintenance** : ajouter une dépendance livrée **sans** entrée de licence doit **faire échouer la CI**, pour que l'écran ne devienne jamais faux sans que personne le voie. (La façon de le faire est le choix du développeur.)
- **Jamais** de formule « conforme », « validé » ou « relu par un juriste » sur cet écran ni dans la PR.

---

## 6. Différences iOS / Android

| Sujet | Android | iOS |
|---|---|---|
| Version | À **ajouter** (nom + numéro de version interne) | **Déjà affichée** en pied de liste des Réglages [À vérifier] : **déplacée** dans l'écran (Q4), le pied de liste disparaît |
| Aide « rappels » | Oui | Non (pas d'équivalent) |
| Aide « détections » | Écran existant | Écran propre à iOS existant |
| Formulaire externe | `BuildConfig` existant, **renseigné le 2026-11-13** (même adresse) | **À créer** (même règle, même texte) |
| Licences | Bibliothèques (README Android) + MapLibre, OpenFreeMap, OSM, Photon | firebase-ios-sdk, GoogleSignIn-iOS, AppAuth, GTMAppAuth, PhoneNumberKit (MIT), etc. + mention d'Apple pour les cartes ; **aucun MapLibre / OpenFreeMap / Photon** |
| Copie de la version | Toast du système sur Android 13+ ; sinon message dans l'écran | Message dans l'écran (pas de toast système) |

---

## 7. Critères d'acceptation

**Accès**
- [ ] **AP1** Réglages contient « À propos et aide » en dernière ligne, avec son sous-titre ; la ligne est présente **aussi** dans les Réglages restreints (compte sans Miff).
- [ ] **AP2** Depuis l'onglet Profil, l'écran est atteignable en **deux touches** (engrenage, « À propos et aide ») et « Nous contacter » en **trois**.

**Version**
- [ ] **AP3** L'écran affiche « Version {nom} (build {numéro}) » correspondant **exactement** à la version installée (vérifié sur le build de recette). Toucher la ligne copie « Miff {nom} ({numéro}) » et rien d'autre ; une confirmation brève apparaît.
- [ ] **AP4** iOS : la version n'apparaît **plus** en pied de la liste des Réglages (pas de doublon).

**Aide et contact**
- [ ] **AP5** « Si les détections tardent » (et « Si les rappels n'arrivent pas » sur Android) ouvrent les mêmes écrans que depuis Confidentialité / Réglages > Notifications ; le bouton retour ramène à « À propos et aide ».
- [ ] **AP6** « Nous contacter » ouvre l'application e-mail avec l'adresse du contact provisoire, **sans texte pré-rempli**. Sans application e-mail : l'adresse est **affichée** et copiable, avec un message calme.
- [ ] **AP7** « Nous contacter » **n'existe plus** dans Confidentialité (déplacé, Q2) ; les critères **RG6** (`onboarding.md`) et **B5** (`feed-profil-reglages.md`), les checklists `onb-3b-android-recette-pm.md` et `onb-7-ios-recette-pm.md` sont **mis à jour** pour le dire.

**Donner mon avis**
- [ ] **AP8** Avec l'adresse **vide** : **aucune** ligne « Donner mon avis » (Android et iOS) et rien d'autre ne change.
- [ ] **AP9** Avec une adresse valide (build d'essai) : la ligne apparaît ; la toucher ouvre **exactement** cette adresse dans le navigateur, sans rien d'ajouté. Une adresse non `https://` est **refusée à la compilation** et **jamais ouverte**.
- [ ] **AP10** Aucun texte de la ligne ne parle de store, de note ou d'étoiles.

**Textes légaux**
- [ ] **AP11** Conditions et Politique s'ouvrent dans la **langue de l'app** (comme dans Confidentialité) ; leurs liens dans Confidentialité fonctionnent toujours.

**Licences**
- [ ] **AP12** L'écran s'ouvre **sans réseau** (mode avion) et chaque ligne de licence ouvre son texte complet.
- [ ] **AP13** Les lignes « Carte », « Adresses » et le copyright MapLibre figurent **sur Android** avec le libellé de §5.2 ; **iOS** affiche la ligne Apple et **ne mentionne ni MapLibre, ni OpenFreeMap, ni Photon**.
- [ ] **AP14** Aucune dépendance de test, de debug ni la fenêtre d'avis n'est listée dans un build de diffusion.
- [ ] **AP15** Une dépendance livrée sans licence fait échouer la CI (preuve : un essai volontaire consigné dans la PR).

**Transversal**
- [ ] **AP16** Textes **français et anglais**, tutoiement, clés `about_` avec parité Android / iOS (ajoutées à `docs/contracts/i18n-keys.md`) ; contact et adresse du formulaire **jamais écrits dans un texte traduit**.
- [ ] **AP17** Accessibilité : chaque ligne est un seul élément nommé (« {titre}, {sous-titre} ») ; cibles 48 dp / 44 pt ; **plus grand texte du système** : tout défile, rien n'est coupé ; droite-à-gauche correct ; la copie de la version est annoncée.
- [ ] **AP18** **Aucune** permission nouvelle, **aucun** événement Analytics, **aucune** lecture ni écriture Firestore, **aucun** appel réseau à l'ouverture de l'écran ; aucune donnée personnelle dans un journal.
- [ ] **AP19** Les liens externes (page légale, formulaire) n'acceptent que `https://` ; l'adresse du formulaire n'est lue que depuis la configuration de build.

---

## 8. Hors-scope et v2

**Hors-scope (ce ticket)** : FAQ ou « Comment ça marche » ; relecture du tutoriel d'accueil ; notes de version ; page d'état du service ; « Évaluer Miff » sur le store (reste dans `pop-ins-avis.md`) ; partage de l'application ; informations techniques détaillées (modèle de téléphone, identifiants) ; **rédaction** des textes légaux ; mise à jour de la Politique de confidentialité (à faire par `release-manager`, voir §5.2) ; carte hors connexion.
**v2 possible** : FAQ courte dans l'app (contenu à écrire avec le PM) ; message de contact pré-rempli avec la version (décision de confidentialité à prendre) ; page d'aide hébergée.

## 9. Découpage

Une branche et une PR par plateforme, **Android d'abord** ; maquettes : **aucune n'existe** pour cet écran. **[Proposé]** : pas d'export à demander au PM ; le développeur réutilise le gabarit des écrans de Réglages et **liste ses écarts dans la PR** (règle du 2026-10-28 : liberté d'ajouter les écrans non designés).

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **ABOUT-1A** | **Android** : « À propos et aide » | Ligne dans Réglages (et Réglages restreints), écran, version + copie, aide, contact déplacé, formulaire masqué si vide, textes légaux, **liste des licences** (hors connexion, garde CI), clés `about_`, mise à jour de RG6 / B5 et des checklists | **M** | Décision du PM (§10) |
| **ABOUT-1I** | **iOS** : mêmes livrables | + équivalent iOS de l'adresse du formulaire (configuration de build, un seul endroit), retrait du pied de liste, inventaire des 17 paquets | **M** | ABOUT-1A ; TestFlight sur iPhone 11 Pro |

Si la PR Android devenait trop grosse, scinder : **ABOUT-1A-a** (écran, version, aide, contact, formulaire) et **ABOUT-1A-b** (licences) : à la discrétion du développeur, sans changer ce document.

---

## 10. Questions ouvertes pour le PM (avec valeur par défaut : le développement peut démarrer avec elles)

| # | Question | Valeur par défaut proposée |
|---|---|---|
| **Q1** | Une seule ligne **« À propos et aide »**, **en dernier** dans Réglages, **aussi présente** dans les Réglages restreints ? | **Oui** |
| **Q2** | « Nous contacter » : **déplacé** hors de Confidentialité (avec mise à jour de RG6 / B5 et des checklists), ou **dupliqué** pour ne rien casser ? Les liens Conditions et Politique **restent** dans Confidentialité dans les deux cas. | **Déplacé** (un seul endroit, plus simple, pas de doublon à maintenir) |
| **Q3** | **Formulaire** : à quelle date donnes-tu l'adresse et quel **service** choisis-tu (gratuit, hébergement des réponses à vérifier) ? La ligne est-elle **autorisée dans un build de diffusion** (elle n'ouvre qu'un formulaire privé, séparé des stores) ? | **Masquée tant que vide** ; autorisée en diffusion, à reconfirmer à la porte « prêt à soumettre » |
| **Q4** | **Version** : l'afficher **seulement** dans cet écran (iOS perd son pied de liste), avec **copie** au toucher ? | **Oui** |
| **Q5** | **Licences** : texte complet au toucher (hors connexion), plutôt qu'une simple liste de noms ? | **Oui** |
| **Q6** | Les aides existantes (« détections », « rappels ») : **lien depuis ici en plus** de leur place actuelle ? | **Oui**, mêmes écrans, aucun contenu copié |
| **Q7** | Aucune info technique de plus (modèle de téléphone, identifiants) dans la version copiée ? | **Aucune** (confidentialité) |
| **Q8** | Pas de maquette Figma pour cet écran (gabarit des Réglages, écarts listés dans la PR) ? | **Pas de maquette** |

*Ce document ne modifie ni le code, ni Notion, ni Figma. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste.*
