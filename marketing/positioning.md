# Positionnement de Miff

**Statut : brouillon de l'agent `aso-marketing`, à valider par le PM. Rien n'est publié.**
Rédigé le 2026-11-07. Sources : `CLAUDE.md`, `docs/specs/*`, `docs/tickets/feed-profil-reglages.md`, `docs/legal/*`, `web/src/landing.json` (textes déjà écrits pour la page d'accueil, dont ce document reprend le ton).

Ce document est la référence de ton et de vocabulaire pour toutes les autres pièces de `marketing/` (fiches des stores, plan de lancement, légendes de captures, contenus). En cas de doute sur une phrase, on revient ici.

---

## 1. La promesse

**Promesse centrale (reprise du pitch du PM)** : *savoir que ses proches vont bien, sans les déranger.*

En anglais : *know your loved ones are okay, without bothering them.*

Ce qu'elle veut dire, concrètement et sans en rajouter :

- Les proches ne se posent plus la question « il/elle est bien arrivé(e) ? » : un statut simple existe quand on le cherche.
- Personne n'a de message à écrire ni de réponse à donner : la détection d'une arrivée ou d'un départ d'un lieu de confiance se fait toute seule, **si la personne a accepté de partager**.
- Quand on a envie de donner des nouvelles (une photo, un petit mot), on peut le faire en un geste.
- Chacun garde la main : le partage est consenti, par Miff, et révocable à tout moment.

Ce que la promesse **ne veut pas dire** (à ne jamais écrire) : « tu seras toujours rassuré(e) », « tes proches sont en sécurité », « tu sauras toujours où ils sont ». Les CGU le disent déjà : un statut « tout va bien » n'est pas une garantie, un statut en retard ne veut pas forcément dire qu'il y a un problème, et Miff n'est pas un service d'urgence.

### Formulations validées (déjà en ligne sur la page d'accueil du dépôt, `web/src/landing.json`)

| Usage | FR | EN |
|---|---|---|
| Titre principal | Savoir que tes proches vont bien, sans les déranger. | Know your loved ones are okay, without bothering them. |
| Slogan court | Des nouvelles, sans déranger. | News, without the interruptions. (proposition, voir note) |
| Trois mots | Simple. Consenti. Discret. | Simple. Consented. Quiet. (proposition) |
| Principe | Rassurer sans surveiller. | Reassure without watching. |

Note : « News, without the interruptions » est une proposition de l'agent, pas encore validée ; l'équivalent anglais exact de « Des nouvelles, sans déranger » n'existe pas dans le dépôt.

---

## 2. Pour qui ? (audience cible)

Miff n'est pas limité à la relation parent-enfant, même si c'est un cas d'usage probable (`CLAUDE.md`). Le cercle de confiance peut être familial, amical ou romantique. Le produit a un effet de réseau : **il ne sert à rien tout seul**, il faut au moins deux personnes dans une Miff. Chaque message doit donc s'adresser à la fois à celui qui s'inquiète un peu et à celui qui sera « rassurant » (celui qui installe l'app pour faire plaisir à un proche). Le second est le plus difficile à convaincre : le message doit lui dire « ça ne te demande rien et tu gardes la main ».

### Trois cas d'usage (seuls retenus, ancrés dans le produit réel)

| Cas | Situation | Ce que montre Miff | Ce qu'on ne dit pas |
|---|---|---|---|
| **Famille** | Retour de l'école, du travail, chez mamie. Un parent, un grand-parent, des frères et sœurs, un adulte et son parent âgé. | Un statut simple (« À la maison »), l'heure, éventuellement une photo envoyée par la personne. | Pas « surveiller ses enfants ». Les mineurs : voir section 5. |
| **Amis** | Retour de soirée, colocation, week-end à plusieurs. | On voit que chacun est bien rentré sans lancer de conversation. | Pas « savoir où sont tes amis » ni de carte en direct. |
| **Couple** | Un coup d'œil pour savoir que l'autre est bien arrivé(e). | Chacun garde son espace et la liberté de partager ou non. | Jamais de formulation qui laisse entendre un contrôle de l'autre. C'est le cas d'usage le plus sensible : le ton doit insister sur l'accord mutuel. |

### Personas de travail (hypothèses, à confirmer avec les testeurs de la bêta)

- **Camille, 38 ans, parent** : s'inquiète un peu quand son ado rentre seul(e). Veut une information, pas une conversation de plus. Peur : être perçue comme intrusive.
- **Léo, 17 ans** : accepte de partager si c'est simple, discret, et si ses parents ne voient pas plus que « à la maison ». Peur : être pisté(e).
- **Nadia et Yann, couple** : veulent un petit confort partagé, sans que cela devienne du contrôle.
- **Marie, 74 ans** : un proche lui installe l'app. Besoin : très peu d'étapes, rien à écrire.

