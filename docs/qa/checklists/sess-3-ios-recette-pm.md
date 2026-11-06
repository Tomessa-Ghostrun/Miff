# Recette de SESS-3 (iPhone) et SESS-2 (OPPO) : « un compte, un seul appareil » — parcours pour le PM

**Pour qui** : vous, avec **l'iPhone 11 Pro** (iOS 26.5, build TestFlight de la branche `ios/sess-3-appareil-unique`) **et l'OPPO Find X5 Lite** (APK de la branche `android/sess-2-appareil-unique`). Les deux téléphones sont nécessaires : c'est la première fois que vous pouvez utiliser **le même compte sur deux téléphones** et voir l'un prendre la place de l'autre.
**Durée** : environ 20 minutes.
**Ce que vous testez** : quand vous vous connectez avec un compte sur un second téléphone, le premier est déconnecté tout seul, avec un message calme. Pas de statut de l'ancien téléphone qui rassurerait à tort votre cercle (c'est la raison d'être de la fonction, ticket QL17).
**Ce qui est déjà en place côté serveur** : les règles de sécurité de SESS-1 (premier temps) sont déployées. Rien à faire dans la console Firebase pour cette recette.
**Ce qui n'est PAS là (voulu, ne le comptez pas comme un défaut)** : la détection des lieux et l'envoi automatique du statut (LOC-4b : aucun statut n'est envoyé automatiquement sur iPhone pour l'instant, donc le « statut qui rassure à tort » ne peut pas encore se voir) ; la suppression de compte (AUTH-5) ; le second temps du serveur (identifiant rendu obligatoire).
**Limite connue, validée par vous (S1)** : un téléphone **hors ligne** ne peut pas être déconnecté tant qu'il n'a pas de réseau. Il l'est **dès que le réseau revient**, avant d'envoyer quoi que ce soit.
**Rappel de sécurité** : les mots de passe de test se tapent **uniquement sur les téléphones**, jamais dans une conversation. L'identifiant de session (aléatoire) est invisible dans l'app : rien à noter.

Dans la suite : **T** = un compte de test qui a déjà un profil (par exemple le compte « A » des recettes précédentes). **iPhone** et **OPPO** : les deux appareils.

## Avant de commencer (sur ordinateur, 3 min)

1. **iPhone** : GitHub > Actions > **ios-testflight** > Run workflow > branche `ios/sess-3-appareil-unique` > mode **signed** > Run, puis installez le build dans TestFlight. (Claude ne lance jamais ce workflow.) **Ne désinstallez pas l'ancien build** : l'étape H teste la mise à jour par-dessus.
2. **OPPO** : installez l'APK de la PR SESS-2 (page de la PR > Checks > exécution `android` > Artifacts > **miff-debug-apk**).
3. Déconnectez **T** des deux téléphones si ce n'est pas déjà fait (« Se déconnecter »).

## Partie A — Une première connexion sur l'iPhone (3 min)

1. **iPhone** : ouvrez l'app, connectez-vous avec **T** (e-mail ou Google ou Apple).
   - [ ] La connexion se fait **comme avant** : aucun écran de plus, aucun message d'erreur, vous arrivez sur l'accueil.
2. **iPhone** : si T n'a pas de Miff, créez-en une (« Recette SESS-3 ») ; sinon rejoignez/ouvrez la sienne.
   - [ ] La création ou l'entrée **réussit** sans message « ça n'a pas fonctionné » (le serveur a accepté le premier statut avec l'identifiant de session).

## Partie B — Le second téléphone prend la place (4 min)

1. **iPhone** : laissez l'app **ouverte à l'écran** (accueil).
2. **OPPO** : connectez-vous avec **T**.
   - [ ] L'OPPO arrive normalement sur son accueil et **reste connecté**.
3. **iPhone** : regardez l'écran, sans rien toucher (quelques secondes).
   - [ ] L'iPhone revient **tout seul** sur l'écran de connexion.
   - [ ] Le message calme est lu **mot pour mot** : « Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. » (il n'est pas rouge ; ce n'est pas présenté comme une erreur).
   - [ ] **Aucun** message du type « Tu n'as plus accès à la Miff… » (une déconnexion forcée n'est jamais un accès perdu).
4. **iPhone** : touchez un champ de l'écran de connexion et tapez une lettre.
   - [ ] Le message calme **disparaît** dès que vous essayez de vous reconnecter (il ne revient pas tout seul).
5. **iPhone** : reconnectez-vous avec **T**.
   - [ ] La connexion marche, vous retrouvez votre Miff.
6. **OPPO** : regardez l'écran (quelques secondes).
   - [ ] L'OPPO est **à son tour déconnecté**, avec le même message (en anglais si l'OPPO est en anglais : « Your account is now being used on another phone. You've been signed out of this one. »). **La dernière connexion gagne** (critère 4 du ticket).
7. [ ] Les deux téléphones ne se « disputent » pas : rien ne se reconnecte tout seul, personne n'est déconnecté en boucle (attendez 30 secondes sans toucher à rien).

## Partie C — L'iPhone était fermé ou en arrière-plan (3 min)

1. **iPhone** connecté avec **T**. Remontez le doigt depuis le bas pour **fermer complètement** l'app (ou mettez-la en arrière-plan 1 minute).
2. **OPPO** : connectez-vous avec **T** (il prend la place).
3. **iPhone** : rouvrez l'app.
   - [ ] Vous arrivez sur l'**écran de connexion avec le message calme** (pas sur l'accueil, pas sur la Miff). Cela peut prendre une ou deux secondes de chargement.
   - [ ] Si l'app a été **fermée complètement** : même résultat à l'ouverture (la session est vérifiée **avant** de montrer vos données).

