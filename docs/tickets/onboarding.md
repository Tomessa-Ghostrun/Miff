# Ticket — Onboarding (splash, icône, carrousel, profil, permissions, routage, barre d'onglets, Profil et Réglages minimaux)

**Statut** : Brouillon mis à jour avec les décisions du PM du 2026-10-24 (`docs/tickets/onboarding-decisions-pm.md` ; les numéros Q1 à Q14 de ce document y renvoient) ; **N1 (visibilité du numéro) tranchée le 2026-10-24** : option A (§6.4) ; aucune question ouverte ; aucun développement lancé
**Rédigé le** : 2026-10-24 (agent `product-specs`, étape « challenge » du process par ticket) ; mis à jour le 2026-10-24 après les réponses du PM aux 14 questions. Quatre décisions diffèrent de mes recommandations (Q1 téléphone obligatoire, Q4 notifications demandées dès la bêta, Q5 écran Paywall provisoire, Q14 pas de lien de déconnexion pendant l'onboarding) : elles sont appliquées telles quelles, avec leurs risques écrits comme **risques assumés** (§13).
**Remplace** : fusionne les tickets Notion « Onboarding », « splash screen », « Tabbar », « Icon de l'app », « Profil » et « Paramètre » (statut Notion actuel : « Spec en attente de review IA » pour tous). Rend redondantes les deux entrées « Backlog discovery » « Onboarding — Bienvenue (carrousel de valeur) » et « Profil & Réglages (à approfondir) » (miroirs de `docs/specs/onboarding-bienvenue.md` et `docs/specs/profil-et-reglages.md`) : à clore ou à lier par le PM une fois ce ticket validé. Notion n'a pas été modifié, aucun statut n'a été changé.
**Sources lues** : les 8 pages Notion listées ci-dessus (lecture seule) ; en contexte : Notion « Tracking » (permissions de localisation), « Premium » (paywall), « Feed » ; `CLAUDE.md` ; `docs/tickets/authentification.md` (§5, §7.6, §7.7, §7.8, §8, §12, §17), `docs/tickets/initialisation-technique.md` (§3, §4.F, §6, §9), `docs/tickets/durcissement-regles-firestore.md` (§6, §15) ; `backend/data-model.md` (§1, §2.1, §2.2, §2.6, §4, §5, §6, §8, §10, §11, §15) et le bloc `users` de `backend/firestore.rules` ; `design/design-system.md` ; `docs/specs/*.md` (dont `localisation-lieux-de-confiance.md`, `paywall-premium.md`, `feed-cercle-de-confiance.md`) ; `docs/legal/README.md` ; `docs/contracts/analytics-events.md` et `i18n-keys.md` ; `android/CHANGELOG.md`, `android/README.md`, `ios/CHANGELOG.md` (état du socle déjà livré) ; Figma « Miff - Claude » (`RbX84qKbxppvAy5iypLYP2`, lecture seule : écrans 01 à 03 carrousel, 09 profil, 10 téléphone, 11 choix créer/rejoindre, 12 nouvelle Miff, 13 et 14 amorces notifications et localisation) et, pour les écrans qui n'y sont pas encore reconstruits (Profil, Réglages), Figma original `Byi9NgQxkBPbmbhU403HSf` (lecture seule : pages Profil et Réglages).
**Limite de lecture** : les images insérées dans les pages Notion (maquettes embarquées, logo, icône) ne sont pas lisibles par l'outil Notion. Les écrans viennent donc du Figma. Le contenu de l'image « Icon de l'app » et du GIF/logo du splash n'a **pas** pu être vu : tout ce qui les concerne est une hypothèse (§5.1, §5.2). Rien du reste n'est inventé : ce qui manque est signalé en §4.
**Convention** : « vérifié » = confirmé sur une source du dépôt. « À vérifier » = non confirmé (souvent un comportement de système d'exploitation ou une règle de store tirée de la connaissance générale), à contrôler avant de s'y fier. Aucune commande Firebase n'a été exécutée pour ce ticket.

---

## 1. Résumé pour le PM

En une phrase : accompagner une personne depuis l'ouverture de l'app jusqu'à son premier écran utile (le Feed) en quelques gestes, en lui expliquant **honnêtement** ce que l'app fait de sa position **avant** de la lui demander, sans jamais la forcer, et lui donner une barre d'onglets et un espace Profil et Réglages qui permettent de revenir sur chaque choix.

**Décisions du PM du 2026-10-24 qui structurent ce ticket** (liste complète en §14) :
- **Téléphone obligatoire** dans l'onboarding (Q1), pays et numéro, sans vérification par SMS ; il sert aux boutons « Appeler » et « Message » du Feed. Conséquence technique : les règles Firestore actuelles **refusent** ce champ ; un sous-ticket **ONB-B** (schéma, règles, tests, revue de sécurité, accord PM avant déploiement) passe **avant** les écrans de profil.
- **Notifications demandées directement, y compris en bêta** (Q4), alors qu'aucun envoi n'est possible sans plan payant : textes honnêtes (« bientôt »), risque de relecture des stores assumé.
- **Écran Paywall provisoire en fin d'onboarding** (Q5) : facile à passer, non fonctionnel, sans prix ni engagement, jamais relancé.
- **Aucune échappatoire** « Ce n'est pas toi ? » pendant l'onboarding (Q14) : la déconnexion se fait depuis les Paramètres ; risque assumé.
- Les autres recommandations sont retenues : permissions jamais bloquantes (Q2), amorce unique de localisation fusionnée avec le consentement de la Miff (Q3), prénom et nom de 1 à 50 caractères (Q6), photo facultative (Q7), carrousel manuel (Q8), splash dessiné dans l'app (Q9), logo extrait du Figma par `design-system` (Q10), langue (Q11), Réglages minimaux (Q12), barre d'onglets à 3 destinations et écran de choix sans barre quand il n'y a pas de Miff (Q13).

Ce que le challenge a changé par rapport aux tickets Notion et aux maquettes (détails en §4, §5, §6) :

| # | Constat | Conséquence |
|---|---|---|
| 1 | Les tickets « Onboarding » et « Tracking » **bloquent** l'app tant que la position « Toujours » n'est pas accordée, ré-affichent la fenêtre de notifications toutes les 20 visites, et ne laissent qu'un seul bouton (« Autoriser »). | Contraire à la spec `localisation-lieux-de-confiance.md` (jamais bloquer, repli par mise à jour manuelle), au ton non anxiogène et au consentement libre de `CLAUDE.md`, et probablement aux règles des stores (§6.6, risque R1). **Décision (Q2)** : toujours un « Plus tard », rappel discret et non bloquant, aucune relance par fenêtre. |
| 2 | L'amorce de localisation du Figma (« Miff a besoin d'accéder à votre localisation… ») ne dit ni « en arrière-plan », ni **qui voit quoi**, ni comment arrêter. Or Apple et Google exigent une information claire avant la demande. Elle mélange aussi deux choses : le **consentement au partage avec une Miff** (déjà dans le modèle de données, par Miff, daté et versionné) et la **permission du système**. | **Décision (Q3)** : un seul écran d'information juste après l'entrée dans une Miff, qui fusionne le consentement de cette Miff et l'amorce de la permission (§6.6). Le consentement par Miff reste la propriété des tickets Miff ; la permission du système, son amorce et ses rappels appartiennent à ce ticket. |
| 3 | La fenêtre d'autorisation des **notifications** est demandée dès l'arrivée sur le Feed, alors que l'envoi de notifications quand l'app est fermée est **impossible sans plan Blaze** (`data-model.md` §13), non activé par décision du PM. | **Décision (Q4, contre ma recommandation)** : la permission est **demandée directement, y compris en bêta**. Rien ne sera délivré tant que le serveur n'existe pas ; les textes le disent honnêtement (§6.11), « Plus tard » reste possible et ne consomme pas la fenêtre du système. Risque de relecture des stores (permission demandée sans fonctionnalité) assumé (R2, R15). |
| 4 | Le numéro de téléphone (écran 10) sert, d'après le Feed, aux boutons « Appeler » et « Message » sur la carte d'un proche. Il n'existe ni dans le schéma ni dans les règles (qui le **refusent** aujourd'hui) ; il n'est pas vérifié (pas de SMS sur Spark). | **Décision (Q1, contre ma recommandation)** : **obligatoire** dans l'onboarding. Spécifié en §6.4 (E.164, sélecteur de pays, pas de vérification) ; **visibilité décidée (N1, option A)** : seuls les membres des Miffs de la personne voient son numéro ; risques assumés (R7) ; sous-ticket backend **ONB-B** avant les écrans de profil (§12). |
| 5 | Le ticket Premium affiche le paywall à la première arrivée sur le Feed après l'inscription, après chaque connexion, et « toutes les 10 visites ». | **Décision (Q5)** : un écran **provisoire** en fin d'onboarding, facile à passer, sans achat ni prix, « bientôt disponible » (§6.12) ; le vrai paywall sera traité dans le ticket Premium ; **aucune relance automatique**. |
| 6 | Le carrousel annonce des **notifications** en slide 3, répète le même titre trois fois, montre des photos de stock (droits à vérifier) et une phrase qui inquiète. L'événement d'analyse `onboarding_cta_tapped` **ne peut jamais partir** (le consentement statistiques est demandé après le carrousel). | **Décision (Q8)** : défilement manuel, un titre par slide, slide 3 reformulée honnêtement, illustrations libres, lien « J'ai déjà un compte », aucun événement ; contrat d'analyse à ajuster par une PR distincte (§4 #12). |
| 7 | **Logo et icône** : le PM n'a pas le logo en SVG ; l'icône actuelle des deux apps est provisoire (« ne pas publier »). Le splash en dépend aussi. | **Décision (Q10)** : l'agent `design-system` extrait le logo depuis le Figma (lecture) et le PM valide le rendu ; ONB-0. |
| 8 | Frontière avec l'Authentification : le routage de §5 de l'Authentification est repris et complété. Le retour arrière de Notion (« retour = connexion ») est remplacé par la décision Q11 de l'Authentification (pas de flèche retour sur la première étape). | §3 et §6.2. **Décision (Q14)** : pas de lien « Ce n'est pas toi ? » ; la déconnexion se fait depuis les Paramètres, atteints seulement après l'onboarding : **risque assumé** (R16). |
| 9 | Réglages : la maquette propose 6 langues (décision actée : français et anglais), un pop-in de suppression de compte qui dit « Vous perdrez instantanément toutes vos données y compris votre abonnement » (anxiogène **et** inexact : l'abonnement du magasin n'est pas résilié), aucun lien vers les CGU/politique (exigé par les stores), aucun interrupteur de consentement statistiques. | **Décision (Q12)** : Réglages **minimaux** réécrits en §6.9 ; l'abonnement reste au ticket Premium, la suppression au ticket Authentification (AUTH-5). |

**Risque juridique assumé** (décision PM du 2026-10-23, pas de relecture juridique) : applicable aux textes de consentement et d'amorce de ce ticket, comme au reste du projet. Aucun texte ne prétend avoir été relu par un juriste.

**Coût : 0 €** (aucune bibliothèque payante, aucun service ajouté, aucun moyen de paiement ; détails en §11).

**Taille globale (relative, sans durée)** : L (voir découpage §12). Android d'abord, puis iOS.

---

## 2. Périmètre

### Dans le périmètre
- **Splash** : écran de lancement animé (fond bleu, cœur vert qui apparaît, gonfle puis éclate), sa cohabitation avec l'écran de lancement système et avec la vérification de session.
- **Identité** : logo et icône d'application (extraction du logo par `design-system`, Q10 ; icône définitive avant les stores).
- **Carrousel de bienvenue** : 3 slides, première ouverture seulement.
- **Création du profil** : photo facultative, prénom, nom **et téléphone obligatoire** (pays + numéro) ; écriture de `users/{uid}` (Q1).
- **ONB-B (backend)** : schéma, règles Firestore, tests et revue de sécurité pour le champ téléphone sur `users/{uid}` et sa copie sur `members/{uid}` (§8).
- **Routage** selon l'état du compte, au lancement et après chaque étape (reprise après fermeture, perte d'une Miff).
- **Écran de choix « Créer / Rejoindre une Miff »** (le routeur seulement, voir §3).
- **Permissions du système** : amorce et états de la localisation (premier plan, arrière-plan), **amorce et demande des notifications dès la bêta** (Q4), rappels non bloquants, règles de nouvelle demande.
- **Écran Paywall provisoire** en fin d'onboarding : non fonctionnel, facile à passer (Q5).
- **Barre d'onglets** (3 destinations) et coquille de l'onglet Profil.
- **Profil et Réglages minimaux** : « Mes informations » (dont pays et téléphone), « Mon compte » (e-mail affiché, déconnexion, point d'entrée de la suppression), langue, confidentialité (consentement statistiques, état des permissions, liens CGU/politique, contact).

### Hors périmètre
- Inscription, connexion, mot de passe oublié, panneau de consentement du nouvel utilisateur, session, suppression de compte (ticket Authentification, AUTH-1 à AUTH-5).
- Écrans « Nouvelle Miff » (création), saisie et validation du code pour rejoindre, écran de confirmation et de **consentement au partage par Miff**, partage du code, déclenchement du paywall pour une 2e Miff (tickets Miff, à écrire).
- Création, modification et surveillance des lieux de confiance ; algorithmes de géofencing, choix des 20 (iOS) / 100 (Android) zones, écritures de statut (tickets Lieux et Tracking).
- Contenu du Feed (dont les boutons « Appeler » et « Message » qui **consomment** le numéro), de l'Update (bouton central de la barre d'onglets), du centre de notifications.
- **Le vrai paywall**, l'abonnement, le bandeau Premium des Réglages, la gestion de l'abonnement (ticket Premium).
- **Envoi** de notifications (exige Blaze) : seule la demande de permission est dans ce ticket.
- Vérification du numéro de téléphone par SMS (indisponible sur Spark).
- Changement de mot de passe depuis les Réglages (différé, §6.9), changement d'adresse e-mail.
- Langues autres que le français et l'anglais ; arabe et écriture droite-à-gauche livrés (la structure est prête, INIT).
- Mode sombre (thème clair imposé, décision INIT).
- Rédiger les textes définitifs, publier sur les stores.

---

## 3. Frontières exactes avec les tickets voisins

Principe : **chaque écran et chaque règle a un seul propriétaire**. Ce ticket pilote la séquence ; il ne duplique pas les règles des autres.

