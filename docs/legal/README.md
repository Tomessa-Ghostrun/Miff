> **BROUILLON — non relu par un juriste — ne pas publier en l'état**

# Textes légaux de Miff (CGU et politique de confidentialité)

Version de travail 0.1 — 2026-10-23 — rédigée par l'agent `release-manager`.
**Ces textes sont des brouillons. Ils ne sont pas un avis juridique.** Ils décrivent ce que l'app fait réellement d'après les documents du dépôt (`backend/data-model.md`, tickets « Initialisation technique » et « Durcissement des règles Firestore », specs). Aucun texte ne doit être publié, ni lié dans une fiche store, avant relecture par un juriste.

## 1. Les fichiers

| Fichier | Contenu |
|---|---|
| `cgu.fr.md` / `cgu.en.md` | Conditions Générales d'Utilisation (avertissement « pas un service d'urgence » en tête) |
| `politique-confidentialite.fr.md` / `.en.md` | Politique de confidentialité (RGPD) |

Le français est la version de référence ; l'anglais en est la traduction fidèle (le texte l'indique). **Toute modification se fait dans les deux langues en même temps.** Tutoiement partout (décision PM).

## 2. Comment ces textes seront utilisés (à faire plus tard, rien n'est publié)

1. **Hébergement** : une page web statique gratuite sur Firebase Hosting (projet `miff-3d283`, jamais `ghostrun-app`). Il faudra une URL publique par texte et par langue. Le domaine `miff.fr` **n'est pas acheté** : l'adresse fournie par Firebase Hosting (`...web.app`) suffit pour commencer. Quotas gratuits de Hosting : à vérifier avant mise en ligne. La mise en ligne est une action de déploiement soumise à l'accord du PM, comme tout déploiement.
2. **Dans l'app** (exigé par Apple 5.1.1(i) et Google Play) : liens vers CGU et politique, accessibles facilement (écran d'inscription, Réglages). À l'inscription, l'app enregistre déjà `termsAcceptedAt` et `termsVersion`.
3. **Dans les fiches store** : l'URL de la politique de confidentialité est obligatoire dans App Store Connect et dans Google Play Console (`marketing/store-listing-*.md`).
4. **Google Play, suppression de compte** : en plus du chemin dans l'app, Google demande une **page web** où l'on peut demander la suppression de son compte et de ses données (vérifié le 2026-10-23, aide Google Play « account deletion »). Prévoir cette page (ou une section de la page politique) avec l'e-mail de contact.
5. **Versions** : à chaque modification, changer le numéro de version en tête de texte et celui envoyé par l'app (`termsVersion`, et `consentVersion` pour le texte de consentement à la localisation, par langue). Une modification qui touche la localisation impose de redemander le consentement.

## 3. PLACEHOLDERS à compléter par le PM

Rechercher `[` dans les quatre fichiers. Liste exacte :

