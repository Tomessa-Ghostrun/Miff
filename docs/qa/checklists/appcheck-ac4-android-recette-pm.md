# Recette Android AC-4 : interrupteur « Sans App Check » (build debug) et message « appareil non reconnu » : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Durée** : 10 minutes pour la partie A, 5 minutes pour la partie B.
**Ce que vous testez** : un réglage **réservé à la version de test** (celle qui a l'écran de diagnostic). Quand il est activé, l'app n'envoie **aucun jeton App Check**. Il servira plus tard à prouver qu'une fois App Check appliqué, une requête sans jeton est refusée (`docs/dev/app-check.md` §8, AC-4 et AC-7).
**Ce que vous ne faites PAS** : vous n'appliquez **rien** dans la console Firebase pour cette recette. App Check reste « Non appliqué » : l'app doit donc continuer à fonctionner normalement, même sans jeton.

## Règles à retenir

- Ne copiez jamais dans une conversation un « jeton de débogage » vu dans un journal technique.
- Si quelque chose ne se passe pas comme écrit : faites une capture d'écran, notez le numéro de l'étape, passez à la suivante.

## Partie A : l’interrupteur

### Étape 1. L'interrupteur existe, éteint (1 min)

- [ ] Installez l'APK **debug** de la PR (artefact `miff-debug-apk` de l'exécution de la CI) et ouvrez l'app : l'écran de diagnostic s'affiche.
- [ ] Dans la carte **App Check**, sous le bouton « Vérifier », une phrase explique le test et un interrupteur **« Sans App Check (au prochain lancement) »** est **éteint**. Aucun autre message.

### Étape 2. L'activer (2 min)

- [ ] Touchez « Vérifier » : notez le résultat (« Jeton obtenu » ou « Pas de jeton pour le moment » : les deux sont normaux, cela dépend de l'enregistrement de votre jeton de débogage).
- [ ] Allumez l'interrupteur. Le message **« Enregistré. Ferme complètement l'app puis rouvre-la : elle n'enverra alors aucun jeton. »** apparaît.
- [ ] Touchez « Vérifier » **sans relancer** : le résultat est **le même qu'avant** (le réglage ne s'applique qu'au prochain lancement : c'est voulu).

### Étape 3. Le lancement suivant n'envoie aucun jeton (3 min)

- [ ] **Fermez complètement l'app** (écran des applications récentes, balayez-la), puis rouvrez-la.
- [ ] Le message **« Actif : ce lancement n'envoie aucun jeton App Check (au mieux : l'app a essayé d'oublier le dernier jeton gardé par le SDK ; en cas de doute, efface les données de l'app ou attends plus d'1 h)… »** est affiché et l'interrupteur est allumé.
- [ ] Touchez « Vérifier » : **« Pas de jeton pour le moment… »**, même si vous aviez « Jeton obtenu » à l'étape 2 (ce bouton redemande le jeton au fournisseur sans jeton).
- [ ] **Pour la preuve réelle (AC-7), consigne fiable** : après avoir activé l'interrupteur, **effacez les données de l'app** (Réglages Android > Applications > Miff > Stockage > Effacer les données, puis reconnectez-vous) ou attendez plus d'1 h avant de rouvrir ; et regardez la **console App Check** (requêtes non vérifiées) plutôt que l'écran. L'oubli automatique du jeton gardé est « au mieux » et **non vérifié sur un téléphone** : dites-moi ce que vous constatez.
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

## Partie B : la phrase « appareil non reconnu » (5 min, avec l'interrupteur de la partie A)

**Rappel** : App Check n'est pas appliqué, donc le serveur ne refuse rien à cause de l'absence de jeton. Cette partie vérifie la **phrase** dans le seul cas qu'on peut provoquer sans toucher à la console : un refus ordinaire des règles, sur un appareil **sans jeton**. En vraie application d'App Check, la même phrase s'affichera (étape AC-7).

- [ ] Interrupteur « Sans App Check » **allumé** et app relancée (message « Actif »). Compte de test avec **une Miff déjà rejointe** (compte gratuit).
- [ ] « Rejoindre une Miff » avec un **faux code** valide en forme (par exemple « AAAAAAAAAAAA ») : la phrase habituelle **« On ne trouve pas de Miff avec ce code… »** (le serveur répond « code absent », pas un refus : rien ne change ici).
- [ ] Éteignez l'interrupteur, relancez : même parcours, **même phrase**. Les phrases du parcours normal sont donc **inchangées** quand l'appareil a un jeton.
- [ ] Phrase attendue en cas de refus sans jeton (à constater seulement lors de la vraie application, AC-7) : **« Cet appareil n'est pas reconnu pour le moment. Mets Miff à jour depuis le store, puis réessaie. »** Elle est calme, ne parle ni de « sécurité » ni de « piratage », et ne compte pas comme un faux code (pas de « petite pause d'une minute »).
- [ ] Relisez la phrase : tutoiement, aucun reproche, une action claire (mettre à jour). Si elle vous paraît inquiétante ou trop technique, dites-le : le texte est modifiable sans toucher au code.

**Limite à connaître** : avant l'application d'App Check, un appareil sans jeton (APK hors Google Play, build de test dont le jeton de débogage n'est pas enregistré) dont le refus vient en réalité des règles verra aussi cette phrase (`docs/dev/app-check.md` §8, limite 2).
