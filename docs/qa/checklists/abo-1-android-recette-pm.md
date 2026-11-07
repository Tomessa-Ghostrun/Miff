# Recette Android de ABO-1 (écran « Abonnement » en lecture seule) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **trois comptes jetables** (un Miff+ accordé à la main, un gratuit, un Miff+ expiré).
**Durée** : environ 20 minutes.
**Ce qui n'est PAS dans cette recette** : l'achat, l'essai gratuit, les prix, le prochain paiement, le changement ou l'arrêt de l'abonnement (rien de payant n'est branché : c'est le ticket Premium), l'iPhone (ABO-2).

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- Les trois comptes doivent **avoir au moins une Miff** (créée ou rejointe) pour voir les Réglages complets ; le compte sans Miff sert à l'étape 12.

## Préparer les trois comptes (console Firebase, procédure `docs/dev/premium-manuel.md`)

Pour chaque compte, copiez son **UID** (Authentication > Users), puis Firestore > collection `entitlements` > document dont l'ID est cet UID. **Attention à la casse** des noms de champs (`expiresAt`, `updatedAt`).

| Compte | Document `entitlements/{uid}` |
|---|---|
| **A : Miff+ accordé à la main** | `active` = `true` (boolean), `expiresAt` = une date **future** par exemple le 12/01/2027 (timestamp), `source` = `manual` (string), `updatedAt` = maintenant (timestamp) |
| **B : gratuit** | **aucun document** |
| **C : Miff+ expiré** | mêmes champs que A, mais `expiresAt` dans le **passé** (par exemple hier), ou `active` = `false` |

## Partie A : compte Miff+ accordé à la main (compte A) (8 min)

1. [ ] Ouvrez Profil > engrenage : la liste des Réglages montre **Mes informations, Mon compte, Langue, Confidentialité, puis Abonnement** (icône « + », sous-titre « Consulte ton abonnement »). Pas de bandeau « Débloquez l'ensemble des fonctionnalités », aucun prix, aucun essai gratuit.
2. [ ] Touchez **Abonnement** : l'écran affiche, de haut en bas, **Miff+** (en gras), « **Actif jusqu'au 12 janvier 2027** » (la date que vous avez saisie, écrite en toutes lettres), « **Accordé par l'équipe Miff** », puis la phrase « La gestion de l'abonnement via le store arrive bientôt. ».
3. [ ] **Aucun bouton** : ni « Changer d'abonnement », ni « Arrêter l'abonnement », ni « Prochain paiement », ni prix (« 4,99 € », « 6,99 € »), ni « presque 30 % d'économie ». Le mot « Premium » n'apparaît nulle part. Seule la flèche retour est touchable.
4. [ ] Flèche retour, puis retour du système : retour aux Réglages, la ligne est toujours là.
5. [ ] Passez l'app en **anglais** (Réglages > Langue) : « Subscription », « Active until January 12, 2027 », « Granted by the Miff team », « Managing your subscription through the store is coming soon. ». Repassez en français.
6. [ ] Dans la console, **changez `source`** en `google_play` (ou supprimez le champ), fermez les Réglages et rouvrez-les : l'écran dit « **Offre en cours** » à la place de « Accordé par l'équipe Miff » (jamais la mention d'une équipe).
7. [ ] Remettez `source` à `manual`.
8. [ ] TalkBack (facultatif) : « Abonnement » est annoncé comme un bouton ; sur l'écran, « Miff+ » est annoncé comme un titre. Agrandissez la police au maximum : tout reste lisible et défile.

## Partie B : compte gratuit (compte B) (4 min)

9. [ ] Réglages : **Mes informations, Mon compte, Langue, Confidentialité**, et **rien d'autre** : aucune ligne Abonnement, aucun encart, aucune trace du mot « Miff+ », d'un prix ou d'un essai (comme avant ABO-1).

## Partie C : compte Miff+ expiré (compte C) (3 min)

10. [ ] Même résultat que l'étape 9 (aucune ligne Abonnement), que `expiresAt` soit dans le passé ou que `active` soit `false`.
11. [ ] Avec le compte A : dans la console, mettez `expiresAt` dans le passé, **fermez** les Réglages et rouvrez-les : la ligne a **disparu** (la lecture se refait à chaque ouverture des Réglages ; pas besoin de tuer l'app). Remettez une date future pour la suite.

## Partie D : réglages restreints, hors connexion, cas limites (5 min)

12. [ ] **Compte sans Miff** (c'est le cas du compte B avant de créer sa Miff, ou un compte neuf) : sur l'écran « Choisis comment tu veux commencer », la roue crantée ouvre une liste **Mon compte + Confidentialité seulement** : **jamais** de ligne Abonnement, même si le compte a un document `entitlements` actif. *(Cette étape n'existe que si la PR #116 est fusionnée dans le build testé.)*
13. [ ] **Mode avion** avec le compte A, ouvrez les Réglages : la ligne **Abonnement n'apparaît pas** et **aucun message d'erreur** ne s'affiche (les autres lignes marchent). Réactivez le réseau, quittez les Réglages et rouvrez-les : la ligne apparaît.
14. [ ] Dans la console, vérifiez le document du compte A : **rien n'a changé** (`updatedAt` identique : l'app n'écrit jamais `entitlements`).
15. [ ] Lisez tous les textes : **ton calme**, aucune promesse de prix ni de date de fonctionnalité. Notez ce que vous voudriez changer (par exemple « via le store » : le nom exact « Google Play » viendra avec le ticket Premium).

## Contrôle de coût (facultatif, développeur)

Une ouverture des Réglages = **1 lecture** de `entitlements/{uid}` (console Firebase > Firestore > Utilisation). Revenir d'un sous-écran ou ouvrir l'écran Abonnement ne relit rien.

## Résultat

Étapes en échec : ______ . Points à corriger : ______ . Date et appareil : ______ .
