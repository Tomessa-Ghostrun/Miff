# Mise à jour des textes légaux du 24 novembre 2026 (version 1.3) : ce qui change, dans quel ordre publier, ce que tu dois valider

Préparé le 2026-11-22 par `release-manager`. **Rien n'est publié.** C'est toi, et toi seul, qui publies le site (aucun agent ne publie ni ne déploie). Ces textes sont écrits par l'équipe d'agents et publiés sous ta responsabilité ; **aucun juriste ne les a relus et aucun texte ne le prétend** (décision du 2026-10-23, risque assumé).

Cette note **complète** `maj-2026-11-21-a-publier.md` (version 1.2), qu'elle ne remplace pas : le dépôt ne garde aucune trace de la mise en ligne de la 1.2 ni de la 1.1. **La 1.3 contient tout ce que contenait la 1.2** (date de la dernière photo, registre de 30 jours, consentement v4, langues) **et y ajoute** ce qui suit. Si la 1.2 n'a jamais été publiée, la 1.3 la remplace d'un coup ; si elle l'a été, la 1.3 en est la suite.

## 1. Ce qui change, en clair

Pourquoi maintenant : la PR iPhone **#311** (branche `ios/interrupteur-statistiques`, CI verte, en recette) livre sur iPhone l'interrupteur « statistiques d'usage et rapports de plantage » dans Réglages > Confidentialité, qui existait déjà sur Android. Le retrait de cet accord se fait donc **dans l'app, sur iPhone comme sur Android** : les textes ne disent plus « écris-nous » pour l'iPhone.

1. **Le retrait des statistiques, dans les Réglages sur les deux téléphones.** Les phrases « sur iPhone, tant que cet interrupteur n'est pas livré, … en écrivant à tomessa.mfouka@gmail.com » sont **supprimées** (politique, article 3, FR et EN ; conditions, article 3, FR et EN). Le résumé (point 8) et l'article 8 de la politique le disent aussi. L'adresse de contact reste, comme avant, le moyen d'exercer tous tes droits (article 8) : on n'enlève rien d'autre.
2. **Une « marque de retrait en attente »**, gardée **sur le téléphone seulement** quand quelqu'un retire cet accord et que le retrait **n'a pas pu être enregistré tout de suite** dans son compte (pas de réseau, ou le serveur n'a pas pris le choix : c'est ce que font les deux apps) : une empreinte à sens unique de son compte (SHA-256, une pseudonymisation) et l'heure, rien d'autre. Elle sert à garder la collecte coupée même si l'app est fermée, tant que le compte ne connaît pas le choix. **Elle n'est jamais envoyée**, exclue des sauvegardes, effacée quand le retrait est enregistré dans le compte, quand la personne accepte de nouveau, ou quand elle supprime son compte. **Elle n'est pas effacée par une simple déconnexion** (elle attend son compte, n'est jamais appliquée à un autre ; 20 comptes au plus par téléphone). Écrit dans la politique : article 3 (ligne « Preuves de consentement » et « Comment les retirer »), article 7 (nouvelle ligne et exception de la ligne « Données stockées sur ton téléphone »), article 11. J'ai lu le code Android (`PendingWithdrawals.kt`, `ConsentWithdrawals.kt`, `SessionManager.kt`, `backup_rules.xml`) et celui de la PR #311 (`PendingWithdrawals.swift`, `WithdrawalFileStorage.swift`) : les deux plateformes font la même chose.
3. **La phrase d'inscription de l'iPhone.** Elle cite aussi « Continuer avec Apple » (`ios_auth_consent_notice`) ; la politique et les conditions le disent maintenant (article 3 des deux, résumé point 8), ainsi que l'écran de consentement affiché à une personne nouvelle arrivée par Apple depuis « Se connecter » (comme pour Google).
4. **Rien sur Miff+, ni sur les prix.** L'achat réel n'est pas en service : le serveur de facturation (SRV-7) est écrit mais **non déployé**, et les apps n'ont pas l'achat. Les textes n'annoncent donc rien. Un test vérifie qu'aucun prix (59,88 / 4,99 / 6,99) ni « aucune référence d'achat » n'apparaît dans les quatre textes.

Version **1.3**, date **24 novembre 2026** (la date est écrite en tête de chaque texte, et en un seul endroit dans le test : `VERSION` / `DATE_FR` / `DATE_EN` de `web/tests/legal-1-1.test.mjs`). Un court encadré « Ce qui a changé depuis la version 1.2 » est en tête de chaque texte, suivi de celui de la 1.2 gardé pour mémoire. `termsVersion` dans les apps reste « 1.0 » (aucune acceptation forcée). Éditeur et contact inchangés (provisoires).

## 2. Dans quel ordre publier (chaque étape sur ton « oui »)