| Placeholder | Où | Qui décide |
|---|---|---|
| `[NOM / RAISON SOCIALE À COMPLÉTER]` | CGU art. 1 et 14, politique art. 2, pieds de page | PM (personne physique ou structure ? voir point juridique J1) |
| `[ADRESSE À COMPLÉTER]` | idem | PM (adresse postale publique : choisir avec un juriste, une adresse personnelle sera publiée) |
| `[E-MAIL DE CONTACT À DÉFINIR]` | partout | PM : créer une adresse dédiée, **à consulter au moins une fois par semaine** (délai légal de réponse d'un mois pour les droits RGPD) |
| `[DATE D'ENTRÉE EN VIGUEUR À COMPLÉTER]` et `[VERSION À COMPLÉTER, ex. 2026-11-v1]` | en-têtes | PM, à la publication |
| `[MÉDIATEUR DE LA CONSOMMATION À COMPLÉTER]` | CGU art. 16 | PM, avec le juriste (adhésion à un médiateur) |
| `[DÉLAI À DÉFINIR]` | politique art. 7 (suppression de l'état d'abonnement après suppression du compte) | PM / backend (aujourd'hui `entitlements` n'est pas supprimable par l'app, purge manuelle) |
| `[DURÉE À DÉFINIR AVEC UN JURISTE]` | politique art. 7 (conservation des e-mails de contact) | juriste |
| `[À CONFIRMER PAR LE PM : ...]` (1 par fichier, 4 au total) | suppression de compte d'un créateur de Miff (question Q18 de `data-model.md`, non tranchée) | PM |
| `[À COMPLÉTER PAR LE PM : ...]` | CGU art. 9, fin d'abonnement (que deviennent Miffs et lieux au-delà des limites gratuites ? question Q3) | PM |
| Sections `[SECTION CONDITIONNELLE ...]` et lignes `[SI PUSH ACTIVÉ ...]` | politique art. 3 et 6 | PM : garder **seulement si** les notifications push existent au lancement (elles exigent le plan Blaze, non activé à ce jour). Sinon supprimer ces passages. Si elles existent : Firebase Cloud Messaging et APNs deviennent des prestataires à citer. |
| Mentions `[À VÉRIFIER ...]` | politique art. 7 et 11 | agents dev / PM : réglage de rétention Analytics, durée Crashlytics, absence d'identifiant publicitaire sur iOS et Android |

Le prix du Premium n'est volontairement **pas** écrit dans les CGU (il est affiché par le store avant l'achat).

## 4. Points à faire valider par un juriste (avant la bêta externe et avant le lancement public)

Tous sont aussi marqués `[À VALIDER PAR UN JURISTE]` dans les textes. **Avant la bêta externe** (vrais utilisateurs hors équipe) : J1 à J8. **Avant le lancement public** : tous.

| # | Sujet | Pourquoi |
|---|---|---|
| J1 | **Identité de l'éditeur** et mentions légales (forme juridique, immatriculation, directeur de publication, hébergeur) | Obligations de la LCEN ; ces infos ne sont pas fournies. Une page « Mentions légales » est probablement à créer. |
| J2 | **Mineurs et âge minimum** (RGPD art. 8, âge de consentement numérique de 15 ans en France, absence de contrôle d'âge voulue par le PM) | Le PM n'impose pas d'âge minimum. Le texte est volontairement prudent et ne tranche pas. Risque aussi côté stores : questionnaires de classification d'âge Apple/Google et leurs règles pour les mineurs (guideline Apple 5.1.4 si des enfants sont visés). Le juriste doit dire si l'absence d'âge minimum est tenable. |
| J3 | **Bases légales** de chaque traitement (consentement, contrat, intérêt légitime), en particulier pour batterie / état de la permission, preuves de consentement | Propositions de `data-model.md` §11, non validées. |
| J4 | **Analyse d'impact (AIPD)** | Localisation + mineurs possibles + relations familiales : critères pouvant la rendre obligatoire. |
| J5 | **Transferts hors UE** : Firebase Authentication tourne depuis les États-Unis (documentation Google consultée le 2026-10-23) ; Crashlytics, App Check, Analytics : localisation non précisée par service. Vérifier l'accord de traitement des données Firebase (DPA) accepté pour le projet, le mécanisme de transfert (Data Privacy Framework, clauses types) et l'éventuelle évaluation des risques. | Le texte reste volontairement prudent. |
| J6 | **Consentement à la localisation en arrière-plan** (texte affiché dans l'app, version, preuve) et **consentement Analytics/Crashlytics** | Doit être cohérent avec le texte de la politique. La doc du projet prévoit déjà une validation juridique par langue. |
| J7 | **Durées de conservation** : compte inactif (non défini), preuve de consentement après départ, e-mails de contact | Politique art. 7. |
| J8 | **Photos « 24 h »** : les règles les rendent illisibles après 24 h, mais la suppression physique n'est pas garantie sur le plan gratuit (TTL probablement payant). Le texte dit « supprimée dès que possible ». | Acceptable ou non ? Sinon option payante (TTL / Blaze). |
| J9 | **Données de survivants d'une Miff supprimée** : illisibles tout de suite mais effacées physiquement au prochain lancement de leur app (décision PM 2026-10-22, « acceptable pour la bêta, avis juridique avant le lancement public ») | Rétention résiduelle. |
| J10 | **Suppression de compte** : effacement fiable (le client fait la suppression en plusieurs étapes ; si l'app plante, des données peuvent rester ; `entitlements` purgé à la main) | Garantie réelle de l'effacement vs. ce que dit le texte. |
| J11 | **Responsabilité** (CGU art. 11) : limitations, clauses abusives, risque lié au contexte « inquiétude pour un proche » ; avertissement « pas un service d'urgence » | Rédaction à sécuriser. |
| J12 | **Abonnements** : informations précontractuelles, droit de rétractation de 14 jours, rôle d'Apple/Google, essai gratuit (7 j vu dans la maquette, non décidé), changement de prix | Les achats passent par les stores. |
| J13 | **Médiateur de la consommation**, tribunaux compétents, clause « la version française prévaut » | Obligations de droit de la consommation. |
| J14 | **Relation entre membres d'une Miff** : chacun partage des données sur les autres (noms, photos, lieux). Rôle du créateur (qui définit les lieux, vus par tous). Responsabilité de l'éditeur vs. des utilisateurs. | Le juriste doit confirmer l'angle. |
| J15 | **Accès interne** de l'équipe aux données (console Firebase) et formulation de la politique | Éviter une promesse non tenable. |
| J16 | **Procédure de signalement de contenus** et statut d'hébergeur (LCEN / règlement sur les services numériques) | Photos et textes envoyés par les utilisateurs. |
| J17 | **Droit d'accès / portabilité** : export manuel par e-mail pendant la bêta ; format et vérification d'identité à définir ; solution automatisée plus tard | Obligation de réponse sous un mois. |
| J18 | **Marque « Miff »** : aucune vérification d'antériorité n'a été faite | Propriété intellectuelle (CGU art. 14). |

## 5. Cohérence avec les stores (à vérifier avant toute soumission)

Les textes, les formulaires des stores et l'app doivent dire **la même chose** : un écart est un motif de rejet. Vérifié sur les sources officielles le 2026-10-23 :
- **Apple** (App Review Guidelines) : 5.1.1(i) lien de politique dans App Store Connect **et** dans l'app, qui explique collecte, usages, durées de conservation et comment retirer son consentement ; 5.1.1(v) la **suppression de compte doit être possible dans l'app** (un simple e-mail de demande ne suffit pas ; l'export sur demande par e-mail, lui, est compatible) ; 5.1.2 ne pas imposer la localisation pour utiliser l'app (c'est le cas : repli sur les mises à jour manuelles) ; 5.1.5 localisation seulement si directement liée au service, avec information et consentement avant collecte. Remplir aussi les « privacy nutrition labels » (App Privacy) et le texte d'usage de la localisation « Toujours ».
- **Google Play** : politique de confidentialité dans l'app **et** sur la fiche ; localisation en arrière-plan = formulaire de déclaration, vidéo de démonstration et **information bien visible dans l'app avant la demande de permission** (elle ne peut pas se limiter à la politique) ; section Sécurité des données (Data safety) ; chemin de suppression dans l'app **et** lien web de demande de suppression.
- Ces règles évoluent : `release-manager` les revérifie avant la soumission.

## 6. Ce qui reste à faire par chacun

- **PM (actions humaines)** : choisir qui est l'éditeur ; créer l'adresse e-mail de contact ; compléter les placeholders ; trancher Q18 et Q3 (voir §3) ; trouver un juriste (J1 à J8 avant la bêta externe) ; valider la mise en ligne (déploiement Firebase Hosting) ; ne signer ni accepter aucun document légal sans relecture.
- **Agents dev** : liens vers les textes dans l'app, enregistrement de `termsVersion`/`consentVersion`, suppression de compte dans l'app, interrupteurs Analytics/Crashlytics, vérifier l'absence d'identifiant publicitaire (surtout Android).
- **`release-manager`** : intégrer les URL dans les checklists et fiches store une fois les textes validés et hébergés ; ne rien déclarer « prêt à soumettre » avant la checklist QA (`docs/qa/release-checklist.md`).
- **`aso-marketing`** : s'assurer que la fiche store ne promet rien que ces textes ne disent pas (pas de « sécurité garantie », pas de « suivi en temps réel »).
