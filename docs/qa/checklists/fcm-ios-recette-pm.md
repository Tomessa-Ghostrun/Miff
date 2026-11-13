# Recette iPhone des notifications push entre proches (SRV-2I / SRV-4I) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26), avec **deux comptes de test dans la même Miff** (A sur l'iPhone, B sur l'OPPO ou un second téléphone).
**Ticket** : `docs/tickets/notification-update-membres.md` (critères U1 à U8, P1 à P5, J1 à J5), décision du PM du 2026-11-12 (son et vibration selon le téléphone le jour, mode passif la nuit).
**PR** : branche `ios/fcm-client` (client iOS seulement). Notes techniques : `docs/dev/fcm-ios.md`. Vos étapes Apple et Firebase : `docs/dev/apns-etapes-pm.md`.

## À savoir avant de commencer

- **L'essai de bout en bout (parties 3 à 6) est impossible tant que vous n'avez pas fait, sur un « oui » explicite de votre part, TOUT ceci** : (1) déposé la **clé APNs** dans la console Firebase du projet **Miff (`miff-3d283`)** et activé **Push Notifications** sur `com.tomessa.miff` (`docs/dev/apns-etapes-pm.md`) ; (2) déployé les **règles Firestore** du registre des appareils (branche `backend/jetons-appareil`) ; (3) déployé la **fonction** d'envoi (SRV-4B). Aucun agent ne déploie ni ne touche à la clé. Tant que (2) n'est pas fait, l'app **n'enregistre rien** (les règles actuelles refusent le nouveau document, sans aucun message à l'écran) ; tant que (3) n'est pas fait, un jeton peut être enregistré mais **aucune notification n'arrive**.
- Les parties **0 à 2** se font **sans fonction déployée** (la partie 0.4 demande les règles).
- Le build vient du workflow **ios-testflight** (TestFlight). Une app TestFlight utilise l'environnement **production** d'Apple.
- **Les testeurs qui avaient autorisé les notifications avant cette version** revoient **une fois** la fenêtre « Te prévenir, seulement quand c'est utile ! » (avec le nouveau texte) : c'est voulu, c'est elle qui donne l'accord.
- **Réglages > Notifications dans l'app n'existe pas encore sur iOS** (PR NOTIF-P0 en attente) : l'interrupteur « Quand un proche donne des nouvelles » est actif par défaut, sans bouton pour l'instant. Pour tout couper : réglages d'iOS > Notifications > Miff.
- Les textes sont à **relire à voix haute** : notez toute phrase trop dure ou anxiogène.

## Partie 0 : consentement (rien n'est enregistré avant)

| # | Étape | Résultat attendu |
|---|---|---|
| 0.1 | Compte neuf, onboarding jusqu'à la fenêtre « Te prévenir, seulement quand c'est utile ! ». | Le texte du bas dit que Miff **prévient quand un proche donne des nouvelles** et **enregistre un identifiant technique de votre téléphone, fourni par Apple pour envoyer les notifications**, et qu'on peut tout couper dans les réglages du téléphone. (Proposition de rédaction à relire.) |
| 0.2 | Sans toucher « Autoriser », console Firebase (projet Miff) > Firestore > `users/{uid}/devices`. | **Aucun document** `devices` pour ce compte. |
| 0.3 | Touchez **Autoriser**, puis **Autoriser** dans la fenêtre d'iOS. (Règles déployées.) | Un document `users/{uid}/devices/{identifiant}` apparaît en quelques secondes avec exactement `token`, `platform = ios`, `appVersion`, `tz` (votre fuseau), `notify.updates = true`, `updatedAt`, `createdAt` (rien d'autre : ni position, ni nom). **Le jeton est dans le document mais n'est lisible par aucun autre compte.** |
| 0.4 | Autre compte neuf : **Autoriser**, puis **Ne pas autoriser** dans la fenêtre d'iOS. | Aucun document `devices`. |
| 0.5 | Compte qui avait autorisé avant cette version (installez le build par-dessus). | La fenêtre revient **une fois** ; avant de la toucher, aucun document `devices` ; après « Autoriser », le document apparaît. |

## Partie 1 : retirer et redonner l'autorisation

| # | Étape | Résultat attendu |
|---|---|---|
| 1.1 | Réglages d'iOS > Notifications > Miff : coupez **Autoriser les notifications**, revenez dans Miff. | Le document `devices` **disparaît** (au retour dans l'app). |
| 1.2 | Rallumez dans iOS, revenez dans Miff. | Si la fenêtre d'explication revient, touchez « Autoriser » : le document est recréé. |

## Partie 2 : déconnexion et suppression de compte

