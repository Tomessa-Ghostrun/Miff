# Ticket : Paywall Miff+ périodique : une apparition « toutes les 10 ouvertures » pour les personnes sans Miff+ (PAY-PER-A, PAY-PER-I)

**Statut** : **ticket validé ; Android livré (PAY-PER-A, PR 324, finitions PR 327) ; iOS en revue (PR `ios/paywall-periodique`, PAY-PER-I, recette `docs/qa/checklists/paywall-periodique-ios-recette-pm.md`)**. Les réglages par défaut du §3 ont été validés par le PM le 2026-11-25 ; **deux décisions du PM du 2026-11-25 priment sur la rédaction d'origine de ce ticket : P1 (règle active dans TOUS les builds, sans drapeau d'extinction, §14) et « aucune mise à jour des textes légaux » (§13)**. Les passages d'origine qu'elles remplacent sont corrigés ci-dessous. Aucune écriture Notion, aucun texte légal modifié.
**Rédigé le** : 2026-11-25.
**Demande du PM (2026-11-25)** : afficher le paywall Miff+ aux personnes **sans Miff+** « **toutes les 10 ouvertures de l'app, après le premier clic de l'utilisateur une fois sur l'app** ».
**Ticket Notion** : à créer ou relier par le PM (je n'écris rien dans Notion). Statut Notion suggéré : « A cadrer » puis « Spec en attente de review IA ».
**Dépend de** : `docs/tickets/paywall-v2.md` (l'écran, ses états, la croix différée de 3 s : PAY-UI-A/I, **livré sur Android**, présent dans `main`) ; `docs/tickets/premium-achat-reel.md` (états d'abonnement §8, achat réel SRV-8A/I) ; `docs/tickets/pop-ins-avis.md` (précédent le plus proche : fenêtre qui se montre « au retour au premier plan », règles « jamais pendant… » §8 C1 à C10) ; `docs/tickets/notifications-p1-derniere-visite.md` (seuil d'absence de 30 minutes, décision Q3).
**Ne dépend PAS de** : aucune donnée serveur, aucune nouvelle règle Firestore, aucune Cloud Function.

**Convention** : **[Décidé]** = décision du PM (source citée). **[Proposé]** = ma proposition, à valider. **[Vérifié]** = confirmé sur une page officielle lue le 2026-11-25. **[Non vérifié]** = non confirmé. **[Code]** = constaté en lisant `main` le 2026-11-25. Aucun texte n'est relu par un juriste (décision du PM du 2026-10-23, risque assumé).

---

## 1. Résumé pour le PM

1. **Ce que ça livre** : à une ouverture sur 10, une personne **sans Miff+** voit **le paywall existant** (même écran, même croix différée de 3 s, aucun nouveau texte), **après son premier appui dans l'app**, **une seule fois par session**, jamais pendant l'onboarding, une alerte danger, la création ou l'arrivée dans une Miff, la suppression du compte.
2. **Ce que ça change dans tes décisions antérieures** : jusqu'ici « **aucune relance automatique** » (Onboarding Q5, rappelé dans `paywall-v2.md` §6.2 qui écartait explicitement « toutes les 10 visites »). **Ta demande du 2026-11-25 remplace cette règle** pour le paywall ; la règle de l'**écran provisoire** de fin d'onboarding (« jamais relancé ») reste inchangée. Je n'ai modifié aucun de ces deux tickets : la liste des phrases à aligner est au §13.
3. **Un compteur, c'est tout** : un **entier** gardé **sur le téléphone** (aucune date, aucun serveur, aucun événement qui contienne le compteur), remis à zéro à chaque affichage du paywall, quel qu'en soit le déclencheur.
4. **« Ouverture » = démarrage à froid, ou retour au premier plan après 30 minutes ou plus** (proposition N = 30 min, §4.2, **même définition** que le « dernier passage » des notifications : « 30 minutes ou plus »).
5. **Le risque le plus important est le tien (§10)** : c'est la **première fois que le paywall s'affiche sans que la personne l'ait demandé** (jusqu'ici : 4e lieu, 2e Miff, zone de danger, toujours après un geste de sa part), et il garde sa **croix différée de 3 s**. Google Play interdit les plein écrans inattendus « surtout quand la personne fait autre chose » et ceux qu'on ne peut pas fermer après 15 s ; le délai de 3 s est très en dessous de 15 s, mais l'effet « surprise » est réel. **Risque assumé par toi**, avec les parades du §10.
6. **Textes légaux** [**Décidé par le PM, 2026-11-25**] : **aucune mise à jour des textes légaux ni de la politique de confidentialité** pour ce compteur local ; **risque assumé** (§13, risque J76 de `docs/legal/README.md`). La recommandation « version 1.4 » d'origine est retirée. Aucun texte publié n'est modifié.
7. **Point P1 tranché par le PM le 2026-11-25** : la règle est **ACTIVE dans TOUS les builds distribués (release, interne, debug), SANS drapeau d'extinction**, **même si l'achat réel n'est pas en service**. Le paywall affiche alors **son état actuel** (y compris « Miff+ arrive bientôt » quand l'achat est désactivé) : on réutilise l'écran existant, on ne crée aucun écran. **Risque assumé par le PM** : offre périodique sans achat réel, politique Google Play sur les écrans intercalaires, règle Apple 3.1.2 (§10). La croix reste à 3 s.

---

## 2. Contexte et existant

- **Paywall actuel** [Code] : un écran unique, ouvert aujourd'hui par trois raisons (`PaywallReason` = `LIMIT` : 4e lieu, `MIFF` : 2e Miff, `DANGER` : zone de danger), tous **déclenchés par un geste de la personne**. Croix de fermeture après 3 s (`PaywallCloseDelay.MILLIS`, `PaywallCloseGate`, **horloge injectable**), prix lus dans le store, variante « Miff+ arrive bientôt » (`PaywallOffer.ComingSoon`) seule visible en production tant que l'achat n'existe pas.
- **Android** : l'application sait déjà compter les retours au premier plan (`ForegroundObserver` → `ForegroundTicks`, utilisé par la fenêtre d'avis ; « rotation » exclue) et connaît l'état Miff+ (`OfferState.Classic` / `Premium`, lu comme les règles : `active == true` et `expiresAt` dans le futur). **iOS** : la fenêtre d'avis écoute `willEnterForegroundNotification` (l'ouverture à froid compte) [`pop-ins-avis.md` §9].
- **Précédents à réutiliser, pas à réinventer** : la fenêtre d'avis (retour au premier plan, 1,5 s d'attente, « jamais pendant l'Update, le clavier, l'onboarding, les autorisations, la suppression de compte, jamais par-dessus un message de l'app », état local effacé à la déconnexion et à la suppression du compte, interrupteur de build) et le « dernier passage » des notifications (**30 minutes** d'absence, lancement à froid : toujours).
- **Offre** : Miff+ [Décidé, 2026-10-31] : essai de 7 jours sur l'annuel, annuel 59,88 € / an, mensuel 6,99 €. Formule gratuite : 1 Miff, 3 lieux, pas de zone de danger.
- **Pas d'écran maquetté nouveau** : on réutilise l'écran de `design/exports/paywall/` tel que livré par PAY-UI. Les écarts de ce ticket avec la règle d'avant sont dans le §13.