| Sujet | Propriétaire | Ce que ce ticket fait ou attend |
|---|---|---|
| Écrans d'inscription et de connexion, boutons Apple/Google, cases de consentement (a) et (b), panneau de consentement du nouvel utilisateur (§7.8 Authentification) | **Authentification** | Ce ticket **n'affiche rien** tant que ce panneau n'est pas résolu. Il reçoit de l'Authentification : une session valide, la **version des CGU** acceptée (`termsVersion`) et le **choix (b)** conservés sur l'appareil jusqu'à l'écriture du profil. |
| Écriture de `users/{uid}` (création du profil) | **Ce ticket** | L'écran de profil écrit, **en une seule écriture à la fin de l'étape téléphone**, prénom, nom, photo facultative, **téléphone**, `termsAcceptedAt`, `termsVersion`, `createdAt` et le bloc `analyticsConsent` défini par AUTH-B (fusionné). Garde-fou : cet écran est **inaccessible** sans acceptation (a) enregistrée sur l'appareil (critère C14 de l'Authentification). |
| **Schéma, règles et tests du téléphone** sur `users/{uid}` et `members/{uid}` | **ONB-B** (`backend-data`) | Les règles actuelles refusent toute clé téléphone (`firestore.rules`, `data-model.md` §8) : **ONB-B se termine avant la PR du profil** ; déploiement des règles seulement après tests verts, revue de sécurité et « oui » explicite du PM (`docs/dev/deploiement-regles.md`). |
| Routage au lancement (état du compte) | **Authentification (§5) pose la règle ; ce ticket l'implémente et la complète** (§6.2) | La règle des quatre ordres de §5 de l'Authentification est reprise telle quelle. |
| Déconnexion : purge du cache, arrêt du suivi, retour à la connexion sans carrousel (§7.6) | **Authentification** | Le bouton « Se déconnecter » des Réglages **appelle** cette procédure ; il ne la réécrit pas. |
| Suppression de compte (§7.7, AUTH-5) | **Authentification** | Le bouton « Supprimer mon compte » **ouvre** ce flux. Le texte du pop-in de Notion est remplacé (§4 #17). |
| Écran « Choisissez comment commencer » (routeur créer / rejoindre) | **Ce ticket** | Un écran, deux cartes. Il ne contient aucune règle métier de Miff. |
| Création d'une Miff, saisie et validation du code, erreurs, **consentement par Miff** (`members.sharing`), paywall pour une 2e Miff | **Tickets Miff** | Ce ticket donne la main aux écrans Miff et reprend la main quand une Miff existe (`memberships` non vide). Les règles notées dans le Notion Onboarding (code de 12 caractères, nom de 50 caractères, message d'erreur) sont **transmises** aux tickets Miff (§4 #8 et #9). |
| Définition de « onboarding terminé » et événement `onboarding_completed` | **Ce ticket** (la condition) ; les tickets Miff émettent `miff_created` / `miff_joined` | Terminé = première Miff créée ou rejointe **par une personne qui n'en avait aucune** ; jamais pour une reconnexion sur un nouvel appareil (une Miff existe déjà). |
| **Consentement au partage de position avec une Miff** (texte versionné, `consentedAt`) | **Tickets Miff** (donnée et écran) | Ce ticket **s'enchaîne** juste après (amorce de la permission du système) et réutilise la même présentation : écran fusionné retenu (décision Q3). |
| **Permission du système pour la localisation** : amorce, états, rappels, nouvelle demande | **Ce ticket** | Composant réutilisable. Appelé par l'onboarding, par le ticket Miff (activation du partage) et par le ticket Lieux (si la carte a besoin de la position **en premier plan seulement**). Jamais de demande « arrière-plan » depuis un autre ticket. |
| Géofencing, choix des zones surveillées, écritures de statut, `locationAccess` dans `status` | **Tracking / Lieux / Feed** | Ce ticket **fournit l'état de la permission** (lu, jamais écrit ailleurs) ; il n'enregistre aucune position. |
| Envoi de notifications push, centre de notifications, interrupteur « intelligentes » | **Ticket Notifications (Blaze)** | Ce ticket **demande la permission** dès la bêta (Q4) ; il n'envoie rien et ne fabrique aucun écran de réglage de notifications. |
| Vrai paywall, bandeau et écran « Abonnement » des Réglages, déclencheurs | **Ticket Premium** | Ce ticket livre seulement l'**écran provisoire** de fin d'onboarding (§6.12) ; aucune autre apparition automatique du paywall (Q5). |
| Contenu du Feed (dont boutons « Appeler » / « Message »), de l'Update, des lieux affichés dans l'onglet Profil, carrousel « Mes Miffs » | **Tickets Feed, Update, Miff, Lieux** | Ce ticket livre les **emplacements** (coquille de l'onglet Profil, onglets Feed et Update vides ou provisoires) et **fournit le numéro** (copie dans la fiche de membre) ; l'usage des boutons est décrit au ticket Feed. |
| Consentement statistiques / plantages : interrupteur des Réglages | **Ce ticket** (l'interrupteur) ; **AUTH-B** (donnée, règles ; **fusionné dans `main`**, PR #16) | L'interrupteur lit et écrit le bloc `analyticsConsent` ; effet immédiat sur la collecte (critère X20 de l'Authentification). |

### 3.1 Parcours nominal

```
Lancement ─ splash (animation) ─ pendant ce temps : purge du cache si demandée, App Check, lecture de la session
  ├─ pas de session
  │     ├─ première ouverture ─► [Carrousel 3 slides] ─► Inscription (Authentification) ─► …
  │     └─ app déjà ouverte avant (ex. après déconnexion) ─► Connexion (Authentification)
  └─ session valide ─► (état du compte, §6.2)

Après authentification réussie (Authentification, panneau de consentement résolu si nécessaire) :
  profil absent ─► [Profil : photo facultative, prénom, nom] ─► [Téléphone : pays + numéro, obligatoire]
                      └─► écriture unique de users/{uid} (confirmée par le serveur)
  profil présent, aucune Miff ─► [Choisissez comment commencer] ─► Créer / Rejoindre (tickets Miff)
        └─► [Consentement au partage + amorce de localisation, fusionnés : « Autoriser » / « Plus tard »]   (ticket Miff + ce ticket)
              └─► [Amorce de notifications : « Autoriser » / « Plus tard »]                                    (ce ticket)
                    └─► [Écran Paywall provisoire : « Continuer »]                                              (ce ticket, §6.12)
                          └─► Feed dans la barre d'onglets
  Miff présente ─► Feed dans la barre d'onglets
```

L'ordre localisation puis notifications diffère de celui de Notion (notifications d'abord) : la localisation est le geste central du produit et se fusionne naturellement avec le consentement de la Miff, qui vient juste après l'entrée dans la Miff. Le PM peut inverser sans conséquence technique.

### 3.2 États par écran

| Écran | États à couvrir | Dans les maquettes ? |
|---|---|---|
| **Splash** | animation complète ; animation réduite (réglage « réduire les animations ») ; session prête avant la fin ; session pas prête à la fin (fond bleu avec indicateur discret, jamais d'écran blanc) ; hors connexion | Description Notion seulement (pas de maquette lisible) |
| **Carrousel** | slide 1, 2, 3 ; bouton « Commencer » ; lien « J'ai déjà un compte » (retenu, Q8) ; lecteur d'écran ; droite-à-gauche | Slides 01 à 03 : oui. Lien « déjà un compte » : non |
| **Profil (étape d'onboarding)** | vide ; avec photo ; prénom/nom trop courts ou trop longs ; chargement ; hors connexion ; choix « prendre une photo / galerie » ; recadrage ; refus de l'appareil photo | Vide et rempli (écran 09) : oui. Erreurs : maquette d'erreur de l'original (« 3 caractères minimum »). Choix photo, recadrage : **non dessinés** |
| **Téléphone (obligatoire, Q1)** | vide ; pays par défaut ; numéro invalide ; numéro valide ; écriture en cours ; hors connexion ; erreur d'écriture ; mention de visibilité | Écran 10 : oui (état vide ; libellé « Nom » sur le champ numéro : erreur de maquette à corriger). Erreur de numéro : texte seulement dans l'original (« Numéro invalide ») |
| **Choix créer / rejoindre** | repos ; champ de code ouvert dans la carte « Rejoindre » ; (erreurs de code : tickets Miff) | Écran 11 : oui (la maquette montre la carte « Rejoindre » avec un champ de code vide ; superposition de formes à corriger par `design-system`) |
| **Amorce de localisation** (fusionnée avec le consentement de la Miff, Q3) | affichage ; après « Autoriser » : demande du système ; accordée en premier plan seulement ; accordée « toujours » ; refusée ; « plus tard » ; Android 11 et plus : écran de redirection vers les réglages ; localisation approximative seulement ; retour des réglages | Écran 14 (une seule variante, un seul bouton). Les autres états : **non dessinés** |
| **Amorce de notifications** (demandée dès la bêta, Q4) | affichage ; « Autoriser » puis demande du système ; accordée ; refusée ; « Plus tard » | Écran 13 : oui (texte et exemple à revoir, §6.11) |
| **Écran Paywall provisoire** (Q5) | affichage ; « Continuer » ; fermeture | Le Figma 15 montre un paywall **fonctionnel** avec prix : **à ne pas reprendre tel quel** (§6.12) |
| **Barre d'onglets** | onglet actif (pastille bleue) ; trois onglets ; masquée pendant l'onboarding, avec le clavier ouvert et dans les écrans plein écran | Dessin de la barre : oui (image aplatie, états non variantes) |
| **Onglet Profil** | photo et nom ; sans photo (substitut) ; emplacements « Mes Miffs » et « Lieux » | Original, page Profil : oui ; **non reconstruit** dans « Miff - Claude » |
| **Réglages (liste)** | liste ; bandeau Premium (hors ticket) | Original, page Réglages : oui ; non reconstruit |
| **Mes informations** | inchangé (bouton grisé) ; modifié (bouton actif) ; erreurs (prénom, nom, numéro) ; enregistrement ; hors connexion | Original : oui (prénom, nom, pays, téléphone, compteurs « 7/50 ») |
| **Mon compte** | e-mail affiché ; déconnexion avec confirmation ; accès à la suppression | Original : oui (avec des champs de changement de mot de passe, différés, §6.11) |
| **Langue** | liste de 2 langues ; langue actuelle cochée | Original : liste de 6 langues |
| **Confidentialité** (nouveau) | interrupteur statistiques ; état de la localisation ; état des notifications ; liens | **Non dessiné** |

Restent à dessiner par `design-system` : le recadrage de photo, la mention de visibilité du téléphone, l'écran Paywall provisoire, les états des amorces de permissions, l'écran « Confidentialité », l'onglet Profil et les Réglages dans le fichier de travail, le texte au **tutoiement** (décision de l'Authentification, Q2) sur tous les écrans repris du Figma, et les variantes de la barre d'onglets (actif/inactif, icônes recolorables).

---

## 4. Ambiguïtés, contradictions et incohérences relevées

Rien n'est tranché ici sans le dire. Chaque point renvoie à la règle proposée (§6) ou à une question.

| # | Point | Sources en conflit | Traitement proposé |
|---|---|---|---|
| 1 | **Blocage par la permission « Toujours »** : « l'utilisateur ne doit pas pouvoir utiliser l'app sans donner cette autorisation » (Onboarding, Tracking) | Notion vs `localisation-lieux-de-confiance.md` (critère : l'app reste pleinement fonctionnelle si « Toujours » est refusée, mise à jour manuelle + escalade) ; `docs/legal/README.md` §5 (Apple 5.1.2 : ne pas imposer la localisation ; Google : consentement avec information avant demande) ; `CLAUDE.md` (consentement explicite) | Jamais bloquant (§6.6, Q2). Rappel non bloquant. |
| 2 | **Une seule voie de sortie** (« le seul moyen de faire disparaître la pop-in est Autoriser ») et **relance toutes les 20 visites** pour les notifications | Onboarding vs ton bienveillant, faible charge cognitive | « Plus tard » toujours présent ; aucune relance par fenêtre ; rappel discret et non bloquant. Sur les téléphones, une permission refusée **ne peut plus être redemandée** par fenêtre (iOS : une seule fois ; Android 13 et plus : deux refus puis la fenêtre ne s'affiche plus, **à vérifier**) : la « relance toutes les 20 visites » **ne fonctionnerait donc pas** et ne ferait qu'agacer. |
| 3 | **Ordre « Toujours » d'emblée** | Onboarding/Tracking vs spec localisation (« jamais d'emblée, d'abord pendant l'utilisation »), et mécanique des systèmes (iOS : en deux temps ; Android 11 et plus : « Toujours » ne s'accorde que dans les réglages du système) | Deux temps, avec amorce avant (§6.6). Android : comportement distinct selon la version (10, 11 et plus), absent des tickets Notion pour Android 8 à 10 (minSdk 26). |
| 4 | **Texte de l'amorce de localisation** (« Miff a besoin d'accéder à votre localisation pour permettre à vos proches de savoir que vous êtes ou non en sécurité ») : n'explique ni « en arrière-plan », ni **ce qui est partagé** (un état, pas une position), ni **avec qui**, ni comment arrêter | Figma 14 vs exigence des stores (`docs/legal/README.md` §5 : information visible dans l'app **avant** la demande, pour la localisation en arrière-plan, pas seulement dans la politique) | Amorce réécrite (§6.11) ; texte à valider par le PM. |
| 5 | **Consentement par Miff absent des tickets Notion** : « un code valide → il rejoint la Miff et on affiche le feed » | Onboarding vs `data-model.md` §4 et §5 (écran de confirmation + consentement avant d'écrire la fiche membre) | Signalé aux tickets Miff ; ce ticket s'enchaîne après (§3). |
| 6 | **Slide 3 du carrousel et amorce 13 promettent des notifications** (« Amandine n'est toujours pas rentré à la maison ») alors qu'aucun envoi n'est possible sans Blaze ; formulation inquiétante ; faute d'accord (« rentré » pour Amandine) | Figma vs décision Blaze et ton (`CLAUDE.md`) | **Q8** : slide 3 reformulée honnêtement. **Q4** : l'amorce de notifications est conservée (permission demandée dès la bêta) mais son texte dit qu'elles arrivent « bientôt » et son exemple est retiré ou marqué comme exemple (§6.11). |
| 7 | **Numéro de téléphone** : obligatoire de fait (le bouton ne s'active qu'avec au moins un chiffre), pays par défaut, aucune vérification ; usage = boutons « Appeler » / « Message » du Feed | Onboarding/Paramètre vs minimisation (`data-model.md` §11), absence du schéma et **refus par les règles actuelles**, ticket Authentification §12 | **Décision Q1 : obligatoire.** Spécifié en §6.4 ; incohérence « règles refusent le champ » résolue par ONB-B (§8, §12) ; visibilité décidée (N1, option A) ; risques assumés (R7). |
| 8 | **Longueur du nom de Miff** : « maximum 50 caractères » (Onboarding) contre **1 à 40** dans le modèle de données et les règles | Notion vs `data-model.md` §2.5 | À transmettre aux tickets Miff ; la règle des règles Firestore (40) l'emporte tant qu'elle n'est pas modifiée par `backend-data`. |
| 9 | **Code de la Miff** : « exactement 12 caractères » (cohérent avec le modèle) ; message d'erreur dans une image illisible ; 31 symboles (sans I, L, O, 0, 1) | Notion vs `data-model.md` §2.4 | À transmettre aux tickets Miff : majuscules automatiques, espaces et tirets tolérés à la saisie, message unique bienveillant (`data-model.md` §8). |
| 10 | **Prénom et nom** : « au moins 2 caractères » (Onboarding, Paramètre, 2 sur chaque), « 3 caractères minimum » (Figma, maquette d'erreur de l'original), **1 à 50** (règles Firestore) ; compteur « 7/50 » dans Réglages | Notion vs Figma vs règles ; question Q15 de `data-model.md` ouverte | Q6 (recommandation : 1 à 50 après suppression des espaces). |
| 11 | **Photo obligatoire ?** Le gros bouton photo du Figma et le titre « Permettez à vos proches de vous identifier » le laissent croire ; le Notion n'exige que les noms ; les règles la rendent facultative (décision PM du 2026-10-16) ; la spec `inscription-connexion.md` dit « étape obligatoire : photo + prénom + nom » | Figma/spec vs Notion/règles | Facultative (Q7). Un substitut (initiales) s'affiche partout sans photo. |
| 12 | **`onboarding_cta_tapped` ne peut jamais partir** : le contrat impose le consentement avant tout événement ; le consentement (b) n'est demandé qu'à l'inscription, **après** le carrousel | `analytics-events.md` vs ordre des écrans (Authentification Q8) | Ne rien envoyer depuis le carrousel ; **PR distincte** sur le contrat pour retirer ou requalifier cet événement (décision PM requise, mais non bloquante pour le ticket). |
| 13 | **Retour arrière** : « retour = écran de connexion, on reverra l'onboarding » (Notion) | Décision Q11 de l'Authentification (pas de flèche retour sur la première étape après la création du compte ; carrousel seulement à la première ouverture) | Décision de l'Authentification appliquée. **Décision Q14 : aucun lien « Ce n'est pas toi ? »** ; la déconnexion se fait depuis les Paramètres (risque assumé R16). |
| 14 | **« Le tout premier écran de l'app » après déconnexion ou suppression** (Paramètre) | Notion vs Authentification §7.6 (retour à la connexion **sans** carrousel) | « Premier écran » = écran de connexion (après suppression de compte : le même, avec un message calme). |
| 15 | **Langues** : 6 dans la maquette Réglages (français, anglais, espagnol, allemand, portugais, italien) ; « par défaut l'anglais, puis détecter la langue de l'appareil » | Figma/Notion vs décision INIT §9 (français + anglais) et §4.F | Deux langues listées ; comportement « langue de l'appareil, sinon anglais » **fourni par le système** (§6.11). |
| 16 | **Deux écrans Profil/Réglages** : le « Profil » de Notion est un **onglet** de la barre ; « Mes informations » est dans les **Réglages** ; l'étape de profil d'onboarding est un troisième écran, avec les mêmes champs | Notion | Mêmes champs, mêmes règles, un composant partagé (§6.3, §6.9). |
| 17 | **Pop-in de suppression de compte** : « Vous perdrez instantanément toutes vos données y compris votre abonnement… Attention, vos données seront irrécupérables » | Paramètre vs Authentification §7.7 (liste des Miffs et des membres, ton calme, rappel que l'abonnement du magasin **n'est pas** résilié) | Le texte de Notion est **remplacé** par celui d'AUTH-5. |
| 18 | **Changement de mot de passe dans « Mon compte »** (3 champs) | Paramètre vs comptes Apple/Google (aucun mot de passe) et faible charge | Différé ; « Mot de passe oublié » couvre le besoin (Q12). Ligne masquée pour un compte Apple ou Google. |
| 19 | **Photos de stock dans les maquettes** (calques nommés « headshot-portrait-smiling-woman-…-260nw-… », type vignette de banque d'images) dans le Profil et le carrousel | Figma original | Droits d'utilisation à vérifier ; remplacer par des illustrations libres avant tout visuel de store. |
| 20 | **Tabbar : page Notion vide** ; trois destinations déduites du Figma (maison, bouton rond central, profil) ; le rôle du bouton central n'est documenté nulle part | Notion vs Figma, `design-system.md` §4 | §6.7 : Feed, Update (bouton central, ticket Update), Profil ; Q13 à confirmer. |
| 21 | **Sans Miff** : l'Authentification dit « écran de choix » (§5, ordre 3) ; le ticket Feed prévoit un état « Pas encore de Miff » **dans le Feed** avec les deux boutons | Authentification/Onboarding vs Feed | Même règle partout : écran de choix plein écran, sans barre d'onglets (Q13). Le Feed vide devient inatteignable (à retirer du ticket Feed ou à assumer). |
| 22 | **« Une Miff » nommée « Famille »** dans le Profil (« Famille Martin ») et la maquette de création | Figma/Notion Profil vs `CLAUDE.md` (cercle familial, amical, romantique) | Vocabulaire neutre (« Miff »). Le nom proposé en exemple dans « Nouvelle Miff » est une suggestion, pas une valeur par défaut. |
| 23 | **Cadre légal des permissions de caméra et de photothèque** | Onboarding (« choisir dans la galerie ou prendre une photo ») | Utiliser le sélecteur de photos du système (aucune permission de photothèque) ; l'appareil photo ne demande de permission que sur iOS et seulement à l'appui (§6.3). |
| 24 | **Statut Notion** : le brief de départ les décrit parfois « Backlog discovery » ; Notion donne « Spec en attente de review IA » pour les 6 tickets de ce périmètre, « A cadrer » pour Premium | brief vs Notion | À titre d'information. |

### Incohérences **hors périmètre** repérées en chemin (à porter à leur challenge, sans réponse nécessaire ici)

- **Tracking** : « on enregistre sa localisation » à l'ouverture et à la fermeture de l'app, et **Feed** : bouton « Localiser » vers une carte avec « les coordonnées de la dernière position connue » ; or le modèle de données **ne stocke jamais de coordonnées** (`data-model.md` §1). L'amorce de localisation (§6.11) ne peut promettre « jamais ta position précise » que si ces deux tickets sont alignés : **tant qu'ils ne le sont pas, l'amorce n'écrit pas cette promesse** (texte conditionnel noté en §6.11).
- **Tracking** : « les 20 lieux les plus proches » pour Android alors que la limite est de 100 ; « Toujours + précision exacte exigées » (message d'erreur anxiogène « Miff a besoin de l'option… pour fonctionner »).
- **Premium** : « alors qu'il en a déjà 2 » pour les lieux alors que la règle gratuite est **1** lieu ; paywall « toutes les 10 visites » ; achats non validables sans serveur.
- **Feed** : bouton « Message » (SMS) et « Téléphone » : voir Q1.
- **Centre de notifications** (icône du Feed) : sans push, sa source de données n'existe pas.
- **`data-model.md` Q20** (déconnexion et partage) : se déconnecter n'arrête pas le statut des proches, qui voient alors un état figé ; la confirmation de déconnexion de §6.9 l'écrit honnêtement.

---

## 5. Splash, logo et icône

### 5.1 Splash (animation de lancement)

**Contenu décrit par le PM (Notion)** : fond bleu ; au milieu, le logo dans un cœur vert qui pivote légèrement de droite à gauche ; le cœur vert apparaît, gonfle jusqu'à éclater ; à l'éclatement, l'animation se termine et l'app s'ouvre. Le ticket parle d'un « GIF » à créer à partir du logo.

**Hypothèses (l'image du logo n'est pas lisible)** : bleu = `action/primary` `#043cd1` ; cœur vert = `brand/primary` `#d1d828` (jaune-vert du design system) ; logo = la marque (M blanc dans le cœur, ou mot-symbole). **À confirmer par le PM (Q9, Q10).**

**Règles proposées** :
1. **Pas de GIF.** Animation dessinée dans l'app (vectorielle) : plus légère, nette à toutes les densités, pilotable (réduction des animations, durée). Aucune bibliothèque payante ; une bibliothèque d'animation gratuite n'est pas nécessaire.
2. **Durée totale** : environ 1,8 s, plafond 2,5 s (Q9). Lue **une fois par démarrage à froid**, jamais à la reprise depuis l'arrière-plan.
3. **Pendant l'animation**, l'app fait en parallèle : lecture de la purge de cache éventuelle, App Check, vérification de session et premières lectures de routage. L'animation **ne dépend pas du réseau** et **ne retarde pas** l'ouverture au-delà de sa durée propre. Si le routage n'est pas prêt à la fin : fond bleu avec un indicateur très discret (jamais un écran blanc ni un clignotement).
4. **Réglage « réduire les animations »** du système : pas de rotation ni de gonflement ; logo fixe environ 0,6 s puis fondu.
5. **Accessibilité** : aucun clignotement rapide (pas plus de trois flashs par seconde) ; l'éclatement est **doux** (pas d'éclair blanc ni rouge, pas d'effet d'alarme : ton bienveillant). Contenu annoncé une fois au lecteur d'écran (« Miff »).
6. **Aucun son, aucune vibration, aucun événement d'analyse.**
7. **Cohabitation avec l'écran de lancement du système** : Android 12 et plus impose un écran de lancement système (icône sur fond de couleur, durée courte, peu personnalisable : **à vérifier**) ; avant Android 12 et sur iOS, un écran de lancement statique. Dans les deux cas : **même fond bleu** que le début de l'animation et même emplacement du cœur, pour une transition sans saut. L'animation complète est donc une **vue de l'app** qui prend le relais de l'écran système.
8. **Coût de batterie et de mémoire** : négligeable (une seule animation brève).

### 5.2 Logo

Trois usages, **tous bloqués par l'absence de fichier source** :
- **Mot-symbole** en haut des écrans d'authentification et d'onboarding (aujourd'hui des vecteurs aplatis dans le Figma, nommés « AppMockup ») ;
- **Marque de l'icône d'application** (M blanc et cœur sur fond bleu, hypothèse de `design-system.md` §6) ;
- **Version pour le splash** (cœur + logo).

Le socle (INIT-2 et INIT-4) a posé une icône **provisoire, « ne pas publier »** sur les deux plateformes. Elle suffit pour développer et tester ; **elle ne peut pas partir sur les stores**.

**Voies pour obtenir le logo, toutes à 0 €** :
- (A) Le PM exporte lui-même les calques du logo en SVG depuis le Figma original (sélection du calque, panneau d'export, format SVG) : reste possible si l'extraction ne donne pas un rendu satisfaisant.
- (B) **Retenue (décision Q10)** : l'agent `design-system` **extrait les vecteurs depuis le Figma en lecture** (assets déjà vectoriels, aucune écriture dans le Figma original) et le PM valide le rendu. Lancée séparément, dans ONB-0.
- (C) Redessin : non recommandé (coût, risque de dériver de l'identité).

Dans tous les cas, deux livrables sont nécessaires avant les stores : l'**icône définitive** (Android : couches adaptatives 108 dp avec couche monochrome pour Android 13 et plus ; iOS : 1024 × 1024 px, opaque, sans coins arrondis ; icône de fiche Google Play 512 × 512 px) et le **logo en haute résolution** (déjà réclamé par le ticket Authentification, §13 étape 9, pour l'écran de consentement Google). Spécifications de marque (zone de protection, taille minimale, usages interdits, version sur fond sombre) : inconnues, à fournir si elles existent.

### 5.3 Ce qui peut avancer sans le logo
Toute la mécanique du splash (durée, réduction des animations, transition, cohabitation avec l'écran système) avec le dessin provisoire ; tout le reste du ticket. Seule la **fermeture** du ticket « identité » (icône et logo définitifs) attend le fichier.

---

## 6. Règles métier et comportements

### 6.1 Carrousel de bienvenue

- **Quand** : à la **première ouverture sans session** seulement (décision Q11 de l'Authentification). Un drapeau local (non sensible, jamais envoyé) mémorise que le carrousel a été vu ; il est posé quand la personne appuie sur « Commencer » ou rejoint l'inscription. Réinstaller l'app peut le réinitialiser : aucune conséquence.
- **Après une déconnexion, ou une suppression de compte** : écran de connexion directement (Authentification §7.6).
- **Contenu** : 3 slides, balayage horizontal, points de pagination. **Défilement 100 % manuel** (décision Q8 : le défilement automatique gêne la lecture et l'accessibilité et n'est pas nécessaire pour trois écrans). Bouton « Commencer » présent sur chaque slide (Figma), qui mène à l'inscription ; la flèche retour de l'inscription ramène au carrousel.
- **Lien « J'ai déjà un compte »** (décision Q8), sous le bouton : évite de faire traverser « carrousel, inscription, déjà un compte » à quelqu'un qui change de téléphone. Mène à la connexion.
- **Textes** : un titre **différent par slide** (le Figma répète trois fois « Savoir qu'ils vont bien, sans les déranger ») ; au tutoiement ; aucune promesse que l'app ne tient pas (notifications, voir §4 #6) ; au moins une phrase qui dit que **la personne choisit ce qu'elle partage et avec qui**, et qu'elle peut arrêter à tout moment (confiance, principe transverse du `CLAUDE.md`) ; cette phrase ne dit « pas de trajet enregistré » que si Feed et Tracking sont alignés sur le modèle (§4 hors périmètre). Les textes viennent du PM (Q8) ; des propositions sont en §6.11.
- **Visuels** : captures de l'app avec des **illustrations ou avatars libres de droits**, pas de photos de banque d'images (§4 #19) ; les formes décoratives en vecteur.
- **Analyse** : aucun événement (consentement non encore demandé, §4 #12).
- **Accessibilité** : pagination annoncée (« Page 1 sur 3 ») ; image décorative ou texte alternatif court ; navigation possible sans balayage (bouton ou geste d'accessibilité) ; lisible en police agrandie ; en droite-à-gauche, l'ordre des slides et des points s'inverse.

### 6.2 Routage selon l'état du compte

Reprise de §5 de l'Authentification, **complétée**. À chaque ouverture de l'app, après le splash, et après chaque étape :

| Ordre | Condition | Écran | Remarques |
|---|---|---|---|
| 0 | Drapeau « purge du cache à faire » présent | Purge **avant tout accès à Firestore** (`data-model.md` §8) | Invisible pour la personne |
| 1 | Aucune session | Carrousel (première ouverture) ou Connexion | Authentification |
| 1b | Session, acceptation (a) absente sur l'appareil, ou compte tout juste créé par connexion sociale | Panneau de consentement de l'Authentification (§7.8) | Rien d'autre n'est accessible |
| 2 | Session, document `users/{uid}` absent | **Profil** puis **Téléphone** (§6.3, §6.4) | Reprise à l'étape où la personne s'était arrêtée d'après le brouillon local (ci-dessous), sinon au profil. Un compte ancien abandonné avant le profil suit le même chemin. |
| 2b | Session, `users/{uid}` présent **sans numéro de téléphone** (compte créé avant ONB-B, ou jeu de test) | **Étape téléphone seule** (mise à jour du document) | Garantit qu'aucun compte n'utilise l'app sans numéro, sans migration serveur |
| 3 | Profil et téléphone présents, `users/{uid}/memberships` vide **après nettoyage au lancement** | **Choix créer / rejoindre** (§6.5) | Si la personne vient de perdre sa dernière Miff (retrait, Miff supprimée) : message neutre (« Tu ne fais plus partie de la Miff « X » » / « La Miff « X » n'existe plus »), puis le même écran, **sans barre d'onglets** (Q13) |
| 4 | Miff présente | **Feed** dans la barre d'onglets | L'état des permissions de localisation et de notifications est lu ; si insuffisant, rappel non bloquant (§6.6) |

Règles :
- **Aucune progression n'est stockée côté serveur** : l'étape se lit dans les données (2 lectures, négligeable devant 50 000 par jour, `data-model.md` §12). Fermer l'app en plein onboarding ramène à la bonne étape (exigence du Notion).
- **Brouillon local** : tant que `users/{uid}` n'est pas écrit, les valeurs saisies (prénom, nom, photo recadrée, pays, numéro) et l'étape atteinte sont gardées dans le stockage privé de l'app (jamais envoyées, jamais sauvegardées dans le cloud : `allowBackup` désactivé, INIT-2). Le brouillon est supprimé **après la confirmation du serveur** et à toute déconnexion.
- **Le nettoyage au lancement** des Miffs perdues (`data-model.md` §8, ligne « Nettoyage au lancement ») s'exécute **avant** l'ordre 3, sinon un index périmé ferait croire à une Miff existante.
- **Hors connexion** au lancement avec une session valide et aucun cache (cas possible sur iOS si la session persiste après réinstallation, Authentification §9) : message neutre « Pas de connexion pour le moment » avec « Réessayer », **jamais** l'écran de profil par défaut (on ne sait pas si le profil existe).
- **Une lecture refusée par App Check** ou un quota atteint : même message neutre.
- **Reprise d'un onboarding en pleine écriture** : le profil n'est considéré écrit qu'après confirmation du serveur (§6.4).

### 6.3 Création du profil (étape d'onboarding)

**Champs de cette étape** : photo (facultative), prénom, nom. **Aucune écriture à ce stade** : le profil est écrit **en une seule fois à la fin de l'étape téléphone** (§6.4). La flèche mène à l'étape téléphone.

| Règle | Détail |
|---|---|
| Bouton « flèche » | Actif quand prénom **et** nom ont au moins 1 caractère après suppression des espaces autour (Q6). Jamais bloqué par une règle invisible : si un champ est trop long ou vide, message au ton calme sous le champ. |
| Longueur maximale | 50 caractères par champ (règles Firestore) ; compteur discret quand on approche (« 45/50 ») ; au-delà, saisie empêchée avec le message « 50 caractères maximum ». |
| Contenu | Aucune restriction de caractères au-delà de la longueur (lettres accentuées, tirets, apostrophes, caractères non latins acceptés) ; espaces autour supprimés, espaces multiples réduits. Aucune unicité. |
| Préremplissage | Prénom et nom fournis par Google ou Apple (fournis **une seule fois** par Apple, Authentification §6), tronqués à 50 caractères, modifiables. **Aucune photo n'est importée.** |
| Photo (facultative, Q7) | Appui sur le rond : choix « Prendre une photo » ou « Choisir dans la galerie » ; si une photo existe : « Supprimer la photo ». Recadrage **carré** obligatoire avant validation (Notion : « possibilité de la redimensionner »). Sans photo, un substitut (initiales sur fond de la palette) s'affiche partout. |
| Taille stockée | JPEG carré d'environ 160 × 160 px, qualité réduite jusqu'à **20 000 octets au plus** (`data-model.md` §2.1) ; si l'image ne rentre pas, nouvelle compression ; si elle ne peut pas rentrer, message calme et choix d'une autre image. |
| Vie privée de la photo | L'image réencodée **ne conserve aucune métadonnée** (position GPS, date, appareil). La photo n'est enregistrée ni dans la galerie ni ailleurs que dans le profil. Mention sous le rond : « Visible par les membres de tes Miffs » (la photo est une donnée sensible, `data-model.md` §11). |
| Permissions de l'appareil | **Galerie** : sélecteur de photos du système, aucune permission à demander (à confirmer par les devs). **Appareil photo** : Android, via l'application photo du système sans permission déclarée (à confirmer) ; iOS, demande à l'appui sur « Prendre une photo », avec un texte d'usage bienveillant. Un refus renvoie vers la galerie, sans blocage. |
| Retour | **Pas de flèche retour** sur cette première étape (Authentification Q11). Le bouton ou geste « retour » du système, sur Android, met l'app en arrière-plan (jamais déconnexion ni retour à l'inscription). |
| Échappatoire | **Aucune** (décision Q14) : pas de lien « Se déconnecter » pendant l'onboarding. La déconnexion se fait depuis les Paramètres, accessibles une fois l'onboarding terminé. Conséquence assumée : une personne qui a créé un compte par erreur doit aller au bout des étapes pour pouvoir se déconnecter (R16). |
| Analyse | Aucun événement propre à cette étape. |

### 6.4 Téléphone obligatoire (écran 10, décision Q1)

**Décision du PM (contre ma recommandation de retirer ce champ)** : le numéro de téléphone est **obligatoire** dans l'onboarding. Il suit immédiatement l'étape profil et **ferme** le profil : c'est ici que `users/{uid}` est écrit.

**Champs** : « Pays » (sélecteur) et « Numéro de téléphone ». Titre proposé, au tutoiement : « Pour que tes proches puissent t'appeler ou t'écrire » (§6.11).

| Règle | Détail |
|---|---|
| Obligatoire | Aucun « Passer ». Le bouton est grisé tant qu'aucun chiffre n'est saisi (Notion : « au moins 1 chiffre »). À l'appui, la forme est vérifiée ; si elle est invalide, un message calme apparaît sous le champ (« Ce numéro ne semble pas correct. ») et la personne peut corriger ; jamais de message technique. |
| Pays | Sélecteur listant **tous les pays** (drapeau, nom, indicatif). Valeur par défaut : le pays correspondant à la **région de l'appareil** (Notion : « le pays qui correspond à la langue détectée »), **sinon la France**. Choisir un pays met à jour l'indicatif affiché (ex. +33 France, +242 Congo-Brazzaville). Coller un numéro international (« + … ») déduit le pays. |
| Saisie | Numéro **national**, espaces, tirets, points et parenthèses tolérés, zéro initial toléré et retiré (la maquette dit « sans le premier zéro » : le texte d'aide en tient compte). Clavier numérique ; correction automatique désactivée ; saisie automatique du système autorisée (numéro de l'appareil non lu par l'app). |
| Format stocké | **E.164** : chaîne `+` suivie de l'indicatif et du numéro, 8 à 16 caractères au total (ex. `+33612345678`). Jamais de format national en base. Normalisation et validation de forme par une bibliothèque libre (par exemple libphonenumber, licence Apache 2.0, gratuite : poids à vérifier) selon les plans de numérotation du pays choisi. Tout numéro de **forme valide** est accepté, mobile ou fixe. |
| Aucune vérification | **Pas de SMS, pas de code** : l'authentification par téléphone est indisponible sur Spark (INIT §3). La validité est une validité de **forme** : rien ne prouve que la personne possède le numéro. **Aucune unicité** : deux comptes peuvent saisir le même numéro (non empêchable sans serveur). |
| Écriture | À la fin de cette étape : **une seule écriture** de `users/{uid}` avec prénom, nom, photo, numéro E.164, `createdAt`, `termsAcceptedAt`, `termsVersion` (conservés depuis l'inscription) et `analyticsConsent` (AUTH-B). L'étape suivante n'apparaît qu'après **confirmation du serveur** (délai court, puis message neutre) ; hors connexion : « Pas de connexion pour le moment… » et champs conservés (brouillon local, §6.2). |
| Copie dans la Miff | À la création ou à l'arrivée dans une Miff (tickets Miff), le numéro est copié dans la fiche de membre avec le profil (`members/{uid}`, comme prénom, nom et photo, `data-model.md` §2.6). |
| Modification | Dans Réglages > Mes informations (pays et numéro, mêmes règles) ; l'enregistrement met à jour `users/{uid}` et chaque `members/{uid}`. Le numéro **ne peut pas être vidé** (obligatoire). |
| Suppression | Avec la fiche de membre quand la personne quitte ou est retirée d'une Miff, avec `users/{uid}` à la suppression du compte (AUTH-5). Aucune copie ailleurs, aucun export automatique. |
| Analyse et journaux | Jamais dans un événement, un journal ou un rapport de plantage. |

**Utilisation (décrite pour le ticket Feed, hors périmètre ici)** : sur la carte d'un proche, le bouton « Appeler » ouvre l'application Téléphone du système avec le numéro, le bouton « Message » ouvre l'application de messages du système. **Miff n'appelle ni n'envoie de SMS lui-même, ne lit pas les contacts de l'appareil, n'envoie rien au numéro et ne l'utilise ni pour se connecter, ni pour la prospection, ni pour l'analyse.** Les deux boutons ne s'affichent que si le proche a un numéro (les comptes antérieurs à ONB-B n'en ont pas).

**Qui voit le numéro : décision du PM du 2026-10-24 (N1, option A)**

**Décision (option A)** : un utilisateur ne voit **que** les numéros de téléphone des membres des Miffs auxquelles il appartient, et son numéro n'est vu **que** par les membres des Miffs dont il fait partie. Le numéro est donc visible **par tous les membres de chaque Miff dont la personne fait partie, et par eux seuls**, par le biais de la copie dans la fiche de membre. Il n'est jamais visible : du public, de quelqu'un qui n'est pas membre d'une de ses Miffs, de l'équipe via l'app, d'un ex-membre (la copie disparaît avec la fiche de membre). La lecture n'est **pas** conditionnée au partage de position (les fiches de membre sont lisibles de tous les membres tant que la Miff existe, `data-model.md` §9). **Mention obligatoire sur l'écran de saisie du numéro**, sous le champ, de façon claire et non masquée : « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » (§6.11). Le même texte figure dans Réglages > Mes informations, à côté du champ.

Options **écartées** par le PM : (B) visible du seul créateur de la Miff ; (C) visible seulement des membres qui partagent eux-mêmes leur position ; (D) choix par Miff (interrupteur). **Conséquence : ONB-B, ONB-2 et ONB-6 sont débloqués** (plus aucune décision en attente) ; aucune règle de lecture spécifique n'est ajoutée : la visibilité est celle de la fiche de membre.

**Risques assumés par le PM avec cette décision** (détail en §13, R7) :
1. **Minimisation (RGPD)** : une donnée de contact obligatoire, non strictement nécessaire au geste central. À déclarer dans la politique de confidentialité (qui **ne la mentionne pas encore**, `docs/legal`) et dans les déclarations des stores (« numéro de téléphone » : étiquettes de confidentialité d'Apple, section Sécurité des données de Google). Pas de relecture juridique (décision du 2026-10-23).
2. **Numéro non vérifié** : faute de frappe, numéro d'un tiers ou numéro inventé : on appelle ou écrit à la mauvaise personne ; usage possible pour embêter quelqu'un en inscrivant son numéro dans une Miff.
3. **Visible des membres** : toute personne qui connaît le code d'une Miff la rejoint **immédiatement**, sans approbation (`data-model.md` §5, question Q7 ouverte) et **peut alors lire les numéros de tous les membres**. Une fuite de code devient une fuite de numéros. Atténuations existantes : le code est renouvelé à chaque retrait d'un membre (DUR-2) et le créateur peut le régénérer ; atténuation possible à reconsidérer : approbation par le créateur à l'arrivée.
4. **Mineurs** : aucun âge minimum (décision Q7 de l'Authentification) : le numéro d'un mineur est lisible de ses co-membres.
5. **Obligation = friction** : une personne qui ne veut pas donner son numéro ne peut pas utiliser l'app (risque d'abandon).

**Conséquence technique : les règles Firestore actuelles n'acceptent pas ce champ** (`users` : seulement `firstName`, `lastName`, `avatar`, `createdAt`, `termsAcceptedAt`, `termsVersion`, `analyticsConsent` ; `members` : liste fermée de clés ; `data-model.md` dit explicitement « ne jamais écrire de téléphone, refusé »). Le sous-ticket **ONB-B** (§8, §12) change le schéma, les règles et les tests **avant** les écrans de profil. Aucune écriture du téléphone n'est possible sans lui.

### 6.5 Choix « Créer / Rejoindre une Miff » (routeur)

- **Quand** : ordre 3 du routage. Titre au tutoiement (proposition §6.11) ; deux cartes, « Créer une nouvelle Miff » (« Tu pourras ensuite inviter tes proches ») et « Rejoindre une Miff existante » avec le champ de code de 12 caractères dans la carte (Figma, Notion).
- **Ce que ce ticket fait** : afficher l'écran, **ouvrir** l'écran de création (ticket Miff) ou **transmettre** le code saisi à l'écran de jonction (ticket Miff). Il ne valide pas le code, ne génère rien, n'écrit rien.
- **Règles de saisie du code transmises aux tickets Miff** : majuscules automatiques ; espaces et tirets tolérés et ignorés ; bouton actif quand les 12 caractères sont saisis ; correction automatique et suggestions désactivées ; collage autorisé ; erreur **unique et bienveillante** sans distinguer les causes (« Ce code ne correspond à aucune Miff ») ; pause après 5 échecs consécutifs (`data-model.md` §8).
- **Gratuit** : une personne sans Miff peut créer ou rejoindre sa première Miff sans paywall. Le paywall n'apparaît que pour la **2e** Miff (tickets Miff, Premium), jamais ici.
- **Pas de flèche retour** sur cet écran : le profil et le numéro sont déjà enregistrés et restent modifiables dans Réglages > Mes informations.
- **Reconnexion sur un nouvel appareil** : si une Miff existe déjà, cet écran n'est **jamais** affiché (ordre 4).
- **Fin d'onboarding** : dès que la première Miff existe, `onboarding_completed` (`via` = `created` ou `joined`) part **si et seulement si** le consentement (b) est donné et que la personne n'avait aucune Miff avant ; une seule fois.

### 6.6 Permissions du système

#### Principes (non négociables)
1. **Expliquer avant de demander** : écran d'information dans l'app, avant la fenêtre du système (exigence Apple et Google pour la localisation en arrière-plan, `docs/legal/README.md` §5 ; spec localisation).
2. **Jamais bloquant** : l'app reste utilisable, avec ses mises à jour manuelles et l'escalade automatique (spec `localisation-lieux-de-confiance.md`), quelle que soit la réponse.
3. **Au moment de la valeur** : juste après l'entrée dans une Miff, jamais sur une première ouverture à froid ni sous forme de fenêtre posée sur le Feed.
4. **Aucune relance par fenêtre** : un seul affichage de l'amorce par contexte ; ensuite un rappel **non modal**, que la personne ferme.
5. **Le système a la vérité** : l'état réel de la permission est relu à chaque ouverture et au retour des réglages ; aucune table « demandé / non demandé » n'est stockée côté serveur.
6. **Deux couches, deux propriétaires** : (1) **consentement au partage avec une Miff** (`members.sharing`, texte versionné, daté ; tickets Miff) ; (2) **permission du système** (ce ticket). Un utilisateur peut consentir à partager et refuser la localisation en arrière-plan : le modèle prévoit cet état (`status.locationAccess`, badge « localisation limitée », `data-model.md` §3).
7. **Honnêteté** : l'amorce ne promet que ce que l'app fait (§4 hors périmètre).

#### Localisation : séquence retenue (décision Q3)
Après la création ou l'arrivée dans une Miff, **un seul écran d'information** (fusionné avec le consentement de la Miff, décision Q3) :
- Ce que l'app utilise : la position **de ton téléphone**, **en arrière-plan** (« même quand l'app est fermée »), pour te signaler automatiquement quand tu arrives dans un de tes lieux de confiance ou en pars.
- Ce que les autres voient : **uniquement un état** (par exemple « à la maison » ou « hors de tes lieux ») et l'heure ; **jamais** ta position précise ni ton trajet, **tant que** Feed et Tracking confirment ce principe (§4 hors périmètre).
- **Avec qui** : les membres de la Miff nommée à l'écran.
- **Contrôle** : désactivable à tout moment dans les Réglages ; sans la localisation en arrière-plan, l'app fonctionne avec des mises à jour manuelles.
- **Deux boutons de poids égal** : « Autoriser » et « Plus tard » (jamais un seul bouton).

Puis, après « Autoriser » :

| Plateforme et version | Séquence |
|---|---|
| **Android 8 à 9** (API 26 à 28) | Une seule fenêtre du système (la localisation couvre aussi l'arrière-plan : **à vérifier**) |
| **Android 10** (API 29) | Fenêtre du système avec le choix « Toujours autoriser » (**à vérifier**) |
| **Android 11 et plus** (API 30+) | 1) Fenêtre « pendant l'utilisation » ; 2) **second écran d'information** de l'app expliquant pourquoi « Toujours » est utile et comment le choisir ; 3) bouton « Ouvrir les réglages » vers la page de la permission du système ; 4) au retour dans l'app, relecture de l'état ; l'écran se ferme tout seul si « Toujours » est accordé. « Plus tard » reste possible à chaque écran. |
| **Android 12 et plus** | La personne peut accorder seulement la position **approximative** : état « localisation approximative », explication douce (la précision améliore la détection des lieux), jamais d'insistance |
| **iOS** | 1) Demande « pendant l'utilisation » ; 2) demande d'évolution vers « Toujours » (le système peut l'afficher plus tard) ; état « Autoriser une fois » possible ; position **approximative** possible (iOS 14 et plus) : mêmes états que sur Android. Textes d'usage du système (« NSLocationWhenInUse… », « …AlwaysAndWhenInUse… ») bienveillants et précis (§7) |

États à reconnaître : **jamais demandé**, **premier plan seulement**, **toujours**, **refusé**, **refusé définitivement** (Android : « ne plus demander » ; iOS : après une réponse), **restreint** (iOS : contrôle parental), **approximatif**. À chaque changement vers un état moins bon (une personne retire la permission dans les réglages du téléphone), un rappel non modal réapparaît (Notion : « Si un jour il désactive… »).

**Rappel non modal** : bandeau d'une ligne dans le Feed et ligne d'état dans « Confidentialité » (Réglages) : « La localisation en arrière-plan est désactivée : tes proches ne voient que tes mises à jour manuelles. » avec « Activer » ; fermable. L'action « Activer » rouvre l'amorce (ou renvoie vers les réglages du téléphone si la fenêtre du système ne peut plus s'afficher). Jamais de fenêtre automatique à l'ouverture.

#### Notifications (demandées directement, y compris en bêta, Q4)

**Décision du PM, contre ma recommandation** : la permission de notifications est demandée **dès l'onboarding, y compris en bêta**, par une amorce (écran 13) puis la fenêtre du système.

- **Honnêteté** : l'envoi de notifications quand l'app est fermée exige un serveur (**plan Blaze, non activé**, `data-model.md` §13). **Tant que ce n'est pas le cas, aucune notification ne sera délivrée** : la permission est demandée, rien n'arrivera. L'amorce le dit sans détour (« bientôt », §6.11) et ne promet aucune date ; l'exemple du Figma (« Amandine n'est toujours pas rentré à la maison ») est retiré, et tout exemple est marqué comme tel.
- **Où et quand** : après l'amorce de localisation (§3.1) ; deux boutons de poids égal, « Autoriser » et « Plus tard ». **« Plus tard » ne consomme pas la fenêtre du système** : elle n'est pas demandée, la personne pourra encore l'accorder plus tard.
- **Aucune relance par fenêtre** (Q2) : pas de « toutes les 20 visites ». L'état est visible dans Réglages > Confidentialité (« Notifications : autorisées / désactivées / pas encore demandées ») avec « Ouvrir les réglages du téléphone ».
- **Refus dans la fenêtre du système** : définitif par fenêtre (iOS : une seule fenêtre ; Android 13 et plus : fenêtre qui n'apparaît plus après deux refus, **à vérifier**). Le jour où l'envoi existera, la personne devra passer par les réglages du téléphone : risque connu et assumé (R2).
- **Android** : à partir d'Android 13, permission `POST_NOTIFICATIONS` demandée au runtime. **Avant Android 13, il n'y a pas de fenêtre du système** (notifications autorisées par défaut) : l'amorce ne s'affiche pas (inutile, et elle induirait en erreur).
- **iOS** : fenêtre du système unique ; l'autorisation provisoire (notifications silencieuses sans fenêtre) est une alternative à étudier par le futur ticket Notifications.
- **Risque de relecture des stores** (R15) : une permission demandée **sans fonctionnalité livrée** peut être questionnée par Apple ou Google (collecte de permission non nécessaire, **à vérifier**). Atténuations : texte honnête, fiche de store qui ne promet pas de notifications, et **un interrupteur de configuration à la compilation** qui permet de ne pas appeler l'amorce dans le build soumis aux stores si la relecture l'exige, sans retoucher le code ; décision de l'activer revenant au PM.
- **Réglages** : la ligne « Notifications » (interrupteurs classiques / intelligentes) reste masquée tant qu'il n'y a rien à régler (§6.9). Le composant d'amorce est testé et réutilisé par le futur ticket Notifications.

### 6.7 Barre d'onglets (Tabbar)

- **Trois destinations** (Figma, `design-system.md` §4) : **Feed** (maison avec un cœur, à gauche), **bouton rond central** (rôle non documenté : déduit « donner des nouvelles / Update », ticket Update ; Q13), **Profil** (silhouette, à droite). Onglet actif = pastille bleue derrière l'icône (Figma). Pas de libellés visibles : **étiquettes d'accessibilité** obligatoires (`tabbar_feed_a11y`, `tabbar_update_a11y`, `tabbar_profile_a11y`).
- **Visible** seulement quand l'onboarding est terminé (profil présent et Miff présente). Masquée pendant le splash, le carrousel, l'authentification, le profil, le choix de Miff, les amorces de permissions, ainsi que dans les écrans plein écran (recadrage de photo, sélecteur) et quand le clavier est ouvert.
- **Comportement** : l'état de chaque onglet est conservé quand on change d'onglet ; toucher l'onglet actif remonte en haut de la liste ; le bouton « retour » du système, sur un onglet racine, quitte l'app (Android) ; l'onglet par défaut est le Feed.
- **Livrable** : la **coquille** (navigation, états, icônes recolorables), avec des écrans provisoires pour Feed et Update tant que leurs tickets n'existent pas. Ces écrans provisoires **ne partent jamais chez des testeurs externes**.
- **Droite-à-gauche** : l'ordre des onglets s'inverse (début/fin, jamais gauche/droite) ; les icônes sans direction (cœur, silhouette) ne sont pas retournées.
- **Design** : icônes et pastille à reconstruire par `design-system` en composant avec variantes (actif/inactif), aujourd'hui image aplatie non recolorable.

### 6.8 Onglet Profil (coquille)

Contenu minimal livré par ce ticket :
- En-tête : photo (ou substitut), « Prénom Nom », mention « C'est toi » sous le nom (maquette « C'est vous », au tutoiement).
- Appui sur la photo : « Mes informations » (Notion).
- Icône d'engrenage en haut à droite : **Réglages** (Figma, Notion).
- **Emplacements** réservés : « Mes Miffs » (carrousel, bouton « Créer une nouvelle Miff » en dernier : tickets Miff, le paywall 2e Miff relève de Premium) et « Lieux de la Miff » avec « Ajouter » (visible **seulement** pour le créateur de la Miff ; détail d'un lieu modifiable **seulement** par son créateur : règles du ticket Lieux). Ces emplacements sont vides ou masqués tant que ces tickets n'existent pas.
- Vocabulaire : « Miff », pas « famille » (§4 #22).

### 6.9 Profil et Réglages minimaux

Écran « Réglages » (accessible par l'engrenage du Profil). Liste, dans l'ordre :

| Entrée | Contenu | Remarques |
|---|---|---|
| **Mes informations** | Photo, prénom, nom, **pays et téléphone** (mêmes règles que §6.3 et §6.4) ; compteur « 7/50 » ; bouton « Enregistrer » actif dès qu'une valeur a changé | Écriture : `users/{uid}` **et** la copie dans chaque Miff (`members/{uid}`), `data-model.md` §8. En cas d'échec partiel : message calme, nouvelle tentative automatique ; **cohérence à tester** (copies divergentes, R5). Le numéro ne peut pas être vidé. |
| **Mon compte** | Adresse e-mail **affichée en lecture seule** (adresse relais Apple possible) ; « Se déconnecter » ; « Supprimer mon compte » | Déconnexion : confirmation en deux boutons avec la phrase « Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e) » (honnêteté, `data-model.md` Q20), puis procédure de l'Authentification §7.6. Suppression : ouvre le flux d'AUTH-5 (liste des Miffs, ré-authentification) ; **le texte de Notion est abandonné**. Changement de mot de passe : **différé** (Q12) ; compte Apple ou Google : aucune ligne mot de passe. |
| **Langue** | Liste de **deux** langues (français, anglais) avec la langue en cours cochée, **plus** « Langue du téléphone » | Voir §6.10 |
| **Confidentialité** (nouveau) | (1) Interrupteur « Partager des statistiques d'usage et des rapports de plantage anonymes » (état lu et écrit via `analyticsConsent`, AUTH-B, effet **immédiat**) ; (2) état de la **localisation** (premier plan, toujours, approximative, refusée) avec « Ouvrir les réglages du téléphone » ; (3) état des **notifications** (autorisées, désactivées, pas encore demandées) avec « Ouvrir les réglages du téléphone » ; (4) liens **Conditions de service** et **Politique de protection des données** (pages web d'AUTH-W, ouvertes dans la langue de l'app) ; (5) « Nous contacter » (e-mail de contact provisoire de l'Authentification §13 étape 3) | Les liens vers les CGU et la politique doivent être **dans l'app** (Apple 5.1.1(i), Google : `docs/legal/README.md` §2 et §5). Emplacement réservé pour « Partage par Miff » (activer/arrêter le partage, `data-model.md` §4 : ticket Miff). |
| **Notifications** | **Masquée** tant que rien n'est envoyé (Q4, Q12) : la permission est demandée à l'onboarding et son état est dans « Confidentialité » | La ligne et ses interrupteurs (« classiques », « intelligentes ») réapparaissent avec le ticket Notifications. |
| **Abonnement** / bandeau Premium | **Masqués** en bêta | Ticket Premium (emplacement réservé). Le seul paywall de cette version est l'écran provisoire de l'onboarding (§6.12) ; **pas** de bandeau d'essai gratuit tant que les achats ne sont pas validables, `data-model.md` §6. |

Aucune action ne déclenche un paywall depuis les Réglages dans ce ticket (Premium §« depuis l'entrée dans les paramètres » **non retenu**, Q5).

### 6.10 Langue

- **Langues disponibles** : français et anglais (décision INIT §9) ; structure prête pour d'autres. Anglais = langue de repli (déjà dans les ressources par défaut d'Android et `CFBundleDevelopmentRegion` d'iOS).
- **Au premier lancement** : la langue de l'appareil est utilisée si elle est gérée, sinon l'anglais : comportement **fourni par le système** sur les deux plateformes, sans code ni écran (conforme au Notion « on détecte la langue du device »).
- **Choix manuel** :
  - **Android** : choix dans l'app (liste de deux langues + « Langue du téléphone »), appliqué immédiatement, sans redémarrage visible ; `LocaleConfig` déjà générée (INIT-2) ; sur Android 13 et plus, l'application apparaît aussi dans « Langues des applications » du système.
  - **iOS** : pas de mécanisme officiel pour changer la langue **dans** l'app sans redémarrage ; **décision Q11** : la ligne « Langue » ouvre les réglages de l'iPhone pour Miff (« Langue préférée » par application, iOS 13 et plus) et affiche la langue en cours. Évite un contournement fragile ; coûte un aller-retour (§7). Option alternative : choix dans l'app avec redémarrage de l'interface (à étudier par `ios-dev`, risque de comportement incohérent).
- **Effet** : tous les textes de l'app, l'**e-mail de réinitialisation** (Authentification) et les **liens légaux** suivent la langue choisie. Pas de langue stockée côté serveur dans cette version ; elle sera nécessaire pour les notifications (INIT §6 point 6).
- **Clé et ton** : relecture par un natif pour chaque langue ajoutée (INIT §4.F).

### 6.11 Textes (propositions, tutoiement, à valider par le PM)

Clés avec préfixes `onboarding_`, `profile_`, `settings_`, `permission_`, `tabbar_` (à ajouter au tableau des préfixes de `docs/contracts/i18n-keys.md` et à `KNOWN_PREFIXES` du script de parité, par la PR du ticket). Une phrase complète par clé.

| Écran | Texte proposé |
|---|---|
| Carrousel, bouton | « Commencer » |
| Carrousel, lien | « J'ai déjà un compte » |
| Carrousel, slide 1 | « Savoir qu'ils vont bien, sans les déranger » (titre du PM) |
| Carrousel, slide 2 | « Tes lieux de confiance, repérés automatiquement » (proposition) |
| Carrousel, slide 3 | « Toi seul(e) décides ce que tu partages » (retenue, Q8). Si le PM veut garder l'idée des notifications, version honnête : « Bientôt : un petit signe au bon moment », avec en sous-texte « Les notifications arrivent dans une prochaine version. » |
| Profil, titre | « Pour que tes proches te reconnaissent » |
| Profil, photo | « Ajouter une photo (facultatif) » |
| Profil, sous la photo | « Visible par les membres de tes Miffs » |
| Profil, champs | « Prénom », « Nom » |
| Profil, trop long | « 50 caractères maximum. » |
| Téléphone, titre | « Pour que tes proches puissent t'appeler ou t'écrire » |
| Téléphone, champs et aide | « Pays », « Numéro de téléphone » ; « Saisis ton numéro sans le premier zéro. » |
| Téléphone, mention de visibilité (obligatoire, sous le champ) | « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » (décision N1, option A ; même texte dans Réglages > Mes informations) |
| Téléphone, erreur | « Ce numéro ne semble pas correct. » |
| Choix, titre | « Comment veux-tu commencer ? » |
| Choix, cartes | « Créer une nouvelle Miff » / « Tu pourras ensuite inviter tes proches » ; « Rejoindre une Miff existante » / « Saisis le code que ton proche t'a donné » |
| Amorce localisation, titre | « Te signaler que tu vas bien, sans y penser » |
| Amorce localisation, corps (version conditionnelle, voir §4 hors périmètre) | « Pour prévenir ta Miff « {nom} » quand tu arrives ou quittes un de tes lieux de confiance, Miff utilise la position de ton téléphone, y compris quand l'app est fermée. Tes proches voient seulement un état (par exemple « à la maison ») et l'heure, pas ta position précise. Tu peux arrêter à tout moment dans Réglages. Sans cela, tu peux toujours donner des nouvelles toi-même. » |
| Amorce localisation, boutons | « Autoriser » / « Plus tard » |
| Android 11 et plus, second écran | « Dernière étape : choisis « Autoriser tout le temps » dans les réglages, pour que la détection fonctionne quand l'app est fermée. » + « Ouvrir les réglages » / « Plus tard » |
| Rappel non modal | « La localisation en arrière-plan est désactivée : tes proches ne voient que tes mises à jour manuelles. » + « Activer » |
| Texte d'usage iOS (localisation) | « Miff utilise ta position, y compris en arrière-plan, pour prévenir tes proches quand tu arrives ou quittes tes lieux de confiance. Elle n'est jamais enregistrée comme un trajet. » (à conditionner comme ci-dessus) |
| Texte d'usage iOS (appareil photo) | « Miff utilise l'appareil photo pour prendre ta photo de profil. » |
| Amorce notifications, titre | « Les notifications arrivent bientôt » |
| Amorce notifications, corps | « Dès qu'elles seront disponibles, Miff pourra te prévenir quand c'est utile, par exemple quand un proche arrive quelque part. Rien n'est envoyé pour l'instant : si tu les autorises maintenant, tu les recevras dès leur arrivée. Tu peux changer d'avis à tout moment dans les réglages du téléphone. » (aucun exemple de notification présenté comme réel) |
| Amorce notifications, boutons | « Autoriser » / « Plus tard » |
| Déconnexion, confirmation | « Te déconnecter ? Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). » + « Se déconnecter » / « Annuler » |
| Hors connexion | « Pas de connexion pour le moment. Réessaie dès que tu as du réseau. » (texte de l'Authentification) |
| Perte d'une Miff | « Tu ne fais plus partie de la Miff « {nom} ». » / « La Miff « {nom} » n'existe plus. » (textes de DUR) |
| Paywall provisoire, titre | « Miff+ arrive bientôt » |
| Paywall provisoire, corps | « Miff est gratuit pour le moment. Une formule Miff+ (plus de lieux, plus de Miffs) arrivera plus tard. Rien à payer aujourd'hui, rien à faire. » |
| Paywall provisoire, bouton | « Continuer » |

### 6.12 Écran Paywall provisoire (décision Q5)

**Décision du PM** : un écran **placeholder** « Paywall » en fin d'onboarding, **facile à passer**, **non fonctionnel** ; le vrai paywall sera traité plus tard dans le ticket Premium.

- **Position** : dernier écran de l'onboarding (après l'amorce de notifications, avant le Feed), affiché **une seule fois** par compte (un drapeau local est posé à l'affichage).
- **Contenu** : le titre et le corps de §6.11 ; les quatre bénéfices du Figma (lieux de confiance illimités, Miffs illimitées, zones de danger, notifications intelligentes) présentés comme **à venir**. **Aucun prix, aucun essai gratuit, aucune mention d'abonnement ni d'« annulable à tout moment », aucun bouton d'achat** : le Figma 15 montre un paywall fonctionnel (« Commencer l'essai gratuit (7 j), puis 4,99 €/mois ») qui **ne doit pas être repris**, car il promettrait un service qui n'existe pas.
- **Facile à passer** : bouton « Continuer » visible sans défilement, croix de fermeture, bouton « retour » du système qui ferme l'écran ; aucun délai, aucun compte à rebours, aucun bouton de fermeture camouflé ; l'écran ne dépend pas du réseau.
- **Non fonctionnel** : aucun appel à l'App Store ou à Google Play Billing, aucune écriture de `entitlements`.
- **Aucune relance automatique** (décision Q5) : ni à la première arrivée sur le Feed, ni à une connexion, ni « toutes les 10 visites », ni depuis les Réglages. Le déclenchement du vrai paywall (limite atteinte, entrée d'abonnement) relève du ticket Premium.
- **Honnêteté** : « bientôt disponible », sans engagement ni date ; pas de pression.
- **Analyse** : aucun événement (`paywall_shown` et `paywall_dismissed` du contrat sont réservés au vrai paywall).
- **Risque de relecture des stores** : un écran qui annonce une fonction « à venir » peut être questionné (R17). Même interrupteur de configuration à la compilation que pour l'amorce de notifications : l'écran peut être retiré du build soumis aux stores sans toucher au code.

---

## 7. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Écran de lancement | Système (Android 12 et plus : icône sur fond de couleur, **à vérifier**) puis animation dans l'app ; avant Android 12, fond bleu statique | Écran de lancement statique (fond bleu) puis animation dans l'app |
| Icône | Couches adaptatives, couche monochrome Android 13 et plus, icône 512 px de la fiche Play | 1024 × 1024 px opaque ; style à couches iOS 26 demande un Mac (INIT §4.C) : icône classique |
| Photo de profil | Sélecteur de photos du système ; appareil photo via l'application du système | Sélecteur du système ; appareil photo avec texte d'usage, permission à l'appui |
| Localisation en arrière-plan | Selon la version : 8 à 9 (une fenêtre), 10 (fenêtre avec « Toujours »), 11 et plus (réglages du système obligatoires, retour à relire) ; position approximative possible (12 et plus) | Deux demandes successives ; « Autoriser une fois » ; position approximative possible ; relancer l'évolution vers « Toujours » est contrôlé par le système |
| Nouvelle demande après refus | Fenêtre non affichée après un refus définitif (**à vérifier**) : renvoi aux réglages | Une seule fenêtre : renvoi aux réglages |
| Notifications | `POST_NOTIFICATIONS` demandée au runtime à partir d'Android 13 ; **aucune fenêtre avant Android 13** (l'amorce ne s'affiche pas) | Fenêtre unique ; autorisation provisoire possible (à étudier) |
| Numéro de téléphone | Clavier numérique, saisie automatique du système, sélecteur de pays ; boutons du Feed via l'application Téléphone et l'application de messages du système | Idem ; ouverture de l'application Téléphone et de Messages via les liens système ; aucune permission « contacts » |
| Retour | Geste ou bouton « retour » du système : quitte l'app depuis un onglet racine ; en première étape de profil, met l'app en arrière-plan | Geste de bord d'écran : mêmes règles que la flèche |
| Langue dans l'app | Choix dans l'app + « Langues des applications » (Android 13+) | Ligne « Langue » qui ouvre les réglages de l'iPhone (décision Q11) |
| Barre d'onglets | Barre de navigation inférieure ; bouton central accentué | Barre inférieure personnalisée ; zone sûre du bas |
| Apparence | Thème clair imposé | Apparence claire imposée |
| Test | En local sur l'OPPO Find X5 Lite (Android 13) : ColorOS peut tuer l'app en arrière-plan (INIT §9), à constater dès le géofencing | Uniquement par TestFlight sur l'iPhone 11 Pro (pas de Mac) : chaque essai passe par la CI ; permissions et splash **seulement vérifiables sur appareil** |
| Saisie du nom | Majuscule automatique sur les noms, pas de correction automatique | Idem |

---

## 8. Lien avec le modèle de données (`backend/data-model.md`)

### Ce qui existe déjà et suffit
| Besoin | Où |
|---|---|
| Profil : `firstName`, `lastName` (1 à 50), `avatar` (facultatif, 20 000 octets au plus), `createdAt`, `termsAcceptedAt`, `termsVersion` | `users/{uid}` (§2.1), création par le propriétaire uniquement |
| « Onboarding terminé » = Miff créée ou rejointe | `users/{uid}/memberships` non vide (§2.2) ; aucun champ de progression |
| Copie du profil dans chaque Miff | `members/{uid}` (§2.6) ; mise à jour par le client dans chaque Miff (§8, ligne « Inscription / profil ») |
| Consentement au partage par Miff, état de la permission du téléphone | `members.sharing.*` et `status.locationAccess` (§2.6, §2.7, §4) |
| Purge du cache à la déconnexion | Contrat du §8 (ligne « Purge du cache local ») |

### Ce qui manque ou est à décider (non modifié ici, à reporter par `backend-data`)
| Manque | Détail | Bloquant ? |
|---|---|---|
| Consentement statistiques / plantages | **AUTH-B fusionné dans `main` (PR #16)** : bloc `analyticsConsent` sur `users/{uid}`, **toujours écrit** par le client à la création du profil ; l'interrupteur des Réglages le lit et le met à jour | Non (livré) ; **à coordonner avec ONB-B** : les deux changent les clés acceptées de `users` |
| **Téléphone (décision Q1)** : clé `phone` sur `users/{uid}` et copie sur `members/{uid}` | Absent du modèle (§2.1, §2.6) ; **les règles actuelles refusent toute clé téléphone** et `data-model.md` §8 demande de ne jamais en écrire. À ajouter : chaîne E.164, obligatoire à la création du profil et de la fiche de membre, modifiable, non vidable ; forme exacte, tests, revue de sécurité : **ONB-B** (ci-dessous) | **Oui : ONB-B se termine avant les écrans de profil (ONB-2, ONB-6)** |
| Forme de `avatar` absent | Le modèle demande d'écrire toutes les clés avec `null` quand une valeur manque (§2) alors que les règles acceptent l'**absence** de la clé `avatar` : à confirmer pour que les deux apps écrivent pareil | Non (à trancher avant la PR) |
| Longueur minimale du prénom et du nom | Règles : 1 à 50 ; question Q15 ouverte du modèle. Si le PM retient 2 ou 3, c'est une règle d'**interface** seulement (les règles restent permissives) | Non |
| Longueur du nom de Miff | 40 dans les règles, 50 dans Notion (§4 #8) | Non pour ce ticket ; tickets Miff |
| Langue préférée | Nécessaire plus tard pour les notifications ; pas dans cette version | Non |
| Drapeaux **locaux** (carrousel vu, rappel de permission fermé) | Stockés **sur l'appareil seulement**, non sensibles, jamais envoyés | Non |

Aucune nouvelle collection n'est requise.

### ONB-B : contenu du sous-ticket backend (`backend-data`, à faire avant les écrans de profil)

1. **`users/{uid}`** : nouvelle clé `phone` (chaîne E.164 : `+` suivi de 7 à 15 chiffres, premier chiffre non nul ; forme exacte à décider par `backend-data`). **Obligatoire à la création**, modifiable, **jamais supprimée ni vidée**. Les clés acceptées à la création passent de sept à huit.
2. **`members/{uid}`** : copie `phone` avec les mêmes contraintes de forme, **obligatoire à la création de la fiche**, modifiable avec le profil. Les fiches antérieures sans numéro restent valides (une mise à jour qui l'ajoute est acceptée). **Pas de vérification croisée** `users`/`members` (elle coûterait une lecture de règle de plus) : au pire, une personne inscrit un autre numéro que le sien dans la copie, ce qui ne touche que l'affichage de sa propre fiche.
3. **Visibilité (décision N1, option A)** : celle de la fiche de membre, c'est-à-dire tous les membres de la Miff tant qu'elle existe (`data-model.md` §9), et eux seuls. Aucune règle de lecture supplémentaire (ni créateur seul, ni réciprocité, ni choix par Miff : options écartées). Les règles `users` restent en lecture propriétaire seulement : le numéro d'un utilisateur n'est lisible des autres que par la copie `members/{uid}.phone`.
4. **Tests** : cas acceptés et refusés (format invalide, trop long ou trop court, clé absente à la création, tentative de suppression ou de vidage, écriture d'un numéro sur le document d'un autre, lecture par un membre et par un non-membre) ; les tests existants qui affirmaient le **refus** du téléphone sont **inversés** ; test par mutation sur les nouvelles protections (plan de test à mettre à jour).
5. **`backend/data-model.md`** : §2.1, §2.6, §8 (lignes « Inscription / profil », « Créer une Miff », « Rejoindre une Miff », modification de profil, qui disent aujourd'hui « ne jamais écrire de téléphone »), §11 (tableau RGPD : nouvelle donnée, sensibilité moyenne à élevée, base légale proposée, conservation) et §15.
6. **Revue de sécurité** avant tout déploiement : usurpation, fuite des numéros à quiconque rejoint avec un code (R7), absence de journalisation.
7. **Déploiement** des règles sur `miff-3d283` : uniquement après tests verts, revue de sécurité et « oui » explicite du PM, selon `docs/dev/deploiement-regles.md` (`--project miff-3d283` explicite, jamais depuis la CI, retour arrière documenté). Les règles sont déployées **avant** la livraison d'une app qui écrit le numéro.
8. **Textes légaux et déclarations** (hors ONB-B mais **à faire avant toute publication**) : la politique de confidentialité et les CGU de `docs/legal` et du site (AUTH-W) ne mentionnent pas le numéro de téléphone ; les déclarations des stores (étiquettes de confidentialité d'Apple, section Sécurité des données de Google) doivent l'inclure.

---

## 9. Cas limites

1. **Fermeture de l'app** pendant le profil, le choix de Miff ou l'amorce : reprise à l'étape correcte, sans nouvelle inscription (§6.2).
2. **Hors connexion** au profil : message neutre, champs conservés ; pas de passage à l'étape suivante sans confirmation.
3. **Compte sans profil, ancien** (abandon avant le profil) : même chemin que la création.
4. **Nom très long, collé avec espaces, emojis** : coupé à 50 caractères avec message ; espaces nettoyés ; emojis acceptés.
5. **Photo trop lourde ou illisible**, fichier qui n'est pas une image, photo prise en mode paysage, rotation d'écran pendant le recadrage : le recadrage garde l'état ; message calme si l'image est inutilisable.
6. **Refus de l'appareil photo** (iOS) : retour à la galerie ; **refus définitif** : message d'aide vers les réglages, sans blocage.
7. **Retrait de la photo** : retour au substitut ; le document est mis à jour (clé `avatar` absente ou nulle, à confirmer).
8. **Modification du profil depuis les Réglages** avec échec partiel (profil mis à jour mais une copie `members` non) : nouvelle tentative automatique, message discret ; état cohérent à terme (§13 R5).
9. **Reconnexion sur un nouvel appareil** avec Miff existante : ni profil, ni choix ; permission de localisation à reconsentir **sur l'appareil** (le système la réinitialise) : rappel non modal, jamais de pop-in.
10. **Réinstallation sur iOS** avec session conservée (Authentification §9) : routage normal ; carrousel non revu.
11. **Perte de la dernière Miff** : message neutre, puis écran de choix (§6.2).
12. **Permission retirée dans les réglages du téléphone** pendant que l'app est ouverte : l'état est relu au retour au premier plan ; rappel non modal.
13. **Retour des réglages Android** sans avoir changé la permission : aucune erreur, aucun reproche ; l'amorce reste disponible.
14. **Position approximative seulement** : état dédié, jamais présenté comme une erreur.
15. **Mode économie d'énergie / optimisations du fabricant** : peuvent retarder la détection ; hors périmètre du ticket (Tracking) ; mention dans la documentation utilisateur à prévoir.
16. **Réduction des animations**, **police agrandie**, **lecteur d'écran**, **droite-à-gauche** : voir critères X.
17. **Changement de langue** en cours de route : l'étape en cours se réaffiche dans la nouvelle langue, sans perdre la saisie.
18. **Déconnexion avec écritures en attente** : contrat de purge (`waitForPendingWrites` puis purge) ; aucune donnée de Miff visible hors connexion après coup.
19. **Deux personnes sur le même téléphone** : le profil et le routage sont ceux du compte connecté ; rien de la session précédente (Authentification X12).
20. **Compte Apple avec adresse relais** : adresse affichée telle quelle dans « Mon compte » ; aucune tentative de la « corriger ».
21. **Carrousel** : app rouverte en cours de carrousel : retour à la slide 1 sans conséquence ; drapeau « vu » posé seulement à « Commencer ».
22. **Splash** sur appareil lent ou en démarrage à froid pendant un appel entrant : l'animation se termine, l'app s'ouvre ; jamais d'écran blanc.
23. **Plusieurs Miffs (Premium)** : l'amorce de localisation ne s'affiche qu'une fois ; le consentement par Miff reste par Miff (tickets Miff).
24. **Création d'une Miff pendant que la localisation est « jamais demandée »** : le consentement de la Miff peut être donné avec une permission du système absente (état « localisation limitée »), cohérent avec le modèle.
25. **Suppression de compte depuis les Réglages interrompue** : reprise par l'Authentification ; ce ticket n'a pas de logique propre.
26. **Appareil sans région** (ou région non reconnue) pour le pays par défaut du téléphone : France, modifiable.
27. **Numéro déjà utilisé par un autre compte** : accepté (aucune unicité, non empêchable sans serveur) ; aucune information sur l'autre compte n'est donnée.
28. **Compte avec profil mais sans numéro** (compte antérieur à ONB-B, jeu de test) : étape téléphone seule au lancement suivant (ordre 2b), puis suite normale ; tant qu'elle n'est pas faite, rien d'autre n'est accessible.
29. **Fiche de membre sans numéro** (antérieure à ONB-B) : les boutons « Appeler » et « Message » du Feed ne s'affichent pas pour ce membre, sans message d'erreur.
30. **Personne qui ne veut pas donner son numéro** : ne peut pas terminer l'onboarding ni utiliser l'app (conséquence assumée de la décision Q1, R7) ; message calme, aucun reproche.
31. **Android 8 à 12** : pas de fenêtre de notifications du système, donc pas d'amorce de notifications ; l'étape est sautée sans effet visible.
32. **Écran Paywall provisoire déjà vu** puis déconnexion et reconnexion sur le même appareil : il ne se réaffiche pas (drapeau local) ; sur un nouvel appareil avec Miff existante, l'onboarding n'est pas rejoué, donc l'écran n'apparaît pas.
33. **Brouillon local** laissé par une autre personne sur le même appareil (changement de compte) : il est lié à l'identifiant du compte et supprimé à la déconnexion ; il n'est jamais réutilisé pour un autre compte.

---

## 10. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.

### Avant ONB-B et les écrans de profil
1. **Question N1 : tranchée le 2026-10-24** (option A : un utilisateur voit uniquement les numéros des membres des Miffs auxquelles il appartient). Rien à décider de plus ; à toi de donner l'accord de déploiement (point 2).
2. **Donner l'accord explicite** de déploiement des règles Firestore quand ONB-B sera prêt (tests verts, revue de sécurité faite).

### Avant les sous-tickets Android
3. **Valider le rendu du logo** extrait du Figma par l'agent `design-system` (décision Q10, lancée séparément) ; sinon, l'exporter toi-même en SVG (§5.2 voie A).
4. **Textes** : titres des 3 slides du carrousel, version de la slide 3, amorces de permissions, texte de l'amorce de notifications, écran Paywall provisoire, confirmation de déconnexion (propositions de §6.11).
5. **Maquettes** : validation du dessin des écrans listés en fin de §3.2 une fois produits par `design-system` (dont le téléphone avec sa mention, l'écran Paywall provisoire) ; **vérification des droits des photos** de banque d'images utilisées dans les maquettes (§4 #19).
6. **Un compte de test Google/e-mail** déjà prévu par l'Authentification, et un numéro de téléphone de test réel pour la recette (le tien ou un numéro dédié ; jamais celui d'un tiers sans son accord).

### Avant les stores (pas pour la bêta interne)
7. **Icône et logo définitifs** (formats du §5.2).
8. **Décider** si l'amorce de notifications et l'écran Paywall provisoire restent dans le build soumis aux stores (interrupteur de compilation, §6.6 et §6.12).
9. **Vidéo de démonstration** et formulaire « localisation en arrière-plan » de Google Play, textes d'usage d'Apple, **déclaration du numéro de téléphone** dans les étiquettes de confidentialité et la section Sécurité des données : préparés par `release-manager` à partir des textes validés ici (le PM les soumet).

---

## 11. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Animation du splash | Dessin vectoriel dans l'app (Compose, SwiftUI), sans bibliothèque | Effort de développement (dessin, réglage) | Si le rendu exige plus de finesse : bibliothèque d'animation à licence libre ; aucun achat |
| Logo et icône | Export Figma par le PM ou extraction | Qualité des vecteurs du Figma (aplatis) | Redessin par un designer : payant, **jamais sans décision PM** |
| Photo de profil | Stockée dans Firestore (≤ 20 000 octets), sélecteur du système | 1 Gio de stockage Spark ; ≈ 20 Ko par utilisateur (`data-model.md` §12) | Au-delà de 40 000 utilisateurs environ (estimation), signal stockage ; voir surveillance des quotas |
| Lectures du routage | 2 lectures par ouverture (profil, memberships) | 50 000 lectures par jour sur Spark ; cumulées avec le Feed | Ratio lectures par utilisateur actif, `docs/dev/surveillance-quotas.md` |
| Validation et normalisation du numéro de téléphone | Bibliothèque libre (par exemple libphonenumber, Apache 2.0), aucun SMS | Poids d'application ; validation de **forme** seulement, aucune preuve de possession | Vérification par SMS : exigerait Blaze et un service d'envoi payant, **jamais sans décision PM** |
| Notifications | Permission demandée dès la bêta (Q4), aucun envoi | Sans Blaze, rien n'est délivré | Blaze : décision PM avant la première bêta externe, jamais sans accord |
| Langues | Ressources du système | Deux langues | Chaque langue ajoutée : relecture du ton, textes légaux, captures (INIT §4.F) |

Rien dans ce ticket n'exige un moyen de paiement. Aucun choix « économique » ne réduit la sécurité : numéro de téléphone limité aux membres des Miffs de la personne (risque assumé R7), consentement explicite et révocable, pas de métadonnées de photo.

---

## 12. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket (`CLAUDE.md`). Tailles relatives, sans durée.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| ONB-0 | **Prérequis : décisions, logo, textes, design** (PM et `design-system`) | N1 tranchée (option A, mention de visibilité à dessiner), **extraction du logo par `design-system`** (décision Q10), textes, dessin des écrans manquants (recadrage, téléphone avec mention de visibilité, états de permission, Paywall provisoire, Confidentialité, Profil, Réglages, Tabbar à variantes, tutoiement) | S à M | Validation de ce ticket |
| **ONB-B** | **Backend : schéma, règles, tests et revue de sécurité du téléphone** (`backend-data`) | §8 (« ONB-B : contenu du sous-ticket ») ; `users/{uid}.phone` obligatoire, copie `members/{uid}.phone`, tests inversés et mutation, `data-model.md`, revue de sécurité ; **déploiement des règles avec accord explicite du PM** (`--project miff-3d283`, jamais depuis la CI) | S à M | ONB-0 (réponse à N1), AUTH-B (fusionné) ; **à terminer avant ONB-2 et ONB-6** |
| ONB-1 | **Android : splash, icône, carrousel** | §5, §6.1 ; icône provisoire conservée si le logo manque (« ne pas publier ») | M | INIT-2, ONB-0 (textes, logo pour la version définitive) |
| ONB-2 | **Android : profil, téléphone, routage, choix de Miff** | §6.2, §6.3, §6.4, §6.5 ; écriture unique de `users/{uid}` à la fin de l'étape téléphone ; brouillon local ; ordre 2b (compte sans numéro) ; tests du routage, du format E.164 et de l'écriture | M à L | AUTH-1, AUTH-B (fusionné), **ONB-B**, ONB-1 |
| ONB-3 | **Android : barre d'onglets, onglet Profil, Réglages minimaux** | §6.7 à §6.10 ; interrupteur statistiques ; langue ; liens légaux | M | ONB-2, AUTH-1 (déconnexion), AUTH-B, AUTH-W (liens), AUTH-5 (suppression : entrée seulement) |
| ONB-4 | **Android : permissions et écran Paywall provisoire** | §6.6 (amorce de localisation fusionnée avec le consentement de la Miff, états par version d'Android, rappel non modal, **amorce de notifications demandée dès la bêta**, état dans Confidentialité), §6.12 (écran provisoire), interrupteur de compilation | M | ONB-3, **tickets Miff (consentement par Miff)** |
| ONB-5 | **iOS : splash, icône, carrousel** | Mêmes livrables qu'ONB-1 ; écran de lancement statique | M | INIT-4, ONB-1 |
| ONB-6 | **iOS : profil, téléphone, routage, choix de Miff** | Mêmes livrables qu'ONB-2 ; textes d'usage de l'appareil photo | M à L | AUTH-3, AUTH-B, ONB-B, ONB-5 |
| ONB-7 | **iOS : barre d'onglets, onglet Profil, Réglages minimaux** | Mêmes livrables qu'ONB-3 ; ligne Langue vers les réglages de l'iPhone | M | ONB-6, ONB-3 |
| ONB-8 | **iOS : permissions et écran Paywall provisoire** | Mêmes livrables qu'ONB-4 ; textes d'usage de la localisation | M | ONB-7, tickets Miff |

**Ordre recommandé** : ONB-0 (N1 est tranchée), puis **ONB-B** (règles et tests, sans déploiement avant ton accord), puis ONB-1, ONB-2, ONB-3 ; les tickets **Miff (créer, rejoindre, consentement)** s'intercalent avant ONB-4 (l'onboarding n'est complet qu'avec eux : **ne pas distribuer à des testeurs externes avant**) ; puis ONB-4 ; puis iOS (ONB-5 à ONB-8). Chaque PR : revue de code en contexte neuf, revue de sécurité (consentement, permissions, données du profil, numéro de téléphone, métadonnées de photo, journaux), QA, recette du PM, fusion par Claude après « oui » explicite du PM pour chaque PR (`CLAUDE.md`).

---

## 13. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Rejet d'un store** : blocage par la permission « Toujours », amorce incomplète, pop-in à répétition, texte qui n'annonce pas l'arrière-plan | Retard de publication | Jamais bloquant, amorce complète avant la demande, rappel non modal ; `release-manager` relit les règles à jour (Apple 5.1.1, 5.1.2, 5.1.5 ; Google « divulgation visible ») avant toute soumission ; à reconfirmer sur les textes en vigueur |
| R2 | **Fenêtre du système « grillée »** par une demande trop tôt ou trop répétée (notifications, localisation) | Fonction définitivement coupée, renvoi aux réglages du système | Amorce d'abord, une seule demande par contexte, « Plus tard » qui ne consomme pas la fenêtre. **Pour les notifications, risque assumé par le PM (décision Q4)** : une personne qui refuse dans la fenêtre du système devra passer par les réglages du téléphone le jour où l'envoi existera |
| R3 | **Logo absent** | Icône provisoire, splash provisoire, impossibilité de publier | Voie A ou B de §5.2, avant les stores |
| R4 | **Promesse inexacte** (carrousel et amorces) : notifications, « jamais ta position précise » alors que le Feed ou Tracking pourraient la stocker | Perte de confiance, motif de rejet, risque juridique assumé par le PM (décision du 2026-10-23) | Textes conditionnels (§6.6, §6.11) ; alignement de Feed et Tracking sur le modèle |
| R5 | **Profil et copies `members` divergents** après une modification partielle | Un membre voit un ancien nom ou une ancienne photo | Nouvelle tentative automatique ; test de cohérence ; (amélioration possible plus tard par un champ de version) |
| R6 | **Photo de profil** : donnée sensible (visage, parfois mineur), métadonnées GPS d'une photo prise sur place | Fuite involontaire de position | Réencodage sans métadonnées, mention de visibilité, sélecteur du système sans permission large |
| R7 | **Numéro de téléphone obligatoire (décision Q1, risque assumé par le PM)** | (1) **Minimisation RGPD** : donnée de contact obligatoire non strictement nécessaire, à déclarer dans la politique et les stores (non encore mentionnée) ; (2) **numéro non vérifié** : mauvais numéro, numéro d'un tiers, usage pour embêter quelqu'un ; (3) **visible des membres** : toute personne qui connaît le code d'une Miff la rejoint sans approbation et lit **tous les numéros** : une fuite de code devient une fuite de numéros ; (4) mineurs sans âge minimum : numéro lisible des co-membres ; (5) friction : abandon possible | Mention de visibilité à l'écran ; copie supprimée avec la fiche de membre ; code renouvelé à chaque retrait (DUR-2) ; approbation par le créateur à l'arrivée (`data-model.md` Q7) à reconsidérer ; jamais dans les journaux ni l'analyse ; ONB-B avec revue de sécurité ; mise à jour des textes légaux et des déclarations des stores avant publication ; pas de relecture juridique (décision du 2026-10-23, risque assumé) |
| R8 | **Charge cognitive** : l'onboarding cumule carrousel, inscription, profil, téléphone, choix de Miff, consentement et amorce de localisation, amorce de notifications, écran Paywall provisoire | Abandon avant la première Miff | Étapes courtes et toutes passables sauf profil et téléphone ; « Plus tard » partout ; aucun paywall fonctionnel ; l'ordre de §3.1 place la localisation avant les notifications |
| R9 | **Session iOS persistante après réinstallation** : l'état du compte est différent de ce que l'utilisateur attend | Confusion (carrousel non revu, déjà connecté) | Routage fondé sur les données ; constat sur iPhone (critère I9 de l'Authentification) |
| R10 | **Changement d'état de permission non détecté** (retrait dans les réglages) | Statut figé, proches inquiétés à tort | Relecture à chaque retour au premier plan ; champ `locationAccess` du statut (Tracking) |
| R11 | **Animation de splash mal calibrée** : trop longue, clignotements, saut avec l'écran du système | Irritation, accessibilité | Durée plafonnée, transition de même fond, réduction des animations, test sur appareil (OPPO, iPhone) |
| R12 | **Photos de banque d'images** dans les visuels du carrousel ou des fiches de store | Litige de droits | Remplacement par des illustrations libres avant tout visuel public |
| R13 | **Aucun test iOS sans TestFlight** pour permissions et splash | Cycle lent, régressions tardives | Android d'abord, captures de CI, tests XCTest de la logique d'états |
| R14 | **Optimisations des fabricants Android** (ColorOS) qui tuent l'app | Détection non fiable malgré toutes les permissions | Hors ticket (Tracking) ; à documenter pour l'utilisateur |
| R15 | **Notifications demandées sans fonctionnalité livrée** (décision Q4, risque assumé) | Relecture Apple ou Google : permission jugée non nécessaire (à vérifier) ; promesse non tenue côté utilisateur | Textes honnêtes (« bientôt », rien n'est envoyé) ; fiche de store sans promesse de notifications ; interrupteur de configuration à la compilation pour retirer l'amorce du build soumis ; permission non demandée avant Android 13 |
| R16 | **Aucune échappatoire pendant l'onboarding** (décision Q14, risque assumé) | Une personne qui a créé un compte par erreur (mauvaise adresse, mauvais compte Google ou Apple) est bloquée tant qu'elle n'a pas terminé le profil, le téléphone et la première Miff, seul chemin vers les Paramètres et la déconnexion. Sur iOS, la session peut survivre à une réinstallation (Authentification §9, à constater) : désinstaller ne suffirait pas toujours. Sur Android, effacer les données de l'app règle le cas | Risque accepté tel quel ; à revoir si la recette ou les premiers testeurs le rencontrent (lien discret de déconnexion à rouvrir) ; le bouton « retour » du système ne déconnecte pas |
| R17 | **Écran Paywall provisoire** (« bientôt disponible ») | Relecture des stores : fonction annoncée mais absente ; attente créée chez l'utilisateur | Texte sans prix ni engagement, une seule fois, facile à passer ; interrupteur de compilation pour le retirer du build soumis ; remplacé par le vrai paywall du ticket Premium |
| R18 | **Déclarations non à jour** : la politique de confidentialité, les CGU et les déclarations des stores ne mentionnent pas encore le numéro de téléphone | Incohérence entre l'app et ses textes (motif de rejet, risque juridique assumé par le PM) | B5 : mise à jour dans les deux langues avant toute publication, par `release-manager` ; le garde-fou de déploiement du site (`web-predeploy-check`) reste en place |

---

## 14. Décisions du PM (2026-10-24)

Réponses du PM aux 14 questions du challenge (détail et motifs : `docs/tickets/onboarding-decisions-pm.md`). « Retenue » = ma recommandation a été suivie ; **les décisions marquées « contre ma recommandation » sont appliquées telles quelles et leurs risques sont écrits comme risques assumés**.

| # | Sujet | Décision du PM | Répercussion dans ce ticket |
|---|---|---|---|
| Q1 | Téléphone | **Obligatoire dans l'onboarding (contre ma recommandation)** | §6.4, §8 (ONB-B), §12, R7, R18, critères PR, TEL, B |
| Q2 | Permissions bloquantes ? | Recommandation retenue : jamais bloquantes, « Plus tard » toujours présent | §6.6, PE |
| Q3 | Amorce de localisation | Recommandation retenue : un écran fusionné avec le consentement de la Miff, après la création ou l'arrivée | §3.1, §6.6 |
| Q4 | Notifications | **Demandées directement, y compris en bêta (contre ma recommandation)** | §6.6, R2, R15, PE9 à PE12 |
| Q5 | Paywall | **Écran Paywall provisoire en fin d'onboarding, facile à passer, non fonctionnel, sans relance (variante de ma recommandation : aucun paywall)** | §6.12, R17, PW |
| Q6 | Prénom et nom | Recommandation retenue : 1 à 50 caractères | §6.3, PR2 |
| Q7 | Photo | Recommandation retenue : facultative | §6.3, PR3 à PR6 |
| Q8 | Carrousel | Recommandation retenue | §6.1, CA |
| Q9 | Splash | Recommandation retenue | §5.1, SP |
| Q10 | Logo | **Le PM autorise `design-system` à extraire le logo depuis le Figma** (lancée séparément) | §5.2, ONB-0 |
| Q11 | Langue | Recommandation retenue | §6.10, RG5, I4 |
| Q12 | Réglages minimaux | Recommandation retenue | §6.9, RG |
| Q13 | Barre d'onglets et état « sans Miff » | Recommandation retenue | §6.2, §6.7 |
| Q14 | Échappatoire | **Pas de lien « Ce n'est pas toi ? » ; la déconnexion se fait depuis les Paramètres (contre ma recommandation)** | §6.3, R16, PR10 |

**N1 tranchée (2026-10-24, option A)** : un utilisateur voit uniquement les numéros de téléphone des membres des Miffs auxquelles il appartient, et son numéro n'est vu que par eux (via la copie dans la fiche de membre), sans condition de partage de position. Options B, C, D écartées (§6.4). La mention de visibilité est obligatoire sur l'écran de saisie. **Plus aucune question ouverte** ; le risque « fuite du code = fuite des numéros » reste un risque assumé (R7).

Rappel des décisions **déjà prises** dont ce ticket dépend et qui ne sont **pas** remises en cause : tutoiement, boutons de connexion, deux cases de consentement (a) et (b), panneau de consentement du nouvel utilisateur, pas de flèche retour sur la première étape et carrousel à la première ouverture seulement (Authentification Q2, Q8, N1, Q11) ; français et anglais seulement (INIT §9) ; Spark sans Blaze ; photo facultative (2026-10-16) ; suppression d'un compte créateur supprime ses Miffs (2026-10-24) ; contact provisoire Tomessa MFOUKA, `tomessa.mfouka@gmail.com` ; risque juridique assumé, pas de relecture juridique (2026-10-23).

---

## 15. Critères d'acceptation

Découpage : **SP** splash, **IC** identité (logo et icône), **CA** carrousel, **RT** routage, **PR** profil, **TEL** téléphone, **CH** choix de Miff, **PW** paywall provisoire, **B** ONB-B (backend), **PE** permissions, **TB** barre d'onglets, **PT** onglet Profil, **RG** réglages, **X** transverses, **A** Android, **I** iOS, **P** recette du PM sur téléphone, **QA** vérifications du QA. Les critères valent pour les deux plateformes sauf mention.

### Splash
- [ ] **SP1** À chaque démarrage à froid, l'animation (fond bleu, cœur qui apparaît, logo qui pivote légèrement, cœur qui gonfle puis éclate) se joue une seule fois, puis l'app s'ouvre sur l'écran correspondant à l'état du compte.
- [ ] **SP2** La durée totale ne dépasse pas 2,5 s ; l'animation n'attend ni le réseau ni la vérification de session pour se jouer ; si le routage n'est pas prêt à la fin, un fond bleu avec un indicateur discret s'affiche (jamais un écran blanc).
- [ ] **SP3** Avec « réduire les animations » activé : aucune rotation ni gonflement, logo fixe puis fondu.
- [ ] **SP4** Aucun clignotement de plus de trois flashs par seconde ; l'éclatement n'utilise ni éclair blanc ni rouge.
- [ ] **SP5** La transition entre l'écran de lancement du système et l'animation ne montre aucun saut de couleur ni de position visible.
- [ ] **SP6** À la reprise depuis l'arrière-plan, l'animation ne se rejoue pas ; aucun son, aucune vibration, aucun événement d'analyse.

### Identité
- [ ] **IC1** Le logo source (SVG) est dans le dépôt avec sa provenance ; les écrans l'utilisent sans valeur de couleur en dur (tokens).
- [ ] **IC2** Android : icône adaptative définitive (108 dp, zone sûre) avec couche monochrome ; iOS : icône 1024 × 1024 opaque ; icône de fiche Google Play 512 × 512 : **avant soumission aux stores** (jusque-là, l'icône provisoire reste marquée « ne pas publier »).
- [ ] **IC3** Le README de chaque plateforme n'affirme plus « logo provisoire » une fois le logo livré.

### Carrousel
- [ ] **CA1** À la première ouverture sans session, le carrousel s'affiche avec 3 slides, balayage horizontal et points de pagination ; il n'exige aucune authentification.
- [ ] **CA2** Aucun défilement automatique ; chaque slide a un titre propre validé par le PM ; aucun texte n'annonce une fonction inexistante comme si elle existait déjà (la slide 3 est la version « Toi seul(e) décides… » ou la version « Bientôt : … »).
- [ ] **CA3** « Commencer » est présent sur chaque slide et mène à l'inscription ; la flèche retour de l'inscription revient au carrousel.
- [ ] **CA4** Le lien « J'ai déjà un compte » (décision Q8) est présent sous « Commencer » et mène à la connexion.
- [ ] **CA5** Après une déconnexion ou une suppression de compte, ou si le carrousel a déjà été validé, il n'est plus affiché.
- [ ] **CA6** La pagination est annoncée au lecteur d'écran (« Page 1 sur 3 ») ; texte lisible en police au maximum ; en droite-à-gauche, l'ordre des slides et des points est inversé.
- [ ] **CA7** Aucun événement d'analyse n'est envoyé depuis le carrousel.
- [ ] **CA8** Aucune photo de banque d'images dans les visuels livrés.

### Routage
- [ ] **RT1** Pour chaque état (aucune session ; session sans profil ; session avec profil mais sans numéro ; profil sans Miff ; Miff présente), le premier écran affiché est celui de §6.2, après un démarrage à froid.
- [ ] **RT2** Fermer l'app de force pendant le profil, le téléphone, le choix de Miff ou les amorces, puis la rouvrir : retour à l'étape correcte (brouillon local conservé), sans nouvelle inscription et sans repasser par le carrousel.
- [ ] **RT3** Un compte complet qui se reconnecte sur un nouvel appareil arrive au Feed, sans profil ni choix de Miff.
- [ ] **RT4** Perte de la dernière Miff (retrait, Miff supprimée) : message neutre de DUR, puis écran de choix ; l'index périmé n'a pas fait croire à une Miff existante.
- [ ] **RT5** Session valide, hors connexion, sans cache : message neutre avec « Réessayer » ; jamais l'écran de profil ; aucun plantage.
- [ ] **RT6** Avec le drapeau de purge du cache présent au démarrage, la purge s'exécute avant tout accès à Firestore.
- [ ] **RT7** Le routage ne repose sur aucun champ de progression stocké ; il coûte 2 lectures Firestore au plus par ouverture (mesuré).

### Profil et téléphone
- [ ] **PR1** L'étape profil est inaccessible sans l'acceptation (a) enregistrée sur l'appareil (critère C14 de l'Authentification).
- [ ] **PR2** La flèche est grisée tant que prénom et nom n'ont pas au moins 1 caractère après suppression des espaces autour (Q6) ; plus de 50 caractères est impossible, avec « 50 caractères maximum. ».
- [ ] **PR3** La photo est facultative : sans photo, on peut continuer et un substitut (initiales) s'affiche dans le Profil.
- [ ] **PR4** Avec une photo : choix prise de vue / galerie, recadrage carré obligatoire avant validation, possibilité de supprimer ; le fichier enregistré fait **20 000 octets au plus**, ne contient **aucune métadonnée** (vérifié sur un fichier de test contenant une position) et n'est pas copié dans la galerie.
- [ ] **PR5** La galerie ne demande aucune permission de photothèque ; l'appareil photo ne demande une permission que sur iOS, à l'appui sur « Prendre une photo », avec un texte bienveillant ; un refus laisse un chemin (galerie).
- [ ] **PR6** Le texte « Visible par les membres de tes Miffs » est présent sous la photo.
- [ ] **PR7** Aucune écriture Firestore à l'étape profil ; le document `users/{uid}` est écrit **en une seule fois à la fin de l'étape téléphone**, avec `termsAcceptedAt` et `termsVersion` de l'inscription, le numéro E.164 et `analyticsConsent` ; l'étape suivante n'apparaît qu'après confirmation du serveur ; hors connexion, message neutre et champs conservés.
- [ ] **PR8** Avec Google ou Apple, prénom et nom préremplis (tronqués à 50), modifiables ; aucune photo importée.
- [ ] **PR9** Aucune flèche retour sur l'étape profil ; le bouton « retour » du système, sur Android, met l'app en arrière-plan ; l'étape téléphone a une flèche retour vers le profil, brouillon conservé.
- [ ] **PR10** Aucun lien « Se déconnecter » ni « Ce n'est pas toi ? » n'existe pendant l'onboarding (décision Q14) ; la déconnexion reste possible depuis Réglages > Mon compte une fois l'onboarding terminé.
- [ ] **TEL1** L'étape téléphone est **obligatoire** : aucun « Passer », bouton grisé tant qu'aucun chiffre n'est saisi.
- [ ] **TEL2** Pays par défaut = pays de la région de l'appareil, sinon France ; tous les pays sont listés avec indicatif ; choisir un pays met l'indicatif à jour ; coller un numéro international (« + … ») déduit le pays.
- [ ] **TEL3** Un numéro de forme invalide pour le pays choisi affiche « Ce numéro ne semble pas correct. » et n'écrit rien ; un zéro initial, des espaces, tirets ou parenthèses sont tolérés.
- [ ] **TEL4** Le numéro est stocké en **E.164** (test unitaire : plusieurs saisies équivalentes donnent la même chaîne) ; aucun format national en base.
- [ ] **TEL5** Aucune vérification par SMS, aucun appel à une authentification par téléphone ; aucune unicité exigée.
- [ ] **TEL6** La mention « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » est visible sous le champ numéro de l'étape téléphone et dans Réglages > Mes informations ; elle ne promet pas plus que ce que les règles appliquent (lecture limitée aux membres des Miffs de la personne).
- [ ] **TEL7** Le numéro n'apparaît dans aucun journal, événement d'analyse ou rapport de plantage (recherche par le QA).
- [ ] **TEL8** Un compte avec profil mais sans numéro arrive à l'étape téléphone seule, puis à la suite normale (ordre 2b du routage).
- [ ] **TEL9** Le brouillon local (prénom, nom, photo, pays, numéro, étape) survit à la fermeture de l'app, est supprimé après confirmation du serveur et à la déconnexion, et n'est pas sauvegardé dans le cloud de l'appareil.

### Choix de Miff
- [ ] **CH1** Le titre et les deux cartes correspondent à §6.5 (tutoiement) ; la carte « Rejoindre » contient un champ de code qui s'active à 12 caractères valides.
- [ ] **CH2** Aucune écriture Firestore n'est faite par cet écran ; il ne contient aucune validation de code.
- [ ] **CH3** Un utilisateur sans Miff accède à la création et à la jonction **sans** paywall.
- [ ] **CH4** Aucune flèche retour sur cet écran ; le profil et le numéro restent modifiables dans Réglages > Mes informations.
- [ ] **CH5** Un compte ayant déjà une Miff ne voit jamais cet écran.
- [ ] **CH6** `onboarding_completed` part une seule fois, avec la valeur `via` correcte, **si et seulement si** (b) est coché et que la personne n'avait aucune Miff avant ; jamais pour une reconnexion.

### Permissions
- [ ] **PE1** Aucune permission de localisation ni de notification n'est demandée avant l'entrée dans une Miff ; aucune demande n'est posée en fenêtre sur le Feed à l'ouverture.
- [ ] **PE2** L'amorce de localisation s'affiche **avant** toute fenêtre du système, fusionnée avec le consentement de la Miff (Q3) ; elle dit : utilisation en arrière-plan, ce que voient les membres, avec qui, comment arrêter, et ce qui se passe sans permission ; **deux boutons** « Autoriser » et « Plus tard » de même importance ; aucun texte qui promet plus que l'app ne fait (§4 hors périmètre).
- [ ] **PE3** « Plus tard » ferme l'amorce sans reproche : l'app est pleinement utilisable (mise à jour manuelle, escalade) ; un bandeau non modal fermable apparaît ; aucune fenêtre ne se réaffiche automatiquement.
- [ ] **PE4** Android 11 et plus : après la fenêtre « pendant l'utilisation », un second écran d'information et un bouton vers les réglages du système ; au retour, l'état est relu ; si « Toujours » est accordé, l'écran se ferme.
- [ ] **PE5** Android 8 à 10 : comportement conforme au tableau de §6.6 (vérifié ou noté comme non vérifié sur appareil).
- [ ] **PE6** Position approximative seulement : état « localisation approximative » distinct, jamais présenté comme une erreur, sans insistance.
- [ ] **PE7** iOS : demande « pendant l'utilisation » puis évolution vers « Toujours » ; « Autoriser une fois » et position approximative reconnus ; textes d'usage conformes à §6.11.
- [ ] **PE8** L'état réel de chaque permission est relu à chaque ouverture et au retour au premier plan ; un retrait dans les réglages du téléphone fait réapparaître le bandeau non modal, sans fenêtre.
- [ ] **PE9** Notifications (décision Q4) : l'amorce s'affiche **dès l'onboarding, y compris en bêta**, après l'amorce de localisation ; « Autoriser » déclenche la fenêtre du système (Android 13 et plus ; sur Android 8 à 12 l'amorce ne s'affiche pas, faute de fenêtre) ; « Plus tard » **ne** déclenche **pas** la fenêtre ; aucune relance par fenêtre.
- [ ] **PE10** Le texte de l'amorce de notifications dit qu'elles arrivent **bientôt** et que **rien n'est envoyé pour l'instant** ; aucun exemple n'est présenté comme réel ; la fiche de store ne promet aucune notification.
- [ ] **PE11** Aucun événement d'analyse ne porte sur les permissions ni la localisation (contrat `analytics-events.md`) ; aucune position n'est lue, enregistrée ni transmise par ce ticket (il ne lit que l'**état** des permissions).
- [ ] **PE12** Un interrupteur de configuration à la compilation permet de ne pas appeler l'amorce de notifications ni l'écran Paywall provisoire dans un build ; il est testé dans les deux positions ; la position par défaut est « affichés » en bêta.

### Écran Paywall provisoire
- [ ] **PW1** L'écran apparaît **une seule fois** par compte, en fin d'onboarding, après l'amorce de notifications et avant le Feed.
- [ ] **PW2** Aucun prix, aucun essai gratuit, aucune mention d'abonnement, aucun bouton d'achat ; le texte dit « bientôt » et n'engage à rien.
- [ ] **PW3** « Continuer » est visible sans défilement ; la croix et le bouton « retour » du système ferment l'écran ; aucun délai, aucun compte à rebours ; fonctionne hors connexion.
- [ ] **PW4** Aucune relance automatique : pas à la connexion, pas après N visites, pas depuis les Réglages.
- [ ] **PW5** Aucun appel à l'App Store ni à Google Play Billing, aucune écriture de `entitlements`.
- [ ] **PW6** Aucun événement d'analyse n'est envoyé par cet écran.

### ONB-B (backend, avant les écrans de profil)
- [ ] **B1** `users/{uid}` accepte et exige à la création une clé `phone` E.164 valide ; elle est modifiable, non vidable, non supprimable ; `members/{uid}` accepte la copie dans les mêmes limites ; `data-model.md` (§2.1, §2.6, §8, §11) est à jour.
- [ ] **B2** Les tests de règles couvrent les cas acceptés et refusés listés en §8 ; les tests qui affirmaient le refus du téléphone sont inversés ; le test par mutation couvre les nouvelles protections ; la CI des règles est verte.
- [ ] **B3** Revue de sécurité faite (usurpation, fuite des numéros à un nouvel arrivant avec le code, journalisation) avant tout déploiement.
- [ ] **B4** Déploiement des règles uniquement après tests verts, revue et « oui » explicite du PM, avec `--project miff-3d283` ; retour arrière documenté.
- [ ] **B5** La politique de confidentialité, les CGU (français et anglais) et les déclarations des stores mentionnent le numéro de téléphone avant toute publication.

### Barre d'onglets
- [ ] **TB1** Trois onglets (Feed, bouton central, Profil) avec états actif/inactif, icônes recolorables et étiquettes d'accessibilité.
- [ ] **TB2** La barre n'est visible qu'une fois l'onboarding terminé, et masquée dans les écrans plein écran et avec le clavier ouvert.
- [ ] **TB3** L'état de chaque onglet est conservé au changement ; toucher l'onglet actif remonte en haut ; « retour » sur un onglet racine quitte l'app (Android).
- [ ] **TB4** En droite-à-gauche, l'ordre est inversé ; les icônes sans direction ne sont pas retournées.
- [ ] **TB5** Les écrans provisoires de Feed et Update ne figurent pas dans un build destiné à des testeurs externes.

### Onglet Profil
- [ ] **PT1** Photo (ou substitut), « Prénom Nom » et « C'est toi » s'affichent ; appui sur la photo → « Mes informations » ; engrenage → Réglages.
- [ ] **PT2** Emplacements « Mes Miffs » et « Lieux » présents sans contenu parasite tant que les tickets Miff et Lieux n'existent pas ; le mot « famille » n'est pas utilisé comme nom de Miff par défaut.

### Réglages
- [ ] **RG1** Les entrées sont celles de §6.9 ; aucune ligne « Notifications », aucun bandeau ni écran d'abonnement, aucune ligne de changement de mot de passe, dans cette version.
- [ ] **RG2** « Mes informations » : « Enregistrer » est grisé tant qu'aucune valeur n'a changé ; mêmes règles que l'étape profil et l'étape téléphone (le numéro ne peut pas être vidé) ; l'enregistrement met à jour `users/{uid}` et chaque `members/{uid}` des Miffs de la personne ; un échec partiel est repris sans perte et sans message alarmant.
- [ ] **RG3** « Se déconnecter » affiche la confirmation de §6.11 puis déclenche la procédure de l'Authentification (§7.6) : écoutes arrêtées, cache purgé, retour à la connexion sans carrousel ; relancer l'app hors connexion n'affiche aucune donnée de Miff.
- [ ] **RG4** « Supprimer mon compte » ouvre le flux d'AUTH-5 (liste des Miffs, ré-authentification) ; le texte de Notion (« Vous perdrez instantanément… ») n'existe nulle part dans l'app.
- [ ] **RG5** « Langue » : deux langues (+ « Langue du téléphone ») ; Android : changement immédiat dans l'app ; iOS : ouverture des réglages de l'iPhone (décision Q11) ; tous les textes, les liens légaux et l'e-mail de réinitialisation suivent la langue.
- [ ] **RG6** « Confidentialité » : l'interrupteur statistiques/plantages lit et écrit le consentement `analyticsConsent` ; le retrait coupe la collecte **immédiatement** ; état de la localisation et état des notifications affichés avec « Ouvrir les réglages du téléphone » ; liens CGU et politique ouvrent les pages web de la langue de l'app ; « Nous contacter » utilise le contact provisoire.
- [ ] **RG7** Aucun écran des Réglages ne déclenche un paywall.
- [ ] **RG8** L'adresse e-mail affichée est celle du compte (relais Apple incluse) et n'est pas copiée dans Firestore, ni dans un événement, ni dans un journal.

### Transverses
- [ ] **X1** Tous les textes sont ceux de §6.11 une fois validés ; ton bienveillant, aucun reproche ; aucun code technique visible.
- [ ] **X2** Hors connexion, chaque bouton d'envoi affiche le message neutre sans vider les champs ni planter.
- [ ] **X3** Aucune chaîne en dur ; clés `onboarding_`, `profile_`, `settings_`, `permission_`, `tabbar_` identiques sur les deux plateformes (contrôle de parité de la CI) ; français et anglais présents ; préfixes ajoutés au contrat `i18n-keys.md` et au script.
- [ ] **X4** Les écrans respectent le design system (tokens, polices, `text/error`), restent en thème clair, suivent la taille de police du système sans masquer le bouton principal, sont lisibles au lecteur d'écran et en pseudo-langue droite-à-gauche.
- [ ] **X5** Aucune position, adresse e-mail, mot de passe, nom ou photo n'apparaît dans un journal, un événement d'analyse ou un rapport de plantage (recherche par le QA).
- [ ] **X6** Plan Spark inchangé, aucun moyen de paiement ajouté, aucun secret ni fichier de configuration Firebase dans le dépôt.
- [ ] **X7** Aucun paywall fonctionnel n'est affiché par ce ticket ; le seul écran de ce type est le Paywall provisoire de §6.12, une seule fois, sans prix.

### Android
- [ ] **A1** Le splash se joue sans saut sur l'OPPO Find X5 Lite (Android 13) ; le comportement d'Android 12 et plus (écran de lancement du système) est noté.
- [ ] **A2** Le sélecteur de photos du système et l'application photo fonctionnent sans permission déclarée dans le manifeste (vérifié dans le manifeste fusionné).
- [ ] **A3** Les permissions déclarées pour la localisation (premier plan, arrière-plan) sont déclarées avec justification prête pour le formulaire de Google Play ; **aucune** n'est demandée avant l'amorce.
- [ ] **A4** Les tests unitaires couvrent le routage, la longueur des noms, la compression de la photo (≤ 20 000 octets, sans métadonnées) et la machine d'états des permissions.

### iOS
- [ ] **I1** Le splash se joue sur l'iPhone 11 Pro (TestFlight) sans saut avec l'écran de lancement.
- [ ] **I2** Les textes d'usage de la localisation et de l'appareil photo sont présents, en français et en anglais, et conformes à §6.11 ; aucune clé n'est ajoutée sans usage (la clé de localisation « toujours » n'apparaît qu'avec ONB-8).
- [ ] **I3** Les deux demandes de localisation s'enchaînent comme décrit ; « Autoriser une fois » et position approximative sont gérés.
- [ ] **I4** La ligne « Langue » ouvre les réglages de l'iPhone pour Miff (décision Q11).
- [ ] **I5** Les tests XCTest couvrent le routage, les noms, la compression de la photo et la machine d'états (mêmes cas qu'Android).

### Ce que le PM peut vérifier lui-même sur téléphone
- [ ] **P1** Ouvrir l'app après installation : animation d'ouverture (moins de 2,5 s), puis carrousel ; relancer l'app après avoir appuyé sur « Commencer » : plus de carrousel.
- [ ] **P2** Créer un compte, fermer l'app au milieu de l'écran de profil, la rouvrir : on retrouve le profil, sans nouvelle inscription.
- [ ] **P3** Valider un profil sans photo : l'app continue ; avec photo, vérifier le recadrage ; photo du même profil visible dans l'onglet Profil.
- [ ] **P4** Créer ou rejoindre une Miff (tickets Miff livrés) : l'amorce de localisation s'affiche **avant** la fenêtre du système ; choisir « Plus tard » : l'app reste utilisable et affiche un bandeau fermable, sans nouvelle fenêtre à la réouverture.
- [ ] **P5** Retirer la localisation dans les réglages du téléphone, rouvrir l'app : bandeau, pas de fenêtre.
- [ ] **P6** Réglages : changer la langue (Android : dans l'app ; iOS : réglages de l'iPhone), activer puis désactiver les statistiques, lire les CGU et la politique.
- [ ] **P7** Se déconnecter : confirmation avec la phrase d'information ; retour à la connexion sans carrousel ; mode avion + relance : aucune donnée de Miff.
- [ ] **P8** Mode avion pendant le profil : message neutre, champs conservés.
- [ ] **P9** Dans les consoles Firebase et GitHub, la facture reste à 0 €.
- [ ] **P10** Créer un compte de test : l'étape téléphone est obligatoire (impossible de passer), un numéro invalide est refusé calmement, un numéro valide est accepté ; vérifier dans la console Firestore (projet de développement ou après accord) que le document contient un numéro au format `+…`.
- [ ] **P11** À la fin de l'onboarding : l'amorce de notifications dit « bientôt » (aucun envoi n'a lieu) ; l'écran Paywall provisoire s'affiche une seule fois, sans prix, et se ferme facilement ; il ne revient pas à la réouverture.
- [ ] **P12** Constater (sans la contourner) la conséquence de la décision Q14 : pendant l'onboarding, aucune déconnexion n'est possible ; elle l'est depuis Réglages > Mon compte une fois l'onboarding terminé.

### Pour le QA (`qa-tester`)
- [ ] **QA1** Chaque critère est rejoué et consigné dans `docs/qa/` avec preuve (capture, journal, lien CI).
- [ ] **QA2** Vérification de l'absence de métadonnées dans le fichier de photo, et de sa taille sur 20 images de tailles et d'orientations variées.
- [ ] **QA3** Machine d'états des permissions : toutes les combinaisons de §6.6 par version d'Android disponible et par réponse iOS (au minimum Android 13 sur l'OPPO et iOS 26 sur l'iPhone ; les autres versions notées « non vérifiées »).
- [ ] **QA4** Cas limites de §9, dont l'abandon à chaque étape, la perte de la dernière Miff, le retrait d'une permission pendant que l'app est ouverte et le mode avion.
- [ ] **QA5** Recherche de position, d'e-mail, de nom et de photo dans les journaux, la base et les événements d'analyse.
- [ ] **QA6** Le numéro de téléphone est recherché dans les journaux, la base de test, les événements d'analyse et les rapports de plantage : aucune occurrence en dehors de `users` et `members`.
- [ ] **QA7** Cas limites du téléphone : appareil sans région (repli France), numéro collé avec indicatif, zéro initial, caractères non numériques, très long, pays différent de la langue, compte sans numéro (ordre 2b).

### Définition de « terminé »
Critères cochés, décisions du PM enregistrées, revue de code en contexte neuf, revue de sécurité (consentement, permissions, photo, journaux), QA, recette du PM, puis fusion par Claude après « oui » explicite du PM pour chaque PR. Aucun push direct sur `main`.

---

## 16. Dépendances

| Avec | Nature |
|---|---|
| **INIT-2 / INIT-4** (socles, déjà livrés) | Design system en code, traductions, App Check, écran d'accueil minimal et icône provisoires à remplacer |
| **ONB-B** (backend, `backend-data`) | Schéma, règles, tests et revue de sécurité du téléphone ; avant ONB-2 et ONB-6 ; déploiement sur accord du PM |
| **Authentification** (AUTH-1, AUTH-3, AUTH-B, AUTH-W, AUTH-5) | Session, consentement (a) et (b), panneau de consentement, déconnexion et purge, pages web légales, suppression de compte ; **AUTH-B** (champ de consentement statistiques) bloque l'écriture du profil |
| **DUR** (durcissement) | Purge du cache, messages neutres de perte d'accès, nettoyage au lancement, App Check |
| **Tickets Miff** (créer, rejoindre, consentement par Miff, partage du code) | Écrans et règles amont ; ONB-4 et ONB-8 en dépendent ; l'onboarding n'est complet qu'avec eux |
| **Ticket Lieux** | Consomme le composant de permission de localisation (premier plan seulement) ; emplacement « Lieux » de l'onglet Profil |
| **Tickets Feed, Update** | Contenu des onglets Feed et central ; boutons « Appeler » et « Message » qui consomment le numéro ; décisions sur « Localiser » (§4 hors périmètre) |
| **Ticket Tracking** (géofencing) | Usage de la permission, `locationAccess` ; alignement du texte de l'amorce avec le stockage réel |
| **Ticket Premium** | Vrai paywall, bandeau, écran d'abonnement, déclencheurs (aucun dans ce ticket hormis l'écran provisoire de §6.12, à remplacer) |
| **Ticket Notifications (Blaze)** | Envoi réel, ligne « Notifications » des Réglages, centre de notifications ; réutilise le composant d'amorce livré ici ; tant qu'il n'existe pas, la permission est demandée sans qu'aucune notification soit délivrée (R15) |
| **Contrat `analytics-events.md`** | `onboarding_cta_tapped` à retirer ou requalifier ; `onboarding_completed` conforme (PR distincte) |
| **Contrat `i18n-keys.md`** | Nouveaux préfixes (PR du ticket) |
| **`design-system`** | Dessin des écrans et composants manquants (§3.2), tutoiement, illustrations libres, Tabbar à variantes |
| **`release-manager`** | Textes d'usage, vidéo et formulaire Google Play de localisation en arrière-plan, fiches des stores, **déclaration du numéro de téléphone**, cohérence avec la politique de confidentialité |
| **Actions du PM** | §10 |

---

*Ce ticket ne modifie ni Figma, ni Notion, ni les règles Firestore, ni les tests, ni `backend/data-model.md`. Aucune commande Firebase n'a été exécutée. Aucun texte juridique n'est rédigé ici et aucun n'est présenté comme relu par un juriste.*
