# Mise à jour des textes légaux du 21 novembre 2026 (version 1.2) : ce qui change, dans quel ordre publier, ce que tu dois valider

Préparé le 2026-11-21 par `release-manager`. **Rien n'est publié.** C'est toi, et toi seul, qui publies le site (aucun agent ne publie ni ne déploie). Ces textes sont écrits par l'équipe d'agents et publiés sous ta responsabilité ; **aucun juriste ne les a relus et aucun texte ne le prétend** (décision du 2026-10-23, risque assumé).

## 1. Ce qui change, en clair

Les textes (conditions d'utilisation et politique de confidentialité, en français et en anglais) décrivent maintenant **exactement** ce que l'app fait au 21 novembre :

1. **La date de la dernière photo.** Chaque membre a maintenant, dans son statut, la date de sa dernière photo publiée (une seule par Miff). Elle sert uniquement à classer les cartes du Feed (la photo la plus récente en premier). Elle **reste après que la photo a disparu** (24 h) tant que le partage est actif, et elle est **effacée avec le statut** (arrêt du partage, départ, retrait, suppression de la Miff ou du compte). Elle n'est jamais affichée comme une date. Les membres voient donc l'ordre des cartes, et peuvent en déduire qui a donné des nouvelles en dernier : le texte le dit.
2. **Un registre de 30 jours des notifications envoyées, sur nos serveurs.** À chaque notification envoyée à un proche (nouvelles, début ou fin d'alerte), le serveur garde une trace technique pour ce proche : le type, la Miff, l'identifiant de la personne concernée, la date. **Jamais de prénom, de photo, de lieu ni de position.** Il est supprimé au bout de 30 jours, par « Supprimer » ou « Tout effacer » dans le centre de notifications, ou à la suppression du compte. La phrase « nous ne gardons aucun historique des notifications envoyées » est **supprimée** parce qu'elle devient fausse. Le texte dit que le registre est « mis en service progressivement » et que, tant que le serveur ne l'écrit pas, rien n'est enregistré : **il est donc publiable dès maintenant**, sans changement le jour où tu déploies les fonctions.
3. **L'alerte « Je suis en danger » et les notifications aux proches** : j'ai revérifié les textes de la 1.1 contre le code et le ticket (ils n'ont pas bougé). Un seul ajout : le message de notification contient aussi un identifiant technique de la notification. J'ai aussi corrigé deux petites choses : les rappels de ton téléphone existent maintenant (« quand ils seront livrés » est retiré), et le rappel « Ta Miff est passée en orange » (calculé sur le téléphone du proche, rien ne part de nos serveurs) est décrit.
4. **Consentement à la localisation, version 4** (`2026-11-v04`, déjà dans les apps) : les textes expliquent maintenant la version 4, ce que voient les personnes restées en version 3 (une carte calme « Une précision sur ce que tu partages », au plus 3 fois), et ne disent plus que la version 3 « décrit » l'endroit de la photo (c'était inexact). Le nouveau fichier `consentement-localisation-v4.md` reprend les textes exacts des apps ; l'ancien (version 3) est archivé.
5. **Langues** : l'app est disponible en français, anglais, espagnol, italien, portugais (Portugal) et allemand, **mais les textes légaux restent en français et en anglais seulement** (décision du 2026-11-18, risque assumé, écrit comme tel). Les liens de l'app ouvrent la version anglaise pour les quatre autres langues. C'est écrit dans la politique (article 13) et les conditions (article 17).
6. **Petites corrections factuelles** : la phrase d'inscription est citée comme dans l'app (« En touchant » et non « En cliquant ») ; le centre de notifications du téléphone est décrit exactement (type, heure, Miff ; prénom seulement sur Android).

Chaque texte a, en tête, un court encadré « Ce qui a changé depuis la version 1.1 ». Version **1.2**, date **21 novembre 2026**. `termsVersion` dans les apps reste « 1.0 » (aucune acceptation forcée, comme tu l'as décidé le 18 novembre). Éditeur et contact inchangés (provisoires).

## 2. Dans quel ordre publier (chaque étape sur ton « oui »)

| # | Étape | Pourquoi |
|---|---|---|
| 1 | **Poser la règle TTL** `notifications.expireAt` dans Firestore (projet `miff-3d283`, groupe de collections `notifications`) et attendre l'état « Actif » : `docs/dev/deploiement-centre-notifications.md` §1 | Sans elle, les lignes ne s'effacent jamais et « 30 jours » dans le texte serait faux. **Je n'ai trouvé aucune trace dans le dépôt qu'elle soit posée.** |
| 2 | **Publier le site avec les textes 1.2** (mêmes commandes que `docs/dev/publication-textes-legaux-v1-1.md` ; vérifie que le jour est bien le 21 novembre, sinon dis-le-moi avant) | Le texte doit être en ligne **avant** que le serveur écrive le moindre registre |
| 3 | **Déployer les règles Firestore** (`photoAt` et registre) : `docs/dev/deploiement-regles.md`. Attention, un déploiement de règles publie **le fichier entier** de `main` : relis l'en-tête de `backend/firestore.rules` pour voir tout ce qui part avec | Règles d'abord, puis les apps qui écrivent `photoAt` (sinon leurs photos sont refusées). Les règles ne changent rien de visible tant que rien n'écrit |
| 4 | **Redéployer `notifierUpdate` et `notifierAlerte`** : `docs/dev/deploiement-centre-notifications.md` §3 | **C'est l'interrupteur du registre** : c'est le moment où les premières lignes s'écrivent. Le texte est déjà publié (étape 2) |
| 5 | Distribuer les apps (Android puis iOS) | Les apps qui écrivent `photoAt` ne doivent pas sortir avant le texte (consigne du ticket « Feed, ordre par photo ») |

Ce que **je ne peux pas savoir** : si la 1.1 (19 novembre) a été mise en ligne. Le dépôt n'en garde aucune trace. Si elle ne l'a pas été, la 1.2 la remplace : rien d'autre à faire ; si elle l'a été, la 1.2 est la suite.

## 3. Les 3 points à valider

1. **Base légale du registre de notifications.** Rien n'était décidé : j'ai écrit « Contrat (la fonction « centre de notifications » que tu utilises) ; l'envoi des notifications repose, lui, sur ton accord aux notifications ». L'autre option est « Intérêt légitime », ce qui ouvrirait un droit d'opposition (article 8). Je ne tranche pas : dis-moi ce que tu préfères, ou laisse « Contrat » (risque J67 f).
2. **La date de la dernière photo (risque J68).** Le ticket « Feed, ordre par photo » est un brouillon dont les défauts s'appliquent par ta délégation : tu n'as pas écrit que tu assumes ce risque (donnée gardée après la disparition de la photo ; l'ordre des cartes trahit « qui a donné des nouvelles en dernier » ; non vérifiable par le serveur). **Confirme que tu l'assumes**, ou dis-moi ce qu'il faut adoucir dans le texte.
3. **Date et état de la 1.1.** Dis-moi si la 1.1 est déjà en ligne et quel jour tu publies la 1.2 (le jour est écrit dans les quatre textes et dans un test).

## 4. Ce que cette mise à jour ne fait pas

- Elle ne déploie rien (règles, fonctions, site) et ne soumet rien à un store.
- Elle ne touche ni les fiches des stores ni les déclarations Apple / Google (Data safety, App Privacy) : le registre et la date de la dernière photo n'y sont pas déclarés. **À contrôler par `release-manager` avant toute soumission** (risque J71, non vérifié, aucune conformité affirmée).
- Elle ne change pas l'abonnement Miff+ : les conditions (article 9) restent génériques, le prix et l'essai (7 jours sur l'annuel seulement ; 59,88 € par an, soit 4,99 € par mois ; mensuel 6,99 €) sont affichés par les stores. **L'achat réel n'est pas encore dans le code** : quand il le sera, la politique devra décrire la validation serveur et le jeton d'achat.
- Elle ne développe pas la bulle bleue « En savoir plus » (J61) : prévenir dans l'app reste « lorsque c'est possible » (J47).
- Elle ne tranche aucune question juridique : tout est écrit en risque assumé ou en question (README, §4 et §15).

## 5. Contrôles faits

`node --test "web/tests/*.test.mjs"` (82 tests, dont 3 nouveaux pour la 1.2), `node scripts/build-web.mjs --demo` (10 pages, 0 emplacement), `node scripts/check-forbidden-files.mjs`, `node scripts/check-i18n-parity.mjs`. Revue d'exactitude par un second agent indépendant : voir la pull request.
