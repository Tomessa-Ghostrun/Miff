# Ticket : Feed, onglet Profil, Réglages complets et Abonnement (challenge des maquettes Figma du 2026-10-30)

**Statut** : brouillon issu de l'étape « challenge » du process par ticket ; **15 questions ouvertes pour le PM (§10)** ; aucun développement lancé ; aucun statut Notion modifié.
**Rédigé le** : 2026-10-30 (agent `product-specs`).
**Complète et confronte aux maquettes** : `docs/tickets/onboarding.md` (ONB-3 : barre d'onglets, onglet Profil, Réglages minimaux ; §6.7 à §6.10), `docs/tickets/miff.md` (§7 : constats Feed F1 à F9), `docs/tickets/authentification.md` (§7.6, §7.7, AUTH-2, AUTH-5), `docs/specs/feed-cercle-de-confiance.md`, `docs/specs/update-et-escalade-statut.md`, `docs/specs/paywall-premium.md`, `docs/specs/notifications.md`, `docs/specs/lieux-de-confiance.md`, `docs/specs/profil-et-reglages.md`. **Ne remplace aucun de ces documents** : là où une maquette contredit une décision déjà prise, c'est la décision qui l'emporte tant que le PM ne l'a pas changée (§4B).
**Maquettes lues (les 26 images)** : `design/exports/feed/` (9), `design/exports/profil/` (2), `design/exports/reglages/` (15), avec leurs README de repérage. Elles sont citées ci-dessous par « feed-0 » (`Welcoming screen.png`), « feed-1 » (`Welcoming screen-1.png`), etc., et « profil-0/1 », « reglages-0 » à « reglages-14 ». J'ai aussi regardé `design/exports/onboarding-original/Paywall.png` (liste des quatre bénéfices Premium).
**Sources lues** : `CLAUDE.md` ; les tickets `onboarding.md` (en entier), `onboarding-decisions-pm.md`, `miff.md`, `miff-questions-pm.md`, `authentification.md` (§7, §15), `initialisation-technique.md` (§9 et décisions du 2026-10-16) ; `backend/data-model.md` (§1 à §4, §6, §8, §12) ; `design/design-system.md` (couleurs de statut) ; `design/screens/miff-ecran-miff.md` ; `docs/specs/*.md` ; état du code Android sur `main` (`AppRouting.kt`, `CHANGELOG.md`).
**Limites de cette lecture (à connaître)** :
- **Notion n'a pas pu être relu directement** pendant cette session (outil indisponible). Les notes Notion « Feed » (ticket « A cadrer »), « ONB-3 », « AUTH-5 » et « Premium » ont été lues **par leurs copies et citations** dans les tickets du dépôt (`onboarding.md` §4, `miff.md` §7). Si le ticket Notion « FEED » a reçu des règles depuis le 2026-10-24, elles ne sont pas prises en compte : à me signaler.
- Les maquettes sont des **images de 320 px** : aucune mesure fine (tailles, espacements) n'est possible. Les écrans montrent des **données de test** (noms de Miff « Famille Alberta », « Les tuches », prénoms variés) : je ne m'y arrête pas.
- Je n'ai modifié ni le code, ni Notion, ni Figma, ni les règles Firestore.
**Convention** : [Décidé] = décision du PM déjà écrite quelque part (source citée). [Proposé] = ma proposition, **à valider**. [À vérifier] = fait de plateforme ou de store tiré de la connaissance générale, non confirmé dans le dépôt.

---

## 1. Résumé pour le PM

En une phrase : les maquettes donnent enfin le **vrai visage de l'app après l'onboarding** (Feed, Profil, Réglages, Abonnement) ; la plupart des écrans sont **réalisables tout de suite et gratuitement**, mais **trois blocs dépendent de choses qui n'existent pas encore** : les statuts réels (géofencing, mise à jour manuelle), les achats d'abonnement (pas de serveur) et les notifications (pas de serveur).

**Ce qui est bon à prendre tel quel** : la structure des Réglages, « Mes informations », le sélecteur de Miff, le dessin des cartes de proches, le carrousel de Miffs du Profil, la logique des trois niveaux de couleur.

**Les sept constats qui comptent** (détail en §4) :

| # | Constat | Pourquoi c'est important |
|---|---|---|
| 1 | **Le Feed affiche des états que personne ne peut encore produire** : « À la maison », « a quitté la maison », « plus de nouvelles » demandent le suivi des lieux (géofencing) et/ou la mise à jour manuelle, deux tickets qui ne sont pas écrits. | Le Feed peut être construit et testé, mais **montrera « Pas encore de nouvelles » pour tout le monde** tant que rien n'écrit de statut (§5). |
| 2 | **Plusieurs états de la maquette contredisent des décisions déjà prises** : bandeau « Tout le monde est en sécurité » affiché sans personne ou sans nouvelles (décision QM12), barre d'onglets visible sans Miff (Q13), orange après 18 min alors que la règle est 2 h, 6 langues au lieu de 2, vouvoiement au lieu de tutoiement. | À corriger dans les maquettes avant le développement (§4B). |
| 3 | **« Zone à risque » existe bel et bien dans les specs** (type de lieu Premium, `lieux-de-confiance.md`, `paywall-premium.md`, `data-model.md` §2.9), **mais son comportement n'a jamais été défini** et la formulation rouge (« Thomas est dans une zone à risque ») est le contraire du ton voulu. Elle peut aussi servir de contrôle d'un proche dans un couple. | Question 10 : je recommande de ne pas la livrer dans la v1 du Feed. |
| 4 | **« Localiser » promet une position que le produit ne stocke pas** (décision : jamais de coordonnées d'une personne). | Question 13 : « Voir le lieu » à la place, ou retrait. |
| 5 | **L'abonnement des maquettes ne peut pas fonctionner aujourd'hui** : pas de serveur pour valider un achat, le premium est accordé à la main ; et sur les deux stores, **c'est le magasin (pas l'app) qui arrête ou change un abonnement** [À vérifier]. Prix, essai gratuit de 7 jours, nom « Miff+ », « Économisez 30 % » (c'est 28,6 %) sont à arbitrer. | Questions 7 à 9. Rien de payant n'est branché dans ce ticket ; on prévoit seulement une coquille honnête. |
| 6 | **Des écrans obligatoires pour les stores manquent** : liens CGU et politique, interrupteur statistiques (« Confidentialité »), confirmation de déconnexion, état des permissions. Ils sont déjà décidés dans `onboarding.md` §6.9 mais ne sont pas dans les maquettes. | À dessiner (§4C). |
| 7 | **Erreurs de maquette** : libellé « Nom » sur le champ téléphone, compteur « 8/50 » pour « Laura », date « 02/18/12 », dates incohérentes dans l'abonnement, fautes d'orthographe. | Liste en §4A. |

**Ce que je propose de livrer, dans l'ordre** (détail en §7) : d'abord la **navigation et les Réglages** (utiles seuls, 0 dépendance serveur), puis le **Feed « cercle »** (cartes, Message / Appeler, Ajouter un proche) une fois les écrans Miff livrés, puis le **rendu des statuts** (testé sur données de test) en même temps que le ticket qui écrit les premiers statuts, puis le Profil complet (Miffs et lieux) avec le ticket Lieux. L'abonnement et les notifications restent des **coquilles masquées** jusqu'à la décision sur le serveur.

**Coût : 0 €** (aucun service, aucune bibliothèque payante, aucun moyen de paiement ; §11). **Taille globale** : L à XL, répartie sur plusieurs PR Android puis iOS.

---

## 2. Périmètre et frontières

### Dans le périmètre de ce ticket
- **Cartographie et challenge** des 26 maquettes (§3, §4).
- **Barre d'onglets**, **onglet Profil**, **Réglages** (liste, Mes informations, Mon compte, Langue, Confidentialité) : reprise et **modification d'ONB-3 / ONB-7** (§7).
- **Feed** (en-tête, sélecteur, bandeau, cartes, boutons) : nouveau ticket **FEED**, découpé en deux temps (cercle sans statuts, puis statuts).
- **Coquille Abonnement** (lecture seule, premium accordé à la main).
- Liste des **écrans à dessiner ou corriger** pour `design-system`.

### Hors périmètre (propriétaires indiqués)
| Sujet | Propriétaire | Frontière |
|---|---|---|
| Créer / rejoindre une Miff, écran de la Miff, partage du code, retirer / quitter / supprimer | Tickets **Miff** (`miff.md`, MIFF-1 à MIFF-4) | Le Feed et le Profil **ouvrent** leurs écrans ; ils ne les refont pas. |
| Création et modification des **lieux**, catégories, zones à risque | Ticket **Lieux** (à écrire) | Le Profil **affiche** la liste ; « Ajouter un lieu » ouvre le ticket Lieux. |
| **Géofencing** (détection des entrées / sorties), permissions de localisation | Ticket **Tracking** (à écrire) et ONB-4 / ONB-8 | Le Feed **lit** des statuts ; il n'en écrit jamais. |
| **Mise à jour manuelle** (« Donner des nouvelles », photo, texte), bouton central de la barre d'onglets | Ticket **Update** (à écrire) | Voir Question 1. |
| **Envoi** de notifications, centre de notifications (cloche), « notifications intelligentes » | Ticket **Notifications** (exige Blaze) | Voir Questions 6 et 15. |
| **Vrai paywall, achat, essai gratuit, tarifs, validation d'achat** | Ticket **Premium** (exige Blaze pour valider les achats, `data-model.md` §6 et §13) | Ce ticket ne livre qu'une **coquille en lecture seule** (ABO-1). |
| Suppression de compte (flux complet) | **AUTH-5** | Réglages ne fait que l'**ouvrir**. |
| Inscription, connexion, mot de passe oublié, déconnexion (procédure) | **AUTH-1 à AUTH-4** | Réglages **appelle** la procédure existante. |
| Textes légaux, déclarations des stores | `release-manager`, site AUTH-W | Les liens sont dans Confidentialité. |

---

## 3. Cartographie écran par écran

Légende du verdict : **OK** (conforme aux specs, à habiller) ; **À corriger** (écart listé en §4) ; **Bloqué** (dépend d'un ticket ou d'une décision absente).

### 3.1 Feed (`design/exports/feed/`)

| Maquette | Ce qu'on voit | Fonctionnalité | Spec / ticket existant | Verdict |
|---|---|---|---|---|
| feed-0 | Bandeau vert « Tout le monde est en sécurité, mis à jour il y a 2 min » ; carte de « Tomessa » : « À la maison », batterie 85 %, « Arrivé il y a 12 min », photo, trois boutons (lieu, message, téléphone) | Feed nominal : bandeau de synthèse, carte de membre | `feed-cercle-de-confiance.md` ; `miff.md` §7 (F1, F2, F4, F5) ; `data-model.md` §3 et §8 (Feed) | À corriger (A7, A11, B3, B4) ; **Bloqué** pour « À la maison » (Tracking, Lieux) |
| feed-1 | Miff sans autre proche : bandeau vert + bouton « Ajouter un proche » | Miff vide | `miff.md` §3.1 (« Ajouter un proche » ouvre le partage du code, §4 #6), G9 | À corriger (B3 : bandeau « en sécurité » alors que personne n'a donné de nouvelles) |
| feed-2 | « Pas encore de Miff » dans le sélecteur ; bandeau vert ; boutons « Créer une miff » / « Rejoindre une miff » ; barre d'onglets visible | Aucune Miff | `miff.md` §7 F3 (QM12) ; `onboarding.md` §6.2 et Q13 (écran de choix **plein écran sans barre d'onglets**) | **À corriger** : contredit QM12 et Q13 (B2, B3). Cet état ne devrait jamais s'afficher |
| feed-3 | Sélecteur ouvert : « Famille Mfouka », « Les tuches », « + Rejoindre une Miff », « + Créer une Miff » | Choix de la Miff active (Premium) | `feed-cercle-de-confiance.md` (sélecteur si plusieurs Miffs) ; `data-model.md` §8 (Sélecteur) ; QM13 | À corriger / Question 2 (rien ne mène à l'écran de la Miff ; entrées « Rejoindre / Créer » à réserver au Premium) |
| feed-4 | Feed complet vu en entier : deux proches (« À la maison », « À l'école »), bouton « + Ajouter un proche » | Liste de cartes | Idem feed-0 | À corriger (A6 : « Arrivé » non accordé ; catégories de lieux absentes du schéma, §5) |
| feed-5 | Bandeau et carte **orange** : « Thomas a quitté la maison », « Parti(e) il y a 18 min » | Palier orange | `update-et-escalade-statut.md` (orange après **2 h** sans update) ; `data-model.md` §3 | **À corriger** : 18 min ne doit pas être orange (B1) |
| feed-6 | Idem avec deux pastilles de pagination sur le bandeau | Plusieurs alertes dans le bandeau | Aucune spec (« carrousel » cité en F7) | **Bloqué** : règle de pagination à définir (Question 11) ; une seule alerte visible ici (A8) |
| feed-7 | Bandeau et carte **rouge** : « Thomas est dans une zone à risque » | Zone à risque | `lieux-de-confiance.md`, `paywall-premium.md`, `data-model.md` §2.9 et §3 (`in_risk`) | **Bloqué** : comportement jamais défini, ton anxiogène (Question 10) |
| feed-8 | Bandeau rouge « Plus de nouvelles de Thomas, depuis 16 heures » + carte d'Amandine « Dans une zone à risque » | Palier rouge | `update-et-escalade-statut.md` ; `data-model.md` §3 | À corriger (A11, Question 11) |
| Éléments communs | Avatar en haut à gauche, logo, cloche avec pastille | En-tête | Aucune spec (cloche : « centre de notifications » cité dans `onboarding.md` §4 hors périmètre) | **Bloqué** (Question 15) |

### 3.2 Onglet Profil (`design/exports/profil/`)

| Maquette | Ce qu'on voit | Fonctionnalité | Spec / ticket existant | Verdict |
|---|---|---|---|---|
| profil-0 | Photo, « Emmanuelle Martin, C'est vous », engrenage ; carte de Miff « Famille Martin, 4 membres » avec avatars, 3 pastilles ; « Lieux de la Miff » (2 lieux avec adresse, « Ajouter », « + Ajouter un lieu ») ; barre d'onglets, Profil actif | Profil : en-tête, carrousel de Miffs, lieux | `onboarding.md` §6.8 (coquille) ; `miff.md` QM13 ; ticket Lieux (à écrire) | OK pour l'en-tête (vouvoiement à corriger, B6) ; **Bloqué** pour Miffs (MIFF-1) et lieux (Lieux) ; À corriger (adresses : Question 3 ; « Ajouter » en double, A12) |
| profil-1 | Dernière carte du carrousel : « Créer une nouvelle Miff » (3e pastille) | Création d'une 2e Miff | `miff.md` QM7 (paywall 2e Miff = ticket Premium) ; règles : 1 Miff en gratuit | **Bloqué** (Question 2) : refusé par les règles pour un compte gratuit |

### 3.3 Réglages (`design/exports/reglages/`)

| Maquette | Ce qu'on voit | Fonctionnalité | Spec / ticket existant | Verdict |
|---|---|---|---|---|
| reglages-0 | Liste : Mes informations, Mon Compte, Langue, Notifications ; encart Premium « Commencer l'essai gratuit (7 j), puis 4,99 €/mois pendant 1 an », pastille « Annulable à tout moment » | Liste des réglages + incitation Premium | `onboarding.md` §6.9 (liste : Mes informations, Mon compte, Langue, **Confidentialité** ; Notifications et Abonnement **masqués**) | **À corriger** : Confidentialité absente, Notifications et encart Premium contredisent §6.9 (B5, B7) |
| reglages-1 | Encart déplié : « Annuel (4,99 €/mois pour 1 an), Économisez 30 % » / « Sans engagement (6,99 €/mois) » | Choix d'offre | `paywall-premium.md` ; `data-model.md` §6 (pas de paywall payant sans validation serveur) | **Bloqué** (Questions 7, 8) ; A5 (28,6 %, pas 30 %) |
| reglages-2 | Liste pour un utilisateur Premium : ligne « Abonnement » à la place de l'encart | Accès à l'abonnement | `initialisation-technique.md` (statut premium, décisions du 2026-10-16) | **Bloqué** (ABO-1 pour la coquille) |
| reglages-3 | Mes informations : photo (crayon), prénom (8/50), nom (7/50), pays « France + 33 », champ téléphone libellé « Nom », aide « sans le premier zéro », « Enregistrer » | Modifier son profil | `onboarding.md` §6.3, §6.4, §6.9 (RG2) ; `data-model.md` §2.1 et §8 | À corriger (A1, A2, A15 ; mention de visibilité du numéro absente, B8 ; vouvoiement) |
| reglages-14 | Mes informations en erreur : « 3 caractères minimum » sous prénom et nom, « Numéro invalide », bouton grisé | Validation | `onboarding.md` Q6 : **1 à 50** caractères | **À corriger** : « 3 caractères minimum » contredit Q6 (B9) |
| reglages-4 | Mon compte : e-mail (grisé), mot de passe actuel / nouveau / confirmation, « Enregistrer le nouveau mot de passe » (grisé), « Déconnexion » (bouton plein), « Supprimer mon compte » (rouge) | Compte | `onboarding.md` §6.9 (e-mail en lecture seule, déconnexion, suppression ; mot de passe **différé**, Q12) ; `authentification.md` §7.6, §7.7 | À corriger / Question 5 |
| reglages-10 | Mon compte, champs remplis avec œil, bouton actif | Changement de mot de passe | Idem | Question 5 |
| reglages-11 | Erreurs : « Mot de passe invalide », « 8 caractères minimum », « Mot de passe non identique » | Validation | `authentification.md` §7.1 et §7.3 (8 caractères, ton bienveillant) | À corriger (ton, tutoiement) |
| reglages-12 | Dialogue « Attention, vos données seront irrécupérables : vous perdrez instantanément toutes vos données y compris votre abonnement si vous en disposez » + Supprimer / Annuler | Suppression de compte | `authentification.md` §7.7 et AUTH-5 ; `onboarding.md` §4 #17 (texte de la maquette **abandonné**) | **À corriger** : texte anxiogène **et inexact** (l'abonnement du magasin n'est pas résilié ; les Miffs créées sont supprimées) |
| reglages-5 | Langue : français, anglais, espagnol, allemand, portugais, italien ; « Enregistrer » | Choix de la langue | `onboarding.md` §6.10 ; `initialisation-technique.md` (FR + EN) | **À corriger** (B5 ; Question 4) |
| reglages-7 | Notifications : interrupteurs « classiques » (activé) et « intelligentes » (désactivé) | Réglage des notifications | `onboarding.md` §6.9 (**masquée** tant que rien n'est envoyé) ; `notifications.md` | **Bloqué** (Question 6) |
| reglages-6 | Abonnement : « Prochain paiement : abonnement annuel avec engagement, le 22/08/27 : 59,88 € » ; « Changer d'abonnement » (Annuel / Mensuel) ; « Arrêter l'abonnement » | Gérer l'abonnement | `paywall-premium.md` (Premium) ; décisions INIT du 2026-10-16 | **Bloqué** (Questions 7, 8, 9) |
| reglages-8 | Changement programmé : « Votre abonnement change 22/08/26 ; à cette date vous passerez d'un prélèvement mensuel à un prélèvement annuel » | Changement d'offre programmé | Idem | À corriger (A4) ; **Bloqué** |
| reglages-9 | Dialogue « Êtes-vous sûr de quitter Miff+ ? Vous perdrez vos accès… y compris les Miff et lieux de confiance créées dans le cadre de l'abonnement » | Arrêt d'abonnement | `data-model.md` §6 (Miffs créées au-delà de la limite **supprimées** à la fin du Premium, décision du 2026-10-24) | **À corriger / Question 9** : conséquence grave et floue |
| reglages-13 | Abonnement après arrêt : « Votre abonnement prendra fin le 02/18/12. Vous ne serez plus prélevé. » + « Annuler l'arrêt de l'abonnement » | Reprise d'un abonnement arrêté | Idem | À corriger (A3) ; **Bloqué** |

---

## 4. Écarts, incohérences et manques

### 4A. Erreurs et incohérences internes aux maquettes (à corriger par `design-system`, dans « Miff - Claude » uniquement)

| # | Maquette | Constat | Correction proposée |
|---|---|---|---|
| A1 | reglages-3 | Le champ téléphone est libellé « **Nom** » (corrigé en « Téléphone » dans reglages-14). | « Téléphone ». |
| A2 | reglages-3 | Compteur « **8/50** » pour « Laura » (5 caractères) ; « 7/50 » pour « Kouajou » (7, juste). | Le compteur affiche le nombre réel de caractères. |
| A3 | reglages-13 | « Prendra fin le **02/18/12** » : format américain (mois/jour) **et** année 2012, alors que la même page affiche « 22/08/27 ». L'écran garde aussi le titre « Prochain paiement » alors qu'il n'y en a plus. | Dates en **format long de la langue de l'app** (« 22 août 2027 ») pour éviter toute confusion jour/mois ; titre « Fin de l'abonnement » après un arrêt. |
| A4 | reglages-8 | « Votre abonnement change **22/08/26** » alors que le prochain paiement est le **22/08/27** (un an plus tôt, et déjà passé à la date de rédaction) ; la phrase dit « mensuel vers annuel » alors que le dossier décrit « annuel vers mensuel » ; la case « Annuel » reste cochée, donc on ne voit pas ce qui est programmé. | Une seule phrase claire par situation, avec la nouvelle offre cochée ou mentionnée. À reprendre dans le ticket Premium. |
| A5 | reglages-1 | « Économisez **30 %** » : (6,99 − 4,99) ÷ 6,99 = **28,6 %**. Le calcul doit venir des prix réels des magasins. | Ne pas écrire de pourcentage en dur ; le calculer, ou le retirer (Question 8). |
| A6 | feed-4, feed-5 | Accords et fautes : « Arrivé il **ya** 12 min » (« il y a »), « Arrivé » pour Amandine (accord), « Parti(e) » (écriture inclusive d'un côté, masculin de l'autre), badge « A quitté maison » (« la » manque), « **Etes**-vous sûr » (reglages-9), « **A** cette date » (reglages-8), « Mon **Compte** » (reglages-0) contre « Mon compte » (reglages-4), sous-titres « Modifier la langue » / « Changez la langue », « notifs » (familier). | Formulations **sans accord de genre** : « À la maison depuis 12 min », « A quitté la maison à 16 h 40 ». Orthographe et casse corrigées ; une seule formule par ligne. |
| A7 | feed-0, feed-8 | « Mis à jour il y a 2 min » est **identique dans tous les états**, y compris sous « Plus de nouvelles de Thomas, depuis 16 heures » : deux notions de temps qui se contredisent (la mise à jour de l'écran / la dernière nouvelle d'un proche). Avec des écouteurs en direct, « mis à jour » n'a plus de sens (`miff.md` §7 F5). | Un seul repère de temps par bandeau : « Dernière nouvelle il y a 2 min » (de quelqu'un de la Miff) ; en hors connexion : « Dernières informations à 16 h 02 ». |
| A8 | feed-5, feed-6 | Les deux écrans montrent **le même état** (une seule alerte) mais feed-6 a deux pastilles de pagination. La règle de pagination n'est écrite nulle part. | Question 11. |
| A9 | feed-7, feed-8 | Texte d'horodatage copié d'un autre état : « Dans une zone à risque » avec « **Parti(e) il y a 18 min** » (feed-7) ; « Dans une zone à risque » avec « **Arrivé il y a 6 h et 38 min** » (feed-8). | Texte propre à chaque état (à définir avec la Question 10). |
| A10 | feed-0 à feed-8 | La **même photo** (un salon) est affichée sur la carte de Thomas dans tous les états, y compris juste après son départ de la maison (feed-5). Une photo de mise à jour ne vit que 24 h (`data-model.md` §2.8) et décrit un moment passé. | Afficher la photo avec son heure (« Photo de 15 h 40 ») ; jamais en absence de nouvelle récente. |
| A11 | feed-0 à feed-8 | Les badges « À la maison », « À l'école » (texte jaune-vert, texte orange, texte saumon sur fond de la même teinte) ont un **contraste insuffisant** : `design-system.md` interdit les couleurs de statut pour du texte (3,0:1 pour `#ff5650`). | Texte des badges en couleur foncée dédiée (à définir par `design-system`, comme `text/error` `#d60700`) ; l'état ne repose pas sur la couleur seule (les icônes maison / école / triangle sont un bon départ). |
| A12 | profil-0, profil-1 | « Ajouter » (en haut à droite) et « + Ajouter un lieu » (en bas) **font la même chose**. | Un seul point d'entrée. |
| A13 | reglages-0, reglages-1 | La pastille « Annulable à tout moment » est en corps ~8 px (déjà signalé dans `initialisation-technique.md`, illisible et intraduisible). L'encart déplié (reglages-1) **perd** la mention de l'essai gratuit et de l'annulation. | À revoir dans le ticket Premium. |
| A14 | reglages-4 | Le bouton « Déconnexion » est un **gros bouton plein bleu**, aussi mis en avant qu'une action principale ; l'e-mail ressemble à un champ modifiable. | Déconnexion en style secondaire ; e-mail clairement en lecture seule (§6). |
| A15 | reglages-3 | « Enregistrer » est actif **sans aucune modification**. | Grisé tant que rien n'a changé (`onboarding.md` RG2). |
| A16 | reglages-5 | Les noms de langues sont traduits (« Anglais ») ; pas d'option « Langue du téléphone ». | Chaque langue dans **sa propre langue** (« Français », « English ») pour qu'on la retrouve même si l'app s'affiche dans une langue inconnue ; option « Langue du téléphone » [Décidé, `onboarding.md` §6.10]. |

### 4B. Contradictions avec des décisions déjà prises

| # | Maquette | Décision en vigueur | Source | Traitement proposé |
|---|---|---|---|---|
| B1 | feed-5 : orange après **18 min** (« Parti(e) il y a 18 min ») | Orange seulement après **2 h** sans update en dehors d'un lieu de confiance ; rouge après 4 h. Avant 2 h : **vert** (« parti, tout va bien »). | `update-et-escalade-statut.md` ; `data-model.md` §3 | La maquette montre un état d'exemple ; **je garde la règle** (Question 11 pour confirmation). |
| B2 | feed-2 : écran « Pas encore de Miff » **avec** barre d'onglets | Sans Miff : **écran de choix plein écran, sans barre d'onglets** ; le Feed vide est inatteignable. | `onboarding.md` Q13, §6.2 | Retirer l'état de la maquette (ou le garder comme filet de sécurité sans phrase de sécurité). |
| B3 | feed-0, -1, -2 : « Tout le monde est en sécurité » alors qu'il n'y a **personne** ou **aucune nouvelle** | Ne jamais afficher « tout va bien » sans statuts connus ; dire combien ont donné des nouvelles (« 3 sur 4 »). | `miff-questions-pm.md` QM12 ; `miff.md` §7 F3, F4 | Question 12 (libellés). |
| B4 | feed-0 : bouton « Localiser » (icône de lieu) | **Aucune coordonnée d'une personne n'est stockée** ; le ticket Feed doit définir ce que fait « Localiser ». | `data-model.md` §1 ; QM11 ; `miff.md` §7 F1 | Question 13. |
| B5 | reglages-5 : 6 langues ; reglages-0 : ligne Notifications ; Abonnement / encart Premium | Français + anglais seulement ; Notifications **masquée** tant que rien n'est envoyé ; Abonnement et bandeau Premium **masqués** en bêta, aucun paywall depuis les Réglages. | `initialisation-technique.md` ; `onboarding.md` §6.9 (Q4, Q5, Q12) | Questions 4, 6, 7. |
| B6 | Tous les textes (« Débloquez », « votre compte », « C'est vous », « Renseignez ») | **Tutoiement** partout. | `authentification.md` Q2 | `design-system` réécrit les textes. |
| B7 | reglages-0/1 : essai gratuit 7 j, prix 4,99 / 6,99 € | Le seul écran de ce type livré en bêta est un écran provisoire **sans prix ni essai**. Pas de paywall payant avant validation serveur. | `onboarding.md` §6.12 ; `data-model.md` §6 | Questions 7 et 8. |
| B8 | reglages-3 : aucune mention de visibilité du numéro | Mention **obligatoire** : « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » (décision N1) | `onboarding-decisions-pm.md` N1 | À ajouter sous le champ. |
| B9 | reglages-14 : « 3 caractères minimum » | **1 à 50** caractères pour prénom et nom (décision Q6). | `onboarding-decisions-pm.md` Q6 | Message « 50 caractères maximum. » ; plus de minimum de 3. |
| B10 | reglages-12 : texte de suppression de compte | Texte d'AUTH-5 : liste des Miffs créées, ré-authentification, rappel que l'abonnement du magasin **n'est pas résilié**, ton calme. | `authentification.md` §7.7 ; `onboarding.md` §4 #17 | Texte de la maquette abandonné (comme déjà décidé). |
| B11 | reglages-4/10/11 : changement de mot de passe | **Différé** : « Mot de passe oublié » couvre le besoin ; aucune ligne pour un compte Apple ou Google. | `onboarding.md` §6.9, Q12 | Question 5. |
| B12 | Profil et Feed : « Mes Miffs » absent de Réglages ; pastille de Miff = sélecteur | QM13 : l'écran de la Miff s'ouvre par la **pastille de Miff du Feed** et par **« Mes Miffs » dans Réglages**. | `miff-questions-pm.md` QM13 | Les maquettes proposent un autre chemin (carte de Miff du Profil) : Question 2. |
| B13 | profil-0 : adresses complètes des lieux ; « Ajouter » visible | La liste des lieux de l'écran de la Miff affiche **le nom seul** ; seul le **créateur** ajoute ou modifie un lieu. | `design/screens/miff-ecran-miff.md` ; `onboarding.md` §6.8 | Question 3. |
| B14 | profil-1 : « Créer une nouvelle Miff » pour tous | Gratuit = **1 Miff** (règles Firestore) ; le paywall de la 2e Miff est du ressort du ticket Premium. | `miff-questions-pm.md` QM7 | Question 2. |
| B15 | reglages-9 : « vous perdrez … les Miff et lieux créés dans le cadre de l'abonnement » | À la fin du Premium, les Miffs **créées** au-delà de la limite gratuite (les dernières ajoutées) sont **supprimées** et leurs membres perdent leur cercle (décision du 2026-10-24) ; préavis et choix de la Miff conservée restent à définir. | `data-model.md` §6 ; `miff.md` R9 | Question 9. |

### 4C. Ce qui n'est pas dans les maquettes et doit y être (liste pour `design-system`)

1. **Confidentialité** (déjà décidée) : interrupteur statistiques et plantages, état de la localisation et des notifications avec « Ouvrir les réglages du téléphone », liens Conditions et Politique, « Nous contacter ».
2. **Confirmation de déconnexion** (« Te déconnecter ? Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). ») ; **dialogue d'abandon** d'une modification de « Mes informations » [Proposé].
3. **Choix et recadrage de la photo** de profil (comme l'onboarding).
4. **États du Feed manquants** : partage désactivé (`PARTAGE_DESACTIVE`), « localisation limitée », statut inconnu / « Partage de ton statut en cours… », hors connexion, chargement, carte « moi » (F6 de `miff.md`), carte sans photo, **batterie inconnue**, photo expirée.
5. **Écran de l'abonnement pour un premium accordé à la main** (pas de prochain paiement) ; **« Restaurer les achats »** (exigé par Apple pour les achats intégrés [À vérifier]) ; passage par la gestion d'abonnement **du magasin**.
6. **Écrans cibles de la cloche, du bouton central et de l'avatar** (non dessinés ; Questions 1 et 15).
7. **Variante iOS de la ligne « Langue »** (ouvre les réglages de l'iPhone, `onboarding.md` Q11) : la liste de reglages-5 est celle d'Android.
8. États **vide**, **chargement**, **hors connexion** des Réglages et du Profil.

### 4D. Ton et principes transverses (`CLAUDE.md`)

- Les **bandeaux rouges** pleine largeur (« zone à risque », « Plus de nouvelles ») sont le contraire d'un ton non anxiogène s'ils sont posés sur un écran qu'on ouvre tous les jours. Les trois niveaux de couleur restent utiles (déjà dans le design system) ; ce qui change : la **formulation** (informer, ne pas alarmer), l'**intensité** (le rouge seulement après 4 h) et l'**action proposée** (« Envoyer un message », jamais « Appeler en urgence »).
- « Tout le monde est en sécurité » **promet** une sécurité que l'app ne peut pas garantir ; « Tout va bien » ou « Tout le monde a donné des nouvelles » sont plus honnêtes. Question 12.
- La permission de localisation et le consentement restent ceux du ticket Miff : rien dans ce ticket ne demande une position.

---

## 5. Ce qui est faisable maintenant (sans serveur, sans Cloud Functions)

**État du dépôt à la date de rédaction** : sur `main`, Android a le socle, l'authentification (AUTH-1), le splash et le carrousel ; **ONB-2 (profil, téléphone, routage), les tickets Miff, Lieux, Tracking, Update, Premium et Notifications ne sont pas livrés** ; `data-model.md` indique que les règles d'**ONB-B (téléphone)** et de **MIFF-0** sont écrites mais **non déployées** (à confirmer par le PM). Aucune Cloud Function (plan Spark).

### 5.1 Matrice

| Fonction (maquette) | Faisable maintenant ? | Dépend de | En attendant : coquille / état vide honnête |
|---|---|---|---|
| **Barre d'onglets** (3 destinations) | **Oui** | ONB-2 (routage) | Onglet Feed provisoire ; bouton central selon Question 1 |
| **Onglet Profil : en-tête** (photo ou initiales, nom, « C'est toi », engrenage) | **Oui** | ONB-2 | Sections « Mes Miffs » et « Lieux » **absentes** (pas de contenu inventé) |
| **Réglages : liste, Mes informations, Langue** | **Oui** | ONB-2, ONB-B déployé (pour écrire le numéro) | Lignes Notifications et Abonnement masquées |
| **Mon compte** : e-mail, déconnexion | **Oui** | AUTH-1 (livré) | « Supprimer mon compte » masquée jusqu'à AUTH-5 (Question 5 et §7) |
| **Mon compte** : mot de passe | **Oui, simplement** (e-mail de réinitialisation) ; **3 champs : faisable aussi** (changement de mot de passe après ré-authentification, sans coût [À vérifier]) | AUTH-2 pour la version e-mail | Question 5 |
| **Confidentialité** (statistiques, états des permissions, liens, contact) | **Oui** | AUTH-B (livré), AUTH-W (pages web) | Texte « Les notifications arrivent bientôt » pour leur état |
| **Feed : en-tête, sélecteur, cartes de proches** (photo, prénom), « Ajouter un proche » | **Oui, après les écrans Miff** | **MIFF-1** (créer / rejoindre, liste des membres) | Carte de chaque proche avec le badge neutre « Pas encore de nouvelles » |
| **Feed : « Message » et « Appeler »** | **Oui** : on ouvre l'application Téléphone / Messages du système avec le numéro (aucun appel ni SMS envoyé par Miff, aucune permission « contacts ») | MIFF-1 ; ONB-B déployé ; ONB-2 (numéro saisi) | Boutons masqués pour un proche sans numéro (comptes antérieurs à ONB-B) |
| **Feed : calcul des niveaux** (vert / orange / rouge, partage désactivé, localisation limitée) et rendu du bandeau | **Oui**, c'est de la logique locale (aucun serveur : `data-model.md` §3) | MIFF-1 | Testable sur données de test (QA, émulateur) ; **montre « Pas encore de nouvelles » à tout le monde tant que personne n'écrit de statut** |
| **Feed : statuts réels** (« À la maison », « A quitté la maison », « Plus de nouvelles ») | **Non, pas encore** | **Update** (mise à jour manuelle) et/ou **Tracking** (géofencing) + **Lieux** (nom et catégorie du lieu) | État neutre honnête (§6.4) |
| **Feed : « À la maison / À l'école »** (catégorie du lieu : icône maison, école) | **Non** | **Ticket Lieux** : la catégorie de lieu a été décidée le 2026-10-16 mais **n'est pas encore dans `backend/data-model.md`** (§2.9 : pas de champ `category`) | Nom du lieu seul |
| **Feed : batterie** | **Oui côté lecture** (`batteryPct` existe dans le statut) ; **écriture** par l'appareil du proche : ticket qui écrit les statuts | Update / Tracking | Pastille masquée quand la valeur est absente |
| **Feed : photo d'update** | Lecture **oui** (`photos/{uid}`, 24 h) ; **création** : ticket Update | Update | Rien n'est affiché (pas de bloc vide) |
| **Feed : « Localiser »** | **Non en l'état** (aucune coordonnée) ; **« Voir le lieu »** oui avec Lieux (coordonnées **du lieu**, pas de la personne) | Lieux ; Question 13 | Bouton absent |
| **Feed : cloche** | **Non** (aucune source de données : pas de push, pas d'historique par conception) | Ticket Notifications (Blaze) | Masquée (Question 15) |
| **Profil : carrousel de Miffs** (nom, nombre de membres, avatars) | **Oui, après MIFF-1** | MIFF-1 | Masqué avant |
| **Profil : lieux de la Miff** (liste en lecture) | **Oui, après Lieux** (lecture des `places`) ; création : ticket Lieux | Lieux | « Pas encore de lieu pour cette Miff. » |
| **Abonnement : écran de gestion, essai, changement, arrêt** | **Non** : aucun achat n'est validable sans serveur ; **c'est le magasin qui gère** arrêt, changement et reprise [À vérifier] | Ticket Premium + Blaze | Coquille **en lecture seule** pour un premium accordé à la main (ABO-1, Question 7) |
| **Notifications : interrupteurs** | **Non** : rien ne peut être envoyé | Ticket Notifications + Blaze | Ligne masquée ; état de la permission du système dans Confidentialité |

### 5.2 Ordre conseillé pour que le Feed devienne vivant le plus tôt possible

1. ONB-2 (profil, téléphone) → ONB-3a / ONB-3b (cette spec) → MIFF-1 → **FEED-1** (cercle sans statuts).
2. **Ticket Update (mise à jour manuelle) avant le Tracking** : « Je vais bien » (+ photo facultative) est le geste le plus simple, il alimente tout de suite le Feed (statut `lastUpdateAt`, texte, photo, batterie) et ne demande ni permission d'arrière-plan, ni Lieux, ni fabricant Android capricieux. C'est aussi lui qui donne un rôle au **bouton central** (Question 1). [Proposé]
3. **FEED-2** (niveaux, bandeau) livré en même temps que le premier ticket qui écrit des statuts, pour pouvoir être recetté par le PM sur un vrai téléphone.
4. Tracking + Lieux (géofencing) : badges « À la maison », « A quitté la maison à 16 h 40 », Profil complet (PRO-1).

### 5.3 Limites techniques à connaître (pas des décisions)
- **Sans serveur, les niveaux orange / rouge ne se calculent que quand l'app d'un proche est ouverte** (`data-model.md` §3 et §13) : aucun message n'arrive tout seul.
- **Le Feed lit les statuts seulement après que la personne a publié son propre statut** (réciprocité, `data-model.md` §8 « Feed ») : tant que ce n'est pas fait, l'écran affiche un état neutre (« Partage de ton statut en cours… »), **jamais une erreur**.
- **Coût** : l'ouverture du Feed lit ~15 à 20 documents pour une Miff de 4 (estimation `data-model.md` §12) ; le premier plafond du plan gratuit est le nombre de lectures (~650 utilisateurs actifs par jour). Chaque PR Feed **mesure** ses lectures.
- **Hors connexion**, le cache peut afficher un « vert » ancien (`miff.md` §7 F5) : il doit être **daté**.

---

## 6. Règles de comportement proposées (en attente des réponses)

Ces règles reprennent ce qui est décidé et **proposent** le reste ; chaque point marqué [Proposé] dépend d'une question de §10.

### 6.1 Feed : en-tête et sélecteur
- Logo Miff au centre. Avatar (gauche) et cloche (droite) : **voir Question 15** (recommandation : retirer la cloche en v1 ; l'avatar mène au Profil).
- **Sélecteur** : [Décidé, F8] affiché comme sélecteur seulement s'il y a plusieurs Miffs (Premium) ; avec une seule Miff, c'est une **pastille non cliquable** avec le nom (Question 2 pour l'entrée vers l'écran de la Miff). Entrées « + Rejoindre / + Créer une Miff » seulement pour un compte Premium tant que le ticket Premium n'a pas défini le paywall de la 2e Miff.
- La Miff « active » est une préférence **locale**, effacée à la déconnexion [Décidé, F8].

### 6.2 Feed : bandeau de synthèse [Proposé, Questions 11 et 12]
- **Un seul message à la fois**, choisi dans cet ordre : alerte la plus avancée d'abord (rouge, puis orange), sinon synthèse neutre ou positive.
- **Synthèse** : « Tout va bien » **seulement** si au moins un proche a un statut connu **et** que tous les statuts connus sont verts ; « {n} sur {m} ont donné des nouvelles » sinon [Décidé, QM12] ; « Pas encore de nouvelles » si aucun statut connu. Jamais de phrase de sécurité avec zéro proche (« Tu es seul(e) dans cette Miff pour l'instant. »).
- **Alertes** : si plusieurs, pastilles de pagination ; chaque page est aussi accessible **sans balayage** (boutons, ou liste lue au lecteur d'écran) ; ordre : plus ancien silence d'abord.
- **Repère de temps** : un seul (A7).
- **Pas de compte à rebours qui défile** : l'heure est absolue (« à 16 h 02 ») pour les paliers orange et rouge, relative (« il y a 12 min ») pour le vert.

### 6.3 Feed : carte de proche
- Avatar (photo de profil ou initiales) avec anneau de la couleur du niveau (`status/safe`, `warning`, `danger` ; jamais la couleur seule), prénom, badge d'état (texte + icône), repère de temps, batterie (Question 14), photo d'update (si `hasPhoto` et non expirée, avec son heure), boutons.
- **Ma propre carte** : affichée, sans « Message » ni « Appeler », avec l'état de mon partage (`miff.md` §7 F6) [Proposé : en premier de liste].
- Boutons : « Message » et « Appeler » ouvrent l'application du système ; **masqués** si le proche n'a pas de numéro. « Localiser » : Question 13. Chaque bouton a un nom lisible par le lecteur d'écran (« Appeler Thomas »).
- **Ordre** : stable (moi, puis ordre d'arrivée dans la Miff) [Proposé] : une liste qui se réordonne selon l'urgence stresse et déplace les boutons sous le doigt.

### 6.4 Feed : états neutres honnêtes (textes proposés, tutoiement, à valider)
| Situation | Bandeau | Carte |
|---|---|---|
| Miff avec proches, aucun statut encore | « Pas encore de nouvelles » / « Dès que tes proches donneront des nouvelles, tu les verras ici. » | Badge « Pas encore de nouvelles » (couleur neutre) |
| Seul dans la Miff | « Tu es seul(e) dans cette Miff pour l'instant. » | Bouton « Ajouter un proche » |
| Proche qui a arrêté son partage | Compté « sans nouvelles » **sans alerte** | Badge « Partage désactivé » (neutre, jamais alarmiste, `data-model.md` §3) |
| Proche avec « localisation limitée » | Aucun effet sur le bandeau | Petit badge doux « Localisation limitée » + suggestion de mise à jour manuelle |
| Hors connexion | « Dernières informations à 16 h 02 » | États conservés, **datés** |
| Chargement | Squelette (pas d'animation si « réduire les animations ») | Idem |
| Statut en cours de publication | « Partage de ton statut en cours… » | Idem, jamais une erreur |

### 6.5 Onglet Profil
- En-tête [Décidé, `onboarding.md` §6.8] : photo ou initiales, « Prénom Nom », « C'est toi », engrenage ; **pas de « Famille » par défaut** (§4 #22 d'`onboarding.md`).
- Carrousel « Mes Miffs » [Proposé, Question 2] : une carte par Miff (nom, nombre de membres, avatars) qui **ouvre l'écran de la Miff** (MIFF-1) ; carte « Créer une nouvelle Miff » **seulement pour un Premium**.
- « Lieux de la Miff » : liste en lecture de la Miff affichée ; « Ajouter un lieu » visible **seulement pour le créateur** [Décidé, `onboarding.md` §6.8] ; adresse : Question 3.

### 6.6 Réglages
- Liste [Proposé] : **Mes informations, Mon compte, Langue, Confidentialité** (décidé) ; **Abonnement** seulement pour un compte Premium (Question 7) ; **Notifications** masquée (Question 6) ; **Mes Miffs** : voir Question 2.
- **Mes informations** : mêmes champs et règles que l'onboarding (prénom, nom 1 à 50 ; téléphone obligatoire, jamais vidé ; mention de visibilité N1 ; photo facultative carrée ≤ 20 000 octets sans métadonnées). Le **pays n'est pas stocké** (il se déduit de l'indicatif, `data-model.md` §2.1) : après enregistrement, le pays affiché pour un indicatif partagé (+1, +7) est le plus courant ; sans conséquence pour le numéro. Écriture : un lot `users/{uid}` + `members/{uid}` de chaque Miff confirmée (au plus 15 fiches par lot).
- **Mon compte** : e-mail **en lecture seule** [Proposé : pas de changement d'e-mail en v1] ; compte Google : « Connecté avec Google », jamais de champ mot de passe ; compte Apple : adresse relais affichée telle quelle ; déconnexion avec confirmation [Décidé] ; suppression : flux AUTH-5 [Décidé].
- **Langue** : FR, EN, « Langue du téléphone » ; Android : effet immédiat sans bouton « Enregistrer » ; iOS : ouvre les réglages de l'iPhone [Décidé, Q11].
- **Confidentialité** [Décidé, §6.9].
- **Abonnement** : voir ABO-1 (§7).

---

## 7. Découpage en tickets (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket (`CLAUDE.md`). Tailles relatives, sans durée. **Avant chaque développement : export PNG fourni (fait pour ces écrans le 2026-10-30), écarts de §4 listés et justifiés dans la PR** (règle du 2026-10-28). `ONB-3` et `ONB-7` d'`onboarding.md` sont **remplacés** par les sous-tickets ci-dessous (même périmètre, redécoupé et adapté aux maquettes).

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **FPR-0** | **Décisions, maquettes corrigées** (PM, `design-system`) | Réponses aux 15 questions ; maquettes corrigées et complétées dans « Miff - Claude » (§4A, §4C) ; exports PNG rangés dans `design/exports/<flux>/` | S à M | Validation de ce document |
| **ONB-3a** | **Android : barre d'onglets, onglet Profil (en-tête), liste des Réglages, Mes informations, Langue** | §6.5 (en-tête), §6.6 | M | ONB-2, ONB-B **déployé**, FPR-0 |
| **ONB-3b** | **Android : Mon compte et Confidentialité** | §6.6 | M | ONB-3a, AUTH-1 (livré), AUTH-B (livré), AUTH-W (liens) ; AUTH-2 si Question 5 retient le bouton « Changer mon mot de passe » ; AUTH-5 pour la ligne de suppression |
| **FEED-1** | **Android : Feed « cercle »** (en-tête, sélecteur, cartes sans statut, « Ajouter un proche », Message / Appeler, états neutres) | §6.1, §6.3, §6.4 | M à L | ONB-3a, **MIFF-1** |
| **UPDATE-1** *(ticket à écrire)* | Mise à jour manuelle : bouton central, « Je vais bien », photo facultative | Voir Question 1 | M | FEED-1 |
| **FEED-2** | **Android : statuts** (niveaux, bandeau, pagination, batterie, photo d'update, partage désactivé, localisation limitée, hors connexion) | §6.2, §6.3, §6.4 | M à L | FEED-1 ; Questions 10 à 14 ; recettable sur téléphone **avec** UPDATE-1 ou Tracking |
| **PRO-1** | **Android : Profil complet** (carrousel de Miffs, lieux de la Miff en lecture) | §6.5 | M | ONB-3a, MIFF-1, ticket Lieux ; Questions 2 et 3 |
| **ABO-1** | **Android : coquille Abonnement** (lecture seule, premium accordé à la main) | Optionnel (Question 7) | S | ONB-3a ; `entitlements/{uid}` |
| **ONB-7a / ONB-7b / FEED-3 / FEED-4 / PRO-2 / ABO-2** | **iOS** : mêmes livrables que ONB-3a, ONB-3b, FEED-1, FEED-2, PRO-1, ABO-1 | Spécificités §8 | Même taille | Équivalents Android, INIT-4, AUTH-3 |
| *Tickets voisins à écrire* | **Lieux**, **Update**, **Tracking**, **Premium** (achat, essai, tarifs, validation), **Notifications** (cloche, interrupteurs) | Hors de ce document | — | Voir §2 |

### 7.1 Critères d'acceptation

Les critères valent pour Android puis iOS sauf mention. Les critères existants d'`onboarding.md` (TB1 à TB5, PT1, PT2, RG1 à RG8, X1 à X7) restent valables **sauf là où ils sont modifiés ci-dessous**.

#### FPR-0 (prérequis)
- [ ] **FPR0-1** Le PM a répondu aux 15 questions de §10 ; les réponses sont reportées dans un tableau « Décisions du PM » de ce document.
- [ ] **FPR0-2** `design-system` a reconstruit, dans « Miff - Claude » uniquement (jamais dans le fichier original), les écrans corrigés et manquants de §4A et §4C, **au tutoiement**, sans photo de banque d'images.
- [ ] **FPR0-3** Les exports PNG correspondants sont dans `design/exports/<flux>/` avec un README de repérage.

#### ONB-3a (Android) : onglets, Profil, Mes informations, Langue
- [ ] **A1 (TB1 modifié)** La barre a trois destinations (Feed, bouton central, Profil), l'onglet actif a sa pastille, chaque onglet a une étiquette pour le lecteur d'écran ; **le rôle du bouton central est celui décidé à la Question 1**.
- [ ] **A2 (TB2 à TB4)** La barre n'est visible qu'une fois l'onboarding terminé ; elle est masquée avec le clavier ouvert et en plein écran ; l'état de chaque onglet est conservé ; le bouton « retour » sur un onglet racine quitte l'app ; l'ordre s'inverse en droite-à-gauche.
- [ ] **A3 (PT1 modifié)** L'en-tête du Profil affiche la photo (ou les initiales), « Prénom Nom », « C'est toi » ; l'engrenage ouvre les Réglages ; **aucune donnée fictive** (« Famille Martin », « Lieux de la Miff ») n'apparaît tant que MIFF-1 / Lieux ne sont pas livrés.
- [ ] **A4 (RG1 modifié)** La liste des Réglages contient **Mes informations, Mon compte, Langue, Confidentialité** ; elle ne contient ni « Notifications » ni encart Premium (sauf décision contraire aux Questions 6 et 7).
- [ ] **A5** « Mes informations » : le champ téléphone est libellé « Téléphone » ; les compteurs affichent le nombre **réel** de caractères ; prénom et nom : 1 à 50 caractères après suppression des espaces (aucun minimum de 3) ; « Enregistrer » est grisé tant que rien n'a changé ou qu'une valeur est invalide.
- [ ] **A6** La mention « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » est visible sous le champ téléphone ; le numéro ne peut pas être vidé ; saisie nationale sans zéro initial, stockage E.164, message calme « Ce numéro ne semble pas correct. » (mêmes règles que l'onboarding, TEL3 et TEL4).
- [ ] **A7 (RG2)** L'enregistrement envoie **un seul lot** : `users/{uid}` + `members/{uid}` de chaque Miff **confirmée** (au plus 15 fiches par lot) ; un échec partiel est repris sans message alarmant ; hors connexion : message neutre, **aucune écriture en file d'attente**.
- [ ] **A8** Photo : mêmes règles qu'à l'onboarding (recadrage carré, ≤ 20 000 octets, sans métadonnées, suppression possible, aucune permission de photothèque).
- [ ] **A9 (RG5 modifié)** Langue : « Français », « English » (chacune dans sa langue) et « Langue du téléphone » ; **effet immédiat**, sans bouton « Enregistrer » ; tous les textes, les liens légaux et l'e-mail de réinitialisation suivent la langue ; aucune autre langue n'est proposée.
- [ ] **A10** Tous les textes sont en français et en anglais (clés `tabbar_`, `profile_`, `settings_` identiques sur les deux plateformes, parité vérifiée par la CI), au tutoiement ; aucune valeur de design en dur ; thème clair.
- [ ] **A11** Aucun numéro, nom ni photo dans un journal, un événement d'analyse ou un rapport de plantage.

#### ONB-3b (Android) : Mon compte, Confidentialité
- [ ] **B1** L'e-mail est affiché en **lecture seule** (adresse relais Apple telle quelle) ; un compte Google affiche « Connecté avec Google » et **aucune** ligne de mot de passe ; l'e-mail n'est copié ni dans Firestore, ni dans un événement, ni dans un journal (RG8).
- [ ] **B2 (RG3)** « Se déconnecter » (style secondaire) affiche « Te déconnecter ? Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). » avec deux boutons de poids égal, puis déclenche la procédure d'AUTH-1 (écoutes arrêtées, cache purgé, retour à la connexion sans carrousel).
- [ ] **B3** Mot de passe : comportement **décidé à la Question 5** (par défaut : un bouton « Changer mon mot de passe » qui envoie l'e-mail de réinitialisation d'AUTH-2, masqué pour un compte Google ou Apple ; messages bienveillants, tutoiement).
- [ ] **B4 (RG4)** « Supprimer mon compte » n'apparaît **que lorsque AUTH-5 est livré** et ouvre son flux ; les textes de reglages-12 (« irrécupérables », « instantanément ») n'existent nulle part dans l'app.
- [ ] **B5 (RG6)** « Confidentialité » contient l'interrupteur statistiques et plantages (effet immédiat), l'état de la localisation et celui des notifications avec « Ouvrir les réglages du téléphone », les liens Conditions et Politique (pages web de la langue de l'app) et « Nous contacter » (contact provisoire de `CLAUDE.md`).
- [ ] **B6** L'état des notifications dit honnêtement qu'elles arrivent bientôt tant que rien n'est envoyé.
- [ ] **B7** Hors connexion : les actions d'écriture sont désactivées avec une mention calme ; la lecture reste possible.

#### FEED-1 (Android) : cercle sans statuts
- [ ] **C1** Un compte **sans Miff** n'atteint jamais le Feed (écran de choix plein écran, sans barre d'onglets) ; si l'état est atteint par accident, il affiche « Pas encore de Miff » avec les deux boutons et **aucune phrase de sécurité**.
- [ ] **C2** Avec une seule Miff, le nom est affiché sans flèche de sélecteur ; avec plusieurs (Premium), la liste s'ouvre ; les entrées « Rejoindre / Créer une Miff » n'existent que pour un Premium (Question 2).
- [ ] **C3** Chaque proche a une carte (photo ou initiales, prénom) ; sans statut : badge neutre « Pas encore de nouvelles » ; **aucune phrase « tout le monde est en sécurité »** n'est affichée tant qu'aucun statut n'est connu (QM12).
- [ ] **C4** « Ajouter un proche » ouvre le partage du code (MIFF-1) ; à 15 membres ou plus, il est remplacé par « Cette Miff est bien remplie ! ».
- [ ] **C5** « Message » et « Appeler » ouvrent l'application Messages / Téléphone du système avec le numéro **préparé** (aucun appel ni SMS n'est lancé par Miff, aucune permission « appel », « SMS » ou « contacts » dans le manifeste) ; ils n'apparaissent pas pour un proche sans numéro ni sur sa propre carte.
- [ ] **C6** L'écouteur de statuts n'est attaché qu'après publication de son propre statut ; en attendant, état neutre « Partage de ton statut en cours… » ; un refus de lecture n'est jamais présenté comme une erreur.
- [ ] **C7** Perte d'accès (retrait, Miff supprimée) : message unique du ticket Miff, nettoyage, bascule vers une autre Miff ou l'écran de choix ; **mode avion ≠ accès perdu**.
- [ ] **C8** Hors connexion : mention datée (« Dernières informations à … »), jamais de « tout va bien » sans date ; boutons d'écriture désactivés.
- [ ] **C9** Le Feed ne contient ni cloche, ni « Localiser », ni zone à risque tant que les Questions 10, 13 et 15 ne sont pas tranchées autrement.
- [ ] **C10** Les lectures de l'ouverture du Feed sont mesurées et reportées dans la PR (estimation de départ de `data-model.md` §12 : 15 à 20 lectures pour une Miff de 4 membres) ; aucune interrogation périodique du serveur.
- [ ] **C11** Textes `feed_` en français et en anglais, tutoiement, formulations sans accord de genre, parité de clés ; l'état de chaque carte est porté par un texte et une icône, **pas par la couleur seule** ; contraste AA des badges.

#### FEED-2 (Android) : statuts
- [ ] **D1** Le niveau d'un proche est calculé **sur le téléphone** selon `data-model.md` §3 : partage désactivé, statut absent, `in_trusted` (vert), `unknown` (neutre), `out` (vert avant 2 h, orange de 2 h à 4 h, rouge au-delà, repère = dernier événement ou dernière update), `in_risk` (selon Question 10) ; constantes 2 h / 4 h partagées avec iOS ; **tests unitaires de toutes les branches** (dont les limites 1 h 59, 2 h 00, 3 h 59, 4 h 00).
- [ ] **D2** Le niveau se **réévalue sans lecture serveur** (minuteur local) quand l'écran reste ouvert.
- [ ] **D3** Le badge de chaque niveau a son texte, son icône et sa couleur ; les formulations sont celles validées (Questions 11 et 12), sans accord de genre, sans « Plus de nouvelles » définitif, sans « zone à risque » si Question 10 recommande de ne pas la livrer.
- [ ] **D4** Le bandeau suit la règle de §6.2 (alerte la plus avancée, synthèse « {n} sur {m} », « Pas encore de nouvelles ») ; la pagination s'affiche **seulement** s'il y a plusieurs alertes, et reste utilisable sans balayage.
- [ ] **D5** Un proche orange ou rouge ne déclenche **aucune** notification (aucune n'est possible) ; le bandeau propose seulement « Envoyer un message » (ouvre l'application Messages).
- [ ] **D6** Batterie : affichée selon la Question 14 ; masquée si absente.
- [ ] **D7** Photo d'update : chargée à la demande si `hasPhoto` ; expirée ou refusée = **aucune photo, aucun message d'erreur** ; affichée avec son heure.
- [ ] **D8** « Localisation limitée » : badge doux, jamais alarmiste ; aucun effet sur le bandeau.
- [ ] **D9** Le cache hors connexion ne présente jamais un état vert sans mention de date (C8).
- [ ] **D10** Aucune coordonnée, adresse ni nom de lieu n'est écrit dans un journal, un événement d'analyse ou un message de partage.
- [ ] **D11** Recette : jeu de données de test documenté (un statut par cas) ; **recette PM sur téléphone** possible seulement quand UPDATE-1 ou Tracking écrit de vrais statuts.

#### PRO-1 (Android) : Profil complet
- [ ] **E1** Une carte par Miff (nom, nombre de membres, avatars) ouvre l'écran de la Miff ; le nom de Miff n'est jamais « Famille » par défaut.
- [ ] **E2** La carte « Créer une nouvelle Miff » n'est affichée qu'à un compte Premium (Question 2).
- [ ] **E3** « Lieux de la Miff » : liste en lecture ; « Ajouter un lieu » visible **uniquement** du créateur et en un seul exemplaire ; l'affichage de l'adresse suit la Question 3 ; liste vide : « Pas encore de lieu pour cette Miff. »
- [ ] **E4** Le nombre de membres et les avatars sont lus depuis la même source que le Feed (pas de seconde lecture de chaque Miff à chaque ouverture) ; lectures mesurées.

#### ABO-1 (Android) : coquille Abonnement (si Question 7 = oui)
- [ ] **F1** La ligne « Abonnement » n'apparaît que si `entitlements/{uid}` est actif et non expiré ; sinon **aucune** trace d'abonnement, de prix ni d'essai dans les Réglages.
- [ ] **F2** L'écran affiche « Premium actif jusqu'au {date en format long} » et, pour une source « manuelle », « Accordé par l'équipe Miff » ; **aucun** bouton d'achat, d'essai, de changement ni d'arrêt ; aucun prix.
- [ ] **F3** Aucune écriture de `entitlements` (interdite au client) ; aucun appel à Google Play Billing ni à l'App Store.
- [ ] **F4** Dates au format long de la langue de l'app (jamais « 02/18/12 »).

### 7.2 Critères propres à iOS (ONB-7a/b, FEED-3/4, PRO-2, ABO-2)
- [ ] **I1** Mêmes critères que l'équivalent Android, vérifiés par TestFlight sur l'iPhone 11 Pro (aucun test local possible).
- [ ] **I2** La ligne « Langue » ouvre les réglages de l'iPhone pour Miff et affiche la langue en cours (Q11) ; les deux langues sont déclarées dans l'app.
- [ ] **I3** « Message » et « Appeler » utilisent les liens système (`sms:`, `tel:`) ; aucune clé de permission « contacts » ; VoiceOver annonce « Appeler Thomas » ; zones tactiles d'au moins 44 pt.
- [ ] **I4** Pas de textes d'usage ajoutés sans usage (la clé de localisation « toujours » n'apparaît qu'avec ONB-8).

---

## 8. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Barre d'onglets | Barre de navigation inférieure ; bouton central accentué ; « retour » sur onglet racine : quitter l'app | Barre personnalisée, zone sûre du bas ; geste de bord d'écran |
| Langue | Choix dans l'app, effet immédiat (+ « Langues des applications » d'Android 13) : **la liste de reglages-5 convient** | La ligne ouvre les **réglages de l'iPhone** (décision Q11) : **la maquette doit avoir une variante iOS** |
| Message / Appeler | Intent « composer le numéro » (pas d'appel direct, aucune permission) et « envoyer un message » | Liens `tel:` et `sms:` ; iOS demande une confirmation avant l'appel |
| Photo de profil | Sélecteur de photos du système ; application photo du système | Sélecteur du système ; appareil photo avec texte d'usage |
| Abonnement | Gestion dans Google Play (lien vers la page d'abonnement) [À vérifier] | Gestion dans les réglages de l'App Store ; bouton « Restaurer les achats » exigé [À vérifier] |
| Notifications (état) | Android 13+ : permission au runtime ; avant : aucune fenêtre | Fenêtre unique ; autorisation provisoire possible |
| Tests | OPPO Find X5 Lite (Android 13) en local ; ColorOS peut tuer l'app en arrière-plan | iPhone 11 Pro via TestFlight uniquement |
| Zones tactiles | 48 dp | 44 pt |

---

## 9. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Ton anxiogène** : bandeaux rouges, « zone à risque », « Plus de nouvelles », durées qui s'allongent | Perte de confiance, abandon, contre-sens avec `CLAUDE.md` | Formulations informatives (§6.4), rouge après 4 h seulement, heures absolues, action douce, relecture à voix haute par le PM (comme PM8 de `miff.md`) |
| R2 | **Outil de contrôle** : une « zone à risque » définie par le créateur et signalée à tout le cercle peut servir à surveiller quelqu'un (cercle amoureux ou familial) ; règles anti-stalkerware des stores | Rejet de store, préjudice réel | Question 10 : ne pas livrer en v1 ; si livrée, la personne concernée doit **voir** les zones qui la concernent avant d'accepter (consentement éclairé), à rediscuter avec `release-manager` |
| R3 | **Promesse non tenue** : « Localiser » (position non stockée), notifications (aucun envoi), états de géofencing absents | Perte de confiance, motif de rejet | États neutres honnêtes ; « Voir le lieu » ; textes « bientôt » ; coquilles masquées |
| R4 | **Information périmée présentée comme actuelle** (cache hors connexion, batterie vieille de 6 h, photo de 20 h) | Fausse réassurance | Tout est **daté** (A7, A10, C8, D6, D7) |
| R5 | **Abonnement non conforme aux règles des stores** : arrêt, changement ou reprise faits dans l'app ; prix en dur ; absence de « Restaurer » ; prix non localisés [À vérifier] | Rejet de store, litige | Aucune UI d'achat avant le ticket Premium ; prix toujours lus depuis les stores |
| R6 | **Fin d'abonnement qui supprime des Miffs créées** : les proches perdent leur cercle sans y être pour rien | Peine, perte de confiance | Question 9 : préavis, choix de la Miff conservée, « Prévenir mes proches » (`miff.md` R9) |
| R7 | **Numéro de téléphone exposé** à tout porteur du code de la Miff (risque déjà assumé, R7 d'`onboarding.md`) ; ici, les boutons « Appeler / Message » rendent le numéro **plus visible dans l'usage** | Fuite de numéros | Mention de visibilité (A6) ; renouvellement du code ; rien de nouveau décidé ici |
| R8 | **Quotas Spark** : le Feed est le premier consommateur de lectures | Application « cassée » pour tous au plafond | Mesures dans chaque PR ; signal de bascule `data-model.md` §12 ; aucun polling |
| R9 | **Maquettes divergentes du code** (écarts non listés) | Développement qui invente | §4 est la liste des écarts ; chaque PR y renvoie |
| R10 | **Aucun test iOS local** (pas de Mac) | Cycle lent | Android d'abord, tests de logique partagés par cas |
| R11 | **Textes non relus par un juriste** (décision du 2026-10-23, risque assumé) pour la suppression de compte, l'abonnement, la confidentialité | Risque juridique assumé par le PM | Aucun texte ne prétend avoir été relu |

---

## 10. Questions pour le PM (15, par ordre d'importance pour le démarrage)

Chaque question donne **ma recommandation** et **l'impact** de la réponse. Je ne tranche aucune décision produit ou légale à ta place.

**Q1. Que fait le bouton rond central de la barre d'onglets ?**
- *Recommandation* : « Donner des nouvelles » (mise à jour manuelle : « Je vais bien », photo facultative), géré par un ticket **Update** écrit avant le Tracking. Tant qu'il n'existe pas, le bouton est **masqué** dans les builds pour testeurs externes (la barre passe à deux onglets) et visible mais désactivé en interne.
- *Impact* : sans réponse, ONB-3a ne peut pas fixer le critère A1 ; l'Update devient le premier moyen de remplir le Feed (§5.2).

**Q2. Comment ouvre-t-on l'écran d'une Miff, et que voit un compte gratuit ?** (QM13 disait : pastille du Feed + « Mes Miffs » dans Réglages ; les maquettes proposent le carrousel du Profil.)
- *Recommandation* : la **carte de Miff du Profil** ouvre l'écran de la Miff (pas de « Mes Miffs » dans Réglages) ; dans le Feed, avec une seule Miff, le nom est une pastille non cliquable ; la carte « Créer une nouvelle Miff » et les entrées « + Rejoindre / + Créer » du sélecteur n'apparaissent **que pour un Premium**, jusqu'à ce que le ticket Premium définisse le paywall de la 2e Miff (un compte gratuit est de toute façon refusé par les règles).
- *Impact* : change 3 maquettes (feed-3, profil-0/1) ; aucun impact serveur.

**Q3. Les adresses des lieux sont-elles visibles de tous les membres dans le Profil ?** (La maquette affiche l'adresse complète ; l'écran de la Miff prévoyait « nom seul » ; **les règles laissent déjà tous les membres lire l'adresse et les coordonnées**.)
- *Recommandation* : **nom + catégorie seulement** dans la liste ; adresse sur un écran de détail en lecture. L'interface ne protège pas d'un client modifié : la vraie protection est de dire clairement, avant d'entrer dans une Miff, que les lieux sont partagés.
- *Impact* : maquette profil-0 ; texte du consentement d'entrée à relire dans le ticket Miff.

**Q4. Langue : on confirme français et anglais seulement ?** (6 langues dans la maquette.)
- *Recommandation* : oui, FR + EN + « Langue du téléphone », chaque nom dans sa langue, effet immédiat sur Android (sans bouton « Enregistrer »), réglages de l'iPhone sur iOS. Ajouter une langue = relecture du ton, textes légaux, captures (`initialisation-technique.md`).
- *Impact* : retire 4 lignes de la maquette ; aucun code supplémentaire.

**Q5. Mon compte : mot de passe et e-mail.**
- *Recommandation* : **e-mail en lecture seule** (pas de changement d'e-mail en v1) ; **pas** de formulaire à 3 champs : un bouton « Changer mon mot de passe » qui envoie l'e-mail de réinitialisation déjà prévu (AUTH-2), masqué pour les comptes Google et Apple (« Connecté avec Google »). La ligne « Supprimer mon compte » n'apparaît que lorsque AUTH-5 est livré (obligatoire avant les stores).
- *Impact* : dépend d'AUTH-2 ; évite une ré-authentification et trois champs à tester. Si tu veux les 3 champs : faisable gratuitement, +1 PR de taille S.

**Q6. Réglages > Notifications : on affiche la ligne maintenant ?** Que veulent dire « classiques » et « intelligentes » ?
- *Recommandation* : **masquer la ligne** tant que rien n'est envoyé (décision Q12) ; l'état de la permission reste dans Confidentialité. Je ne peux pas définir « intelligentes » sans inventer : la seule trace est « Soyez notifié lors d'une situation anormale » (écran Premium) ; il faut que tu écrives : (a) ce que reçoit « classiques » (chaque arrivée / départ ? seulement les alertes ?), (b) ce que reçoit « intelligentes », (c) si « intelligentes » est Premium.
- *Impact* : bloque uniquement le futur ticket Notifications (Blaze).

**Q7. Abonnement dans les Réglages : coquille maintenant, ou rien avant le ticket Premium ?**
- *Recommandation* : **rien d'achetable** avant la validation serveur (`data-model.md` §6). Je propose **ABO-1** : une ligne « Abonnement » **uniquement pour un compte Premium accordé à la main**, en lecture seule (« Premium actif jusqu'au 12 janvier 2027 », sans prix, sans bouton). L'encart « Débloquez l'ensemble des fonctionnalités » n'apparaît pas.
- *Impact* : S ; permet de recetter le Premium manuel sur téléphone ; peut être supprimé sans conséquence.

**Q8. Offre et prix : à trancher au ticket Premium, mais quelques points peuvent déjà être cadrés.**
- (a) Le **nom** : « Miff+ » (dialogues et écran provisoire) ou « Premium » (specs et CLAUDE.md) ? (b) **Annuel à 4,99 €/mois « pour 1 an »** : prélevé en une fois (59,88 €, comme la maquette) ou chaque mois pendant 12 mois ? Que se passe-t-il après la 1re année ? (c) L'**essai de 7 jours** s'applique-t-il aux deux offres ? (d) « **Économisez 30 %** » : le vrai chiffre est 28,6 % ; recommandation : le calculer ou le retirer. (e) Les **prix affichés doivent venir des magasins** (devise, taxes) : ceux des maquettes ne sont que des exemples.
- *Recommandation* : garder un seul nom partout ; décider (b) et (c) avant de créer les produits dans les stores.
- *Impact* : fiches des stores, textes légaux, produits d'abonnement ; aucun impact sur ONB-3 ni FEED.

**Q9. Arrêter l'abonnement : que dit-on sur la suppression des Miffs ?** (Décision du 2026-10-24 : à la fin du Premium, les Miffs créées au-delà de la limite gratuite, les dernières ajoutées, sont supprimées.)
- *Recommandation* : à la fin de la **période déjà payée** (pas immédiatement), avec **préavis** et **choix de la Miff conservée**, texte explicite (« Ta Miff « X » sera supprimée le {date}, N proches perdront l'accès ») et lien « Prévenir mes proches ». Rappeler aussi que **c'est le magasin qui arrête l'abonnement** [À vérifier] : le bouton de l'app renvoie à la gestion d'abonnement du magasin.
- *Impact* : ticket Premium ; non bloquant pour cette PR.

**Q10. « Zone à risque » (Feed rouge, « Zones de danger » Premium) : on la livre en v1 ?**
- *Recommandation* : **non** (précision : elle est dans les specs comme type de lieu Premium, mais jamais définie). Raisons : qui la définit et que déclenche l'entrée n'est pas écrit (`lieux-de-confiance.md`) ; l'alerte à tout le cercle demande des notifications (Blaze) ; le texte « Thomas est dans une zone à risque » contredit le ton non anxiogène ; elle peut servir à surveiller un proche (R2). Si un statut `in_risk` existe un jour, l'afficher en **neutre** (« Thomas est à {nom du lieu} »).
- *Impact* : le Feed v1 n'a que vert, orange, rouge « pas de nouvelles », neutre ; le type `risk` reste dans le schéma sans interface ; à rediscuter dans le ticket Lieux.

**Q11. Paliers et ton des bandeaux orange et rouge.**
- *Recommandation* : garder les trois niveaux de couleur et **la règle de la spec** (vert avant 2 h hors d'un lieu, orange de 2 h à 4 h, rouge après 4 h ; la maquette montre un orange à 18 min, je la considère comme un exemple). Formulations proposées : orange « {Prénom} a quitté {lieu} à 16 h 40 » ; rouge « Pas de nouvelles de {Prénom} depuis 16 h 02 » + bouton « Envoyer un message ». Heure absolue plutôt que « depuis 16 heures » (qui se lit aussi comme une durée de 16 h). Pagination du bandeau : une page par alerte, seulement s'il y en a plusieurs.
- *Impact* : textes du Feed ; valide les constantes 2 h / 4 h communes aux deux plateformes.

**Q12. Que dit le bandeau quand il n'y a rien à signaler ?** (QM12 est décidé ; il reste le libellé.)
- *Recommandation* : « Tout va bien » **seulement** si au moins un proche a un statut connu et tous sont verts ; « {n} sur {m} ont donné des nouvelles » sinon ; « Pas encore de nouvelles » quand aucun statut n'existe ; « Tu es seul(e) dans cette Miff pour l'instant » quand tu es seul(e). Abandonner « Tout le monde est en sécurité » (promesse que l'app ne peut pas tenir). Remplacer « Mis à jour il y a 2 min » par « Dernière nouvelle il y a 2 min ».
- *Impact* : textes `feed_` ; aucun impact serveur.

**Q13. « Localiser » : que fait ce bouton ?**
- *Recommandation* : le remplacer par **« Voir le lieu »** : il ouvre l'application de cartes du système sur les coordonnées **du lieu enregistré** où le proche est (ou qu'il vient de quitter), jamais sur sa position ; absent si le proche n'est dans aucun lieu connu. À défaut : le retirer en v1. Dans tous les cas, ne jamais laisser croire à un suivi en direct.
- *Impact* : dépend du ticket Lieux ; cohérent avec « aucune coordonnée d'une personne n'est stockée » ; sans conséquence sur FEED-1.

**Q14. Le pourcentage de batterie : on le garde, et à quelles conditions ?**
- *Recommandation* : le garder (utile pour comprendre un silence), **daté** (valeur de la dernière mise à jour du proche, jamais « en direct »), masqué si absent ou vieux de plus de 24 h ; **aucune alerte** de batterie faible en v1 ; le texte de consentement à l'entrée d'une Miff doit citer le **niveau de batterie** parmi ce qui est partagé (cela change la version du texte).
- *Impact* : texte du ticket Miff (`consentVersion`), spec Feed (question ouverte de `feed-cercle-de-confiance.md`).

**Q15. Cloche et avatar de l'en-tête du Feed.**
- *Recommandation* : **retirer la cloche** en v1 (pas de notifications, pas d'historique par conception : aucune donnée à afficher) ; l'avatar mène à l'onglet Profil (ou est retiré, la barre d'onglets suffisant).
- *Impact* : simplifie FEED-1 ; la cloche reviendra avec le ticket Notifications.

---

## 11. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Navigation, Profil, Réglages | Code natif, services Firebase déjà utilisés | Lectures Firestore (Feed, Profil) | `data-model.md` §12 : > 30 000 lectures/jour répétées = signal Blaze |
| Message / Appeler | Applications du système, aucune permission | Aucune | Aucun |
| Voir le lieu | Application de cartes du système (aucune clé de carte) | Dépend du lieu enregistré | Une carte intégrée à l'app serait un autre choix (clé, quotas) : jamais sans décision PM |
| Langue | Ressources du système | 2 langues | Chaque langue ajoutée : relecture, textes légaux, captures |
| Abonnement | Aucun achat branché ; coquille en lecture | Premium accordé à la main | Validation d'achat : **Blaze** ou service tiers : **jamais sans décision PM** |
| Notifications | Aucune | Rien n'est délivré sans serveur | Blaze : décision PM |

Aucun moyen de paiement ajouté ; aucun choix économique ne réduit la sécurité.

---

## 12. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.
1. **Répondre aux 15 questions** de §10 (au minimum Q1 à Q5 pour démarrer ONB-3a / ONB-3b ; Q10 à Q15 avant FEED-2).
2. **Confirmer** si les règles d'ONB-B (téléphone) sont déployées sur `miff-3d283` (bloque l'écriture du numéro dans Mes informations).
3. **Valider** les maquettes corrigées et complétées par `design-system` (§4A, §4C) ; fournir l'export PNG des écrans nouveaux ou corrigés.
4. **Décider** si UPDATE-1 (mise à jour manuelle) s'écrit avant le Tracking (§5.2).
5. **Lancer, séparément, les challenges** des tickets Lieux, Update, Tracking, Premium et Notifications ; ce document n'en tranche aucun.
6. Si tu as modifié le ticket Notion « FEED » depuis le 2026-10-24 : me le signaler (je n'ai pas pu le relire).

*Ce document ne modifie ni le code, ni Figma, ni Notion, ni les règles Firestore, ni `backend/data-model.md`. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste.*
