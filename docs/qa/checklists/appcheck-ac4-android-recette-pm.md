# Recette Android AC-4 : l'interrupteur « Sans App Check » (build debug) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Durée** : 10 minutes.
**Ce que vous testez** : un réglage **réservé à la version de test** (celle qui a l'écran de diagnostic). Quand il est activé, l'app n'envoie **aucun jeton App Check**. Il servira plus tard à prouver qu'une fois App Check appliqué, une requête sans jeton est refusée (`docs/dev/app-check.md` §8, AC-4 et AC-7).
**Ce que vous ne faites PAS** : vous n'appliquez **rien** dans la console Firebase pour cette recette. App Check reste « Non appliqué » : l'app doit donc continuer à fonctionner normalement, même sans jeton.

## Règles à retenir

- Ne copiez jamais dans une conversation un « jeton de débogage » vu dans un journal technique.
- Si quelque chose ne se passe pas comme écrit : faites une capture d'écran, notez le numéro de l'étape, passez à la suivante.

## Parcours

### Étape 1. L'interrupteur existe, éteint (1 min)

- [ ] Installez l'APK **debug** de la PR (artefact `miff-debug-apk` de l'exécution de la CI) et ouvrez l'app : l'écran de diagnostic s'affiche.
- [ ] Dans la carte **App Check**, sous le bouton « Vérifier », une phrase explique le test et un interrupteur **« Sans App Check (au prochain lancement) »** est **éteint**. Aucun autre message.

### Étape 2. L'activer (2 min)

- [ ] Touchez « Vérifier » : notez le résultat (« Jeton obtenu » ou « Pas de jeton pour le moment » : les deux sont normaux, cela dépend de l'enregistrement de votre jeton de débogage).
- [ ] Allumez l'interrupteur. Le message **« Enregistré. Ferme complètement l'app puis rouvre-la : elle n'enverra alors aucun jeton. »** apparaît.
- [ ] Touchez « Vérifier » **sans relancer** : le résultat est **le même qu'avant** (le réglage ne s'applique qu'au prochain lancement : c'est voulu).

### Étape 3. Le lancement suivant n'envoie aucun jeton (3 min)

- [ ] **Fermez complètement l'app** (écran des applications récentes, balayez-la), puis rouvrez-la.
- [ ] Le message **« Actif : ce lancement n'envoie aucun jeton App Check. Désactive l'interrupteur puis relance l'app pour revenir à la normale. »** est affiché et l'interrupteur est allumé.
- [ ] Touchez « Vérifier » : **« Pas de jeton pour le moment… »**, même si vous aviez « Jeton obtenu » à l'étape 2 (l'app a oublié le dernier jeton gardé).
- [ ] L'app **fonctionne encore** (App Check n'est pas appliqué) : saisissez le compte de test dans « Test 2 » (le mot de passe se tape uniquement sur le téléphone) : la connexion et la lecture réussissent.
- [ ] Console Firebase > App Check > onglet « API » > Cloud Firestore : toujours **« Non appliqué »**. Vous n'y avez touché à rien.

### Étape 4. Revenir à la normale (2 min)

- [ ] Éteignez l'interrupteur : le message **« Désactivé. Ce lancement n'envoie encore aucun jeton : ferme complètement l'app puis rouvre-la. »** apparaît.
- [ ] Fermez complètement l'app, rouvrez-la : plus aucun message sous l'interrupteur, qui est éteint. « Vérifier » redonne le résultat de l'étape 2.

### Étape 5. Absent des versions distribuables (vérifié par la CI, rien à faire pour vous)

- [ ] La CI de la PR est **verte**, dont l'étape « Check that the release bundle contains no debug-only code » (le contrôle cherche `DebugAppCheck` et `DEBUGAPPCHECKOFF` dans le bundle release et dans l'APK `releaseLike`).
- [ ] Sur un APK `releaseLike` (si vous en avez un), il n'y a **aucun écran de diagnostic**, donc aucun interrupteur.

## Ce qui reste à faire plus tard (pas dans cette recette)

- La **preuve de refus** elle-même (une requête sans jeton refusée par Firestore **appliqué**) se fait à l'étape AC-7 de `docs/dev/app-check.md`, après la mesure, **avec votre accord explicite et daté** : critère R18 du ticket de durcissement.
