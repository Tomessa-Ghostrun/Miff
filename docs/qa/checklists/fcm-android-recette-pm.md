# Recette Android des notifications push entre proches (SRV-2 / N9) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test dans la même Miff** (A sur l'OPPO, B sur un second téléphone ou sur l'iPhone).
**Ticket** : `docs/tickets/notification-update-membres.md` (critères U1 à U8, P1 à P6, J1 à J5), décision du PM du 2026-11-12 (son et vibration selon le téléphone le jour, mode passif la nuit).
**PR** : branche `android/fcm-client` (client Android seulement).

## À savoir avant de commencer

- **L'essai de bout en bout (parties 3 à 7) est impossible tant que vous n'avez pas déployé, sur un « oui » explicite de votre part, DEUX choses sur le projet `miff-3d283` et rien d'autre** : (1) les **règles Firestore** étendues du registre d'appareils (`tz`, `notify` : branche `backend/jetons-appareil`) ; (2) les **fonctions Cloud** d'envoi (la fonction « update publiée », SRV-4B). Aucun agent ne déploie. Tant que (1) n'est pas fait, l'app **n'enregistre rien** (les règles actuelles refusent le nouveau document, sans message à l'écran) ; tant que (2) n'est pas fait, un jeton peut être enregistré mais **aucune notification n'arrive**.
- Les parties **0 à 2** se font **sans aucun déploiement** : elles vérifient le consentement, les réglages et l'affichage d'une notification de test.
- L'APK à installer est celui **signé par la CI** (artefact `miff-debug-apk` de la PR). Désinstallez l'ancienne version d'abord.
- **Les testeurs qui avaient déjà autorisé les notifications avant cette version ne sont PAS enregistrés, et devront rallumer l’interrupteur** : dans Réglages > Notifications, couper puis rallumer « Recevoir des notifications » (l’ancienne explication ne parlait pas de l’identifiant du téléphone ; le consentement est enregistré au moment où l’on rallume, sur un écran qui porte la phrase). Sinon, utiliser un compte neuf. Aucun écran ne le rappelle aux testeurs : à leur dire.
- **Console** : toute vérification dans la console Firebase se fait dans le projet **Miff (`miff-3d283`)**, jamais celui de Ghostrun.
- Les textes sont à **relire à voix haute** : notez toute phrase qui vous semble trop dure ou anxiogène.

## Partie 0 : consentement (rien n'est enregistré avant)

| # | Étape | Résultat attendu |
|---|---|---|
| 0.1 | Compte neuf, onboarding jusqu'à l'amorce « Un rappel, seulement quand c'est utile ». | Le texte dit que Miff **prévient quand un proche donne des nouvelles** et **enregistre un identifiant du téléphone** pour cela. (À relire : proposition de rédaction.) |
| 0.2 | Touchez **Plus tard**. Console Firebase > Firestore > `users/{uid}/devices`. | **Aucun document** `devices` pour ce compte. |
| 0.3 | Réglages > Notifications. | L'interrupteur « Quand un proche donne des nouvelles » existe (section **Pour tes proches**), **activé**, mais l'autorisation du téléphone n'est pas donnée : rien n'est enregistré. |
| 0.4 | Compte neuf : **Autoriser** sur l'amorce, puis **Autoriser** dans la fenêtre du système. | (Après les deux déploiements, ou règles seules) un document `users/{uid}/devices/{identifiant}` apparaît, avec exactement `token`, `platform = android`, `appVersion`, `tz` (votre fuseau), `notify.updates = true`, `updatedAt`, `createdAt` (rien d’autre : ni position, ni nom). **Le jeton est dans le document mais n'est lisible par aucun autre compte.** |
| 0.5 | Même compte, refus dans la fenêtre du système (autre compte neuf). | Aucun document `devices`. |

## Partie 1 : réglages

| # | Étape | Résultat attendu |
|---|---|---|
| 1.1 | Réglages > Notifications (permission accordée). | Section **Pour tes proches** avec « **Quand un proche donne des nouvelles** » + « Une notification quand un proche de ta Miff publie une update. » |
| 1.2 | Coupez cet interrupteur. | Le document `devices` passe à `notify.updates = false` (le jeton reste) en quelques secondes. |
| 1.3 | Rallumez-le. | `notify.updates = true`. |
| 1.4 | Coupez **Recevoir des notifications** (interrupteur général). | Au retour au premier plan, le document `devices` **disparaît**. Le rallumer le recrée. |
| 1.5 | Dans les réglages du téléphone, coupez les notifications de Miff, revenez dans l'app. | Le document `devices` disparaît au retour dans l'app. |
| 1.6 | Réglages Android > Notifications > Miff : repérez les catégories. | **Nouvelles des proches** (son et vibration activés) et **Nouvelles des proches (nuit calme)** (sans son ni vibration) existent. |

## Partie 2 : l'affichage d'une notification (pas d'essai possible sans serveur)

Aucun moyen sûr de simuler à la main un message de Google : l'affichage (texte, canaux, remplacement, plafonds, nuit, premier plan, appui) est couvert par les tests automatiques de la PR. L'essai réel passe par la partie 3, après les deux déploiements.

## Partie 3 : bout en bout (après les deux déploiements)

| # | Étape | Résultat attendu |
|---|---|---|
| 3.1 | L'OPPO (compte A) est **verrouillé**, l'app **fermée normalement** (retour à l'accueil, pas balayée). B publie une update. | Notification « **{Prénom de B} a donné des nouvelles** / Retrouve-les dans ta Miff. », **avec son et vibration** (si le téléphone est en mode sonore), en moins de 2 minutes. **Notez le délai.** Aucune photo, aucun lieu, aucun nom de Miff, aucun nom de famille. (U1, U4) |
| 3.2 | Idem, mais **balayez l'app** de la liste des applications récentes avant. | Notez si la notification arrive et après combien de temps. **C'est la mesure OPPO** (voir `docs/dev/fcm-android.md`). |
| 3.3 | Idem, mais après **Réglages Android > Applications > Miff > Forcer l'arrêt**. | **Rien n'arrive tant que vous n'ouvrez pas Miff** (comportement d'Android, attendu). À l'ouverture, le Feed montre la nouvelle. |
| 3.4 | Touchez la notification. | Miff s'ouvre sur le **Feed de la bonne Miff**. La ligne correspondante du centre est lue. (U6) |
| 3.5 | App **ouverte sur le Feed**, B publie. | **Aucune notification système** ; le Feed se met à jour ; une ligne du centre apparaît (la cloche a un point). (U7) |
| 3.6 | B publie une **seconde fois dans l'heure**. | Pas de seconde notification (plafond côté serveur) ; la photo est dans le Feed. (P1) |
| 3.7 | Votre propre update. | Vous ne recevez **rien** (U2). |

## Partie 4 : plafonds, remplacement, nuit

| # | Étape | Résultat attendu |
|---|---|---|
| 4.1 | Deux proches différents publient. | **Deux** notifications, une par auteur. |
| 4.2 | Un même proche publie à plus d'une heure d'écart, sans que vous ayez ouvert l'app. | La seconde **remplace** la première dans le tiroir ; le centre garde **deux** lignes. |
| 4.3 | Passez le téléphone à 23 h (réglages de date et heure) puis B publie. | Notification **sans son ni vibration** (canal « nuit calme »), sans allumer l'écran. (P4) |
| 4.4 | Coupez « Nuit calme » dans Réglages, même test. | Son et vibration comme le jour. |
| 4.5 | Plus de 6 notifications dans la journée. | À partir de la septième : **plus de notification**, seulement une ligne du centre. (P6) |

## Partie 5 : les gardes

| # | Étape | Résultat attendu |
|---|---|---|
| 5.1 | Coupez « Quand un proche donne des nouvelles », B publie. | Rien (U3). |
| 5.2 | Le compte A arrête son partage, B publie. | Rien. |
| 5.3 | **Déconnexion** de A puis B publie. | Rien ; le document `devices` de A est **supprimé** (console). (J2) |
| 5.4 | **Suppression** du compte de test. | Plus de document `devices` pour ce compte. |
| 5.5 | Connectez le même compte sur un **second téléphone**. | Un seul document `devices` subsiste : celui du nouvel appareil. (J3) |
| 5.6 | Mode voyage de A actif, B publie. | A reçoit normalement ; aucun texte ne parle du voyage. (U8) |

## Partie 6 : le centre et la vie privée

| # | Étape | Résultat attendu |
|---|---|---|
| 6.1 | Ouvrez le centre (cloche). | Une ligne « {Prénom} a donné des nouvelles » par message, avec l'heure. **Seul type de ligne qui contient un prénom** ; aucune photo, aucun lieu. |
| 6.2 | Déconnexion. | Le centre est vidé. |
| 6.3 | Écran verrouillé avec « aperçus masqués » dans les réglages Android. | Le contenu est masqué selon le réglage du téléphone. |

## Partie 7 : aide

| # | Étape | Résultat attendu |
|---|---|---|
| 7.1 | Réglages > Notifications > « Si les rappels n'arrivent pas ». | L'écran d'aide parle aussi des **nouvelles des proches** qui peuvent arriver en retard, et de « **Forcer l'arrêt** » (rien n'est perdu). Ton calme, sans reproche. |

## Ce qu'il faut me remonter

- Le **délai** de la notification sur l'OPPO (3.1 et 3.2), app fermée, balayée, après arrêt forcé.
- Toute phrase trop dure ou anxiogène.
- Toute notification qui n'aurait pas dû arriver, ou qui contient plus que le prénom et la phrase fixe.
- Dans la console : le **nombre d'invocations** et d'**erreurs** de la fonction, et le coût du jour (doit rester dans la fourchette du ticket §14).

## Partie 8 : les deux contrôles de la revue indépendante

| # | Étape | Résultat attendu |
|---|---|---|
| 8.1 | Regardez les quatre endroits où l’on dit « oui » aux notifications : l’amorce de fin d’onboarding, Réglages > Notifications (sous « Recevoir des notifications »), la carte du Feed « Un rappel si tu oublies… ? » (après « Plus tard » puis une première update), le centre quand les notifications sont coupées. | **Chacun porte la phrase** « … Miff enregistre un identifiant technique de ton téléphone, fourni par Google » (réglages : « Pour t’envoyer ces notifications, … »). Aucun « oui » sans cette phrase sous les yeux. |
| 8.2 | Avec deux comptes dans deux Miffs différentes (A dans la Miff 1, B dans la Miff 2) sur le même téléphone, l’un après l’autre : connectez A, notez la Miff 1 ; déconnectez, connectez B. Un proche de la Miff 1 publie. | B ne reçoit **rien** (ni notification, ni ligne dans le centre) : un message sur une Miff dont le compte n’est pas membre est ignoré. C’est aussi vrai si l’app était **fermée** (démarrage à froid). |
| 8.3 | Quittez une Miff (Premium) puis un proche de cette Miff publie. | Rien pour vous. |