| # | Étape | Pourquoi |
|---|---|---|
| 1 | **Règle TTL** `notifications.expireAt` dans Firestore, état « Actif » : `docs/dev/deploiement-centre-notifications.md` §1 | Sans elle, « 30 jours » serait faux dès que le serveur écrit le registre. Aucune trace dans le dépôt qu'elle soit posée |
| 2 | **Publier le site avec les textes 1.3** : `docs/dev/publication-textes-legaux-v1-3.md` (la 1.2 est archivée) | **Le texte doit être en ligne AVANT** la distribution du build iPhone qui contient l'interrupteur, et avant les fonctions du registre |
| 3 | **Règles Firestore** (`photoAt`, registre) : `docs/dev/deploiement-regles.md` | Règles d'abord, puis les apps qui écrivent `photoAt` |
| 4 | **Fonctions** `notifierUpdate` et `notifierAlerte` : `docs/dev/deploiement-centre-notifications.md` §3 | C'est le moment où les premières lignes du registre s'écrivent |
| 5 | **Apps** : Android, puis **iPhone, une fois la PR #311 fusionnée** (sur ton « oui » à ta recette) | Le texte 1.3 dit « dans les Réglages » pour l'iPhone : c'est vrai avec ce build seulement |

**Pourquoi le texte d'abord.** Le build iPhone de la PR #311 change la phrase d'inscription (« modifiable à tout moment dans les Réglages », au lieu de « écris-nous ») : un testeur qui l'installe doit trouver des textes en ligne qui disent la même chose. Dans l'autre sens, une fois le texte 1.3 en ligne, **un iPhone resté sur un ancien build n'a pas l'interrupteur** : publie donc la 1.3 **peu de temps avant** de distribuer le build, pas des semaines avant (risque J73 de `README.md`). Si tu publies la 1.3 et que le build iPhone n'est pas distribué, rien ne casse : le texte est simplement en avance de quelques jours sur l'iPhone, et l'adresse de contact reste là pour tout retrait (article 8).

## 3. Les points à valider

1. **L'écart entre le texte et le build iPhone (J73).** Es-tu d'accord pour publier la 1.3 juste avant de distribuer le build de la PR #311 (écart de quelques heures à quelques jours), plutôt que de la retarder jusqu'à la distribution ? Alternative : publier la 1.3 en même temps que la distribution, et ne pas distribuer l'iPhone avant.
2. **La marque de retrait en attente (J74).** Je l'ai écrite comme une pseudonymisation locale, jamais envoyée, exclue des sauvegardes, conservée à la déconnexion. Dis-moi si tu préfères une formulation plus courte dans la politique (la donnée est, par nature, peu sensible : une empreinte et une heure, sur le téléphone).
3. **Les points de la 1.2 restent ouverts** (`maj-2026-11-21-a-publier.md` §3, points 1 à 3) : base légale du registre (« Contrat » par défaut), risque J68 (date de la dernière photo) à assumer ou non, état des 1.1 et 1.2 en ligne, et le jour de publication (le 24 novembre est écrit : **dis-moi s'il change**).
4. **Le point 4 de la 1.2 (J72) est résolu côté textes** : plus d'« interrupteur à livrer ou risque à assumer » une fois la PR #311 fusionnée et distribuée.

## 4. Ce que cette mise à jour ne fait pas

- Elle ne déploie rien (site, règles, fonctions) et ne soumet rien à un store.
- Elle ne touche ni les fiches des stores ni les déclarations Apple / Google (J71 : non vérifié). Le journal de la PR #311 dit que `PrivacyInfo.xcprivacy` ne change aucune catégorie ; je n'ai pas contrôlé la déclaration « App Privacy » elle-même.
- **Elle ne parle pas de Miff+ ni de prix.** À la livraison de l'achat réel (ticket `docs/tickets/premium-achat-reel.md`, SRV-3), la politique devra dire que **le serveur garde une empreinte (SHA-256, pseudonymisation) de la référence d'achat, jamais la référence elle-même** (`backend/data-model.md` §2.23) : écrire « empreinte (pseudonymisation) de la référence d'achat », et **non** « aucune référence d'achat » ; elle devra aussi citer l'identifiant d'achat aléatoire relié au compte (`billingAccounts.purchaseId`, §2.22). Les prix et l'essai restent affichés par les stores.
- Elle ne développe pas la bulle bleue « En savoir plus » (J61) : prévenir dans l'app reste « lorsque c'est possible » (J47).
- Elle ne tranche aucune question juridique : tout est écrit en risque assumé ou en question (`README.md` §4, §15 et §16).

## 5. Contrôles faits

`node --test "web/tests/*.test.mjs"` (87 tests, dont 3 nouveaux pour la 1.3, et 4 assertions de la 1.2 inversées : l'ancienne réserve iPhone « par e-mail » ne doit plus figurer), `node scripts/build-web.mjs --demo` (10 pages, 0 emplacement), `node scripts/check-forbidden-files.mjs`, `node scripts/check-i18n-parity.mjs`. Revue d'exactitude par un second agent indépendant (contexte neuf) : voir la PR.
