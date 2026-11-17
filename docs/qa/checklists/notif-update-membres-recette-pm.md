# Recette PM de la notification « Un proche a donné des nouvelles » (SRV-4B, côté serveur)

**Pour qui** : vous, avec **deux comptes de test** dans **une même Miff de test** (aucun vrai proche), l'un sur l'**OPPO Find X5 Lite** (Android 13), l'autre sur l'**iPhone 11 Pro** (iOS, build TestFlight), un appareil par compte.
**Ticket** : `docs/tickets/notification-update-membres.md` §18 à §20 (critères U1 à U8, P1 à P6, J1 à J5, F1 à F6, C1 à C3) et décision du PM du 2026-11-12 sur le son (canal « par défaut » : son et vibration selon le téléphone ; heures calmes 22 h – 7 h en mode passif).
**Ce que cette recette couvre** : la **fonction du serveur** `notifierUpdate` (PR SRV-4B). La réception dans les applications (réglage, affichage, canal, ouverture du Feed) et l'enregistrement des jetons relèvent des PR SRV-2A / SRV-2I / SRV-4A / SRV-4I et de leurs recettes.

## Ce qui est déjà vérifié sans vous (à l'émulateur, dans la CI)

Les vecteurs V1 à V13 du ticket §19, la forme exacte des messages (Android : données seulement ; iOS : alerte avec clés de texte), l'absence de donnée sensible dans les messages et dans les journaux, le plafond d'une notification par auteur, par Miff et par heure, l'idempotence (même événement rejoué ou en parallèle), la purge d'un jeton refusé, les 2 nouvelles tentatives au plus, les heures calmes, le budget de lectures (13 lectures et 1 écriture pour une Miff de 4 ; 1 lecture pour une update plafonnée ou rejouée), l'envoi à l'appareil de la session active seulement (un téléphone dont le compte s'est déconnecté, ou repris par un autre compte, ne reçoit rien), la limite à 20 destinataires. **Un faux client remplace Google/Apple** : la **livraison réelle** n'est vérifiable que par les étapes ci-dessous.

## Prérequis (à faire par vous, avec Claude, dans cet ordre ; rien n'est fait par un agent)

| # | Prérequis | Où / comment |
|---|---|---|
| P1 | Alerte de budget Blaze en place | `docs/dev/deploiement-functions.md` §1.1 |
| P2 | **Compte de service dédié** `notify-update@miff-3d283.iam.gserviceaccount.com` créé avec ses 4 rôles de projet et **sans clé**, et le droit « Utilisateur du compte de service » pour votre compte ; **après le premier déploiement** : le droit « Cloud Run Invoker » sur le seul service `notifierupdate` (sans lui, aucune notification : les journaux disent « request was not authenticated ») | `deploiement-functions.md` §1.4 A à D, puis D bis |
| P3 | Règle **TTL** sur `notifState.expireAt` créée (**après la première notification**, la collection n'existant qu'à partir de là) | `deploiement-functions.md` §1.4 E |
| P4 | **Règles Firestore des jetons d'appareil déployées** (branche `backend/jetons-appareil`, SRV-2R) : c'est vous qui tapez la commande, sur « oui » | `docs/dev/deploiement-regles.md` |
| P5 | **Applications avec enregistrement du jeton** installées sur l'OPPO et l'iPhone (SRV-2A / SRV-2I), notifications autorisées, réglage « Quand un proche donne des nouvelles » activé | recettes SRV-2A / SRV-2I |
| P6 | **Clé APNs `.p8`** déposée par vous dans la console Firebase du projet **Miff** (jamais ailleurs), capacité Push Notifications activée | ticket §12 |
| P7 | Textes légaux (SRV-3) publiés ; aucune fiche de store ne dit encore « pas de notification » | ticket §15, §16 |
| P8 | **« Oui » explicite** à Claude, puis **vous** tapez : `npx firebase deploy --only functions:notifierUpdate --project miff-3d283` depuis `C:\dev\miff-deploy\backend` | `deploiement-functions.md` §11.3 |

## Parcours (le compte 1 est sur l'OPPO, le compte 2 sur l'iPhone, sauf mention contraire)

| # | Étape | Résultat attendu | Critère |
|---|---|---|---|
| 1 | Vérifiez dans la console Firebase > Functions : `notifierUpdate`, région **europe-west9**, instances maximales **5**, mémoire **256 Mio**, compte **`notify-update@…`** (et non « Compute Engine default ») | Conforme | C1 |
| 2 | L'iPhone est **verrouillé**, l'app fermée. Le compte 1 publie une update | Notification **discrète** sur l'iPhone : « {Prénom} a donné des nouvelles / Retrouve-les dans ta Miff. », **sans photo, sans lieu, sans nom de famille, sans nom de Miff**. Rien sur l'OPPO (auteur) | U1, U2, U4 |
| 3 | Notez le délai entre la publication et la notification | Moins de 2 minutes dans les conditions normales | U1 |
| 4 | **Inverse** : l'OPPO est verrouillé, app balayée ; le compte 2 publie | Notification reçue sur l'OPPO ; **notez le délai**. Si elle n'arrive pas : voir « Si ça ne marche pas » | U1, R5 |
| 5 | Le compte 1 publie une **2e update dans l'heure** | **Pas** de 2e notification ; la photo est bien dans le Feed | P1 |
| 6 | Journaux : `npx firebase functions:log --only notifierUpdate -n 30 --project miff-3d283` | Une ligne `notif.update.bilan` avec `envoyes: 1`, `lectures: 7` (2 comptes : 1 contrôle préalable + 1 Miff + 2 fiches + 1 session + 1 téléphone + 1 vérification finale), `ecritures: 1` ; une ligne `notif.update.ignore` avec `motif: plafond` et `lectures: 1` ; **aucun nom, identifiant, jeton, position** dans le texte | F4, C2 |
| 7 | Console Firebase > Firestore : collection `notifState` | Un document pour la Miff de test, avec des **dates** et des identifiants seulement ; règle TTL visible | J5 |
| 8 | L'iPhone **coupe** « Quand un proche donne des nouvelles » ; 1 h plus tard, le compte 1 publie. **Puis remettez le réglage sur activé** | Rien sur l'iPhone | U3 |
| 9 | Le compte 2 **arrête son partage** ; le compte 1 publie (1 h plus tard). **Puis le compte 2 remet son partage actif** | Rien pour le compte 2 | U3 |
| 10 | **Déconnexion** du compte 2, puis publication du compte 1 (1 h plus tard) | Rien ; `users/{compte 2}/devices` vide dans Firestore. **Le compte 2 se reconnecte ensuite avec le partage actif et le réglage « Quand un proche donne des nouvelles » activé** | U3, J2 |
| 11 | **Avant de commencer, vérifiez que le compte 2 est reconnecté, que son partage est actif et que le réglage est activé** (sinon l'absence de notification ne prouverait rien). **Désinstallez** l'app de l'iPhone, le compte 1 publie (1 h plus tard) ; revérifiez les journaux | Une ligne `notif.update.fcm-erreur` avec `code: 1` possible (jeton inconnu) ; le document `devices` du compte 2 a été **supprimé** par le serveur | J4 |
| 12 | **Nuit simulée** : changez l'heure (ou le fuseau) de l'iPhone pour 23 h ; le compte 1 publie (1 h plus tard) | Livraison **sans son, sans vibration, sans allumer l'écran** (iOS : « passif ») | P4 |
| 13 | **Jour** : l'iPhone en mode sonore, à 14 h ; le compte 1 publie | Son/vibration selon le téléphone (décision du 2026-11-12) | Q2 |
| 14 | Toucher la notification | Le Feed s'ouvre sur la carte de l'auteur (recette SRV-4A / SRV-4I) | U6 |
| 15 | OPPO : **« forcer l'arrêt »** de Miff dans les réglages du téléphone, puis publication du compte 2 | Rien tant que l'app n'est pas rouverte (**comportement Android attendu**) ; notez-le | R5 |
| 16 | Console Google Cloud > Facturation > Rapports (le lendemain) | Dépense Cloud Functions de l'ordre de 0 € | C1 |

## Si ça ne marche pas (copiez le message à Claude, ne donnez aucun rôle large)

| Symptôme | Cause probable | Que faire |
|---|---|---|
| Aucune notification, journal `notif.update.bilan` avec `envoyes: 0` et `ecartes` > 0 | Le partage est coupé, le réglage est coupé, ou aucun jeton n'est enregistré | Vérifier P4 et P5 ; ouvrir Firestore `users/{compte}/devices` |
| `notif.update.fcm-erreur` avec **`code: 8`** | Clé APNs absente ou refusée (iPhone) | Reprendre P6 avec Claude (identifiant de clé, identifiant d'équipe, projet Miff) |
| `code: 9` | Message refusé par Google (défaut du message) | Copier la ligne à Claude |
| Aucune ligne de journal du tout | Les événements n'arrivent pas à la fonction (droit de réception du compte dédié) | Copier le message à Claude : un droit précis peut manquer sur `notify-update@…`, **jamais** Éditeur/Propriétaire |
| La fonction échoue à l'envoi avec « permission denied » | Le compte dédié n'a pas le rôle d'envoi FCM | Vérifier la ligne `notify-update@…` dans IAM (4 rôles) |
| Notifications en retard sur l'OPPO | Économie d'énergie du constructeur | Aide « Si les rappels n'arrivent pas » de l'application |

## Arrêt rapide et retour arrière

`docs/dev/deploiement-functions.md` §11.5 (suppression de la fonction, ou retrait du rôle d'envoi du compte dédié) et §11.6.

## Ce que cette recette ne peut pas prouver

La livraison dans des conditions réelles variées (réseau, mode économie d'énergie, application forcée à l'arrêt), le rendu exact par le système iOS des clés de texte (`notif_circle_update_title` / `_body`), l'environnement APNs de production de TestFlight, la suppression automatique de `notifState` (jusqu'à 24 h après l'échéance), et le droit exact minimal du compte de service (à confirmer au premier déploiement).
