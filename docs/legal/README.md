# Textes légaux de Miff (CGU et politique de confidentialité)

Version de travail 0.1 — mise à jour le 2026-10-24 — rédigée par l'agent `release-manager`.

**Statut : textes à compléter, jamais relus par un juriste.** Le PM a décidé le 2026-10-23 de ne pas faire relire ces textes par un juriste et d'en assumer le risque (voir §4). Ils ont été rédigés par l'équipe d'agents à partir des documents du dépôt (`backend/data-model.md`, tickets « Initialisation technique » et « Durcissement des règles Firestore », specs). Ce ne sont pas un avis juridique, et **ils sont publiés sous la seule responsabilité du PM**. Les textes eux-mêmes n'affirment jamais avoir été relus par un juriste.

**Ne rien publier tant que (1) les champs `[À COMPLÉTER ...]` ne sont pas remplis (voir §3) et (2) le PM n'a pas donné son accord explicite.**

## 1. Les fichiers

| Fichier | Contenu |
|---|---|
| `cgu.fr.md` / `cgu.en.md` | Conditions Générales d'Utilisation (avertissement « pas un service d'urgence » en tête) |
| `politique-confidentialite.fr.md` / `.en.md` | Politique de confidentialité (RGPD) |

Le français est la version de référence ; l'anglais en est la traduction fidèle (le texte l'indique). **Toute modification se fait dans les deux langues en même temps.** Tutoiement partout (décision PM).

## 2. Comment ces textes seront utilisés (à faire plus tard, rien n'est publié)

1. **Hébergement** : une page web statique gratuite sur Firebase Hosting (projet `miff-3d283`, jamais `ghostrun-app`). Il faudra une URL publique par texte et par langue. Le domaine `miff.fr` **n'est pas acheté** : l'adresse fournie par Firebase Hosting (`...web.app`) suffit pour commencer. Quotas gratuits de Hosting : à vérifier avant mise en ligne. La mise en ligne est un déploiement soumis à l'accord explicite du PM.
2. **Dans l'app** (exigé par Apple 5.1.1(i) et Google Play) : liens vers CGU et politique, accessibles facilement (écran d'inscription, Réglages). À l'inscription, l'app enregistre déjà `termsAcceptedAt` et `termsVersion`.
3. **Dans les fiches store** : l'URL de la politique de confidentialité est obligatoire dans App Store Connect et dans Google Play Console (`marketing/store-listing-*.md`).
4. **Google Play, suppression de compte** : en plus du chemin dans l'app, Google demande une **page web** où l'on peut demander la suppression de son compte et de ses données (vérifié le 2026-10-23, aide Google Play « account deletion »). Prévoir cette page (ou une section de la page politique) avec l'e-mail de contact.
5. **Versions** : à chaque modification, changer le numéro de version en tête de texte et celui envoyé par l'app (`termsVersion`, et `consentVersion` pour le texte de consentement à la localisation, par langue). Une modification qui touche la localisation impose de redemander le consentement.

## 3. Éditeur, contact et champs à compléter

### Éditeur et contact (décisions PM des 2026-10-24)

Écrits dans les quatre textes :
- **Éditeur, responsable de traitement, contact support et suppression de compte : Tomessa MFOUKA** (personne physique), **tomessa.mfouka@gmail.com**, **76 rue Marx Dormoy, 75018 Paris, France**.
- **Nom, e-mail et adresse postale deviennent publics à la publication** (CGU, politique, fiches stores, page web de suppression de compte) : l'adresse e-mail est personnelle.
- **Ces trois informations sont provisoires et remplaçables en une opération** : rechercher « Tomessa MFOUKA (personne physique) » / « Tomessa MFOUKA (individual) », `tomessa.mfouka@gmail.com` et `76 rue Marx Dormoy, 75018 Paris, France` dans les quatre fichiers, et remplacer (par exemple par une société, une adresse dédiée, une domiciliation). Il vaut mieux le faire avant la bêta externe. L'adresse e-mail doit être consultée au moins une fois par semaine (réponse aux droits RGPD sous un mois).

