# Plan de lancement de Miff

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), à valider par le PM. Rien n'est publié, aucun compte créé, aucune dépense engagée. Le PM publie lui-même tout contenu.**

Budget de base : **0 €**. Toute option payante est présentée avec son coût (quand je peux le vérifier, sinon la mention « coût non vérifié ») **et** son alternative gratuite, et rien n'est engagé sans décision explicite du PM (règle d'or du 2026-10-21).

Références : `positioning.md` (ton, vocabulaire, consentement), `store-listing-ios.md`, `store-listing-android.md`, `aso-keywords.md`, `screenshots-brief.md`, `docs/legal/*`, `docs/specs/paywall-premium.md`.

---

## 1. Ce que le produit est aujourd'hui (et ce qu'on peut promettre)

**Livré (recoupé le 2026-11-07 avec `docs/release/*`, `docs/legal/*` et le code de `main`, `8e87ebd`)** :
- **Android** : compte (e-mail, Google), Miffs par code secret, lieux de confiance, détection automatique des arrivées et départs, Feed avec une carte par proche, photo « Donner des nouvelles » (sans texte, toujours avec son endroit, 24 h), Réglages (arrêt du partage, suppression de compte, contact), français et anglais.
- **iOS** : compte (e-mail, Google, Apple), Miffs par code secret, lieux de confiance, autorisation de localisation, suivi LOC-4b (code livré, recette PM sur iPhone à constater). **Pas encore de Feed, d'Update ni de Réglages (donc pas de suppression de compte dans l'app)** : l'iOS n'est pas soumissible en l'état (Apple 5.1.1(v)).
- **Règles produit** : localisation « Toujours » **obligatoire** (jamais « facultative »), dernière position connue stockée côté serveur (sans historique), gratuit = **une Miff à la fois et 2 lieux de confiance** (le brouillon disait un lieu : ancienne règle).