---

## 3. Réglages validés par le PM le 2026-11-25 [Décidé]

| # | Réglage |
|---|---|
| D1 | Cible : **personnes sans Miff+**. **Jamais** pour les personnes avec Miff+, **y compris pendant l'essai**. |
| D2 | **Jamais avant la première interaction (premier appui)** de la session. **Jamais** pendant l'onboarding / inscription / consentement, ni par-dessus un écran critique : **alerte danger en cours**, flux de **création ou d'arrivée dans une Miff**, **suppression de compte**. |
| D3 | La **croix de fermeture suit le paywall actuel** : absente 3 s, puis en fondu (`paywall-v2.md` §6). |
| D4 | **Compteur d'ouvertures sur l'appareil seulement** : aucune donnée serveur, aucun nouvel accès, **aucun analytics de position**. **Remis à zéro à chaque affichage.** |
| D5 | **« Ouverture »** = démarrage à froid **ou** retour au premier plan après **N minutes ou plus**. N proposé au §4.2 (défaut 30 min). |
| D6 | **Une seule apparition par session.** |
| D7 | Cadence : **10 ouvertures** entre deux apparitions. [**Décision du PM du 2026-11-26** : passée de 15 à 10 ; simple changement de fréquence, rien d'autre ne change.] |
| D8 | **P1 (2026-11-25)** : règle **active dans tous les builds distribués**, **sans drapeau d'extinction**, même sans achat réel ; le paywall montre son état actuel (« Miff+ arrive bientôt » compris). |
| D9 | **Légal (2026-11-25)** : **aucune mise à jour** des textes légaux ni de la politique de confidentialité pour ce compteur ; risque assumé (§13). |

---

## 4. La règle, précisément

### 4.1 Vocabulaire
- **Ouverture** : voir §4.2. **Session** : de une ouverture à la suivante (le démarrage à froid ou le retour après N minutes ou plus ouvrent une nouvelle session ; un retour plus court prolonge la session en cours).
- **Premier appui** : le premier **appui** de la personne sur l'app dans la session, c'est-à-dire un toucher qui active un élément (bouton, carte, onglet), ou son équivalent d'accessibilité (activation par TalkBack ou VoiceOver, touche Entrée au clavier). **Un défilement, un balayage ou un toucher dans le vide ne comptent pas** [Proposé, P3]. L'appui sur l'écran de connexion, d'onboarding ou de consentement ne compte pas : l'app n'est alors pas encore « utilisée » (D2).
- **Moment calme** : un **onglet principal** est affiché (aucun écran empilé, aucune feuille, aucun message de l'app, aucun clavier), et **1,5 s** se sont écoulées depuis le **dernier toucher** [Proposé, P3 ; 1,5 s = la valeur de la fenêtre d'avis]. L'appui lui-même s'exécute **normalement** : le paywall ne remplace jamais l'action demandée, il vient après, quand la personne s'est posée.

### 4.2 Qu'est-ce qu'une « ouverture » ? [Proposé : N = 30 minutes]
Une ouverture est comptée **une seule fois** quand l'app arrive à l'écran **devant une personne connectée** (les onglets principaux), dans l'un de ces deux cas :
1. **Démarrage à froid** (le processus vient de naître et une première fenêtre s'affiche) ;
2. **Retour au premier plan après N = 30 minutes ou plus** passées en arrière-plan.

**Ne comptent pas** : une rotation, un changement de langue ou de thème (recréation de l'écran), un retour au premier plan après **moins de 30 minutes**, le retour d'une fenêtre du système (autorisation de position ou de notifications, appareil photo, choix d'une photo, connexion Google, feuille d'achat du store, Réglages du téléphone), **le démarrage du processus sans écran** (Android : réveil par une arrivée dans un lieu, un message push ou une tâche de fond ; iOS : réveil en arrière-plan par la surveillance des lieux : l'app **n'est pas ouverte** par la personne), l'arrivée sur l'écran de connexion, d'onboarding ou de consentement.

**Pourquoi 30 minutes** :
- **Même seuil que le « dernier passage » des notifications** (décision Q3, `notifications-p1-derniere-visite.md` : « 30 minutes ; lancement à froid : toujours ») : **une seule définition de « ouverture » dans toute l'app**, déjà expliquée au PM ;
- **plus court** (5 à 10 min) : consulter un message, répondre à un appel, autoriser une permission ou aller dans les Réglages du téléphone compteraient comme des ouvertures, et le paywall reviendrait en quelques heures ;
- **plus long** (1 h ou plus) : une app de réassurance s'ouvre souvent brièvement plusieurs fois par jour [hypothèse, **aucune mesure** d'usage disponible] ; au-delà de 30 min on compterait trop peu d'ouvertures et la cadence de 10 n'aurait plus de sens ;
- **côté technique** : la durée d'absence est mesurée **avec une horloge monotone** (celle qui continue en arrière-plan et qu'un changement d'heure ne perturbe pas, comme pour la croix de 3 s), donc **aucune date n'est stockée**. Si le système tue l'app pendant l'absence, le prochain lancement est de toute façon un **démarrage à froid** : il compte.
- **À savoir** : sur l'OPPO Find X5 Lite (et d'autres surcouches), le système tue souvent l'app en arrière-plan ; une personne qui revient 5 minutes plus tard peut donc déclencher un « démarrage à froid » et **compter une ouverture de plus** que prévu. Effet : la cadence réelle peut être un peu plus rapide sur ces téléphones. **Accepté** (alternative : stocker la date de dernière sortie ; non retenue : une donnée de plus à décrire, pour un gain faible).

**Ordre de grandeur de la cadence** : à 2 ou 3 ouvertures par jour, **10 ouvertures ≈ 3 à 5 jours** ; à 6 ouvertures par jour, ≈ 2 jours [calcul sur hypothèse, non mesuré]. Si cela te paraît trop ou pas assez, la cadence est une **constante unique** (§9).

### 4.3 Le compteur
- **Une seule valeur entière** (0 à 10), **sur l'appareil**, dans le stockage privé de l'app (Android : préférences privées, exclues des sauvegardes comme les autres états locaux ; iOS : fichier de l'app **exclu des sauvegardes**, comme l'état de la fenêtre d'avis). **Aucune date, aucun identifiant, aucun nom de Miff, aucune position.**
- **Incrément** : +1 à chaque ouverture comptée (§4.2), **plafonné à 10** (il ne grossit pas indéfiniment : une apparition « due » reste due).
- **« Due »** : compteur ≥ 10 à l'ouverture. **Le paywall est montré dès que toutes les conditions du §5 sont réunies** dans la session ; sinon il reste dû et sera tenté à la **session suivante** (jamais plus d'une fois par session, D6). Le compteur n'est **pas remis à zéro** tant que le paywall n'a pas été **réellement affiché**.
- **Remise à zéro** [Décidé D4, précisé P8] : **à l'instant de l'affichage effectif** du paywall (celui où l'événement `paywall_shown` serait émis), même si la personne ferme l'écran au bout d'une seconde ou si l'app est tuée pendant les 3 s. **Tout affichage du paywall remet le compteur à zéro, quel que soit le déclencheur** (4e lieu, 2e Miff, zone de danger, ou périodique) : une personne qui vient de voir l'offre ne la revoit pas par surprise.
- **Remise à zéro aussi** quand l'app constate que la personne **a Miff+** (à une ouverture, essai compris) : sinon, une personne dont l'abonnement vient de se terminer verrait l'offre dès son premier appui [Proposé, P8].
- **Effacé** à la **déconnexion** et à la **suppression du compte** (la personne suivante repart de zéro ; même règle que l'état de la fenêtre d'avis). Une **réinstallation** repart à zéro (conséquence acceptée : elle retarde seulement l'apparition).
- **Premier comptage** : la première ouverture **d'une personne connectée et ayant terminé l'onboarding** compte pour 1. Aucune ouverture n'est comptée avant (l'onboarding, la connexion, le consentement ne comptent pas) : **la première apparition ne peut donc pas arriver avant la 10e ouverture réelle de l'app utilisée** [Proposé, P9].

---

## 5. Quand le paywall périodique est montré, et quand il ne l'est jamais

**Il est montré si et seulement si TOUT est vrai** (évalué à chaque « moment calme », §4.1) :

| # | Condition |
|---|---|
| C1 | compteur ≥ 10 (due). |
| C2 | **Pas déjà montré dans cette session** (D6) ; un paywall ouvert par la personne elle-même (4e lieu, etc.) **compte comme « déjà montré »** et remet le compteur à zéro (§4.3). |
| C3 | **Premier appui fait** dans cette session (D2). |
| C4 | **Moment calme** (§4.1). |
| C5 | La personne est **sans Miff+** et **son état est connu** : droit lu (cache local ou serveur), jamais deviné. **État inconnu** (démarrage à froid avant la première lecture, hors connexion sans cache) : **on n'affiche pas** et on réessaie au prochain moment calme de la session. Voir §6 pour les états d'abonnement intermédiaires. |
| C6 | [**Remplacé par la décision P1 du 2026-11-25**] L'écran s'ouvre **dans son état actuel**, « Miff+ arrive bientôt » compris (aucune condition sur l'état de l'offre). **Seule garde conservée** : dans un build où l'achat réel est actif (`PREMIUM_PURCHASE`, donc des prix à lire dans le store), **hors connexion on n'affiche pas** (aucun paywall « Prix indisponibles » ou « Chargement » par surprise). Dans un build distribué sans achat, il n'y a aucun prix à lire : la règle s'applique telle quelle. |
| C7 | **Aucune situation interdite** (liste ci-dessous). |
| C8 | [**Supprimé par la décision P1 du 2026-11-25**] Il n'y a **aucun drapeau de build** : la règle est active dans tous les builds. Seul moyen de l'éteindre : mettre `OPENINGS_BETWEEN` à 0 (§9, PP17). |

**Situations où il n'est jamais montré** (liste fermée ; **le compteur n'est pas touché** : le paywall reste dû et sera retenté à un prochain moment calme, ou à la session suivante) :
- **Onboarding, inscription, connexion, consentement** (localisation, statistiques), écrans d'autorisation (D2).
- **Alerte danger** [D2] : toute étape de **ma propre alerte** (geste d'appui long, envoi, écran « alerte envoyée », alerte active) **et** tout moment où le Feed ou le centre de notifications montre l'**alerte d'un proche non encore vue**. **La session entière** est exclue quand l'app a été ouverte **en touchant une notification d'alerte** [Proposé, P11 : une personne qui arrive pour un proche en danger ne doit pas voir une offre commerciale, même plus tard dans la session].
- **Création d'une Miff, arrivée dans une Miff (saisie du code secret)**, ainsi que **la création ou la modification d'un lieu** [D2 ; j'ajoute les lieux : ce sont des flux de configuration du cercle].
- **Suppression du compte**, et tout écran de **fin d'abonnement** ou de mise en veille PREM-FIN s'il existe (le message de la fin d'abonnement gagne ; le paywall attend la session suivante).
- **Update** (photo « Donner des nouvelles », caméra, choix d'une photo), **clavier ouvert**, **fenêtre du système** (autorisation, feuille d'achat du store).
- **Un message de l'app à l'écran** (accès perdu, « En attente de ton accord », feuille « Localiser », « Tu as arrêté de partager », mode voyage, etc.) et **la fenêtre d'avis** (voir §6).
- **Paywall déjà à l'écran** (aucune superposition de deux offres).
- **Une surcouche ouverte dans les onglets** (menu du sélecteur de Miff, photo plein écran, feuille, boîte de dialogue, calendrier) : c'est une autre fenêtre, l'écran de derrière reste « au premier plan » ; chaque surcouche le déclare à la règle (Android : `OverlayPresent()`), l'offre attend 1,5 s APRÈS la fermeture [ajouté à la revue de la PR Android].

---

## 6. Cas limites et décisions par défaut

| Cas | Comportement [Proposé] |
|---|---|
| **Compteur au 10e passage pendant l'essai** | Essai = **Miff+ actif** : pas d'affichage ; le compteur est remis à zéro à cette ouverture (§4.3). Si l'essai se termine sans abonnement, la personne repart de **0**, jamais de « 10 déjà atteints » : l'offre ne revient pas le jour de la fin de l'essai. |
| **Abonnement expiré** (`expired`) | Classique : compteur à zéro au dernier constat de Miff+, puis **10 ouvertures** avant la première apparition. **Jamais** dans la même session que le message de fin d'abonnement (PREM-FIN). |
| **Changement d'abonnement** (mensuel ↔ annuel, reprise après annulation, fin d'essai qui devient payant) | Miff+ reste actif sans interruption : pas d'affichage. Un changement fait **pendant** la session (par les réglages du store) est lu au moment calme suivant : si Miff+ est actif, **annulation** de l'affichage. Le droit est **relu juste avant d'ouvrir** l'écran. |
| **Achat terminé pendant que le paywall périodique est ouvert** | L'écran passe à l'état **« Abonné »** déjà livré (« Continuer » immédiat). Le compteur était déjà à zéro. |
| **Restauration des achats** | Si la restauration réussit, Miff+ est actif : état « Abonné », compteur à zéro. **Aucune restauration automatique** sur iOS (règle de `paywall-v2.md`, PW12) : le paywall périodique ne la lance jamais. |
| **Période de grâce** (`grace`) | Droit actif : **jamais** montré. |
| **Suspendu** (`on_hold`), **en attente** (`pending`), **remboursé** (`revoked`) [Proposé, P4] | **Jamais montré** : la personne a déjà un abonnement chez le store (risque de double achat, ou paiement en cours de validation), ou vient d'être remboursée (un paywall serait maladroit). Tant que l'app **ne lit pas** le champ `state` (SRV-8), ces cas sont traités comme « sans Miff+ » : ils n'existent pas encore en production. |
| **Premium accordé à la main** (`manual`) | Actif : jamais montré. Expiré : traité comme « sans Miff+ ». |
| **Hors connexion** | Compteur et règles **locales** : ils fonctionnent. Le droit vient du **cache** (dernier état connu) ; les **prix viennent du store** : sans prix lisibles (C6), **on n'affiche pas**. Le paywall reste dû, il est retenté à la session suivante. **Aucune erreur, aucun message.** |
| **Droit inconnu** (démarrage à froid avant la première lecture du droit) | On attend ; si l'état n'est pas connu à la fin de la session : rien (C5). |
| **Compte différent sur le même téléphone** | Compteur effacé à la déconnexion (§4.3). |
| **Changement d'heure, horloge reculée** | Sans effet : le compteur ne contient aucune date, l'absence se mesure avec l'horloge monotone. |
| **Première interaction très tardive** | La session peut finir sans aucun appui : **pas d'affichage**, le paywall reste dû. |
| **Plusieurs ouvertures rapprochées** (retours après moins de 30 min) | **Une seule session** : un seul comptage, un seul affichage possible. |
| **Fenêtre d'avis** (`pop-ins-avis.md`, absente des versions distribuées tant que le PM n'a pas reconfirmé) | **Jamais deux fenêtres dans la session** : celle qui s'affiche en premier gagne, l'autre attend la session suivante. L'avis est évalué au retour au premier plan (1,5 s) et arrive donc **avant** le premier appui : en pratique, l'avis passe d'abord. |
| **Rotation, grande police, lecteur d'écran** | Comme le paywall actuel (`paywall-v2.md` §6.4). **Particularité** : l'apparition est **inattendue** pour une personne qui utilise TalkBack ou VoiceOver ; le focus passe à l'écran d'offre comme à toute fenêtre, et **revient à l'élément précédent** à la fermeture. L'annonce « Tu peux maintenant fermer cet écran » existe déjà. |
| **App en arrière-plan avant l'affichage** | Aucun affichage depuis l'arrière-plan (le moment calme exige le premier plan). Si le paywall a été **affiché effectivement** (compteur remis à zéro) puis l'app part en arrière-plan : au retour (moins de 30 min, même session) il est **encore là** ; à 30 min ou plus c'est une **nouvelle session**. |
| **App tuée par le système pendant que le paywall est affiché** | Récréation = **nouvel affichage** de l'écran (`paywall-v2.md` §6.1 point 7), mais **pas** un nouveau déclenchement périodique : le compteur est déjà à zéro. |

---

## 7. Ton (non anxiogène, jamais culpabilisant)

Principe de `CLAUDE.md` : l'app touche à l'inquiétude pour un proche. Pour ce paywall :
- **Aucun nouveau texte** : on réutilise l'écran livré, sa phrase « La formule gratuite reste disponible : 1 Miff et 3 lieux de confiance » et ses 3 bénéfices. **Aucune phrase de contexte** (« Tu as atteint 3 lieux… » n'aurait aucun sens ici) : l'écran s'ouvre **sans** cette phrase [Proposé, P6 ; si l'écran livré n'accepte pas une absence de phrase, le dev le signale dans la PR plutôt que d'en inventer une].
- **Jamais** : « encore », « tu rates », « dernière chance », « n'attends plus », « tes proches… », aucune mention de la fréquence, d'un compte à rebours affiché, d'une limite de Miff atteinte, ni de la sécurité de qui que ce soit. Le paywall **ne dit jamais** que l'absence de Miff+ met les proches en danger.
- **Le geste central ne dépend jamais de l'offre** : le suivi des lieux, les signaux « je vais bien » et l'alerte danger **continuent** que la personne ferme ou non l'écran. Fermer **ne retire** aucune fonction, ne déclenche aucun message de regret, et ne change rien à la suite (aucun « tu as refusé »).
- **Sortie** : la croix après 3 s (D3), le retour système et le geste iOS comme `paywall-v2.md` §6.3.

---

## 8. User stories

- **US1** En tant que **personne sans Miff+ qui utilise Miff régulièrement**, je veux **revoir l'offre de temps en temps, sans qu'elle bloque mes gestes**, afin de pouvoir passer à Miff+ le jour où cela m'intéresse.
- **US2** En tant que **personne qui ouvre l'app pour une raison sérieuse** (alerte d'un proche, arrivée dans une Miff, suppression de mon compte), je veux **ne jamais** voir une offre à ce moment-là.
- **US3** En tant que **personne qui a Miff+** (essai compris), je veux **ne jamais** voir cette offre.
- **US4** En tant que **personne qui tient à sa vie privée**, je veux que le décompte de mes ouvertures **reste sur mon téléphone**, sans date ni envoi.
- **US5** En tant que **PM**, je veux **un seul endroit** où régler la cadence, le seuil d'absence et l'allumage, afin d'ajuster ou d'éteindre la règle sans refonte.

---

## 9. Plan de réalisation : Android d'abord, puis iOS (deux PR)

**Principe** : une **règle pure** (sans plateforme) qui décide, des **branchements** minces. Même logique, mêmes constantes, mêmes noms sur les deux plateformes (parité vérifiée en revue).

**Android (PAY-PER-A)** : [Code : emplacements indicatifs]
- `domain/paywall/PeriodicPaywallRules.kt` : classe pure avec **horloge injectable** (`MonotonicClock`, existante), magasin du compteur et lecteurs de conditions **injectés** ; constantes dans `PeriodicPaywallDefaults` : `OPENINGS_BETWEEN = 10`, `BACKGROUND_MINUTES = 30`, `QUIET_AFTER_TOUCH_MILLIS = 1_500` ; **mettre `OPENINGS_BETWEEN` à 0 désactive la règle** (comptage compris) (une valeur, une PR d'une ligne ; **aucun réglage distant**).
- `data/paywall/PeriodicPaywallStore` (interface) + implémentation en préférences privées, **exclues des sauvegardes** (`backup_rules.xml` et `data_extraction_rules.xml` : à vérifier qu'elles couvrent le nouveau fichier) ; effacé par la déconnexion et la suppression de compte (là où l'état de la fenêtre d'avis l'est déjà).
- Branchement de l'**ouverture** : réutiliser `ForegroundObserver` (premier plan) en y ajoutant la mesure d'absence ; **ne compter que les arrivées avec une fenêtre** (jamais un réveil sans écran).
- Branchement du **premier appui** : détection au niveau de la fenêtre principale (un seul point d'écoute des appuis, sans lire leur contenu, aucune donnée gardée au-delà d'un indicateur « fait » en mémoire pour la session).
- Branchement de l'**affichage** : un **hôte** près de celui de la fenêtre d'avis (`ReviewPromptHost`) qui ouvre le paywall existant avec une nouvelle valeur `PaywallReason.PERIODIC` (sans phrase de contexte). **Aucune** modification de `PaywallCloseGate` : la croix suit la règle commune (CR10).
- **Trois entrées existantes** (`PaywallReason.LIMIT`, `MIFF`, `DANGER`) : à **chaque affichage effectif** par l'une d'elles, appeler la règle pour **remettre le compteur à zéro** et marquer la session « paywall déjà montré » (PP11), au même endroit que l'événement `paywall_shown` ; test par entrée.
- **Aucun drapeau de build** [**décision P1 du PM, 2026-11-25** ; remplace le `BuildConfig.PERIODIC_PAYWALL` prévu à l'origine] : la règle est active dans **tous** les builds ; **mettre `OPENINGS_BETWEEN` à 0** est le seul moyen de l'éteindre (une PR d'une ligne, testé). Un test (`PeriodicPaywallBuildTest`) vérifie qu'aucun drapeau n'existe.
- **Outil d'essai** (Debug seulement, décrit dans `docs/qa/checklists/paywall-periodique-android-recette-pm.md`) : (a) dans la page de diagnostic, une carte « Offre périodique (essai) » : compteur affiché, « compteur = seuil − 1 », « compteur = seuil », « compteur = 0 » ; (b) la propriété de build `-Pmiff.periodicPaywallOpenings=N` (1 à 9) qui **abaisse seulement** le seuil dans le build Debug (`BuildConfig.PERIODIC_PAYWALL_OPENINGS_DEBUG`, **toujours 0 hors Debug**), pour que le PM n'ait pas à ouvrir l'app 10 fois.

**iOS (PAY-PER-I)** : après la PR Android (parité), **mêmes règles, mêmes constantes** ; ouverture par `willEnterForegroundNotification` avec **seuil d'absence** (et lancement à froid) ; **ne pas compter** les réveils en arrière-plan par la surveillance de régions (l'app n'est pas active : état de l'application) ; premier appui par un **reconnaisseur de gestes** sur la fenêtre principale (sans consommer l'appui) ; compteur dans un fichier de l'app **exclu des sauvegardes** (`isExcludedFromBackup`), effacé à la déconnexion et à la suppression de compte ; présentation **plein écran** du paywall existant (déjà décidée), geste de glissement désactivé 3 s comme aujourd'hui ; **mêmes trois entrées existantes** (limite de lieux, 2e Miff, zone de danger ; sur Android l'écran Abonnement, qui ouvre aussi l'offre, est branché pareil) branchées pour remettre le compteur à zéro et marquer la session (PP11) ; **aucun drapeau de build** (décision P1 du 2026-11-25 : règle active dans tous les builds). **Aucune compilation locale** : vérification en CI et sur TestFlight (iPhone 11 Pro).

**Process** (`CLAUDE.md`) : ticket validé → challenge → dev Android puis iOS, une branche et une PR par plateforme, revue de code en contexte neuf + revue sécurité, QA, recette du PM, merge sur « oui » explicite. Un ticket Notion et un statut sont à créer par le PM ou sur son accord.

**Estimation de taille** [Proposé, sans mesure] : petite à moyenne par plateforme (une règle pure, un magasin d'un entier, deux branchements, un hôte, des tests).

---

## 10. Impact sur les stores, risques assumés

**Ce que j'ai pu lire le 2026-11-25** :
- **Google Play, politique « Ads »** [Vérifié, page lue le 2026-11-25 : https://support.google.com/googleplay/android-developer/answer/9857753] : les plein écrans **inattendus** ne sont pas autorisés, « surtout quand la personne fait autre chose » ; ceux qu'on ne peut **pas fermer après 15 secondes** sont interdits sauf s'ils sont choisis ou n'interrompent pas ; ce qui gêne l'usage doit pouvoir être fermé **« facilement, sans pénalité »** ; pas de faux boutons de fermeture. **La page ne dit pas** si cela s'applique à l'**offre d'abonnement propre** de l'app (elle renvoie aux politiques de promotion et d'abonnement) : **applicabilité non tranchée**.
- **Apple, App Review Guidelines** [Lu le 2026-11-25 par un outil de résumé, sur les 100 000 premiers caractères seulement ; la **section 5.6** et toute **règle de fréquence** n'ont **pas été lues** : à lire par `release-manager` avant soumission] : 3.1.2 (valeur continue, décrire ce qu'on obtient avant de demander l'abonnement, pas d'abonnement par erreur, pas de pratiques trompeuses), 2.5.18 pour les **annonces** (fermeture visible et facile). **Je n'ai trouvé aucune règle chiffrée de fréquence** pour une offre d'abonnement.

**Ma lecture** :
- **Fréquence « toutes les 10 ouvertures » (≈ 2 à 5 jours selon l'usage)** : **jamais** une règle chiffrée qui l'interdise dans ce que j'ai lu ; l'écran reste **honnête** (prix, essai, annulation, formule gratuite, `paywall-v2.md` §5.4). **Fréquence acceptable sous réserve de relecture** [Non vérifié : aucune confirmation officielle].
- **Le vrai point sensible est la combinaison « inattendu » + « croix absente 3 s »** : le paywall apparaît ici **sans geste de la personne**, juste après qu'elle a commencé à utiliser l'app. Le délai de 3 s est **très en dessous des 15 s** de Google, mais un relecteur ou une personne peut y voir un **plein écran qui interrompt**. Risque : **rejet à la revue, avis négatifs, perception d'une pratique agressive** (surtout pour une app de réassurance). **Risque assumé par le PM** (même base que `paywall-v2.md` RP1 à RP3), jamais écrit « à valider par un juriste ».

| # | Risque | Parade |
|---|---|---|
| RS1 | **Rejet à la revue** d'un paywall inattendu à croix différée | **Constantes uniques** (éteindre = mettre `OPENINGS_BETWEEN` à 0, une PR d'une ligne ; **plus de drapeau de build**, décision P1 du 2026-11-25) ; **note pour les relecteurs** (App Store Connect, Play Console) : « une offre peut s'afficher toutes les 10 ouvertures, une fois par session, jamais pendant une alerte ; elle se ferme après 3 secondes ; la formule gratuite reste disponible » ; **relecture des pages officielles par `release-manager` avant soumission** (résultat à inscrire dans `docs/release/decisions-stores-avant-soumission.md`) |
| RS2 | **Effet « surprise »** (le paywall tombe pendant que la personne fait autre chose) | Premier appui + **moment calme** (1,5 s sans toucher, onglet principal, rien d'autre à l'écran) ; l'action demandée par l'appui n'est jamais remplacée ; jamais pendant les parcours sensibles du §5 |
| RS3 | **Avis négatifs, réputation** | Ton du §7 ; une seule apparition par session ; cadence modifiable ; jamais pour les abonnés ; aucun blocage de fonction |
| RS4 | **Piège involontaire** (croix qui n'apparaît pas, compteur bloqué à « dû » qui s'affiche en boucle, affichage pendant une alerte) | Tests du §11 ; une seule apparition par session ; compteur remis à zéro **à l'affichage**, pas à la fermeture ; croix **toujours** présente après 3 s (CR13 de `paywall-v2.md`) |
| RS5 | **Paywall périodique sans achat possible** (« Miff+ arrive bientôt » toutes les 10 ouvertures : l'écran ne vend rien, et un relecteur Google ou Apple peut y voir une offre périodique sans contrepartie, voir Apple 3.1.2) | [**Décidé par le PM le 2026-11-25 (P1) : risque assumé.**] Règle **active dans tous les builds distribués, sans drapeau**, y compris sans achat réel ; la parade reste la valeur unique `OPENINGS_BETWEEN` (une PR d'une ligne pour éteindre). Écran inchangé (il dit « arrive bientôt » honnêtement, sans prix ni bouton), croix à 3 s |
| RS6 | **Droit de la consommation / pratiques commerciales** (connaissance générale, **non vérifié**, hors périmètre technique) | Risque assumé (décision du 2026-10-23). L'offre reste complète et lisible dès la première seconde |

**Compte développeur partagé** : le compte développeur est **partagé avec Ghostrun** (`pop-ins-avis.md` §2) : un incident de revue liée à ce paywall pourrait **aussi toucher Ghostrun**. À garder en tête avant d'allumer la règle dans une version soumise.

---

## 11. Critères d'acceptation (testables)

**Remarque tests** : tout est testé avec une **horloge injectable** (aucun test n'attend 30 minutes ni 3 secondes) et des **états simulés** (droit, prix, situation de l'écran). La règle pure est couverte par des tests unitaires sans Android ni iOS.

### Ouverture et compteur
- [ ] **PP1** **Démarrage à froid** avec une fenêtre : le compteur passe de n à n + 1. **Démarrage sans écran** (réveil par un lieu, un push ou une tâche de fond ; iOS : réveil par la surveillance de régions) : **aucun** changement.
- [ ] **PP2** Retour au premier plan après **30 min ou plus** (**30 min exactement compte**) : +1 ; après **moins de 30 min** : aucun changement ; rotation, langue ou thème : aucun changement ; retour d'une fenêtre du système (autorisation, caméra, feuille du store, connexion Google) : aucun changement.
- [ ] **PP3** La durée d'absence se mesure à l'horloge **monotone** : un changement manuel de l'heure du téléphone ne change ni le comptage ni l'affichage.
- [ ] **PP4** Le compteur est **plafonné à 10** ; il ne contient **que** cet entier : un test vérifie que le fichier ou la préférence ne contient **aucune date, aucun identifiant, aucun nom**.
- [ ] **PP5** Une ouverture sur l'écran de connexion, d'onboarding ou de consentement **n'est pas comptée** ; la première ouverture d'une personne connectée qui a terminé l'onboarding compte pour **1**.
- [ ] **PP6** Le compteur est **effacé** à la déconnexion et à la suppression du compte ; une personne qui se connecte ensuite repart de **0**.
- [ ] **PP7** Compteur **non exposé** : il n'est présent dans **aucun** événement Analytics, journal technique, plantage, ni message serveur ; **aucune écriture Firestore** n'est ajoutée par ce ticket.

### Moment et conditions
- [ ] **PP8** Compteur à 10, **aucun appui** dans la session : le paywall ne s'affiche **pas**, quelle que soit la durée de la session. Compteur à 10, un défilement ou un balayage seul : **pas** d'affichage.
- [ ] **PP9** Compteur à 10, **premier appui** sur un bouton d'un onglet : l'appui s'exécute **normalement** ; le paywall s'ouvre **seulement après 1,5 s sans toucher** et **si un onglet principal est affiché** ; si l'appui a ouvert un écran empilé, une feuille ou le clavier, le paywall **attend** le prochain moment calme de la même session.
- [ ] **PP10** **Une seule apparition par session** : après l'affichage, aucune autre apparition périodique dans la même session, même si le compteur remonte (il est à 0). Une nouvelle session (froid ou 30 min ou plus) peut la montrer de nouveau **seulement** si le compteur est de nouveau à 15.
- [ ] **PP11** **Remise à zéro à l'affichage** : fermer au bout de 1 s, tuer l'app pendant les 3 s, ou acheter : le compteur est à **0**. **Un paywall ouvert par la personne** (4e lieu, 2e Miff, zone de danger) remet aussi le compteur à 0 et compte comme « déjà montré » dans la session.
- [ ] **PP12** Jamais montré pendant : onboarding, consentement, autorisations ; **ma propre alerte** (appui long, envoi, alerte envoyée, alerte active) ; **alerte d'un proche non vue** ; **session ouverte par une notification d'alerte** (toute la session) ; création ou arrivée dans une Miff (saisie du code) ; création ou modification d'un lieu ; suppression du compte ; Update (photo) ; clavier ouvert ; fenêtre du système ; message de l'app à l'écran ; fenêtre d'avis ouverte ; paywall déjà ouvert. Un test **par situation**. Dans chaque cas, le compteur **n'est pas** remis à zéro.
- [ ] **PP13** **Miff+ actif** (essai, annuel, mensuel, grâce, accordé à la main) : **jamais** affiché ; le compteur est remis à **0** à l'ouverture qui constate Miff+. **Compteur à 10 pendant l'essai** : aucun affichage, compteur à 0.
- [ ] **PP14** **Fin d'abonnement** : la personne repart de **0** (10 ouvertures avant la première apparition) ; jamais dans la même session que le message de fin d'abonnement.
- [ ] **PP14 bis** États **suspendu (`on_hold`), en attente (`pending`), remboursé (`revoked`)** : **jamais montré**. **À vérifier avec SRV-8 ; non testable avant** (l'app ne lit pas encore `state` : ces états n'existent pas en production). Test simulé dès que le champ est lu.
- [ ] **PP15** **Droit inconnu** : aucun affichage ; réessai au moment calme suivant ; si le droit devient « Miff+ actif » entre-temps (ou pendant les 1,5 s d'attente), l'affichage est **annulé** ; le droit est **relu juste avant d'ouvrir**.
- [ ] **PP16** **Hors connexion** (mode avion) : le comptage fonctionne ; aucune erreur ni message. **Décision P1 (2026-11-25)** : la variante « Miff+ arrive bientôt » **est** montrée par la règle (build sans achat) ; dans un build **avec achat réel actif**, **aucun affichage hors connexion** (aucun écran « Prix indisponibles » ou « Chargement » par surprise) ; le paywall reste dû.
- [ ] **PP17** [**Remplacé par la décision P1 du 2026-11-25**] **Pas de drapeau** : la règle est active dans tous les builds (un test le vérifie : `PeriodicPaywallBuildTest`). **`OPENINGS_BETWEEN = 0`** désactive la règle entière, comptage compris (test). **En Debug seulement**, le seuil peut être **abaissé** (`-Pmiff.periodicPaywallOpenings=N`) ; en release et releaseLike il vaut toujours 10 (test).

### Écran et accessibilité
- [ ] **PP18** L'écran est **le même composant** que les autres entrées : la **croix apparaît après 3 s**, retour ignoré avant (Android), glissement désactivé avant (iOS), annonce « Tu peux maintenant fermer cet écran » (CR1 à CR13 de `paywall-v2.md` valent sans nouvelle règle).
- [ ] **PP19** Aucune **phrase de contexte** n'est affichée pour le déclencheur périodique ; **aucun texte nouveau** : le contrôle de parité des clés FR/EN et des six langues ne détecte **aucune** clé ajoutée.
- [ ] **PP20** **TalkBack / VoiceOver** : à l'apparition du paywall le focus passe à l'écran ; à la fermeture, le focus **revient** à l'élément précédent ; la navigation n'est jamais bloquée.
- [ ] **PP21** **Analytics** (si P7 accepté) : `paywall_shown` et `paywall_dismissed` avec `trigger = periodic`, **seulement avec consentement**, **sans** compteur, durée, position ni nom ; sans consentement, **aucun** événement.

### Transverses
- [ ] **PP22** Aucun secret, aucune clé, aucune règle Firestore ni fonction modifiées ; CHANGELOG et README de la plateforme à jour ; `docs/contracts/analytics-events.md` mis à jour par la PR si P7 est accepté.
- [ ] **PP23** **Parité Android / iOS** : mêmes constantes, mêmes noms, mêmes cas de tests ; écarts listés et justifiés dans la PR iOS.
- [ ] **PP24** Le suivi des lieux, les signaux « je vais bien » et l'alerte danger **continuent de fonctionner** pendant que le paywall est affiché (test manuel et test d'un lieu franchi pendant l'affichage).

### Recette du PM (sur téléphone)
- [ ] **PR1** OPPO Find X5 Lite (build Debug/internal), compteur forcé à 9 : une ouverture à froid → **aucun** paywall tant que je n'ai pas touché ; premier appui → l'action se fait, puis le paywall s'ouvre **après environ 1,5 s de calme** ; la croix apparaît après **environ 3 s**.
- [ ] **PR2** Même parcours sur l'iPhone 11 Pro (TestFlight).
- [ ] **PR3** Fermer, relancer sans quitter l'app 30 min ou plus : **pas** de second paywall dans la session ; compteur à 0 (le diagnostic le montre).
- [ ] **PR4** Compte avec Miff+ (accordé à la main) : jamais. Compte sans Miff+ mais hors connexion : jamais.
- [ ] **PR5** Pendant la création d'une Miff, la saisie d'un code, la suppression du compte, une alerte : jamais.

---

## 12. Textes à traduire

**Aucun nouveau texte** (tutoiement et six langues : rien à produire). Le paywall existant est repris tel quel, sans phrase de contexte. Si le dev constate que l'écran livré **exige** une phrase de contexte, il le **signale dans la PR** et le PM tranche (P6) : **aucune phrase n'est inventée**. Un éventuel **interrupteur** dans les Réglages pour couper ces apparitions n'est **pas** prévu (voir P12).

---

## 13. Légal, confidentialité, documents à aligner

**Impact sur les textes légaux (version 1.3 **publiée par le PM le 2026-11-25**, commit `f9db3a17`, date affichée « 24 novembre 2026 », décalage d'un jour assumé par le PM) : je n'en ai modifié aucun, ni `docs/legal/` ; ce qui suit est une recommandation.**

- **Faut-il citer le compteur ?** [**Décidé par le PM le 2026-11-25 : NON, aucune mise à jour des textes légaux ni de la politique de confidentialité pour ce compteur ; risque assumé.**] Le compteur est **local**, **un entier**, **sans donnée personnelle**, **jamais envoyé** : **aucun consentement prévu**, ni nouvel écran, ni changement des déclarations des stores (**Apple App Privacy et Google Data safety : aucune collecte nouvelle** ; Analytics est déjà déclaré). **Écart connu avec la politique en ligne** : celle-ci **liste de façon détaillée** ce qui est gardé sur le téléphone (art. 11 « Traceurs, identifiants et stockage sur ton téléphone » et la ligne « Données stockées sur ton téléphone » de l'art. 7) et **cite déjà un compteur de ce genre** (« le nombre de fois où la carte « Une précision sur ce que tu partages » t'a été montrée ») ; celui-ci n'y figure pas. **La recommandation d'origine « citer le compteur dans une version 1.4 » est RETIRÉE** : aucune version 1.4 n'est proposée pour ce point.
- **Phrase proposée** : **retirée** (décision du PM du 2026-11-25). Aucun texte publié n'est modifié ; si une version ultérieure des textes est publiée pour une autre raison, l'équipe pourra alors décider d'ajouter une phrase à l'art. 11 et à la ligne « Données stockées sur ton téléphone » de l'art. 7.
- **Risque connu, non analysé par un juriste** [Non vérifié, connaissance générale] : les règles européennes sur l'écriture d'informations dans un terminal distinguent ce qui est « strictement nécessaire » ; un compteur de **fréquence d'une offre commerciale** n'est probablement **pas** « strictement nécessaire » au sens strict. Même situation que le journal local de répartition du temps (risque J48 de `docs/legal/README.md`). **Risque assumé par le PM** (décision du 2026-11-25), jamais écrit « à valider » ; **consigné dans `docs/legal/README.md`, « Risques ajoutés le 2026-11-25 », ligne J76** (compteur d'ouvertures local du paywall périodique non cité par la politique ; précision de l'information RGPD art. 13 ; ePrivacy « strictement nécessaire »).
- **Pas de consentement** à demander (rien de partagé, rien d'identifiant) ; **pas de nouvel accès** (aucune permission) ; **aucune position** dans aucun événement (rappel de `CLAUDE.md`).
- **CGU art. 9** (abonnement) : inchangé (on ne promet aucune fréquence d'affichage de l'offre).

**Documents à aligner par la PR de développement (non modifiés ici)** :
- `docs/tickets/paywall-v2.md` §6.2 (ligne « Paywall automatique… « toutes les 10 visites » : non retenu ») : ajouter « remplacé le 2026-11-25 par `paywall-periodique.md` (toutes les 10 ouvertures) ».
- `docs/specs/paywall-premium.md` (réponse retenue : « aucune relance automatique ») et `docs/tickets/onboarding.md` (§6.12, critère PW4 : « aucune relance automatique ») : préciser que la règle d'**après onboarding** est celle de ce ticket ; l'**écran provisoire** de fin d'onboarding reste **jamais relancé**.
- `docs/contracts/analytics-events.md` : valeur `periodic` de `trigger` (si P7).
- `docs/release/decisions-stores-avant-soumission.md` : lecture des pages Apple et Google, note aux relecteurs (RS1).

---

## 14. Points ouverts avec ma recommandation par défaut

Aucun n'est bloquant : **la valeur recommandée s'applique si tu ne dis rien**.

| # | Question | Recommandation par défaut |
|---|---|---|
| **P1** | Tant que **l'achat n'existe pas** (SRV-8A/I), le seul paywall est « Miff+ arrive bientôt » : l'afficher toutes les 10 ouvertures n'a aucun intérêt commercial et agace. Quand allumer la règle ? | [**TRANCHÉ par le PM le 2026-11-25**] **Règle ACTIVE dans TOUS les builds distribués (release, interne, debug), SANS drapeau d'extinction, même si l'achat réel n'est pas en service** : le paywall affiche son état actuel, « Miff+ arrive bientôt » compris (écran existant réutilisé, aucun nouvel écran). **Risque assumé** : offre périodique sans achat réel, politique Google Play sur les écrans intercalaires, Apple 3.1.2 (§10, RS5). **La croix reste à 3 s.** Recommandation d'origine (éteint en diffusion) **abandonnée**. |
| **P2** | Seuil d'absence **N** | **30 min** (§4.2) |
| **P3** | **Premier appui** et moment exact | Appui sur un élément actionnable (pas défilement, pas balayage), puis **1,5 s sans toucher**, onglet principal affiché ; sinon attente dans la session |
| **P4** | États `on_hold`, `pending`, `revoked` (SRV-8) | **Jamais** montré ; sans lecture du champ `state`, traités comme « sans Miff+ » (inexistants en production) |
| **P5** | **Croix de 3 s** sur une apparition **non sollicitée** (décidée pour « chaque affichage » quand la personne l'avait demandé) | **Appliquée** (D3, tu l'as validée). **À savoir** : c'est le point qui augmente le plus le risque store (§10). Alternatives si tu veux réduire le risque : **croix immédiate** pour le seul déclencheur périodique (une valeur) ; ou délai plus court. **Sans changement de ta part, 3 s.** |
| **P6** | **Phrase de contexte** de l'écran | Aucune (pas de nouveau texte) ; si l'écran livré l'exige, le dev le signale dans la PR |
| **P7** | **Analytics** : distinguer ce déclencheur | Ajouter la valeur `periodic` à `trigger` de `paywall_shown` / `paywall_dismissed` (événements existants, **avec consentement**, **sans** compteur ni durée ni position) pour mesurer la conversion de ce déclencheur. **Si tu préfères zéro changement** : ne rien émettre pour ce déclencheur. |
| **P8** | Remises à zéro | À **tout affichage** du paywall (tous déclencheurs) ; à toute ouverture qui constate Miff+ ; effacement à la déconnexion et à la suppression de compte |
| **P9** | Début du comptage | Première ouverture d'une personne **connectée, onboarding terminé** = 1 ; la 1re apparition ne peut donc pas avoir lieu avant la **10e ouverture réelle** |
| **P10** | **Légal** : citer le compteur ? | [**TRANCHÉ par le PM le 2026-11-25**] **Non** : aucune mise à jour des textes légaux ni de la politique de confidentialité ; **risque assumé** (§13, J76). La « version 1.4 » recommandée d'origine est retirée. |
| **P11** | Session ouverte par une **notification d'alerte** | **Aucun paywall dans toute la session** |
| **P12** | **Interrupteur** dans les Réglages pour ne plus voir ces apparitions | **Non** (la formule gratuite et la croix suffisent ; un interrupteur ajouterait un choix et un texte). Si les avis ou les revues le demandent : ticket séparé |

---

## 15. Hors-scope

- **Achat réel, états d'abonnement serveur, écran Abonnement** : SRV-7, SRV-8A, SRV-8I, PREM-FIN.
- **Paywall à la première arrivée sur le Feed, à la connexion, depuis les Réglages** : toujours **non retenus**.
- **Test A/B**, cadence par pays ou par profil, **réglage à distance** de la cadence ou du seuil : non prévus (valeurs fixes de l'app).
- **Rappels par notification** pour revenir au paywall : **aucun** (jamais de notification commerciale).
- **Personnalisation** de l'offre selon l'usage (nombre de Miffs, de lieux, activité) : non prévue ; aucune donnée d'usage n'est lue.
- **Interrupteur utilisateur** (P12) et **textes légaux** (§13 : recommandation seulement).

---

## 16. Découpage en PR

| PR | Contenu | Prérequis |
|---|---|---|
| **PAY-PER-A** (Android) | Règle pure + tests, magasin du compteur, branchements (ouverture, premier appui, hôte), `PaywallReason.PERIODIC`, **sans drapeau de build** (P1), outil d'essai Debug (carte de diagnostic, seuil abaissable en Debug), CHANGELOG/README, alignement des documents du §13 | Ce ticket validé (porte « ticket validé ») ; PAY-UI-A déjà dans `main` |
| **PAY-PER-I** (iOS) | Mêmes règles, mêmes constantes, branchements iOS, **sans drapeau de build** (P1), tests, CI + TestFlight | PAY-PER-A (parité) |
| *(plus tard)* | Note aux relecteurs, lecture des pages des stores par `release-manager` avant soumission (la règle étant active dans tous les builds, **à faire avant la première version distribuée qui la contient**). **Plus de mise à jour des textes légaux** (décision du 2026-11-25) | « oui » explicite du PM pour toute soumission |

---

*Aucun texte de ce ticket n'a été relu par un juriste ; les risques liés aux règles des stores, à la protection des données et au droit de la consommation sont des risques assumés par le PM (décision du 2026-10-23). Aucun texte légal publié n'a été modifié.*