### Champs restant à compléter

Rechercher `[` dans les quatre fichiers. Ce que le PM n'a pas encore donné :

| Champ | Où | Comment le renseigner |
|---|---|---|
| `[DATE À COMPLÉTER À LA PUBLICATION]` | en-têtes | Date du jour de mise en ligne. |
| `[MÉDIATEUR DE LA CONSOMMATION À COMPLÉTER SI APPLICABLE]` | CGU art. 16 | Nom et coordonnées d'un médiateur de la consommation (adhésion à faire). À supprimer seulement si le PM décide de ne pas en désigner, en assumant le risque J13. |
| `[DÉLAI À COMPLÉTER]` | politique art. 7 (suppression de l'état d'abonnement après suppression du compte) | Délai réaliste : aujourd'hui `entitlements` n'est pas supprimable par l'app, la purge est manuelle (proposition : 30 jours). |
| `[DURÉE À COMPLÉTER]` | politique art. 7 (conservation des e-mails de contact) | Durée choisie par le PM (proposition : 1 an après la fin du traitement de la demande). |
| `[À CONFIRMER AVANT PUBLICATION : ...]` (1 par fichier, 4 au total) | suppression de compte d'un créateur de Miff (question Q18 de `data-model.md`, non tranchée) | Décision produit du PM, puis retirer les crochets. |
| `[À COMPLÉTER AVANT PUBLICATION : ...]` (CGU art. 9, FR et EN) | fin d'abonnement : que deviennent Miffs et lieux au-delà des limites gratuites (question Q3) | Décision produit du PM. |
| Sections `[SECTION CONDITIONNELLE ...]` et lignes `[SI PUSH ACTIVÉ ...]` (politique art. 3 et 6) | notifications push | Garder **seulement si** le push existe au lancement (il exige le plan Blaze, non activé). Sinon supprimer ces passages. Si le push existe : Firebase Cloud Messaging et APNs deviennent des prestataires à citer. |
| `[À VÉRIFIER ...]` (politique art. 7 et 11) | rétention Analytics, durée Crashlytics, absence d'identifiant publicitaire | Agents dev / PM : vérifier dans la console et dans le code (surtout Android), puis retirer les crochets en corrigeant le texte si la réalité diffère. |

Le prix du Premium n'est volontairement **pas** écrit dans les CGU (il est affiché par le store avant l'achat).

**Contrôle avant publication** : plus aucun `[` ne doit rester dans les quatre textes, hors liens Markdown. Puis accord explicite du PM.

## 4. Risques juridiques connus, assumés par le PM (décision du 2026-10-23, pas de relecture juridique prévue)