| # | Étape | Résultat attendu |
|---|---|---|
| 2.1 | **Se déconnecter** de A. | Le document `devices` de A est **supprimé** (console) ; les notifications déjà affichées pour A disparaissent du tiroir. (J2) |
| 2.2 | Se reconnecter avec A. | Aucune fenêtre d'explication nouvelle (l'accord de ce compte est gardé) ; le document est recréé. |
| 2.3 | Mode avion, **se déconnecter**, puis réseau rétabli. | La déconnexion réussit sans erreur affichée. **Notez** si, B publiant ensuite, l'iPhone reçoit quand même quelque chose (ne devrait pas). Le document peut rester dans la console jusqu'au prochain envoi (inerte). |
| 2.3 bis | Après 2.3, sur ce même iPhone, **connectez le compte B** (autre compte, autre Miff ou même Miff). Un proche de A publie dans la Miff de A. | **Rien d'affiché** sur l'iPhone de B. Si une notification apparaît quand même, **notez le prénom affiché et l'heure** : c'est le cas résiduel de `docs/dev/fcm-ios.md` §4 (alerte affichée par iOS sans extension de service). |
| 2.4 | Connectez A sur un **second téléphone**. | Un seul document `devices` subsiste : celui du nouvel appareil. (J3) |
| 2.5 | **Supprimer** le compte de test. | Plus aucun document `devices` pour ce compte. |

## Partie 3 : bout en bout (après la clé et les deux déploiements)

| # | Étape | Résultat attendu |
|---|---|---|
| 3.1 | L'iPhone (compte A) est **verrouillé**, Miff **fermée**. B publie une update. | Notification « **{Prénom de B} a donné des nouvelles** / Retrouve-les dans ta Miff. », **avec le son du téléphone** s'il est en mode sonore, en moins de 2 minutes. **Notez le délai.** Aucune photo, aucun lieu, aucun nom de Miff, aucun nom de famille. (U1, U4) |
| 3.2 | Touchez la notification. | Miff s'ouvre sur le **Feed de la bonne Miff**. (U6) |
| 3.3 | **Même chose à froid** : fermez Miff (balayage), B publie, touchez la notification. | Le Feed de la bonne Miff s'ouvre (après un court chargement). |
| 3.4 | Miff **ouverte sur le Feed**, B publie. | **Aucune notification du système** ; le Feed se met à jour. (U7) |
| 3.5 | B publie une **seconde fois dans l'heure**. | Pas de seconde notification (plafond côté serveur) ; la photo est dans le Feed. (P1) |
| 3.6 | Votre propre update (A publie). | Vous ne recevez **rien**. (U2) |
| 3.7 | Deux proches différents publient. | Deux notifications, **regroupées** par iOS (une pile par auteur). (P3) |
| 3.8 | Écran verrouillé avec « aperçus : jamais » dans iOS. | Le contenu est masqué selon le réglage du téléphone. |

## Partie 4 : nuit

| # | Étape | Résultat attendu |
|---|---|---|
| 4.1 | Réglez l'iPhone sur un fuseau où il est 23 h (ou l'heure à 23 h), B publie. | Notification **sans son, sans allumer l'écran** (niveau « passif »). (P4) |
| 4.2 | Le matin (7 h passées), B publie. | Son comme le jour. |

## Partie 5 : les gardes

| # | Étape | Résultat attendu |
|---|---|---|
| 5.1 | Retirer A de la Miff (ou A quitte la Miff), B publie. | Rien sur l'iPhone de A. (U3) |
| 5.2 | A arrête son partage, B publie. | Rien. |
| 5.3 | Mode voyage de A actif, B publie. | A reçoit normalement ; aucun texte ne parle du voyage. (U8) |
| 5.4 | Touchez une notification d'une Miff dont A n'est plus membre (si elle traînait dans le tiroir). | L'app s'ouvre, mais **ne navigue vers aucune Miff**. |

## Partie 6 : console et coût

| # | Étape | Résultat attendu |
|---|---|---|
| 6.1 | Console Firebase (projet Miff) > Functions : invocations, erreurs, coût du jour. | Dans la fourchette de `docs/tickets/notification-update-membres.md` §14. |
| 6.2 | Console Firebase > Cloud Messaging. | La clé APNs importée est bien listée. |

## Ce qu'il faut me remonter

- Le **délai** des notifications (3.1), app fermée.
- Toute phrase trop dure ou anxiogène, et le texte de la fenêtre d'explication (0.1).
- Toute notification qui n'aurait pas dû arriver, ou qui contient plus que le prénom et la phrase fixe.
- Si le **son** ne suit pas le mode du téléphone (sonore / silencieux).
- Le résumé de l'exécution du workflow : la ligne « Entitlement Push Notifications (aps-environment) présent ».
