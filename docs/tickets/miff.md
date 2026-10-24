# Ticket — Miff : créer, rejoindre et gérer son cercle (et frontière avec le Feed)

**Statut** : brouillon issu de l'étape « challenge » ; **15 questions en attente du PM** (`docs/tickets/miff-questions-pm.md`) ; aucun développement lancé. Les recommandations de ces questions sont appliquées comme **hypothèses par défaut** dans tout le document (voir §15) : si le PM tranche autrement, seuls les passages indiqués changent.
**Rédigé le** : 2026-10-24 (agent `product-specs`, étape « challenge » du process par ticket).
**Fusionne** : les tickets Notion « Miff » (statut Notion : « Spec en attente de review IA ») et « Miff — créer / rejoindre / gérer un groupe » (statut « Backlog discovery », miroir de `docs/specs/miff-groupes.md`, qui devient redondant : à clore ou à lier par le PM une fois ce ticket validé). Le ticket « Feed » (Notion, « Spec en attente de review IA ») et son miroir `docs/specs/feed-cercle-de-confiance.md` sont **traités comme un ticket distinct** (§7) ; seuls leurs constats sont consignés ici. Notion n'a pas été modifié, aucun statut n'a été changé.
**Sources lues** : Notion « Miff » (racine du projet : pitch et règles métier), « Miff » (tâche), « Miff — créer / rejoindre / gérer un groupe », « Feed », « Feed — cercle de confiance » ; en contexte : « Onboarding » (écrans création et saisie du code), « Premium », « Paywall / Premium », « Paramètre » ; `CLAUDE.md` ; `docs/tickets/authentification.md` (§5, §7.6, §7.7, §15 AUTH-5, §17 ; **non remis en cause** : suppression du compte d'un créateur = ses Miffs supprimées) ; `docs/tickets/durcissement-regles-firestore.md` (§3 à §6, §10, §15) ; `docs/tickets/initialisation-technique.md` (§3, §9, §10) ; `backend/data-model.md` (§1 à §10, §12 à §15) ; `backend/firestore.rules` ; `design/design-system.md` ; `docs/contracts/analytics-events.md` et `i18n-keys.md` ; `docs/specs/*.md` (miff-groupes, feed, onboarding, paywall, lieux, notifications, profil).
**Figma (lecture seule)** : « Miff - Claude » (`RbX84qKbxppvAy5iypLYP2`) : écrans 11 « Choisissez comment commencer » (`37:21`) et 12 « Nouvelle Miff » (`40:32`) consultés (seule la page Onboarding y est reconstruite : **les pages Miff, Feed et Pop ins n'y existent pas encore**). Fichier original « Miff - App » (`Byi9NgQxkBPbmbhU403HSf`), consulté en lecture seule faute de mieux : page « Miff » (`13:1948` : écran d'une Miff existante `35:3367`, confirmation de suppression `69:600`, saisie du code `117:86` et son erreur `117:295`) et page « Pop ins » (`69:12`, Feed avec « + Ajouter un proche »). Rien n'a été modifié.
**Limite de lecture** : les images insérées dans les pages Notion (maquettes embarquées) ne sont pas lisibles par l'outil Notion ; les écrans viennent donc de Figma. Si une maquette Notion diffère du Figma, ce n'est pas détectable.
**Convention** : « vérifié » = confirmé sur une source du dépôt ou de la documentation officielle. « À vérifier » = non confirmé. Aucune commande Firebase n'a été exécutée, aucun fichier de `backend/` n'a été modifié.

---

## 1. Résumé pour le PM

En une phrase : permettre à quelqu'un de **créer son cercle en quelques secondes, d'y faire entrer ses proches avec un code qui se partage d'un geste, de le faire vivre (retirer, quitter, supprimer) sans jamais mettre quelqu'un mal à l'aise**, tout en gardant la promesse du produit : personne n'est dans un cercle sans l'avoir choisi, et personne ne partage sa position sans l'avoir décidé.

Le backend est prêt (règles DUR-1 et DUR-2 déployées, selon le PM) : ce ticket est **100 % côté apps** (Android puis iOS). Il ne demande ni Cloud Function, ni Blaze, ni coût : **0 €**.

Ce que le challenge change par rapport aux tickets Notion (détails en §4) :

| # | Constat | Conséquence |
|---|---|---|
| 1 | **Le créateur ne peut pas quitter sa Miff sans la supprimer pour tous** (règles : le créateur ne part qu'en supprimant la Miff ; propriétaire immuable ; pas de transfert). Les maquettes n'ont ni « Quitter » ni écran de membre simple. | Pour le créateur, une seule sortie : « Supprimer la Miff », avec un texte clair. Le transfert de propriété demanderait un changement de règles : hors v1 (QM1). |
| 2 | **Rejoindre une Miff n'est pas partager sa position** (le modèle de données sépare les deux, par Miff). Or le PM écrit que l'app est inutilisable sans « Toujours autoriser » et enchaîne « code valide → Feed ». Un nouvel arrivant ne peut pas voir les lieux de la Miff **avant** d'y entrer (règles) : consentir « à l'aveugle » serait fragile. | Rejoindre ne partage rien. L'étape suivante (ticket Localisation) montre les lieux et les proches, puis demande un « oui » explicite ; « Pas maintenant » reste possible (QM2). |
| 3 | **Le code peut fuiter sans que personne ne le sache** : n'importe quel membre le voit et le partage (Notion : « Ajouter un proche » dans le Feed), et il n'y a pas de validation à l'arrivée. Un inconnu muni du code verrait les statuts de tout le cercle dès qu'il partage lui-même le sien. | Visibilité du « nouveau membre » dans l'app (gratuit, sans serveur), retrait + code renouvelé automatiquement, renouvellement manuel. Validation par le créateur et expiration du code : non retenues en v1, avec leur raison (QM3, QM4). |
| 4 | **Messages d'accès perdu** : le ticket durcissement prévoit deux messages (« Tu ne fais plus partie de… » / « La Miff n'existe plus »). En réalité, quand un créateur supprime sa Miff, les autres membres sont **retirés un par un avant** : ils verraient donc le premier message, qui sonne comme une exclusion personnelle alors que la Miff a juste été fermée. | Un seul message neutre, vrai dans tous les cas : « Tu n'as plus accès à la Miff « X ». » (QM8). |
| 5 | **Le nom** : Notion dit 2 à 50 caractères ; les règles (déjà déployées) acceptent 1 à 40. | 2 à 40 (QM6). Garder 50 obligerait à redéployer des règles pour rien. |
| 6 | **Le code s'affiche avant l'enregistrement** dans Figma, alors que Notion dit qu'il devient actif à l'enregistrement. Quelqu'un qui le partage avant d'enregistrer, puis abandonne, a distribué un code mort. | Création en deux temps : le nom d'abord, puis l'écran « Ta Miff est prête » avec le code et le partage (QM15). |
| 7 | **Hors connexion** : une écriture Firestore faite sans réseau est mise en file d'attente et rejouée plus tard, sans que l'utilisateur le sache. Pour créer, rejoindre, retirer ou supprimer, c'est dangereux (« j'ai rejoint » alors que non ; un retrait exécuté dix minutes plus tard). | Ces six actions exigent l'accusé de réception du serveur ; sinon message neutre et rien en attente (§5.11). |
| 8 | **Le Feed contredit le modèle de confidentialité** : boutons « Localiser » (dernière position connue, que **nous ne stockons pas par décision**), « Message » et « Appeler » (aucun numéro de téléphone n'existe dans le schéma ni dans les profils partagés), phrase « Tout le monde est en sécurité » affichée même sans Miff. | Le Feed devient un **ticket distinct** (§7, QM10 à QM12) ; ce ticket-ci ne livre aucun statut. |
| 9 | **Le nombre de membres ne peut pas être plafonné par les règles** (pas de compteur, et le candidat ne peut pas lire la liste avant d'entrer). | Pas de limite garantie en v1 ; plafond « doux » côté app et suivi des lectures (QM5). |
| 10 | **Fin d'abonnement Premium** : les Miffs « en trop » **créées** par l'utilisateur seront supprimées (décision PM du 2026-10-24) : tous leurs membres perdent leur cercle. | Rien à implémenter ici, mais les procédures « Retirer » et « Supprimer » de ce ticket doivent être **réutilisables sans écran** par le ticket Premium (§13) ; risque noté (§14, R9). |

**Taille globale (relative, sans durée)** : L à XL (Android : 3 PR, iOS : 3 PR, §13). **Coût : 0 €.**

---

## 2. Périmètre

### Dans le périmètre
- Écran de choix « Créer une nouvelle Miff / Rejoindre une Miff existante » (destination des boutons de l'onboarding et de l'état vide du Feed ; les boutons eux-mêmes appartiennent à ces tickets).
- **Créer** une Miff : nom, enregistrement, écran « Ta Miff est prête » avec code et partage.
- **Rejoindre** par code : saisie, vérifications, confirmation, entrée dans la Miff (sans partage de position).
- **Écran de la Miff** (créateur et membre) : nom, code, partage, liste des proches (nom, photo, rôle), section lieux (liste ; création et édition : ticket Lieux), actions selon le rôle.
- **Renouveler le code** (manuel), invitation discrète à le renouveler après un départ.
- **Retirer un proche** (créateur), **quitter** (membre), **supprimer la Miff** (créateur, procédure reprenable).
- **Limites freemium** appliquées à la création et à l'entrée dans une Miff (blocage et appel du paywall ; le paywall lui-même est un autre ticket).
- **Accès perdu** : détection, message, nettoyage (arrêt des écoutes et des lieux surveillés, purge du cache).
- Visibilité d'un nouveau membre, états vides, hors connexion, textes FR + EN au ton bienveillant, accessibilité.
- Procédures « Retirer » et « Supprimer » conçues pour être **réutilisées sans interface** (suppression de compte AUTH-5, fin d'abonnement Premium).

### Hors périmètre
- **Le Feed** et tout affichage de statuts, batterie, photos d'update, bulle de la Miff, sélecteur de Miff, boutons Localiser / Message / Appeler : ticket Feed (§7).
- **Consentement au partage de position** (écran d'explication, activation/révocation, permissions système) : tickets Localisation / Statut ; ce ticket définit seulement le contrat à la frontière (§6).
- Création, modification et suppression des **lieux** : ticket Lieux de confiance (la section n'est ici qu'une liste et un point d'entrée).
- **Paywall et abonnement** : ticket Premium / Paywall ; fin d'abonnement et suppression des éléments en trop : ticket Premium.
- **Profil** (prénom, nom, photo, téléphone) : ticket Onboarding ; **Réglages** : ticket Réglages ; **suppression de compte** : AUTH-5 (qui réutilise nos procédures).
- Notifications push (exigent Blaze) : aucun membre n'est averti « en direct » d'un retrait, d'un départ ou d'une suppression ; tout s'apprend à l'ouverture de l'app ou pendant qu'elle est ouverte.
- Transfert de propriété, plusieurs créateurs, rôles intermédiaires, validation d'une arrivée par le créateur, liste de personnes bannies, invitation par e-mail ou par SMS, lien d'invitation qui ouvre l'app (liens universels), photo ou description de la Miff, historique des membres, expiration du code, plafond garanti du nombre de membres.
- Nouveaux événements Analytics (aucun n'est ajouté, §5.12).

---

## 3. Parcours et états d'écran

### 3.1 Parcours nominaux

```
Écran de choix (onboarding, ou état vide du Feed)
  ├─ « Créer une nouvelle Miff » ─► [limite gratuite ?] ─► Nouvelle Miff (nom) ─► Enregistrer
  │        └─► « Ta Miff est prête » : code + Partager / Copier ─► Étape consentement au partage (ticket Localisation) ─► Feed
  └─ « Rejoindre une Miff existante » ─► Saisie du code ─► [déjà membre ? limite gratuite ?]
           ─► Confirmation « Rejoindre la Miff « X » ? » ─► (entrée, rien n'est partagé)
           ─► Étape consentement au partage (ticket Localisation) : lieux + proches affichés, « Oui, partager » / « Pas maintenant » ─► Feed

Écran de la Miff (créateur)  : nom (modifiable) · code + Partager/Copier · Renouveler le code · proches (« ⋯ » ► Retirer) · lieux · Supprimer la Miff
Écran de la Miff (membre)    : nom · code + Partager/Copier · proches · lieux · Quitter la Miff

Accès perdu (à l'ouverture, ou en direct si l'app est ouverte) ─► « Tu n'as plus accès à la Miff « X » » ─► nettoyage ─► autre Miff ou état vide du Feed
```

### 3.2 Écrans et états à couvrir

| Écran | États à couvrir | Dans les maquettes ? |
|---|---|---|
| **A. Choix créer / rejoindre** | normal ; limite gratuite atteinte (les deux choix mènent au paywall) ; hors connexion (le choix reste possible, l'action échouera proprement) | Oui (écran 11 Figma Claude). Le champ de code est **intégré à la carte** « Rejoindre » ; l'original a aussi une page « Rejoindre une Miff » dédiée : **deux versions du même écran** (§4 #9). Aucun état limite. |
| **B. Nouvelle Miff** | vide (bouton grisé) ; 1 caractère (grisé) ; 2 à 40 caractères (actif) ; 40 atteints ; enregistrement en cours ; hors connexion ; erreur | Vide : oui (écran 12 : nom, code, proches, lieux, deux boutons de validation). États d'erreur, chargement, hors connexion : non. **Le code et le partage y apparaissent avant l'enregistrement** (QM15). |
| **C. « Ta Miff est prête »** (nouveau) | code affiché par groupes de 4 ; partage ouvert ; code copié (confirmation discrète) | Non. À dessiner. |
| **D. Saisie du code** | vide ; saisie partielle ; 12 caractères valides (bouton actif) ; caractère hors alphabet (message local) ; vérification en cours ; code introuvable ; **pause après 5 échecs** ; hors connexion ; erreur réseau ou quota ; déjà membre ; limite gratuite | Vide et erreur « Le code saisit est invalide » (original, faute de frappe) : oui. Les autres : non. |
| **E. Confirmation de rejoindre** (nouveau) | nom de la Miff ; en cours ; succès ; refus (code renouvelé entre-temps) ; hors connexion | Non. À dessiner. Le modèle de données le prévoit (`data-model.md` §5, étape 2). |
| **F. Écran de la Miff, créateur** | nom modifiable (Enregistrer actif seulement si modifié et valide) ; code ; liste des proches (0, 1, N) ; proche « nouveau » ; section lieux (aucun, N) ; renouvellement en cours ; hors connexion (lecture seule) | Oui, dans l'original (`35:3367`, `35:3565`, `69:600`) avec **deux défauts** : le titre reste « Nouvelle Miff » ; chaque proche a une **corbeille rouge** sans confirmation dessinée. |
| **G. Écran de la Miff, membre** (nouveau) | mêmes sections en lecture seule ; « Quitter la Miff » ; pas d'Enregistrer ni de corbeille | **Absent** (ni Figma, ni Notion). |
| **H. Retirer un proche** | confirmation (prénom) ; en cours ; succès avec **nouveau code** et partage ; cible déjà partie ; erreur ; hors connexion | Absent. |
| **I. Quitter la Miff** | confirmation ; en cours ; succès ; hors connexion | **Absent** : la spec « Backlog » parle d'un écran de confirmation vu dans les maquettes, mais **seule la suppression de Miff en a un** (`69:600`). |
| **J. Supprimer la Miff** | confirmation (nombre de proches) ; progression ; interrompue puis reprise ; terminée ; hors connexion | Confirmation : oui (« Etes-vous sûr de supprimer cette miff ? … de manière irrévocable » : vouvoiement et mot anxiogène). Progression, reprise : non. |
| **K. Renouveler le code** | confirmation ; succès (nouveau code) ; invitation discrète après un départ ; hors connexion | Absent. |
| **L. Accès perdu** | message unique ; bascule vers une autre Miff ou vers l'état vide | Absent. |
| **M. Limite gratuite** | blocage avant écriture ; appel du paywall ; retour sans reproche | Le paywall existe (écran 15). Le déclencheur « Miff en plus » : non. |

### 3.3 Écrans à dessiner ou à corriger (`design-system`, avant le développement)

C (« Ta Miff est prête »), E (confirmation de rejoindre), G (vue d'un membre simple), H, I, K, L (boîtes de dialogue : un modèle unique de dialogue de confirmation, ton calme), états d'erreur et de chargement de B et D, ligne de proche avec menu « ⋯ » à la place de la corbeille rouge, étiquettes « Créateur », « Toi », « Nouveau », champ de code par groupes de 4, titre de l'écran F au nom de la Miff. Composants à extraire (absents de `design/design-system.md` §4) : champ de saisie, ligne de proche, dialogue de confirmation, carte de choix, pastille de rôle.

---

## 4. Ambiguïtés, contradictions et incohérences relevées

Rien n'est tranché ici sans le dire. Chaque point renvoie à la proposition retenue (§5) ou à une question (`miff-questions-pm.md`).

| # | Point | Sources en conflit | Traitement proposé |
|---|---|---|---|
| 1 | **Nom** : 2 à 50 caractères (Notion Onboarding et Miff) contre 1 à 40 (règles déployées, `miffs.name` et `joinCodes.miffName`). | Notion vs `firestore.rules` | 2 à 40 (QM6). Le minimum de 2 est une règle d'écran (les règles acceptent 1). |
| 2 | **Messages d'accès perdu** : deux messages au ticket durcissement §6.4 ; en pratique, la suppression d'une Miff retire d'abord chaque membre. | Durcissement §4.1 et §6.4 | Un message unique (QM8, §5.10). |
| 3 | **Deux boutons de validation** sur l'écran de création (coche en en-tête et « Enregistrer »). | Figma 12, Notion Onboarding | Un seul bouton principal (charge cognitive). À arbitrer par `design-system`. |
| 4 | **Code visible et partageable avant l'enregistrement** (Figma) contre « au clic sur Enregistrer, le code est actif » (Notion). | Figma 12 vs Notion Miff | Création en deux temps (QM15). Le code n'est généré qu'à l'enregistrement. |
| 5 | **Titre « Nouvelle Miff » conservé** sur l'écran d'une Miff existante. | Figma original | Titre = nom de la Miff. |
| 6 | **« Ajouter/retirer des membres »** : on ne peut pas ajouter quelqu'un (aucune recherche d'utilisateur, aucun numéro) ; on **partage un code**, les gens entrent d'eux-mêmes. | Spec miff-groupes, Notion | « Ajouter un proche » (libellé du PM, conservé) ouvre le partage du code. Aucune fonction « ajouter ». |
| 7 | **« Quitter »** et **écran du membre simple** : cités par la spec, absents partout. | Spec Backlog vs Figma | Écrans G et I à dessiner. |
| 8 | **Corbeille rouge** sur chaque proche : un retrait est irréversible pour la personne (nouveau code) et touche une personne ; une icône de suppression sans confirmation est à la fois risquée et dure. | Figma original | Menu « ⋯ » puis dialogue de confirmation (§5.6). |
| 9 | **Saisie du code : deux versions** : champ dans la carte de l'onboarding (bouton « Suivant » activé à 12 caractères) et page « Rejoindre une Miff » (bouton « Rejoindre », erreur « Le code saisit est invalide »). | Notion Onboarding vs Figma original | Un seul composant de saisie, réutilisé aux deux endroits. Texte d'erreur adouci (§5.10). |
| 10 | **Entrée de l'écran de gestion non définie** : Notion dit « lorsqu'on clique sur une Miff existante » sans dire où (le Feed a un menu déroulant ; « Paramètre » ne cite pas les Miffs). | Notion Miff, Feed, Paramètre | QM13. |
| 11 | **Créateur de plusieurs Miffs** : phrase inachevée dans la page racine (« Si on est le créateur de plusieurs miff, »). | Notion racine | Sans conséquence : chaque Miff a ses lieux ; premium par utilisateur (`data-model.md` §15, décision 3). |
| 12 | **« Ajouter un proche » dans le Feed pour tous** (Notion) : tout membre partage le code, donc le code est visible de tous (hypothèse Q7 du modèle de données, jamais tranchée). | Notion Feed, `data-model.md` §15 | À confirmer (QM3). |
| 13 | **Téléchargement dans le message de partage** : Notion demande « un lien de téléchargement de l'app » ; l'app n'est pas publiée. | Notion Feed | QM14. |
| 14 | **Où sont ajoutés les lieux à la création** ? L'écran 12 montre une section « Lieux de confiance » dès la création, alors que les lieux sont des documents de la Miff (la Miff doit exister). | Figma 12 | Frontière : la section liste les lieux et renvoie au ticket Lieux ; elle n'écrit rien. |
| 15 | **Statut des règles dans le dépôt** : l'en-tête de `backend/firestore.rules` dit « JAMAIS déployées » alors que `data-model.md` et `backend/README.md` indiquent DUR-1 et DUR-2 déployées (information du PM, à confirmer par la date de publication dans la console). | `firestore.rules` vs `data-model.md` | À faire corriger par `backend-data` (commentaire seulement) ; le PM confirme la date de publication avant la recette (§11). |
| 16 | **Spec locale `miff-groupes.md`** : ses 4 questions ouvertes ont maintenant une réponse (retrait : créateur seul ; historique : aucun, le statut est supprimé ; transfert : non ; régénération : oui, à chaque retrait et à la demande). | Spec vs décisions | À mettre à jour ou à clore par le PM après validation. |
| 17 | **Hors ticket mais signalé** : Notion Premium parle d'un paywall pour qui veut créer un lieu « alors qu'il en a déjà 2 », alors que le gratuit est limité à **1** lieu partout ailleurs. | Notion Premium vs reste | À reporter au challenge Premium / Lieux. |
| 18 | **Hors ticket mais signalé** : Notion Onboarding impose « Toujours autoriser » pour utiliser l'app, alors que le consentement du modèle de données est par Miff et révocable. | Notion Onboarding vs `data-model.md` §4 | À trancher avec QM2 (frontière), puis dans le ticket Localisation. |

---

## 5. Règles métier

### 5.1 Rôles et droits

| Action | Créateur | Membre |
|---|---|---|
| Voir nom, code, liste des proches, lieux | Oui | Oui |
| Partager / copier le code | Oui | Oui (hypothèse QM3) |
| Renommer la Miff | Oui | Non |
| Renouveler le code | Oui | Non |
| Retirer un proche | Oui | Non |
| Créer, modifier, supprimer un lieu | Oui (ticket Lieux) | Non |
| Quitter la Miff | **Non** : il ne peut que la supprimer | Oui |
| Supprimer la Miff | Oui | Non |
| Lire les statuts des autres | Seulement s'il partage et a publié son propre statut (réciprocité, **comme tout membre**) | Idem |

Le créateur n'a **aucun droit de lecture supplémentaire** sur la position ou le statut d'un membre : ce qu'il gagne, c'est la maîtrise du cercle (qui y est, quels lieux), jamais la surveillance (§5.9).

### 5.2 Nom
- 2 à 40 caractères après suppression des espaces au début et à la fin ; aucun caractère de contrôle ; emojis acceptés. **À vérifier à l'émulateur** : la façon dont les règles comptent la longueur d'un texte contenant des emojis ou des caractères composés ; en cas de doute, le client limite par prudence (compter en unités UTF-16) pour ne jamais être refusé par les règles.
- Deux Miffs peuvent porter le même nom (aucune unicité). Le nom est visible de **toute personne qui détient le code** (il s'affiche à la confirmation) : le dire sous le champ (« Les personnes qui reçoivent ton code verront ce nom »).
- Le renommage met à jour la Miff **et** le nom conservé avec le code dans le même lot (`data-model.md` §8, « Renommer »).

### 5.3 Code secret
- **Format** : 12 caractères parmi `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 symboles, sans I, L, O, 0, 1) ; généré **par le téléphone** avec un générateur cryptographique (rejet pour éviter le biais) ; jamais un code dérivé d'une donnée (nom, date). Test unitaire identique sur les deux plateformes (format, alphabet, absence de biais manifeste sur un grand échantillon).
- **Affichage** : par groupes de 4 (« XTZR SP36 7988 »), police à chasse fixe ; le lecteur d'écran l'annonce par groupes. Il est visible de tous les membres (hypothèse QM3), jamais dans les journaux, jamais dans un événement Analytics, jamais dans un rapport de plantage.
- **Saisie** : majuscules automatiques ; pas de correction automatique ni de suggestions ; espaces, tirets et minuscules acceptés puis normalisés ; bouton actif seulement à 12 caractères valides ; un caractère hors alphabet donne un message **local** (aucune lecture réseau, donc aucune lecture facturée). Pas de lecture automatique du presse-papiers : un bouton « Coller » déclenché par la personne.
- **Partage** : feuille de partage native avec un message prêt à envoyer : nom de la Miff, code, lien (QM14) ; « Copier » copie le code seul (Android 13 et plus : contenu marqué sensible pour masquer l'aperçu). Aucun autre champ.
- **Renouvellement** : (a) **automatique à chaque retrait** (imposé par les règles) ; (b) **manuel**, à la demande du créateur ; (c) **invitation discrète** au créateur après un départ volontaire (décision PM du 2026-10-22, Q1 : « Un membre est parti : souhaites-tu renouveler le code ? », une seule fois par départ, sans insister). Dans tous les cas, l'ancien code cesse de fonctionner immédiatement ; les membres actuels restent dans la Miff et voient le nouveau code en direct.
- **Durée de vie** : aucune expiration en v1 (QM4). Un code libéré (renouvellement ou suppression) peut en théorie être réutilisé par n'importe qui pour sa propre Miff ; probabilité négligeable (`data-model.md` §8, contrat client).
- **Essais** : au bout de 5 échecs consécutifs, pause de 60 secondes (message bienveillant) ; compteur remis à zéro après un succès ou 10 minutes. C'est une **aide d'interface, pas une protection** (un client modifié la contourne) ; la protection est l'entropie du code et App Check (`data-model.md` §5).

### 5.4 Rejoindre : ordre des vérifications
1. **Profil présent** (routage `authentification.md` §5) : sinon retour à l'étape profil. Les règles n'exigent pas le profil côté serveur (§9).
2. **Format** du code (local).
3. **Lecture du code** (1 lecture) : « introuvable » = un seul message, quelle que soit la cause (code faux, code renouvelé, Miff supprimée). On ne dit jamais à une personne retirée que son ancien code a été remplacé.
4. **Nettoyage d'un index obsolète** : si l'index d'appartenance gratuit pointe vers une Miff où la personne n'a plus accès (retirée, Miff supprimée), le nettoyer **avant** de conclure à une limite (sinon on afficherait un paywall injustifié, `data-model.md` §6 (b)).
5. **Déjà membre de cette Miff** (comparaison avec les index `memberships`, **jamais avec l'identifiant du document** : lire le champ `miffId`) : message et bouton vers la Miff, aucune écriture.
6. **Limite gratuite** : voir §5.5.
7. **Confirmation** : « Rejoindre la Miff « X » ? » avec ce que cela implique (§5.10). Aucune écriture avant « Rejoindre ».
8. **Lot unique** de 2 écritures (fiche membre avec le code utilisé, index d'appartenance) ; succès affiché **après l'accusé du serveur**.
9. **Rien n'est partagé** : la fiche membre naît avec le partage désactivé (hypothèse QM2) ; la suite est le ticket Localisation.

### 5.5 Limites freemium
- Gratuit : une seule Miff **à la fois, créée ou rejointe**. Premium : autant que souhaité.
- Le blocage se décide sur le **nombre d'appartenances** (lecture de tous les index `memberships`), pas seulement sur la présence de la clé gratuite : un utilisateur qui était premium peut avoir des index dont la clé est l'identifiant de Miff (`data-model.md` §6, failles résiduelles). Sans cela, un ex-premium pourrait cumuler.
- Le client lit `entitlements/{uid}` pour choisir la clé de l'index (gratuite ou identifiant de Miff). Si les règles refusent (abonnement expiré côté serveur alors que l'app le croyait actif) : relire, puis traiter comme limite atteinte (paywall), sans message technique.
- **Moment du paywall** (QM7) : pour créer, au toucher de « Créer une nouvelle Miff » ; pour rejoindre, **après** que le code a été reconnu et **avant** la confirmation (un paywall ne s'affiche jamais pour un code faux ou une faute de frappe, et il peut dire : « Pour rejoindre « Famille Dupont », passe à Miff + »). Événement `paywall_shown` avec `trigger = extra_miff`.
- **Issue sans achat** : sur l'écran de blocage, un lien discret « Ou quitte « X » pour rejoindre « Y » » (QM7). Un utilisateur gratuit ne doit jamais être coincé.
- Après « Quitter » ou « Supprimer », le slot gratuit est libéré dans le même lot : il peut créer ou rejoindre tout de suite.
- **Fin d'abonnement** : aucune règle ici (ticket Premium). Nos procédures de retrait, de départ et de suppression doivent être appelables par ce ticket sans interface (§13).

### 5.6 Retirer, quitter, supprimer
**Retirer un proche (créateur).** Confirmation nominative ; un membre par lot de 6 écritures dont **le renouvellement obligatoire du code** (ne **rien ajouter** à ce lot : la marge de lectures de règles est de 2 sur 20, `data-model.md` §12) ; ensuite, le nouveau code est affiché avec le partage et un texte calme. L'index d'appartenance de la personne retirée n'est pas touché (elle le nettoie à son prochain accès). Aucune notification : elle l'apprend en direct si son app est ouverte, sinon au lancement.

**Quitter (membre).** Confirmation ; lot de 4 suppressions (fiche, statut, photo, index) ; arrêt de la surveillance des lieux de cette Miff ; purge du cache local (même contrat que la perte d'accès, `data-model.md` §8) ; retour au Feed. Le créateur n'est pas notifié ; il voit le départ à sa prochaine ouverture (invitation de §5.3).

**Supprimer (créateur).** Confirmation avec le nombre de proches. Procédure **reprenable** et **idempotente**, dans cet ordre : (1) retrait de chaque autre membre, un lot chacun (le code change à chaque fois) ; (2) suppression des lieux, 5 au plus par lot ; (3) lot final : Miff + **pierre tombale** + code + son propre statut, photo, fiche et index. Un marqueur local « suppression en cours » (identifiant de la Miff seulement) permet de **reprendre au lancement suivant** si l'app a été fermée ou tuée. Interruption réseau : pause puis reprise. Aucune opération n'est lancée hors connexion. Un texte de progression calme (« On finalise la suppression… »). Option « Prévenir mes proches » : QM9.

**Ce que voient les autres pendant une suppression** : les proches disparaissent un à un de la liste (si l'app est ouverte), puis chacun reçoit le message d'accès perdu (§5.10).

### 5.7 Accès perdu et nettoyage
- **Détection** : (a) en direct, un écouteur est refusé (retrait) ou la Miff est introuvable (suppression) ; (b) au lancement, avant d'afficher les données d'une Miff, un contrôle **auprès du serveur** (si réseau). Un **refus de permission** est un accès perdu ; une **erreur réseau** n'en est pas un (mode avion ≠ retrait).
- **Message unique** (une fois, sans boucle d'erreurs) : « Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. »
- **Nettoyage** (contrat `data-model.md` §8) : arrêter écouteurs et surveillance des lieux ; essayer d'abord le lot « Quitter » complet ; s'il est refusé (la fiche n'existe plus), supprimer l'index seul ; purger le cache local ; supprimer ma photo expirée et celles que je constate expirées chez les autres.
- **Suite** : bascule vers une autre Miff si la personne en a une, sinon état vide du Feed.

### 5.8 Taille d'une Miff
Aucun plafond **garanti** en v1 : les règles ne comptent pas les sous-collections et un candidat ne peut pas lire la liste avant d'entrer. Plafond « doux » de 15 proches côté app (le bouton « Ajouter un proche » est remplacé par un texte calme au-delà), à titre de garde-fou de coût (QM5). Le coût des lectures croît avec le nombre de membres (`data-model.md` §12 : la ligne d'estimation de départ est pour 4 membres).

### 5.9 Vie privée et réciprocité
- Le créateur ne voit rien de plus que les autres membres : **aucun écran de ce ticket n'affiche un statut, une position, une batterie ou une photo d'update**.
- Les membres se voient entre eux par leur prénom, nom et photo de profil (copie dans la Miff). C'est inhérent à l'entrée dans un cercle ; la confirmation de rejoindre le dit en une phrase.
- **Le créateur choisit les lieux surveillés pour tout le cercle** : un membre arrive dans une Miff dont les lieux ne sont pas les siens. D'où : le consentement au partage vient **après** que les lieux sont visibles (QM2), et il est révocable à tout moment (modèle de données §4).
- Rien de ce ticket n'écrit de coordonnées ni d'adresse ; le seul message qui pourrait en contenir (partage du code) n'en porte pas.
- Risque d'**intrus avec le code** : atténué par la visibilité des nouveaux membres, le retrait avec renouvellement automatique, le renouvellement manuel et le consentement séparé ; non supprimé (QM3).

### 5.10 Ton et textes (tutoiement, décision PM du 2026-10-23 ; **tous à valider par le PM**)

Principes : phrases courtes (deux au plus), rien de moralisateur ou d'accusateur ; jamais « expulsé », « banni », « irrévocable », « danger », « urgence » ; une erreur ne dit jamais « tu as fait une erreur ». Les prénoms et noms de Miff s'insèrent par paramètres (pas de phrase assemblée, `i18n-keys.md`).

| Contexte | Proposition |
|---|---|
| Aide sous le nom | « Choisis un nom pour retrouver cette Miff facilement. Les personnes qui reçoivent ton code verront ce nom. » |
| Limite de nom | « Limite de 40 caractères atteinte. » |
| Création réussie | « Ta Miff « {nom} » est prête. Partage ce code avec tes proches pour qu'ils puissent la rejoindre. » |
| Saisie du code (aide) | « Saisis le code que ton proche t'a donné. » |
| Code introuvable | « On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. » |
| Caractère impossible | « Ce code ne ressemble pas à un code Miff. Vérifie les lettres et les chiffres. » |
| Pause après 5 essais | « On fait une petite pause d'une minute avant de réessayer. » |
| Déjà membre | « Tu fais déjà partie de la Miff « {nom} ». » + bouton « Ouvrir la Miff » |
| Confirmation de rejoindre | « Rejoindre la Miff « {nom} » ? Tu feras partie de ce cercle de confiance : les autres membres verront ton prénom et ta photo. Ta position ne sera partagée que si tu le choisis, à l'étape suivante. » |
| Limite gratuite (blocage, avant le paywall) | « Avec la formule gratuite, tu peux faire partie d'une Miff à la fois. » |
| Hors connexion | « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » |
| Erreur générique | « Ça n'a pas fonctionné. Réessaie dans un instant. » |
| Renouveler le code | « Créer un nouveau code ? L'ancien ne fonctionnera plus. Les membres actuels restent dans la Miff. » |
| Invitation après un départ | « Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif. » + « Plus tard » |
| Retirer, confirmation | « Retirer {prénom} de la Miff ? {Prénom} n'aura plus accès à ce cercle. Un nouveau code sera créé : l'ancien ne fonctionnera plus. » |
| Retirer, succès | « {Prénom} ne fait plus partie de la Miff. Voici le nouveau code, à partager avec qui tu veux. » |
| Quitter, confirmation | « Quitter la Miff « {nom} » ? Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code. » |
| Supprimer, confirmation | « Supprimer la Miff « {nom} » ? Les {n} autres proches n'y auront plus accès et les lieux seront effacés. Cette action est définitive. » |
| Supprimer, progression / fin | « On finalise la suppression… » / « La Miff a été supprimée. » |
| Accès perdu | « Tu n'as plus accès à la Miff « {nom} ». Ta position n'est plus partagée avec ce cercle. » |
| Nouveau membre (étiquette) | « Nouveau » (pendant 7 jours après `joinedAt`) ; une ligne unique à l'ouverture : « {Prénom} a rejoint la Miff. » |
| Créateur : pas de « Quitter » | « Tu as créé cette Miff : si tu veux la fermer, tu peux la supprimer. Les autres proches n'y auront alors plus accès. » |
| Plafond doux (QM5) | « Cette Miff est bien remplie ! » (aucun chiffre) |

### 5.11 Hors connexion et robustesse
- **Actions qui exigent le serveur** : créer, rejoindre, retirer, quitter, renouveler le code, supprimer. Elles n'affichent un succès **qu'après l'accusé de réception du serveur** et n'**laissent aucune écriture en file d'attente** : sans réseau, le message neutre s'affiche et l'écriture est annulée (mise en œuvre à confirmer par un petit test des devs : transaction ou annulation de la file ; l'exigence est le comportement, pas la méthode).
- **Lecture** (écran de la Miff, liste des proches) : le cache permet de consulter hors connexion ; les boutons d'action sont désactivés avec une mention calme.
- Un seul appui = une seule opération (bouton désactivé pendant l'opération). Rotation, passage en arrière-plan : l'opération se termine ou reprend, jamais de doublon.
- Quota ou App Check refusés : même message que hors connexion (« Pas de connexion pour le moment »), jamais « quota ».
- **Course** (code renouvelé pendant qu'on le saisit ; créateur qui supprime pendant qu'on rejoint ; deux créateurs d'appareils différents qui renouvellent) : le message unique « introuvable » ou l'accès perdu s'applique ; aucun plantage.

### 5.12 Analytics, notifications
- **Aucun nouvel événement** : on réutilise `miff_created` et `miff_joined` (`is_first_miff`), `onboarding_completed` (`via`), `paywall_shown` / `paywall_dismissed` (`trigger = extra_miff`). Retirer, quitter, supprimer, renouveler : **non mesurés** (sensibles, sans besoin produit actuel). Jamais de nom de Miff, de code ni d'identifiant (règle 3 du contrat). Rien n'est envoyé avant le consentement Analytics.
- **Notifications** : aucune (Spark). Tout ce qui devrait prévenir quelqu'un (nouveau membre, départ, retrait, suppression) passe par l'affichage à l'ouverture. À rouvrir quand Blaze sera décidé.

---

## 6. Frontières avec les tickets voisins

| Ticket | Frontière |
|---|---|
| **Onboarding** | Il porte les écrans profil et téléphone, le routage de reprise et le texte d'accueil ; il **appelle** nos écrans A, B, C, D, E. La fin d'onboarding = une appartenance existe (`memberships` non vide). Contrat inchangé : le routage se déduit des données (`authentification.md` §5). Retour arrière : pas de flèche sur la première étape après création du compte (décision PM Q11). **À reporter** : « Toujours autoriser obligatoire » (§4 #18) ; numéro de téléphone sans usage défini (recommandation maintenue : le retirer de la v1). |
| **Lieux de confiance** | Ils créent, modifient et suppriment les lieux. Notre écran n'affiche qu'une liste (nom, catégorie) et un point d'entrée. Les lieux sont lisibles de tous les membres (ils en « héritent »). Un compte gratuit n'a qu'un lieu `p1` de type confiance ; une zone à risque exige le premium du créateur. |
| **Localisation / Statut** | Le consentement au partage est **leur** écran. Contrat à la frontière : (1) créer ou rejoindre écrit la fiche avec `sharing.enabled = false` ; (2) aussitôt après, l'app présente leur étape de consentement avec les lieux et les proches visibles ; (3) « Oui » = un lot (fiche + création du statut initial `presence = "unknown"`, `data-model.md` §8) ; « Pas maintenant » est autorisé et mène au Feed avec un état neutre (« Active le partage pour voir ton cercle ») ; (4) quitter, retirer ou être retiré supprime statut et photo dans le même lot. |
| **Feed** | Voir §7. Nous livrons les destinations (créer, rejoindre, écran de la Miff) et la liste des proches ; le Feed livre l'en-tête, l'état vide, le sélecteur, le bouton « + Ajouter un proche » (qui ouvre **notre** partage), la bulle et les cartes. |
| **Paywall / Premium** | Il fournit l'écran et l'achat ; nous fournissons le déclencheur (§5.5). **Tant qu'il n'est pas livré**, le blocage affiche l'écran de limite sans bouton d'achat (« Cette option arrive bientôt »). Le ticket Premium décide aussi du préavis et du choix de l'élément conservé à la fin d'abonnement (§13, R9). |
| **Réglages** | Un éventuel « Mes Miffs » n'est qu'un autre point d'entrée de l'écran F/G (QM13). Déconnexion et suppression de compte : `authentification.md` §7.6 et §7.7. |
| **Authentification (AUTH-5)** | La suppression de compte d'un créateur **réutilise** nos procédures « Retirer » et « Supprimer » (§13) : elles doivent donc exister sous forme de composants sans interface avant AUTH-5. |
| **Notifications** | Hors périmètre ; voir §5.12. |

---

## 7. Feed : recommandation et constats

**Recommandation : un ticket distinct « Feed — cercle de confiance » (QM10)**, après ce ticket et après les tickets Statut et Localisation. Raisons : il dépend d'eux (statuts, consentement, photos), il pose des questions de confidentialité qu'il vaut mieux trancher à part, et ses constats ne doivent pas retarder la création et l'entrée dans une Miff.

**Constats à reprendre dans le challenge du Feed** (rien n'est tranché ici) :

| # | Constat | Source | Pourquoi c'est un problème |
|---|---|---|---|
| F1 | Bouton **« Localiser »** (carte avec « les coordonnées de la dernière position connue ») et photo en grand avec « la localisation » | Notion Feed | **Aucune coordonnée d'une personne n'est stockée**, par décision (`data-model.md` §1) : le modèle ne contient qu'un état (« à la maison », « hors lieu ») et des horodatages. Ce bouton exigerait de stocker la position, donc de changer la nature du produit (et des justifications aux stores). QM11. |
| F2 | Boutons **« Message »** (SMS) et **« Appeler »** | Notion Feed | Aucun numéro de téléphone n'existe dans le schéma ni dans les fiches partagées (`users` : prénom, nom, photo ; les écrans « Numéro de téléphone » de l'onboarding n'ont pas d'usage défini). Partager un numéro avec tout le cercle est une donnée de plus, à consentir. QM11. |
| F3 | « Aucune Miff : on considère que tout le monde est en sécurité » | Notion Feed | Affirmer la sécurité de personne est faux et peut rassurer à tort. Un état vide doit inviter (« Pas encore de Miff »), pas conclure. QM12. |
| F4 | Bulle « **Tout le monde est en sécurité** » | Notion Feed | L'app ne sait pas si quelqu'un est en sécurité : elle sait **où il est par rapport aux lieux** et **quand il a donné des nouvelles**. Un membre qui ne partage pas ou n'a pas publié son statut ne peut pas compter comme « en sécurité ». Formulation à revoir (par exemple « Tout va bien dans ta Miff » seulement si tous les statuts connus le permettent, et dire « 3 sur 4 ont donné des nouvelles » sinon). |
| F5 | « Mis à jour il y a 2 min » : « la dernière fois où on a checké » | Notion Feed, Figma | Avec un écouteur temps réel, ce n'est plus vrai ; hors connexion, **le cache affiche l'ancien état en vert**. Il faut un état « hors connexion, dernières informations à {heure} » sans couleur rassurante. |
| F6 | La **carte « moi »** est dans la liste (« y compris moi-même ») | Notion Feed | Cohérent, mais ma carte doit afficher mon état de partage (désactivé, en cours de publication). |
| F7 | Bulle **rouge** quand quelqu'un est dans une zone à risque ; carrousel de bulles | Notion Feed | Le ton « rouge » est un pic d'anxiété par construction ; à adoucir et à ne pas répéter. Zone à risque = Premium, définie par le créateur (§5.9). |
| F8 | **Sélecteur de Miff** : seulement s'il y en a plusieurs (Premium) | Notion Feed | OK ; la Miff « active » est une préférence locale (dernière choisie, sinon la plus récente) purgée à la déconnexion. |
| F9 | En-tête : photo avec couleur de statut, logo, cloche | Notion Feed | Frontière Profil / Notifications. |
| F10 | Le paywall s'affiche à la première visite du Feed après inscription ou connexion | Notion Premium | Contredit « paywall au bon moment » (spec paywall) ; à reprendre dans le ticket Premium. |

---

## 8. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| **Partage du code** | Sélecteur système (`ACTION_SEND` / chooser) ; presse-papiers : `EXTRA_IS_SENSITIVE` sur Android 13 pour masquer l'aperçu | `ShareLink` / feuille de partage ; copie simple |
| **Coller le code** | Lecture du presse-papiers **uniquement** sur appui de la personne | `PasteButton` (iOS 16) : pas d'alerte de collage |
| **Saisie** | Clavier ASCII, majuscules, suggestions et correction désactivées | `.textInputAutocapitalization(.characters)`, `.autocorrectionDisabled()`, pas de suggestions |
| **Dialogues** | Boîtes de dialogue Material ; bouton « Retour » : fermer sans agir | `alert` / `confirmationDialog` ; action destructrice marquée comme telle ; geste de retour |
| **Menu d'un proche** | Menu « ⋯ » (appui) ; actions accessibles par TalkBack | Menu contextuel ; VoiceOver : actions personnalisées |
| **Suppression en cours** | Le processus peut être tué (surcouches constructeur type ColorOS, `CLAUDE.md`) : reprise au lancement ; pas de service d'arrière-plan | Tâche d'arrière-plan courte autorisée, puis reprise au lancement |
| **Purge du cache** | `terminate()` puis `clearPersistence()` (contrat `data-model.md` §8) | Idem, signatures exactes à confirmer par `ios-dev` (exclusion des sauvegardes à vérifier) |
| **Tests sur appareil** | OPPO Find X5 Lite (Android 13) | iPhone 11 Pro (iOS 26.5.2) via TestFlight uniquement (aucun Mac) |
| **Textes** | `values-fr` / `values` (anglais de repli) | `Localizable.xcstrings` ; **mêmes clés `miff_*`** |

---

## 9. Lien avec le modèle de données et les règles

**État des règles** : **DUR-1 et DUR-2 sont déployées sur `miff-3d283`** (information donnée par le PM le 2026-10-23, à confirmer par la date de publication dans Firestore > Règles ; voir §4 #15). DUR-3 n'a pas changé les règles. **Ce ticket ne requiert aucun nouveau déploiement de règles.**

**Ce que les règles permettent déjà** (vérifié dans `backend/firestore.rules`) : création de Miff en un lot de 4 (pierre tombale respectée) ; entrée avec preuve du code ; retrait impossible sans renouvellement du code ; départ du créateur seulement avec suppression de la Miff ; suppression refusée sans pierre tombale ni suppression du code ; Miff supprimée illisible ; libération de l'index d'appartenance seulement si l'on n'est plus membre ; limite gratuite par slot (`free`) ; lieux lisibles de tous les membres.

**Ce que les clients implémentent** (`data-model.md` §8, à l'identique sur les deux plateformes) :

| Action | Lectures | Écritures (un seul lot, accusé du serveur) | Sous-ticket |
|---|---|---|---|
| Créer | `entitlements/{uid}`, tous les index `memberships` | `miffs` + `joinCodes` + `members` (rôle `owner`, partage désactivé) + `memberships` (clé `free` ou identifiant de Miff) | MIFF-1 |
| Rejoindre | `joinCodes/{code}`, index `memberships`, `get miffs/{id}` du slot gratuit si doute | `members` (rôle `member`, avec `joinCode`) + `memberships` | MIFF-1 |
| Écran de la Miff | `get miffs/{id}`, écouteur `members`, écouteur `places` | — | MIFF-1 |
| Renommer | — | `miffs` (nom) + `joinCodes` (nom) | MIFF-1 |
| Renouveler le code | — | `miffs` (code) + création du nouveau `joinCodes` + suppression de l'ancien | MIFF-2 |
| Retirer | `get miffs/{id}` (code actuel) | 6 écritures : fiche, statut, photo de la cible + code (miffs, nouveau, ancien) | MIFF-2 |
| Quitter | — | fiche + statut + photo + index | MIFF-2 |
| Supprimer | `get miffs/{id}`, liste des membres | N lots de retrait, lieux (≤ 5 par lot), lot final avec **pierre tombale** | MIFF-2 |
| Accès perdu / nettoyage | `get miffs/{id}` par index | lot « Quitter », sinon index seul ; purge du cache | MIFF-1 |
| Limites | `entitlements`, index | — (décision avant écriture) | MIFF-3 |

**Contrat à respecter** : toutes les clés d'un document sont écrites à chaque écriture ; l'index se lit par son champ `miffId` (jamais par l'identifiant du document) ; `joinedAt`, `createdAt` : heure serveur ; un nouveau code vient du même générateur cryptographique ; une collision (~10^-13) = regénérer et réessayer.

**Évolutions de règles ou de schéma possibles, non bloquantes, à évaluer par `backend-data` (aucune n'est demandée ici sans décision)** :
1. Exiger `exists(users/{uid})` à la création et à l'entrée (preuve serveur du « profil exigé ») : +1 lecture de règles (marge actuelle suffisante pour ces deux lots, ≤ 2 et ≤ 6 sur 20) ; faible valeur de sécurité (le client écrit sa propre copie de nom).
2. Ajouter le **prénom du créateur** dans `joinCodes` pour afficher « Rejoindre la Miff « X » de {prénom} ? » : améliore le consentement éclairé et réduit l'usurpation, au prix d'une donnée de plus visible de quiconque détient le code ; à recopier dans les lots de renouvellement.
3. Plafond de taille ou **validation d'arrivée** (liste d'attente) : non faisable sans serveur proprement ; à rouvrir avec Blaze.
4. **Transfert de propriété** : `ownerUid` est immuable ; changement de règles et de tests ; hors v1 (QM1).
5. Corriger le commentaire d'en-tête de `firestore.rules` (§4 #15).

---

## 10. Cas limites

1. **Code correct mais Miff supprimée juste après** : l'entrée est refusée par les règles ; message unique « introuvable ».
2. **Code renouvelé entre la lecture et la confirmation** : refus à l'écriture ; message unique ; on propose de ressaisir.
3. **Personne retirée qui retente avec l'ancien code** : « introuvable », sans mention du retrait. Avec le nouveau code que le créateur lui redonne volontairement : elle peut revenir (pas de liste de bannis).
4. **Retiré qui rejoint avant que son app ait nettoyé son index gratuit** : le nettoyage (§5.4, étape 4) précède la décision de limite ; sinon un paywall à tort.
5. **Déjà membre via un autre appareil** : détecté par les index ; aucune écriture.
6. **Créateur qui tente de rejoindre sa propre Miff** : « Tu fais déjà partie de cette Miff ».
7. **Gratuit : créer ou rejoindre pendant qu'il est dans une Miff** : paywall, aucune écriture. **Gratuit qui vient de quitter** : immédiatement possible.
8. **Ex-premium avec plusieurs Miffs** : le blocage compte les index ; la suppression des éléments en trop appartient au ticket Premium.
9. **Retrait d'une personne déjà partie** : la suppression d'un document absent est sans effet, le code est renouvelé quand même ; message neutre « {Prénom} n'est plus dans la Miff », liste rafraîchie.
10. **Deux créateurs d'appareils différents qui retirent ou renouvellent en même temps** : un lot réussit, l'autre échoue ou réussit à son tour ; le code affiché est toujours relu avant partage.
11. **Retirer le dernier proche** : fonctionne (le code est renouvelé quand même, règle uniforme).
12. **Supprimer une Miff sans autre membre** : même procédure, sans étape de retrait ; le texte ne mentionne pas « 0 proche ».
13. **Suppression interrompue** : après N retraits, après les lieux, avant le lot final ; reprise au lancement ; le créateur peut aussi relancer à la main. La Miff reste utilisable entre-temps (le code a changé).
14. **App tuée pendant la création** : le lot est atomique ; au lancement, soit la Miff existe (index présent) soit rien n'a eu lieu. Le routage d'`authentification.md` §5 s'en sert.
15. **Fiche sans index (ou l'inverse)** : impossible par les règles (lot atomique) ; si on la rencontre, traiter comme accès perdu et nettoyer.
16. **Nom avec emojis, espaces multiples, texte de droite à gauche** : accepté (§5.2) ; la mise en page utilise début/fin.
17. **Taille de texte système maximale** : le code par groupes de 4 reste lisible, les boutons ne se chevauchent pas.
18. **Deux Miffs au même nom (premium)** : le sélecteur du Feed devra distinguer ; hors ticket.
19. **Retrait pendant la lecture** : l'écouteur d'un membre retiré est refusé ; message unique, nettoyage, bascule ; aucune donnée de la Miff ne reste visible (R17 du ticket durcissement). **Test à deux appareils** (PM, §11).
20. **Mode avion pendant une action** : message neutre, rien en file d'attente, aucun accès perdu faussement déclaré.
21. **Plusieurs appareils d'une même personne** : si elle quitte sur l'un, l'autre reçoit l'accès perdu ; si elle a deux appareils et l'un est hors ligne longtemps, il traite le message à son retour.
22. **Horloge du téléphone décalée** : `joinedAt` vient du serveur ; l'étiquette « Nouveau » se calcule sur la différence avec l'heure locale et tolère un décalage de quelques minutes.
23. **Premier lancement du créateur après installation sur un nouveau téléphone** : l'invitation « un proche a quitté » ne s'affiche **pas** pour des départs anciens (aucun souvenir local = aucune invitation).
24. **Texte de partage dans une autre langue que celle du destinataire** : le message suit la langue de l'expéditeur ; aucune détection du destinataire.

---

## 11. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.

**Avant MIFF-0 / MIFF-1 (Android)**
1. **Répondre aux questions** de `miff-questions-pm.md`, au minimum QM2, QM3, QM6, QM13, QM15 (bloquantes pour MIFF-1).
2. **Valider les textes** de §5.10 (tutoiement).
3. **Confirmer dans la console** la date de publication des règles (Firestore > Règles) : DUR-1 et DUR-2 sont-elles bien actives ? (information PM du 2026-10-23, non vérifiable par les agents.)
4. **Valider les nouveaux écrans** dessinés par `design-system` (§3.3) dans le Figma « Miff - Claude » ; ce fichier ne contient pas encore la page Miff.
5. **2 ou 3 comptes de test** (adresses e-mail que vous pouvez ouvrir) ; **deux appareils** pour les parcours « en direct » (retrait pendant la lecture) : Android seul permet de tout tester **sauf le temps réel entre deux personnes**, qui n'est testable qu'avec l'iPhone (après MIFF-4) ou un second Android.

**Avant la bêta externe**
6. **Lien de téléchargement** du message de partage (QM14) ; tant que l'app n'est pas publiée, une page statique gratuite du site d'AUTH-W est proposée.
7. Rien d'autre : aucun compte à créer, aucun moyen de paiement, aucune action console nouvelle. Le contact par défaut, si un texte devait en citer un, reste Tomessa MFOUKA, `tomessa.mfouka@gmail.com` (aucun texte de ce ticket n'en cite).

---

## 12. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Écritures du ticket | Quelques écritures par action (créer : 4 ; rejoindre : 2 ; retirer : 6 ; supprimer : N lots + lieux + 1) | 20 000 écritures et 20 000 suppressions par jour (Spark) | Très loin des quotas ; signal : `data-model.md` §12 |
| Lectures | Création et entrée : quelques lectures, **plus les lectures de règles** (mesurées dans l'émulateur : créer ≤ 2 ou ≤ 6 sur 20, rejoindre ≤ 2, retirer ≤ 2, supprimer ≤ 3, régénérer ≤ 1) ; écran de la Miff : environ N + 4 lectures pour N membres | 50 000 lectures par jour ; une lecture de règles est facturée même si la requête est refusée | Ratio lectures / utilisateur actif > 100 : à examiner ; > 150 deux jours de suite : signal de bascule (`data-model.md` §12) |
| Saisie de codes en rafale | Format vérifié localement (zéro lecture), pause de 60 s après 5 échecs, App Check | Un client modifié ou un faux compte contourne la pause (risque K8 déjà documenté) | Surveillance quotidienne ; Blaze + fonction « rejoindre » : jamais sans décision du PM |
| Taille des Miffs | Plafond doux de 15 (QM5) | Les lectures croissent avec le nombre de membres | Mesure sur la bêta ; plafond garanti = changement de backend plus tard |
| Page d'atterrissage du partage (QM14) | Firebase Hosting (Spark), domaine `*.web.app` déjà utilisé par AUTH-W | Domaine par défaut | Un domaine propre est payant : jamais sans décision |
| Notifications au cercle | **Aucune** (Blaze) | Retrait, départ, nouvel arrivant : visibles à l'ouverture seulement | À rouvrir avec la décision Blaze |

Rien dans ce ticket n'exige un moyen de paiement. Aucun choix économique ne réduit la sécurité.

---

## 13. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket (`CLAUDE.md`). Tailles relatives, sans durée.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| MIFF-0 | **Décisions, design et mise à jour des documents** (PM, `design-system`, `backend-data`) | Réponses aux questions ; écrans de §3.3 dans Figma « Miff - Claude » ; commentaire d'en-tête des règles (§4 #15) ; mise à jour de `docs/specs/miff-groupes.md` | S | Validation de ce ticket |
| MIFF-1 | **Android : créer, rejoindre, écran de la Miff, accès perdu** | Écrans A à G et L, saisie du code (§5.3), vérifications (§5.4), lots de création, d'entrée et de renommage, liste des proches en direct, partage, étiquette « Nouveau », textes `miff_*` FR + EN, contrat « consentement » à la frontière (§6), nettoyage et purge (§5.7), actions hors connexion (§5.11), tests unitaires (génération et normalisation du code, ordre des vérifications, interprétation des erreurs, accès perdu ≠ erreur réseau), accessibilité | L | INIT-2 ; AUTH-1 (session, routage, déconnexion avec purge) ; DUR-1 à DUR-3 ; MIFF-0 (QM2, QM3, QM6, QM13, QM15) ; Onboarding (appel de nos écrans) |
| MIFF-2 | **Android : retirer, quitter, supprimer, renouveler le code** | Écrans H, I, J, K ; lot de retrait avec code renouvelé ; lot « Quitter » ; suppression reprenable et idempotente (marqueur local) ; invitation après départ ; **composants sans interface** (retrait d'un membre, suppression d'une Miff) réutilisables par AUTH-5 et par la fin d'abonnement ; tests d'interruption à chaque étape | M à L | MIFF-1 ; QM1, QM8, QM9 |
| MIFF-3 | **Android : limites freemium** | Blocage à la création et à l'entrée (§5.5), comptage des index, nettoyage d'un index obsolète, déclencheur du paywall (écran provisoire sans achat tant que le ticket Paywall n'est pas livré) | S | MIFF-1 ; ticket Paywall / Premium ; QM7 |
| MIFF-4 | **iOS : créer, rejoindre, écran de la Miff, accès perdu** | Mêmes livrables que MIFF-1 ; spécificités §8 | L | INIT-4 ; AUTH-3 ; MIFF-1 (référence de comportement) |
| MIFF-5 | **iOS : retirer, quitter, supprimer, renouveler** | Mêmes livrables que MIFF-2 | M à L | MIFF-4, MIFF-2 |
| MIFF-6 | **iOS : limites freemium** | Même contenu que MIFF-3 | S | MIFF-4, MIFF-3 |

Ordre recommandé : MIFF-0, MIFF-1, MIFF-2 (**avant AUTH-5**, qui en dépend, `authentification.md` §15), MIFF-3, puis MIFF-4 à MIFF-6. Le **Feed** démarre après MIFF-1 et les tickets Statut / Localisation. Chaque PR : revue de code en contexte neuf, revue sécurité (codes, journalisation, gestion des erreurs, état local, fusion de comptes), QA, recette du PM, fusion par Claude après « oui » explicite du PM (`CLAUDE.md`).

---

## 14. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Code qui fuite** (capture d'écran, transfert d'un message) : un inconnu entre dans le cercle | Un inconnu qui partage sa position voit les statuts de tout le monde | Consentement au partage séparé et après la liste des proches ; « Nouveau » visible ; retrait + code renouvelé automatiquement ; renouvellement manuel ; validation d'arrivée possible plus tard (QM3) |
| R2 | **Créateur piégé** : il ne peut pas partir sans détruire la Miff pour les autres | Cercles fermés par défaut de transfert, ou créateurs qui restent malgré eux | Texte clair (§5.10) ; transfert à rouvrir si les retours le demandent (QM1) |
| R3 | **Écriture rejouée hors connexion** (création, retrait, suppression) | Action exécutée à un moment que la personne ignore | Exigence d'accusé du serveur (§5.11) ; critère H1 testé en mode avion |
| R4 | **Message d'accès perdu perçu comme une exclusion** (suppression de Miff) | Peine, malentendu | Message unique et neutre (QM8) ; option « Prévenir mes proches » (QM9) |
| R5 | **Retrait et suppression sans notification** (Spark) | Un proche retiré ou une Miff supprimée n'est découvert qu'à l'ouverture | Accepté ; nettoyage à l'ouverture ; à rouvrir avec Blaze |
| R6 | **Marge des lots de règles** (retrait : 18 sur 20 en pire cas, estimation) | Un ajout dans le lot de retrait ferait refuser l'opération | Ne **rien ajouter** aux lots (§5.6) ; mesure déjà faite en DUR-3 |
| R7 | **Suppression de Miff longue et interrompue** | Miff à moitié vidée, code déjà changé, mauvais souvenir | Procédure reprenable, marqueur local, texte calme ; le créateur peut relancer |
| R8 | **Index gratuit obsolète** (retiré dont l'app n'a pas nettoyé) | Paywall injuste, impossibilité de rejoindre | Nettoyage préalable (§5.4, étape 4) ; critère J11 |
| R9 | **Fin d'abonnement supprime des Miffs créées** (décision PM du 2026-10-24) | Des membres perdent leur cercle sans y être pour rien ; sentiment de piège commercial | À traiter dans le ticket Premium : préavis long, choix de la Miff conservée, rappel au créateur avant échéance ; ici : composants sans interface |
| R10 | **Messages de partage vers des tiers** (le code part dans une messagerie) | Le code transite hors de l'app | Inhérent au produit ; texte sobre, aucun autre champ ; renouvellement possible |
| R11 | **Clés d'index mal lues** (identifiant de document pris pour `miffId`) | Fausses limites, mauvais nettoyage | Contrat §9 ; test unitaire sur l'index à clé `miffId` |
| R12 | **Aucun plafond de taille garanti** | Lectures plus coûteuses, Miffs très grandes difficiles à gérer | Plafond doux, mesure, signal de bascule |
| R13 | **Exposition du nom de la Miff à qui détient le code** | Un nom intime (« Famille X ») visible d'un tiers | Texte d'aide sous le champ ; pas d'autre donnée dans `joinCodes` |
| R14 | **Test du temps réel entre deux personnes impossible avant l'iPhone** | Cas « retrait pendant la lecture » non recetté | Comptes tests + émulateur Firebase pour les règles ; recette à deux appareils après MIFF-4 |
| R15 | **Écrans manquants dans Figma** (membre, quitter, retirer, erreurs) | Développement qui invente des écrans | MIFF-0 les dessine avant MIFF-1 |
| R16 | **Risque juridique assumé** : conservation physique des données de survivants d'une Miff supprimée jusqu'à leur nettoyage, données de profil visibles du cercle | Voir `data-model.md` §11 | **Risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)** |

---

## 15. Hypothèses par défaut (recommandations de `miff-questions-pm.md`)

| QM | Sujet | Hypothèse appliquée dans ce ticket |
|---|---|---|
| QM1 | Créateur qui veut partir | Seule « Supprimer la Miff » ; pas de transfert en v1 |
| QM2 | Rejoindre et partage | Rejoindre ne partage rien ; consentement séparé après la liste des lieux et des proches ; « Pas maintenant » autorisé |
| QM3 | Code et arrivées | Code visible et partageable par tous les membres ; pas de validation en v1 ; « Nouveau » visible ; retrait + renouvellement |
| QM4 | Expiration du code | Aucune |
| QM5 | Taille | Pas de plafond garanti ; plafond doux de 15 côté app |
| QM6 | Nom | 2 à 40 caractères |
| QM7 | Paywall | À la création au toucher ; à l'entrée après reconnaissance du code ; lien « quitter ma Miff actuelle » |
| QM8 | Accès perdu | Message unique « Tu n'as plus accès à la Miff « X » » |
| QM9 | Prévenir avant suppression | Option « Prévenir mes proches », facultative |
| QM10 | Feed | Ticket distinct |
| QM11 | Localiser / Message / Appeler | Hors v1 du Feed (pas de coordonnées, pas de numéros partagés) |
| QM12 | Aucune Miff | État vide invitant, jamais « tout le monde est en sécurité » |
| QM13 | Entrée de l'écran de la Miff | Pastille de Miff du Feed (toucher = écran de la Miff) + « Mes Miffs » dans Réglages |
| QM14 | Lien de partage | Page d'atterrissage statique du site d'AUTH-W jusqu'à la publication |
| QM15 | Création | Deux temps : nom, puis « Ta Miff est prête » avec le code |

---

## 16. Critères d'acceptation

Découpage : **C** création, **J** rejoindre, **G** écran de la Miff, **N** renouvellement du code, **R** retirer, **Q** quitter, **S** supprimer, **L** limites, **D** accès perdu, **H** hors connexion et robustesse, **V** vie privée et ton, **T** transverses, **AND** Android, **IOS** iOS, **PM** recette sur téléphone, **QA** vérifications du QA. Les critères C à T valent pour les deux plateformes.

### Création
- [ ] **C1** « Créer une nouvelle Miff » ouvre l'écran « Nouvelle Miff » avec le seul champ Nom (le code n'y est pas affiché).
- [ ] **C2** Le bouton d'enregistrement est grisé tant que le nom, espaces de début et de fin retirés, a moins de 2 caractères ; il s'active à partir de 2.
- [ ] **C3** Au-delà de 40 caractères la saisie est refusée avec « Limite de 40 caractères atteinte. » ; jamais plus de 40 enregistrés (emojis compris).
- [ ] **C4** L'enregistrement envoie **un lot unique** : Miff, code, fiche du créateur (rôle `owner`, partage désactivé), index d'appartenance ; le code est généré à ce moment, 12 caractères de l'alphabet à 31 symboles ; test unitaire sur 1 000 codes : tous valides, aucun I, L, O, 0 ni 1.
- [ ] **C5** Aucun message de succès avant l'accusé du serveur ; hors connexion : message neutre, **aucune Miff n'apparaît au retour du réseau**.
- [ ] **C6** Après succès, l'écran « Ta Miff est prête » affiche le code par groupes de 4, avec « Partager » et « Copier » ; « Copier » confirme discrètement.
- [ ] **C7** Une collision de code relance la création jusqu'à 3 fois sans message ; au-delà, « Ça n'a pas fonctionné. Réessaie dans un instant. »
- [ ] **C8** Sans profil, l'écran n'est pas atteignable (retour à l'étape profil).
- [ ] **C9** `miff_created` (avec `is_first_miff`) n'est envoyé que si le consentement Analytics est donné ; aucun nom ni code dans l'événement.

### Rejoindre
- [ ] **J1** Le champ force les majuscules, désactive correction et suggestions ; espaces, tirets et minuscules saisis ou collés sont normalisés.
- [ ] **J2** Le bouton est actif seulement avec 12 caractères valides de l'alphabet ; un caractère hors alphabet affiche un message **sans aucune lecture réseau**.
- [ ] **J3** Aucune lecture automatique du presse-papiers : « Coller » est une action de la personne (`PasteButton` sur iOS).
- [ ] **J4** Un code faux, un code renouvelé et un code de Miff supprimée affichent **exactement le même message** (test des trois cas avec deux comptes).
- [ ] **J5** Après 5 échecs consécutifs : pause de 60 secondes avec le message bienveillant ; compteur remis à zéro après un succès ou 10 minutes.
- [ ] **J6** Un code reconnu affiche la confirmation « Rejoindre la Miff « X » ? » ; aucune écriture avant l'appui sur « Rejoindre ».
- [ ] **J7** Déjà membre de cette Miff (comparaison avec les index par leur champ `miffId`) : message « Tu fais déjà partie de la Miff « X » » et bouton « Ouvrir la Miff », aucune écriture.
- [ ] **J8** Un index gratuit pointant vers une Miff devenue inaccessible est nettoyé **avant** de décider de la limite (test : retiré, puis tentative de rejoindre une autre Miff sans relancer l'app : aucun paywall à tort).
- [ ] **J9** L'entrée est un lot de 2 écritures (fiche `member` avec le code utilisé, index) ; succès affiché après l'accusé du serveur.
- [ ] **J10** Après l'entrée, la fiche a `sharing.enabled = false` (QM2) ; aucun statut n'est créé avant le choix explicite de la personne.
- [ ] **J11** Hors connexion pendant la lecture du code ou la confirmation : message neutre, aucune écriture en file d'attente.
- [ ] **J12** Code renouvelé entre la lecture et la confirmation : message unique, pas de plantage.
- [ ] **J13** `miff_joined` (avec `is_first_miff`) et `onboarding_completed` (`via = joined`) respectent le consentement Analytics ; aucun code ni nom dans les événements.

### Écran de la Miff
- [ ] **G1** Le créateur et chaque membre ouvrent l'écran de leur Miff depuis le point d'entrée décidé (QM13) ; le titre est le nom de la Miff.
- [ ] **G2** L'écran affiche : nom, code par groupes de 4 avec « Partager » et « Copier », liste des proches (photo ou initiales, prénom et nom, étiquettes « Toi », « Créateur », « Nouveau »), section des lieux (liste seulement).
- [ ] **G3** Seul le créateur peut renommer ; « Enregistrer » est actif seulement si le nom a changé et est valide ; le renommage met à jour la Miff **et** le nom du code dans le même lot (test : les deux valeurs identiques après coup).
- [ ] **G4** Seul le créateur voit « Renouveler le code », le menu « ⋯ » des proches et « Supprimer la Miff » ; le membre voit « Quitter la Miff » ; le créateur n'a **pas** de « Quitter » et lit le texte explicatif.
- [ ] **G5** La liste des proches est en direct : un proche qui arrive ou part apparaît ou disparaît sans recharger ; le code affiché se met à jour si un renouvellement a lieu pendant que l'écran est ouvert.
- [ ] **G6** « Partager » ouvre la feuille de partage native avec nom, code, lien (QM14), dans la langue de l'app ; « Copier » ne copie que le code ; sur Android 13 et plus le contenu copié est marqué sensible.
- [ ] **G7** « Nouveau » s'affiche pendant 7 jours après l'arrivée d'un proche ; une ligne unique « {Prénom} a rejoint la Miff. » s'affiche à l'ouverture suivante, une fois.
- [ ] **G8** Aucun statut, aucune batterie, aucune position, aucune photo d'update n'apparaît sur cet écran.
- [ ] **G9** Au-delà du plafond doux (QM5), « Ajouter un proche » est remplacé par « Cette Miff est bien remplie ! » ; aucun chiffre affiché.

### Renouveler le code
- [ ] **N1** « Renouveler le code » demande une confirmation puis écrit un lot de 3 écritures ; l'ancien code ne fonctionne plus (test : un second compte tente l'ancien code : message unique), le nouveau fonctionne.
- [ ] **N2** Les membres actuels restent dans la Miff et voient le nouveau code en direct.
- [ ] **N3** Après un départ volontaire constaté par l'app du créateur, une **invitation unique** discrète s'affiche (« Plus tard » la ferme sans la répéter) ; aucune invitation pour des départs antérieurs à l'installation locale ; aucune invitation pour un retrait que le créateur vient de faire.

### Retirer
- [ ] **R1** Le menu « ⋯ » d'un proche propose « Retirer de la Miff » ; une confirmation nominative, au ton non accusateur, précède toute action.
- [ ] **R2** Le retrait envoie **un seul lot de 6 écritures** (fiche, statut, photo de la cible ; code mis à jour, nouveau code créé, ancien supprimé) ; rien d'autre dans le lot.
- [ ] **R3** Après succès, le nouveau code est affiché avec « Partager » et le texte « {Prénom} ne fait plus partie de la Miff… ».
- [ ] **R4** Jamais d'envoi d'un retrait sans nouveau code (test du client).
- [ ] **R5** Cible déjà partie : message neutre et liste rafraîchie ; le code est renouvelé quand même.
- [ ] **R6** Le créateur ne peut pas se retirer lui-même (pas de menu sur sa ligne).
- [ ] **R7** Hors connexion : impossible, message neutre, aucune écriture en file d'attente.

### Quitter
- [ ] **Q1** « Quitter la Miff » demande une confirmation ; le lot supprime fiche, statut, photo et index.
- [ ] **Q2** Après le départ : surveillance des lieux de cette Miff arrêtée ; cache local purgé (contrat `data-model.md` §8) ; retour au Feed (autre Miff, sinon état vide).
- [ ] **Q3** Un compte gratuit peut créer ou rejoindre une Miff immédiatement après son départ, sans paywall.
- [ ] **Q4** Hors connexion : impossible, message neutre.

### Supprimer
- [ ] **S1** Une confirmation indique le nombre d'autres proches et le texte « Cette action est définitive » (aucun « irrévocable », aucun vouvoiement).
- [ ] **S2** Ordre : retrait de chaque autre membre (un lot chacun, code renouvelé), lieux par lots de 5 au plus, lot final : Miff, pierre tombale, code, son propre statut, photo, fiche et index.
- [ ] **S3** Une progression calme s'affiche ; l'app reste utilisable pour quitter l'écran ; l'opération continue tant que l'app est ouverte.
- [ ] **S4** **Reprise** : si l'app est tuée ou le réseau coupé après N retraits, après les lieux ou avant le lot final, l'opération reprend au lancement suivant et aboutit (test pour chaque point d'interruption).
- [ ] **S5** Après la suppression : slot gratuit libéré, retour au Feed ; la pierre tombale existe (une recréation de la Miff est refusée par les règles : test de non-régression côté règles déjà couvert, à ne pas casser).
- [ ] **S6** L'option « Prévenir mes proches » (QM9), si retenue, ouvre un message prérempli avant la suppression, sans contenir le code ni d'identifiant.
- [ ] **S7** Hors connexion : impossible de démarrer ; coupure en cours : pause puis reprise.
- [ ] **S8** Les procédures de retrait et de suppression sont exposées sous forme de composants **appelables sans interface** (pour AUTH-5 et la fin d'abonnement), avec leurs propres tests.

### Limites
- [ ] **L1** Un compte gratuit qui a déjà une appartenance ne peut ni créer ni rejoindre ; le blocage se base sur le **nombre d'index** (test : un index à clé `miffId` suffit à déclencher le blocage) et n'écrit rien.
- [ ] **L2** Création : le blocage a lieu au toucher de « Créer une nouvelle Miff » ; entrée : après la reconnaissance du code, avant la confirmation (QM7) ; `paywall_shown` avec `trigger = extra_miff`.
- [ ] **L3** Un compte premium actif peut créer et rejoindre sans limite ; l'index porte l'identifiant de la Miff comme clé.
- [ ] **L4** Si les règles refusent un compte que l'app croyait premium (expiré côté serveur), l'app relit le droit puis affiche le blocage, sans message technique.
- [ ] **L5** Le blocage propose, en plus du paywall, de quitter la Miff actuelle pour rejoindre la nouvelle (QM7) ; sans paywall livré, l'écran de limite n'a pas de bouton d'achat.

### Accès perdu
- [ ] **D1** Retrait ou suppression constatés pendant que l'app est ouverte : un seul message « Tu n'as plus accès à la Miff « X »… », sans boucle d'erreurs ni écran d'erreur technique.
- [ ] **D2** Au lancement, avec réseau, l'accès est vérifié **auprès du serveur** avant d'afficher les données de la Miff ; hors connexion, le cache s'affiche avec une mention calme.
- [ ] **D3** **Mode avion ≠ accès perdu** (test : aucune conclusion de retrait sur une erreur réseau).
- [ ] **D4** Nettoyage : écouteurs et surveillance des lieux arrêtés, lot « Quitter » tenté, sinon suppression de l'index seul, cache purgé.
- [ ] **D5** Après nettoyage, bascule vers une autre Miff ou état vide du Feed ; un compte gratuit peut de nouveau créer ou rejoindre.
- [ ] **D6** Le message est le même pour un retrait et pour une suppression de la Miff (test avec deux comptes).
- [ ] **D7** **Retrait pendant la lecture** : un membre qui regarde l'écran de la Miff au moment du retrait reçoit le message et ne voit plus aucune donnée (test à deux appareils, §11).

### Hors connexion et robustesse
- [ ] **H1** Créer, rejoindre, retirer, quitter, renouveler et supprimer affichent le succès **après l'accusé du serveur** ; en mode avion, aucune de ces écritures n'est exécutée au retour du réseau.
- [ ] **H2** Un double appui ne produit qu'une opération.
- [ ] **H3** Rotation ou passage en arrière-plan en cours d'opération : pas de doublon, l'opération aboutit ou reprend.
- [ ] **H4** Quota ou App Check refusés : message « Pas de connexion pour le moment. », jamais le mot « quota ».
- [ ] **H5** En lecture hors connexion, les boutons d'action sont désactivés avec une mention calme.

### Vie privée et ton
- [ ] **V1** Aucune coordonnée, adresse ou nom de lieu n'est écrit, journalisé, envoyé en Analytics ou placé dans le message de partage.
- [ ] **V2** Le code, le nom de la Miff et les prénoms n'apparaissent dans aucun journal, aucun événement Analytics, aucun rapport de plantage (aucune clé personnalisée Crashlytics).
- [ ] **V3** Revue des textes : tutoiement partout ; aucun mot de la liste interdite (§5.10) ; deux phrases au plus par message ; chaque erreur propose une issue.
- [ ] **V4** Aucun écran de ce ticket ne donne au créateur une information sur un membre que ce membre ne voit pas aussi sur le créateur.
- [ ] **V5** La confirmation de rejoindre précise que la position n'est partagée que sur choix explicite.

### Transverses
- [ ] **T1** Tous les textes en français et en anglais, clés `miff_*` identiques sur les deux plateformes (contrôle de parité de la CI) ; pluriels par le mécanisme de la plateforme ; aucun texte en dur.
- [ ] **T2** Accessibilité : code lu par groupes ; zones tactiles d'au moins 48 dp (Android) ou 44 pt (iOS) ; contraste conforme (`design-system.md` §1) ; texte agrandi sans chevauchement ; focus déplacé correctement après chaque dialogue ; textes `_a11y` pour les boutons icônes.
- [ ] **T3** Aucune valeur de design en dur (tokens du design system) ; mise en page début/fin.
- [ ] **T4** Mesure des lectures : ouvrir l'écran d'une Miff de N membres coûte au plus N + 4 lectures ; rejoindre au plus 2 lectures plus celles des règles ; aucun écouteur supplémentaire inutile.
- [ ] **T5** App Check initialisé avant toute requête ; aucun secret dans le dépôt ; aucune règle modifiée par cette PR.
- [ ] **T6** Aucun nouvel événement Analytics (liste blanche du contrat inchangée).

### Android
- [ ] **AND1** Le bouton Retour ferme les dialogues sans agir ; pendant la suppression, il quitte l'écran sans interrompre l'opération.
- [ ] **AND2** Test sur l'OPPO Find X5 Lite : app tuée en arrière-plan pendant une suppression, reprise au lancement.
- [ ] **AND3** Le clavier de saisie du code est ASCII, majuscules, sans suggestions.
- [ ] **AND4** Les tests unitaires et le lint passent dans la CI ; aucune permission nouvelle dans le manifeste.

### iOS
- [ ] **IOS1** Vérification par TestFlight sur l'iPhone 11 Pro ; `PasteButton` pour coller ; feuille de partage native.
- [ ] **IOS2** Action destructrice marquée comme telle dans les dialogues ; VoiceOver annonce le code par groupes et propose les actions d'un proche.
- [ ] **IOS3** Suppression interrompue (app fermée) : reprise au lancement.
- [ ] **IOS4** Le contrat de purge du cache est vérifié sur appareil (aucune donnée de Miff visible hors connexion après un retrait ou un départ).

### Ce que le PM peut vérifier lui-même sur téléphone
- [ ] **PM1** Créer une Miff, partager le code vers une messagerie, copier le code, relire le message reçu.
- [ ] **PM2** Avec un second compte, rejoindre avec le code ; essayer un faux code ; essayer le code après renouvellement.
- [ ] **PM3** Retirer ce second compte : le code change ; l'ancien ne marche plus ; le second compte voit le message calme à l'ouverture.
- [ ] **PM4** Quitter une Miff avec le second compte, puis rejoindre une autre (gratuit : aucun paywall).
- [ ] **PM5** Supprimer la Miff ; couper le réseau pendant l'opération et vérifier la reprise.
- [ ] **PM6** Mode avion : créer, rejoindre, supprimer : messages calmes, rien d'exécuté plus tard.
- [ ] **PM7** (avec l'iPhone) deux appareils : retirer un compte pendant qu'il regarde l'écran de la Miff.
- [ ] **PM8** Relire chaque texte à voix haute : est-ce que je dirais cela à un proche inquiet ?

### Pour le QA (`qa-tester`)
- [ ] **QA1** Tableau de couverture critère par critère, avec les cas à deux comptes.
- [ ] **QA2** Interruptions : tuer l'app à chaque étape de la suppression et de la création ; vérifier l'absence de doublons et de Miffs fantômes.
- [ ] **QA3** Rejouer le même scénario sur Android et iOS : mêmes textes, mêmes états, mêmes résultats.
- [ ] **QA4** Revue des journaux de la build debug : aucun code, nom ou prénom.
- [ ] **QA5** Vérifier que les règles inchangées passent toujours les tests du dépôt (`backend/tests`).

### Définition de « terminé »
Tous les critères de la plateforme concernée cochés ; revue de code en contexte neuf et revue sécurité faites ; QA faite ; recette du PM faite ; « prêt à merger » validé par un « oui » explicite du PM ; fusion par Claude.

---

## 17. Dépendances

| Avec | Nature |
|---|---|
| **INIT-1, INIT-2, INIT-4** | Socle Android et iOS, App Check, textes, tokens (déjà fusionnés) |
| **AUTH-1 / AUTH-3** | Session, routage au lancement (`authentification.md` §5), déconnexion avec purge (`§7.6`). **Non fusionnés au moment de la rédaction** : MIFF-1 attend AUTH-1 |
| **AUTH-5** | Dépend de MIFF-2 (procédures de retrait et de suppression) |
| **DUR-1 à DUR-3** | Règles de sécurité (déployées selon le PM, à confirmer) ; contrat de purge du cache |
| **Onboarding** | Appelle nos écrans ; à répercuter : fin de l'étape « téléphone » (recommandation : retirer), pas de « Toujours autoriser obligatoire » sans arbitrage |
| **Localisation / Statut** | Contrat à la frontière (§6) : fiche sans partage, puis étape de consentement |
| **Lieux de confiance** | Liste des lieux dans l'écran de la Miff ; création et édition chez eux |
| **Premium / Paywall** | Écran et achat ; fin d'abonnement ; préavis |
| **Feed** | Ticket distinct (§7) |
| **Réglages** | Point d'entrée « Mes Miffs » éventuel |
| **`design-system`** | Écrans et composants de §3.3 |
| **`backend-data`** | Commentaire d'en-tête des règles ; évaluation des évolutions de §9 si le PM les veut |
| **Site d'AUTH-W** | Page d'atterrissage du message de partage (QM14) |
| **Actions du PM** | §11 |

---

*Aucun fichier du projet autre que ce ticket et `miff-questions-pm.md` n'a été modifié. Notion et Figma n'ont pas été modifiés. Aucune commande Firebase n'a été exécutée.*
