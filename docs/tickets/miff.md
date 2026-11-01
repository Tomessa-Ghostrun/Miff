# Ticket — Miff : créer, rejoindre et gérer son cercle (et frontière avec le Feed)

**Statut** : brouillon issu de l'étape « challenge », **mis à jour avec les décisions du PM du 2026-10-24** (`docs/tickets/miff-questions-pm.md`, §15 ci-dessous) ; reste à valider avant tout développement (aucun développement lancé). Les deux points restés à confirmer ont été tranchés le 2026-10-24 (§15, « Précisions du PM »).
**Rédigé le** : 2026-10-24 (agent `product-specs`, étape « challenge » du process par ticket) ; mis à jour le 2026-10-24 après les réponses du PM aux 15 questions (les codes QM1 à QM15 renvoient à ces décisions).
**Fusionne** : les tickets Notion « Miff » (statut Notion : « Spec en attente de review IA ») et « Miff — créer / rejoindre / gérer un groupe » (statut « Backlog discovery », miroir de `docs/specs/miff-groupes.md`, qui devient redondant : à clore ou à lier par le PM une fois ce ticket validé). Le ticket « Feed » (Notion, « Spec en attente de review IA ») et son miroir `docs/specs/feed-cercle-de-confiance.md` sont **traités comme un ticket distinct** (§7, décision QM10) ; seuls leurs constats sont consignés ici. Des pages Notion « MIFF » (parent et sous-tickets) et « FEED » sont créées à part ; aucun autre ticket Notion n'est modifié.
**Sources lues** : Notion « Miff » (racine du projet : pitch et règles métier), « Miff » (tâche), « Miff — créer / rejoindre / gérer un groupe », « Feed », « Feed — cercle de confiance » ; en contexte : « Onboarding » (écrans création et saisie du code), « Premium », « Paywall / Premium », « Paramètre » ; `CLAUDE.md` ; `docs/tickets/authentification.md` (§5, §7.6, §7.7, §15 AUTH-5, §17 ; **non remis en cause** : suppression du compte d'un créateur = ses Miffs supprimées) ; `docs/tickets/durcissement-regles-firestore.md` (§3 à §6, §10, §15) ; `docs/tickets/initialisation-technique.md` (§3, §9, §10) ; `backend/data-model.md` (§1 à §10, §12 à §15) ; `backend/firestore.rules` ; `design/design-system.md` ; `docs/contracts/analytics-events.md` et `i18n-keys.md` ; `docs/specs/*.md`.
**Figma (lecture seule)** : « Miff - Claude » (`RbX84qKbxppvAy5iypLYP2`) : écrans 11 « Choisissez comment commencer » (`37:21`) et 12 « Nouvelle Miff » (`40:32`) consultés (seule la page Onboarding y est reconstruite : **les pages Miff, Feed et Pop ins n'y existent pas encore**). Fichier original « Miff - App » (`Byi9NgQxkBPbmbhU403HSf`), consulté en lecture seule faute de mieux : page « Miff » (`13:1948` : écran d'une Miff existante `35:3367`, confirmation de suppression `69:600`, saisie du code `117:86` et son erreur `117:295`) et page « Pop ins » (`69:12`). Rien n'a été modifié.
**Limite de lecture** : les images insérées dans les pages Notion (maquettes embarquées) ne sont pas lisibles par l'outil Notion ; les écrans viennent donc de Figma.
**Convention** : « vérifié » = confirmé sur une source du dépôt ou de la documentation officielle. « À vérifier » = non confirmé. Aucune commande Firebase n'a été exécutée, aucun fichier de `backend/` n'a été modifié.

---

## 1. Résumé pour le PM

En une phrase : permettre à quelqu'un de **créer son cercle en quelques secondes, d'y faire entrer ses proches avec un code qui se partage d'un geste, de le faire vivre (retirer, quitter, supprimer) sans jamais mettre quelqu'un mal à l'aise**, et de **savoir exactement ce qu'il partage au moment où il entre** : dans Miff, entrer dans un cercle, c'est partager son statut avec lui, et le texte le dit avant le geste.

Le backend est prêt (règles DUR-1 et DUR-2 déployées, selon le PM) : ce ticket est **100 % côté apps** (Android puis iOS). Il ne demande ni Cloud Function, ni Blaze : **0 €**.

Ce que le challenge change par rapport aux tickets Notion (détails en §4) :

| # | Constat | Conséquence |
|---|---|---|
| 1 | **Le créateur ne peut pas quitter sa Miff sans la supprimer pour tous** (règles : le créateur ne part qu'en supprimant la Miff ; propriétaire immuable ; pas de transfert). Les maquettes n'ont ni « Quitter » ni écran de membre simple. | **Décision QM1** : en v1, une seule sortie pour le créateur : « Supprimer la Miff », avec un texte clair. Pas de transfert. |
| 2 | **Rejoindre une Miff vaut partager son statut** (décision PM, QM2, **contre ma recommandation d'un consentement séparé**). Le modèle de données sépare pourtant les deux, et un nouvel arrivant ne peut pas lire les lieux avant d'entrer (règles). | Le partage reste un **consentement explicite** : texte clair **avant** le geste, bouton libellé « Rejoindre et partager », **un seul lot atomique** (adhésion + consentement + statut initial), arrêt possible à tout moment depuis la Miff ; la permission de localisation du système est demandée à part et ne bloque jamais (§5.4, §5.13). Écart avec ma recommandation et attention aux règles anti-stalkerware des stores : **risque assumé** (§14, R1). |
| 3 | **Le code peut fuiter sans que personne ne le sache** : tout membre le voit et le partage, aucune validation à l'arrivée. | **Décision QM3** : tous les membres voient et partagent le code, aucune validation des arrivées en v1 ; le retrait d'un proche renouvelle automatiquement le code (déjà décidé), et le créateur peut le renouveler à la main. |
| 4 | **Messages d'accès perdu** : le ticket durcissement prévoit deux messages. En réalité, quand un créateur supprime sa Miff, les autres membres sont **retirés un par un avant**. | **Décision QM8** : un seul message neutre : « Tu n'as plus accès à la Miff « X ». » |
| 5 | **Le nom** : Notion dit 2 à 50 caractères ; les règles (déjà déployées) acceptent 1 à 40. | **Décision QM6** : 2 à 40 (la spec Notion 2–50 est alignée sur 2–40 ; compatible avec les règles). |
| 6 | **Le code s'affiche avant l'enregistrement** dans Figma, alors que Notion dit qu'il devient actif à l'enregistrement. | **Décision QM15** : création en deux temps (nom, puis « Ta Miff est prête » avec le code). |
| 7 | **Hors connexion** : une écriture Firestore faite sans réseau est mise en file d'attente et rejouée plus tard sans que l'utilisateur le sache. | Créer, rejoindre, retirer, quitter, renouveler et supprimer exigent l'accusé de réception du serveur ; sinon message neutre et rien en attente (§5.11). |
| 8 | **Le Feed contredit en partie le modèle de confidentialité** : « Localiser » (dernière position : **aucune coordonnée n'est stockée**), « Message » et « Appeler » (aucun numéro dans le schéma). | **Décision QM11** : le PM **garde** ces trois boutons dans la v1 du Feed ; le téléphone est obligatoire à l'onboarding. « Localiser » contredit toujours le modèle : **le ticket Feed devra définir précisément ce qu'il fait sans coordonnées stockées** (question ouverte, non inventée ici, §7). |
| 9 | **Le nombre de membres ne peut pas être plafonné par les règles** (pas de compteur ; le candidat ne lit pas la liste avant d'entrer). | **Décision QM5** : 15 membres maximum, **plafond doux dans l'app** ; aucune règle serveur ne l'impose (§5.8). |
| 10 | **Fin d'abonnement Premium** : les Miffs « en trop » **créées** par l'utilisateur seront supprimées (décision du 2026-10-24) : tous leurs membres perdent leur cercle. | Rien à implémenter ici, mais « Retirer » et « Supprimer » doivent être **appelables sans écran** par le ticket Premium (§13) ; risque noté (§14, R9). **Limites gratuites et paywall : renvoyés au ticket Premium (QM7).** |

**Taille globale (relative, sans durée)** : L à XL (Android : 2 PR, iOS : 2 PR, §13). **Coût : 0 €.**

---

## 2. Périmètre

### Dans le périmètre
- Écran de choix « Créer une nouvelle Miff / Rejoindre une Miff existante » (destination des boutons de l'onboarding et de l'état vide du Feed ; les boutons eux-mêmes appartiennent à ces tickets).
- **Créer** une Miff en deux temps : nom, puis écran « Ta Miff est prête » avec code et partage (QM15).
- **Rejoindre** par code : saisie, vérifications, **écran de confirmation avec le texte de partage du statut et le bouton « Rejoindre et partager »** (QM2), entrée en un lot atomique.
- **Écran de la Miff** (créateur et membre) : nom, code, partage, liste des proches (nom, photo, rôle), **ligne « Partager mon statut » (arrêter ou reprendre à tout moment)**, section lieux (liste ; création et édition : ticket Lieux), actions selon le rôle. Points d'entrée : pastille de Miff du Feed et « Mes Miffs » dans Réglages (QM13).
- **Renouveler le code** (manuel), invitation discrète à le renouveler après un départ (décision PM du 2026-10-22).
- **Retirer un proche** (créateur), **quitter** (membre), **supprimer la Miff** (créateur, procédure reprenable), **bouton facultatif « Prévenir mes proches »** avant la suppression (QM9).
- **Accès perdu** : détection, message unique, nettoyage (arrêt des écoutes et des lieux surveillés, purge du cache).
- Plafond doux de 15 membres, états vides, hors connexion, textes FR + EN au ton bienveillant, accessibilité.
- Procédures « Retirer » et « Supprimer » conçues pour être **réutilisées sans interface** (suppression de compte AUTH-5, fin d'abonnement Premium).

### Hors périmètre
- **Limites gratuites et paywall** (blocage d'une deuxième Miff, déclencheurs, écrans, textes, achat) : **ticket Premium / Paywall** (décision QM7 : « plus tard »). Dépendance marquée en §6 et §17. Les règles de sécurité déployées appliquent déjà la limite côté serveur.
- **Le Feed** et tout affichage de statuts, batterie, photos d'update, bulle de la Miff, sélecteur de Miff, boutons Localiser / Message / Appeler : ticket Feed distinct (§7).
- **Demande de la permission de localisation du système**, géofencing, écriture des statuts après l'entrée : tickets Localisation / Statut.
- Création, modification et suppression des **lieux** : ticket Lieux de confiance.
- **Profil** (prénom, nom, photo, téléphone) : ticket Onboarding ; **Réglages** : ticket Réglages ; **suppression de compte** : AUTH-5 (qui réutilise nos procédures).
- Notifications push (exigent Blaze) : aucun membre n'est averti « en direct » d'un retrait, d'un départ ou d'une suppression.
- Transfert de propriété, plusieurs créateurs, rôles intermédiaires, validation d'une arrivée par le créateur, étiquette ou message « nouveau membre », liste de personnes bannies, invitation par e-mail ou SMS, lien d'invitation qui ouvre l'app, photo ou description de la Miff, historique des membres, expiration du code, plafond garanti du nombre de membres.
- Nouveaux événements Analytics (aucun n'est ajouté, §5.12).

---

## 3. Parcours et états d'écran

### 3.1 Parcours nominaux

```
Écran de choix (onboarding, ou état vide du Feed)
  ├─ « Créer une nouvelle Miff » ─► Nouvelle Miff : nom + texte de partage + « Créer et partager » (extension de QM2 à la création, confirmée par le PM)
  │        └─► Lot unique (Miff + code + fiche + index + statut initial) ─► « Ta Miff est prête » : code + Partager / Copier ─► Feed
  └─ « Rejoindre une Miff existante » ─► Saisie du code ─► [déjà membre ?]
           ─► Confirmation « Rejoindre la Miff « X » ? » + texte de partage + « Rejoindre et partager »
           ─► Lot unique (fiche + index + statut initial) ─► Feed
  (la permission de localisation du système est demandée ensuite, par le ticket Localisation, et ne bloque jamais l'entrée)

Écran de la Miff (créateur) : nom (modifiable) · code + Partager/Copier · Renouveler le code · proches (« ⋯ » ► Retirer) · Partager mon statut (arrêter/reprendre) · lieux · Supprimer la Miff
Écran de la Miff (membre)   : nom · code + Partager/Copier · proches · Partager mon statut (arrêter/reprendre) · lieux · Quitter la Miff

Suppression : confirmation (+ « Prévenir mes proches », facultatif) ─► retraits un par un ─► lieux ─► lot final

Accès perdu (à l'ouverture, ou en direct si l'app est ouverte) ─► « Tu n'as plus accès à la Miff « X » » ─► nettoyage ─► autre Miff ou état vide du Feed
```

### 3.2 Écrans et états à couvrir

| Écran | États à couvrir | Dans les maquettes ? |
|---|---|---|
| **A. Choix créer / rejoindre** | normal ; hors connexion (le choix reste possible, l'action échouera proprement) | Oui (écran 11 Figma Claude). Le champ de code est **intégré à la carte** « Rejoindre » ; l'original a aussi une page « Rejoindre une Miff » dédiée : **deux versions du même écran** (§4 #9). |
| **B. Nouvelle Miff** | vide (bouton grisé) ; 1 caractère (grisé) ; 2 à 40 caractères (actif) ; 40 atteints ; enregistrement en cours ; hors connexion ; erreur ; texte de partage visible avant le bouton | Vide : oui (écran 12 : nom, code, proches, lieux, deux boutons de validation). Erreur, chargement, hors connexion, texte de partage : non. **Le code et le partage y apparaissent avant l'enregistrement** : à retirer (QM15). |
| **C. « Ta Miff est prête »** (nouveau) | code par groupes de 4 ; partage ouvert ; code copié | Non. À dessiner. |
| **D. Saisie du code** | vide ; saisie partielle ; 12 caractères valides (bouton actif) ; caractère hors alphabet (message local) ; vérification en cours ; code introuvable ; **pause après 5 échecs** ; hors connexion ; erreur réseau ou quota ; déjà membre | Vide et erreur « Le code saisit est invalide » (original, faute de frappe) : oui. Le reste : non. |
| **E. Confirmation de rejoindre** (nouveau) | nom de la Miff ; **texte de partage du statut** ; bouton « Rejoindre et partager » ; en cours ; succès ; refus (code renouvelé entre-temps) ; hors connexion | Non. À dessiner. |
| **F. Écran de la Miff, créateur** | nom modifiable (Enregistrer actif seulement si modifié et valide) ; code ; liste des proches (0, 1, N) ; ligne « Partager mon statut » (actif, arrêté) ; section lieux (aucun, N) ; plafond atteint ; renouvellement en cours ; hors connexion (lecture seule) | Oui, dans l'original (`35:3367`, `35:3565`, `69:600`) avec **deux défauts** : le titre reste « Nouvelle Miff » ; chaque proche a une **corbeille rouge** sans confirmation dessinée. La ligne « Partager mon statut » : non. |
| **G. Écran de la Miff, membre** (nouveau) | mêmes sections en lecture seule ; « Partager mon statut » ; « Quitter la Miff » ; pas d'Enregistrer ni de corbeille | **Absent** (ni Figma, ni Notion). |
| **H. Retirer un proche** | confirmation (prénom) ; en cours ; succès avec **nouveau code** et partage ; cible déjà partie ; erreur ; hors connexion | Absent. |
| **I. Quitter la Miff** | confirmation ; en cours ; succès ; hors connexion | **Absent** : la spec « Backlog » parle d'un écran de confirmation vu dans les maquettes, mais **seule la suppression de Miff en a un** (`69:600`). |
| **J. Supprimer la Miff** | confirmation (nombre de proches) avec option « Prévenir mes proches » ; progression ; interrompue puis reprise ; terminée ; hors connexion | Confirmation : oui (« Etes-vous sûr de supprimer cette miff ? … de manière irrévocable » : vouvoiement et mot anxiogène). Le reste : non. |
| **K. Renouveler le code** | confirmation ; succès (nouveau code) ; invitation discrète après un départ ; hors connexion | Absent. |
| **L. Accès perdu** | message unique ; bascule vers une autre Miff ou vers l'état vide | Absent. |
| **M. Arrêter / reprendre le partage** | confirmation d'arrêt ; texte de reprise (le même que l'entrée) ; en cours ; hors connexion | Absent. |

### 3.3 Écrans à dessiner ou à corriger (`design-system`, avant le développement)

C, E (avec le texte de partage et le bouton « Rejoindre et partager »), G, H, I, K, L, M (boîtes de dialogue : un modèle unique de dialogue de confirmation, ton calme), texte de partage sur B, états d'erreur et de chargement de B et D, ligne de proche avec menu « ⋯ » à la place de la corbeille rouge, étiquettes « Créateur » et « Toi », ligne « Partager mon statut » (interrupteur), champ de code par groupes de 4, titre de l'écran F au nom de la Miff. Composants à extraire (absents de `design/design-system.md` §4) : champ de saisie, ligne de proche, dialogue de confirmation, carte de choix, pastille de rôle, interrupteur de ligne.

---

## 4. Ambiguïtés, contradictions et incohérences relevées

| # | Point | Sources en conflit | Traitement |
|---|---|---|---|
| 1 | **Nom** : 2 à 50 caractères (Notion) contre 1 à 40 (règles déployées). | Notion vs `firestore.rules` | **Décidé (QM6)** : 2 à 40. Le minimum de 2 est une règle d'écran. |
| 2 | **Messages d'accès perdu** : deux messages au ticket durcissement §6.4 ; en pratique la suppression retire d'abord chaque membre. | Durcissement §4.1 et §6.4 | **Décidé (QM8)** : message unique. |
| 3 | **Deux boutons de validation** sur l'écran de création. | Figma 12, Notion Onboarding | Un seul bouton principal. À arbitrer par `design-system`. |
| 4 | **Code visible avant l'enregistrement** (Figma) contre « actif à l'enregistrement » (Notion). | Figma 12 vs Notion Miff | **Décidé (QM15)** : deux temps. |
| 5 | **Titre « Nouvelle Miff » conservé** sur l'écran d'une Miff existante. | Figma original | Titre = nom de la Miff. |
| 6 | **« Ajouter/retirer des membres »** : on ne peut pas ajouter quelqu'un ; on **partage un code**. | Spec miff-groupes, Notion | « Ajouter un proche » (libellé du PM, conservé) ouvre le partage du code. |
| 7 | **« Quitter »** et **écran du membre simple** : cités par la spec, absents partout. | Spec Backlog vs Figma | Écrans G et I à dessiner. |
| 8 | **Corbeille rouge** sur chaque proche, sans confirmation. | Figma original | Menu « ⋯ » puis dialogue de confirmation. |
| 9 | **Saisie du code : deux versions** (carte dans l'onboarding avec « Suivant » ; page dédiée avec « Rejoindre », erreur « Le code saisit est invalide »). | Notion Onboarding vs Figma original | Un seul composant de saisie ; texte d'erreur adouci. |
| 10 | **Entrée de l'écran de gestion non définie**. | Notion Miff, Feed, Paramètre | **Décidé (QM13)** : pastille de Miff du Feed + « Mes Miffs » dans Réglages. |
| 11 | **Créateur de plusieurs Miffs** : phrase inachevée dans la page racine. | Notion racine | Sans conséquence (premium par utilisateur). |
| 12 | **« Ajouter un proche » dans le Feed pour tous** : le code est visible de tous les membres. | Notion Feed, `data-model.md` Q7 | **Décidé (QM3)**. |
| 13 | **Lien de téléchargement** du message de partage : l'app n'est pas publiée. | Notion Feed | **Décidé (QM14)** : page statique gratuite du site d'AUTH-W. |
| 14 | **Section « Lieux de confiance » dès la création** (Figma 12), alors que les lieux exigent que la Miff existe. | Figma 12 | La section liste les lieux et renvoie au ticket Lieux ; elle n'écrit rien. |
| 15 | **En-tête de `firestore.rules`** : « JAMAIS déployées » ; `data-model.md` et `backend/README.md` : DUR-1 et DUR-2 déployées (information du PM, à confirmer dans la console). | `firestore.rules` vs `data-model.md` | À faire corriger par `backend-data` (commentaire seulement) ; le PM confirme la date de publication. |
| 16 | **Spec locale `miff-groupes.md`** : ses 4 questions ouvertes ont maintenant une réponse (retrait : créateur seul ; historique : aucun ; transfert : non ; régénération : oui). | Spec vs décisions | À mettre à jour ou à clore par le PM après validation. |
| 17 | **Hors ticket** : Notion Premium cite un paywall pour qui veut créer un lieu « alors qu'il en a déjà 2 » alors que le gratuit est limité à **1** lieu. | Notion Premium vs reste | À reporter au ticket Premium / Lieux. |
| 18 | **Permission de localisation** : Notion Onboarding impose « Toujours autoriser » pour utiliser l'app ; pour **ce** ticket, le PM veut qu'elle soit demandée à part et ne bloque jamais l'entrée dans une Miff. | Notion Onboarding vs décision QM2 | À trancher dans les tickets Onboarding / Localisation (obligation d'usage de l'app) ; ici, rejoindre ne dépend jamais de la permission. |
| 19 | **Téléphone obligatoire à l'onboarding** (décision PM, QM11) alors que le schéma n'a **aucun champ téléphone** et que le ticket AUTH recommandait de le retirer de la v1. **Visibilité tranchée par le PM (2026-10-24, proposition A) : le numéro est visible des membres des Miffs de l'utilisateur, et d'eux seuls.** | Notion Onboarding, décision PM vs `data-model.md` §2.1 | À traiter par les tickets Onboarding et Feed avec `backend-data` (champ, règles, mise en œuvre). Conséquence pour ce ticket : le texte de confirmation (§5.10), qui dit aujourd'hui que les membres verront prénom et photo, devra aussi citer le numéro de téléphone (formulation à valider par le PM dans le ticket Onboarding / Feed). Hors du reste de ce ticket. |

---

## 5. Règles métier

### 5.1 Rôles et droits

| Action | Créateur | Membre |
|---|---|---|
| Voir nom, code, liste des proches, lieux | Oui | Oui |
| Partager / copier le code | Oui | Oui (QM3) |
| Arrêter / reprendre **son propre** partage | Oui | Oui |
| Renommer la Miff | Oui | Non |
| Renouveler le code | Oui | Non |
| Retirer un proche | Oui | Non |
| Créer, modifier, supprimer un lieu | Oui (ticket Lieux) | Non |
| Quitter la Miff | **Non** : il ne peut que la supprimer (QM1) | Oui |
| Supprimer la Miff | Oui | Non |
| Lire les statuts des autres | Seulement s'il partage et a publié son propre statut (réciprocité, **comme tout membre**) | Idem |

Le créateur n'a **aucun droit de lecture supplémentaire** sur la position ou le statut d'un membre : il gagne la maîtrise du cercle (qui y est, quels lieux), jamais la surveillance (§5.9).

### 5.2 Nom
- **2 à 40 caractères** après suppression des espaces au début et à la fin ; aucun caractère de contrôle ; emojis acceptés. **À vérifier à l'émulateur** : la façon dont les règles comptent la longueur d'un texte avec emojis ou caractères composés ; en cas de doute, le client limite par prudence (unités UTF-16).
- Deux Miffs peuvent porter le même nom. Le nom est visible de **toute personne qui détient le code** : le dire sous le champ.
- Le renommage met à jour la Miff **et** le nom conservé avec le code dans le même lot.

### 5.3 Code secret
- **Format** : 12 caractères parmi `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 symboles) ; généré **par le téléphone**, générateur cryptographique, **à l'enregistrement** (pas avant, QM15). Test unitaire identique sur les deux plateformes.
- **Affichage** : par groupes de 4 (« XTZR SP36 7988 »), police à chasse fixe ; le lecteur d'écran l'annonce par groupes. Visible de **tous les membres** (QM3) ; jamais dans les journaux, les événements Analytics ni les rapports de plantage.
- **Saisie** : majuscules automatiques ; pas de correction ni de suggestions ; espaces, tirets et minuscules normalisés ; bouton actif à 12 caractères valides ; un caractère hors alphabet donne un message **local** (aucune lecture réseau). Pas de lecture automatique du presse-papiers : un bouton « Coller ».
- **Partage** : feuille de partage native avec nom de la Miff, code et lien (QM14) ; « Copier » copie le code seul (Android 13 et plus : contenu marqué sensible). Aucun autre champ.
- **Renouvellement** : (a) **automatique à chaque retrait** (imposé par les règles, décision du 2026-10-22) ; (b) **manuel**, à la demande du créateur ; (c) **invitation discrète** au créateur après un départ volontaire (décision PM du 2026-10-22, Q1 : une seule fois par départ, sans insister). L'ancien code cesse de fonctionner immédiatement ; les membres actuels restent et voient le nouveau code en direct.
- **Durée de vie** : **le code n'expire pas** (QM4). Un code libéré peut en théorie être réutilisé par n'importe qui pour sa propre Miff ; probabilité négligeable.
- **Essais** : au bout de 5 échecs consécutifs, pause de 60 secondes (message bienveillant) ; compteur remis à zéro après un succès ou 10 minutes. **Aide d'interface, pas une protection** ; la protection est l'entropie du code et App Check.
- **Aucune validation des arrivées** (QM3) : tout porteur du code entre. Risque connu (§14, R2).

### 5.4 Rejoindre : ordre des vérifications et consentement explicite (QM2)
1. **Profil présent** (routage `authentification.md` §5) : sinon retour à l'étape profil. **Le profil est exigé avant de créer ou de rejoindre.**
2. **Format** du code (local).
3. **Lecture du code** (1 lecture) : « introuvable » = un seul message, quelle que soit la cause. On ne dit jamais à une personne retirée que son ancien code a été remplacé.
4. **Nettoyage d'un index obsolète** : si un index d'appartenance pointe vers une Miff où la personne n'a plus accès (retirée, Miff supprimée), le nettoyer **avant** d'écrire (sinon l'écriture serait refusée à tort, `data-model.md` §6 (b)).
5. **Déjà membre de cette Miff** (comparaison avec les index par leur champ `miffId`, jamais par l'identifiant du document) : message et bouton vers la Miff, aucune écriture.
6. **Écran de confirmation avec le texte de partage, AVANT l'action.** Il affiche le nom de la Miff et, juste au-dessus du bouton, le texte : « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » (version complète au §5.10), suivi d'un rappel que la **permission de localisation du système** sera demandée séparément et que la refuser n'empêche pas de rejoindre. Le bouton est libellé « **Rejoindre et partager** » ; « Annuler » ferme l'écran sans rien écrire. Le texte est visible sans action de la personne (ni dissimulé, ni replié, ni accessible seulement par défilement).
7. **Un seul lot atomique** (tout passe ou rien) : (a) fiche de membre (rôle `member`, avec le code utilisé), avec **partage activé** (`consentedAt` = heure serveur, `consentVersion` = version du texte affiché, `revokedAt` vide) ; (b) index d'appartenance ; (c) **statut initial** `status/{moi}` (`presence = "unknown"`, `locationAccess` = permission réelle de l'appareil, `lastUpdateAt` = maintenant). Succès affiché **après l'accusé du serveur**.
8. **La permission de localisation du système reste demandée à part** (ticket Localisation) et **ne bloque jamais** l'entrée : refusée ou limitée, la personne est dans la Miff, son statut indique `locationAccess` (« localisation limitée », jamais alarmiste).
9. **Arrêter à tout moment** : la ligne « Partager mon statut » de l'écran de la Miff (§5.13).

### 5.5 Limites freemium (hors périmètre : ticket Premium, QM7)
Le blocage d'une deuxième Miff pour un compte gratuit, ses déclencheurs, l'écran de limite, le paywall et ses textes **ne sont pas spécifiés ici** : ils relèvent du ticket Premium / Paywall (dépendance marquée en §6 et §17). Ce qui reste vrai techniquement : les règles déployées appliquent déjà la limite (un seul index gratuit ; clés par identifiant de Miff réservées au premium) et **refusent** une deuxième appartenance d'un compte gratuit ; le client lit l'abonnement pour choisir la clé de l'index (`data-model.md` §8) et **lit les index par leur champ `miffId`**. Utile ici : un « Quitter » ou une suppression libère immédiatement l'index (même lot). Le comportement visible d'un compte gratuit qui tente une deuxième Miff **avant** la livraison du ticket Premium est à fixer dans ce ticket-là (aucun comportement inventé ici).

### 5.6 Retirer, quitter, supprimer
**Retirer un proche (créateur).** Confirmation nominative ; un membre par lot de 6 écritures dont **le renouvellement obligatoire du code** (ne **rien ajouter** à ce lot : marge de lectures de règles de 2 sur 20, `data-model.md` §12) ; ensuite, le nouveau code est affiché avec le partage. L'index de la personne retirée n'est pas touché (elle le nettoie à son prochain accès). Aucune notification.

**Quitter (membre).** Confirmation ; lot de 4 suppressions (fiche, statut, photo, index) ; arrêt de la surveillance des lieux de cette Miff ; purge du cache local (même contrat que la perte d'accès) ; retour au Feed. Le créateur n'est pas notifié (invitation de §5.3).

**Supprimer (créateur).** Confirmation avec le nombre de proches. Procédure **reprenable** et **idempotente**, dans cet ordre : (1) retrait de chaque autre membre, un lot chacun (le code change à chaque fois) ; (2) suppression des lieux, 5 au plus par lot ; (3) lot final : Miff + **pierre tombale** + code + son propre statut, photo, fiche et index. Un marqueur local « suppression en cours » (identifiant de la Miff seulement) permet de **reprendre au lancement suivant**. Interruption réseau : pause puis reprise ; aucune opération n'est lancée hors connexion.

**« Prévenir mes proches » (QM9, facultatif).** Sur l'écran de confirmation, un bouton secondaire propose de **préparer un message** dans la feuille de partage native (par exemple « Je ferme notre Miff « X » dans Miff : elle ne sera plus accessible. »). **Ce que cela fait réellement, sans serveur ni notifications** : l'app **propose** seulement le texte ; c'est le créateur qui choisit ses destinataires et envoie lui-même depuis sa messagerie ; l'app ne sait pas qui l'a reçu, n'envoie rien toute seule, et ce message ne contient ni code ni identifiant. Les proches non prévenus l'apprennent à l'ouverture de leur app (message d'accès perdu). Faisable sur Android et iOS avec la feuille de partage native ; aucune notification n'est possible tant que Blaze n'est pas décidé.

**Ce que voient les autres pendant une suppression** : les proches disparaissent un à un de la liste (si l'app est ouverte), puis chacun reçoit le message d'accès perdu.

### 5.7 Accès perdu et nettoyage
- **Détection** : (a) en direct, un écouteur est refusé (retrait) ou la Miff est introuvable (suppression) ; (b) au lancement, avant d'afficher les données d'une Miff, un contrôle **auprès du serveur** (si réseau). Un **refus de permission** est un accès perdu ; une **erreur réseau** n'en est pas un.
- **Message unique** (QM8), une fois, sans boucle : « Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. »
- **Nettoyage** : arrêter écouteurs et surveillance des lieux ; essayer le lot « Quitter » complet ; s'il est refusé (la fiche n'existe plus), supprimer l'index seul ; purger le cache local ; supprimer ma photo expirée et celles que je constate expirées chez les autres.
- **Suite** : bascule vers une autre Miff, sinon état vide du Feed.

### 5.8 Taille d'une Miff (QM5)
**15 membres maximum, créateur compris** (précisé par le PM le 2026-10-24). C'est un **plafond doux, appliqué par l'app seulement : aucune règle serveur ne l'impose** (les règles ne comptent pas les sous-collections et un candidat ne lit pas la liste avant d'entrer). Conséquences : (a) à 15 membres, « Ajouter un proche » est remplacé par un texte calme (§5.10) ; (b) un client modifié, ou une personne qui détient déjà le code, peut entrer au-delà de 15 ; (c) l'app d'un membre n'empêche rien, elle n'affiche que le texte. Le coût des lectures croît avec le nombre de membres (`data-model.md` §12 : estimation de départ pour 4 membres). Un plafond garanti exigerait un changement de backend (Blaze) : hors v1.

### 5.9 Vie privée et réciprocité
- Le créateur ne voit rien de plus que les autres membres : **aucun écran de ce ticket n'affiche un statut, une position, une batterie ou une photo d'update**.
- Les membres se voient entre eux par leur prénom, nom et photo de profil (copie dans la Miff) ; l'écran de confirmation le dit.
- **Le créateur choisit les lieux surveillés pour tout le cercle**, et un arrivant **ne peut pas les lire avant d'entrer** (règles). Avec la décision QM2, le consentement est donné **avant** de voir ces lieux : c'est la faiblesse principale de ce choix (§14, R1). Mitigations : texte explicite, arrêt en un geste dès l'arrivée dans la Miff (où les lieux et les proches sont alors visibles), partage révocable à tout moment avec suppression du statut et de la photo.
- Rien de ce ticket n'écrit de coordonnées ni d'adresse.
- Risque d'**intrus avec le code** : atténué par le retrait avec renouvellement automatique, le renouvellement manuel et l'arrêt du partage ; **non supprimé** (QM3 : aucune validation en v1).

### 5.10 Ton et textes (tutoiement ; **tous à valider par le PM**)

Principes : phrases courtes (deux au plus, sauf le texte de partage), rien de moralisateur ou d'accusateur ; jamais « expulsé », « banni », « irrévocable », « danger », « urgence ». Les prénoms et noms de Miff s'insèrent par paramètres (`i18n-keys.md`).

| Contexte | Proposition |
|---|---|
| Aide sous le nom | « Choisis un nom pour retrouver cette Miff facilement. Les personnes qui reçoivent ton code verront ce nom. » |
| Limite de nom | « Limite de 40 caractères atteinte. » |
| **Texte de partage à l'entrée (QM2, fourni par le PM)** | « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » |
| Rappel système (sous le texte de partage) | « Ton téléphone te demandera séparément l'accès à ta position. Tu peux le refuser : tu rejoins la Miff quand même. » |
| Bouton d'entrée | « Rejoindre et partager » |
| Bouton de création (extension de QM2, confirmée) | « Créer et partager » ; texte : « En créant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » |
| Création réussie | « Ta Miff « {nom} » est prête. Partage ce code avec tes proches pour qu'ils puissent la rejoindre. » |
| Saisie du code (aide) | « Saisis le code que ton proche t'a donné. » |
| Code introuvable | « On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. » |
| Caractère impossible | « Ce code ne ressemble pas à un code Miff. Vérifie les lettres et les chiffres. » |
| Pause après 5 essais | « On fait une petite pause d'une minute avant de réessayer. » |
| Déjà membre | « Tu fais déjà partie de la Miff « {nom} ». » + « Ouvrir la Miff » |
| Hors connexion | « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » |
| Erreur générique | « Ça n'a pas fonctionné. Réessaie dans un instant. » |
| Arrêter le partage | « Arrêter de partager ton statut avec cette Miff ? Ton statut et ta photo d'update seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. » |
| Reprendre le partage | « Reprendre le partage ? Tu partages à nouveau ton statut avec les membres de cette Miff ; tu peux arrêter à tout moment. » |
| Renouveler le code | « Créer un nouveau code ? L'ancien ne fonctionnera plus. Les membres actuels restent dans la Miff. » |
| Invitation après un départ | « Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif. » + « Plus tard » |
| Retirer, confirmation | « Retirer {prénom} de la Miff ? {Prénom} n'aura plus accès à ce cercle. Un nouveau code sera créé : l'ancien ne fonctionnera plus. » |
| Retirer, succès | « {Prénom} ne fait plus partie de la Miff. Voici le nouveau code, à partager avec qui tu veux. » |
| Quitter, confirmation | « Quitter la Miff « {nom} » ? Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code. » |
| Supprimer, confirmation | « Supprimer la Miff « {nom} » ? Les {n} autres proches n'y auront plus accès et les lieux seront effacés. Cette action est définitive. » |
| Prévenir mes proches (bouton) / message | « Prévenir mes proches » / « Je ferme notre Miff « {nom} » dans Miff : elle ne sera plus accessible. » |
| Supprimer, progression / fin | « On finalise la suppression… » / « La Miff a été supprimée. » |
| **Accès perdu (QM8)** | « Tu n'as plus accès à la Miff « {nom} ». Ta position n'est plus partagée avec ce cercle. » |
| Créateur : pas de « Quitter » | « Tu as créé cette Miff : si tu veux la fermer, tu peux la supprimer. Les autres proches n'y auront alors plus accès. » |
| Plafond (QM5) | « Cette Miff est bien remplie ! » (aucun chiffre) |

### 5.11 Hors connexion et robustesse
- **Actions qui exigent le serveur** : créer, rejoindre, retirer, quitter, renouveler le code, supprimer, **arrêter ou reprendre le partage**. Elles n'affichent un succès **qu'après l'accusé de réception du serveur** et ne **laissent aucune écriture en file d'attente** : sans réseau, message neutre et écriture annulée (mise en œuvre à confirmer par un petit test des devs ; l'exigence est le comportement).
- **Lecture** : le cache permet de consulter hors connexion ; les boutons d'action sont désactivés avec une mention calme.
- Un seul appui = une seule opération. Rotation, arrière-plan : l'opération se termine ou reprend, jamais de doublon.
- Quota ou App Check refusés : même message que hors connexion, jamais « quota ».
- **Course** (code renouvelé pendant la saisie ; suppression pendant qu'on rejoint ; deux appareils du créateur) : le message unique « introuvable » ou l'accès perdu s'applique ; aucun plantage.

### 5.12 Analytics, notifications
- **Aucun nouvel événement** : on réutilise `miff_created` et `miff_joined` (`is_first_miff`) et `onboarding_completed` (`via`). Retirer, quitter, supprimer, renouveler, arrêter le partage : **non mesurés**. Jamais de nom de Miff, de code ni d'identifiant. Rien n'est envoyé avant le consentement Analytics. (Les événements de paywall relèvent du ticket Premium.)
- **Notifications** : aucune (Spark). Tout passe par l'affichage à l'ouverture.

### 5.13 Partage du statut : arrêter et reprendre (promesse du texte d'entrée)
- La ligne « Partager mon statut » de l'écran de la Miff (créateur et membre) montre l'état (actif / arrêté) et permet de l'inverser **à tout moment**.
- **Arrêter** : confirmation puis lot atomique : fiche (partage désactivé, `revokedAt` = maintenant) + suppression du statut + suppression de la photo ; surveillance des lieux de cette Miff arrêtée. Les autres voient « partage désactivé » (ton neutre) ; la personne ne voit plus les statuts des autres (réciprocité). Elle **reste dans la Miff**.
- **Reprendre** : le même texte qu'à l'entrée, puis lot atomique : fiche (partage activé, nouveau consentement et version) + création du statut initial `presence = "unknown"`.
- La permission de localisation du système n'est ni demandée ni retirée ici ; elle reste l'affaire du ticket Localisation.
- Contrat avec les tickets Localisation / Statut : ils écrivent les statuts suivants (géofencing, update manuelle) et affichent l'état « localisation limitée » ; **ce ticket écrit seulement le statut initial et la révocation**.

---

## 6. Frontières avec les tickets voisins

| Ticket | Frontière |
|---|---|
| **Onboarding** | Il porte les écrans profil et téléphone (téléphone obligatoire, décision PM, §4 #19), le routage de reprise et le texte d'accueil ; il **appelle** nos écrans A, B, C, D, E. La fin d'onboarding = une appartenance existe. Retour arrière : pas de flèche sur la première étape après création du compte (décision PM Q11 du ticket AUTH). **À répercuter** : obligation d'usage de l'app liée à la permission de localisation (§4 #18). |
| **Lieux de confiance** | Ils créent, modifient et suppriment les lieux. Notre écran n'affiche qu'une liste et un point d'entrée. Les lieux sont lisibles de tous les membres **une fois entrés**. |
| **Localisation / Statut** | Permission de localisation du système, géofencing et écriture des statuts suivants : **leurs** tickets. Contrat : (1) notre lot d'entrée ou de création écrit le consentement **et** le statut initial `unknown` ; (2) arrêter le partage supprime statut et photo dans le même lot (§5.13) ; (3) la permission du système ne bloque jamais l'entrée ; (4) ils définissent l'état « localisation limitée ». |
| **Feed** | Voir §7. Nous livrons les destinations (créer, rejoindre, écran de la Miff) et la liste des proches ; le Feed livre l'en-tête, l'état vide, la pastille de Miff (qui ouvre **notre** écran), le sélecteur, le bouton « + Ajouter un proche » (qui ouvre **notre** partage), la bulle et les cartes. |
| **Paywall / Premium** | **Dépendance forte (QM7)** : limites gratuites, paywall, déclencheurs, textes, fin d'abonnement, préavis et choix de l'élément conservé. Ce ticket ne les spécifie pas. Le ticket Premium doit pouvoir **appeler** nos procédures « Retirer » et « Supprimer » sans écran (§13). |
| **Réglages** | « Mes Miffs » = un autre point d'entrée de l'écran F/G (QM13). Déconnexion et suppression de compte : `authentification.md` §7.6 et §7.7. |
| **Authentification (AUTH-5)** | La suppression de compte d'un créateur **réutilise** nos procédures (§13) : elles doivent exister sous forme de composants sans interface avant AUTH-5. |
| **Notifications** | Hors périmètre ; voir §5.12. |

---

## 7. Feed : recommandation, décision et constats

**Décision du PM (QM10) : le Feed est un ticket distinct « FEED — Feed (cercle de confiance) »**, après ce ticket et les tickets Statut et Localisation (page Notion créée en « A cadrer »).

**Constats à reprendre dans le challenge du Feed** :

| # | Constat | Source | Statut |
|---|---|---|---|
| F1 | Bouton **« Localiser »** (carte avec « les coordonnées de la dernière position connue ») et photo en grand avec « la localisation » | Notion Feed | **Gardé par le PM dans la v1 du Feed (QM11).** Il **contredit le modèle** : aucune coordonnée d'une personne n'est stockée, par décision (`data-model.md` §1) ; le modèle ne contient qu'un état et des horodatages. **Question ouverte pour le ticket Feed, non tranchée ici** : que fait précisément « Localiser » sans coordonnées stockées ? À définir avec le PM, en tenant compte du consentement et des règles des stores. |
| F2 | Boutons **« Message »** (SMS) et **« Appeler »** | Notion Feed | **Gardés (QM11)**, faisables car le téléphone est obligatoire à l'onboarding. Mais le schéma n'a aucun champ téléphone (§4 #19) : champ, copie dans la fiche de membre, règles, règles et consentement sont à définir (tickets Onboarding / Feed avec `backend-data`). **Visibilité décidée par le PM (2026-10-24, proposition A) : le numéro est visible des membres des Miffs de l'utilisateur, et d'eux seuls.** |
| F3 | « Aucune Miff : on considère que tout le monde est en sécurité » | Notion Feed | **Décision QM12 : OK comme recommandé** : état vide qui invite (« Pas encore de Miff »), jamais de phrase de sécurité. |
| F4 | Bulle « **Tout le monde est en sécurité** » | Notion Feed | **Décision QM12** : n'affirmer « Tout va bien » que si tous les statuts connus le permettent ; sinon dire combien ont donné des nouvelles (« 3 sur 4 »). Libellé exact à fixer dans le ticket Feed. |
| F5 | « Mis à jour il y a 2 min » : « la dernière fois où on a checké » | Notion Feed, Figma | Avec un écouteur temps réel ce n'est plus vrai ; hors connexion **le cache affiche l'ancien état en vert** : prévoir un état « hors connexion, dernières informations à {heure} ». |
| F6 | La **carte « moi »** est dans la liste | Notion Feed | Elle doit afficher mon état de partage (arrêté, en cours de publication). |
| F7 | Bulle **rouge** quand quelqu'un est dans une zone à risque ; carrousel | Notion Feed | Ton à adoucir et à ne pas répéter ; zone à risque = Premium, définie par le créateur. |
| F8 | **Sélecteur de Miff** : seulement s'il y en a plusieurs (Premium) | Notion Feed | La Miff « active » est une préférence locale purgée à la déconnexion. |
| F9 | Le paywall s'affiche à la première visite du Feed après inscription ou connexion | Notion Premium | À reprendre dans le ticket Premium. |

---

## 8. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| **Partage du code et du message « Prévenir »** | Sélecteur système (`ACTION_SEND`) ; presse-papiers : `EXTRA_IS_SENSITIVE` sur Android 13 | `ShareLink` / feuille de partage ; copie simple |
| **Coller le code** | Lecture du presse-papiers **uniquement** sur appui | `PasteButton` (iOS 16) : pas d'alerte de collage |
| **Saisie** | Clavier ASCII, majuscules, suggestions et correction désactivées | `.textInputAutocapitalization(.characters)`, `.autocorrectionDisabled()` |
| **Dialogues** | Boîtes de dialogue Material ; « Retour » : fermer sans agir | `alert` / `confirmationDialog` ; action destructrice marquée ; geste de retour |
| **Menu d'un proche** | Menu « ⋯ » ; actions accessibles par TalkBack | Menu contextuel ; VoiceOver : actions personnalisées |
| **Texte de partage à l'entrée** | Visible sans défilement sur petit écran ; taille de texte système maximale testée | Idem ; Dynamic Type testé |
| **Suppression en cours** | Le processus peut être tué (surcouches constructeur type ColorOS) : reprise au lancement ; pas de service d'arrière-plan | Tâche d'arrière-plan courte autorisée, puis reprise au lancement |
| **Purge du cache** | `terminate()` puis `clearPersistence()` (`data-model.md` §8) | Idem, signatures exactes à confirmer par `ios-dev` |
| **Tests sur appareil** | OPPO Find X5 Lite (Android 13) | iPhone 11 Pro (iOS 26.5.2) via TestFlight uniquement |
| **Textes** | `values-fr` / `values` (anglais de repli) | `Localizable.xcstrings` ; **mêmes clés `miff_*`** |

---

## 9. Lien avec le modèle de données et les règles

**État des règles** : **DUR-1 et DUR-2 sont déployées sur `miff-3d283`** (information du PM, 2026-10-23, à confirmer par la date de publication dans la console ; §4 #15). DUR-3 n'a pas changé les règles. **Ce ticket ne requiert aucun nouveau déploiement de règles.** Une **vérification** est demandée à `backend-data` : la création de la fiche avec partage activé **et** du statut initial dans le même lot (entrée, création) n'est pas un cas du plan de test existant (le modèle prévoit l'activation par mise à jour de la fiche) ; aucune modification de règle n'est attendue (la fiche peut naître avec `enabled = true` et `consentedAt` = heure serveur ; le statut exige le partage actif **après** le lot), mais le cas et sa **mesure de lectures de règles** (marge sur 20) doivent être **testés dans l'émulateur avant la PR MIFF-1**.

**Ce que les règles permettent déjà** (vérifié dans `backend/firestore.rules`) : création de Miff en un lot (pierre tombale respectée) ; entrée avec preuve du code ; retrait impossible sans renouvellement du code ; départ du créateur seulement avec suppression de la Miff ; suppression refusée sans pierre tombale ni suppression du code ; Miff supprimée illisible ; libération de l'index seulement si l'on n'est plus membre ; limite gratuite par slot ; lieux lisibles de tous les membres ; révocation du partage exigeant la suppression du statut et de la photo dans le même lot.

**Ce que les clients implémentent** (`data-model.md` §8, à l'identique sur les deux plateformes) :

| Action | Lectures | Écritures (un seul lot, accusé du serveur) | Sous-ticket |
|---|---|---|---|
| Créer | `entitlements/{uid}`, index `memberships` | `miffs` + `joinCodes` + `members` (rôle `owner`, **partage activé**) + `memberships` + `status/{moi}` initial | MIFF-1 |
| Rejoindre | `joinCodes/{code}`, index `memberships` | `members` (rôle `member`, avec `joinCode`, **partage activé**) + `memberships` + `status/{moi}` initial | MIFF-1 |
| Écran de la Miff | `get miffs/{id}`, écouteur `members`, écouteur `places` | — | MIFF-1 |
| Renommer | — | `miffs` (nom) + `joinCodes` (nom) | MIFF-1 |
| Arrêter / reprendre le partage | — | arrêter : fiche (update) + delete statut + delete photo ; reprendre : fiche (update) + create statut initial | MIFF-1 |
| Accès perdu / nettoyage | `get miffs/{id}` par index | lot « Quitter », sinon index seul ; purge du cache | MIFF-1 |
| Renouveler le code | — | `miffs` (code) + nouveau `joinCodes` + suppression de l'ancien | MIFF-2 |
| Retirer | `get miffs/{id}` (code actuel) | 6 écritures : fiche, statut, photo de la cible + code | MIFF-2 |
| Quitter | — | fiche + statut + photo + index | MIFF-2 |
| Supprimer | `get miffs/{id}`, liste des membres | N lots de retrait, lieux (≤ 5 par lot), lot final avec **pierre tombale** | MIFF-2 |

**Contrat à respecter** : toutes les clés d'un document sont écrites à chaque écriture ; l'index se lit par son champ `miffId` ; `joinedAt`, `createdAt`, `consentedAt` : heure serveur ; un nouveau code vient du même générateur cryptographique ; une collision (~10^-13) = regénérer et réessayer ; `consentVersion` identifie le **texte affiché** (un texte modifié = une nouvelle version, constante identique sur les deux plateformes).

**Évolutions de règles ou de schéma possibles, non demandées sans décision, à évaluer par `backend-data`** :
1. Exiger `exists(users/{uid})` à la création et à l'entrée (preuve serveur du « profil exigé ») : +1 lecture de règles ; faible valeur de sécurité.
2. Ajouter le **prénom du créateur** dans `joinCodes` pour afficher « Rejoindre la Miff « X » de {prénom} ? » : améliorerait le consentement éclairé (le texte d'entrée étant donné avant de voir les membres) ; donnée de plus visible de quiconque détient le code.
3. Plafond de taille garanti ou validation d'arrivée : non faisable proprement sans serveur ; à rouvrir avec Blaze.
4. Transfert de propriété (`ownerUid` immuable) : hors v1.
5. Champ téléphone (§4 #19), visible des seuls membres des Miffs de la personne : ticket Onboarding / Feed.
6. Corriger le commentaire d'en-tête de `firestore.rules` (§4 #15).

---

## 10. Cas limites

1. **Code correct mais Miff supprimée juste après** : l'entrée est refusée ; message unique « introuvable ».
2. **Code renouvelé entre la lecture et la confirmation** : refus à l'écriture ; message unique ; on propose de ressaisir.
3. **Personne retirée qui retente avec l'ancien code** : « introuvable », sans mention du retrait. Avec le nouveau code que le créateur lui redonne volontairement : elle peut revenir (pas de liste de bannis).
4. **Retiré qui rejoint avant que son app ait nettoyé son index** : le nettoyage (§5.4, étape 4) précède l'écriture.
5. **Déjà membre via un autre appareil** : détecté par les index ; aucune écriture.
6. **Créateur qui tente de rejoindre sa propre Miff** : « Tu fais déjà partie de cette Miff ».
7. **Compte gratuit qui tente une deuxième Miff** : refusé par les règles ; comportement visible défini par le ticket Premium (§5.5).
8. **Retrait d'une personne déjà partie** : la suppression d'un document absent est sans effet, le code est renouvelé quand même ; message neutre, liste rafraîchie.
9. **Deux appareils du créateur qui retirent ou renouvellent en même temps** : un lot réussit, l'autre échoue ou réussit à son tour ; le code affiché est relu avant partage.
10. **Retirer le dernier proche** : fonctionne (le code est renouvelé quand même).
11. **Supprimer une Miff sans autre membre** : même procédure, sans retrait ; le texte ne dit pas « 0 proche ».
12. **Suppression interrompue** : après N retraits, après les lieux, avant le lot final ; reprise au lancement ; le créateur peut relancer. La Miff reste utilisable entre-temps (le code a changé).
13. **App tuée pendant la création ou l'entrée** : le lot est atomique ; au lancement, soit la Miff existe (index présent), soit rien n'a eu lieu.
14. **Fiche sans index (ou l'inverse)** : impossible par les règles ; si on la rencontre, traiter comme accès perdu et nettoyer.
15. **Entrée avec permission de localisation refusée** : la personne est dans la Miff, statut `unknown` puis `locationAccess = denied` ; aucun blocage.
16. **Partage arrêté puis Miff supprimée** : rien de particulier (le statut est déjà supprimé).
17. **Nom avec emojis, espaces multiples, texte de droite à gauche** : accepté ; mise en page début/fin.
18. **Taille de texte système maximale** : le texte de partage et le code restent lisibles, les boutons ne se chevauchent pas.
19. **Retrait pendant la lecture** : l'écouteur est refusé ; message unique, nettoyage, bascule ; aucune donnée ne reste visible. **Test à deux appareils.**
20. **Mode avion pendant une action** : message neutre, rien en file d'attente, aucun accès perdu faussement déclaré.
21. **Plusieurs appareils d'une même personne** : si elle quitte sur l'un, l'autre reçoit l'accès perdu à son retour.
22. **Premier lancement du créateur sur un nouveau téléphone** : l'invitation « un proche a quitté » ne s'affiche pas pour des départs anciens (aucun souvenir local = aucune invitation).
23. **Plus de 15 membres** (code déjà connu, client modifié) : l'app ne bloque rien ; elle affiche seulement le texte du plafond doux.
24. **Texte de partage dans une autre langue que celle du destinataire** : le message suit la langue de l'expéditeur.

---

## 11. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.

**Avant MIFF-1 (Android)**
1. **Valider les textes** de §5.10 (tutoiement).
2. **Confirmer dans la console** la date de publication des règles (Firestore > Règles) : DUR-1 et DUR-2 sont-elles bien actives ?
3. **Valider les nouveaux écrans** dessinés par `design-system` (§3.3) dans le Figma « Miff - Claude ».
4. **2 ou 3 comptes de test** et **deux appareils** pour les parcours « en direct » : Android seul permet de tout tester **sauf le temps réel entre deux personnes**, testable avec l'iPhone (après MIFF-3) ou un second Android.

**Avant la bêta externe**
5. **Page d'atterrissage** du message de partage (QM14) : petite page statique du site d'AUTH-W, puis liens des stores à la publication.
6. Rien d'autre : aucun compte à créer, aucun moyen de paiement, aucune action console nouvelle. Contact par défaut si un texte devait en citer un : Tomessa MFOUKA, `tomessa.mfouka@gmail.com` (aucun texte de ce ticket n'en cite).

---

## 12. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Écritures du ticket | Quelques écritures par action (créer : 5 ; rejoindre : 3 ; retirer : 6 ; supprimer : N lots + lieux + 1) | 20 000 écritures et 20 000 suppressions par jour (Spark) | Très loin des quotas ; `data-model.md` §12 |
| Lectures | Création et entrée : quelques lectures, **plus les lectures de règles** (mesurées dans l'émulateur avant ajout du statut initial : créer ≤ 2 ou ≤ 6 sur 20, rejoindre ≤ 2, retirer ≤ 2, supprimer ≤ 3 ; **à re-mesurer avec le statut dans le lot**) ; écran de la Miff : environ N + 4 lectures | 50 000 lectures par jour ; une lecture de règles est facturée même refusée | Ratio lectures / utilisateur actif > 100 : à examiner ; > 150 deux jours de suite : signal de bascule |
| Saisie de codes en rafale | Format vérifié localement (zéro lecture), pause de 60 s, App Check | Un client modifié contourne la pause (risque K8 documenté) | Surveillance quotidienne ; Blaze : jamais sans décision du PM |
| Taille des Miffs | Plafond doux de 15 (QM5), **non imposé par le serveur** | Les lectures croissent avec le nombre de membres | Mesure sur la bêta ; plafond garanti = changement de backend plus tard |
| Page d'atterrissage du partage | Firebase Hosting (Spark), domaine `*.web.app` d'AUTH-W | Domaine par défaut | Un domaine propre est payant : jamais sans décision |
| Notifications au cercle | **Aucune** (Blaze) | Retrait, départ, suppression : visibles à l'ouverture seulement | À rouvrir avec la décision Blaze |

Rien dans ce ticket n'exige un moyen de paiement. Aucun choix économique ne réduit la sécurité.

---

## 13. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket (`CLAUDE.md`). Tailles relatives, sans durée.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| MIFF-0 | **Décisions, design, vérification des règles et mise à jour des documents** (PM, `design-system`, `backend-data`) | Écrans de §3.3 dans Figma « Miff - Claude » ; test à l'émulateur du lot « fiche avec partage activé + statut initial » et mesure de ses lectures (§9) ; commentaire d'en-tête des règles (§4 #15) ; mise à jour de `docs/specs/miff-groupes.md` | S | Validation de ce ticket |
| MIFF-1 | **Android : créer, rejoindre, écran de la Miff, partage, accès perdu** | Écrans A à G, L et M ; saisie du code (§5.3) ; vérifications et **consentement explicite** (§5.4) ; lots de création, d'entrée (adhésion + consentement + statut initial) et de renommage ; liste des proches en direct ; partage du code ; ligne « Partager mon statut » (arrêter, reprendre) ; plafond doux ; nettoyage et purge (§5.7) ; actions hors connexion (§5.11) ; textes `miff_*` FR + EN ; tests unitaires (code, ordre des vérifications, atomicité du lot, interprétation des erreurs, accès perdu ≠ erreur réseau) ; accessibilité | L | INIT-2 ; AUTH-1 (session, routage, déconnexion avec purge) ; DUR-1 à DUR-3 ; MIFF-0 ; Onboarding (appel de nos écrans) ; tickets Localisation / Statut (contrat §6) |
| MIFF-2 | **Android : retirer, quitter, supprimer, renouveler le code, « Prévenir mes proches »** | Écrans H, I, J, K ; lot de retrait avec code renouvelé ; lot « Quitter » ; suppression reprenable et idempotente ; invitation après départ ; message « Prévenir mes proches » ; **composants sans interface** (retrait d'un membre, suppression d'une Miff) réutilisables par AUTH-5 et par le ticket Premium ; tests d'interruption à chaque étape | M à L | MIFF-1 |
| MIFF-3 | **iOS : créer, rejoindre, écran de la Miff, partage, accès perdu** | Mêmes livrables que MIFF-1 ; spécificités §8 | L | INIT-4 ; AUTH-3 ; MIFF-1 (référence de comportement) |
| MIFF-4 | **iOS : retirer, quitter, supprimer, renouveler, « Prévenir mes proches »** | Mêmes livrables que MIFF-2 | M à L | MIFF-3, MIFF-2 |

**Limites gratuites et paywall : aucun sous-ticket ici** (décision QM7) ; ils seront spécifiés dans le ticket Premium, qui s'appuiera sur MIFF-1 et MIFF-2.

Ordre recommandé : MIFF-0, MIFF-1, MIFF-2 (**avant AUTH-5**, qui en dépend), puis MIFF-3 et MIFF-4. Le **Feed** démarre après MIFF-1 et les tickets Statut / Localisation. Chaque PR : revue de code en contexte neuf, revue sécurité (codes, journalisation, consentement, gestion des erreurs, état local), QA, recette du PM, fusion par Claude après « oui » explicite du PM (`CLAUDE.md`).

---

## 14. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Rejoindre vaut partager (décision QM2, écart avec ma recommandation d'un consentement séparé)** : le consentement est donné **avant** de voir les lieux et les proches (impossible avant l'entrée, règles), regroupé avec l'adhésion, sans option « entrer sans partager » ; les stores (Apple, Google) appliquent des règles **anti-stalkerware** et exigent une justification claire de l'usage de la localisation | Consentement jugé insuffisamment éclairé ; refus ou retrait à la soumission ; confiance entamée si quelqu'un est entré par un code reçu d'un inconnu | **Risque assumé par le PM (décision du 2026-10-24).** Atténué par : texte explicite **avant** le geste, bouton libellé en conséquence, lot atomique tracé (date et version du texte), arrêt en un geste avec suppression des données, réciprocité, permission du système séparée et jamais bloquante. À faire relire à `release-manager` (règles Apple et Google sur le partage de position) avant toute soumission ; l'amélioration « prénom du créateur dans le code » (§9, évolution 2) réduirait l'écart ; sans relecture juridique (décision du 2026-10-23) |
| R2 | **Code qui fuite** : aucune validation des arrivées (QM3), le code est visible de tous les membres ; un inconnu qui entre voit les statuts du cercle (réciprocité : il partage lui-même) | Intrusion dans le cercle | Retrait avec renouvellement automatique ; renouvellement manuel ; arrêt du partage en un geste ; **validation d'arrivée non retenue en v1** (à rouvrir avec Blaze) ; aucun signal visuel de « nouveau membre » (non retenu) |
| R3 | **Créateur piégé** : il ne peut pas partir sans détruire la Miff pour les autres (QM1) | Cercles fermés ou créateurs qui restent malgré eux | Texte clair ; transfert à rouvrir si les retours le demandent |
| R4 | **Écriture rejouée hors connexion** | Action exécutée à un moment que la personne ignore | Accusé du serveur exigé ; critère H1 en mode avion |
| R5 | **Message d'accès perdu perçu comme une exclusion** | Peine, malentendu | Message unique et neutre (QM8) ; « Prévenir mes proches » (QM9) |
| R6 | **Retrait et suppression sans notification** (Spark) | Découverte à l'ouverture seulement | Accepté ; à rouvrir avec Blaze. « Prévenir mes proches » ne notifie pas : il **propose** un message que le créateur envoie lui-même |
| R7 | **Marge des lots de règles** (retrait : 18 sur 20 en pire cas) ; **nouveau lot d'entrée avec statut initial** non encore mesuré | Opération refusée par la limite de Firestore | Ne rien ajouter aux lots ; mesure à l'émulateur avant MIFF-1 (MIFF-0) |
| R8 | **Suppression de Miff longue et interrompue** | Miff à moitié vidée | Procédure reprenable, marqueur local, texte calme |
| R9 | **Fin d'abonnement supprime des Miffs créées** (décision du 2026-10-24) | Des membres perdent leur cercle sans y être pour rien | À traiter dans le ticket Premium : préavis, choix de la Miff conservée ; ici : composants sans interface |
| R10 | **Plafond de 15 uniquement dans l'app** | Une Miff peut dépasser 15 ; lectures plus coûteuses | Documenté ; mesure ; plafond garanti plus tard |
| R11 | **Clés d'index mal lues** (identifiant de document pris pour `miffId`) | Mauvais nettoyage, fausses détections | Contrat §9 ; test unitaire sur l'index à clé `miffId` |
| R12 | **Exposition du nom de la Miff à qui détient le code** | Un nom intime visible d'un tiers | Texte d'aide sous le champ |
| R13 | **Test du temps réel entre deux personnes impossible avant l'iPhone** | Cas « retrait pendant la lecture » non recetté | Comptes de test et émulateur pour les règles ; recette à deux appareils après MIFF-3 |
| R14 | **Écrans manquants dans Figma** | Développement qui invente des écrans | MIFF-0 les dessine avant MIFF-1 |
| R15 | **Limites gratuites non spécifiées ici** (QM7) | Comportement visible d'une deuxième Miff inconnu avant le ticket Premium | Les règles refusent côté serveur ; dépendance marquée ; ticket Premium |
| R16 | **« Localiser » sans coordonnées stockées** (QM11) | Fonction du Feed non définissable aujourd'hui | Question ouverte du ticket Feed (§7, F1) ; aucune coordonnée n'est stockée entre-temps |
| R17 | **Risque juridique assumé** : conservation physique des données de survivants d'une Miff supprimée, données de profil visibles du cercle, consentement groupé avec l'adhésion | Voir `data-model.md` §11 | **Risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)** |

---

## 15. Décisions du PM (2026-10-24)

Le PM a répondu aux 15 questions du challenge le 2026-10-24 (détail dans `miff-questions-pm.md`). Les mentions « écart » signalent une décision qui diffère de la recommandation de l'agent.

| QM | Sujet | Décision du PM | Répercussion dans ce ticket |
|---|---|---|---|
| QM1 | Créateur qui veut partir | **Il ne peut pas quitter sans supprimer (v1).** Pas de transfert. | §5.1, §5.10, G4, R3 |
| QM2 | Rejoindre et partage | **Oui, rejoindre vaut partager son statut (écart : je recommandais un consentement séparé).** Implémentation spécifiée en consentement **explicite** : texte avant l'action, bouton « Rejoindre et partager », un lot atomique, arrêt à tout moment, permission du système à part et jamais bloquante. | §5.4, §5.13, §9, §14 R1, critères J, K |
| QM3 | Code et arrivées | **Tous les membres voient et partagent le code ; aucune validation des arrivées en v1.** Le retrait + code renouvelé reste. | §5.3, §14 R2 |
| QM4 | Expiration | **Le code n'expire pas.** | §5.3 |
| QM5 | Taille | **15 membres maximum, plafond doux dans l'app** ; aucune règle serveur ne l'impose. | §5.8, G9 |
| QM6 | Nom | **2 à 40 caractères** (Notion 2–50 alignée sur 2–40 ; compatible avec les règles 1–40). | §5.2, C2, C3 |
| QM7 | Paywall | **Plus tard** : non spécifié ici. Dépendance au ticket Premium. | §5.5, §6, §17 |
| QM8 | Accès perdu | **Message unique « Tu n'as plus accès à la Miff « X » ».** | §5.7, §5.10, D |
| QM9 | Prévenir avant suppression | **Oui**, bouton facultatif « Prévenir mes proches ». | §5.6, S6 |
| QM10 | Feed | **Ticket distinct.** | §7 |
| QM11 | Localiser / Message / Appeler | **Non : on garde les trois boutons dans la v1 du Feed (écart : je recommandais de les retirer).** Message / Appeler faisables car le téléphone est obligatoire à l'onboarding. **« Localiser » contredit le modèle : le ticket Feed doit définir ce qu'il fait sans coordonnées stockées.** | §7 F1, F2, §4 #19, §14 R16 |
| QM12 | Aucune Miff / bulle verte | **OK comme recommandé.** | §7 F3, F4 |
| QM13 | Entrée de l'écran de gestion | **OK** : pastille de Miff du Feed + « Mes Miffs » dans Réglages. | §2, G1 |
| QM14 | Lien de partage | **OK** : page statique gratuite sur le site d'AUTH-W. | §5.3, §11 |
| QM15 | Création | **OK** : en deux temps. | §3.1, §5.3, C1 à C6 |

**Précisions du PM (2026-10-24, suite) — plus aucun point en attente** :
- **Extension de QM2 à la création : OUI.** « Créer et partager », même texte explicite avant l'action, **un seul lot atomique** (profil exigé + Miff + code + fiche de membre + consentement de partage + `status/{moi}` initial).
- **« 15 membres maximum » : créateur compris.**
- **Visibilité du téléphone : proposition A** : le numéro est visible des membres des Miffs de l'utilisateur, et d'eux seuls (voir §4 #19, §7 F2). La question « visibilité du numéro » du ticket Feed est résolue ; restent à définir la mise en œuvre (champ, règles) et le texte de consentement.

**Décision du PM (2026-10-31) — départ volontaire et code (QM16)** : **une sortie volontaire d'une Miff ne renouvelle PAS le code** ; **seul le retrait d'un membre par le créateur le renouvelle** (automatiquement, dans le même lot, imposé par les règles). C'est déjà le comportement des règles déployées (un membre simple ne peut pas écrire la Miff) : aucune règle ni aucun lot ne change. Conséquences : (a) aucun texte affiché dans le parcours « Quitter » ne promet un changement de code (« Quitter, confirmation » §5.10 : « Tu pourras revenir si on te redonne le code » ne dit rien de tel) ; (b) la personne qui est partie de son plein gré **peut revenir avec le même code** tant que le créateur ne l'a pas renouvelé ; (c) le créateur garde l'invitation discrète et facultative « Un proche a quitté la Miff. Tu veux créer un nouveau code ? » (décision du 2026-10-22, une fois par départ) ; (d) iOS (MIFF-4) applique la même règle.
---

## 16. Critères d'acceptation

Découpage : **C** création, **J** rejoindre, **K** partage du statut, **G** écran de la Miff, **N** renouvellement du code, **R** retirer, **Q** quitter, **S** supprimer, **D** accès perdu, **H** hors connexion et robustesse, **V** vie privée et ton, **T** transverses, **AND** Android, **IOS** iOS, **PM** recette sur téléphone, **QA** vérifications du QA. Les critères C à T valent pour les deux plateformes. (Les limites gratuites et le paywall n'ont pas de critères ici : ticket Premium.)

### Création
- [ ] **C1** « Créer une nouvelle Miff » ouvre l'écran « Nouvelle Miff » avec le seul champ Nom (le code n'y est pas affiché) et le texte de partage au-dessus du bouton.
- [ ] **C2** Le bouton « Créer et partager » est grisé tant que le nom, espaces de début et de fin retirés, a moins de 2 caractères ; actif à partir de 2.
- [ ] **C3** Au-delà de 40 caractères la saisie est refusée avec « Limite de 40 caractères atteinte. » ; jamais plus de 40 enregistrés (emojis compris).
- [ ] **C4** L'enregistrement envoie **un lot unique** : Miff, code, fiche du créateur (rôle `owner`, partage activé avec consentement daté et versionné), index d'appartenance, statut initial `unknown` ; le code est généré à ce moment ; test unitaire sur 1 000 codes : tous valides, aucun I, L, O, 0 ni 1 ; échec d'une écriture = rien n'est écrit.
- [ ] **C5** Aucun message de succès avant l'accusé du serveur ; hors connexion : message neutre, **aucune Miff n'apparaît au retour du réseau**.
- [ ] **C6** Après succès, l'écran « Ta Miff est prête » affiche le code par groupes de 4, avec « Partager » et « Copier ».
- [ ] **C7** Une collision de code relance la création jusqu'à 3 fois sans message ; au-delà, « Ça n'a pas fonctionné. Réessaie dans un instant. »
- [ ] **C8** Sans profil, l'écran n'est pas atteignable (retour à l'étape profil).
- [ ] **C9** `miff_created` (avec `is_first_miff`) n'est envoyé que si le consentement Analytics est donné ; aucun nom ni code.

### Rejoindre
- [ ] **J1** Le champ force les majuscules, désactive correction et suggestions ; espaces, tirets et minuscules saisis ou collés sont normalisés.
- [ ] **J2** Le bouton est actif seulement avec 12 caractères valides de l'alphabet ; un caractère hors alphabet affiche un message **sans aucune lecture réseau**.
- [ ] **J3** Aucune lecture automatique du presse-papiers : « Coller » est une action de la personne.
- [ ] **J4** Un code faux, un code renouvelé et un code de Miff supprimée affichent **exactement le même message** (test des trois cas).
- [ ] **J5** Après 5 échecs consécutifs : pause de 60 secondes avec message bienveillant ; compteur remis à zéro après un succès ou 10 minutes.
- [ ] **J6** Un code reconnu affiche l'écran de confirmation avec le nom de la Miff ; **aucune écriture avant l'appui sur « Rejoindre et partager »**.
- [ ] **J7** Déjà membre de cette Miff (comparaison par le champ `miffId` des index) : message et bouton « Ouvrir la Miff », aucune écriture.
- [ ] **J8** Un index obsolète (Miff devenue inaccessible) est nettoyé **avant** l'écriture (test : retiré, puis tentative de rejoindre une autre Miff sans relancer l'app : aucune erreur injustifiée).
- [ ] **J9** Hors connexion pendant la lecture du code ou la confirmation : message neutre, aucune écriture en file d'attente.
- [ ] **J10** Code renouvelé entre la lecture et la confirmation : message unique, pas de plantage.
- [ ] **J11** `miff_joined` (avec `is_first_miff`) et `onboarding_completed` (`via = joined`) respectent le consentement Analytics ; aucun code ni nom.
- [ ] **J12** Sans profil, l'entrée est impossible (retour à l'étape profil).

### Partage du statut (consentement explicite, QM2)
- [ ] **K1** L'écran de confirmation affiche, **avant** le bouton et sans action de la personne, le texte « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. » et le rappel sur la permission du système ; le bouton est libellé « Rejoindre et partager » (« Créer et partager » à la création) ; « Annuler » n'écrit rien.
- [ ] **K2** L'entrée est **un seul lot atomique** : fiche de membre (partage activé, `consentedAt` = heure serveur, `consentVersion` = version du texte affiché), index d'appartenance, statut initial `presence = "unknown"` avec `locationAccess` = permission réelle de l'appareil ; si une écriture est refusée, **aucune** n'est appliquée (test sur les deux plateformes).
- [ ] **K3** Aucun statut ni consentement n'est écrit avant l'appui sur le bouton (test : quitter l'écran de confirmation).
- [ ] **K4** Refuser ou limiter la permission de localisation du système **n'empêche ni de rejoindre ni de créer** ; le statut porte `locationAccess` (`denied`, `foreground`, `unknown`) sans message alarmiste.
- [ ] **K5** La ligne « Partager mon statut » de l'écran de la Miff montre l'état et permet de l'arrêter **à tout moment** ; l'arrêt demande une confirmation puis envoie un lot : fiche (`enabled = false`, `revokedAt` = maintenant) + suppression du statut + suppression de la photo ; la personne reste dans la Miff.
- [ ] **K6** « Reprendre » affiche le même texte qu'à l'entrée puis envoie un lot : fiche (partage activé, nouveau consentement et version) + création du statut initial.
- [ ] **K7** Après un arrêt, la surveillance des lieux de cette Miff est arrêtée, aucun statut n'est réécrit, et les autres voient « partage désactivé » (ton neutre).
- [ ] **K8** La version du texte (`consentVersion`) est une constante unique, identique sur Android et iOS ; modifier le texte exige une nouvelle version.

### Écran de la Miff
- [ ] **G1** Le créateur et chaque membre ouvrent l'écran depuis la pastille de Miff du Feed et depuis « Mes Miffs » dans Réglages ; le titre est le nom de la Miff.
- [ ] **G2** L'écran affiche : nom, code par groupes de 4 avec « Partager » et « Copier », liste des proches (photo ou initiales, prénom et nom, étiquettes « Toi » et « Créateur »), ligne « Partager mon statut », section des lieux (liste seulement).
- [ ] **G3** Seul le créateur peut renommer ; « Enregistrer » est actif seulement si le nom a changé et est valide ; le renommage met à jour la Miff **et** le nom du code dans le même lot.
- [ ] **G4** Seul le créateur voit « Renouveler le code », le menu « ⋯ » des proches et « Supprimer la Miff » ; le membre voit « Quitter la Miff » ; le créateur n'a **pas** de « Quitter » et lit le texte explicatif.
- [ ] **G5** La liste des proches est en direct ; le code affiché se met à jour si un renouvellement a lieu pendant que l'écran est ouvert.
- [ ] **G6** « Partager » ouvre la feuille de partage native avec nom, code et lien (QM14), dans la langue de l'app ; « Copier » ne copie que le code ; sur Android 13 et plus le contenu est marqué sensible.
- [ ] **G7** Aucun statut, aucune batterie, aucune position, aucune photo d'update n'apparaît sur cet écran.
- [ ] **G8** La lecture seule (proches, lieux) fonctionne hors connexion depuis le cache avec une mention calme.
- [ ] **G9** À 15 membres ou plus, « Ajouter un proche » est remplacé par « Cette Miff est bien remplie ! » (aucun chiffre) ; **aucun contrôle serveur** n'est attendu ni testé pour ce plafond.

### Renouveler le code
- [ ] **N1** « Renouveler le code » demande une confirmation puis écrit un lot de 3 écritures ; l'ancien code ne fonctionne plus (un second compte tente l'ancien : message unique), le nouveau fonctionne.
- [ ] **N2** Les membres actuels restent dans la Miff et voient le nouveau code en direct.
- [ ] **N3** Après un départ volontaire constaté par l'app du créateur, une **invitation unique** discrète s'affiche (« Plus tard » la ferme sans la répéter) ; l'invitation n'apparaît que si le créateur a l'écran de la Miff sous les yeux au moment du départ (Android : non conservée) ; aucune invitation pour des départs antérieurs à l'installation locale ni pour un retrait que le créateur vient de faire.

### Retirer
- [ ] **R1** Le menu « ⋯ » d'un proche propose « Retirer de la Miff » ; une confirmation nominative, au ton non accusateur, précède toute action.
- [ ] **R2** Le retrait envoie **un seul lot de 6 écritures** (fiche, statut, photo de la cible ; code mis à jour, nouveau code créé, ancien supprimé) ; rien d'autre dans le lot.
- [ ] **R3** Après succès, le nouveau code est affiché avec « Partager » et « {Prénom} ne fait plus partie de la Miff… ».
- [ ] **R4** Jamais d'envoi d'un retrait sans nouveau code (test du client).
- [ ] **R5** Cible déjà partie : message neutre et liste rafraîchie ; le code est renouvelé quand même.
- [ ] **R6** Le créateur ne peut pas se retirer lui-même (pas de menu sur sa ligne).
- [ ] **R7** Hors connexion : impossible, message neutre, aucune écriture en file d'attente.

### Quitter
- [ ] **Q1** « Quitter la Miff » demande une confirmation ; le lot supprime fiche, statut, photo et index.
- [ ] **Q2** Après le départ : surveillance des lieux de cette Miff arrêtée ; cache local purgé ; retour au Feed (autre Miff, sinon état vide).
- [ ] **Q3** L'index d'appartenance est libéré dans le même lot (la personne peut ensuite créer ou rejoindre une autre Miff, dans la limite que fixera le ticket Premium).
- [ ] **Q4** Hors connexion : impossible, message neutre.

### Supprimer
- [ ] **S1** Une confirmation indique le nombre d'autres proches et « Cette action est définitive » (aucun « irrévocable », aucun vouvoiement).
- [ ] **S2** Ordre : retrait de chaque autre membre (un lot chacun, code renouvelé), lieux par lots de 5 au plus, lot final : Miff, pierre tombale, code, son propre statut, photo, fiche et index.
- [ ] **S3** Une progression calme s'affiche ; l'app reste utilisable pour quitter l'écran ; l'opération continue tant que l'app est ouverte.
- [ ] **S4** **Reprise** : si l'app est tuée ou le réseau coupé après N retraits, après les lieux ou avant le lot final, l'opération reprend au lancement suivant et aboutit (test pour chaque point d'interruption).
- [ ] **S5** Après la suppression : index libéré, retour au Feed ; la pierre tombale existe (une recréation est refusée par les règles : test de non-régression déjà couvert à ne pas casser).
- [ ] **S6** Le bouton facultatif « Prévenir mes proches » ouvre la feuille de partage avec un message prérempli (sans code ni identifiant) **avant** la suppression ; l'app n'envoie rien toute seule, ne sait pas qui l'a reçu, et ne bloque pas la suppression si la personne ne l'utilise pas.
- [ ] **S7** Hors connexion : impossible de démarrer ; coupure en cours : pause puis reprise.
- [ ] **S8** Les procédures de retrait et de suppression sont exposées sous forme de composants **appelables sans interface** (pour AUTH-5 et le ticket Premium), avec leurs propres tests.

### Accès perdu
- [ ] **D1** Retrait ou suppression constatés pendant que l'app est ouverte : un seul message « Tu n'as plus accès à la Miff « X »… », sans boucle d'erreurs ni écran technique.
- [ ] **D2** Au lancement, avec réseau, l'accès est vérifié **auprès du serveur** avant d'afficher les données de la Miff ; hors connexion, le cache s'affiche avec une mention calme.
- [ ] **D3** **Mode avion ≠ accès perdu** (aucune conclusion de retrait sur une erreur réseau).
- [ ] **D4** Nettoyage : écouteurs et surveillance des lieux arrêtés, lot « Quitter » tenté, sinon suppression de l'index seul, cache purgé.
- [ ] **D5** Après nettoyage, bascule vers une autre Miff ou état vide du Feed.
- [ ] **D6** Le message est le même pour un retrait et pour une suppression de la Miff (test avec deux comptes).
- [ ] **D7** **Retrait pendant la lecture** : un membre qui regarde l'écran de la Miff au moment du retrait reçoit le message et ne voit plus aucune donnée (test à deux appareils).

### Hors connexion et robustesse
- [ ] **H1** Créer, rejoindre, retirer, quitter, renouveler, supprimer, arrêter et reprendre le partage affichent le succès **après l'accusé du serveur** ; en mode avion, aucune de ces écritures n'est exécutée au retour du réseau.
- [ ] **H2** Un double appui ne produit qu'une opération.
- [ ] **H3** Rotation ou arrière-plan en cours d'opération : pas de doublon, l'opération aboutit ou reprend.
- [ ] **H4** Quota ou App Check refusés : message « Pas de connexion pour le moment. », jamais le mot « quota ».
- [ ] **H5** En lecture hors connexion, les boutons d'action sont désactivés avec une mention calme.

### Vie privée et ton
- [ ] **V1** Aucune coordonnée, adresse ou nom de lieu n'est écrit, journalisé, envoyé en Analytics ou placé dans un message de partage.
- [ ] **V2** Le code, le nom de la Miff et les prénoms n'apparaissent dans aucun journal, événement Analytics ni rapport de plantage.
- [ ] **V3** Revue des textes : tutoiement partout ; aucun mot de la liste interdite ; chaque erreur propose une issue.
- [ ] **V4** Aucun écran de ce ticket ne donne au créateur une information sur un membre que ce membre ne voit pas aussi sur le créateur.

### Transverses
- [ ] **T1** Tous les textes en français et en anglais, clés `miff_*` identiques sur les deux plateformes (contrôle de parité de la CI) ; pluriels par le mécanisme de la plateforme ; aucun texte en dur.
- [ ] **T2** Accessibilité : code lu par groupes ; zones tactiles d'au moins 48 dp (Android) ou 44 pt (iOS) ; contraste conforme ; texte agrandi sans chevauchement ; focus après chaque dialogue ; textes `_a11y` pour les boutons icônes ; **texte de partage lisible en entier au lecteur d'écran avant le bouton**.
- [ ] **T3** Aucune valeur de design en dur ; mise en page début/fin.
- [ ] **T4** Mesure des lectures : ouvrir l'écran d'une Miff de N membres coûte au plus N + 4 lectures ; entrée et création : lectures de règles mesurées **avec le statut initial dans le lot** et marge d'au moins 2 sur 20.
- [ ] **T5** App Check initialisé avant toute requête ; aucun secret dans le dépôt ; **aucune règle modifiée par cette PR**.
- [ ] **T6** Aucun nouvel événement Analytics (liste blanche inchangée).

### Android
- [ ] **AND1** Le bouton Retour ferme les dialogues sans agir ; pendant la suppression, il quitte l'écran sans interrompre l'opération.
- [ ] **AND2** Test sur l'OPPO Find X5 Lite : app tuée pendant une suppression, reprise au lancement.
- [ ] **AND3** Le clavier de saisie du code est ASCII, majuscules, sans suggestions.
- [ ] **AND4** Tests unitaires et lint passent dans la CI ; aucune permission nouvelle dans le manifeste.

### iOS
- [ ] **IOS1** Vérification par TestFlight sur l'iPhone 11 Pro ; `PasteButton` pour coller ; feuille de partage native.
- [ ] **IOS2** Action destructrice marquée comme telle ; VoiceOver annonce le code par groupes, le texte de partage et les actions d'un proche.
- [ ] **IOS3** Suppression interrompue (app fermée) : reprise au lancement.
- [ ] **IOS4** Le contrat de purge du cache est vérifié sur appareil.

### Ce que le PM peut vérifier lui-même sur téléphone
- [ ] **PM1** Créer une Miff, partager le code vers une messagerie, copier le code, relire le message reçu.
- [ ] **PM2** Avec un second compte, lire le texte de partage puis rejoindre ; essayer un faux code ; essayer l'ancien code après renouvellement.
- [ ] **PM3** Arrêter puis reprendre le partage ; refuser la permission de localisation et constater que l'entrée reste possible.
- [ ] **PM4** Retirer le second compte : le code change ; l'ancien ne marche plus ; le second compte voit le message calme.
- [ ] **PM5** Supprimer la Miff avec « Prévenir mes proches » ; couper le réseau pendant l'opération et vérifier la reprise.
- [ ] **PM6** Mode avion : créer, rejoindre, supprimer : messages calmes, rien d'exécuté plus tard.
- [ ] **PM7** (avec l'iPhone) deux appareils : retirer un compte pendant qu'il regarde l'écran de la Miff.
- [ ] **PM8** Relire chaque texte à voix haute : est-ce que je dirais cela à un proche inquiet ?

### Pour le QA (`qa-tester`)
- [ ] **QA1** Tableau de couverture critère par critère, avec les cas à deux comptes.
- [ ] **QA2** Interruptions : tuer l'app à chaque étape de la suppression, de la création et de l'entrée ; aucun doublon ni Miff fantôme.
- [ ] **QA3** Même scénario sur Android et iOS : mêmes textes, mêmes états, mêmes résultats.
- [ ] **QA4** Revue des journaux de la build debug : aucun code, nom ou prénom.
- [ ] **QA5** Les règles inchangées passent toujours les tests du dépôt (`backend/tests`).

### Définition de « terminé »
Tous les critères de la plateforme concernée cochés ; revue de code en contexte neuf et revue sécurité faites ; QA faite ; recette du PM faite ; « prêt à merger » validé par un « oui » explicite du PM ; fusion par Claude.

---

## 17. Dépendances

| Avec | Nature |
|---|---|
| **INIT-1, INIT-2, INIT-4** | Socle Android et iOS, App Check, textes, tokens (fusionnés) |
| **AUTH-1 / AUTH-3** | Session, routage au lancement, déconnexion avec purge. **Non fusionnés au moment de la rédaction** : MIFF-1 attend AUTH-1 |
| **AUTH-5** | Dépend de MIFF-2 (procédures de retrait et de suppression) |
| **DUR-1 à DUR-3** | Règles de sécurité (déployées selon le PM, à confirmer) ; contrat de purge du cache |
| **`backend-data`** | Test à l'émulateur et mesure du lot « fiche avec partage activé + statut initial » (MIFF-0) ; commentaire d'en-tête des règles ; évolutions de §9 si le PM les veut |
| **Onboarding** | Appelle nos écrans ; à répercuter : téléphone obligatoire (schéma à étendre), permission de localisation et obligation d'usage (§4 #18, #19) |
| **Localisation / Statut** | Contrat à la frontière (§6) : consentement et statut initial écrits par nous, suite chez eux |
| **Lieux de confiance** | Liste des lieux dans l'écran de la Miff ; création et édition chez eux |
| **Premium / Paywall** | **Limites gratuites, paywall, fin d'abonnement (QM7)** : ce ticket n'en spécifie rien ; le ticket Premium réutilise nos procédures sans écran |
| **Feed** | Ticket distinct (§7) ; contient les questions ouvertes sur « Localiser », le téléphone, l'état vide et la bulle verte |
| **Réglages** | Entrée « Mes Miffs » |
| **`design-system`** | Écrans et composants de §3.3 |
| **`release-manager`** | Relecture des règles Apple / Google (partage de position, anti-stalkerware) avant soumission (R1) |
| **Site d'AUTH-W** | Page d'atterrissage du message de partage (QM14) |
| **Actions du PM** | §11 |

---

*Aucun fichier du projet autre que ce ticket et `miff-questions-pm.md` n'a été modifié par cette étape. Notion (hors les nouvelles pages MIFF et FEED) et Figma n'ont pas été modifiés. Aucune commande Firebase n'a été exécutée.*