Rappel : **les textes sont rédigés par l'équipe d'agents et publiés sous la seule responsabilité du PM.** Ils appliquent partout la formulation la plus prudente et la plus honnête possible, mais ne remplacent pas un avis juridique. Si le projet grandit (beaucoup d'utilisateurs, mineurs, première réclamation, rejet par un store), la relecture juridique devient la première dépense à envisager. Les risques J1 à J8 sont ceux à surveiller en priorité dès la bêta externe.

| # | Risque connu | Conséquence possible | Mitigation actuelle |
|---|---|---|---|
| J1 | **Identité de l'éditeur et mentions légales** : l'éditeur est une personne physique dont l'adresse postale et l'e-mail seront publics ; les mentions légales complètes (hébergeur, directeur de publication) ne sont pas rédigées. | Non-conformité LCEN ; exposition de coordonnées personnelles. | Nom, e-mail et adresse renseignés dans les textes ; remplaçables en une opération (§3) ; recourir à une domiciliation si le PM ne veut pas publier son adresse. |
| J2 | **Mineurs, pas d'âge minimum** (RGPD art. 8, 15 ans en France ; aucun contrôle d'âge). | Traitement de données de mineurs sans accord parental valable ; plainte CNIL ; questions des stores (classification d'âge, règles sur les mineurs, guideline Apple 5.1.4 si des enfants sont visés). | Le texte dit que Miff ne vérifie pas l'âge, recommande l'accord des parents, prévoit la suppression à la demande d'un parent ; consentement individuel à la localisation. Répondre honnêtement aux questionnaires de classification des stores. |
| J3 | **Bases légales** (consentement, contrat, intérêt légitime), notamment batterie et état de la permission, preuves de consentement. | Contestation de la base légale. | Bases issues de `data-model.md` §11, appliquées avec prudence (consentement quand il y a un doute). |
| J4 | **Pas d'analyse d'impact (AIPD)** alors que localisation + mineurs possibles + relations familiales sont des critères. | Manquement reproché par la CNIL. | Minimisation (aucune position brute, pas d'historique) ; l'AIPD reste à faire par le PM si le projet grandit. |
| J5 | **Transferts hors UE** : Firebase Authentication tourne depuis les États-Unis (doc Google du 2026-10-23) ; Crashlytics, App Check, Analytics : localisation non précisée. Le DPA Firebase et le mécanisme de transfert n'ont pas été vérifiés dans la console. | Transfert jugé insuffisamment encadré. | Texte prudent (« peuvent », Data Privacy Framework « auquel Google indique adhérer »). Action simple pour le PM : vérifier dans la console Firebase que les conditions de traitement des données sont bien acceptées. |
| J6 | **Textes de consentement** (localisation en arrière-plan, Analytics, Crashlytics) non revus ; cohérence avec la politique. | Consentement jugé non valable ; rejet par Apple/Google (information visible dans l'app exigée avant la permission). | Consentement explicite, par Miff, daté et versionné ; Analytics/Crashlytics désactivés par défaut. |
| J7 | **Durées de conservation** : pas de politique pour les comptes inactifs ; preuve de consentement supprimée au départ de la Miff ; e-mails de contact. | Conservation excessive ou preuve manquante en cas de litige. | Le texte dit la vérité (pas de suppression automatique des comptes inactifs). |
| J8 | **Photos « 24 h »** : illisibles après 24 h, mais suppression physique non garantie sur le plan gratuit (TTL probablement payant). | Écart entre promesse et réalité. | Le texte dit « supprimée dès que possible » et précise que la suppression exactement à 24 h n'est pas garantie. |
| J9 | **Données des survivants d'une Miff supprimée** : illisibles tout de suite, effacées physiquement au prochain lancement de leur app (décision PM 2026-10-22). | Rétention résiduelle. | Décrit tel quel dans la politique art. 7. |
| J10 | **Suppression de compte** : suppression en plusieurs étapes côté app ; si l'app plante, des données peuvent rester ; `entitlements` purgé à la main. | Effacement incomplet. | Décrit honnêtement ; une fonction de nettoyage exigerait Blaze (non activé). |
| J11 | **Clause de responsabilité** (CGU art. 11) et avertissement « pas un service d'urgence », dans un contexte d'inquiétude pour un proche. | Clause jugée abusive ou insuffisante ; mise en cause en cas d'incident. | Avertissement en tête, rappelé aux articles 6 et 10 ; limitations « dans les limites permises par la loi », sans exclure les cas où la loi l'interdit. |
| J12 | **Abonnements** : informations précontractuelles, droit de rétractation de 14 jours, rôle d'Apple/Google, essai gratuit (7 j vu dans la maquette, non décidé), changement de prix. | Réclamation de consommateur. | Achat et résiliation gérés par les stores ; les CGU renvoient aux règles du store et réservent les droits légaux du consommateur. |
| J13 | **Médiateur de la consommation**, tribunaux compétents, clause « la version française prévaut ». | Manquement au droit de la consommation. | Champ `[À COMPLÉTER SI APPLICABLE]` ; adhésion à un médiateur à faire par le PM. |
| J14 | **Relation entre membres d'une Miff** : chacun partage des données sur les autres (noms, photos, lieux). Le créateur définit les lieux vus par tous. | Litige entre membres ; responsabilité de l'éditeur discutée. | Règles d'usage (art. 6 à 8), consentement individuel, interdiction de surveiller à l'insu. |
| J15 | **Accès interne** de l'équipe aux données (console Firebase). | Promesse non tenue. | Formulation limitée aux « personnes habilitées et à ce qui est nécessaire » ; le PM doit protéger son compte Google (double authentification). |
| J16 | **Signalement de contenus** et statut d'hébergeur (LCEN, règlement sur les services numériques). | Responsabilité sur un contenu illicite. | Possibilité de signaler par e-mail et de supprimer un contenu. |
| J17 | **Droit d'accès / portabilité** : export manuel par e-mail pendant la bêta, sans procédure formalisée (format, vérification d'identité). | Réponse tardive ou incomplète. | Délai d'un mois annoncé ; le PM doit consulter l'adresse régulièrement. |
| J18 | **Marque « Miff »** : aucune vérification d'antériorité. | Litige de propriété intellectuelle, retrait de l'app. | Aucune ; vérification gratuite possible dans les bases de l'INPI et des stores avant le lancement public. |

## 5. Cohérence avec les stores (à vérifier avant toute soumission)

Les textes, les formulaires des stores et l'app doivent dire **la même chose** : un écart est un motif de rejet. Vérifié sur les sources officielles le 2026-10-23 :
- **Apple** (App Review Guidelines) : 5.1.1(i) lien de politique dans App Store Connect **et** dans l'app, qui explique collecte, usages, durées de conservation et comment retirer son consentement ; 5.1.1(v) la **suppression de compte doit être possible dans l'app** (un simple e-mail de demande ne suffit pas ; l'export sur demande par e-mail, lui, est compatible) ; 5.1.2 ne pas imposer la localisation pour utiliser l'app (c'est le cas : repli sur les mises à jour manuelles) ; 5.1.5 localisation seulement si directement liée au service, avec information et consentement avant collecte. Remplir aussi les « privacy nutrition labels » (App Privacy) et le texte d'usage de la localisation « Toujours ».
- **Google Play** : politique de confidentialité dans l'app **et** sur la fiche ; localisation en arrière-plan = formulaire de déclaration, vidéo de démonstration et **information bien visible dans l'app avant la demande de permission** (elle ne peut pas se limiter à la politique) ; section Sécurité des données (Data safety) ; chemin de suppression dans l'app **et** lien web de demande de suppression.
- Ces règles évoluent : `release-manager` les revérifie avant la soumission.

## 6. Ce qui reste à faire par chacun

- **PM (actions humaines)** : décider Q18 et Q3 (§3) ; adhérer à un médiateur de la consommation si applicable ; vérifier dans la console Firebase que les conditions de traitement des données sont acceptées et protéger son compte Google ; donner l'accord explicite de publication (déploiement Firebase Hosting). Aucun document légal n'est signé ni accepté par un agent.
- **Agents dev** : liens vers les textes dans l'app, enregistrement de `termsVersion`/`consentVersion`, suppression de compte dans l'app, interrupteurs Analytics/Crashlytics, vérifier l'absence d'identifiant publicitaire (surtout Android).
- **`release-manager`** : intégrer les URL dans les checklists et fiches store une fois les champs remplis et le PM d'accord ; ne rien déclarer « prêt à soumettre » avant la checklist QA (`docs/qa/release-checklist.md`).
- **`aso-marketing`** : s'assurer que la fiche store ne promet rien que ces textes ne disent pas (pas de « sécurité garantie », pas de « suivi en temps réel »).