Ces personas sont des **hypothèses de travail**, pas des études d'utilisateurs. Elles servent à écrire des messages, pas à décider du produit.

---

## 3. Le ton

Reprend `CLAUDE.md` (« bienveillant, non anxiogène ») et le tutoiement décidé par le PM.

- **Tutoiement partout** (FR). En anglais : phrases directes, sans formalisme.
- **Calme et concret** : des phrases courtes, des exemples de la vie courante (la maison, l'école, le travail), pas de chiffres alarmants.
- **Informer, jamais alarmer** : « plus de nouvelles depuis 16 h » rassure ou informe, il ne panique pas.
- **Honnête sur les limites** : la détection dépend du téléphone et peut être en retard ; Miff n'est pas un service d'urgence. On le dit simplement, sans le cacher en bas de page.
- **Pas de culpabilisation** : jamais « un bon parent… », jamais « ne laisse pas tes proches sans nouvelles ».
- **Pas d'argumentaire de peur** : aucun fait divers, aucune statistique d'agressions ou de disparitions, aucun scénario catastrophe, même en « storytelling ».

---

## 4. Vocabulaire

### À utiliser

| FR | EN | Pourquoi |
|---|---|---|
| proches, cercle de confiance, ceux qui comptent pour toi | loved ones, circle of trust, the people who matter to you | Cadre relationnel, pas technique |
| Miff (le groupe) | a Miff | Le mot du produit ; une Miff = un cercle |
| lieu de confiance | trusted place | Terme du produit |
| des nouvelles, un statut simple | news, a simple status | Informer sans suivre |
| mise à jour (photo + petit mot) | update (photo + short note) | Donner des nouvelles quand on veut |
| tu choisis ce que tu partages | you choose what you share | Consentement |
| partage consenti, révocable, Miff par Miff | consented, revocable sharing, Miff by Miff | Consentement individuel |
| tu peux arrêter à tout moment | you can stop at any time | Révocabilité |
| arrivée / départ d'un lieu de confiance | arriving at / leaving a trusted place | Ce que le produit fait réellement |
| sans les déranger / sans conversation imposée | without bothering them / no forced conversation | Cœur de la promesse |
| rassurer, se rassurer | reassure, feel reassured | Registre du soulagement |
| code secret | secret code | Terme du produit |

### À éviter

| À éviter | Pourquoi | À la place |
|---|---|---|
| surveiller, surveillance, traquer, tracker, pister, espionner, filer | Contraire au produit et motif de rejet des stores. Les CGU interdisent de suivre quelqu'un à son insu. | « savoir que ça va », « avoir des nouvelles » |
| « suivi en direct », « temps réel », « où est-il/elle en ce moment ? », GPS en direct | Faux : Miff n'est pas un suivi en direct, et les textes légaux disent « pas de suivi en temps réel » | « un statut simple », « arrivée / départ » |
| sécurité garantie, « en sécurité », protéger, protection, sauver, urgence, SOS, alerte | Miff n'est pas un service d'urgence et ne garantit rien. Le ticket Feed a déjà jugé « Tout le monde est en sécurité » trop promettant. | « tout va bien », « des nouvelles » |
| danger, risque, menace, inquiétant, anxieux, angoisse, stress (comme argument de vente) | Anxiogène | « se rassurer », « un peu d'inquiétude du quotidien » (avec parcimonie) |
| « tu devrais », « il faut que », « un bon parent / un bon ami » | Culpabilisant, moralisateur | formulations à l'indicatif ou à l'invitation |
| contrôler, savoir tout, ne rien manquer, « sans qu'il/elle le sache » | Contraire au consentement | « avec son accord » |
| « Premium », « Miff+ », prix, essai gratuit | **Non actif** : aucune mention dans les fiches des stores (voir plan de lancement) | (rien) |
| « notifications », « alertes automatiques » | **Non actives** : la page d'accueil mentionne des notifications dans sa maquette, à corriger ou à qualifier | « tu ouvres l'app, tu vois » |
| « anonyme », « 100 % privé », « données jamais partagées » | Promesses absolues non tenables (les textes légaux disent que même les statistiques ne sont pas anonymes au sens strict) | décrire précisément ce qui est partagé et avec qui |
| « Miff ne stocke pas ta position GPS » | **À ne pas répéter tant que le PM n'a pas tranché** : une décision du 2026-11-01 stocke la dernière position connue côté serveur (voir « Points de vigilance » ci-dessous) | « tu choisis ce que tu partages, avec qui, et tu peux arrêter » |
| noms de marques tierces (autres apps, réseaux, constructeurs) | Règle du PM pour ce chantier | termes génériques |

---

## 5. Consentement et confiance : à rappeler dans tout contenu

À chaque fois qu'on parle de localisation ou de détection, au moins une de ces idées doit être présente, avec des mots simples :

1. **Le partage est consenti** : il ne démarre qu'après un accord explicite, Miff par Miff, distinct de l'inscription.
2. **Il est révocable** : on peut l'arrêter à tout moment, sans se justifier ; l'arrêt retire le statut et la photo de mise à jour de ce que voit le cercle.
3. **Seul le cercle voit** : les membres d'une Miff, qu'on ne rejoint qu'avec un code secret ; pas de Miff publique, pas d'annonceurs.
4. **Aucune publicité, aucune revente de données** (affirmé dans la politique de confidentialité).
5. **Miff reste utilisable sans la localisation en arrière-plan**, avec des mises à jour à la main. On ne force pas.
6. **Explication avant la demande** : l'app explique à quoi sert la localisation avant que le téléphone ne demande l'autorisation.
7. **Pas d'usage à l'insu de quelqu'un** : les CGU l'interdisent. Les contenus ne présentent jamais Miff comme un moyen de suivre quelqu'un qui ne le sait pas.

Rappel de transparence à garder présent : **la détection dépend du téléphone et peut être retardée**, et Miff n'est pas un service d'urgence (en cas de doute sérieux : 112).

---

## 6. Différenciation (sans nommer personne)

Le marché des applications de partage de position entre proches est encombré (observation du 2026-11-07 : une recherche dans l'App Store montre de nombreuses applications de « localisation familiale » qui mettent en avant la carte en direct, les alertes de zone et la possibilité de rejoindre un cercle avec un code d'invitation). Pour ne pas se noyer, Miff ne doit pas entrer sur leur terrain (la carte) :

| Eux (typiquement) | Miff |
|---|---|
| La carte, la position en direct | Un statut simple, pas une carte à suivre |
| Un outil de contrôle | Un outil de réassurance consentie |
| « Sache où ils sont » | « Sache que ça va, sans déranger » |
| Beaucoup d'options, d'alertes | Peu d'options, rien à faire |

Cette différenciation n'est **pas une preuve** que le marché la récompense : c'est une hypothèse (voir `aso-keywords.md`, H1 et H2). Le risque : les personnes qui cherchent explicitement une carte en direct ne trouveront pas ce qu'elles veulent dans Miff, et le diront dans leurs avis. Il vaut mieux les décourager honnêtement dans la fiche (« pas de carte en direct »), mais **seulement** si la décision sur le stockage de la position (point de vigilance 1) est tranchée.

---

## 7. Points de vigilance (à trancher avec le PM, voir aussi la liste de décisions du rapport)

1. **Position stockée côté serveur ou non** : les textes légaux (`docs/legal/*`), la page d'accueil (`web/src/landing.json`, « Miff ne stocke pas ta position GPS ») et la CGU article 6 disent que Miff ne stocke pas la position GPS. Une décision du PM du 2026-11-01 stocke la **dernière position connue** côté serveur (pour « Localiser » dans le Feed et pour la localisation des photos de mise à jour). Je n'ai **pas pu relire** `docs/release/*` (absent de ma copie du dépôt, voir le rapport) et je ne sais pas si les textes légaux ont été mis à jour depuis. **Tant que ce n'est pas tranché, aucun contenu marketing ne dit « Miff ne stocke pas ta position » ni son contraire.** Les fiches des stores restent sur « tu choisis ce que tu partages ».
2. **Mineurs** : Miff ne vérifie pas l'âge et n'impose aucun âge minimum (risque J2 assumé par le PM). Aucun contenu marketing ne doit viser des enfants (pas de visuels d'enfants, pas de « pour les parents d'enfants de 8 ans », pas de catégorie « Enfants »). Voir les propositions de classification d'âge dans les fiches des stores.
3. **Couple** : cas d'usage à ne pas surexposer en pub. Il attire de l'attention mais il est le plus proche du contrôle. Toujours associer « chacun garde sa liberté de partager ou non ».
4. **Notifications et abonnement non actifs** : la maquette de la page d'accueil montre deux notifications calmes ; tant qu'elles n'existent pas, c'est une promesse non livrée. À corriger côté `web/` (hors périmètre de ce chantier, signalé au PM).
5. **« Économisez 30 % »** : écart assumé par le PM dans le paywall (le calcul réel est 28,6 %). Il ne doit **jamais** apparaître dans les fiches des stores ni dans les contenus (risque de mention publicitaire inexacte, `paywall-premium.md`).