**Non actif, donc jamais dans les fiches des stores ni dans les contenus publics** :
- les **notifications push** (la politique de confidentialité dit explicitement qu'aucune n'est envoyée et qu'aucun jeton n'est enregistré ; elles exigent un serveur : plan Blaze non activé) ;
- l'**abonnement Miff+** (décidé le 2026-10-31 : essai de 7 jours sur l'annuel seulement, annuel 4,99 EUR/mois soit 59,88 EUR par an reconduit au même tarif, mensuel 6,99 EUR/mois ; mais aucun achat n'est branché : le premium est accordé à la main) ;
- la phrase « Économisez 30 % » (le calcul réel est 28,6 %, écart assumé par le PM dans le paywall : ne jamais la reprendre dans un contenu public) **et sa remplaçante décidée le 2026-11-07, « presque 30 % d'économie » / « almost 30% off »** : tant que Miff+ n'est pas vendu, **aucun pourcentage dans un contenu public** (règle « false price » d'Apple 2.3.1(a), à recalculer si les tarifs changent).

**Contrainte structurante : l'effet de réseau.** Une Miff ne sert à rien toute seule : chaque nouvel utilisateur doit en convaincre au moins un autre. C'est à la fois le risque principal (taux d'activation faible) et le levier de croissance gratuit (le partage du code). Tout le plan est construit autour de **cercles complets** (2 à 5 personnes qui installent ensemble), pas d'utilisateurs isolés.

---

## 2. Audience et messages (voir `positioning.md` pour le détail)

| Audience | Besoin | Message clé FR | Message clé EN |
|---|---|---|---|
| **Famille** (parent, grand-parent, enfant devenu adulte) | Savoir que l'autre est bien arrivé sans appeler | Des nouvelles de tes proches, sans les déranger. | News from your loved ones, without bothering them. |
| **Amis, colocation** | Retour de soirée, week-end | Tout le monde est bien rentré ? Un coup d'oeil, c'est tout. | Did everyone get home okay? One glance, that is all. |
| **Couple** | Un petit confort partagé | Chacun garde sa liberté de partager, ou non. | Each of you keeps the freedom to share, or not. |
| **Celui qu'on convainc d'installer** (le « rassurant ») | Peur d'être pisté(e) | Tu choisis ce que tu partages, et tu peux arrêter à tout moment. | You choose what you share, and you can stop at any time. |

Règles de ton : aucune peur, aucune culpabilité, aucune promesse de sécurité (`positioning.md`, sections 3 à 5).

---

## 3. Prérequis avant tout testeur externe (porte « Phase 0 »)

À faire par le PM ou à décider ; **aucun agent ne déploie, ne crée de compte ni ne soumet**.

1. **Pages légales en ligne** (déploiement Firebase Hosting, soumis à l'accord explicite du PM) : conditions, politique de confidentialité, suppression de compte, page d'accueil. Les stores exigent les URL de politique et de suppression de compte. Actions de console Analytics du PM (`docs/legal/README.md` §3, « Avant le déploiement ») à faire d'abord.
2. **Cohérence des pages publiques avec le produit réel** (voir section 9, points de vigilance). **État vérifié le 2026-11-07** : la phrase « Miff ne stocke pas ta position GPS » n'existe **plus** dans les CGU, la politique ni la page d'accueil (la décision du 2026-11-01 y est appliquée). **Restent à corriger avant la mise en ligne** : la maquette de la page d'accueil montre encore deux notifications qui n'existent pas, et le texte n'y cite pas encore l'endroit de la photo (`docs/release/update-photo-declarations.md` §9.5).
3. **Contact public** : l'adresse provisoire de `CLAUDE.md` devient publique ; décider si elle est remplacée avant la bêta externe (recommandé par `docs/legal/README.md` §3).
4. **Vérification du nom** « Miff » dans les deux stores et dans la base de marques (risque J18) : gratuit, à faire tôt.
5. **Support** : une adresse consultée au moins une fois par semaine (réponses RGPD sous un mois) et un canal de retours (formulaire ou e-mail).
6. **Application prête à tester** : les parcours de consentement (accord distinct à la localisation, arrêt du partage, suppression de compte) fonctionnent sur les deux plateformes. Ils conditionnent aussi la revue des stores.

---

## 4. Phases et calendrier

Pas de dates inventées : le calendrier dépend de l'avancement des tickets et des délais des stores, hors de notre contrôle. **J0 = soumission publique aux stores.** Les durées sont des ordres de grandeur à ajuster.

| Phase | Quand | Objectif | Qui voit l'app |
|---|---|---|---|
| **0. Préparation** | Maintenant | Lever les prérequis de la section 3 ; relire fiches et captures | Personne |
| **1. Test fermé** | Dès que les parcours de base tournent sur les deux plateformes | Vérifier que la boucle centrale marche **dans la vraie vie** (création, code, lieux, détection, mise à jour) et que le consentement est compris | PM + proches (pairs ou trios) |
| **2. Bêta externe** | Après correction des bloquants de la phase 1 | Mesurer l'activation et la qualité sur des inconnus, valider le texte de fiche | Quelques dizaines de personnes, par cercles |
| **3. Lancement public** | Quand les critères de la section 8 sont remplis et les stores ont validé | Faire connaître Miff, premières installations organiques | Tout le monde |
| **4. Après lancement** | J0 + 30 jours puis en continu | Itérer ASO et produit ; préparer notifications et Miff+ (« à venir », pas annoncés) | Tout le monde |

### Phase 1 : test fermé

**iOS (TestFlight)** : 100 testeurs **internes** au maximum, mais un testeur interne doit être un utilisateur d'App Store Connect (`docs/tickets/initialisation-technique.md`, ligne 80) ; pour des proches qui ne sont pas dans l'équipe, il faut un **groupe externe** (jusqu'à 10 000 testeurs), dont le **premier build passe en revue Apple**. Un build reste utilisable 90 jours. Coût : 0 € (compte développeur déjà payé).

**Android (Google Play)** : piste de **test interne** (sans revue) pour démarrer ; puis piste de **test fermé**. **Règle vérifiée le 2026-11-07 sur la page officielle de Google (14151465, lue directement)** : « Google Play requires personal developer accounts created after November 13, 2023, to test their apps » ; au moins **12 testeurs** inscrits au test fermé au moment de la demande d'accès à la production, **inscrits en continu depuis 14 jours** ; un testeur qui se désinscrit avant 14 jours ne compte pas. **Non précisé par Google dans cette page** : si la règle revient pour chaque nouvelle app d'un même compte, et le cas des comptes « organisation ». La date et le type du compte du PM (créé pour Ghostrun) restent à lire dans la Play Console : **planifier comme si la règle s'appliquait** (donc un test fermé de 14 jours au moins avant la production). **Ne pas recourir à des services « testeurs à louer »** (des offres de ce type existent en ligne) : ce n'est pas le test réel que la règle demande, et `CLAUDE.md` classe ce genre de contournement pire qu'un coût maîtrisé (risque de bannissement du compte) ; recruter de vrais testeurs. Je n'ai pas lu de texte de Google qui l'interdise nommément : c'est un choix de prudence, pas une règle citée.

**Recrutement** : le PM et ses proches, **par cercles de 2 à 5** (cinq cercles de trois personnes couvrent largement les 12 testeurs de Google). Chaque cercle teste sur au moins un iPhone et un Android si possible (le matériel du PM : un iPhone 11 Pro et un OPPO Find X5 Lite).

**À observer** : la détection d'arrivée et de départ sur le téléphone OPPO (économie d'énergie du fabricant, risque connu pour le géofencing), les retards, la compréhension de l'écran de consentement, la facilité à partager le code. Retours **qualitatifs** (appels courts, messages), pas de sondage lourd.

Textes de test (description de la bêta, « Que tester ? ») : `store-listing-ios.md`, section 3.

### Phase 2 : bêta externe

- **iOS** : lien public TestFlight (jusqu'à 10 000 testeurs).
- **Android** : test fermé ou ouvert selon la règle ci-dessus.
- **Recrutement gratuit** : réseau personnel du PM, communautés (section 5), proches des testeurs. **Ne pas utiliser la liste « Être prévenu(e) du lancement » de la page d'accueil pour la bêta** : la politique de confidentialité dit que l'adresse ne sert qu'à prévenir de la **sortie** de l'app, puis est supprimée. Décision du PM : utiliser ces adresses pour la bêta ou seulement à la sortie publique (recommandation : seulement à la sortie, pour ne pas contredire le texte).
- **Objectif de la phase** : valider l'activation (inscription vers cercle complet), la fiabilité de la détection, la clarté du consentement, les avis qualitatifs. Taille proposée : **quelques dizaines de personnes** en cercles, suffisante pour trouver les bloquants ; **ordre de grandeur arbitraire, à fixer par le PM**.
- **Gouvernance** : tout retour mentionnant un malaise sur la localisation est traité en priorité et peut modifier le texte de consentement (avec nouvelle version du texte et nouveau consentement : `docs/legal/README.md` §2).

### Phase 3 : lancement public

**J-14 à J-7** : fiches des stores validées par le PM ; captures prêtes (`screenshots-brief.md`) ; page d'accueil mise à jour (liens aux stores à la place de « Bientôt sur ») ; textes de contenus écrits ; message d'annonce aux testeurs ; communiqué court prêt.

**J0** : le PM soumet dans les stores (aucun agent ne soumet) ; annonce sur les canaux gratuits de la section 5, dès la mise en ligne. Les stores peuvent prendre du temps de revue : **ne pas annoncer de date publique avant l'acceptation**.

**J+1 à J+7** : réponse à chaque avis et à chaque e-mail ; correction des bloquants ; relevé quotidien des indicateurs (section 6).

**Après la fenêtre de lancement** : une publication de contenu par semaine au plus (le produit est calme : le marketing aussi).

### Phase 4 : après lancement

1. **Itérations ASO** une variation à la fois (`aso-keywords.md`, section 8).
2. **Notifications push : « à venir »**. Prérequis : serveur (plan Blaze, décision du PM, non activé), accord de l'utilisateur avant d'enregistrer un jeton, mise à jour de la politique de confidentialité avant activation. Ne rien annoncer avant que ce soit actif.
3. **Miff+ : « à venir »**. Prérequis : validation serveur des achats (Blaze), création des produits dans les stores, textes légaux d'abonnement (reconduction tacite, rétractation), **médiateur de la consommation** (risque J13, coût non vérifié, à présenter avant tout engagement), retrait de « Économisez 30 % » ou calcul exact, prix lus depuis les stores et jamais en dur. Quand ce sera actif : mettre à jour la déclaration « achats intégrés » des stores, la classification, la description (retirer « Dans cette version, une Miff... », ligne C13), et les captures.

---

## 5. Canaux (gratuits d'abord)

**Règle** : on propose, le PM publie. Aucune marque tierce nommée dans les contenus. Respecter les règles de chaque communauté ; se présenter comme créateur de l'app ; demander des retours plutôt que des téléchargements.

| Canal | Type | Coût | Effort | Message | Risque / remarque |
|---|---|---|---|---|---|
| **Cercle personnel du PM** (famille, amis proches) | Organique | 0 € | Faible | « Je teste une app, installons-la à plusieurs » | Le plus fiable pour l'effet de réseau ; biais d'indulgence des proches |
| **Partage du code d'une Miff** (le geste central du produit) | Organique, intégré | 0 € | Faible | Texte d'invitation calme et clair | Levier principal de croissance. **Texte réel relevé le 2026-11-07** (Android `miff_share_message`, iOS `MiffSharing.swift`) : « Rejoins ma Miff « {nom} » sur Miff. Voici le code à saisir : {code} / Pour télécharger l'application : {lien} ». Le lien est celui du **site** (`LEGAL_BASE_URL` ; décision QM14 du ticket Miff : « le site public, tant qu'une page dédiée n'existe pas »), donc la page d'accueil, qui dit aujourd'hui « Bientôt sur » et « Miff n'est pas encore disponible ». Le texte n'explique ni ce qu'est Miff ni que le partage est consenti et révocable. **Proposition** à décider (n° 10 de `decisions-stores-avant-soumission.md`) |
| **Page d'accueil** (déjà dans le dépôt, avec référencement et partage soignés) | Organique | 0 € (adresse `web.app` fournie) | Faible | Titre « Savoir que tes proches vont bien, sans les déranger. » | À corriger avant publication (section 9) |
| **Communautés en ligne** (forums de parents, colocataires, étudiants, couples, développeurs indépendants) | Organique | 0 € | Moyen | Retour d'expérience honnête, questions sur les attentes de vie privée | Spam = bannissement ; chaque communauté a ses règles ; ne pas cibler les enfants |
| **Courtes vidéos et réseaux sociaux** | Organique | 0 € (outils de montage gratuits) | Moyen à élevé | Scènes de vie calmes, démonstration de l'app, FAQ « suis-je pisté(e) ? » | Pas de scénario de peur ; pas d'enfants à l'image ; voir idées de contenu (section 7) |
| **Annuaires de lancement de produits et communautés de créateurs** | Organique | 0 € | Faible | Histoire du créateur, choix de la réassurance consentie | Impact incertain, audience pas forcément la cible |
| **Presse et blogs spécialisés** (parentalité, bien-être numérique, applications utiles) | Organique | 0 € | Moyen | Angle : « réassurance consentie plutôt que surveillance » | Faible probabilité de reprise ; préparer un dossier de presse simple (une page, captures, contact) |
| **Mise en avant par les stores** (formulaires de proposition, si disponibles) | Organique | 0 € | Faible | Fiche soignée | **Hypothèse non vérifiée** (existence et conditions) |
| **Avis et notes dans les stores** | Organique | 0 € | Faible | Demande d'avis après un moment positif | Voir remarque sur la maquette d'avis ci-dessous |

**Remarque sur la maquette d'avis** (`design/exports/pop-ins/03-avis-store.png`) : le choix « J'adore ! » / « Peux faire mieux... » qui n'ouvrirait la boîte d'avis du store que pour les personnes satisfaites est un **filtrage des avis**. **Vérifié le 2026-11-07** : (1) Apple, guideline 5.6.1 (texte relu) : « Use the provided API to prompt users to review your app… and we will disallow custom review prompts » ; la 5.6.3 interdit de manipuler « charts, search, reviews, or referrals ». (2) Google, documentation de l'API d'avis dans l'app (relue) : « Your app shouldn't ask the user any questions before or while presenting the rating button or card, including questions about their opinion (such as "Do you like the app?") » ; pas de bouton qui déclenche l'API. **Non lu** : le texte de la politique Google « User Ratings, Reviews, and Installs » (la page ouverte était une autre politique). **Conclusion** : l'écran tel que maquetté (question d'opinion avant la boîte du store, boîte réservée aux satisfaits) est à ne pas livrer ; une demande d'avis doit passer par l'API du système, sans question préalable. Décision du PM : n° 11 de `decisions-stores-avant-soumission.md`. L'écran n'existe pas dans le code (recherche faite dans `android/` et `ios/`).

### Options payantes (aucune engagée)

| Option | Ce qu'elle apporte | Coût | Alternative gratuite |
|---|---|---|---|
| Publicité dans les stores (annonces de recherche, campagnes d'installation) | Visibilité immédiate sur des mots-clés | **Variable, fixé par le PM ; aucun tarif vérifié** | ASO (`aso-keywords.md`) et partage entre proches |
| Influence rémunérée | Audience tierce | **Non vérifié ; très variable** | Micro-communautés, retours spontanés des testeurs, contenu du créateur |
| Agence de relations presse | Accès à des journalistes | **Non vérifié** | Contact direct avec dossier de presse simple |
| Nom de domaine dédié (ex. un `.fr`) | Adresse plus mémorable ; courriel dédié | **Coût annuel non vérifié** (le domaine n'est **pas** acheté, `docs/legal/README.md` §2) | Adresse `web.app` fournie gratuitement par l'hébergement |
| Relecture juridique des textes | Réduit les risques J1 à J8, J19 à J21 | **Non vérifié** ; non prévue (décision du 2026-10-23, risque assumé) ; « première dépense à envisager si le projet grandit » (`docs/legal/README.md` §4) | Textes actuels, risques assumés |
| Adhésion à un organisme de médiation de la consommation | Obligatoire en pratique dès qu'un abonnement est vendu (risque J13) | **Non vérifié** | Aucune pour le Premium ; pas d'abonnement vendu tant que ce n'est pas réglé |
| Outils d'analyse ASO payants | Estimations de volume | **Non vérifié** | Méthodes gratuites de `aso-keywords.md` §7 |

---

## 6. Indicateurs à suivre

Principe : **mesurer ce qui dit si le concept plaît**, avec le minimum de collecte. Analytics n'est activé que pour les personnes qui ont accepté (clic sur « Rejoindre ») et jamais avec une position (`docs/contracts/analytics-events.md`) ; les chiffres d'analyse **sous-estiment donc** l'usage réel (hypothèse : les utilisateurs qui désactivent les statistiques ne sont pas représentatifs).

| Famille | Indicateur | Source | Remarque |
|---|---|---|---|
| **Acquisition** | Impressions, vues de la fiche, conversion fiche vers installation, part « recherche » vs « référent » | Consoles des deux stores (gratuites) | Hypothèse H8 : la recherche pèsera peu au début |
| **Activation** | Inscription terminée (`sign_up_completed`) vers onboarding terminé (`onboarding_completed`) vers Miff créée ou rejointe (`miff_created`, `miff_joined`) | Événements existants du contrat d'analytique | Déjà prévus |
| **Activation (à ajouter)** | Premier lieu de confiance créé ; consentement à la localisation donné (oui ou non, **sans aucune position**) ; première photo publiée ; premier statut automatique reçu | **Propositions** d'événements nouveaux, à faire valider par le PM puis ajouter au contrat **et** au code des deux plateformes (`docs/contracts/analytics-events.md`, « Évolution du contrat ») avant de s'y fier | Le contrat actuel ne les contient pas et dit : « Aucun événement sur les lieux de confiance, le feed, les mises à jour de statut, les notifications ou les réglages : non spécifiés ici, et ils frôlent la localisation. À proposer plus tard, un par un, avec avis du PM. » **Contrainte technique** : le contrat refuse tout nom d'événement ou de paramètre contenant `place`, `location`, `lieu`, `position`, `zone`, `address`, `gps`… : les noms ci-contre devront être choisis sans ces mots. Décision du PM : n° 14 de `decisions-stores-avant-soumission.md` |
| **Réseau** | Part des Miffs avec au moins 2 membres ; taille moyenne d'une Miff | À définir avec `backend-data` (comptage agrégé, sans lecture du contenu des statuts) | Indicateur central de l'effet de réseau |
| **Rétention** | Utilisateurs actifs à J1, J7, J30 | Consoles des stores | Seuils à fixer après la bêta |
| **Qualité** | Taux de sessions sans plantage ; note moyenne ; thèmes des avis (attente de carte en direct ? sentiment d'être pisté ?) | Crashlytics (si consentement), consoles des stores | Lire chaque avis |
| **Confiance** | Part des comptes qui n'obtiennent pas la localisation « Toujours » (état seulement : elle est **obligatoire**, donc un refus bloque l'app) ; arrêts de partage ; demandes de suppression de compte | Événements à définir (mêmes réserves que ci-dessus) ; courriels ; retours des testeurs | Indicateur de santé du consentement. Mesure fiable seulement via les retours qualitatifs tant qu'aucun événement n'est validé |
| **Support** | Nombre d'e-mails par semaine, délai de réponse | Boîte de contact | Réponses RGPD sous un mois |
| **Coûts** | Lectures Firestore par utilisateur actif par rapport aux quotas gratuits | `docs/dev/surveillance-quotas.md` | Premier plafond : nombre de lectures (voir `feed-profil-reglages.md` §5.3) |

---

## 7. Idées de contenu (non publiées ; le PM publie)

Ton : calme, concret, tutoiement. Aucun scénario de peur.

1. **« Tu es bien arrivé(e) ? » : la conversation qu'on n'a plus à avoir** (courte vidéo ou texte, scène de la vie courante).
2. **Comment fonctionne le consentement dans Miff** : trois écrans en quinze secondes (écran d'explication, arrêt du partage, suppression de compte).
3. **FAQ en vidéo : « Mes proches peuvent-ils me suivre en direct ? »** (réponse : non, un statut simple et une dernière position connue avec l'heure ; reprendre la réponse `a1` de la page d'accueil : « ce n'est pas un suivi en direct, et Miff ne garde aucun historique de tes déplacements »). **Ne jamais dire « Miff ne stocke pas ta position »** : c'est faux depuis le 2026-11-01.
4. **Miff et ton téléphone : pourquoi une arrivée peut être en retard** (honnêteté sur l'économie d'énergie).
5. **Les coulisses** : pourquoi un produit sans publicité ni revente de données (le créateur parle en son nom).
6. **Pour les rassurants** : « Tu installes Miff pour quelqu'un ? Voici ce que ça ne fait pas. »
7. **Cas d'usage** : colocation, retour de soirée, grands-parents (pas d'enfants à l'image).

Interdits (voir `positioning.md`) : faits divers, statistiques de disparitions, formulations culpabilisantes, comparaisons chiffrées avec d'autres apps, avis de témoins anonymes.

---

## 8. Critères de passage entre phases (propositions, à ajuster par le PM)

| Passage | Critères proposés |
|---|---|
| Phase 1 vers 2 | Aucun bloquant sur le parcours de consentement et sur la suppression de compte ; détection d'arrivée observée au moins une fois sur chaque plateforme en vie réelle ; aucun plantage récurrent. **Prérequis iOS** : Feed, Update et Réglages (dont suppression de compte) livrés sur iOS ; sans eux, la phase 2 iOS ne couvre pas le produit annoncé |
| Phase 2 vers 3 | Retours qualitatifs sans malaise de confiance non résolu ; pages légales en ligne et cohérentes avec le produit ; fiches et captures validées ; classification d'âge et déclarations de confidentialité cohérentes avec `docs/release/*` ; règle des 12 testeurs de Google respectée si elle s'applique ; nom « Miff » vérifié |
| Phase 3 vers 4 | Fenêtre de lancement terminée ; bloquants traités ; indicateurs de la section 6 relevés une première fois |

Les **seuils chiffrés** (taux d'activation, de rétention) ne sont volontairement pas fixés : je n'ai aucune donnée de référence vérifiée ; à fixer après la phase 2.

---

## 9. Points de vigilance et risques

1. **Stockage de la position** : **tranché et appliqué** (vérifié le 2026-11-07). La décision du PM du 2026-11-01 stocke la dernière position connue côté serveur ; la CGU, la politique et la page d'accueil le disent déjà. Les contenus publics disent « dernière position connue, sans historique » et jamais l'inverse. Reste à aligner avant publication : l'endroit de la photo dans la page d'accueil (le ticket Update et les règles Firestore disent déjà « photo toujours localisée », D27).
2. **Page d'accueil** : montre deux notifications dans sa maquette alors que rien n'est envoyé ; parle de Miff+ sans prix (« Miff+ permettra d'en avoir davantage », exact aujourd'hui) ; ne cite pas encore l'endroit de la photo ; à revoir avant le lancement public (hors périmètre de ce chantier : signalé).
3. **Rejet par les stores** : motifs probables à anticiper (Apple : localisation « Toujours » **obligatoire** [5.1.2(i), risque R1 assumé, interrupteur de compilation prêt], suppression de compte dans l'app [absente d'iOS aujourd'hui], contenu généré par l'utilisateur sans filtrage ni signalement ni blocage [1.2, risque J34 et J35 assumé] ; Google : formulaire de localisation en arrière-plan, politique « stalkerware » [O8, assumé], contenu généré par l'utilisateur « in-app system for reporting and blocking » [assumé], section Sécurité des données, chemin de suppression de compte) ; le consentement groupé à l'inscription (risque J21, assumé par le PM) peut aussi poser problème aux deux stores. Plan de repli UGC par étapes : `docs/release/update-photo-declarations.md` §11 bis. **Prévoir du temps de marge.**
4. **Mineurs** : pas de vérification d'âge (risque J2). Aucune communication ne vise des enfants ; classification d'âge à décider (fiches).
5. **Perception de traçage** : le mot « localisation » fait peur à une partie de l'audience cible. Le plan répond par la transparence (section 7, contenus 2, 3 et 6), jamais par des superlatifs de sécurité.
6. **Fiabilité de la détection sur Android** : les surcouches constructeur peuvent tuer l'app en arrière-plan ; une mauvaise expérience de bêta se transforme en mauvais avis. À mesurer en phase 1.
7. **Effet de réseau** : une installation isolée produit une expérience vide ; prévoir un état vide clair et un partage de code facile (point de décision de la section 5).
8. **Coûts** : un usage soudain peut dépasser les quotas gratuits (voir `docs/dev/surveillance-quotas.md`) ; ne pas lancer une campagne large sans avoir relu les quotas.
9. **Ne pas acheter de testeurs, d'avis ni de notes** : motif de bannissement (principe du `CLAUDE.md`).
10. **Calendrier** : les délais de revue et la règle des 12 testeurs sont hors de notre contrôle.