## Partie D — Un téléphone hors ligne (4 min)

1. **iPhone** : connectez-vous avec **T** (l'OPPO est déconnecté par la même occasion). Puis activez le **mode avion** sur l'iPhone.
2. **OPPO** : connectez-vous avec **T**.
3. **iPhone** (toujours en mode avion) : ouvrez l'app.
   - [ ] L'iPhone **n'est pas déconnecté** tant qu'il est hors ligne (c'est la limite connue). L'accueil s'affiche avec ce que le téléphone avait gardé, sans attente longue.
4. **iPhone** : désactivez le mode avion et attendez quelques secondes.
   - [ ] L'iPhone est déconnecté **dès le retour du réseau**, avec le message calme.
5. **Variante : se déconnecter à la main hors ligne** (2 min) : **iPhone** : connectez-vous avec **T** (l'OPPO est déconnecté), mode avion sur l'iPhone, **OPPO** : connectez-vous avec **T**, puis **iPhone** (toujours en mode avion) : **Se déconnecter** à la main, puis désactivez le mode avion.
   - [ ] L'**OPPO reste connecté** : se déconnecter à la main ne chasse jamais le téléphone qui a pris la place.

6. **Coupure courte** : **iPhone** connecté avec **T**, personne d'autre n'est connecté. Activez le mode avion 10 secondes puis désactivez-le.
   - [ ] **Rien ne se passe** : pas de déconnexion, pas de message, l'accueil reste affiché (la session est relue en silence au retour du réseau).

## Partie E — Se déconnecter à la main et réinstaller (3 min)

1. **iPhone** connecté avec **T**, accueil ouvert : **Se déconnecter**.
   - [ ] Vous arrivez sur l'écran de connexion **sans** le message « autre téléphone » (c'est vous qui avez choisi de partir).
   - [ ] Reconnectez-vous : tout fonctionne. (Facultatif, console Firebase : le document `users/{uid}/session/active` existe pendant la connexion et disparaît après la déconnexion volontaire.)
2. **Réinstallation** : **iPhone** connecté avec **T**. Supprimez l'app (appui long > Supprimer l'app), réinstallez-la depuis TestFlight, ouvrez-la.
   - [ ] L'app s'ouvre comme une **première installation** (carrousel ou inscription), **pas** connectée (comportement déjà en place pour les réinstallations).
   - [ ] Connectez-vous avec **T** : ça marche ; si l'OPPO était connecté avec **T**, il est déconnecté (la dernière connexion gagne).

## Partie F — Mise à jour par-dessus un ancien build (1 min)

C'est le cas d'une personne qui avait l'app **avant** cette fonction : connectée, sans session d'appareil.

- [ ] Avec l'ancien build de l'iPhone encore installé et **T connecté**, installez le build SESS-3 **par-dessus** (TestFlight > Mettre à jour) et ouvrez l'app : vous restez **connecté**, sans message ni écran de plus (la session est écrite en silence). Puis refaites la partie B.2 : l'iPhone est bien déconnecté quand l'OPPO se connecte.
  *(Si l'iPhone a déjà été mis à jour pour la partie A, cette étape n'est plus faisable : passez-la.)*

## Partie F2 — Compte tout juste créé puis panneau refusé, sans réseau (2 min, facultatif)

Avec un **nouveau** compte Google ou Apple (écran de connexion, « Continuer avec Google »), sur le panneau de consentement :

- [ ] **Mode avion** activé, touchez **Annuler** : un message calme explique que ça n'a pas fonctionné ; **le compte n'est pas supprimé** (vous restez sur le panneau). Désactivez le mode avion, touchez de nouveau **Annuler** : « Pas de souci, ton compte n'a pas été créé… » sur l'écran de connexion.
- [ ] Variante : si, malgré tout, le compte est gardé (par exemple en fermant la fenêtre Google/Apple de reconnexion), le panneau reste ouvert ; continuez normalement jusqu'à une Miff, puis connectez le même compte sur l'OPPO : l'**iPhone est bien déconnecté** (sa session a été réécrite).

## Partie G — Ton, langue, lecture (2 min)

- [ ] Relisez à voix haute le message calme : « est-ce que je le dirais à un proche inquiet ? » Il informe, il ne culpabilise pas et n'alarme pas. Notez toute phrase qui sonne froide.
- [ ] Passez l'iPhone en **English** (Réglages iOS > Général > Langue et région) puis provoquez le message (partie B) : « Your account is now being used on another phone. You've been signed out of this one. »
- [ ] Taille de texte au **maximum** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte) : le message est lisible en entier, on peut faire défiler l'écran de connexion, rien n'est coupé.
- [ ] (Si vous utilisez **VoiceOver**) à l'arrivée sur l'écran de connexion après une déconnexion forcée, le message est **lu à voix haute** sans qu'il faille le chercher.

## À me remonter

Tout écart de texte ; tout moment où un téléphone n'est **pas** déconnecté alors que l'autre vient de se connecter (en ligne) ; toute **boucle** (deux téléphones qui se déconnectent à tour de rôle sans que vous touchiez à rien) ; tout message « Tu n'as plus accès… » ou rouge pendant une déconnexion forcée ; un écran « Réessayer » à la connexion (il est possible si le réseau est mauvais : notez si « Réessayer » rétablit tout) ; un lancement de l'app qui reste longtemps sur le chargement hors ligne ; une désinstallation/réinstallation qui laisse l'iPhone connecté.
